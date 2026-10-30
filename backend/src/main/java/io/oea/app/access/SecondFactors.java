package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.RecentAuthentication;
import io.oea.app.security.Sessions;
import io.oea.app.security.SetupPhase;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Second factors of a principal (design D5, D6 of us-021-person-totp): the overview and removal of
 * the own factors, and the factor reset of a person by an administrator (REQ-173 AC5).
 */
@Service
public class SecondFactors {

	/** Types of local credentials that are second factors. */
	private static final String SECOND_FACTOR_TYPES = "('totp', 'passkey')";

	private final JdbcTemplate jdbc;

	private final RecentAuthentication recentAuthentication;

	private final SetupPhase setupPhase;

	private final PermissionDenials denials;

	private final AuditWriter audit;

	private final Clock clock;

	public SecondFactors(JdbcTemplate jdbc, RecentAuthentication recentAuthentication, SetupPhase setupPhase,
			PermissionDenials denials, AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.recentAuthentication = recentAuthentication;
		this.setupPhase = setupPhase;
		this.denials = denials;
		this.audit = audit;
		this.clock = clock;
	}

	/** An active second factor; never with a secret. */
	public record Factor(UUID credentialId, String type, String label, Instant createdAt, Instant lastUsedAt) {
	}

	/** The active second factors of the principal, ordered by creation. */
	@Transactional(readOnly = true)
	public List<Factor> list(OeaPrincipal principal) {
		return this.jdbc.query("""
				SELECT c.id, c.type, t.label, c.created_at, c.last_used_at FROM local_credentials c
				LEFT JOIN totp_credentials t ON t.credential_id = c.id
				WHERE (c.person_id = ? OR c.system_admin_account_id = ?) AND c.status = 'active'
				  AND c.type IN """ + SECOND_FACTOR_TYPES + " ORDER BY c.created_at, c.id", (rs, n) -> {
			Timestamp used = rs.getTimestamp(5);
			return new Factor(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
					rs.getTimestamp(4).toInstant(), (used != null) ? used.toInstant() : null);
		}, principal.personId(), principal.systemAdminAccountId());
	}

	/**
	 * Removes an own second factor (D5).
	 * @throws NoSuchElementException for an unknown, foreign or inactive credential
	 * @throws LastSecondFactorException for the last TOTP credential of the system admin account
	 * after the setup phase
	 */
	@Transactional
	public void remove(OeaPrincipal principal, UUID credentialId) {
		this.recentAuthentication.require(principal);
		Factor factor = list(principal).stream()
			.filter((f) -> f.credentialId().equals(credentialId))
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no second factor " + credentialId));
		if (principal.type() == OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT && "totp".equals(factor.type())
				&& !this.setupPhase.isActive()
				&& list(principal).stream().filter((f) -> "totp".equals(f.type())).count() == 1) {
			throw new LastSecondFactorException();
		}
		Instant now = this.clock.instant();
		this.jdbc.update("""
				UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = 'person_request',
				       revoked_by_person_id = ?, revoked_by_admin_id = ?
				WHERE id = ?""", Timestamp.from(now), principal.personId(), principal.systemAdminAccountId(),
				credentialId);
		int others = this.jdbc.update("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = 'credential-revoked', revoked_by_person_id = ?,
				       revoked_by_admin_id = ?
				WHERE id <> ? AND revoked_at IS NULL AND (person_id = ? OR system_admin_account_id = ?)""",
				Timestamp.from(now), principal.personId(), principal.systemAdminAccountId(), principal.sessionId(),
				principal.personId(), principal.systemAdminAccountId());
		Map<String, Object> details = new HashMap<>();
		details.put("type", factor.type());
		if (factor.label() != null) {
			details.put("label", factor.label());
		}
		details.put("otherSessionsRevoked", others);
		this.audit.append(new AuditEvent("auth.factor-removed", AuditEvent.Category.SECURITY, AuditEvent.Severity.HIGH,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "credential",
				credentialId.toString(), details));
	}

	/**
	 * Resets the second factors of a person (D6).
	 * @param reason optional reason, recorded in the audit event
	 * @return the number of revoked second factors
	 * @throws NoSuchElementException for an unknown person
	 * @throws InvalidAccessRequestException for a service account
	 * @throws SelfAdministrationException for the caller's own person (SoD-1)
	 * @throws AccessConflictException for a person without an active second factor
	 * @throws PasswordResets.AdminResetRestrictedException for an administrator reset by someone
	 * else than the system admin account
	 */
	@Transactional
	public int reset(OeaPrincipal principal, long personId, String reason) {
		List<String> types = this.jdbc.queryForList("SELECT person_type FROM persons WHERE id = ? FOR UPDATE",
				String.class, personId);
		if (types.isEmpty()) {
			throw new NoSuchElementException("no person " + personId);
		}
		if (!"human".equals(types.getFirst())) {
			throw new InvalidAccessRequestException("personId", "Service accounts have no second factors.");
		}
		if (principal.personId() != null && principal.personId() == personId) {
			this.denials.record(principal, "resetPersonSecondFactors", "person", Long.toString(personId),
					PermissionDenials.SELF_ADMINISTRATION);
			throw new SelfAdministrationException();
		}
		Integer active = this.jdbc.queryForObject("""
				SELECT count(*) FROM local_credentials WHERE person_id = ? AND status = 'active' AND type IN """
				+ SECOND_FACTOR_TYPES, Integer.class, personId);
		if (active == null || active == 0) {
			throw new AccessConflictException("The person has no second factor to reset.");
		}
		Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
		if (principal.type() != OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT
				&& PasswordResets.holdsAdminClassPermission(this.jdbc, personId, now)) {
			this.denials.record(principal, "resetPersonSecondFactors", "person", Long.toString(personId),
					PasswordResets.ADMIN_RESET_RESTRICTED);
			throw new PasswordResets.AdminResetRestrictedException();
		}
		// pending enrollments end as well, so that no half-finished factor survives the reset
		int revoked = this.jdbc.update("""
				UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = 'factor_reset',
				       revoked_by_person_id = ?, revoked_by_admin_id = ?
				WHERE person_id = ? AND status IN ('active', 'pending') AND type IN """ + SECOND_FACTOR_TYPES,
				Timestamp.from(now), principal.personId(), principal.systemAdminAccountId(), personId);
		List<UUID> sessions = this.jdbc.queryForList("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = 'factor-reset', revoked_by_person_id = ?,
				       revoked_by_admin_id = ?
				WHERE person_id = ? AND revoked_at IS NULL RETURNING id""", UUID.class, Timestamp.from(now),
				principal.personId(), principal.systemAdminAccountId(), personId);
		for (UUID session : sessions) {
			this.audit.append(new AuditEvent("session.revoked", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
					AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "session",
					session.toString(), Map.of("reason", "factor-reset")));
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("credentialsRevoked", revoked);
		details.put("sessionsRevoked", sessions.size());
		if (reason != null && !reason.isBlank()) {
			details.put("reason", reason);
		}
		this.audit.append(new AuditEvent("person.second-factors-reset", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"person", Long.toString(personId), details));
		return revoked;
	}

}

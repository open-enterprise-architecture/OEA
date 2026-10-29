package io.oea.app.access;

import io.oea.app.login.PersonPasswordProperties;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.service.PasswordHasher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reset of a person's local password by an administrator (US-030, REQ-031; design D6 of
 * us-027-password-administration): the active password is revoked and replaced by a generated
 * temporary one, returned once; every session of the person ends; one transaction.
 */
@Service
public class PasswordResets {

	/** Reason of the denial: an administrator is reset by someone else than the system admin account (REQ-173 AC3). */
	public static final String ADMIN_RESET_RESTRICTED = "admin-reset-restricted";

	private final JdbcTemplate jdbc;

	private final PasswordHasher hasher;

	private final PasswordPolicies policies;

	private final PersonPasswordProperties properties;

	private final PermissionDenials denials;

	private final AuditWriter audit;

	private final Clock clock;

	public PasswordResets(JdbcTemplate jdbc, PasswordHasher hasher, PasswordPolicies policies, PersonPasswordProperties properties, PermissionDenials denials, AuditWriter audit,
			Clock clock) {
		this.jdbc = jdbc;
		this.hasher = hasher;
		this.policies = policies;
		this.properties = properties;
		this.denials = denials;
		this.audit = audit;
		this.clock = clock;
	}

	/** The generated temporary password, returned this once, and its expiry. */
	public record Result(String password, Instant expiresAt) {
	}

	/**
	 * Resets the password.
	 * @param reason optional reason, recorded in the audit event
	 * @throws NoSuchElementException for an unknown person
	 * @throws InvalidAccessRequestException for a service account
	 * @throws AccessConflictException while password login of persons is not possible, or for a
	 * person without a password
	 * @throws SelfAdministrationException for the caller's own person (SoD-1)
	 * @throws AdminResetRestrictedException for an administrator reset by someone else than the
	 * system admin account
	 */
	@Transactional
	public Result reset(OeaPrincipal principal, long personId, String reason) {
		List<String> types = this.jdbc.queryForList("SELECT person_type FROM persons WHERE id = ? FOR UPDATE",
				String.class, personId);
		if (types.isEmpty()) {
			throw new NoSuchElementException("no person " + personId);
		}
		if (!"human".equals(types.getFirst())) {
			throw new InvalidAccessRequestException("personId", "Service accounts have no password.");
		}
		if (principal.personId() != null && principal.personId() == personId) {
			this.denials.record(principal, "resetPersonPassword", "person", Long.toString(personId),
					PermissionDenials.SELF_ADMINISTRATION);
			throw new SelfAdministrationException();
		}
		if (!this.policies.personPasswordLoginPossible()) {
			throw new AccessConflictException("Password login of persons is switched off on this instance.");
		}
		List<UUID> active = this.jdbc.queryForList("""
				SELECT id FROM local_credentials WHERE person_id = ? AND type = 'password' AND status = 'active'
				FOR UPDATE""", UUID.class, personId);
		if (active.isEmpty()) {
			throw new AccessConflictException("The person has no password to reset.");
		}
		Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
		if (principal.type() != OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT && holdsAdminClassPermission(personId, now)) {
			this.denials.record(principal, "resetPersonPassword", "person", Long.toString(personId),
					ADMIN_RESET_RESTRICTED);
			throw new AdminResetRestrictedException();
		}
		this.jdbc.update("""
				UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = 'admin_action',
				       revoked_by_person_id = ?, revoked_by_admin_id = ?
				WHERE id = ?""", Timestamp.from(now), principal.personId(), principal.systemAdminAccountId(),
				active.getFirst());
		String password = this.policies.generate();
		Instant expiresAt = now.plus(this.properties.temporaryPasswordValidity());
		UUID credentialId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO local_credentials (id, person_id, type, status, created_at)
				VALUES (?, ?, 'password', 'active', ?)""", credentialId, personId, Timestamp.from(now));
		this.jdbc.update("""
				INSERT INTO password_credentials (credential_id, password_hash, origin, expires_at)
				VALUES (?, ?, 'admin-reset', ?)""", credentialId, this.hasher.hash(password), Timestamp.from(expiresAt));
		int sessions = revokeSessions(principal, personId, now);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("expiresAt", expiresAt.toString());
		details.put("sessionsRevoked", sessions);
		if (reason != null && !reason.isBlank()) {
			details.put("reason", reason);
		}
		this.audit.append(new AuditEvent("person.password-reset", AuditEvent.Category.SECURITY, AuditEvent.Severity.HIGH,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "person",
				Long.toString(personId), details));
		return new Result(password, expiresAt);
	}

	/** A granted local role assignment that is not over contains an admin-class permission (REQ-031 AC9). */
	private boolean holdsAdminClassPermission(long personId, Instant now) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM role_assignments a
				               JOIN role_permissions rp ON rp.role_id = a.role_id
				               JOIN permissions p ON p.id = rp.permission_id
				               WHERE a.person_id = ? AND a.status = 'granted' AND p.admin_class
				                 AND (a.valid_to IS NULL OR a.valid_to > ?))""", Boolean.class, personId,
				Timestamp.from(now)));
	}

	/** REQ-031 AC3: every session of the person ends at once, each audited as {@code session.revoked}. */
	private int revokeSessions(OeaPrincipal principal, long personId, Instant now) {
		List<UUID> revoked = this.jdbc.queryForList("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = 'password-reset', revoked_by_person_id = ?,
				       revoked_by_admin_id = ?
				WHERE person_id = ? AND revoked_at IS NULL RETURNING id""", UUID.class, Timestamp.from(now),
				principal.personId(), principal.systemAdminAccountId(), personId);
		for (UUID session : revoked) {
			this.audit.append(new AuditEvent("session.revoked", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
					AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "session",
					session.toString(), Map.of("reason", "password-reset")));
		}
		return revoked.size();
	}

	/** An administrator is reset by someone else than the system admin account (403, REQ-173 AC3). */
	public static class AdminResetRestrictedException extends RuntimeException {

		public AdminResetRestrictedException() {
			super("Only the system admin account resets the password of an administrator.");
		}

	}

}

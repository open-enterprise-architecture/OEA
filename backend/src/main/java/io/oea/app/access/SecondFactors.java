package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.RecentAuthentication;
import io.oea.app.security.Sessions;
import io.oea.app.security.SetupPhase;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.CredentialStore;
import io.oea.core.access.PersonStore;
import io.oea.core.access.RoleStore;
import io.oea.core.access.SessionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Second factors of a principal (design D5, D6 of us-021-person-totp): the overview and removal of
 * the own factors, and the factor reset of a person by an administrator (REQ-173 AC5).
 */
@Service
public class SecondFactors {

	private final PersonStore persons;

	private final CredentialStore credentials;

	private final RoleStore roles;

	private final SessionStore sessionStore;

	private final RecentAuthentication recentAuthentication;

	private final SetupPhase setupPhase;

	private final SecondFactorPolicies policies;

	private final PermissionDenials denials;

	private final AuditWriter audit;

	private final Clock clock;

	public SecondFactors(PersonStore persons, CredentialStore credentials, RoleStore roles, SessionStore sessionStore,
			RecentAuthentication recentAuthentication, SetupPhase setupPhase, SecondFactorPolicies policies,
			PermissionDenials denials, AuditWriter audit, Clock clock) {
		this.persons = persons;
		this.credentials = credentials;
		this.roles = roles;
		this.sessionStore = sessionStore;
		this.recentAuthentication = recentAuthentication;
		this.setupPhase = setupPhase;
		this.policies = policies;
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
		return this.credentials.activeSecondFactors(principal.personId(), principal.systemAdminAccountId())
			.stream()
			.map((row) -> new Factor(row.credentialId(), row.type(), row.label(), row.createdAt(), row.lastUsedAt()))
			.toList();
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
		// design D5 of us-031-second-factor-enforcement: the enforced second factor stays
		if (principal.personId() != null && this.policies.enrollmentRequired(principal.personId(), credentialId)) {
			throw new LastSecondFactorException();
		}
		Instant now = this.clock.instant();
		this.credentials.revoke(credentialId, now, "person_request", principal.personId(),
				principal.systemAdminAccountId());
		int others = this.sessionStore.revokeOthers(principal.sessionId(), principal.personId(),
				principal.systemAdminAccountId(), now, "credential-revoked", principal.personId(),
				principal.systemAdminAccountId());
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
		PersonStore.LockedPerson locked = this.persons.lockPerson(personId)
			.orElseThrow(() -> new NoSuchElementException("no person " + personId));
		if (!"human".equals(locked.personType())) {
			throw new InvalidAccessRequestException("personId", "Service accounts have no second factors.");
		}
		if (principal.personId() != null && principal.personId() == personId) {
			this.denials.record(principal, "resetPersonSecondFactors", "person", Long.toString(personId),
					PermissionDenials.SELF_ADMINISTRATION);
			throw new SelfAdministrationException();
		}
		if (this.credentials.countActiveSecondFactors(personId) == 0) {
			throw new AccessConflictException("The person has no second factor to reset.");
		}
		Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
		if (principal.type() != OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT
				&& this.roles.holdsAdminClassPermission(personId, now)) {
			this.denials.record(principal, "resetPersonSecondFactors", "person", Long.toString(personId),
					PasswordResets.ADMIN_RESET_RESTRICTED);
			throw new PasswordResets.AdminResetRestrictedException();
		}
		// pending enrollments end as well, so that no half-finished factor survives the reset
		int revoked = this.credentials.revokeSecondFactors(personId, now, "factor_reset", principal.personId(),
				principal.systemAdminAccountId());
		List<UUID> sessions = this.sessionStore.revokeAllOfPersonReturningIds(personId, now, "factor-reset",
				principal.personId(), principal.systemAdminAccountId());
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

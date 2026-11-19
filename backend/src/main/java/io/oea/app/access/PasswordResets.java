package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.CredentialStore;
import io.oea.core.access.PersonStore;
import io.oea.core.access.RoleStore;
import io.oea.core.access.SessionStore;
import io.oea.core.service.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
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

	private final PersonStore persons;

	private final CredentialStore credentials;

	private final RoleStore roles;

	private final SessionStore sessionStore;

	private final PasswordHasher hasher;

	private final PasswordPolicies policies;

	private final PersonPasswordProperties properties;

	private final PermissionDenials denials;

	private final AuditWriter audit;

	private final Clock clock;

	public PasswordResets(PersonStore persons, CredentialStore credentials, RoleStore roles, SessionStore sessionStore,
			PasswordHasher hasher, PasswordPolicies policies, PersonPasswordProperties properties,
			PermissionDenials denials, AuditWriter audit, Clock clock) {
		this.persons = persons;
		this.credentials = credentials;
		this.roles = roles;
		this.sessionStore = sessionStore;
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
		PersonStore.LockedPerson locked = this.persons.lockPerson(personId)
			.orElseThrow(() -> new NoSuchElementException("no person " + personId));
		if (!"human".equals(locked.personType())) {
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
		List<UUID> active = this.credentials.lockActivePasswordCredentials(personId);
		if (active.isEmpty()) {
			throw new AccessConflictException("The person has no password to reset.");
		}
		Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
		if (principal.type() != OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT
				&& this.roles.holdsAdminClassPermission(personId, now)) {
			this.denials.record(principal, "resetPersonPassword", "person", Long.toString(personId),
					ADMIN_RESET_RESTRICTED);
			throw new AdminResetRestrictedException();
		}
		this.credentials.revoke(active.getFirst(), now, "admin_action", principal.personId(),
				principal.systemAdminAccountId());
		String password = this.policies.generate();
		Instant expiresAt = now.plus(this.properties.temporaryPasswordValidity());
		UUID credentialId = UUID.randomUUID();
		this.credentials.insertActivePassword(credentialId, personId, null, now);
		this.credentials.insertPasswordHash(credentialId, this.hasher.hash(password), "admin-reset", expiresAt);
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

	/** REQ-031 AC3: every session of the person ends at once, each audited as {@code session.revoked}. */
	private int revokeSessions(OeaPrincipal principal, long personId, Instant now) {
		List<UUID> revoked = this.sessionStore.revokeAllOfPersonReturningIds(personId, now, "password-reset",
				principal.personId(), principal.systemAdminAccountId());
		for (UUID session : revoked) {
			this.audit.append(new AuditEvent("session.revoked", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
					AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "session",
					session.toString(), Map.of("reason", "password-reset")));
		}
		return revoked.size();
	}

	/**
	 * The password or the second factors of an administrator are reset by someone else than the
	 * system admin account (403, REQ-173 AC3).
	 */
	public static class AdminResetRestrictedException extends RuntimeException {

		public AdminResetRestrictedException() {
			super("Only the system admin account resets the credentials of an administrator.");
		}

	}

}

package io.oea.app.login;

import io.oea.app.access.PasswordPolicies;
import io.oea.app.access.SecondFactorPolicies;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.CredentialStore;
import io.oea.core.access.SessionStore;
import io.oea.core.service.PasswordHasher;
import io.oea.core.service.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Change of the own local password (REQ-173 AC2; design D5 of us-011-local-password) for persons
 * and the system admin account: the new password is self-set without expiry, lifts the required
 * action of the session and ends every other session of the principal. The new password follows
 * the password policy including its history, and a suggestion is generated on request (design D3,
 * D5 of us-027-password-administration).
 */
@Service
public class PasswordChange {

	private final CredentialStore credentials;

	private final SessionStore sessionStore;

	private final PasswordHasher hasher;

	private final LoginThrottle throttle;

	private final PasswordPolicies policies;

	private final SecondFactorPolicies secondFactorPolicies;


	private final Sessions sessions;

	private final AuditWriter audit;

	private final Clock clock;

	public PasswordChange(CredentialStore credentials, SessionStore sessionStore, PasswordHasher hasher, LoginThrottle throttle, PasswordPolicies policies,
			SecondFactorPolicies secondFactorPolicies, Sessions sessions, AuditWriter audit, Clock clock) {
		this.credentials = credentials;
		this.sessionStore = sessionStore;
		this.hasher = hasher;
		this.throttle = throttle;
		this.policies = policies;
		this.secondFactorPolicies = secondFactorPolicies;
		this.sessions = sessions;
		this.audit = audit;
		this.clock = clock;
	}

	/** Thrown for a wrong current password or a principal without local password (403). */
	public static class WrongCurrentPasswordException extends RuntimeException {

		public WrongCurrentPasswordException() {
			super("The current password is not correct.");
		}

	}

	/** Thrown for a suggestion to a principal without local password (403). */
	public static class NoLocalPasswordException extends RuntimeException {

		public NoLocalPasswordException() {
			super("You have no local password.");
		}

	}

	/**
	 * A password that satisfies the effective policy; not stored (REQ-027).
	 * @throws NoLocalPasswordException for a principal without local password
	 */
	@Transactional(readOnly = true)
	public String suggest(OeaPrincipal principal) {
		requireLocalPassword(principal);
		return this.policies.generate();
	}

	/** The rules a new own password must meet (issue #262 API-6, design D5 of s13-issue-fixes). */
	public record Rules(int minLength, int maxLength, boolean requireUppercase, boolean requireLowercase,
			boolean requireDigits, boolean requireSpecialChars) {
	}

	/**
	 * The rules of the effective policy for the principal's own password change.
	 *
	 * @throws NoLocalPasswordException for a principal without an active local password (403)
	 */
	public Rules rules(OeaPrincipal principal) {
		requireLocalPassword(principal);
		PasswordPolicy policy = this.policies.effective();
		return new Rules(Math.max(policy.minLength(), PasswordHasher.MINIMUM_LENGTH), PasswordHasher.MAXIMUM_LENGTH,
				policy.requireUppercase(), policy.requireLowercase(), policy.requireDigits(),
				policy.requireSpecialChars());
	}

	private void requireLocalPassword(OeaPrincipal principal) {
		boolean person = principal.type() == OeaPrincipal.Type.PERSON;
		boolean exists = person ? this.credentials.hasActivePassword(principal.personId())
				: this.credentials.hasActivePasswordOfAdmin(principal.systemAdminAccountId());
		if (!exists) {
			throw new NoLocalPasswordException();
		}
	}

	@Transactional(noRollbackFor = WrongCurrentPasswordException.class)
	public void change(OeaPrincipal principal, String currentPassword, String newPassword, String clientAddress) {
		boolean person = principal.type() == OeaPrincipal.Type.PERSON;
		CredentialStore.CurrentPassword current = (person ? this.credentials.findCurrentPassword(principal.personId())
				: this.credentials.findCurrentPasswordOfAdmin(principal.systemAdminAccountId()))
			.orElseThrow(WrongCurrentPasswordException::new);
		boolean usable = currentPassword != null && currentPassword.length() <= PasswordHasher.MAXIMUM_LENGTH;
		if (!this.hasher.matches(usable ? currentPassword : "", current.hash()) || !usable) {
			// REQ-168: guessing the current password is throttled like a login
			this.throttle.recordFailure(current.loginName(), clientAddress);
			throw new WrongCurrentPasswordException();
		}
		if (newPassword != null && newPassword.equals(currentPassword)) {
			throw new InvalidAccessRequestException("newPassword", "The new password must differ from the current one.");
		}
		this.policies.check(newPassword, principal.personId(), principal.systemAdminAccountId());
		Instant now = this.clock.instant();
		this.credentials.revoke(current.credentialId(), now, "superseded", null, null);
		UUID credentialId = UUID.randomUUID();
		this.credentials.insertActivePassword(credentialId, principal.personId(), principal.systemAdminAccountId(),
				now);
		this.credentials.insertPasswordHash(credentialId, this.hasher.hash(newPassword));
		if (person && !principal.secondFactorVerified()
				&& this.secondFactorPolicies.enrollmentRequired(principal.personId())) {
			// design D3 of us-031-second-factor-enforcement: the enrollment follows the password change
			this.sessions.requireSecondFactorEnrollment(principal.sessionId());
		}
		else {
			this.sessions.clearRequiredAction(principal.sessionId());
		}
		int revoked = this.sessionStore.revokeOthers(principal.sessionId(), principal.personId(),
				principal.systemAdminAccountId(), now, "credential-revoked", principal.personId(),
				principal.systemAdminAccountId());
		this.throttle.recordSuccess(current.loginName());
		this.audit.append(new AuditEvent("auth.password-changed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"credential", credentialId.toString(), Map.of("otherSessionsRevoked", revoked)));
	}

}

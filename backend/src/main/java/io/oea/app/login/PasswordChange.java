package io.oea.app.login;

import io.oea.app.access.InvalidAccessRequestException;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.service.PasswordHasher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Change of the own local password (REQ-173 AC2; design D5 of us-011-local-password) for persons
 * and the system admin account: the new password is self-set without expiry, lifts the required
 * action of the session and ends every other session of the principal.
 */
@Service
public class PasswordChange {

	private final JdbcTemplate jdbc;

	private final PasswordHasher hasher;

	private final LoginThrottle throttle;

	private final Sessions sessions;

	private final AuditWriter audit;

	private final Clock clock;

	public PasswordChange(JdbcTemplate jdbc, PasswordHasher hasher, LoginThrottle throttle, Sessions sessions,
			AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.hasher = hasher;
		this.throttle = throttle;
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

	@Transactional(noRollbackFor = WrongCurrentPasswordException.class)
	public void change(OeaPrincipal principal, String currentPassword, String newPassword, String clientAddress) {
		boolean person = principal.type() == OeaPrincipal.Type.PERSON;
		record Current(UUID credentialId, String hash, String loginName) {
		}
		List<Current> found = this.jdbc.query(person ? """
				SELECT c.id, pc.password_hash, p.email FROM local_credentials c
				JOIN password_credentials pc ON pc.credential_id = c.id JOIN persons p ON p.id = c.person_id
				WHERE c.person_id = ? AND c.type = 'password' AND c.status = 'active'""" : """
				SELECT c.id, pc.password_hash, a.login_name FROM local_credentials c
				JOIN password_credentials pc ON pc.credential_id = c.id
				JOIN system_admin_accounts a ON a.id = c.system_admin_account_id
				WHERE c.system_admin_account_id = ? AND c.type = 'password' AND c.status = 'active'""",
				(rs, n) -> new Current(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
				person ? principal.personId() : principal.systemAdminAccountId());
		if (found.isEmpty()) {
			throw new WrongCurrentPasswordException();
		}
		Current current = found.getFirst();
		boolean usable = currentPassword != null && currentPassword.length() <= PasswordHasher.MAXIMUM_LENGTH;
		if (!this.hasher.matches(usable ? currentPassword : "", current.hash()) || !usable) {
			// REQ-168: guessing the current password is throttled like a login
			this.throttle.recordFailure(current.loginName(), clientAddress);
			throw new WrongCurrentPasswordException();
		}
		if (newPassword == null || newPassword.length() < PasswordHasher.MINIMUM_LENGTH
				|| newPassword.length() > PasswordHasher.MAXIMUM_LENGTH) {
			throw new InvalidAccessRequestException("newPassword", "The new password must have 12 to 128 characters.");
		}
		if (newPassword.equals(currentPassword)) {
			throw new InvalidAccessRequestException("newPassword", "The new password must differ from the current one.");
		}
		Instant now = this.clock.instant();
		this.jdbc.update("""
				UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = 'superseded'
				WHERE id = ?""", Timestamp.from(now), current.credentialId());
		UUID credentialId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO local_credentials (id, person_id, system_admin_account_id, type, status, created_at)
				VALUES (?, ?, ?, 'password', 'active', ?)""", credentialId, principal.personId(),
				principal.systemAdminAccountId(), Timestamp.from(now));
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credentialId,
				this.hasher.hash(newPassword));
		this.sessions.clearRequiredAction(principal.sessionId());
		int revoked = this.jdbc.update("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = 'credential-revoked', revoked_by_person_id = ?,
				       revoked_by_admin_id = ?
				WHERE id <> ? AND revoked_at IS NULL AND (person_id = ? OR system_admin_account_id = ?)""",
				Timestamp.from(now), principal.personId(), principal.systemAdminAccountId(), principal.sessionId(),
				principal.personId(), principal.systemAdminAccountId());
		this.throttle.recordSuccess(current.loginName());
		this.audit.append(new AuditEvent("auth.password-changed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"credential", credentialId.toString(), Map.of("otherSessionsRevoked", revoked)));
	}

}

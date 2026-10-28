package io.oea.app.recovery;

import io.oea.app.login.LoginThrottle;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.config.secrets.SecretCipher;
import io.oea.core.domain.auth.TotpCredential;
import io.oea.core.service.PasswordHasher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Restores the local system admin account in one transaction (design D5, D6 of
 * us-019-admin-safeguards): reactivation, new password, optionally a new TOTP factor, revoked
 * sessions, cleared login throttling and the audit event {@code system.admin-recovered}. The
 * inputs are validated by the caller before; nothing is written if a step fails.
 */
public class AdminRecovery {

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	private final SecretCipher cipher;

	private final PasswordHasher hasher;

	private final AuditWriter audit;

	private final Clock clock;

	/** The local account to recover. */
	public record Account(UUID id, String loginName, String status) {
	}

	/** A confirmed new TOTP factor: its secret and the time step of the accepted code. */
	public record NewFactor(String secret, long acceptedStep) {
	}

	public AdminRecovery(JdbcTemplate jdbc, TransactionTemplate transaction, SecretCipher cipher,
			PasswordHasher hasher, AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.transaction = transaction;
		this.cipher = cipher;
		this.hasher = hasher;
		this.audit = audit;
		this.clock = clock;
	}

	/** The account of the bootstrapping; empty on an instance that is not bootstrapped. */
	public Optional<Account> account() {
		return this.jdbc.query(
				"SELECT id, login_name, status FROM system_admin_accounts ORDER BY created_at, id LIMIT 1",
				(rs) -> rs.next() ? Optional.of(new Account(rs.getObject(1, UUID.class), rs.getString(2),
						rs.getString(3))) : Optional.<Account>empty());
	}

	/** After the setup phase every login needs TOTP, so the recovery must replace the factor (ADR-042). */
	public boolean setupPhaseEnded() {
		return Boolean.TRUE.equals(this.jdbc.query(
				"SELECT setup_phase_ended_at IS NOT NULL FROM system_settings WHERE id = 1",
				(rs) -> rs.next() && rs.getBoolean(1)));
	}

	/**
	 * Applies the recovery.
	 * @param factor the new second factor; required after the setup phase, {@code null} during it
	 * @return the number of revoked sessions
	 */
	public int recover(Account account, String password, NewFactor factor) {
		Integer revoked = this.transaction.execute((status) -> recoverInTransaction(account, password, factor));
		return (revoked != null) ? revoked : 0;
	}

	private int recoverInTransaction(Account account, String password, NewFactor factor) {
		Instant now = this.clock.instant();
		Timestamp at = Timestamp.from(now);
		UUID id = account.id();
		this.jdbc.queryForObject("SELECT id FROM system_admin_accounts WHERE id = ? FOR UPDATE", UUID.class, id);
		boolean reactivated = this.jdbc.update("""
				UPDATE system_admin_accounts
				SET status = CASE WHEN first_login_at IS NULL THEN 'bootstrapped' ELSE 'active' END,
				    deactivated_at = NULL, deactivated_by_admin_id = NULL, deactivated_by_person_id = NULL
				WHERE id = ? AND status = 'deactivated'""", id) == 1;

		this.jdbc.update("""
				UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = 'admin_action'
				WHERE system_admin_account_id = ? AND type = 'password' AND status = 'active'""", at, id);
		UUID passwordId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO local_credentials (id, system_admin_account_id, type, status, created_at)
				VALUES (?, ?, 'password', 'active', ?)""", passwordId, id, at);
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", passwordId,
				this.hasher.hash(password));

		if (factor != null) {
			this.jdbc.update("""
					UPDATE local_credentials SET status = 'revoked', revoked_at = ?, revoked_reason = 'factor_reset'
					WHERE system_admin_account_id = ? AND type = 'totp' AND status IN ('active', 'pending')""", at, id);
			UUID totpId = UUID.randomUUID();
			this.jdbc.update("""
					INSERT INTO local_credentials (id, system_admin_account_id, type, status, created_at, last_used_at)
					VALUES (?, ?, 'totp', 'active', ?, ?)""", totpId, id, at, at);
			SecretCipher.Encrypted secret = this.cipher.encrypt(factor.secret(),
					TotpCredential.encryptionContext(totpId));
			this.jdbc.update("""
					INSERT INTO totp_credentials (credential_id, secret_encrypted, key_id, last_used_time_step, label)
					VALUES (?, ?, ?, ?, 'break-glass recovery')""", totpId, secret.ciphertext(), secret.keyId(),
					factor.acceptedStep());
		}

		int revoked = this.jdbc.update("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = 'credential-revoked'
				WHERE system_admin_account_id = ? AND revoked_at IS NULL""", at, id);
		this.jdbc.update("DELETE FROM login_transactions WHERE system_admin_account_id = ?", id);
		this.jdbc.update("DELETE FROM auth_throttle WHERE throttle_key = ?", LoginThrottle.accountKey(account.loginName()));

		Map<String, Object> details = new LinkedHashMap<>();
		details.put("reactivated", reactivated);
		details.put("secondFactorReplaced", factor != null);
		details.put("sessionsRevoked", revoked);
		this.audit.append(new AuditEvent("system.admin-recovered", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.CLI, AuditEvent.Actor.system(),
				"system-admin-account", id.toString(), details));
		return revoked;
	}

}

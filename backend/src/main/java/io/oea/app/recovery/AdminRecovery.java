package io.oea.app.recovery;

import io.oea.app.login.LoginThrottle;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.CredentialStore;
import io.oea.core.access.IdentitySettingsStore;
import io.oea.core.access.LoginStateStore;
import io.oea.core.access.LoginThrottleStore;
import io.oea.core.access.SessionStore;
import io.oea.core.access.SystemAdminAccountStore;
import io.oea.core.domain.auth.TotpCredential;
import io.oea.core.service.PasswordHasher;
import io.oea.infrastructure.secrets.SecretCipher;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Restores the local system admin account in one transaction (design D5, D6 of
 * us-019-admin-safeguards): reactivation, new password, optionally a new TOTP factor, revoked
 * sessions, cleared login throttling and the audit event {@code system.admin-recovered}. The
 * inputs are validated by the caller before; nothing is written if a step fails.
 */
public class AdminRecovery {

	private final SystemAdminAccountStore accounts;

	private final CredentialStore credentials;

	private final SessionStore sessions;

	private final LoginStateStore loginStates;

	private final LoginThrottleStore throttle;

	private final IdentitySettingsStore settings;

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

	public AdminRecovery(SystemAdminAccountStore accounts, CredentialStore credentials, SessionStore sessions,
			LoginStateStore loginStates, LoginThrottleStore throttle, IdentitySettingsStore settings,
			TransactionTemplate transaction, SecretCipher cipher, PasswordHasher hasher, AuditWriter audit,
			Clock clock) {
		this.accounts = accounts;
		this.credentials = credentials;
		this.sessions = sessions;
		this.loginStates = loginStates;
		this.throttle = throttle;
		this.settings = settings;
		this.transaction = transaction;
		this.cipher = cipher;
		this.hasher = hasher;
		this.audit = audit;
		this.clock = clock;
	}

	/** The account of the bootstrapping; empty on an instance that is not bootstrapped. */
	public Optional<Account> account() {
		return this.accounts.firstAccount().map((a) -> new Account(a.id(), a.loginName(), a.status()));
	}

	/** After the setup phase every login needs TOTP, so the recovery must replace the factor (ADR-042). */
	public boolean setupPhaseEnded() {
		return this.settings.setupPhaseEnded();
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
		UUID id = account.id();
		this.accounts.lockAccount(id);
		boolean reactivated = this.accounts.reactivate(id);

		this.credentials.revokeActivePasswordOfAdmin(id, now);
		UUID passwordId = UUID.randomUUID();
		this.credentials.insertActivePassword(passwordId, null, id, now);
		this.credentials.insertPasswordHash(passwordId, this.hasher.hash(password));

		if (factor != null) {
			this.credentials.revokeTotpOfAdmin(id, now);
			UUID totpId = UUID.randomUUID();
			this.credentials.insertActiveTotpOfAdmin(totpId, id, now);
			SecretCipher.Encrypted secret = this.cipher.encrypt(factor.secret(),
					TotpCredential.encryptionContext(totpId));
			this.credentials.insertRecoveredTotp(totpId, secret.ciphertext(), secret.keyId(), factor.acceptedStep());
		}

		int revoked = this.sessions.revokeAllOfAdmin(id, now, "credential-revoked", null, null);
		this.loginStates.deleteTransactionsOfAdmin(id);
		this.throttle.delete(LoginThrottle.accountKey(account.loginName()));

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

package io.oea.app.login;

import io.oea.app.access.PasswordPolicies;
import io.oea.app.security.Sessions;
import io.oea.app.security.SetupPhase;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.CredentialStore;
import io.oea.core.access.LoginStateStore;
import io.oea.core.domain.auth.LocalCredential;
import io.oea.core.domain.auth.PasswordCredential;
import io.oea.core.domain.auth.SystemAdminAccount;
import io.oea.core.domain.auth.TotpCredential;
import io.oea.core.repository.LocalCredentialRepository;
import io.oea.core.repository.PasswordCredentialRepository;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.repository.TotpCredentialRepository;
import io.oea.core.service.PasswordHasher;
import io.oea.core.service.TotpAlgorithm;
import io.oea.infrastructure.secrets.SecretCipher;
import io.oea.infrastructure.webauthn.PasskeyRejectedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local login of the system admin account (design D5): password, and after the setup phase
 * (ADR-042) a TOTP step through a login transaction. Persons log in with their e-mail address
 * and password while password login of persons is possible (us-011-local-password); an expired
 * password leads to the password change (design D4 of us-027-password-administration). A person
 * with an active TOTP credential completes the login with a code (design D4 of us-021-person-totp).
 */
@Service
public class LocalLogin {

	static final Duration TRANSACTION_VALIDITY = Duration.ofMinutes(5);

	static final int MAXIMUM_WRONG_CODES = 5;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SystemAdminAccountRepository accounts;

	private final PasswordCredentialRepository passwords;

	private final LocalCredentialRepository credentials;

	private final TotpCredentialRepository totps;

	private final PasswordHasher hasher;

	private final SecretCipher cipher;

	private final LoginThrottle throttle;

	private final SetupPhase setupPhase;

	private final Sessions sessions;

	private final AuditWriter audit;

	private final CredentialStore credentialStore;

	private final LoginStateStore loginStates;

	private final Clock clock;

	private final String dummyHash;

	private final PasswordPolicies policies;

	private final PersonSessions personSessions;

	private final Passkeys passkeys;

	private final LoginMetrics metrics;

	public LocalLogin(SystemAdminAccountRepository accounts, PasswordCredentialRepository passwords,
			LocalCredentialRepository credentials, TotpCredentialRepository totps, PasswordHasher hasher,
			SecretCipher cipher, LoginThrottle throttle, SetupPhase setupPhase, Sessions sessions, AuditWriter audit,
			CredentialStore credentialStore, LoginStateStore loginStates, Clock clock, PasswordPolicies policies, PersonSessions personSessions,
			Passkeys passkeys, LoginMetrics metrics) {
		this.accounts = accounts;
		this.passwords = passwords;
		this.credentials = credentials;
		this.totps = totps;
		this.hasher = hasher;
		this.cipher = cipher;
		this.throttle = throttle;
		this.setupPhase = setupPhase;
		this.sessions = sessions;
		this.audit = audit;
		this.credentialStore = credentialStore;
		this.loginStates = loginStates;
		this.clock = clock;
		this.dummyHash = hasher.hash("timing-equalizer-" + UUID.randomUUID());
		this.policies = policies;
		this.personSessions = personSessions;
		this.passkeys = passkeys;
		this.metrics = metrics;
	}

	/** Client data of the request, for the session and the throttling. */
	public record Client(String address, String userAgent) {
	}

	/**
	 * Password step.
	 * @return the new session during the setup phase
	 * @throws SecondFactorRequiredException after the setup phase (with a login transaction)
	 * @throws InvalidCredentialsException for any wrong or throttled attempt
	 */
	@Transactional(noRollbackFor = SecondFactorRequiredException.class)
	public Sessions.NewSession login(String loginName, String password, Client client) {
		return this.metrics.time("password", () -> passwordStep(loginName, password, client));
	}

	private Sessions.NewSession passwordStep(String loginName, String password, Client client) {
		long wait = this.throttle.retryAfterSeconds(loginName, client.address());
		if (wait > 0) {
			throw new InvalidCredentialsException(wait);
		}
		if (loginName != null && loginName.contains("@")) {
			// system admin login names cannot contain "@": the name is a person's e-mail address (D2)
			return loginPerson(loginName, password, client);
		}
		Optional<SystemAdminAccount> account = (loginName != null) ? this.accounts.findByLoginName(loginName)
				: Optional.empty();
		Optional<PasswordCredential> credential = account.filter(SystemAdminAccount::canLogIn)
			.flatMap((a) -> this.passwords.findActiveForSystemAdmin(a.getId()));
		String hash = credential.map(PasswordCredential::getPasswordHash).orElse(this.dummyHash);
		boolean valid = verify(password, hash) && credential.isPresent();
		if (!valid) {
			fail(loginName, client);
		}
		this.throttle.recordSuccess(loginName);
		if (this.hasher.needsRehash(hash)) {
			credential.get().rehash(this.hasher.hash(password));
		}
		SystemAdminAccount admin = account.get();
		if (!this.setupPhase.isActive()) {
			throw new SecondFactorRequiredException(newTransaction(admin.getId(), null), List.of("totp"));
		}
		return startSession(admin, false, client);
	}

	/**
	 * Password login of a person (US-011; design D2, D4, D6 of us-011-local-password): only while
	 * password login of persons is possible, for persons in status {@code invited} or {@code active}
	 * with an unexpired temporary password. Every failing variant verifies exactly one hash.
	 */
	private Sessions.NewSession loginPerson(String loginName, String password, Client client) {
		Instant now = this.clock.instant();
		boolean possible = this.policies.personPasswordLoginPossible();
		List<CredentialStore.PersonLoginCandidate> found = this.credentialStore
			.findPersonLoginCandidates(loginName.strip());
		Optional<CredentialStore.PersonLoginCandidate> candidate = found.stream()
			.filter((c) -> possible)
			.filter((c) -> "human".equals(c.personType()))
			.filter((c) -> "invited".equals(c.status()) || "active".equals(c.status()))
			.filter((c) -> c.hash() != null)
			.filter((c) -> c.expiresAt() == null || c.expiresAt().isAfter(now))
			.findFirst();
		String hash = candidate.map(CredentialStore.PersonLoginCandidate::hash).orElse(this.dummyHash);
		if (!verify(password, hash) || candidate.isEmpty()) {
			fail(loginName, client);
		}
		this.throttle.recordSuccess(loginName);
		CredentialStore.PersonLoginCandidate person = candidate.get();
		if (this.hasher.needsRehash(hash)) {
			this.credentialStore.updatePasswordHash(person.credentialId(), this.hasher.hash(password));
		}
		this.credentialStore.markUsed(person.credentialId(), now);
		List<String> methods = new ArrayList<>();
		if (!this.credentials.findByPersonIdAndTypeAndStatus(person.personId(), "totp", "active").isEmpty()) {
			methods.add("totp");
		}
		if (this.passkeys.available() && this.passkeys.hasActivePasskey(person.personId())) {
			methods.add("passkey");
		}
		if (!methods.isEmpty()) {
			// REQ-010, design D5 of us-022-passkeys: the second step follows; the person is activated only after it
			throw new SecondFactorRequiredException(newTransaction(null, person.personId()), methods);
		}
		return this.personSessions.start(person.personId(), "password", client);
	}

	/** One hash verification for every attempt, also for overlong or missing passwords (D6). */
	private boolean verify(String password, String hash) {
		boolean usable = password != null && password.length() <= PasswordHasher.MAXIMUM_LENGTH;
		return this.hasher.matches(usable ? password : "", hash) && usable;
	}

	/**
	 * TOTP step after the setup phase (REQ-171, REQ-168 AC2, REQ-170), and of persons with an
	 * active TOTP credential (REQ-010; design D4 of us-021-person-totp).
	 */
	@Transactional(noRollbackFor = InvalidCredentialsException.class)
	public Sessions.NewSession loginWithTotp(String loginTransaction, String code, Client client) {
		return this.metrics.time("password-totp", () -> totpStep(loginTransaction, code, client));
	}

	private Sessions.NewSession totpStep(String loginTransaction, String code, Client client) {
		Instant now = this.clock.instant();
		byte[] hash = sha256(loginTransaction);
		Optional<LoginStateStore.Owner> owner = this.loginStates.lockOpenTransaction(hash, now, MAXIMUM_WRONG_CODES);
		if (owner.isEmpty()) {
			throw new InvalidCredentialsException(0);
		}
		if (!acceptCode(owner.get(), code, now)) {
			wrongSecondFactor(hash);
		}
		this.loginStates.deleteTransaction(hash);
		if (owner.get().personId() != null) {
			return this.personSessions.start(owner.get().personId(), "password-totp", client);
		}
		return startSession(this.accounts.findById(owner.get().systemAdminAccountId()).orElseThrow(), true, client);
	}

	/**
	 * Request options for the passkey step of a person's login transaction (design D5 of
	 * us-022-passkeys).
	 * @throws InvalidCredentialsException for an unknown, expired or invalidated transaction, or a
	 * person without passkey
	 */
	@Transactional
	public PasskeyLogin.Options passkeyOptions(String loginTransaction) {
		Instant now = this.clock.instant();
		List<UUID> transactions = this.loginStates.findOpenPersonTransactionIds(sha256(loginTransaction), now,
				MAXIMUM_WRONG_CODES);
		Long personId = transactions.isEmpty() ? null : this.loginStates.personOfTransaction(transactions.getFirst());
		if (personId == null || !this.passkeys.available() || !this.passkeys.hasActivePasskey(personId)) {
			throw new InvalidCredentialsException(0);
		}
		Passkeys.Challenge challenge = this.passkeys.newChallenge("second-factor", personId, transactions.getFirst(),
				null, Passkeys.LOGIN_VALIDITY);
		return new PasskeyLogin.Options(challenge.id(),
				this.passkeys.requestOptions(challenge, this.passkeys.credentialIds(personId)));
	}

	/**
	 * Passkey step of a person's login transaction (design D5 of us-022-passkeys): a failure counts
	 * like a wrong code.
	 * @throws InvalidCredentialsException for every failure
	 */
	@Transactional(noRollbackFor = InvalidCredentialsException.class)
	public Sessions.NewSession loginWithPasskey(String loginTransaction, UUID challengeId, String assertionJson,
			Client client) {
		return this.metrics.time("password-passkey",
				() -> passkeyStep(loginTransaction, challengeId, assertionJson, client));
	}

	private Sessions.NewSession passkeyStep(String loginTransaction, UUID challengeId, String assertionJson,
			Client client) {
		Instant now = this.clock.instant();
		byte[] hash = sha256(loginTransaction);
		Optional<LoginStateStore.PersonTransaction> transaction = this.loginStates.lockOpenPersonTransaction(hash,
				now, MAXIMUM_WRONG_CODES);
		if (transaction.isEmpty()) {
			throw new InvalidCredentialsException(0);
		}
		Optional<Passkeys.Challenge> challenge = this.passkeys.consume(challengeId, "second-factor")
			.filter((c) -> transaction.get().id().equals(c.loginTransactionId()));
		try {
			if (challenge.isEmpty()) {
				throw new PasskeyRejectedException("unknown or expired challenge");
			}
			this.passkeys.verify(assertionJson, challenge.get(), transaction.get().personId());
		}
		catch (PasskeyRejectedException ex) {
			wrongSecondFactor(hash);
		}
		this.loginStates.deleteTransaction(hash);
		return this.personSessions.start(transaction.get().personId(), "password-passkey", client);
	}

	/** A wrong code or passkey: counted on the transaction, five invalidate it (REQ-168 AC2). */
	private void wrongSecondFactor(byte[] hash) {
		int failed = this.loginStates.incrementFailedCodes(hash);
		if (failed >= MAXIMUM_WRONG_CODES) {
			this.loginStates.deleteTransaction(hash);
		}
		this.audit.appendIndependently(failedEvent("wrong-second-factor"));
		throw new InvalidCredentialsException(0);
	}

	/** Verifies a code against the active TOTP credentials of the owner and records its time step (REQ-030). */
	private boolean acceptCode(LoginStateStore.Owner owner, String code, Instant now) {
		List<LocalCredential> active = (owner.personId() != null)
				? this.credentials.findByPersonIdAndTypeAndStatus(owner.personId(), "totp", "active")
				: this.credentials.findBySystemAdminAccountIdAndTypeAndStatus(owner.systemAdminAccountId(), "totp",
						"active");
		for (LocalCredential credential : active) {
			Optional<TotpCredential> totp = this.totps.findById(credential.getId());
			if (totp.isEmpty()) {
				continue;
			}
			String secret = this.cipher.decrypt(
					new SecretCipher.Encrypted(totp.get().getSecretEncrypted(), totp.get().getKeyId()),
					TotpCredential.encryptionContext(credential.getId()));
			OptionalLong step = TotpAlgorithm.matchingStep(secret, code, now);
			if (step.isPresent() && totp.get().acceptTimeStep(step.getAsLong())) {
				credential.markUsed(now);
				return true;
			}
		}
		return false;
	}

	private Sessions.NewSession startSession(SystemAdminAccount admin, boolean secondFactor, Client client) {
		admin.recordLogin(this.clock.instant());
		Sessions.NewSession session = this.sessions.createForSystemAdmin(admin, secondFactor, client.address(),
				client.userAgent());
		this.audit.append(new AuditEvent("auth.login", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, AuditEvent.Actor.systemAdminAccount(admin.getId()),
				"session", session.principal().sessionId().toString(),
				Map.of("method", secondFactor ? "password-totp" : "password", "setupPhase", !secondFactor)));
		return session;
	}

	private void fail(String loginName, Client client) {
		this.throttle.recordFailure(loginName, client.address());
		this.audit.appendIndependently(failedEvent("invalid-credentials"));
		throw new InvalidCredentialsException(0);
	}

	private static AuditEvent failedEvent(String reason) {
		// pseudonymous: the attempted login name is not recorded (ADR-033 §3)
		return new AuditEvent("auth.login-failed", AuditEvent.Category.SECURITY, AuditEvent.Severity.WARNING,
				AuditEvent.Outcome.FAILURE, AuditEvent.Source.API, AuditEvent.Actor.unauthenticated(), null, null,
				Map.of("reason", reason));
	}

	private String newTransaction(UUID systemAdminAccountId, Long personId) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant now = this.clock.instant();
		this.loginStates.insertTransaction(sha256(token), systemAdminAccountId, personId, now,
				now.plus(TRANSACTION_VALIDITY));
		return token;
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(((value != null) ? value : "").getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

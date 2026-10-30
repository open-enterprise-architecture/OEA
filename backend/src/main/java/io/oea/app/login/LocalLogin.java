package io.oea.app.login;

import io.oea.app.access.PasswordPolicies;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.app.security.SetupPhase;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.config.secrets.SecretCipher;
import io.oea.core.domain.auth.LocalCredential;
import io.oea.core.domain.auth.PasswordCredential;
import io.oea.core.domain.auth.SystemAdminAccount;
import io.oea.core.domain.auth.TotpCredential;
import io.oea.core.domain.person.Person;
import io.oea.core.repository.LocalCredentialRepository;
import io.oea.core.repository.PasswordCredentialRepository;
import io.oea.core.repository.PersonRepository;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.repository.TotpCredentialRepository;
import io.oea.core.service.PasswordHasher;
import io.oea.core.service.PasswordPolicy;
import io.oea.core.service.TotpAlgorithm;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
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

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final String dummyHash;

	private final PasswordPolicies policies;

	private final PersonRepository persons;

	public LocalLogin(SystemAdminAccountRepository accounts, PasswordCredentialRepository passwords,
			LocalCredentialRepository credentials, TotpCredentialRepository totps, PasswordHasher hasher,
			SecretCipher cipher, LoginThrottle throttle, SetupPhase setupPhase, Sessions sessions, AuditWriter audit,
			JdbcTemplate jdbc, Clock clock, PasswordPolicies policies, PersonRepository persons) {
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
		this.jdbc = jdbc;
		this.clock = clock;
		this.dummyHash = hasher.hash("timing-equalizer-" + UUID.randomUUID());
		this.policies = policies;
		this.persons = persons;
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
			throw new SecondFactorRequiredException(newTransaction(admin.getId(), null));
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
		record Candidate(long personId, String status, String personType, UUID credentialId, String hash, String origin,
				Instant expiresAt, Instant setAt) {
		}
		boolean possible = this.policies.personPasswordLoginPossible();
		List<Candidate> found = this.jdbc.query("""
				SELECT p.id, p.status, p.person_type, c.id, pc.password_hash, pc.origin, pc.expires_at, c.created_at
				FROM persons p
				LEFT JOIN local_credentials c ON c.person_id = p.id AND c.type = 'password' AND c.status = 'active'
				LEFT JOIN password_credentials pc ON pc.credential_id = c.id
				WHERE lower(p.email) = lower(?)""", (rs, n) -> {
			Timestamp expires = rs.getTimestamp(7);
			Timestamp set = rs.getTimestamp(8);
			return new Candidate(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class),
					rs.getString(5), rs.getString(6), (expires != null) ? expires.toInstant() : null,
					(set != null) ? set.toInstant() : null);
		}, loginName.strip());
		Optional<Candidate> candidate = found.stream()
			.filter((c) -> possible)
			.filter((c) -> "human".equals(c.personType()))
			.filter((c) -> "invited".equals(c.status()) || "active".equals(c.status()))
			.filter((c) -> c.hash() != null)
			.filter((c) -> c.expiresAt() == null || c.expiresAt().isAfter(now))
			.findFirst();
		String hash = candidate.map(Candidate::hash).orElse(this.dummyHash);
		if (!verify(password, hash) || candidate.isEmpty()) {
			fail(loginName, client);
		}
		this.throttle.recordSuccess(loginName);
		Candidate person = candidate.get();
		if (this.hasher.needsRehash(hash)) {
			this.jdbc.update("UPDATE password_credentials SET password_hash = ? WHERE credential_id = ?",
					this.hasher.hash(password), person.credentialId());
		}
		this.jdbc.update("UPDATE local_credentials SET last_used_at = ? WHERE id = ?", Timestamp.from(now),
				person.credentialId());
		if (!this.credentials.findByPersonIdAndTypeAndStatus(person.personId(), "totp", "active").isEmpty()) {
			// REQ-010: the TOTP step follows; the person is activated only after it
			throw new SecondFactorRequiredException(newTransaction(null, person.personId()));
		}
		return startPersonSession(person.personId(), person.origin(), person.setAt(), false, client);
	}

	/**
	 * Session of a person after the password step, or after the TOTP step with {@code secondFactor}:
	 * activation at the first login and the required password change.
	 */
	private Sessions.NewSession startPersonSession(long personId, String origin, Instant setAt, boolean secondFactor,
			Client client) {
		Instant now = this.clock.instant();
		Person entity = this.persons.findById(personId).orElseThrow();
		if (entity.activateOnFirstLogin(now)) {
			this.audit.append(new AuditEvent("person.activated", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
					AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API,
					new AuditEvent.Actor("person", personId, null), "person", Long.toString(personId),
					Map.of("trigger", "first-login")));
		}
		// REQ-173 AC2, REQ-028 AC2: a temporary or expired password only leads to the password change
		PasswordPolicy policy = this.policies.effective();
		boolean expired = policy.expiryDays() > 0 && !setAt.plus(Duration.ofDays(policy.expiryDays())).isAfter(now);
		String requiredAction = ("self-set".equals(origin) && !expired) ? null : OeaPrincipal.CHANGE_PASSWORD;
		Sessions.NewSession session = this.sessions.createForPersonPassword(entity, requiredAction, secondFactor,
				client.address(), client.userAgent());
		this.audit.append(new AuditEvent("auth.login", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, new AuditEvent.Actor("person", personId, null),
				"session", session.principal().sessionId().toString(),
				Map.of("method", secondFactor ? "password-totp" : "password", "secondFactor", secondFactor,
						"temporaryPassword", !"self-set".equals(origin), "passwordExpired", expired)));
		return session;
	}

	/** One hash verification for every attempt, also for overlong or missing passwords (D6). */
	private boolean verify(String password, String hash) {
		boolean usable = password != null && password.length() <= PasswordHasher.MAXIMUM_LENGTH;
		return this.hasher.matches(usable ? password : "", hash) && usable;
	}

	/** Owner of a login transaction: the system admin account or a person. */
	private record Owner(UUID systemAdminAccountId, Long personId) {
	}

	/**
	 * TOTP step after the setup phase (REQ-171, REQ-168 AC2, REQ-170), and of persons with an
	 * active TOTP credential (REQ-010; design D4 of us-021-person-totp).
	 */
	@Transactional(noRollbackFor = InvalidCredentialsException.class)
	public Sessions.NewSession loginWithTotp(String loginTransaction, String code, Client client) {
		Instant now = this.clock.instant();
		byte[] hash = sha256(loginTransaction);
		Optional<Owner> owner = this.jdbc.query("""
				SELECT system_admin_account_id, person_id FROM login_transactions
				WHERE token_hash = ? AND expires_at > ? AND failed_codes < ? FOR UPDATE""",
				(rs) -> rs.next() ? Optional.of(new Owner(rs.getObject(1, UUID.class), rs.getObject(2, Long.class)))
						: Optional.<Owner>empty(),
				hash, Timestamp.from(now), MAXIMUM_WRONG_CODES);
		if (owner.isEmpty()) {
			throw new InvalidCredentialsException(0);
		}
		if (!acceptCode(owner.get(), code, now)) {
			int failed = this.jdbc.queryForObject("""
					UPDATE login_transactions SET failed_codes = failed_codes + 1 WHERE token_hash = ?
					RETURNING failed_codes""", Integer.class, hash);
			if (failed >= MAXIMUM_WRONG_CODES) {
				this.jdbc.update("DELETE FROM login_transactions WHERE token_hash = ?", (Object) hash);
			}
			this.audit.appendIndependently(failedEvent("wrong-second-factor"));
			throw new InvalidCredentialsException(0);
		}
		this.jdbc.update("DELETE FROM login_transactions WHERE token_hash = ?", (Object) hash);
		if (owner.get().personId() != null) {
			record Password(String origin, Instant setAt) {
			}
			Password password = this.jdbc.queryForObject("""
					SELECT pc.origin, c.created_at FROM local_credentials c
					JOIN password_credentials pc ON pc.credential_id = c.id
					WHERE c.person_id = ? AND c.type = 'password' AND c.status = 'active'""",
					(rs, n) -> new Password(rs.getString(1), rs.getTimestamp(2).toInstant()), owner.get().personId());
			return startPersonSession(owner.get().personId(), password.origin(), password.setAt(), true, client);
		}
		return startSession(this.accounts.findById(owner.get().systemAdminAccountId()).orElseThrow(), true, client);
	}

	/** Verifies a code against the active TOTP credentials of the owner and records its time step (REQ-030). */
	private boolean acceptCode(Owner owner, String code, Instant now) {
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
		this.jdbc.update("""
				INSERT INTO login_transactions (token_hash, system_admin_account_id, person_id, created_at, expires_at)
				VALUES (?, ?, ?, ?, ?)""", sha256(token), systemAdminAccountId, personId, Timestamp.from(now),
				Timestamp.from(now.plus(TRANSACTION_VALIDITY)));
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

package io.oea.app.login;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.RecentAuthentication;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.config.secrets.SecretCipher;
import io.oea.core.domain.auth.LocalCredential;
import io.oea.core.domain.auth.TotpCredential;
import io.oea.core.repository.LocalCredentialRepository;
import io.oea.core.repository.PersonRepository;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.repository.TotpCredentialRepository;
import io.oea.core.service.TotpAlgorithm;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOTP enrollment in the running session (design D7, REQ-171, REQ-172, REQ-188) for the system
 * admin account and for persons with a local password (design D1 to D3 of us-021-person-totp,
 * REQ-022, REQ-030).
 */
@Service
public class TotpEnrollment {

	static final Duration PENDING_VALIDITY = Duration.ofMinutes(10);

	private final LocalCredentialRepository credentials;

	private final TotpCredentialRepository totps;

	private final SystemAdminAccountRepository accounts;

	private final PersonRepository persons;

	private final JdbcTemplate jdbc;

	private final SecretCipher cipher;

	private final Sessions sessions;

	private final AuditWriter audit;

	private final RecentAuthentication recentAuthentication;

	private final Clock clock;

	public TotpEnrollment(LocalCredentialRepository credentials, TotpCredentialRepository totps,
			SystemAdminAccountRepository accounts, PersonRepository persons, JdbcTemplate jdbc, SecretCipher cipher,
			Sessions sessions, AuditWriter audit, RecentAuthentication recentAuthentication, Clock clock) {
		this.credentials = credentials;
		this.totps = totps;
		this.accounts = accounts;
		this.persons = persons;
		this.jdbc = jdbc;
		this.cipher = cipher;
		this.sessions = sessions;
		this.audit = audit;
		this.recentAuthentication = recentAuthentication;
		this.clock = clock;
	}

	/** Started enrollment; the secret is shown only now. */
	public record Started(UUID credentialId, String secret, String otpauthUri, Instant expiresAt) {
	}

	/** Confirmed second factor. */
	public record Confirmed(UUID credentialId, String label, Instant createdAt) {
	}

	/**
	 * Starts an enrollment.
	 * @throws PasswordChange.NoLocalPasswordException for a person without local password
	 */
	@Transactional
	public Started start(OeaPrincipal principal, String label) {
		String account = accountName(principal);
		this.recentAuthentication.require(principal);
		Instant now = this.clock.instant();
		String secret = TotpAlgorithm.newSecret();
		LocalCredential credential = this.credentials
			.save(LocalCredential.pendingTotp(principal.systemAdminAccountId(), principal.personId(), now));
		SecretCipher.Encrypted encrypted = this.cipher.encrypt(secret, TotpCredential.encryptionContext(credential.getId()));
		this.totps.save(new TotpCredential(credential.getId(), encrypted.ciphertext(), encrypted.keyId(),
				(label != null && !label.isBlank()) ? label.strip() : defaultLabel(now)));
		String uri = "otpauth://totp/" + encode("OEA:" + account) + "?secret=" + secret + "&issuer=OEA&algorithm=SHA1"
				+ "&digits=6&period=" + TotpAlgorithm.PERIOD_SECONDS;
		return new Started(credential.getId(), secret, uri, now.plus(PENDING_VALIDITY));
	}

	@Transactional(noRollbackFor = { InvalidTotpCodeException.class, NoSuchElementException.class })
	public Confirmed confirm(OeaPrincipal principal, UUID credentialId, String code) {
		Instant now = this.clock.instant();
		LocalCredential credential = this.credentials.findById(credentialId)
			.filter((c) -> owns(principal, c))
			.filter((c) -> c.isPendingSince(now, PENDING_VALIDITY))
			.orElse(null);
		if (credential == null) {
			failed(principal, credentialId, "enrollment-expired");
			throw new NoSuchElementException("no pending enrollment " + credentialId);
		}
		TotpCredential totp = this.totps.findById(credentialId).orElseThrow();
		String secret = this.cipher.decrypt(new SecretCipher.Encrypted(totp.getSecretEncrypted(), totp.getKeyId()),
				TotpCredential.encryptionContext(credentialId));
		OptionalLong step = TotpAlgorithm.matchingStep(secret, code, now);
		if (step.isEmpty() || !totp.acceptTimeStep(step.getAsLong())) {
			// REQ-022 AC3: the enrollment stays pending and can be confirmed again
			failed(principal, credentialId, "invalid-code");
			throw new InvalidTotpCodeException();
		}
		credential.activate();
		credential.markUsed(now);
		this.sessions.confirmSecondFactor(principal.sessionId());
		Map<String, Object> details = new HashMap<>();
		details.put("type", "totp");
		if (totp.getLabel() != null) {
			details.put("label", totp.getLabel());
		}
		this.audit.append(new AuditEvent("auth.factor-added", AuditEvent.Category.SECURITY, AuditEvent.Severity.HIGH,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "credential",
				credentialId.toString(), details));
		return new Confirmed(credentialId, totp.getLabel(), now);
	}

	/** The account name of the key URI; a person needs a local password (D1). */
	private String accountName(OeaPrincipal principal) {
		if (principal.type() == OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT) {
			return this.accounts.findById(principal.systemAdminAccountId()).orElseThrow().getLoginName();
		}
		Boolean password = this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM local_credentials WHERE person_id = ? AND type = 'password'
				               AND status = 'active')""", Boolean.class, principal.personId());
		if (!Boolean.TRUE.equals(password)) {
			throw new PasswordChange.NoLocalPasswordException();
		}
		return this.persons.findById(principal.personId()).orElseThrow().getEmail();
	}

	private static boolean owns(OeaPrincipal principal, LocalCredential credential) {
		return (principal.type() == OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT)
				? principal.systemAdminAccountId().equals(credential.getSystemAdminAccountId())
				: principal.personId() != null && principal.personId().equals(credential.getPersonId());
	}

	/** REQ-030 AC4: a credential without label gets one with the date of the enrollment (D2). */
	static String defaultLabel(Instant now) {
		return "Authenticator app (" + now.atOffset(ZoneOffset.UTC).toLocalDate() + ")";
	}

	/** REQ-025 AC2: a failed confirmation, in its own transaction (D3). */
	private void failed(OeaPrincipal principal, UUID credentialId, String reason) {
		this.audit.appendIndependently(new AuditEvent("auth.factor-enrollment-failed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.FAILURE, AuditEvent.Source.API, Sessions.actor(principal),
				"credential", credentialId.toString(), Map.of("type", "totp", "reason", reason)));
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

}

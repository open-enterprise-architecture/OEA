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
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.repository.TotpCredentialRepository;
import io.oea.core.service.TotpAlgorithm;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TOTP enrollment in the running session (design D7, REQ-171, REQ-172, REQ-188). */
@Service
public class TotpEnrollment {

	static final Duration PENDING_VALIDITY = Duration.ofMinutes(10);

	private final LocalCredentialRepository credentials;

	private final TotpCredentialRepository totps;

	private final SystemAdminAccountRepository accounts;

	private final SecretCipher cipher;

	private final Sessions sessions;

	private final AuditWriter audit;

	private final RecentAuthentication recentAuthentication;

	private final Clock clock;

	public TotpEnrollment(LocalCredentialRepository credentials, TotpCredentialRepository totps,
			SystemAdminAccountRepository accounts, SecretCipher cipher, Sessions sessions, AuditWriter audit,
			RecentAuthentication recentAuthentication, Clock clock) {
		this.credentials = credentials;
		this.totps = totps;
		this.accounts = accounts;
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

	@Transactional
	public Started start(OeaPrincipal principal, String label) {
		requireSystemAdmin(principal);
		this.recentAuthentication.require(principal);
		Instant now = this.clock.instant();
		String secret = TotpAlgorithm.newSecret();
		LocalCredential credential = this.credentials.save(LocalCredential.pendingTotp(principal.systemAdminAccountId(), now));
		SecretCipher.Encrypted encrypted = this.cipher.encrypt(secret, TotpCredential.encryptionContext(credential.getId()));
		this.totps.save(new TotpCredential(credential.getId(), encrypted.ciphertext(), encrypted.keyId(), label));
		String account = this.accounts.findById(principal.systemAdminAccountId()).orElseThrow().getLoginName();
		String uri = "otpauth://totp/" + encode("OEA:" + account) + "?secret=" + secret + "&issuer=OEA&algorithm=SHA1"
				+ "&digits=6&period=" + TotpAlgorithm.PERIOD_SECONDS;
		return new Started(credential.getId(), secret, uri, now.plus(PENDING_VALIDITY));
	}

	@Transactional(noRollbackFor = InvalidTotpCodeException.class)
	public Confirmed confirm(OeaPrincipal principal, UUID credentialId, String code) {
		requireSystemAdmin(principal);
		Instant now = this.clock.instant();
		LocalCredential credential = this.credentials.findById(credentialId)
			.filter((c) -> principal.systemAdminAccountId().equals(c.getSystemAdminAccountId()))
			.filter((c) -> c.isPendingSince(now, PENDING_VALIDITY))
			.orElseThrow(() -> new NoSuchElementException("no pending enrollment " + credentialId));
		TotpCredential totp = this.totps.findById(credentialId).orElseThrow();
		String secret = this.cipher.decrypt(new SecretCipher.Encrypted(totp.getSecretEncrypted(), totp.getKeyId()),
				TotpCredential.encryptionContext(credentialId));
		OptionalLong step = TotpAlgorithm.matchingStep(secret, code, now);
		if (step.isEmpty() || !totp.acceptTimeStep(step.getAsLong())) {
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

	private static void requireSystemAdmin(OeaPrincipal principal) {
		if (principal.type() != OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT) {
			// local factors of persons follow with REQ-009/REQ-010
			throw new UnsupportedOperationException("second factors of persons are not supported yet");
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

}

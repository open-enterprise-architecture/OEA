package io.oea.app.login;

import io.oea.app.security.IdpRoles;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.config.oidc.InvalidIdTokenException;
import io.oea.config.oidc.OidcClient;
import io.oea.config.oidc.OidcClientSettings;
import io.oea.config.oidc.OidcProviderUnavailableException;
import io.oea.config.oidc.OidcTokenRejectedException;
import io.oea.config.secrets.SecretCipher;
import io.oea.core.domain.auth.AuthProvider;
import io.oea.core.domain.auth.LoginSession;
import io.oea.core.domain.person.Person;
import io.oea.core.domain.person.PersonIdentity;
import io.oea.core.repository.AuthProviderRepository;
import io.oea.core.repository.LoginSessionRepository;
import io.oea.core.repository.PersonIdentityRepository;
import io.oea.core.repository.PersonRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OIDC login of persons (US-001, REQ-001, REQ-004, REQ-166; design D2-D6, D10). The running
 * login is stored in {@code oidc_login_states} and bound to the browser by the login cookie;
 * the person is found only through the binding ({@code iss}, {@code sub}).
 */
@Service
public class OidcLoginFlow {

	static final Duration LOGIN_VALIDITY = Duration.ofMinutes(10);

	static final String CALLBACK_PATH = "/api/v1/auth/oidc/callback";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AuthProviderRepository providers;

	private final PersonIdentityRepository identities;

	private final PersonRepository persons;

	private final LoginSessionRepository loginSessions;

	private final Sessions sessions;

	private final IdpRoles idpRoles;

	private final OidcClient client;

	private final SecretCipher cipher;

	private final AuditWriter audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final String publicUrl;

	private final LoginMetrics metrics;

	public OidcLoginFlow(AuthProviderRepository providers, PersonIdentityRepository identities,
			PersonRepository persons, LoginSessionRepository loginSessions, Sessions sessions, IdpRoles idpRoles,
			OidcClient client, SecretCipher cipher, AuditWriter audit, JdbcTemplate jdbc, Clock clock,
			@Value("${oea.web.public-url:}") String publicUrl, LoginMetrics metrics) {
		this.providers = providers;
		this.identities = identities;
		this.persons = persons;
		this.loginSessions = loginSessions;
		this.sessions = sessions;
		this.idpRoles = idpRoles;
		this.client = client;
		this.cipher = cipher;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
		this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
		this.metrics = metrics;
	}

	/** An enabled provider as offered on the login page. */
	public record LoginProvider(UUID id, String name) {
	}

	/** Started login: redirect to the IdP and the value of the login cookie. */
	public record Started(URI authorizationUri, String browserBinding) {
	}

	/** Completed login: the new session and the portal path to open. */
	public record Completed(Sessions.NewSession session, String returnTo) {
	}

	/** The browser that called back. */
	public record Client(String address, String userAgent) {
	}

	@Transactional(readOnly = true)
	public List<LoginProvider> providers() {
		return this.providers.findAllByActiveTrueOrderByName()
			.stream()
			.map((provider) -> new LoginProvider(provider.getId(), provider.getName()))
			.toList();
	}

	/** Base URL of the portal (design D3). */
	public String publicUrl() {
		return this.publicUrl;
	}

	/**
	 * Starts a login (design D2).
	 *
	 * @throws OidcProviderUnavailableException if the provider is unknown, disabled or unreachable
	 */
	@Transactional
	public Started start(UUID providerId, String returnTo) {
		AuthProvider provider = this.providers.findById(providerId)
			.filter(AuthProvider::isActive)
			.orElseThrow(() -> new OidcProviderUnavailableException("provider " + providerId + " not available", null));
		String state = random();
		String nonce = random();
		String verifier = random();
		String browserBinding = random();
		URI authorizationUri = this.client.authorizationUri(settings(provider), redirectUri(), state, nonce,
				challenge(verifier));
		byte[] stateHash = sha256(state);
		SecretCipher.Encrypted encrypted = this.cipher.encrypt(verifier, verifierContext(stateHash));
		Instant now = this.clock.instant();
		this.jdbc.update("DELETE FROM oidc_login_states WHERE expires_at < ?", Timestamp.from(now));
		this.jdbc.update("""
				INSERT INTO oidc_login_states (state_hash, browser_binding_hash, auth_provider_id, nonce,
				    code_verifier_encrypted, code_verifier_key_id, return_to, created_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", stateHash, sha256(browserBinding), provider.getId(), nonce,
				encrypted.ciphertext(), encrypted.keyId(), safeReturnTo(returnTo), Timestamp.from(now),
				Timestamp.from(now.plus(LOGIN_VALIDITY)));
		return new Started(authorizationUri, browserBinding);
	}

	/**
	 * Completes a login at the callback.
	 *
	 * @throws OidcLoginFailedException for every refused login; the reason is already audited
	 * @throws OidcProviderUnavailableException if the provider could not be reached
	 */
	@Transactional(noRollbackFor = OidcLoginFailedException.class)
	public Completed complete(String state, String code, String error, String browserBinding, Client caller) {
		LoginMetrics.Measurement measurement = this.metrics.start();
		try {
			Completed completed = complete(measurement, state, code, error, browserBinding, caller);
			measurement.record("oidc", "success");
			return completed;
		}
		catch (RuntimeException ex) {
			measurement.record("oidc", "failure");
			throw ex;
		}
	}

	/** D1 of us-008-login-latency: the token request to the identity provider is not counted. */
	private Completed complete(LoginMetrics.Measurement measurement, String state, String code, String error,
			String browserBinding, Client caller) {
		LoginState login = consume(state, browserBinding).orElseThrow(() -> failed(null, "state-invalid"));
		if (error != null || code == null) {
			throw failed(login.providerId(), "access_denied".equals(error) ? "cancelled" : "provider-error");
		}
		AuthProvider provider = this.providers.findById(login.providerId())
			.filter(AuthProvider::isActive)
			.orElseThrow(() -> failed(login.providerId(), "provider-error"));
		OidcClientSettings settings = settings(provider);
		OidcClient.Tokens tokens;
		Jwt idToken;
		try {
			measurement.pause();
			try {
				tokens = this.client.redeem(settings, redirectUri(), code, state, login.codeVerifier());
			}
			finally {
				measurement.resume();
			}
			if (tokens.idToken() == null) {
				throw failed(provider.getId(), "token-invalid");
			}
			idToken = this.client.validateIdToken(settings, tokens.idToken(), login.nonce());
		}
		catch (OidcTokenRejectedException ex) {
			throw failed(provider.getId(), "provider-error");
		}
		catch (OidcProviderUnavailableException ex) {
			failed(provider.getId(), "provider-unavailable");
			throw ex;
		}
		catch (InvalidIdTokenException ex) {
			throw failed(provider.getId(), "token-invalid");
		}
		// REQ-166 AC1, REQ-004: only the immutable pair (iss, sub), never e-mail
		PersonIdentity identity = this.identities.findByIssuerAndSubject(provider.getIssuer(), idToken.getSubject())
			.filter((candidate) -> candidate.getAuthProviderId().equals(provider.getId()))
			.orElseThrow(() -> failed(provider.getId(), "identity-unknown"));
		Person person = this.persons.findById(identity.getPersonId())
			.filter(Person::mayLogIn)
			.orElseThrow(() -> failed(provider.getId(), "person-not-active"));
		Instant now = this.clock.instant();
		if (person.activateOnFirstLogin(now)) {
			this.audit.append(new AuditEvent("person.activated", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
					AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, actor(person), "person",
					person.getId().toString(), Map.of("trigger", "first-login")));
		}
		identity.recordLogin(now);
		boolean secondFactor = AuthenticationMethods.secondFactor(idToken.getClaimAsStringList("amr"));
		Sessions.NewSession session = this.sessions.createForPerson(person, provider.getId(), secondFactor,
				caller.address(), caller.userAgent());
		LoginSession stored = this.loginSessions.findById(session.principal().sessionId()).orElseThrow();
		storeRefreshToken(stored, tokens);
		this.idpRoles.evaluate(stored, provider.getIssuer(), IdpRoles.GroupsClaim.of(idToken.getClaims()),
				secondFactor, "login");
		Map<String, Object> details = new HashMap<>();
		details.put("method", "oidc");
		details.put("providerId", provider.getId().toString());
		details.put("secondFactor", secondFactor);
		this.audit.append(new AuditEvent("auth.login", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, actor(person), "session",
				session.principal().sessionId().toString(), details));
		return new Completed(session, login.returnTo());
	}

	/** Stores the rotated refresh token of a token response, encrypted (REQ-188). */
	public void storeRefreshToken(LoginSession session, OidcClient.Tokens tokens) {
		if (tokens.refreshToken() == null) {
			session.storeIdpRefreshToken(null, null, null);
			return;
		}
		SecretCipher.Encrypted encrypted = this.cipher.encrypt(tokens.refreshToken(),
				LoginSession.refreshTokenEncryptionContext(session.getId()));
		session.storeIdpRefreshToken(encrypted.ciphertext(), encrypted.keyId(), tokens.refreshTokenExpiresAt());
	}

	/** Client settings of a stored provider with the decrypted client secret. */
	public OidcClientSettings settings(AuthProvider provider) {
		String secret = provider.hasClientSecret()
				? this.cipher.decrypt(new SecretCipher.Encrypted(provider.getClientSecretEncrypted(),
						provider.getClientSecretKeyId()), AuthProvider.encryptionContext(provider.getId()))
				: null;
		Set<String> scopes = new LinkedHashSet<>(Arrays.asList(provider.getScopes().trim().split("\\s+")));
		scopes.add("openid");
		return new OidcClientSettings(provider.getId(), provider.getIssuer(), provider.getClientId(), secret, scopes);
	}

	private record LoginState(UUID providerId, String nonce, String codeVerifier, String returnTo) {
	}

	/** Deletes and returns the running login if state and login cookie match and it has not expired. */
	private Optional<LoginState> consume(String state, String browserBinding) {
		if (state == null || state.isBlank() || browserBinding == null || browserBinding.isBlank()) {
			return Optional.empty();
		}
		byte[] stateHash = sha256(state);
		List<Map<String, Object>> rows = this.jdbc.queryForList("""
				DELETE FROM oidc_login_states WHERE state_hash = ?
				RETURNING browser_binding_hash, auth_provider_id, nonce, code_verifier_encrypted,
				          code_verifier_key_id, return_to, expires_at""", stateHash);
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Map<String, Object> row = rows.get(0);
		boolean bound = MessageDigest.isEqual((byte[]) row.get("browser_binding_hash"), sha256(browserBinding));
		boolean current = ((Timestamp) row.get("expires_at")).toInstant().isAfter(this.clock.instant());
		if (!bound || !current) {
			return Optional.empty();
		}
		String verifier = this.cipher.decrypt(new SecretCipher.Encrypted((String) row.get("code_verifier_encrypted"),
				(String) row.get("code_verifier_key_id")), verifierContext(stateHash));
		return Optional.of(new LoginState((UUID) row.get("auth_provider_id"), (String) row.get("nonce"), verifier,
				(String) row.get("return_to")));
	}

	private OidcLoginFailedException failed(UUID providerId, String reason) {
		Map<String, Object> details = new HashMap<>();
		details.put("mechanism", "oidc");
		details.put("reason", reason);
		if (providerId != null) {
			details.put("providerId", providerId.toString());
		}
		this.audit.appendIndependently(new AuditEvent("auth.login-failed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.FAILURE, AuditEvent.Source.API,
				AuditEvent.Actor.unauthenticated(), null, null, details));
		return new OidcLoginFailedException(reason);
	}

	private String redirectUri() {
		return this.publicUrl + CALLBACK_PATH;
	}

	/** Only relative portal paths; anything else opens the start page (no open redirect). */
	static String safeReturnTo(String returnTo) {
		if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//") || returnTo.contains("\\")
				|| returnTo.length() > 512) {
			return "/";
		}
		return returnTo;
	}

	private static String verifierContext(byte[] stateHash) {
		return "oidc_login_states.code_verifier_encrypted:" + HexFormat.of().formatHex(stateHash);
	}

	private static AuditEvent.Actor actor(Person person) {
		return new AuditEvent.Actor("person", person.getId(), null);
	}

	private static String random() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String challenge(String verifier) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

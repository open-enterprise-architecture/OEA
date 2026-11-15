package io.oea.app.login;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.oea.app.security.OeaPrincipal;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditFloodGuard;
import io.oea.audit.AuditWriter;
import io.oea.core.access.AccessTokenStore;
import io.oea.core.access.ServiceAccountStore;
import io.oea.core.service.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Access tokens of service accounts (ADR-050, REQ-176; design D3–D5, D8 of
 * s12-client-credentials-token): the client credentials grant with the throttle of the local
 * login, a limit of 4 concurrent secret checks and a uniform refusal, and the check of a bearer
 * token. A token is {@code oea_at_} plus 256 random bits; only its SHA-256 hash is stored. Neither
 * secret nor token is ever logged or audited.
 */
@Service
public class AccessTokens {

	public static final String TOKEN_PREFIX = "oea_at_";

	/** At most this many secret checks run at the same time (ADR-050 rule 6, owner decision 2026-11-15). */
	static final int CONCURRENT_CHECKS = 4;

	static final Duration CHECK_WAIT = Duration.ofSeconds(2);

	static final String TIMER = "oea.token.processing";

	private static final int TOKEN_BYTES = 32;

	private final ServiceAccountStore accounts;

	private final AccessTokenStore tokens;

	private final PasswordHasher hasher;

	private final LoginThrottle throttle;

	private final AuditWriter audit;

	private final AuditFloodGuard flood;

	private final MeterRegistry registry;

	private final TransactionTemplate transactions;

	private final Clock clock;

	private final Semaphore checks = new Semaphore(CONCURRENT_CHECKS, true);

	private final SecureRandom random = new SecureRandom();

	/** Checked for unknown clients, so the response time does not reveal whether a client exists. */
	private final String dummyHash;

	/** An issued token, returned once. */
	public record Issued(String accessToken, long expiresIn) {
	}

	/** The client authentication failed; always answered with the same 401 {@code invalid_client}. */
	public static class InvalidClientException extends RuntimeException {

		public InvalidClientException() {
			super("invalid_client");
		}

	}

	/** The token endpoint is throttled for the client or the address, or all secret checks are busy. */
	public static class ThrottledException extends RuntimeException {

		private final long retryAfterSeconds;

		public ThrottledException(long retryAfterSeconds) {
			super("throttled");
			this.retryAfterSeconds = retryAfterSeconds;
		}

		public long getRetryAfterSeconds() {
			return this.retryAfterSeconds;
		}

	}

	public AccessTokens(ServiceAccountStore accounts, AccessTokenStore tokens, PasswordHasher hasher,
			LoginThrottle throttle, AuditWriter audit, AuditFloodGuard flood, MeterRegistry registry,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.accounts = accounts;
		this.tokens = tokens;
		this.hasher = hasher;
		this.throttle = throttle;
		this.audit = audit;
		this.flood = flood;
		this.registry = registry;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
		this.dummyHash = hasher.hash("timing-equalizer-" + UUID.randomUUID());
	}

	/**
	 * The client credentials grant: checks the client ID and secret and issues a token.
	 * @param clientId the client ID as sent, possibly malformed
	 * @param secret the secret as sent
	 * @param address the client address for the throttle
	 * @throws ThrottledException while the client or the address is throttled, or no secret check
	 * becomes free within 2 seconds
	 * @throws InvalidClientException for an unknown client, a wrong or expired secret and a
	 * deactivated service account
	 */
	public Issued issue(String clientId, String secret, String address) {
		return timed(() -> {
			long retryAfter = this.throttle.clientRetryAfterSeconds(clientId, address);
			if (retryAfter > 0) {
				throw new ThrottledException(retryAfter);
			}
			Optional<ServiceAccountStore.ActiveSecret> active = parse(clientId).flatMap(this.accounts::activeSecret);
			boolean matches = check(secret, active.map(ServiceAccountStore.ActiveSecret::hash).orElse(this.dummyHash));
			Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
			String reason = active.isEmpty() ? "unknown-client"
					: !matches ? "wrong-secret" : !active.get().expiresAt().isAfter(now) ? "expired-secret" : null;
			if (reason != null) {
				this.throttle.recordClientFailure(clientId, address);
				deny(clientId, reason);
				throw new InvalidClientException();
			}
			ServiceAccountStore.ActiveSecret granted = active.get();
			String token = token();
			UUID tokenId = UUID.randomUUID();
			this.transactions.executeWithoutResult((status) -> {
				this.tokens.insert(tokenId, hash(token), granted.clientId(), granted.id(), now);
				this.tokens.markUsed(granted.id(), now);
				this.throttle.recordClientSuccess(clientId);
				Map<String, Object> details = new LinkedHashMap<>();
				details.put("clientId", granted.clientId().toString());
				details.put("tokenId", tokenId.toString());
				details.put("expiresAt", now.plus(AccessTokenStore.LIFETIME).toString());
				this.audit.append(new AuditEvent("token.issued", AuditEvent.Category.SECURITY, AuditEvent.Severity.INFO,
						AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API,
						AuditEvent.Actor.serviceAccount(granted.personId(), granted.clientId()), "access-token",
						tokenId.toString(), details));
			});
			return new Issued(token, AccessTokenStore.LIFETIME.toSeconds());
		});
	}

	/** The limit of concurrent secret checks; tests hold its permits. */
	Semaphore checks() {
		return this.checks;
	}

	/** The service account of a valid bearer token, if any (design D5). */
	public Optional<OeaPrincipal> authenticate(String token) {
		if (token == null || !token.startsWith(TOKEN_PREFIX)) {
			return Optional.empty();
		}
		return this.tokens.findValid(hash(token), this.clock.instant())
			.map((valid) -> OeaPrincipal.serviceAccount(valid.personId(), valid.clientId(), valid.name(),
					valid.issuedAt()));
	}

	/** SHA-256 of a token, the only form in which a token is stored (ADR-050 rule 2). */
	public static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private boolean check(String secret, String hash) {
		boolean acquired;
		try {
			acquired = this.checks.tryAcquire(CHECK_WAIT.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ThrottledException(1);
		}
		if (!acquired) {
			throw new ThrottledException(1);
		}
		try {
			return secret != null && this.hasher.matches(secret, hash);
		}
		finally {
			this.checks.release();
		}
	}

	private void deny(String clientId, String reason) {
		Map<String, Object> details = new LinkedHashMap<>();
		// the claimed client is not authenticated; it is recorded only for investigation (ADR-050 rule 8)
		details.put("claimedClientId", (clientId != null && clientId.length() <= 64) ? clientId : null);
		details.put("unverified", true);
		details.put("reason", reason);
		this.flood.append(new AuditEvent("token.denied", AuditEvent.Category.SECURITY, AuditEvent.Severity.WARNING,
				AuditEvent.Outcome.DENIED, AuditEvent.Source.API, AuditEvent.Actor.unauthenticated(), "access-token",
				null, details), "issueClientCredentialsToken");
	}

	private <T> T timed(Supplier<T> step) {
		long started = this.registry.config().clock().monotonicTime();
		String outcome = "issued";
		try {
			return step.get();
		}
		catch (InvalidClientException ex) {
			outcome = "denied";
			throw ex;
		}
		catch (ThrottledException ex) {
			outcome = "throttled";
			throw ex;
		}
		finally {
			Timer.builder(TIMER)
				.description("Server-side processing time of token requests")
				.tag("outcome", outcome)
				.publishPercentiles(0.5, 0.95)
				.publishPercentileHistogram()
				.register(this.registry)
				.record(this.registry.config().clock().monotonicTime() - started, TimeUnit.NANOSECONDS);
		}
	}

	private String token() {
		byte[] bytes = new byte[TOKEN_BYTES];
		this.random.nextBytes(bytes);
		return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static Optional<UUID> parse(String clientId) {
		try {
			return (clientId != null) ? Optional.of(UUID.fromString(clientId.strip())) : Optional.empty();
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}

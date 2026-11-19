package io.oea.app.login;

import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.LoginThrottleStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Throttling of local logins (REQ-168, design D6), stored in the database so that several
 * backend instances share it: per login name (also unknown ones) exponential backoff from the
 * sixth failure within 15 minutes, capped at a 15-minute temporary lock; per source address
 * a limit of 100 failures within 10 minutes. Failed setup-token attempts count towards the
 * same per-address limit (REQ-168; design D6 of s3-throttling-and-denials). Token requests of service
 * accounts are throttled by the same rules per client ID and share the per-address limit (design D4
 * of s12-client-credentials-token).
 */
@Service
public class LoginThrottle {

	static final int ACCOUNT_FAILURES_BEFORE_BACKOFF = 5;

	static final Duration ACCOUNT_WINDOW = Duration.ofMinutes(15);

	static final Duration MAXIMUM_LOCK = Duration.ofMinutes(15);

	static final int ADDRESS_FAILURES = 100;

	static final Duration ADDRESS_WINDOW = Duration.ofMinutes(10);

	private final LoginThrottleStore store;

	private final AuditWriter audit;

	private final Clock clock;

	public LoginThrottle(LoginThrottleStore store, AuditWriter audit, Clock clock) {
		this.store = store;
		this.audit = audit;
		this.clock = clock;
	}

	/** Throttle key of a login name; also cleared by the break-glass recovery. */
	public static String accountKey(String loginName) {
		return "account:" + ((loginName != null) ? loginName.strip().toLowerCase(Locale.ROOT) : "");
	}

	/** Throttle key of a client ID at the token endpoint, also of malformed or unknown ones. */
	public static String clientKey(String clientId) {
		return "client:" + ((clientId != null) ? clientId.strip().toLowerCase(Locale.ROOT) : "");
	}

	static String addressKey(String address) {
		return "address:" + address;
	}

	/** Seconds until the keys accept attempts again; 0 if not throttled. */
	@Transactional(readOnly = true)
	public long retryAfterSeconds(String loginName, String address) {
		Instant now = this.clock.instant();
		return Math.max(lockedFor(accountKey(loginName), now), lockedFor(addressKey(address), now));
	}

	/** Seconds until token requests of the client from the address are accepted again; 0 if not throttled. */
	@Transactional(readOnly = true)
	public long clientRetryAfterSeconds(String clientId, String address) {
		Instant now = this.clock.instant();
		return Math.max(lockedFor(clientKey(clientId), now), lockedFor(addressKey(address), now));
	}

	/** Records a failed token request in its own transaction, by the rules of a failed login. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordClientFailure(String clientId, String address) {
		recordFailure(clientKey(clientId), "client", address);
	}

	@Transactional
	public void recordClientSuccess(String clientId) {
		this.store.delete(clientKey(clientId));
	}

	/** Seconds until the source address accepts attempts again; 0 if not throttled. */
	@Transactional(readOnly = true)
	public long retryAfterSecondsForAddress(String address) {
		return lockedFor(addressKey(address), this.clock.instant());
	}

	/**
	 * Records a failed attempt that has no login name (a wrong setup token) for the source
	 * address only, in its own transaction.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordAddressFailure(String address) {
		Instant now = this.clock.instant();
		int addressFailures = increment(addressKey(address), now, ADDRESS_WINDOW);
		if (addressFailures >= ADDRESS_FAILURES) {
			lock(addressKey(address), now.plus(ADDRESS_WINDOW), addressFailures == ADDRESS_FAILURES, "address");
		}
	}

	private long lockedFor(String key, Instant now) {
		Instant lockedUntil = this.store.lockedUntil(key);
		if (lockedUntil != null && lockedUntil.isAfter(now)) {
			return Duration.between(now, lockedUntil).toSeconds() + 1;
		}
		return 0;
	}

	/** Records a failed attempt in its own transaction (it survives the rollback of the login). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(String loginName, String address) {
		recordFailure(accountKey(loginName), "account", address);
	}

	private void recordFailure(String key, String scope, String address) {
		Instant now = this.clock.instant();
		int accountFailures = increment(key, now, ACCOUNT_WINDOW);
		if (accountFailures > ACCOUNT_FAILURES_BEFORE_BACKOFF - 1) {
			long seconds = Math.min(1L << Math.min(accountFailures - ACCOUNT_FAILURES_BEFORE_BACKOFF, 20),
					MAXIMUM_LOCK.toSeconds());
			lock(key, now.plusSeconds(seconds), accountFailures == ACCOUNT_FAILURES_BEFORE_BACKOFF, scope);
		}
		int addressFailures = increment(addressKey(address), now, ADDRESS_WINDOW);
		if (addressFailures >= ADDRESS_FAILURES) {
			lock(addressKey(address), now.plus(ADDRESS_WINDOW), addressFailures == ADDRESS_FAILURES, "address");
		}
	}

	@Transactional
	public void recordSuccess(String loginName) {
		this.store.delete(accountKey(loginName));
	}

	private int increment(String key, Instant now, Duration window) {
		return this.store.incrementFailures(key, now, now.minus(window));
	}

	private void lock(String key, Instant until, boolean first, String scope) {
		this.store.lockUntil(key, until);
		if (first) {
			this.audit.append(new AuditEvent("auth.throttled", AuditEvent.Category.SECURITY, AuditEvent.Severity.WARNING,
					AuditEvent.Outcome.DENIED, AuditEvent.Source.API, AuditEvent.Actor.unauthenticated(), null, null,
					Map.of("scope", scope)));
		}
	}

}

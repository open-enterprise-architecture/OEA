package io.oea.app.login;

import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Throttling of local logins (REQ-168, design D6), stored in the database so that several
 * backend instances share it: per login name (also unknown ones) exponential backoff from the
 * sixth failure within 15 minutes, capped at a 15-minute temporary lock; per source address
 * a limit of 100 failures within 10 minutes. Failed setup-token attempts count towards the
 * same per-address limit (REQ-168; design D6 of s3-throttling-and-denials).
 */
@Service
public class LoginThrottle {

	static final int ACCOUNT_FAILURES_BEFORE_BACKOFF = 5;

	static final Duration ACCOUNT_WINDOW = Duration.ofMinutes(15);

	static final Duration MAXIMUM_LOCK = Duration.ofMinutes(15);

	static final int ADDRESS_FAILURES = 100;

	static final Duration ADDRESS_WINDOW = Duration.ofMinutes(10);

	private final JdbcTemplate jdbc;

	private final AuditWriter audit;

	private final Clock clock;

	public LoginThrottle(JdbcTemplate jdbc, AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** Throttle key of a login name; also cleared by the break-glass recovery. */
	public static String accountKey(String loginName) {
		return "account:" + ((loginName != null) ? loginName.strip().toLowerCase(Locale.ROOT) : "");
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
		Timestamp lockedUntil = this.jdbc.query("SELECT locked_until FROM auth_throttle WHERE throttle_key = ?",
				(rs) -> rs.next() ? rs.getTimestamp(1) : null, key);
		if (lockedUntil != null && lockedUntil.toInstant().isAfter(now)) {
			return Duration.between(now, lockedUntil.toInstant()).toSeconds() + 1;
		}
		return 0;
	}

	/** Records a failed attempt in its own transaction (it survives the rollback of the login). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(String loginName, String address) {
		Instant now = this.clock.instant();
		int accountFailures = increment(accountKey(loginName), now, ACCOUNT_WINDOW);
		if (accountFailures > ACCOUNT_FAILURES_BEFORE_BACKOFF - 1) {
			long seconds = Math.min(1L << Math.min(accountFailures - ACCOUNT_FAILURES_BEFORE_BACKOFF, 20),
					MAXIMUM_LOCK.toSeconds());
			lock(accountKey(loginName), now.plusSeconds(seconds), accountFailures == ACCOUNT_FAILURES_BEFORE_BACKOFF,
					"account");
		}
		int addressFailures = increment(addressKey(address), now, ADDRESS_WINDOW);
		if (addressFailures >= ADDRESS_FAILURES) {
			lock(addressKey(address), now.plus(ADDRESS_WINDOW), addressFailures == ADDRESS_FAILURES, "address");
		}
	}

	@Transactional
	public void recordSuccess(String loginName) {
		this.jdbc.update("DELETE FROM auth_throttle WHERE throttle_key = ?", accountKey(loginName));
	}

	private int increment(String key, Instant now, Duration window) {
		Timestamp windowStart = Timestamp.from(now.minus(window));
		return this.jdbc.queryForObject("""
				INSERT INTO auth_throttle (throttle_key, failures, window_started_at) VALUES (?, 1, ?)
				ON CONFLICT (throttle_key) DO UPDATE SET
				    failures = CASE WHEN auth_throttle.window_started_at < ? THEN 1 ELSE auth_throttle.failures + 1 END,
				    window_started_at = CASE WHEN auth_throttle.window_started_at < ? THEN EXCLUDED.window_started_at
				                             ELSE auth_throttle.window_started_at END
				RETURNING failures""", Integer.class, key, Timestamp.from(now), windowStart, windowStart);
	}

	private void lock(String key, Instant until, boolean first, String scope) {
		this.jdbc.update("UPDATE auth_throttle SET locked_until = ? WHERE throttle_key = ?", Timestamp.from(until), key);
		if (first) {
			this.audit.append(new AuditEvent("auth.throttled", AuditEvent.Category.SECURITY, AuditEvent.Severity.WARNING,
					AuditEvent.Outcome.DENIED, AuditEvent.Source.API, AuditEvent.Actor.unauthenticated(), null, null,
					Map.of("scope", scope)));
		}
	}

}

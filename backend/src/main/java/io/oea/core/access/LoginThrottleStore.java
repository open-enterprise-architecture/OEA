package io.oea.core.access;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL on {@code auth_throttle}, shared by all backend instances (S17 change 3, ADR-051). */
@Repository
public class LoginThrottleStore {

	private final JdbcTemplate jdbc;

	public LoginThrottleStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The end of the lock of the key, or {@code null} if it is not locked. */
	public Instant lockedUntil(String key) {
		Timestamp lockedUntil = this.jdbc.query("SELECT locked_until FROM auth_throttle WHERE throttle_key = ?",
				(rs) -> rs.next() ? rs.getTimestamp(1) : null, key);
		return (lockedUntil != null) ? lockedUntil.toInstant() : null;
	}

	/** Counts a failure of the key; a window that started before {@code windowStart} begins anew. */
	public int incrementFailures(String key, Instant now, Instant windowStart) {
		Timestamp start = Timestamp.from(windowStart);
		return this.jdbc.queryForObject("""
				INSERT INTO auth_throttle (throttle_key, failures, window_started_at) VALUES (?, 1, ?)
				ON CONFLICT (throttle_key) DO UPDATE SET
				    failures = CASE WHEN auth_throttle.window_started_at < ? THEN 1 ELSE auth_throttle.failures + 1 END,
				    window_started_at = CASE WHEN auth_throttle.window_started_at < ? THEN EXCLUDED.window_started_at
				                             ELSE auth_throttle.window_started_at END
				RETURNING failures""", Integer.class, key, Timestamp.from(now), start, start);
	}

	/** Locks the key until the time. */
	public void lockUntil(String key, Instant until) {
		this.jdbc.update("UPDATE auth_throttle SET locked_until = ? WHERE throttle_key = ?", Timestamp.from(until), key);
	}

	/** Clears the key. */
	public void delete(String key) {
		this.jdbc.update("DELETE FROM auth_throttle WHERE throttle_key = ?", key);
	}

}

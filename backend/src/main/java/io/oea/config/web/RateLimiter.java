package io.oea.config.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Per-principal request counters (REQ-186; design D1, D2 of s3-throttling-and-denials): a fixed
 * window of one minute per principal and limit that starts with the first request. Counters
 * are kept in memory of this instance; idle windows are removed periodically.
 */
public class RateLimiter {

	static final Duration WINDOW = Duration.ofMinutes(1);

	private final Clock clock;

	private final Map<Key, Window> windows = new ConcurrentHashMap<>();

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/**
	 * The outcome of one request.
	 * @param allowed the request may be processed
	 * @param retryAfterSeconds seconds until the window ends, for a rejected request
	 * @param firstRejection the first rejection of its window (audited once)
	 */
	public record Decision(boolean allowed, long retryAfterSeconds, boolean firstRejection) {
	}

	/** Counts a request of the principal against the limit, unless the limit is reached. */
	public Decision acquire(String principal, String limit, int perMinute) {
		Instant now = this.clock.instant();
		Decision[] decision = new Decision[1];
		this.windows.compute(new Key(principal, limit), (key, current) -> {
			Window window = (current == null || !now.isBefore(current.end)) ? new Window(now.plus(WINDOW)) : current;
			if (window.count < perMinute) {
				window.count++;
				decision[0] = new Decision(true, 0, false);
			}
			else {
				long millis = Duration.between(now, window.end).toMillis();
				decision[0] = new Decision(false, Math.max(1, (millis + 999) / 1000), !window.rejected);
				window.rejected = true;
			}
			return window;
		});
		return decision[0];
	}

	/** Forgets the windows that have ended. */
	@Scheduled(fixedDelayString = "PT1M")
	public void removeEndedWindows() {
		Instant now = this.clock.instant();
		this.windows.values().removeIf((window) -> !now.isBefore(window.end));
	}

	private record Key(String principal, String limit) {
	}

	private static final class Window {

		private final Instant end;

		private int count;

		private boolean rejected;

		private Window(Instant end) {
			this.end = end;
		}

	}

}

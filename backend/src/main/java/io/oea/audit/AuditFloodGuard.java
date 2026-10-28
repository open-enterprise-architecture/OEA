package io.oea.audit;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Flood suppression of repeated security events (REQ-175 AC5; design D5 of
 * s3-throttling-and-denials): per event type, actor, source address and operation, the first
 * events of a window are written individually, further ones are counted and written as one
 * summary event of the same type after the window has closed. Counters are kept in memory
 * (one backend instance per installation); open windows are flushed on a regular shutdown.
 */
@Component
public class AuditFloodGuard {

	private final AuditWriter audit;

	private final Clock clock;

	private final Duration window;

	private final int individualEvents;

	private final Map<Key, Window> windows = new ConcurrentHashMap<>();

	public AuditFloodGuard(AuditWriter audit, Clock clock,
			@Value("${oea.audit.flood-suppression.window:PT1M}") Duration window,
			@Value("${oea.audit.flood-suppression.individual-events:20}") int individualEvents) {
		if (window.isNegative() || window.isZero() || individualEvents < 1) {
			throw new IllegalArgumentException("oea.audit.flood-suppression needs a positive window and count");
		}
		this.audit = audit;
		this.clock = clock;
		this.window = window;
		this.individualEvents = individualEvents;
	}

	/**
	 * Writes the event in its own transaction, or counts it if the individual events of its
	 * window are used up.
	 * @param event the event; its actor is part of the key
	 * @param operation operation ID the event is about, part of the key
	 */
	public void append(AuditEvent event, String operation) {
		Instant now = this.clock.instant();
		String sourceAddress = AuditWriter.sourceIp();
		Key key = new Key(event.eventType(), event.actor(), sourceAddress, operation);
		boolean[] individual = new boolean[1];
		Window[] closed = new Window[1];
		this.windows.compute(key, (k, current) -> {
			Window active = current;
			if (active == null || !now.isBefore(active.end)) {
				closed[0] = current;
				active = new Window(event, now, now.plus(this.window));
			}
			if (active.individual < this.individualEvents) {
				active.individual++;
				individual[0] = true;
			}
			else {
				active.suppressed++;
			}
			return active;
		});
		if (closed[0] != null) {
			writeSummary(key, closed[0]);
		}
		if (individual[0]) {
			this.audit.appendIndependently(event);
		}
	}

	/** Writes the summaries of closed windows and forgets them. */
	@Scheduled(fixedDelayString = "${oea.audit.flood-suppression.flush-interval:PT10S}")
	public void flushClosedWindows() {
		Instant now = this.clock.instant();
		for (Key key : this.windows.keySet()) {
			Window[] closed = new Window[1];
			this.windows.computeIfPresent(key, (k, current) -> {
				if (now.isBefore(current.end)) {
					return current;
				}
				closed[0] = current;
				return null;
			});
			if (closed[0] != null) {
				writeSummary(key, closed[0]);
			}
		}
	}

	/** Writes the summaries of all windows, also of open ones, on a regular shutdown. */
	@PreDestroy
	public void flushAll() {
		for (Key key : this.windows.keySet()) {
			Window removed = this.windows.remove(key);
			if (removed != null) {
				writeSummary(key, removed);
			}
		}
	}

	private void writeSummary(Key key, Window closed) {
		if (closed.suppressed == 0) {
			return;
		}
		AuditEvent first = closed.first;
		Map<String, Object> details = new LinkedHashMap<>();
		if (key.operation() != null) {
			details.put("operation", key.operation());
		}
		details.put("summary", true);
		details.put("suppressedCount", closed.suppressed);
		details.put("windowStart", closed.start.toString());
		details.put("windowEnd", closed.end.toString());
		this.audit.appendIndependently(new AuditEvent(first.eventType(), first.category(), first.severity(),
				first.outcome(), first.source(), first.actor(), first.targetType(), null, details),
				key.sourceAddress());
	}

	private record Key(String eventType, AuditEvent.Actor actor, String sourceAddress, String operation) {
	}

	private static final class Window {

		private final AuditEvent first;

		private final Instant start;

		private final Instant end;

		private int individual;

		private int suppressed;

		private Window(AuditEvent first, Instant start, Instant end) {
			this.first = first;
			this.start = start;
			this.end = end;
		}

	}

}

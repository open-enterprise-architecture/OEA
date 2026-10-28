package io.oea.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.oea.testsupport.time.MutableClock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Flood suppression of REQ-175 AC5 (design D5 of s3-throttling-and-denials). */
class AuditFloodGuardTests {

	private final AuditWriter writer = mock(AuditWriter.class);

	private final MutableClock clock = new MutableClock();

	private final AuditFloodGuard guard = new AuditFloodGuard(this.writer, this.clock, Duration.ofMinutes(1), 20);

	private static AuditEvent denied(long personId) {
		return new AuditEvent("authz.denied", AuditEvent.Category.SECURITY, AuditEvent.Severity.WARNING,
				AuditEvent.Outcome.DENIED, AuditEvent.Source.API, new AuditEvent.Actor("person", personId, null),
				null, null, Map.of("operation", "listIdpGroupMappings", "reason", "missing-permission"));
	}

	@Test
	void fiftyDenialsGiveTwentyEventsAndOneSummaryWithThirty() {
		for (int i = 0; i < 50; i++) {
			this.guard.append(denied(7), "listIdpGroupMappings");
		}
		verify(this.writer, times(20)).appendIndependently(any(AuditEvent.class));

		this.guard.flushClosedWindows();
		verify(this.writer, never()).appendIndependently(any(AuditEvent.class), any());

		this.clock.advance(Duration.ofSeconds(61));
		this.guard.flushClosedWindows();
		ArgumentCaptor<AuditEvent> summary = ArgumentCaptor.forClass(AuditEvent.class);
		verify(this.writer).appendIndependently(summary.capture(), isNull());
		assertThat(summary.getValue().eventType()).isEqualTo("authz.denied");
		assertThat(summary.getValue().actor()).isEqualTo(new AuditEvent.Actor("person", 7L, null));
		assertThat(summary.getValue().details()).containsEntry("summary", true)
			.containsEntry("suppressedCount", 30)
			.containsEntry("operation", "listIdpGroupMappings")
			.containsKeys("windowStart", "windowEnd");
	}

	@Test
	void operationsAndActorsAreCountedSeparately() {
		for (int i = 0; i < 15; i++) {
			this.guard.append(denied(7), "listIdpGroupMappings");
			this.guard.append(denied(7), "listPersons");
			this.guard.append(denied(8), "listPersons");
		}
		this.clock.advance(Duration.ofMinutes(2));
		this.guard.flushClosedWindows();
		verify(this.writer, times(45)).appendIndependently(any(AuditEvent.class));
		verify(this.writer, never()).appendIndependently(any(AuditEvent.class), any());
	}

	@Test
	void newWindowWritesTheSummaryOfThePreviousOne() {
		for (int i = 0; i < 21; i++) {
			this.guard.append(denied(7), "listPersons");
		}
		this.clock.advance(Duration.ofSeconds(60));
		this.guard.append(denied(7), "listPersons");
		verify(this.writer, times(21)).appendIndependently(any(AuditEvent.class));
		ArgumentCaptor<AuditEvent> summary = ArgumentCaptor.forClass(AuditEvent.class);
		verify(this.writer).appendIndependently(summary.capture(), isNull());
		assertThat(summary.getValue().details()).containsEntry("suppressedCount", 1);
	}

	@Test
	void shutdownFlushesOpenWindows() {
		for (int i = 0; i < 25; i++) {
			this.guard.append(denied(7), "listPersons");
		}
		this.guard.flushAll();
		ArgumentCaptor<AuditEvent> summary = ArgumentCaptor.forClass(AuditEvent.class);
		verify(this.writer).appendIndependently(summary.capture(), eq(null));
		assertThat(summary.getValue().details()).containsEntry("suppressedCount", 5);
	}

}

package io.oea.testsupport.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward (throttling, timeouts). */
public final class MutableClock extends Clock {

	private Instant now = Instant.now();

	public synchronized void advance(Duration duration) {
		this.now = this.now.plus(duration);
	}

	@Override
	public synchronized Instant instant() {
		return this.now;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}

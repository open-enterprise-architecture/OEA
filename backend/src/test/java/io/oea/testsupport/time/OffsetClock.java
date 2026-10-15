package io.oea.testsupport.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock that runs with the system time plus an offset tests can move forward, for flows
 * that also involve real tokens of the IdP (their {@code iat} must not lie in the future).
 */
public final class OffsetClock extends Clock {

	private volatile Duration offset = Duration.ZERO;

	public void advance(Duration duration) {
		this.offset = this.offset.plus(duration);
	}

	@Override
	public Instant instant() {
		return Instant.now().plus(this.offset);
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

package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.time.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Window logic of the per-principal rate limits (design D2 of s3-throttling-and-denials). */
class RateLimiterTests {

	private final MutableClock clock = new MutableClock();

	private final RateLimiter limiter = new RateLimiter(this.clock);

	@Test
	void requestsAboveTheLimitAreRejectedUntilTheWindowEnds() {
		for (int i = 0; i < 3; i++) {
			assertThat(this.limiter.acquire("person:1", "requests", 3).allowed()).isTrue();
		}
		this.clock.advance(Duration.ofSeconds(20));
		RateLimiter.Decision first = this.limiter.acquire("person:1", "requests", 3);
		assertThat(first.allowed()).isFalse();
		assertThat(first.retryAfterSeconds()).isEqualTo(40);
		assertThat(first.firstRejection()).isTrue();

		this.clock.advance(Duration.ofMillis(39_500));
		RateLimiter.Decision second = this.limiter.acquire("person:1", "requests", 3);
		assertThat(second.allowed()).isFalse();
		assertThat(second.retryAfterSeconds()).isEqualTo(1);
		assertThat(second.firstRejection()).isFalse();

		this.clock.advance(Duration.ofMillis(500));
		assertThat(this.limiter.acquire("person:1", "requests", 3).allowed()).isTrue();
	}

	@Test
	void principalsAndLimitsAreCountedSeparately() {
		assertThat(this.limiter.acquire("person:1", "requests", 1).allowed()).isTrue();
		assertThat(this.limiter.acquire("person:2", "requests", 1).allowed()).isTrue();
		assertThat(this.limiter.acquire("person:1", "entity-batch", 1).allowed()).isTrue();
		assertThat(this.limiter.acquire("person:1", "requests", 1).allowed()).isFalse();
	}

	@Test
	void endedWindowsAreRemoved() {
		this.limiter.acquire("person:1", "requests", 1);
		this.clock.advance(Duration.ofMinutes(2));
		this.limiter.removeEndedWindows();
		assertThat(this.limiter.acquire("person:1", "requests", 1).firstRejection()).isFalse();
		RateLimiter.Decision rejected = this.limiter.acquire("person:1", "requests", 1);
		assertThat(rejected.allowed()).isFalse();
		assertThat(rejected.firstRejection()).isTrue();
	}

}

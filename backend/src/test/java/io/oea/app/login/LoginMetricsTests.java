package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The login processing metric (design D1 of us-008-login-latency). */
class LoginMetricsTests {

	private final MockClock clock = new MockClock();

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, this.clock);

	private final LoginMetrics metrics = new LoginMetrics(this.registry);

	@Test
	void theRequestToTheIdentityProviderIsNotCounted() {
		LoginMetrics.Measurement measurement = this.metrics.start();
		this.clock.add(Duration.ofMillis(10));
		measurement.pause();
		this.clock.add(Duration.ofMillis(500));
		measurement.resume();
		this.clock.add(Duration.ofMillis(20));
		measurement.record("oidc", "success");
		Timer timer = timer("oidc", "success");
		assertThat(timer.count()).isOne();
		assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(30);
	}

	@Test
	void outcomesFollowTheResultOfTheStep() {
		this.metrics.time("password", () -> "session");
		assertThatExceptionOfType(SecondFactorRequiredException.class).isThrownBy(() -> this.metrics.time("password", () -> {
			throw new SecondFactorRequiredException("transaction", List.of("totp"));
		}));
		assertThatExceptionOfType(InvalidCredentialsException.class).isThrownBy(() -> this.metrics.time("password", () -> {
			throw new InvalidCredentialsException(0);
		}));
		assertThat(timer("password", "success").count()).isOne();
		assertThat(timer("password", "second-factor-required").count()).isOne();
		assertThat(timer("password", "failure").count()).isOne();
	}

	private Timer timer(String method, String outcome) {
		return this.registry.get(LoginMetrics.NAME).tag("method", method).tag("outcome", outcome).timer();
	}

}

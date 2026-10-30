package io.oea.app.login;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Server-side processing time of logins (US-008, REQ-008; design D1 of us-008-login-latency): the
 * timer {@code oea.login.processing} with the tags {@code method} and {@code outcome}. Requests to
 * the identity provider are excluded with {@link Measurement#pause()} and
 * {@link Measurement#resume()}.
 */
@Component
public class LoginMetrics {

	static final String NAME = "oea.login.processing";

	private final MeterRegistry registry;

	public LoginMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	/** Starts a measurement now. */
	public Measurement start() {
		return new Measurement();
	}

	/** Runs a login step and records its time with the outcome of its result or exception. */
	public <T> T time(String method, Supplier<T> step) {
		Measurement measurement = start();
		try {
			T result = step.get();
			measurement.record(method, "success");
			return result;
		}
		catch (RuntimeException ex) {
			measurement.record(method, outcome(ex));
			throw ex;
		}
	}

	/** {@code second-factor-required} if the password step continues, otherwise {@code failure}. */
	static String outcome(RuntimeException ex) {
		return (ex instanceof SecondFactorRequiredException) ? "second-factor-required" : "failure";
	}

	/** One running measurement; not thread-safe, used within one request. */
	public final class Measurement {

		private final long started = monotonic();

		private long excluded;

		private long pausedAt = -1;

		/** Stops counting, before a request to the identity provider. */
		public void pause() {
			if (this.pausedAt < 0) {
				this.pausedAt = monotonic();
			}
		}

		/** Counts again, after the request to the identity provider. */
		public void resume() {
			if (this.pausedAt >= 0) {
				this.excluded += monotonic() - this.pausedAt;
				this.pausedAt = -1;
			}
		}

		public void record(String method, String outcome) {
			resume();
			Timer.builder(NAME)
				.description("Server-side processing time of logins, without requests to the identity provider")
				.tag("method", method)
				.tag("outcome", outcome)
				.publishPercentiles(0.5, 0.95)
				.publishPercentileHistogram()
				.register(LoginMetrics.this.registry)
				.record(monotonic() - this.started - this.excluded, TimeUnit.NANOSECONDS);
		}

	}

	private long monotonic() {
		return this.registry.config().clock().monotonicTime();
	}

}

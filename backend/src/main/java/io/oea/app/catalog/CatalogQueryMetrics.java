package io.oea.app.catalog;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Server-side processing time of catalog and portal queries (design D5 of
 * s9-catalog-query-performance, REQ-071): the timer {@code oea.catalog.query} with the tags
 * {@code channel} ({@code catalog} or {@code portal}) and {@code outcome} ({@code success},
 * {@code rejected} or {@code timeout}).
 */
@Component
public class CatalogQueryMetrics {

	static final String NAME = "oea.catalog.query";

	private final MeterRegistry registry;

	public CatalogQueryMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	/** The current monotonic time in nanoseconds, the start of a measurement. */
	long start() {
		return this.registry.config().clock().monotonicTime();
	}

	/** Records the time since {@code started}. */
	void record(long started, CatalogQueries.Channel channel, String outcome) {
		Timer.builder(NAME)
			.description("Server-side processing time of catalog queries")
			.tag("channel", channel.tag())
			.tag("outcome", outcome)
			.publishPercentiles(0.5, 0.95)
			.publishPercentileHistogram()
			.register(this.registry)
			.record(start() - started, TimeUnit.NANOSECONDS);
	}

}

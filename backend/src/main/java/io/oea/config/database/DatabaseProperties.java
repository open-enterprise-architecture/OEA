package io.oea.config.database;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The emergency time limit of every statement of the application (design D4 of
 * s9-catalog-query-performance, REQ-186): between 1 second and 1 hour, 60 seconds by default.
 */
@ConfigurationProperties("oea.database")
public record DatabaseProperties(@DefaultValue("60s") Duration statementTimeout) {

	static final Duration MINIMUM = Duration.ofSeconds(1);

	static final Duration MAXIMUM = Duration.ofHours(1);

	public DatabaseProperties {
		if (statementTimeout.compareTo(MINIMUM) < 0 || statementTimeout.compareTo(MAXIMUM) > 0) {
			throw new IllegalArgumentException("oea.database.statement-timeout must be between 1 second and 1 hour");
		}
	}

}

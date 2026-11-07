package io.oea.app.catalog;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The time limit of a catalog or portal query (design D4 of s9-catalog-query-performance,
 * REQ-186): at least 1 second, 10 seconds by default, and at most the statement time limit
 * {@code oea.database.statement-timeout}, which the database configuration checks.
 */
@ConfigurationProperties("oea.catalog")
public record CatalogQueryProperties(@DefaultValue("10s") Duration queryTimeout) {

	public CatalogQueryProperties {
		if (queryTimeout.compareTo(Duration.ofSeconds(1)) < 0) {
			throw new IllegalArgumentException("oea.catalog.query-timeout must be at least 1 second");
		}
	}

}

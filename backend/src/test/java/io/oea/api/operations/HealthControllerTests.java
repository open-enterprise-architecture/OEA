package io.oea.api.operations;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.config.ApiPathConfiguration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(HealthController.class)
@Import({ ApiPathConfiguration.class, HealthControllerTests.Indicators.class })
class HealthControllerTests {

	private static final AtomicReference<Status> databaseStatus = new AtomicReference<>(Status.UP);

	@Autowired
	private MockMvcTester mvc;

	@Test
	void returnsUpWithStatusOnly() {
		databaseStatus.set(Status.UP);
		assertThat(this.mvc.get().uri("/api/v1/health")).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isStrictlyEqualTo("{\"status\":\"UP\"}");
	}

	@Test
	void returnsServiceUnavailableWhenAnIndicatorIsDown() {
		databaseStatus.set(Status.DOWN);
		assertThat(this.mvc.get().uri("/api/v1/health")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.bodyJson()
			.isStrictlyEqualTo("{\"status\":\"DOWN\"}");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Indicators {

		@Bean
		HealthContributors healthContributors() {
			HealthIndicator ping = () -> Health.up().build();
			HealthIndicator db = () -> Health.status(databaseStatus.get()).withDetail("secret", "hidden").build();
			return CompositeHealthContributor.fromMap(Map.of("ping", ping, "nested", CompositeHealthContributor.fromMap(Map.of("db", db))));
		}

	}

}

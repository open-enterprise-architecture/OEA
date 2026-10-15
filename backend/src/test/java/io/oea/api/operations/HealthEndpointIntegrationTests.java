package io.oea.api.operations;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthEndpointIntegrationTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void healthIsUpAgainstRealDatabase() {
		assertThat(this.mvc.get().uri("/api/v1/health")).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("{\"status\":\"UP\"}");
	}

	@Test
	void actuatorEndpointsAreNotExposed() {
		// without a session every non-public path is rejected first (ADR-032 §8 L1, REQ-163)
		assertThat(this.mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get().uri("/actuator/health")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get().uri("/actuator")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

}

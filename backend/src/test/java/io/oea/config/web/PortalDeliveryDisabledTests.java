package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * "Without a portal directory" of platform/portal-delivery (s9-portal-catalogs D3): the backend
 * serves no portal, as in the development environment, and the API works unchanged.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PortalDeliveryDisabledTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		String tokens = Files.createTempDirectory("oea-portal-off").resolve("setup-token").toString();
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> tokens);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Autowired
	private MockMvcTester mvc;

	@Test
	void withoutAPortalDirectory() {
		assertThat(this.mvc.get().uri("/").exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get().uri("/portal").exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get().uri("/api/v1/health").exchange()).hasStatus(HttpStatus.OK);
	}

}

package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Spec lineage/lineage-query, requirement "Access to the lineage": the query counts against the per-principal rate
 * limit (us-065-lineage-model-and-query D4). The operator of this context lowered the limit to 5 per minute: the
 * fifth request is answered, the sixth gets 429. Own database, because the context has its own settings.
 */
@SpringBootTest(properties = "oea.web.rate-limit.requests-per-minute=5")
@AutoConfigureMockMvc
class LineageRateLimitTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", LineageRateLimitTests::tokenFile);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static String tokenFile() {
		try {
			return Files.createTempDirectory("oea-lineage-rate").resolve("setup-token").toString();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@Autowired
	private JdbcTemplate jdbc;

	private MvcTestResult query(TestSession session) {
		return this.mvc.post()
			.uri("/api/v1/views/lineage/query")
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content("{\"startEntityId\": 1}")
			.exchange();
	}

	@Test
	void theFifthQueryIsAnsweredAndTheSixthGets429() {
		TestSession viewer = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("viewer");
		for (int i = 0; i < 5; i++) {
			// no entity 1 exists: the answer is 404, which the limit counts like any other
			assertThat(query(viewer)).hasStatus(HttpStatus.NOT_FOUND);
		}
		MvcTestResult rejected = query(viewer);
		assertThat(rejected).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:too-many-requests");
	}

}

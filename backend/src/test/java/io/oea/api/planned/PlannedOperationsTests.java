package io.oea.api.planned;

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
import java.nio.file.Files;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec platform/api-contract, requirement "Answer of planned operations" (s2b-ci-and-contract
 * D2): {@code createEntity} and {@code getExplorerView} are planned and have no own controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlannedOperationsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> TOKEN_FILE);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	private TestPrincipals principals;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
	}

	@Test
	void authorizedCallOfAPlannedOperation() {
		TestSession architect = this.principals.personWithIdpRole("enterprise-architect");
		int before = count();
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/entities")
			.cookie(new Cookie(SessionCookie.NAME, architect.token()))
			.header(CsrfOriginFilter.HEADER, architect.csrfToken())
			.contentType("application/json")
			.content("{\"name\": \"Planned\"}")
			.exchange();
		assertThat(result).hasStatus(HttpStatus.NOT_IMPLEMENTED);
		assertThat(result.getResponse().getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
		assertThat(JsonMapper.shared().readTree(result.getResponse().getContentAsByteArray()).path("type").asString())
			.isEqualTo("urn:oea:problem:not-implemented");
		assertThat(count()).isEqualTo(before);
	}

	@Test
	void plannedOperationWithoutSession() {
		assertThat(this.mvc.get().uri("/api/v1/views/explorer").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void plannedOperationWithoutPermission() {
		TestSession portalReader = this.principals.personWithIdpRole("portal-reader");
		assertThat(this.mvc.get()
			.uri("/api/v1/views/explorer")
			.cookie(new Cookie(SessionCookie.NAME, portalReader.token()))
			.exchange()).hasStatus(HttpStatus.FORBIDDEN);
	}

	private int count() {
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM entities", Integer.class);
		return (count != null) ? count : 0;
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-planned").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

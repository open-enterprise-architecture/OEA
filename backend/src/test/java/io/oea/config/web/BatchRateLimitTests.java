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
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Spec platform/request-limits: Rate limit of batch writes (REQ-186, issue #74), default limits. */
@SpringBootTest
@AutoConfigureMockMvc
class BatchRateLimitTests {

	// own database: the IdP providers of the test principals must not reach the shared database
	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", BatchRateLimitTests::tokenFile);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static String tokenFile() {
		try {
			return Files.createTempDirectory("oea-test").resolve("setup-token").toString();
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

	@Test
	void eleventhBatchWithinAMinuteIsRejectedWithoutWriting() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		TestSession architect = principals.personWithIdpRole("enterprise-architect");
		if (this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Integer.class) == 0) {
			TestSession metamodelAdmin = principals.personWithIdpRole("metamodel-admin");
			assertThat(this.mvc.post()
				.uri("/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import")
				.cookie(new Cookie(SessionCookie.NAME, metamodelAdmin.token()))
				.header(CsrfOriginFilter.HEADER, metamodelAdmin.csrfToken())
				.exchange()).hasStatus(HttpStatus.OK);
		}
		UUID component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
		String marker = "Rate limited " + UUID.randomUUID();
		for (int i = 1; i <= 10; i++) {
			assertThat(batch(architect, marker + " " + i, component)).hasStatus(HttpStatus.OK);
		}
		MvcTestResult rejected = batch(architect, marker + " 11", component);
		assertThat(rejected).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:too-many-requests");
		assertThat(rejected.getResponse().getHeader("Retry-After")).isNotBlank();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = ?", Integer.class,
				marker + " 11")).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name LIKE ?", Integer.class,
				marker + " %")).isEqualTo(10);
		assertThat(this.mvc.get().uri("/api/v1/catalogs").cookie(new Cookie(SessionCookie.NAME, architect.token())))
			.as("other operations stay within the request limit")
			.hasStatus(HttpStatus.OK);
	}

	private MvcTestResult batch(TestSession session, String name, UUID metatype) {
		return this.mvc.post()
			.uri("/api/v1/entities/batch")
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"entities": [{"name": "%s", "metatypeId": "%s"}]}""".formatted(name, metatype))
			.exchange();
	}

}

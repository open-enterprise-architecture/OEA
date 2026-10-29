package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.oea.app.security.Sessions;
import io.oea.core.service.PasswordHasher;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.logging.LogCapture;
import io.oea.testsupport.time.MutableClock;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec identity/person-password "Password login of persons is switched on explicitly" (US-011
 * AC2; design D1 of us-011-local-password): an instance with the default configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PersonPasswordSwitchedOffTests.ClockConfiguration.class)
class PersonPasswordSwitchedOffTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-person-passwords-off").resolve("setup-token");
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", tokenFile::toString);
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

	@Autowired
	private MutableClock clock;

	@MockitoSpyBean
	private PasswordHasher hasher;

	private TestPrincipals principals;

	private TestSession admin;

	@BeforeEach
	void administrator() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		// the setup phase has ended (ADR-042); the administrator holds role:assign
		this.jdbc.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'identity-binding') ON CONFLICT (id) DO NOTHING""");
		this.admin = this.principals.personWithIdpRole("security-admin");
	}

	@Test
	void passwordLoginOfPersonsIsOffByDefault() {
		Long anna = this.jdbc.queryForObject(
				"INSERT INTO persons (name, email, status) VALUES ('Anna', 'anna@example.org', 'active') RETURNING id",
				Long.class);
		UUID credential = UUID.randomUUID();
		this.jdbc.update("INSERT INTO local_credentials (id, person_id, type) VALUES (?, ?, 'password')", credential, anna);
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credential,
				this.hasher.hash("chosen-password-456"));
		MvcTestResult login = this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.contentType("application/json")
			.content("{\"loginName\":\"anna@example.org\",\"password\":\"chosen-password-456\"}")
			.exchange();
		assertThat(login).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(login).path("type").asString()).isEqualTo("urn:oea:problem:invalid-credentials");
		Long ben = this.jdbc.queryForObject(
				"INSERT INTO persons (name, email) VALUES ('Ben', 'ben@example.org') RETURNING id", Long.class);
		MvcTestResult set = post("/api/v1/admin/persons/" + ben + "/password", this.admin,
				"{\"password\":\"initial-password-123\"}");
		assertThat(set).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(set).path("type").asString()).isEqualTo("urn:oea:problem:conflict");
	}

	private MvcTestResult post(String uri, TestSession session, String body) {
		return this.mvc.post()
			.uri(uri)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body)
			.exchange();
	}

	private int count(String sql) {
		Integer count = this.jdbc.queryForObject(sql, Integer.class);
		return (count != null) ? count : 0;
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}

	}

}

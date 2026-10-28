package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.time.MutableClock;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Spec platform/request-limits: per-principal rate limits (REQ-186 AC1, issue #74). The
 * operator of this context lowered the request limit to 5 per minute; the batch limit keeps its
 * default of 10. Own database, because the context has its own settings.
 */
@SpringBootTest(properties = "oea.web.rate-limit.requests-per-minute=5")
@AutoConfigureMockMvc
@Import(RateLimitTests.ClockConfiguration.class)
class RateLimitTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", RateLimitTests::tokenFile);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static String tokenFile() {
		try {
			return Files.createTempDirectory("oea-rate").resolve("setup-token").toString();
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

	@Autowired
	private MutableClock clock;

	private TestPrincipals principals;

	@BeforeEach
	void principals() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
	}

	private MvcTestResult listCatalogs(TestSession session) {
		return this.mvc.get().uri("/api/v1/catalogs").cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
	}

	private int throttledEvents(TestSession session) {
		return this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events e JOIN sessions s ON s.person_id = e.actor_person_id
				WHERE e.event_type = 'auth.throttled' AND e.details ->> 'scope' = 'principal' AND s.id = ?""",
				Integer.class, session.sessionId());
	}

	@Test
	void principalAboveTheLimitGets429WithRetryAfter() {
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		for (int i = 0; i < 5; i++) {
			assertThat(listCatalogs(viewer)).hasStatus(HttpStatus.OK);
		}
		MvcTestResult rejected = listCatalogs(viewer);
		assertThat(rejected).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:too-many-requests");
		assertThat(rejected).headers().hasValue("Retry-After", "60");
	}

	@Test
	void otherPrincipalsAreUnaffected() {
		TestSession architect = this.principals.personWithIdpRole("enterprise-architect");
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		for (int i = 0; i < 6; i++) {
			listCatalogs(architect);
		}
		assertThat(listCatalogs(architect)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(listCatalogs(viewer)).hasStatus(HttpStatus.OK);
	}

	@Test
	void requestsAreAcceptedAgainAfterTheWaitingTime() {
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		for (int i = 0; i < 5; i++) {
			listCatalogs(viewer);
		}
		this.clock.advance(Duration.ofSeconds(15));
		MvcTestResult rejected = listCatalogs(viewer);
		assertThat(rejected).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		long retryAfter = Long.parseLong(rejected.getResponse().getHeader("Retry-After"));
		assertThat(retryAfter).isEqualTo(45);
		this.clock.advance(Duration.ofSeconds(retryAfter));
		assertThat(listCatalogs(viewer)).hasStatus(HttpStatus.OK);
	}

	@Test
	void throttledPrincipalIsAuditedOncePerWindow() {
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		for (int i = 0; i < 12; i++) {
			listCatalogs(viewer);
		}
		assertThat(throttledEvents(viewer)).isEqualTo(1);
	}

	@Test
	void rejectedRequestIsNotAuditedAsDenial() {
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		for (int i = 0; i < 7; i++) {
			listCatalogs(viewer);
		}
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events e JOIN sessions s ON s.person_id = e.actor_person_id
				WHERE e.event_type = 'authz.denied' AND s.id = ?""", Integer.class, viewer.sessionId())).isZero();
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

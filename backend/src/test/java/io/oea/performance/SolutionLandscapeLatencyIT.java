package io.oea.performance;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-039 Definition of Done, realigned in us-039-solution-baseline: a page of the baseline with a
 * search term over 10,000 entities answers within 500 ms (median of 5 requests after a warm-up).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SolutionLandscapeLatencyIT {

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

	private TestSession michael;

	private long michaelId;

	private String solution;

	private UUID application;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = this.principals.personWithIdpRole("solution-architect");
		this.michaelId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.michael.sessionId());
		// Every test starts from an empty landscape.
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE deleted_at IS NULL",
				this.michaelId);
		this.application = metatype("Application", false);
		this.solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), ?, ?, ?)
				RETURNING id::text""", String.class, "Baseline " + UUID.randomUUID(), this.michaelId,
				this.michaelId);
	}

	// Performance (US-039 Definition of Done, realigned)

	@Test
	void pageOverTenThousandEntitiesWithin500Milliseconds() {
		this.jdbc.update("""
				INSERT INTO entities (metatype_id, name, created_by)
				SELECT ?, 'System ' || g, ? FROM generate_series(1, 10000) g""", this.application, this.michaelId);
		String query = "&q=system%2099&pageSize=50";
		assertThat(ok(landscape(query)).path("page").path("totalElements").asInt()).isEqualTo(111);
		List<Long> millis = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			long started = System.nanoTime();
			ok(landscape(query));
			millis.add((System.nanoTime() - started) / 1_000_000);
		}
		Collections.sort(millis);
		assertThat(millis.get(2)).as("median of %s ms", millis).isLessThan(500);
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?) RETURNING id""", UUID.class,
				name + " " + UUID.randomUUID().toString().substring(0, 8), connection);
	}

	private String path(String query) {
		return "/api/v1/solutions/" + this.solution + "/landscape?" + (query.isEmpty() ? "" : query.substring(1));
	}

	private MvcTestResult landscape(String query) {
		return call(path(query), this.michael);
	}

	private MvcTestResult call(String path, TestSession session) {
		return this.mvc.get()
			.uri(UriComponentsBuilder.fromUriString(path).build(true).toUri())
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return JsonMapper.shared().readTree(content(result));
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-solution-landscape-latency").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

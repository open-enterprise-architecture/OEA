package io.oea.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec catalog/catalog-query, requirement "Query time limit" (design D4 of
 * s9-catalog-query-performance), with a limit of 1 second. A slow query is a query that waits
 * for a lock on {@code entities}, which another connection holds; the wait counts towards the
 * statement time limit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogQueryTimeoutTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> TOKEN_FILE);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
		registry.add("oea.catalog.query-timeout", () -> "1s");
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
	private DataSource dataSource;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@Autowired
	private MeterRegistry meters;

	private TestSession architect;

	private String catalog;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = principals.personWithIdpRole("enterprise-architect");
		long architectId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.architect.sessionId());
		UUID type = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?)
				RETURNING id""", UUID.class, "Timeout Type " + UUID.randomUUID().toString().substring(0, 8));
		this.jdbc.update("INSERT INTO entities (metatype_id, name, created_by) VALUES (?, 'CRM System', ?)", type,
				architectId);
		MvcTestResult created = this.mvc.post()
			.uri("/api/v1/catalogs")
			.cookie(new Cookie(SessionCookie.NAME, this.architect.token()))
			.header(CsrfOriginFilter.HEADER, this.architect.csrfToken())
			.contentType("application/json")
			.content(JsonMapper.shared()
				.createObjectNode()
				.put("name", "Catalog " + UUID.randomUUID())
				.put("primaryMetatypeId", type.toString())
				.put("scope", "instance")
				.toString())
			.exchange();
		assertThat(created).hasStatus(HttpStatus.CREATED);
		this.catalog = body(created).path("id").asString();
	}

	// Scenario: Query above the limit

	@Test
	void queryAboveTheLimit() throws SQLException {
		int audited = audited();
		double timeouts = count("timeout");
		long started = System.nanoTime();
		MvcTestResult result;
		try (Connection lock = this.dataSource.getConnection()) {
			lock.setAutoCommit(false);
			try (Statement statement = lock.createStatement()) {
				statement.execute("LOCK TABLE entities IN ACCESS EXCLUSIVE MODE");
			}
			result = query();
			lock.rollback();
		}
		long millis = (System.nanoTime() - started) / 1_000_000;
		assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:query-timeout");
		assertThat(problem.path("title").asString()).isEqualTo("Query took too long");
		assertThat(problem.path("detail").asString()).doesNotContain("SQL", "statement", "entities");
		assertThat(millis).isBetween(1000L, 9000L);
		assertThat(audited()).isEqualTo(audited + 1);
		Map<String, Object> event = this.jdbc.queryForMap("""
				SELECT event_category, severity, outcome, target_type, details::text AS details FROM audit.events
				WHERE event_type = 'catalog.query-timeout' AND target_id = ? ORDER BY id DESC LIMIT 1""", this.catalog);
		assertThat(event).containsEntry("event_category", "security")
			.containsEntry("severity", "warning")
			.containsEntry("outcome", "failure")
			.containsEntry("target_type", "catalog");
		JsonNode details = JsonMapper.shared().readTree((String) event.get("details"));
		assertThat(details.path("channel").asString()).isEqualTo("catalog");
		assertThat(details.path("limitMillis").asLong()).isEqualTo(1000L);
		assertThat(count("timeout")).isEqualTo(timeouts + 1);
		// the limit ends with the transaction: the next query is answered as before
		assertThat(query()).hasStatus(HttpStatus.OK);
	}

	// Scenario: Query within the limit

	@Test
	void queryWithinTheLimit() {
		int audited = audited();
		MvcTestResult result = query();
		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(body(result).path("rows").get(0).path("values").path("name").asString()).isEqualTo("CRM System");
		assertThat(audited()).isEqualTo(audited);
		// the limit of the query does not stay on the pooled connection
		assertThat(this.jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("1min");
	}

	private MvcTestResult query() {
		return this.mvc.get()
			.uri("/api/v1/catalogs/" + this.catalog + "/query")
			.cookie(new Cookie(SessionCookie.NAME, this.architect.token()))
			.exchange();
	}

	private int audited() {
		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'catalog.query-timeout' AND target_id = ?",
				Integer.class, this.catalog);
		return (count != null) ? count : 0;
	}

	private double count(String outcome) {
		Timer timer = this.meters.find("oea.catalog.query").tags("channel", "catalog", "outcome", outcome).timer();
		return (timer != null) ? timer.count() : 0;
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-catalog-timeout").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

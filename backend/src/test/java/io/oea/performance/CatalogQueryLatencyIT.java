package io.oea.performance;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec catalog/catalog-query-latency, REQ-071 AC1–AC3 (design D8 of
 * s9-catalog-query-performance): the 95th percentile of the server-side processing time of
 * catalog queries on 10,000 applications stays below the targets. Every query is measured by the
 * difference of the timer {@code oea.catalog.query} before and after it, so the warm-up and the
 * HTTP layer do not count.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogQueryLatencyIT {

	private static final Logger LOG = LoggerFactory.getLogger(CatalogQueryLatencyIT.class);

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CATALOGS = "/api/v1/catalogs";

	private static final int APPLICATIONS = 10_000;

	private static final int TARGETS = 2_000;

	private static final int CONNECTIONS_PER_JOIN = 5;

	private static final int WARM_UP = 5;

	private static final int MEASURED = 30;

	private static Seed seed;

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

	@Autowired
	private MeterRegistry meters;

	private TestSession architect;

	@BeforeEach
	void instance() throws SQLException {
		this.architect = new TestPrincipals(this.sessions, this.accounts, this.jdbc)
			.personWithIdpRole("enterprise-architect");
		if (seed == null) {
			seed = seed(this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
					this.architect.sessionId()));
			seed = seed.withCatalogs(catalog(false), catalog(true));
		}
	}

	// Scenario: Without joins

	@Test
	void withoutJoins() {
		assertBelowTarget("without joins", 200, measure(() -> query(seed.plain, "")));
	}

	// Scenario: Three joins

	@Test
	void threeJoins() {
		assertBelowTarget("three joins", 500, measure(() -> {
			JsonNode result = query(seed.joined, "");
			assertThat(result.path("joins")).hasSize(3);
			assertThat(result.path("rows").get(0).path("joinResults").properties()).hasSize(3);
		}));
	}

	// Scenario: Filter and three joins

	@Test
	void filterAndThreeJoins() {
		String filters = "filter=" + seed.status + ":eq:active&filter=" + seed.vendor + ":contains:soft";
		assertBelowTarget("filter and three joins", 600, measure(() -> {
			JsonNode result = query(seed.joined, filters);
			assertThat(result.path("page").path("totalElements").asInt()).isPositive().isLessThan(APPLICATIONS);
		}));
	}

	// Scenario: Deep page through the cursor

	@Test
	void deepPageThroughTheCursorSortedByName() {
		deepPage("sort=name:asc", "cursor sorted by name");
	}

	@Test
	void deepPageThroughTheCursorSortedByStatus() {
		deepPage("sort=" + seed.status + ":desc", "cursor sorted by status");
	}

	private void deepPage(String sort, String scenario) {
		String cursor = query(seed.plain, sort + "&page=150").path("nextCursor").asString();
		assertThat(cursor).isNotEmpty();
		String parameters = sort + "&page=151&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
		assertBelowTarget(scenario, 200, measure(() -> {
			JsonNode result = query(seed.plain, parameters);
			assertThat(result.path("rows")).hasSize(50);
			assertThat(result.path("page").path("page").asInt()).isEqualTo(151);
		}));
	}

	/** Runs the warm-up and the measured queries; returns the processing time of each measured query. */
	private List<Double> measure(Runnable query) {
		for (int i = 0; i < WARM_UP; i++) {
			query.run();
		}
		List<Double> times = new ArrayList<>();
		for (int i = 0; i < MEASURED; i++) {
			double before = total();
			query.run();
			times.add(total() - before);
		}
		return times;
	}

	private static void assertBelowTarget(String scenario, double targetMillis, List<Double> times) {
		List<Double> sorted = new ArrayList<>(times);
		Collections.sort(sorted);
		double median = sorted.get(sorted.size() / 2);
		double p95 = sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
		LOG.info("Catalog query latency {}: median {} ms, p95 {} ms, max {} ms over {} queries (target {} ms)",
				scenario, Math.round(median), Math.round(p95), Math.round(sorted.getLast()), sorted.size(),
				Math.round(targetMillis));
		assertThat(p95).as("p95 of %s in ms", scenario).isLessThan(targetMillis);
	}

	private double total() {
		Supplier<Timer> timer = () -> this.meters.find("oea.catalog.query")
			.tags("channel", "catalog", "outcome", "success")
			.timer();
		return (timer.get() != null) ? timer.get().totalTime(TimeUnit.MILLISECONDS) : 0;
	}

	private JsonNode query(String catalog, String parameters) {
		MvcTestResult result = call(HttpMethod.GET,
				CATALOGS + "/" + catalog + "/query" + (parameters.isEmpty() ? "" : "?" + parameters), null);
		assertThat(result).hasStatus(HttpStatus.OK);
		return body(result);
	}

	/** A catalog of the applications with the columns name, status and vendor; with the three joins. */
	private String catalog(boolean joins) {
		MvcTestResult created = call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Latency " + (joins ? "Joined " : "Plain ") + UUID.randomUUID())
			.put("primaryMetatypeId", seed.application.toString())
			.put("scope", "instance"));
		assertThat(created).hasStatus(HttpStatus.CREATED);
		String catalog = body(created).path("id").asString();
		ObjectNode columns = JsonMapper.shared().createObjectNode();
		columns.putArray("columns")
			.add(JsonMapper.shared().createObjectNode().put("field", "name"))
			.add(JsonMapper.shared().createObjectNode().put("propertyDefId", seed.statusId.toString()))
			.add(JsonMapper.shared().createObjectNode().put("propertyDefId", seed.vendorId.toString()));
		assertThat(call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/columns", columns)).hasStatus(HttpStatus.OK);
		if (joins) {
			for (int k = 0; k < 3; k++) {
				ObjectNode join = JsonMapper.shared()
					.createObjectNode()
					.put("name", "Join " + k)
					.put("connectionMetatypeId", seed.connections.get(k).toString())
					.put("joinDirection", "outbound")
					.put("targetMetatypeId", seed.targets.get(k).toString());
				join.putArray("targetColumns").add(JsonMapper.shared().createObjectNode().put("field", "name"));
				assertThat(call(HttpMethod.POST, CATALOGS + "/" + catalog + "/joins", join))
					.hasStatus(HttpStatus.CREATED);
			}
		}
		return catalog;
	}

	/**
	 * Seeds on the superuser connection: 10,000 applications with a status, a vendor and a
	 * description, 3 target metatypes of 2,000 entities each and per application and join 5
	 * connections, then {@code ANALYZE}.
	 */
	private static Seed seed(long personId) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement sql = su.createStatement()) {
			UUID application = uuid(sql, metatype("Application", false));
			UUID status = uuid(sql, "INSERT INTO property_definitions (name, sql_column_name, data_type) "
					+ "VALUES ('Status', 'status', 'enum') RETURNING id");
			UUID vendor = uuid(sql, "INSERT INTO property_definitions (name, sql_column_name, data_type) "
					+ "VALUES ('Vendor', 'vendor', 'string') RETURNING id");
			sql.execute("INSERT INTO property_enum_values (property_def_id, value, sort_order) VALUES ('%1$s', 'planned', 0), "
				.formatted(status) + "('%1$s', 'active', 1), ('%1$s', 'retired', 2)".formatted(status));
			for (UUID property : List.of(status, vendor)) {
				sql.execute("""
						INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
						VALUES ('%s', '%s', 'optional', DATE '2026-01-01', 'organization')""".formatted(application, property));
			}
			sql.execute("""
					INSERT INTO entities (metatype_id, name, description, created_by)
					SELECT '%s', 'Application ' || lpad(g::text, 5, '0'), 'Description of application ' || g, %d
					FROM generate_series(1, %d) AS g""".formatted(application, personId, APPLICATIONS));
			sql.execute("""
					INSERT INTO entity_property_values (entity_id, property_def_id, value_enum)
					SELECT id, '%s', (ARRAY['planned', 'active', 'retired'])[1 + id %% 3] FROM entities
					WHERE metatype_id = '%s'""".formatted(status, application));
			sql.execute("""
					INSERT INTO entity_property_values (entity_id, property_def_id, value_text)
					SELECT id, '%s', (ARRAY['Microsoft', 'SAP', 'Oracle', 'Salesforce', 'Acme Soft'])[1 + id %% 5]
					FROM entities WHERE metatype_id = '%s'""".formatted(vendor, application));
			List<UUID> targets = new ArrayList<>();
			List<UUID> connections = new ArrayList<>();
			for (int k = 0; k < 3; k++) {
				UUID target = uuid(sql, metatype("Target " + k, false));
				UUID connection = uuid(sql, metatype("Connection " + k, true));
				targets.add(target);
				connections.add(connection);
				sql.execute("""
						INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
						VALUES ('%1$s', 'source', '%2$s'), ('%1$s', 'target', '%3$s')"""
					.formatted(connection, application, target));
				sql.execute("""
						INSERT INTO entities (metatype_id, name, created_by)
						SELECT '%s', 'Target %d ' || lpad(g::text, 4, '0'), %d FROM generate_series(1, %d) AS g"""
					.formatted(target, k, personId, TARGETS));
				sql.execute("DROP TABLE IF EXISTS seed_pairs");
				sql.execute("""
						CREATE TEMP TABLE seed_pairs AS
						SELECT nextval(pg_get_serial_sequence('entities', 'id')) AS cid, a.id AS src, t.id AS tgt
						FROM (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM entities WHERE metatype_id = '%s') a
						CROSS JOIN generate_series(0, %d) AS i
						JOIN (SELECT id, row_number() OVER (ORDER BY id) - 1 AS tn FROM entities WHERE metatype_id = '%s') t
						  ON t.tn = (a.rn * 7 + i * 397 + %d) %% %d"""
					.formatted(application, CONNECTIONS_PER_JOIN - 1, target, k * 101, TARGETS));
				sql.execute("""
						INSERT INTO entities (id, metatype_id, name, created_by)
						SELECT cid, '%s', 'Connection ' || cid, %d FROM seed_pairs""".formatted(connection, personId));
				sql.execute("""
						INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id)
						SELECT cid, src, tgt FROM seed_pairs""");
			}
			sql.execute("ANALYZE");
			try (ResultSet rs = sql.executeQuery("SELECT count(*) FROM entity_connections")) {
				rs.next();
				assertThat(rs.getLong(1)).isEqualTo(3L * APPLICATIONS * CONNECTIONS_PER_JOIN);
			}
			return new Seed(application, status, vendor, "status", "vendor", targets, connections, null, null);
		}
	}

	private record Seed(UUID application, UUID statusId, UUID vendorId, String status, String vendor,
			List<UUID> targets, List<UUID> connections, String plain, String joined) {

		Seed withCatalogs(String plain, String joined) {
			return new Seed(this.application, this.statusId, this.vendorId, this.status, this.vendor, this.targets,
					this.connections, plain, joined);
		}

	}

	private static String metatype(String name, boolean connection) {
		return """
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', 'Latency %s', %s) RETURNING id""".formatted(name, connection);
	}

	private static UUID uuid(Statement sql, String insert) throws SQLException {
		try (ResultSet rs = sql.executeQuery(insert)) {
			rs.next();
			return rs.getObject(1, UUID.class);
		}
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, this.architect.token()))
			.header(CsrfOriginFilter.HEADER, this.architect.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
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
			return Files.createTempDirectory("oea-catalog-latency").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

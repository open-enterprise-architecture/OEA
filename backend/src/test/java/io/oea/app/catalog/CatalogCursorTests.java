package io.oea.app.catalog;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec catalog/catalog-query, requirement "Next page through a cursor" (design D1–D3 of
 * s9-catalog-query-performance). Each test queries its own metatypes, so that the rows of one
 * test do not appear in another.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogCursorTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CATALOGS = "/api/v1/catalogs";

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

	private TestSession architect;

	private long architectId;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = principals.personWithIdpRole("enterprise-architect");
		this.architectId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.architect.sessionId());
	}

	// Scenario: Next page by cursor

	@Test
	void nextPageByCursor() {
		UUID type = metatype("Application", false);
		for (int i = 1; i <= 60; i++) {
			entity(type, "Application %02d".formatted(i));
		}
		String catalog = catalog(type);
		JsonNode first = ok(query(catalog, "pageSize=20"));
		String cursor = first.path("nextCursor").asString();
		assertThat(cursor).isNotEmpty();
		JsonNode byCursor = ok(query(catalog, "pageSize=20&page=1&cursor=" + encode(cursor)));
		JsonNode byPage = ok(query(catalog, "pageSize=20&page=1"));
		assertThat(names(byCursor)).hasSize(20).first().isEqualTo("Application 21");
		assertThat(names(byCursor)).last().isEqualTo("Application 40");
		assertThat(names(byCursor)).isEqualTo(names(byPage));
		assertThat(byCursor.path("page").path("totalElements").asInt()).isEqualTo(60);
		assertThat(byCursor.path("page").path("page").asInt()).isEqualTo(1);
		assertThat(byCursor.path("nextCursor").asString()).isEqualTo(byPage.path("nextCursor").asString());
	}

	// Scenario: Last page; boundaries of nextCursor

	@Test
	void lastPageHasNoCursor() {
		UUID type = metatype("Application", false);
		for (int i = 1; i <= 41; i++) {
			entity(type, "Application %02d".formatted(i));
		}
		String catalog = catalog(type);
		JsonNode second = ok(query(catalog, "pageSize=20&page=1"));
		assertThat(second.path("nextCursor").isString()).isTrue();
		JsonNode third = ok(query(catalog, "pageSize=20&page=2&cursor=" + encode(second.path("nextCursor").asString())));
		assertThat(names(third)).containsExactly("Application 41");
		assertThat(third.path("nextCursor").isNull()).isTrue();
		this.jdbc.update("UPDATE entities SET deleted_at = now() WHERE metatype_id = ? AND name = 'Application 41'",
				type);
		JsonNode exactlyTwoPages = ok(query(catalog, "pageSize=20&page=1"));
		assertThat(exactlyTwoPages.path("rows")).hasSize(20);
		assertThat(exactlyTwoPages.path("nextCursor").isNull()).isTrue();
		assertThat(ok(query(catalog, "pageSize=20&page=0")).path("nextCursor").isString()).isTrue();
		JsonNode empty = ok(query(catalog(metatype("Empty", false)), ""));
		assertThat(empty.path("nextCursor").isNull()).isTrue();
	}

	@Test
	void sortValuesTooLongForACursorLeaveThePageNumber() {
		UUID type = metatype("Application", false);
		for (int i = 1; i <= 5; i++) {
			int app = entity(type, "Application " + i);
			this.jdbc.update("UPDATE entities SET description = ? WHERE id = ?", ((i <= 2) ? "a" : "b").repeat(1900), app);
		}
		String catalog = catalog(type);
		ok(columns(catalog, field("name"), field("description")));
		JsonNode first = ok(query(catalog, "pageSize=2&sort=description:asc"));
		assertThat(first.path("nextCursor").isNull()).isTrue();
		assertThat(first.path("page").path("totalPages").asInt()).isEqualTo(3);
		assertThat(names(ok(query(catalog, "pageSize=2&page=1&sort=description:asc"))))
			.containsExactly("Application 3", "Application 4");
		assertThat(ok(query(catalog, "pageSize=2&sort=name:asc")).path("nextCursor").isString()).isTrue();
	}

	// Scenario: Cursor with ties, empty values and rows mode

	@ParameterizedTest
	@ValueSource(strings = { "", "sort=name:desc", "sort={status}:asc", "sort={status}:desc", "sort={users}:asc",
			"sort={users}:desc", "sort=createdAt:asc", "sort=description:desc" })
	void everyPageByCursorEqualsThePagesByNumber(String sort) {
		Fixture fixture = fixture();
		walk(fixture.catalog, fixture.expand(sort));
	}

	@Test
	void everyPageByCursorInRowsModeWithFilter() {
		Fixture fixture = fixture();
		String rows = "joinMode[" + fixture.interfaces + "]=rows&joinMode[" + fixture.hosts + "]=rows";
		walk(fixture.catalog, rows);
		walk(fixture.catalog, rows + "&" + fixture.expand("sort={status}:desc&filter={status}:neq:retired"));
		walk(fixture.catalog, rows + "&sort=" + fixture.interfaces + ".name:desc");
		walk(fixture.catalog, fixture.expand("filter={users}:gte:3&filterMode=or&filter={status}:isNull"));
	}

	// Scenario: Cursor of another query

	@Test
	void cursorOfAnotherQuery() {
		Fixture fixture = fixture();
		String cursor = ok(query(fixture.catalog, "pageSize=4&sort=name:asc")).path("nextCursor").asString();
		String sent = "&cursor=" + encode(cursor);
		assertThat(query(fixture.catalog, "pageSize=4&page=1&sort=name:asc" + sent)).hasStatus(HttpStatus.OK);
		assertInvalid(query(fixture.catalog, fixture.expand("pageSize=4&page=1&sort={status}:asc") + sent));
		assertInvalid(query(fixture.catalog, "pageSize=4&page=1&sort=name:desc" + sent));
		assertInvalid(query(fixture.catalog, "pageSize=4&page=1&sort=name:asc&filter=name:contains:a" + sent));
		assertInvalid(query(fixture.catalog, "pageSize=5&page=1&sort=name:asc" + sent));
		assertInvalid(query(fixture.catalog, "pageSize=4&page=2&sort=name:asc" + sent));
		assertInvalid(query(fixture.catalog, "pageSize=4&sort=name:asc" + sent));
		assertInvalid(query(fixture.catalog,
				"pageSize=4&page=1&sort=name:asc&joinMode[" + fixture.interfaces + "]=rows" + sent));
		assertInvalid(query(catalog(fixture.type), "pageSize=4&page=1&sort=name:asc" + sent));
		ok(columns(fixture.catalog, field("name"), property(fixture.status)));
		assertInvalid(query(fixture.catalog, "pageSize=4&page=1&sort=name:asc" + sent));
	}

	// Scenario: Tampered cursor

	@Test
	void tamperedCursor() {
		Fixture fixture = fixture();
		String cursor = ok(query(fixture.catalog, "pageSize=4")).path("nextCursor").asString();
		char last = cursor.charAt(cursor.length() / 2);
		String changed = cursor.substring(0, cursor.length() / 2) + ((last == 'A') ? 'B' : 'A')
				+ cursor.substring(cursor.length() / 2 + 1);
		assertInvalid(query(fixture.catalog, "pageSize=4&page=1&cursor=" + encode(changed)));
		assertInvalid(query(fixture.catalog, "pageSize=4&page=1&cursor=garbage"));
		assertInvalid(query(fixture.catalog, "pageSize=4&page=1&cursor=" + encode("{\"k\":[\"a\"]}")));
		assertInvalid(query(fixture.catalog, "pageSize=4&page=1&cursor=" + "A".repeat(CatalogCursors.MAX_LENGTH)));
		assertInvalid(query(fixture.catalog, "pageSize=4&page=1&cursor=" + "A".repeat(CatalogCursors.MAX_LENGTH + 1)));
		assertThat(query(fixture.catalog, "pageSize=4&page=1&cursor=")).hasStatus(HttpStatus.OK);
	}

	/**
	 * Loads every page through the cursor of the previous one and compares the rows, the total
	 * and the cursors with the pages loaded by number.
	 */
	private void walk(String catalog, String parameters) {
		String base = "pageSize=4" + (parameters.isEmpty() ? "" : "&" + parameters);
		List<String> byNumber = new ArrayList<>();
		List<String> byCursor = new ArrayList<>();
		JsonNode first = ok(query(catalog, base));
		int total = first.path("page").path("totalElements").asInt();
		int pages = first.path("page").path("totalPages").asInt();
		assertThat(pages).as(base).isGreaterThan(2);
		JsonNode current = first;
		byCursor.addAll(identities(first));
		for (int page = 0; page < pages; page++) {
			JsonNode numbered = ok(query(catalog, base + "&page=" + page));
			byNumber.addAll(identities(numbered));
			if (page > 0) {
				current = ok(query(catalog,
						base + "&page=" + page + "&cursor=" + encode(current.path("nextCursor").asString())));
				assertThat(current.path("page").path("totalElements").asInt()).as(base).isEqualTo(total);
				assertThat(identities(current)).as(base + " page " + page).isEqualTo(identities(numbered));
				byCursor.addAll(identities(current));
			}
			assertThat(current.path("nextCursor").isNull()).as(base + " page " + page).isEqualTo(page == pages - 1);
		}
		assertThat(byCursor).as(base).hasSize(total).doesNotHaveDuplicates().isEqualTo(byNumber);
	}

	/** Each row as entity ID with the IDs of its join results. */
	private static List<String> identities(JsonNode result) {
		List<String> rows = new ArrayList<>();
		result.path("rows").forEach((row) -> {
			StringBuilder identity = new StringBuilder(row.path("entityId").asString());
			row.path("joinResults").properties().forEach((join) -> {
				identity.append('|');
				join.getValue().forEach((target) -> identity.append(target.path("entityId").asString()).append(','));
			});
			rows.add(identity.toString());
		});
		return rows;
	}

	/**
	 * 30 applications with equal names, names differing in case, empty statuses and user counts,
	 * and two joins (interfaces and hosting nodes) with zero to three results each.
	 */
	private Fixture fixture() {
		UUID type = metatype("Application", false);
		UUID iface = metatype("Interface", false);
		UUID node = metatype("Node", false);
		UUID dataFlow = metatype("Data Flow", true);
		UUID hosting = metatype("Hosting", true);
		UUID status = property("Status " + suffix(), "enum", "planned", "active", "retired");
		UUID users = property("Users " + suffix(), "integer");
		map(type, status);
		map(type, users);
		Random random = new Random(7);
		List<Integer> interfaces = new ArrayList<>();
		List<Integer> nodes = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			interfaces.add(entity(iface, (i % 2 == 0) ? "API" : "Export " + i));
			nodes.add(entity(node, "Node " + (char) ('a' + i)));
		}
		String[] names = { "Alpha", "alpha", "Alpha", "Beta", "Gamma", "gamma", "Delta" };
		String[] statuses = { "planned", "active", "retired", null };
		for (int i = 0; i < 30; i++) {
			int app = entity(type, names[i % names.length] + ((i < 14) ? "" : " " + (i % 5)));
			if (i % 3 == 0) {
				this.jdbc.update("UPDATE entities SET description = ? WHERE id = ?", "Note " + (i % 4), app);
			}
			String value = statuses[random.nextInt(statuses.length)];
			if (value != null) {
				value(app, status, "value_enum", value);
			}
			if (random.nextInt(4) > 0) {
				value(app, users, "value_int", (long) random.nextInt(5));
			}
			for (int c = random.nextInt(4); c > 0; c--) {
				connect(dataFlow, app, interfaces.get(random.nextInt(interfaces.size())));
			}
			for (int c = random.nextInt(3); c > 0; c--) {
				connect(hosting, app, nodes.get(random.nextInt(nodes.size())));
			}
		}
		String catalog = catalog(type);
		ok(columns(catalog, field("name"), property(status), property(users), field("description"), field("createdAt")));
		String interfacesJoin = created(call(HttpMethod.POST, CATALOGS + "/" + catalog + "/joins",
				join("Interfaces", dataFlow, iface)))
			.path("joinDefinitions").get(0).path("id").asString();
		String hostsJoin = created(call(HttpMethod.POST, CATALOGS + "/" + catalog + "/joins",
				join("Hosts", hosting, node)))
			.path("joinDefinitions").get(1).path("id").asString();
		return new Fixture(type, catalog, status, sqlName(status), sqlName(users), interfacesJoin, hostsJoin);
	}

	private record Fixture(UUID type, String catalog, UUID status, String statusKey, String usersKey,
			String interfaces, String hosts) {

		/** The parameters with {@code {status}} and {@code {users}} replaced by their column keys. */
		String expand(String parameters) {
			return parameters.replace("{status}", this.statusKey).replace("{users}", this.usersKey);
		}

	}

	private static ObjectNode join(String name, UUID connection, UUID target) {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("connectionMetatypeId", connection.toString())
			.put("joinDirection", "outbound")
			.put("targetMetatypeId", target.toString());
		body.putArray("targetColumns").add(field("name"));
		return body;
	}

	private MvcTestResult columns(String catalog, ObjectNode... columns) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode list = body.putArray("columns");
		for (ObjectNode column : columns) {
			list.add(column);
		}
		return call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/columns", body);
	}

	private static ObjectNode field(String field) {
		return JsonMapper.shared().createObjectNode().put("field", field);
	}

	private static ObjectNode property(UUID id) {
		return JsonMapper.shared().createObjectNode().put("propertyDefId", id.toString());
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, ?)
				RETURNING id""", UUID.class, name + " " + suffix(), connection);
	}

	private UUID property(String name, String dataType, String... enumValues) {
		UUID id = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, ?) RETURNING id""",
				UUID.class, name, "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), dataType);
		for (int i = 0; i < enumValues.length; i++) {
			this.jdbc.update("INSERT INTO property_enum_values (property_def_id, value, sort_order) VALUES (?, ?, ?)", id,
					enumValues[i], i);
		}
		return id;
	}

	private void map(UUID type, UUID property) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", type, property);
	}

	private int entity(UUID type, String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class, type,
				name, this.architectId);
	}

	private void value(int entity, UUID property, String column, Object value) {
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, " + column
				+ ") VALUES (?, ?, ?)", entity, property, value);
	}

	private void connect(UUID type, int source, int target) {
		int id = entity(type, "Connection " + suffix());
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
	}

	private String sqlName(UUID property) {
		return this.jdbc.queryForObject("SELECT sql_column_name FROM property_definitions WHERE id = ?", String.class,
				property);
	}

	private String catalog(UUID type) {
		JsonNode created = created(call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Catalog " + UUID.randomUUID())
			.put("primaryMetatypeId", type.toString())
			.put("scope", "instance")));
		return created.path("id").asString();
	}

	private MvcTestResult query(String catalog, String parameters) {
		return call(HttpMethod.GET, CATALOGS + "/" + catalog + "/query" + (parameters.isEmpty() ? "" : "?" + parameters),
				null);
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

	private static void assertInvalid(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.BAD_REQUEST);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:validation-failed");
		List<String> fields = new ArrayList<>();
		problem.path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly("cursor");
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static List<String> names(JsonNode result) {
		List<String> names = new ArrayList<>();
		result.path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		return names;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
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
			return Files.createTempDirectory("oea-catalog-cursor").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

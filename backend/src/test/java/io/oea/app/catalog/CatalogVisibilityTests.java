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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Specs catalog/catalog-query "Visibility of the query result", catalog/catalog-filters "No
 * filter on an unreadable column" and portal/portal-catalogs "Visibility in the portal" (US-126
 * AC4, REQ-160 AC2; design D2 of us-126-role-restricted-enforcement). `Investment Cost` is
 * role-restricted with the read role `enterprise-architect`; Kurt holds it, Anna is a viewer and
 * Petra a portal reader.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogVisibilityTests {

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

	private TestPrincipals principals;

	private TestSession kurt;

	private TestSession anna;

	private long kurtId;

	private UUID application;

	private UUID cost;

	private String costKey;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.anna = this.principals.personWithIdpRole("viewer");
		this.kurtId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.kurt.sessionId());
		this.application = metatype("Application", false);
		this.costKey = "c_" + suffix().replace("-", "");
		this.cost = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				VALUES (?, ?, 'integer', 'role-restricted') RETURNING id""", UUID.class, "Investment Cost " + suffix(),
				this.costKey);
		this.jdbc.update("""
				INSERT INTO property_visibility_roles (property_def_id, role_id, access)
				VALUES (?, 'enterprise-architect', 'read'), (?, 'enterprise-architect', 'write')""", this.cost, this.cost);
		map(this.application, this.cost);
		int crm = entity(this.application, "CRM System");
		int erp = entity(this.application, "ERP");
		entity(this.application, "Intranet");
		cost(crm, 100);
		cost(erp, 250);
	}

	@Test
	void catalogColumnEmptyWithoutReadRole() {
		String catalog = catalog(this.application, field("name"), property(this.cost));
		JsonNode result = ok(query(catalog, "", this.anna));
		JsonNode column = column(result, this.costKey);
		assertThat(column.path("readable").asBoolean()).isFalse();
		assertThat(column.path("sortable").asBoolean()).isFalse();
		result.path("rows").forEach((row) -> assertThat(row.path("values").path(this.costKey).isNull()).isTrue());
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(3);
	}

	@Test
	void columnReadableWithTheReadRole() {
		String catalog = catalog(this.application, field("name"), property(this.cost));
		JsonNode result = ok(query(catalog, "sort=" + this.costKey + ":desc", this.kurt));
		JsonNode column = column(result, this.costKey);
		assertThat(column.path("readable").asBoolean(true)).isTrue();
		assertThat(column.path("sortable").asBoolean()).isTrue();
		assertThat(values(result, this.costKey)).containsExactly("250", "100", null);
	}

	@Test
	void sortAndFilterByAnUnreadableColumn() {
		String catalog = catalog(this.application, field("name"), property(this.cost));
		assertInvalid(query(catalog, "sort=" + this.costKey + ":desc", this.anna), "sort");
		assertInvalid(query(catalog, "filter=" + this.costKey + ":gt:100", this.anna), "filter");
		// with the read role the filter applies
		JsonNode filtered = ok(query(catalog, "filter=" + this.costKey + ":gt:100", this.kurt));
		assertThat(names(filtered)).containsExactly("ERP");
		assertThat(filtered.path("page").path("totalElements").asInt()).isEqualTo(1);
	}

	@Test
	void joinAndEndpointColumns() {
		UUID funding = metatype("Funding", true);
		UUID project = metatype("Project", false);
		map(project, this.cost);
		int alpha = entity(project, "Alpha");
		cost(alpha, 900);
		int crm = this.jdbc.queryForObject("SELECT id FROM entities WHERE metatype_id = ? AND name = 'CRM System'",
				Integer.class, this.application);
		connect(funding, crm, alpha);
		// a join on the funded projects with their cost
		String catalog = catalog(this.application, field("name"));
		ObjectNode join = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Projects")
			.put("connectionMetatypeId", funding.toString())
			.put("joinDirection", "outbound")
			.put("targetMetatypeId", project.toString());
		join.putArray("targetColumns").add(field("name")).add(property(this.cost));
		String joinId = created(call(HttpMethod.POST, CATALOGS + "/" + catalog + "/joins", join, this.kurt))
			.path("joinDefinitions").get(0).path("id").asString();
		JsonNode hidden = ok(query(catalog, "", this.anna));
		JsonNode joinColumn = hidden.path("joins").get(0).path("columns").get(1);
		assertThat(joinColumn.path("readable").asBoolean()).isFalse();
		JsonNode result = hidden.path("rows").get(0).path("joinResults").path(joinId).get(0);
		assertThat(result.path("values").path("name").asString()).isEqualTo("Alpha");
		assertThat(result.path("values").path(this.costKey).isNull()).isTrue();
		assertInvalid(query(catalog, "filter=" + joinId + "." + this.costKey + ":gt:1", this.anna), "filter");
		assertThat(ok(query(catalog, "", this.kurt)).path("rows").get(0).path("joinResults").path(joinId).get(0)
			.path("values").path(this.costKey).asString()).isEqualTo("900");
		// the target of a funding as a column of a connection catalog
		String connections = catalog(funding, field("name"), property(this.cost).put("endpoint", "target"));
		JsonNode endpoint = ok(query(connections, "", this.anna));
		assertThat(column(endpoint, "target." + this.costKey).path("readable").asBoolean()).isFalse();
		assertThat(endpoint.path("rows").get(0).path("values").path("target." + this.costKey).isNull()).isTrue();
		assertInvalid(query(connections, "sort=target." + this.costKey + ":asc", this.anna), "sort");
		assertThat(ok(query(connections, "", this.kurt)).path("rows").get(0).path("values")
			.path("target." + this.costKey).asString()).isEqualTo("900");
	}

	@Test
	void connectionScopedProperty() {
		UUID assignment = metatype("Domain Assignment", true);
		this.jdbc.update("""
				UPDATE property_definitions SET visibility_mode = 'connection-scoped', scoping_connection_type_id = ?
				WHERE id = ?""", assignment, this.cost);
		String catalog = catalog(this.application, field("name"), property(this.cost));
		JsonNode result = ok(query(catalog, "", this.kurt));
		assertThat(column(result, this.costKey).path("readable").asBoolean()).isFalse();
		assertThat(values(result, this.costKey)).containsOnlyNulls();
	}

	@Test
	void portalReaders() {
		String catalog = catalog(this.application, field("name"), property(this.cost));
		TestSession petra = this.principals.personWithIdpRole("portal-reader");
		JsonNode portal = ok(get("/api/v1/portal/catalogs/" + catalog + "/query", petra));
		assertThat(column(portal, this.costKey).path("readable").asBoolean()).isFalse();
		assertThat(values(portal, this.costKey)).containsOnlyNulls();
		assertInvalid(get("/api/v1/portal/catalogs/" + catalog + "/query?sort=" + this.costKey + ":asc", petra), "sort");
		// a portal reader who also holds the read role sees the values
		TestSession reader = this.principals.personWithIdpRoles("portal-reader", "enterprise-architect");
		assertThat(values(ok(get("/api/v1/portal/catalogs/" + catalog + "/query", reader)), this.costKey))
			.containsExactly("100", "250", null);
	}

	private JsonNode column(JsonNode result, String key) {
		for (JsonNode column : result.path("columns")) {
			if (column.path("key").asString().equals(key)) {
				return column;
			}
		}
		throw new AssertionError("no column " + key + " in " + result.path("columns"));
	}

	private static List<String> values(JsonNode result, String key) {
		List<String> values = new ArrayList<>();
		result.path("rows").forEach((row) -> values.add(row.path("values").path(key).isNull() ? null
				: row.path("values").path(key).asString()));
		return values;
	}

	private static List<String> names(JsonNode result) {
		return values(result, "name");
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, ?)
				RETURNING id""", UUID.class, name + " " + suffix(), connection);
	}

	private void map(UUID type, UUID property) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", type, property);
	}

	private int entity(UUID type, String name) {
		return this.jdbc.queryForObject("INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id",
				Integer.class, type, name, this.kurtId);
	}

	private void cost(int entity, long value) {
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES (?, ?, ?)",
				entity, this.cost, value);
	}

	private void connect(UUID type, int source, int target) {
		int id = entity(type, "Funding " + suffix());
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
	}

	private String catalog(UUID type, ObjectNode... columns) {
		String catalog = created(call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Catalog " + UUID.randomUUID())
			.put("primaryMetatypeId", type.toString())
			.put("scope", "instance"), this.kurt)).path("id").asString();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode list = body.putArray("columns");
		for (ObjectNode column : columns) {
			list.add(column);
		}
		ok(call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/columns", body, this.kurt));
		return catalog;
	}

	private static ObjectNode field(String field) {
		return JsonMapper.shared().createObjectNode().put("field", field);
	}

	private static ObjectNode property(UUID id) {
		return JsonMapper.shared().createObjectNode().put("propertyDefId", id.toString());
	}

	private MvcTestResult query(String catalog, String parameters, TestSession session) {
		return get(CATALOGS + "/" + catalog + "/query" + (parameters.isEmpty() ? "" : "?" + parameters), session);
	}

	private MvcTestResult get(String path, TestSession session) {
		return call(HttpMethod.GET, path, null, session);
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.BAD_REQUEST);
		List<String> fields = new ArrayList<>();
		body(result).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
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
			return Files.createTempDirectory("oea-catalog-visibility").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

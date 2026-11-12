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
 * Spec metamodel/visibility-override, requirements "Reading through the override" and "Audit of
 * reads through the override", and the override scenarios of catalog/catalog-query and
 * portal/portal-catalogs (US-128, REQ-128; design D2, D3 of us-128-override-role-list).
 * `Investment Cost` is role-restricted to `auditor`, `Security Classification` connection-scoped;
 * Anna holds `viewer` and the override role.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OverrideReadTests {

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

	private UUID actor;

	private UUID assignment;

	private UUID classification;

	private String key;

	private UUID cost;

	private String costKey;

	private String migration;

	private int erp;

	private int crm;

	private int portal;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.kurtId = personOf(this.kurt);
		this.application = metatype("Application", false);
		this.actor = metatype("Business Actor", false);
		this.assignment = metatype("Domain Assignment", true);
		this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = ?", this.actor);
		this.key = "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		this.classification = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, ?, 'string', 'connection-scoped', ?) RETURNING id""", UUID.class,
				"Security Classification " + suffix(), this.key, this.assignment);
		this.costKey = "i_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		this.cost = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				VALUES (?, ?, 'string', 'role-restricted') RETURNING id""", UUID.class, "Investment Cost " + suffix(),
				this.costKey);
		this.jdbc.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?, 'auditor', 'read')",
				this.cost);
		map(this.application, this.classification);
		map(this.application, this.cost);
		this.erp = entity(this.application, "ERP Warehouse");
		this.crm = entity(this.application, "CRM Sales");
		this.portal = entity(this.application, "Portal");
		value(this.erp, this.classification, "internal");
		value(this.crm, this.classification, "secret");
		value(this.portal, this.classification, "public");
		value(this.erp, this.cost, "100");
		value(this.crm, this.cost, "200");
		value(this.portal, this.cost, "300");
		this.migration = "migration-support-" + suffix();
		this.jdbc.update("UPDATE authorization_roles SET overrides_property_visibility = false");
		this.jdbc.update("INSERT INTO authorization_roles (id, name, overrides_property_visibility) VALUES (?, ?, true)",
				this.migration, this.migration);
		this.anna = this.principals.personWithIdpRoles("viewer", this.migration);
	}

	// Requirement: Reading through the override

	@Test
	void overrideEffective() {
		String catalog = catalog(this.application, this.classification, this.cost);
		JsonNode result = ok(query(catalog, "sort=" + this.costKey + ":desc", this.anna));
		assertThat(column(result, this.key).path("readable").asBoolean()).isTrue();
		assertThat(column(result, this.costKey).path("sortable").asBoolean()).isTrue();
		assertThat(names(result)).containsExactly("Portal", "CRM Sales", "ERP Warehouse");
		assertThat(value(result, "CRM Sales", this.key)).isEqualTo("secret");
		assertThat(value(result, "ERP Warehouse", this.costKey)).isEqualTo("100");
		assertThat(names(ok(query(catalog, "filter=" + this.key + ":eq:secret", this.anna)))).containsExactly("CRM Sales");
	}

	@Test
	void noImplicitOverride() {
		String catalog = catalog(this.application, this.classification, this.cost);
		TestSession admin = this.principals.personWithIdpRoles("viewer", "system-admin", "security-admin");
		JsonNode result = ok(query(catalog, "", admin));
		assertThat(column(result, this.key).path("readable").asBoolean()).isFalse();
		assertThat(column(result, this.costKey).path("readable").asBoolean()).isFalse();
		// the list changes at once
		this.jdbc.update("UPDATE authorization_roles SET overrides_property_visibility = false WHERE id = ?", this.migration);
		assertThat(column(ok(query(catalog, "", this.anna)), this.costKey).path("readable").asBoolean()).isFalse();
	}

	@Test
	void noObjectAccess() {
		String catalog = catalog(this.application, this.classification, this.cost);
		TestSession only = this.principals.personWithIdpRole(this.migration);
		assertThat(query(catalog, "", only)).hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Audit of reads through the override

	@Test
	void oneEventPerQuery() {
		String catalog = catalog(this.application, this.classification, this.cost);
		int before = events(catalog);
		ok(query(catalog, "", this.anna));
		assertThat(events(catalog)).isEqualTo(before + 1);
		JsonNode details = lastEvent(catalog);
		assertThat(details.path("channel").asString()).isEqualTo("catalog");
		assertThat(details.path("page").asInt()).isZero();
		assertThat(entityIds(details, this.cost)).containsExactlyInAnyOrder(this.erp, this.crm, this.portal);
		assertThat(entityIds(details, this.classification)).containsExactlyInAnyOrder(this.erp, this.crm, this.portal);
	}

	@Test
	void valueReadableAnyway() {
		String catalog = catalog(this.application, this.cost);
		TestSession auditor = this.principals.personWithIdpRoles("auditor", "viewer", this.migration);
		ok(query(catalog, "", auditor));
		assertThat(events(catalog)).isZero();
		// a public column needs no override either
		ok(query(catalog(this.application), "", this.anna));
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'property-override-access' AND actor_person_id = ?",
				Integer.class, personOf(auditor))).isZero();
	}

	@Test
	void scopeGrantsPartOfTheRows() {
		String catalog = catalog(this.application, this.classification);
		TestSession michael = this.principals.personWithIdpRoles("enterprise-architect", this.migration);
		int element = entity(this.actor, "Michael");
		this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", element, personOf(michael));
		connect(this.assignment, element, this.erp);
		connect(this.assignment, this.portal, element);
		JsonNode result = ok(query(catalog, "", michael));
		assertThat(value(result, "CRM Sales", this.key)).isEqualTo("secret");
		assertThat(entityIds(lastEvent(catalog), this.classification)).containsExactly(this.crm);
	}

	@Test
	void joinAndEndpointColumns() {
		UUID project = metatype("Project", false);
		UUID funding = metatype("Funding", true);
		map(project, this.cost);
		int alpha = entity(project, "Alpha");
		int beta = entity(project, "Beta");
		value(alpha, this.cost, "5");
		connect(funding, this.crm, alpha);
		int toBeta = connect(funding, this.crm, beta);
		String catalog = catalog(this.application);
		ObjectNode join = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Projects")
			.put("connectionMetatypeId", funding.toString())
			.put("joinDirection", "outbound")
			.put("targetMetatypeId", project.toString());
		join.putArray("targetColumns")
			.add(JsonMapper.shared().createObjectNode().put("field", "name"))
			.add(JsonMapper.shared().createObjectNode().put("propertyDefId", this.cost.toString()));
		assertThat(call(HttpMethod.POST, CATALOGS + "/" + catalog + "/joins", join, this.kurt)).hasStatus(HttpStatus.CREATED);
		ok(query(catalog, "", this.anna));
		// only the join result with a value
		assertThat(entityIds(lastEvent(catalog), this.cost)).containsExactly(alpha);
		// the target of a funding in a connection catalog names the target entity
		String connections = catalog(funding, JsonMapper.shared().createObjectNode()
			.put("propertyDefId", this.cost.toString())
			.put("endpoint", "target"));
		ok(query(connections, "", this.anna));
		assertThat(entityIds(lastEvent(connections), this.cost)).containsExactly(alpha).doesNotContain(toBeta);
	}

	@Test
	void nothingToAudit() {
		String catalog = catalog(this.application, this.cost);
		// an empty page
		ok(query(catalog, "filter=name:eq:Nothing", this.anna));
		// a page whose overridden column has only empty values
		UUID other = metatype("Device", false);
		map(other, this.cost);
		entity(other, "Printer");
		String devices = catalog(other, this.cost);
		ok(query(devices, "", this.anna));
		// a rejected query
		assertThat(query(catalog, "sort=unknown:asc", this.anna)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(events(catalog)).isZero();
		assertThat(events(devices)).isZero();
	}

	@Test
	void overrideRoleInThePortal() {
		String catalog = catalog(this.application, this.cost);
		TestSession petra = this.principals.personWithIdpRoles("portal-reader", this.migration);
		JsonNode result = ok(call(HttpMethod.GET, "/api/v1/portal/catalogs/" + catalog + "/query", null, petra));
		assertThat(value(result, "Portal", this.costKey)).isEqualTo("300");
		JsonNode details = lastEvent(catalog);
		assertThat(details.path("channel").asString()).isEqualTo("portal");
		assertThat(entityIds(details, this.cost)).containsExactlyInAnyOrder(this.erp, this.crm, this.portal);
	}

	@Test
	void aFullPageInOneEvent() {
		UUID many = metatype("Server", false);
		map(many, this.cost);
		this.jdbc.update("""
				WITH created AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    SELECT ?, 'Server ' || n, ? FROM generate_series(1, 200) n RETURNING id)
				INSERT INTO entity_property_values (entity_id, property_def_id, value_text)
				SELECT id, ?, 'x' FROM created""", many, this.kurtId, this.cost);
		String catalog = catalog(many, this.cost);
		ok(query(catalog, "pageSize=200", this.anna));
		assertThat(events(catalog)).isEqualTo(1);
		assertThat(entityIds(lastEvent(catalog), this.cost)).hasSize(200);
	}

	private int events(String catalog) {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'property-override-access' AND target_id = ?",
				Integer.class, catalog);
	}

	private JsonNode lastEvent(String catalog) {
		return JsonMapper.shared().readTree(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'property-override-access' AND target_type = 'catalog'
				AND target_id = ? ORDER BY chain_seq DESC LIMIT 1""", String.class, catalog));
	}

	private static List<Integer> entityIds(JsonNode details, UUID property) {
		List<Integer> ids = new ArrayList<>();
		for (JsonNode entry : details.path("properties")) {
			if (entry.path("propertyDefId").asString().equals(property.toString())) {
				entry.path("entityIds").forEach((id) -> ids.add(id.asInt()));
			}
		}
		return ids;
	}

	private String catalog(UUID type, UUID... properties) {
		ArrayNode extra = JsonMapper.shared().createArrayNode();
		for (UUID property : properties) {
			extra.add(JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()));
		}
		return catalogWith(type, extra);
	}

	private String catalog(UUID type, ObjectNode column) {
		ArrayNode extra = JsonMapper.shared().createArrayNode();
		extra.add(column);
		return catalogWith(type, extra);
	}

	private String catalogWith(UUID type, ArrayNode extra) {
		String catalog = body(call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Catalog " + UUID.randomUUID())
			.put("primaryMetatypeId", type.toString())
			.put("scope", "instance"), this.kurt)).path("id").asString();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode columns = body.putArray("columns");
		columns.add(JsonMapper.shared().createObjectNode().put("field", "name"));
		columns.addAll(extra);
		ok(call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/columns", body, this.kurt));
		return catalog;
	}

	private static JsonNode column(JsonNode result, String key) {
		for (JsonNode column : result.path("columns")) {
			if (column.path("key").asString().equals(key)) {
				return column;
			}
		}
		throw new AssertionError("no column " + key);
	}

	private static JsonNode row(JsonNode result, String name) {
		for (JsonNode row : result.path("rows")) {
			if (row.path("values").path("name").asString().equals(name)) {
				return row;
			}
		}
		throw new AssertionError("no row " + name);
	}

	private static String value(JsonNode result, String name, String key) {
		JsonNode value = row(result, name).path("values").path(key);
		return value.isNull() ? null : value.asString();
	}

	private void value(int entity, UUID property, String value) {
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, ?)",
				entity, property, value);
	}

	private static List<String> names(JsonNode result) {
		List<String> names = new ArrayList<>();
		result.path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		return names;
	}

	private long personOf(TestSession session) {
		return this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
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

	private int connect(UUID type, int source, int target) {
		int id = entity(type, "Connection " + suffix());
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private MvcTestResult query(String catalog, String parameters, TestSession session) {
		return call(HttpMethod.GET, CATALOGS + "/" + catalog + "/query" + (parameters.isEmpty() ? "" : "?" + parameters),
				null, session);
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
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
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
			return Files.createTempDirectory("oea-override-read").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

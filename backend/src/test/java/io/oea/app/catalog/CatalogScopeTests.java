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
 * Specs catalog/catalog-query "Connection-scoped values per row", catalog/catalog-filters
 * "Filter on a connection-scoped column" and portal/portal-catalogs "Connection-scoped values in
 * the portal" (US-127, REQ-127; design D4, D5 of us-127-connection-scoped-visibility).
 * `Security Classification` is connection-scoped with `Domain Assignment`; Michael's element is
 * assigned to "ERP Warehouse", not to "CRM Sales".
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogScopeTests {

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

	private TestSession michael;

	private long kurtId;

	private UUID application;

	private UUID actor;

	private UUID assignment;

	private UUID classification;

	private String key;

	private int michaelElement;

	private int erp;

	private int crm;

	private int assignmentToErp;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.michael = this.principals.personWithIdpRole("enterprise-architect");
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
		map(this.application, this.classification);
		this.michaelElement = entity(this.actor, "Michael");
		this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", this.michaelElement, personOf(this.michael));
		this.erp = entity(this.application, "ERP Warehouse");
		this.crm = entity(this.application, "CRM Sales");
		int portal = entity(this.application, "Portal");
		classify(this.erp, "internal");
		classify(this.crm, "secret");
		classify(portal, "public");
		this.assignmentToErp = connect(this.assignment, this.michaelElement, this.erp);
		// the other direction counts as well
		connect(this.assignment, portal, this.michaelElement);
	}

	@Test
	void ownDomainAndOtherDomain() {
		String catalog = catalog(this.application);
		JsonNode result = ok(query(catalog, "", this.michael));
		assertThat(column(result).path("readable").asBoolean()).isTrue();
		assertThat(value(result, "ERP Warehouse")).isEqualTo("internal");
		assertThat(value(result, "Portal")).isEqualTo("public");
		assertThat(value(result, "CRM Sales")).isNull();
	}

	@Test
	void sortAndFilterInScopeOnly() {
		String catalog = catalog(this.application);
		assertThat(names(ok(query(catalog, "filter=" + this.key + ":eq:secret", this.michael)))).isEmpty();
		assertThat(names(ok(query(catalog, "filter=" + this.key + ":eq:internal", this.michael))))
			.containsExactly("ERP Warehouse");
		// out-of-scope rows sort as entries without value: last in both directions
		assertThat(names(ok(query(catalog, "sort=" + this.key + ":asc", this.michael)))).containsExactly("ERP Warehouse",
				"Portal", "CRM Sales");
		assertThat(names(ok(query(catalog, "sort=" + this.key + ":desc", this.michael)))).containsExactly("Portal",
				"ERP Warehouse", "CRM Sales");
		// paging by cursor with the scoped sort equals the pages by number
		String sort = "sort=" + this.key + ":asc&pageSize=1";
		JsonNode first = ok(query(catalog, sort, this.michael));
		JsonNode byCursor = ok(query(catalog, sort + "&page=1&cursor="
				+ URLEncoder.encode(first.path("nextCursor").asString(), StandardCharsets.UTF_8), this.michael));
		assertThat(names(byCursor)).isEqualTo(names(ok(query(catalog, sort + "&page=1", this.michael))));
	}

	@Test
	void assignmentRemovedAndOtherConnections() {
		String catalog = catalog(this.application);
		this.jdbc.update("UPDATE entities SET deleted_at = now() WHERE id = ?", this.assignmentToErp);
		assertThat(value(ok(query(catalog, "", this.michael)), "ERP Warehouse")).isNull();
		// a connection of another type does not count
		connect(metatype("Data Flow", true), this.michaelElement, this.crm);
		assertThat(value(ok(query(catalog, "", this.michael)), "CRM Sales")).isNull();
	}

	@Test
	void withoutAnEffectiveLink() {
		String catalog = catalog(this.application);
		// no link at all
		JsonNode kurts = ok(query(catalog, "", this.kurt));
		assertThat(column(kurts).path("readable").asBoolean()).isFalse();
		assertThat(value(kurts, "ERP Warehouse")).isNull();
		assertInvalid(query(catalog, "sort=" + this.key + ":asc", this.kurt), "sort");
		// the element is no longer of the person metatype
		this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = ?", metatype("Team", false));
		assertThat(column(ok(query(catalog, "", this.michael))).path("readable").asBoolean()).isFalse();
		this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = ?", this.actor);
		// the linked element is deleted
		this.jdbc.update("UPDATE entities SET deleted_at = now() WHERE id = ?", this.michaelElement);
		assertThat(column(ok(query(catalog, "", this.michael))).path("readable").asBoolean()).isFalse();
	}

	@Test
	void joinAndEndpointColumnsInScope() {
		UUID project = metatype("Project", false);
		UUID funding = metatype("Funding", true);
		map(project, this.classification);
		int alpha = entity(project, "Alpha");
		int beta = entity(project, "Beta");
		classify(alpha, "restricted");
		classify(beta, "confidential");
		connect(this.assignment, this.michaelElement, alpha);
		connect(funding, this.crm, alpha);
		connect(funding, this.crm, beta);
		String catalog = catalog(this.application);
		ObjectNode join = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Projects")
			.put("connectionMetatypeId", funding.toString())
			.put("joinDirection", "outbound")
			.put("targetMetatypeId", project.toString());
		join.putArray("targetColumns")
			.add(JsonMapper.shared().createObjectNode().put("field", "name"))
			.add(JsonMapper.shared().createObjectNode().put("propertyDefId", this.classification.toString()));
		String joinId = body(call(HttpMethod.POST, CATALOGS + "/" + catalog + "/joins", join, this.kurt))
			.path("joinDefinitions").get(0).path("id").asString();
		JsonNode crmRow = row(ok(query(catalog, "", this.michael)), "CRM Sales");
		List<String> values = new ArrayList<>();
		crmRow.path("joinResults").path(joinId).forEach((result) -> values.add(result.path("values").path("name").asString()
				+ "=" + (result.path("values").path(this.key).isNull() ? null : result.path("values").path(this.key).asString())));
		assertThat(values).containsExactly("Alpha=restricted", "Beta=null");
		// the target of a funding in a connection catalog
		String connections = catalog(funding, JsonMapper.shared().createObjectNode()
			.put("propertyDefId", this.classification.toString())
			.put("endpoint", "target"));
		List<String> targets = new ArrayList<>();
		ok(query(connections, "", this.michael)).path("rows").forEach((row) -> targets
			.add(row.path("values").path("target." + this.key).isNull() ? null : row.path("values").path("target." + this.key).asString()));
		assertThat(targets).containsExactlyInAnyOrder("restricted", null);
	}

	@Test
	void portalReaderWithALinkedAccount() {
		String catalog = catalog(this.application);
		TestSession petra = this.principals.personWithIdpRole("portal-reader");
		int petraElement = entity(this.actor, "Petra");
		this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", petraElement, personOf(petra));
		connect(this.assignment, petraElement, this.crm);
		JsonNode result = ok(call(HttpMethod.GET, "/api/v1/portal/catalogs/" + catalog + "/query", null, petra));
		assertThat(value(result, "CRM Sales")).isEqualTo("secret");
		assertThat(value(result, "ERP Warehouse")).isNull();
	}

	private String catalog(UUID type) {
		return catalog(type, JsonMapper.shared().createObjectNode().put("propertyDefId", this.classification.toString()));
	}

	private String catalog(UUID type, ObjectNode column) {
		String catalog = body(call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Catalog " + UUID.randomUUID())
			.put("primaryMetatypeId", type.toString())
			.put("scope", "instance"), this.kurt)).path("id").asString();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode columns = body.putArray("columns");
		columns.add(JsonMapper.shared().createObjectNode().put("field", "name"));
		columns.add(column);
		ok(call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/columns", body, this.kurt));
		return catalog;
	}

	private JsonNode column(JsonNode result) {
		for (JsonNode column : result.path("columns")) {
			if (column.path("key").asString().equals(this.key)) {
				return column;
			}
		}
		throw new AssertionError("no column " + this.key);
	}

	private static JsonNode row(JsonNode result, String name) {
		for (JsonNode row : result.path("rows")) {
			if (row.path("values").path("name").asString().equals(name)) {
				return row;
			}
		}
		throw new AssertionError("no row " + name);
	}

	private String value(JsonNode result, String name) {
		JsonNode value = row(result, name).path("values").path(this.key);
		return value.isNull() ? null : value.asString();
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

	private void classify(int entity, String value) {
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, ?)",
				entity, this.classification, value);
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
			return Files.createTempDirectory("oea-catalog-scope").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

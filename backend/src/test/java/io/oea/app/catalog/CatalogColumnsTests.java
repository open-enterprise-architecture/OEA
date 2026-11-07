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
import java.util.Arrays;
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
 * Spec catalog/catalog-columns and the configured columns of catalog/catalog-query
 * (us-047-configure-columns), on an own database with the starter package imported. Every test
 * uses its own metatype mapping "Status" and its own properties.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogColumnsTests {

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

	private TestSession architect;

	private TestSession viewer;

	private long architectId;

	private UUID status;

	private UUID type;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = this.principals.personWithIdpRole("enterprise-architect");
		this.viewer = this.principals.personWithIdpRole("viewer");
		this.architectId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.architect.sessionId());
		if (count("SELECT count(*) FROM metatypes") == 0) {
			TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
			assertThat(call(HttpMethod.POST, "/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import",
					null, metamodelAdmin)).hasStatus(HttpStatus.OK);
		}
		this.status = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Status'", UUID.class);
		this.type = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?)
				RETURNING id""", UUID.class, "Columns Type " + suffix());
		map(this.type, this.status);
	}

	// All attributes as columns, order, label and visibility

	@Test
	void statusBeforeNameWithOwnLabelAndHiddenDescription() {
		String catalog = catalog("instance", this.architect);
		JsonNode saved = ok(columns(catalog, this.architect, property(this.status).put("displayLabel", " Operational Status "),
				field("name"), field("description").put("visible", false)));
		assertThat(saved.path("columns")).hasSize(3);
		assertThat(saved.path("columns").get(0).path("propertyDefId").asString()).isEqualTo(this.status.toString());
		assertThat(saved.path("columns").get(0).path("displayLabel").asString()).isEqualTo("Operational Status");
		assertThat(saved.path("columns").get(2).path("visible").asBoolean()).isFalse();
		assertThat(saved.path("columns").get(0).path("available").asBoolean()).isTrue();
		entity("Gateway", "active", "The gateway", "GW-1");
		JsonNode result = ok(query(catalog, "", this.architect));
		assertThat(keys(result)).containsExactly("status", "name");
		assertThat(result.path("columns").get(0).path("label").asString()).isEqualTo("Operational Status");
		assertThat(result.path("rows").get(0).path("values").path("status").asString()).isEqualTo("active");
		// the configuration survives a reload
		JsonNode read = ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.architect));
		assertThat(read.path("columns").get(0).path("displayLabel").asString()).isEqualTo("Operational Status");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'catalog.columns-changed' AND target_id = '"
				+ catalog + "'")).isOne();
	}

	@Test
	void systemFieldsWithTheirValues() {
		String catalog = catalog("instance", this.architect);
		ok(columns(catalog, this.architect, field("name"), field("description"), field("externalId"), field("createdAt"),
				field("updatedAt"), field("entityVersion")));
		entity("Portal", null, "Customer portal", "PRT-7");
		JsonNode result = ok(query(catalog, "", this.architect));
		assertThat(keys(result)).containsExactly("name", "description", "externalId", "createdAt", "updatedAt",
				"entityVersion");
		List<String> labels = new ArrayList<>();
		result.path("columns").forEach((column) -> labels.add(column.path("label").asString()));
		assertThat(labels).containsExactly("Name", "Description", "External ID", "Created", "Updated", "Version");
		JsonNode values = result.path("rows").get(0).path("values");
		assertThat(values.path("description").asString()).isEqualTo("Customer portal");
		assertThat(values.path("externalId").asString()).isEqualTo("PRT-7");
		assertThat(values.path("createdAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
		assertThat(values.path("updatedAt").isNull()).isTrue();
		assertThat(values.path("entityVersion").asString()).isEqualTo("1");
		assertThat(names(ok(query(catalog, "sort=externalId:desc", this.architect)))).containsExactly("Portal");
	}

	@Test
	void invalidColumns() {
		String catalog = catalog("instance", this.architect);
		UUID unmapped = publicProperty("Unmapped " + suffix());
		UUID restricted = publicProperty("Restricted " + suffix());
		map(this.type, restricted);
		this.jdbc.update("UPDATE property_definitions SET visibility_mode = 'role-restricted' WHERE id = ?", restricted);
		assertInvalid(columns(catalog, this.architect, field("name"), property(UUID.randomUUID())),
				"columns[1].propertyDefId");
		assertInvalid(columns(catalog, this.architect, property(unmapped)), "columns[0].propertyDefId");
		assertInvalid(columns(catalog, this.architect, property(restricted)), "columns[0].propertyDefId");
		assertInvalid(columns(catalog, this.architect, field("name"), field("name")), "columns[1]");
		assertInvalid(columns(catalog, this.architect, field("name").put("propertyDefId", this.status.toString())),
				"columns[0]");
		assertInvalid(columns(catalog, this.architect, field("name").put("displayLabel", "x".repeat(101))),
				"columns[0].displayLabel");
		assertInvalid(columns(catalog, this.architect, field("name").put("visible", false)), "columns");
		assertInvalid(columns(catalog, this.architect, field("name").put("defaultSortDirection", "asc"),
				property(this.status).put("defaultSortDirection", "desc")), "columns");
		assertThat(ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.architect)).path("columns")).isEmpty();
	}

	@Test
	void labelAndColumnCountAtTheirLimits() {
		String catalog = catalog("instance", this.architect);
		ObjectNode[] hundred = new ObjectNode[100];
		hundred[0] = field("name").put("displayLabel", "x".repeat(100));
		for (int i = 1; i < hundred.length; i++) {
			UUID property = publicProperty("Column " + i + " " + suffix());
			map(this.type, property);
			hundred[i] = property(property);
		}
		JsonNode saved = ok(columns(catalog, this.architect, hundred));
		assertThat(saved.path("columns")).hasSize(100);
		assertThat(saved.path("columns").get(0).path("displayLabel").asString()).hasSize(100);
		ObjectNode[] hundredOne = Arrays.copyOf(hundred, 101);
		hundredOne[100] = field("description");
		assertInvalid(columns(catalog, this.architect, hundredOne), "columns");
		assertThat(ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.architect)).path("columns")).hasSize(100);
	}

	// Sortable columns and default sort

	@Test
	void defaultSortAndSortability() {
		String catalog = catalog("instance", this.architect);
		ok(columns(catalog, this.architect, field("name").put("sortable", false),
				property(this.status).put("defaultSortDirection", "desc")));
		entity("Alpha", "planned", null, null);
		entity("Beta", "active", null, null);
		JsonNode result = ok(query(catalog, "", this.architect));
		assertThat(names(result)).containsExactly("Beta", "Alpha");
		assertThat(result.path("sort").path("column").asString()).isEqualTo("status");
		assertThat(result.path("sort").path("direction").asString()).isEqualTo("desc");
		assertThat(result.path("columns").get(0).path("sortable").asBoolean()).isFalse();
		assertThat(query(catalog, "sort=name:asc", this.architect)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(query(catalog, "sort=description:asc", this.architect)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void staleColumnsAndTheDefaultColumnsAgain() {
		String catalog = catalog("instance", this.architect);
		UUID vendor = publicProperty("Vendor " + suffix());
		map(this.type, vendor);
		ok(columns(catalog, this.architect, property(vendor), field("name")));
		this.jdbc.update("DELETE FROM metatype_property_mappings WHERE metatype_id = ? AND property_def_id = ?", this.type,
				vendor);
		assertThat(keys(ok(query(catalog, "", this.architect)))).containsExactly("name");
		JsonNode read = ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.architect));
		assertThat(read.path("columns").get(0).path("available").asBoolean()).isFalse();
		// an empty list restores the default columns
		assertThat(ok(columns(catalog, this.architect)).path("columns")).isEmpty();
		assertThat(keys(ok(query(catalog, "", this.architect)))).containsExactly("name", "status");
		// the other parts of the configuration stay
		assertThat(this.jdbc.queryForObject("SELECT config::text FROM catalogs WHERE id = ?::uuid", String.class, catalog))
			.contains("joinDefinitions", "savedFilters", "savedViews");
	}

	// Who configures the columns

	@Test
	void readerWithoutCatalogManagement() {
		String instance = catalog("instance", this.architect);
		assertThat(columns(instance, this.viewer, field("name"))).hasStatus(HttpStatus.FORBIDDEN);
		String foreign = catalog("personal", this.architect);
		assertThat(columns(foreign, this.viewer, field("name"))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(columns(UUID.randomUUID().toString(), this.viewer, field("name"))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ok(call(HttpMethod.GET, CATALOGS + "/" + instance, null, this.architect)).path("columns")).isEmpty();
		// with catalog:manage, a foreign personal catalog and an unknown one do not exist
		String viewersOwn = catalog("personal", this.viewer);
		assertThat(columns(viewersOwn, this.architect, field("name"))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(columns(UUID.randomUUID().toString(), this.architect, field("name"))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void ownPersonalCatalog() {
		String own = catalog("personal", this.viewer);
		assertThat(ok(columns(own, this.viewer, field("name"), field("description"))).path("columns")).hasSize(2);
		Long actor = this.jdbc.queryForObject("""
				SELECT actor_person_id FROM audit.events WHERE event_type = 'catalog.columns-changed' AND target_id = ?""",
				Long.class, own);
		assertThat(actor).isEqualTo(this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.viewer.sessionId()));
	}

	// helpers

	private MvcTestResult columns(String catalog, TestSession session, ObjectNode... columns) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode list = body.putArray("columns");
		for (ObjectNode column : columns) {
			list.add(column);
		}
		return call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/columns", body, session);
	}

	private static ObjectNode field(String field) {
		return JsonMapper.shared().createObjectNode().put("field", field);
	}

	private static ObjectNode property(UUID id) {
		return JsonMapper.shared().createObjectNode().put("propertyDefId", id.toString());
	}

	private void map(UUID type, UUID property) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", type, property);
	}

	private UUID publicProperty(String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, 'string') RETURNING id""",
				UUID.class, name, "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
	}

	private void entity(String name, String status, String description, String externalId) {
		Integer id = this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, description, external_id, created_by) VALUES (?, ?, ?, ?, ?)
				RETURNING id""", Integer.class, this.type, name, (description != null) ? description : "", externalId,
				this.architectId);
		if (status != null) {
			this.jdbc.update(
					"INSERT INTO entity_property_values (entity_id, property_def_id, value_enum) VALUES (?, ?, ?)", id,
					this.status, status);
		}
	}

	private String catalog(String scope, TestSession session) {
		MvcTestResult created = call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Catalog " + UUID.randomUUID())
			.put("primaryMetatypeId", this.type.toString())
			.put("scope", scope), session);
		assertThat(created).hasStatus(HttpStatus.CREATED);
		return body(created).path("id").asString();
	}

	private MvcTestResult query(String catalogId, String parameters, TestSession session) {
		return call(HttpMethod.GET, CATALOGS + "/" + catalogId + "/query" + (parameters.isEmpty() ? "" : "?" + parameters),
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
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		List<String> fields = new ArrayList<>();
		body(result).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
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

	private static List<String> keys(JsonNode result) {
		List<String> keys = new ArrayList<>();
		result.path("columns").forEach((column) -> keys.add(column.path("key").asString()));
		return keys;
	}

	private int count(String sql) {
		Integer count = this.jdbc.queryForObject(sql, Integer.class);
		return (count != null) ? count : 0;
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
			return Files.createTempDirectory("oea-catalog-columns").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

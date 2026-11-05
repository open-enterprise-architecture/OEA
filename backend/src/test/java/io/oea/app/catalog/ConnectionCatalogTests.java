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
 * Connection catalogs (us-080-connection-catalogs): the endpoint columns of catalog/catalog-columns
 * and "Connections as rows" and "Default columns" of catalog/catalog-query, on an own database with
 * the starter package imported. Every test uses its own metatypes: applications with a vendor,
 * data objects with a classification, a connection metatype "Data Flow" between applications,
 * a connection metatype "Carries Data" from data flows to data objects and an unconstrained
 * "Link".
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionCatalogTests {

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

	private long architectId;

	private UUID application;

	private UUID dataObject;

	private UUID dataFlow;

	private UUID carriesData;

	private UUID link;

	private UUID vendor;

	private UUID classification;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = this.principals.personWithIdpRole("enterprise-architect");
		this.architectId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.architect.sessionId());
		if (this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Integer.class) == 0) {
			TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
			assertThat(call(HttpMethod.POST, "/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import",
					null, metamodelAdmin)).hasStatus(HttpStatus.OK);
		}
		this.application = metatype("Application", false);
		this.dataObject = metatype("Data Object", false);
		this.dataFlow = metatype("Data Flow", true);
		this.carriesData = metatype("Carries Data", true);
		this.link = metatype("Link", true);
		constraint(this.dataFlow, "source", this.application);
		constraint(this.dataFlow, "target", this.application);
		constraint(this.carriesData, "source", this.dataFlow);
		constraint(this.carriesData, "target", this.dataObject);
		this.vendor = publicProperty("Vendor " + suffix());
		map(this.application, this.vendor);
		this.classification = publicProperty("Classification " + suffix());
		map(this.dataObject, this.classification);
	}

	// Connections as rows

	@Test
	void dataFlowsWithSourceAndTargetByDefault() {
		int crm = entity(this.application, "CRM System");
		int erp = entity(this.application, "ERP");
		int accounting = entity(this.application, "Accounting");
		connect(this.dataFlow, "Orders", crm, erp);
		connect(this.dataFlow, "Invoices", erp, accounting);
		String catalog = catalog(this.dataFlow);
		JsonNode result = ok(query(catalog, "", this.architect));
		assertThat(keys(result)).containsExactly("source.name", "name", "target.name");
		assertThat(labels(result)).containsExactly("Source", "Name", "Target");
		assertThat(names(result)).containsExactly("Invoices", "Orders");
		assertThat(values(result, "source.name")).containsExactly("ERP", "CRM System");
		assertThat(values(result, "target.name")).containsExactly("Accounting", "ERP");
		JsonNode sorted = ok(query(catalog, "sort=source.name:asc", this.architect));
		assertThat(names(sorted)).containsExactly("Orders", "Invoices");
		assertThat(sorted.path("sort").path("column").asString()).isEqualTo("source.name");
		assertThat(names(ok(query(catalog, "sort=target.name:desc", this.architect)))).containsExactly("Orders",
				"Invoices");
	}

	@Test
	void configuredAttributesOfSourceAndTarget() {
		int crm = entity(this.application, "CRM System", this.vendor, "Salesforce");
		int erp = entity(this.application, "ERP", this.vendor, "SAP");
		connect(this.dataFlow, "Orders", crm, erp);
		String catalog = catalog(this.dataFlow);
		JsonNode saved = ok(columns(catalog, at("source", field("name")), field("name"),
				at("target", property(this.vendor)).put("defaultSortDirection", "asc"),
				at("source", property(this.vendor)).put("displayLabel", "Supplier")));
		assertThat(saved.path("columns").get(0).path("endpoint").asString()).isEqualTo("source");
		assertThat(saved.path("columns").get(1).path("endpoint").isNull()).isTrue();
		assertThat(saved.path("columns").get(2).path("available").asBoolean()).isTrue();
		JsonNode result = ok(query(catalog, "", this.architect));
		String vendorKey = sqlName(this.vendor);
		assertThat(keys(result)).containsExactly("source.name", "name", "target." + vendorKey, "source." + vendorKey);
		assertThat(labels(result)).containsExactly("Source", "Name", "Target: " + name(this.vendor), "Supplier");
		assertThat(values(result, "target." + vendorKey)).containsExactly("SAP");
		assertThat(values(result, "source." + vendorKey)).containsExactly("Salesforce");
		assertThat(result.path("sort").path("column").asString()).isEqualTo("target." + vendorKey);
	}

	@Test
	void deletedEndpointShowsEmptyCells() {
		int crm = entity(this.application, "CRM System");
		int legacy = entity(this.application, "Legacy");
		connect(this.dataFlow, "Orders", crm, legacy);
		this.jdbc.update("UPDATE entities SET deleted_at = now() WHERE id = ?", legacy);
		JsonNode result = ok(query(catalog(this.dataFlow), "", this.architect));
		assertThat(names(result)).containsExactly("Orders");
		assertThat(values(result, "source.name")).containsExactly("CRM System");
		assertThat(values(result, "target.name")).containsExactly((String) null);
	}

	@Test
	void dataObjectsOfADataFlow() {
		int crm = entity(this.application, "CRM System");
		int erp = entity(this.application, "ERP");
		int orders = connect(this.dataFlow, "Orders", crm, erp);
		connect(this.carriesData, orders, entity(this.dataObject, "Customer master data", this.classification, "PII"));
		connect(this.carriesData, orders, entity(this.dataObject, "Order", this.classification, "Internal"));
		String catalog = catalog(this.dataFlow);
		String id = created(addJoin(catalog, this.architect, join("Data objects", this.carriesData, "outbound",
				this.dataObject, field("name"), property(this.classification))))
			.path("joinDefinitions").get(0).path("id").asString();
		JsonNode row = ok(query(catalog, "", this.architect)).path("rows").get(0);
		List<String> objects = new ArrayList<>();
		row.path("joinResults").path(id).forEach((r) -> objects.add(r.path("values").path("name").asString()));
		assertThat(objects).containsExactly("Customer master data", "Order");
		JsonNode rows = ok(query(catalog, mode(id, "rows"), this.architect));
		assertThat(rows.path("page").path("totalElements").asInt()).isEqualTo(2);
		assertThat(values(rows, "source.name")).containsExactly("CRM System", "CRM System");
	}

	@Test
	void elementCatalogWithADataFlowJoinUnchanged() {
		int crm = entity(this.application, "CRM System");
		connect(this.dataFlow, "Orders", crm, entity(this.application, "ERP"));
		String catalog = catalog(this.application);
		String id = created(addJoin(catalog, this.architect,
				join("Receivers", this.dataFlow, "outbound", this.application, field("name"))))
			.path("joinDefinitions").get(0).path("id").asString();
		JsonNode result = ok(query(catalog, "", this.architect));
		assertThat(keys(result)).containsExactly("name");
		assertThat(names(result)).containsExactly("CRM System", "ERP");
		assertThat(result.path("rows").get(0).path("joinResults").path(id).get(0).path("values").path("name")
			.asString()).isEqualTo("ERP");
	}

	// All attributes as columns

	@Test
	void propertyOfNoAllowedEndpointIsRejected() {
		assertInvalid(columns(catalog(this.dataFlow), at("source", property(this.classification))),
				"columns[0].propertyDefId");
	}

	@Test
	void endpointColumnOutsideAConnectionCatalogIsRejected() {
		assertInvalid(columns(catalog(this.application), at("source", field("name"))), "columns[0].endpoint");
		String catalog = catalog(this.dataFlow);
		assertInvalid(addJoin(catalog, this.architect, join("Data objects", this.carriesData, "outbound",
				this.dataObject, at("target", field("name")))), "targetColumns[0].endpoint");
	}

	@Test
	void unconstrainedSideOffersEveryPublicProperty() {
		String catalog = catalog(this.link);
		ok(columns(catalog, field("name"), at("source", property(this.classification)),
				at("target", property(this.vendor))));
		// a property of the endpoint metatype as well as one the endpoint does not map
		int crm = entity(this.application, "CRM System", this.vendor, "Salesforce");
		connect(this.link, "Link", crm, entity(this.dataObject, "Order", this.classification, "Internal"));
		JsonNode result = ok(query(catalog, "", this.architect));
		assertThat(values(result, "source." + sqlName(this.classification))).containsExactly((String) null);
		assertThat(values(result, "target." + sqlName(this.vendor))).containsExactly((String) null);
	}

	@Test
	void duplicatePerEndpointOnly() {
		String catalog = catalog(this.dataFlow);
		ok(columns(catalog, field("name"), at("source", field("name")), at("target", field("name"))));
		assertInvalid(columns(catalog, at("source", field("name")), at("source", field("name"))), "columns[1]");
	}

	// helpers

	private static ObjectNode join(String name, UUID connection, String direction, UUID target, ObjectNode... columns) {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("connectionMetatypeId", connection.toString())
			.put("joinDirection", direction)
			.put("targetMetatypeId", target.toString());
		ArrayNode list = body.putArray("targetColumns");
		for (ObjectNode column : columns) {
			list.add(column);
		}
		return body;
	}

	private MvcTestResult addJoin(String catalog, TestSession session, ObjectNode join) {
		return call(HttpMethod.POST, CATALOGS + "/" + catalog + "/joins", join, session);
	}

	private MvcTestResult columns(String catalog, ObjectNode... columns) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode list = body.putArray("columns");
		for (ObjectNode column : columns) {
			list.add(column);
		}
		return call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/columns", body, this.architect);
	}

	private static ObjectNode at(String endpoint, ObjectNode column) {
		return column.put("endpoint", endpoint);
	}

	private static List<String> keys(JsonNode result) {
		List<String> keys = new ArrayList<>();
		result.path("columns").forEach((column) -> keys.add(column.path("key").asString()));
		return keys;
	}

	private static List<String> labels(JsonNode result) {
		List<String> labels = new ArrayList<>();
		result.path("columns").forEach((column) -> labels.add(column.path("label").asString()));
		return labels;
	}

	private static List<String> values(JsonNode result, String key) {
		List<String> values = new ArrayList<>();
		result.path("rows").forEach((row) -> values.add(row.path("values").path(key).asString(null)));
		return values;
	}

	private static String mode(String joinId, String mode) {
		return "joinMode[" + joinId + "]=" + mode;
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, ?)
				RETURNING id""", UUID.class, name + " " + suffix(), connection);
	}

	private void constraint(UUID connection, String direction, UUID allowed) {
		this.jdbc.update("""
				INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
				VALUES (?, ?, ?)""", connection, direction, allowed);
	}

	private int entity(UUID type, String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class, type,
				name, this.architectId);
	}

	private int entity(UUID type, String name, UUID property, String value) {
		int id = entity(type, name);
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, ?)",
				id, property, value);
		return id;
	}

	/** A connection entity from source to target; returns its ID. */
	private int connect(UUID type, int source, int target) {
		return connect(type, "Connection " + suffix(), source, target);
	}

	private int connect(UUID type, String name, int source, int target) {
		int id = entity(type, name);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private String name(UUID property) {
		return this.jdbc.queryForObject("SELECT name FROM property_definitions WHERE id = ?", String.class, property);
	}

	private String sqlName(UUID property) {
		return this.jdbc.queryForObject("SELECT sql_column_name FROM property_definitions WHERE id = ?", String.class,
				property);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
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

	private String catalog(UUID primary) {
		MvcTestResult created = call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Catalog " + UUID.randomUUID())
			.put("primaryMetatypeId", primary.toString())
			.put("scope", "instance"), this.architect);
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
			return Files.createTempDirectory("oea-connection-catalogs").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

package io.oea.app.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oea.app.security.Sessions;
import io.oea.audit.verify.ChainVerifier;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec repository/connection-write of us-036-connection-metatypes (US-036 AC1–AC3, US-141 AC2) on
 * an own database with the starter package and the connection metatypes DataFlow (applications
 * and data flows) and AppLink (only applications as source).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionWriteTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String BATCH = "/api/v1/entities/batch";

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

	private UUID component;

	private UUID status;

	private UUID dataFlow;

	private UUID appLink;

	private UUID protocol;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = principals.personWithIdpRole("enterprise-architect");
		if (count("SELECT count(*) FROM metatypes") == 0) {
			TestSession metamodelAdmin = principals.personWithIdpRole("metamodel-admin");
			assertThat(this.mvc.post()
				.uri("/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import")
				.cookie(new Cookie(SessionCookie.NAME, metamodelAdmin.token()))
				.header(CsrfOriginFilter.HEADER, metamodelAdmin.csrfToken())
				.exchange()).hasStatus(HttpStatus.OK);
			UUID component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
					UUID.class);
			UUID flow = this.jdbc.queryForObject("""
					INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001',
					    'DataFlow', true) RETURNING id""", UUID.class);
			UUID link = this.jdbc.queryForObject("""
					INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001',
					    'AppLink', true) RETURNING id""", UUID.class);
			this.jdbc.update("""
					INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
					VALUES (?, 'source', ?), (?, 'source', ?), (?, 'target', ?), (?, 'target', ?), (?, 'source', ?)""",
					flow, component, flow, flow, flow, component, flow, flow, link, component);
			UUID protocol = this.jdbc.queryForObject("""
					INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('Protocol', 'protocol', 'enum')
					RETURNING id""", UUID.class);
			this.jdbc.update("INSERT INTO property_enum_values (property_def_id, value, sort_order) VALUES (?, 'http', 0), (?, 'sftp', 1)",
					protocol, protocol);
			this.jdbc.update("""
					INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, sort_order, scope)
					VALUES (?, ?, 'optional', DATE '2026-01-01', 0, 'organization')""", flow, protocol);
		}
		this.component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
		this.status = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Status'", UUID.class);
		this.dataFlow = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'DataFlow'", UUID.class);
		this.appLink = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'AppLink'", UUID.class);
		this.protocol = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Protocol'",
				UUID.class);
	}

	// "Entities and connections in one batch", "Default name"

	@Test
	void entitiesAndConnectionsInOneBatch() {
		String crm = unique("crm");
		String erp = unique("erp");
		ObjectNode request = batch(component(crm, "CRM", null), component(erp, "ERP", null));
		connection(request, this.dataFlow, crm, erp, null).put("externalId", unique("flow"));
		JsonNode result = ok(post(request));
		JsonNode items = result.path("items");
		assertThat(items).hasSize(3);
		JsonNode flow = items.get(2);
		assertThat(flow.path("itemType").asString()).isEqualTo("connection");
		assertThat(flow.path("index").asInt()).isZero();
		assertThat(flow.path("status").asString()).isEqualTo("created");
		int id = flow.path("id").asInt();
		assertThat(this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, id))
			.isEqualTo("CRM → ERP");
		assertThat(this.jdbc.queryForObject("""
				SELECT s.name FROM entity_connections c JOIN entities s ON s.id = c.source_entity_id
				WHERE c.entity_id = ?""", String.class, id)).isEqualTo("CRM");
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'sourceEntityId' FROM audit.events WHERE event_type = 'entity.created'
				    AND target_id = ?""", String.class, Integer.toString(id))).isEqualTo(
						Integer.toString(items.get(0).path("id").asInt()));
	}

	// "DataFlow without source", "Unknown target", "Connection as source not allowed"

	@Test
	void endpointsAreChecked() {
		String crm = unique("crm");
		String erp = unique("erp");
		ok(post(batch(component(crm, "CRM", null), component(erp, "ERP", null))));
		ObjectNode request = batch();
		connection(request, this.dataFlow, null, erp, "No source");
		connection(request, this.dataFlow, crm, unique("missing"), "Unknown target");
		String flow = unique("flow");
		connection(request, this.dataFlow, crm, erp, "Flow").put("externalId", flow);
		JsonNode items = ok(post(request)).path("items");
		assertThat(code(items.get(0))).isEqualTo("SOURCE_REQUIRED");
		assertThat(code(items.get(1))).isEqualTo("TARGET_NOT_FOUND");
		assertThat(items.get(1).path("errors").get(0).path("field").asString()).isEqualTo("targetExternalId");
		assertThat(items.get(2).path("status").asString()).isEqualTo("created");

		ObjectNode notAllowed = batch();
		connection(notAllowed, this.appLink, flow, erp, "Link from a flow");
		connection(notAllowed, this.component, crm, erp, "Element type");
		ObjectNode both = connection(notAllowed, this.dataFlow, crm, erp, "Both");
		both.put("sourceId", 1);
		JsonNode rejected = ok(post(notAllowed)).path("items");
		assertThat(code(rejected.get(0))).isEqualTo("SOURCE_NOT_ALLOWED");
		assertThat(code(rejected.get(1))).isEqualTo("METATYPE_NOT_CONNECTION");
		assertThat(rejected.get(1).path("errors").get(0).path("field").asString()).isEqualTo("connectionMetatypeId");
		assertThat(code(rejected.get(2))).isEqualTo("SOURCE_AMBIGUOUS");
	}

	// "Branching data flow" (T-relationship), "Upsert of an existing connection", "Other target on update"

	@Test
	void branchingFlowUpsertAndFixedEndpoints() {
		String a = unique("a");
		String b = unique("b");
		String c = unique("c");
		String df1 = unique("df1");
		String df2 = unique("df2");
		ObjectNode request = batch(component(a, "AppA", null), component(b, "AppB", null), component(c, "AppC", null));
		connection(request, this.dataFlow, a, b, "DF-1").put("externalId", df1);
		connection(request, this.dataFlow, df1, c, "DF-2").put("externalId", df2);
		JsonNode items = ok(post(request.put("atomic", true))).path("items");
		assertThat(items.get(4).path("status").asString()).isEqualTo("created");
		int branch = items.get(4).path("id").asInt();
		assertThat(this.jdbc.queryForObject("""
				SELECT s.external_id FROM entity_connections x JOIN entities s ON s.id = x.source_entity_id
				WHERE x.entity_id = ?""", String.class, branch)).isEqualTo(df1);

		ObjectNode upsert = batch();
		connection(upsert, this.dataFlow, df1, c, "DF-2").put("externalId", df2).putArray("properties").addObject()
			.put("propertyDefId", this.protocol.toString()).put("value", "sftp");
		JsonNode updated = ok(post(upsert)).path("items").get(0);
		assertThat(updated.path("status").asString()).isEqualTo("updated");
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, branch))
			.isEqualTo(2);

		ObjectNode moved = batch();
		connection(moved, this.dataFlow, df1, b, "DF-2").put("externalId", df2);
		assertThat(code(ok(post(moved)).path("items").get(0))).isEqualTo("ENDPOINT_CHANGE_NOT_SUPPORTED");
		assertThat(this.jdbc.queryForObject("""
				SELECT t.external_id FROM entity_connections x JOIN entities t ON t.id = x.target_entity_id
				WHERE x.entity_id = ?""", String.class, branch)).isEqualTo(c);
	}

	@Test
	void atomicBatchWithFaultyConnectionWritesNothing() {
		String a = unique("a");
		ObjectNode request = batch(component(a, "AppA", null)).put("atomic", true);
		connection(request, this.dataFlow, a, unique("missing"), "Broken");
		MvcTestResult result = post(request);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("errors").get(0).path("field").asString())
			.isEqualTo("connections[0].targetExternalId");
		assertThat(count("SELECT count(*) FROM entities WHERE external_id = '" + a + "'")).isZero();
	}

	@Test
	void createConnection() {
		String crm = unique("crm");
		String erp = unique("erp");
		JsonNode items = ok(post(batch(component(crm, "CRM", null), component(erp, "ERP", null)))).path("items");
		ObjectNode write = JsonMapper.shared().createObjectNode()
			.put("connectionMetatypeId", this.dataFlow.toString())
			.put("sourceId", items.get(0).path("id").asInt())
			.put("targetExternalId", erp);
		MvcTestResult created = connect(write);
		assertThat(created).hasStatus(HttpStatus.CREATED);
		JsonNode detail = body(created);
		assertThat(detail.path("name").asString()).isEqualTo("CRM → ERP");
		assertThat(detail.path("version").asInt()).isEqualTo(1);
		assertThat(detail.path("metatype").path("name").asString()).isEqualTo("DataFlow");
		assertThat(detail.path("source").path("name").asString()).isEqualTo("CRM");
		assertThat(detail.path("target").path("metatype").path("name").asString()).isEqualTo("Application Component");

		MvcTestResult missing = connect(write.put("targetExternalId", unique("missing")));
		assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(body(missing).path("errors").get(0).path("code").asString()).isEqualTo("TARGET_NOT_FOUND");
		MvcTestResult notAllowed = connect(write.put("targetExternalId", erp).put("connectionMetatypeId",
				this.component.toString()));
		assertThat(notAllowed).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(count("SELECT count(*) FROM entity_connections")).isPositive();
	}

	@Test
	void entityOfAConnectionMetatypeIsWrittenOnlyAsConnection() {
		ObjectNode entity = JsonMapper.shared().createObjectNode().put("name", "Flow")
			.put("metatypeId", this.dataFlow.toString());
		assertThat(code(ok(post(batch(entity))).path("items").get(0))).isEqualTo("METATYPE_IS_CONNECTION");
	}

	@Test
	void endpointsCannotBeChangedByTheApplicationRole() {
		// V19: the runtime role only inserts and reads entity_connections (design D1, D4)
		assertThatThrownBy(() -> this.jdbc.update("UPDATE entity_connections SET target_entity_id = source_entity_id"))
			.isInstanceOf(org.springframework.dao.DataAccessException.class)
			.hasStackTraceContaining("permission denied");
		assertThatThrownBy(() -> this.jdbc.update("DELETE FROM entity_connections"))
			.hasStackTraceContaining("permission denied");
	}

	private ObjectNode connection(ObjectNode request, UUID metatype, String source, String target, String name) {
		ObjectNode connection = request.withArray("connections").addObject();
		connection.put("connectionMetatypeId", metatype.toString());
		if (source != null) {
			connection.put("sourceExternalId", source);
		}
		if (target != null) {
			connection.put("targetExternalId", target);
		}
		if (name != null) {
			connection.put("name", name);
		}
		return connection;
	}

	private MvcTestResult connect(ObjectNode write) {
		return this.mvc.post()
			.uri("/api/v1/connections")
			.cookie(new Cookie(SessionCookie.NAME, this.architect.token()))
			.header(CsrfOriginFilter.HEADER, this.architect.csrfToken())
			.contentType("application/json")
			.content(write.toString())
			.exchange();
	}

	private static String code(JsonNode item) {
		assertThat(item.path("status").asString()).as(item.toString()).isEqualTo("error");
		return item.path("errors").get(0).path("code").asString();
	}

	private ObjectNode component(String externalId, String name, String statusValue) {
		ObjectNode entity = JsonMapper.shared().createObjectNode();
		entity.put("name", name).put("metatypeId", this.component.toString()).put("externalId", externalId);
		if (statusValue != null) {
			entity.putArray("properties").addObject().put("propertyDefId", this.status.toString())
				.put("value", statusValue);
		}
		return entity;
	}

	private static ObjectNode batch(ObjectNode... entities) {
		ObjectNode request = JsonMapper.shared().createObjectNode();
		ArrayNode items = request.putArray("entities");
		for (ObjectNode entity : entities) {
			items.add(entity);
		}
		return request;
	}

	private MvcTestResult post(ObjectNode request) {
		return post(request, this.architect);
	}

	private MvcTestResult post(ObjectNode request, TestSession session) {
		return this.mvc.post()
			.uri(BATCH)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(request.toString())
			.exchange();
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private static String unique(String name) {
		return "test:" + name + "-" + UUID.randomUUID();
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-connections").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

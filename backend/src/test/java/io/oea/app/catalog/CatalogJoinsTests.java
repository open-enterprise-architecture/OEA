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
 * Spec catalog/catalog-joins and the join requirements of catalog/catalog-query
 * (us-048-catalog-joins), on an own database with the starter package imported. Every test uses
 * its own metatypes: applications, interfaces, a connection metatype "Data Flow" from
 * applications to interfaces, a connection metatype "Feeds" from interfaces to applications and
 * an unconstrained "Link".
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogJoinsTests {

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

	private UUID application;

	private UUID iface;

	private UUID other;

	private UUID dataFlow;

	private UUID feeds;

	private UUID link;

	private UUID protocol;

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
		this.application = metatype("Application", false);
		this.iface = metatype("Interface", false);
		this.other = metatype("Node", false);
		this.dataFlow = metatype("Data Flow", true);
		this.feeds = metatype("Feeds", true);
		this.link = metatype("Link", true);
		constraint(this.dataFlow, "source", this.application);
		constraint(this.dataFlow, "target", this.iface);
		constraint(this.feeds, "source", this.iface);
		constraint(this.feeds, "target", this.application);
		this.protocol = publicProperty("Protocol " + suffix());
		map(this.iface, this.protocol);
	}

	// Add a join

	@Test
	void interfacesOfAnApplicationAggregated() {
		String catalog = catalog("instance", this.architect);
		int crm = entity(this.application, "CRM System");
		entity(this.application, "ERP");
		connect(this.dataFlow, crm, entity(this.iface, "SFTP Export", "SFTP"));
		connect(this.dataFlow, crm, entity(this.iface, "REST API", "REST"));
		JsonNode saved = created(addJoin(catalog, this.architect, join("Interfaces", this.dataFlow, "outbound", this.iface,
				field("name"), property(this.protocol))));
		JsonNode join = saved.path("joinDefinitions").get(0);
		assertThat(join.path("id").asString()).isNotEmpty();
		assertThat(join.path("available").asBoolean()).isTrue();
		assertThat(join.path("targetColumns")).hasSize(2);
		String id = join.path("id").asString();
		JsonNode result = ok(query(catalog, "", this.architect));
		assertThat(result.path("joins").get(0).path("mode").asString()).isEqualTo("aggregate");
		assertThat(result.path("joins").get(0).path("columns").get(1).path("key").asString())
			.isEqualTo(sqlName(this.protocol));
		assertThat(result.path("joins").get(0).path("columns").get(0).path("sortable").asBoolean()).isFalse();
		assertThat(result.path("rows")).hasSize(2);
		JsonNode crmRow = result.path("rows").get(0);
		assertThat(crmRow.path("values").path("name").asString()).isEqualTo("CRM System");
		List<String> interfaces = new ArrayList<>();
		crmRow.path("joinResults").path(id).forEach((r) -> interfaces.add(r.path("values").path("name").asString()));
		assertThat(interfaces).containsExactly("REST API", "SFTP Export");
		assertThat(crmRow.path("joinResults").path(id).get(0).path("values").path(sqlName(this.protocol)).asString())
			.isEqualTo("REST");
		assertThat(result.path("rows").get(1).path("joinResults").path(id).isArray()).isTrue();
		assertThat(result.path("rows").get(1).path("joinResults").path(id)).isEmpty();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'catalog.join-added' AND target_id = '"
				+ catalog + "'")).isOne();
	}

	@Test
	void inboundAndBothDirections() {
		String catalog = catalog("instance", this.architect);
		int crm = entity(this.application, "CRM System");
		int api = entity(this.iface, "REST API", "REST");
		connect(this.feeds, api, crm);
		connect(this.link, crm, api);
		connect(this.link, api, crm);
		String inbound = created(addJoin(catalog, this.architect,
				join("Feeding interfaces", this.feeds, "inbound", this.iface, field("name"))))
			.path("joinDefinitions").get(0).path("id").asString();
		String both = created(addJoin(catalog, this.architect,
				join("Linked", this.link, "both", this.iface, field("name"))))
			.path("joinDefinitions").get(1).path("id").asString();
		JsonNode row = ok(query(catalog, "", this.architect)).path("rows").get(0);
		assertThat(row.path("joinResults").path(inbound)).hasSize(1);
		// reached in both directions, listed once
		assertThat(row.path("joinResults").path(both)).hasSize(1);
		assertThat(row.path("joinResults").path(both).get(0).path("entityId").asInt()).isEqualTo(api);
	}

	@Test
	void defaultModeOfTheJoin() {
		String catalog = catalog("instance", this.architect);
		int crm = entity(this.application, "CRM System");
		connect(this.dataFlow, crm, entity(this.iface, "REST API", "REST"));
		connect(this.dataFlow, crm, entity(this.iface, "SFTP Export", "SFTP"));
		created(addJoin(catalog, this.architect,
				join("Interfaces", this.dataFlow, "outbound", this.iface, field("name")).put("defaultJoinMode", "rows")));
		JsonNode result = ok(query(catalog, "", this.architect));
		assertThat(result.path("rows")).hasSize(2);
		assertThat(result.path("joins").get(0).path("mode").asString()).isEqualTo("rows");
		JsonNode read = ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.architect));
		assertThat(read.path("defaultJoinMode").asString()).isEqualTo("aggregate");
	}

	// Validation of a join

	@Test
	void invalidJoins() {
		String catalog = catalog("instance", this.architect);
		UUID unmapped = publicProperty("Unmapped " + suffix());
		assertInvalid(addJoin(catalog, this.architect, join("X", this.application, "outbound", this.iface, field("name"))),
				"connectionMetatypeId");
		assertInvalid(addJoin(catalog, this.architect, join("X", UUID.randomUUID(), "outbound", this.iface, field("name"))),
				"connectionMetatypeId");
		assertInvalid(addJoin(catalog, this.architect, join("X", this.dataFlow, "outbound", this.other, field("name"))),
				"targetMetatypeId");
		assertInvalid(addJoin(catalog, this.architect, join("X", this.dataFlow, "inbound", this.iface, field("name"))),
				"targetMetatypeId");
		assertInvalid(addJoin(catalog, this.architect, join("X", this.dataFlow, "outbound", this.iface, property(unmapped))),
				"targetColumns[0].propertyDefId");
		assertInvalid(addJoin(catalog, this.architect,
				join("X", this.dataFlow, "outbound", this.iface, field("name").put("defaultSortDirection", "asc"))),
				"targetColumns[0].defaultSortDirection");
		assertInvalid(addJoin(catalog, this.architect,
				join("X", this.dataFlow, "outbound", this.iface, field("name").put("visible", false))), "targetColumns");
		assertInvalid(addJoin(catalog, this.architect, join(" ", this.dataFlow, "outbound", this.iface, field("name"))),
				"name");
		assertThat(ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.architect)).path("joinDefinitions"))
			.isEmpty();
		for (int i = 0; i < 10; i++) {
			created(addJoin(catalog, this.architect, join("J" + i, this.dataFlow, "outbound", this.iface, field("name"))));
		}
		assertInvalid(addJoin(catalog, this.architect, join("J10", this.dataFlow, "outbound", this.iface, field("name"))),
				"joinDefinitions");
	}

	// Edit and remove a join

	@Test
	void editKeepsTheIdAndRemovalCleansUpSavedViews() {
		String catalog = catalog("instance", this.architect);
		String id = created(addJoin(catalog, this.architect,
				join("Interfaces", this.dataFlow, "outbound", this.iface, field("name"))))
			.path("joinDefinitions").get(0).path("id").asString();
		JsonNode changed = ok(updateJoin(catalog, id, this.architect,
				join("APIs", this.dataFlow, "outbound", this.iface, field("name"), property(this.protocol))));
		assertThat(changed.path("joinDefinitions").get(0).path("id").asString()).isEqualTo(id);
		assertThat(changed.path("joinDefinitions").get(0).path("name").asString()).isEqualTo("APIs");
		String kept = UUID.randomUUID().toString();
		this.jdbc.update("""
				UPDATE catalogs SET config = jsonb_set(config, '{savedViews}', ?::jsonb) WHERE id = ?::uuid""",
				"[{\"id\": \"%s\", \"name\": \"Detail\", \"isDefault\": false, \"joinModeOverrides\": [{\"joinDefinitionId\": \"%s\", \"joinMode\": \"rows\"}, {\"joinDefinitionId\": \"%s\", \"joinMode\": \"rows\"}]}]"
					.formatted(UUID.randomUUID(), id, kept), catalog);
		assertThat(updateJoin(catalog, UUID.randomUUID().toString(), this.architect,
				join("X", this.dataFlow, "outbound", this.iface, field("name")))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(removeJoin(catalog, UUID.randomUUID().toString(), this.architect)).hasStatus(HttpStatus.NOT_FOUND);
		JsonNode removed = ok(removeJoin(catalog, id, this.architect));
		assertThat(removed.path("joinDefinitions")).isEmpty();
		String views = this.jdbc.queryForObject("SELECT config -> 'savedViews' FROM catalogs WHERE id = ?::uuid",
				String.class, catalog);
		assertThat(views).doesNotContain(id).contains(kept);
		assertThat(ok(query(catalog, "", this.architect)).path("joins")).isEmpty();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type IN ('catalog.join-changed', "
				+ "'catalog.join-removed') AND target_id = '" + catalog + "'")).isEqualTo(2);
	}

	// Who configures the joins

	@Test
	void readerWithoutCatalogManagementAndOwnPersonalCatalog() {
		String instance = catalog("instance", this.architect);
		assertThat(addJoin(instance, this.viewer, join("X", this.dataFlow, "outbound", this.iface, field("name"))))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ok(call(HttpMethod.GET, CATALOGS + "/" + instance, null, this.architect)).path("joinDefinitions"))
			.isEmpty();
		String own = catalog("personal", this.viewer);
		String id = created(addJoin(own, this.viewer, join("X", this.dataFlow, "outbound", this.iface, field("name"))))
			.path("joinDefinitions").get(0).path("id").asString();
		assertThat(removeJoin(own, id, this.architect)).hasStatus(HttpStatus.NOT_FOUND);
		Long actor = this.jdbc.queryForObject("""
				SELECT actor_person_id FROM audit.events WHERE event_type = 'catalog.join-added' AND target_id = ?""",
				Long.class, own);
		assertThat(actor).isEqualTo(this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.viewer.sessionId()));
	}

	// Join mode at runtime, sorting and paging in rows mode

	@Test
	void rowsModeAtRuntime() {
		String catalog = catalog("instance", this.architect);
		int crm = entity(this.application, "CRM System");
		entity(this.application, "ERP");
		int intranet = entity(this.application, "Intranet");
		connect(this.dataFlow, crm, entity(this.iface, "REST API", "REST"));
		connect(this.dataFlow, crm, entity(this.iface, "SFTP Export", "SFTP"));
		connect(this.dataFlow, intranet, entity(this.iface, "LDAP", "LDAP"));
		String id = created(addJoin(catalog, this.architect,
				join("Interfaces", this.dataFlow, "outbound", this.iface, field("name"), property(this.protocol))))
			.path("joinDefinitions").get(0).path("id").asString();
		JsonNode rows = ok(query(catalog, mode(id, "rows"), this.architect));
		assertThat(rows.path("page").path("totalElements").asInt()).isEqualTo(4);
		assertThat(names(rows)).containsExactly("CRM System", "CRM System", "ERP", "Intranet");
		assertThat(rows.path("rows").get(0).path("joinResults").path(id)).hasSize(1);
		assertThat(rows.path("rows").get(2).path("joinResults").path(id)).isEmpty();
		assertThat(rows.path("joins").get(0).path("columns").get(0).path("sortable").asBoolean()).isTrue();
		// sorting by a join column, only in rows mode
		JsonNode sorted = ok(query(catalog, mode(id, "rows") + "&sort=" + id + "." + sqlName(this.protocol) + ":asc",
				this.architect));
		List<String> protocols = new ArrayList<>();
		sorted.path("rows").forEach((row) -> protocols.add(row.path("joinResults").path(id).path(0).path("values")
			.path(sqlName(this.protocol)).asString(null)));
		assertThat(protocols).containsExactly("LDAP", "REST", "SFTP", null);
		assertThat(sorted.path("sort").path("column").asString()).isEqualTo(id + "." + sqlName(this.protocol));
		assertBadRequest(query(catalog, "sort=" + id + ".name:asc", this.architect), "sort");
		// paging counts rows
		JsonNode second = ok(query(catalog, mode(id, "rows") + "&page=1&pageSize=2", this.architect));
		assertThat(names(second)).containsExactly("ERP", "Intranet");
		assertThat(second.path("page").path("totalPages").asInt()).isEqualTo(2);
		// the override changes nothing stored
		assertThat(ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.architect)).path("joinDefinitions").get(0)
			.path("defaultJoinMode").isNull()).isTrue();
		assertThat(ok(query(catalog, mode(id, "aggregate"), this.architect)).path("rows")).hasSize(3);
		assertBadRequest(query(catalog, mode(UUID.randomUUID().toString(), "rows"), this.architect), "joinMode");
		assertBadRequest(query(catalog, mode(id, "wide"), this.architect), "joinMode");
	}

	@Test
	void joinsSwitchedIndependentlyAndCrossProduct() {
		String catalog = catalog("instance", this.architect);
		int crm = entity(this.application, "CRM System");
		int api = entity(this.iface, "REST API", "REST");
		int sftp = entity(this.iface, "SFTP Export", "SFTP");
		connect(this.dataFlow, crm, api);
		connect(this.dataFlow, crm, sftp);
		connect(this.feeds, api, crm);
		connect(this.feeds, sftp, crm);
		String interfaces = created(addJoin(catalog, this.architect,
				join("Interfaces", this.dataFlow, "outbound", this.iface, field("name"))))
			.path("joinDefinitions").get(0).path("id").asString();
		String feeding = created(addJoin(catalog, this.architect,
				join("Feeding", this.feeds, "inbound", this.iface, field("name"))))
			.path("joinDefinitions").get(1).path("id").asString();
		JsonNode one = ok(query(catalog, mode(interfaces, "rows"), this.architect));
		assertThat(one.path("rows")).hasSize(2);
		assertThat(one.path("rows").get(0).path("joinResults").path(feeding)).hasSize(2);
		JsonNode both = ok(query(catalog, mode(interfaces, "rows") + "&" + mode(feeding, "rows"), this.architect));
		assertThat(both.path("page").path("totalElements").asInt()).isEqualTo(4);
		assertThat(both.path("rows")).hasSize(4);
	}

	@Test
	void deletedEntitiesAndStaleJoinsAreLeftOut() {
		String catalog = catalog("instance", this.architect);
		int crm = entity(this.application, "CRM System");
		int gone = entity(this.iface, "Old API", "SOAP");
		connect(this.dataFlow, crm, gone);
		int removedConnection = connect(this.dataFlow, crm, entity(this.iface, "Removed link", "REST"));
		connect(this.dataFlow, crm, entity(this.iface, "REST API", "REST"));
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id IN (?, ?)", this.architectId,
				gone, removedConnection);
		String id = created(addJoin(catalog, this.architect,
				join("Interfaces", this.dataFlow, "outbound", this.iface, field("name"))))
			.path("joinDefinitions").get(0).path("id").asString();
		assertThat(ok(query(catalog, "", this.architect)).path("rows").get(0).path("joinResults").path(id)).hasSize(1);
		// the target is no longer reachable
		this.jdbc.update("DELETE FROM metatype_connection_constraints WHERE metatype_id = ? AND direction = 'target'",
				this.dataFlow);
		constraint(this.dataFlow, "target", this.other);
		JsonNode read = ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.architect));
		assertThat(read.path("joinDefinitions").get(0).path("available").asBoolean()).isFalse();
		JsonNode result = ok(query(catalog, "", this.architect));
		assertThat(result.path("joins")).isEmpty();
		assertThat(result.path("rows").get(0).path("joinResults").isEmpty()).isTrue();
		assertBadRequest(query(catalog, mode(id, "rows"), this.architect), "joinMode");
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

	private MvcTestResult updateJoin(String catalog, String joinId, TestSession session, ObjectNode join) {
		return call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/joins/" + joinId, join, session);
	}

	private MvcTestResult removeJoin(String catalog, String joinId, TestSession session) {
		return call(HttpMethod.DELETE, CATALOGS + "/" + catalog + "/joins/" + joinId, null, session);
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

	private int entity(UUID type, String name, String protocol) {
		int id = entity(type, name);
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, ?)",
				id, this.protocol, protocol);
		return id;
	}

	/** A connection entity from source to target; returns its ID. */
	private int connect(UUID type, int source, int target) {
		int id = entity(type, "Connection " + suffix());
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private String sqlName(UUID property) {
		return this.jdbc.queryForObject("SELECT sql_column_name FROM property_definitions WHERE id = ?", String.class,
				property);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static void assertBadRequest(MvcTestResult result, String field) {
		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		List<String> fields = new ArrayList<>();
		body(result).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
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

	private String catalog(String scope, TestSession session) {
		MvcTestResult created = call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Catalog " + UUID.randomUUID())
			.put("primaryMetatypeId", this.application.toString())
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
			return Files.createTempDirectory("oea-catalog-joins").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

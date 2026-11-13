package io.oea.app.solution;

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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

/** Spec solution/solution-diff (US-043, REQ-041; design D2 of us-043-diff-view). */
@SpringBootTest
@AutoConfigureMockMvc
class SolutionDiffTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

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

	private TestSession michael;

	private long michaelId;

	private String solution;

	private UUID application;

	private UUID component;

	private UUID owner;

	private UUID release;

	private UUID users;

	private UUID hosting;

	private UUID cost;

	private UUID scoped;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = this.principals.personWithIdpRole("solution-architect");
		this.michaelId = personOf(this.michael);
		this.application = metatype("Application", false);
		this.component = metatype("Component", false);
		this.owner = property("Owner", "string", "public", null, null);
		this.release = property("Release", "string", "public", null, null);
		this.users = property("Users", "integer", "public", null, null);
		this.hosting = property("Hosted on", "reference", "public", null, this.component);
		this.cost = property("Cost", "integer", "role-restricted", null, null);
		this.jdbc.update("""
				INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES
				    (?, 'enterprise-architect', 'read'), (?, 'enterprise-architect', 'write'),
				    (?, 'solution-architect', 'write')""", this.cost, this.cost, this.cost);
		this.scoped = property("Salary Band", "string", "connection-scoped", metatype("Domain Assignment", true), null);
		map(this.owner, "mandatory");
		for (UUID property : List.of(this.release, this.users, this.hosting, this.cost, this.scoped)) {
			map(property, "optional");
		}
		this.solution = solution("CRM Replacement");
	}

	// Requirement: Read the diff

	@Test
	void noDeltas() {
		JsonNode diff = diff(this.michael, this.solution, "");
		assertThat(diff.path("entries")).isEmpty();
		assertThat(diff.path("page").path("totalElements").asInt()).isZero();
		assertThat(diff.path("nextCursor").isNull()).isTrue();
	}

	@Test
	void unknownSolutionOrMissingPermission() {
		assertThat(get(this.michael, UUID.randomUUID().toString(), "")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(this.principals.personWithIdpRole("portal-reader"), this.solution, ""))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Only affected entities

	@Test
	void onlyAffectedEntities() {
		for (int n = 0; n < 10; n++) {
			entity(this.application, "Other " + n);
		}
		int crm = entity(this.application, "CRM-Legacy");
		int erp = erp();
		created(create(this.michael, this.solution, retiring(crm)));
		created(create(this.michael, this.solution, modified(erp, null, null, value(this.release, "v5"))));
		created(create(this.michael, this.solution, newEntity("Salesforce", null, value(this.owner, "Sales"))));
		JsonNode entries = diff(this.michael, this.solution, "").path("entries");
		List<String> names = new ArrayList<>();
		entries.forEach((entry) -> names.add(entry.path("deltaType").asString() + " " + entry.path("name").asString()));
		// one entry per delta, in the order of the delta list
		assertThat(names).containsExactly("new Salesforce", "modified ERP-Core", "retiring CRM-Legacy");
	}

	@Test
	void singleDelta() {
		created(create(this.michael, this.solution, retiring(entity(this.application, "CRM-Legacy"))));
		assertThat(diff(this.michael, this.solution, "").path("entries")).hasSize(1);
	}

	// Requirement: Paging of the diff

	@Test
	void pagesByCursor() {
		insert("new", 50);
		insert("modified", 40);
		insert("retiring", 30);
		List<String> all = new ArrayList<>();
		diff(this.michael, this.solution, "pageSize=200").path("entries")
			.forEach((entry) -> all.add(entry.path("deltaId").asString()));
		assertThat(all).hasSize(120).doesNotHaveDuplicates();
		List<String> paged = new ArrayList<>();
		String cursor = null;
		for (int page = 0; page < 3; page++) {
			JsonNode result = diff(this.michael, this.solution,
					"page=" + page + "&pageSize=50" + ((cursor != null) ? "&cursor=" + cursor : ""));
			result.path("entries").forEach((entry) -> paged.add(entry.path("deltaId").asString()));
			assertThat(result.path("counts").path("new").asInt()).isEqualTo(50);
			assertThat(result.path("counts").path("modified").asInt()).isEqualTo(40);
			assertThat(result.path("counts").path("retiring").asInt()).isEqualTo(30);
			cursor = result.path("nextCursor").isNull() ? null : result.path("nextCursor").asString();
		}
		assertThat(paged).containsExactlyElementsOf(all);
		assertThat(cursor).isNull();
	}

	@ParameterizedTest
	@CsvSource({ "1, 200", "200, 200", "0, 400", "201, 400" })
	void limitsOfThePageSize(int pageSize, int status) {
		insert("new", 2);
		MvcTestResult result = get(this.michael, this.solution, "pageSize=" + pageSize);
		assertThat(result).hasStatus(status);
		if (status == 200) {
			assertThat(json(result).path("entries")).hasSize(Math.min(pageSize, 2));
		}
	}

	@Test
	void changedCursor() {
		insert("new", 3);
		String cursor = diff(this.michael, this.solution, "pageSize=2").path("nextCursor").asString();
		MvcTestResult result = get(this.michael, this.solution, "page=1&pageSize=2&cursor=" + cursor + "x");
		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(json(result).path("invalidFields").toString()).isEqualTo("[\"cursor\"]");
	}

	// Requirement: Fields of an entry; Entry of a retiring, new and modified entity

	@Test
	void retiringEntity() {
		int crm = entity(this.application, "CRM-Legacy");
		this.jdbc.update("UPDATE entities SET description = 'Old CRM' WHERE id = ?", crm);
		catalog(crm, this.release, "v2");
		created(create(this.michael, this.solution, retiring(crm)));
		JsonNode entry = only();
		assertThat(entry.path("deltaType").asString()).isEqualTo("retiring");
		assertThat(entry.path("entityId").asInt()).isEqualTo(crm);
		assertThat(entry.path("inCatalog").asBoolean()).isTrue();
		Map<String, JsonNode> fields = fields(entry);
		assertThat(fields.get("name").path("current").path("valueText").asString()).isEqualTo("CRM-Legacy");
		assertThat(fields.get("description").path("current").path("valueText").asString()).isEqualTo("Old CRM");
		assertThat(fields.get(this.release.toString()).path("current").path("valueText").asString()).isEqualTo("v2");
		fields.values().forEach((field) -> {
			assertThat(field.path("target").isNull()).isTrue();
			assertThat(field.path("changed").asBoolean()).isTrue();
		});
	}

	@Test
	void newEntity() {
		created(create(this.michael, this.solution,
				newEntity("Salesforce", "Cloud CRM", value(this.owner, "Sales"), value(this.users, "250"))));
		JsonNode entry = only();
		assertThat(entry.path("entityId").isNull()).isTrue();
		assertThat(entry.path("inCatalog").asBoolean()).isFalse();
		Map<String, JsonNode> fields = fields(entry);
		assertThat(fields.get("name").path("target").path("valueText").asString()).isEqualTo("Salesforce");
		assertThat(fields.get("description").path("target").path("valueText").asString()).isEqualTo("Cloud CRM");
		assertThat(fields.get(this.owner.toString()).path("target").path("valueText").asString()).isEqualTo("Sales");
		assertThat(fields.get(this.users.toString()).path("target").path("valueInt").asLong()).isEqualTo(250L);
		fields.values().forEach((field) -> {
			assertThat(field.path("current").isNull()).isTrue();
			assertThat(field.path("changed").asBoolean()).isTrue();
		});
	}

	@Test
	void modifiedEntity() {
		int erp = erp();
		created(create(this.michael, this.solution, modified(erp, null, null, value(this.release, "v5"))));
		Map<String, JsonNode> fields = fields(only());
		JsonNode release = fields.get(this.release.toString());
		assertThat(release.path("current").path("valueText").asString()).isEqualTo("v4");
		assertThat(release.path("target").path("valueText").asString()).isEqualTo("v5");
		assertThat(release.path("changed").asBoolean()).isTrue();
		JsonNode owner = fields.get(this.owner.toString());
		assertThat(owner.path("current").path("valueText").asString()).isEqualTo("IT");
		assertThat(owner.path("target").path("valueText").asString()).isEqualTo("IT");
		assertThat(owner.path("changed").asBoolean()).isFalse();
		JsonNode name = fields.get("name");
		assertThat(name.path("current").path("valueText").asString()).isEqualTo("ERP-Core");
		assertThat(name.path("target").path("valueText").asString()).isEqualTo("ERP-Core");
		assertThat(name.path("changed").asBoolean()).isFalse();
		// a property without a value on either side is not listed
		assertThat(fields).doesNotContainKey(this.hosting.toString());
	}

	@Test
	void renamedEntity() {
		int erp = erp();
		created(create(this.michael, this.solution, modified(erp, "ERP-Core 2", "New core", new ObjectNode[0])));
		Map<String, JsonNode> fields = fields(only());
		assertThat(fields.get("name").path("target").path("valueText").asString()).isEqualTo("ERP-Core 2");
		assertThat(fields.get("name").path("changed").asBoolean()).isTrue();
		assertThat(fields.get("description").path("current").isNull()).isTrue();
		assertThat(fields.get("description").path("target").path("valueText").asString()).isEqualTo("New core");
	}

	@Test
	void removedValue() {
		int erp = erp();
		catalog(erp, this.users, 250);
		created(create(this.michael, this.solution, modified(erp, null, null, value(this.users, ""))));
		JsonNode users = fields(only()).get(this.users.toString());
		assertThat(users.path("current").path("valueInt").asLong()).isEqualTo(250L);
		assertThat(users.path("target").isNull()).isTrue();
		assertThat(users.path("changed").asBoolean()).isTrue();
	}

	@Test
	void referenceValue() {
		int erp = erp();
		int dc = entity(this.component, "DC-Zurich");
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_ref) VALUES (?, ?, ?)", erp,
				this.hosting, dc);
		created(create(this.michael, this.solution, modified(erp, null, null, value(this.release, "v5"))));
		JsonNode hosting = fields(only()).get(this.hosting.toString());
		for (String side : List.of("current", "target")) {
			assertThat(hosting.path(side).path("valueRef").path("id").asInt()).isEqualTo(dc);
			assertThat(hosting.path(side).path("valueRef").path("name").asString()).isEqualTo("DC-Zurich");
		}
	}

	// Requirement: Drift since capture

	@Test
	void catalogChangedSinceCapture() {
		int erp = erp();
		created(create(this.michael, this.solution, modified(erp, null, null, value(this.release, "v5"))));
		this.jdbc.update("UPDATE entity_property_values SET value_text = 'v4.1' WHERE entity_id = ? AND property_def_id = ?",
				erp, this.release);
		JsonNode release = fields(only()).get(this.release.toString());
		assertThat(release.path("current").path("valueText").asString()).isEqualTo("v4.1");
		assertThat(release.path("target").path("valueText").asString()).isEqualTo("v5");
		assertThat(release.path("catalogChanged").asBoolean()).isTrue();
	}

	@Test
	void catalogUnchanged() {
		int erp = erp();
		created(create(this.michael, this.solution, modified(erp, "ERP-Core 2", null, value(this.release, "v5"))));
		Map<String, JsonNode> fields = fields(only());
		assertThat(fields.get(this.release.toString()).path("catalogChanged").asBoolean()).isFalse();
		assertThat(fields.get("name").path("catalogChanged").asBoolean()).isFalse();
		assertThat(fields.get(this.owner.toString()).path("catalogChanged").asBoolean()).isFalse();
	}

	@Test
	void nameChangedSinceCapture() {
		int erp = erp();
		created(create(this.michael, this.solution, modified(erp, "ERP-Core 2", null, new ObjectNode[0])));
		this.jdbc.update("UPDATE entities SET name = 'ERP-Core 1.5' WHERE id = ?", erp);
		JsonNode name = fields(only()).get("name");
		assertThat(name.path("current").path("valueText").asString()).isEqualTo("ERP-Core 1.5");
		assertThat(name.path("catalogChanged").asBoolean()).isTrue();
	}

	// Requirement: Entity no longer in the catalog

	@Test
	void entityDeletedAfterCapture() {
		int crm = entity(this.application, "CRM-Legacy");
		int erp = erp();
		created(create(this.michael, this.solution, retiring(crm)));
		created(create(this.michael, this.solution, modified(erp, null, null, value(this.release, "v5"))));
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id IN (?, ?)", this.michaelId, crm,
				erp);
		JsonNode entries = diff(this.michael, this.solution, "").path("entries");
		JsonNode modified = entries.get(0);
		assertThat(modified.path("inCatalog").asBoolean()).isFalse();
		JsonNode release = fields(modified).get(this.release.toString());
		assertThat(release.path("current").isNull()).isTrue();
		assertThat(release.path("target").path("valueText").asString()).isEqualTo("v5");
		assertThat(release.path("catalogChanged").asBoolean()).isFalse();
		JsonNode retiring = entries.get(1);
		assertThat(retiring.path("inCatalog").asBoolean()).isFalse();
		assertThat(retiring.path("fields")).isEmpty();
	}

	// Requirement: Visibility in the diff

	@Test
	void restrictedValueRedacted() {
		int erp = erp();
		catalog(erp, this.cost, 900);
		created(create(this.michael, this.solution, modified(erp, null, null, value(this.cost, "1000"))));
		this.jdbc.update("UPDATE entity_property_values SET value_int = 950 WHERE entity_id = ? AND property_def_id = ?",
				erp, this.cost);
		// michael (solution-architect) may write but not read "Cost"
		JsonNode hidden = fields(only()).get(this.cost.toString());
		assertThat(hidden.path("redacted").asBoolean()).isTrue();
		assertThat(hidden.path("current").isNull()).isTrue();
		assertThat(hidden.path("target").isNull()).isTrue();
		assertThat(hidden.path("catalogChanged").asBoolean()).isFalse();
		TestSession finance = this.principals.personWithIdpRoles("solution-architect", "enterprise-architect");
		JsonNode shown = fields(diff(finance, this.solution, "").path("entries").get(0)).get(this.cost.toString());
		assertThat(shown.path("redacted").asBoolean()).isFalse();
		assertThat(shown.path("current").path("valueInt").asLong()).isEqualTo(950L);
		assertThat(shown.path("target").path("valueInt").asLong()).isEqualTo(1000L);
		assertThat(shown.path("catalogChanged").asBoolean()).isTrue();
	}

	@Test
	void connectionScopedValueOutsideTheScope() {
		int crm = entity(this.application, "CRM-Legacy");
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, 'B4')",
				crm, this.scoped);
		created(create(this.michael, this.solution, retiring(crm)));
		JsonNode scoped = fields(only()).get(this.scoped.toString());
		assertThat(scoped.path("redacted").asBoolean()).isTrue();
		assertThat(scoped.path("current").isNull()).isTrue();
	}

	private int erp() {
		int erp = entity(this.application, "ERP-Core");
		catalog(erp, this.owner, "IT");
		catalog(erp, this.release, "v4");
		return erp;
	}

	private void catalog(int entity, UUID property, String text) {
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, ?)",
				entity, property, text);
	}

	private void catalog(int entity, UUID property, long number) {
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES (?, ?, ?)",
				entity, property, number);
	}

	private void insert(String type, int count) {
		if (type.equals("new")) {
			this.jdbc.update("""
					INSERT INTO entity_deltas (id, solution_id, delta_type, metatype_id, name, created_by)
					SELECT gen_random_uuid(), ?::uuid, 'new', ?, 'Filler ' || n, ? FROM generate_series(1, ?) n""",
					this.solution, this.component, this.michaelId, count);
			return;
		}
		this.jdbc.update("""
				WITH e AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    SELECT ?, ? || ' ' || (n % 7), ? FROM generate_series(1, ?) n RETURNING id)
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				SELECT gen_random_uuid(), ?::uuid, ?, id, ? FROM e""", this.application, type, this.michaelId, count,
				this.solution, type, this.michaelId);
	}

	private JsonNode only() {
		JsonNode entries = diff(this.michael, this.solution, "").path("entries");
		assertThat(entries).hasSize(1);
		return entries.get(0);
	}

	/** The fields of an entry, keyed by "name", "description" or the property ID. */
	private static Map<String, JsonNode> fields(JsonNode entry) {
		Map<String, JsonNode> fields = new java.util.LinkedHashMap<>();
		entry.path("fields").forEach((field) -> fields.put(
				field.path("field").asString().equals("property") ? field.path("propertyDefId").asString()
						: field.path("field").asString(),
				field));
		return fields;
	}

	private MvcTestResult get(TestSession session, String solutionId, String query) {
		return this.mvc.get()
			.uri("/api/v1/solutions/" + solutionId + "/diff" + (query.isEmpty() ? "" : "?" + query))
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.exchange();
	}

	private JsonNode diff(TestSession session, String solutionId, String query) {
		MvcTestResult result = get(session, solutionId, query);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return json(result);
	}

	private static ObjectNode modified(int entityId, String name, String description, ObjectNode... values) {
		ObjectNode body = modification(name, description, values);
		body.put("deltaType", "modified").put("entityId", entityId);
		return body;
	}

	private static ObjectNode modification(String name, String description, ObjectNode... values) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		if (name != null) {
			body.put("name", name);
		}
		if (description != null) {
			body.put("description", description);
		}
		ArrayNode properties = body.putArray("properties");
		for (ObjectNode value : values) {
			properties.add(value);
		}
		return body;
	}

	private MvcTestResult create(TestSession session, String solutionId, ObjectNode body) {
		return this.mvc.post()
			.uri("/api/v1/solutions/" + solutionId + "/deltas")
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
	}

	private ObjectNode newEntity(String name, String description, ObjectNode... values) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("deltaType", "new");
		body.put("metatypeId", this.application.toString()).put("name", name);
		if (description != null) {
			body.put("description", description);
		}
		ArrayNode properties = body.putArray("properties");
		for (ObjectNode value : values) {
			properties.add(value);
		}
		return body;
	}

	private static ObjectNode retiring(int entityId) {
		return JsonMapper.shared().createObjectNode().put("deltaType", "retiring").put("entityId", entityId);
	}

	private static ObjectNode value(UUID property, String value) {
		return JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()).put("value", value);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return json(result);
	}

	private String solution(String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), ?, ?, ?)
				RETURNING id::text""", String.class, name + " " + suffix(), this.michaelId, this.michaelId);
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?) RETURNING id""", UUID.class,
				name + " " + suffix(), connection);
	}

	private UUID property(String name, String dataType, String mode, UUID scopingType, UUID refMetatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions
				    (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id, ref_metatype_id)
				VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", UUID.class, name + " " + suffix(),
				"p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), dataType, mode, scopingType,
				refMetatype);
	}

	private void map(UUID property, String necessity) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, ?, DATE '2026-01-01', 'organization')""", this.application, property, necessity);
	}

	private int entity(UUID type, String name) {
		return this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class, type,
				name, this.michaelId);
	}

	private long personOf(TestSession session) {
		return this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
	}

	private static JsonNode json(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-solution-diff").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

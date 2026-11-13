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

/** Spec solution/solution-deltas (US-040, US-042, REQ-040; design D1–D8 of us-040-entity-deltas). */
@SpringBootTest
@AutoConfigureMockMvc
class SolutionDeltasTests {

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

	private UUID dataFlow;

	private UUID owner;

	private UUID users;

	private UUID live;

	private UUID goLive;

	private UUID tier;

	private UUID hosting;

	private UUID cost;

	private UUID scoped;

	private UUID release;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = this.principals.personWithIdpRole("solution-architect");
		this.michaelId = personOf(this.michael);
		this.application = metatype("Application", false);
		this.component = metatype("Component", false);
		this.dataFlow = metatype("Data Flow", true);
		this.owner = property("Owner", "string", "public", null, null);
		this.users = property("Users", "integer", "public", null, null);
		this.live = property("Live", "boolean", "public", null, null);
		this.goLive = property("Go-live", "date", "public", null, null);
		this.tier = property("Tier", "enum", "public", null, null);
		this.jdbc.update("INSERT INTO property_enum_values (property_def_id, value) VALUES (?, 'gold'), (?, 'silver')",
				this.tier, this.tier);
		this.hosting = property("Hosted on", "reference", "public", null, this.component);
		this.cost = property("Cost", "integer", "role-restricted", null, null);
		this.jdbc.update("""
				INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES
				    (?, 'enterprise-architect', 'read'), (?, 'enterprise-architect', 'write')""", this.cost, this.cost);
		UUID scoping = metatype("Domain Assignment", true);
		this.scoped = property("Salary Band", "string", "connection-scoped", scoping, null);
		this.release = property("Release", "string", "public", null, null);
		map(this.owner, "mandatory");
		for (UUID property : List.of(this.release, this.users, this.live, this.goLive, this.tier, this.hosting, this.cost,
				this.scoped)) {
			map(property, "optional");
		}
		this.solution = solution("CRM Replacement");
	}

	// Requirement: Capture a new entity / Checks of a new entity

	@Test
	void newEntityWithEveryDataType() {
		int server = entity(this.component, "Server 1");
		JsonNode created = created(create(this.michael, this.solution, newEntity("Salesforce", "Sales CRM",
				value(this.owner, "Sales"), value(this.users, "250"), value(this.live, "true"),
				value(this.goLive, "2027-03-01"), value(this.tier, "gold"), value(this.hosting, Integer.toString(server)))));
		assertThat(created.path("deltaType").asString()).isEqualTo("new");
		assertThat(created.path("entityId").isNull()).isTrue();
		assertThat(created.path("name").asString()).isEqualTo("Salesforce");
		assertThat(created.path("metatypeId").asString()).isEqualTo(this.application.toString());
		assertThat(created.path("description").asString()).isEqualTo("Sales CRM");
		Map<String, JsonNode> values = byName(created.path("properties"));
		assertThat(values.get("Owner").path("valueText").asString()).isEqualTo("Sales");
		assertThat(values.get("Users").path("valueInt").asLong()).isEqualTo(250L);
		assertThat(values.get("Live").path("valueBool").asBoolean()).isTrue();
		assertThat(values.get("Go-live").path("valueDate").asString()).isEqualTo("2027-03-01");
		assertThat(values.get("Tier").path("valueEnum").asString()).isEqualTo("gold");
		assertThat(values.get("Hosted on").path("valueRef").path("id").asInt()).isEqualTo(server);
		assertThat(values.get("Hosted on").path("valueRef").path("name").asString()).isEqualTo("Server 1");
		// read back through the list; no entity is written and the baseline does not list it
		JsonNode listed = list(this.michael, this.solution).path("items").get(0);
		assertThat(listed.path("id").asString()).isEqualTo(created.path("id").asString());
		assertThat(listed.path("properties")).hasSize(6);
		assertThat(count("SELECT count(*) FROM entities WHERE name = 'Salesforce'")).isZero();
	}

	@Test
	void unknownAndConnectionMetatype() {
		ObjectNode unknown = newEntity("Salesforce", null, value(this.owner, "Sales"));
		unknown.put("metatypeId", UUID.randomUUID().toString());
		assertInvalid(create(this.michael, this.solution, unknown), "metatypeId");
		ObjectNode connection = newEntity("Orders", null);
		connection.put("metatypeId", this.dataFlow.toString());
		assertInvalid(create(this.michael, this.solution, connection), "metatypeId");
		ObjectNode missing = newEntity("Salesforce", null, value(this.owner, "Sales"));
		missing.remove("metatypeId");
		assertInvalid(create(this.michael, this.solution, missing), "metatypeId");
		assertThat(deltas()).isZero();
	}

	@Test
	void invalidValueAndMissingMandatory() {
		JsonNode problem = assertInvalid(
				create(this.michael, this.solution, newEntity("Salesforce", null, value(this.owner, "Sales"),
						value(this.users, "many"))),
				"properties[1].value");
		assertThat(problem.path("errors").get(0).path("message").asString()).isNotBlank();
		assertInvalid(create(this.michael, this.solution, newEntity("Salesforce", null, value(this.tier, "bronze"),
				value(this.owner, "Sales"))), "properties[0].value");
		assertInvalid(create(this.michael, this.solution, newEntity("Salesforce", null)), "properties");
		assertThat(deltas()).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "255, 201", "256, 422", "0, 422" })
	void lengthOfTheName(int length, int status) {
		String name = (length == 0) ? "   " : "n".repeat(length);
		MvcTestResult result = create(this.michael, this.solution, newEntity(name, null, value(this.owner, "Sales")));
		assertThat(result).hasStatus(status);
		if (status == 422) {
			assertInvalid(result, "name");
		}
	}

	@ParameterizedTest
	@CsvSource({ "2000, 201", "2001, 422" })
	void lengthOfTheDescription(int length, int status) {
		MvcTestResult result = create(this.michael, this.solution,
				newEntity("Described", "d".repeat(length), value(this.owner, "Sales")));
		assertThat(result).hasStatus(status);
		if (status == 422) {
			assertInvalid(result, "description");
		}
	}

	// Requirement: Visibility of the values of a new entity

	@Test
	void restrictedPropertyNeedsAWriteRole() {
		assertThat(create(this.michael, this.solution,
				newEntity("Salesforce", null, value(this.owner, "Sales"), value(this.cost, "100"))))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deltas()).isZero();
		TestSession finance = this.principals.personWithIdpRoles("solution-architect", "enterprise-architect");
		created(create(finance, this.solution,
				newEntity("Salesforce", null, value(this.owner, "Sales"), value(this.cost, "100"))));
		// redacted for a reader without a read role, shown to one with it
		JsonNode hidden = byName(list(this.michael, this.solution).path("items").get(0).path("properties")).get("Cost");
		assertThat(hidden.path("redacted").asBoolean()).isTrue();
		assertThat(hidden.path("valueInt").isNull()).isTrue();
		JsonNode shown = byName(list(finance, this.solution).path("items").get(0).path("properties")).get("Cost");
		assertThat(shown.path("redacted").asBoolean()).isFalse();
		assertThat(shown.path("valueInt").asLong()).isEqualTo(100L);
	}

	@Test
	void connectionScopedPropertyCannotBeSet() {
		TestSession finance = this.principals.personWithIdpRoles("solution-architect", "enterprise-architect");
		assertThat(create(finance, this.solution,
				newEntity("Salesforce", null, value(this.owner, "Sales"), value(this.scoped, "B4"))))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deltas()).isZero();
	}

	// Requirement: Retire an entity of the baseline

	@Test
	void retireAnEntity() {
		int crm = entity(this.application, "CRM-Legacy");
		JsonNode created = created(create(this.michael, this.solution, retiring(crm)));
		assertThat(created.path("deltaType").asString()).isEqualTo("retiring");
		assertThat(created.path("entityId").asInt()).isEqualTo(crm);
		assertThat(created.path("name").asString()).isEqualTo("CRM-Legacy");
		assertThat(created.path("metatypeName").asString()).startsWith("Application");
		assertThat(created.path("properties")).isEmpty();
		// the list shows the current name of the entity
		this.jdbc.update("UPDATE entities SET name = 'CRM-Legacy 2' WHERE id = ?", crm);
		assertThat(list(this.michael, this.solution).path("items").get(0).path("name").asString())
			.isEqualTo("CRM-Legacy 2");
	}

	@Test
	void entityNotInTheBaseline() {
		int deleted = entity(this.application, "Old");
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.michaelId, deleted);
		int flow = entity(this.dataFlow, "Orders");
		for (int id : new int[] { 999_999, deleted, flow }) {
			JsonNode problem = assertInvalid(create(this.michael, this.solution, retiring(id)), "entityId");
			assertThat(problem.path("detail").asString()).isEqualTo("Entity not found in the current landscape");
		}
		ObjectNode named = retiring(entity(this.application, "CRM-Legacy"));
		named.put("name", "Other");
		assertInvalid(create(this.michael, this.solution, named), "name");
		assertThat(deltas()).isZero();
	}

	// Requirement: One delta per entity

	@Test
	void oneDeltaPerEntity() {
		int crm = entity(this.application, "CRM-Legacy");
		created(create(this.michael, this.solution, retiring(crm)));
		JsonNode twice = assertInvalid(create(this.michael, this.solution, retiring(crm)), "entityId");
		assertThat(twice.path("detail").asString()).isEqualTo("A delta already exists for this entity in this solution");
		created(create(this.michael, this.solution, newEntity("Salesforce", null, value(this.owner, "Sales"))));
		JsonNode sameName = assertInvalid(
				create(this.michael, this.solution, newEntity("salesforce", null, value(this.owner, "Sales"))), "name");
		assertThat(sameName.path("detail").asString())
			.isEqualTo("A delta already exists for this entity in this solution");
		// another metatype, and another solution
		ObjectNode component = newEntity("Salesforce", null);
		component.put("metatypeId", this.component.toString());
		created(create(this.michael, this.solution, component));
		created(create(this.michael, solution("Sales Portal"), retiring(crm)));
		assertThat(deltas()).isEqualTo(3);
	}

	// Requirement: Any number of deltas (owner decision 2026-11-13; us-043-diff-view D0)

	@Test
	void noLimitOfDeltas() {
		fillers(this.solution, 999);
		// the 1000th, the former limit, and the 1001st are both accepted
		ObjectNode last = newEntity("Last", null);
		last.put("metatypeId", this.component.toString());
		created(create(this.michael, this.solution, last));
		ObjectNode beyond = newEntity("Beyond", null);
		beyond.put("metatypeId", this.component.toString());
		created(create(this.michael, this.solution, beyond));
		assertThat(deltas()).isEqualTo(1001);
		JsonNode page = list(this.michael, this.solution);
		assertThat(page.path("items")).hasSize(50);
		assertThat(page.path("page").path("totalElements").asInt()).isEqualTo(1001);
	}

	// Requirement: Paging of the deltas

	@Test
	void pagesByCursor() {
		// 60 deltas of all three types, some names equal across types
		fillers(this.solution, 20);
		for (int n = 1; n <= 20; n++) {
			created(create(this.michael, this.solution, retiring(entity(this.application, "Filler " + n))));
		}
		for (int n = 1; n <= 20; n++) {
			int entity = entity(this.application, "Mod " + (n % 7));
			created(create(this.michael, this.solution, modified(entity, null, null, value(this.owner, "IT"), value(this.release, "v" + n))));
		}
		JsonNode byPage = page(this.michael, this.solution, "page=0&pageSize=60");
		List<String> expected = new ArrayList<>();
		byPage.path("items").forEach((item) -> expected.add(item.path("id").asString()));
		assertThat(expected).hasSize(60).doesNotHaveDuplicates();

		List<String> ids = new ArrayList<>();
		String cursor = null;
		for (int page = 0; page < 3; page++) {
			JsonNode result = page(this.michael, this.solution,
					"page=" + page + "&pageSize=20" + ((cursor != null) ? "&cursor=" + cursor : ""));
			result.path("items").forEach((item) -> ids.add(item.path("id").asString()));
			assertThat(result.path("counts").path("new").asInt()).isEqualTo(20);
			assertThat(result.path("counts").path("modified").asInt()).isEqualTo(20);
			assertThat(result.path("counts").path("retiring").asInt()).isEqualTo(20);
			assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(60);
			cursor = result.path("nextCursor").isNull() ? null : result.path("nextCursor").asString();
		}
		assertThat(ids).containsExactlyElementsOf(expected);
		assertThat(cursor).isNull();
	}

	@Test
	void emptyPage() {
		JsonNode result = list(this.michael, this.solution);
		assertThat(result.path("items")).isEmpty();
		assertThat(result.path("counts").path("new").asInt()).isZero();
		assertThat(result.path("page").path("totalElements").asInt()).isZero();
		assertThat(result.path("nextCursor").isNull()).isTrue();
	}

	@Test
	void cursorOfAnotherQuery() {
		fillers(this.solution, 3);
		String other = solution("Sales Portal");
		fillers(other, 3);
		String cursor = page(this.michael, other, "page=0&pageSize=2").path("nextCursor").asString();
		String own = page(this.michael, this.solution, "page=0&pageSize=2").path("nextCursor").asString();
		for (String query : List.of("page=1&pageSize=2&cursor=" + cursor, "page=1&pageSize=2&cursor=" + own + "x",
				"pageSize=2&cursor=" + own, "page=1&pageSize=3&cursor=" + own)) {
			MvcTestResult result = getQuery(this.michael, this.solution, query);
			assertThat(result).as(query).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(content(result)).contains("cursor");
		}
	}

	@ParameterizedTest
	@CsvSource({ "1, 200", "200, 200", "0, 400", "201, 400" })
	void limitsOfThePageSize(int pageSize, int status) {
		fillers(this.solution, 2);
		MvcTestResult result = getQuery(this.michael, this.solution, "pageSize=" + pageSize);
		assertThat(result).hasStatus(status);
		if (status == 200) {
			assertThat(json(result).path("items")).hasSize(Math.min(pageSize, 2));
		}
	}

	// Requirement: Completed solution

	@ParameterizedTest
	@ValueSource(strings = { "implemented", "archived" })
	void completedSolution(String status) throws SQLException {
		int crm = entity(this.application, "CRM-Legacy");
		String delta = created(create(this.michael, this.solution, retiring(crm))).path("id").asString();
		setStatus(this.solution, status);
		MvcTestResult capture = create(this.michael, this.solution,
				newEntity("Salesforce", null, value(this.owner, "Sales")));
		assertThat(capture).hasStatus(HttpStatus.CONFLICT);
		assertThat(json(capture).path("detail").asString())
			.isEqualTo("This solution is completed and can no longer be changed");
		assertThat(json(capture).path("type").asString()).isEqualTo("urn:oea:problem:solution-completed");
		assertThat(delete(this.michael, this.solution, delta)).hasStatus(HttpStatus.CONFLICT);
		assertThat(deltas()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "proposed", "approved", "in-progress" })
	void openSolution(String status) throws SQLException {
		setStatus(this.solution, status);
		created(create(this.michael, this.solution, retiring(entity(this.application, "CRM-Legacy"))));
	}

	// Requirement: Read the deltas

	@Test
	void orderOfTheList() {
		created(create(this.michael, this.solution, retiring(entity(this.application, "alpha"))));
		created(create(this.michael, this.solution, newEntity("Zeta", null, value(this.owner, "x"))));
		created(create(this.michael, this.solution, newEntity("beta", null, value(this.owner, "x"))));
		created(create(this.michael, this.solution, retiring(entity(this.application, "Gamma"))));
		List<String> names = new ArrayList<>();
		list(this.michael, this.solution).path("items").forEach((item) -> names.add(item.path("name").asString()));
		assertThat(names).containsExactly("beta", "Zeta", "alpha", "Gamma");
		assertThat(list(this.michael, solution("Empty")).path("items")).isEmpty();
	}

	@Test
	void unknownSolution() {
		String unknown = UUID.randomUUID().toString();
		assertThat(get(this.michael, unknown)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(create(this.michael, unknown, retiring(entity(this.application, "CRM-Legacy"))))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(delete(this.michael, unknown, UUID.randomUUID().toString())).hasStatus(HttpStatus.NOT_FOUND);
	}

	// Requirement: Remove a delta

	@Test
	void removeADelta() {
		int crm = entity(this.application, "CRM-Legacy");
		String delta = created(create(this.michael, this.solution, retiring(crm))).path("id").asString();
		String valued = created(create(this.michael, this.solution,
				newEntity("Salesforce", null, value(this.owner, "Sales"), value(this.users, "1"))))
			.path("id")
			.asString();
		assertThat(delete(this.michael, this.solution, delta)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(delete(this.michael, this.solution, valued)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count("SELECT count(*) FROM entity_delta_property_values WHERE delta_id = '" + valued + "'")).isZero();
		assertThat(list(this.michael, this.solution).path("items")).isEmpty();
		created(create(this.michael, this.solution, retiring(crm)));
	}

	@Test
	void deltaOfAnotherSolution() {
		String other = solution("Sales Portal");
		String delta = created(create(this.michael, other, retiring(entity(this.application, "CRM-Legacy"))))
			.path("id")
			.asString();
		assertThat(delete(this.michael, this.solution, delta)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(delete(this.michael, this.solution, UUID.randomUUID().toString())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(list(this.michael, other).path("items")).hasSize(1);
	}

	// Requirement: Permissions for deltas

	@Test
	void permissions() {
		int crm = entity(this.application, "CRM-Legacy");
		TestSession other = this.principals.personWithIdpRole("solution-architect");
		String delta = created(create(other, this.solution, retiring(crm))).path("id").asString();
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		assertThat(create(viewer, this.solution, retiring(entity(this.application, "ERP-Core"))))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(delete(viewer, this.solution, delta)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(get(viewer, this.solution)).hasStatus(HttpStatus.OK);
		TestSession reader = this.principals.personWithIdpRole("portal-reader");
		assertThat(get(reader, this.solution)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deltas()).isEqualTo(1);
	}

	// us-041-modified-delta – Requirements: Capture a modification, Before values recorded by the
	// server, Checks of a modification, Visibility of a modification, Edit a modification, Audit of
	// modifications, One delta per entity

	@Test
	void modifyAPropertyRecordsTheCatalogValue() {
		int erp = erp();
		JsonNode created = created(create(this.michael, this.solution, modified(erp, null, null,
				value(this.release, "v5"))));
		assertThat(created.path("deltaType").asString()).isEqualTo("modified");
		assertThat(created.path("entityId").asInt()).isEqualTo(erp);
		assertThat(created.path("name").asString()).isEqualTo("ERP-Core");
		JsonNode change = created.path("changes").get(0);
		assertThat(change.path("field").asString()).isEqualTo("property");
		assertThat(change.path("propertyDefId").asString()).isEqualTo(this.release.toString());
		assertThat(change.path("before").path("valueText").asString()).isEqualTo("v4");
		assertThat(change.path("after").path("valueText").asString()).isEqualTo("v5");
		// the catalog is unchanged, and the before value stays when it changes later
		assertThat(catalogText(erp, this.release)).isEqualTo("v4");
		this.jdbc.update("UPDATE entity_property_values SET value_text = 'v4.1' WHERE entity_id = ? AND property_def_id = ?",
				erp, this.release);
		JsonNode listed = list(this.michael, this.solution).path("items").get(0);
		assertThat(listed.path("changes").get(0).path("before").path("valueText").asString()).isEqualTo("v4");
	}

	@Test
	void renameDescribeAndRemoveAValue() {
		int erp = erp();
		JsonNode created = created(create(this.michael, this.solution, modified(erp, "ERP-Core 2", "Core ERP",
				value(this.users, ""), value(this.release, "v5"))));
		List<String> fields = new ArrayList<>();
		created.path("changes").forEach((change) -> fields.add(change.path("field").asString()));
		assertThat(fields).containsExactly("name", "description", "property", "property");
		JsonNode name = created.path("changes").get(0);
		assertThat(name.path("before").path("valueText").asString()).isEqualTo("ERP-Core");
		assertThat(name.path("after").path("valueText").asString()).isEqualTo("ERP-Core 2");
		// properties by name: Release, then Users; removing Users has no after value
		JsonNode users = created.path("changes").get(3);
		assertThat(users.path("propertyDefId").asString()).isEqualTo(this.users.toString());
		assertThat(users.path("before").path("valueInt").asLong()).isEqualTo(250L);
		assertThat(users.path("after").isNull()).isTrue();
	}

	@Test
	void modificationOfAnEntityNotInTheBaseline() {
		int deleted = entity(this.application, "Old");
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.michaelId, deleted);
		int flow = entity(this.dataFlow, "Orders");
		for (int id : new int[] { 999_999, deleted, flow }) {
			JsonNode problem = assertInvalid(
					create(this.michael, this.solution, modified(id, null, null, value(this.release, "v5"))), "entityId");
			assertThat(problem.path("detail").asString()).isEqualTo("Entity not found in the current landscape");
		}
		ObjectNode withMetatype = modified(erp(), null, null, value(this.release, "v5"));
		withMetatype.put("metatypeId", this.component.toString());
		assertInvalid(create(this.michael, this.solution, withMetatype), "metatypeId");
		assertThat(deltas()).isZero();
	}

	@Test
	void nothingChanges() {
		int erp = erp();
		assertInvalid(create(this.michael, this.solution, modified(erp, null, null, value(this.release, "v4"))),
				"properties");
		assertInvalid(create(this.michael, this.solution, modified(erp, "ERP-Core", null)), "properties");
		assertInvalid(create(this.michael, this.solution, modified(erp, null, null)), "properties");
		assertThat(deltas()).isZero();
	}

	@Test
	void checksOfAModification() {
		int erp = erp();
		assertInvalid(create(this.michael, this.solution, modified(erp, null, null, value(this.users, "many"))),
				"properties[0].value");
		// Owner is mandatory: its value cannot be removed
		assertInvalid(create(this.michael, this.solution, modified(erp, null, null, value(this.owner, ""))),
				"properties");
		assertThat(deltas()).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "255, 201", "256, 422", "0, 422" })
	void lengthOfTheNewName(int length, int status) {
		String name = (length == 0) ? "   " : "n".repeat(length);
		MvcTestResult result = create(this.michael, this.solution, modified(erp(), name, null));
		assertThat(result).hasStatus(status);
		if (status == 422) {
			assertInvalid(result, "name");
		}
	}

	@ParameterizedTest
	@CsvSource({ "2000, 201", "2001, 422" })
	void lengthOfTheNewDescription(int length, int status) {
		MvcTestResult result = create(this.michael, this.solution, modified(erp(), null, "d".repeat(length)));
		assertThat(result).hasStatus(status);
		if (status == 422) {
			assertInvalid(result, "description");
		}
	}

	@Test
	void visibilityOfAModification() {
		int erp = erp();
		assertThat(create(this.michael, this.solution, modified(erp, null, null, value(this.cost, "100"))))
			.hasStatus(HttpStatus.FORBIDDEN);
		TestSession finance = this.principals.personWithIdpRoles("solution-architect", "enterprise-architect");
		// connection-scoped outside the writer's scope (no account link)
		assertThat(create(finance, this.solution, modified(erp, null, null, value(this.scoped, "B4"))))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deltas()).isZero();
		String delta = created(create(finance, this.solution, modified(erp, null, null, value(this.cost, "120"))))
			.path("id")
			.asString();
		// redacted for a reader without a read role, shown to one with it
		JsonNode hidden = list(this.michael, this.solution).path("items").get(0).path("changes").get(0);
		assertThat(hidden.path("redacted").asBoolean()).isTrue();
		assertThat(hidden.path("before").isNull()).isTrue();
		assertThat(hidden.path("after").isNull()).isTrue();
		JsonNode shown = list(finance, this.solution).path("items").get(0).path("changes").get(0);
		assertThat(shown.path("redacted").asBoolean()).isFalse();
		assertThat(shown.path("before").isNull()).isTrue();
		assertThat(shown.path("after").path("valueInt").asLong()).isEqualTo(120L);
		// an edit is checked as well
		assertThat(put(this.michael, this.solution, delta, modification(null, null, value(this.cost, "130"))))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void editKeepsRecordedBeforeValues() {
		int erp = erp();
		String delta = created(create(this.michael, this.solution, modified(erp, null, null,
				value(this.release, "v5"), value(this.live, "true"))))
			.path("id")
			.asString();
		this.jdbc.update("UPDATE entity_property_values SET value_text = 'v4.1' WHERE entity_id = ? AND property_def_id = ?",
				erp, this.release);
		MvcTestResult edited = put(this.michael, this.solution, delta,
				modification(null, null, value(this.release, "v6"), value(this.users, "300")));
		assertThat(edited).as(() -> content(edited)).hasStatus(HttpStatus.OK);
		JsonNode changes = json(edited).path("changes");
		assertThat(changes).hasSize(2);
		// Release keeps v4, Users records its current value, Live is no longer sent
		assertThat(changes.get(0).path("propertyDefId").asString()).isEqualTo(this.release.toString());
		assertThat(changes.get(0).path("before").path("valueText").asString()).isEqualTo("v4");
		assertThat(changes.get(0).path("after").path("valueText").asString()).isEqualTo("v6");
		assertThat(changes.get(1).path("before").path("valueInt").asLong()).isEqualTo(250L);
		assertThat(changes.get(1).path("after").path("valueInt").asLong()).isEqualTo(300L);
		assertThat(json(edited).path("updatedAt").isNull()).isFalse();
		// back to the recorded before value is no change
		assertInvalid(put(this.michael, this.solution, delta, modification(null, null, value(this.release, "v4"))),
				"properties");
	}

	@Test
	void editKeepsAChangeTheEditorMayNotRead() {
		int erp = erp();
		TestSession finance = this.principals.personWithIdpRoles("solution-architect", "enterprise-architect");
		String delta = created(create(finance, this.solution, modified(erp, null, null, value(this.release, "v5"),
				value(this.cost, "120"))))
			.path("id")
			.asString();
		// Michael cannot read Cost, so he cannot resend it; the edit keeps it as recorded
		assertThat(put(this.michael, this.solution, delta, modification(null, null, value(this.release, "v6"))))
			.hasStatus(HttpStatus.OK);
		JsonNode changes = list(finance, this.solution).path("items").get(0).path("changes");
		assertThat(changes).hasSize(2);
		List<String> after = new ArrayList<>();
		changes.forEach((change) -> after.add(change.path("after").path("valueText").asString("")
				+ change.path("after").path("valueInt").asString("")));
		assertThat(after).containsExactlyInAnyOrder("v6", "120");
		// with only the hidden change left, the edit is still valid
		assertThat(put(this.michael, this.solution, delta, modification(null, null, value(this.release, "v4"))))
			.hasStatus(HttpStatus.OK);
		assertThat(list(finance, this.solution).path("items").get(0).path("changes")).hasSize(1);
	}

	@Test
	void editRules() throws SQLException {
		int crm = entity(this.application, "CRM-Legacy");
		String retiring = created(create(this.michael, this.solution, retiring(crm))).path("id").asString();
		assertInvalid(put(this.michael, this.solution, retiring, modification("CRM 2", null)), "deltaType");
		String fresh = created(create(this.michael, this.solution, newEntity("Salesforce", null, value(this.owner, "x"))))
			.path("id")
			.asString();
		assertInvalid(put(this.michael, this.solution, fresh, modification("Salesforce 2", null)), "deltaType");
		String modified = created(create(this.michael, this.solution, modified(erp(), "ERP 2", null)))
			.path("id")
			.asString();
		assertThat(put(this.michael, solution("Other"), modified, modification("ERP 3", null)))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(put(this.michael, UUID.randomUUID().toString(), modified, modification("ERP 3", null)))
			.hasStatus(HttpStatus.NOT_FOUND);
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		assertThat(put(viewer, this.solution, modified, modification("ERP 3", null))).hasStatus(HttpStatus.FORBIDDEN);
		setStatus(this.solution, "implemented");
		assertThat(put(this.michael, this.solution, modified, modification("ERP 3", null))).hasStatus(HttpStatus.CONFLICT);
		assertThat(json(get(this.michael, this.solution)).path("items").get(1).path("changes").get(0).path("after")
			.path("valueText")
			.asString()).isEqualTo("ERP 2");
	}

	@Test
	void retiredEntityCannotBeModified() {
		int erp = erp();
		created(create(this.michael, this.solution, retiring(erp)));
		JsonNode problem = assertInvalid(create(this.michael, this.solution, modified(erp, "ERP 2", null)), "entityId");
		assertThat(problem.path("detail").asString()).isEqualTo("A delta already exists for this entity in this solution");
	}

	@Test
	void orderNewModifiedRetiring() {
		created(create(this.michael, this.solution, retiring(entity(this.application, "Alpha"))));
		created(create(this.michael, this.solution, modified(erp(), "ERP 2", null)));
		created(create(this.michael, this.solution, newEntity("Zeta", null, value(this.owner, "x"))));
		List<String> types = new ArrayList<>();
		list(this.michael, this.solution).path("items").forEach((item) -> types.add(item.path("deltaType").asString()));
		assertThat(types).containsExactly("new", "modified", "retiring");
	}

	@Test
	void auditOfModifications() {
		int erp = erp();
		String delta = created(create(this.michael, this.solution, modified(erp, null, null, value(this.release, "v5"))))
			.path("id")
			.asString();
		assertThat(put(this.michael, this.solution, delta, modification(null, null, value(this.release, "v6"))))
			.hasStatus(HttpStatus.OK);
		for (String type : List.of("solution.delta-added", "solution.delta-updated")) {
			Map<String, Object> event = event(type);
			assertThat(event.get("type")).isEqualTo("modified");
			assertThat(event.get("entity")).isEqualTo(Integer.toString(erp));
			assertThat((String) event.get("details")).contains("Release").doesNotContain("v5").doesNotContain("v6");
		}
		int before = count("SELECT count(*) FROM audit.events WHERE event_type = 'solution.delta-updated'");
		assertInvalid(put(this.michael, this.solution, delta, modification(null, null, value(this.users, "x"))),
				"properties[0].value");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'solution.delta-updated'"))
			.isEqualTo(before);
	}

	/** ERP-Core with Owner "IT", Release "v4" and Users 250. */
	private int erp() {
		int erp = entity(this.application, "ERP-Core");
		this.jdbc.update("""
				INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, 'IT'), (?, ?, 'v4')""",
				erp, this.owner, erp, this.release);
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES (?, ?, 250)",
				erp, this.users);
		return erp;
	}

	private String catalogText(int entity, UUID property) {
		return this.jdbc.queryForObject(
				"SELECT value_text FROM entity_property_values WHERE entity_id = ? AND property_def_id = ?", String.class,
				entity, property);
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

	private MvcTestResult put(TestSession session, String solutionId, String deltaId, ObjectNode body) {
		return this.mvc.put()
			.uri("/api/v1/solutions/" + solutionId + "/deltas/" + deltaId)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
	}

	// Requirement: Audit of deltas

	@Test
	void audit() {
		created(create(this.michael, this.solution,
				newEntity("Salesforce", null, value(this.owner, "Sales"), value(this.users, "250"))));
		Map<String, Object> added = event("solution.delta-added");
		assertThat(added.get("type")).isEqualTo("new");
		assertThat(added.get("name")).isEqualTo("Salesforce");
		assertThat(added.get("metatype")).isEqualTo(this.application.toString());
		assertThat((String) added.get("details")).doesNotContain("250").doesNotContain("\"Sales\"");
		int crm = entity(this.application, "CRM-Legacy");
		String delta = created(create(this.michael, this.solution, retiring(crm))).path("id").asString();
		assertThat(delete(this.michael, this.solution, delta)).hasStatus(HttpStatus.NO_CONTENT);
		Map<String, Object> removed = event("solution.delta-removed");
		assertThat(removed.get("type")).isEqualTo("retiring");
		assertThat(removed.get("entity")).isEqualTo(Integer.toString(crm));
		assertThat(removed.get("delta")).isEqualTo(delta);
		int before = count("SELECT count(*) FROM audit.events WHERE event_type = 'solution.delta-added'");
		assertInvalid(create(this.michael, this.solution, newEntity("Rejected", null)), "properties");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'solution.delta-added'"))
			.isEqualTo(before);
	}

	private Map<String, Object> event(String type) {
		return this.jdbc.queryForMap("""
				SELECT details->>'deltaType' AS type, details->>'name' AS name, details->>'metatypeId' AS metatype,
				       details->>'entityId' AS entity, details->>'deltaId' AS delta, details::text AS details
				FROM audit.events WHERE event_type = ? AND target_id = ? ORDER BY id DESC LIMIT 1""", type,
				this.solution);
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

	private MvcTestResult delete(TestSession session, String solutionId, String deltaId) {
		return this.mvc.delete()
			.uri("/api/v1/solutions/" + solutionId + "/deltas/" + deltaId)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
	}

	private MvcTestResult get(TestSession session, String solutionId) {
		return this.mvc.get()
			.uri("/api/v1/solutions/" + solutionId + "/deltas")
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.exchange();
	}

	private void fillers(String solutionId, int count) {
		this.jdbc.update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, metatype_id, name, created_by)
				SELECT gen_random_uuid(), ?::uuid, 'new', ?, 'Filler ' || n, ? FROM generate_series(1, ?) n""",
				solutionId, this.component, this.michaelId, count);
	}

	private MvcTestResult getQuery(TestSession session, String solutionId, String query) {
		return this.mvc.get()
			.uri("/api/v1/solutions/" + solutionId + "/deltas?" + query)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.exchange();
	}

	private JsonNode page(TestSession session, String solutionId, String query) {
		MvcTestResult result = getQuery(session, solutionId, query);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return json(result);
	}

	private JsonNode list(TestSession session, String solutionId) {
		MvcTestResult result = get(session, solutionId);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return json(result);
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

	private static JsonNode assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		JsonNode problem = json(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:validation-failed");
		List<String> fields = new ArrayList<>();
		problem.path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).contains(field);
		return problem;
	}

	private static Map<String, JsonNode> byName(JsonNode properties) {
		Map<String, JsonNode> byName = new java.util.HashMap<>();
		properties.forEach((property) -> byName.put(property.path("propertyName").asString().replaceAll(" [0-9a-f]{8}$", ""),
				property));
		return byName;
	}

	private int deltas() {
		return count("SELECT count(*) FROM entity_deltas WHERE solution_id = '" + this.solution + "'");
	}

	private String solution(String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), ?, ?, ?)
				RETURNING id::text""", String.class, name + " " + suffix(), this.michaelId, this.michaelId);
	}

	private static void setStatus(String solutionId, String status) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.execute("UPDATE solutions SET status = '" + status + "' WHERE id = '" + solutionId + "'");
		}
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

	private int count(String sql) {
		Integer count = this.jdbc.queryForObject(sql, Integer.class);
		return (count != null) ? count : 0;
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
			return Files.createTempDirectory("oea-solution-deltas").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

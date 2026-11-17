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
import java.util.ArrayList;
import java.util.List;
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

/**
 * Specs solution/solution-go-live (status, resolution, permission and blocking of open issues),
 * solution/solution-deltas, repository/entity-batch-write, repository/entity-restore,
 * catalog/catalog-query and repository/entity-history of us-153-open-issues (US-153). An
 * implemented solution "CRM Replacement" left two open issues: "Salesforce" misses the mandatory
 * "Contact", and "ERP-Core" has 5000 "Users" where at most 1000 are accepted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenIssuesTests {

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

	private UUID contact;

	private UUID users;

	private int salesforce;

	private int erp;

	private UUID missing;

	private UUID tooMany;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = this.principals.personWithIdpRole("solution-architect");
		this.michaelId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.michael.sessionId());
		this.application = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, false) RETURNING id""", UUID.class,
				"Application " + suffix());
		this.contact = property("Contact", "string");
		this.users = property("Users", "integer");
		this.jdbc.update("UPDATE property_definitions SET max_value = 1000 WHERE id = ?", this.users);
		map(this.contact, "mandatory");
		map(this.users, "optional");
		this.salesforce = entity("Salesforce", "sf-" + suffix());
		this.erp = entity("ERP-Core", "erp-" + suffix());
		this.jdbc.update(
				"INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES (?, ?, 5000)", this.erp,
				this.users);
		this.solution = solution("CRM Replacement", "implemented");
		this.missing = issue(this.solution, this.salesforce, this.contact, "mandatory-missing");
		this.tooMany = issue(this.solution, this.erp, this.users, "invalid-value");
	}

	// Requirement: Status of an open issue

	@Test
	void openIssuesAreListedWithTheirStatus() {
		JsonNode issues = ok(list(this.michael));

		assertThat(statuses(issues)).containsExactlyInAnyOrder("mandatory-missing open null",
				"invalid-value open null");
		assertThat(solutionIssueCount()).isEqualTo(2);
	}

	@Test
	void resolvedAutomaticallyWhenThePropertyBecomesOptional() {
		this.jdbc.update("UPDATE metatype_property_mappings SET necessity = 'optional' WHERE property_def_id = ?",
				this.contact);

		assertThat(statuses(ok(list(this.michael)))).contains("mandatory-missing resolved automatic");
		assertThat(solutionIssueCount()).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({ "4999, open", "5000, resolved" })
	void invalidValueHoldsUntilTheMaximumAcceptsIt(int maximum, String status) {
		this.jdbc.update("UPDATE property_definitions SET max_value = ? WHERE id = ?", maximum, this.users);

		assertThat(statuses(ok(list(this.michael)))).anyMatch((entry) -> entry.startsWith("invalid-value " + status));
	}

	// Requirement: Resolve open issues

	@Test
	void resolveWithAValidValue() {
		JsonNode issues = ok(resolve(this.michael, entry(this.missing, "sales@example.com")));

		assertThat(statuses(issues)).contains("mandatory-missing resolved value");
		assertThat(text(this.salesforce, this.contact)).isEqualTo("sales@example.com");
		assertThat(version(this.salesforce)).isEqualTo(3);
		JsonNode resolved = find(issues, this.missing);
		assertThat(resolved.path("resolvedBy").path("id").asLong()).isEqualTo(this.michaelId);
		assertThat(resolved.path("resolvedAt").isNull()).isFalse();
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events WHERE event_type = 'solution.open-issues-resolved' AND target_id = ?""",
				Integer.class, this.solution)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events WHERE event_type = 'entity.updated' AND target_id = ?""", Integer.class,
				Integer.toString(this.salesforce))).isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({ "1000, 200", "1001, 422" })
	void boundaryOfTheMaximum(String value, int expected) {
		MvcTestResult result = resolve(this.michael, entry(this.tooMany, value));

		assertThat(result).as(() -> content(result)).hasStatus(expected);
		assertThat(integer(this.erp, this.users)).isEqualTo((expected == 200) ? Long.valueOf(value) : 5000L);
	}

	@Test
	void removeAnOptionalValue() {
		JsonNode issues = ok(resolve(this.michael, removal(this.tooMany)));

		assertThat(statuses(issues)).contains("invalid-value resolved removed");
		assertThat(integer(this.erp, this.users)).isNull();
	}

	@Test
	void removeAMandatoryValue() {
		MvcTestResult result = resolve(this.michael, removal(this.missing));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(json(result).path("invalidFields").toString()).contains(this.missing.toString());
	}

	@Test
	void issueAlreadyResolved() {
		ok(resolve(this.michael, removal(this.tooMany)));

		MvcTestResult again = resolve(this.michael, removal(this.tooMany));

		assertThat(again).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(json(again).path("invalidFields").toString()).contains(this.tooMany.toString());
	}

	@Test
	void issueOfAnotherSolution() {
		String other = solution("ERP Extension", "implemented");
		int crm = entity("CRM-Legacy", "crm-" + suffix());
		UUID foreign = issue(other, crm, this.contact, "mandatory-missing");

		MvcTestResult result = resolve(this.michael, entry(foreign, "x"));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(text(crm, this.contact)).isEqualTo("it@example.com");
	}

	@Test
	void rejectionChangesNothing() {
		MvcTestResult result = resolve(this.michael, entry(this.missing, "sales@example.com"),
				entry(this.tooMany, "1001"));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(text(this.salesforce, this.contact)).isNull();
		assertThat(statuses(ok(list(this.michael)))).allMatch((entry) -> entry.contains(" open "));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 200 })
	void listSizesAccepted(int size) {
		List<ObjectNode> entries = new ArrayList<>();
		entries.add(removal(this.tooMany));
		for (int i = 1; i < size; i++) {
			int extra = entity("Extra " + i, "x-" + suffix());
			this.jdbc.update(
					"INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES (?, ?, 5000)",
					extra, this.users);
			entries.add(removal(issue(this.solution, extra, this.users, "invalid-value")));
		}

		assertThat(resolve(this.michael, entries.toArray(ObjectNode[]::new))).hasStatus(HttpStatus.OK);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 201 })
	void listSizesRejected(int size) {
		List<ObjectNode> entries = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			entries.add(removal(UUID.randomUUID()));
		}

		MvcTestResult result = resolve(this.michael, entries.toArray(ObjectNode[]::new));

		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// Requirement: Permission to resolve open issues

	@Test
	void catalogWriterResolves() {
		assertThat(resolve(this.principals.personWithIdpRole("integration-writer"), removal(this.tooMany)))
			.hasStatus(HttpStatus.OK);
	}

	@Test
	void catalogWriterWithoutTheWriteRole() {
		this.jdbc.update("UPDATE property_definitions SET visibility_mode = 'role-restricted' WHERE id = ?", this.users);

		MvcTestResult result = resolve(this.principals.personWithIdpRole("integration-writer"), removal(this.tooMany));

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(integer(this.erp, this.users)).isEqualTo(5000L);
	}

	@Test
	void solutionWriterResolvesARestrictedProperty() {
		this.jdbc.update("UPDATE property_definitions SET visibility_mode = 'role-restricted' WHERE id = ?", this.users);

		assertThat(resolve(this.michael, entry(this.tooMany, "900"))).hasStatus(HttpStatus.OK);
		assertThat(integer(this.erp, this.users)).isEqualTo(900L);
	}

	@Test
	void viewerMayNotResolve() {
		assertThat(resolve(this.principals.personWithIdpRole("viewer"), removal(this.tooMany)))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Entities blocked by open issues

	@Test
	void goLiveOfAnotherSolutionIsBlocked() {
		String other = solution("ERP Extension", "draft");
		String delta = this.jdbc.queryForObject("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				VALUES (gen_random_uuid(), ?::uuid, 'retiring', ?, ?) RETURNING id::text""", String.class, other, this.erp,
				this.michaelId);

		MvcTestResult blocked = post("/api/v1/solutions/" + other + "/go-live", null, this.michael);

		assertThat(blocked).hasStatus(HttpStatus.CONFLICT);
		JsonNode problem = json(blocked);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:entity-blocked");
		assertThat(problem.path("deltaIds").toString()).contains(delta);
		assertThat(problem.path("entities").get(0).path("issues").get(0).path("id").asString())
			.isEqualTo(this.tooMany.toString());
		assertThat(status(other)).isEqualTo("draft");

		ok(resolve(this.michael, removal(this.tooMany)));

		assertThat(post("/api/v1/solutions/" + other + "/go-live", null, this.michael)).hasStatus(HttpStatus.OK);
	}

	// Requirement: Deltas of a blocked entity

	@ParameterizedTest
	@ValueSource(strings = { "modified", "retiring" })
	void deltaOfABlockedEntity(String type) {
		String other = solution("ERP Extension", "draft");
		ObjectNode body = JsonMapper.shared().createObjectNode().put("deltaType", type).put("entityId", this.erp);
		if ("modified".equals(type)) {
			body.put("description", "New");
		}

		MvcTestResult result = post("/api/v1/solutions/" + other + "/deltas", body, this.michael);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(json(result).path("type").asString()).isEqualTo("urn:oea:problem:entity-blocked");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_deltas WHERE solution_id = ?::uuid",
				Integer.class, other)).isZero();
	}

	@Test
	void deltaAfterTheResolution() {
		String other = solution("ERP Extension", "draft");
		ok(resolve(this.michael, removal(this.tooMany)));
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("deltaType", "modified")
			.put("entityId", this.erp)
			.put("description", "New");

		assertThat(post("/api/v1/solutions/" + other + "/deltas", body, this.michael)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void existingDeltaStaysEditable() {
		String other = solution("ERP Extension", "draft");
		String delta = this.jdbc.queryForObject("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				VALUES (gen_random_uuid(), ?::uuid, 'modified', ?, ?) RETURNING id::text""", String.class, other, this.erp,
				this.michaelId);
		this.jdbc.update("""
				INSERT INTO entity_delta_property_values (id, delta_id, system_field, after_value_text)
				VALUES (gen_random_uuid(), ?::uuid, 'description', 'Old plan')""", delta);
		ObjectNode body = JsonMapper.shared().createObjectNode().put("description", "New plan");

		MvcTestResult result = this.mvc.put()
			.uri("/api/v1/solutions/" + other + "/deltas/" + delta)
			.cookie(cookie(this.michael))
			.header(CsrfOriginFilter.HEADER, this.michael.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();

		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
	}

	@Test
	void newDeltaIsNotBlocked() {
		String other = solution("ERP Extension", "draft");
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("deltaType", "new")
			.put("metatypeId", this.application.toString())
			.put("name", "Salesforce 2");
		body.putArray("properties").addObject().put("propertyDefId", this.contact.toString()).put("value", "x");

		assertThat(post("/api/v1/solutions/" + other + "/deltas", body, this.michael)).hasStatus(HttpStatus.CREATED);
	}

	// Requirement: Updates of a blocked entity in a batch

	@ParameterizedTest
	@CsvSource({ "false, 1", "true, 0" })
	void batchUpdateOfABlockedEntity(boolean atomic, int writtenOthers) {
		String erpKey = externalId(this.erp);
		int other = entity("Other", "o-" + suffix());
		String otherKey = externalId(other);
		ObjectNode request = JsonMapper.shared().createObjectNode().put("atomic", atomic);
		ArrayNode entities = request.putArray("entities");
		entities.addObject()
			.put("externalId", erpKey)
			.put("name", "ERP-Core")
			.put("metatypeId", this.application.toString())
			.put("description", "Changed");
		entities.addObject()
			.put("externalId", otherKey)
			.put("name", "Other")
			.put("metatypeId", this.application.toString())
			.put("description", "Changed");

		MvcTestResult result = post("/api/v1/entities/batch", request,
				this.principals.personWithIdpRole("integration-writer"));

		assertThat(content(result)).contains("OPEN_ISSUES");
		assertThat(description(this.erp)).isNotEqualTo("Changed");
		assertThat("Changed".equals(description(other)) ? 1 : 0).isEqualTo(writtenOthers);
	}

	// Requirement: Restore of a blocked entity

	@Test
	void restoreOfABlockedEntity() {
		TestSession architect = this.principals.personWithIdpRole("enterprise-architect");

		MvcTestResult result = restore(architect);

		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CONFLICT);
		assertThat(json(result).path("type").asString()).isEqualTo("urn:oea:problem:entity-blocked");
		assertThat(version(this.erp)).isEqualTo(2);

		ok(resolve(this.michael, removal(this.tooMany)));

		assertThat(restore(architect).getResponse().getStatus()).isNotEqualTo(409);
	}

	// Requirements: Entities with open issues in the catalog, Open issues on the history of an entity

	@Test
	void catalogRowsAreMarked() {
		String catalog = this.jdbc.queryForObject("""
				INSERT INTO catalogs (name, primary_metatype_id, created_by, last_modified_by)
				VALUES (?, ?, ?, ?) RETURNING id::text""", String.class, "Applications " + suffix(), this.application,
				this.michaelId, this.michaelId);
		ok(resolve(this.michael, removal(this.tooMany)));

		JsonNode rows = ok(this.mvc.get()
			.uri("/api/v1/catalogs/" + catalog + "/query")
			.cookie(cookie(this.principals.personWithIdpRole("enterprise-architect")))
			.exchange()).path("rows");

		List<String> marks = new ArrayList<>();
		rows.forEach((row) -> marks.add(row.path("entityId").asInt() + " " + row.path("openIssues").asBoolean()));
		assertThat(marks).contains(this.salesforce + " true", this.erp + " false");
	}

	@Test
	void historyNamesTheOpenIssues() {
		TestSession architect = this.principals.personWithIdpRole("enterprise-architect");

		JsonNode blocked = ok(history(architect, this.salesforce)).path("openIssues");
		JsonNode free = ok(history(architect, entity("Free", "f-" + suffix()))).path("openIssues");

		assertThat(blocked).hasSize(1);
		assertThat(blocked.get(0).path("solutionId").asString()).isEqualTo(this.solution);
		assertThat(blocked.get(0).path("solutionName").asString()).startsWith("CRM Replacement");
		assertThat(blocked.get(0).path("kind").asString()).isEqualTo("mandatory-missing");
		assertThat(free).isEmpty();
	}

	private MvcTestResult list(TestSession session) {
		return this.mvc.get()
			.uri("/api/v1/solutions/" + this.solution + "/open-issues")
			.cookie(cookie(session))
			.exchange();
	}

	private MvcTestResult resolve(TestSession session, ObjectNode... entries) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode resolutions = body.putArray("resolutions");
		for (ObjectNode entry : entries) {
			resolutions.add(entry);
		}
		return post("/api/v1/solutions/" + this.solution + "/open-issues/resolution", body, session);
	}

	private MvcTestResult restore(TestSession session) {
		return this.mvc.post()
			.uri("/api/v1/entities/" + this.erp + "/restore")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.header("If-Match", "\"2\"")
			.contentType("application/json")
			.content("{\"targetVersion\": 1}")
			.exchange();
	}

	private MvcTestResult history(TestSession session, int entity) {
		return this.mvc.get().uri("/api/v1/entities/" + entity + "/history").cookie(cookie(session)).exchange();
	}

	private MvcTestResult post(String uri, ObjectNode body, TestSession session) {
		var request = this.mvc.post()
			.uri(uri)
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private static ObjectNode entry(UUID issue, String value) {
		return JsonMapper.shared().createObjectNode().put("issueId", issue.toString()).put("value", value);
	}

	private static ObjectNode removal(UUID issue) {
		return JsonMapper.shared().createObjectNode().put("issueId", issue.toString()).put("remove", true);
	}

	private static List<String> statuses(JsonNode issues) {
		List<String> statuses = new ArrayList<>();
		issues.forEach((issue) -> statuses.add(issue.path("kind").asString() + " " + issue.path("status").asString()
				+ " " + (issue.path("resolution").isNull() ? "null" : issue.path("resolution").asString())));
		return statuses;
	}

	private static JsonNode find(JsonNode issues, UUID id) {
		for (JsonNode issue : issues) {
			if (issue.path("id").asString().equals(id.toString())) {
				return issue;
			}
		}
		throw new AssertionError("no issue " + id);
	}

	private int solutionIssueCount() {
		return ok(this.mvc.get().uri("/api/v1/solutions/" + this.solution).cookie(cookie(this.michael)).exchange())
			.path("openIssueCount")
			.asInt();
	}

	private String text(int entity, UUID property) {
		List<String> values = this.jdbc.queryForList(
				"SELECT value_text FROM entity_property_values WHERE entity_id = ? AND property_def_id = ?", String.class,
				entity, property);
		return values.isEmpty() ? null : values.get(0);
	}

	private Long integer(int entity, UUID property) {
		List<Long> values = this.jdbc.queryForList(
				"SELECT value_int FROM entity_property_values WHERE entity_id = ? AND property_def_id = ?", Long.class,
				entity, property);
		return values.isEmpty() ? null : values.get(0);
	}

	private int version(int entity) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, entity);
	}

	private String description(int entity) {
		return this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, entity);
	}

	private String status(String solution) {
		return this.jdbc.queryForObject("SELECT status FROM solutions WHERE id = ?::uuid", String.class, solution);
	}

	private String externalId(int entity) {
		return this.jdbc.queryForObject("SELECT external_id FROM entities WHERE id = ?", String.class, entity);
	}

	private String solution(String name, String status) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES (gen_random_uuid(), ?, ?, ?, ?)
				RETURNING id::text""", String.class, name + " " + suffix(), this.michaelId, this.michaelId, status);
	}

	private UUID issue(String solution, int entity, UUID property, String kind) {
		return this.jdbc.queryForObject("""
				INSERT INTO solution_open_issues (id, solution_id, entity_id, field, property_def_id, kind)
				VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, ?) RETURNING id""", UUID.class, solution, entity,
				property.toString(), property, kind);
	}

	private UUID property(String name, String dataType) {
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, ?) RETURNING id""",
				UUID.class, name + " " + suffix(), "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
				dataType);
	}

	private void map(UUID property, String necessity) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, ?, DATE '2026-01-01', 'organization')""", this.application, property, necessity);
	}

	/** An entity at version 2 with both version headers and an external ID; all but "Salesforce" have a contact. */
	private int entity(String name, String externalId) {
		Integer id = this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, description, external_id, version, created_by)
				VALUES (?, ?, 'Old', ?, 2, ?) RETURNING id""", Integer.class, this.application, name, externalId,
				this.michaelId);
		this.jdbc.update("""
				INSERT INTO entity_version_headers (entity_id, version, changed_by) VALUES (?, 1, ?), (?, 2, ?)""", id,
				this.michaelId, id, this.michaelId);
		if (!"Salesforce".equals(name)) {
			this.jdbc.update(
					"INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, 'it@example.com')",
					id, this.contact);
		}
		return id;
	}

	private static Cookie cookie(TestSession session) {
		return new Cookie(SessionCookie.NAME, session.token());
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return json(result);
	}

	private static JsonNode json(MvcTestResult result) {
		return JsonMapper.shared().readTree(content(result));
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-open-issues").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

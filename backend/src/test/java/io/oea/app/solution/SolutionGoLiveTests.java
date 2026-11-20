package io.oea.app.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import io.oea.app.security.Sessions;
import io.oea.audit.AuditWriter;
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
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec solution/solution-go-live (US-152; design D2 to D4 of us-152-solution-go-live): preview,
 * field conflicts and their resolution, the go-live with its effects, open issues, audit and
 * rejections. "ERP-Core" (owner Kurt, description "Old") and "CRM-Legacy" exist in the catalog;
 * "CRM-Legacy" is the source of the connection "CRM to ERP".
 */
@SpringBootTest
@AutoConfigureMockMvc
class SolutionGoLiveTests {

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

	@MockitoSpyBean
	private AuditWriter audit;

	private TestPrincipals principals;

	private TestSession michael;

	private long michaelId;

	private String solution;

	private UUID application;

	private UUID flow;

	private UUID owner;

	private UUID tier;

	private UUID contact;

	private int erp;

	private int crm;

	private int connection;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = this.principals.personWithIdpRole("solution-architect");
		this.michaelId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.michael.sessionId());
		this.application = metatype("Application", false);
		this.flow = metatype("Flow", true);
		this.owner = property("Owner", "string");
		this.tier = property("Tier", "enum");
		this.jdbc.update("INSERT INTO property_enum_values (property_def_id, value) VALUES (?, 'gold'), (?, 'silver')",
				this.tier, this.tier);
		this.contact = property("Contact", "string");
		map(this.owner, "optional");
		map(this.tier, "optional");
		map(this.contact, "optional");
		this.erp = entity(this.application, "ERP-Core", "Old");
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, 'Kurt')",
				this.erp, this.owner);
		this.crm = entity(this.application, "CRM-Legacy", "");
		this.connection = entity(this.flow, "CRM to ERP", "");
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				this.connection, this.crm, this.erp);
		this.solution = solution("ERP Migration");
	}

	@AfterEach
	void resetAudit() {
		reset(this.audit);
	}

	// Requirement: Preview of a go-live

	@Test
	void previewCountsTheEffects() {
		capture(newEntity("Salesforce", value(this.owner, "Sales")));
		capture(modified(this.erp, "New", value(this.owner, "Michael")));
		capture(retiring(this.crm));

		JsonNode preview = ok(preview(this.michael));

		assertThat(preview.path("counts").path("created").asInt()).isEqualTo(1);
		assertThat(preview.path("counts").path("modified").asInt()).isEqualTo(1);
		assertThat(preview.path("counts").path("retired").asInt()).isEqualTo(1);
		assertThat(preview.path("counts").path("retiredConnections").asInt()).isEqualTo(1);
		assertThat(preview.path("conflicts")).isEmpty();
		assertThat(status()).isEqualTo("draft");
	}

	// Requirement: Field conflicts

	@Test
	void fieldChangedAfterTheCaptureIsAConflict() {
		capture(modified(this.erp, "New"));
		this.jdbc.update("UPDATE entities SET description = 'Other' WHERE id = ?", this.erp);

		JsonNode conflict = ok(preview(this.michael)).path("conflicts").get(0);

		assertThat(conflict.path("field").asString()).isEqualTo("description");
		assertThat(conflict.path("before").asString()).isEqualTo("Old");
		assertThat(conflict.path("current").asString()).isEqualTo("Other");
		assertThat(conflict.path("after").asString()).isEqualTo("New");
		assertThat(conflict.path("entityName").asString()).isEqualTo("ERP-Core");
	}

	@Test
	void otherFieldChangedAfterTheCaptureIsNoConflict() {
		capture(modified(this.erp, "New"));
		this.jdbc.update("UPDATE entity_property_values SET value_text = 'Anna' WHERE entity_id = ? AND property_def_id = ?",
				this.erp, this.owner);

		assertThat(ok(preview(this.michael)).path("conflicts")).isEmpty();
		ok(goLive(this.michael));
		assertThat(description(this.erp)).isEqualTo("New");
		assertThat(text(this.erp, this.owner)).isEqualTo("Anna");
	}

	// Requirement: Resolution of field conflicts

	@ParameterizedTest
	@CsvSource({ "delta, , New", "value, Merged, Merged" })
	void conflictResolvedWithTheDeltaOrAnOwnValue(String choice, String value, String expected) {
		String delta = capture(modified(this.erp, "New")).path("id").asString();
		this.jdbc.update("UPDATE entities SET description = 'Other' WHERE id = ?", this.erp);

		assertThat(resolve(delta, "description", choice, value)).hasStatus(HttpStatus.OK);

		assertThat(ok(preview(this.michael)).path("conflicts")).isEmpty();
		ok(goLive(this.michael));
		assertThat(description(this.erp)).isEqualTo(expected);
	}

	@Test
	void conflictResolvedWithTheCurrentValue() {
		String delta = capture(modified(this.erp, "New")).path("id").asString();
		this.jdbc.update("UPDATE entities SET description = 'Other' WHERE id = ?", this.erp);

		assertThat(resolve(delta, "description", "current", null)).hasStatus(HttpStatus.OK);

		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM entity_delta_property_values WHERE delta_id = ?::uuid AND system_field = 'description'""",
				Integer.class, delta)).isZero();
		assertThat(ok(preview(this.michael)).path("conflicts")).isEmpty();
	}

	@Test
	void fieldWithoutAConflictIsRejected() {
		String delta = capture(modified(this.erp, "New", value(this.owner, "Michael"))).path("id").asString();
		this.jdbc.update("UPDATE entities SET description = 'Other' WHERE id = ?", this.erp);

		MvcTestResult result = resolve(delta, this.owner.toString(), "delta", null);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(json(result).path("invalidFields").get(0).asString()).isEqualTo(this.owner.toString());
		assertThat(ok(preview(this.michael)).path("conflicts")).hasSize(1);
	}

	@Test
	void invalidOwnValueIsRejected() {
		String delta = capture(modified(this.erp, null, value(this.tier, "gold"))).path("id").asString();
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_enum) VALUES (?, ?, 'silver')",
				this.erp, this.tier);

		MvcTestResult result = resolve(delta, this.tier.toString(), "value", "platinum");

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(ok(preview(this.michael)).path("conflicts")).hasSize(1);
	}

	// Requirement: Go-live of a solution

	@Test
	void unresolvedConflictBlocksTheGoLive() {
		capture(modified(this.erp, "New"));
		this.jdbc.update("UPDATE entities SET description = 'Other' WHERE id = ?", this.erp);

		MvcTestResult result = goLive(this.michael);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(json(result).path("type").asString()).isEqualTo("urn:oea:problem:merge-required");
		assertThat(json(result).path("conflicts").get(0).path("field").asString()).isEqualTo("description");
		assertThat(status()).isEqualTo("draft");
		assertThat(description(this.erp)).isEqualTo("Other");
	}

	@ParameterizedTest
	@CsvSource({ "draft, 200", "in-progress, 200", "implemented, 409", "archived, 409" })
	void statusBoundaries(String status, int expected) {
		capture(modified(this.erp, "New"));
		this.jdbc.update("UPDATE solutions SET status = ? WHERE id = ?::uuid", status, this.solution);

		MvcTestResult result = goLive(this.michael);

		assertThat(result).hasStatus(expected);
		if (expected == 409) {
			assertThat(json(result).path("type").asString()).isEqualTo("urn:oea:problem:solution-completed");
			assertThat(description(this.erp)).isEqualTo("Old");
		}
	}

	@Test
	void secondGoLiveIsRejected() {
		capture(modified(this.erp, "New"));
		ok(goLive(this.michael));

		assertThat(goLive(this.michael)).hasStatus(HttpStatus.CONFLICT);
		assertThat(version(this.erp)).isEqualTo(2);
	}

	@Test
	void withoutPermission() {
		capture(modified(this.erp, "New"));
		TestSession viewer = this.principals.personWithIdpRole("viewer");

		assertThat(goLive(viewer)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(preview(viewer)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(status()).isEqualTo("draft");
	}

	// Requirement: Effects of a go-live

	@Test
	void newModifiedAndRetired() {
		String created = capture(newEntity("Salesforce", value(this.owner, "Sales"))).path("id").asString();
		capture(modified(this.erp, "New", value(this.owner, "Michael")));
		capture(retiring(this.crm));

		JsonNode result = ok(goLive(this.michael));

		assertThat(result.path("status").asString()).isEqualTo("implemented");
		Integer salesforce = this.jdbc.queryForObject("SELECT entity_id FROM entity_deltas WHERE id = ?::uuid",
				Integer.class, created);
		assertThat(salesforce).isNotNull();
		assertThat(this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, salesforce))
			.isEqualTo("Salesforce");
		assertThat(text(salesforce, this.owner)).isEqualTo("Sales");
		assertThat(description(this.erp)).isEqualTo("New");
		assertThat(text(this.erp, this.owner)).isEqualTo("Michael");
		assertThat(deleted(this.crm)).isTrue();
		assertThat(deleted(this.connection)).isTrue();
		assertThat(deleted(this.erp)).isFalse();
		assertThat(status()).isEqualTo("implemented");
		// the completed solution accepts no more delta changes
		assertThat(this.mvc.post()
			.uri("/api/v1/solutions/" + this.solution + "/deltas")
			.cookie(cookie(this.michael))
			.header(CsrfOriginFilter.HEADER, this.michael.csrfToken())
			.contentType("application/json")
			.content(newEntity("Later").toString())
			.exchange()).hasStatus(HttpStatus.CONFLICT);
	}

	// Requirement: Deltas on deleted entities (s17-entity-delete)

	@ParameterizedTest
	@ValueSource(strings = { "modified", "retiring" })
	void deletionConflictInThePreviewAndTheGoLive(String type) {
		int entity = "modified".equals(type) ? this.erp : this.crm;
		String delta = capture("modified".equals(type) ? modified(entity, "New") : retiring(entity)).path("id")
			.asString();
		deleteInTheRepository(entity);

		JsonNode preview = ok(preview(this.michael));
		assertThat(preview.path("deletionConflicts")).hasSize(1);
		JsonNode conflict = preview.path("deletionConflicts").get(0);
		assertThat(conflict.path("deltaId").asString()).isEqualTo(delta);
		assertThat(conflict.path("deltaType").asString()).isEqualTo(type);
		assertThat(conflict.path("entityId").asInt()).isEqualTo(entity);
		assertThat(conflict.path("entityName").asString()).isEqualTo("modified".equals(type) ? "ERP-Core" : "CRM-Legacy");

		MvcTestResult result = goLive(this.michael);

		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CONFLICT);
		assertThat(json(result).path("type").asString()).isEqualTo("urn:oea:problem:entity-deleted");
		assertThat(json(result).path("deltaIds").get(0).asString()).isEqualTo(delta);
		assertThat(status()).isEqualTo("draft");
		assertThat(description(this.erp)).isEqualTo("Old");
		assertThat(deleted(this.connection)).isFalse();
	}

	@Test
	void deletionConflictResolvedByRemovingTheDelta() {
		String delta = capture(modified(this.erp, "New")).path("id").asString();
		capture(retiring(this.crm));
		deleteInTheRepository(this.erp);
		assertThat(goLive(this.michael)).hasStatus(HttpStatus.CONFLICT);

		assertThat(this.mvc.delete()
			.uri("/api/v1/solutions/" + this.solution + "/deltas/" + delta)
			.cookie(cookie(this.michael))
			.header(CsrfOriginFilter.HEADER, this.michael.csrfToken())
			.exchange()).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(ok(preview(this.michael)).path("deletionConflicts")).isEmpty();
		ok(goLive(this.michael));
		assertThat(deleted(this.crm)).isTrue();
		assertThat(status()).isEqualTo("implemented");
	}

	@Test
	void deletionConflictResolvedByRestoringTheEntity() {
		capture(modified(this.erp, "New"));
		TestSession kurt = this.principals.personWithIdpRole("enterprise-architect");
		MvcTestResult deletion = this.mvc.delete()
			.uri("/api/v1/entities/" + this.erp)
			.cookie(cookie(kurt))
			.header(CsrfOriginFilter.HEADER, kurt.csrfToken())
			.header("If-Match", "\"1\"")
			.exchange();
		assertThat(deletion).as(() -> content(deletion)).hasStatus(HttpStatus.OK);
		assertThat(goLive(this.michael)).hasStatus(HttpStatus.CONFLICT);

		MvcTestResult restore = this.mvc.post()
			.uri("/api/v1/entities/" + this.erp + "/undelete")
			.cookie(cookie(kurt))
			.header(CsrfOriginFilter.HEADER, kurt.csrfToken())
			.header("If-Match", "\"1\"")
			.exchange();
		assertThat(restore).as(() -> content(restore)).hasStatus(HttpStatus.OK);

		assertThat(ok(preview(this.michael)).path("deletionConflicts")).isEmpty();
		ok(goLive(this.michael));
		assertThat(description(this.erp)).isEqualTo("New");
	}

	@Test
	void aDeltaNewAndDeltasOfActiveEntitiesAreNoDeletionConflict() {
		capture(newEntity("Salesforce"));
		capture(modified(this.erp, "New"));
		// an unrelated entity is deleted
		int other = entity(this.application, "Other-" + suffix(), "");
		deleteInTheRepository(other);

		assertThat(ok(preview(this.michael)).path("deletionConflicts")).isEmpty();
		ok(goLive(this.michael));
		assertThat(description(this.erp)).isEqualTo("New");
	}

	@Test
	void retiredConnectionsRecordTheRetiredEntityAsOriginAndComeBackWithIt() {
		capture(retiring(this.crm));
		ok(goLive(this.michael));
		assertThat(origin(this.crm)).isNull();
		assertThat(origin(this.connection)).isEqualTo(this.crm);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(DISTINCT deleted_at) FROM entities WHERE id IN (?, ?)""", Integer.class, this.crm,
				this.connection)).isOne();

		TestSession kurt = this.principals.personWithIdpRole("enterprise-architect");
		MvcTestResult restore = this.mvc.post()
			.uri("/api/v1/entities/" + this.crm + "/undelete")
			.cookie(cookie(kurt))
			.header(CsrfOriginFilter.HEADER, kurt.csrfToken())
			.header("If-Match", "\"1\"")
			.exchange();

		JsonNode restored = ok(restore).path("restored");
		assertThat(restored).hasSize(2);
		assertThat(deleted(this.crm)).isFalse();
		assertThat(deleted(this.connection)).isFalse();
	}

	private void deleteInTheRepository(int entity) {
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.michaelId, entity);
	}

	private Integer origin(int id) {
		return this.jdbc.queryForObject("SELECT deleted_with_entity_id FROM entities WHERE id = ?", Integer.class, id);
	}

	@Test
	void failedAuditRollsTheGoLiveBack() {
		capture(newEntity("Salesforce"));
		capture(modified(this.erp, "New"));
		doThrow(new IllegalStateException("audit unavailable")).when(this.audit)
			.append(argThat((io.oea.audit.AuditEvent event) -> event != null
					&& "solution.went-live".equals(event.eventType())));

		assertThat(goLive(this.michael)).hasFailed().failure().hasRootCauseMessage("audit unavailable");

		assertThat(status()).isEqualTo("draft");
		assertThat(description(this.erp)).isEqualTo("Old");
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM entity_deltas WHERE solution_id = ?::uuid AND entity_id IS NOT NULL AND delta_type = 'new'""",
				Integer.class, this.solution)).isZero();
	}

	// Requirement: Open issues of a go-live

	@Test
	void violationsBecomeOpenIssues() {
		capture(newEntity("Salesforce", value(this.tier, "gold")));
		// after the capture: Contact becomes mandatory, and gold leaves the enumeration
		this.jdbc.update("UPDATE metatype_property_mappings SET necessity = 'mandatory' WHERE property_def_id = ?",
				this.contact);
		this.jdbc.update("DELETE FROM property_enum_values WHERE property_def_id = ? AND value = 'gold'", this.tier);

		JsonNode preview = ok(preview(this.michael));
		assertThat(kinds(preview.path("violations"))).containsExactlyInAnyOrder("mandatory-missing " + this.contact,
				"invalid-value " + this.tier);

		JsonNode result = ok(goLive(this.michael));

		assertThat(kinds(result.path("openIssues"))).containsExactlyInAnyOrder("mandatory-missing " + this.contact,
				"invalid-value " + this.tier);
		int salesforce = this.jdbc.queryForObject("""
				SELECT d.entity_id FROM entity_deltas d WHERE d.solution_id = ?::uuid AND d.delta_type = 'new'""",
				Integer.class, this.solution);
		assertThat(this.jdbc.queryForObject("SELECT value_enum FROM entity_property_values WHERE entity_id = ?",
				String.class, salesforce)).isEqualTo("gold");
		assertThat(this.jdbc.queryForList("SELECT property_def_id FROM entity_mandatory_marks WHERE entity_id = ?",
				UUID.class, salesforce)).containsExactly(this.contact);
		JsonNode solution = ok(this.mvc.get().uri("/api/v1/solutions/" + this.solution).cookie(cookie(this.michael)).exchange());
		assertThat(solution.path("openIssueCount").asInt()).isEqualTo(2);
		assertThat(ok(this.mvc.get()
			.uri("/api/v1/solutions/" + this.solution + "/open-issues")
			.cookie(cookie(this.michael))
			.exchange())).hasSize(2);
	}

	// Requirement: Audit of a go-live

	@Test
	void eventsOfAGoLive() {
		String created = capture(newEntity("Salesforce")).path("id").asString();
		capture(modified(this.erp, "New"));
		capture(retiring(this.crm));

		ok(goLive(this.michael));

		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'retiredConnections' FROM audit.events WHERE event_type = 'solution.went-live'
				AND target_id = ?""", String.class, this.solution)).isEqualTo("1");
		String salesforce = Integer.toString(this.jdbc
			.queryForObject("SELECT entity_id FROM entity_deltas WHERE id = ?::uuid", Integer.class, created));
		assertThat(this.jdbc.queryForList("""
				SELECT event_type FROM audit.events WHERE event_type IN ('entity.created', 'entity.updated', 'entity.deleted')
				AND target_id IN (?, ?, ?, ?)""", String.class, Integer.toString(this.erp), Integer.toString(this.crm),
				Integer.toString(this.connection), salesforce))
			.containsExactlyInAnyOrder("entity.created", "entity.updated", "entity.deleted", "entity.deleted");
	}

	private JsonNode capture(ObjectNode body) {
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/solutions/" + this.solution + "/deltas")
			.cookie(cookie(this.michael))
			.header(CsrfOriginFilter.HEADER, this.michael.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return json(result);
	}

	private MvcTestResult preview(TestSession session) {
		return this.mvc.get().uri("/api/v1/solutions/" + this.solution + "/go-live").cookie(cookie(session)).exchange();
	}

	private MvcTestResult goLive(TestSession session) {
		return this.mvc.post()
			.uri("/api/v1/solutions/" + this.solution + "/go-live")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
	}

	private MvcTestResult resolve(String delta, String field, String choice, String value) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ObjectNode entry = body.putArray("fields").addObject().put("field", field).put("choice", choice);
		if (value != null) {
			entry.put("value", value);
		}
		return this.mvc.post()
			.uri("/api/v1/solutions/" + this.solution + "/deltas/" + delta + "/resolution")
			.cookie(cookie(this.michael))
			.header(CsrfOriginFilter.HEADER, this.michael.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
	}

	private ObjectNode newEntity(String name, ObjectNode... values) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("deltaType", "new");
		body.put("metatypeId", this.application.toString()).put("name", name);
		ArrayNode properties = body.putArray("properties");
		for (ObjectNode value : values) {
			properties.add(value);
		}
		return body;
	}

	private static ObjectNode modified(int entityId, String description, ObjectNode... values) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("deltaType", "modified").put("entityId", entityId);
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

	private static List<String> kinds(JsonNode entries) {
		List<String> kinds = new ArrayList<>();
		entries.forEach((entry) -> kinds.add(entry.path("kind").asString() + " " + entry.path("field").asString()));
		return kinds;
	}

	private String status() {
		return this.jdbc.queryForObject("SELECT status FROM solutions WHERE id = ?::uuid", String.class, this.solution);
	}

	private String description(int id) {
		return this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, id);
	}

	private int version(int id) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, id);
	}

	private boolean deleted(int id) {
		return Boolean.TRUE.equals(
				this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM entities WHERE id = ?", Boolean.class, id));
	}

	private String text(int id, UUID property) {
		List<String> values = this.jdbc.queryForList(
				"SELECT value_text FROM entity_property_values WHERE entity_id = ? AND property_def_id = ?", String.class,
				id, property);
		return values.isEmpty() ? null : values.get(0);
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

	private int entity(UUID type, String name, String description) {
		int id = this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, description, created_by) VALUES (?, ?, ?, ?) RETURNING id""",
				Integer.class, type, name, description, this.michaelId);
		this.jdbc.update("INSERT INTO entity_version_headers (entity_id, version, changed_by) VALUES (?, 1, ?)", id,
				this.michaelId);
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
			return Files.createTempDirectory("oea-go-live").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

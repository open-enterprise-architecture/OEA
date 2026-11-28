package io.oea.app.organization;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Specs diagram/bpmn-assignments and diagram/diagram-connections (assignments in the save) at API level
 * (us-075-lane-assignments D3, D4, D8): the assignments of the view and the details, the connections without an
 * edge, the entities without a shape, the removed connections, the organizational unit limit and the search
 * of the assignment targets. The instance has the BPMN and the organization package; each test builds its own
 * entities.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramAssignmentTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final String BPMN = "oea-starter-bpmn";

	private static final String ORGANIZATION = "oea-starter-organization";

	private static boolean prepared;

	/** The viewpoint that maps "Role" to a lane and "Organizational Unit" to a pool, with the tasks of the package. */
	private static UUID roleLanes;

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

	private Api api;

	private TestSession admin;

	private TestSession anna;

	private TestSession writer;

	private TestSession franz;

	@BeforeEach
	void instance() {
		this.api = new Api(this.mvc);
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.anna = principals.personWithIdpRole("enterprise-architect");
		this.writer = principals.personWithIdpRole("integration-writer");
		this.franz = principals.personWithIdpRole("viewer");
		if (!prepared) {
			this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + BPMN + "/import", null, this.admin));
			this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + ORGANIZATION + "/import", null, this.admin));
			roleLanes = roleLaneViewpoint();
			prepared = true;
		}
	}

	// Requirement: Assignments of a shape in the view

	@Test
	void aLaneShowsItsOrganizationalUnitAndRolesAndAUserTaskItsPerson() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Purchasing " + Api.id());
		int task = entity("User Task", "Approve order " + Api.id());
		int unit = entity("Organizational Unit", "Purchasing Department");
		int reviewer = entity("Role", "Reviewer");
		int approver = entity("Role", "Approver");
		int maria = entity("Person", "Maria Müller");
		int belongs = connect("Belongs To", lane, unit);
		connect("Performs Role", lane, reviewer);
		connect("Performs Role", lane, approver);
		connect("Responsible", task, maria);
		ObjectNode body = layout();
		shape(body, lane, 0, 0, 600, 200, null);
		shape(body, task, 20, 20, 90, 60, null);
		JsonNode view = this.api.ok(save(diagram, body));
		JsonNode assignments = shapeOf(view, lane).path("assignments");
		assertThat(assignments.path("orgUnits")).hasSize(1);
		assertThat(assignments.path("orgUnits").get(0).path("name").asString()).isEqualTo("Purchasing Department");
		assertThat(assignments.path("orgUnits").get(0).path("connectionId").asInt()).isEqualTo(belongs);
		assertThat(assignments.path("orgUnits").get(0).path("connectionVersion").asInt()).isEqualTo(1);
		assertThat(assignments.path("orgUnits").get(0).path("entityId").asInt()).isEqualTo(unit);
		assertThat(assignments.path("roles").valueStream().map((role) -> role.path("name").asString()))
			.containsExactly("Approver", "Reviewer");
		assertThat(assignments.path("persons")).isEmpty();
		assertThat(shapeOf(view, task).path("assignments").path("persons").get(0).path("name").asString())
			.isEqualTo("Maria Müller");
		// the details of the entity carry the same
		JsonNode details = this.api.ok(this.api.get(VIEWS + "/" + diagram + "/entities/" + task, this.franz));
		assertThat(details.path("assignments").path("persons").get(0).path("entityId").asInt()).isEqualTo(maria);
	}

	@Test
	void aDeletedConnectionOrTargetIsLeftOutAndShownAgainAfterTheRestore() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Lane " + Api.id());
		int auditor = entity("Role", "Auditor " + Api.id());
		int other = entity("Role", "Controller " + Api.id());
		int auditorConnection = connect("Performs Role", lane, auditor);
		connect("Performs Role", lane, other);
		ObjectNode body = layout();
		shape(body, lane, 0, 0, 300, 100, null);
		this.api.ok(save(diagram, body));
		assertThat(roles(diagram, lane)).hasSize(2);
		delete(auditor);
		assertThat(roles(diagram, lane)).hasSize(1);
		undelete(auditor);
		assertThat(roles(diagram, lane)).hasSize(2);
		delete(auditorConnection);
		assertThat(roles(diagram, lane)).hasSize(1);
		undelete(auditorConnection);
		assertThat(roles(diagram, lane)).hasSize(2);
	}

	@Test
	void aGatewayAndAShapeThatIsNoBpmnTypeCarryNoAssignments() {
		String diagram = diagram(bpmnViewpoint());
		int gateway = entity("Exclusive Gateway", "Valid? " + Api.id());
		int process = entity("Process", "Order handling " + Api.id());
		int maria = entity("Person", "Maria " + Api.id());
		connect("Responsible", gateway, maria);
		connect("Responsible", process, maria);
		ObjectNode body = layout();
		shape(body, gateway, 0, 0, 38, 38, null);
		shape(body, process, 100, 0, 90, 30, null);
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(shapeOf(view, gateway).path("assignments").isNull()).isTrue();
		assertThat(shapeOf(view, process).path("assignments").isNull()).isTrue();
		assertThat(Api.status(this.api.get(VIEWS + "/" + diagram + "/entities/" + gateway, this.franz))).isEqualTo(200);
		assertThat(this.api.ok(this.api.get(VIEWS + "/" + diagram + "/entities/" + gateway, this.franz))
			.path("assignments")
			.isNull()).isTrue();
	}

	@Test
	void anUnsetPartGivesNoAssignmentsOfThatPart() {
		String diagram = diagram(bpmnViewpoint());
		int task = entity("User Task", "Approve " + Api.id());
		int maria = entity("Person", "Maria " + Api.id());
		connect("Responsible", task, maria);
		ObjectNode body = layout();
		shape(body, task, 0, 0, 90, 60, null);
		this.api.ok(save(diagram, body));
		String person = this.jdbc.queryForObject("SELECT person_metatype_id::text FROM organization_model", String.class);
		this.jdbc.update("UPDATE organization_model SET person_metatype_id = NULL");
		try {
			assertThat(shapeOf(view(diagram), task).path("assignments").path("persons")).isEmpty();
			assertThat(shapeOf(view(diagram), task).path("assignments").path("orgUnits")).isEmpty();
		}
		finally {
			this.jdbc.update("UPDATE organization_model SET person_metatype_id = ?::uuid", person);
		}
		assertThat(shapeOf(view(diagram), task).path("assignments").path("persons")).hasSize(1);
	}

	@Test
	void aShapeInAnAssignmentLaneCarriesAssignments() {
		String diagram = diagram(roleLanes);
		int pool = entity("Organizational Unit", "Order handling " + Api.id());
		int lane = entity("Role", "Approver " + Api.id());
		int note = entity("Text Annotation", "Note " + Api.id());
		int unit = entity("Organizational Unit", "Sales " + Api.id());
		connect("Belongs To", note, unit);
		ObjectNode body = layout();
		String poolShape = shape(body, pool, 0, 0, 600, 300, null);
		String laneShape = shape(body, lane, 30, 0, 570, 150, poolShape);
		shape(body, note, 60, 20, 90, 45, laneShape);
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(shapeOf(view, note).path("assignments").path("orgUnits")).hasSize(1);
		// outside a lane, a text annotation carries none
		String other = diagram(roleLanes);
		ObjectNode alone = layout();
		shape(alone, note, 0, 0, 90, 45, null);
		assertThat(shapeOf(this.api.ok(save(other, alone)), note).path("assignments").isNull()).isTrue();
	}

	// Requirement: Assignment connections without an edge in the save

	@Test
	void aConnectionWithoutAnEdgeIsCreatedAndAuditedAndTheViewShowsNoEdgeForIt() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Purchasing " + Api.id());
		int unit = entity("Organizational Unit", "Purchasing Department " + Api.id());
		ObjectNode body = layout();
		String laneShape = shape(body, lane, 0, 0, 600, 200, null);
		assign(body, "Belongs To", laneShape, unit, null);
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(view.path("edges")).isEmpty();
		assertThat(shapeOf(view, lane).path("assignments").path("orgUnits").get(0).path("entityId").asInt()).isEqualTo(unit);
		int connection = shapeOf(view, lane).path("assignments").path("orgUnits").get(0).path("connectionId").asInt();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'entity.created' "
				+ "AND target_id = ?", Integer.class, Integer.toString(connection))).isOne();
		assertThat(auditDetails(diagram).path("connectionsCreated").asInt()).isEqualTo(1);
		// two roles
		int approver = entity("Role", "Approver " + Api.id());
		int reviewer = entity("Role", "Reviewer " + Api.id());
		ObjectNode more = rewrite(view);
		assign(more, "Performs Role", laneShape, approver, null);
		assign(more, "Performs Role", laneShape, reviewer, null);
		JsonNode next = this.api.ok(save(diagram, more));
		assertThat(shapeOf(next, lane).path("assignments").path("roles")).hasSize(2);
	}

	/** metatype of the connection, expected issue code (none: accepted) */
	@ParameterizedTest
	@CsvSource(nullValues = "-", value = { "Belongs To, -", "Performs Role, -", "Responsible, -",
			"Sequence Flow, METATYPE_NOT_ALLOWED", "Association, METATYPE_NOT_ALLOWED" })
	void onlyTheAssignmentTypesAreAcceptedWithoutAnEdge(String type, String code) {
		String diagram = diagram(bpmnViewpoint());
		int task = entity("User Task", "Task " + Api.id());
		int target = entity("Person".equals(typeTarget(type)) ? "Person" : typeTarget(type), "Target " + Api.id());
		ObjectNode body = layout();
		String shape = shape(body, task, 0, 0, 90, 60, null);
		assign(body, type, shape, target, null);
		MvcTestResult result = save(diagram, body);
		if (code == null) {
			assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
		}
		else {
			assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
			assertThat(issueCodes(result)).contains(code);
			assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
				.anyMatch((field) -> field.startsWith("newConnections[0]"));
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid",
					Integer.class, diagram)).isZero();
		}
	}

	@Test
	void aConnectionWithAnEdgeAndOwnEndpointsIsRejected() {
		String diagram = diagram(bpmnViewpoint());
		int one = entity("User Task", "One " + Api.id());
		int two = entity("User Task", "Two " + Api.id());
		ObjectNode body = layout();
		String first = shape(body, one, 0, 0, 90, 60, null);
		String second = shape(body, two, 200, 0, 90, 60, null);
		String key = Api.id();
		ObjectNode connection = Api.object().put("key", key).put("metatypeId", metatype("Sequence Flow"))
			.put("sourceShapeId", first).put("targetEntityId", two);
		body.putArray("newConnections").add(connection);
		body.putArray("edges")
			.add(Api.object().put("id", Api.id()).put("newConnectionKey", key).put("sourceShapeId", first)
				.put("targetShapeId", second).<ObjectNode>set("points", JsonMapperHolder.array()));
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString)).contains("newConnections[0]");
	}

	@Test
	void aConnectionWithoutAnEdgeAndWithoutEndpointsIsRejected() {
		String diagram = diagram(bpmnViewpoint());
		ObjectNode body = layout();
		body.putArray("newConnections").add(Api.object().put("key", Api.id()).put("metatypeId", metatype("Belongs To")));
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).isEqualTo(422);
		assertThat(issueCodes(result)).contains("NEW_CONNECTION_UNUSED");
	}

	@Test
	void aNewEntityWithoutAShapeNamedByAConnectionIsCreatedAndOneNamedByNothingIsRejected() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Logistics lane " + Api.id());
		ObjectNode body = layout();
		String laneShape = shape(body, lane, 0, 0, 600, 200, null);
		String key = Api.id();
		body.putArray("newEntities")
			.add(Api.object().put("key", key).put("metatypeId", metatype("Organizational Unit")).put("name", "Logistics"));
		assign(body, "Belongs To", laneShape, null, key);
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(view.path("shapes")).hasSize(1);
		assertThat(shapeOf(view, lane).path("assignments").path("orgUnits").get(0).path("name").asString()).isEqualTo("Logistics");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = 'Logistics'", Integer.class)).isOne();
		// named by nothing
		String lonely = diagram(bpmnViewpoint());
		ObjectNode alone = layout();
		alone.putArray("newEntities")
			.add(Api.object().put("key", Api.id()).put("metatypeId", metatype("Organizational Unit")).put("name", "Nowhere"));
		MvcTestResult rejected = save(lonely, alone);
		assertThat(Api.status(rejected)).isEqualTo(422);
		assertThat(issueCodes(rejected)).contains("NEW_ENTITY_UNUSED");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = 'Nowhere'", Integer.class)).isZero();
	}

	@Test
	void aNewEntityWithoutAShapeOfAnotherTypeIsRejected() {
		String diagram = diagram(bpmnViewpoint());
		int task = entity("User Task", "Task " + Api.id());
		ObjectNode body = layout();
		String shape = shape(body, task, 0, 0, 90, 60, null);
		String key = Api.id();
		body.putArray("newEntities")
			.add(Api.object().put("key", key).put("metatypeId", metatype("Task")).put("name", "Not an organization type"));
		assign(body, "Responsible", shape, null, key);
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
			.contains("newEntities[0].metatypeId");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = 'Not an organization type'",
				Integer.class)).isZero();
	}

	/** length of the name of the quick-created role, expected status */
	@ParameterizedTest
	@CsvSource({ "1, 200", "255, 200", "256, 400" })
	void theNameOfAQuickCreatedEntityHasAtMost255Characters(int length, int expected) {
		String diagram = diagram(bpmnViewpoint());
		int task = entity("Task", "Task " + Api.id());
		ObjectNode body = layout();
		String shape = shape(body, task, 0, 0, 90, 60, null);
		String key = Api.id();
		body.putArray("newEntities")
			.add(Api.object().put("key", key).put("metatypeId", metatype("Role")).put("name", "r".repeat(length)));
		assign(body, "Performs Role", shape, null, key);
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
		if (expected == 400) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = ?", Integer.class,
					"r".repeat(length))).isZero();
		}
	}

	@Test
	void aConnectionToTheEntityOfALaneShapeAlsoANewOneIsAcceptedAndTheParentStaysUnchanged() {
		String diagram = diagram(roleLanes);
		int pool = entity("Organizational Unit", "Order handling " + Api.id());
		int task = entity("User Task", "Approve order " + Api.id());
		ObjectNode body = layout();
		String key = Api.id();
		String poolShape = shape(body, pool, 0, 0, 600, 300, null);
		body.putArray("newEntities").add(Api.object().put("key", key).put("metatypeId", metatype("Role")).put("name", "Approver"));
		String laneShape = Api.id();
		((ArrayNode) body.path("shapes")).add(Api.object().put("id", laneShape).put("newEntityKey", key).put("x", 30).put("y", 0)
			.put("width", 570).put("height", 150).put("parentShapeId", poolShape));
		String taskShape = shape(body, task, 60, 20, 90, 60, laneShape);
		assign(body, "Performs Role", taskShape, null, key);
		JsonNode view = this.api.ok(save(diagram, body));
		int role = this.jdbc.queryForObject("SELECT id FROM entities WHERE name = 'Approver' ORDER BY id DESC LIMIT 1", Integer.class);
		assertThat(shapeOf(view, task).path("assignments").path("roles").get(0).path("entityId").asInt()).isEqualTo(role);
		assertThat(this.jdbc.queryForObject("SELECT parent_entity_id FROM entities WHERE id = ?", Integer.class, task)).isNull();
		// an existing entity of a lane shape
		String second = diagram(roleLanes);
		int other = entity("User Task", "Check order " + Api.id());
		ObjectNode again = layout();
		String roleLane = shape(again, role, 0, 0, 300, 150, null);
		String otherShape = shape(again, other, 20, 20, 90, 60, roleLane);
		assign(again, "Performs Role", otherShape, role, null);
		JsonNode saved = this.api.ok(save(second, again));
		assertThat(shapeOf(saved, other).path("assignments").path("roles").get(0).path("entityId").asInt()).isEqualTo(role);
		assertThat(this.jdbc.queryForObject("SELECT parent_entity_id FROM entities WHERE id = ?", Integer.class, other)).isNull();
	}

	// Requirement: Removed connections

	@Test
	void aRemovedConnectionIsDeletedSoftlyAuditedAndRestorable() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Lane " + Api.id());
		int unit = entity("Organizational Unit", "Unit " + Api.id());
		int connection = connect("Belongs To", lane, unit);
		ObjectNode body = layout();
		shape(body, lane, 0, 0, 300, 100, null);
		JsonNode view = this.api.ok(save(diagram, body));
		ObjectNode removal = rewrite(view);
		remove(removal, connection, 1);
		JsonNode saved = this.api.ok(save(diagram, removal));
		assertThat(shapeOf(saved, lane).path("assignments").path("orgUnits")).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM entities WHERE id = ?", Boolean.class, connection))
			.isTrue();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'entity.deleted' AND target_id = ?",
				Integer.class, Integer.toString(connection))).isOne();
		assertThat(auditDetails(diagram).path("connectionsRemoved").asInt()).isEqualTo(1);
		undelete(connection);
		assertThat(shapeOf(view(diagram), lane).path("assignments").path("orgUnits")).hasSize(1);
	}

	@Test
	void aConnectionWhoseSourceIsOnlyOnAShapeOfTheStoredLayoutCanBeRemoved() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Lane " + Api.id());
		int unit = entity("Organizational Unit", "Unit " + Api.id());
		int connection = connect("Belongs To", lane, unit);
		ObjectNode body = layout();
		shape(body, lane, 0, 0, 300, 100, null);
		this.api.ok(save(diagram, body));
		// the shape is removed from the diagram and the assignment with it
		ObjectNode empty = layout();
		remove(empty, connection, 1);
		JsonNode saved = this.api.ok(save(diagram, empty));
		assertThat(saved.path("shapes")).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM entities WHERE id = ?", Boolean.class, connection))
			.isTrue();
	}

	/** what is wrong with the removed connection, the expected code */
	@ParameterizedTest
	@CsvSource({ "unrelated, SOURCE_NOT_ON_DIAGRAM", "no assignment, NOT_AN_ASSIGNMENT", "shown by an edge, REMOVED_CONNECTION_SHOWN",
			"unknown, CONNECTION_NOT_FOUND", "twice, DUPLICATE_ENTITY" })
	void aRemovedConnectionThatIsUnrelatedNoAssignmentOrShownIsRejected(String what, String code) {
		String diagram = diagram(withBelongsTo());
		int lane = entity("Lane", "Lane " + Api.id());
		int unit = entity("Organizational Unit", "Unit " + Api.id());
		int connection = connect("Belongs To", lane, unit);
		int elsewhere = connect("Belongs To", entity("Lane", "Elsewhere " + Api.id()), unit);
		int flow = connect("Sequence Flow", entity("User Task", "A " + Api.id()), entity("User Task", "B " + Api.id()));
		ObjectNode body = layout();
		String laneShape = shape(body, lane, 0, 0, 300, 100, null);
		int removed = switch (what) {
			case "unrelated" -> elsewhere;
			case "no assignment" -> flow;
			case "unknown" -> 999999;
			default -> connection;
		};
		if ("shown by an edge".equals(what)) {
			String unitShape = shape(body, unit, 400, 0, 90, 60, null);
			body.putArray("edges")
				.add(Api.object().put("id", Api.id()).put("connectionId", connection).put("sourceShapeId", laneShape)
					.put("targetShapeId", unitShape).<ObjectNode>set("points", JsonMapperHolder.array()));
		}
		remove(body, removed, version(removed));
		if ("twice".equals(what)) {
			remove(body, removed, version(removed));
		}
		int versionBefore = version(diagram);
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(issueCodes(result)).contains(code);
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
			.anyMatch((field) -> field.startsWith("removedConnections["));
		assertThat(version(diagram)).isEqualTo(versionBefore);
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NULL FROM entities WHERE id = ?", Boolean.class, connection))
			.isTrue();
	}

	@Test
	void aStaleVersionOfARemovedConnectionIsAConflictAndChangesNothing() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Lane " + Api.id());
		int unit = entity("Organizational Unit", "Unit " + Api.id());
		int connection = connect("Belongs To", lane, unit);
		ObjectNode body = layout();
		shape(body, lane, 0, 0, 300, 100, null);
		this.api.ok(save(diagram, body));
		// Kurt changes the connection after Anna read it
		this.jdbc.update("UPDATE entities SET version = version + 1 WHERE id = ?", connection);
		ObjectNode removal = rewrite(view(diagram));
		remove(removal, connection, 1);
		MvcTestResult result = save(diagram, removal);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(409);
		assertThat(Api.content(result)).contains("entity-version-conflict").contains("removedConnections[0]");
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NULL FROM entities WHERE id = ?", Boolean.class, connection))
			.isTrue();
	}

	@Test
	void removingNeedsEntityDeleteAndTheDenialComesBeforeAnInvalidShape() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Lane " + Api.id());
		int unit = entity("Organizational Unit", "Unit " + Api.id());
		int connection = connect("Belongs To", lane, unit);
		ObjectNode body = layout();
		shape(body, lane, 0, 0, 300, 100, null);
		this.api.ok(save(diagram, body));
		// an invalid shape (an unknown entity) and a removal, by a holder of entity:write without entity:delete
		ObjectNode request = layout();
		((ArrayNode) request.path("shapes")).add(Api.object().put("id", Api.id()).put("entityId", 999999).put("x", 0)
			.put("y", 0).put("width", 90).put("height", 60));
		remove(request, connection, 1);
		MvcTestResult denied = this.api.put(VIEWS + "/" + diagram, request, this.writer, "\"" + version(diagram) + "\"");
		assertThat(Api.status(denied)).as(() -> Api.content(denied)).isEqualTo(403);
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NULL FROM entities WHERE id = ?", Boolean.class, connection))
			.isTrue();
		// without the invalid shape, the architect removes it
		ObjectNode valid = layout();
		shape(valid, lane, 0, 0, 300, 100, null);
		remove(valid, connection, 1);
		this.api.ok(save(diagram, valid));
	}

	/** number of removed connections, expected status */
	@ParameterizedTest
	@CsvSource({ "500, 200", "501, 400" })
	void atMost500ConnectionsAreRemovedWithOneSave(int count, int expected) {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Lane " + Api.id());
		ObjectNode body = layout();
		shape(body, lane, 0, 0, 300, 100, null);
		this.api.ok(save(diagram, body));
		List<Integer> connections = new ArrayList<>();
		if (expected == 200) {
			ObjectNode roles = Api.object().put("atomic", true);
			ArrayNode entities = roles.putArray("entities");
			for (int i = 0; i < count; i++) {
				entities.add(Api.object().put("name", "Role " + Api.id()).put("metatypeId", metatype("Role")));
			}
			JsonNode created = this.api.ok(this.api.post("/api/v1/entities/batch", roles, this.anna));
			ObjectNode links = Api.object().put("atomic", true);
			ArrayNode array = links.putArray("connections");
			for (JsonNode item : created.path("items")) {
				array.add(Api.object().put("connectionMetatypeId", metatype("Performs Role")).put("sourceId", lane)
					.put("targetId", item.path("id").asInt()));
			}
			for (JsonNode item : this.api.ok(this.api.post("/api/v1/entities/batch", links, this.anna)).path("items")) {
				connections.add(item.path("id").asInt());
			}
		}
		ObjectNode request = layout();
		shape(request, lane, 0, 0, 300, 100, null);
		ArrayNode removed = request.putArray("removedConnections");
		for (int i = 0; i < count; i++) {
			removed.add(Api.object().put("connectionId", expected == 200 ? connections.get(i) : i + 1).put("expectedVersion", 1));
		}
		MvcTestResult result = save(diagram, request);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
		if (expected == 200) {
			assertThat(shapeOf(Api.body(result), lane).path("assignments").path("roles")).isEmpty();
		}
	}

	// Requirement: Limits of assignments

	@Test
	void aLaneHasOneOrganizationalUnitAtMostAndReplacingItIsAccepted() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Lane " + Api.id());
		int first = entity("Organizational Unit", "First " + Api.id());
		int second = entity("Organizational Unit", "Second " + Api.id());
		int third = entity("Organizational Unit", "Third " + Api.id());
		ObjectNode body = layout();
		String laneShape = shape(body, lane, 0, 0, 300, 100, null);
		assign(body, "Belongs To", laneShape, first, null);
		JsonNode view = this.api.ok(save(diagram, body));
		int firstConnection = shapeOf(view, lane).path("assignments").path("orgUnits").get(0).path("connectionId").asInt();
		// a second one is rejected naming the shape, and nothing changes
		ObjectNode two = rewrite(view);
		assign(two, "Belongs To", shapeId(view, lane), second, null);
		MvcTestResult rejected = save(diagram, two);
		assertThat(Api.status(rejected)).as(() -> Api.content(rejected)).isEqualTo(422);
		assertThat(issueCodes(rejected)).contains("ORG_UNIT_LIMIT");
		assertThat(Api.body(rejected).path("invalidFields").valueStream().map(JsonNode::asString)).contains("shapes[0]");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities e JOIN entity_connections c ON c.entity_id = e.id "
				+ "WHERE c.source_entity_id = ? AND e.deleted_at IS NULL", Integer.class, lane)).isOne();
		// replacing the unit in one save is accepted
		ObjectNode replace = rewrite(view);
		remove(replace, firstConnection, 1);
		assign(replace, "Belongs To", shapeId(view, lane), third, null);
		JsonNode replaced = this.api.ok(save(diagram, replace));
		assertThat(shapeOf(replaced, lane).path("assignments").path("orgUnits")).hasSize(1);
		assertThat(shapeOf(replaced, lane).path("assignments").path("orgUnits").get(0).path("entityId").asInt()).isEqualTo(third);
	}

	@Test
	void aLaneWithTwoUnitsFromABatchDoesNotBlockASaveThatAddsNoUnit() {
		String diagram = diagram(bpmnViewpoint());
		int lane = entity("Lane", "Lane " + Api.id());
		connect("Belongs To", lane, entity("Organizational Unit", "One " + Api.id()));
		connect("Belongs To", lane, entity("Organizational Unit", "Two " + Api.id()));
		int role = entity("Role", "Approver " + Api.id());
		ObjectNode body = layout();
		String laneShape = shape(body, lane, 0, 0, 300, 100, null);
		assign(body, "Performs Role", laneShape, role, null);
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(shapeOf(view, lane).path("assignments").path("orgUnits")).hasSize(2);
		// a further unit is blocked, as the lane would have three
		ObjectNode more = rewrite(view);
		assign(more, "Belongs To", shapeId(view, lane), entity("Organizational Unit", "Three " + Api.id()), null);
		assertThat(Api.status(save(diagram, more))).isEqualTo(422);
	}

	@Test
	void theLimitAppliesToARoleDrawnAsALaneAndNotToATask() {
		String diagram = diagram(roleLanes);
		int roleLane = entity("Role", "Approver " + Api.id());
		int task = entity("User Task", "Approve " + Api.id());
		int one = entity("Organizational Unit", "One " + Api.id());
		int two = entity("Organizational Unit", "Two " + Api.id());
		ObjectNode body = layout();
		String laneShape = shape(body, roleLane, 0, 0, 300, 100, null);
		String taskShape = shape(body, task, 400, 0, 90, 60, null);
		assign(body, "Belongs To", laneShape, one, null);
		assign(body, "Belongs To", laneShape, two, null);
		MvcTestResult rejected = save(diagram, body);
		assertThat(Api.status(rejected)).as(() -> Api.content(rejected)).isEqualTo(422);
		assertThat(issueCodes(rejected)).contains("ORG_UNIT_LIMIT");
		// the task is no lane or pool
		ObjectNode tasks = layout();
		String again = shape(tasks, task, 400, 0, 90, 60, null);
		assign(tasks, "Belongs To", again, one, null);
		assign(tasks, "Belongs To", again, two, null);
		assertThat(Api.status(save(diagram, tasks))).isEqualTo(200);
		assertThat(taskShape).isNotNull();
	}

	// the search of the assignment targets

	@Test
	void theSearchListsTheActiveEntitiesOfThePartAndItsSubtypes() {
		String diagram = diagram(bpmnViewpoint());
		String department = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, base_metatype_id) VALUES ('00000000-0000-0000-0000-000000000001', ?,
				(SELECT org_unit_metatype_id FROM organization_model)) RETURNING id::text""", String.class, "Department " + Api.id());
		String prefix = "Search " + Api.id().substring(0, 8);
		int plain = entity("Organizational Unit", prefix + " plain");
		int subtype = this.api.entity(this.anna, department, prefix + " subtype");
		int removed = entity("Organizational Unit", prefix + " removed");
		entity("Role", prefix + " role");
		delete(removed);
		JsonNode page = this.api.ok(this.api.get(VIEWS + "/" + diagram + "/reference-targets?part=org-unit&q=" + prefix.replace(" ", "%20"),
				this.franz));
		assertThat(page.path("items").valueStream().map((item) -> item.path("id").asInt())).containsExactlyInAnyOrder(plain, subtype);
		assertThat(page.path("items").get(0).path("metatype").path("name").asString()).isNotBlank();
		assertThat(this.api.ok(this.api.get(VIEWS + "/" + diagram + "/reference-targets?part=role&q=" + prefix.replace(" ", "%20"),
				this.franz)).path("items")).hasSize(1);
	}

	/** query, expected status, the field named by a 422 */
	@ParameterizedTest
	@CsvSource(nullValues = "-", value = { "part=person, 200, -", "part=unknown, 422, part", "-, 422, part",
			"part=role&propertyDefId=00000000-0000-4000-8000-000000000001, 422, part",
			"propertyDefId=00000000-0000-4000-8000-000000000001, 422, propertyDefId" })
	void exactlyOneOfPartAndPropertyIsGiven(String query, int expected, String field) {
		String diagram = diagram(bpmnViewpoint());
		MvcTestResult result = this.api.get(VIEWS + "/" + diagram + "/reference-targets" + ((query != null) ? "?" + query : ""),
				this.franz);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
		if (field != null) {
			assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString)).contains(field);
		}
	}

	@Test
	void anUnsetPartIsRejectedNamingThePart() {
		String diagram = diagram(bpmnViewpoint());
		String role = this.jdbc.queryForObject("SELECT role_metatype_id::text FROM organization_model", String.class);
		this.jdbc.update("UPDATE organization_model SET role_metatype_id = NULL");
		try {
			MvcTestResult result = this.api.get(VIEWS + "/" + diagram + "/reference-targets?part=role", this.franz);
			assertThat(Api.status(result)).isEqualTo(422);
			assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString)).contains("part");
		}
		finally {
			this.jdbc.update("UPDATE organization_model SET role_metatype_id = ?::uuid", role);
		}
	}

	/** length of the search text, expected status */
	@ParameterizedTest
	@CsvSource({ "255, 200", "256, 400" })
	void theSearchTextHasAtMost255Characters(int length, int expected) {
		String diagram = diagram(bpmnViewpoint());
		MvcTestResult result = this.api.get(VIEWS + "/" + diagram + "/reference-targets?part=person&q=" + "q".repeat(length),
				this.franz);
		assertThat(Api.status(result)).isEqualTo(expected);
	}

	// helpers

	private JsonNode view(String diagram) {
		return this.api.ok(this.api.get(VIEWS + "/" + diagram, this.franz));
	}

	private List<String> roles(String diagram, int lane) {
		List<String> names = new ArrayList<>();
		shapeOf(view(diagram), lane).path("assignments").path("roles").forEach((role) -> names.add(role.path("name").asString()));
		return names;
	}

	private UUID bpmnViewpoint() {
		return UUID.fromString(this.jdbc.queryForObject("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'",
				String.class));
	}

	/** The BPMN viewpoint that also allows the belongs-to connection type. */
	private UUID withBelongsTo() {
		UUID viewpoint = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, 'bpmn2',
				(SELECT min(id) FROM persons)) RETURNING id""", UUID.class, "With belongs-to " + Api.id());
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) SELECT ?, metatype_id FROM viewpoint_metatypes "
				+ "WHERE viewpoint_id = ?", viewpoint, bpmnViewpoint());
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?::uuid)", viewpoint,
				metatype("Organizational Unit"));
		this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?::uuid)", viewpoint,
				metatype("Belongs To"));
		return viewpoint;
	}

	/** "Role" is a lane, "Organizational Unit" a pool, and the tasks and a text annotation are allowed. */
	private UUID roleLaneViewpoint() {
		UUID viewpoint = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), 'Role lanes', 'bpmn2',
				(SELECT min(id) FROM persons)) RETURNING id""", UUID.class);
		for (String type : List.of("Role", "Organizational Unit", "User Task", "Task", "Text Annotation")) {
			this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?::uuid)", viewpoint,
					metatype(type));
		}
		Map<String, String> mappings = Map.of("Role", "bpmn:Lane", "Organizational Unit", "bpmn:Pool", "User Task",
				"bpmn:UserTask", "Task", "bpmn:Task", "Text Annotation", "bpmn:TextAnnotation");
		mappings.forEach((type, element) -> this.jdbc.update(
				"INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element) VALUES (?, ?::uuid, ?)", viewpoint,
				metatype(type), element));
		return viewpoint;
	}

	private String diagram(UUID viewpoint) {
		return this.api.created(this.api.post("/api/v1/diagrams",
				Api.object().put("name", "Order handling " + Api.id()).put("viewpointId", viewpoint.toString()), this.anna))
			.path("id")
			.asString();
	}

	private int version(String diagram) {
		return this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, diagram);
	}

	private int version(int entity) {
		return this.jdbc.queryForList("SELECT version FROM entities WHERE id = ?", Integer.class, entity)
			.stream()
			.findFirst()
			.orElse(1);
	}

	private MvcTestResult save(String diagram, ObjectNode body) {
		return this.api.put(VIEWS + "/" + diagram, body, this.anna, "\"" + version(diagram) + "\"");
	}

	private ObjectNode layout() {
		ObjectNode body = Api.object();
		body.putArray("shapes");
		return body;
	}

	/** A shape of an existing entity; its ID. */
	private String shape(ObjectNode body, int entityId, double x, double y, double width, double height, String parent) {
		String id = Api.id();
		ObjectNode shape = Api.object().put("id", id).put("entityId", entityId).put("x", BigDecimal.valueOf(x))
			.put("y", BigDecimal.valueOf(y)).put("width", BigDecimal.valueOf(width)).put("height", BigDecimal.valueOf(height));
		if (parent != null) {
			shape.put("parentShapeId", parent);
		}
		((ArrayNode) body.path("shapes")).add(shape);
		return id;
	}

	/** An assignment: a new connection without an edge to an entity or a new entity. */
	private void assign(ObjectNode body, String type, String sourceShape, Integer targetEntity, String targetKey) {
		ObjectNode connection = Api.object().put("key", Api.id()).put("metatypeId", metatype(type))
			.put("sourceShapeId", sourceShape);
		if (targetEntity != null) {
			connection.put("targetEntityId", targetEntity);
		}
		if (targetKey != null) {
			connection.put("targetNewEntityKey", targetKey);
		}
		if (!body.has("newConnections")) {
			body.putArray("newConnections");
		}
		((ArrayNode) body.path("newConnections")).add(connection);
	}

	private void remove(ObjectNode body, int connection, int version) {
		if (!body.has("removedConnections")) {
			body.putArray("removedConnections");
		}
		((ArrayNode) body.path("removedConnections")).add(Api.object().put("connectionId", connection).put("expectedVersion", version));
	}

	/** The shapes of a view as a save request. */
	private ObjectNode rewrite(JsonNode view) {
		ObjectNode body = layout();
		for (JsonNode shape : view.path("shapes")) {
			ObjectNode write = Api.object().put("id", shape.path("id").asString())
				.put("entityId", shape.path("entity").path("id").asInt()).put("x", shape.path("x").decimalValue())
				.put("y", shape.path("y").decimalValue()).put("width", shape.path("width").decimalValue())
				.put("height", shape.path("height").decimalValue());
			if (!shape.path("parentShapeId").isNull()) {
				write.put("parentShapeId", shape.path("parentShapeId").asString());
			}
			((ArrayNode) body.path("shapes")).add(write);
		}
		return body;
	}

	private static JsonNode shapeOf(JsonNode view, int entityId) {
		return view.path("shapes")
			.valueStream()
			.filter((shape) -> shape.path("entity").path("id").asInt() == entityId)
			.findFirst()
			.orElseThrow();
	}

	private static String shapeId(JsonNode view, int entityId) {
		return shapeOf(view, entityId).path("id").asString();
	}

	private List<String> issueCodes(MvcTestResult result) {
		List<String> codes = new ArrayList<>();
		Api.body(result).path("errors").forEach((error) -> codes.add(error.path("code").asString()));
		return codes;
	}

	private JsonNode auditDetails(String diagram) {
		return tools.jackson.databind.json.JsonMapper.shared().readTree(this.jdbc.queryForObject(
				"SELECT details::text FROM audit.events WHERE event_type = 'diagram.layout-saved' AND target_id = ? ORDER BY id DESC LIMIT 1",
				String.class, diagram));
	}

	private int entity(String type, String name) {
		return this.api.entity(this.anna, metatype(type), name);
	}

	private int connect(String type, int source, int target) {
		return this.api.connection(this.anna, metatype(type), source, target);
	}

	private String metatype(String name) {
		return this.jdbc.queryForObject("SELECT id::text FROM metatypes WHERE name = ? AND source_package_id IN (?, ?)", String.class,
				name, BPMN, ORGANIZATION);
	}

	private static String typeTarget(String connection) {
		return switch (connection) {
			case "Belongs To" -> "Organizational Unit";
			case "Performs Role" -> "Role";
			case "Responsible" -> "Person";
			default -> "User Task";
		};
	}

	private void delete(int entity) {
		MvcTestResult result = this.api.call(HttpMethod.DELETE, "/api/v1/entities/" + entity, null, this.anna,
				"\"" + version(entity) + "\"");
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
	}

	private void undelete(int entity) {
		MvcTestResult result = this.api.call(HttpMethod.POST, "/api/v1/entities/" + entity + "/undelete", null, this.anna,
				"\"" + version(entity) + "\"");
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-diagram-assignments").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static final class JsonMapperHolder {

		static ArrayNode array() {
			return tools.jackson.databind.json.JsonMapper.shared().createArrayNode();
		}

	}

}

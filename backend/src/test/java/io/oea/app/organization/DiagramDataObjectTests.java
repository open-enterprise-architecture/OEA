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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * Specs diagram/bpmn-data-objects, diagram/bpmn-process-diagrams and diagram/bpmn-assignments (data shapes) at
 * API level (us-083-bpmn-data-objects D2 to D4, D8): the direction of data associations, data objects that do
 * not nest, the values of an output association, the collection value of the view and "Realized By" of a data
 * store with its search, save and removal. The instance has the TOGAF, the BPMN and the organization package.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramDataObjectTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final List<String> PACKAGES = List.of("oea-starter-togaf-classic", "oea-starter-bpmn",
			"oea-starter-organization");

	private static final String TRANSFORMATION_TYPE = "1b6e9540-fcfd-5cf4-afca-62dbc7785f25";

	private static final String AFFECTED_ATTRIBUTES = "d3bb9baa-ae33-5540-a74f-3ae976dc303b";

	private static final String CONDITION = "23cbc853-a1c7-550e-b263-d6f7949ed180";

	private static final String COLLECTION = "6e7ce41f-90d4-5add-a06a-0df0161a4916";

	private static boolean prepared;

	/** Process types, the data types, the associations mapped to their keys, and "Realized By" as an association. */
	private static UUID dataViewpoint;

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

	private TestPrincipals principals;

	private TestSession admin;

	private TestSession anna;

	private TestSession writer;

	private TestSession franz;

	@BeforeEach
	void instance() {
		this.api = new Api(this.mvc);
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = this.principals.personWithIdpRole("metamodel-admin");
		this.anna = this.principals.personWithIdpRole("enterprise-architect");
		this.writer = this.principals.personWithIdpRole("integration-writer");
		this.franz = this.principals.personWithIdpRole("viewer");
		if (!prepared) {
			for (String name : PACKAGES) {
				this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + name + "/import", null, this.admin));
			}
			dataViewpoint = viewpoint("Data processes", "bpmn2",
					List.of("Lane", "Role", "Organizational Unit", "Task", "User Task", "Exclusive Gateway",
							"BPMN Data Object", "BPMN Data Store", "Data Object", "System Software"),
					List.of("Data Input Association", "Data Output Association", "Realized By", "Sequence Flow"),
					pairs("Lane", "bpmn:Lane", "Role", "bpmn:Lane", "Organizational Unit", "bpmn:Pool", "Task",
							"bpmn:Task", "User Task", "bpmn:UserTask", "Exclusive Gateway", "bpmn:ExclusiveGateway",
							"BPMN Data Object", "bpmn:DataObject", "BPMN Data Store", "bpmn:DataStore", "Data Object",
							"bpmn:DataObject", "Data Input Association", "bpmn:DataInputAssociation",
							"Data Output Association", "bpmn:DataOutputAssociation", "Realized By", "bpmn:Association",
							"Sequence Flow", "bpmn:SequenceFlow"));
			prepared = true;
		}
	}

	// Requirement: Data associations and their direction

	/** association type, source type, target type, expected issue code (none: accepted) */
	@ParameterizedTest
	@CsvSource(nullValues = "-", value = { "Data Input Association, BPMN Data Object, User Task, -",
			"Data Input Association, BPMN Data Store, User Task, -", "Data Input Association, Data Object, Task, -",
			"Data Input Association, User Task, Task, DATA_ASSOCIATION_DIRECTION",
			"Data Input Association, Exclusive Gateway, User Task, DATA_ASSOCIATION_DIRECTION",
			"Data Output Association, User Task, BPMN Data Object, -",
			"Data Output Association, User Task, BPMN Data Store, -", "Data Output Association, Task, Data Object, -",
			"Data Output Association, User Task, Exclusive Gateway, DATA_ASSOCIATION_DIRECTION",
			"Data Output Association, User Task, User Task, DATA_ASSOCIATION_DIRECTION",
			"Realized By, BPMN Data Store, System Software, -", "Realized By, BPMN Data Store, BPMN Data Object, -",
			"Realized By, BPMN Data Object, System Software, DATA_ASSOCIATION_DIRECTION",
			"Realized By, User Task, BPMN Data Store, DATA_ASSOCIATION_DIRECTION" })
	void theDirectionOfANewAssociationIsChecked(String type, String source, String target, String code) {
		String diagram = diagram(dataViewpoint);
		ObjectNode body = layout();
		String from = shape(body, entity(source, "From " + Api.id()), 0, 0, 90, 60, null);
		String to = shape(body, entity(target, "To " + Api.id()), 300, 0, 90, 60, null);
		edge(body, type, from, to);
		MvcTestResult result = save(diagram, body);
		if (code == null) {
			assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
			assertThat(Api.body(result).path("edges")).hasSize(1);
		}
		else {
			assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
			assertThat(issueCodes(result)).contains(code);
			assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
				.contains("newConnections[0]");
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid",
					Integer.class, diagram)).isZero();
		}
	}

	@Test
	void aRealizedByInAViewpointOfAnotherNotationIsNotChecked() {
		UUID other = viewpoint("Not BPMN " + Api.id(), "archimate3", List.of("User Task", "BPMN Data Store"),
				List.of("Realized By"), Map.of());
		String diagram = diagram(other);
		ObjectNode body = layout();
		String from = shape(body, entity("User Task", "Task " + Api.id()), 0, 0, 90, 60, null);
		String to = shape(body, entity("BPMN Data Store", "Store " + Api.id()), 300, 0, 90, 60, null);
		edge(body, "Realized By", from, to);
		assertThat(Api.status(save(diagram, body))).isEqualTo(200);
	}

	@Test
	void aNewEdgeOfAnExistingConnectionWithTheWrongDirectionIsAcceptedAndBatchWritesAreNotChecked() {
		int one = entity("User Task", "One " + Api.id());
		int two = entity("User Task", "Two " + Api.id());
		// a batch write is checked by the endpoint constraints only: an input association from a task
		int connection = connect("Data Input Association", one, two);
		String diagram = diagram(dataViewpoint);
		ObjectNode body = layout();
		String from = shape(body, one, 0, 0, 90, 60, null);
		String to = shape(body, two, 300, 0, 90, 60, null);
		body.putArray("edges")
			.add(Api.object().put("id", Api.id()).put("connectionId", connection).put("sourceShapeId", from)
				.put("targetShapeId", to).<ObjectNode>set("points", JsonMapper.shared().createArrayNode()));
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(view.path("edges")).hasSize(1);
		assertThat(view.path("conflictCount").asInt()).isZero();
	}

	@Test
	void aTypeOfTheOrganizationMappedToTheInputKeyIsCheckedLikeThePackageType() {
		UUID mapped = viewpoint("Belongs to as input " + Api.id(), "bpmn2",
				List.of("User Task", "Organizational Unit", "BPMN Data Object"), List.of("Belongs To"),
				Map.of("User Task", "bpmn:UserTask", "BPMN Data Object", "bpmn:DataObject", "Belongs To",
						"bpmn:DataInputAssociation"));
		String diagram = diagram(mapped);
		ObjectNode wrong = layout();
		edge(wrong, "Belongs To", shape(wrong, entity("User Task", "Task " + Api.id()), 0, 0, 90, 60, null),
				shape(wrong, entity("Organizational Unit", "Unit " + Api.id()), 300, 0, 90, 60, null));
		MvcTestResult rejected = save(diagram, wrong);
		assertThat(Api.status(rejected)).as(() -> Api.content(rejected)).isEqualTo(422);
		assertThat(issueCodes(rejected)).contains("DATA_ASSOCIATION_DIRECTION");
		ObjectNode right = layout();
		edge(right, "Belongs To", shape(right, entity("BPMN Data Object", "Object " + Api.id()), 0, 0, 36, 50, null),
				shape(right, entity("Organizational Unit", "Unit " + Api.id()), 300, 0, 90, 60, null));
		assertThat(Api.status(save(diagram, right))).isEqualTo(200);
	}

	@Test
	void aViewpointWithoutTheMappingHasNoDirectionCheck() {
		UUID unmapped = viewpoint("Unmapped " + Api.id(), "bpmn2", List.of("User Task", "BPMN Data Object"),
				List.of("Data Input Association"), Map.of());
		String diagram = diagram(unmapped);
		ObjectNode body = layout();
		edge(body, "Data Input Association", shape(body, entity("User Task", "One " + Api.id()), 0, 0, 90, 60, null),
				shape(body, entity("User Task", "Two " + Api.id()), 300, 0, 90, 60, null));
		assertThat(Api.status(save(diagram, body))).isEqualTo(200);
	}

	@Test
	void aWrongDirectionTogetherWithAConflictIsA422AndStoresNothing() {
		UUID custom = viewpoint("With a gateway " + Api.id(), "bpmn2",
				List.of("User Task", "Exclusive Gateway", "BPMN Data Object"), List.of("Data Input Association"),
				Map.of("User Task", "bpmn:UserTask", "Exclusive Gateway", "bpmn:ExclusiveGateway", "BPMN Data Object",
						"bpmn:DataObject", "Data Input Association", "bpmn:DataInputAssociation"));
		String diagram = diagram(custom);
		int gateway = entity("Exclusive Gateway", "Valid? " + Api.id());
		ObjectNode first = layout();
		shape(first, gateway, 0, 0, 38, 38, null);
		JsonNode view = this.api.ok(save(diagram, first));
		// the viewpoint no longer allows gateways: the stored shape is a conflict
		this.jdbc.update("DELETE FROM viewpoint_metatypes WHERE viewpoint_id = ? AND metatype_id = ?::uuid", custom,
				metatype("Exclusive Gateway"));
		ObjectNode body = rewrite(view);
		edge(body, "Data Input Association", shape(body, entity("User Task", "One " + Api.id()), 100, 0, 90, 60, null),
				shape(body, entity("User Task", "Two " + Api.id()), 300, 0, 90, 60, null));
		int version = version(diagram);
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(issueCodes(result)).contains("DATA_ASSOCIATION_DIRECTION");
		assertThat(version(diagram)).isEqualTo(version);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid",
				Integer.class, diagram)).isOne();
		// without the wrong association the conflict is a 409
		ObjectNode conflict = rewrite(view);
		assertThat(Api.status(save(diagram, conflict))).isEqualTo(409);
	}

	// Requirement: Data objects do not nest

	@Test
	void aParentChangeForADataObjectShapeIsRejectedAndOneForATaskIsAccepted() {
		String diagram = diagram(dataViewpoint);
		int lane = entity("Lane", "Purchasing " + Api.id());
		int order = entity("BPMN Data Object", "Order " + Api.id());
		int task = entity("User Task", "Check " + Api.id());
		ObjectNode body = layout();
		String laneShape = shape(body, lane, 0, 0, 600, 200, null);
		shape(body, order, 20, 20, 36, 50, laneShape);
		shape(body, task, 100, 20, 90, 60, laneShape);
		JsonNode view = this.api.ok(save(diagram, body));
		for (boolean clear : new boolean[] { false, true }) {
			ObjectNode change = rewrite(view);
			ObjectNode entry = Api.object().put("entityId", order).put("expectedVersion", 1);
			if (!clear) {
				entry.put("parentEntityId", lane);
			}
			change.putArray("parentChanges").add(entry);
			MvcTestResult result = save(diagram, change);
			assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
			assertThat(issueCodes(result)).contains("DATA_OBJECT_PARENT");
			assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
				.contains("parentChanges[0]");
			assertThat(parent(order)).isNull();
		}
		ObjectNode accepted = rewrite(view);
		accepted.putArray("parentChanges").add(Api.object().put("entityId", task).put("expectedVersion", 1)
			.put("parentEntityId", lane));
		MvcTestResult ok = save(diagram, accepted);
		assertThat(Api.status(ok)).as(() -> Api.content(ok)).isEqualTo(200);
		assertThat(parent(task)).isEqualTo(lane);
		assertThat(parent(order)).isNull();
	}

	@Test
	void aNewDataObjectWithAParentIsRejectedAndOneWithoutIsAccepted() {
		String diagram = diagram(dataViewpoint);
		int lane = entity("Lane", "Purchasing " + Api.id());
		ObjectNode body = layout();
		String laneShape = shape(body, lane, 0, 0, 600, 200, null);
		String key = Api.id();
		body.putArray("newEntities")
			.add(Api.object().put("key", key).put("metatypeId", metatype("BPMN Data Object")).put("name", "Order " + Api.id())
				.put("parentEntityId", lane));
		newShape(body, key, 20, 20, 36, 50, laneShape);
		MvcTestResult rejected = save(diagram, body);
		assertThat(Api.status(rejected)).as(() -> Api.content(rejected)).isEqualTo(422);
		assertThat(issueCodes(rejected)).contains("DATA_OBJECT_PARENT");
		assertThat(Api.body(rejected).path("invalidFields").valueStream().map(JsonNode::asString))
			.contains("newEntities[0]");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid",
				Integer.class, diagram)).isZero();
		// without a parent, next to a task that gets the lane as parent
		ObjectNode ok = layout();
		String again = shape(ok, lane, 0, 0, 600, 200, null);
		String data = Api.id();
		String taskKey = Api.id();
		ok.putArray("newEntities")
			.add(Api.object().put("key", data).put("metatypeId", metatype("BPMN Data Object")).put("name", "Order " + Api.id()))
			.add(Api.object().put("key", taskKey).put("metatypeId", metatype("User Task")).put("name", "Check " + Api.id())
				.put("parentEntityId", lane));
		newShape(ok, data, 20, 20, 36, 50, again);
		newShape(ok, taskKey, 100, 20, 90, 60, again);
		JsonNode view = this.api.ok(save(diagram, ok));
		assertThat(view.path("shapes")).hasSize(3);
		JsonNode orderShape = view.path("shapes").get(1);
		assertThat(orderShape.path("parentShapeId").asString()).isEqualTo(again);
		assertThat(parent(orderShape.path("entity").path("id").asInt())).isNull();
		assertThat(parent(view.path("shapes").get(2).path("entity").path("id").asInt())).isEqualTo(lane);
	}

	@Test
	void aDataShapeInAnAssignmentLaneHasNoAssignmentsAndATaskHasThem() {
		String diagram = diagram(dataViewpoint);
		int pool = entity("Organizational Unit", "Order handling " + Api.id());
		int lane = entity("Role", "Approver " + Api.id());
		int object = entity("BPMN Data Object", "Order " + Api.id());
		int togaf = entity("Data Object", "Invoice " + Api.id());
		int store = entity("BPMN Data Store", "Order DB " + Api.id());
		int task = entity("User Task", "Approve " + Api.id());
		int unit = entity("Organizational Unit", "Sales " + Api.id());
		connect("Belongs To", object, unit);
		connect("Belongs To", store, unit);
		ObjectNode body = layout();
		String poolShape = shape(body, pool, 0, 0, 600, 300, null);
		String laneShape = shape(body, lane, 30, 0, 570, 150, poolShape);
		shape(body, object, 60, 20, 36, 50, laneShape);
		shape(body, togaf, 120, 20, 36, 50, laneShape);
		shape(body, store, 180, 20, 50, 50, laneShape);
		shape(body, task, 260, 20, 90, 60, laneShape);
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(shapeOf(view, object).path("assignments").isNull()).isTrue();
		assertThat(shapeOf(view, togaf).path("assignments").isNull()).isTrue();
		JsonNode storeAssignments = shapeOf(view, store).path("assignments");
		assertThat(storeAssignments.path("orgUnits")).isEmpty();
		assertThat(storeAssignments.path("roles")).isEmpty();
		assertThat(storeAssignments.path("persons")).isEmpty();
		assertThat(storeAssignments.path("realizedBy")).isEmpty();
		assertThat(shapeOf(view, task).path("assignments").path("realizedBy")).isEmpty();
		assertThat(shapeOf(view, task).path("assignments").path("orgUnits")).isEmpty();
		// a new belongs-to connection for the task from the pool is the assignment; the data objects get none
		assertThat(shapeOf(view, lane).path("assignments").path("roles")).isEmpty();
	}

	// Requirement: Transformation of an output association

	/** transformation type, expected status */
	@ParameterizedTest
	@CsvSource({ "create, 200", "enrich, 200", "overwrite, 200", "delete, 200", "read-only, 200", "merge, 422",
			"Create, 422" })
	void theTransformationTypeIsOneOfFiveValues(String value, int expected) {
		MvcTestResult result = outputWith(TRANSFORMATION_TYPE, value);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
		assertRejectedNamingTheValue(result, expected);
	}

	/** length of "Affected attributes", expected status */
	@ParameterizedTest
	@CsvSource({ "5049, 200", "5050, 422" })
	void theAffectedAttributesHaveAtMost5049Characters(int length, int expected) {
		MvcTestResult result = outputWith(AFFECTED_ATTRIBUTES, "a".repeat(length));
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
		assertRejectedNamingTheValue(result, expected);
	}

	/** length of "Condition", expected status */
	@ParameterizedTest
	@CsvSource({ "2000, 200", "2001, 422" })
	void theConditionHasAtMost2000Characters(int length, int expected) {
		MvcTestResult result = outputWith(CONDITION, "c".repeat(length));
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
		assertRejectedNamingTheValue(result, expected);
	}

	@Test
	void fiftyAttributesOfOneHundredCharactersAreStoredAndReadBack() {
		String attributes = String.join("\n", java.util.Collections.nCopies(50, "x".repeat(100)));
		assertThat(attributes).hasSize(5049);
		MvcTestResult result = outputWith(AFFECTED_ATTRIBUTES, attributes);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
		int connection = Api.body(result).path("edges").get(0).path("connection").path("id").asInt();
		assertThat(this.jdbc.queryForObject("SELECT value_text FROM entity_property_values WHERE entity_id = ? "
				+ "AND property_def_id = ?::uuid", String.class, connection, AFFECTED_ATTRIBUTES)).isEqualTo(attributes);
	}

	@Test
	void anEnrichWithTwoAttributesIsStoredOnTwoLinesAndAReadAndACreateOfTheSameObjectIsAccepted() {
		String diagram = diagram(dataViewpoint);
		int object = entity("BPMN Data Object", "Order " + Api.id());
		int task = entity("User Task", "Review order " + Api.id());
		connect("Data Input Association", object, task);
		ObjectNode body = layout();
		String objectShape = shape(body, object, 0, 0, 36, 50, null);
		String taskShape = shape(body, task, 300, 0, 90, 60, null);
		String key = Api.id();
		ObjectNode connection = Api.object().put("key", key).put("metatypeId", metatype("Data Output Association"));
		ArrayNode properties = connection.putArray("properties");
		properties.add(Api.object().put("propertyDefId", TRANSFORMATION_TYPE).put("value", "enrich"));
		properties.add(Api.object().put("propertyDefId", AFFECTED_ATTRIBUTES).put("value", "status\nreviewedBy"));
		body.putArray("newConnections").add(connection);
		body.putArray("edges")
			.add(Api.object().put("id", Api.id()).put("newConnectionKey", key).put("sourceShapeId", taskShape)
				.put("targetShapeId", objectShape).<ObjectNode>set("points", JsonMapper.shared().createArrayNode()));
		JsonNode view = this.api.ok(save(diagram, body));
		int id = view.path("edges").get(0).path("connection").path("id").asInt();
		assertThat(this.jdbc.queryForObject("SELECT value_enum FROM entity_property_values WHERE entity_id = ? "
				+ "AND property_def_id = ?::uuid", String.class, id, TRANSFORMATION_TYPE)).isEqualTo("enrich");
		assertThat(this.jdbc.queryForObject("SELECT value_text FROM entity_property_values WHERE entity_id = ? "
				+ "AND property_def_id = ?::uuid", String.class, id, AFFECTED_ATTRIBUTES)).isEqualTo("status\nreviewedBy");
		assertThat(shapeOf(view, object).path("assignments").isNull()).isTrue();
	}

	// Requirement: Data object and data store symbols

	@Test
	void collectionIsTrueOnlyForADataObjectWithTheValueTrueThatTheReaderMayRead() {
		String diagram = diagram(dataViewpoint);
		int collection = entity("BPMN Data Object", "Orders " + Api.id());
		int single = entity("BPMN Data Object", "Order " + Api.id());
		int none = entity("BPMN Data Object", "Draft " + Api.id());
		int togaf = entity("Data Object", "Invoice " + Api.id());
		int store = entity("BPMN Data Store", "Order DB " + Api.id());
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_bool) VALUES (?, ?::uuid, TRUE)",
				collection, COLLECTION);
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_bool) VALUES (?, ?::uuid, FALSE)",
				single, COLLECTION);
		ObjectNode body = layout();
		for (int entity : List.of(collection, single, none, togaf, store)) {
			shape(body, entity, 10 * entity, 0, 36, 50, null);
		}
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(shapeOf(view, collection).path("collection").asBoolean()).isTrue();
		for (int entity : List.of(single, none, togaf, store)) {
			assertThat(shapeOf(view, entity).path("collection").asBoolean()).as("entity " + entity).isFalse();
		}
		// restricted to the architects: the viewer reads false, the architect true
		this.jdbc.update("UPDATE property_definitions SET visibility_mode = 'role-restricted' WHERE id = ?::uuid", COLLECTION);
		this.jdbc.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?::uuid, "
				+ "'enterprise-architect', 'read')", COLLECTION);
		try {
			assertThat(shapeOf(this.api.ok(this.api.get(VIEWS + "/" + diagram, this.franz)), collection)
				.path("collection")
				.asBoolean()).isFalse();
			assertThat(shapeOf(this.api.ok(this.api.get(VIEWS + "/" + diagram, this.anna)), collection)
				.path("collection")
				.asBoolean()).isTrue();
		}
		finally {
			this.jdbc.update("DELETE FROM property_visibility_roles WHERE property_def_id = ?::uuid", COLLECTION);
			this.jdbc.update("UPDATE property_definitions SET visibility_mode = 'public' WHERE id = ?::uuid", COLLECTION);
		}
	}

	// Requirement: Realized by on a data store (search)

	@Test
	void theRealizerSearchListsActiveElementsOfAnyTypeAndNoConnections() {
		String diagram = diagram(dataViewpoint);
		String prefix = "Realizer " + Api.id().substring(0, 8);
		int software = entity("System Software", prefix + " software");
		// a type the viewpoint does not allow
		int component = this.api.entity(this.anna, metatype("Application Component"), prefix + " component");
		int gone = entity("System Software", prefix + " gone");
		int store = entity("BPMN Data Store", prefix + " store");
		connect("Realized By", store, software);
		delete(gone);
		JsonNode page = this.api.ok(this.api.get(VIEWS + "/" + diagram + "/reference-targets?part=realizer&q="
				+ prefix.replace(" ", "%20"), this.franz));
		assertThat(page.path("items").valueStream().map((item) -> item.path("id").asInt()))
			.containsExactlyInAnyOrder(software, component, store);
		assertThat(page.path("page").path("totalElements").asInt()).isEqualTo(3);
	}

	/** length of the search text, expected status */
	@ParameterizedTest
	@CsvSource({ "255, 200", "256, 400" })
	void theRealizerSearchTextHasAtMost255Characters(int length, int expected) {
		String diagram = diagram(dataViewpoint);
		assertThat(Api.status(this.api.get(VIEWS + "/" + diagram + "/reference-targets?part=realizer&q=" + "q".repeat(length),
				this.franz))).isEqualTo(expected);
	}

	/** page size, expected status */
	@ParameterizedTest
	@CsvSource({ "1, 200", "200, 200", "0, 400", "201, 400" })
	void theRealizerSearchPageSizeIsOneToTwoHundred(int pageSize, int expected) {
		String diagram = diagram(dataViewpoint);
		assertThat(Api.status(this.api.get(VIEWS + "/" + diagram + "/reference-targets?part=realizer&pageSize=" + pageSize,
				this.franz))).isEqualTo(expected);
	}

	@Test
	void theRealizerSearchNeedsEntityRead() {
		String diagram = diagram(dataViewpoint);
		assertThat(Api.status(this.api.get(VIEWS + "/" + diagram + "/reference-targets?part=realizer",
				this.principals.personWithoutRole()))).isEqualTo(403);
	}

	// Requirement: Realized by on a data store (save)

	@ParameterizedTest
	@ValueSource(strings = { "Realized By", "Subtype of Realized By" })
	void aRealizedByWithoutAnEdgeIsCreatedAuditedAndListedInTheView(String type) {
		String diagram = diagram(dataViewpoint);
		int store = entity("BPMN Data Store", "Order DB " + Api.id());
		int postgres = entity("System Software", "PostgreSQL " + Api.id());
		String connectionType = "Realized By".equals(type) ? metatype("Realized By") : subtype("Realized By");
		ObjectNode body = layout();
		String storeShape = shape(body, store, 0, 0, 50, 50, null);
		assignWith(body, connectionType, storeShape, postgres, null);
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(view.path("shapes")).hasSize(1);
		assertThat(view.path("edges")).isEmpty();
		JsonNode realized = shapeOf(view, store).path("assignments").path("realizedBy");
		assertThat(realized).hasSize(1);
		assertThat(realized.get(0).path("entityId").asInt()).isEqualTo(postgres);
		assertThat(realized.get(0).path("connectionVersion").asInt()).isEqualTo(1);
		int connection = realized.get(0).path("connectionId").asInt();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'entity.created' "
				+ "AND target_id = ?", Integer.class, Integer.toString(connection))).isOne();
		// the same connection also listed in the details of the data store
		assertThat(this.api.ok(this.api.get(VIEWS + "/" + diagram + "/entities/" + store, this.franz))
			.path("assignments")
			.path("realizedBy")).hasSize(1);
	}

	/** source type, what is wrong, expected code */
	@ParameterizedTest
	@CsvSource({ "User Task, source, DATA_ASSOCIATION_DIRECTION", "BPMN Data Object, source, DATA_ASSOCIATION_DIRECTION",
			"BPMN Data Store, new target, ASSIGNMENT_TARGET_INVALID", "BPMN Data Store, deleted target, TARGET_NOT_FOUND" })
	void aRealizedByWithoutAnEdgeFromAnotherShapeOrWithAnotherTargetIsRejected(String source, String what, String code) {
		String diagram = diagram(dataViewpoint);
		int from = entity(source, "From " + Api.id());
		int target = entity("System Software", "PostgreSQL " + Api.id());
		ObjectNode body = layout();
		String shape = shape(body, from, 0, 0, 50, 50, null);
		if ("new target".equals(what)) {
			assignWith(body, metatype("Realized By"), shape, null, Api.id());
		}
		else {
			if ("deleted target".equals(what)) {
				delete(target);
			}
			assignWith(body, metatype("Realized By"), shape, target, null);
		}
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		if (code != null) {
			assertThat(issueCodes(result)).contains(code);
		}
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
			.anyMatch((field) -> field.startsWith("newConnections[0]"));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid",
				Integer.class, diagram)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_connections WHERE source_entity_id = ?",
				Integer.class, from)).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Data Input Association", "Data Output Association" })
	void anotherConnectionTypeWithoutAnEdgeOutsideTheOrganizationIsStillRejected(String type) {
		String diagram = diagram(dataViewpoint);
		ObjectNode body = layout();
		String shape = shape(body, entity("BPMN Data Store", "Store " + Api.id()), 0, 0, 50, 50, null);
		assignWith(body, metatype(type), shape, entity("User Task", "Task " + Api.id()), null);
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(issueCodes(result)).contains("METATYPE_NOT_ALLOWED");
	}

	/** number of new connections, expected status */
	@ParameterizedTest
	@CsvSource({ "500, 200", "501, 400" })
	void atMost500RealizedByConnectionsAreSavedAtOnce(int count, int expected) {
		String diagram = diagram(dataViewpoint);
		int store = entity("BPMN Data Store", "Store " + Api.id());
		ObjectNode body = layout();
		String shape = shape(body, store, 0, 0, 50, 50, null);
		if (expected == 200) {
			ObjectNode software = Api.object().put("atomic", true);
			ArrayNode entities = software.putArray("entities");
			for (int i = 0; i < count; i++) {
				entities.add(Api.object().put("name", "Software " + Api.id()).put("metatypeId", metatype("System Software")));
			}
			for (JsonNode item : this.api.ok(this.api.post("/api/v1/entities/batch", software, this.anna)).path("items")) {
				assignWith(body, metatype("Realized By"), shape, item.path("id").asInt(), null);
			}
		}
		else {
			for (int i = 0; i <= count - 1; i++) {
				assignWith(body, metatype("Realized By"), shape, 1, null);
			}
			assignWith(body, metatype("Realized By"), shape, 1, null);
		}
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).as(() -> Api.content(result).substring(0, Math.min(500, Api.content(result).length())))
			.isEqualTo(expected);
	}

	// Requirement: Realized by on a data store (removal)

	@Test
	void aRealizedByIsRemovedSoftlyAuditedAndLeftOutOfTheView() {
		String diagram = diagram(dataViewpoint);
		int store = entity("BPMN Data Store", "Order DB " + Api.id());
		int postgres = entity("System Software", "PostgreSQL " + Api.id());
		int connection = connect("Realized By", store, postgres);
		ObjectNode body = layout();
		shape(body, store, 0, 0, 50, 50, null);
		JsonNode view = this.api.ok(save(diagram, body));
		assertThat(shapeOf(view, store).path("assignments").path("realizedBy")).hasSize(1);
		ObjectNode removal = rewrite(view);
		remove(removal, connection, 1);
		JsonNode saved = this.api.ok(save(diagram, removal));
		assertThat(shapeOf(saved, store).path("assignments").path("realizedBy")).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM entities WHERE id = ?", Boolean.class,
				connection)).isTrue();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'entity.deleted' AND target_id = ?",
				Integer.class, Integer.toString(connection))).isOne();
		undelete(connection);
		assertThat(shapeOf(view(diagram), store).path("assignments").path("realizedBy")).hasSize(1);
		// a deleted target is left out
		delete(postgres);
		assertThat(shapeOf(view(diagram), store).path("assignments").path("realizedBy")).isEmpty();
	}

	@Test
	void removingARealizedByNeedsEntityDeleteAndTheDenialComesBeforeAnInvalidShape() {
		String diagram = diagram(dataViewpoint);
		int store = entity("BPMN Data Store", "Order DB " + Api.id());
		int connection = connect("Realized By", store, entity("System Software", "PostgreSQL " + Api.id()));
		ObjectNode body = layout();
		shape(body, store, 0, 0, 50, 50, null);
		this.api.ok(save(diagram, body));
		ObjectNode request = layout();
		((ArrayNode) request.path("shapes")).add(Api.object().put("id", Api.id()).put("entityId", 999999).put("x", 0)
			.put("y", 0).put("width", 90).put("height", 60));
		remove(request, connection, 1);
		MvcTestResult denied = this.api.put(VIEWS + "/" + diagram, request, this.writer, "\"" + version(diagram) + "\"");
		assertThat(Api.status(denied)).as(() -> Api.content(denied)).isEqualTo(403);
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NULL FROM entities WHERE id = ?", Boolean.class, connection))
			.isTrue();
	}

	@Test
	void aStaleVersionOfARemovedRealizedByIsAConflict() {
		String diagram = diagram(dataViewpoint);
		int store = entity("BPMN Data Store", "Order DB " + Api.id());
		int connection = connect("Realized By", store, entity("System Software", "PostgreSQL " + Api.id()));
		ObjectNode body = layout();
		shape(body, store, 0, 0, 50, 50, null);
		this.api.ok(save(diagram, body));
		this.jdbc.update("UPDATE entities SET version = version + 1 WHERE id = ?", connection);
		ObjectNode removal = rewrite(view(diagram));
		remove(removal, connection, 1);
		MvcTestResult result = save(diagram, removal);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(409);
		assertThat(Api.content(result)).contains("entity-version-conflict").contains("removedConnections[0]");
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NULL FROM entities WHERE id = ?", Boolean.class, connection))
			.isTrue();
	}

	/** what is removed, the expected code */
	@ParameterizedTest
	@CsvSource({ "shown by an edge, REMOVED_CONNECTION_SHOWN", "other type, NOT_AN_ASSIGNMENT",
			"unrelated, SOURCE_NOT_ON_DIAGRAM" })
	void aRemovedConnectionThatAnEdgeShowsOrIsOfAnotherTypeIsRejected(String what, String code) {
		String diagram = diagram(dataViewpoint);
		int store = entity("BPMN Data Store", "Order DB " + Api.id());
		int software = entity("System Software", "PostgreSQL " + Api.id());
		int connection = connect("Realized By", store, software);
		int task = entity("User Task", "Task " + Api.id());
		int input = connect("Data Input Association", store, task);
		int elsewhere = connect("Realized By", entity("BPMN Data Store", "Other " + Api.id()), software);
		ObjectNode body = layout();
		String storeShape = shape(body, store, 0, 0, 50, 50, null);
		if ("shown by an edge".equals(what)) {
			String softwareShape = shape(body, software, 300, 0, 90, 60, null);
			body.putArray("edges")
				.add(Api.object().put("id", Api.id()).put("connectionId", connection).put("sourceShapeId", storeShape)
					.put("targetShapeId", softwareShape).<ObjectNode>set("points", JsonMapper.shared().createArrayNode()));
		}
		int removed = switch (what) {
			case "other type" -> input;
			case "unrelated" -> elsewhere;
			default -> connection;
		};
		remove(body, removed, 1);
		MvcTestResult result = save(diagram, body);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(issueCodes(result)).contains(code);
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NULL FROM entities WHERE id = ?", Boolean.class, connection))
			.isTrue();
	}

	// helpers

	/** A diagram with an output association from a task to a data object that carries one value. */
	private MvcTestResult outputWith(String property, String value) {
		String diagram = diagram(dataViewpoint);
		ObjectNode body = layout();
		String task = shape(body, entity("User Task", "Review " + Api.id()), 0, 0, 90, 60, null);
		String object = shape(body, entity("BPMN Data Object", "Order " + Api.id()), 300, 0, 36, 50, null);
		String key = Api.id();
		ObjectNode connection = Api.object().put("key", key).put("metatypeId", metatype("Data Output Association"));
		connection.putArray("properties").add(Api.object().put("propertyDefId", property).put("value", value));
		body.putArray("newConnections").add(connection);
		body.putArray("edges")
			.add(Api.object().put("id", Api.id()).put("newConnectionKey", key).put("sourceShapeId", task)
				.put("targetShapeId", object).<ObjectNode>set("points", JsonMapper.shared().createArrayNode()));
		MvcTestResult result = save(diagram, body);
		if (Api.status(result) != 200) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid",
					Integer.class, diagram)).isZero();
		}
		return result;
	}

	private void assertRejectedNamingTheValue(MvcTestResult result, int expected) {
		if (expected == 422) {
			assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
				.anyMatch((field) -> field.startsWith("newConnections[0]"));
		}
	}

	private JsonNode view(String diagram) {
		return this.api.ok(this.api.get(VIEWS + "/" + diagram, this.franz));
	}

	/** A viewpoint of the notation with the allowed element and connection types and the mappings by type name. */
	private UUID viewpoint(String name, String notation, List<String> types, List<String> connections,
			Map<String, String> mappings) {
		UUID viewpoint = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, ?,
				(SELECT min(id) FROM persons)) RETURNING id""", UUID.class, name, notation);
		for (String type : types) {
			this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?::uuid)", viewpoint,
					metatype(type));
		}
		for (String type : connections) {
			this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?::uuid)",
					viewpoint, metatype(type));
		}
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

	private Integer parent(int entity) {
		return this.jdbc.queryForObject("SELECT parent_entity_id FROM entities WHERE id = ?", Integer.class, entity);
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

	/** A shape of a new entity; its ID. */
	private String newShape(ObjectNode body, String key, double x, double y, double width, double height, String parent) {
		String id = Api.id();
		ObjectNode shape = Api.object().put("id", id).put("newEntityKey", key).put("x", BigDecimal.valueOf(x))
			.put("y", BigDecimal.valueOf(y)).put("width", BigDecimal.valueOf(width)).put("height", BigDecimal.valueOf(height));
		if (parent != null) {
			shape.put("parentShapeId", parent);
		}
		((ArrayNode) body.path("shapes")).add(shape);
		return id;
	}

	/** A new connection of the type with one edge between two shapes. */
	private void edge(ObjectNode body, String type, String sourceShape, String targetShape) {
		String key = Api.id();
		if (!body.has("newConnections")) {
			body.putArray("newConnections");
			body.putArray("edges");
		}
		((ArrayNode) body.path("newConnections")).add(Api.object().put("key", key).put("metatypeId", metatype(type)));
		((ArrayNode) body.path("edges"))
			.add(Api.object().put("id", Api.id()).put("newConnectionKey", key).put("sourceShapeId", sourceShape)
				.put("targetShapeId", targetShape).<ObjectNode>set("points", JsonMapper.shared().createArrayNode()));
	}

	/** A connection without an edge to an entity or a new entity. */
	private void assignWith(ObjectNode body, String metatypeId, String sourceShape, Integer targetEntity, String targetKey) {
		ObjectNode connection = Api.object().put("key", Api.id()).put("metatypeId", metatypeId)
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

	private List<String> issueCodes(MvcTestResult result) {
		List<String> codes = new ArrayList<>();
		Api.body(result).path("errors").forEach((error) -> codes.add(error.path("code").asString()));
		return codes;
	}

	private int entity(String type, String name) {
		return this.api.entity(this.anna, metatype(type), name);
	}

	private int connect(String type, int source, int target) {
		return this.api.connection(this.anna, metatype(type), source, target);
	}

	private String metatype(String name) {
		return this.jdbc.queryForObject("SELECT id::text FROM metatypes WHERE name = ? AND source_package_id = ANY (?)",
				String.class, name, (Object) PACKAGES.toArray(String[]::new));
	}

	/** A metatype of the organization with the base type "Realized By", also allowed in the data viewpoint. */
	private String subtype(String base) {
		String id = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection, base_metatype_id)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, TRUE, ?::uuid) RETURNING id::text""", String.class,
				"Realizes " + Api.id().substring(0, 8), metatype(base));
		this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?::uuid)",
				dataViewpoint, id);
		return id;
	}

	private void delete(int entity) {
		MvcTestResult result = this.api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/entities/" + entity, null,
				this.anna, "\"" + version(entity) + "\"");
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
	}

	private void undelete(int entity) {
		MvcTestResult result = this.api.call(org.springframework.http.HttpMethod.POST,
				"/api/v1/entities/" + entity + "/undelete", null, this.anna, "\"" + version(entity) + "\"");
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
	}

	private static Map<String, String> pairs(String... keysAndValues) {
		Map<String, String> map = new java.util.LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			map.put(keysAndValues[i], keysAndValues[i + 1]);
		}
		return map;
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-diagram-data-objects").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

package io.oea.app.bpmn;

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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Specs metamodel/starter-packages (BPMN starter package) and diagram/bpmn-process-diagrams at API level
 * (us-074-bpmn-process-diagrams D1, D2, D9). The import changes the shared metamodel, so the class has a
 * database of its own and imports the package once, in the first tests; the others rely on the order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BpmnStarterPackageTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String BPMN = "oea-starter-bpmn";

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final String DIAGRAMS = "/api/v1/diagrams";

	private static final String VIEWPOINT = "Process Model (BPMN 2.0)";

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

	private TestSession admin;

	private TestSession anna;

	private TestSession franz;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = this.principals.personWithIdpRole("metamodel-admin");
		this.anna = this.principals.personWithIdpRole("enterprise-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
	}

	// Requirement: BPMN starter package

	@Test
	@Order(1)
	void theListShowsThePackageAsNotImported() {
		JsonNode entry = shipped(ok(call(HttpMethod.GET, "/api/v1/metamodel/starter-packages", null, this.admin, null)));
		assertThat(entry.path("name").asString()).isEqualTo("BPMN 2.0 process modeling");
		assertThat(entry.path("metatypeCount").asInt()).isEqualTo(31);
		assertThat(entry.path("propertyDefinitionCount").asInt()).isEqualTo(2);
		assertThat(entry.path("metatypeNames").valueStream().map(JsonNode::asString)).contains("Pool", "User Task",
				"Sequence Flow", "Message Start Event");
		assertThat(entry.path("imported").asBoolean()).isFalse();
	}

	@Test
	@Order(2)
	void importedAloneWithoutAComposition() {
		MvcTestResult result = call(HttpMethod.POST, "/api/v1/metamodel/starter-packages/" + BPMN + "/import", null,
				this.admin, null);
		JsonNode outcome = ok(result);
		assertThat(outcome.path("created").asInt()).isEqualTo(31);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = ? AND scope = 'imported' AND NOT is_connection"))
			.isEqualTo(26);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = ? AND is_connection")).isEqualTo(5);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = ? AND is_composition")).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes WHERE is_composition", Integer.class)).isZero();
		assertThat(count("SELECT count(*) FROM viewpoints WHERE source_package_id = ? AND notation = 'bpmn2'")).isOne();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM property_definitions WHERE name IN "
				+ "('Association direction', 'Group category')", Integer.class)).isEqualTo(2);
		assertThat(shipped(ok(call(HttpMethod.GET, "/api/v1/metamodel/starter-packages", null, this.admin, null)))
			.path("imported")
			.asBoolean()).isTrue();
	}

	@Test
	@Order(3)
	void aSecondImportCreatesNothingNew() {
		int before = this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Integer.class);
		JsonNode outcome = ok(call(HttpMethod.POST, "/api/v1/metamodel/starter-packages/" + BPMN + "/import", null,
				this.admin, null));
		assertThat(outcome.path("created").asInt()).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Integer.class)).isEqualTo(before);
	}

	@Test
	@Order(4)
	void importNeedsMetamodelWrite() {
		assertThat(status(call(HttpMethod.POST, "/api/v1/metamodel/starter-packages/" + BPMN + "/import", null,
				this.franz, null))).isEqualTo(403);
	}

	/** subtype, base type */
	@ParameterizedTest
	@CsvSource({ "User Task, Task", "Service Task, Task", "Send Task, Task", "Receive Task, Task",
			"Business Rule Task, Task", "Script Task, Task", "Message Start Event, Start Event",
			"Timer Start Event, Start Event", "Message End Event, End Event", "Terminate End Event, End Event" })
	@Order(5)
	void variantsHaveTheirBaseType(String name, String base) {
		assertThat(this.jdbc.queryForObject("""
				SELECT b.name FROM metatypes m JOIN metatypes b ON b.id = m.base_metatype_id WHERE m.name = ?""",
				String.class, name)).isEqualTo(base);
	}

	/** the types that have no base type */
	@ParameterizedTest
	@CsvSource({ "Task", "Start Event", "End Event", "Pool", "Lane", "Sub-Process", "Call Activity", "Process",
			"Exclusive Gateway", "Group", "Text Annotation", "Sequence Flow" })
	@Order(5)
	void otherTypesHaveNoBaseType(String name) {
		assertThat(this.jdbc.queryForObject("SELECT base_metatype_id FROM metatypes WHERE name = ?", UUID.class, name))
			.isNull();
	}

	/** metatype, notation element, width, height */
	@ParameterizedTest
	@CsvSource({ "Pool, bpmn:Pool, 600, 200", "Lane, bpmn:Lane, 570, 100", "Task, bpmn:Task, 90, 60",
			"User Task, bpmn:UserTask, 90, 60", "Service Task, bpmn:ServiceTask, 90, 60",
			"Send Task, bpmn:SendTask, 90, 60", "Receive Task, bpmn:ReceiveTask, 90, 60",
			"Business Rule Task, bpmn:BusinessRuleTask, 90, 60", "Script Task, bpmn:ScriptTask, 90, 60",
			"Sub-Process, bpmn:SubProcess, 270, 150", "Call Activity, bpmn:CallActivity, 90, 60",
			"Start Event, bpmn:StartEvent, 27, 27", "Message Start Event, bpmn:MessageStartEvent, 27, 27",
			"Timer Start Event, bpmn:TimerStartEvent, 27, 27", "End Event, bpmn:EndEvent, 27, 27",
			"Message End Event, bpmn:MessageEndEvent, 27, 27", "Terminate End Event, bpmn:TerminateEndEvent, 27, 27",
			"Intermediate Catch Event, bpmn:IntermediateCatchEvent, 27, 27",
			"Intermediate Throw Event, bpmn:IntermediateThrowEvent, 27, 27",
			"Exclusive Gateway, bpmn:ExclusiveGateway, 38, 38", "Parallel Gateway, bpmn:ParallelGateway, 38, 38",
			"Inclusive Gateway, bpmn:InclusiveGateway, 38, 38", "Event-Based Gateway, bpmn:EventBasedGateway, 38, 38",
			"Text Annotation, bpmn:TextAnnotation, 90, 45", "Group, bpmn:Group, 180, 120",
			"Process, , 90, 30" })
	@Order(6)
	void elementTypesAreMappedAndSized(String name, String element, int width, int height) {
		JsonNode viewpoint = viewpoint();
		String id = metatype(name);
		JsonNode mapping = byMetatype(viewpoint.path("mappings"), id);
		if (element == null || element.isBlank()) {
			assertThat(mapping).as("unmapped").isNull();
		}
		else {
			assertThat(mapping.path("notationElement").asString()).isEqualTo(element);
		}
		JsonNode size = byMetatype(viewpoint.path("sizes"), id);
		assertThat(size.path("width").asInt()).isEqualTo(width);
		assertThat(size.path("height").asInt()).isEqualTo(height);
	}

	/** connection type, notation element, length */
	@ParameterizedTest
	@CsvSource({ "Sequence Flow, bpmn:SequenceFlow, 45", "Message Flow, bpmn:MessageFlow, 90",
			"Association, bpmn:Association, 45", "Calls, , ", "References Process, , " })
	@Order(6)
	void connectionTypesAreMappedAndSized(String name, String element, Integer length) {
		JsonNode viewpoint = viewpoint();
		String id = metatype(name);
		JsonNode mapping = byMetatype(viewpoint.path("mappings"), id);
		if (element == null || element.isBlank()) {
			assertThat(mapping).isNull();
		}
		else {
			assertThat(mapping.path("notationElement").asString()).isEqualTo(element);
		}
		JsonNode size = byMetatype(viewpoint.path("sizes"), id);
		if (length == null) {
			assertThat(size).isNull();
		}
		else {
			assertThat(size.path("length").asInt()).isEqualTo(length);
		}
		assertThat(viewpoint.path("allowedRelations").valueStream().map((m) -> m.path("id").asString()))
			.contains(id);
	}

	@Test
	@Order(7)
	void theViewpointAllowsEveryTypeOfThePackage() {
		JsonNode viewpoint = viewpoint();
		assertThat(viewpoint.path("notation").asString()).isEqualTo("bpmn2");
		assertThat(viewpoint.path("allowedMetatypes")).hasSize(26);
		assertThat(viewpoint.path("allowedRelations")).hasSize(5);
		assertThat(viewpoint.path("sourcePackage").path("packageId").asString()).isEqualTo(BPMN);
	}

	@Test
	@Order(8)
	void labelsInEnglishAndGerman() {
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM i18n_entries WHERE locale = 'de' AND entity_type = 'metatype'
				AND value IN ('Aufgabe', 'Benutzeraufgabe', 'Sequenzfluss', 'Nachrichtenfluss', 'Exklusives Gateway')""",
				Integer.class)).isEqualTo(5);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM i18n_entries WHERE locale = 'en' AND entity_type = "
				+ "'metatype' AND value = 'User Task'", Integer.class)).isOne();
	}

	// Requirement: BPMN flows between BPMN elements only (BR-01)

	/** source type, target type, flow type, field, code (null: accepted) */
	@ParameterizedTest
	@CsvSource(nullValues = "-", value = { "User Task, Exclusive Gateway, Sequence Flow, -, -",
			"Start Event, User Task, Sequence Flow, -, -", "Sub-Process, End Event, Sequence Flow, -, -",
			"User Task, Task, Sequence Flow, -, -", "Message Start Event, Terminate End Event, Sequence Flow, -, -",
			"User Task, Pool, Sequence Flow, newConnections[0], TARGET_NOT_ALLOWED",
			"Pool, User Task, Sequence Flow, newConnections[0], SOURCE_NOT_ALLOWED",
			"User Task, Text Annotation, Sequence Flow, newConnections[0], TARGET_NOT_ALLOWED",
			"User Task, Process, Sequence Flow, newConnections[0], TARGET_NOT_ALLOWED",
			"Pool, Pool, Message Flow, -, -", "Pool, User Task, Message Flow, -, -",
			"Send Task, Receive Task, Message Flow, -, -", "Start Event, Intermediate Throw Event, Message Flow, -, -",
			"Exclusive Gateway, User Task, Message Flow, newConnections[0], SOURCE_NOT_ALLOWED",
			"Lane, Pool, Message Flow, newConnections[0], SOURCE_NOT_ALLOWED",
			"Text Annotation, User Task, Association, -, -", "Text Annotation, Pool, Association, -, -",
			"Text Annotation, Exclusive Gateway, Association, -, -", "Text Annotation, Group, Association, -, -",
			"User Task, Text Annotation, Association, newConnections[0], SOURCE_NOT_ALLOWED",
			"Text Annotation, Process, Association, newConnections[0], TARGET_NOT_ALLOWED",
			"Call Activity, Process, Calls, -, -", "Task, Process, Calls, newConnections[0], SOURCE_NOT_ALLOWED",
			"Call Activity, Task, Calls, newConnections[0], TARGET_NOT_ALLOWED",
			"Pool, Process, References Process, -, -", "Lane, Process, References Process, newConnections[0], SOURCE_NOT_ALLOWED" })
	@Order(9)
	void flowEndpoints(String source, String target, String flow, String field, String code) {
		ObjectNode body = layout(List.of(new Item("a", source, 0, 0), new Item("b", target, 300, 0)));
		addFlow(body, flow, "a", "b");
		String diagram = diagram();
		MvcTestResult result = save(diagram, 1, body);
		if (code == null) {
			assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
			assertThat(ok(result).path("edges")).hasSize(1);
		}
		else {
			assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
			JsonNode error = body(result).path("errors").get(0);
			assertThat(error.path("field").asString()).isEqualTo(field);
			assertThat(error.path("code").asString()).isEqualTo(code);
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid",
					Integer.class, diagram)).isZero();
		}
	}

	@Test
	@Order(10)
	void aSequenceFlowToAnApplicationComponentIsRejected() {
		UUID component = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001',
				'Application Component') RETURNING id""", UUID.class);
		UUID viewpoint = UUID.fromString(viewpoint().path("id").asString());
		UUID custom = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), 'BPMN and applications',
				'bpmn2', (SELECT min(id) FROM persons)) RETURNING id""", UUID.class);
		this.jdbc.update("""
				INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id)
				SELECT ?, metatype_id FROM viewpoint_metatypes WHERE viewpoint_id = ?""", custom, viewpoint);
		this.jdbc.update("""
				INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id)
				SELECT ?, metatype_id FROM viewpoint_connection_types WHERE viewpoint_id = ?""", custom, viewpoint);
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", custom, component);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		ArrayNode entities = body.putArray("newEntities");
		String taskKey = id();
		String componentKey = id();
		entities.add(newEntity(taskKey, UUID.fromString(metatype("User Task")), "Check order"));
		entities.add(newEntity(componentKey, component, "SAP ERP"));
		String taskShape = id();
		String componentShape = id();
		shapes.add(newShape(taskShape, taskKey, 0, 0, 90, 60));
		shapes.add(newShape(componentShape, componentKey, 300, 0, 90, 30));
		String key = id();
		body.putArray("newConnections").add(newConnection(key, metatype("Sequence Flow")));
		body.putArray("edges").add(newEdge(id(), key, taskShape, componentShape));
		MvcTestResult result = call(HttpMethod.PUT, VIEWS + "/" + diagram(custom), body, this.anna, "\"1\"");
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		assertThat(body(result).path("errors").get(0).path("code").asString()).isEqualTo("TARGET_NOT_ALLOWED");
	}

	@Test
	@Order(11)
	void aCompanySubtypeOfAUserTaskIsAnEndpointOfASequenceFlow() {
		UUID approval = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, base_metatype_id)
				VALUES ('00000000-0000-0000-0000-000000000001', 'Approval Task', ?::uuid) RETURNING id""", UUID.class,
				metatype("User Task"));
		UUID custom = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), 'Approvals', 'bpmn2',
				(SELECT min(id) FROM persons)) RETURNING id""", UUID.class);
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?), (?, ?::uuid)",
				custom, approval, custom, metatype("End Event"));
		this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?::uuid)", custom,
				metatype("Sequence Flow"));
		this.jdbc.update("INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element) VALUES (?, ?, 'bpmn:UserTask')",
				custom, approval);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		ArrayNode entities = body.putArray("newEntities");
		String taskKey = id();
		String endKey = id();
		entities.add(newEntity(taskKey, approval, "Approve order"));
		entities.add(newEntity(endKey, UUID.fromString(metatype("End Event")), "Approved"));
		String taskShape = id();
		String endShape = id();
		shapes.add(newShape(taskShape, taskKey, 0, 0, 90, 60));
		shapes.add(newShape(endShape, endKey, 300, 0, 27, 27));
		String key = id();
		body.putArray("newConnections").add(newConnection(key, metatype("Sequence Flow")));
		body.putArray("edges").add(newEdge(id(), key, taskShape, endShape));
		MvcTestResult result = call(HttpMethod.PUT, VIEWS + "/" + diagram(custom), body, this.anna, "\"1\"");
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		assertThat(ok(result).path("edges")).hasSize(1);
	}

	// Requirement: Descriptive process saved and restored

	@Test
	@Order(12)
	void theDescriptiveProcessIsSavedAndRestoredUnchanged() {
		String diagram = diagram();
		String category = "c".repeat(255);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		ArrayNode entities = body.putArray("newEntities");
		ArrayNode connections = body.putArray("newConnections");
		ArrayNode edges = body.putArray("edges");
		Map<String, String> keys = new java.util.LinkedHashMap<>();
		Map<String, String> shapeIds = new java.util.LinkedHashMap<>();

		// pool A with a lane and a nested lane, the lane holding tasks, a gateway and an event
		record Placed(String name, String type, String parent, double x, double y, double width, double height) {
		}
		List<Placed> placed = new ArrayList<>(List.of(new Placed("Customer", "Pool", null, 0, 0, 700, 400),
				new Placed("Sales", "Lane", "Customer", 30, 10, 660, 380),
				new Placed("Back office", "Lane", "Sales", 60, 20, 620, 360),
				new Placed("Order received", "Message Start Event", "Back office", 80, 60, 27, 27),
				new Placed("Check order", "User Task", "Back office", 150, 50, 90, 60),
				new Placed("Valid?", "Exclusive Gateway", "Back office", 280, 60, 38, 38),
				new Placed("Ship order", "Service Task", "Back office", 360, 50, 90, 60),
				new Placed("Supplier", "Pool", null, 0, 500, 700, 200)));
		// one element of every other type on the canvas
		int column = 0;
		for (String type : List.of("Process", "Task", "Send Task", "Receive Task", "Business Rule Task", "Script Task",
				"Sub-Process", "Call Activity", "Start Event", "Timer Start Event", "End Event", "Message End Event",
				"Terminate End Event", "Intermediate Catch Event", "Intermediate Throw Event", "Parallel Gateway",
				"Inclusive Gateway", "Event-Based Gateway", "Text Annotation", "Group", "Lane")) {
			placed.add(new Placed(type + " alone", type, null, 800 + 300 * (column % 7), 100 * (column / 7), 90, 60));
			column++;
		}
		for (Placed item : placed) {
			String key = id();
			String shape = id();
			keys.put(item.name(), key);
			shapeIds.put(item.name(), shape);
			ObjectNode entity = newEntity(key, UUID.fromString(metatype(item.type())), item.name());
			if (item.parent() != null) {
				entity.put("parentNewEntityKey", keys.get(item.parent()));
			}
			if (item.type().equals("Group")) {
				entity.putArray("properties")
					.add(JsonMapper.shared()
						.createObjectNode()
						.put("propertyDefId", propertyDefinition("Group category"))
						.put("value", category));
			}
			entities.add(entity);
			ObjectNode shapeNode = newShape(shape, key, item.x(), item.y(), item.width(), item.height());
			if (item.parent() != null) {
				shapeNode.put("parentShapeId", shapeIds.get(item.parent()));
			}
			shapes.add(shapeNode);
		}
		record Link(String type, String source, String target, String direction) {
		}
		List<Link> links = List.of(new Link("Sequence Flow", "Check order", "Valid?", null),
				new Link("Sequence Flow", "Order received", "Check order", null),
				new Link("Message Flow", "Customer", "Supplier", null),
				new Link("Calls", "Call Activity alone", "Process alone", null),
				new Link("References Process", "Supplier", "Process alone", null),
				new Link("Association", "Text Annotation alone", "Check order", "both"));
		for (Link link : links) {
			String key = id();
			ObjectNode connection = newConnection(key, metatype(link.type()));
			if (link.direction() != null) {
				connection.putArray("properties")
					.add(JsonMapper.shared()
						.createObjectNode()
						.put("propertyDefId", propertyDefinition("Association direction"))
						.put("value", link.direction()));
			}
			connections.add(connection);
			edges.add(newEdge(id(), key, shapeIds.get(link.source()), shapeIds.get(link.target())));
		}
		MvcTestResult saved = call(HttpMethod.PUT, VIEWS + "/" + diagram, body, this.anna, "\"1\"");
		assertThat(status(saved)).as(() -> content(saved)).isEqualTo(200);

		JsonNode view = ok(call(HttpMethod.GET, VIEWS + "/" + diagram, null, this.franz, null));
		assertThat(view.path("shapes")).hasSize(placed.size());
		assertThat(view.path("edges")).hasSize(links.size());
		assertThat(view.path("conflictCount").asInt()).isZero();
		for (Placed item : placed) {
			JsonNode shape = shapeOf(view, shapeIds.get(item.name()));
			assertThat(shape.path("entity").path("name").asString()).isEqualTo(item.name());
			assertThat(shape.path("entity").path("metatypeId").asString()).isEqualTo(metatype(item.type()));
			assertThat(shape.path("x").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(item.x()));
			assertThat(shape.path("width").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(item.width()));
			if (item.parent() == null) {
				assertThat(shape.path("parentShapeId").isNull()).as(item.name()).isTrue();
			}
			else {
				assertThat(shape.path("parentShapeId").asString()).as(item.name()).isEqualTo(shapeIds.get(item.parent()));
				assertThat(shape.path("entity").path("parentEntityId").asInt()).as(item.name())
					.isEqualTo(entityId(item.parent()));
			}
		}
		for (JsonNode edge : view.path("edges")) {
			assertThat(edge.path("conflict").asBoolean()).isFalse();
		}
		assertThat(view.path("edges").valueStream().map((e) -> e.path("connection").path("metatypeId").asString()))
			.containsExactlyInAnyOrder(metatype("Sequence Flow"), metatype("Sequence Flow"), metatype("Message Flow"),
					metatype("Calls"), metatype("References Process"), metatype("Association"));
		JsonNode association = view.path("edges").valueStream()
			.filter((e) -> e.path("connection").path("metatypeId").asString().equals(metatype("Association")))
			.findFirst().orElseThrow();
		JsonNode connection = ok(call(HttpMethod.GET,
				VIEWS + "/" + diagram + "/entities/" + association.path("connection").path("id").asInt(), null,
				this.franz, null));
		assertThat(connection.path("properties").valueStream()
			.filter((p) -> p.path("propertyName").asString().equals("Association direction"))
			.map((p) -> p.path("valueEnum").asString())).containsExactly("both");
		JsonNode group = ok(call(HttpMethod.GET, VIEWS + "/" + diagram + "/entities/" + entityId("Group alone"),
				null, this.franz, null));
		assertThat(group.path("properties").valueStream()
			.filter((p) -> p.path("propertyName").asString().equals("Group category"))
			.map((p) -> p.path("valueText").asString())).containsExactly(category);
	}

	/** length of the group category, status */
	@ParameterizedTest
	@CsvSource({ "255, 200", "256, 422" })
	@Order(13)
	void theGroupCategoryHasAtMost255Characters(int length, int status) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		String key = id();
		ObjectNode entity = newEntity(key, UUID.fromString(metatype("Group")), "Group " + length);
		entity.putArray("properties")
			.add(JsonMapper.shared()
				.createObjectNode()
				.put("propertyDefId", propertyDefinition("Group category"))
				.put("value", "g".repeat(length)));
		body.putArray("newEntities").add(entity);
		body.putArray("shapes").add(newShape(id(), key, 0, 0, 180, 120));
		MvcTestResult result = call(HttpMethod.PUT, VIEWS + "/" + diagram(), body, this.anna, "\"1\"");
		assertThat(status(result)).as(() -> content(result)).isEqualTo(status);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = ?", Integer.class,
				"Group " + length)).isEqualTo(status == 200 ? 1 : 0);
	}

	@Test
	@Order(14)
	void anUnknownAssociationDirectionIsRejected() {
		ObjectNode body = layout(List.of(new Item("a", "Text Annotation", 0, 0), new Item("b", "User Task", 300, 0)));
		addFlow(body, "Association", "a", "b");
		((ObjectNode) body.path("newConnections").get(0)).putArray("properties")
			.add(JsonMapper.shared()
				.createObjectNode()
				.put("propertyDefId", propertyDefinition("Association direction"))
				.put("value", "sideways"));
		MvcTestResult result = save(diagram(), 1, body);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
	}

	// Requirement: Collapsed sub-processes (us-074-bpmn-pools-and-lanes D2)

	@Test
	@Order(15)
	void aSubProcessIsSavedCollapsedWithItsExpandedSizeAndExpandedAgain() {
		String diagram = diagram();
		ObjectNode body = subProcessLayout(true);
		JsonNode view = ok(save(diagram, 1, body));
		JsonNode sub = shape(view, "Check credit");
		assertThat(sub.path("collapsed").asBoolean()).isTrue();
		assertThat(sub.path("width").decimalValue()).isEqualByComparingTo("270");
		assertThat(sub.path("height").decimalValue()).isEqualByComparingTo("150");
		JsonNode task = shape(view, "Check score");
		assertThat(task.path("collapsed").asBoolean()).isFalse();
		assertThat(task.path("parentShapeId").asString()).isEqualTo(sub.path("id").asString());
		assertThat(view(diagram).path("shapes").valueStream().filter((s) -> s.path("collapsed").asBoolean()).count())
			.isEqualTo(1);
		// the entities and their parents are not touched
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name IN ('Check credit', 'Check score') AND parent_entity_id IS NOT NULL",
				Integer.class)).isZero();
		// expanded again
		JsonNode expanded = ok(save(diagram, 2, rewrite(view, Map.of(sub.path("id").asString(), false), null)));
		assertThat(shape(expanded, "Check credit").path("collapsed").asBoolean()).isFalse();
		assertThat(shape(expanded, "Check credit").path("height").decimalValue()).isEqualByComparingTo("150");
		assertThat(auditDetails(diagram).path("changed").asInt()).isEqualTo(1);
	}

	@Test
	@Order(16)
	void theFlagIsAnAuditedChangeOnlyWhenItChanges() {
		String diagram = diagram();
		JsonNode view = ok(save(diagram, 1, subProcessLayout(false)));
		String sub = shape(view, "Check credit").path("id").asString();
		JsonNode collapsed = ok(save(diagram, 2, rewrite(view, Map.of(sub, true), null)));
		assertThat(auditDetails(diagram).path("changed").asInt()).isEqualTo(1);
		// the stored flag sent unchanged
		ok(save(diagram, 3, rewrite(collapsed, Map.of(), null)));
		assertThat(auditDetails(diagram).path("changed").asInt()).isZero();
		// a shape without the field is saved as false
		JsonNode omitted = ok(save(diagram, 4, rewrite(collapsed, Map.of(), sub)));
		assertThat(shape(omitted, "Check credit").path("collapsed").asBoolean()).isFalse();
		assertThat(auditDetails(diagram).path("changed").asInt()).isEqualTo(1);
	}

	/** metatype, whether the entity is new */
	@ParameterizedTest
	@CsvSource({ "User Task, true", "User Task, false", "Pool, true", "Lane, false" })
	@Order(17)
	void collapsedOnAShapeThatIsNoSubProcessIsRejectedAndChangesNothing(String type, boolean created) {
		String diagram = diagram();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		String key = id();
		String shapeId = id();
		ObjectNode shape;
		if (created) {
			body.putArray("newEntities").add(newEntity(key, metatype(type), type + " collapsed " + created));
			shape = newShape(shapeId, key, 0, 0, 90, 60);
		}
		else {
			ObjectNode setup = layout(List.of(new Item("t", type, 0, 0)));
			JsonNode first = ok(save(diagram, 1, setup));
			int entity = first.path("shapes").get(0).path("entity").path("id").asInt();
			shape = JsonMapper.shared().createObjectNode().put("id", first.path("shapes").get(0).path("id").asString())
				.put("entityId", entity).put("x", 0).put("y", 0).put("width", 90).put("height", 60);
		}
		shape.put("collapsed", true);
		body.putArray("shapes").add(shape);
		int version = created ? 1 : 2;
		int entities = this.jdbc.queryForObject("SELECT count(*) FROM entities", Integer.class);
		MvcTestResult result = save(diagram, version, body);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		JsonNode error = body(result).path("errors").get(0);
		assertThat(error.path("field").asString()).isEqualTo("shapes[0].collapsed");
		assertThat(error.path("code").asString()).isEqualTo("COLLAPSED_NOT_SUB_PROCESS");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities", Integer.class)).isEqualTo(entities);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid AND collapsed",
				Integer.class, diagram)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, diagram))
			.isEqualTo(version);
	}

	@Test
	@Order(18)
	void collapsedOnASubProcessOfAViewpointWithoutTheMappingIsRejected() {
		UUID plain = viewpointWithSubProcess(false);
		MvcTestResult result = save(diagram(plain), 1, subProcessLayout(true));
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		assertThat(body(result).path("errors").get(0).path("code").asString()).isEqualTo("COLLAPSED_NOT_SUB_PROCESS");
		// the same type is accepted where the viewpoint maps it
		UUID mapped = viewpointWithSubProcess(true);
		JsonNode view = ok(save(diagram(mapped), 1, subProcessLayout(true)));
		assertThat(shape(view, "Check credit").path("collapsed").asBoolean()).isTrue();
	}

	@Test
	@Order(19)
	void theViewReturnsFalseAfterTheViewpointLosesTheMapping() {
		UUID mapped = viewpointWithSubProcess(true);
		UUID plain = viewpointWithSubProcess(false);
		String diagram = diagram(mapped);
		ok(save(diagram, 1, subProcessLayout(true)));
		assertThat(shape(view(diagram), "Check credit").path("collapsed").asBoolean()).isTrue();
		MvcTestResult changed = call(HttpMethod.PUT, DIAGRAMS + "/" + diagram + "/viewpoint",
				JsonMapper.shared().createObjectNode().put("viewpointId", plain.toString()), this.anna, "\"2\"");
		assertThat(status(changed)).as(() -> content(changed)).isEqualTo(200);
		JsonNode view = view(diagram);
		assertThat(shape(view, "Check credit").path("collapsed").asBoolean()).isFalse();
		// the stored flag is cleared by the next save and counts as a change
		ok(save(diagram, version(diagram), rewrite(view, Map.of(), null)));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid AND collapsed",
				Integer.class, diagram)).isZero();
		assertThat(auditDetails(diagram).path("changed").asInt()).isEqualTo(1);
	}

	@Test
	@Order(20)
	void theFlagBelongsToTheDiagramNotToTheEntity() {
		String first = diagram();
		JsonNode view = ok(save(first, 1, subProcessLayout(true)));
		int entity = shape(view, "Check credit").path("entity").path("id").asInt();
		String second = diagram();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("shapes")
			.add(JsonMapper.shared().createObjectNode().put("id", id()).put("entityId", entity).put("x", 0).put("y", 0)
				.put("width", 270).put("height", 150));
		JsonNode other = ok(save(second, 1, body));
		assertThat(other.path("shapes").get(0).path("collapsed").asBoolean()).isFalse();
		assertThat(view(first).path("shapes").valueStream().anyMatch((s) -> s.path("collapsed").asBoolean())).isTrue();
	}

	// Requirement: Vertical pools (us-074-bpmn-pools-and-lanes D5)

	@Test
	@Order(21)
	void aPoolIsSavedVerticalAndHorizontalAgainAndTheChangeIsAudited() {
		String diagram = diagram();
		ObjectNode body = poolLayout("vertical");
		JsonNode view = ok(save(diagram, 1, body));
		JsonNode pool = shape(view, "Customer");
		assertThat(pool.path("orientation").asString()).isEqualTo("vertical");
		assertThat(shape(view, "Sales").path("orientation").asString()).isEqualTo("horizontal");
		assertThat(this.jdbc.queryForObject("SELECT orientation FROM diagram_shapes WHERE id = ?::uuid", String.class,
				pool.path("id").asString())).isEqualTo("vertical");
		// unchanged: not a change
		JsonNode same = ok(save(diagram, 2, rewrite(view, Map.of(), null)));
		assertThat(auditDetails(diagram).path("changed").asInt()).isZero();
		// switched back
		ObjectNode back = rewrite(same, Map.of(), null);
		((ObjectNode) back.path("shapes").get(0)).put("orientation", "horizontal");
		JsonNode horizontal = ok(save(diagram, 3, back));
		assertThat(shape(horizontal, "Customer").path("orientation").asString()).isEqualTo("horizontal");
		assertThat(auditDetails(diagram).path("changed").asInt()).isEqualTo(1);
		// a shape without the field is horizontal
		ObjectNode vertical = rewrite(horizontal, Map.of(), null);
		((ObjectNode) vertical.path("shapes").get(0)).put("orientation", "vertical");
		JsonNode again = ok(save(diagram, 4, vertical));
		ObjectNode without = rewrite(again, Map.of(), null);
		((ObjectNode) without.path("shapes").get(0)).remove("orientation");
		JsonNode omitted = ok(save(diagram, 5, without));
		assertThat(shape(omitted, "Customer").path("orientation").asString()).isEqualTo("horizontal");
	}

	/** metatype, whether the entity is new */
	@ParameterizedTest
	@CsvSource({ "Lane, true", "Lane, false", "User Task, true", "Sub-Process, false" })
	@Order(22)
	void verticalOnAShapeThatIsNoPoolIsRejectedAndChangesNothing(String type, boolean created) {
		String diagram = diagram();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ObjectNode shape;
		int version = 1;
		if (created) {
			String key = id();
			body.putArray("newEntities").add(newEntity(key, metatype(type), type + " vertical"));
			shape = newShape(id(), key, 0, 0, 90, 60);
		}
		else {
			JsonNode first = ok(save(diagram, 1, layout(List.of(new Item("t", type, 0, 0)))));
			version = 2;
			shape = JsonMapper.shared().createObjectNode().put("id", first.path("shapes").get(0).path("id").asString())
				.put("entityId", first.path("shapes").get(0).path("entity").path("id").asInt()).put("x", 0).put("y", 0)
				.put("width", 90).put("height", 60);
		}
		shape.put("orientation", "vertical");
		body.putArray("shapes").add(shape);
		MvcTestResult result = save(diagram, version, body);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		JsonNode error = body(result).path("errors").get(0);
		assertThat(error.path("field").asString()).isEqualTo("shapes[0].orientation");
		assertThat(error.path("code").asString()).isEqualTo("ORIENTATION_NOT_POOL");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid AND orientation = 'vertical'",
				Integer.class, diagram)).isZero();
		assertThat(version(diagram)).isEqualTo(version);
	}

	@Test
	@Order(23)
	void anUnknownOrientationIsRejectedWith400() {
		ObjectNode body = poolLayout("diagonal");
		MvcTestResult result = save(diagram(), 1, body);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(400);
	}

	@Test
	@Order(24)
	void theViewReturnsHorizontalAfterTheViewpointLosesThePoolMapping() {
		UUID mapped = viewpointWithPool(true);
		UUID plain = viewpointWithPool(false);
		String diagram = diagram(mapped);
		ok(save(diagram, 1, poolLayout("vertical")));
		assertThat(shape(view(diagram), "Customer").path("orientation").asString()).isEqualTo("vertical");
		MvcTestResult changed = call(HttpMethod.PUT, DIAGRAMS + "/" + diagram + "/viewpoint",
				JsonMapper.shared().createObjectNode().put("viewpointId", plain.toString()), this.anna, "\"2\"");
		assertThat(status(changed)).as(() -> content(changed)).isEqualTo(200);
		assertThat(shape(view(diagram), "Customer").path("orientation").asString()).isEqualTo("horizontal");
		// and it is rejected where the viewpoint has no pool mapping
		MvcTestResult rejected = save(diagram(plain), 1, poolLayout("vertical"));
		assertThat(status(rejected)).as(() -> content(rejected)).isEqualTo(422);
	}

	@Test
	@Order(25)
	void theOrientationBelongsToTheDiagram() {
		String first = diagram();
		JsonNode view = ok(save(first, 1, poolLayout("vertical")));
		int entity = shape(view, "Customer").path("entity").path("id").asInt();
		String second = diagram();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("shapes").add(JsonMapper.shared().createObjectNode().put("id", id()).put("entityId", entity)
			.put("x", 0).put("y", 0).put("width", 600).put("height", 200));
		assertThat(shape(ok(save(second, 1, body)), "Customer").path("orientation").asString()).isEqualTo("horizontal");
		assertThat(shape(view(first), "Customer").path("orientation").asString()).isEqualTo("vertical");
	}

	// helpers

	/** The pool "Customer" (600 x 200) with the lane "Sales" and the task "Check" in it, the pool with the given orientation. */
	private ObjectNode poolLayout(String orientation) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode entities = body.putArray("newEntities");
		ArrayNode shapes = body.putArray("shapes");
		String poolKey = id();
		String poolShape = id();
		entities.add(newEntity(poolKey, metatype("Pool"), "Customer"));
		shapes.add(newShape(poolShape, poolKey, 0, 0, 600, 200).put("orientation", orientation));
		String laneKey = id();
		String laneShape = id();
		entities.add(newEntity(laneKey, metatype("Lane"), "Sales"));
		shapes.add(newShape(laneShape, laneKey, 30, 0, 570, 100).put("parentShapeId", poolShape));
		return body;
	}

	private UUID viewpointWithPool(boolean mapped) {
		UUID viewpoint = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, 'bpmn2',
				(SELECT min(id) FROM persons)) RETURNING id""", UUID.class, "Pools " + mapped + " " + id());
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?::uuid), (?, ?::uuid)",
				viewpoint, metatype("Pool"), viewpoint, metatype("Lane"));
		if (mapped) {
			this.jdbc.update("INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element) VALUES (?, ?::uuid, 'bpmn:Pool')",
					viewpoint, metatype("Pool"));
		}
		return viewpoint;
	}

	/** A sub-process "Check credit" (270 x 150) with the nested tasks "Check score" and "Check income". */
	private ObjectNode subProcessLayout(boolean collapsed) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode entities = body.putArray("newEntities");
		ArrayNode shapes = body.putArray("shapes");
		String subKey = id();
		String subShape = id();
		entities.add(newEntity(subKey, metatype("Sub-Process"), "Check credit"));
		shapes.add(newShape(subShape, subKey, 0, 0, 270, 150).put("collapsed", collapsed));
		for (String[] task : new String[][] { { "Check score", "10" }, { "Check income", "150" } }) {
			String key = id();
			entities.add(newEntity(key, metatype("User Task"), task[0]));
			shapes.add(newShape(id(), key, Double.parseDouble(task[1]), 10, 90, 60).put("parentShapeId", subShape));
		}
		return body;
	}

	/**
	 * The shapes of a view as a save request: the flags in {@code overrides} replace the stored ones, and the
	 * shape {@code withoutField} is sent without the field.
	 */
	private static ObjectNode rewrite(JsonNode view, Map<String, Boolean> overrides, String withoutField) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		for (JsonNode shape : view.path("shapes")) {
			String id = shape.path("id").asString();
			ObjectNode write = JsonMapper.shared()
				.createObjectNode()
				.put("id", id)
				.put("entityId", shape.path("entity").path("id").asInt())
				.put("x", shape.path("x").decimalValue())
				.put("y", shape.path("y").decimalValue())
				.put("width", shape.path("width").decimalValue())
				.put("height", shape.path("height").decimalValue());
			if (!shape.path("parentShapeId").isNull()) {
				write.put("parentShapeId", shape.path("parentShapeId").asString());
			}
			if ("vertical".equals(shape.path("orientation").asString())) {
				write.put("orientation", "vertical");
			}
			if (!id.equals(withoutField)) {
				write.put("collapsed", overrides.getOrDefault(id, shape.path("collapsed").asBoolean()));
			}
			shapes.add(write);
		}
		return body;
	}

	private static JsonNode shape(JsonNode view, String entityName) {
		return view.path("shapes")
			.valueStream()
			.filter((s) -> entityName.equals(s.path("entity").path("name").asString()))
			.findFirst()
			.orElseThrow();
	}

	/** A viewpoint of notation bpmn2 that allows the sub-process and the task, with or without the mapping. */
	private UUID viewpointWithSubProcess(boolean mapped) {
		UUID viewpoint = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, 'bpmn2',
				(SELECT min(id) FROM persons)) RETURNING id""", UUID.class, "Sub-processes " + mapped + " " + id());
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?::uuid), (?, ?::uuid)",
				viewpoint, metatype("Sub-Process"), viewpoint, metatype("User Task"));
		if (mapped) {
			this.jdbc.update("INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element) VALUES (?, ?::uuid, 'bpmn:SubProcess')",
					viewpoint, metatype("Sub-Process"));
		}
		return viewpoint;
	}

	private JsonNode view(String diagram) {
		return ok(call(HttpMethod.GET, VIEWS + "/" + diagram, null, this.franz, null));
	}

	private int version(String diagram) {
		return this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, diagram);
	}

	private JsonNode auditDetails(String diagram) {
		return JsonMapper.shared()
			.readTree(this.jdbc.queryForObject("SELECT details::text FROM audit.events WHERE event_type = 'diagram.layout-saved' "
					+ "AND target_id = ? ORDER BY id DESC LIMIT 1", String.class, diagram));
	}

	private record Item(String key, String type, double x, double y) {
	}

	/** New entities and shapes for the items; the shape of an item is referred to by its key. */
	private ObjectNode layout(List<Item> items) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		ArrayNode entities = body.putArray("newEntities");
		for (Item item : items) {
			entities.add(newEntity(keyOf(body, item.key()), UUID.fromString(metatype(item.type())), item.type() + " " + item.key()));
			shapes.add(newShape(shapeOf(body, item.key()), keyOf(body, item.key()), item.x(), item.y(), 90, 60));
		}
		return body;
	}

	private final Map<String, String> generated = new java.util.HashMap<>();

	private String keyOf(ObjectNode body, String item) {
		return this.generated.computeIfAbsent(System.identityHashCode(body) + "k" + item, (k) -> id());
	}

	private String shapeOf(ObjectNode body, String item) {
		return this.generated.computeIfAbsent(System.identityHashCode(body) + "s" + item, (k) -> id());
	}

	private void addFlow(ObjectNode body, String flow, String source, String target) {
		String key = id();
		body.putArray("newConnections").add(newConnection(key, metatype(flow)));
		body.putArray("edges").add(newEdge(id(), key, shapeOf(body, source), shapeOf(body, target)));
	}

	private JsonNode viewpoint() {
		String id = this.jdbc.queryForObject("SELECT id::text FROM viewpoints WHERE name = ?", String.class, VIEWPOINT);
		return ok(call(HttpMethod.GET, "/api/v1/admin/viewpoints/" + id, null, this.anna, null));
	}

	private static JsonNode byMetatype(JsonNode list, String metatypeId) {
		return list.valueStream().filter((m) -> metatypeId.equals(m.path("metatypeId").asString())).findFirst()
			.orElse(null);
	}

	private static JsonNode shapeOf(JsonNode view, String shapeId) {
		return view.path("shapes").valueStream().filter((s) -> shapeId.equals(s.path("id").asString())).findFirst()
			.orElseThrow();
	}

	private int entityId(String name) {
		return this.jdbc.queryForObject("SELECT id FROM entities WHERE name = ? AND deleted_at IS NULL", Integer.class, name);
	}

	private String metatype(String name) {
		return this.jdbc.queryForObject("SELECT id::text FROM metatypes WHERE name = ? AND source_package_id = ?",
				String.class, name, BPMN);
	}

	private String propertyDefinition(String name) {
		return this.jdbc.queryForObject("SELECT id::text FROM property_definitions WHERE name = ?", String.class, name);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class, BPMN);
	}

	private JsonNode shipped(JsonNode packages) {
		return packages.valueStream()
			.filter((candidate) -> BPMN.equals(candidate.path("packageId").asString()))
			.findFirst()
			.orElseThrow();
	}

	private String diagram() {
		return diagram(UUID.fromString(this.jdbc.queryForObject("SELECT id::text FROM viewpoints WHERE name = ?",
				String.class, VIEWPOINT)));
	}

	private String diagram(UUID viewpoint) {
		MvcTestResult result = call(HttpMethod.POST, DIAGRAMS,
				JsonMapper.shared()
					.createObjectNode()
					.put("name", "Order handling " + id())
					.put("viewpointId", viewpoint.toString()),
				this.anna, null);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(201);
		return body(result).path("id").asString();
	}

	private MvcTestResult save(String diagram, int version, ObjectNode body) {
		return call(HttpMethod.PUT, VIEWS + "/" + diagram, body, this.anna, "\"" + version + "\"");
	}

	private static ObjectNode newEntity(String key, UUID metatype, String name) {
		return newEntity(key, metatype.toString(), name);
	}

	private static ObjectNode newEntity(String key, String metatype, String name) {
		return JsonMapper.shared().createObjectNode().put("key", key).put("metatypeId", metatype).put("name", name);
	}

	private static ObjectNode newShape(String id, String key, double x, double y, double width, double height) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("id", id)
			.put("newEntityKey", key)
			.put("x", BigDecimal.valueOf(x))
			.put("y", BigDecimal.valueOf(y))
			.put("width", BigDecimal.valueOf(width))
			.put("height", BigDecimal.valueOf(height));
	}

	private static ObjectNode newConnection(String key, String metatype) {
		return JsonMapper.shared().createObjectNode().put("key", key).put("metatypeId", metatype);
	}

	private static ObjectNode newEdge(String id, String key, String source, String target) {
		ObjectNode edge = JsonMapper.shared()
			.createObjectNode()
			.put("id", id)
			.put("newConnectionKey", key)
			.put("sourceShapeId", source)
			.put("targetShapeId", target);
		edge.putArray("points");
		return edge;
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		return body(result);
	}

	private static int status(MvcTestResult result) {
		return result.getResponse().getStatus();
	}

	private MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session, String ifMatch) {
		var request = this.mvc.method(method)
			.uri(java.net.URI.create(path.replace(" ", "%20")))
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
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
		return JsonMapper.shared().readTree(content(result));
	}

	private static String id() {
		return UUID.randomUUID().toString();
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-bpmn-package").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

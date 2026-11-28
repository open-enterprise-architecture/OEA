package io.oea.steps.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/diagram/bpmn-assignments.feature (us-075-lane-assignments). The imports change the metamodel
 * of the instance, so the feature has an instance of its own.
 */
public class BpmnAssignmentSteps {

	private static final String BPMN = "oea-starter-bpmn";

	private static final String ORGANIZATION = "oea-starter-organization";

	private static final String BATCH = "/api/v1/entities/batch";

	private static RepositoryInstance shared;

	private static int counter;

	private String diagram;

	private HttpResponse<String> response;

	private HttpResponse<String> catalog;

	private String catalogId;

	private final Map<String, Integer> entities = new java.util.HashMap<>();

	private final List<Integer> removed = new ArrayList<>();

	@Given("an instance of its own with the BPMN and the organization package")
	public void instance() throws Exception {
		synchronized (BpmnAssignmentSteps.class) {
			if (shared == null) {
				shared = RepositoryInstance.start();
				for (String id : List.of(BPMN, ORGANIZATION)) {
					HttpResponse<String> imported = shared.post("/api/v1/metamodel/starter-packages/" + id + "/import", "",
							shared.metamodelAdmin(), Map.of());
					assertThat(imported.statusCode()).as(imported.body()).isEqualTo(200);
				}
			}
		}
		this.entities.clear();
		this.removed.clear();
	}

	@AfterAll
	public static void stop() {
		if (shared != null) {
			shared.close();
			shared = null;
		}
	}

	// Given

	@Given("a process diagram {string} with the lane {string}")
	public void withLane(String name, String lane) throws Exception {
		withShapes(name, List.of(new Item(lane, "Lane", 570, 100)));
	}

	@Given("a process diagram {string} with the lanes {string} and {string}")
	public void withLanes(String name, String first, String second) throws Exception {
		withShapes(name, List.of(new Item(first, "Lane", 570, 100), new Item(second, "Lane", 570, 100)));
	}

	@Given("a process diagram {string} with the pool {string}")
	public void withPool(String name, String pool) throws Exception {
		withShapes(name, List.of(new Item(pool, "Pool", 600, 200)));
	}

	@Given("a process diagram {string} with the user task {string}")
	public void withUserTask(String name, String task) throws Exception {
		withShapes(name, List.of(new Item(task, "User Task", 90, 60)));
	}

	@Given("a process diagram {string} with the user tasks {string} and {string}")
	public void withUserTasks(String name, String first, String second) throws Exception {
		withShapes(name, List.of(new Item(first, "User Task", 90, 60), new Item(second, "User Task", 90, 60)));
	}

	@Given("a process diagram {string} with the task {string}")
	public void withTask(String name, String task) throws Exception {
		withShapes(name, List.of(new Item(task, "Task", 90, 60)));
	}

	@Given("the organizational unit {string}")
	public void unit(String name) throws Exception {
		create(name, "Organizational Unit");
	}

	@Given("the organizational units {string} and {string}")
	public void units(String first, String second) throws Exception {
		unit(first);
		unit(second);
	}

	@Given("the roles {string} and {string}")
	public void roles(String first, String second) throws Exception {
		create(first, "Role");
		create(second, "Role");
	}

	@Given("the person {string}")
	public void person(String name) throws Exception {
		create(name, "Person");
	}

	@Given("the enterprise architect assigned {string} to {string}")
	public void assigned(String target, String source) throws Exception {
		String type = connectionOf(typeOf(target));
		ObjectNode body = JsonMapper.shared().createObjectNode().put("atomic", true);
		body.putArray("connections")
			.addObject()
			.put("connectionMetatypeId", metatype(type))
			.put("sourceId", this.entities.get(source))
			.put("targetId", this.entities.get(target));
		HttpResponse<String> done = shared.post(BATCH, body.toString(), shared.architect(), Map.of());
		assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
	}

	@Given("a catalog {string} of user tasks with the join {string} to the persons")
	public void taskCatalog(String name, String connection) throws Exception {
		catalog(name, "User Task", connection, "Person");
	}

	@Given("a catalog {string} of lanes with the join {string} to the organizational units")
	public void laneCatalog(String name, String connection) throws Exception {
		catalog(name, "Lane", connection, "Organizational Unit");
	}

	@Given("a diagram {string} of the viewpoint that maps {string} to a lane and {string} to a pool")
	public void roleLaneDiagram(String name, String role, String unit) throws Exception {
		String viewpoint = shared.oidc()
			.single("INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), 'Role lanes " + (++counter)
					+ "', 'bpmn2', (SELECT min(id) FROM persons)) RETURNING id::text");
		for (String type : List.of(role, unit, "User Task", "Task")) {
			shared.oidc().update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES ('" + viewpoint + "', '"
					+ metatype(type) + "')");
		}
		shared.oidc().update("INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element) VALUES ('" + viewpoint
				+ "', '" + metatype(role) + "', 'bpmn:Lane'), ('" + viewpoint + "', '" + metatype(unit) + "', 'bpmn:Pool'), ('"
				+ viewpoint + "', '" + metatype("User Task") + "', 'bpmn:UserTask')");
		this.diagram = createDiagram(name, viewpoint);
	}

	@Given("the pool {string} holds the role lane {string}")
	public void poolHolds(String pool, String lane) throws Exception {
		this.entities.put(pool, create(pool, "Organizational Unit"));
		this.entities.put(lane, create(lane, "Role"));
		ObjectNode body = layout();
		String poolShape = shape(body, this.entities.get(pool), 0, 0, 600, 300, null);
		shape(body, this.entities.get(lane), 30, 0, 570, 150, poolShape);
		this.response = save(body);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	// When

	@When("the enterprise architect assigns the organizational unit {string} to {string} and saves")
	public void assignUnit(String target, String source) throws Exception {
		assign(source, target, "Belongs To");
	}

	@When("the enterprise architect assigns the role {string} to {string} and saves")
	public void assignRole(String target, String source) throws Exception {
		assign(source, target, "Performs Role");
	}

	@When("the enterprise architect assigns the person {string} to {string} and saves")
	public void assignPerson(String target, String source) throws Exception {
		assign(source, target, "Responsible");
	}

	@When("the enterprise architect removes the assignment of {string} and saves")
	public void removeAssignment(String source) throws Exception {
		JsonNode view = view();
		JsonNode assignment = shapeOf(view, source).path("assignments").path("orgUnits").get(0);
		ObjectNode body = rewrite(view);
		body.putArray("removedConnections")
			.addObject()
			.put("connectionId", assignment.path("connectionId").asInt())
			.put("expectedVersion", assignment.path("connectionVersion").asInt());
		this.removed.add(assignment.path("connectionId").asInt());
		this.response = save(body);
	}

	@When("the enterprise architect assigns the new organizational unit {string} to {string} and saves")
	public void assignNew(String name, String source) throws Exception {
		JsonNode view = view();
		ObjectNode body = rewrite(view);
		String key = UUID.randomUUID().toString();
		body.putArray("newEntities").addObject().put("key", key).put("metatypeId", metatype("Organizational Unit")).put("name", name);
		body.putArray("newConnections")
			.addObject()
			.put("key", UUID.randomUUID().toString())
			.put("metatypeId", metatype("Belongs To"))
			.put("sourceShapeId", shapeOf(view, source).path("id").asString())
			.put("targetNewEntityKey", key);
		this.response = save(body);
	}

	@When("the join column of {string} is filtered by {string}")
	public void filter(String name, String value) throws Exception {
		String join = shared.oidc()
			.single("SELECT j ->> 'id' FROM catalogs, jsonb_array_elements(config -> 'joinDefinitions') j WHERE name = '" + name
					+ "'");
		this.catalog = shared.oidc()
			.get("/api/v1/catalogs/" + this.catalogId + "/query?filter="
					+ URLEncoder.encode(join + ".name:eq:" + value, StandardCharsets.UTF_8), shared.viewer().token());
	}

	@When("the enterprise architect drops the new task {string} into {string} and saves with the assignment")
	public void dropTask(String task, String lane) throws Exception {
		JsonNode view = view();
		ObjectNode body = rewrite(view);
		String key = UUID.randomUUID().toString();
		String shapeId = UUID.randomUUID().toString();
		body.putArray("newEntities").addObject().put("key", key).put("metatypeId", metatype("User Task")).put("name", task);
		ObjectNode shape = ((ArrayNode) body.path("shapes")).addObject()
			.put("id", shapeId)
			.put("newEntityKey", key)
			.put("x", 70)
			.put("y", 20)
			.put("width", 90)
			.put("height", 60);
		shape.put("parentShapeId", shapeOf(view, lane).path("id").asString());
		body.putArray("newConnections")
			.addObject()
			.put("key", UUID.randomUUID().toString())
			.put("metatypeId", metatype("Performs Role"))
			.put("sourceShapeId", shapeId)
			.put("targetEntityId", shapeOf(view, lane).path("entity").path("id").asInt());
		this.response = save(body);
	}

	// Then

	@Then("the save is accepted")
	public void accepted() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Then("{string} belongs to {string} and the creation is audited")
	public void belongs(String source, String target) throws Exception {
		JsonNode orgUnit = shapeOf(view(), source).path("assignments").path("orgUnits").get(0);
		assertThat(orgUnit.path("name").asString()).isEqualTo(target);
		assertThat(shared.oidc().single("SELECT count(*)::text FROM audit.events WHERE event_type = 'entity.created' AND target_id = '"
				+ orgUnit.path("connectionId").asInt() + "'")).isEqualTo("1");
	}

	@Then("the view shows no edge for it")
	public void noEdge() throws Exception {
		assertThat(view().path("edges")).isEmpty();
	}

	@Then("the lane {string} shows the roles {string} and {string}")
	public void laneRoles(String lane, String first, String second) throws Exception {
		assertThat(names(shapeOf(view(), lane).path("assignments").path("roles"))).containsExactly(first, second);
	}

	@Then("the pool {string} shows the organizational unit {string}")
	public void poolUnit(String pool, String unit) throws Exception {
		assertThat(names(shapeOf(view(), pool).path("assignments").path("orgUnits"))).containsExactly(unit);
	}

	@Then("the task {string} shows the person {string}")
	public void taskPerson(String task, String person) throws Exception {
		assertThat(names(shapeOf(view(), task).path("assignments").path("persons"))).containsExactly(person);
	}

	@Then("the task {string} shows the role {string}")
	public void taskRole(String task, String role) throws Exception {
		assertThat(names(shapeOf(view(), task).path("assignments").path("roles"))).containsExactly(role);
	}

	@Then("the lane {string} shows no organizational unit")
	public void noUnit(String lane) throws Exception {
		assertThat(shapeOf(view(), lane).path("assignments").path("orgUnits")).isEmpty();
	}

	@Then("the removed connection is restorable and audited as deleted")
	public void restorable() throws Exception {
		int connection = this.removed.get(0);
		assertThat(shared.oidc().single("SELECT count(*)::text FROM audit.events WHERE event_type = 'entity.deleted' AND target_id = '"
				+ connection + "'")).isEqualTo("1");
		String version = shared.oidc().single("SELECT version::text FROM entities WHERE id = " + connection);
		HttpResponse<String> restored = shared.send("POST", "/api/v1/entities/" + connection + "/undelete", null, shared.architect(),
				Map.of("If-Match", "\"" + version + "\""));
		assertThat(restored.statusCode()).as(restored.body()).isEqualTo(200);
	}

	@Then("the organizational unit {string} exists and the lane {string} belongs to it")
	public void created(String unit, String lane) throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM entities WHERE name = '" + unit + "' AND deleted_at IS NULL"))
			.isEqualTo("1");
		assertThat(names(shapeOf(view(), lane).path("assignments").path("orgUnits"))).containsExactly(unit);
	}

	@Then("the diagram has no shape for {string}")
	public void noShape(String name) throws Exception {
		assertThat(view().path("shapes").valueStream().map((shape) -> shape.path("entity").path("name").asString()))
			.doesNotContain(name);
	}

	@Then("{string} shows {string} in the join column")
	public void joinValue(String task, String person) throws Exception {
		JsonNode row = row(task);
		assertThat(joinNames(row)).containsExactly(person);
	}

	@Then("{string} shows an empty cell in the join column")
	public void emptyCell(String task) throws Exception {
		assertThat(joinNames(row(task))).isEmpty();
	}

	@Then("the filtered catalog shows only the lane {string}")
	public void filteredOnly(String lane) {
		assertThat(this.catalog.statusCode()).as(this.catalog.body()).isEqualTo(200);
		List<String> names = new ArrayList<>();
		JsonMapper.shared().readTree(this.catalog.body()).path("rows")
			.forEach((row) -> names.add(row.path("values").path("name").asString()));
		assertThat(names).containsExactly(lane);
	}

	@Then("the task {string} performs the role {string} and its parent is unchanged")
	public void performs(String task, String role) throws Exception {
		accepted();
		JsonNode shape = shapeOf(view(), task);
		assertThat(names(shape.path("assignments").path("roles"))).containsExactly(role);
		assertThat(shared.oidc().single("SELECT coalesce(parent_entity_id::text, 'none') FROM entities WHERE id = "
				+ shape.path("entity").path("id").asInt())).isEqualTo("none");
	}

	// helpers

	private record Item(String name, String type, double width, double height) {
	}

	private void withShapes(String name, List<Item> items) throws Exception {
		this.diagram = createDiagram(name, shared.oidc().single("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'"));
		ObjectNode body = layout();
		double y = 0;
		for (Item item : items) {
			int id = create(item.name(), item.type());
			this.entities.put(item.name(), id);
			shape(body, id, 0, y, item.width(), item.height(), null);
			y += item.height() + 20;
		}
		this.response = save(body);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	private int create(String name, String type) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("atomic", true);
		body.putArray("entities").addObject().put("name", name).put("metatypeId", metatype(type));
		HttpResponse<String> done = shared.post(BATCH, body.toString(), shared.architect(), Map.of());
		assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
		int id = JsonMapper.shared().readTree(done.body()).path("items").get(0).path("id").asInt();
		this.entities.put(name, id);
		return id;
	}

	private void assign(String source, String target, String type) throws Exception {
		JsonNode view = view();
		ObjectNode body = rewrite(view);
		body.putArray("newConnections")
			.addObject()
			.put("key", UUID.randomUUID().toString())
			.put("metatypeId", metatype(type))
			.put("sourceShapeId", shapeOf(view, source).path("id").asString())
			.put("targetEntityId", this.entities.get(target));
		this.response = save(body);
	}

	private void catalog(String name, String primary, String connection, String target) throws Exception {
		ObjectNode create = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("primaryMetatypeId", metatype(primary))
			.put("scope", "instance");
		HttpResponse<String> created = shared.send("POST", "/api/v1/catalogs", create.toString(), shared.architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.catalogId = JsonMapper.shared().readTree(created.body()).path("id").asString();
		ObjectNode join = JsonMapper.shared()
			.createObjectNode()
			.put("name", connection)
			.put("connectionMetatypeId", metatype(connection))
			.put("joinDirection", "outbound")
			.put("targetMetatypeId", metatype(target));
		join.putArray("targetColumns").addObject().put("field", "name");
		HttpResponse<String> joined = shared.send("POST", "/api/v1/catalogs/" + this.catalogId + "/joins", join.toString(),
				shared.architect());
		assertThat(joined.statusCode()).as(joined.body()).isEqualTo(201);
		this.catalog = shared.oidc().get("/api/v1/catalogs/" + this.catalogId + "/query", shared.viewer().token());
	}

	private JsonNode row(String name) {
		assertThat(this.catalog.statusCode()).as(this.catalog.body()).isEqualTo(200);
		JsonNode rows = JsonMapper.shared().readTree(this.catalog.body()).path("rows");
		return rows.valueStream().filter((row) -> name.equals(row.path("values").path("name").asString())).findFirst().orElseThrow();
	}

	private static List<String> joinNames(JsonNode row) {
		List<String> names = new ArrayList<>();
		row.path("joinResults").forEach((results) -> results
			.forEach((result) -> names.add(result.path("values").path("name").asString())));
		return names;
	}

	private String typeOf(String entity) throws Exception {
		return shared.oidc().single("SELECT m.name FROM entities e JOIN metatypes m ON m.id = e.metatype_id WHERE e.id = "
				+ this.entities.get(entity));
	}

	private static String connectionOf(String type) {
		return switch (type) {
			case "Organizational Unit" -> "Belongs To";
			case "Role" -> "Performs Role";
			default -> "Responsible";
		};
	}

	private static String metatype(String name) throws Exception {
		return shared.oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name + "' AND source_package_id IN ('"
				+ BPMN + "', '" + ORGANIZATION + "')");
	}

	private static ObjectNode layout() {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("shapes");
		return body;
	}

	private static String shape(ObjectNode body, int entityId, double x, double y, double width, double height, String parent) {
		String id = UUID.randomUUID().toString();
		ObjectNode shape = ((ArrayNode) body.path("shapes")).addObject()
			.put("id", id)
			.put("entityId", entityId)
			.put("x", BigDecimal.valueOf(x))
			.put("y", BigDecimal.valueOf(y))
			.put("width", BigDecimal.valueOf(width))
			.put("height", BigDecimal.valueOf(height));
		if (parent != null) {
			shape.put("parentShapeId", parent);
		}
		return id;
	}

	private JsonNode view() throws Exception {
		HttpResponse<String> got = shared.send("GET", "/api/v1/views/diagrams/" + this.diagram, null, shared.architect());
		assertThat(got.statusCode()).as(got.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(got.body());
	}

	private static ObjectNode rewrite(JsonNode view) {
		ObjectNode body = layout();
		for (JsonNode shape : view.path("shapes")) {
			ObjectNode write = ((ArrayNode) body.path("shapes")).addObject()
				.put("id", shape.path("id").asString())
				.put("entityId", shape.path("entity").path("id").asInt())
				.put("x", shape.path("x").decimalValue())
				.put("y", shape.path("y").decimalValue())
				.put("width", shape.path("width").decimalValue())
				.put("height", shape.path("height").decimalValue());
			if (!shape.path("parentShapeId").isNull()) {
				write.put("parentShapeId", shape.path("parentShapeId").asString());
			}
		}
		return body;
	}

	private HttpResponse<String> save(ObjectNode body) throws Exception {
		String version = shared.oidc().single("SELECT version::text FROM diagrams WHERE id = '" + this.diagram + "'");
		return shared.send("PUT", "/api/v1/views/diagrams/" + this.diagram, body.toString(), shared.architect(),
				Map.of("If-Match", "\"" + version + "\""));
	}

	private String createDiagram(String name, String viewpoint) throws Exception {
		HttpResponse<String> created = shared.send("POST", "/api/v1/diagrams", JsonMapper.shared()
			.createObjectNode()
			.put("name", name + " " + UUID.randomUUID())
			.put("viewpointId", viewpoint)
			.toString(), shared.architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		return JsonMapper.shared().readTree(created.body()).path("id").asString();
	}

	private static JsonNode shapeOf(JsonNode view, String entityName) {
		return view.path("shapes")
			.valueStream()
			.filter((shape) -> entityName.equals(shape.path("entity").path("name").asString()))
			.findFirst()
			.orElseThrow();
	}

	private static List<String> names(JsonNode items) {
		List<String> names = new ArrayList<>();
		items.forEach((item) -> names.add(item.path("name").asString()));
		return names;
	}

}

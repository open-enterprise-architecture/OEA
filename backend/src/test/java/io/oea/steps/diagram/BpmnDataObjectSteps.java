package io.oea.steps.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/diagram/bpmn-data-objects.feature (us-083-bpmn-data-objects). The imports change the metamodel
 * of the instance, so the feature has an instance of its own.
 */
public class BpmnDataObjectSteps {

	private static final List<String> PACKAGES = List.of("oea-starter-togaf-classic", "oea-starter-bpmn",
			"oea-starter-organization");

	private static final String BATCH = "/api/v1/entities/batch";

	private static final String TRANSFORMATION_TYPE = "1b6e9540-fcfd-5cf4-afca-62dbc7785f25";

	private static final String AFFECTED_ATTRIBUTES = "d3bb9baa-ae33-5540-a74f-3ae976dc303b";

	private static RepositoryInstance shared;

	private String diagram;

	private HttpResponse<String> response;

	private HttpResponse<String> catalog;

	private int removed;

	private final Map<String, Integer> entities = new HashMap<>();

	@Given("an instance of its own with the TOGAF, the BPMN and the organization package")
	public void instance() throws Exception {
		synchronized (BpmnDataObjectSteps.class) {
			if (shared == null) {
				shared = RepositoryInstance.start();
				for (String id : PACKAGES) {
					HttpResponse<String> imported = shared.post("/api/v1/metamodel/starter-packages/" + id + "/import", "",
							shared.metamodelAdmin(), Map.of());
					assertThat(imported.statusCode()).as(imported.body()).isEqualTo(200);
				}
			}
		}
		this.entities.clear();
	}

	@AfterAll
	public static void stop() {
		if (shared != null) {
			shared.close();
			shared = null;
		}
	}

	// Given

	@Given("a process diagram {string} with the data object {string} and the task {string}")
	public void objectAndTask(String name, String object, String task) throws Exception {
		withShapes(name, List.of(new Item(object, "BPMN Data Object", 36, 50), new Item(task, "Task", 90, 60)));
	}

	@Given("a process diagram {string} with the data objects {string} and {string} and the task {string}")
	public void objectsAndTask(String name, String first, String second, String task) throws Exception {
		withShapes(name, List.of(new Item(first, "BPMN Data Object", 36, 50), new Item(second, "BPMN Data Object", 36, 50),
				new Item(task, "Task", 90, 60)));
	}

	@Given("a process diagram {string} with the data store {string}")
	public void store(String name, String store) throws Exception {
		withShapes(name, List.of(new Item(store, "BPMN Data Store", 50, 50)));
	}

	@Given("the system software {string}")
	public void software(String name) throws Exception {
		create(name, "System Software");
	}

	@Given("the enterprise architect set {string} as the realizing element of {string}")
	public void realized(String target, String store) throws Exception {
		realize(store, target);
		accepted();
	}

	@Given("the enterprise architect saved the output association from {string} to {string} with the transformation {string} and the attributes {string} and {string}")
	public void savedOutput(String task, String object, String transformation, String first, String second) throws Exception {
		output(task, object, transformation, first, second);
		accepted();
	}

	@Given("a catalog {string} of data objects with the inbound join {string} to the tasks")
	public void catalog(String name, String connection) throws Exception {
		ObjectNode create = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("primaryMetatypeId", metatype("BPMN Data Object"))
			.put("scope", "instance");
		HttpResponse<String> created = shared.send("POST", "/api/v1/catalogs", create.toString(), shared.architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		String id = JsonMapper.shared().readTree(created.body()).path("id").asString();
		ObjectNode join = JsonMapper.shared()
			.createObjectNode()
			.put("name", connection)
			.put("connectionMetatypeId", metatype(connection))
			.put("joinDirection", "inbound")
			.put("targetMetatypeId", metatype("Task"));
		join.putArray("targetColumns").addObject().put("field", "name");
		HttpResponse<String> joined = shared.send("POST", "/api/v1/catalogs/" + id + "/joins", join.toString(), shared.architect());
		assertThat(joined.statusCode()).as(joined.body()).isEqualTo(201);
		this.catalog = shared.oidc().get("/api/v1/catalogs/" + id + "/query", shared.viewer().token());
	}

	// When

	@When("the enterprise architect saves the input association from {string} to {string}")
	public void input(String source, String target) throws Exception {
		association("Data Input Association", source, target, null, null, null);
	}

	@When("the enterprise architect saves the output association from {string} to {string} with the transformation {string} and the attributes {string} and {string}")
	public void output(String source, String target, String transformation, String first, String second) throws Exception {
		association("Data Output Association", source, target, transformation, first, second);
	}

	@When("the enterprise architect sets {string} as the realizing element of {string} and saves")
	public void setRealizer(String target, String store) throws Exception {
		realize(store, target);
	}

	private void realize(String store, String target) throws Exception {
		JsonNode view = view();
		ObjectNode body = rewrite(view);
		body.putArray("newConnections")
			.addObject()
			.put("key", UUID.randomUUID().toString())
			.put("metatypeId", metatype("Realized By"))
			.put("sourceShapeId", shapeOf(view, store).path("id").asString())
			.put("targetEntityId", this.entities.get(target));
		this.response = save(body);
	}

	@When("the enterprise architect removes the realizing element of {string} and saves")
	public void removeRealizer(String store) throws Exception {
		JsonNode view = view();
		JsonNode realizer = shapeOf(view, store).path("assignments").path("realizedBy").get(0);
		ObjectNode body = rewrite(view);
		body.putArray("removedConnections")
			.addObject()
			.put("connectionId", realizer.path("connectionId").asInt())
			.put("expectedVersion", realizer.path("connectionVersion").asInt());
		this.removed = realizer.path("connectionId").asInt();
		this.response = save(body);
	}

	// Then

	@Then("the data save is accepted")
	public void accepted() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Then("the data save is rejected with 422 {string} naming the new connection")
	public void rejected(String code) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		JsonNode body = JsonMapper.shared().readTree(this.response.body());
		assertThat(body.path("errors").valueStream().map((error) -> error.path("code").asString())).contains(code);
		assertThat(body.path("invalidFields").valueStream().map(JsonNode::asString)).contains("newConnections[0]");
	}

	@Then("the data diagram has no edge")
	public void noEdge() throws Exception {
		assertThat(view().path("edges")).isEmpty();
	}

	@Then("the output association has the transformation {string} and the attributes {string} and {string} on two lines")
	public void values(String transformation, String first, String second) throws Exception {
		JsonNode edge = null;
		for (JsonNode candidate : view().path("edges")) {
			String type = shared.oidc()
				.single("SELECT m.name FROM entities e JOIN metatypes m ON m.id = e.metatype_id WHERE e.id = "
						+ candidate.path("connection").path("id").asInt());
			if ("Data Output Association".equals(type)) {
				edge = candidate;
			}
		}
		assertThat(edge).isNotNull();
		int id = edge.path("connection").path("id").asInt();
		assertThat(shared.oidc().single("SELECT value_enum FROM entity_property_values WHERE entity_id = " + id
				+ " AND property_def_id = '" + TRANSFORMATION_TYPE + "'")).isEqualTo(transformation);
		assertThat(shared.oidc().single("SELECT replace(value_text, chr(10), '|') FROM entity_property_values WHERE entity_id = "
				+ id + " AND property_def_id = '" + AFFECTED_ATTRIBUTES + "'")).isEqualTo(first + "|" + second);
	}

	@Then("the data diagram shows an edge from {string} to {string} and one from {string} to {string}")
	public void edges(String firstSource, String firstTarget, String secondSource, String secondTarget) throws Exception {
		JsonNode view = view();
		List<String> edges = new ArrayList<>();
		for (JsonNode edge : view.path("edges")) {
			edges.add(nameOfShape(view, edge.path("sourceShapeId").asString()) + ">"
					+ nameOfShape(view, edge.path("targetShapeId").asString()));
		}
		assertThat(edges).contains(firstSource + ">" + firstTarget, secondSource + ">" + secondTarget);
	}

	@Then("{string} shows the badge {string} and the creation is audited")
	public void badge(String store, String target) throws Exception {
		JsonNode realized = shapeOf(view(), store).path("assignments").path("realizedBy");
		assertThat(names(realized)).containsExactly(target);
		assertThat(shared.oidc().single("SELECT count(*)::text FROM audit.events WHERE event_type = 'entity.created' AND target_id = '"
				+ realized.get(0).path("connectionId").asInt() + "'")).isEqualTo("1");
	}

	@Then("{string} shows no badge")
	public void noBadge(String store) throws Exception {
		assertThat(shapeOf(view(), store).path("assignments").path("realizedBy")).isEmpty();
	}

	@Then("the data diagram has no shape for {string}")
	public void noShape(String name) throws Exception {
		assertThat(view().path("shapes").valueStream().map((shape) -> shape.path("entity").path("name").asString()))
			.doesNotContain(name);
	}

	@Then("the removed realizing connection is restorable and audited as deleted")
	public void restorable() throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM audit.events WHERE event_type = 'entity.deleted' AND target_id = '"
				+ this.removed + "'")).isEqualTo("1");
		String version = shared.oidc().single("SELECT version::text FROM entities WHERE id = " + this.removed);
		HttpResponse<String> restored = shared.send("POST", "/api/v1/entities/" + this.removed + "/undelete", null,
				shared.architect(), Map.of("If-Match", "\"" + version + "\""));
		assertThat(restored.statusCode()).as(restored.body()).isEqualTo(200);
	}

	@Then("the data object {string} shows the task {string} in the join column")
	public void joinValue(String object, String task) {
		assertThat(joinNames(row(object))).containsExactly(task);
	}

	@Then("the data object {string} shows an empty cell in the join column")
	public void emptyCell(String object) {
		assertThat(joinNames(row(object))).isEmpty();
	}

	// helpers

	private record Item(String name, String type, double width, double height) {
	}

	private void association(String type, String source, String target, String transformation, String first, String second)
			throws Exception {
		JsonNode view = view();
		ObjectNode body = rewrite(view);
		String key = UUID.randomUUID().toString();
		ObjectNode connection = ((ArrayNode) body.withArray("newConnections")).addObject()
			.put("key", key)
			.put("metatypeId", metatype(type));
		if (transformation != null) {
			ArrayNode properties = connection.putArray("properties");
			properties.addObject().put("propertyDefId", TRANSFORMATION_TYPE).put("value", transformation);
			properties.addObject().put("propertyDefId", AFFECTED_ATTRIBUTES).put("value", first + "\n" + second);
		}
		// the edges of the saved layout stay
		for (JsonNode edge : view.path("edges")) {
			body.withArray("edges")
				.addObject()
				.put("id", edge.path("id").asString())
				.put("connectionId", edge.path("connection").path("id").asInt())
				.put("sourceShapeId", edge.path("sourceShapeId").asString())
				.put("targetShapeId", edge.path("targetShapeId").asString())
				.putArray("points");
		}
		body.withArray("edges")
			.addObject()
			.put("id", UUID.randomUUID().toString())
			.put("newConnectionKey", key)
			.put("sourceShapeId", shapeOf(view, source).path("id").asString())
			.put("targetShapeId", shapeOf(view, target).path("id").asString())
			.putArray("points");
		this.response = save(body);
	}

	private void withShapes(String name, List<Item> items) throws Exception {
		this.diagram = createDiagram(name, shared.oidc().single("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'"));
		ObjectNode body = layout();
		double y = 0;
		for (Item item : items) {
			int id = create(item.name(), item.type());
			shape(body, id, 0, y, item.width(), item.height());
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

	private static String metatype(String name) throws Exception {
		return shared.oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name + "' AND source_package_id IN ('"
				+ String.join("', '", PACKAGES) + "')");
	}

	private static ObjectNode layout() {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("shapes");
		return body;
	}

	private static void shape(ObjectNode body, int entityId, double x, double y, double width, double height) {
		((ArrayNode) body.path("shapes")).addObject()
			.put("id", UUID.randomUUID().toString())
			.put("entityId", entityId)
			.put("x", BigDecimal.valueOf(x))
			.put("y", BigDecimal.valueOf(y))
			.put("width", BigDecimal.valueOf(width))
			.put("height", BigDecimal.valueOf(height));
	}

	private JsonNode view() throws Exception {
		HttpResponse<String> got = shared.send("GET", "/api/v1/views/diagrams/" + this.diagram, null, shared.architect());
		assertThat(got.statusCode()).as(got.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(got.body());
	}

	private static ObjectNode rewrite(JsonNode view) {
		ObjectNode body = layout();
		for (JsonNode shape : view.path("shapes")) {
			((ArrayNode) body.path("shapes")).addObject()
				.put("id", shape.path("id").asString())
				.put("entityId", shape.path("entity").path("id").asInt())
				.put("x", shape.path("x").decimalValue())
				.put("y", shape.path("y").decimalValue())
				.put("width", shape.path("width").decimalValue())
				.put("height", shape.path("height").decimalValue());
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

	private static String nameOfShape(JsonNode view, String shapeId) {
		return view.path("shapes")
			.valueStream()
			.filter((shape) -> shapeId.equals(shape.path("id").asString()))
			.findFirst()
			.orElseThrow()
			.path("entity")
			.path("name")
			.asString();
	}

	private static List<String> names(JsonNode items) {
		List<String> names = new ArrayList<>();
		items.forEach((item) -> names.add(item.path("name").asString()));
		return names;
	}

}

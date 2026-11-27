package io.oea.steps.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/diagram/bpmn-process-diagrams.feature (us-074-bpmn-process-diagrams). The BPMN import
 * changes the metamodel of its instance, so the feature has an instance of its own (design D9): no other
 * feature imports the package or relies on its types.
 */
public class BpmnProcessSteps {

	private static final String BPMN = "oea-starter-bpmn";

	private static final String TOGAF = "oea-starter-togaf-classic";

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private static RepositoryInstance shared;

	private HttpResponse<String> response;

	private String diagram;

	private String second;

	private boolean taskCreated;

	@Given("an instance of its own with the TOGAF starter package, an enterprise architect and a metamodel administrator for BPMN")
	public void instance() throws Exception {
		synchronized (BpmnProcessSteps.class) {
			if (shared == null) {
				shared = RepositoryInstance.start();
			}
		}
	}

	@AfterAll
	public static void stop() {
		if (shared != null) {
			shared.close();
			shared = null;
		}
	}

	/** The organization metatype of the scenario "Name taken" does not stay. */
	@After
	public void clean() throws Exception {
		if (this.taskCreated && shared != null) {
			shared.oidc().update("DELETE FROM metatypes WHERE name = 'Task' AND source_package_id IS NULL");
		}
	}

	// Given

	@Given("the organization has the BPMN metatype {string}")
	public void organizationMetatype(String name) throws Exception {
		shared.oidc().update("INSERT INTO metatypes (config_id, name) VALUES ('" + CONFIG + "', '" + name + "')");
		this.taskCreated = true;
	}

	@Given("a BPMN diagram {string}")
	public void diagram(String name) throws Exception {
		this.diagram = createDiagram(name, shared.oidc().single("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'"));
	}

	@Given("a BPMN diagram {string} that also allows {string}")
	public void diagramWith(String name, String metatype) throws Exception {
		String type = shared.oidc().single("SELECT id::text FROM metatypes WHERE name = '" + metatype + "' AND source_package_id = '" + TOGAF + "'");
		String viewpoint = shared.oidc()
			.single("INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), 'BPMN with " + metatype
					+ "', 'bpmn2', (SELECT min(id) FROM persons)) RETURNING id::text");
		String source = shared.oidc().single("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'");
		shared.oidc()
			.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) SELECT '" + viewpoint
					+ "', metatype_id FROM viewpoint_metatypes WHERE viewpoint_id = '" + source + "'");
		shared.oidc()
			.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) SELECT '" + viewpoint
					+ "', metatype_id FROM viewpoint_connection_types WHERE viewpoint_id = '" + source + "'");
		shared.oidc().update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES ('" + viewpoint + "', '" + type + "')");
		this.diagram = createDiagram(name, viewpoint);
	}

	// When

	@When("the metamodel administrator imports the BPMN starter package")
	public void importBpmn() throws Exception {
		this.response = shared.post("/api/v1/metamodel/starter-packages/" + BPMN + "/import", "", shared.metamodelAdmin(),
				java.util.Map.of());
	}

	@When("the enterprise architect saves the tasks {string} and {string} with a {string}")
	public void saveTasks(String first, String second, String flow) throws Exception {
		save(entity(first, "User Task", 0, 0), entity(second, "Service Task", 300, 0), flow);
	}

	@When("the enterprise architect saves the task {string} and the component {string} with a {string}")
	public void saveTaskAndComponent(String task, String component, String flow) throws Exception {
		String type = shared.oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component' AND source_package_id = '" + TOGAF + "'");
		save(entity(task, "User Task", 0, 0), entityOf(component, type, 300, 0), flow);
	}

	// Then

	@Then("the BPMN import is rejected with 409 naming {string}")
	public void conflict(String name) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(409);
		assertThat(this.response.body()).contains("metatype " + name);
	}

	@Then("no metatype of the BPMN starter package exists")
	public void none() throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '" + BPMN + "'"))
			.isEqualTo("0");
	}

	@Then("the organization metatype {string} is removed")
	public void removed(String name) throws Exception {
		shared.oidc().update("DELETE FROM metatypes WHERE name = '" + name + "' AND source_package_id IS NULL");
		this.taskCreated = false;
	}

	@Then("the BPMN import succeeds with {int} created metatypes")
	public void succeeds(int created) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("created").asInt()).isEqualTo(created);
	}

	@Then("{int} element metatypes and {int} connection types exist with scope {string} and the origin {string}")
	public void counts(int elements, int connections, String scope, String origin) throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '" + origin
				+ "' AND scope = '" + scope + "' AND NOT is_connection")).isEqualTo(String.valueOf(elements));
		assertThat(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '" + origin
				+ "' AND scope = '" + scope + "' AND is_connection")).isEqualTo(String.valueOf(connections));
	}

	@Then("the viewpoint {string} can be chosen for a new diagram")
	public void chosen(String name) throws Exception {
		String viewpoint = shared.oidc().single("SELECT id::text FROM viewpoints WHERE name = '" + name + "'");
		HttpResponse<String> created = shared.send("POST", "/api/v1/diagrams", JsonMapper.shared()
			.createObjectNode()
			.put("name", "Chosen " + UUID.randomUUID())
			.put("viewpointId", viewpoint)
			.toString(), shared.architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
	}

	@Then("the {int} metatypes of the TOGAF starter package are unchanged")
	public void togafUnchanged(int count) throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '" + TOGAF + "'"))
			.isEqualTo(String.valueOf(count));
		assertThat(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '" + TOGAF
				+ "' AND is_composition")).isEqualTo("1");
	}

	@Then("no metatype of the package is the composition")
	public void noComposition() throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '" + BPMN
				+ "' AND is_composition")).isEqualTo("0");
	}

	@Then("the BPMN starter package is listed as imported")
	public void listed() throws Exception {
		HttpResponse<String> list = shared.send("GET", "/api/v1/metamodel/starter-packages", null, shared.metamodelAdmin());
		assertThat(list.statusCode()).isEqualTo(200);
		JsonNode entry = JsonMapper.shared().readTree(list.body()).valueStream()
			.filter((candidate) -> BPMN.equals(candidate.path("packageId").asString()))
			.findFirst()
			.orElseThrow();
		assertThat(entry.path("imported").asBoolean()).isTrue();
	}

	@Then("the base type of the BPMN metatype {string} is {string}")
	public void base(String type, String base) throws Exception {
		assertThat(shared.oidc().single("SELECT b.name FROM metatypes m JOIN metatypes b ON b.id = m.base_metatype_id "
				+ "WHERE m.name = '" + type + "' AND m.source_package_id = '" + BPMN + "'")).isEqualTo(base);
	}

	@Then("the BPMN save is accepted with {int} edge")
	public void accepted(int edges) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("edges")).hasSize(edges);
	}

	@Then("the BPMN save is rejected with 422 and the code {string}")
	public void rejected(String code) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("errors").get(0).path("code").asString())
			.isEqualTo(code);
	}

	// collapsed sub-processes (us-074-bpmn-pools-and-lanes)

	@Given("a BPMN diagram {string} with the sub-process {string} holding the tasks {string} and {string}")
	public void withSubProcess(String name, String subProcess, String first, String second) throws Exception {
		this.diagram = createDiagram(name, shared.oidc().single("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'"));
		Placed sub = entity(subProcess, "Sub-Process", 0, 0);
		sub.shapeNode().put("width", BigDecimal.valueOf(270)).put("height", BigDecimal.valueOf(150));
		Placed one = entity(first, "User Task", 10, 10);
		Placed two = entity(second, "User Task", 150, 10);
		one.shapeNode().put("parentShapeId", sub.shape());
		two.shapeNode().put("parentShapeId", sub.shape());
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("shapes").add(sub.shapeNode()).add(one.shapeNode()).add(two.shapeNode());
		body.putArray("newEntities").add(sub.entity()).add(one.entity()).add(two.entity());
		this.response = shared.send("PUT", "/api/v1/views/diagrams/" + this.diagram, body.toString(), shared.architect(),
				java.util.Map.of("If-Match", "\"1\""));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Given("a second BPMN diagram showing {string}")
	public void secondDiagram(String name) throws Exception {
		String viewpoint = shared.oidc().single("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'");
		this.second = createDiagram("Second", viewpoint);
		JsonNode shape = shapeOf(view(this.diagram), name);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("shapes").add(writeOf(shape, UUID.randomUUID().toString(), null, null));
		HttpResponse<String> saved = shared.send("PUT", "/api/v1/views/diagrams/" + this.second, body.toString(),
				shared.architect(), java.util.Map.of("If-Match", "\"1\""));
		assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
	}

	@When("the enterprise architect collapses {string} and saves")
	public void collapse(String name) throws Exception {
		saveCollapsed(name, true);
	}

	@When("the enterprise architect collapses the task {string} and saves")
	public void collapseTask(String name) throws Exception {
		saveCollapsed(name, true);
	}

	@When("the enterprise architect expands {string} and saves")
	public void expand(String name) throws Exception {
		saveCollapsed(name, false);
	}

	@Then("the BPMN view shows {string} collapsed with the size {int} x {int}")
	public void collapsedView(String name, int width, int height) throws Exception {
		checkShape(name, true, width, height);
	}

	@Then("the BPMN view shows {string} expanded with the size {int} x {int}")
	public void expandedView(String name, int width, int height) throws Exception {
		checkShape(name, false, width, height);
	}

	@Then("the task {string} is still nested in {string}")
	public void nested(String task, String container) throws Exception {
		JsonNode view = view(this.diagram);
		assertThat(shapeOf(view, task).path("parentShapeId").asString())
			.isEqualTo(shapeOf(view, container).path("id").asString());
	}

	@Then("the second BPMN diagram shows {string} expanded")
	public void secondExpanded(String name) throws Exception {
		assertThat(shapeOf(view(this.second), name).path("collapsed").asBoolean()).isFalse();
	}

	@Then("the layout is audited with {int} changed shape")
	public void auditedChanged(int changed) throws Exception {
		assertThat(shared.oidc().single("SELECT details ->> 'changed' FROM audit.events WHERE event_type = 'diagram.layout-saved' "
				+ "AND target_id = '" + this.diagram + "' ORDER BY id DESC LIMIT 1")).isEqualTo(String.valueOf(changed));
	}

	@Given("a BPMN diagram {string} with the pool {string}")
	public void withPool(String name, String pool) throws Exception {
		this.diagram = createDiagram(name, shared.oidc().single("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'"));
		Placed container = entity(pool, "Pool", 0, 0);
		container.shapeNode().put("width", BigDecimal.valueOf(600)).put("height", BigDecimal.valueOf(200));
		Placed lane = entity("Sales", "Lane", 30, 0);
		lane.shapeNode().put("width", BigDecimal.valueOf(570)).put("height", BigDecimal.valueOf(100)).put("parentShapeId", container.shape());
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("shapes").add(container.shapeNode()).add(lane.shapeNode());
		body.putArray("newEntities").add(container.entity()).add(lane.entity());
		this.response = shared.send("PUT", "/api/v1/views/diagrams/" + this.diagram, body.toString(), shared.architect(),
				java.util.Map.of("If-Match", "\"1\""));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@When("the enterprise architect switches {string} to {word} and saves")
	public void switchPool(String name, String orientation) throws Exception {
		saveOrientation(name, orientation);
	}

	@When("the enterprise architect switches the lane {string} to {word} and saves")
	public void switchLane(String name, String orientation) throws Exception {
		saveOrientation(name, orientation);
	}

	@Then("the BPMN view shows {string} as {word}")
	public void orientationOf(String name, String orientation) throws Exception {
		assertThat(shapeOf(view(this.diagram), name).path("orientation").asString()).isEqualTo(orientation);
	}

	@Then("the second BPMN diagram shows {string} as {word}")
	public void secondOrientation(String name, String orientation) throws Exception {
		assertThat(shapeOf(view(this.second), name).path("orientation").asString()).isEqualTo(orientation);
	}

	private void saveOrientation(String name, String orientation) throws Exception {
		JsonNode view = view(this.diagram);
		String id = shapeOf(view, name).path("id").asString();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		var shapes = body.putArray("shapes");
		for (JsonNode shape : view.path("shapes")) {
			ObjectNode write = writeOf(shape, shape.path("id").asString(), shape.path("parentShapeId").isNull() ? null
					: shape.path("parentShapeId").asString(), null);
			write.put("orientation", id.equals(shape.path("id").asString()) ? orientation : shape.path("orientation").asString());
			shapes.add(write);
		}
		this.response = shared.send("PUT", "/api/v1/views/diagrams/" + this.diagram, body.toString(), shared.architect(),
				java.util.Map.of("If-Match", "\"" + version(this.diagram) + "\""));
	}

	// helpers

	private void saveCollapsed(String name, boolean collapsed) throws Exception {
		JsonNode view = view(this.diagram);
		String id = shapeOf(view, name).path("id").asString();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		var shapes = body.putArray("shapes");
		for (JsonNode shape : view.path("shapes")) {
			shapes.add(writeOf(shape, shape.path("id").asString(), shape.path("parentShapeId").isNull() ? null
					: shape.path("parentShapeId").asString(), id.equals(shape.path("id").asString()) ? collapsed : null));
		}
		this.response = shared.send("PUT", "/api/v1/views/diagrams/" + this.diagram, body.toString(), shared.architect(),
				java.util.Map.of("If-Match", "\"" + version(this.diagram) + "\""));
	}

	private void checkShape(String name, boolean collapsed, int width, int height) throws Exception {
		JsonNode shape = shapeOf(view(this.diagram), name);
		assertThat(shape.path("collapsed").asBoolean()).isEqualTo(collapsed);
		assertThat(shape.path("width").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(width));
		assertThat(shape.path("height").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(height));
	}

	private JsonNode view(String diagram) throws Exception {
		HttpResponse<String> got = shared.send("GET", "/api/v1/views/diagrams/" + diagram, null, shared.architect());
		assertThat(got.statusCode()).as(got.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(got.body());
	}

	private int version(String diagram) throws Exception {
		return Integer.parseInt(shared.oidc().single("SELECT version::text FROM diagrams WHERE id = '" + diagram + "'"));
	}

	private static JsonNode shapeOf(JsonNode view, String entityName) {
		return view.path("shapes").valueStream()
			.filter((shape) -> entityName.equals(shape.path("entity").path("name").asString()))
			.findFirst()
			.orElseThrow();
	}

	/** A shape of the view as a write of an existing entity; {@code collapsed} null keeps the stored flag. */
	private static ObjectNode writeOf(JsonNode shape, String id, String parent, Boolean collapsed) {
		ObjectNode write = JsonMapper.shared()
			.createObjectNode()
			.put("id", id)
			.put("entityId", shape.path("entity").path("id").asInt())
			.put("x", shape.path("x").decimalValue())
			.put("y", shape.path("y").decimalValue())
			.put("width", shape.path("width").decimalValue())
			.put("height", shape.path("height").decimalValue())
			.put("collapsed", (collapsed != null) ? collapsed : shape.path("collapsed").asBoolean());
		if (parent != null) {
			write.put("parentShapeId", parent);
		}
		return write;
	}

	private record Placed(String key, String shape, ObjectNode entity, ObjectNode shapeNode) {
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

	private Placed entity(String name, String type, double x, double y) throws Exception {
		return entityOf(name, shared.oidc().single("SELECT id::text FROM metatypes WHERE name = '" + type + "' AND source_package_id = '" + BPMN + "'"), x, y);
	}

	private Placed entityOf(String name, String metatypeId, double x, double y) {
		String key = UUID.randomUUID().toString();
		String shape = UUID.randomUUID().toString();
		ObjectNode entity = JsonMapper.shared().createObjectNode().put("key", key).put("metatypeId", metatypeId).put("name", name);
		ObjectNode node = JsonMapper.shared()
			.createObjectNode()
			.put("id", shape)
			.put("newEntityKey", key)
			.put("x", BigDecimal.valueOf(x))
			.put("y", BigDecimal.valueOf(y))
			.put("width", BigDecimal.valueOf(90))
			.put("height", BigDecimal.valueOf(60));
		return new Placed(key, shape, entity, node);
	}

	private void save(Placed source, Placed target, String flow) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("shapes").add(source.shapeNode()).add(target.shapeNode());
		body.putArray("newEntities").add(source.entity()).add(target.entity());
		String key = UUID.randomUUID().toString();
		String type = shared.oidc().single("SELECT id::text FROM metatypes WHERE name = '" + flow + "' AND source_package_id = '" + BPMN + "'");
		body.putArray("newConnections").add(JsonMapper.shared().createObjectNode().put("key", key).put("metatypeId", type));
		ObjectNode edge = JsonMapper.shared()
			.createObjectNode()
			.put("id", UUID.randomUUID().toString())
			.put("newConnectionKey", key)
			.put("sourceShapeId", source.shape())
			.put("targetShapeId", target.shape());
		edge.putArray("points");
		body.putArray("edges").add(edge);
		this.response = shared.send("PUT", "/api/v1/views/diagrams/" + this.diagram, body.toString(), shared.architect(),
				java.util.Map.of("If-Match", "\"1\""));
	}

}

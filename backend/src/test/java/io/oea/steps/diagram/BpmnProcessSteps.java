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

	// helpers

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

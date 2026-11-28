package io.oea.steps.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/lineage/data-lineage.feature (us-065-lineage-model-and-query). The imports change the metamodel of
 * the instance, so the feature has an instance of its own. Entities are addressed by their names within a scenario.
 */
public class DataLineageSteps {

	private static final List<String> PACKAGES = List.of("oea-starter-togaf-classic", "oea-starter-bpmn");

	private static final String BATCH = "/api/v1/entities/batch";

	private static final String QUERY = "/api/v1/views/lineage/query";

	private static RepositoryInstance shared;

	private final Map<String, Integer> entities = new HashMap<>();

	private final List<Integer> flows = new ArrayList<>();

	private int lastCarries;

	private int outputId;

	private JsonNode result;

	private HttpResponse<String> response;

	private JsonNode catalog;

	@Given("an instance of its own with the TOGAF and the BPMN package")
	public void instance() throws Exception {
		synchronized (DataLineageSteps.class) {
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
		this.flows.clear();
	}

	@AfterAll
	public static void stop() {
		if (shared != null) {
			shared.close();
			shared = null;
		}
	}

	// Given

	@Given("the data object {string}")
	public void dataObject(String name) throws Exception {
		create(name, "Data Object");
	}

	@Given("the data object {string} and the task {string}")
	public void dataObjectAndTask(String object, String task) throws Exception {
		create(object, "Data Object");
		create(task, "User Task");
	}

	@Given("the data object {string} and the systems {string}")
	public void objectAndOne(String object, String system) throws Exception {
		create(object, "Data Object");
		create(system, "Application Component");
	}

	@Given("the data object {string} and the systems {string} and {string}")
	public void objectAndTwo(String object, String first, String second) throws Exception {
		objectAndOne(object, first);
		create(second, "Application Component");
	}

	@Given("the data object {string} and the systems {string}, {string} and {string}")
	public void objectAndThree(String object, String first, String second, String third) throws Exception {
		objectAndTwo(object, first, second);
		create(third, third.equals("BI Tool") ? "System Software" : "Application Component");
	}

	@Given("the systems {string} and {string}")
	public void twoSystems(String first, String second) throws Exception {
		create(first, "Application Component");
		create(second, "Application Component");
	}

	@Given("the systems {string}, {string} and {string}")
	public void threeSystems(String first, String second, String third) throws Exception {
		twoSystems(first, second);
		create(third, "Application Component");
	}

	@Given("a data flow from {string} to {string} carrying {string}")
	public void carrying(String source, String target, String object) throws Exception {
		int flow = connect("Data Flow", entities.get(source), entities.get(target), null);
		this.flows.add(flow);
		this.lastCarries = connect("Carries Data", flow, entities.get(object), null);
		assertThat(this.lastCarries).isPositive();
	}

	@Given("a data flow from {string} to {string} carrying nothing")
	public void carryingNothing(String source, String target) throws Exception {
		this.flows.add(connect("Data Flow", entities.get(source), entities.get(target), null));
	}

	@Given("a data flow from {string} to {string} with the protocol {string} and the personal data categories {string}")
	public void withPersonalData(String source, String target, String protocol, String categories) throws Exception {
		this.flows.add(connect("Data Flow", entities.get(source), entities.get(target),
				properties("Protocol", protocol, "Personal data categories", categories)));
	}

	@Given("a data flow from {string} to {string} with the protocol {string} and no personal data categories")
	public void withoutPersonalData(String source, String target, String protocol) throws Exception {
		this.flows.add(connect("Data Flow", entities.get(source), entities.get(target),
				properties("Protocol", protocol, null, null)));
	}

	@Given("{string} reads {string} and enriches it")
	public void readsAndEnriches(String task, String object) throws Exception {
		connect("Data Input Association", entities.get(object), entities.get(task), null);
		this.outputId = connect("Data Output Association", entities.get(task), entities.get(object),
				properties("Transformation type", "enrich", null, null));
	}

	// When

	@When("the viewer queries the lineage of {string} downstream")
	public void downstream(String start) throws Exception {
		query(start, "downstream", null);
	}

	@When("the viewer queries the lineage of {string} upstream")
	public void upstream(String start) throws Exception {
		query(start, "upstream", null);
	}

	@When("the viewer queries the lineage of {string} with impact")
	public void impact(String start) throws Exception {
		query(start, "impact", null);
	}

	@When("the viewer queries the lineage of {string} with both directions")
	public void both(String start) throws Exception {
		query(start, "both", null);
	}

	@When("the viewer queries the lineage of {string} downstream for the metatype {string}")
	public void filtered(String start, String metatype) throws Exception {
		query(start, "downstream", metatype);
	}

	@When("the architect connects {string} to {string} by {string}")
	public void connects(String source, String target, String type) throws Exception {
		write(type, entities.get(source), entities.get(target));
	}

	@When("the architect connects the first data flow to the second by {string}")
	public void connectsFlows(String type) throws Exception {
		write(type, this.flows.get(0), this.flows.get(1));
	}

	@When("the architect connects the last carries-data connection to {string} by {string}")
	public void connectsCarries(String target, String type) throws Exception {
		write(type, this.lastCarries, entities.get(target));
	}

	@When("the viewer filters a catalog of data flows by personal data categories that are not empty and the protocol {string}")
	public void filterCatalog(String protocol) throws Exception {
		catalog("personal_data_categories:isNotNull", "protocol:eq:" + protocol);
	}

	@When("the viewer filters a catalog of data flows by personal data categories that are not empty")
	public void filterCatalog() throws Exception {
		catalog("personal_data_categories:isNotNull");
	}

	// Then

	@Then("the lineage nodes are")
	public void nodes(DataTable table) {
		List<String> expected = new ArrayList<>();
		for (Map<String, String> row : table.asMaps()) {
			expected.add(row.get("name") + ":" + row.get("depth") + ":" + row.get("roles"));
		}
		List<String> actual = new ArrayList<>();
		this.result.path("nodes").forEach((node) -> actual.add(node.path("name").asString() + ":" + node.path("depth").asInt()
				+ ":" + String.join(",", node.path("roles").valueStream().map(JsonNode::asString).toList())));
		assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Then("the lineage path is {string}")
	public void path(String expected) {
		Map<Integer, String> names = new HashMap<>();
		this.result.path("nodes").forEach((node) -> names.put(node.path("id").asInt(), node.path("name").asString()));
		assertThat(this.result.path("paths")).hasSize(1);
		List<String> path = this.result.path("paths").get(0).valueStream().map((id) -> names.get(id.asInt())).toList();
		assertThat(String.join(" > ", path)).isEqualTo(expected);
	}

	@Then("the lineage has no cycle")
	public void noCycle() {
		assertThat(this.result.path("cycleDetected").asBoolean()).isFalse();
	}

	@Then("the lineage has a cycle")
	public void cycle() {
		assertThat(this.result.path("cycleDetected").asBoolean()).isTrue();
	}

	@Then("the lineage has {int} nodes, all with impact")
	public void impactNodes(int count) {
		assertThat(this.result.path("nodes")).hasSize(count);
		this.result.path("nodes").forEach((node) -> assertThat(node.path("impact").asBoolean()).isTrue());
	}

	@Then("the lineage has {int} edges")
	public void edges(int count) {
		assertThat(this.result.path("edges")).hasSize(count);
	}

	@Then("the lineage is empty")
	public void empty() {
		assertThat(this.result.path("nodes")).isEmpty();
		assertThat(this.result.path("edges")).isEmpty();
		assertThat(this.result.path("paths")).isEmpty();
	}

	@Then("the lineage has the nodes {string} and {string} and {int} edges")
	public void nodesAndEdges(String first, String second, int edges) {
		assertThat(this.result.path("nodes").valueStream().map((node) -> node.path("name").asString()))
			.containsExactlyInAnyOrder(first, second);
		assertThat(this.result.path("edges")).hasSize(edges);
	}

	@Then("the lineage has the task {string} as reader and writer")
	public void task(String name) {
		assertThat(this.result.path("nodes")).hasSize(1);
		JsonNode node = this.result.path("nodes").get(0);
		assertThat(node.path("name").asString()).isEqualTo(name);
		assertThat(node.path("roles").valueStream().map(JsonNode::asString)).containsExactlyInAnyOrder("reader", "writer");
	}

	@Then("the output edge has the transformation {string}")
	public void transformation(String value) {
		JsonNode edge = this.result.path("edges")
			.valueStream()
			.filter((candidate) -> candidate.path("id").asInt() == this.outputId)
			.findFirst()
			.orElseThrow();
		assertThat(edge.path("transformationType").asString()).isEqualTo(value);
	}

	@Then("the carries-data connection has its own ID, the data flow as source and {string} as target")
	public void carriesStored(String target) throws Exception {
		assertThat(this.lastCarries).isNotEqualTo(this.flows.get(this.flows.size() - 1));
		assertThat(shared.oidc().single("SELECT source_entity_id || ':' || target_entity_id FROM entity_connections WHERE entity_id = "
				+ this.lastCarries)).isEqualTo(this.flows.get(this.flows.size() - 1) + ":" + entities.get(target));
	}

	@Then("the connection write is rejected with 422 {string}")
	public void rejected(String code) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		assertThat(this.response.body()).contains(code);
	}

	@Then("the catalog shows {int} data flow")
	public void catalogOne(int count) {
		catalogCount(count);
	}

	@Then("the catalog shows {int} data flows")
	public void catalogMany(int count) {
		catalogCount(count);
	}

	// helpers

	private void catalogCount(int count) {
		assertThat(this.catalog.path("rows")).hasSize(count);
	}

	private void catalog(String... filters) throws Exception {
		ObjectNode create = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Flows " + System.nanoTime())
			.put("primaryMetatypeId", metatype("Data Flow"))
			.put("scope", "instance");
		HttpResponse<String> created = shared.send("POST", "/api/v1/catalogs", create.toString(), shared.architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		String id = JsonMapper.shared().readTree(created.body()).path("id").asString();
		ObjectNode columns = JsonMapper.shared().createObjectNode();
		columns.putArray("columns").addObject().put("field", "name");
		columns.withArray("columns").addObject().put("propertyDefId", property("Personal data categories"));
		columns.withArray("columns").addObject().put("propertyDefId", property("Protocol"));
		HttpResponse<String> configured = shared.send("PUT", "/api/v1/catalogs/" + id + "/columns", columns.toString(),
				shared.architect());
		assertThat(configured.statusCode()).as(configured.body()).isEqualTo(200);
		StringBuilder query = new StringBuilder("?");
		for (String filter : filters) {
			query.append("filter=").append(URLEncoder.encode(filter, StandardCharsets.UTF_8)).append('&');
		}
		HttpResponse<String> got = shared.send("GET", "/api/v1/catalogs/" + id + "/query" + query, null, shared.viewer());
		assertThat(got.statusCode()).as(got.body()).isEqualTo(200);
		this.catalog = JsonMapper.shared().readTree(got.body());
	}

	private void query(String start, String direction, String metatype) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("startEntityId", entities.get(start)).put("direction", direction);
		if (metatype != null) {
			body.putArray("metatypeIds").add(metatype(metatype));
		}
		this.response = shared.send("POST", QUERY, body.toString(), shared.viewer());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		this.result = JsonMapper.shared().readTree(this.response.body());
	}

	private void write(String type, int source, int target) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("atomic", true);
		body.putArray("connections")
			.addObject()
			.put("connectionMetatypeId", metatype(type))
			.put("sourceId", source)
			.put("targetId", target);
		this.response = shared.post(BATCH, body.toString(), shared.architect(), Map.of());
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

	private int connect(String type, int source, int target, ObjectNode properties) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("atomic", true);
		ObjectNode connection = body.putArray("connections")
			.addObject()
			.put("connectionMetatypeId", metatype(type))
			.put("sourceId", source)
			.put("targetId", target);
		if (properties != null) {
			connection.set("properties", properties.path("list"));
		}
		HttpResponse<String> done = shared.post(BATCH, body.toString(), shared.architect(), Map.of());
		assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(done.body()).path("items").get(0).path("id").asInt();
	}

	private ObjectNode properties(String first, String firstValue, String second, String secondValue) throws Exception {
		ObjectNode holder = JsonMapper.shared().createObjectNode();
		var list = holder.putArray("list");
		list.addObject().put("propertyDefId", property(first)).put("value", firstValue);
		if (second != null) {
			list.addObject().put("propertyDefId", property(second)).put("value", secondValue);
		}
		return holder;
	}

	private static String property(String name) throws Exception {
		return shared.oidc().single("SELECT id::text FROM property_definitions WHERE name = '" + name + "'");
	}

	private static String metatype(String name) throws Exception {
		return shared.oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name + "' AND source_package_id IN ('"
				+ String.join("', '", PACKAGES) + "')");
	}

}

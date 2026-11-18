package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.metamodel.MetamodelInstance;
import io.oea.testsupport.metamodel.MetamodelInstance.Session;
import io.oea.testsupport.metamodel.TestPackages;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/metamodel/architecture-layers.feature (change s17-architecture-layers). The
 * feature has its own instance without the starter package; every scenario starts with a reset of
 * the layers, so that the scenarios do not depend on each other. Sabine is the metamodel
 * administrator, Franz the viewer; both log in once.
 */
public class ArchitectureLayerSteps {

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private static final String LAYERS = "/api/v1/metamodel/architecture-layers";

	private static final String METATYPES = "/api/v1/metamodel/metatypes";

	private static final String LAST_SCENARIO = "Layer dropped by a new package version";

	private static MetamodelInstance shared;

	private HttpResponse<String> response;

	private final List<HttpResponse<String>> responses = new ArrayList<>();

	private int layersBefore;

	private int importsBefore;

	private String packageId;

	private String motivationType;

	private final Map<String, String> metatypes = new java.util.HashMap<>();

	@Given("a repository instance with the metamodel administrator Sabine and the viewer Franz")
	public void instance() throws Exception {
		synchronized (ArchitectureLayerSteps.class) {
			if (shared == null) {
				shared = MetamodelInstance.start();
			}
		}
		shared.oidc().update("UPDATE metamodel_configurations SET edit_mode = 'gui-and-import' WHERE id = '" + INSTANCE + "'");
		shared.oidc().update("UPDATE metatypes SET architecture_layer_id = NULL");
		shared.oidc().update("DELETE FROM metatypes WHERE scope = 'organization'");
		shared.oidc().update("DELETE FROM i18n_entries WHERE entity_type = 'architecture_layer'");
		shared.oidc().update("DELETE FROM architecture_layers");
	}

	/**
	 * Every running instance holds its database connections until the end of the run, and the
	 * database accepts few of them (see MetatypeEditingSteps): the instance of this feature is closed
	 * after its last scenario, so that the features that follow still get their connections.
	 */
	@After
	public void closeAfterTheLastScenario(Scenario scenario) {
		if (LAST_SCENARIO.equals(scenario.getName())) {
			stop();
		}
	}

	@AfterAll
	public static void stop() {
		if (shared != null) {
			shared.close();
			shared = null;
		}
	}

	// Requirement: Architecture layers

	@Given("the layer {string} exists")
	public void layerExists(String name) throws Exception {
		assertThat(create(name).statusCode()).isEqualTo(201);
	}

	@Given("the layers {string}, {string} and {string} exist")
	public void layersExist(String first, String second, String third) throws Exception {
		layerExists(first);
		layerExists(second);
		layerExists(third);
	}

	@Given("{int} layers exist")
	public void layersExist(int count) throws Exception {
		for (int i = 1; i <= count; i++) {
			layerExists("Layer " + i);
		}
	}

	@Given("the instance metamodel is set to {string}")
	public void instanceMode(String editMode) throws Exception {
		shared.oidc().update("UPDATE metamodel_configurations SET edit_mode = '" + editMode + "' WHERE id = '" + INSTANCE
				+ "'");
	}

	@When("Sabine creates the layer {string}")
	public void sabineCreates(String name) throws Exception {
		this.layersBefore = layers().size();
		this.response = create(name);
	}

	@When("Franz creates the layer {string}")
	public void franzCreates(String name) throws Exception {
		this.layersBefore = layers().size();
		this.response = shared.send("POST", LAYERS, write(name).toString(), shared.viewer(), Map.of());
	}

	@Then("the layer {string} is listed after the existing layers and the audit records {string} under the kind {string}")
	public void listedAndAudited(String name, String eventType, String kind) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
		JsonNode layers = layers();
		assertThat(layers.get(layers.size() - 1).path("name").asString()).isEqualTo(name);
		String id = body(this.response).path("id").asString();
		JsonNode changes = body(shared.get("/api/v1/metamodel/changes?kind=" + kind, shared.metamodelAdmin())).path("items");
		assertThat(changes.valueStream()
			.filter((change) -> id.equals(change.path("targetId").asString()))
			.map((change) -> change.path("eventType").asString())).containsExactly(eventType);
	}

	@When("Sabine creates one layer with a name of {int} characters, one with {int} characters and one with only blanks")
	public void createAtTheLimits(int valid, int tooLong) throws Exception {
		this.responses.clear();
		this.responses.add(create("n".repeat(valid)));
		this.responses.add(create("m".repeat(tooLong)));
		this.responses.add(create("   "));
	}

	@Then("the layer with {int} characters is created, and the others are rejected with {int} naming {string}")
	public void limitsChecked(int length, int status, String field) throws Exception {
		assertThat(this.responses.get(0).statusCode()).isEqualTo(201);
		assertThat(body(this.responses.get(0)).path("name").asString()).hasSize(length);
		for (HttpResponse<String> rejected : this.responses.subList(1, 3)) {
			assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(status);
			assertThat(body(rejected).path("invalidFields").valueStream().map(JsonNode::asString)).contains(field);
		}
		assertThat(layers()).hasSize(1);
	}

	@Then("the layer request is rejected with {int} {string} and no layer is created")
	public void rejectedNoLayer(int status, String problem) throws Exception {
		rejected(status, problem);
		assertThat(layers()).hasSize(this.layersBefore);
	}

	@Then("the layer request is rejected with {int} and no layer is created")
	public void rejectedNoLayer(int status) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		assertThat(layers()).hasSize(this.layersBefore);
	}

	@When("Sabine creates a layer, and then another one")
	public void createTwo() throws Exception {
		this.responses.clear();
		this.responses.add(create("Layer 20"));
		this.responses.add(create("Layer 21"));
	}

	@Then("the 20th is created, and the 21st is rejected with {int}")
	public void twentyAndTwentyOne(int status) throws Exception {
		assertThat(this.responses.get(0).statusCode()).isEqualTo(201);
		assertThat(this.responses.get(1).statusCode()).as(this.responses.get(1).body()).isEqualTo(status);
		assertThat(layers()).hasSize(20);
	}

	@When("Sabine moves {string} before {string}")
	public void move(String name, String before) throws Exception {
		JsonNode layers = layers();
		JsonNode moved = find(layers, name);
		int target = find(layers, before).path("sortOrder").asInt();
		// the position is the one after the move: removing the layer first shifts the later ones up
		int position = (moved.path("sortOrder").asInt() < target) ? target - 1 : target;
		this.response = update(moved, name, position);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Then("Franz sees {string} before {string}")
	public void order(String first, String second) throws Exception {
		List<String> names = names(body(shared.get(LAYERS, shared.viewer())));
		assertThat(names.indexOf(first)).isGreaterThanOrEqualTo(0).isLessThan(names.indexOf(second));
	}

	@When("Sabine renames the layer {string} to {string}")
	public void rename(String name, String newName) throws Exception {
		this.response = update(find(layers(), name), newName, null);
	}

	@Then("the layer request is rejected with {int} {string} and the layer {string} is unchanged")
	public void rejectedUnchanged(int status, String problem, String name) throws Exception {
		rejected(status, problem);
		assertThat(names(layers())).contains(name);
	}

	// Requirement: Labels of a layer

	@When("Sabine sets the German label {string} for the layer {string}")
	public void setLabel(String label, String name) throws Exception {
		ObjectNode labels = JsonMapper.shared().createObjectNode();
		labels.putObject("labels").put(key(name), label);
		this.response = shared.send("PUT", "/api/v1/metamodel/labels/de", labels.toString(), shared.metamodelAdmin(),
				Map.of());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Then("the German label bundle carries {string} under the key of the layer {string}")
	public void bundle(String label, String name) throws Exception {
		JsonNode bundle = body(shared.get("/api/v1/i18n/de", shared.viewer()));
		assertThat(bundle.path(key(name)).asString()).isEqualTo(label);
	}

	// Requirement: Layer of a metatype

	@Given("the metatype {string} without a layer")
	public void organizationMetatype(String name) throws Exception {
		this.metatypes.put(name, createMetatype(name, null));
	}

	@When("Sabine sets the layer {string} on the metatype {string}")
	public void setLayer(String layer, String metatype) throws Exception {
		this.response = putMetatype(metatype, layerId(layer));
	}

	@Then("the metatype {string} returns the layer {string} and the change is audited as a metatype change")
	public void metatypeLayer(String metatype, String layer) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		JsonNode read = body(shared.get(METATYPES + "/" + this.metatypes.get(metatype), shared.viewer()));
		assertThat(read.path("architectureLayerId").asString()).isEqualTo(layerId(layer));
		assertThat(read.path("architectureLayerName").asString()).isEqualTo(layer);
		assertThat(shared.oidc().single("""
				SELECT count(*)::text FROM audit.events WHERE event_type = 'metamodel.metatype-updated'
				AND target_id = '%s' AND details::text LIKE '%%architectureLayerId%%'"""
			.formatted(this.metatypes.get(metatype)))).isEqualTo("1");
	}

	@When("Sabine sets a layer ID that names no layer on the metatype {string}")
	public void setUnknownLayer(String metatype) throws Exception {
		this.response = putMetatype(metatype, UUID.randomUUID().toString());
	}

	@Then("the layer request is rejected with {int} naming {string} and the metatype {string} has no layer")
	public void unknownLayer(int status, String field, String metatype) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		assertThat(body(this.response).path("invalidFields").valueStream().map(JsonNode::asString)).containsExactly(field);
		JsonNode read = body(shared.get(METATYPES + "/" + this.metatypes.get(metatype), shared.viewer()));
		assertThat(read.path("architectureLayerId").isNull()).isTrue();
	}

	@Given("{string} is the layer of the metatypes {string} and {string}")
	public void layerOfTwo(String layer, String first, String second) throws Exception {
		this.metatypes.put(first, createMetatype(first, layerId(layer)));
		this.metatypes.put(second, createMetatype(second, layerId(layer)));
	}

	@When("Sabine deletes the layer {string}")
	public void delete(String name) throws Exception {
		JsonNode layer = find(layers(), name);
		this.response = shared.send("DELETE", LAYERS + "/" + layer.path("id").asString(), null, shared.metamodelAdmin(),
				Map.of("If-Match", "\"" + layer.path("version").asInt() + "\""));
	}

	@Then("the layer request is rejected with {int} {string} and the count {int}, and the layer {string} stays")
	public void inUse(int status, String problem, int count, String name) throws Exception {
		rejected(status, problem);
		assertThat(body(this.response).path("count").asInt()).isEqualTo(count);
		assertThat(names(layers())).contains(name);
	}

	// Requirement: Layers in packages

	@Given("the starter package {string} is installed")
	@When("Sabine imports the starter package {string}")
	public void importStarter(String packageId) throws Exception {
		this.importsBefore = Integer.parseInt(shared.oidc().single("SELECT count(*)::text FROM metamodel_package_imports"));
		this.response = shared.post("/api/v1/admin/metamodel/starter-packages/" + packageId + "/import", "",
				shared.metamodelAdmin());
	}

	@Then("the layers Business, Data, Application and Technology exist as imported, {string} has the layer {string} and {string} the layer {string}")
	public void starterLayers(String first, String firstLayer, String second, String secondLayer) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		JsonNode layers = layers();
		assertThat(names(layers)).containsExactly("Business", "Data", "Application", "Technology");
		assertThat(layers.valueStream().map((layer) -> layer.path("scope").asString())).containsOnly("imported");
		assertThat(layerOfMetatype(first)).isEqualTo(firstLayer);
		assertThat(layerOfMetatype(second)).isEqualTo(secondLayer);
	}

	@When("Sabine imports a package whose metatype references a layer that is not in the package")
	public void importUnknownReference() throws Exception {
		this.importsBefore = Integer.parseInt(shared.oidc().single("SELECT count(*)::text FROM metamodel_package_imports"));
		this.packageId = "layers-unknown-" + suffix();
		ObjectNode pkg = TestPackages.pkg(this.packageId, 1);
		((ObjectNode) pkg.path("metatypes").get(0)).put("architectureLayerId", UUID.randomUUID().toString());
		this.response = shared.post("/api/v1/admin/metamodel/import", pkg.toString(), shared.metamodelAdmin());
	}

	@Then("the import is rejected with {int} and nothing is imported")
	public void importRejected(int status) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		assertThat(Integer.parseInt(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '"
				+ this.packageId + "'"))).isZero();
		assertThat(Integer.parseInt(shared.oidc().single("SELECT count(*)::text FROM metamodel_package_imports")))
			.isEqualTo(this.importsBefore);
	}

	@Then("the import is rejected with {int} {string} listing the layer {string} and the layers are unchanged")
	public void importConflict(int status, String problem, String element) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		assertThat(body(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + problem);
		assertThat(body(this.response).path("conflictList").get(0).path("element").asString()).isEqualTo(element);
		assertThat(Integer.parseInt(shared.oidc().single("SELECT count(*)::text FROM metamodel_package_imports")))
			.isEqualTo(this.importsBefore);
		assertThat(names(layers())).containsExactly("Business");
	}

	@Then("the layer {string} is unchanged")
	public void unchanged(String name) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		JsonNode layer = find(layers(), name);
		assertThat(layer.path("scope").asString()).isEqualTo("organization");
		assertThat(layer.path("version").asInt()).isOne();
	}

	@Given("a package brought the layer {string}, assigned to one of its metatypes")
	public void packageWithLayer(String name) throws Exception {
		this.packageId = "layers-motivation-" + suffix();
		ObjectNode pkg = TestPackages.pkg(this.packageId, 1);
		pkg.putArray("architectureLayers")
			.addObject()
			.put("id", TestPackages.uuid(this.packageId, "layer0"))
			.put("name", name)
			.put("sortOrder", 0);
		((ObjectNode) pkg.path("metatypes").get(0)).put("architectureLayerId", TestPackages.uuid(this.packageId, "layer0"));
		HttpResponse<String> imported = shared.post("/api/v1/admin/metamodel/import", pkg.toString(), shared.metamodelAdmin());
		assertThat(imported.statusCode()).as(imported.body()).isEqualTo(200);
		this.motivationType = this.packageId + " Type 1";
		assertThat(layerOfMetatype(this.motivationType)).isEqualTo(name);
	}

	@When("Sabine imports a version of the package without {string}")
	public void importWithout(String name) throws Exception {
		ObjectNode pkg = TestPackages.pkg(this.packageId, 1);
		pkg.put("packageVersion", "2.0.0");
		this.response = shared.post("/api/v1/admin/metamodel/import", pkg.toString(), shared.metamodelAdmin());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Then("{string} no longer exists, and the metatype has no layer")
	public void dropped(String name) throws Exception {
		assertThat(names(layers())).doesNotContain(name);
		assertThat(layerOfMetatype(this.motivationType)).isNull();
		assertThat(body(this.response).path("layersDeleted").asInt()).isOne();
	}

	// helpers

	private HttpResponse<String> create(String name) throws Exception {
		return shared.send("POST", LAYERS, write(name).toString(), shared.metamodelAdmin(), Map.of());
	}

	private HttpResponse<String> update(JsonNode layer, String name, Integer position) throws Exception {
		ObjectNode write = write(name);
		if (position != null) {
			write.put("sortOrder", position);
		}
		return shared.send("PUT", LAYERS + "/" + layer.path("id").asString(), write.toString(), shared.metamodelAdmin(),
				Map.of("If-Match", "\"" + layer.path("version").asInt() + "\""));
	}

	private void rejected(int status, String problem) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		assertThat(body(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + problem);
	}

	private String createMetatype(String name, String layer) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("configId", INSTANCE);
		if (layer != null) {
			body.put("architectureLayerId", layer);
		}
		HttpResponse<String> created = shared.post(METATYPES, body.toString(), shared.metamodelAdmin());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		return body(created).path("id").asString();
	}

	private HttpResponse<String> putMetatype(String name, String layer) throws Exception {
		String id = this.metatypes.get(name);
		JsonNode read = body(shared.get(METATYPES + "/" + id, shared.metamodelAdmin()));
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("configId", INSTANCE);
		body.put("architectureLayerId", layer);
		return shared.send("PUT", METATYPES + "/" + id, body.toString(), shared.metamodelAdmin(),
				Map.of("If-Match", "\"" + read.path("version").asInt() + "\""));
	}

	private String layerId(String name) throws Exception {
		return find(layers(), name).path("id").asString();
	}

	private String key(String layerName) throws Exception {
		return "architecture-layer." + layerId(layerName) + ".name";
	}

	private String layerOfMetatype(String metatype) throws Exception {
		List<String> layer = shared.oidc().column("""
				SELECT coalesce(l.name, '') FROM metatypes m LEFT JOIN architecture_layers l ON l.id = m.architecture_layer_id
				WHERE m.name = '%s'""".formatted(metatype));
		assertThat(layer).hasSize(1);
		return layer.get(0).isEmpty() ? null : layer.get(0);
	}

	private JsonNode layers() throws Exception {
		HttpResponse<String> list = shared.get(LAYERS, shared.metamodelAdmin());
		assertThat(list.statusCode()).as(list.body()).isEqualTo(200);
		return body(list);
	}

	private static List<String> names(JsonNode layers) {
		return layers.valueStream().map((layer) -> layer.path("name").asString()).toList();
	}

	private static JsonNode find(JsonNode layers, String name) {
		return layers.valueStream()
			.filter((layer) -> name.equals(layer.path("name").asString()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no layer " + name));
	}

	private static ObjectNode write(String name) {
		return JsonMapper.shared().createObjectNode().put("name", name);
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static JsonNode body(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

}

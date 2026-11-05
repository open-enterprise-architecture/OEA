package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.metamodel.TestPackages;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/metamodel/solution-extensions.feature and features/metamodel/lock-mode.feature
 * (us-037-solution-extensions-and-lock-mode). The scenarios use the instance of the metamodel
 * editing features; every scenario creates its extensions and metatypes under unique names, and a
 * scenario that locks the instance unlocks it afterwards.
 */
public class MetamodelConfigurationSteps {

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private static final String CONFIGURATIONS = "/api/v1/metamodel/configurations";

	private static final String METATYPES = "/api/v1/metamodel/metatypes";

	private final String suffix = "-" + UUID.randomUUID().toString().substring(0, 8);

	/** Solution IDs by the name in the feature. */
	private final Map<String, String> solutions = new HashMap<>();

	/** Metatype IDs by the name in the feature. */
	private final Map<String, String> metatypes = new HashMap<>();

	private final List<String> created = new ArrayList<>();

	private String extension;

	private boolean locked;

	private int metatypesBefore;

	private String packageId;

	private HttpResponse<String> response;

	@After
	public void unlock() throws Exception {
		if (this.locked) {
			ok(send("PUT", CONFIGURATIONS + "/" + INSTANCE, change("gui-and-import"), instance().metamodelAdmin()), 200);
		}
	}

	// metamodel/solution-extensions

	@When("the architect creates an extension for the solution {string} in edit mode {string}")
	public void createExtension(String solution, String editMode) throws Exception {
		this.response = send("POST", CONFIGURATIONS, extensionBody(solution).put("editMode", editMode),
				instance().architect());
		this.extension = id(ok(this.response, 201));
	}

	@Then("the configurations list the extension of {string} with the instance configuration as parent")
	public void extensionListed(String solution) throws Exception {
		JsonNode listed = configuration(this.extension);
		assertThat(listed.path("scope").asString()).isEqualTo("solution");
		assertThat(listed.path("parentId").asString()).isEqualTo(INSTANCE);
		assertThat(listed.path("solutionId").asString()).isEqualTo(this.solutions.get(solution));
		assertThat(listed.path("editMode").asString()).isEqualTo("gui-and-import");
	}

	@When("the viewer creates an extension for the solution {string}")
	public void viewerCreatesExtension(String solution) throws Exception {
		this.response = send("POST", CONFIGURATIONS, extensionBody(solution), instance().viewer());
	}

	@Then("the extension is rejected with {int}")
	public void extensionRejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Given("an extension for the solution {string}")
	public void extensionFor(String solution) throws Exception {
		this.extension = id(ok(send("POST", CONFIGURATIONS, extensionBody(solution), instance().architect()), 201));
	}

	@Given("an extension for the solution {string} with {string}")
	public void extensionWith(String solution, String metatype) throws Exception {
		extensionFor(solution);
		createInExtension(metatype);
		ok(this.response, 201);
	}

	@When("the architect creates {string} in the extension")
	public void createInExtension(String name) throws Exception {
		this.response = send("POST", METATYPES, metatypeBody(name, this.extension), instance().architect());
		if (this.response.statusCode() == 201) {
			this.metatypes.put(name, id(body(this.response)));
		}
	}

	@Then("{string} is part of the effective metamodel of the extension")
	public void inEffectiveMetamodel(String name) throws Exception {
		ok(this.response, 201);
		JsonNode metatype = byName(metatypes(this.extension), name + this.suffix);
		assertThat(metatype.path("configId").asString()).isEqualTo(this.extension);
	}

	@Then("the metatypes of the instance do not list {string}")
	public void notInInstance(String name) throws Exception {
		List<String> names = new ArrayList<>();
		ok(send("GET", METATYPES, null, instance().viewer()), 200).forEach((m) -> names.add(m.path("name").asString()));
		assertThat(names).doesNotContain(name + this.suffix).contains("Application Component");
	}

	@Then("the metatype is rejected with {int} {string}")
	public void metatypeRejected(int status, String type) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		assertThat(body(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the extension has no metatypes")
	public void extensionEmpty() throws Exception {
		assertThat(configuration(this.extension).path("metatypeCount").asInt()).isZero();
	}

	@When("the architect and the metamodel administrator each create a metatype in the extension")
	public void twoAuthors() throws Exception {
		for (RepositoryInstance.Session session : List.of(instance().architect(), instance().metamodelAdmin())) {
			JsonNode metatype = ok(send("POST", METATYPES,
					metatypeBody("Trial " + this.created.size(), this.extension), session), 201);
			this.created.add(id(metatype));
		}
	}

	@Then("both metatypes are part of the extension and each creation is audited with its author")
	public void bothAudited() throws Exception {
		assertThat(configuration(this.extension).path("metatypeCount").asInt()).isEqualTo(2);
		String authors = instance().oidc().single("""
				SELECT count(DISTINCT actor_person_id)::text FROM audit.events
				WHERE event_type = 'metamodel.metatype-created' AND target_id IN ('%s', '%s')"""
			.formatted(this.created.get(0), this.created.get(1)));
		assertThat(authors).isEqualTo("2");
	}

	@When("a client reads the metatypes of the extension")
	public void readEffective() throws Exception {
		this.response = send("GET", METATYPES + "?configId=" + this.extension, null, instance().viewer());
	}

	@Then("the answer contains {string} with the instance configuration and {string} with the extension")
	public void bothGroups(String instanceType, String extensionType) {
		JsonNode list = ok(this.response, 200);
		assertThat(byName(list, instanceType).path("configId").asString()).isEqualTo(INSTANCE);
		assertThat(byName(list, extensionType + this.suffix).path("configId").asString()).isEqualTo(this.extension);
	}

	@When("the architect writes an entity of {string} through the batch")
	public void entityOfExtensionType(String name) throws Exception {
		ObjectNode request = object();
		request.putArray("entities").addObject().put("name", "AWS Lambda").put("metatypeId", this.metatypes.get(name));
		this.response = send("POST", "/api/v1/entities/batch", request, instance().architect());
	}

	@Then("the entity is rejected with {string}")
	public void entityRejected(String code) {
		JsonNode item = ok(this.response, 200).path("items").get(0);
		assertThat(item.path("errors").get(0).path("code").asString()).as(item.toString()).isEqualTo(code);
	}

	// metamodel/lock-mode

	@When("the metamodel administrator sets the instance configuration to {string}")
	public void setInstanceMode(String editMode) throws Exception {
		this.locked = true;
		this.response = send("PUT", CONFIGURATIONS + "/" + INSTANCE, change(editMode), instance().metamodelAdmin());
		ok(this.response, 200);
	}

	@Given("the instance configuration is {string}")
	public void instanceMode(String editMode) throws Exception {
		setInstanceMode(editMode);
	}

	@Then("the configurations show the instance configuration as {string}")
	public void instanceShown(String editMode) throws Exception {
		assertThat(configuration(INSTANCE).path("editMode").asString()).isEqualTo(editMode);
	}

	@Then("the audit trail names the metamodel administrator for the edit mode change")
	public void editModeAudited() throws Exception {
		String actor = instance().oidc().single("""
				SELECT p.email FROM audit.events e JOIN persons p ON p.id = e.actor_person_id
				WHERE e.event_type = 'metamodel.edit-mode-changed' AND e.target_id = '%s'
				ORDER BY e.id DESC LIMIT 1""".formatted(INSTANCE));
		assertThat(actor).startsWith("dev-metamodel-admin");
	}

	@When("the metamodel administrator creates a metatype in the instance")
	public void createInLockedInstance() throws Exception {
		this.metatypesBefore = Integer.parseInt(instance().oidc().single("SELECT count(*)::text FROM metatypes"));
		this.response = send("POST", METATYPES, metatypeBody("Locked", INSTANCE), instance().metamodelAdmin());
	}

	@Then("the request is rejected with 403 {string} and no metatype is created")
	public void lockedRejected(String type) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(403);
		assertThat(body(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + type);
		assertThat(Integer.parseInt(instance().oidc().single("SELECT count(*)::text FROM metatypes")))
			.isEqualTo(this.metatypesBefore);
	}

	@When("the metamodel administrator imports a package")
	public void importPackage() throws Exception {
		this.packageId = "test-locked" + this.suffix;
		this.response = send("POST", "/api/v1/admin/metamodel/import", TestPackages.pkg(this.packageId, 1),
				instance().metamodelAdmin());
	}

	@Then("the package is imported as usual")
	public void imported() throws Exception {
		ok(this.response, 200);
		assertThat(instance().oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '"
				+ this.packageId + "'")).isEqualTo("1");
	}

	@Given("the instance configuration is {string} and an extension is {string}")
	public void lockedWithExtension(String instanceMode, String extensionMode) throws Exception {
		setInstanceMode(instanceMode);
		this.extension = id(ok(send("POST", CONFIGURATIONS,
				extensionBody("Cloud Migration 2027").put("editMode", extensionMode), instance().architect()), 201));
	}

	@When("the architect creates a metatype in the extension")
	public void createInEditableExtension() throws Exception {
		this.response = send("POST", METATYPES, metatypeBody("CloudService", this.extension), instance().architect());
	}

	@Then("the metatype is created")
	public void metatypeCreated() {
		ok(this.response, 201);
	}

	// helpers

	private ObjectNode extensionBody(String solution) {
		String id = this.solutions.computeIfAbsent(solution, (k) -> UUID.randomUUID().toString());
		return object().put("solutionId", id).put("name", solution + this.suffix);
	}

	private ObjectNode metatypeBody(String name, String config) {
		// instance names stay as in the feature, so that a redefinition is detected
		String stored = "Application Component".equals(name) ? name : name + this.suffix;
		return object().put("name", stored).put("configId", config);
	}

	private static ObjectNode change(String editMode) {
		return object().put("name", "Instance metamodel").put("editMode", editMode);
	}

	private JsonNode configuration(String id) throws Exception {
		for (JsonNode node : ok(send("GET", CONFIGURATIONS, null, instance().viewer()), 200)) {
			if (node.path("id").asString().equals(id)) {
				return node;
			}
		}
		throw new AssertionError("no configuration " + id);
	}

	private JsonNode metatypes(String config) throws Exception {
		return ok(send("GET", METATYPES + "?configId=" + config, null, instance().viewer()), 200);
	}

	private static JsonNode byName(JsonNode list, String name) {
		for (JsonNode node : list) {
			if (node.path("name").asString().equals(name)) {
				return node;
			}
		}
		throw new AssertionError("no metatype " + name);
	}

	private HttpResponse<String> send(String method, String path, ObjectNode body, RepositoryInstance.Session session)
			throws Exception {
		return instance().send(method, path, (body != null) ? body.toString() : null, session);
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

	private static ObjectNode object() {
		return JsonMapper.shared().createObjectNode();
	}

	private static String id(JsonNode node) {
		return node.path("id").asString();
	}

	private static JsonNode ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return body(response);
	}

	private static JsonNode body(HttpResponse<String> response) {
		try {
			return JsonMapper.shared().readTree(response.body());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}

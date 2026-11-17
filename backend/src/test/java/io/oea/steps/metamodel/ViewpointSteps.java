package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
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
 * Steps for features/metamodel/viewpoints.feature (us-090-viewpoints D2, D3), on the instance of the
 * metamodel features. The architect (`enterprise-architect`) holds `viewpoint:manage`; the viewer
 * only `entity:read`. Names carry a run suffix, and the steps name them without it.
 */
public class ViewpointSteps {

	private static final String VIEWPOINTS = "/api/v1/admin/viewpoints";

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private final String suffix = " " + UUID.randomUUID().toString().substring(0, 8);

	private final Map<String, String> metatypes = new HashMap<>();

	private final Map<String, JsonNode> viewpoints = new HashMap<>();

	private HttpResponse<String> response;

	@Given("the instance has the metatypes {string} and {string} and the connection type {string}")
	public void metatypes(String first, String second, String connection) throws Exception {
		metatype(first, false);
		metatype(second, false);
		metatype(connection, true);
	}

	@Given("the viewpoint {string} allows {string}")
	public void viewpoint(String name, String metatype) throws Exception {
		createWith(name, List.of(metatype), List.of(), architect());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
		this.viewpoints.put(name, json());
	}

	@When("the architect creates the viewpoint {string} with {string} and {string} and the connection type {string}")
	public void create(String name, String first, String second, String connection) throws Exception {
		createWith(name, List.of(first, second), List.of(connection), architect());
	}

	@When("the architect creates a viewpoint {string} with the connection type {string} as metatype")
	public void createWithConnection(String name, String connection) throws Exception {
		createWith(name, List.of(connection), List.of(), architect());
	}

	@When("the viewer creates the viewpoint {string} with {string}")
	public void viewerCreates(String name, String metatype) throws Exception {
		createWith(name, List.of(metatype), List.of(), instance().viewer());
	}

	@When("the architect creates a viewpoint with a name of {int} characters")
	public void createWithLength(int length) throws Exception {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		ObjectNode body = body(prefix + "n".repeat(length - prefix.length()), List.of(this.metatypes.values().iterator().next()),
				List.of());
		this.response = instance().send("POST", VIEWPOINTS, body.toString(), architect());
	}

	@When("the architect changes the description of {string} with its version")
	public void changeDescription(String name) throws Exception {
		change(name, "\"" + this.viewpoints.get(name).path("version").asInt() + "\"");
	}

	@When("the architect changes the description of {string} with the version {int}")
	public void changeWithVersion(String name, int version) throws Exception {
		change(name, "\"" + version + "\"");
	}

	@When("the architect deletes {string} with its version")
	public void delete(String name) throws Exception {
		JsonNode viewpoint = this.viewpoints.get(name);
		this.response = instance().send("DELETE", VIEWPOINTS + "/" + viewpoint.path("id").asString(), null, architect(),
				Map.of("If-Match", "\"" + viewpoint.path("version").asInt() + "\""));
	}

	@When("the viewer lists the viewpoints")
	public void viewerLists() throws Exception {
		this.response = instance().oidc().get(VIEWPOINTS, instance().viewer().token());
	}

	@Then("the viewpoint answer is {int}")
	public void status(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the viewpoint answer is 422 naming {string}")
	public void invalid(String field) throws Exception {
		status(422);
		assertThat(json().path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
	}

	@Then("the viewpoint allows {string} and {string} and the connection type {string}")
	public void allows(String first, String second, String connection) throws Exception {
		status(201);
		JsonNode viewpoint = json();
		assertThat(ids(viewpoint.path("allowedMetatypes"))).containsExactlyInAnyOrder(this.metatypes.get(first),
				this.metatypes.get(second));
		assertThat(ids(viewpoint.path("allowedRelations"))).containsExactly(this.metatypes.get(connection));
		assertThat(viewpoint.path("version").asInt()).isOne();
	}

	@Then("the viewpoint has the version {int}")
	public void version(int version) throws Exception {
		status(200);
		assertThat(json().path("version").asInt()).isEqualTo(version);
	}

	@Then("the viewpoint list holds {string}")
	public void listed(String name) throws Exception {
		status(200);
		List<String> names = new ArrayList<>();
		json().forEach((viewpoint) -> names.add(viewpoint.path("name").asString()));
		assertThat(names).contains(name + this.suffix);
	}

	@Then("{string} is no longer listed")
	public void notListed(String name) throws Exception {
		HttpResponse<String> list = instance().oidc().get(VIEWPOINTS, architect().token());
		List<String> names = new ArrayList<>();
		JsonMapper.shared().readTree(list.body()).forEach((viewpoint) -> names.add(viewpoint.path("name").asString()));
		assertThat(names).doesNotContain(name + this.suffix);
	}

	private void change(String name, String ifMatch) throws Exception {
		JsonNode viewpoint = this.viewpoints.get(name);
		List<String> types = new ArrayList<>();
		viewpoint.path("allowedMetatypes").forEach((type) -> types.add(type.path("id").asString()));
		ObjectNode body = body(viewpoint.path("name").asString(), types, List.of()).put("description", "Changed");
		this.response = instance().send("PUT", VIEWPOINTS + "/" + viewpoint.path("id").asString(), body.toString(),
				architect(), Map.of("If-Match", ifMatch));
	}

	private void createWith(String name, List<String> metatypeNames, List<String> connectionNames, Session session)
			throws Exception {
		ObjectNode body = body(name + this.suffix, metatypeNames.stream().map(this.metatypes::get).toList(),
				connectionNames.stream().map(this.metatypes::get).toList());
		this.response = instance().send("POST", VIEWPOINTS, body.toString(), session);
	}

	private static ObjectNode body(String name, List<String> metatypeIds, List<String> connectionIds) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name);
		ArrayNode types = body.putArray("metatypeIds");
		metatypeIds.forEach(types::add);
		ArrayNode connections = body.putArray("connectionTypeIds");
		connectionIds.forEach(connections::add);
		return body;
	}

	private void metatype(String name, boolean connection) throws Exception {
		this.metatypes.put(name, instance().oidc().single("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('%s', '%s', %s) RETURNING id::text"""
			.formatted(INSTANCE, name + this.suffix, connection)));
	}

	private static List<String> ids(JsonNode types) {
		List<String> ids = new ArrayList<>();
		types.forEach((type) -> ids.add(type.path("id").asString()));
		return ids;
	}

	private JsonNode json() {
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static Session architect() throws Exception {
		return instance().architect();
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/metamodel/metatype-editing.feature (us-032-metatype-editing). All scenarios
 * share one instance; each scenario uses its own names.
 */
public class MetatypeEditingSteps {

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private static final String BATCH = "/api/v1/entities/batch";

	private static RepositoryInstance shared;

	private HttpResponse<String> response;

	private final Map<String, String> ids = new HashMap<>();

	@Given("a running repository instance with a metamodel administrator")
	public void instance() throws Exception {
		synchronized (MetatypeEditingSteps.class) {
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

	@When("the metamodel administrator creates the metatype {string} with the mandatory enum property {string}")
	public void createWithProperty(String metatype, String property) throws Exception {
		String type = createMetatype(metatype);
		String trust = createProperty(property, "enum");
		for (String value : new String[] { "low", "high" }) {
			ok(send("POST", "/api/v1/metamodel/property-definitions/" + trust + "/enum-values",
					object().put("value", value), shared.metamodelAdmin()), 201);
		}
		map(type, trust, "mandatory");
	}

	@When("the metamodel administrator creates the metatype {string}")
	public void create(String metatype) throws Exception {
		this.response = send("POST", "/api/v1/metamodel/metatypes", metatype(metatype), shared.metamodelAdmin());
	}

	@When("the architect creates the metatype {string}")
	public void createAsArchitect(String metatype) throws Exception {
		this.response = send("POST", "/api/v1/metamodel/metatypes", metatype(metatype), shared.architect());
	}

	@Given("the metatype {string} with the optional property {string} and {int} entities with a value")
	public void metatypeWithValues(String metatype, String property, int entities) throws Exception {
		String type = createMetatype(metatype);
		String cost = createProperty(property, "string");
		map(type, cost, "optional");
		ArrayNode items = JsonMapper.shared().createArrayNode();
		for (int i = 0; i < entities; i++) {
			items.add(entity(type, metatype + " " + i).put("externalId", metatype + "-" + i).set("properties",
					JsonMapper.shared().createArrayNode().add(object().put("propertyDefId", cost).put("value", "C" + i))));
		}
		write(items);
	}

	@Given("the metatype {string} with {int} entities")
	public void metatypeWithEntities(String metatype, int entities) throws Exception {
		String type = createMetatype(metatype);
		ArrayNode items = JsonMapper.shared().createArrayNode();
		for (int i = 0; i < entities; i++) {
			items.add(entity(type, metatype + " " + i).put("externalId", metatype + "-" + i));
		}
		write(items);
	}

	@When("the metamodel administrator removes {string} from {string}")
	public void remove(String property, String metatype) throws Exception {
		String type = this.ids.get(metatype);
		JsonNode mappings = body(send("GET", "/api/v1/metamodel/metatypes/" + type + "/property-mappings", null,
				shared.metamodelAdmin()));
		String mapping = null;
		for (JsonNode candidate : mappings) {
			if (candidate.path("propertyDefinition").path("id").asString().equals(this.ids.get(property))) {
				mapping = candidate.path("id").asString();
			}
		}
		ok(send("DELETE", "/api/v1/metamodel/metatypes/" + type + "/property-mappings/" + mapping, null,
				shared.metamodelAdmin()), 204);
	}

	@When("the metamodel administrator maps the mandatory property {string} to {string}")
	public void mapMandatory(String property, String metatype) throws Exception {
		String classification = createProperty(property, "string");
		this.response = map(this.ids.get(metatype), classification, "mandatory");
	}

	@Then("{string} is listed with scope {string}")
	public void listed(String metatype, String scope) throws Exception {
		JsonNode metatypes = body(send("GET", "/api/v1/metamodel/metatypes", null, shared.architect()));
		assertThat(metatypes).anySatisfy((type) -> {
			assertThat(type.path("name").asString()).isEqualTo(name(metatype));
			assertThat(type.path("scope").asString()).isEqualTo(scope);
		});
	}

	@Then("an architect can write a {string} with {string} {string}")
	public void write(String metatype, String property, String value) throws Exception {
		ObjectNode item = entity(this.ids.get(metatype), name(metatype) + " 1");
		item.putArray("properties").add(object().put("propertyDefId", this.ids.get(property)).put("value", value));
		JsonNode result = write(JsonMapper.shared().createArrayNode().add(item));
		assertThat(result.path("summary").path("created").asInt()).isOne();
	}

	@Then("the audit trail attributes the creation of {string} to the metamodel administrator")
	public void audited(String metatype) throws Exception {
		assertThat(shared.oidc().single("""
				SELECT p.email FROM audit.events e JOIN persons p ON p.id = e.actor_person_id
				WHERE e.event_type = 'metamodel.metatype-created' AND e.target_id = '%s'"""
			.formatted(this.ids.get(metatype)))).startsWith("dev-metamodel-admin");
	}

	@Then("the metamodel change is rejected with {int} and problem type {string}")
	public void rejectedWithType(int status, String type) {
		assertThat(this.response.statusCode()).isEqualTo(status);
		assertThat(body(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the metamodel change is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).isEqualTo(status);
	}

	@Then("no entity of {string} has a value for {string}")
	public void noValue(String metatype, String property) throws Exception {
		assertThat(shared.oidc().single("""
				SELECT count(*)::text FROM entity_property_values v JOIN entities e ON e.id = v.entity_id
				WHERE e.metatype_id = '%s' AND v.property_def_id = '%s'"""
			.formatted(this.ids.get(metatype), this.ids.get(property)))).isEqualTo("0");
	}

	@Then("the history of each of the {int} entities holds the removed value")
	public void history(int entities) throws Exception {
		assertThat(shared.oidc().single("""
				SELECT count(DISTINCT entity_id)::text FROM entity_property_value_history
				WHERE property_def_id = '%s' AND value_text IS NOT NULL AND entity_version = 2"""
			.formatted(this.ids.get("Cost Center")))).isEqualTo(Integer.toString(entities));
	}

	@Then("the response states {int} marked entities")
	public void marked(int entities) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
		assertThat(body(this.response).path("markedEntityCount").asInt()).isEqualTo(entities);
	}

	@Then("writing one of them again without {string} is rejected with {string}")
	public void writeAgain(String property, String code) throws Exception {
		String metatype = "Data Zone";
		ObjectNode item = entity(this.ids.get(metatype), name(metatype) + " 0").put("externalId", metatype + "-0")
			.put("description", "changed");
		JsonNode result = write(JsonMapper.shared().createArrayNode().add(item));
		assertThat(result.path("items").get(0).path("errors").get(0).path("code").asString()).isEqualTo(code);
	}

	private String createMetatype(String metatype) throws Exception {
		JsonNode created = ok(send("POST", "/api/v1/metamodel/metatypes", metatype(metatype), shared.metamodelAdmin()),
				201);
		this.ids.put(metatype, created.path("id").asString());
		return created.path("id").asString();
	}

	private String createProperty(String property, String dataType) throws Exception {
		JsonNode created = ok(send("POST", "/api/v1/metamodel/property-definitions",
				object().put("name", name(property)).put("dataType", dataType), shared.metamodelAdmin()), 201);
		this.ids.put(property, created.path("id").asString());
		return created.path("id").asString();
	}

	private HttpResponse<String> map(String metatype, String property, String necessity) throws Exception {
		HttpResponse<String> mapped = send("POST", "/api/v1/metamodel/metatypes/" + metatype + "/property-mappings",
				object().put("propertyDefId", property).put("necessity", necessity), shared.metamodelAdmin());
		ok(mapped, 201);
		return mapped;
	}

	private ObjectNode metatype(String metatype) {
		return object().put("name", name(metatype)).put("configId", CONFIG);
	}

	private ObjectNode entity(String metatype, String name) {
		return object().put("name", name).put("metatypeId", metatype);
	}

	private JsonNode write(ArrayNode items) throws Exception {
		ObjectNode request = object();
		request.set("entities", items);
		return ok(shared.post(BATCH, request.toString(), shared.architect(), Map.of()), 200);
	}

	/** Names are unique per scenario run except for the name of an existing metatype. */
	private String name(String name) {
		return "application component".equals(name) ? name : name + " " + Integer.toHexString(hashCode());
	}

	private static HttpResponse<String> send(String method, String path, ObjectNode body, Session session)
			throws Exception {
		return shared.send(method, path, (body != null) ? body.toString() : null, session);
	}

	private static JsonNode ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return body(response);
	}

	private static ObjectNode object() {
		return JsonMapper.shared().createObjectNode();
	}

	private static JsonNode body(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

}

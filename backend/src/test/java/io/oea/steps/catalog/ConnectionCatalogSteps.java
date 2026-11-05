package io.oea.steps.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/catalog/connection-catalogs.feature (us-080-connection-catalogs), on the
 * instance of the catalog features. Applications, information flows and data objects are written
 * through the batch API with a scenario suffix in their external IDs, so that repeated names of
 * earlier scenarios do not collide; a flow is addressed by its name.
 */
public class ConnectionCatalogSteps {

	private static final String CATALOGS = "/api/v1/catalogs";

	private static final String BATCH = "/api/v1/entities/batch";

	private final String suffix = UUID.randomUUID().toString().substring(0, 8);

	private HttpResponse<String> response;

	// repository

	@Given("the information flows {string} from {string} to {string} and {string} from {string} to {string}")
	public void flows(String first, String firstSource, String firstTarget, String second, String secondSource,
			String secondTarget) throws Exception {
		ObjectNode request = object();
		ArrayNode entities = request.putArray("entities");
		for (String application : List.of(firstSource, firstTarget, secondSource, secondTarget)) {
			if (!contains(entities, ref(application))) {
				entities.addObject()
					.put("name", application)
					.put("metatypeId", metatypeId("Application Component"))
					.put("externalId", ref(application));
			}
		}
		ArrayNode connections = request.putArray("connections");
		connections.add(connection(first, firstSource, firstTarget));
		connections.add(connection(second, secondSource, secondTarget));
		ok(instance().post(BATCH, request.toString(), instance().architect(), Map.of()), 200);
	}

	@Given("the information flow {string} carries the data objects {string} and {string}")
	public void carries(String flow, String first, String second) throws Exception {
		ObjectNode request = object();
		ArrayNode entities = request.putArray("entities");
		ArrayNode connections = request.putArray("connections");
		for (String object : List.of(first, second)) {
			entities.addObject()
				.put("name", object)
				.put("metatypeId", metatypeId("Data Object"))
				.put("externalId", ref(object));
			connections.addObject()
				.put("connectionMetatypeId", metatypeId("Carries Data"))
				.put("sourceExternalId", ref(flow))
				.put("targetExternalId", ref(object));
		}
		ok(instance().post(BATCH, request.toString(), instance().architect(), Map.of()), 200);
	}

	// configuring and querying

	@When("the enterprise architect configures the source name as column of {string}")
	public void sourceNameColumn(String catalog) throws Exception {
		ObjectNode body = object();
		body.putArray("columns").addObject().put("endpoint", "source").put("field", "name");
		this.response = instance().send("PUT", CATALOGS + "/" + catalogId(catalog) + "/columns", body.toString(),
				instance().architect());
	}

	@When("the viewer sorts the catalog {string} by {string} ascending")
	public void sorted(String catalog, String key) throws Exception {
		query(catalog, "?sort=" + key + ":asc");
	}

	// outcomes

	@Then("the viewer sees the flow {string} from {string} to {string} in the catalog {string}")
	public void seesFlow(String flow, String source, String target, String catalog) throws Exception {
		query(catalog, "");
		JsonNode row = rowOf(flow);
		assertThat(row.path("values").path("source.name").asString()).isEqualTo(source);
		assertThat(row.path("values").path("target.name").asString()).isEqualTo(target);
	}

	@Then("the viewer sees the columns {string}, {string} and {string} with the keys {string}, {string} and {string} in the catalog {string}")
	public void seesColumns(String first, String second, String third, String firstKey, String secondKey,
			String thirdKey, String catalog) throws Exception {
		query(catalog, "");
		List<String> labels = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		result().path("columns").forEach((column) -> {
			labels.add(column.path("label").asString());
			keys.add(column.path("key").asString());
		});
		assertThat(labels).containsExactly(first, second, third);
		assertThat(keys).containsExactly(firstKey, secondKey, thirdKey);
	}

	@Then("the flow {string} comes before {string}")
	public void before(String first, String second) throws Exception {
		List<String> names = new ArrayList<>();
		result().path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		assertThat(names).contains(first, second);
		assertThat(names.indexOf(first)).isLessThan(names.indexOf(second));
	}

	@Then("the viewer sees the flow {string} in one row of {string} with the data objects {string} and {string}")
	public void seesDataObjects(String flow, String catalog, String first, String second) throws Exception {
		query(catalog, "");
		JsonNode result = result();
		String join = result.path("joins").get(0).path("id").asString();
		List<String> objects = new ArrayList<>();
		rowOf(flow).path("joinResults").path(join).forEach((r) -> objects.add(r.path("values").path("name").asString()));
		assertThat(objects).containsExactly(first, second);
	}

	@Then("the endpoint column is rejected with 422 naming {string}")
	public void rejected(String field) throws Exception {
		JsonNode problem = ok(this.response, 422);
		List<String> fields = new ArrayList<>();
		problem.path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	// helpers

	private ObjectNode connection(String name, String source, String target) throws Exception {
		return object().put("connectionMetatypeId", metatypeId("Information Flow"))
			.put("name", name)
			.put("externalId", ref(name))
			.put("sourceExternalId", ref(source))
			.put("targetExternalId", ref(target));
	}

	private String ref(String name) {
		return "s" + this.suffix + ":" + name.replace(' ', '-');
	}

	private static boolean contains(ArrayNode entities, String externalId) {
		for (JsonNode entity : entities) {
			if (externalId.equals(entity.path("externalId").asString())) {
				return true;
			}
		}
		return false;
	}

	private void query(String catalog, String parameters) throws Exception {
		this.response = instance().oidc()
			.get(CATALOGS + "/" + catalogId(catalog) + "/query" + parameters, instance().viewer().token());
	}

	private JsonNode result() throws Exception {
		return ok(this.response, 200);
	}

	private JsonNode rowOf(String flow) throws Exception {
		List<JsonNode> rows = new ArrayList<>();
		result().path("rows").forEach((row) -> {
			if (flow.equals(row.path("values").path("name").asString())) {
				rows.add(row);
			}
		});
		assertThat(rows).hasSize(1);
		return rows.get(0);
	}

	private static String catalogId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM catalogs WHERE name = '" + name.replace("'", "''") + "'");
	}

	private static String metatypeId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name.replace("'", "''") + "'");
	}

	private static ObjectNode object() {
		return JsonMapper.shared().createObjectNode();
	}

	private static JsonNode ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return JsonMapper.shared().readTree(response.body());
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

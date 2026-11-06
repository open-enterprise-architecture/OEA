package io.oea.steps.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
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
 * Steps for features/catalog/catalog-views.feature and "Remove a join used by a view" of
 * catalog-joins.feature (us-053-saved-views), on the instance of {@link CatalogSteps}. Catalogs,
 * joins and views are addressed by their names.
 */
public class CatalogViewSteps {

	private static final String CATALOGS = "/api/v1/catalogs";

	private HttpResponse<String> response;

	// saving views

	@When("the {role} saves the view {string} with the columns {string} and {string} and the filter {string} in {string}")
	public void save(Session session, String name, String first, String second, String filter, String catalog)
			throws Exception {
		ObjectNode body = view(name, false, first, second);
		String[] parts = filter.split(":", 3);
		ObjectNode expression = body.putArray("filters").addObject().put("attributePath", parts[0]).put("operator", parts[1]);
		if (parts.length > 2) {
			expression.put("value", parts[2]);
		}
		this.response = instance().post(CATALOGS + "/" + catalogId(catalog) + "/views", body.toString(), session, Map.of());
	}

	@Given("the enterprise architect saves the default view {string} in {string}")
	public void saveDefault(String name, String catalog) throws Exception {
		this.response = instance().post(CATALOGS + "/" + catalogId(catalog) + "/views",
				view(name, true, "name").toString(), instance().architect(), Map.of());
		ok(this.response, 201);
	}

	@Given("the enterprise architect saves the view {string} with the join {string} in rows mode sorted by its name in {string}")
	public void saveWithJoin(String name, String join, String catalog) throws Exception {
		String joinId = joinId(catalog, join);
		ObjectNode body = view(name, false, "name");
		body.putArray("joinModeOverrides").addObject().put("joinDefinitionId", joinId).put("joinMode", "rows");
		body.putObject("sort").put("column", joinId + ".name").put("direction", "asc");
		ok(instance().post(CATALOGS + "/" + catalogId(catalog) + "/views", body.toString(), instance().architect(),
				Map.of()), 201);
	}

	@When("the enterprise architect removes the view {string} from {string}")
	public void remove(String name, String catalog) throws Exception {
		String id = instance().oidc()
			.single("SELECT v ->> 'id' FROM catalogs, jsonb_array_elements(config -> 'savedViews') v WHERE name = '"
					+ quoted(catalog) + "' AND v ->> 'name' = '" + quoted(name) + "'");
		ok(instance().send("DELETE", CATALOGS + "/" + catalogId(catalog) + "/views/" + id, null, instance().architect()),
				200);
	}

	@When("the enterprise architect configures only the name column of {string}")
	public void onlyName(String catalog) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("columns").addObject().put("field", "name");
		ok(instance().send("PUT", CATALOGS + "/" + catalogId(catalog) + "/columns", body.toString(),
				instance().architect()), 200);
	}

	// querying

	@When("the viewer queries the catalog {string} with the view {string}")
	public void queryView(String catalog, String name) throws Exception {
		query(catalog, viewOf(catalog, name).path("id").asString());
	}

	@When("the viewer queries the catalog {string} with an unknown view")
	public void queryUnknown(String catalog) throws Exception {
		query(catalog, UUID.randomUUID().toString());
	}

	// outcomes

	@Then("the viewer sees the view {string} with the columns {string} and {string} and the filter {string} in {string}")
	public void seesView(String name, String first, String second, String filter, String catalog) throws Exception {
		ok(this.response, 201);
		JsonNode view = viewOf(catalog, name);
		assertThat(strings(view.path("columnOrder"))).containsExactly(first, second);
		JsonNode expression = view.path("filters").get(0);
		assertThat(expression.path("attributePath").asString() + ":" + expression.path("operator").asString() + ":"
				+ expression.path("value").asString()).isEqualTo(filter);
		assertThat(view.path("available").asBoolean()).isTrue();
	}

	@Then("the view query shows the columns {string} and {string}")
	public void columns(String first, String second) throws Exception {
		List<String> keys = new ArrayList<>();
		ok(this.response, 200).path("columns").forEach((column) -> keys.add(column.path("key").asString()));
		assertThat(keys).containsExactly(first, second);
	}

	@Then("the view is rejected with 422 naming {string}")
	public void rejected(String field) throws Exception {
		assertThat(strings(ok(this.response, 422).path("invalidFields"))).containsExactly(field);
	}

	@Then("the view is rejected with {int}")
	public void rejectedWith(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the view query is rejected with 400 naming {string}")
	public void badQuery(String field) throws Exception {
		assertThat(strings(ok(this.response, 400).path("invalidFields"))).containsExactly(field);
	}

	@Then("the default view of {string} is {string}")
	public void defaultView(String catalog, String name) throws Exception {
		assertThat(defaults(catalog)).containsExactly(name);
	}

	@Then("the catalog {string} has no default view")
	public void noDefault(String catalog) throws Exception {
		assertThat(defaults(catalog)).isEmpty();
	}

	@Then("the view {string} of {string} is not available")
	public void unavailable(String name, String catalog) throws Exception {
		assertThat(viewOf(catalog, name).path("available").asBoolean()).isFalse();
	}

	@Then("the view {string} of {string} has neither a join mode nor a sort")
	public void cleaned(String name, String catalog) throws Exception {
		JsonNode view = viewOf(catalog, name);
		assertThat(view.path("joinModeOverrides")).isEmpty();
		assertThat(view.path("sort").isNull() || view.path("sort").isMissingNode()).isTrue();
	}

	// helpers

	private static ObjectNode view(String name, boolean isDefault, String... columns) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("isDefault", isDefault);
		ArrayNode list = body.putArray("columnOrder");
		for (String column : columns) {
			list.add(column);
		}
		return body;
	}

	private void query(String catalog, String view) throws Exception {
		this.response = instance().oidc()
			.get(CATALOGS + "/" + catalogId(catalog) + "/query?view=" + view, instance().viewer().token());
	}

	private static JsonNode viewOf(String catalog, String name) throws Exception {
		for (JsonNode view : catalog(catalog).path("savedViews")) {
			if (name.equals(view.path("name").asString())) {
				return view;
			}
		}
		throw new AssertionError("no view " + name);
	}

	private static List<String> defaults(String catalog) throws Exception {
		List<String> names = new ArrayList<>();
		catalog(catalog).path("savedViews").forEach((view) -> {
			if (view.path("isDefault").asBoolean()) {
				names.add(view.path("name").asString());
			}
		});
		return names;
	}

	private static JsonNode catalog(String name) throws Exception {
		return ok(instance().oidc().get(CATALOGS + "/" + catalogId(name), instance().viewer().token()), 200);
	}

	private static String joinId(String catalog, String join) throws Exception {
		return instance().oidc()
			.single("SELECT j ->> 'id' FROM catalogs, jsonb_array_elements(config -> 'joinDefinitions') j WHERE name = '"
					+ quoted(catalog) + "' AND j ->> 'name' = '" + quoted(join) + "'");
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach((value) -> values.add(value.asString()));
		return values;
	}

	private static String catalogId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM catalogs WHERE name = '" + quoted(name) + "'");
	}

	private static String quoted(String text) {
		return text.replace("'", "''");
	}

	private static JsonNode ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return JsonMapper.shared().readTree(response.body());
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

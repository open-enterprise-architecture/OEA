package io.oea.steps.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/catalog/catalog-filters.feature and "Filtered total" of
 * catalog-query.feature (us-051-catalog-filters), on the instance of {@link CatalogSteps}.
 * Information flows come from {@link ConnectionCatalogSteps}, whose external IDs the data
 * objects connect to; the property "Classification" of data objects is created once for the
 * instance.
 */
public class CatalogFilterSteps {

	private static final String CATALOGS = "/api/v1/catalogs";

	private static final String BATCH = "/api/v1/entities/batch";

	private final ConnectionCatalogSteps connections;

	private HttpResponse<String> response;

	public CatalogFilterSteps(ConnectionCatalogSteps connections) {
		this.connections = connections;
	}

	// repository

	@Given("{int} applications with the prefix {string} exist, {int} of them with the status {string}")
	public void applications(int count, String prefix, int withStatus, String status) throws Exception {
		String statusProperty = instance().oidc().single("SELECT id::text FROM property_definitions WHERE name = 'Status'");
		ObjectNode request = object();
		ArrayNode entities = request.putArray("entities");
		for (int i = 1; i <= count; i++) {
			ObjectNode entity = entities.addObject()
				.put("name", "%s %03d".formatted(prefix, i))
				.put("metatypeId", metatypeId("Application Component"));
			if (i <= withStatus) {
				entity.putArray("properties").addObject().put("propertyDefId", statusProperty).put("value", status);
			}
		}
		ok(instance().post(BATCH, request.toString(), instance().architect(), Map.of()), 200);
	}

	@Given("data objects carry a classification")
	public void classification() throws Exception {
		instance().oidc().update("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('Classification', 'classification', 'enum')
				ON CONFLICT DO NOTHING""");
		String property = classificationId();
		instance().oidc().update("""
				INSERT INTO property_enum_values (property_def_id, value, sort_order)
				SELECT '%1$s', v.value, v.sort_order FROM (VALUES ('PII', 0), ('Internal', 1), ('Public', 2)) v (value, sort_order)
				ON CONFLICT DO NOTHING""".formatted(property));
		instance().oidc().update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				SELECT m.id, '%s', 'optional', DATE '2026-01-01', 'organization' FROM metatypes m
				WHERE m.name = 'Data Object' AND NOT EXISTS (SELECT 1 FROM metatype_property_mappings x
				                                             WHERE x.metatype_id = m.id AND x.property_def_id = '%1$s')"""
			.formatted(property));
	}

	@Given("the information flow {string} carries {string} classified {string} and {string} classified {string}")
	public void carriesTwo(String flow, String first, String firstClass, String second, String secondClass)
			throws Exception {
		carry(flow, List.of(first, second), List.of(firstClass, secondClass));
	}

	@Given("the information flow {string} carries {string} classified {string}")
	public void carriesOne(String flow, String object, String classification) throws Exception {
		carry(flow, List.of(object), List.of(classification));
	}

	@Given("the enterprise architect adds the join {string} with name and classification via {string} outbound to {string} to {string}")
	public void join(String name, String connection, String target, String catalog) throws Exception {
		ObjectNode body = object().put("name", name)
			.put("connectionMetatypeId", metatypeId(connection))
			.put("joinDirection", "outbound")
			.put("targetMetatypeId", metatypeId(target));
		ArrayNode columns = body.putArray("targetColumns");
		columns.addObject().put("field", "name");
		columns.addObject().put("propertyDefId", classificationId());
		ok(instance().post(CATALOGS + "/" + catalogId(catalog) + "/joins", body.toString(), instance().architect(),
				Map.of()), 201);
	}

	// filtering

	@When("the viewer filters the catalog {string} by {string}")
	public void filter(String catalog, String filter) throws Exception {
		query(catalog, List.of(filter), null);
	}

	@When("the viewer filters the catalog {string} by {string} and {string}")
	public void filterBoth(String catalog, String first, String second) throws Exception {
		query(catalog, List.of(first, second), "and");
	}

	@When("the viewer filters the catalog {string} by {string} or {string}")
	public void filterEither(String catalog, String first, String second) throws Exception {
		query(catalog, List.of(first, second), "or");
	}

	@When("the viewer queries the catalog {string} without filters")
	public void unfiltered(String catalog) throws Exception {
		query(catalog, List.of(), null);
	}

	@When("the viewer filters the catalog {string} by {string} classification {string}")
	public void filterJoin(String catalog, String join, String classification) throws Exception {
		String joinId = instance().oidc()
			.single("SELECT j ->> 'id' FROM catalogs, jsonb_array_elements(config -> 'joinDefinitions') j WHERE name = '"
					+ catalog.replace("'", "''") + "' AND j ->> 'name' = '" + join.replace("'", "''") + "'");
		query(catalog, List.of(joinId + ".classification:eq:" + classification), null);
	}

	// saved filters

	@When("the {role} saves the filter {string} with {string} in {string}")
	public void save(Session session, String name, String filter, String catalog) throws Exception {
		String[] parts = filter.split(":", 3);
		ObjectNode body = object().put("name", name).put("logicalOperator", "and");
		ObjectNode expression = body.putArray("expressions").addObject()
			.put("attributePath", parts[0])
			.put("operator", parts[1]);
		if (parts.length > 2) {
			expression.put("value", parts[2]);
		}
		this.response = instance().post(CATALOGS + "/" + catalogId(catalog) + "/filters", body.toString(), session,
				Map.of());
	}

	@When("the enterprise architect removes the saved filter {string} from {string}")
	public void remove(String name, String catalog) throws Exception {
		String id = instance().oidc()
			.single("SELECT f ->> 'id' FROM catalogs, jsonb_array_elements(config -> 'savedFilters') f WHERE name = '"
					+ catalog.replace("'", "''") + "' AND f ->> 'name' = '" + name.replace("'", "''") + "'");
		this.response = instance().send("DELETE", CATALOGS + "/" + catalogId(catalog) + "/filters/" + id, null,
				instance().architect());
		ok(this.response, 200);
	}

	// outcomes

	@Then("the filtered catalog shows {int} rows and a total of {int} entries")
	public void rows(int rows, int total) throws Exception {
		JsonNode result = ok(this.response, 200);
		assertThat(result.path("rows")).hasSize(rows);
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(total);
	}

	@Then("the filtered catalog shows {int} rows and a total of {int} entries on {int} page")
	public void rowsOnPages(int rows, int total, int pages) throws Exception {
		rows(rows, total);
		assertThat(ok(this.response, 200).path("page").path("totalPages").asInt()).isEqualTo(pages);
	}

	@Then("the filtered catalog shows only {string}")
	public void only(String name) throws Exception {
		assertThat(names(ok(this.response, 200))).containsExactly(name);
	}

	@Then("the filtered catalog shows only {string} with the data object {string}")
	public void onlyWith(String name, String object) throws Exception {
		JsonNode result = ok(this.response, 200);
		assertThat(names(result)).containsExactly(name);
		String join = result.path("joins").get(0).path("id").asString();
		List<String> objects = new ArrayList<>();
		result.path("rows").get(0).path("joinResults").path(join)
			.forEach((r) -> objects.add(r.path("values").path("name").asString()));
		assertThat(objects).containsExactly(object);
	}

	@Then("the filter is rejected with 400 naming {string}")
	public void badFilter(String field) throws Exception {
		assertThat(fields(ok(this.response, 400))).containsExactly(field);
	}

	@Then("the viewer sees the saved filter {string} with {string} {string} {string} in {string}")
	public void seesSaved(String name, String attribute, String operator, String value, String catalog)
			throws Exception {
		JsonNode filter = savedFilters(catalog).get(0);
		assertThat(filter.path("name").asString()).isEqualTo(name);
		assertThat(filter.path("available").asBoolean()).isTrue();
		JsonNode expression = filter.path("expressions").get(0);
		assertThat(expression.path("attributePath").asString()).isEqualTo(attribute);
		assertThat(expression.path("operator").asString()).isEqualTo(operator);
		assertThat(expression.path("value").asString()).isEqualTo(value);
	}

	@Then("the saved filter is rejected with 422 naming {string}")
	public void rejected(String field) throws Exception {
		assertThat(fields(ok(this.response, 422))).containsExactly(field);
	}

	@Then("the saved filter is rejected with {int}")
	public void rejectedWith(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the catalog {string} has no saved filters")
	public void noSavedFilters(String catalog) throws Exception {
		assertThat(ok(this.response, 200).path("savedFilters")).isEmpty();
		assertThat(savedFilters(catalog)).isEmpty();
	}

	@Then("the audit trail names the viewer for the saved filter in {string}")
	public void audited(String catalog) throws Exception {
		ok(this.response, 201);
		String actor = instance().oidc().single("""
				SELECT p.name FROM audit.events a JOIN persons p ON p.id = a.actor_person_id
				WHERE a.event_type = 'catalog.filter-added' AND a.target_id = '%s'""".formatted(catalogId(catalog)));
		String viewer = instance().oidc().single("""
				SELECT p.name FROM catalogs c JOIN persons p ON p.id = c.created_by WHERE c.id = '%s'"""
			.formatted(catalogId(catalog)));
		assertThat(actor).isEqualTo(viewer);
	}

	// helpers

	private void carry(String flow, List<String> objects, List<String> classifications) throws Exception {
		ObjectNode request = object();
		ArrayNode entities = request.putArray("entities");
		ArrayNode links = request.putArray("connections");
		for (int i = 0; i < objects.size(); i++) {
			ObjectNode entity = entities.addObject()
				.put("name", objects.get(i))
				.put("metatypeId", metatypeId("Data Object"))
				.put("externalId", this.connections.ref(objects.get(i)));
			entity.putArray("properties").addObject().put("propertyDefId", classificationId())
				.put("value", classifications.get(i));
			links.addObject()
				.put("connectionMetatypeId", metatypeId("Carries Data"))
				.put("sourceExternalId", this.connections.ref(flow))
				.put("targetExternalId", this.connections.ref(objects.get(i)));
		}
		ok(instance().post(BATCH, request.toString(), instance().architect(), Map.of()), 200);
	}

	private void query(String catalog, List<String> filters, String mode) throws Exception {
		String parameters = Stream
			.concat(filters.stream().map((filter) -> "filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8)),
					Stream.ofNullable(mode).map((value) -> "filterMode=" + value))
			.collect(Collectors.joining("&"));
		this.response = instance().oidc()
			.get(CATALOGS + "/" + catalogId(catalog) + "/query" + (parameters.isEmpty() ? "" : "?" + parameters),
					instance().viewer().token());
	}

	private JsonNode savedFilters(String catalog) throws Exception {
		return ok(instance().oidc().get(CATALOGS + "/" + catalogId(catalog), instance().viewer().token()), 200)
			.path("savedFilters");
	}

	private static List<String> names(JsonNode result) {
		List<String> names = new ArrayList<>();
		result.path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		return names;
	}

	private static List<String> fields(JsonNode problem) {
		List<String> fields = new ArrayList<>();
		problem.path("invalidFields").forEach((node) -> fields.add(node.asString()));
		return fields;
	}

	private static String classificationId() throws Exception {
		return instance().oidc().single("SELECT id::text FROM property_definitions WHERE name = 'Classification'");
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

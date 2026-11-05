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
 * Steps for features/catalog/catalog-columns.feature and the configured columns of
 * features/catalog/catalog-query.feature (us-047-configure-columns), on the instance of the
 * catalog features. A column named "Name" or "Description" is the system field, any other name
 * a property of that name.
 */
public class CatalogColumnSteps {

	private static final String CATALOGS = "/api/v1/catalogs";

	private static final String BATCH = "/api/v1/entities/batch";

	private static final List<String> FIELDS = List.of("name", "description", "externalId", "createdAt", "updatedAt",
			"entityVersion");

	private HttpResponse<String> response;

	// configuring

	@Given("{string} maps the properties {string} and {string}")
	public void maps(String metatype, String first, String second) throws Exception {
		for (String property : List.of(first, second)) {
			String id = propertyId(property);
			if (id == null) {
				id = instance().oidc().single("""
						INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('%s', '%s', 'string')
						RETURNING id::text""".formatted(property, "p_" + UUID.randomUUID().toString().replace("-", "")
					.substring(0, 12)));
			}
			instance().oidc().update("""
					INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
					SELECT '%s', '%s', 'optional', DATE '2026-01-01', 'organization'
					WHERE NOT EXISTS (SELECT 1 FROM metatype_property_mappings WHERE metatype_id = '%s'
					                  AND property_def_id = '%s')""".formatted(metatypeId(metatype), id,
					metatypeId(metatype), id));
		}
	}

	@When("the enterprise architect configures every attribute as a column of {string}")
	public void everyAttribute(String catalog) throws Exception {
		List<ObjectNode> columns = new ArrayList<>();
		FIELDS.forEach((field) -> columns.add(object().put("field", field)));
		JsonNode metatype = ok(instance().send("GET", "/api/v1/metamodel/metatypes/" + metatypeId("Application Component"),
				null, instance().architect()), 200);
		List<String> seen = new ArrayList<>();
		for (JsonNode mapping : metatype.path("propertyMappings")) {
			String id = mapping.path("propertyDefinition").path("id").asString();
			if (!seen.contains(id) && "public".equals(instance().oidc()
				.single("SELECT visibility_mode FROM property_definitions WHERE id = '" + id + "'"))) {
				seen.add(id);
				columns.add(object().put("propertyDefId", id));
			}
		}
		configure(instance().architect(), catalog, columns);
		ok(this.response, 200);
	}

	@When("the enterprise architect configures a property that {string} does not map as column of {string}")
	public void foreignProperty(String metatype, String catalog) throws Exception {
		String id = instance().oidc().single("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('%s', '%s', 'string')
				RETURNING id::text""".formatted("Unmapped " + UUID.randomUUID().toString().substring(0, 8),
				"p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
		configure(instance().architect(), catalog, List.of(object().put("propertyDefId", id)));
	}

	@When("the {role} configures the columns {string} and {string} of {string}")
	public void configureTwo(Session session, String first, String second, String catalog) throws Exception {
		configure(session, catalog, List.of(column(first), column(second)));
	}

	@When("the enterprise architect configures the columns {string} labelled {string} and {string} of {string}")
	public void configureLabelled(String first, String label, String second, String catalog) throws Exception {
		configure(instance().architect(), catalog, List.of(column(first).put("displayLabel", label), column(second)));
		ok(this.response, 200);
	}

	@When("the enterprise architect configures the columns {string} labelled {string}, {string} and a hidden {string} of {string}")
	public void configureLabelledAndHidden(String first, String label, String second, String hidden, String catalog)
			throws Exception {
		configure(instance().architect(), catalog, List.of(column(first).put("displayLabel", label), column(second),
				column(hidden).put("visible", false)));
		ok(this.response, 200);
	}

	@When("the enterprise architect configures the columns {string} and a hidden {string} of {string}")
	public void configureHidden(String first, String hidden, String catalog) throws Exception {
		configure(instance().architect(), catalog, List.of(column(first), column(hidden).put("visible", false)));
		ok(this.response, 200);
	}

	@When("the enterprise architect configures only a hidden {string} column of {string}")
	public void configureOnlyHidden(String hidden, String catalog) throws Exception {
		configure(instance().architect(), catalog, List.of(column(hidden).put("visible", false)));
	}

	@When("the enterprise architect configures the columns {string} and {string} sorted descending of {string}")
	public void configureSorted(String first, String sorted, String catalog) throws Exception {
		configure(instance().architect(), catalog,
				List.of(column(first), column(sorted).put("defaultSortDirection", "desc")));
		ok(this.response, 200);
	}

	@When("the enterprise architect configures two columns with a default sort of {string}")
	public void configureTwoSorts(String catalog) throws Exception {
		configure(instance().architect(), catalog, List.of(column("Name").put("defaultSortDirection", "asc"),
				column("Status").put("defaultSortDirection", "desc")));
	}

	@When("the enterprise architect configures the columns {string} not sortable and {string} of {string}")
	public void configureNotSortable(String first, String second, String catalog) throws Exception {
		configure(instance().architect(), catalog, List.of(column(first).put("sortable", false), column(second)));
		ok(this.response, 200);
	}

	// outcomes

	@Then("the columns of {string} name the system fields and the properties {string} and {string}")
	public void allAttributes(String catalog, String first, String second) throws Exception {
		List<String> attributes = new ArrayList<>();
		for (JsonNode column : catalog(catalog).path("columns")) {
			attributes.add(column.hasNonNull("field") ? column.path("field").asString() : column.path("propertyDefId").asString());
		}
		assertThat(attributes).containsAll(FIELDS).contains(propertyId(first), propertyId(second));
	}

	@Then("the column change is rejected with 422 naming {string}")
	public void rejected422(String field) throws Exception {
		JsonNode problem = ok(this.response, 422);
		List<String> fields = new ArrayList<>();
		problem.path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	@Then("the column change is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the viewer sees the columns {string} and {string} in the catalog {string}")
	public void seesColumns(String first, String second, String catalog) throws Exception {
		assertThat(labels(query(catalog, ""))).containsExactly(first, second);
	}

	@Then("the viewer sees only the column {string} in the catalog {string}")
	public void seesOnly(String column, String catalog) throws Exception {
		assertThat(labels(query(catalog, ""))).containsExactly(column);
	}

	@Then("the first column of {string} still refers to the property {string}")
	public void refersTo(String catalog, String property) throws Exception {
		assertThat(catalog(catalog).path("columns").get(0).path("propertyDefId").asString())
			.isEqualTo(propertyId(property));
	}

	@Then("the configuration of {string} still lists the hidden {string}")
	public void listsHidden(String catalog, String hidden) throws Exception {
		JsonNode column = catalog(catalog).path("columns").get(1);
		assertThat(column.path("field").asString()).isEqualTo(hidden.toLowerCase());
		assertThat(column.path("visible").asBoolean()).isFalse();
	}

	@Then("the viewer sees the rows {string} and {string} in this order in the catalog {string}")
	public void seesRows(String first, String second, String catalog) throws Exception {
		List<String> names = new ArrayList<>();
		query(catalog, "").path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		assertThat(names).containsExactly(first, second);
	}

	@Then("the catalog {string} has no configured columns")
	public void noColumns(String catalog) throws Exception {
		assertThat(catalog(catalog).path("columns")).isEmpty();
	}

	@Then("the columns of {string} are saved and the audit trail names the viewer")
	public void savedByViewer(String catalog) throws Exception {
		ok(this.response, 200);
		assertThat(catalog(catalog, instance().viewer()).path("columns")).hasSize(2);
		assertThat(instance().oidc().single("""
				SELECT p.email FROM audit.events e JOIN persons p ON p.id = e.actor_person_id
				WHERE e.event_type = 'catalog.columns-changed' AND e.target_id = '%s'""".formatted(catalogId(catalog))))
			.startsWith("dev-viewer");
	}

	// entities with different statuses for the default sort and the enum order

	@Given("the applications {string} with the status {string} and {string} with the status {string} exist")
	public void twoApplications(String first, String firstStatus, String second, String secondStatus) throws Exception {
		write(Map.of(first, firstStatus, second, secondStatus));
	}

	@Given("the applications {string} with the status {string}, {string} with the status {string} and {string} with the status {string} exist")
	public void threeApplications(String first, String firstStatus, String second, String secondStatus, String third,
			String thirdStatus) throws Exception {
		write(Map.of(first, firstStatus, second, secondStatus, third, thirdStatus));
	}

	@Given("the application {string} exists without a status")
	public void withoutStatus(String name) throws Exception {
		write(Map.of(name, ""));
	}

	@Then("the catalog shows the rows {string}, {string}, {string} and {string} in this order")
	public void fourRows(String first, String second, String third, String fourth) throws Exception {
		List<String> names = new ArrayList<>();
		CatalogQuerySteps.lastResult().path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		assertThat(names).containsExactly(first, second, third, fourth);
	}

	// helpers

	private void write(Map<String, String> applications) throws Exception {
		ObjectNode request = object();
		ArrayNode entities = request.putArray("entities");
		String status = propertyId("Status");
		applications.forEach((name, value) -> {
			ObjectNode entity = entities.addObject().put("name", name).put("metatypeId", metatypeIdUnchecked());
			if (!value.isEmpty()) {
				entity.putArray("properties").addObject().put("propertyDefId", status).put("value", value);
			}
		});
		ok(instance().post(BATCH, request.toString(), instance().architect(), Map.of()), 200);
	}

	private void configure(Session session, String catalog, List<ObjectNode> columns) throws Exception {
		ObjectNode body = object();
		ArrayNode list = body.putArray("columns");
		columns.forEach(list::add);
		this.response = instance().send("PUT", CATALOGS + "/" + catalogId(catalog) + "/columns", body.toString(), session);
	}

	private ObjectNode column(String name) throws Exception {
		return switch (name) {
			case "Name" -> object().put("field", "name");
			case "Description" -> object().put("field", "description");
			default -> object().put("propertyDefId", propertyId(name));
		};
	}

	private JsonNode catalog(String name) throws Exception {
		return catalog(name, instance().architect());
	}

	private JsonNode catalog(String name, Session session) throws Exception {
		return ok(instance().send("GET", CATALOGS + "/" + catalogId(name), null, session), 200);
	}

	private JsonNode query(String catalog, String parameters) throws Exception {
		return ok(instance().send("GET", CATALOGS + "/" + catalogId(catalog) + "/query" + parameters, null,
				instance().viewer()), 200);
	}

	private static List<String> labels(JsonNode result) {
		List<String> labels = new ArrayList<>();
		result.path("columns").forEach((column) -> labels.add(column.path("label").asString()));
		return labels;
	}

	private static String catalogId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM catalogs WHERE name = '" + name.replace("'", "''") + "'");
	}

	private static String propertyId(String name) throws Exception {
		List<String> ids = instance().oidc()
			.column("SELECT id::text FROM property_definitions WHERE name = '" + name.replace("'", "''") + "'");
		return ids.isEmpty() ? null : ids.get(0);
	}

	private static String metatypeId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name.replace("'", "''") + "'");
	}

	private static String metatypeIdUnchecked() {
		try {
			return metatypeId("Application Component");
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
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

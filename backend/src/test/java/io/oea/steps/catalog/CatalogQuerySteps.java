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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/catalog/catalog-query.feature on the instance of {@link CatalogSteps}.
 * Applications are written through the batch API; each scenario starts without active
 * applications, earlier ones are soft-deleted.
 */
public class CatalogQuerySteps {

	private static final String BATCH = "/api/v1/entities/batch";

	private HttpResponse<String> response;

	@Given("the repository contains no applications")
	public void noApplications() throws Exception {
		instance().oidc().update("""
				UPDATE entities SET deleted_at = now(), deleted_by = created_by
				WHERE deleted_at IS NULL
				  AND metatype_id = (SELECT id FROM metatypes WHERE name = 'Application Component')""");
	}

	@Given("the applications {string}, {string} and {string} exist with the status {string}")
	public void applications(String first, String second, String third, String status) throws Exception {
		write(List.of(first, second, third), status);
	}

	@Given("{int} applications with the prefix {string} exist")
	public void manyApplications(int count, String prefix) throws Exception {
		List<String> names = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			names.add("%s %03d".formatted(prefix, i));
		}
		write(names, null);
	}

	@Given("the application {string} is soft-deleted")
	public void softDeleted(String name) throws Exception {
		instance().oidc().update("UPDATE entities SET deleted_at = now(), deleted_by = created_by WHERE name = '"
				+ name.replace("'", "''") + "' AND deleted_at IS NULL");
	}

	@When("the {role} queries the catalog {string}")
	public void query(Session session, String catalog) throws Exception {
		query(session, catalog, "");
	}

	@When("the {role} queries the catalog {string} sorted by {string}")
	public void querySorted(Session session, String catalog, String sort) throws Exception {
		query(session, catalog, "?sort=" + sort);
	}

	@When("the {role} queries the catalog {string} with page {int}, page size {int} and sort {string}")
	public void queryPage(Session session, String catalog, int page, int pageSize, String sort) throws Exception {
		query(session, catalog, "?page=" + page + "&pageSize=" + pageSize + "&sort=" + sort);
	}

	@Then("the catalog shows the columns {string} and {string}")
	public void columns(String first, String second) throws Exception {
		List<String> labels = new ArrayList<>();
		result().path("columns").forEach((column) -> labels.add(column.path("label").asString()));
		assertThat(labels).containsExactly(first, second);
	}

	@Then("the catalog shows the rows {string}, {string} and {string} in this order with the status {string}")
	public void rowsInOrder(String first, String second, String third, String status) throws Exception {
		assertThat(names()).containsExactly(first, second, third);
		result().path("rows").forEach((row) -> assertThat(row.path("values").path("status").asString()).isEqualTo(status));
	}

	@Then("the catalog shows no rows and a total of {int} entries")
	public void noRows(int total) throws Exception {
		assertThat(names()).isEmpty();
		total(total);
	}

	@Then("the catalog shows a total of {int} entries")
	public void total(int total) throws Exception {
		assertThat(result().path("page").path("totalElements").asInt()).isEqualTo(total);
	}

	@Then("the catalog shows the {int} rows {string} to {string} in alphabetical order")
	public void rowRange(int count, String first, String last) throws Exception {
		List<String> names = names();
		assertThat(names).hasSize(count).isSorted();
		assertThat(names).first().isEqualTo(first);
		assertThat(names).last().isEqualTo(last);
	}

	@Then("the catalog shows {int} rows on page {int} of {int}")
	public void pageOf(int rows, int page, int pages) throws Exception {
		assertThat(names()).hasSize(rows);
		assertThat(result().path("page").path("page").asInt()).isEqualTo(page - 1);
		assertThat(result().path("page").path("totalPages").asInt()).isEqualTo(pages);
	}

	@Then("the catalog shows the rows {string} and {string} and a total of {int} entries")
	public void twoRows(String first, String second, int total) throws Exception {
		assertThat(names()).containsExactly(first, second);
		total(total);
	}

	@Then("the query is answered with {int}")
	public void answeredWith(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	private void write(List<String> names, String status) throws Exception {
		String metatype = instance().oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'");
		String statusProperty = instance().oidc().single("SELECT id::text FROM property_definitions WHERE name = 'Status'");
		ObjectNode request = JsonMapper.shared().createObjectNode();
		ArrayNode entities = request.putArray("entities");
		for (String name : names) {
			ObjectNode entity = entities.addObject().put("name", name).put("metatypeId", metatype);
			if (status != null) {
				entity.putArray("properties").addObject().put("propertyDefId", statusProperty).put("value", status);
			}
		}
		HttpResponse<String> written = instance().post(BATCH, request.toString(), instance().architect(), Map.of());
		assertThat(written.statusCode()).as(written.body()).isEqualTo(200);
		assertThat(JsonMapper.shared().readTree(written.body()).path("summary").path("created").asInt())
			.isEqualTo(names.size());
	}

	private void query(Session session, String catalog, String parameters) throws Exception {
		String id = instance().oidc()
			.single("SELECT id::text FROM catalogs WHERE name = '" + catalog.replace("'", "''") + "'");
		this.response = instance().oidc().get("/api/v1/catalogs/" + id + "/query" + parameters, session.token());
	}

	private JsonNode result() throws Exception {
		answeredWith(200);
		return JsonMapper.shared().readTree(this.response.body());
	}

	private List<String> names() throws Exception {
		List<String> names = new ArrayList<>();
		result().path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		return names;
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

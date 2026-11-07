package io.oea.steps.portal;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/portal/portal-catalogs.feature (s9-portal-catalogs), on the instance of
 * {@link CatalogSteps}. `dev-portal-reader` holds only `portal-reader` through the mapping of its
 * realm group; `dev-no-role` is bound without role. Catalogs are addressed by their names.
 */
public class PortalSteps {

	private static final String PORTAL = "/api/v1/portal/catalogs";

	private static RepositoryInstance loggedIn;

	private static Session portalReader;

	private static Session withoutRole;

	private HttpResponse<String> response;

	private HttpResponse<String> byPage;

	private String sort;

	@Given("the portal reader and a person without role are logged in through the IdP")
	public void logIn() throws Exception {
		synchronized (PortalSteps.class) {
			// the instance is shared by the catalog features and restarted per run
			if (loggedIn != instance()) {
				instance().oidc().mapping("oea-portal-readers", "portal-reader");
				portalReader = instance().login("dev-portal-reader");
				withoutRole = instance().login("dev-no-role");
				loggedIn = instance();
			}
		}
	}

	@ParameterType("portal reader|enterprise architect|person without role")
	public Session portalRole(String role) {
		return switch (role) {
			case "portal reader" -> portalReader;
			case "person without role" -> withoutRole;
			default -> instance().architect();
		};
	}

	@When("the {portalRole} lists the portal catalogs")
	public void list(Session session) throws Exception {
		this.response = instance().oidc().get(PORTAL, session.token());
	}

	@When("the portal reader queries the portal catalog {string}")
	public void query(String name) throws Exception {
		this.response = instance().oidc().get(PORTAL + "/" + catalogId(name) + "/query", portalReader.token());
	}

	@When("the portal reader queries the portal catalog {string} with page size {int} and sort {string}")
	public void querySorted(String name, int pageSize, String sort) throws Exception {
		this.sort = sort;
		this.response = instance().oidc()
			.get(PORTAL + "/" + catalogId(name) + "/query?pageSize=" + pageSize + "&sort=" + sort,
					portalReader.token());
	}

	@When("the portal reader queries the next page of the portal catalog {string} through the cursor")
	public void queryNextPage(String name) throws Exception {
		JsonNode previous = body(200);
		String parameters = "?page=" + (previous.path("page").path("page").asInt() + 1) + "&pageSize="
				+ previous.path("page").path("pageSize").asInt() + "&sort=" + this.sort;
		this.byPage = instance().oidc().get(PORTAL + "/" + catalogId(name) + "/query" + parameters, portalReader.token());
		this.response = instance().oidc()
			.get(PORTAL + "/" + catalogId(name) + "/query" + parameters + "&cursor="
					+ URLEncoder.encode(previous.path("nextCursor").asString(), StandardCharsets.UTF_8),
					portalReader.token());
	}

	@Then("the portal rows are the same as with the page alone")
	public void sameAsByPage() throws Exception {
		assertThat(this.byPage.statusCode()).isEqualTo(200);
		JsonNode byCursor = body(200);
		assertThat(byCursor.path("rows")).isNotEmpty();
		assertThat(byCursor.path("rows")).isEqualTo(JsonMapper.shared().readTree(this.byPage.body()).path("rows"));
	}

	@Then("the portal list contains {string} and not {string}")
	public void listContains(String shown, String hidden) {
		assertThat(listed()).contains(shown).doesNotContain(hidden);
	}

	@Then("the portal list does not contain {string}")
	public void listLacks(String hidden) {
		assertThat(listed()).doesNotContain(hidden);
	}

	@Then("the portal result has the columns {string} and {string}")
	public void columns(String first, String second) {
		List<String> labels = new ArrayList<>();
		body(200).path("columns").forEach((column) -> labels.add(column.path("label").asString()));
		assertThat(labels).containsExactly(first, second);
	}

	@Then("the portal result equals the catalog query of the enterprise architect")
	public void equalsCatalogQuery() throws Exception {
		assertThat(body(200).path("rows")).isNotEmpty();
		String catalog = this.response.uri().getPath().split("/")[5];
		HttpResponse<String> work = instance().oidc()
			.get("/api/v1/catalogs/" + catalog + "/query", instance().architect().token());
		assertThat(work.statusCode()).isEqualTo(200);
		assertThat(body(200)).isEqualTo(JsonMapper.shared().readTree(work.body()));
	}

	@Then("the portal result has {int} rows sorted by name descending")
	public void sortedDescending(int rows) {
		List<String> names = new ArrayList<>();
		body(200).path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		assertThat(names).hasSize(rows)
			.isSortedAccordingTo(
					Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT)).reversed());
	}

	@Then("the portal query is answered with {int}")
	public void answered(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the portal query of the enterprise architect for {string} is answered with {int}")
	public void architectAnswered(String name, int status) throws Exception {
		assertThat(instance().oidc()
			.get(PORTAL + "/" + catalogId(name) + "/query", instance().architect().token())
			.statusCode()).isEqualTo(status);
	}

	@Then("the portal query is rejected with 400 naming {string}")
	public void rejected(String field) {
		List<String> fields = new ArrayList<>();
		body(400).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	private List<String> listed() {
		List<String> names = new ArrayList<>();
		body(200).forEach((catalog) -> names.add(catalog.path("name").asString()));
		return names;
	}

	private JsonNode body(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static String catalogId(String name) throws Exception {
		return instance().oidc()
			.single("SELECT id::text FROM catalogs WHERE name = '" + name.replace("'", "''") + "'");
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

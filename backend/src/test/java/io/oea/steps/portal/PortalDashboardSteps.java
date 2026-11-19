package io.oea.steps.portal;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/portal/portal-dashboards.feature (us-061-portal-dashboards), on the instance
 * of {@link CatalogSteps} and the sessions of {@link PortalSteps} (the parameter type
 * {@code portalRole}). Dashboards are created with the steps of the dashboard features and
 * addressed by their names.
 */
public class PortalDashboardSteps {

	private static final String PORTAL = "/api/v1/portal/dashboards";

	private HttpResponse<String> response;

	private String readDashboard;

	@When("the {portalRole} lists the portal dashboards")
	public void list(Session session) throws Exception {
		this.response = instance().oidc().get(PORTAL, session.token());
	}

	@When("the {portalRole} reads the portal dashboard {string}")
	public void read(Session session, String name) throws Exception {
		this.response = instance().oidc().get(PORTAL + "/" + id(name), session.token());
	}

	@When("the {portalRole} reads the data of the portal dashboard {string}")
	public void readData(Session session, String name) throws Exception {
		this.readDashboard = name;
		this.response = instance().oidc().get(PORTAL + "/" + id(name) + "/data", session.token());
	}

	@When("the portal reader reads the data of an unknown portal dashboard")
	public void readUnknown() throws Exception {
		this.response = instance().oidc()
			.get(PORTAL + "/" + UUID.randomUUID() + "/data", PortalSteps.portalReader().token());
	}

	/** {@code page} is the 1-based page of the scenario; the operation counts from 0. */
	@When("the portal reader reads page {int} of the widget {string} of the portal dashboard {string}")
	public void readPage(int page, String title, String name) throws Exception {
		this.response = instance().oidc()
			.get(PORTAL + "/" + id(name) + "/widgets/" + widgetId(name, title) + "/data?page=" + (page - 1),
					PortalSteps.portalReader().token());
	}

	@Then("the portal request is answered with {int}")
	public void answered(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the portal dashboard list contains {string} and not {string}")
	public void listContains(String shown, String hidden) {
		assertThat(names()).contains(shown).doesNotContain(hidden);
	}

	@Then("the portal dashboard list contains {string} with scope {string}")
	public void listContainsScope(String name, String scope) {
		for (JsonNode summary : body(200)) {
			if (summary.path("name").asString().equals(name)) {
				assertThat(summary.path("scope").asString()).isEqualTo(scope);
				return;
			}
		}
		throw new AssertionError("not listed: " + name);
	}

	@Then("no portal dashboard is editable")
	public void noneEditable() {
		body(200).forEach((summary) -> assertThat(summary.path("canEdit").asBoolean()).isFalse());
	}

	@Then("the portal dashboard has {int} widgets and is not editable")
	public void configuration(int widgets) {
		JsonNode config = body(200);
		assertThat(config.path("widgets")).hasSize(widgets);
		assertThat(config.path("canEdit").asBoolean()).isFalse();
	}

	@Then("the portal widget {string} is at column {int}, row {int}, width {int} and height {int}")
	public void positioned(String title, int col, int row, int width, int height) {
		for (JsonNode widget : body(200).path("widgets")) {
			if (widget.path("title").asString().equals(title)) {
				JsonNode position = widget.path("position");
				assertThat(List.of(position.path("col").asInt(), position.path("row").asInt(),
						position.path("width").asInt(), position.path("height").asInt()))
					.containsExactly(col, row, width, height);
				return;
			}
		}
		throw new AssertionError("no widget " + title);
	}

	@Then("the portal widget {string} has the status {string}")
	public void status(String title, String status) throws Exception {
		assertThat(widget(title).path("status").asString()).isEqualTo(status);
	}

	@Then("the portal widget {string} has the value {double}")
	public void value(String title, double value) throws Exception {
		assertThat(widget(title).path("status").asString()).isEqualTo("ok");
		assertThat(widget(title).path("value").asDouble()).isEqualTo(value);
	}

	@Then("the portal widget {string} shows the error {string}")
	public void error(String title, String code) throws Exception {
		assertThat(widget(title).path("error").path("code").asString()).isEqualTo(code);
	}

	@Then("the portal widget shows the entities {string} to {string}, page {int} of {int}")
	public void pageContent(String first, String last, int page, int pages) {
		JsonNode table = body(200).path("table");
		List<String> names = new ArrayList<>();
		table.path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		assertThat(names.get(0)).isEqualTo(first);
		assertThat(names.get(names.size() - 1)).isEqualTo(last);
		assertThat(table.path("page").path("page").asInt() + 1).isEqualTo(page);
		assertThat(table.path("page").path("totalPages").asInt()).isEqualTo(pages);
	}

	private JsonNode widget(String title) throws Exception {
		JsonNode data = body(200);
		String widgetId = widgetId(this.readDashboard, title);
		for (JsonNode widget : data.path("widgets")) {
			if (widget.path("widgetId").asString().equals(widgetId)) {
				return widget;
			}
		}
		throw new AssertionError("no widget " + title);
	}

	private List<String> names() {
		List<String> names = new ArrayList<>();
		body(200).forEach((summary) -> names.add(summary.path("name").asString()));
		return names;
	}

	private JsonNode body(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static String id(String name) throws Exception {
		return instance().oidc()
			.single("SELECT id::text FROM dashboards WHERE name = '" + name.replace("'", "''")
					+ "' ORDER BY created_at LIMIT 1");
	}

	private static String widgetId(String dashboard, String title) throws Exception {
		return instance().oidc()
			.single("SELECT id::text FROM dashboard_widgets WHERE dashboard_id = '" + id(dashboard) + "' AND title = '"
					+ title.replace("'", "''") + "'");
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

package io.oea.steps.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/dashboard/dashboards.feature and widgets.feature (us-055-dashboards), on the
 * instance of {@link CatalogSteps}: the enterprise architect holds {@code dashboard:manage}, the
 * viewer does not. Dashboards, widgets and catalogs are addressed by their names, which are
 * unique in the features. The other roles of the specs (a second manager, an administrator, a
 * service account) are tested in DashboardsTests.
 */
public class DashboardSteps {

	private static final String DASHBOARDS = "/api/v1/dashboards";

	private static final String VIEWS = "/api/v1/views/dashboards";

	private static final String CATALOGS = "/api/v1/catalogs";

	private HttpResponse<String> response;

	private HttpResponse<String> second;

	private String readDashboard;

	// ---- dashboards

	@When("the {role} creates the dashboard {string} with scope {string}")
	public void create(Session session, String name, String scope) throws Exception {
		this.response = instance().post(DASHBOARDS, dashboard(name, scope).toString(), session, Map.of());
	}

	@Given("the {role} has created the dashboard {string} with scope {string}")
	public void created(Session session, String name, String scope) throws Exception {
		create(session, name, scope);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@When("the {role} creates a dashboard with a name of {int} characters")
	public void createWithName(Session session, int length) throws Exception {
		create(session, "n".repeat(length - 1) + System.nanoTime() % 10, "personal");
	}

	@When("the {role} creates a dashboard named with only blanks")
	public void createBlank(Session session) throws Exception {
		create(session, "   ", "personal");
	}

	@When("the {role} creates a dashboard with a description of {int} characters")
	public void createWithDescription(Session session, int length) throws Exception {
		ObjectNode body = dashboard("Dashboard described " + System.nanoTime(), "personal").put("description",
				"d".repeat(length));
		this.response = instance().post(DASHBOARDS, body.toString(), session, Map.of());
	}

	@When("the {role} changes the scope of the dashboard {string} to {string}")
	public void changeScope(Session session, String name, String scope) throws Exception {
		this.response = put(session, name, dashboard(name, scope));
	}

	@When("the {role} renames the dashboard {string} to {string}")
	public void rename(Session session, String name, String newName) throws Exception {
		this.response = put(session, name, dashboard(newName, scope(name)));
	}

	@When("the {role} deletes the dashboard {string}")
	public void delete(Session session, String name) throws Exception {
		this.response = instance().send("DELETE", DASHBOARDS + "/" + id(name), null, session,
				Map.of("If-Match", "\"" + version(name) + "\""));
	}

	@Given("the dashboard {string} has the version {int}")
	public void hasVersion(String name, int version) throws Exception {
		instance().oidc().update("UPDATE dashboards SET version = " + version + " WHERE id = '" + id(name) + "'");
	}

	@When("the {role} adds a widget to the dashboard {string} with the version {int} and then without a version")
	public void staleWrites(Session session, String name, int version) throws Exception {
		String body = text("Stale", "x", 1, 1, 3, 1).toString();
		this.response = instance().post(DASHBOARDS + "/" + id(name) + "/widgets", body, session,
				Map.of("If-Match", "\"" + version + "\""));
		this.second = instance().post(DASHBOARDS + "/" + id(name) + "/widgets", body, session, Map.of());
	}

	@Then("the first dashboard request is rejected with {int} and the second with {int}")
	public void firstAndSecond(int first, int second) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(first);
		assertThat(this.second.statusCode()).as(this.second.body()).isEqualTo(second);
	}

	@Then("the dashboard is created without widgets and with the scope {string}")
	public void createdEmpty(String scope) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
		JsonNode body = body();
		assertThat(body.path("scope").asString()).isEqualTo(scope);
		assertThat(body.path("widgets")).isEmpty();
		assertThat(body.path("version").asInt()).isEqualTo(1);
	}

	@Then("the dashboard is created")
	public void isCreated() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@Then("the dashboard request is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the dashboard request is rejected with 422 naming {string}")
	public void rejectedNaming(String field) throws Exception {
		rejected(422);
		List<String> fields = new ArrayList<>();
		body().path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).anyMatch((candidate) -> candidate.equals(field) || candidate.endsWith("." + field));
	}

	@Then("the dashboard request is rejected with 422 {string} naming {string}")
	public void rejectedTyped(String type, String field) throws Exception {
		rejectedNaming(field);
		assertThat(body().path("type").asString()).endsWith(type);
	}

	@Then("the dashboard request is rejected with 422 and the message {string}")
	public void rejectedWithMessage(String message) throws Exception {
		rejected(422);
		assertThat(body().path("detail").asString()).isEqualTo(message);
	}

	@Then("the dashboard request is accepted")
	public void accepted() {
		assertThat(this.response.statusCode()).as(this.response.body()).isBetween(200, 201);
	}

	@Then("no dashboard is named {string}")
	public void noDashboard(String name) throws Exception {
		assertThat(named(name)).isZero();
	}

	@Then("exactly one dashboard is named {string} ignoring case")
	public void exactlyOne(String name) throws Exception {
		assertThat(named(name)).isEqualTo(1);
	}

	@Then("the dashboard overview of the {role} lists {string}")
	public void lists(Session session, String name) throws Exception {
		assertThat(overview(session)).contains(name);
	}

	@Then("the dashboard overview of the {role} does not list {string}")
	public void doesNotList(Session session, String name) throws Exception {
		assertThat(overview(session)).doesNotContain(name);
	}

	@Then("the {role} receives {int} for the dashboard {string}")
	public void read(Session session, int status, String name) throws Exception {
		assertThat(instance().oidc().get(VIEWS + "/" + id(name), session.token()).statusCode()).isEqualTo(status);
	}

	@Then("the dashboard {string} and its widgets are gone")
	public void gone(String name) throws Exception {
		assertThat(named(name)).isZero();
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM dashboard_widgets w WHERE NOT EXISTS (SELECT 1 FROM dashboards d WHERE d.id = w.dashboard_id)"))
			.isEqualTo("0");
	}

	@Then("the dashboard {string} has the scope {string}")
	public void hasScope(String name, String scope) throws Exception {
		assertThat(scope(name)).isEqualTo(scope);
	}

	@Then("the audit records {string} for the dashboard {string} with the changed field {string}")
	public void audited(String eventType, String name, String field) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '" + eventType + "' AND target_id = '"
					+ id(name) + "' AND details -> 'changed' ? '" + field + "'")).isEqualTo("1");
	}

	@Then("the audit records {string} for the dashboard {string}")
	public void auditedEvent(String eventType, String name) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '" + eventType + "' AND target_id = '"
					+ id(name) + "'")).isEqualTo("1");
	}

	// ---- widgets

	@Given("the dashboard {string} has {int} widgets")
	public void hasWidgets(String name, int count) throws Exception {
		instance().oidc().update("""
				INSERT INTO dashboard_widgets (dashboard_id, type, title, col, width, grid_row, height, config, created_by)
				SELECT '%s', 'text', 'Filler ' || n, 1, 3, n, 1, '{"content": ""}', (SELECT min(id) FROM persons)
				FROM generate_series(1, %d) AS n""".formatted(id(name), count));
	}

	@When("the {role} adds the text widget {string} with the content {string} to the dashboard {string}")
	public void addText(Session session, String title, String content, String dashboard) throws Exception {
		this.response = post(session, dashboard, text(title, content, 1, nextRow(dashboard), 12, 1));
	}

	@When("the {role} adds the text widget {string} with a content of {int} characters to the dashboard {string}")
	public void addLongText(Session session, String title, int length, String dashboard) throws Exception {
		this.response = post(session, dashboard, text(title, "c".repeat(length), 1, nextRow(dashboard), 12, 1));
	}

	@When("the {role} adds a widget of type {string} to the dashboard {string}")
	public void addType(Session session, String type, String dashboard) throws Exception {
		this.response = post(session, dashboard,
				text("Typed", "x", 1, nextRow(dashboard), 3, 1).put("type", type));
	}

	@When("the {role} changes the widget {string} of the dashboard {string} into a table widget for the catalog {string}")
	public void changeType(Session session, String title, String dashboard, String catalog) throws Exception {
		ObjectNode body = table(title, catalog, null, 10, 1, 1, 3, 1);
		this.response = instance().send("PUT", DASHBOARDS + "/" + id(dashboard) + "/widgets/" + widgetId(dashboard, title),
				body.toString(), session, Map.of("If-Match", "\"" + version(dashboard) + "\""));
	}

	@When("the {role} adds the table widget {string} for the catalog {string} to the dashboard {string}")
	public void addTable(Session session, String title, String catalog, String dashboard) throws Exception {
		this.response = post(session, dashboard, table(title, catalog, null, null, 1, nextRow(dashboard), 12, 3));
	}

	@When("the {role} adds the table widget {string} for the catalog {string} with the saved view {string} and {int} rows per page to the dashboard {string}")
	public void addTableWithView(Session session, String title, String catalog, String view, int pageSize,
			String dashboard) throws Exception {
		this.response = post(session, dashboard,
				table(title, catalog, viewId(catalog, view), pageSize, 1, nextRow(dashboard), 12, 3));
	}

	@When("the {role} adds the table widget {string} for the catalog {string} with {int} rows per page to the dashboard {string}")
	public void addTableWithPageSize(Session session, String title, String catalog, int pageSize, String dashboard)
			throws Exception {
		this.response = post(session, dashboard,
				table(title, catalog, null, pageSize, 1, nextRow(dashboard), 12, 3));
	}

	@When("the {role} adds the table widget {string} for the catalog {string} with a property aggregation as data source to the dashboard {string}")
	public void addAggregation(Session session, String title, String catalog, String dashboard) throws Exception {
		ObjectNode body = table(title, catalog, null, 10, 1, nextRow(dashboard), 12, 3);
		((ObjectNode) body.path("config")).put("dataSource", "property-aggregation");
		this.response = post(session, dashboard, body);
	}

	@Given("the {role} has created the personal catalog {string}")
	public void personalCatalog(Session session, String name) throws Exception {
		HttpResponse<String> created = instance().post(CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("primaryMetatypeId", instance().oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'"))
			.put("scope", "personal")
			.toString(), session, Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
	}

	@Given("the shared catalog {string} has {int} entities and the saved view {string}")
	public void catalogWithEntities(String catalog, int count, String view) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", catalog)
			.put("primaryMetatypeId", instance().oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'"))
			.put("scope", "instance");
		HttpResponse<String> created = instance().post(CATALOGS, body.toString(), instance().architect(), Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		// the elements are removed after the scenario; other features count applications
		instance().oidc().update("""
				INSERT INTO entities (metatype_id, name, created_by)
				SELECT (SELECT id FROM metatypes WHERE name = 'Application Component'), 'Dash App ' || lpad(n::text, 2, '0'),
				       (SELECT min(id) FROM persons) FROM generate_series(1, %d) AS n""".formatted(count));
		ObjectNode saved = JsonMapper.shared().createObjectNode().put("name", view).put("isDefault", false);
		saved.putArray("columnOrder").add("name");
		HttpResponse<String> viewed = instance().post(CATALOGS + "/" + catalogId(catalog) + "/views", saved.toString(),
				instance().architect(), Map.of());
		assertThat(viewed.statusCode()).as(viewed.body()).isEqualTo(201);
	}

	@After("@dashboard-entities")
	public void removeEntities() throws Exception {
		if (instance() == null) {
			return;
		}
		instance().oidc().update("DELETE FROM entities WHERE name LIKE 'Dash App %'");
	}

	@Given("the catalog {string} is deleted")
	public void catalogDeleted(String catalog) throws Exception {
		// there is no operation that deletes a catalog yet
		instance().oidc().update("DELETE FROM catalogs WHERE id = '" + catalogId(catalog) + "'");
	}

	@When("the {role} places widgets of the dashboard {string} in one request:")
	public void layout(Session session, String dashboard, DataTable table) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		var positions = body.putArray("positions");
		for (Map<String, String> row : table.asMaps()) {
			positions.add(JsonMapper.shared()
				.createObjectNode()
				.put("widgetId", widgetId(dashboard, row.get("title")))
				.put("col", Integer.parseInt(row.get("col")))
				.put("row", Integer.parseInt(row.get("row")))
				.put("width", Integer.parseInt(row.get("width")))
				.put("height", Integer.parseInt(row.get("height"))));
		}
		this.response = instance().send("PUT", DASHBOARDS + "/" + id(dashboard) + "/layout", body.toString(), session,
				Map.of("If-Match", "\"" + version(dashboard) + "\""));
	}

	@When("the {role} places the widget {string} of the dashboard {string} at column {int}, row {int}, width {int} and height {int}")
	public void place(Session session, String title, String dashboard, int col, int row, int width, int height)
			throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("positions")
			.add(JsonMapper.shared()
				.createObjectNode()
				.put("widgetId", widgetId(dashboard, title))
				.put("col", col)
				.put("row", row)
				.put("width", width)
				.put("height", height));
		this.response = instance().send("PUT", DASHBOARDS + "/" + id(dashboard) + "/layout", body.toString(), session,
				Map.of("If-Match", "\"" + version(dashboard) + "\""));
	}

	@Then("the widget {string} of the dashboard {string} is at column {int}, row {int}, width {int} and height {int}")
	public void positioned(String title, String dashboard, int col, int row, int width, int height) throws Exception {
		JsonNode reloaded = JsonMapper.shared()
			.readTree(instance().oidc().get(VIEWS + "/" + id(dashboard), instance().architect().token()).body());
		for (JsonNode widget : reloaded.path("widgets")) {
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

	// ---- data

	@When("the {role} reads the data of the dashboard {string}")
	public void readData(Session session, String dashboard) throws Exception {
		this.readDashboard = dashboard;
		this.response = instance().oidc().get(VIEWS + "/" + id(dashboard) + "/data", session.token());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@When("the {role} reads page {int} of the widget {string} of the dashboard {string}")
	public void readPage(Session session, int page, String title, String dashboard) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(dashboard) + "/widgets/" + widgetId(dashboard, title) + "/data?page=" + (page - 1),
					session.token());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Then("the widget {string} has the status {string}")
	public void status(String title, String status) throws Exception {
		assertThat(widgetData(title).path("status").asString()).isEqualTo(status);
	}

	@Then("the widget {string} shows the error {string}")
	public void error(String title, String code) throws Exception {
		assertThat(widgetData(title).path("error").path("code").asString()).isEqualTo(code);
	}

	@Then("the widget {string} shows {int} rows of {int} entities in {int} pages")
	public void rows(String title, int rows, int total, int pages) throws Exception {
		JsonNode table = widgetData(title).path("table");
		assertThat(table.path("rows")).hasSize(rows);
		assertThat(table.path("page").path("totalElements").asInt()).isEqualTo(total);
		assertThat(table.path("page").path("totalPages").asInt()).isEqualTo(pages);
	}

	@Then("the widget {string} shows only the column {string}")
	public void columns(String title, String column) throws Exception {
		List<String> keys = new ArrayList<>();
		widgetData(title).path("table").path("columns").forEach((node) -> keys.add(node.path("key").asString()));
		assertThat(keys).containsExactly(column);
	}

	@Then("the widget shows the entities {string} to {string}, page {int} of {int}")
	public void pageContent(String first, String last, int page, int pages) throws Exception {
		JsonNode table = JsonMapper.shared().readTree(this.response.body()).path("table");
		List<String> names = new ArrayList<>();
		table.path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		assertThat(names.get(0)).isEqualTo(first);
		assertThat(names.get(names.size() - 1)).isEqualTo(last);
		assertThat(table.path("page").path("page").asInt() + 1).isEqualTo(page);
		assertThat(table.path("page").path("totalPages").asInt()).isEqualTo(pages);
	}

	@Then("the widget {string} shows a column that is not readable")
	public void notReadable(String title) throws Exception {
		boolean found = false;
		for (JsonNode column : widgetData(title).path("table").path("columns")) {
			found |= !column.path("readable").asBoolean(true);
		}
		assertThat(found).isTrue();
	}

	// ---- KPI and chart widgets (us-056-kpi-and-chart-widgets)

	/**
	 * Adds a KPI or chart widget described by one row: {@code type}, {@code title}, {@code metatype},
	 * {@code property}, {@code function}, {@code groupBy} ({@code property:Name}, {@code metatype} or
	 * {@code layer}), {@code unit}, {@code comparison}, {@code target} and {@code chartType}; an empty
	 * cell leaves the field out.
	 */
	@When("the {role} adds this widget to the dashboard {string}:")
	public void addAggregation(Session session, String dashboard, DataTable table) throws Exception {
		Map<String, String> row = table.asMaps().get(0);
		boolean chart = "chart".equals(row.get("type"));
		ObjectNode body = JsonMapper.shared().createObjectNode().put("type", row.get("type")).put("title", row.get("title"));
		body.putObject("position").put("col", 1).put("row", nextRow(dashboard)).put("width", 6).put("height", 2);
		ObjectNode config = body.putObject("config");
		ObjectNode aggregation = config.putObject("aggregation").put("function", row.get("function"));
		put(aggregation, "metatypeId", lookup("metatypes", row.get("metatype")));
		put(aggregation, "propertyDefId", lookup("property_definitions", row.get("property")));
		String groupBy = row.get("groupBy");
		if (groupBy != null && !groupBy.isEmpty()) {
			String[] parts = groupBy.split(":", 2);
			ObjectNode group = aggregation.putObject("groupBy").put("kind", parts[0]);
			if (parts.length > 1) {
				put(group, "propertyDefId", lookup("property_definitions", parts[1]));
			}
		}
		if (row.get("unit") != null && !row.get("unit").isEmpty()) {
			config.put("unit", row.get("unit"));
		}
		if (row.get("comparison") != null && !row.get("comparison").isEmpty()) {
			config.put("comparison", row.get("comparison"));
		}
		if (row.get("target") != null && !row.get("target").isEmpty()) {
			config.put("targetValue", Double.parseDouble(row.get("target")));
		}
		if (chart || (row.get("chartType") != null && !row.get("chartType").isEmpty())) {
			config.put("chartType", row.get("chartType"));
		}
		this.response = post(session, dashboard, body);
	}

	@Then("the widget {string} of the dashboard {string} has the unit {string}")
	public void hasUnit(String title, String dashboard, String unit) throws Exception {
		JsonNode reloaded = JsonMapper.shared()
			.readTree(instance().oidc().get(VIEWS + "/" + id(dashboard), instance().architect().token()).body());
		for (JsonNode widget : reloaded.path("widgets")) {
			if (widget.path("title").asString().equals(title)) {
				assertThat(widget.path("config").path("unit").asString()).isEqualTo(unit);
				return;
			}
		}
		throw new AssertionError("no widget " + title);
	}

	@Then("the widget {string} has the value {double}")
	public void value(String title, double value) throws Exception {
		assertThat(widgetData(title).path("status").asString()).isEqualTo("ok");
		assertThat(widgetData(title).path("value").asDouble()).isEqualTo(value);
	}

	@Then("the widget {string} has no value")
	public void noValue(String title) throws Exception {
		assertThat(widgetData(title).path("status").asString()).isEqualTo("ok");
		assertThat(widgetData(title).path("value").isNull()).isTrue();
	}

	@Then("the widget {string} has the target {double}")
	public void target(String title, double target) throws Exception {
		assertThat(widgetData(title).path("target").asDouble()).isEqualTo(target);
	}

	/** The groups in order; the names of {@code none}, {@code other}, {@code true} and {@code false} are those of the client. */
	@Then("the widget {string} shows the groups:")
	public void groups(String title, DataTable table) throws Exception {
		List<String> expected = new ArrayList<>();
		table.asMaps().forEach((row) -> expected.add(row.get("label") + "=" + row.get("value")));
		assertThat(groupsOf(title)).containsExactlyElementsOf(expected);
	}

	@Then("the widget {string} shows {int} groups, the last one named {string} with the value {int}")
	public void lastGroup(String title, int count, String name, int value) throws Exception {
		List<String> groups = groupsOf(title);
		assertThat(groups).hasSize(count);
		assertThat(groups.get(count - 1)).isEqualTo(name + "=" + value);
	}

	@Then("the widget {string} shows {int} groups without {string}")
	public void groupCount(String title, int count, String name) throws Exception {
		List<String> groups = groupsOf(title);
		assertThat(groups).hasSize(count);
		assertThat(groups).noneMatch((group) -> group.startsWith(name + "="));
	}

	@Then("the widget {string} has no error")
	public void noError(String title) throws Exception {
		assertThat(widgetData(title).path("status").asString()).isEqualTo("ok");
		assertThat(widgetData(title).path("error").isMissingNode() || widgetData(title).path("error").isNull())
			.isTrue();
	}

	private List<String> groupsOf(String title) throws Exception {
		List<String> groups = new ArrayList<>();
		for (JsonNode entry : widgetData(title).path("series")) {
			String name = switch (entry.path("kind").asString()) {
				case "none" -> "(none)";
				case "other" -> "Other";
				case "true" -> "Yes";
				case "false" -> "No";
				default -> entry.path("label").asString();
			};
			groups.add(name + "=" + Math.round(entry.path("value").asDouble()));
		}
		return groups;
	}

	private static void put(ObjectNode node, String field, String value) {
		if (value != null) {
			node.put(field, value);
		}
	}

	private static String lookup(String table, String name) throws Exception {
		if (name == null || name.isEmpty()) {
			return null;
		}
		String id = instance().oidc().single("SELECT id::text FROM " + table + " WHERE name = '" + name.replace("'", "''") + "'");
		assertThat(id).as(table + " " + name).isNotNull();
		return id;
	}

	// ---- helpers

	private JsonNode widgetData(String title) throws Exception {
		JsonNode body = JsonMapper.shared().readTree(this.response.body());
		if (body.has("widgets")) {
			for (JsonNode widget : body.path("widgets")) {
				if (widget.path("widgetId").asString().equals(widgetIdByTitle(title))) {
					return widget;
				}
			}
			throw new AssertionError("no widget " + title);
		}
		return body;
	}

	private String widgetIdByTitle(String title) throws Exception {
		return widgetId(this.readDashboard, title);
	}

	private HttpResponse<String> put(Session session, String name, ObjectNode body) throws Exception {
		return instance().send("PUT", DASHBOARDS + "/" + id(name), body.toString(), session,
				Map.of("If-Match", "\"" + version(name) + "\""));
	}

	private HttpResponse<String> post(Session session, String dashboard, ObjectNode body) throws Exception {
		return instance().send("POST", DASHBOARDS + "/" + id(dashboard) + "/widgets", body.toString(), session,
				Map.of("If-Match", "\"" + version(dashboard) + "\""));
	}

	private JsonNode body() throws Exception {
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static ObjectNode dashboard(String name, String scope) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("description", "").put("scope", scope);
	}

	private static ObjectNode text(String title, String content, int col, int row, int width, int height) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("type", "text").put("title", title);
		body.putObject("position").put("col", col).put("row", row).put("width", width).put("height", height);
		body.putObject("config").put("content", content);
		return body;
	}

	private ObjectNode table(String title, String catalog, String view, Integer pageSize, int col, int row, int width,
			int height) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("type", "table").put("title", title);
		body.putObject("position").put("col", col).put("row", row).put("width", width).put("height", height);
		ObjectNode config = body.putObject("config").put("catalogId", catalogId(catalog));
		if (view != null) {
			config.put("savedViewId", view);
		}
		if (pageSize != null) {
			config.put("pageSize", pageSize);
		}
		return body;
	}

	private int nextRow(String dashboard) throws Exception {
		return Integer.parseInt(instance().oidc()
			.single("SELECT coalesce(max(grid_row), 0) + 1 FROM dashboard_widgets WHERE dashboard_id = '" + id(dashboard)
					+ "'"));
	}

	private String viewId(String catalog, String view) throws Exception {
		return instance().oidc()
			.single("SELECT v ->> 'id' FROM catalogs, jsonb_array_elements(config -> 'savedViews') v WHERE id = '"
					+ catalogId(catalog) + "' AND v ->> 'name' = '" + view.replace("'", "''") + "'");
	}

	private static String catalogId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM catalogs WHERE name = '" + name.replace("'", "''") + "'");
	}

	private static String id(String name) throws Exception {
		return instance().oidc()
			.single("SELECT id::text FROM dashboards WHERE name = '" + name.replace("'", "''") + "' ORDER BY created_at LIMIT 1");
	}

	private static int version(String name) throws Exception {
		return Integer.parseInt(instance().oidc().single("SELECT version::text FROM dashboards WHERE id = '" + id(name) + "'"));
	}

	private static String scope(String name) throws Exception {
		return instance().oidc().single("SELECT scope FROM dashboards WHERE id = '" + id(name) + "'");
	}

	private static String widgetId(String dashboard, String title) throws Exception {
		return instance().oidc()
			.single("SELECT id::text FROM dashboard_widgets WHERE dashboard_id = '" + id(dashboard) + "' AND title = '"
					+ title.replace("'", "''") + "'");
	}

	private static int named(String name) throws Exception {
		return Integer.parseInt(instance().oidc()
			.single("SELECT count(*)::text FROM dashboards WHERE lower(btrim(name)) = lower(btrim('"
					+ name.replace("'", "''") + "'))"));
	}

	private static List<String> overview(Session session) throws Exception {
		HttpResponse<String> listed = instance().oidc().get(DASHBOARDS, session.token());
		assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
		List<String> names = new ArrayList<>();
		JsonMapper.shared().readTree(listed.body()).forEach((summary) -> names.add(summary.path("name").asString()));
		return names;
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

package io.oea.app.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.dashboard.DashboardTestSupport.Reply;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec dashboard/widgets (US-058 to US-060, REQ-052, REQ-054, REQ-057; design D1 to D3 of
 * us-055-dashboards): text and table widgets, the grid and the layout request.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardWidgetsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = DashboardTestSupport.tempDirectory("oea-widgets") + "/setup-token";

	private static final String DASHBOARDS = DashboardTestSupport.DASHBOARDS;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> TOKEN_FILE);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	private DashboardTestSupport support;

	private TestSession kurt;

	private TestSession franz;

	private String sharedCatalog;

	@BeforeEach
	void instance() {
		this.support = new DashboardTestSupport(this.mvc, this.jdbc);
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = principals.personWithIdpRole("enterprise-architect");
		this.franz = principals.personWithIdpRole("enterprise-architect");
		this.sharedCatalog = this.support.catalog(this.kurt, unique("Application Inventory"), "instance");
	}

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private Reply shared() {
		return this.support.created(this.kurt, unique("Widgets"), "instance");
	}

	private static ObjectNode pos(int col, int row, int width, int height) {
		return DashboardTestSupport.position(col, row, width, height);
	}

	private static String widgetsPath(Reply dashboard) {
		return DASHBOARDS + "/" + dashboard.id() + "/widgets";
	}

	private Reply add(TestSession session, Reply dashboard, ObjectNode widget) {
		return this.support.write(HttpMethod.POST, widgetsPath(dashboard), session, widget, dashboard.version());
	}

	private int widgetCount(Reply dashboard) {
		return this.support.count("SELECT count(*) FROM dashboard_widgets WHERE dashboard_id = ?::uuid",
				dashboard.id());
	}

	// ---- text widgets

	@Test
	void textWidgetIsAddedChangedAndRemoved() {
		Reply dashboard = shared();
		ObjectNode notes = DashboardTestSupport.textWidget("Methodological Notes", "**bold**\n\n- one\n- two",
				pos(1, 1, 6, 2));
		Reply added = add(this.kurt, dashboard, notes);
		assertThat(added.status()).as(added.raw()).isEqualTo(200);
		assertThat(added.version()).isEqualTo(2);
		assertThat(added.etag()).isEqualTo("\"2\"");
		JsonNode widget = added.json().path("widgets").get(0);
		assertThat(widget.path("type").asString()).isEqualTo("text");
		assertThat(widget.path("config").path("content").asString()).isEqualTo("**bold**\n\n- one\n- two");
		assertThat(widget.path("position").path("width").asInt()).isEqualTo(6);
		String widgetId = widget.path("id").asString();

		Reply changed = this.support.write(HttpMethod.PUT, widgetsPath(dashboard) + "/" + widgetId, this.kurt,
				DashboardTestSupport.textWidget("Renamed notes", "changed", pos(7, 1, 6, 3)), 2);
		assertThat(changed.status()).as(changed.raw()).isEqualTo(200);
		assertThat(changed.version()).isEqualTo(3);
		assertThat(changed.json().path("widgets").get(0).path("title").asString()).isEqualTo("Renamed notes");
		assertThat(changed.json().path("widgets").get(0).path("position").path("col").asInt()).isEqualTo(7);

		Reply removed = this.support.write(HttpMethod.DELETE, widgetsPath(dashboard) + "/" + widgetId, this.kurt, null,
				3);
		assertThat(removed.status()).as(removed.raw()).isEqualTo(200);
		assertThat(removed.version()).isEqualTo(4);
		assertThat(removed.json().path("widgets")).isEmpty();
		assertThat(this.support.auditDetails("dashboard.updated", dashboard.id())).hasSize(3);
		assertThat(this.support.auditDetails("dashboard.updated", dashboard.id()).get(0)).contains("added")
			.contains(widgetId)
			.doesNotContain("Methodological");
		assertThat(this.support.auditDetails("dashboard.updated", dashboard.id()).get(2)).contains("removed");
	}

	@ParameterizedTest
	@CsvSource({ "0, true", "1, true", "10000, true", "10001, false" })
	void textContentLengthAtTheBoundaries(int length, boolean accepted) {
		Reply dashboard = shared();
		Reply reply = add(this.kurt, dashboard,
				DashboardTestSupport.textWidget("Content", "c".repeat(length), pos(1, 1, 3, 1)));
		if (accepted) {
			assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
			assertThat(reply.json().path("widgets").get(0).path("config").path("content").asString()).hasSize(length);
		}
		else {
			assertThat(reply.status()).isEqualTo(422);
			assertThat(reply.names("content")).isTrue();
			assertThat(widgetCount(dashboard)).isZero();
		}
	}

	@Test
	void markdownIsStoredUnchanged() {
		Reply dashboard = shared();
		String content = "<script>alert(1)</script> <img src=x onerror=alert(1)> [x](javascript:alert(1))";
		Reply reply = add(this.kurt, dashboard, DashboardTestSupport.textWidget("Script", content, pos(1, 1, 3, 1)));
		assertThat(reply.json().path("widgets").get(0).path("config").path("content").asString()).isEqualTo(content);
	}

	@ParameterizedTest
	@CsvSource({ "1, true", "150, true", "151, false" })
	void titleLengthAtTheBoundaries(int length, boolean accepted) {
		Reply dashboard = shared();
		Reply reply = add(this.kurt, dashboard,
				DashboardTestSupport.textWidget("t".repeat(length), "x", pos(1, 1, 3, 1)));
		if (accepted) {
			assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		}
		else {
			assertThat(reply.status()).isEqualTo(422);
			assertThat(reply.names("title")).isTrue();
		}
	}

	@Test
	void blankTitleIsRejected() {
		Reply dashboard = shared();
		Reply reply = add(this.kurt, dashboard, DashboardTestSupport.textWidget("   ", "x", pos(1, 1, 3, 1)));
		assertThat(reply.status()).isEqualTo(422);
		assertThat(reply.names("title")).isTrue();
		assertThat(widgetCount(dashboard)).isZero();
	}

	// ---- types

	@ParameterizedTest
	@CsvSource({ "kpi", "chart" })
	void kpiAndChartAreNotSupportedYet(String type) {
		Reply dashboard = shared();
		ObjectNode widget = DashboardTestSupport.textWidget("Later", "x", pos(1, 1, 3, 1)).put("type", type);
		Reply reply = add(this.kurt, dashboard, widget);
		assertThat(reply.status()).isEqualTo(422);
		assertThat(reply.names("type")).isTrue();
		assertThat(widgetCount(dashboard)).isZero();
	}

	@Test
	void unknownTypeIsRejected() {
		Reply dashboard = shared();
		ObjectNode widget = DashboardTestSupport.textWidget("Odd", "x", pos(1, 1, 3, 1)).put("type", "gauge");
		assertThat(add(this.kurt, dashboard, widget).status()).isIn(400, 422);
	}

	@Test
	void theTypeCannotChange() {
		Reply dashboard = shared();
		Reply added = add(this.kurt, dashboard, DashboardTestSupport.textWidget("Text", "x", pos(1, 1, 3, 1)));
		String widgetId = DashboardTestSupport.widgetId(added, "Text");
		Reply changed = this.support.write(HttpMethod.PUT, widgetsPath(dashboard) + "/" + widgetId, this.kurt,
				DashboardTestSupport.tableWidget("Text", this.sharedCatalog, null, 10, pos(1, 1, 3, 1)), 2);
		assertThat(changed.status()).isEqualTo(422);
		assertThat(changed.invalidFields()).containsExactly("type");
	}

	// ---- widget count

	@Test
	void fiftyWidgetsAreTheLimit() {
		Reply dashboard = shared();
		long person = this.support.personOf(this.kurt);
		for (int i = 0; i < 49; i++) {
			this.jdbc.update("""
					INSERT INTO dashboard_widgets (dashboard_id, type, title, col, width, grid_row, height, config, created_by)
					VALUES (?::uuid, 'text', ?, 1, 3, ?, 1, '{"content": ""}', ?)""", dashboard.id(), "W" + i, i + 1,
					person);
		}
		Reply fiftieth = add(this.kurt, dashboard, DashboardTestSupport.textWidget("50th", "x", pos(1, 50, 3, 1)));
		assertThat(fiftieth.status()).as(fiftieth.raw()).isEqualTo(200);
		Reply fiftyFirst = add(this.kurt, fiftieth, DashboardTestSupport.textWidget("51st", "x", pos(1, 51, 3, 1)));
		assertThat(fiftyFirst.status()).isEqualTo(422);
		assertThat(fiftyFirst.invalidFields()).containsExactly("widgets");
		assertThat(widgetCount(dashboard)).isEqualTo(50);
	}

	// ---- table widgets

	@Test
	void tableWidgetWithAndWithoutAView() {
		Reply dashboard = shared();
		String view = this.support.view(this.kurt, this.sharedCatalog, "Compact view");
		Reply withView = add(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Application Overview", this.sharedCatalog, view, 10, pos(1, 1, 12, 3)));
		assertThat(withView.status()).as(withView.raw()).isEqualTo(200);
		JsonNode config = withView.json().path("widgets").get(0).path("config");
		assertThat(config.path("catalogId").asString()).isEqualTo(this.sharedCatalog);
		assertThat(config.path("savedViewId").asString()).isEqualTo(view);
		assertThat(config.path("pageSize").asInt()).isEqualTo(10);
		Reply without = add(this.kurt, withView,
				DashboardTestSupport.tableWidget("Default state", this.sharedCatalog, null, null, pos(1, 4, 12, 3)));
		assertThat(without.status()).as(without.raw()).isEqualTo(200);
		JsonNode second = without.json().path("widgets").get(1).path("config");
		assertThat(second.path("savedViewId").isNull() || second.path("savedViewId").isMissingNode()).isTrue();
		assertThat(second.path("pageSize").asInt()).as("the default page size").isEqualTo(10);
	}

	@Test
	void anotherPersonsPersonalCatalogAndAnUnknownCatalogGive403() {
		Reply dashboard = shared();
		String foreign = this.support.catalog(this.franz, unique("Franz private"), "personal");
		for (String catalog : new String[] { foreign, UUID.randomUUID().toString() }) {
			Reply reply = add(this.kurt, dashboard,
					DashboardTestSupport.tableWidget("Foreign", catalog, null, 10, pos(1, 1, 6, 2)));
			assertThat(reply.status()).as(catalog).isEqualTo(403);
		}
		assertThat(widgetCount(dashboard)).isZero();
	}

	@Test
	void ownPersonalCatalogOnlyOnAPersonalDashboard() {
		String own = this.support.catalog(this.kurt, unique("Kurt private"), "personal");
		Reply onShared = add(this.kurt, shared(),
				DashboardTestSupport.tableWidget("Own", own, null, 10, pos(1, 1, 6, 2)));
		assertThat(onShared.status()).isEqualTo(422);
		assertThat(onShared.invalidFields()).containsExactly("catalogId");
		Reply personal = this.support.created(this.kurt, unique("Personal"), "personal");
		Reply onPersonal = add(this.kurt, personal,
				DashboardTestSupport.tableWidget("Own", own, null, 10, pos(1, 1, 6, 2)));
		assertThat(onPersonal.status()).as(onPersonal.raw()).isEqualTo(200);
	}

	@Test
	void aViewOfAnotherCatalogIsRejected() {
		Reply dashboard = shared();
		String other = this.support.catalog(this.kurt, unique("Other catalog"), "instance");
		String foreignView = this.support.view(this.kurt, other, "Other view");
		Reply reply = add(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Wrong view", this.sharedCatalog, foreignView, 10, pos(1, 1, 6, 2)));
		assertThat(reply.status()).isEqualTo(422);
		assertThat(reply.invalidFields()).containsExactly("savedViewId");
		Reply unknown = add(this.kurt, dashboard, DashboardTestSupport.tableWidget("Unknown view", this.sharedCatalog,
				UUID.randomUUID().toString(), 10, pos(1, 1, 6, 2)));
		assertThat(unknown.status()).isEqualTo(422);
		assertThat(widgetCount(dashboard)).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "5, true", "100, true", "4, false", "101, false", "0, false", "-1, false" })
	void pageSizeAtTheBoundaries(int pageSize, boolean accepted) {
		Reply dashboard = shared();
		Reply reply = add(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Page size", this.sharedCatalog, null, pageSize, pos(1, 1, 6, 2)));
		if (accepted) {
			assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
			assertThat(reply.json().path("widgets").get(0).path("config").path("pageSize").asInt())
				.isEqualTo(pageSize);
		}
		else {
			assertThat(reply.status()).isEqualTo(422);
			assertThat(reply.names("pageSize")).isTrue();
			assertThat(widgetCount(dashboard)).isZero();
		}
	}

	@Test
	void aTableWidgetNeedsACatalog() {
		Reply dashboard = shared();
		ObjectNode widget = DashboardTestSupport.tableWidget("No catalog", this.sharedCatalog, null, 10, pos(1, 1, 6, 2));
		((ObjectNode) widget.path("config")).remove("catalogId");
		Reply reply = add(this.kurt, dashboard, widget);
		assertThat(reply.status()).isEqualTo(422);
		assertThat(reply.invalidFields()).containsExactly("catalogId");
	}

	@Test
	void aPropertyAggregationIsNoCatalogQuery() {
		Reply dashboard = shared();
		ObjectNode widget = DashboardTestSupport.tableWidget("Aggregation", this.sharedCatalog, null, 10, pos(1, 1, 6, 2));
		((ObjectNode) widget.path("config")).put("dataSource", "property-aggregation");
		Reply reply = add(this.kurt, dashboard, widget);
		assertThat(reply.status()).isEqualTo(422);
		assertThat(reply.json().path("detail").asString()).isEqualTo("Table widget requires CatalogQuery DataSource");
		((ObjectNode) widget.path("config")).put("dataSource", "catalog-query");
		assertThat(add(this.kurt, dashboard, widget).status()).isEqualTo(200);
	}

	// ---- grid

	/** col, row, width, height, accepted, the rejected field */
	@ParameterizedTest
	@CsvSource({ "1, 1, 1, 1, true,", "12, 500, 1, 6, true,", "1, 1, 12, 1, true,", "5, 1, 8, 1, true,",
			"0, 1, 3, 1, false, col", "13, 1, 1, 1, false, col", "1, 1, 0, 1, false, width",
			"1, 1, 13, 1, false, width", "5, 1, 9, 1, false, width", "12, 1, 2, 1, false, width",
			"1, 0, 3, 1, false, row", "1, 501, 3, 1, false, row", "1, 1, 3, 0, false, height",
			"1, 1, 3, 7, false, height", "1, 1, 3, 6, true," })
	void gridPositionAtTheBoundaries(int col, int row, int width, int height, boolean accepted, String field) {
		Reply dashboard = shared();
		Reply reply = add(this.kurt, dashboard,
				DashboardTestSupport.textWidget("Grid", "x", pos(col, row, width, height)));
		if (accepted) {
			assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
			JsonNode position = reply.json().path("widgets").get(0).path("position");
			assertThat(position.path("col").asInt()).isEqualTo(col);
			assertThat(position.path("row").asInt()).isEqualTo(row);
			assertThat(position.path("width").asInt()).isEqualTo(width);
			assertThat(position.path("height").asInt()).isEqualTo(height);
		}
		else {
			assertThat(reply.status()).isEqualTo(422);
			assertThat(reply.names(field)).as(reply.raw()).isTrue();
			assertThat(widgetCount(dashboard)).isZero();
		}
	}

	@Test
	void widthBeyondTheGridNamesTheWidth() {
		Reply dashboard = shared();
		Reply reply = add(this.kurt, dashboard, DashboardTestSupport.textWidget("Grid", "x", pos(5, 1, 9, 1)));
		assertThat(reply.status()).isEqualTo(422);
		assertThat(reply.invalidFields()).containsExactly("width");
		assertThat(reply.json().path("detail").asString()).isEqualTo("Widget exceeds the grid width");
	}

	// ---- layout

	@Test
	void twoPositionsInOneRequestAreSavedAndKept() {
		Reply dashboard = shared();
		Reply first = add(this.kurt, dashboard, DashboardTestSupport.textWidget("First", "x", pos(1, 1, 12, 1)));
		Reply second = add(this.kurt, first, DashboardTestSupport.textWidget("Second", "y", pos(1, 2, 12, 1)));
		ObjectNode layout = layout(position(DashboardTestSupport.widgetId(second, "First"), 1, 1, 3, 1),
				position(DashboardTestSupport.widgetId(second, "Second"), 4, 1, 9, 3));
		Reply reply = this.support.write(HttpMethod.PUT, DASHBOARDS + "/" + dashboard.id() + "/layout", this.kurt,
				layout, 3);
		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(reply.version()).isEqualTo(4);
		Reply reloaded = this.support.get(DashboardTestSupport.VIEWS + "/" + dashboard.id(), this.kurt);
		for (JsonNode widget : reloaded.json().path("widgets")) {
			JsonNode position = widget.path("position");
			if (widget.path("title").asString().equals("First")) {
				assertThat(position.path("col").asInt()).isEqualTo(1);
				assertThat(position.path("width").asInt()).isEqualTo(3);
				assertThat(position.path("height").asInt()).isEqualTo(1);
			}
			else {
				assertThat(position.path("col").asInt()).isEqualTo(4);
				assertThat(position.path("width").asInt()).isEqualTo(9);
				assertThat(position.path("height").asInt()).isEqualTo(3);
			}
		}
		assertThat(this.support.auditDetails("dashboard.updated", dashboard.id()).get(2)).contains("layout");
	}

	@Test
	void overlappingPositionsAreAccepted() {
		Reply dashboard = shared();
		Reply first = add(this.kurt, dashboard, DashboardTestSupport.textWidget("First", "x", pos(1, 1, 6, 2)));
		Reply second = add(this.kurt, first, DashboardTestSupport.textWidget("Second", "y", pos(1, 3, 6, 2)));
		Reply reply = this.support.write(HttpMethod.PUT, DASHBOARDS + "/" + dashboard.id() + "/layout", this.kurt,
				layout(position(DashboardTestSupport.widgetId(second, "Second"), 3, 1, 6, 2)), 3);
		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
	}

	@Test
	void layoutNeedsOneToFiftyPositionsOfTheDashboard() {
		Reply dashboard = shared();
		Reply added = add(this.kurt, dashboard, DashboardTestSupport.textWidget("First", "x", pos(1, 1, 6, 2)));
		String own = DashboardTestSupport.widgetId(added, "First");
		String path = DASHBOARDS + "/" + dashboard.id() + "/layout";
		// empty
		Reply empty = this.support.write(HttpMethod.PUT, path, this.kurt, layout(), 2);
		assertThat(empty.status()).isEqualTo(422);
		assertThat(empty.names("positions")).isTrue();
		// a widget of another dashboard
		Reply other = add(this.kurt, shared(), DashboardTestSupport.textWidget("Other", "x", pos(1, 1, 6, 2)));
		Reply foreign = this.support.write(HttpMethod.PUT, path, this.kurt,
				layout(position(DashboardTestSupport.widgetId(other, "Other"), 1, 1, 3, 1)), 2);
		assertThat(foreign.status()).isEqualTo(422);
		assertThat(foreign.invalidFields()).containsExactly("widgetId");
		// the same widget twice, and an unknown widget
		Reply twice = this.support.write(HttpMethod.PUT, path, this.kurt,
				layout(position(own, 1, 1, 3, 1), position(own, 2, 1, 3, 1)), 2);
		assertThat(twice.status()).isEqualTo(422);
		Reply unknown = this.support.write(HttpMethod.PUT, path, this.kurt,
				layout(position(UUID.randomUUID().toString(), 1, 1, 3, 1)), 2);
		assertThat(unknown.status()).isEqualTo(422);
		// one position
		assertThat(this.support.write(HttpMethod.PUT, path, this.kurt, layout(position(own, 2, 2, 3, 1)), 2)
			.status()).isEqualTo(200);
		// a position beyond the grid rejects the whole request
		Reply wide = this.support.write(HttpMethod.PUT, path, this.kurt, layout(position(own, 5, 2, 9, 1)), 3);
		assertThat(wide.status()).isEqualTo(422);
		assertThat(wide.json().path("detail").asString()).isEqualTo("Widget exceeds the grid width");
		assertThat(this.support.count("SELECT count(*) FROM dashboard_widgets WHERE id = ?::uuid AND col = 2", own))
			.isEqualTo(1);
	}

	@Test
	void layoutOfFiftyAndFiftyOnePositions() {
		Reply dashboard = shared();
		long person = this.support.personOf(this.kurt);
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 50; i++) {
			ids.add(this.jdbc.queryForObject("""
					INSERT INTO dashboard_widgets (dashboard_id, type, title, col, width, grid_row, height, config, created_by)
					VALUES (?::uuid, 'text', ?, 1, 3, ?, 1, '{"content": ""}', ?) RETURNING id::text""", String.class,
					dashboard.id(), "W" + i, i + 1, person));
		}
		String path = DASHBOARDS + "/" + dashboard.id() + "/layout";
		ObjectNode fifty = layout();
		ids.forEach((id) -> ((tools.jackson.databind.node.ArrayNode) fifty.path("positions")).add(position(id, 4, 1, 3, 1)));
		Reply accepted = this.support.write(HttpMethod.PUT, path, this.kurt, fifty, 1);
		assertThat(accepted.status()).as(accepted.raw()).isEqualTo(200);
		ObjectNode tooMany = fifty.deepCopy();
		((tools.jackson.databind.node.ArrayNode) tooMany.path("positions")).add(position(ids.get(0), 4, 1, 3, 1));
		Reply rejected = this.support.write(HttpMethod.PUT, path, this.kurt, tooMany, 2);
		assertThat(rejected.status()).isEqualTo(422);
		assertThat(rejected.names("positions")).isTrue();
	}

	// ---- rights and versions

	@Test
	void widgetWritesNeedTheRightsAndTheVersionOfTheDashboard() {
		Reply dashboard = shared();
		TestSession reader = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("viewer");
		ObjectNode widget = DashboardTestSupport.textWidget("Notes", "x", pos(1, 1, 3, 1));
		assertThat(add(reader, dashboard, widget).status()).isEqualTo(403);
		assertThat(this.support.call(HttpMethod.POST, widgetsPath(dashboard), this.kurt, widget, null).status())
			.isEqualTo(428);
		Reply stale = this.support.write(HttpMethod.POST, widgetsPath(dashboard), this.kurt, widget, 5);
		assertThat(stale.status()).isEqualTo(412);
		assertThat(widgetCount(dashboard)).isZero();
		// an unknown widget
		assertThat(this.support
			.write(HttpMethod.DELETE, widgetsPath(dashboard) + "/" + UUID.randomUUID(), this.kurt, null, 1)
			.status()).isEqualTo(404);
		assertThat(this.support
			.write(HttpMethod.PUT, widgetsPath(dashboard) + "/" + UUID.randomUUID(), this.kurt, widget, 1)
			.status()).isEqualTo(404);
	}

	@Test
	void aPersonalDashboardIsChangedByItsOwnerOnly() {
		Reply mine = this.support.created(this.franz, unique("Franz"), "personal");
		ObjectNode widget = DashboardTestSupport.textWidget("Notes", "x", pos(1, 1, 3, 1));
		assertThat(add(this.kurt, mine, widget).status()).isEqualTo(404);
		assertThat(add(this.franz, mine, widget).status()).isEqualTo(200);
	}

	private static ObjectNode position(String widgetId, int col, int row, int width, int height) {
		return pos(col, row, width, height).put("widgetId", widgetId);
	}

	private static ObjectNode layout(ObjectNode... positions) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		var array = body.putArray("positions");
		for (ObjectNode position : positions) {
			array.add(position);
		}
		return body;
	}

}

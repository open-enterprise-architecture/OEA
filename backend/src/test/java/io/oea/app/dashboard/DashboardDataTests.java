package io.oea.app.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.oea.app.dashboard.DashboardTestSupport.Reply;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
 * Spec dashboard/widgets "Dashboard data" (US-058 AC4, REQ-055, REQ-056; design D3 of
 * us-055-dashboards): the data of text and table widgets, the further pages, the error status of a
 * widget and the reader's visibility.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardDataTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = DashboardTestSupport.tempDirectory("oea-dashboard-data") + "/setup-token";

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

	@Autowired
	private MeterRegistry meters;

	private DashboardTestSupport support;

	private TestPrincipals principals;

	private TestSession kurt;

	private TestSession franz;

	private String catalog;

	@BeforeEach
	void instance() {
		this.support = new DashboardTestSupport(this.mvc, this.jdbc);
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.catalog = this.support.catalog(this.kurt, unique("Application Inventory"), "instance");
	}

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static ObjectNode pos(int col, int row, int width, int height) {
		return DashboardTestSupport.position(col, row, width, height);
	}

	private String dataPath(Reply dashboard) {
		return DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/data";
	}

	private static JsonNode widget(Reply data, String type, int index) {
		int seen = 0;
		for (JsonNode widget : data.json().path("widgets")) {
			if (widget.path("type").asString().equals(type) && seen++ == index) {
				return widget;
			}
		}
		throw new AssertionError("no " + type + " widget " + index);
	}

	private static List<String> names(JsonNode table) {
		List<String> names = new ArrayList<>();
		table.path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		return names;
	}

	@Test
	void textWidgetIsStaticAndTableWidgetGivesTheFirstPage() {
		this.support.entities("Inventory Item", 47, this.support.personOf(this.kurt));
		Reply dashboard = this.support.created(this.kurt, unique("Data"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.textWidget("Notes", "**x**", pos(1, 1, 12, 1)));
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Application Overview", this.catalog, null, 10, pos(1, 2, 12, 3)));
		Reply data = this.support.get(dataPath(dashboard), this.franz);
		assertThat(data.status()).as(data.raw()).isEqualTo(200);
		assertThat(data.json().path("dashboardId").asString()).isEqualTo(dashboard.id());
		assertThat(data.json().path("generatedAt").asString()).isNotEmpty();
		JsonNode text = widget(data, "text", 0);
		assertThat(text.path("status").asString()).isEqualTo("static");
		assertThat(text.path("table").isMissingNode() || text.path("table").isNull()).isTrue();
		JsonNode table = widget(data, "table", 0);
		assertThat(table.path("status").asString()).isEqualTo("ok");
		assertThat(table.path("table").path("rows")).hasSize(10);
		assertThat(table.path("table").path("page").path("totalElements").asInt()).isEqualTo(47);
		assertThat(table.path("table").path("page").path("totalPages").asInt()).isEqualTo(5);
		assertThat(names(table.path("table")).get(0)).isEqualTo("Inventory Item 01");
	}

	@Test
	void furtherPagesAreReadPerWidget() {
		this.support.entities("Paged Item", 47, this.support.personOf(this.kurt));
		Reply dashboard = this.support.created(this.kurt, unique("Pages"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Application Overview", this.catalog, null, 10, pos(1, 1, 12, 3)));
		String widgetId = DashboardTestSupport.widgetId(dashboard, "Application Overview");
		String path = DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/widgets/" + widgetId + "/data";
		Reply second = this.support.get(path + "?page=1", this.franz);
		assertThat(second.status()).as(second.raw()).isEqualTo(200);
		List<String> names = names(second.json().path("table"));
		assertThat(names).hasSize(10);
		assertThat(names.get(0)).isEqualTo("Paged Item 11");
		assertThat(names.get(9)).isEqualTo("Paged Item 20");
		assertThat(second.json().path("table").path("page").path("page").asInt()).isEqualTo(1);
		// the last page and a page beyond it
		assertThat(names(this.support.get(path + "?page=4", this.franz).json().path("table"))).hasSize(7);
		assertThat(names(this.support.get(path + "?page=5", this.franz).json().path("table"))).isEmpty();
		// without a page, the first one
		assertThat(names(this.support.get(path, this.franz).json().path("table")).get(0)).isEqualTo("Paged Item 01");
		// a negative page and an unknown widget
		assertThat(this.support.get(path + "?page=-1", this.franz).status()).isEqualTo(400);
		assertThat(this.support
			.get(DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/widgets/" + UUID.randomUUID() + "/data",
					this.franz)
			.status()).isEqualTo(404);
	}

	@Test
	void aTextWidgetHasNoFurtherPage() {
		Reply dashboard = this.support.created(this.kurt, unique("Text"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.textWidget("Notes", "x", pos(1, 1, 12, 1)));
		String widgetId = DashboardTestSupport.widgetId(dashboard, "Notes");
		Reply reply = this.support
			.get(DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/widgets/" + widgetId + "/data?page=3", this.franz);
		assertThat(reply.status()).isEqualTo(200);
		assertThat(reply.json().path("status").asString()).isEqualTo("static");
	}

	@Test
	void aDeletedCatalogGivesAnErrorForThatWidgetOnly() throws SQLException {
		Reply dashboard = this.support.created(this.kurt, unique("Deleted"), "instance");
		String doomed = this.support.catalog(this.kurt, unique("Inventory"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.textWidget("Notes", "x", pos(1, 1, 12, 1)));
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Inventory", doomed, null, 10, pos(1, 2, 12, 3)));
		// there is no operation that deletes a catalog yet
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate("DELETE FROM catalogs WHERE id = '" + doomed + "'");
		}
		Reply data = this.support.get(dataPath(dashboard), this.kurt);
		assertThat(data.status()).as(data.raw()).isEqualTo(200);
		JsonNode table = widget(data, "table", 0);
		assertThat(table.path("status").asString()).isEqualTo("error");
		assertThat(table.path("error").path("code").asString()).isEqualTo("catalog-not-found");
		assertThat(table.path("error").path("message").asString()).isEqualTo("Catalog not found");
		assertThat(widget(data, "text", 0).path("status").asString()).isEqualTo("static");
	}

	@Test
	void aRemovedViewGivesAnErrorForThatWidget() {
		Reply dashboard = this.support.created(this.kurt, unique("Views"), "instance");
		String view = this.support.view(this.kurt, this.catalog, "Compact view");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("With view", this.catalog, view, 10, pos(1, 1, 12, 3)));
		assertThat(widget(this.support.get(dataPath(dashboard), this.kurt), "table", 0).path("status").asString())
			.isEqualTo("ok");
		Reply removed = this.support.call(HttpMethod.DELETE, DashboardTestSupport.CATALOGS + "/" + this.catalog
				+ "/views/" + view, this.kurt, null, null);
		assertThat(removed.status()).as(removed.raw()).isEqualTo(200);
		JsonNode table = widget(this.support.get(dataPath(dashboard), this.kurt), "table", 0);
		assertThat(table.path("status").asString()).isEqualTo("error");
		assertThat(table.path("error").path("code").asString()).isEqualTo("view-not-found");
	}

	@Test
	void anAdministratorDoesNotSeeTheRowsOfAnotherPersonsPersonalCatalog() {
		TestSession sandra = this.principals.personWithIdpRoles("viewer", "system-admin");
		String personal = this.support.catalog(this.franz, unique("Franz private"), "personal");
		Reply dashboard = this.support.created(this.franz, unique("Franz"), "personal");
		dashboard = this.support.addWidget(this.franz, dashboard,
				DashboardTestSupport.tableWidget("Mine", personal, null, 10, pos(1, 1, 12, 3)));
		JsonNode own = widget(this.support.get(dataPath(dashboard), this.franz), "table", 0);
		assertThat(own.path("status").asString()).isEqualTo("ok");
		Reply byAdmin = this.support.get(dataPath(dashboard), sandra);
		assertThat(byAdmin.status()).isEqualTo(200);
		JsonNode seen = widget(byAdmin, "table", 0);
		assertThat(seen.path("status").asString()).isEqualTo("error");
		assertThat(seen.path("error").path("code").asString()).isEqualTo("catalog-not-found");
		assertThat(seen.path("table").isMissingNode() || seen.path("table").isNull()).isTrue();
	}

	@Test
	void theQueryRunsOnTheDashboardChannel() {
		Reply dashboard = this.support.created(this.kurt, unique("Channel"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Metered", this.catalog, null, 10, pos(1, 1, 12, 3)));
		long before = count("dashboard");
		long catalogBefore = count("catalog");
		this.support.get(dataPath(dashboard), this.franz);
		assertThat(count("dashboard")).isEqualTo(before + 1);
		assertThat(count("catalog")).isEqualTo(catalogBefore);
	}

	private long count(String channel) {
		return this.meters.find("oea.catalog.query")
			.tag("channel", channel)
			.timers()
			.stream()
			.mapToLong(io.micrometer.core.instrument.Timer::count)
			.sum();
	}

	/** reader holds the role: whether the restricted column is readable */
	@ParameterizedTest
	@CsvSource({ "analyst, true", "viewer, false" })
	void aRestrictedColumnIsRedactedForTheReader(String role, boolean readable) {
		String key = "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		UUID property = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				VALUES (?, ?, 'string', 'role-restricted') RETURNING id""", UUID.class, "Secret " + key, key);
		this.jdbc.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?, 'analyst', 'read')",
				property);
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", this.support.metatype(), property);
		String restricted = this.support.catalog(this.kurt, unique("Restricted"), "instance");
		ObjectNode columns = JsonMapper.shared().createObjectNode();
		var array = columns.putArray("columns");
		array.add(JsonMapper.shared().createObjectNode().put("field", "name"));
		array.add(JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()));
		assertThat(this.support
			.call(HttpMethod.PUT, DashboardTestSupport.CATALOGS + "/" + restricted + "/columns", this.kurt, columns, null)
			.status()).isEqualTo(200);
		this.support.entities("Restricted Item", 1, this.support.personOf(this.kurt));
		Reply dashboard = this.support.created(this.kurt, unique("Redacted"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Restricted", restricted, null, 10, pos(1, 1, 12, 3)));
		TestSession reader = this.principals.personWithIdpRole(role);
		JsonNode table = widget(this.support.get(dataPath(dashboard), reader), "table", 0).path("table");
		JsonNode column = null;
		for (JsonNode candidate : table.path("columns")) {
			if (candidate.path("key").asString().equals(key)) {
				column = candidate;
			}
		}
		assertThat(column).as("column " + key).isNotNull();
		assertThat(column.path("readable").asBoolean(true)).isEqualTo(readable);
	}

	@Test
	void personalDashboardsOfOthersAreNotReadable() {
		Reply mine = this.support.created(this.franz, unique("Franz"), "personal");
		assertThat(this.support.get(dataPath(mine), this.kurt).status()).isEqualTo(404);
		assertThat(this.support.get(dataPath(mine), this.franz).status()).isEqualTo(200);
	}

}

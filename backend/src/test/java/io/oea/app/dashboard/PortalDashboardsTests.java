package io.oea.app.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.oea.app.dashboard.DashboardTestSupport.Reply;
import io.oea.app.portal.PortalDashboards;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.time.MutableClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec portal/portal-dashboards (US-061, US-060 AC5, REQ-055, REQ-056; design D1 to D3 of
 * us-061-portal-dashboards): the dashboards of the portal on an own database. Kurt is an
 * enterprise architect with {@code dashboard:manage}, Franz a viewer, Michael a viewer with a
 * personal dashboard, Petra holds only {@code portal-reader}, Sandra {@code settings:manage}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PortalDashboardsTests.ClockConfiguration.class)
class PortalDashboardsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = DashboardTestSupport.tempDirectory("oea-portal-dashboards")
			+ "/setup-token";

	private static final String PORTAL = "/api/v1/portal/dashboards";

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

	@Autowired
	private PortalDashboards portal;

	private DashboardTestSupport support;

	private TestPrincipals principals;

	private TestSession kurt;

	private TestSession franz;

	private TestSession michael;

	private TestSession petra;

	private TestSession sandra;

	private String catalog;

	@BeforeEach
	void instance() {
		this.support = new DashboardTestSupport(this.mvc, this.jdbc);
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.michael = this.principals.personWithIdpRole("viewer");
		this.petra = this.principals.personWithIdpRole("portal-reader");
		this.sandra = this.principals.personWithIdpRoles("viewer", "system-admin");
		this.catalog = this.support.catalog(this.kurt, unique("Application Inventory"), "instance");
	}

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static ObjectNode pos(int col, int row, int width, int height) {
		return DashboardTestSupport.position(col, row, width, height);
	}

	private List<String> names(Reply list, String prefix) {
		List<String> names = new ArrayList<>();
		list.json().forEach((summary) -> {
			if (summary.path("name").asString().startsWith(prefix)) {
				names.add(summary.path("name").asString());
			}
		});
		return names;
	}

	private static JsonNode byName(Reply list, String name) {
		for (JsonNode summary : list.json()) {
			if (summary.path("name").asString().equals(name)) {
				return summary;
			}
		}
		throw new AssertionError("not listed: " + name);
	}

	private static JsonNode widget(JsonNode data, String type, int index) {
		int seen = 0;
		for (JsonNode widget : data.path("widgets")) {
			if (widget.path("type").asString().equals(type) && seen++ == index) {
				return widget;
			}
		}
		throw new AssertionError("no " + type + " widget " + index);
	}

	private static JsonNode withoutCursors(JsonNode data) {
		ObjectNode copy = (ObjectNode) data.deepCopy();
		copy.path("widgets").forEach((widget) -> {
			if (widget.path("table") instanceof ObjectNode table) {
				table.remove("nextCursor");
			}
		});
		return copy;
	}

	private Reply portalGet(String suffix, TestSession session) {
		return this.support.get(PORTAL + suffix, session);
	}

	private long count(String channel) {
		Timer timer = this.meters.find("oea.catalog.query").tags("channel", channel, "outcome", "success").timer();
		return (timer != null) ? timer.count() : 0;
	}

	// ---- Requirement: List the portal dashboards

	@Test
	void aPortalReaderListsTheSharedDashboardsSortedByNameIgnoringCaseAndNeverEditable() {
		String tag = unique("Zlist");
		this.support.created(this.kurt, tag + " beta", "instance");
		this.support.created(this.kurt, tag + " Alpha", "instance");
		this.support.created(this.kurt, tag + " gamma", "instance");
		this.support.created(this.michael, tag + " My analysis", "personal");
		Reply list = portalGet("", this.petra);
		assertThat(list.status()).as(list.raw()).isEqualTo(200);
		assertThat(names(list, tag)).containsExactly(tag + " Alpha", tag + " beta", tag + " gamma");
		list.json().forEach((summary) -> assertThat(summary.path("canEdit").asBoolean()).isFalse());
		assertThat(byName(list, tag + " Alpha").path("scope").asString()).isEqualTo("instance");
		assertThat(byName(list, tag + " Alpha").path("owner").path("displayName").asString()).isNotEmpty();
	}

	@Test
	void theOwnerListsTheOwnPersonalDashboardAndNobodyElseDoes() {
		String name = unique("My analysis");
		this.support.created(this.michael, name, "personal");
		JsonNode own = byName(portalGet("", this.michael), name);
		assertThat(own.path("scope").asString()).isEqualTo("personal");
		assertThat(own.path("canEdit").asBoolean()).isFalse();
		assertThat(names(portalGet("", this.petra), name)).isEmpty();
		assertThat(names(portalGet("", this.franz), name)).isEmpty();
		// the work area shows it editable to its owner
		assertThat(byName(this.support.get(DashboardTestSupport.DASHBOARDS, this.michael), name).path("canEdit")
			.asBoolean()).isTrue();
	}

	@Test
	void anAdministratorDoesNotSeeAnotherPersonsPersonalDashboardInThePortalButInTheWorkArea() {
		String name = unique("My analysis");
		Reply mine = this.support.created(this.michael, name, "personal");
		assertThat(names(portalGet("", this.sandra), name)).isEmpty();
		assertThat(names(this.support.get(DashboardTestSupport.DASHBOARDS, this.sandra), name)).containsExactly(name);
		assertThat(this.support.get(DashboardTestSupport.VIEWS + "/" + mine.id(), this.sandra).status())
			.isEqualTo(200);
	}

	@Test
	void withoutPortalReadTheListIsForbidden() {
		TestSession nobody = this.principals.personWithoutRole();
		assertThat(portalGet("", nobody).status()).isEqualTo(403);
		assertThat(portalGet("", this.petra).status()).isEqualTo(200);
		assertThat(this.support.get(PORTAL, nobody).status()).isEqualTo(403);
	}

	@Test
	void serviceAccountsAreRejectedBeforeAnyLookupOnEveryOperation() {
		OeaPrincipal account = OeaPrincipal.serviceAccount(1L, UUID.randomUUID(), "robot", Instant.now());
		UUID unknown = UUID.randomUUID();
		assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(() -> this.portal.list(account));
		assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(() -> this.portal.get(account, unknown));
		assertThatExceptionOfType(PermissionDeniedException.class)
			.isThrownBy(() -> this.portal.data(account, unknown));
		assertThatExceptionOfType(PermissionDeniedException.class)
			.isThrownBy(() -> this.portal.widget(account, unknown, unknown, 0));
	}

	// ---- Requirement: Read a dashboard in the portal

	@Test
	void aPortalReaderReadsTheConfigurationOfASharedDashboardNeverEditable() {
		Reply dashboard = this.support.created(this.kurt, unique("IT Investment Planning"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.textWidget("Notes", "**x**", pos(1, 1, 6, 1)));
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Apps", this.catalog, null, 10, pos(7, 1, 6, 3)));
		Reply config = portalGet("/" + dashboard.id(), this.petra);
		assertThat(config.status()).as(config.raw()).isEqualTo(200);
		assertThat(config.etag()).isNull();
		assertThat(config.json().path("canEdit").asBoolean()).isFalse();
		assertThat(config.json().path("widgets").size()).isEqualTo(2);
		JsonNode table = config.json().path("widgets").get(1);
		assertThat(table.path("position").path("col").asInt()).isEqualTo(7);
		assertThat(table.path("position").path("width").asInt()).isEqualTo(6);
		assertThat(table.path("config").path("catalogId").asString()).isEqualTo(this.catalog);
		assertThat(table.path("config").path("pageSize").asInt()).isEqualTo(10);
	}

	@Test
	void aManagerReadsASharedDashboardWithoutEditRightsInThePortalAndWithThemInTheWorkArea() {
		Reply dashboard = this.support.created(this.kurt, unique("Managed"), "instance");
		assertThat(portalGet("/" + dashboard.id(), this.kurt).json().path("canEdit").asBoolean()).isFalse();
		assertThat(this.support.get(DashboardTestSupport.VIEWS + "/" + dashboard.id(), this.kurt)
			.json()
			.path("canEdit")
			.asBoolean()).isTrue();
	}

	@Test
	void theOwnerReadsThePersonalDashboardAndItsData() {
		Reply mine = this.support.created(this.michael, unique("My analysis"), "personal");
		mine = this.support.addWidget(this.michael, mine,
				DashboardTestSupport.textWidget("Notes", "mine", pos(1, 1, 6, 1)));
		Reply config = portalGet("/" + mine.id(), this.michael);
		assertThat(config.status()).isEqualTo(200);
		assertThat(config.json().path("canEdit").asBoolean()).isFalse();
		assertThat(config.json().path("widgets").size()).isEqualTo(1);
		Reply data = portalGet("/" + mine.id() + "/data", this.michael);
		assertThat(data.status()).isEqualTo(200);
		assertThat(widget(data.json(), "text", 0).path("status").asString()).isEqualTo("static");
	}

	@Test
	void anotherPersonsPersonalDashboardIsNotFoundForReaderAndAdministratorOnEveryRead() {
		Reply mine = this.support.created(this.michael, unique("My analysis"), "personal");
		mine = this.support.addWidget(this.michael, mine,
				DashboardTestSupport.textWidget("Notes", "secret", pos(1, 1, 6, 1)));
		String widgetId = DashboardTestSupport.widgetId(mine, "Notes");
		String unknown = UUID.randomUUID().toString();
		String unknownBody = portalGet("/" + unknown, this.petra).raw().replace(unknown, "ID");
		for (TestSession session : List.of(this.petra, this.sandra, this.franz)) {
			for (String suffix : List.of("", "/data", "/widgets/" + widgetId + "/data")) {
				Reply reply = portalGet("/" + mine.id() + suffix, session);
				assertThat(reply.status()).as(suffix + " " + reply.raw()).isEqualTo(404);
				assertThat(reply.raw()).doesNotContain("secret");
			}
			assertThat(portalGet("/" + mine.id(), session).raw().replace(mine.id(), "ID")).isEqualTo(unknownBody);
		}
	}

	@Test
	void anUnknownDashboardOrWidgetIsNotFound() {
		Reply shared = this.support.created(this.kurt, unique("Shared"), "instance");
		String unknown = UUID.randomUUID().toString();
		assertThat(portalGet("/" + unknown, this.petra).status()).isEqualTo(404);
		assertThat(portalGet("/" + unknown + "/data", this.petra).status()).isEqualTo(404);
		assertThat(portalGet("/" + unknown + "/widgets/" + unknown + "/data", this.petra).status()).isEqualTo(404);
		assertThat(portalGet("/" + shared.id() + "/widgets/" + unknown + "/data", this.petra).status())
			.isEqualTo(404);
	}

	@Test
	void withoutPortalReadEveryOperationIsForbiddenAlsoForAnUnknownDashboard() {
		TestSession nobody = this.principals.personWithoutRole();
		Reply shared = this.support.created(this.kurt, unique("Shared"), "instance");
		String unknown = UUID.randomUUID().toString();
		for (String id : List.of(shared.id(), unknown)) {
			assertThat(portalGet("/" + id, nobody).status()).isEqualTo(403);
			assertThat(portalGet("/" + id + "/data", nobody).status()).isEqualTo(403);
			assertThat(portalGet("/" + id + "/widgets/" + unknown + "/data", nobody).status()).isEqualTo(403);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "/not-a-uuid", "/not-a-uuid/data", "/00000000-0000-4000-8000-000000000001/widgets/x/data" })
	void anIdThatIsNoUuidIsRejected(String suffix) {
		assertThat(portalGet(suffix, this.petra).status()).isEqualTo(400);
	}

	// ---- Requirement: Widget data in the portal

	@Test
	void theWidgetDataEqualsTheWorkAreaDataForTheSameReader() {
		this.support.entities("Inventory Item", 12, this.support.personOf(this.kurt));
		UUID type = this.support.elementType("Application", null, null);
		UUID cost = this.support.property("Cost", "integer", "public");
		UUID status = this.support.property("Status", "string", "public");
		this.support.map(type, cost);
		this.support.map(type, status);
		for (int i = 0; i < 4; i++) {
			int entity = this.support.entity(type, unique("App"), this.support.personOf(this.kurt));
			this.support.integer(entity, cost, 10 * (i + 1));
			this.support.text(entity, status, (i % 2 == 0) ? "live" : "retired");
		}
		Reply dashboard = this.support.created(this.kurt, unique("Parity"), "instance");
		ObjectNode kpi = DashboardTestSupport.kpiWidget("Cost", DashboardTestSupport.aggregation(type, cost, "sum", null),
				pos(1, 1, 3, 1));
		DashboardTestSupport.config(kpi).put("comparison", "vs-previous-value");
		dashboard = this.support.addWidget(this.kurt, dashboard, kpi);
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.chartWidget("By status",
						DashboardTestSupport.aggregation(type, null, "count",
								DashboardTestSupport.groupBy("property", status.toString())),
						"bar", pos(4, 1, 6, 2)));
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.textWidget("Notes", "**x**", pos(1, 2, 3, 1)));
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Apps", this.catalog, null, 10, pos(1, 3, 12, 3)));
		// a widget that fails next to the others
		dashboard = this.support.addWidget(this.kurt, dashboard, DashboardTestSupport.tableWidget("Gone",
				this.catalog, null, 10, pos(1, 6, 12, 3)));
		this.jdbc.update("UPDATE dashboard_widgets SET config = jsonb_build_object('catalogId', ?::text, 'pageSize', 10) WHERE title = 'Gone'",
				UUID.randomUUID().toString());
		Reply work = this.support.get(DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/data", this.franz);
		Reply portal = portalGet("/" + dashboard.id() + "/data", this.franz);
		assertThat(portal.status()).as(portal.raw()).isEqualTo(200);
		// the cursors are bound to their channel, everything else is equal
		assertThat(withoutCursors(portal.json())).isEqualTo(withoutCursors(work.json()));
		assertThat(widget(portal.json(), "kpi", 0).path("status").asString()).isEqualTo("ok");
		assertThat(widget(portal.json(), "kpi", 0).path("previousAt").isString()).isTrue();
		assertThat(widget(portal.json(), "chart", 0).path("series").size()).isEqualTo(2);
		JsonNode gone = widget(portal.json(), "table", 1);
		assertThat(gone.path("status").asString()).isEqualTo("error");
		assertThat(gone.path("error").path("code").asString()).isEqualTo("catalog-not-found");
		assertThat(widget(portal.json(), "table", 0).path("status").asString()).isEqualTo("ok");
		// the configuration of the portal equals that of the work area apart from edit rights
		ObjectNode portalConfig = (ObjectNode) portalGet("/" + dashboard.id(), this.franz).json().deepCopy();
		ObjectNode workConfig = (ObjectNode) this.support.get(DashboardTestSupport.VIEWS + "/" + dashboard.id(),
				this.franz).json().deepCopy();
		assertThat(portalConfig).isEqualTo(workConfig.put("canEdit", false));
	}

	@Test
	void aPortalReaderWithoutEntityReadGetsTheDataAndAFailingWidgetDoesNotHideTheOthers() {
		UUID type = this.support.elementType("Application", null, null);
		UUID cost = this.support.property("Cost", "integer", "public");
		this.support.map(type, cost);
		this.support.integer(this.support.entity(type, unique("App"), this.support.personOf(this.kurt)), cost, 42);
		Reply dashboard = this.support.created(this.kurt, unique("Failing"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard, DashboardTestSupport.kpiWidget("Cost",
				DashboardTestSupport.aggregation(type, cost, "sum", null), pos(1, 1, 3, 1)));
		UUID tmp = this.support.property("Tmp", "string", "public");
		this.support.map(type, tmp);
		dashboard = this.support.addWidget(this.kurt, dashboard, DashboardTestSupport.chartWidget("Broken",
				DashboardTestSupport.aggregation(type, cost, "sum", DashboardTestSupport.groupBy("property", tmp.toString())),
				"bar", pos(4, 1, 3, 1)));
		this.jdbc.update("""
				UPDATE dashboard_widgets SET config = jsonb_set(config, '{aggregation,groupBy,propertyDefId}',
				to_jsonb(?::text)) WHERE title = 'Broken'""", UUID.randomUUID().toString());
		JsonNode data = portalGet("/" + dashboard.id() + "/data", this.petra).json();
		assertThat(widget(data, "kpi", 0).path("value").asDouble()).isEqualTo(42.0);
		assertThat(widget(data, "chart", 0).path("status").asString()).isEqualTo("error");
		assertThat(widget(data, "chart", 0).path("error").path("code").asString()).isEqualTo("invalid-configuration");
	}

	@Test
	void aRestrictedPropertyIsRestrictedInThePortalToo() {
		UUID type = this.support.elementType("Application", null, null);
		UUID investment = this.support.property("Investment", "integer", "role-restricted");
		this.support.readRole(investment, "analyst");
		this.support.map(type, investment);
		this.support.integer(this.support.entity(type, unique("App"), this.support.personOf(this.kurt)), investment, 5);
		Reply dashboard = this.support.created(this.kurt, unique("Restricted"), "instance");
		this.support.addWidget(this.kurt, dashboard, DashboardTestSupport.kpiWidget("Investment",
				DashboardTestSupport.aggregation(type, investment, "sum", null), pos(1, 1, 3, 1)));
		JsonNode kpi = widget(portalGet("/" + dashboard.id() + "/data", this.petra).json(), "kpi", 0);
		assertThat(kpi.path("status").asString()).isEqualTo("error");
		assertThat(kpi.path("error").path("code").asString()).isEqualTo("restricted");
		assertThat(kpi.path("value").isNull() || kpi.path("value").isMissingNode()).isTrue();
	}

	@Test
	void theFurtherPagesOfATableWidgetAreReadPerWidgetAndANegativePageIsRejected() {
		UUID type = this.support.metatype();
		for (int i = 1; i <= 47; i++) {
			this.jdbc.update("INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?)", type,
					"Paged %02d".formatted(i), this.support.personOf(this.kurt));
		}
		String paged = this.support.catalog(this.kurt, unique("Paged"), "instance");
		Reply dashboard = this.support.created(this.kurt, unique("Pages"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Rows", paged, null, 10, pos(1, 1, 12, 3)));
		String base = "/" + dashboard.id() + "/widgets/" + DashboardTestSupport.widgetId(dashboard, "Rows")
				+ "/data";
		JsonNode first = portalGet(base + "?page=0", this.petra).json().path("table");
		JsonNode second = portalGet(base + "?page=1", this.petra).json().path("table");
		assertThat(first.path("rows").size()).isEqualTo(10);
		assertThat(second.path("rows").size()).isEqualTo(10);
		assertThat(second.path("page").path("totalElements").asInt()).isEqualTo(47);
		assertThat(second.path("rows").get(0).path("values").path("name").asString()).isNotEqualTo(
				first.path("rows").get(0).path("values").path("name").asString());
		// the default page is page 0
		assertThat(portalGet(base, this.petra).json().path("table")).isEqualTo(first);
		// the last page holds the remaining 7 rows, a page after it is empty
		assertThat(portalGet(base + "?page=4", this.petra).json().path("table").path("rows").size()).isEqualTo(7);
		assertThat(portalGet(base + "?page=5", this.petra).json().path("table").path("rows").size()).isZero();
		assertThat(portalGet(base + "?page=-1", this.petra).status()).isEqualTo(400);
	}

	@Test
	void aTableWidgetIsMeasuredWithTheChannelPortalAndTheWorkAreaWithDashboard() {
		Reply dashboard = this.support.created(this.kurt, unique("Channel"), "instance");
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.tableWidget("Metered", this.catalog, null, 10, pos(1, 1, 12, 3)));
		long portal = count("portal");
		long work = count("dashboard");
		assertThat(portalGet("/" + dashboard.id() + "/data", this.petra).status()).isEqualTo(200);
		assertThat(count("portal")).isEqualTo(portal + 1);
		assertThat(count("dashboard")).isEqualTo(work);
		assertThat(this.support.get(DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/data", this.franz)
			.status()).isEqualTo(200);
		assertThat(count("dashboard")).isEqualTo(work + 1);
		assertThat(count("portal")).isEqualTo(portal + 1);
	}

	@Test
	void aTableOnTheOwnersPersonalCatalogShowsItsRowsOnTheOwnersPersonalDashboard() {
		this.support.entities("Mine", 3, this.support.personOf(this.michael));
		String personal = this.support.catalog(this.michael, unique("Michael private"), "personal");
		Reply dashboard = this.support.created(this.michael, unique("My analysis"), "personal");
		dashboard = this.support.addWidget(this.michael, dashboard,
				DashboardTestSupport.tableWidget("Mine", personal, null, 10, pos(1, 1, 12, 3)));
		JsonNode table = widget(portalGet("/" + dashboard.id() + "/data", this.michael).json(), "table", 0);
		assertThat(table.path("status").asString()).isEqualTo("ok");
		assertThat(table.path("table").path("rows").size()).isGreaterThan(0);
	}

	@TestConfiguration
	static class ClockConfiguration {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}

	}

}

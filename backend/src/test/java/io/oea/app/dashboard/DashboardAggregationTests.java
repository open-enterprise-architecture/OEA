package io.oea.app.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.oea.app.dashboard.DashboardTestSupport.Reply;
import io.oea.app.security.Sessions;
import io.oea.core.entity.EntityAggregationStore;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec dashboard/widgets "Property aggregation", "KPI widget", "Chart widget", "Aggregates respect
 * visibility" and "Widgets after metamodel changes" (US-056, US-057; design D3 to D5 of
 * us-056-kpi-and-chart-widgets): the data of KPI and chart widgets.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardAggregationTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = DashboardTestSupport.tempDirectory("oea-dashboard-aggregation")
			+ "/setup-token";

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

	@MockitoSpyBean
	private EntityAggregationStore store;

	private DashboardTestSupport support;

	private TestPrincipals principals;

	private TestSession kurt;

	private TestSession franz;

	private long kurtId;

	@BeforeEach
	void instance() {
		this.support = new DashboardTestSupport(this.mvc, this.jdbc);
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.kurtId = this.support.personOf(this.kurt);
	}

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static ObjectNode pos() {
		return DashboardTestSupport.position(1, 1, 6, 2);
	}

	private Reply shared() {
		return this.support.created(this.kurt, unique("Aggregation"), "instance");
	}

	/** Adds the widget to a new shared dashboard and reads its data as the reader. */
	private JsonNode compute(ObjectNode widget, TestSession reader) {
		Reply dashboard = this.support.addWidget(this.kurt, shared(), widget);
		String id = DashboardTestSupport.widgetId(dashboard, widget.path("title").asString());
		Reply data = this.support.get(DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/widgets/" + id + "/data",
				reader);
		assertThat(data.status()).as(data.raw()).isEqualTo(200);
		return data.json();
	}

	private JsonNode kpi(ObjectNode aggregation, TestSession reader) {
		return compute(DashboardTestSupport.kpiWidget("KPI", aggregation, pos()), reader);
	}

	private JsonNode chart(ObjectNode aggregation, String type, TestSession reader) {
		return compute(DashboardTestSupport.chartWidget("Chart", aggregation, type, pos()), reader);
	}

	private static ObjectNode agg(Object type, Object property, String function) {
		return DashboardTestSupport.aggregation(type, property, function, null);
	}

	private static ObjectNode group(Object type, Object property, String function, String kind, Object groupProperty) {
		return DashboardTestSupport.aggregation(type, property, function,
				DashboardTestSupport.groupBy(kind, (groupProperty != null) ? groupProperty.toString() : null));
	}

	/** {@code kind:label=value} of each group. */
	private static List<String> series(JsonNode data) {
		List<String> entries = new ArrayList<>();
		for (JsonNode entry : data.path("series")) {
			String label = entry.path("label").isString() ? entry.path("label").asString() : "";
			entries.add(entry.path("kind").asString() + ":" + label + "=" + entry.path("value").asDouble());
		}
		return entries;
	}

	private static void assertError(JsonNode data, String code) {
		assertThat(data.path("status").asString()).as(data.toString()).isEqualTo("error");
		assertThat(data.path("error").path("code").asString()).isEqualTo(code);
		assertThat(data.path("value").isNull() || data.path("value").isMissingNode()).isTrue();
		assertThat(data.path("series").isNull() || data.path("series").isMissingNode()
				|| data.path("series").isEmpty()).isTrue();
	}

	// ---- KPI

	@Test
	void theSumIgnoresDeletedEntitiesOtherTypesAndMissingValues() {
		UUID type = this.support.elementType("Application", null, null);
		UUID other = this.support.elementType("Server", null, null);
		UUID investment = this.support.property("Investment", "integer", "public");
		this.support.map(type, investment);
		this.support.map(other, investment);
		for (long value : new long[] { 5_000_000, 12_000_000, 25_000_000 }) {
			this.support.integer(this.support.entity(type, unique("App"), this.kurtId), investment, value);
		}
		int gone = this.support.entity(type, unique("Gone"), this.kurtId);
		this.support.integer(gone, investment, 1_000_000_000);
		this.support.deleted(gone, this.kurtId);
		this.support.integer(this.support.entity(other, unique("Box"), this.kurtId), investment, 7);
		this.support.entity(type, unique("No value"), this.kurtId);

		JsonNode data = kpi(agg(type, investment, "sum"), this.franz);
		assertThat(data.path("status").asString()).isEqualTo("ok");
		assertThat(data.path("value").asDouble()).isEqualTo(42_000_000.0);
		assertThat(data.path("target").isNull() || data.path("target").isMissingNode()).isTrue();
		assertThat(data.path("series").isMissingNode() || data.path("series").isNull()).isTrue();
	}

	/** function, expected value over the values 10, 20, 60 and one entity without a value */
	@ParameterizedTest
	@CsvSource({ "count, 3", "sum, 90", "avg, 30", "min, 10", "max, 60" })
	void everyFunctionOverAnIntegerProperty(String function, double expected) {
		UUID type = this.support.elementType("Application", null, null);
		UUID cost = this.support.property("Cost", "integer", "public");
		this.support.map(type, cost);
		for (long value : new long[] { 10, 20, 60 }) {
			this.support.integer(this.support.entity(type, unique("App"), this.kurtId), cost, value);
		}
		this.support.entity(type, unique("No value"), this.kurtId);
		assertThat(kpi(agg(type, cost, function), this.franz).path("value").asDouble()).isEqualTo(expected);
	}

	@Test
	void theAverageIsNotRoundedToAnInteger() {
		UUID type = this.support.elementType("Application", null, null);
		UUID cost = this.support.property("Cost", "integer", "public");
		this.support.map(type, cost);
		for (long value : new long[] { 1, 2 }) {
			this.support.integer(this.support.entity(type, unique("App"), this.kurtId), cost, value);
		}
		assertThat(kpi(agg(type, cost, "avg"), this.franz).path("value").asDouble()).isEqualTo(1.5);
	}

	@Test
	void countIncludesSubtypesAndAllEntitiesOfAType() {
		UUID type = this.support.elementType("Application", null, null);
		UUID subtype = this.support.elementType("Cloud Application", type, null);
		for (int i = 0; i < 14; i++) {
			this.support.entity(type, unique("App"), this.kurtId);
		}
		this.support.entity(subtype, unique("Cloud"), this.kurtId);
		this.support.deleted(this.support.entity(type, unique("Gone"), this.kurtId), this.kurtId);
		assertThat(kpi(agg(type, null, "count"), this.franz).path("value").asDouble()).isEqualTo(15.0);
		// the subtype alone does not include the base type
		assertThat(kpi(agg(subtype, null, "count"), this.franz).path("value").asDouble()).isEqualTo(1.0);
	}

	@Test
	void countOfAPropertyCountsTheEntitiesWithAValueOfAnyType() {
		UUID type = this.support.elementType("Application", null, null);
		UUID vendor = this.support.property("Vendor", "string", "public");
		this.support.map(type, vendor);
		this.support.text(this.support.entity(type, unique("A"), this.kurtId), vendor, "SAP");
		this.support.text(this.support.entity(type, unique("B"), this.kurtId), vendor, "Oracle");
		this.support.entity(type, unique("C"), this.kurtId);
		assertThat(kpi(agg(type, vendor, "count"), this.franz).path("value").asDouble()).isEqualTo(2.0);
	}

	@Test
	void countWithoutAMetatypeCountsAllElementEntities() {
		UUID type = this.support.elementType("Application", null, null);
		int before = this.support.count("SELECT count(*) FROM entities WHERE deleted_at IS NULL");
		this.support.entity(type, unique("A"), this.kurtId);
		this.support.entity(type, unique("B"), this.kurtId);
		this.support.deleted(this.support.entity(type, unique("Gone"), this.kurtId), this.kurtId);
		assertThat(kpi(agg(null, null, "count"), this.franz).path("value").asDouble()).isEqualTo(before + 2.0);
	}

	/** function, expected value of a KPI over no values */
	@ParameterizedTest
	@CsvSource({ "sum, null", "avg, null", "min, null", "max, null", "count, 0" })
	void aKpiOverNoValuesHasNoDataExceptCount(String function, String expected) {
		UUID type = this.support.elementType("Application", null, null);
		UUID cost = this.support.property("Cost", "integer", "public");
		this.support.map(type, cost);
		this.support.entity(type, unique("No value"), this.kurtId);
		JsonNode data = kpi(agg(type, cost, function), this.franz);
		assertThat(data.path("status").asString()).isEqualTo("ok");
		if (expected.equals("null")) {
			assertThat(data.path("value").isNull() || data.path("value").isMissingNode()).isTrue();
		}
		else {
			assertThat(data.path("value").asDouble()).isEqualTo(Double.parseDouble(expected));
		}
	}

	@Test
	void theTargetIsPassedOnOnlyForVsTarget() {
		UUID type = this.support.elementType("Application", null, null);
		UUID cost = this.support.property("Cost", "integer", "public");
		this.support.map(type, cost);
		this.support.integer(this.support.entity(type, unique("A"), this.kurtId), cost, 42);
		ObjectNode withTarget = DashboardTestSupport.kpiWidget("KPI", agg(type, cost, "sum"), pos());
		DashboardTestSupport.config(withTarget).put("comparison", "vs-target").put("targetValue", 50);
		JsonNode data = compute(withTarget, this.franz);
		assertThat(data.path("value").asDouble()).isEqualTo(42.0);
		assertThat(data.path("target").asDouble()).isEqualTo(50.0);
		ObjectNode without = DashboardTestSupport.kpiWidget("KPI", agg(type, cost, "sum"), pos());
		DashboardTestSupport.config(without).put("comparison", "none");
		assertThat(compute(without, this.franz).path("target").isNull()).isTrue();
	}

	// ---- chart

	@Test
	void investmentPerPlateauHasTheLabelsOfTheEnumValuesAndNone() {
		UUID type = this.support.elementType("Application", null, null);
		UUID investment = this.support.property("Investment", "integer", "public");
		UUID plateau = this.support.property("Plateau", "enum", "public");
		this.support.map(type, investment);
		this.support.map(type, plateau);
		UUID p1 = this.support.enumValue(plateau, "P1");
		this.support.enumValue(plateau, "P2");
		this.support.enumValue(plateau, "P3");
		String[][] rows = { { "P1", "5000000" }, { "P1", "7000000" }, { "P2", "12000000" }, { "P3", "25000000" },
				{ null, "1000000" } };
		for (String[] row : rows) {
			int entity = this.support.entity(type, unique("App"), this.kurtId);
			this.support.integer(entity, investment, Long.parseLong(row[1]));
			if (row[0] != null) {
				this.support.enumeration(entity, plateau, row[0]);
			}
		}
		// a plateau without an investment does not count
		this.support.enumeration(this.support.entity(type, unique("App"), this.kurtId), plateau, "P2");
		JsonNode data = chart(group(type, investment, "sum", "property", plateau), "bar", this.franz);
		assertThat(series(data)).containsExactly("value:P3=2.5E7", "value:P1=1.2E7", "value:P2=1.2E7", "none:=1000000.0");
		JsonNode first = data.path("series").get(1);
		assertThat(first.path("key").asString()).isEqualTo("P1");
		assertThat(first.path("labelKey").asString()).isEqualTo("enum-value." + p1 + ".label");
		JsonNode none = data.path("series").get(3);
		assertThat(none.path("key").isNull() || none.path("key").isMissingNode()).isTrue();
		assertThat(none.path("labelKey").isNull() || none.path("labelKey").isMissingNode()).isTrue();
	}

	@Test
	void groupingByAStringAndByABoolean() {
		UUID type = this.support.elementType("Application", null, null);
		UUID vendor = this.support.property("Vendor", "string", "public");
		UUID critical = this.support.property("Critical", "boolean", "public");
		this.support.map(type, vendor);
		this.support.map(type, critical);
		String[] vendors = { "SAP", "SAP", "Oracle", null };
		Boolean[] flags = { true, false, true, null };
		for (int i = 0; i < vendors.length; i++) {
			int entity = this.support.entity(type, unique("App"), this.kurtId);
			if (vendors[i] != null) {
				this.support.text(entity, vendor, vendors[i]);
			}
			if (flags[i] != null) {
				this.support.bool(entity, critical, flags[i]);
			}
		}
		JsonNode byVendor = chart(group(type, null, "count", "property", vendor), "pie", this.franz);
		assertThat(series(byVendor)).containsExactly("value:SAP=2.0", "value:Oracle=1.0", "none:=1.0");
		assertThat(byVendor.path("series").get(0).path("key").asString()).isEqualTo("SAP");
		assertThat(byVendor.path("series").get(0).path("labelKey").isNull()).isTrue();
		JsonNode byFlag = chart(group(type, null, "count", "property", critical), "pie", this.franz);
		assertThat(series(byFlag)).containsExactly("true:=2.0", "false:=1.0", "none:=1.0");
	}

	@Test
	void groupingByAReferenceUsesTheNameOfTheReferencedEntity() {
		UUID type = this.support.elementType("Application", null, null);
		UUID hosting = this.support.property("Hosted by", "reference", "public");
		this.support.map(type, hosting);
		int datacenter = this.support.entity(this.support.metatype(), "Data Center <b>Zurich</b>", this.kurtId);
		int cloud = this.support.entity(this.support.metatype(), "Cloud", this.kurtId);
		for (int target : new int[] { datacenter, datacenter, cloud }) {
			this.support.reference(this.support.entity(type, unique("App"), this.kurtId), hosting, target);
		}
		JsonNode data = chart(group(type, null, "count", "property", hosting), "treemap", this.franz);
		assertThat(series(data)).containsExactly("value:Data Center <b>Zurich</b>=2.0", "value:Cloud=1.0");
		assertThat(data.path("series").get(0).path("key").asString()).isEqualTo(Integer.toString(datacenter));
	}

	@Test
	void groupingByMetatypeAndByLayerFoldsSubtypesAndHasNone() {
		UUID business = this.support.layer("Business");
		UUID application = this.support.layer("Application");
		UUID root = this.support.elementType("Root", null, null);
		UUID inBusiness = this.support.elementType("Process", root, business);
		UUID inApplication = this.support.elementType("Application Component", root, application);
		UUID alsoApplication = this.support.elementType("Application Service", root, application);
		UUID plain = this.support.elementType("Plain", root, null);
		int[][] counts = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 1 }, { 4, 1 } };
		UUID[] types = { root, inBusiness, inApplication, alsoApplication, plain };
		for (int[] count : counts) {
			for (int i = 0; i < count[1]; i++) {
				this.support.entity(types[count[0]], unique("E"), this.kurtId);
			}
		}
		JsonNode byType = chart(group(root, null, "count", "metatype", null), "bar", this.franz);
		assertThat(byType.path("series")).hasSize(5);
		JsonNode largest = byType.path("series").get(0);
		assertThat(largest.path("key").asString()).isEqualTo(inApplication.toString());
		assertThat(largest.path("labelKey").asString()).isEqualTo("metatype." + inApplication + ".name");
		assertThat(largest.path("label").asString()).startsWith("Application Component ");
		assertThat(largest.path("value").asDouble()).isEqualTo(3.0);

		JsonNode byLayer = chart(group(root, null, "count", "layer", null), "pie", this.franz);
		JsonNode entries = byLayer.path("series");
		assertThat(entries).hasSize(3);
		// Application 3 + 1, Business 2, none 1 + 1
		assertThat(entries.get(0).path("value").asDouble()).isEqualTo(4.0);
		assertThat(entries.get(0).path("key").asString()).isEqualTo(application.toString());
		assertThat(entries.get(0).path("labelKey").asString()).isEqualTo("architecture-layer." + application + ".name");
		assertThat(entries.get(0).path("label").asString()).startsWith("Application ");
		assertThat(entries.get(1).path("value").asDouble()).isEqualTo(2.0);
		assertThat(entries.get(1).path("key").asString()).isEqualTo(business.toString());
		assertThat(entries.get(2).path("kind").asString()).isEqualTo("none");
		assertThat(entries.get(2).path("value").asDouble()).isEqualTo(2.0);

		// the deleted layer moves its metatypes into "(none)"
		this.jdbc.update("UPDATE metatypes SET architecture_layer_id = NULL WHERE architecture_layer_id = ?", application);
		this.jdbc.update("DELETE FROM architecture_layers WHERE id = ?", application);
		JsonNode after = chart(group(root, null, "count", "layer", null), "pie", this.franz).path("series");
		assertThat(after).hasSize(2);
		assertThat(after.get(0).path("kind").asString()).isEqualTo("none");
		assertThat(after.get(0).path("value").asDouble()).isEqualTo(6.0);
		assertThat(after.get(1).path("key").asString()).isEqualTo(business.toString());
	}

	/** groups, expected groups shown, whether "Other" is shown */
	@ParameterizedTest
	@CsvSource({ "1, 1, false", "49, 49, false", "50, 50, false", "51, 50, true", "60, 50, true" })
	void atMostFiftyGroupsAreShownAndTheRestIsOther(int groups, int shown, boolean other) {
		UUID type = this.support.elementType("Application", null, null);
		UUID vendor = this.support.property("Vendor", "string", "public");
		this.support.map(type, vendor);
		for (int i = 1; i <= groups; i++) {
			this.support.text(this.support.entity(type, unique("App"), this.kurtId), vendor, "v%02d".formatted(i));
		}
		JsonNode series = chart(group(type, null, "count", "property", vendor), "bar", this.franz).path("series");
		assertThat(series).hasSize(shown + (other ? 1 : 0));
		assertThat(series.get(shown - 1).path("kind").asString()).isEqualTo("value");
		if (other) {
			JsonNode last = series.get(shown);
			assertThat(last.path("kind").asString()).isEqualTo("other");
			assertThat(last.path("value").asDouble()).isEqualTo(groups - 50.0);
			assertThat(last.path("key").isNull() || last.path("key").isMissingNode()).isTrue();
		}
	}

	/**
	 * function, the value of "Other": 53 groups with the value of their number, the first group
	 * with a second entity of the value 1; for the count, the group with two entities is shown,
	 * the rest are the last three groups by label
	 */
	@ParameterizedTest
	@CsvSource({ "count, 3", "sum, 7", "avg, 1.75", "min, 1", "max, 3" })
	void theRestIsFoldedWithTheSameFunction(String function, double expected) {
		UUID type = this.support.elementType("Application", null, null);
		UUID vendor = this.support.property("Vendor", "string", "public");
		UUID cost = this.support.property("Cost", "integer", "public");
		this.support.map(type, vendor);
		this.support.map(type, cost);
		for (int i = 1; i <= 53; i++) {
			int entity = this.support.entity(type, unique("App"), this.kurtId);
			this.support.text(entity, vendor, "v%02d".formatted(i));
			this.support.integer(entity, cost, i);
		}
		int twin = this.support.entity(type, unique("Twin"), this.kurtId);
		this.support.text(twin, vendor, "v01");
		this.support.integer(twin, cost, 1);
		JsonNode series = chart(group(type, cost, function, "property", vendor), "bar", this.franz).path("series");
		assertThat(series).hasSize(51);
		JsonNode last = series.get(50);
		assertThat(last.path("kind").asString()).isEqualTo("other");
		assertThat(last.path("value").asDouble()).isEqualTo(expected);
	}

	@Test
	void groupsAreOrderedByValueDescendingThenByLabel() {
		UUID type = this.support.elementType("Application", null, null);
		UUID vendor = this.support.property("Vendor", "string", "public");
		this.support.map(type, vendor);
		for (String[] row : new String[][] { { "beta", "2" }, { "alpha", "2" }, { "Zeta", "3" }, { "gamma", "1" } }) {
			for (int i = 0; i < Integer.parseInt(row[1]); i++) {
				this.support.text(this.support.entity(type, unique("App"), this.kurtId), vendor, row[0]);
			}
		}
		assertThat(series(chart(group(type, null, "count", "property", vendor), "bar", this.franz)))
			.containsExactly("value:Zeta=3.0", "value:alpha=2.0", "value:beta=2.0", "value:gamma=1.0");
	}

	@Test
	void aChartOverNoEntitiesHasNoGroups() {
		UUID type = this.support.elementType("Application", null, null);
		UUID vendor = this.support.property("Vendor", "string", "public");
		this.support.map(type, vendor);
		JsonNode data = chart(group(type, null, "count", "property", vendor), "bar", this.franz);
		assertThat(data.path("status").asString()).isEqualTo("ok");
		assertThat(data.path("series")).isEmpty();
	}

	// ---- visibility

	/** reader role, whether the sum is readable */
	@ParameterizedTest
	@CsvSource({ "analyst, true", "viewer, false" })
	void aRoleRestrictedPropertyIsAggregatedOnlyForAReadRole(String role, boolean readable) {
		UUID type = this.support.elementType("Application", null, null);
		UUID investment = this.support.property("Investment", "integer", "role-restricted");
		this.support.readRole(investment, "analyst");
		this.support.map(type, investment);
		this.support.integer(this.support.entity(type, unique("App"), this.kurtId), investment, 40);
		this.support.integer(this.support.entity(type, unique("App"), this.kurtId), investment, 2);
		JsonNode data = kpi(agg(type, investment, "sum"), this.principals.personWithIdpRole(role));
		if (readable) {
			assertThat(data.path("status").asString()).isEqualTo("ok");
			assertThat(data.path("value").asDouble()).isEqualTo(42.0);
		}
		else {
			assertError(data, "restricted");
		}
	}

	@Test
	void aRestrictedGroupingPropertyIsRestrictedToo() {
		UUID type = this.support.elementType("Application", null, null);
		UUID vendor = this.support.property("Vendor", "string", "role-restricted");
		this.support.readRole(vendor, "analyst");
		this.support.map(type, vendor);
		this.support.text(this.support.entity(type, unique("App"), this.kurtId), vendor, "SAP");
		ObjectNode widget = DashboardTestSupport.chartWidget("Chart", group(type, null, "count", "property", vendor),
				"pie", pos());
		assertError(compute(widget, this.franz), "restricted");
		assertThat(series(compute(widget, this.principals.personWithIdpRole("analyst")))).containsExactly("value:SAP=1.0");
	}

	@Test
	void aConnectionScopedPropertyIsRestrictedEvenWithAnAccountLink() {
		UUID type = this.support.elementType("Application", null, null);
		UUID owner = this.support.property("Owner Unit", "string", "connection-scoped");
		this.support.map(type, owner);
		int app = this.support.entity(type, unique("App"), this.kurtId);
		this.support.text(app, owner, "Finance");
		// franz is linked to a person element: the property would be readable per entity, but not as an aggregate
		TestSession linked = this.principals.personWithIdpRole("viewer");
		UUID personType = this.support.elementType("Person", null, null);
		int element = this.support.entity(personType, unique("Linked"), this.kurtId);
		this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = ? WHERE id = 1", personType);
		this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", element, this.support.personOf(linked));
		ObjectNode widget = DashboardTestSupport.chartWidget("Chart", group(type, null, "count", "property", owner),
				"pie", pos());
		assertError(compute(widget, this.franz), "restricted");
		assertError(compute(widget, linked), "restricted");
		assertError(kpi(agg(type, owner, "count"), linked), "restricted");
		this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = NULL WHERE id = 1");
	}

	@Test
	void anOverrideRoleDoesNotLiftRestricted() {
		this.jdbc.update("UPDATE authorization_roles SET overrides_property_visibility = (id = 'viewer')");
		try {
			UUID type = this.support.elementType("Application", null, null);
			UUID investment = this.support.property("Investment", "integer", "role-restricted");
			this.support.readRole(investment, "analyst");
			this.support.map(type, investment);
			this.support.integer(this.support.entity(type, unique("App"), this.kurtId), investment, 40);
			// the viewer holds an override role and no read role
			assertError(kpi(agg(type, investment, "sum"), this.franz), "restricted");
		}
		finally {
			this.jdbc.update("UPDATE authorization_roles SET overrides_property_visibility = false");
		}
	}

	@Test
	void aRestrictedWidgetRunsNoQuery() {
		UUID type = this.support.elementType("Application", null, null);
		UUID investment = this.support.property("Investment", "integer", "role-restricted");
		this.support.readRole(investment, "analyst");
		this.support.map(type, investment);
		this.support.integer(this.support.entity(type, unique("App"), this.kurtId), investment, 40);
		TestSession analyst = this.principals.personWithIdpRoles("analyst", "enterprise-architect");
		ObjectNode widget = DashboardTestSupport.kpiWidget("KPI", agg(type, investment, "sum"), pos());
		Reply dashboard = this.support.addWidget(this.kurt, shared(), widget);
		String path = DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/data";
		clearInvocations(this.store);
		assertError(this.support.get(path, this.franz).json().path("widgets").get(0), "restricted");
		verifyNoInteractions(this.store);
		this.support.get(path, analyst);
		verify(this.store).aggregate(org.mockito.ArgumentMatchers.anyCollection(),
				org.mockito.ArgumentMatchers.eq(investment), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void countingAndGroupingByMetatypeOrLayerAreNeverRestricted() {
		UUID type = this.support.elementType("Application", null, null);
		UUID investment = this.support.property("Investment", "integer", "role-restricted");
		this.support.readRole(investment, "analyst");
		this.support.map(type, investment);
		this.support.entity(type, unique("App"), this.kurtId);
		assertThat(kpi(agg(type, null, "count"), this.franz).path("value").asDouble()).isEqualTo(1.0);
		assertThat(chart(group(type, null, "count", "metatype", null), "pie", this.franz).path("status").asString())
			.isEqualTo("ok");
		assertThat(chart(group(type, null, "count", "layer", null), "pie", this.franz).path("status").asString())
			.isEqualTo("ok");
	}

	// ---- metamodel changes

	@Test
	void aWidgetThatNoLongerFitsGivesInvalidConfigurationAndTheOthersCompute() {
		UUID type = this.support.elementType("Application", null, null);
		UUID investment = this.support.property("Investment", "integer", "public");
		this.support.map(type, investment);
		this.support.integer(this.support.entity(type, unique("App"), this.kurtId), investment, 40);
		Reply dashboard = shared();
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.kpiWidget("Sum", agg(type, investment, "sum"), DashboardTestSupport.position(1, 1, 3, 1)));
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.kpiWidget("Count", agg(type, null, "count"), DashboardTestSupport.position(4, 1, 3, 1)));
		dashboard = this.support.addWidget(this.kurt, dashboard,
				DashboardTestSupport.textWidget("Notes", "x", DashboardTestSupport.position(1, 2, 6, 1)));
		String path = DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/data";
		assertThat(this.support.get(path, this.franz).json().path("widgets").get(0).path("value").asDouble())
			.isEqualTo(40.0);

		this.support.unmap(type, investment);
		JsonNode widgets = this.support.get(path, this.franz).json().path("widgets");
		assertError(widgets.get(0), "invalid-configuration");
		assertThat(widgets.get(1).path("status").asString()).isEqualTo("ok");
		assertThat(widgets.get(1).path("value").asDouble()).isEqualTo(1.0);
		assertThat(widgets.get(2).path("status").asString()).isEqualTo("static");
		// the widget is not deleted
		assertThat(this.support.get(DashboardTestSupport.DASHBOARDS.replace("/dashboards", "/views/dashboards") + "/"
				+ dashboard.id(), this.franz).json().path("widgets")).hasSize(3);
	}

	@Test
	void aDeletedMetatypeGivesInvalidConfigurationForChartAndKpi() throws SQLException {
		UUID type = this.support.elementType("Doomed", null, null);
		UUID vendor = this.support.property("Vendor", "string", "public");
		this.support.map(type, vendor);
		Reply dashboard = shared();
		dashboard = this.support.addWidget(this.kurt, dashboard, DashboardTestSupport.chartWidget("Chart",
				group(type, null, "count", "property", vendor), "bar", DashboardTestSupport.position(1, 1, 6, 2)));
		dashboard = this.support.addWidget(this.kurt, dashboard, DashboardTestSupport.kpiWidget("KPI",
				agg(type, null, "count"), DashboardTestSupport.position(7, 1, 3, 1)));
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate("DELETE FROM metatype_property_mappings WHERE metatype_id = '" + type + "'");
			statement.executeUpdate("DELETE FROM metatypes WHERE id = '" + type + "'");
		}
		JsonNode widgets = this.support.get(DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/data", this.franz)
			.json()
			.path("widgets");
		assertError(widgets.get(0), "invalid-configuration");
		assertError(widgets.get(1), "invalid-configuration");
	}

	@Test
	void aGroupingPropertyThatChangedItsTypeGivesInvalidConfiguration() {
		UUID type = this.support.elementType("Application", null, null);
		UUID vendor = this.support.property("Vendor", "string", "public");
		this.support.map(type, vendor);
		Reply dashboard = this.support.addWidget(this.kurt, shared(), DashboardTestSupport.chartWidget("Chart",
				group(type, null, "count", "property", vendor), "bar", pos()));
		this.jdbc.update("UPDATE property_definitions SET retired_at = now() WHERE id = ?", vendor);
		assertError(this.support.get(DashboardTestSupport.VIEWS + "/" + dashboard.id() + "/data", this.franz)
			.json()
			.path("widgets")
			.get(0), "invalid-configuration");
	}

}

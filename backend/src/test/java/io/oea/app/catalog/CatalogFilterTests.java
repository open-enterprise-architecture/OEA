package io.oea.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Catalog filters (us-051-catalog-filters): the ad-hoc filters and saved filters of
 * catalog/catalog-filters and "Filtered total" of catalog/catalog-query, on an own database with
 * the starter package imported. Every test uses its own metatypes: applications with a status
 * (enum), a vendor (string), a support end (date), a cost (integer) and a cloud flag (boolean),
 * interfaces with a protocol (enum) reached through "Provides", and data flows between
 * applications that carry data objects with a classification (enum).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogFilterTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CATALOGS = "/api/v1/catalogs";

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

	private TestPrincipals principals;

	private TestSession architect;

	private TestSession viewer;

	private long architectId;

	private UUID application;

	private UUID iface;

	private UUID provides;

	private UUID dataFlow;

	private UUID dataObject;

	private UUID carriesData;

	private UUID status;

	private UUID vendor;

	private UUID supportEnd;

	private UUID cost;

	private UUID cloud;

	private UUID protocol;

	private UUID classification;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = this.principals.personWithIdpRole("enterprise-architect");
		this.viewer = this.principals.personWithIdpRole("viewer");
		this.architectId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.architect.sessionId());
		if (this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Integer.class) == 0) {
			TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
			assertThat(call(HttpMethod.POST, "/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import",
					null, metamodelAdmin)).hasStatus(HttpStatus.OK);
		}
		this.application = metatype("Application", false);
		this.iface = metatype("Interface", false);
		this.provides = metatype("Provides", true);
		this.dataFlow = metatype("Data Flow", true);
		this.dataObject = metatype("Data Object", false);
		this.carriesData = metatype("Carries Data", true);
		constraint(this.provides, "source", this.application);
		constraint(this.provides, "target", this.iface);
		constraint(this.dataFlow, "source", this.application);
		constraint(this.dataFlow, "target", this.application);
		constraint(this.carriesData, "source", this.dataFlow);
		constraint(this.carriesData, "target", this.dataObject);
		this.status = property("Status " + suffix(), "enum", "active", "planned", "retired");
		this.vendor = property("Vendor " + suffix(), "string");
		this.supportEnd = property("Support End " + suffix(), "date");
		this.cost = property("Cost " + suffix(), "integer");
		this.cloud = property("Cloud " + suffix(), "boolean");
		this.protocol = property("Protocol " + suffix(), "enum", "REST", "SFTP", "SOAP");
		this.classification = property("Classification " + suffix(), "enum", "PII", "Internal", "Public");
		for (UUID property : List.of(this.status, this.vendor, this.supportEnd, this.cost, this.cloud)) {
			map(this.application, property);
		}
		map(this.iface, this.protocol);
		map(this.dataObject, this.classification);
	}

	// Filter on a column

	@Test
	void activeApplicationsOnly() {
		for (int i = 0; i < 10; i++) {
			int id = entity(this.application, "App " + i);
			value(id, this.status, (i < 4) ? "active" : "retired");
		}
		String catalog = applicationCatalog();
		JsonNode result = ok(query(catalog, filter("%s:eq:active", key(this.status))));
		assertThat(result.path("rows")).hasSize(4);
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(4);
		assertThat(names(result)).containsExactly("App 0", "App 1", "App 2", "App 3");
		assertThat(ok(query(catalog, "")).path("page").path("totalElements").asInt()).isEqualTo(10);
	}

	@Test
	void columnsCarryTheirFilterTypes() {
		JsonNode result = ok(query(applicationCatalog(), ""));
		List<String> types = new ArrayList<>();
		result.path("columns").forEach((column) -> types.add(column.path("filterType").asString()));
		assertThat(types).containsExactly("text", "enum", "text", "date", "number", "boolean", "date", "number");
		List<String> values = new ArrayList<>();
		result.path("columns").get(1).path("enumValues").forEach((value) -> values.add(value.asString()));
		assertThat(values).containsExactly("active", "planned", "retired");
	}

	// Operators

	@Test
	void textComparisonsIgnoreCase() {
		int crm = entity(this.application, "CRM System");
		value(crm, this.vendor, "Salesforce");
		int erp = entity(this.application, "ERP");
		value(erp, this.vendor, "SAP");
		int portal = entity(this.application, "Customer Portal 100%");
		String catalog = applicationCatalog();
		assertThat(names(ok(query(catalog, filter("name:contains:SYST"))))).containsExactly("CRM System");
		assertThat(names(ok(query(catalog, filter("name:startsWith:crm"))))).containsExactly("CRM System");
		assertThat(names(ok(query(catalog, filter("name:eq:erp"))))).containsExactly("ERP");
		assertThat(names(ok(query(catalog, filter("name:contains:0%"))))).containsExactly("Customer Portal 100%");
		assertThat(names(ok(query(catalog, filter("name:contains:_"))))).isEmpty();
		String vendorKey = key(this.vendor);
		assertThat(names(ok(query(catalog, filter("%s:in:sap, salesforce", vendorKey))))).containsExactly("CRM System",
				"ERP");
		// not equals and not in also hold without a value
		assertThat(names(ok(query(catalog, filter("%s:neq:SAP", vendorKey))))).containsExactly("CRM System",
				"Customer Portal 100%");
		assertThat(names(ok(query(catalog, filter("%s:notIn:SAP,Salesforce", vendorKey)))))
			.containsExactly("Customer Portal 100%");
		assertThat(names(ok(query(catalog, filter("%s:isNull", vendorKey))))).containsExactly("Customer Portal 100%");
		assertThat(names(ok(query(catalog, filter("%s:isNotNull", vendorKey))))).containsExactly("CRM System", "ERP");
		assertThat(portal).isPositive();
	}

	@Test
	void supportEndingBeforeADate() {
		int crm = entity(this.application, "CRM System");
		value(crm, this.supportEnd, LocalDate.of(2026, 12, 31));
		int erp = entity(this.application, "ERP");
		value(erp, this.supportEnd, LocalDate.of(2027, 1, 1));
		entity(this.application, "Portal");
		String catalog = applicationCatalog();
		String key = key(this.supportEnd);
		assertThat(names(ok(query(catalog, filter("%s:lt:2027-01-01", key))))).containsExactly("CRM System");
		assertThat(names(ok(query(catalog, filter("%s:gte:2027-01-01", key))))).containsExactly("ERP");
		assertThat(names(ok(query(catalog, filter("%s:eq:2026-12-31", key))))).containsExactly("CRM System");
		String today = LocalDate.now(ZoneOffset.UTC).toString();
		assertThat(names(ok(query(catalog, filter("createdAt:eq:" + today))))).hasSize(3);
		assertThat(names(ok(query(catalog, filter("createdAt:lt:" + today))))).isEmpty();
	}

	@Test
	void numbersBooleansAndEnums() {
		int crm = entity(this.application, "CRM System");
		value(crm, this.cost, 900L);
		value(crm, this.cloud, true);
		value(crm, this.status, "active");
		int erp = entity(this.application, "ERP");
		value(erp, this.cost, 15000L);
		value(erp, this.cloud, false);
		value(erp, this.status, "planned");
		String catalog = applicationCatalog();
		assertThat(names(ok(query(catalog, filter("%s:gt:1000", key(this.cost)))))).containsExactly("ERP");
		assertThat(names(ok(query(catalog, filter("%s:lte:900", key(this.cost)))))).containsExactly("CRM System");
		assertThat(names(ok(query(catalog, filter("%s:eq:true", key(this.cloud)))))).containsExactly("CRM System");
		assertThat(names(ok(query(catalog, filter("%s:neq:true", key(this.cloud)))))).containsExactly("ERP");
		assertThat(names(ok(query(catalog, filter("%s:in:planned,retired", key(this.status))))))
			.containsExactly("ERP");
		assertThat(names(ok(query(catalog, filter("entityVersion:gte:1"))))).containsExactly("CRM System", "ERP");
	}

	@Test
	void operatorNotAllowedForTheType() {
		String catalog = applicationCatalog();
		assertBadFilter(query(catalog, filter("%s:contains:act", key(this.status))));
		assertBadFilter(query(catalog, filter("name:gt:A")));
		assertBadFilter(query(catalog, filter("%s:startsWith:t", key(this.cloud))));
		assertBadFilter(query(catalog, filter("%s:contains:1", key(this.cost))));
		assertBadFilter(query(catalog, filter("%s:in:2027-01-01", key(this.supportEnd))));
	}

	@Test
	void unknownColumnOperatorAndValue() {
		String catalog = applicationCatalog();
		assertBadFilter(query(catalog, filter("nonexistent:eq:x")));
		assertBadFilter(query(catalog, filter("name:like:x")));
		assertBadFilter(query(catalog, filter("name")));
		assertBadFilter(query(catalog, filter("name:eq")));
		assertBadFilter(query(catalog, filter("name:isNull:x")));
		assertBadFilter(query(catalog, filter("%s:gt:many", key(this.cost))));
		assertBadFilter(query(catalog, filter("%s:lt:01.01.2027", key(this.supportEnd))));
		assertBadFilter(query(catalog, filter("%s:eq:yes", key(this.cloud))));
		assertBadFilter(query(catalog, filter("%s:in:", key(this.status))));
		assertBadFilter(query(catalog, filter("%s:lt:0000-01-01", key(this.supportEnd))));
		assertBadFilter(query(catalog, filter("name:eq:a\0b")));
		// a hidden or unconfigured attribute is no column of the result
		assertBadFilter(query(catalog(this.application), filter("description:isNull")));
	}

	@Test
	void enumValueOutsideTheEnum() {
		String catalog = applicationCatalog();
		assertBadFilter(query(catalog, filter("%s:eq:Active", key(this.status))));
		assertBadFilter(query(catalog, filter("%s:in:active,gone", key(this.status))));
	}

	@Test
	void atMostTwentyFilters() {
		String catalog = applicationCatalog();
		String twenty = Stream.generate(() -> filter("name:isNotNull")).limit(20).collect(Collectors.joining("&"));
		ok(query(catalog, twenty));
		MvcTestResult result = query(catalog, twenty + "&" + filter("name:isNotNull"));
		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(fields(result)).contains("filter");
	}

	@Test
	void valueLengthAtItsLimits() {
		String catalog = applicationCatalog();
		ok(query(catalog, filter("name:contains:%s", "a".repeat(1000))));
		assertBadFilter(query(catalog, filter("name:contains:%s", "a".repeat(1001))));
	}

	@Test
	void unknownFilterMode() {
		MvcTestResult result = query(applicationCatalog(), filter("name:isNotNull") + "&filterMode=xor");
		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(fields(result)).containsExactly("filterMode");
	}

	// Filter on a join column

	@Test
	void applicationsWithARestInterfaceInBothModes() {
		int crm = entity(this.application, "CRM System");
		int erp = entity(this.application, "ERP");
		int rest = entity(this.iface, "REST API");
		value(rest, this.protocol, "REST");
		int sftp = entity(this.iface, "SFTP Export");
		value(sftp, this.protocol, "SFTP");
		connect(this.provides, crm, rest);
		connect(this.provides, crm, sftp);
		connect(this.provides, erp, sftp);
		String catalog = catalog(this.application);
		String join = interfacesJoin(catalog);
		String restFilter = filter("%s.%s:eq:REST", join, key(this.protocol));
		JsonNode aggregate = ok(query(catalog, restFilter));
		assertThat(names(aggregate)).containsExactly("CRM System");
		assertThat(joinNames(aggregate.path("rows").get(0), join)).containsExactly("REST API");
		assertThat(aggregate.path("page").path("totalElements").asInt()).isEqualTo(1);
		JsonNode rows = ok(query(catalog, restFilter + "&" + mode(join, "rows")));
		assertThat(names(rows)).containsExactly("CRM System");
		assertThat(joinNames(rows.path("rows").get(0), join)).containsExactly("REST API");
		assertThat(rows.path("page").path("totalElements").asInt()).isEqualTo(1);
		// a join without a filter stays unfiltered
		assertThat(joinNames(ok(query(catalog, filter("name:eq:CRM System"))).path("rows").get(0), join))
			.containsExactly("REST API", "SFTP Export");
	}

	@Test
	void dataFlowsCarryingPersonalData() {
		int crm = entity(this.application, "CRM System");
		int erp = entity(this.application, "ERP");
		int orders = connect(this.dataFlow, "Orders", crm, erp);
		int stock = connect(this.dataFlow, "Stock levels", erp, crm);
		int customer = entity(this.dataObject, "Customer master data");
		value(customer, this.classification, "PII");
		int order = entity(this.dataObject, "Order");
		value(order, this.classification, "Internal");
		connect(this.carriesData, orders, customer);
		connect(this.carriesData, orders, order);
		connect(this.carriesData, stock, order);
		String catalog = catalog(this.dataFlow);
		String join = created(addJoin(catalog, this.architect, join("Data objects", this.carriesData, "outbound",
				this.dataObject, field("name"), property(this.classification))))
			.path("joinDefinitions").get(0).path("id").asString();
		JsonNode result = ok(query(catalog, filter("%s.%s:eq:PII", join, key(this.classification))));
		assertThat(names(result)).containsExactly("Orders");
		assertThat(joinNames(result.path("rows").get(0), join)).containsExactly("Customer master data");
	}

	// Filter on source and target

	@Test
	void flowsFromTheCrm() {
		int crm = entity(this.application, "CRM System");
		int erp = entity(this.application, "ERP");
		connect(this.dataFlow, "Orders", crm, erp);
		connect(this.dataFlow, "Invoices", erp, crm);
		String catalog = catalog(this.dataFlow);
		assertThat(names(ok(query(catalog, filter("source.name:contains:CRM"))))).containsExactly("Orders");
		assertThat(names(ok(query(catalog, filter("target.name:contains:crm"))))).containsExactly("Invoices");
		assertThat(ok(query(catalog, filter("source.name:contains:CRM"))).path("page").path("totalElements").asInt())
			.isEqualTo(1);
	}

	// AND and OR

	@Test
	void bothAndEitherCondition() {
		int crm = entity(this.application, "CRM System");
		value(crm, this.status, "active");
		value(crm, this.vendor, "Salesforce");
		int erp = entity(this.application, "ERP");
		value(erp, this.status, "active");
		value(erp, this.vendor, "SAP");
		int hr = entity(this.application, "HR");
		value(hr, this.status, "retired");
		value(hr, this.vendor, "SAP");
		entity(this.application, "Legacy");
		String catalog = applicationCatalog();
		String both = filter("%s:eq:active", key(this.status)) + "&" + filter("%s:eq:SAP", key(this.vendor));
		assertThat(names(ok(query(catalog, both)))).containsExactly("ERP");
		assertThat(names(ok(query(catalog, both + "&filterMode=and")))).containsExactly("ERP");
		JsonNode either = ok(query(catalog, both + "&filterMode=or"));
		assertThat(names(either)).containsExactly("CRM System", "ERP", "HR");
		assertThat(either.path("page").path("totalElements").asInt()).isEqualTo(3);
	}

	@Test
	void eitherConditionWithAJoin() {
		int crm = entity(this.application, "CRM System");
		int erp = entity(this.application, "ERP");
		value(erp, this.status, "active");
		entity(this.application, "Legacy");
		int rest = entity(this.iface, "REST API");
		value(rest, this.protocol, "REST");
		int sftp = entity(this.iface, "SFTP Export");
		value(sftp, this.protocol, "SFTP");
		connect(this.provides, crm, rest);
		connect(this.provides, erp, sftp);
		String catalog = applicationCatalog();
		String join = interfacesJoin(catalog);
		String either = filter("%s:eq:active", key(this.status)) + "&"
				+ filter("%s.%s:eq:REST", join, key(this.protocol)) + "&filterMode=or";
		JsonNode aggregate = ok(query(catalog, either));
		assertThat(names(aggregate)).containsExactly("CRM System", "ERP");
		assertThat(joinNames(aggregate.path("rows").get(0), join)).containsExactly("REST API");
		assertThat(joinNames(aggregate.path("rows").get(1), join)).isEmpty();
		JsonNode rows = ok(query(catalog, either + "&" + mode(join, "rows")));
		assertThat(names(rows)).containsExactly("CRM System", "ERP");
		assertThat(rows.path("page").path("totalElements").asInt()).isEqualTo(2);
		assertThat(joinNames(rows.path("rows").get(1), join)).isEmpty();
	}

	// Pagination

	@Test
	void inListOfAHundredValues() {
		String catalog = applicationCatalog();
		String hundred = IntStream.range(0, 100).mapToObj((i) -> "V" + i).collect(Collectors.joining(","));
		ok(query(catalog, filter("%s:in:%s", key(this.vendor), hundred)));
		assertBadFilter(query(catalog, filter("%s:in:%s", key(this.vendor), hundred + ",V100")));
	}

	@Test
	void filterOfAtMost2000Characters() {
		String catalog = applicationCatalog();
		String prefix = key(this.vendor) + ":in:" + "a".repeat(1000) + ",";
		ok(query(catalog, filter(prefix + "b".repeat(2000 - prefix.length()))));
		MvcTestResult tooLong = query(catalog, filter(prefix + "b".repeat(2001 - prefix.length())));
		assertThat(tooLong).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(fields(tooLong)).containsExactly("filter");
	}

	@Test
	void filteredPaging() {
		for (int i = 0; i < 25; i++) {
			int id = entity(this.application, "App %02d".formatted(i));
			value(id, this.status, (i % 2 == 0) ? "active" : "planned");
		}
		String catalog = applicationCatalog();
		JsonNode page = ok(query(catalog, filter("%s:eq:active", key(this.status)) + "&page=2&pageSize=5"));
		assertThat(page.path("page").path("totalElements").asInt()).isEqualTo(13);
		assertThat(page.path("page").path("totalPages").asInt()).isEqualTo(3);
		assertThat(names(page)).containsExactly("App 20", "App 22", "App 24");
	}

	// Save a filter

	@Test
	void saveAndReadBack() {
		String catalog = applicationCatalog();
		JsonNode saved = created(saveFilter(catalog, this.architect, savedFilter("Active systems only", "and",
				expression(key(this.status), "eq", "active"),
				expression(key(this.vendor), "in", JsonMapper.shared().createArrayNode().add("SAP").add("Oracle")),
				expression("name", "isNotNull", null))));
		JsonNode filter = saved.path("savedFilters").get(0);
		assertThat(filter.path("name").asString()).isEqualTo("Active systems only");
		assertThat(filter.path("logicalOperator").asString()).isEqualTo("and");
		assertThat(filter.path("available").asBoolean()).isTrue();
		assertThat(filter.path("expressions")).hasSize(3);
		assertThat(filter.path("expressions").get(0).path("value").asString()).isEqualTo("active");
		assertThat(filter.path("expressions").get(1).path("value").get(1).asString()).isEqualTo("Oracle");
		assertThat(filter.path("expressions").get(2).has("value")
				&& !filter.path("expressions").get(2).path("value").isNull()).isFalse();
		JsonNode read = ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.viewer));
		assertThat(read.path("savedFilters").get(0).path("id").asString()).isEqualTo(filter.path("id").asString());
	}

	@Test
	void invalidSavedFilters() {
		String catalog = applicationCatalog();
		assertInvalid(saveFilter(catalog, this.architect,
				savedFilter("X", "and", expression("nonexistent", "eq", "x"))), "expressions[0].attributePath");
		assertInvalid(saveFilter(catalog, this.architect,
				savedFilter("X", "and", expression("name", "isNotNull", null), expression("name", "gt", "A"))),
				"expressions[1].operator");
		assertInvalid(saveFilter(catalog, this.architect,
				savedFilter("X", "and", expression(key(this.status), "eq", "gone"))), "expressions[0].value");
		assertInvalid(saveFilter(catalog, this.architect,
				savedFilter("X", "and", expression(key(this.status), "in", "active"))), "expressions[0].value");
		assertInvalid(saveFilter(catalog, this.architect, savedFilter("X", "and", expression(key(this.vendor), "in",
				JsonMapper.shared().createArrayNode().add("SAP, Oracle")))), "expressions[0].value");
		assertInvalid(saveFilter(catalog, this.architect, savedFilter("X", "and")), "expressions");
		assertInvalid(saveFilter(catalog, this.architect,
				savedFilter(" ", "and", expression("name", "isNotNull", null))), "name");
		created(saveFilter(catalog, this.architect,
				savedFilter("Active systems only", "and", expression(key(this.status), "eq", "active"))));
		assertInvalid(saveFilter(catalog, this.architect,
				savedFilter(" active SYSTEMS only ", "or", expression("name", "isNotNull", null))), "name");
	}

	@Test
	void savedFilterAtItsLimits() {
		String catalog = applicationCatalog();
		ObjectNode[] twenty = Stream.generate(() -> expression("name", "isNotNull", null))
			.limit(20)
			.toArray(ObjectNode[]::new);
		ObjectNode longest = savedFilter("f".repeat(100), "and", twenty).put("description", "d".repeat(1000));
		JsonNode saved = created(saveFilter(catalog, this.architect, longest));
		assertThat(saved.path("savedFilters").get(0).path("expressions")).hasSize(20);
		ObjectNode[] twentyOne = Stream.generate(() -> expression("name", "isNotNull", null))
			.limit(21)
			.toArray(ObjectNode[]::new);
		assertInvalid(saveFilter(catalog, this.architect, savedFilter("Too many", "and", twentyOne)), "expressions");
		assertInvalid(saveFilter(catalog, this.architect,
				savedFilter("f".repeat(101), "and", expression("name", "isNotNull", null))), "name");
		assertInvalid(saveFilter(catalog, this.architect,
				savedFilter("Long description", "and", expression("name", "isNotNull", null)).put("description",
						"d".repeat(1001))),
				"description");
		// the expression, written as a query filter, has at most 2000 characters
		String prefix = key(this.vendor) + ":in:";
		created(saveFilter(catalog, this.architect, savedFilter("Longest list", "and",
				expression(key(this.vendor), "in", values(listOfLength(2000 - prefix.length()))))));
		assertInvalid(saveFilter(catalog, this.architect, savedFilter("Longer list", "and",
				expression(key(this.vendor), "in", values(listOfLength(2001 - prefix.length()))))),
				"expressions[0].value");
	}

	@Test
	void atMostFiftySavedFilters() {
		String catalog = applicationCatalog();
		for (int i = 0; i < 50; i++) {
			created(saveFilter(catalog, this.architect, savedFilter("F" + i, "and", expression("name", "isNotNull", null))));
		}
		assertInvalid(saveFilter(catalog, this.architect, savedFilter("F50", "and", expression("name", "isNotNull", null))),
				"savedFilters");
	}

	// Who saves filters

	@Test
	void readerOfAnInstanceCatalog() {
		String catalog = applicationCatalog();
		assertThat(saveFilter(catalog, this.viewer, savedFilter("Mine", "and", expression("name", "isNotNull", null))))
			.hasStatus(HttpStatus.FORBIDDEN);
		String id = created(saveFilter(catalog, this.architect,
				savedFilter("Theirs", "and", expression("name", "isNotNull", null))))
			.path("savedFilters").get(0).path("id").asString();
		assertThat(call(HttpMethod.DELETE, CATALOGS + "/" + catalog + "/filters/" + id, null, this.viewer))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void ownPersonalCatalog() {
		String own = catalog(this.application, "personal", this.viewer);
		JsonNode saved = created(saveFilter(own, this.viewer, savedFilter("Mine", "or",
				expression("name", "contains", "CRM"))));
		assertThat(saved.path("savedFilters")).hasSize(1);
		long viewerId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.viewer.sessionId());
		assertThat(this.jdbc.queryForObject("""
				SELECT actor_person_id FROM audit.events WHERE event_type = 'catalog.filter-added' AND target_id = ?""",
				Long.class, own)).isEqualTo(viewerId);
	}

	// Remove a saved filter

	@Test
	void removeAFilter() {
		String catalog = applicationCatalog();
		String id = created(saveFilter(catalog, this.architect,
				savedFilter("Active systems only", "and", expression(key(this.status), "eq", "active"))))
			.path("savedFilters").get(0).path("id").asString();
		JsonNode removed = ok(call(HttpMethod.DELETE, CATALOGS + "/" + catalog + "/filters/" + id, null, this.architect));
		assertThat(removed.path("savedFilters")).isEmpty();
		assertThat(call(HttpMethod.DELETE, CATALOGS + "/" + catalog + "/filters/" + id, null, this.architect))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type IN ('catalog.filter-added', "
				+ "'catalog.filter-removed') AND target_id = '" + catalog + "'")).isEqualTo(2);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'name' FROM audit.events WHERE event_type = 'catalog.filter-removed' AND target_id = ?""",
				String.class, catalog)).isEqualTo("Active systems only");
	}

	@Test
	void removingAJoinRemovesItsExpressions() {
		String catalog = applicationCatalog();
		String join = interfacesJoin(catalog);
		String protocolKey = join + "." + key(this.protocol);
		created(saveFilter(catalog, this.architect, savedFilter("Active REST", "and",
				expression(key(this.status), "eq", "active"), expression(protocolKey, "eq", "REST"))));
		created(saveFilter(catalog, this.architect, savedFilter("REST only", "and", expression(protocolKey, "eq", "REST"))));
		JsonNode changed = ok(call(HttpMethod.DELETE, CATALOGS + "/" + catalog + "/joins/" + join, null, this.architect));
		assertThat(changed.path("savedFilters")).hasSize(1);
		assertThat(changed.path("savedFilters").get(0).path("name").asString()).isEqualTo("Active REST");
		assertThat(changed.path("savedFilters").get(0).path("expressions")).hasSize(1);
		assertThat(changed.path("savedFilters").get(0).path("available").asBoolean()).isTrue();
	}

	@Test
	void filterOnAColumnNoLongerShownIsUnavailable() {
		String catalog = applicationCatalog();
		created(saveFilter(catalog, this.architect, savedFilter("SAP", "and", expression(key(this.vendor), "eq", "SAP"))));
		JsonNode changed = ok(columns(catalog, field("name")));
		assertThat(changed.path("savedFilters").get(0).path("available").asBoolean()).isFalse();
	}

	// helpers

	/** A catalog of the applications with name, status, vendor, support end, cost, cloud, created and version. */
	private String applicationCatalog() {
		String catalog = catalog(this.application);
		ok(columns(catalog, field("name"), property(this.status), property(this.vendor), property(this.supportEnd),
				property(this.cost), property(this.cloud), field("createdAt"), field("entityVersion")));
		return catalog;
	}

	/** Adds the join "Interfaces" with name and protocol; returns its ID. */
	private String interfacesJoin(String catalog) {
		return created(addJoin(catalog, this.architect,
				join("Interfaces", this.provides, "outbound", this.iface, field("name"), property(this.protocol))))
			.path("joinDefinitions").get(0).path("id").asString();
	}

	private static String filter(String format, Object... args) {
		// the request URI is a template, which encodes the value itself
		return "filter=" + ((args.length > 0) ? format.formatted(args) : format);
	}

	/** Two comma-separated values of at most 1000 characters each, of the given total length. */
	private static String listOfLength(int length) {
		int first = Math.min(1000, length - 2);
		return "a".repeat(first) + "," + "b".repeat(length - first - 1);
	}

	private static ArrayNode values(String list) {
		ArrayNode values = JsonMapper.shared().createArrayNode();
		for (String value : list.split(",")) {
			values.add(value);
		}
		return values;
	}

	private static ObjectNode savedFilter(String name, String operator, ObjectNode... expressions) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("logicalOperator", operator);
		ArrayNode list = body.putArray("expressions");
		for (ObjectNode expression : expressions) {
			list.add(expression);
		}
		return body;
	}

	private static ObjectNode expression(String attributePath, String operator, Object value) {
		ObjectNode node = JsonMapper.shared()
			.createObjectNode()
			.put("attributePath", attributePath)
			.put("operator", operator);
		if (value instanceof JsonNode json) {
			node.set("value", json);
		}
		else if (value != null) {
			node.put("value", (String) value);
		}
		return node;
	}

	private MvcTestResult saveFilter(String catalog, TestSession session, ObjectNode filter) {
		return call(HttpMethod.POST, CATALOGS + "/" + catalog + "/filters", filter, session);
	}

	private static List<String> joinNames(JsonNode row, String join) {
		List<String> names = new ArrayList<>();
		row.path("joinResults").path(join).forEach((r) -> names.add(r.path("values").path("name").asString()));
		return names;
	}

	private static void assertBadFilter(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(fields(result)).containsExactly("filter");
	}

	private static List<String> fields(MvcTestResult result) {
		List<String> fields = new ArrayList<>();
		body(result).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		return fields;
	}

	private long count(String sql) {
		return this.jdbc.queryForObject(sql, Long.class);
	}

	private static ObjectNode join(String name, UUID connection, String direction, UUID target, ObjectNode... columns) {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("connectionMetatypeId", connection.toString())
			.put("joinDirection", direction)
			.put("targetMetatypeId", target.toString());
		ArrayNode list = body.putArray("targetColumns");
		for (ObjectNode column : columns) {
			list.add(column);
		}
		return body;
	}

	private MvcTestResult addJoin(String catalog, TestSession session, ObjectNode join) {
		return call(HttpMethod.POST, CATALOGS + "/" + catalog + "/joins", join, session);
	}

	private MvcTestResult columns(String catalog, ObjectNode... columns) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode list = body.putArray("columns");
		for (ObjectNode column : columns) {
			list.add(column);
		}
		return call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/columns", body, this.architect);
	}

	private static String mode(String joinId, String mode) {
		return "joinMode[" + joinId + "]=" + mode;
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, ?)
				RETURNING id""", UUID.class, name + " " + suffix(), connection);
	}

	private void constraint(UUID connection, String direction, UUID allowed) {
		this.jdbc.update("""
				INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
				VALUES (?, ?, ?)""", connection, direction, allowed);
	}

	private int entity(UUID type, String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class, type,
				name, this.architectId);
	}

	/** Stores a value in the typed column of its Java type. */
	private void value(int entity, UUID property, Object value) {
		String column = switch (value) {
			case LocalDate date -> "value_date";
			case Long number -> "value_int";
			case Boolean flag -> "value_bool";
			default -> (this.jdbc.queryForObject("SELECT data_type FROM property_definitions WHERE id = ?", String.class,
					property)
				.equals("enum")) ? "value_enum" : "value_text";
		};
		Object bound = (value instanceof LocalDate date) ? java.sql.Date.valueOf(date) : value;
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, " + column
				+ ") VALUES (?, ?, ?)", entity, property, bound);
	}

	/** A connection entity from source to target; returns its ID. */
	private int connect(UUID type, int source, int target) {
		return connect(type, "Connection " + suffix(), source, target);
	}

	private int connect(UUID type, String name, int source, int target) {
		int id = entity(type, name);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	/** The column key (storage name) of a property. */
	private String key(UUID property) {
		return this.jdbc.queryForObject("SELECT sql_column_name FROM property_definitions WHERE id = ?", String.class,
				property);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static ObjectNode field(String field) {
		return JsonMapper.shared().createObjectNode().put("field", field);
	}

	private static ObjectNode property(UUID id) {
		return JsonMapper.shared().createObjectNode().put("propertyDefId", id.toString());
	}

	private void map(UUID type, UUID property) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", type, property);
	}

	private UUID property(String name, String dataType, String... enumValues) {
		UUID id = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, ?) RETURNING id""",
				UUID.class, name, "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), dataType);
		for (int i = 0; i < enumValues.length; i++) {
			this.jdbc.update("INSERT INTO property_enum_values (property_def_id, value, sort_order) VALUES (?, ?, ?)", id,
					enumValues[i], i);
		}
		return id;
	}

	private String catalog(UUID primary) {
		return catalog(primary, "instance", this.architect);
	}

	private String catalog(UUID primary, String scope, TestSession session) {
		MvcTestResult created = call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Catalog " + UUID.randomUUID())
			.put("primaryMetatypeId", primary.toString())
			.put("scope", scope), session);
		assertThat(created).hasStatus(HttpStatus.CREATED);
		return body(created).path("id").asString();
	}

	private MvcTestResult query(String catalogId, String parameters) {
		return call(HttpMethod.GET, CATALOGS + "/" + catalogId + "/query" + (parameters.isEmpty() ? "" : "?" + parameters),
				null, this.architect);
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		List<String> fields = new ArrayList<>();
		body(result).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static List<String> names(JsonNode result) {
		List<String> names = new ArrayList<>();
		result.path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		return names;
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-catalog-filters").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * Saved views (us-053-saved-views): catalog/catalog-views and "Remove a join used by a view" of
 * catalog/catalog-joins, on an own database with the starter package imported. Every test uses its own metatypes: applications with a status
 * (enum), a vendor (string), a support end (date), a cost (integer) and a cloud flag (boolean),
 * interfaces with a protocol (enum) reached through "Provides", and data flows between
 * applications that carry data objects with a classification (enum).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogViewTests {

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

	// Save a view

	@Test
	void saveAndReadBack() {
		String catalog = applicationCatalog();
		String join = interfacesJoin(catalog);
		JsonNode saved = created(saveView(catalog, this.architect, view("Compact view", true, "name", key(this.status))
			.<ObjectNode>set("filters", array(expression(key(this.status), "eq", "active")))
			.<ObjectNode>set("joinModeOverrides", array(override(join, "rows")))
			.<ObjectNode>set("sort", sort(join + "." + key(this.protocol), "desc"))));
		JsonNode view = saved.path("savedViews").get(0);
		assertThat(view.path("name").asString()).isEqualTo("Compact view");
		assertThat(strings(view.path("columnOrder"))).containsExactly("name", key(this.status));
		assertThat(view.path("filters").get(0).path("value").asString()).isEqualTo("active");
		assertThat(view.path("filterMode").asString()).isEqualTo("and");
		assertThat(view.path("joinModeOverrides").get(0).path("joinMode").asString()).isEqualTo("rows");
		assertThat(view.path("sort").path("direction").asString()).isEqualTo("desc");
		assertThat(view.path("isDefault").asBoolean()).isTrue();
		assertThat(view.path("available").asBoolean()).isTrue();
		JsonNode read = ok(call(HttpMethod.GET, CATALOGS + "/" + catalog, null, this.viewer));
		assertThat(read.path("savedViews").get(0).path("id").asString()).isEqualTo(view.path("id").asString());
	}

	// Validation of a view and of the view state

	@Test
	void invalidViews() {
		String catalog = applicationCatalog();
		String join = interfacesJoin(catalog);
		assertInvalid(saveView(catalog, this.architect, view("X", false, "name", "unknown")), "columnOrder[1]");
		assertInvalid(saveView(catalog, this.architect, view("X", false, "name", "name")), "columnOrder[1]");
		assertInvalid(saveView(catalog, this.architect, view("X", false)), "columnOrder");
		assertInvalid(saveView(catalog, this.architect, view("X", false, "name", key(this.status))
			.<ObjectNode>set("filters", array(expression(key(this.vendor), "eq", "SAP")))), "filters[0].attributePath");
		assertInvalid(saveView(catalog, this.architect, view("X", false, "name")
			.<ObjectNode>set("filters", array(expression("name", "gt", "A")))), "filters[0].operator");
		assertInvalid(saveView(catalog, this.architect, view("X", false, "name")
			.<ObjectNode>set("sort", sort(key(this.vendor), "asc"))), "sort");
		// a join column sorts only in rows mode
		assertInvalid(saveView(catalog, this.architect, view("X", false, "name")
			.<ObjectNode>set("sort", sort(join + ".name", "asc"))), "sort");
		assertInvalid(saveView(catalog, this.architect, view("X", false, "name")
			.<ObjectNode>set("joinModeOverrides", array(override(UUID.randomUUID().toString(), "rows")))),
				"joinModeOverrides[0].joinDefinitionId");
		assertInvalid(saveView(catalog, this.architect, view(" ", false, "name")), "name");
		created(saveView(catalog, this.architect, view("Compact view", false, "name")));
		assertInvalid(saveView(catalog, this.architect, view("COMPACT VIEW ", false, "name")), "name");
		// a join column of a view's filter and a join filter are accepted
		created(saveView(catalog, this.architect, view("Detail view", false, "name")
			.<ObjectNode>set("filters", array(expression(join + "." + key(this.protocol), "eq", "REST")))
			.<ObjectNode>set("joinModeOverrides", array(override(join, "rows")))
			.<ObjectNode>set("sort", sort(join + ".name", "asc"))));
	}

	@Test
	void viewAtItsLimits() {
		String catalog = catalog(this.application);
		ObjectNode[] columns = new ObjectNode[100];
		String[] order = new String[100];
		columns[0] = field("name");
		order[0] = "name";
		for (int i = 1; i < columns.length; i++) {
			UUID property = property("Column " + i + " " + suffix(), "string");
			map(this.application, property);
			columns[i] = property(property);
			order[i] = key(property);
		}
		ok(columns(catalog, columns));
		ObjectNode[] twenty = Stream.generate(() -> expression("name", "isNotNull", null))
			.limit(20)
			.toArray(ObjectNode[]::new);
		JsonNode saved = created(saveView(catalog, this.architect, view("v".repeat(100), false, order)
			.put("description", "d".repeat(1000))
			.<ObjectNode>set("filters", array(twenty))));
		assertThat(saved.path("savedViews").get(0).path("columnOrder")).hasSize(100);
		String[] tooMany = Arrays.copyOf(order, 101);
		tooMany[100] = "name";
		assertInvalid(saveView(catalog, this.architect, view("Too many columns", false, tooMany)), "columnOrder");
		ObjectNode[] twentyOne = Stream.generate(() -> expression("name", "isNotNull", null))
			.limit(21)
			.toArray(ObjectNode[]::new);
		assertInvalid(saveView(catalog, this.architect,
				view("Too many filters", false, "name").<ObjectNode>set("filters", array(twentyOne))), "filters");
		assertInvalid(saveView(catalog, this.architect, view("v".repeat(101), false, "name")), "name");
		assertInvalid(saveView(catalog, this.architect,
				view("Long description", false, "name").put("description", "d".repeat(1001))), "description");
	}

	@Test
	void atMostTwentyViews() {
		String catalog = applicationCatalog();
		for (int i = 0; i < 20; i++) {
			created(saveView(catalog, this.architect, view("V" + i, false, "name")));
		}
		assertInvalid(saveView(catalog, this.architect, view("V20", false, "name")), "savedViews");
	}

	// Default view

	@Test
	void defaultSwitchAndRemoval() {
		String catalog = applicationCatalog();
		String compact = created(saveView(catalog, this.architect, view("Compact view", true, "name")))
			.path("savedViews").get(0).path("id").asString();
		JsonNode both = created(saveView(catalog, this.architect, view("Full view", true, "name", key(this.status))));
		assertThat(defaults(both)).containsExactly("Full view");
		JsonNode switched = ok(call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/views/" + compact,
				view("Compact view", true, "name", key(this.vendor)), this.architect));
		assertThat(defaults(switched)).containsExactly("Compact view");
		assertThat(strings(switched.path("savedViews").get(0).path("columnOrder"))).containsExactly("name",
				key(this.vendor));
		JsonNode removed = ok(call(HttpMethod.DELETE, CATALOGS + "/" + catalog + "/views/" + compact, null,
				this.architect));
		assertThat(removed.path("savedViews")).hasSize(1);
		assertThat(defaults(removed)).isEmpty();
		assertThat(call(HttpMethod.DELETE, CATALOGS + "/" + catalog + "/views/" + compact, null, this.architect))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type IN ('catalog.view-added', "
				+ "'catalog.view-changed', 'catalog.view-removed') AND target_id = '" + catalog + "'")).isEqualTo(4);
	}

	// Columns of a view in the query

	@Test
	void columnsOfAView() {
		int crm = entity(this.application, "CRM System");
		value(crm, this.status, "active");
		value(crm, this.vendor, "Salesforce");
		String catalog = applicationCatalog();
		String join = interfacesJoin(catalog);
		String view = created(saveView(catalog, this.architect, view("Compact view", false, key(this.status), "name")))
			.path("savedViews").get(0).path("id").asString();
		JsonNode result = ok(query(catalog, "view=" + view));
		assertThat(keys(result)).containsExactly(key(this.status), "name");
		assertThat(result.path("joins").get(0).path("id").asString()).isEqualTo(join);
		assertThat(result.path("rows").get(0).path("values").has(key(this.vendor))).isFalse();
		ok(query(catalog, "view=" + view + "&filter=" + key(this.status) + ":eq:active&sort=name:desc"));
		assertBad(query(catalog, "view=" + view + "&filter=" + key(this.vendor) + ":eq:SAP"), "filter");
		assertBad(query(catalog, "view=" + view + "&sort=" + key(this.vendor) + ":asc"), "sort");
		assertBad(query(catalog, "view=" + UUID.randomUUID()), "view");
		assertBad(query(catalog, "view=nonsense"), "view");
	}

	// Availability of a view

	@Test
	void removedColumnMakesTheViewUnavailable() {
		String catalog = applicationCatalog();
		String view = created(saveView(catalog, this.architect, view("Vendors", false, "name", key(this.vendor))))
			.path("savedViews").get(0).path("id").asString();
		JsonNode changed = ok(columns(catalog, field("name"), property(this.status)));
		assertThat(changed.path("savedViews").get(0).path("available").asBoolean()).isFalse();
		assertBad(query(catalog, "view=" + view), "view");
	}

	@Test
	void removingAJoinCleansTheViews() {
		String catalog = applicationCatalog();
		String join = interfacesJoin(catalog);
		created(saveView(catalog, this.architect, view("Detail view", false, "name")
			.<ObjectNode>set("filters", array(expression(join + "." + key(this.protocol), "eq", "REST"),
					expression("name", "isNotNull", null)))
			.<ObjectNode>set("joinModeOverrides", array(override(join, "rows")))
			.<ObjectNode>set("sort", sort(join + ".name", "asc"))));
		JsonNode changed = ok(call(HttpMethod.DELETE, CATALOGS + "/" + catalog + "/joins/" + join, null, this.architect));
		JsonNode view = changed.path("savedViews").get(0);
		assertThat(view.path("joinModeOverrides")).isEmpty();
		assertThat(view.path("sort").isNull() || view.path("sort").isMissingNode()).isTrue();
		assertThat(view.path("filters")).hasSize(1);
		assertThat(view.path("available").asBoolean()).isTrue();
	}

	// Who changes views

	@Test
	void readerOfAnInstanceCatalog() {
		String catalog = applicationCatalog();
		assertThat(saveView(catalog, this.viewer, view("Mine", false, "name"))).hasStatus(HttpStatus.FORBIDDEN);
		String id = created(saveView(catalog, this.architect, view("Theirs", false, "name")))
			.path("savedViews").get(0).path("id").asString();
		assertThat(call(HttpMethod.PUT, CATALOGS + "/" + catalog + "/views/" + id, view("Theirs", true, "name"),
				this.viewer)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.DELETE, CATALOGS + "/" + catalog + "/views/" + id, null, this.viewer))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void ownPersonalCatalog() {
		String own = catalog(this.application, "personal", this.viewer);
		assertThat(created(saveView(own, this.viewer, view("Mine", true, "name"))).path("savedViews")).hasSize(1);
		long viewerId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.viewer.sessionId());
		assertThat(this.jdbc.queryForObject("""
				SELECT actor_person_id FROM audit.events WHERE event_type = 'catalog.view-added' AND target_id = ?""",
				Long.class, own)).isEqualTo(viewerId);
	}

	// view helpers

	private static ObjectNode view(String name, boolean isDefault, String... columns) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("isDefault", isDefault);
		ArrayNode list = body.putArray("columnOrder");
		for (String column : columns) {
			list.add(column);
		}
		return body;
	}

	private static ArrayNode array(ObjectNode... items) {
		ArrayNode array = JsonMapper.shared().createArrayNode();
		for (ObjectNode item : items) {
			array.add(item);
		}
		return array;
	}

	private static ObjectNode override(String join, String mode) {
		return JsonMapper.shared().createObjectNode().put("joinDefinitionId", join).put("joinMode", mode);
	}

	private static ObjectNode sort(String column, String direction) {
		return JsonMapper.shared().createObjectNode().put("column", column).put("direction", direction);
	}

	private MvcTestResult saveView(String catalog, TestSession session, ObjectNode view) {
		return call(HttpMethod.POST, CATALOGS + "/" + catalog + "/views", view, session);
	}

	private static List<String> defaults(JsonNode catalog) {
		List<String> names = new ArrayList<>();
		catalog.path("savedViews").forEach((view) -> {
			if (view.path("isDefault").asBoolean()) {
				names.add(view.path("name").asString());
			}
		});
		return names;
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach((value) -> values.add(value.asString()));
		return values;
	}

	private static List<String> keys(JsonNode result) {
		List<String> keys = new ArrayList<>();
		result.path("columns").forEach((column) -> keys.add(column.path("key").asString()));
		return keys;
	}

	private static void assertBad(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(fields(result)).containsExactly(field);
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
			return Files.createTempDirectory("oea-catalog-views").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

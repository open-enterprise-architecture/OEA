package io.oea.app.solution;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Specs solution/plateaus (content of a plateau), solution/solution-baseline (baseline in plateau
 * mode, lifecycle of an entry) and solution/solution-deltas (before values from the basis): US-155;
 * design D4, D5 of us-084-plateaus.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlateauContentTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = PlateausTests.tempDirectory() + "/setup-token";

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

	private TestSession michael;

	private long michaelId;

	private UUID application;

	private UUID component;

	private UUID cost;

	private String baseline;

	/** Target 2027: succeeds the baseline, basis catalog. */
	private String target2027;

	/** Target 2028: succeeds Target 2027, basis predecessor. */
	private String target2028;

	/** Phase 2: succeeds Target 2028, basis predecessor. */
	private String phase2;

	private String crm;

	private String analytics;

	private String website;

	private int legacy;

	private int erp;

	private long clock;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = principals.personWithIdpRole("solution-architect");
		this.michaelId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.michael.sessionId());
		// every test starts from an empty landscape and its own plateaus
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE deleted_at IS NULL",
				this.michaelId);
		this.jdbc.update("""
				INSERT INTO plateaus (id, name, status, created_by) VALUES (gen_random_uuid(), 'Baseline', 'baseline', ?)
				ON CONFLICT DO NOTHING""", this.michaelId);
		this.baseline = this.jdbc.queryForObject("SELECT id::text FROM plateaus WHERE status = 'baseline'",
				String.class);
		this.target2027 = plateau("Target 2027", "target", this.baseline, "catalog");
		this.target2028 = plateau("Target 2028", "target", this.target2027, "predecessor");
		this.phase2 = plateau("Phase 2", "transition", this.target2028, "predecessor");
		this.application = metatype("Application");
		this.component = metatype("Component");
		this.cost = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, 'string')
				RETURNING id""", UUID.class, "Cost " + suffix(), "p_" + suffix() + suffix());
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", this.application, this.cost);
		this.crm = solution("CRM Replacement", this.baseline, this.target2027);
		this.analytics = solution("Analytics Platform", this.target2027, this.target2028);
		this.website = solution("Website Relaunch", null, null);
		this.legacy = entity(this.application, "Legacy CRM", null);
		this.erp = entity(this.application, "ERP-Core", "ERP");
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, ?)",
				this.erp, this.cost, "CC-1");
		this.clock = 0;
	}

	// Requirement: Basis of a plateau, Baseline of a solution in plateau mode

	@Test
	void basisCatalog() {
		retiring(this.crm, this.legacy);
		JsonNode page = landscape(this.crm, "");
		assertThat(names(page)).containsExactly("ERP-Core", "Legacy CRM");
		page.path("items").forEach((item) -> {
			assertThat(item.path("lifecycle").asString()).isEqualTo("active");
			assertThat(item.path("changedBy").isNull()).isTrue();
		});
	}

	@Test
	void projectSolutionSeesTheCatalog() {
		retiring(this.crm, this.legacy);
		JsonNode page = landscape(this.website, "");
		assertThat(names(page)).containsExactly("ERP-Core", "Legacy CRM");
		page.path("items").forEach((item) -> assertThat(item.path("lifecycle").asString()).isEqualTo("active"));
	}

	// Requirement: Content of a plateau, Lifecycle of a baseline entry

	@Test
	void contentOfThePredecessor() {
		retiring(this.crm, this.legacy);
		modified(this.crm, this.erp, "name", "ERP-Core 2");
		String proposal = proposed(this.crm, this.application, "Salesforce");
		JsonNode page = landscape(this.analytics, "");
		Map<String, JsonNode> byName = byName(page);
		assertThat(byName.keySet()).containsExactly("ERP-Core 2", "Legacy CRM", "Salesforce");
		assertThat(byName.get("Legacy CRM").path("lifecycle").asString()).isEqualTo("retiring");
		assertThat(byName.get("Legacy CRM").path("changedBy").path("solutionId").asString()).isEqualTo(this.crm);
		assertThat(byName.get("ERP-Core 2").path("lifecycle").asString()).isEqualTo("active");
		assertThat(byName.get("ERP-Core 2").path("changedBy").path("name").asString()).startsWith("CRM Replacement");
		JsonNode salesforce = byName.get("Salesforce");
		assertThat(salesforce.path("lifecycle").asString()).isEqualTo("proposed");
		assertThat(salesforce.path("id").isNull()).isTrue();
		assertThat(salesforce.path("deltaId").asString()).isEqualTo(proposal);
		assertThat(salesforce.path("version").asInt()).isZero();
		assertThat(salesforce.path("parallelChanges")).isEmpty();
	}

	@Test
	void searchFindsTheOverlaidName() {
		modified(this.crm, this.erp, "name", "Core Platform");
		assertThat(names(landscape(this.analytics, "&q=Core%20Platform"))).containsExactly("Core Platform");
		assertThat(names(landscape(this.analytics, "&q=ERP"))).isEmpty();
		// the solution of Target 2027 itself still sees the catalog
		assertThat(names(landscape(this.crm, "&q=ERP"))).containsExactly("ERP-Core");
	}

	@Test
	void metatypeFilterIncludesProposedEntries() {
		proposed(this.crm, this.component, "Gateway");
		proposed(this.crm, this.application, "Salesforce");
		assertThat(names(landscape(this.analytics, "&metatypeId=" + this.component))).containsExactly("Gateway");
	}

	@Test
	void chainOfPredecessors() {
		String phase = solution("Phase Work", this.target2028, this.phase2);
		retiring(this.crm, this.legacy);
		modified(this.analytics, this.erp, "name", "ERP Cloud");
		Map<String, JsonNode> byName = byName(landscape(phase, ""));
		assertThat(byName.keySet()).containsExactly("ERP Cloud", "Legacy CRM");
		assertThat(byName.get("Legacy CRM").path("lifecycle").asString()).isEqualTo("retiring");
		assertThat(byName.get("ERP Cloud").path("changedBy").path("solutionId").asString()).isEqualTo(this.analytics);
	}

	@ParameterizedTest
	@ValueSource(strings = { "implemented", "archived" })
	void completedSolutionsAreNotApplied(String status) {
		retiring(this.crm, this.legacy);
		this.jdbc.update("UPDATE solutions SET status = ? WHERE id = ?::uuid", status, this.crm);
		byName(landscape(this.analytics, ""))
			.forEach((name, item) -> assertThat(item.path("lifecycle").asString()).as(name).isEqualTo("active"));
	}

	@Test
	void laterDeltaWinsInOneLayer() {
		String erpExtension = solution("ERP Extension", this.baseline, this.target2027);
		modified(this.crm, this.erp, "description", "From CRM");
		modified(erpExtension, this.erp, "description", "From ERP Extension");
		assertThat(entry(this.analytics, this.erp).path("description").asString()).isEqualTo("From ERP Extension");
	}

	@Test
	void laterLayerWins() {
		String phase = solution("Phase Work", this.target2028, this.phase2);
		// the delta of Target 2028 is older, but its layer comes later
		modified(this.analytics, this.erp, "description", "From 2028");
		modified(this.crm, this.erp, "description", "From 2027");
		assertThat(entry(phase, this.erp).path("description").asString()).isEqualTo("From 2028");
	}

	@Test
	void laterModificationKeepsItRetiring() {
		String erpExtension = solution("ERP Extension", this.baseline, this.target2027);
		retiring(this.crm, this.legacy);
		modified(erpExtension, this.legacy, "name", "Legacy CRM 2");
		JsonNode item = byName(landscape(this.analytics, "")).get("Legacy CRM 2");
		assertThat(item.path("lifecycle").asString()).isEqualTo("retiring");
		assertThat(item.path("changedBy").path("solutionId").asString()).isEqualTo(this.crm);
	}

	@Test
	void entryWithOverlaidValues() {
		modified(this.crm, this.erp, "description", "Core ERP");
		modifiedProperty(solution("ERP Extension", this.baseline, this.target2027), this.erp, "CC-2");
		JsonNode entry = entry(this.analytics, this.erp);
		assertThat(entry.path("description").asString()).isEqualTo("Core ERP");
		assertThat(entry.path("lifecycle").asString()).isEqualTo("active");
		assertThat(entry.path("properties").get(0).path("valueText").asString()).isEqualTo("CC-2");
		// the catalog and a project solution are unchanged
		JsonNode project = entry(this.website, this.erp);
		assertThat(project.path("description").asString()).isEqualTo("ERP");
		assertThat(project.path("properties").get(0).path("valueText").asString()).isEqualTo("CC-1");
	}

	@Test
	void removedValueIsGone() {
		modifiedProperty(this.crm, this.erp, null);
		assertThat(entry(this.analytics, this.erp).path("properties")).isEmpty();
	}

	// Requirement: Order and paging of the baseline across proposed and catalog entries

	@ParameterizedTest
	@ValueSource(ints = { 1, 2 })
	void pagingAcrossProposedEntries(int pageSize) {
		entity(this.application, "Billing", null);
		proposed(this.crm, this.application, "Analytics");
		proposed(this.crm, this.application, "ERP-Core");
		List<String> all = names(landscape(this.analytics, "&pageSize=100"));
		assertThat(all).containsExactly("Analytics", "Billing", "ERP-Core", "ERP-Core", "Legacy CRM");
		List<String> byOffset = new ArrayList<>();
		List<String> byCursor = new ArrayList<>();
		String cursor = null;
		for (int page = 0; page * pageSize < all.size(); page++) {
			byOffset.addAll(names(landscape(this.analytics, "&pageSize=" + pageSize + "&page=" + page)));
			JsonNode next = landscape(this.analytics,
					"&pageSize=" + pageSize + "&page=" + page + ((cursor != null) ? "&cursor=" + cursor : ""));
			byCursor.addAll(names(next));
			cursor = next.path("nextCursor").isNull() ? null : next.path("nextCursor").asString();
		}
		assertThat(byOffset).isEqualTo(all);
		assertThat(byCursor).isEqualTo(all);
		assertThat(cursor).isNull();
	}

	// us-086-plateau-go-live D5 – Requirement: Diff of a plateau solution

	@Test
	void diffAgainstTheBasis() {
		modified(this.crm, this.erp, "description", "Core ERP");
		capture(this.analytics, this.erp, "Analytics ERP");
		JsonNode field = description(get("/api/v1/solutions/" + this.analytics + "/diff").path("entries").get(0));
		assertThat(field.path("current").path("valueText").asString()).isEqualTo("Core ERP");
		assertThat(field.path("catalogChanged").asBoolean()).isFalse();
		// a project solution compares with the catalog
		capture(this.website, this.erp, "Website ERP");
		JsonNode project = description(get("/api/v1/solutions/" + this.website + "/diff").path("entries").get(0));
		assertThat(project.path("current").path("valueText").asString()).isEqualTo("ERP");
	}

	private static JsonNode description(JsonNode entry) {
		for (JsonNode field : entry.path("fields")) {
			if ("description".equals(field.path("field").asString())) {
				return field;
			}
		}
		throw new AssertionError("no description in " + entry);
	}

	// Requirement: Before values from the basis

	@Test
	void beforeValueFromThePredecessor() {
		modified(this.crm, this.erp, "description", "Core ERP");
		JsonNode created = capture(this.analytics, this.erp, "Analytics ERP");
		assertThat(created.path("changes").get(0).path("before").path("valueText").asString()).isEqualTo("Core ERP");
	}

	@Test
	void beforeValueFromTheCatalog() {
		modified(this.crm, this.erp, "description", "Core ERP");
		JsonNode created = capture(this.website, this.erp, "Website ERP");
		assertThat(created.path("changes").get(0).path("before").path("valueText").asString()).isEqualTo("ERP");
	}

	@Test
	void retiringEntityStillGetsADelta() {
		retiring(this.crm, this.legacy);
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("deltaType", "retiring")
			.put("entityId", this.legacy);
		MvcTestResult result = post(this.analytics, body);
		assertThat(result).as(() -> PlateausTests.content(result)).hasStatus(HttpStatus.CREATED);
	}

	// helpers

	private JsonNode capture(String solutionId, int entityId, String description) {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("deltaType", "modified")
			.put("entityId", entityId)
			.put("description", description);
		body.putArray("properties");
		MvcTestResult result = post(solutionId, body);
		assertThat(result).as(() -> PlateausTests.content(result)).hasStatus(HttpStatus.CREATED);
		return PlateausTests.body(result);
	}

	private MvcTestResult post(String solutionId, ObjectNode body) {
		return this.mvc.post()
			.uri("/api/v1/solutions/" + solutionId + "/deltas")
			.cookie(new Cookie(SessionCookie.NAME, this.michael.token()))
			.header(CsrfOriginFilter.HEADER, this.michael.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
	}

	private JsonNode landscape(String solutionId, String query) {
		return get("/api/v1/solutions/" + solutionId + "/landscape?pageSize=50" + query);
	}

	private JsonNode entry(String solutionId, int entityId) {
		return get("/api/v1/solutions/" + solutionId + "/landscape/" + entityId);
	}

	private JsonNode get(String path) {
		MvcTestResult result = this.mvc.get()
			.uri(UriComponentsBuilder.fromUriString(path).build(true).toUri())
			.cookie(new Cookie(SessionCookie.NAME, this.michael.token()))
			.header(CsrfOriginFilter.HEADER, this.michael.csrfToken())
			.exchange();
		assertThat(result).as(() -> PlateausTests.content(result)).hasStatus(HttpStatus.OK);
		return PlateausTests.body(result);
	}

	private static List<String> names(JsonNode page) {
		return page.path("items").valueStream().map((item) -> item.path("name").asString()).toList();
	}

	private static Map<String, JsonNode> byName(JsonNode page) {
		return page.path("items")
			.valueStream()
			.collect(Collectors.toMap((item) -> item.path("name").asString(), (item) -> item, (a, b) -> a,
					java.util.LinkedHashMap::new));
	}

	private String plateau(String name, String status, String succeeds, String basis) {
		return this.jdbc.queryForObject("""
				INSERT INTO plateaus (id, name, status, succeeds, basis, created_by)
				VALUES (gen_random_uuid(), ?, ?, ?::uuid, ?, ?) RETURNING id::text""", String.class,
				name + " " + suffix(), status, succeeds, basis, this.michaelId);
	}

	private String solution(String name, String from, String to) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, from_plateau_id, to_plateau_id)
				VALUES (gen_random_uuid(), ?, ?, ?, ?::uuid, ?::uuid) RETURNING id::text""", String.class,
				name + " " + suffix(), this.michaelId, this.michaelId, from, to);
	}

	private UUID metatype(String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, false) RETURNING id""", UUID.class,
				name + " " + suffix());
	}

	private int entity(UUID type, String name, String description) {
		return this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, description, created_by) VALUES (?, ?, coalesce(?, ''), ?) RETURNING id",
				Integer.class, type, name, description, this.michaelId);
	}

	/** A delta created one minute after the previous one, so that the order of creation is the rank. */
	private String delta(String solutionId, String type, Integer entityId, UUID metatype, String name) {
		this.clock++;
		return this.jdbc.queryForObject("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, metatype_id, name, created_by, created_at)
				VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, ?, ?, TIMESTAMPTZ '2026-11-01 00:00:00Z' + ? * interval '1 minute')
				RETURNING id::text""", String.class, solutionId, type, entityId, metatype, name, this.michaelId,
				this.clock);
	}

	private void retiring(String solutionId, int entityId) {
		delta(solutionId, "retiring", entityId, null, null);
	}

	private String proposed(String solutionId, UUID metatype, String name) {
		return delta(solutionId, "new", null, metatype, name);
	}

	private void modified(String solutionId, int entityId, String field, String value) {
		String delta = delta(solutionId, "modified", entityId, null, null);
		this.jdbc.update("""
				INSERT INTO entity_delta_property_values (id, delta_id, system_field, after_value_text)
				VALUES (gen_random_uuid(), ?::uuid, ?, ?)""", delta, field, value);
	}

	private void modifiedProperty(String solutionId, int entityId, String value) {
		String delta = delta(solutionId, "modified", entityId, null, null);
		this.jdbc.update("""
				INSERT INTO entity_delta_property_values (id, delta_id, property_def_id, after_value_text, before_value_text)
				VALUES (gen_random_uuid(), ?::uuid, ?, ?, 'CC-1')""", delta, this.cost, value);
	}

	private static String suffix() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

}

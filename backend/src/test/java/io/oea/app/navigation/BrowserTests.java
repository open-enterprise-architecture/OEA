package io.oea.app.navigation;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The solution browser (us-132-solution-browser): navigation/solution-browser on an own database.
 * Kurt is an enterprise architect, Franz a viewer. The database is shared by the tests of this
 * class, so counts are compared with the counts before a test changed the data, and objects are
 * found by their unique names.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BrowserTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String BROWSER = "/api/v1/views/browser";

	private static final String ITEMS = "/api/v1/views/browser/items";

	private static final UUID STRUCTURING_AID = UUID.fromString("c3f523b8-f07f-5475-9ab0-6f2ea39b537e");

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

	private TestSession kurt;

	private TestSession franz;

	private long kurtId;

	private UUID element;

	private UUID connection;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.kurtId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.kurt.sessionId());
		this.element = metatype("Application " + suffix(), false, "APP");
		this.connection = metatype("Data Flow " + suffix(), true, null);
	}

	// Top level

	@Test
	void plateausByValidFromThenName() {
		UUID baseline = baselinePlateau();
		String late = "TO-BE 2030 " + suffix();
		String early = "Phase 2027 " + suffix();
		String sameDayB = "B-Same " + suffix();
		String sameDayA = "a-Same " + suffix();
		plateau(late, "target", baseline, "2030-01-01");
		plateau(early, "transition", baseline, "2027-01-01");
		plateau(sameDayB, "target", baseline, "2028-06-01");
		plateau(sameDayA, "target", baseline, "2028-06-01");
		List<String> names = names(ok(get(BROWSER, this.kurt)).path("plateaus"));
		assertThat(names).containsSubsequence(early, sameDayA, sameDayB, late);
	}

	@Test
	void baselineAndRealizedHaveNoNode() {
		UUID baseline = baselinePlateau();
		String baselineName = this.jdbc.queryForObject("SELECT name FROM plateaus WHERE id = ?", String.class,
				baseline);
		String realized = "Realized " + suffix();
		plateau(realized, "realized", baseline, "2020-01-01");
		List<String> names = names(ok(get(BROWSER, this.kurt)).path("plateaus"));
		assertThat(names).doesNotContain(baselineName, realized);
	}

	@Test
	void emptyPlateauStaysWithoutSolutions() {
		String name = "Empty " + suffix();
		plateau(name, "target", baselinePlateau(), "2031-01-01");
		JsonNode plateau = byName(ok(get(BROWSER, this.kurt)).path("plateaus"), name);
		assertThat(plateau.path("solutions").size()).isZero();
		assertThat(plateau.path("status").asString()).isEqualTo("target");
	}

	@Test
	void everyUserSeesTheTopLevel() {
		JsonNode top = ok(get(BROWSER, this.franz));
		assertThat(top.has("currentState")).isTrue();
		assertThat(top.path("projects").isArray()).isTrue();
		assertThat(top.path("plateaus").isArray()).isTrue();
	}

	// Solutions

	@Test
	void plateauAndProjectSolutionsSortedCaseInsensitively() {
		UUID target = plateau("Target " + suffix(), "target", baselinePlateau(), "2032-01-01");
		String targetName = this.jdbc.queryForObject("SELECT name FROM plateaus WHERE id = ?", String.class, target);
		String suffix = suffix();
		solution("crm b " + suffix, "draft", target);
		solution("CRM A " + suffix, "approved", target);
		solution("erp b " + suffix, "proposed", null);
		solution("ERP A " + suffix, "in-progress", null);
		JsonNode top = ok(get(BROWSER, this.kurt));
		assertThat(names(byName(top.path("plateaus"), targetName).path("solutions"))).containsExactly(
				"CRM A " + suffix, "crm b " + suffix);
		assertThat(names(top.path("projects"))).containsSubsequence("ERP A " + suffix, "erp b " + suffix);
		assertThat(names(top.path("projects"))).doesNotContain("CRM A " + suffix);
	}

	@ParameterizedTest
	@ValueSource(strings = { "implemented", "archived" })
	void liveAndArchivedSolutionsLeaveTheBrowser(String status) {
		UUID target = plateau("Target " + suffix(), "target", baselinePlateau(), "2033-01-01");
		String targetName = this.jdbc.queryForObject("SELECT name FROM plateaus WHERE id = ?", String.class, target);
		String inPlateau = "Plateau " + status + " " + suffix();
		String project = "Project " + status + " " + suffix();
		solution(inPlateau, status, target);
		solution(project, status, null);
		JsonNode top = ok(get(BROWSER, this.kurt));
		assertThat(byName(top.path("plateaus"), targetName).path("solutions").size()).isZero();
		assertThat(names(top.path("projects"))).doesNotContain(project);
	}

	@ParameterizedTest
	@ValueSource(strings = { "draft", "proposed", "approved", "in-progress" })
	void activeStatusesAppear(String status) {
		String project = "Active " + status + " " + suffix();
		solution(project, status, null);
		JsonNode listed = byName(ok(get(BROWSER, this.kurt)).path("projects"), project);
		assertThat(listed.path("status").asString()).isEqualTo(status);
	}

	@Test
	void solutionCountsItsDeltas() {
		int sap = entity("SAP ERP " + suffix(), this.element);
		int legacy = entity("Legacy CRM " + suffix(), this.element);
		String name = "CRM Migration " + suffix();
		UUID solution = solution(name, "draft", null);
		String empty = "ERP Upgrade " + suffix();
		solution(empty, "draft", null);
		newDelta(solution, "CRM Cloud", this.element);
		delta(solution, "modified", sap);
		delta(solution, "retiring", legacy);
		JsonNode projects = ok(get(BROWSER, this.kurt)).path("projects");
		assertThat(byName(projects, name).path("components").asInt()).isEqualTo(3);
		assertThat(byName(projects, empty).path("components").asInt()).isZero();
	}

	// Categories

	@Test
	void currentStateCountsWithoutDeletedEntities() {
		JsonNode before = ok(get(BROWSER, this.kurt)).path("currentState");
		entity("Alive 1 " + suffix(), this.element);
		entity("Alive 2 " + suffix(), this.element);
		int gone = entity("Gone " + suffix(), this.element);
		deleteEntity(gone);
		entity("Link " + suffix(), this.connection);
		JsonNode after = ok(get(BROWSER, this.kurt)).path("currentState");
		assertThat(after.path("components").asInt() - before.path("components").asInt()).isEqualTo(2);
		assertThat(after.path("connections").asInt() - before.path("connections").asInt()).isEqualTo(1);
		assertThat(after.path("catalogs").asInt()).isEqualTo(before.path("catalogs").asInt());
	}

	@Test
	void componentsAndConnectionsListByNameWithAbbreviation() {
		String suffix = suffix();
		entity("0browse b" + suffix, this.element);
		entity("0browse A" + suffix, this.element);
		entity("0browse link" + suffix, this.connection);
		JsonNode components = ok(items("current-state", null, "components", 0, 200, this.kurt)).path("items");
		JsonNode first = byName(components, "0browse A" + suffix);
		assertThat(first.path("kind").asString()).isEqualTo("entity");
		assertThat(first.path("metatypeAbbreviation").asString()).isEqualTo("APP");
		assertThat(first.path("entityId").isNull()).isTrue();
		assertThat(names(components)).containsSubsequence("0browse A" + suffix, "0browse b" + suffix);
		assertThat(names(components)).doesNotContain("0browse link" + suffix);
		JsonNode connections = ok(items("current-state", null, "connections", 0, 200, this.kurt)).path("items");
		JsonNode link = byName(connections, "0browse link" + suffix);
		assertThat(link.path("metatypeAbbreviation").isNull()).isTrue();
	}

	@Test
	void equalNamesOrderById() {
		String name = "0same " + suffix();
		int a = entity(name, this.element);
		int b = entity(name, this.element);
		JsonNode items = ok(items("current-state", null, "components", 0, 200, this.kurt)).path("items");
		List<Integer> ids = new ArrayList<>();
		items.forEach((item) -> {
			if (name.equals(item.path("name").asString())) {
				ids.add(Integer.parseInt(item.path("id").asString()));
			}
		});
		assertThat(ids).containsExactly(Math.min(a, b), Math.max(a, b));
	}

	@Test
	void personalCatalogOfAnotherPersonIsInvisible() {
		JsonNode franzBefore = ok(get(BROWSER, this.franz)).path("currentState");
		JsonNode kurtBefore = ok(get(BROWSER, this.kurt)).path("currentState");
		String instance = "Instance " + suffix();
		String personal = "Kurt's Candidates " + suffix();
		catalog(instance, "instance", this.kurtId);
		catalog(personal, "personal", this.kurtId);
		JsonNode franzAfter = ok(get(BROWSER, this.franz)).path("currentState");
		JsonNode kurtAfter = ok(get(BROWSER, this.kurt)).path("currentState");
		assertThat(franzAfter.path("catalogs").asInt() - franzBefore.path("catalogs").asInt()).isEqualTo(1);
		assertThat(kurtAfter.path("catalogs").asInt() - kurtBefore.path("catalogs").asInt()).isEqualTo(2);
		assertThat(names(ok(items("current-state", null, "catalogs", 0, 200, this.franz)).path("items")))
			.contains(instance)
			.doesNotContain(personal);
		JsonNode own = byName(ok(items("current-state", null, "catalogs", 0, 200, this.kurt)).path("items"),
				personal);
		assertThat(own.path("kind").asString()).isEqualTo("catalog");
		assertThat(own.path("metatypeAbbreviation").isNull()).isTrue();
	}

	@Test
	void deltasOfASolutionWithTheirTypes() {
		String suffix = suffix();
		int sap = entity("SAP ERP " + suffix, this.element);
		int legacy = entity("Legacy CRM " + suffix, this.element);
		UUID solution = solution("CRM Migration " + suffix, "draft", null);
		String cloud = newDelta(solution, "CRM Cloud " + suffix, this.element);
		delta(solution, "modified", sap);
		delta(solution, "retiring", legacy);
		JsonNode result = ok(items("solution", solution, "components", 0, 50, this.kurt));
		JsonNode items = result.path("items");
		assertThat(names(items)).containsExactly("CRM Cloud " + suffix, "Legacy CRM " + suffix, "SAP ERP " + suffix);
		assertThat(texts(items, "deltaType")).containsExactly("new", "retiring", "modified");
		assertThat(items.get(0).path("id").asString()).isEqualTo(cloud);
		assertThat(items.get(0).path("entityId").isNull()).isTrue();
		assertThat(items.get(0).path("solutionId").asString()).isEqualTo(solution.toString());
		assertThat(items.get(0).path("kind").asString()).isEqualTo("delta");
		assertThat(items.get(0).path("metatypeAbbreviation").asString()).isEqualTo("APP");
		assertThat(items.get(1).path("entityId").asInt()).isEqualTo(legacy);
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(3);
	}

	@Test
	void deltaNamesFollowTheDeltaTypeNotTheEntityId() {
		String suffix = suffix();
		int renamed = entity("Before " + suffix, this.element);
		int deleted = entity("Deleted " + suffix, this.element);
		int live = entity("Planned " + suffix, this.element);
		UUID solution = solution("Names " + suffix, "draft", null);
		delta(solution, "modified", renamed);
		delta(solution, "retiring", deleted);
		String goneLive = newDelta(solution, "Planned " + suffix, this.element);
		// the go-live gave the delta new its entity; the entity may have another name meanwhile
		this.jdbc.update("""
				UPDATE entity_deltas SET entity_id = ? WHERE id = ?::uuid""", live,
				goneLive);
		this.jdbc.update("UPDATE entities SET name = ? WHERE id = ?", "After " + suffix, renamed);
		this.jdbc.update("UPDATE entities SET name = ? WHERE id = ?", "Renamed live " + suffix, live);
		deleteEntity(deleted);
		assertThat(names(ok(items("solution", solution, "components", 0, 50, this.kurt)).path("items")))
			.containsExactly("After " + suffix, "Deleted " + suffix, "Planned " + suffix);
	}

	@ParameterizedTest
	@ValueSource(strings = { "implemented", "archived" })
	void liveSolutionStillListsItsDeltas(String status) {
		UUID solution = solution("Done " + status + " " + suffix(), status, null);
		delta(solution, "retiring", entity("Old " + suffix(), this.element));
		assertThat(ok(items("solution", solution, "components", 0, 50, this.kurt)).path("items").size()).isEqualTo(1);
	}

	@Test
	void solutionWithoutDeltas() {
		UUID solution = solution("Nothing " + suffix(), "draft", null);
		JsonNode result = ok(items("solution", solution, "components", 0, 50, this.kurt));
		assertThat(result.path("items").size()).isZero();
		assertThat(result.path("page").path("totalElements").asInt()).isZero();
		assertThat(result.path("page").path("totalPages").asInt()).isZero();
	}

	// Errors

	@Test
	void solutionScopeNeedsSolutionId() {
		assertInvalid(items("solution", null, "components", 0, 50, this.kurt), "solutionId");
	}

	@Test
	void currentStateTakesNoSolutionId() {
		UUID solution = solution("Any " + suffix(), "draft", null);
		assertInvalid(items("current-state", solution, "components", 0, 50, this.kurt), "solutionId");
	}

	@ParameterizedTest
	@ValueSource(strings = { "connections", "catalogs" })
	void solutionHasComponentsOnly(String category) {
		UUID solution = solution("Only components " + suffix(), "draft", null);
		assertInvalid(items("solution", solution, category, 0, 50, this.kurt), "category");
	}

	@Test
	void unknownSolution() {
		assertThat(items("solution", UUID.randomUUID(), "components", 0, 50, this.kurt)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-a-uuid", "1", "00000000-0000-0000-0000-00000000000g" })
	void solutionIdWithoutUuidFormat(String value) {
		assertThat(get(ITEMS + "?scope=solution&category=components&solutionId=" + value, this.kurt))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@ParameterizedTest
	@ValueSource(strings = { "?category=components", "?scope=current-state", "?scope=all&category=components",
			"?scope=current-state&category=diagrams" })
	void missingOrUnknownEnumValue(String query) {
		assertThat(get(ITEMS + query, this.kurt)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// Paging

	@ParameterizedTest
	@CsvSource({ "1,200", "200,200", "0,400", "201,400", "-1,400" })
	void pageSizeBoundaries(int pageSize, int status) {
		assertThat(items("current-state", null, "components", 0, pageSize, this.kurt)).hasStatus(status);
	}

	@ParameterizedTest
	@CsvSource({ "0,200", "-1,400" })
	void pageBoundaries(int page, int status) {
		assertThat(items("current-state", null, "components", page, 50, this.kurt)).hasStatus(status);
	}

	@Test
	void pagesOfFiftyAndTheRest() {
		int before = ok(items("current-state", null, "components", 0, 50, this.kurt)).path("page")
			.path("totalElements")
			.asInt();
		String prefix = "0page " + suffix() + " ";
		for (int i = 0; i < 51; i++) {
			entity(prefix + String.format("%02d", i), this.element);
		}
		int total = before + 51;
		JsonNode first = ok(items("current-state", null, "components", 0, 50, this.kurt));
		assertThat(first.path("items").size()).isEqualTo(50);
		assertThat(first.path("page").path("totalElements").asInt()).isEqualTo(total);
		assertThat(first.path("page").path("totalPages").asInt()).isEqualTo((total + 49) / 50);
		assertThat(first.path("page").path("page").asInt()).isZero();
		assertThat(first.path("page").path("pageSize").asInt()).isEqualTo(50);
		JsonNode last = ok(items("current-state", null, "components", (total - 1) / 50, 50, this.kurt));
		assertThat(last.path("items").size()).isEqualTo(total - 50 * ((total - 1) / 50));
		JsonNode beyond = ok(items("current-state", null, "components", (total + 49) / 50, 50, this.kurt));
		assertThat(beyond.path("items").size()).isZero();
	}

	// Hierarchy (us-135-component-hierarchy)

	@Test
	void theRootLevelHoldsElementsWithoutAParentOrWithADeletedParent() {
		String suffix = suffix();
		int system = entity("SAP S/4HANA " + suffix, this.element);
		int module = entity("Finance module " + suffix, this.element);
		int orphan = entity("Orphan " + suffix, this.element);
		int ofDeleted = entity("Payroll " + suffix, this.element);
		int deleted = entity("Deleted parent " + suffix, this.element);
		parent(module, system);
		parent(ofDeleted, deleted);
		deleteEntity(deleted);
		int connection = entity("Flow " + suffix, this.connection);

		List<String> root = names(ok(items("current-state", null, "components", 0, 200, this.kurt)).path("items"));

		assertThat(root).contains("SAP S/4HANA " + suffix, "Orphan " + suffix, "Payroll " + suffix)
			.doesNotContain("Finance module " + suffix, "Deleted parent " + suffix, "Flow " + suffix);
		assertThat(orphan).isPositive();
		assertThat(connection).isPositive();
	}

	@Test
	void parentEntityIdListsTheChildrenByName() {
		String suffix = suffix();
		int system = entity("SAP S/4HANA " + suffix, this.element);
		int zulu = entity("zulu " + suffix, this.element);
		int alpha = entity("Alpha " + suffix, this.element);
		int gone = entity("Gone " + suffix, this.element);
		parent(zulu, system);
		parent(alpha, system);
		parent(gone, system);
		deleteEntity(gone);

		JsonNode result = ok(children(system, 0, 50, this.kurt));

		assertThat(names(result.path("items"))).containsExactly("Alpha " + suffix, "zulu " + suffix);
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(2);
		assertThat(result.path("items").get(0).path("kind").asString()).isEqualTo("entity");
	}

	@Test
	void childrenComeInPages() {
		int system = entity("SAP S/4HANA " + suffix(), this.element);
		for (int i = 0; i < 51; i++) {
			parent(entity("Module " + String.format("%02d", i) + " " + suffix(), this.element), system);
		}

		JsonNode first = ok(children(system, 0, 50, this.kurt));
		JsonNode second = ok(children(system, 1, 50, this.kurt));

		assertThat(first.path("items").size()).isEqualTo(50);
		assertThat(second.path("items").size()).isOne();
		assertThat(first.path("page").path("totalElements").asInt()).isEqualTo(51);
		assertThat(first.path("page").path("totalPages").asInt()).isEqualTo(2);
	}

	@Test
	void childCountCountsTheChildrenThatAreNotDeleted() {
		String suffix = suffix();
		int system = entity("SAP S/4HANA " + suffix, this.element);
		int leaf = entity("Leaf " + suffix, this.element);
		int one = entity("One " + suffix, this.element);
		int two = entity("Two " + suffix, this.element);
		int gone = entity("Gone " + suffix, this.element);
		parent(one, system);
		parent(two, system);
		parent(gone, system);
		deleteEntity(gone);

		JsonNode root = ok(items("current-state", null, "components", 0, 200, this.kurt)).path("items");

		assertThat(byName(root, "SAP S/4HANA " + suffix).path("childCount").asInt()).isEqualTo(2);
		assertThat(byName(root, "Leaf " + suffix).path("childCount").asInt()).isZero();
		assertThat(leaf).isPositive();
	}

	@Test
	void structuringAidAndVersionAreSet() {
		String suffix = suffix();
		int aid = entity("Landscape " + suffix, STRUCTURING_AID);
		this.jdbc.update("UPDATE entities SET is_structural_grouper = true, version = 3 WHERE id = ?", aid);
		int plain = entity("Plain " + suffix, this.element);

		JsonNode root = ok(items("current-state", null, "components", 0, 200, this.kurt)).path("items");

		JsonNode shownAid = byName(root, "Landscape " + suffix);
		assertThat(shownAid.path("structuringAid").asBoolean()).isTrue();
		assertThat(shownAid.path("version").asInt()).isEqualTo(3);
		assertThat(shownAid.path("metatypeAbbreviation").isNull()).isTrue();
		JsonNode shownPlain = byName(root, "Plain " + suffix);
		assertThat(shownPlain.path("structuringAid").asBoolean()).isFalse();
		assertThat(shownPlain.path("version").asInt()).isOne();
		assertThat(plain).isPositive();
	}

	@Test
	void catalogsAndDeltasHaveNoChildrenAndNoVersion() {
		String catalogName = "Catalog " + suffix();
		catalog(catalogName, "instance", this.kurtId);
		JsonNode catalog = byName(ok(items("current-state", null, "catalogs", 0, 200, this.kurt)).path("items"),
				catalogName);
		assertThat(catalog.path("childCount").asInt()).isZero();
		assertThat(catalog.path("structuringAid").asBoolean()).isFalse();
		assertThat(catalog.path("version").asInt()).isZero();
		UUID solution = solution("Deltas " + suffix(), "draft", null);
		newDelta(solution, "CRM Cloud", this.element);
		JsonNode delta = ok(items("solution", solution, "components", 0, 50, this.kurt)).path("items").get(0);
		assertThat(delta.path("childCount").asInt()).isZero();
		assertThat(delta.path("version").asInt()).isZero();
	}

	@Test
	void theCountOfTheComponentsStaysTheNumberOfAllElements() {
		int before = ok(get(BROWSER, this.kurt)).path("currentState").path("components").asInt();
		int system = entity("SAP S/4HANA " + suffix(), this.element);
		parent(entity("Finance module " + suffix(), this.element), system);

		JsonNode top = ok(get(BROWSER, this.kurt)).path("currentState");

		assertThat(top.path("components").asInt()).isEqualTo(before + 2);
	}

	@ParameterizedTest
	@CsvSource({ "components, solution", "connections, current-state", "catalogs, current-state" })
	void parentEntityIdOnlyWithTheComponentsOfTheCurrentState(String category, String scope) {
		int system = entity("SAP S/4HANA " + suffix(), this.element);
		UUID solution = solution("Parent " + suffix(), "draft", null);

		MvcTestResult result = get(ITEMS + "?scope=" + scope + "&category=" + category + "&parentEntityId=" + system
				+ (scope.equals("solution") ? "&solutionId=" + solution : ""), this.kurt);

		assertInvalid(result, "parentEntityId");
	}

	@ParameterizedTest
	@ValueSource(strings = { "unknown", "deleted", "connection" })
	void anUnknownOrDeletedParentIsNotFound(String kind) {
		String suffix = suffix();
		int deleted = entity("Deleted " + suffix, this.element);
		deleteEntity(deleted);
		int connection = entity("Flow " + suffix, this.connection);
		int parent = switch (kind) {
			case "deleted" -> deleted;
			case "connection" -> connection;
			default -> 987_654_321;
		};

		assertThat(children(parent, 0, 50, this.kurt)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1 })
	void parentEntityIdBelowOneGives400(int parent) {
		assertThat(children(parent, 0, 50, this.kurt)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void parentEntityIdOfOneIsAValidValue() {
		// the entity 1 may or may not exist in this database: the answer is a page or 404, never 400
		assertThat(children(1, 0, 50, this.kurt).getResponse().getStatus()).isIn(200, 404);
	}

	@Test
	void aReaderWithoutEntityReadGetsNoChildren() {
		int system = entity("SAP S/4HANA " + suffix(), this.element);

		assertThat(children(system, 0, 50, this.principals.personWithoutRole())).hasStatus(HttpStatus.FORBIDDEN);
	}

	// Authorization

	@Test
	void withoutEntityRead() {
		TestSession nobody = this.principals.personWithoutRole();
		assertThat(get(BROWSER, nobody)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(items("current-state", null, "components", 0, 50, nobody)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void withoutSession() {
		assertThat(this.mvc.get().uri(BROWSER).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get().uri(ITEMS + "?scope=current-state&category=components").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// helpers

	private MvcTestResult children(int parent, int page, int pageSize, TestSession session) {
		return get(ITEMS + "?scope=current-state&category=components&parentEntityId=" + parent + "&page=" + page
				+ "&pageSize=" + pageSize, session);
	}

	private void parent(int child, int parent) {
		this.jdbc.update("UPDATE entities SET parent_entity_id = ? WHERE id = ?", parent, child);
	}

	private UUID metatype(String name, boolean connection, String abbreviation) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection, abbreviation)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?, ?) RETURNING id""", UUID.class, name, connection,
				abbreviation);
	}

	private int entity(String name, UUID metatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class,
				metatype, name, this.kurtId);
	}

	private void deleteEntity(int id) {
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, id);
	}

	private UUID baselinePlateau() {
		this.jdbc.update("""
				INSERT INTO plateaus (id, name, status, created_by) VALUES (gen_random_uuid(), 'Browser Baseline', 'baseline', ?)
				ON CONFLICT DO NOTHING""", this.kurtId);
		return this.jdbc.queryForObject("SELECT id FROM plateaus WHERE status = 'baseline'", UUID.class);
	}

	private UUID plateau(String name, String status, UUID succeeds, String validFrom) {
		return this.jdbc.queryForObject("""
				INSERT INTO plateaus (id, name, status, succeeds, basis, valid_from, created_by)
				VALUES (gen_random_uuid(), ?, ?, ?, 'catalog', ?::date, ?) RETURNING id""", UUID.class, name, status,
				succeeds, validFrom, this.kurtId);
	}

	private UUID solution(String name, String status, UUID toPlateau) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, status, from_plateau_id, to_plateau_id, owner_id, created_by)
				VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?) RETURNING id""", UUID.class, name, status,
				(toPlateau != null) ? baselinePlateau() : null, toPlateau, this.kurtId, this.kurtId);
	}

	private void delta(UUID solution, String type, int entityId) {
		this.jdbc.update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				VALUES (gen_random_uuid(), ?, ?, ?, ?)""", solution, type, entityId, this.kurtId);
	}

	/** A delta new with its own name and metatype; returns its ID. */
	private String newDelta(UUID solution, String name, UUID metatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, metatype_id, name, created_by)
				VALUES (gen_random_uuid(), ?, 'new', ?, ?, ?) RETURNING id::text""", String.class, solution, metatype,
				name, this.kurtId);
	}

	private void catalog(String name, String scope, long personId) {
		this.jdbc.update("""
				INSERT INTO catalogs (name, primary_metatype_id, scope, created_by, created_at, last_modified_by,
				                      last_modified_at)
				VALUES (?, ?, ?, ?, now(), ?, now())""", name, this.element, scope, personId, personId);
	}

	private MvcTestResult items(String scope, UUID solutionId, String category, int page, int pageSize,
			TestSession session) {
		String query = "?scope=" + scope + "&category=" + category + "&page=" + page + "&pageSize=" + pageSize
				+ ((solutionId != null) ? "&solutionId=" + solutionId : "");
		return get(ITEMS + query, session);
	}

	private MvcTestResult get(String path, TestSession session) {
		return this.mvc.get()
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(texts(body(result).path("invalidFields"), null)).containsExactly(field);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static List<String> names(JsonNode array) {
		return texts(array, "name");
	}

	private static List<String> texts(JsonNode array, String field) {
		List<String> texts = new ArrayList<>();
		array.forEach((node) -> texts.add((field != null) ? node.path(field).asString() : node.asString()));
		return texts;
	}

	private static JsonNode byName(JsonNode array, String name) {
		for (JsonNode node : array) {
			if (name.equals(node.path("name").asString())) {
				return node;
			}
		}
		throw new AssertionError("no " + name + " in " + array);
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
		return JsonMapper.shared().readTree(content(result));
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-browser").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

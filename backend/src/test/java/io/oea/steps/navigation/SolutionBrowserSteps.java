package io.oea.steps.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.steps.portal.PortalSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/navigation/solution-browser.feature (us-132-solution-browser), on the
 * instance of {@link CatalogSteps}. The instance is shared with other features, so counts are
 * compared with the counts noted before, and plateaus, solutions and entities carry unique names.
 */
public class SolutionBrowserSteps {

	private static final String BROWSER = "/api/v1/views/browser";

	private static final String ITEMS = "/api/v1/views/browser/items";

	private static final Map<String, String> SOLUTIONS = new HashMap<>();

	private static final Map<String, String> PLATEAUS = new HashMap<>();

	private static final Map<String, Integer> ENTITIES = new HashMap<>();


	private HttpResponse<String> response;

	private JsonNode counts;

	private int added;

	/** The size of the root level of the components before the entities were added. */
	private int rootBefore;

	private JsonNode rootItems;

	// Given

	@Given("the browser has a baseline plateau")
	public void baseline() throws Exception {
		instance().oidc()
			.update("INSERT INTO plateaus (id, name, status, created_by) SELECT gen_random_uuid(), 'Browser Baseline', 'baseline', min(id) FROM persons ON CONFLICT DO NOTHING");
		PLATEAUS.put("baseline", instance().oidc().single("SELECT id::text FROM plateaus WHERE status = 'baseline'"));
	}

	@Given("the transition plateau {string} is valid from {string}")
	public void transition(String name, String validFrom) throws Exception {
		plateau(name, "transition", validFrom);
	}

	@Given("the target plateau {string} is valid from {string}")
	public void target(String name, String validFrom) throws Exception {
		plateau(name, "target", validFrom);
	}

	@Given("the browser solution {string} targets {string}")
	public void solutionTargets(String name, String plateau) throws Exception {
		SOLUTIONS.put(name, instance().oidc().single("""
				INSERT INTO solutions (id, name, owner_id, created_by, from_plateau_id, to_plateau_id)
				SELECT gen_random_uuid(), '%s', min(id), min(id), '%s', '%s' FROM persons RETURNING id::text"""
			.formatted(name, PLATEAUS.get("baseline"), PLATEAUS.get(plateau))));
	}

	@Given("the browser project solution {string} exists")
	public void projectSolution(String name) throws Exception {
		projectSolutionWithStatus(name, "draft");
	}

	@Given("the browser project solution {string} has the status {string}")
	public void projectSolutionWithStatus(String name, String status) throws Exception {
		SOLUTIONS.put(name, instance().oidc().single("""
				INSERT INTO solutions (id, name, status, owner_id, created_by)
				SELECT gen_random_uuid(), '%s', '%s', min(id), min(id) FROM persons RETURNING id::text"""
			.formatted(name, status)));
	}

	@Given("{string} has the delta {string} {string}")
	public void newDelta(String solution, String type, String name) throws Exception {
		instance().oidc().update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, metatype_id, name, created_by)
				SELECT gen_random_uuid(), '%s', '%s', '%s', '%s', min(id) FROM persons"""
			.formatted(SOLUTIONS.get(solution), type, metatype("Browser Element", false), name));
	}

	@Given("{string} has the delta {string} for the entity {string}")
	public void entityDelta(String solution, String type, String entity) throws Exception {
		int id = entity(entity, "Browser Element", false, false);
		instance().oidc().update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				SELECT gen_random_uuid(), '%s', '%s', %d, min(id) FROM persons""".formatted(SOLUTIONS.get(solution),
				type, id));
	}

	@Given("the browser notes the counts of the current state")
	public void noteCounts() throws Exception {
		this.counts = body(instance().oidc().get(BROWSER, instance().viewer().token())).path("currentState");
	}

	@Given("{int} element entities, {int} connections and {int} deleted element entity were added")
	public void addEntities(int elements, int connections, int deleted) throws Exception {
		String prefix = "Browser Count " + UUID.randomUUID() + " ";
		for (int i = 0; i < elements; i++) {
			entity(prefix + "e" + i, "Browser Element", false, false);
		}
		for (int i = 0; i < connections; i++) {
			entity(prefix + "c" + i, "Browser Connection", true, false);
		}
		for (int i = 0; i < deleted; i++) {
			entity(prefix + "d" + i, "Browser Element", false, true);
		}
	}

	@Given("{int} element entities were added")
	public void addElements(int count) throws Exception {
		this.added = count;
		noteCounts();
		// the root level holds only the components without a parent, which other features may nest
		this.rootBefore = ok(items("current-state", null, "components", 0, 1, instance().viewer())).path("page")
			.path("totalElements")
			.asInt();
		String prefix = "Browser Page " + UUID.randomUUID() + " ";
		for (int i = 0; i < count; i++) {
			entity(prefix + i, "Browser Element", false, false);
		}
	}

	@Given("the enterprise architect has the personal catalog {string} and the instance catalog {string}")
	public void catalogs(String personal, String shared) throws Exception {
		String metatype = metatype("Browser Element", false);
		for (String[] catalog : new String[][] { { personal, "personal" }, { shared, "instance" } }) {
			ObjectNode body = JsonMapper.shared()
				.createObjectNode()
				.put("name", catalog[0])
				.put("primaryMetatypeId", metatype)
				.put("scope", catalog[1]);
			HttpResponse<String> created = instance().post("/api/v1/catalogs", body.toString(),
					instance().architect(), Map.of());
			assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		}
	}

	@Given("the browser component {string} with the child {string} and the structuring aid {string} holding {string}")
	public void nested(String parent, String child, String aid, String held) throws Exception {
		int parentId = entity(parent, "Browser Element", false, false);
		int childId = entity(child, "Browser Element", false, false);
		int aidId = entity(aid, "Browser Element", false, false);
		int heldId = entity(held, "Browser Element", false, false);
		instance().oidc().update("UPDATE entities SET parent_entity_id = " + parentId + " WHERE id = " + childId);
		instance().oidc().update("UPDATE entities SET parent_entity_id = " + aidId + " WHERE id = " + heldId);
		instance().oidc()
			.update("UPDATE entities SET is_structural_grouper = true, metatype_id = 'c3f523b8-f07f-5475-9ab0-6f2ea39b537e' WHERE id = "
					+ aidId);
	}

	@Given("the browser component {string} with the child {string} whose parent was deleted")
	public void orphan(String parent, String child) throws Exception {
		int parentId = entity(parent, "Browser Element", false, true);
		int childId = entity(child, "Browser Element", false, false);
		instance().oidc().update("UPDATE entities SET parent_entity_id = " + parentId + " WHERE id = " + childId);
	}

	@Given("the browser component {string} without a parent")
	public void plain(String name) throws Exception {
		entity(name, "Browser Element", false, false);
	}

	// When

	@When("the viewer reads the root level of the components")
	public void readRoot() throws Exception {
		// the instance is shared, so the root level may have more than one page
		ArrayNode all = JsonMapper.shared().createArrayNode();
		for (int page = 0;; page++) {
			JsonNode read = ok(items("current-state", null, "components", page, 200, instance().viewer()));
			all.addAll((ArrayNode) read.path("items"));
			if (page + 1 >= read.path("page").path("totalPages").asInt()) {
				break;
			}
		}
		this.rootItems = all;
	}

	@When("the viewer reads the category {string} of the current state with a parent")
	public void readWithParent(String category) throws Exception {
		this.response = instance().oidc()
			.get(ITEMS + "?scope=current-state&category=" + category + "&parentEntityId=" + any(),
					instance().viewer().token());
	}

	@When("the viewer reads the children of the entity {int}")
	public void readChildren(int parent) throws Exception {
		this.response = instance().oidc()
			.get(ITEMS + "?scope=current-state&category=components&parentEntityId=" + parent,
					instance().viewer().token());
	}

	@When("the viewer tries to place {string} under itself")
	public void viewerPlaces(String name) throws Exception {
		int id = ENTITIES.get(name);
		this.response = instance().send("PUT", "/api/v1/entities/" + id + "/parent", "{\"parentEntityId\": " + id + "}",
				instance().viewer(), Map.of("If-Match", "\"1\""));
	}

	@When("the viewer reads the browser")
	public void read() throws Exception {
		this.response = instance().oidc().get(BROWSER, instance().viewer().token());
	}

	@When("a reader without entity:read reads the browser")
	public void readWithoutRight() throws Exception {
		this.response = instance().oidc().get(BROWSER, withoutRole().token());
	}

	@When("the viewer reads the components of {string}")
	public void readComponents(String solution) throws Exception {
		this.response = items("solution", SOLUTIONS.get(solution), "components", 0, 50, instance().viewer());
	}

	@When("the viewer reads the category {string} of {string}")
	public void readCategory(String category, String solution) throws Exception {
		this.response = items("solution", SOLUTIONS.get(solution), category, 0, 50, instance().viewer());
	}

	@When("the viewer reads the components of an unknown solution")
	public void readUnknown() throws Exception {
		this.response = items("solution", UUID.randomUUID().toString(), "components", 0, 50, instance().viewer());
	}

	@When("the viewer reads the components of the current state with the page size {int}")
	public void readPageSize(int size) throws Exception {
		this.response = items("current-state", null, "components", 0, size, instance().viewer());
	}

	@When("the viewer reads the components of the current state in pages of {int}")
	public void readPages(int size) throws Exception {
		this.response = items("current-state", null, "components", 0, size, instance().viewer());
		JsonNode first = ok(this.response);
		assertThat(first.path("items").size()).isEqualTo(size);
		int total = first.path("page").path("totalElements").asInt();
		assertThat(total - this.rootBefore).isEqualTo(this.added);
		int lastPage = (total - 1) / size;
		this.response = items("current-state", null, "components", lastPage, size, instance().viewer());
		assertThat(ok(this.response).path("items").size()).isEqualTo(total - size * lastPage);
	}

	// Then

	@Then("the plateaus of the browser include {string} before {string}")
	public void plateausOrder(String first, String second) throws Exception {
		List<String> names = names(ok(this.response).path("plateaus"));
		assertThat(names).containsSubsequence(first, second);
	}

	@Then("the plateaus of the browser do not include the baseline")
	public void noBaseline() throws Exception {
		assertThat(names(ok(this.response).path("plateaus"))).doesNotContain("Browser Baseline");
	}

	@Then("the plateau {string} has {int} solutions")
	public void plateauSolutions(String name, int count) throws Exception {
		assertThat(find(ok(this.response).path("plateaus"), name).path("solutions").size()).isEqualTo(count);
	}

	@Then("{string} is listed under the plateau {string}")
	public void underPlateau(String solution, String plateau) throws Exception {
		assertThat(names(find(ok(this.response).path("plateaus"), plateau).path("solutions"))).contains(solution);
	}

	@Then("{string} is listed under the projects")
	public void underProjects(String solution) throws Exception {
		assertThat(names(ok(this.response).path("projects"))).contains(solution);
	}

	@Then("{string} is not listed under the projects")
	public void notUnderProjects(String solution) throws Exception {
		assertThat(names(ok(this.response).path("projects"))).doesNotContain(solution);
	}

	@Then("the current state counts {int} more components, {int} more connections and {int} more catalogs")
	public void countsGrew(int components, int connections, int catalogs) throws Exception {
		JsonNode now = ok(this.response).path("currentState");
		assertThat(now.path("components").asInt() - this.counts.path("components").asInt()).isEqualTo(components);
		assertThat(now.path("connections").asInt() - this.counts.path("connections").asInt()).isEqualTo(connections);
		assertThat(now.path("catalogs").asInt() - this.counts.path("catalogs").asInt()).isEqualTo(catalogs);
	}

	@Then("{string} shows {int} components and {string} shows {int}")
	public void solutionCounts(String first, int firstCount, String second, int secondCount) throws Exception {
		JsonNode projects = ok(this.response).path("projects");
		assertThat(find(projects, first).path("components").asInt()).isEqualTo(firstCount);
		assertThat(find(projects, second).path("components").asInt()).isEqualTo(secondCount);
	}

	@Then("the components are listed as {string} new, {string} retiring, {string} modified")
	public void deltaTypes(String first, String second, String third) throws Exception {
		JsonNode items = ok(this.response).path("items");
		assertThat(names(items)).containsExactly(first, second, third);
		List<String> types = new ArrayList<>();
		items.forEach((item) -> types.add(item.path("deltaType").asString()));
		assertThat(types).containsExactly("new", "retiring", "modified");
	}

	@Then("the viewer does not see {string} among the catalogs but the architect does")
	public void personalCatalog(String name) throws Exception {
		assertThat(names(ok(items("current-state", null, "catalogs", 0, 200, instance().viewer())).path("items")))
			.doesNotContain(name);
		assertThat(names(ok(items("current-state", null, "catalogs", 0, 200, instance().architect())).path("items")))
			.contains(name);
	}

	@Then("the last page holds the rest of the components and the first page holds {int}")
	public void pagesChecked(int size) {
		assertThat(this.response.statusCode()).isEqualTo(200);
	}

	@Then("the root level lists {string} with {int} child and {string} as a structuring aid but not {string}")
	public void rootLevel(String parent, int children, String aid, String nested) throws Exception {
		JsonNode items = this.rootItems;
		assertThat(find(items, parent).path("childCount").asInt()).isEqualTo(children);
		assertThat(find(items, aid).path("structuringAid").asBoolean()).isTrue();
		assertThat(names(items)).doesNotContain(nested);
	}

	@Then("the root level lists {string}")
	public void rootLists(String name) throws Exception {
		assertThat(names(this.rootItems)).contains(name);
	}

	@Then("the children of {string} are {string}")
	public void children(String parent, String child) throws Exception {
		HttpResponse<String> read = instance().oidc()
			.get(ITEMS + "?scope=current-state&category=components&parentEntityId=" + ENTITIES.get(parent),
					instance().viewer().token());
		assertThat(names(ok(read).path("items"))).containsExactly(child);
	}

	@Then("the browser answer is {int}")
	public void answer(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the browser answer is 422 naming {string}")
	public void invalid(String field) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		List<String> fields = new ArrayList<>();
		JsonMapper.shared()
			.readTree(this.response.body())
			.path("invalidFields")
			.forEach((value) -> fields.add(value.asString()));
		assertThat(fields).containsExactly(field);
	}

	// helpers

	private static void plateau(String name, String status, String validFrom) throws Exception {
		PLATEAUS.put(name, instance().oidc().single("""
				INSERT INTO plateaus (id, name, status, succeeds, basis, valid_from, created_by)
				SELECT gen_random_uuid(), '%s', '%s', '%s', 'catalog', DATE '%s', min(id) FROM persons
				RETURNING id::text""".formatted(name, status, PLATEAUS.get("baseline"), validFrom)));
	}

	private static int any() throws Exception {
		return Integer.parseInt(instance().oidc().single("SELECT min(id)::text FROM entities"));
	}

	private static String metatype(String name, boolean connection) throws Exception {
		instance().oidc()
			.update("INSERT INTO metatypes (config_id, name, is_connection, abbreviation) SELECT '00000000-0000-0000-0000-000000000001', '"
					+ name + "', " + connection + ", 'BRW' WHERE NOT EXISTS (SELECT 1 FROM metatypes WHERE name = '"
					+ name + "')");
		return instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name + "'");
	}

	private static int entity(String name, String metatype, boolean connection, boolean deleted) throws Exception {
		String id = instance().oidc().single("""
				INSERT INTO entities (metatype_id, name, created_by, deleted_at, deleted_by)
				SELECT '%s', '%s', min(id), %s, %s FROM persons RETURNING id::text"""
			.formatted(metatype(metatype, connection), name, deleted ? "now()" : "NULL",
					deleted ? "min(id)" : "NULL"));
		ENTITIES.put(name, Integer.parseInt(id));
		return Integer.parseInt(id);
	}

	private static HttpResponse<String> items(String scope, String solutionId, String category, int page, int size,
			Session session) throws Exception {
		return instance().oidc()
			.get(ITEMS + "?scope=" + scope + "&category=" + category + "&page=" + page + "&pageSize=" + size
					+ ((solutionId != null) ? "&solutionId=" + solutionId : ""), session.token());
	}

	// a person without any role lacks entity:read; the login is shared with the portal features,
	// because logging the same IdP user in twice on the shared instance creates the person twice
	private static Session withoutRole() throws Exception {
		return PortalSteps.personWithoutRole();
	}

	private static JsonNode find(JsonNode array, String name) {
		for (JsonNode node : array) {
			if (name.equals(node.path("name").asString())) {
				return node;
			}
		}
		throw new AssertionError("no " + name + " in " + array);
	}

	private static List<String> names(JsonNode array) {
		List<String> names = new ArrayList<>();
		array.forEach((node) -> names.add(node.path("name").asString()));
		return names;
	}

	private static JsonNode ok(HttpResponse<String> response) {
		return body(response);
	}

	private static JsonNode body(HttpResponse<String> response) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(response.body());
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

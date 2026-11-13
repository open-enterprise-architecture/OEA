package io.oea.steps.solution;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.metamodel.MetatypeEditingSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/solution/solution-baseline.feature (us-039-solution-baseline D1–D4), on the
 * shared instance of the metamodel features. The baseline spans the whole catalog, which other
 * features fill as well, so every scenario works on its own entities: their names end with a
 * scenario suffix, and "loads the baseline" searches for that suffix; the metatype filter uses
 * metatypes created for the scenario.
 */
public class BaselineSteps {

	private final String suffix = "bl" + UUID.randomUUID().toString().substring(0, 8);

	private String solution;

	private UUID applications;

	private UUID components;

	private UUID dataFlows;

	private HttpResponse<String> response;

	private final List<String> pagesByNumber = new ArrayList<>();

	private final List<String> pagesByCursor = new ArrayList<>();

	private boolean lastPageWithoutCursor;

	private final java.util.Map<String, String> entryProperties = new java.util.HashMap<>();

	@Given("the architect created a solution for the baseline")
	public void solution() throws Exception {
		HttpResponse<String> created = instance().send("POST", "/api/v1/solutions",
				JsonMapper.shared().createObjectNode().put("name", "Baseline " + this.suffix).toString(),
				instance().architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.solution = JsonMapper.shared().readTree(created.body()).path("id").asString();
		this.applications = metatype("Application", false);
		this.components = metatype("Component", false);
		this.dataFlows = metatype("Data Flow", true);
	}

	@Given("the landscape holds the applications {string} and {string} and the data flow {string}")
	public void applicationsAndDataFlow(String first, String second, String dataFlow) throws Exception {
		applications(first, second);
		entity(this.dataFlows, dataFlow);
	}

	@Given("the landscape holds the applications {string} and {string}")
	public void applications(String first, String second) throws Exception {
		entity(this.applications, first);
		entity(this.applications, second);
	}

	@Given("{string} is deleted")
	public void deleted(String name) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET deleted_at = now(), deleted_by = created_by WHERE name = '" + named(name) + "'");
	}

	@Given("{string} was renamed to {string} in version {int}")
	public void renamed(String name, String newName, int version) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET name = '" + named(newName) + "', version = " + version + " WHERE name = '"
					+ named(name) + "'");
	}

	@Given("the landscape holds {int} applications and {int} components")
	public void applicationsAndComponents(int applications, int components) throws Exception {
		many(this.applications, "App", applications, applications);
		many(this.components, "Component", components, components);
	}

	@Given("the landscape holds {int} applications, some with equal names")
	public void applicationsWithEqualNames(int count) throws Exception {
		many(this.applications, "App", count, Math.max(1, count / 3));
	}

	@Given("the solution holds a delta {string} for {string}")
	public void delta(String type, String name) throws Exception {
		delta(this.solution, type, name);
	}

	@Given("another solution holds a delta {string} for {string}")
	public void otherDelta(String type, String name) throws Exception {
		HttpResponse<String> created = instance().send("POST", "/api/v1/solutions",
				JsonMapper.shared().createObjectNode().put("name", "Other " + this.suffix).toString(),
				instance().architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		delta(JsonMapper.shared().readTree(created.body()).path("id").asString(), type, name);
	}

	@Then("{string} carries the delta type {string}")
	public void carriesDeltaType(String name, String type) throws Exception {
		assertThat(baselineEntry(name).path("deltaType").asString()).isEqualTo(type);
	}

	@Then("{string} carries no delta type")
	public void carriesNoDeltaType(String name) throws Exception {
		assertThat(baselineEntry(name).path("deltaType").isNull()).isTrue();
	}

	private void delta(String solutionId, String type, String name) throws Exception {
		instance().oidc().update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				SELECT gen_random_uuid(), '%s', '%s', id, created_by FROM entities WHERE name = '%s'"""
			.formatted(solutionId, type, named(name)));
	}

	private JsonNode baselineEntry(String name) throws Exception {
		for (JsonNode entry : ok(this.response).path("items")) {
			if (entry.path("name").asString().equals(named(name))) {
				return entry;
			}
		}
		throw new AssertionError("no entry " + name);
	}

	@When("the architect loads the baseline")
	public void load() throws Exception {
		this.response = get("q=" + this.suffix);
	}

	@When("the architect loads the baseline of an unknown solution")
	public void unknownSolution() throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/solutions/" + UUID.randomUUID() + "/landscape", instance().architect().token());
	}

	@When("a portal reader loads the baseline")
	public void portalReader() throws Exception {
		this.response = instance().oidc().get(path(""), SolutionSteps.portalReaderSession().token());
	}

	@When("the architect loads the baseline filtered by the applications' metatype")
	public void filtered() throws Exception {
		this.response = get("metatypeId=" + this.applications);
	}

	@When("the architect searches the applications for {string}")
	public void search(String term) throws Exception {
		this.response = get("metatypeId=" + this.applications + "&q=" + encode(term));
	}

	@When("the architect searches the baseline with a term of {int} characters")
	public void searchWithLength(int length) throws Exception {
		this.response = get("q=" + this.suffix + "x".repeat(length - this.suffix.length()));
	}

	@When("the architect loads the baseline with page size {int}")
	public void pageSize(int size) throws Exception {
		this.response = get("q=" + this.suffix + "&pageSize=" + size);
	}

	@When("the architect loads every page of size {int} with the cursor of the previous one")
	public void everyPage(int size) throws Exception {
		String cursor = null;
		for (int page = 0;; page++) {
			JsonNode byNumber = ok(get("q=" + this.suffix + "&pageSize=" + size + "&page=" + page));
			byNumber.path("items").forEach((entry) -> this.pagesByNumber.add(entry.path("id").asString()));
			JsonNode byCursor = ok(get("q=" + this.suffix + "&pageSize=" + size + "&page=" + page
					+ ((cursor != null) ? "&cursor=" + cursor : "")));
			assertThat(byCursor.path("page").path("totalElements").asInt())
				.isEqualTo(byNumber.path("page").path("totalElements").asInt());
			byCursor.path("items").forEach((entry) -> this.pagesByCursor.add(entry.path("id").asString()));
			if (byCursor.path("nextCursor").isNull()) {
				this.lastPageWithoutCursor = page == byCursor.path("page").path("totalPages").asInt() - 1;
				this.response = null;
				return;
			}
			cursor = byCursor.path("nextCursor").asString();
		}
	}

	@When("the architect sends the cursor of the unfiltered baseline with a metatype filter")
	public void cursorWithFilter() throws Exception {
		this.response = get("q=" + this.suffix + "&pageSize=2&page=1&cursor=" + firstCursor() + "&metatypeId="
				+ this.applications);
	}

	@When("the architect sends a changed cursor")
	public void changedCursor() throws Exception {
		String cursor = firstCursor();
		String changed = ((cursor.charAt(0) == 'A') ? "B" : "A") + cursor.substring(1);
		this.response = get("q=" + this.suffix + "&pageSize=2&page=1&cursor=" + changed);
	}

	@Then("the baseline lists exactly {string}")
	public void listsExactly(String names) throws Exception {
		assertThat(names(ok(this.response))).containsExactlyElementsOf(Arrays.asList(names.split(", ")));
	}

	@Then("the baseline lists {string} in version {int} and not {string}")
	public void listsInVersion(String name, int version, String old) throws Exception {
		JsonNode page = ok(this.response);
		assertThat(names(page)).contains(name).doesNotContain(old);
		for (JsonNode entry : page.path("items")) {
			if (entry.path("name").asString().equals(named(name))) {
				assertThat(entry.path("version").asInt()).isEqualTo(version);
			}
		}
	}

	@Then("the baseline lists {int} entities, all applications")
	public void listsApplications(int count) throws Exception {
		JsonNode page = ok(this.response);
		assertThat(page.path("items").size()).isEqualTo(count);
		assertThat(page.path("page").path("totalElements").asInt()).isEqualTo(count);
		page.path("items")
			.forEach((entry) -> assertThat(entry.path("metatypeId").asString()).isEqualTo(this.applications.toString()));
	}

	@Then("the baseline answers {int}")
	public void answers(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the baseline answers 400 naming {string}")
	public void answersNaming(String field) throws Exception {
		answers(400);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("invalidFields").toString())
			.isEqualTo("[\"" + field + "\"]");
	}

	@Then("the pages hold every entity exactly once in the order of the pages by number, with the total {int}")
	public void everyEntityOnce(int total) {
		assertThat(this.pagesByCursor).hasSize(total).doesNotHaveDuplicates().isEqualTo(this.pagesByNumber);
	}

	@Then("the last page has no next cursor")
	public void lastPage() {
		assertThat(this.lastPageWithoutCursor).isTrue();
	}

	// us-041-modified-delta – Requirement: Read one entry of the baseline

	@Given("{string} has the value {string} of the property {string}")
	public void value(String name, String value, String property) throws Exception {
		String id = instance().oidc().single("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES ('%s %s', 'p_%s', 'string') RETURNING id""".formatted(property, this.suffix,
				UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
		this.entryProperties.put(property, id);
		instance().oidc().update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) SELECT id, '"
				+ id + "', '" + value + "' FROM entities WHERE name = '" + named(name) + "'");
	}

	@Given("the property {string} is readable only by the role {string}")
	public void restricted(String property, String role) throws Exception {
		String id = this.entryProperties.get(property);
		instance().oidc().update("UPDATE property_definitions SET visibility_mode = 'role-restricted' WHERE id = '" + id + "'");
		instance().oidc().update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES ('" + id
				+ "', '" + role + "', 'read')");
	}

	@When("the architect reads the entry {string} of the baseline")
	public void readEntry(String name) throws Exception {
		this.response = instance().oidc().get(entryPath(name), instance().architect().token());
	}

	@When("the viewer reads the entry {string} of the baseline")
	public void viewerReadsEntry(String name) throws Exception {
		this.response = instance().oidc().get(entryPath(name), instance().viewer().token());
	}

	@When("a portal reader reads the entry {string} of the baseline")
	public void portalReaderReadsEntry(String name) throws Exception {
		this.response = instance().oidc().get(entryPath(name), SolutionSteps.portalReaderSession().token());
	}

	@When("the architect reads an unknown entity ID as an entry of the baseline")
	public void readUnknownEntry() throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/solutions/" + this.solution + "/landscape/" + Integer.MAX_VALUE, instance().architect().token());
	}

	@Then("the entry holds the name {string}, its metatype, version 1 and {string} {string}")
	public void entryHolds(String name, String property, String value) throws Exception {
		JsonNode entry = ok(this.response);
		assertThat(entry.path("name").asString()).isEqualTo(named(name));
		assertThat(entry.path("metatypeId").asString()).isEqualTo(this.applications.toString());
		assertThat(entry.path("version").asInt()).isEqualTo(1);
		assertThat(entryValue(entry, property).path("valueText").asString()).isEqualTo(value);
	}

	@Then("{string} of the entry is redacted")
	public void entryRedacted(String property) throws Exception {
		JsonNode value = entryValue(ok(this.response), property);
		assertThat(value.path("redacted").asBoolean()).isTrue();
		assertThat(value.path("valueText").isNull()).isTrue();
	}

	private JsonNode entryValue(JsonNode entry, String property) {
		for (JsonNode value : entry.path("properties")) {
			if (value.path("propertyDefId").asString().equals(this.entryProperties.get(property))) {
				return value;
			}
		}
		throw new AssertionError("no value " + property + " in " + entry);
	}

	private String entryPath(String name) throws Exception {
		String id = instance().oidc().single("SELECT id::text FROM entities WHERE name = '" + named(name) + "'");
		return "/api/v1/solutions/" + this.solution + "/landscape/" + id;
	}

	private String firstCursor() throws Exception {
		JsonNode first = ok(get("q=" + this.suffix + "&pageSize=2"));
		assertThat(first.path("nextCursor").isNull()).isFalse();
		return first.path("nextCursor").asString();
	}

	private UUID metatype(String name, boolean connection) throws Exception {
		return UUID.fromString(instance().oidc().single("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', '%s %s', %s) RETURNING id"""
			.formatted(name, this.suffix, connection)));
	}

	private void entity(UUID type, String name) throws Exception {
		instance().oidc().update("""
				INSERT INTO entities (metatype_id, name, created_by)
				SELECT '%s', '%s', min(id) FROM persons""".formatted(type, named(name)));
	}

	private void many(UUID type, String prefix, int count, int distinctNames) throws Exception {
		instance().oidc().update("""
				INSERT INTO entities (metatype_id, name, created_by)
				SELECT '%s', '%s ' || (g %% %d) || ' %s', (SELECT min(id) FROM persons)
				FROM generate_series(1, %d) g""".formatted(type, prefix, distinctNames, this.suffix, count));
	}

	private String named(String name) {
		return (name + " " + this.suffix).replace("'", "''");
	}

	private List<String> names(JsonNode page) {
		List<String> names = new ArrayList<>();
		page.path("items").forEach((entry) -> names.add(entry.path("name").asString().replace(" " + this.suffix, "")));
		return names;
	}

	private HttpResponse<String> get(String query) throws Exception {
		return instance().oidc().get(path(query), instance().architect().token());
	}

	private String path(String query) {
		return "/api/v1/solutions/" + this.solution + "/landscape" + (query.isEmpty() ? "" : "?" + query);
	}

	private static JsonNode ok(HttpResponse<String> response) throws Exception {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(response.body());
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

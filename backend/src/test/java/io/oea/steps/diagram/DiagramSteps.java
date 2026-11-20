package io.oea.steps.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/diagram/diagrams.feature and the diagram scenarios of the viewpoint, browser
 * and navigation tree features (us-074-diagrams), on the instance of {@link CatalogSteps}. The
 * architect (`enterprise-architect`) holds entity:write, entity:restore and viewpoint:manage, the
 * viewer only entity:read. Diagrams, viewpoints and folders are addressed by their names, which are
 * unique in the features.
 */
public class DiagramSteps {

	private static final String DIAGRAMS = "/api/v1/diagrams";

	private static final String VIEWPOINTS = "/api/v1/admin/viewpoints";

	private static final String BROWSER = "/api/v1/views/browser";

	private static final String NODES = "/api/v1/tree-nodes";

	private HttpResponse<String> response;

	// Given

	@Given("the diagram viewpoint {string} with the notation {string} exists")
	public void viewpoint(String name, String notation) throws Exception {
		instance().oidc()
			.update("INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), '"
					+ quoted(name) + "', '" + notation
					+ "', (SELECT min(id) FROM persons)) ON CONFLICT (lower(name)) DO NOTHING");
	}

	@Given("the diagram {string} exists in the viewpoint {string}")
	public void diagram(String name, String viewpoint) throws Exception {
		this.response = createAs(instance().architect(), name, null, viewpointId(viewpoint));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@Given("the diagram {string} is deleted")
	public void deleted(String name) throws Exception {
		delete(name, version(name));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(204);
	}

	@Given("{int} diagrams named {string} followed by a number exist in the viewpoint {string}")
	public void many(int count, String prefix, String viewpoint) throws Exception {
		for (int i = 0; i < count; i++) {
			instance().oidc()
				.update("INSERT INTO diagrams (name, viewpoint_id, created_by, updated_by) VALUES ('" + quoted(prefix)
						+ " " + String.format("%02d", i) + "', '" + viewpointId(viewpoint)
						+ "', (SELECT min(id) FROM persons), (SELECT min(id) FROM persons))");
		}
	}

	@Given("the diagram {string} is placed in the folder {string}")
	public void placedGiven(String diagram, String folder) throws Exception {
		place(diagram, folder);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@Given("the diagram folder {string} exists")
	public void folder(String name) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("parentId", root());
		HttpResponse<String> created = instance().post(NODES, body.toString(), instance().architect(), Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
	}

	// When

	@When("the enterprise architect creates the diagram {string} with the viewpoint {string}")
	public void create(String name, String viewpoint) throws Exception {
		this.response = createAs(instance().architect(), name, null, viewpointId(viewpoint));
	}

	@When("the viewer creates the diagram {string} with the viewpoint {string}")
	public void viewerCreates(String name, String viewpoint) throws Exception {
		this.response = createAs(instance().viewer(), name, null, viewpointId(viewpoint));
	}

	@When("the enterprise architect creates a diagram with a name of {int} characters")
	public void createWithNameLength(int length) throws Exception {
		this.response = createAs(instance().architect(), "n".repeat(length), null, anyViewpoint());
	}

	@When("the enterprise architect creates a diagram with a description of {int} characters")
	public void createWithDescriptionLength(int length) throws Exception {
		this.response = createAs(instance().architect(), "Described " + length + " " + System.nanoTime(),
				"d".repeat(length), anyViewpoint());
	}

	@When("the enterprise architect creates a diagram with only blanks as its name")
	public void createBlank() throws Exception {
		this.response = createAs(instance().architect(), "   ", null, anyViewpoint());
	}

	@When("the enterprise architect creates a diagram with an unknown viewpoint")
	public void createUnknownViewpoint() throws Exception {
		this.response = createAs(instance().architect(), "Unknown viewpoint " + System.nanoTime(), null,
				"00000000-0000-4000-8000-000000000abc");
	}

	@When("the enterprise architect renames {string} to {string} with its version")
	public void rename(String name, String newName) throws Exception {
		update(name, newName, "\"" + version(name) + "\"");
	}

	@When("the enterprise architect renames {string} to {string} with the version {int}")
	public void renameWithVersion(String name, String newName, int version) throws Exception {
		update(name, newName, "\"" + version + "\"");
	}

	@When("the enterprise architect renames {string} to {string} without a version")
	public void renameWithoutVersion(String name, String newName) throws Exception {
		update(name, newName, null);
	}

	@When("the viewer renames {string} to {string} with its version")
	public void viewerRenames(String name, String newName) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", newName);
		this.response = instance().send("PUT", DIAGRAMS + "/" + id(name), body.toString(), instance().viewer(),
				Map.of("If-Match", "\"" + version(name) + "\""));
	}

	@When("the enterprise architect changes {string} with another viewpoint")
	public void changeViewpoint(String name) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("viewpointId", anyOtherViewpoint(id(name)));
		this.response = instance().send("PUT", DIAGRAMS + "/" + id(name), body.toString(), instance().architect(),
				Map.of("If-Match", "\"" + version(name) + "\""));
	}

	@When("the enterprise architect deletes the diagram {string} with its version")
	public void deleteWithVersion(String name) throws Exception {
		delete(name, version(name));
	}

	@When("the viewer deletes the diagram {string} with its version")
	public void viewerDeletes(String name) throws Exception {
		this.response = instance().send("DELETE", DIAGRAMS + "/" + id(name), null, instance().viewer(),
				Map.of("If-Match", "\"" + version(name) + "\""));
	}

	@When("the enterprise architect restores the diagram {string} with its version")
	public void restore(String name) throws Exception {
		this.response = instance().send("POST", DIAGRAMS + "/" + restorableId(name) + "/restore", null,
				instance().architect(), Map.of("If-Match", "\"" + restorableVersion(name) + "\""));
	}

	@When("the enterprise architect restores the diagram {string} with the version {int}")
	public void restoreWithVersion(String name, int version) throws Exception {
		this.response = instance().send("POST", DIAGRAMS + "/" + restorableId(name) + "/restore", null,
				instance().architect(), Map.of("If-Match", "\"" + version + "\""));
	}

	@When("the viewer restores the diagram {string} with its version")
	public void viewerRestores(String name) throws Exception {
		this.response = instance().send("POST", DIAGRAMS + "/" + restorableId(name) + "/restore", null,
				instance().viewer(), Map.of("If-Match", "\"" + restorableVersion(name) + "\""));
	}

	@When("the viewer lists the deleted diagrams")
	public void viewerListsDeleted() throws Exception {
		this.response = get(DIAGRAMS + "/deleted", instance().viewer());
	}

	@When("the viewer lists the diagrams with the filter {string}")
	public void listFiltered(String filter) throws Exception {
		this.response = get(DIAGRAMS + "?pageSize=200&q=" + java.net.URLEncoder.encode(filter,
				java.nio.charset.StandardCharsets.UTF_8), instance().viewer());
	}

	@When("the viewer lists the diagrams with the page size {int}")
	public void listPageSize(int size) throws Exception {
		this.response = get(DIAGRAMS + "?pageSize=" + size, instance().viewer());
	}

	@When("the viewer lists the diagrams with a filter of {int} characters")
	public void listFilterLength(int length) throws Exception {
		this.response = get(DIAGRAMS + "?q=" + "f".repeat(length), instance().viewer());
	}

	@When("the viewer reads the diagram {string}")
	public void read(String name) throws Exception {
		this.response = get(DIAGRAMS + "/" + id(name), instance().viewer());
	}

	@When("the viewer reads an unknown diagram")
	public void readUnknown() throws Exception {
		this.response = get(DIAGRAMS + "/00000000-0000-4000-8000-000000000abc", instance().viewer());
	}

	@When("the enterprise architect deletes the viewpoint {string} with its version")
	public void deleteViewpoint(String name) throws Exception {
		viewpointDelete(name, "1");
	}

	@When("the enterprise architect deletes the viewpoint {string} with the version {int}")
	public void deleteViewpointWithVersion(String name, int version) throws Exception {
		viewpointDelete(name, Integer.toString(version));
	}

	@When("the enterprise architect places the diagram {string} in {string}")
	public void place(String diagram, String folder) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("itemType", "diagram")
			.put("referenceId", id(diagram));
		this.response = instance().post(NODES + "/" + folderId(folder) + "/items", body.toString(),
				instance().architect(), Map.of());
	}

	@When("the enterprise architect places an unknown diagram in {string}")
	public void placeUnknown(String folder) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("itemType", "diagram")
			.put("referenceId", "00000000-0000-4000-8000-000000000abc");
		this.response = instance().post(NODES + "/" + folderId(folder) + "/items", body.toString(),
				instance().architect(), Map.of());
	}

	@When("the viewer reads the category diagrams of the browser")
	public void browserCategory() throws Exception {
		this.response = get(BROWSER + "/items?scope=current-state&category=diagrams&pageSize=200",
				instance().viewer());
	}

	@When("the viewer reads page {int} of the category diagrams of the browser")
	public void browserPage(int page) throws Exception {
		this.response = get(BROWSER + "/items?scope=current-state&category=diagrams&page=" + page,
				instance().viewer());
	}

	@When("the viewer reads the category diagrams of the solution {string}")
	public void browserSolution(String solution) throws Exception {
		String id = instance().oidc().single("SELECT id::text FROM solutions WHERE name = '" + quoted(solution) + "'");
		this.response = get(BROWSER + "/items?scope=solution&solutionId=" + id + "&category=diagrams",
				instance().viewer());
	}

	@Given("the diagram solution {string} exists")
	public void solution(String name) throws Exception {
		instance().oidc()
			.update("INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), '"
					+ quoted(name) + "', (SELECT min(id) FROM persons), (SELECT min(id) FROM persons))");
	}

	// Then

	@Then("the diagram answer is {int}")
	public void status(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the diagram answer is {int} naming {string}")
	public void statusNaming(int status, String field) throws Exception {
		status(status);
		assertThat(json().path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
	}

	@Then("the diagram answer is {int} with the problem {string}")
	public void problem(int status, String type) throws Exception {
		status(status);
		assertThat(json().path("type").asString()).isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the diagram has the version {int}, the viewpoint {string} and the creator Anna")
	public void created(int version, String viewpoint) throws Exception {
		status(201);
		assertThat(json().path("version").asInt()).isEqualTo(version);
		assertThat(json().path("viewpoint").path("name").asString()).isEqualTo(viewpoint);
		assertThat(json().path("createdBy").path("id").asInt()).isPositive();
		assertThat(this.response.headers().firstValue("ETag")).hasValue("\"" + version + "\"");
	}

	@Then("the diagram has the version {int}")
	public void version(int version) throws Exception {
		status(200);
		assertThat(json().path("version").asInt()).isEqualTo(version);
	}

	@Then("the diagram {string} keeps its viewpoint {string}")
	public void keepsViewpoint(String name, String viewpoint) throws Exception {
		status(200);
		assertThat(json().path("viewpoint").path("name").asString()).isEqualTo(viewpoint);
	}

	@Then("the diagrams listed are {stringList}")
	public void listed(List<String> names) throws Exception {
		status(200);
		assertThat(names()).containsExactlyElementsOf(names);
	}

	@Then("{int} diagrams are listed and a cursor to the next page is given")
	public void firstPage(int count) throws Exception {
		status(200);
		assertThat(json().path("items")).hasSize(count);
		assertThat(json().path("nextCursor").asString()).isNotEmpty();
	}

	@Then("{string} is no longer listed and answers 404")
	public void notListed(String name) throws Exception {
		HttpResponse<String> list = get(DIAGRAMS + "?pageSize=200&q=" + java.net.URLEncoder.encode(name,
				java.nio.charset.StandardCharsets.UTF_8), instance().viewer());
		assertThat(JsonMapper.shared().readTree(list.body()).path("items").toString()).doesNotContain(name);
		assertThat(get(DIAGRAMS + "/" + id(name), instance().viewer()).statusCode()).isEqualTo(404);
	}

	@Then("{string} is listed among the diagrams")
	public void listedAmong(String name) throws Exception {
		HttpResponse<String> list = get(DIAGRAMS + "?pageSize=200&q=" + java.net.URLEncoder.encode(name,
				java.nio.charset.StandardCharsets.UTF_8), instance().viewer());
		assertThat(JsonMapper.shared().readTree(list.body()).path("items").toString()).contains(name);
	}

	@Then("the deleted diagrams hold {string}")
	public void deletedHolds(String name) throws Exception {
		HttpResponse<String> list = get(DIAGRAMS + "/deleted?pageSize=200&q=" + java.net.URLEncoder.encode(name,
				java.nio.charset.StandardCharsets.UTF_8), instance().architect());
		assertThat(JsonMapper.shared().readTree(list.body()).path("items").toString()).contains(name);
	}

	@Then("one {string} audit event names the diagram {string}")
	public void audited(String type, String name) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '" + type + "' AND target_id = '" + id(name)
					+ "' AND details ->> 'name' = '" + quoted(name) + "'")).isEqualTo("1");
	}

	@Then("no {string} audit event names the diagram {string}")
	public void notAudited(String type, String name) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '" + type + "' AND target_id = '" + id(name)
					+ "'")).isEqualTo("0");
	}

	@Then("the diagram {string} was removed with its viewpoint, audited with the cause {string}")
	public void removedWithViewpoint(String name, String cause) throws Exception {
		assertThat(instance().oidc().single("SELECT count(*) FROM diagrams WHERE name = '" + quoted(name) + "'"))
			.isEqualTo("0");
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = 'diagram.deleted' AND details ->> 'name' = '"
					+ quoted(name) + "' AND details ->> 'cause' = '" + cause + "'")).isEqualTo("1");
	}

	@Then("the viewpoint {string} and its diagram {string} still exist")
	public void stillExist(String viewpoint, String diagram) throws Exception {
		assertThat(instance().oidc().single("SELECT count(*) FROM viewpoints WHERE name = '" + quoted(viewpoint) + "'"))
			.isEqualTo("1");
		assertThat(instance().oidc().single("SELECT count(*) FROM diagrams WHERE name = '" + quoted(diagram) + "'"))
			.isEqualTo("1");
	}

	@Then("the browser counts the diagrams that are not deleted")
	public void browserCount() throws Exception {
		HttpResponse<String> top = get(BROWSER, instance().viewer());
		assertThat(top.statusCode()).as(top.body()).isEqualTo(200);
		int counted = JsonMapper.shared().readTree(top.body()).path("currentState").path("diagrams").asInt();
		assertThat(Integer.toString(counted)).isEqualTo(
				instance().oidc().single("SELECT count(*) FROM diagrams WHERE deleted_at IS NULL"));
	}

	@Then("the browser lists {string} marked {string} before {string} marked {string}")
	public void browserOrder(String first, String firstNotation, String second, String secondNotation)
			throws Exception {
		status(200);
		List<String> names = names();
		assertThat(names.indexOf(first)).isNotNegative().isLessThan(names.indexOf(second));
		assertThat(notation(first)).isEqualTo(firstNotation);
		assertThat(notation(second)).isEqualTo(secondNotation);
	}

	@Then("the browser page holds the diagrams beyond the first 50")
	public void browserPageHolds() throws Exception {
		status(200);
		long total = json().path("page").path("totalElements").asLong();
		assertThat(total).isGreaterThanOrEqualTo(51);
		// a page holds at most 50 items
		assertThat(json().path("items")).hasSize((int) Math.min(50, total - 50));
	}

	@Then("the placement is audited with the item type {string} and the reference of {string}")
	public void placementAudited(String itemType, String diagram) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = 'tree-node.item-added' AND details ->> 'itemType' = '"
					+ itemType + "' AND details ->> 'referenceId' = '" + id(diagram) + "'")).isEqualTo("1");
	}

	// helpers

	private HttpResponse<String> createAs(Session session, String name, String description, String viewpointId)
			throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name).put("viewpointId", viewpointId);
		if (description != null) {
			body.put("description", description);
		}
		return instance().post(DIAGRAMS, body.toString(), session, Map.of());
	}

	private void update(String name, String newName, String ifMatch) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", newName);
		this.response = instance().send("PUT", DIAGRAMS + "/" + id(name), body.toString(), instance().architect(),
				(ifMatch != null) ? Map.of("If-Match", ifMatch) : Map.of());
	}

	private void delete(String name, int version) throws Exception {
		this.response = instance().send("DELETE", DIAGRAMS + "/" + id(name), null, instance().architect(),
				Map.of("If-Match", "\"" + version + "\""));
	}

	private void viewpointDelete(String name, String version) throws Exception {
		this.response = instance().send("DELETE", VIEWPOINTS + "/" + viewpointId(name), null, instance().architect(),
				Map.of("If-Match", "\"" + version + "\""));
	}

	private HttpResponse<String> get(String path, Session session) throws Exception {
		return instance().oidc().get(path, session.token());
	}

	private JsonNode json() {
		return JsonMapper.shared().readTree(this.response.body());
	}

	private List<String> names() {
		List<String> names = new ArrayList<>();
		json().path("items").forEach((item) -> names.add(item.path("name").asString()));
		return names;
	}

	private String notation(String name) {
		for (JsonNode item : json().path("items")) {
			if (name.equals(item.path("name").asString())) {
				return item.path("notation").asString();
			}
		}
		throw new AssertionError("no item " + name);
	}

	private List<String> labels(String folder) throws Exception {
		HttpResponse<String> tree = get("/api/v1/views/explorer", instance().viewer());
		JsonNode found = find(JsonMapper.shared().readTree(tree.body()), folder);
		assertThat(found).isNotNull();
		List<String> labels = new ArrayList<>();
		found.path("items").forEach((item) -> labels.add(item.path("displayLabel").isNull()
				? item.path("name").asString() : item.path("displayLabel").asString()));
		return labels;
	}

	private static JsonNode find(JsonNode node, String name) {
		if (node.path("name").asString().equals(name)) {
			return node;
		}
		for (JsonNode child : node.path("children")) {
			JsonNode found = find(child, name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static String id(String diagram) throws Exception {
		return instance().oidc().single("SELECT id::text FROM diagrams WHERE name = '" + quoted(diagram) + "'");
	}

	private static int version(String diagram) throws Exception {
		return Integer.parseInt(
				instance().oidc().single("SELECT version::text FROM diagrams WHERE name = '" + quoted(diagram) + "'"));
	}

	/** The deleted diagram of the name if there is one (another may have taken the name), else the diagram. */
	private static String restorableId(String name) throws Exception {
		return instance().oidc()
			.single("SELECT id::text FROM diagrams WHERE name = '" + quoted(name) + "' ORDER BY deleted_at NULLS LAST LIMIT 1");
	}

	private static int restorableVersion(String name) throws Exception {
		return Integer.parseInt(instance().oidc()
			.single("SELECT version::text FROM diagrams WHERE name = '" + quoted(name)
					+ "' ORDER BY deleted_at NULLS LAST LIMIT 1"));
	}

	private static String viewpointId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM viewpoints WHERE name = '" + quoted(name) + "'");
	}

	private static String anyViewpoint() throws Exception {
		return instance().oidc().single("SELECT id::text FROM viewpoints ORDER BY name LIMIT 1");
	}

	private static String anyOtherViewpoint(String diagramId) throws Exception {
		return instance().oidc()
			.single("SELECT id::text FROM viewpoints WHERE id <> (SELECT viewpoint_id FROM diagrams WHERE id = '"
					+ diagramId + "') LIMIT 1");
	}

	private static String folderId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM tree_nodes WHERE name = '" + quoted(name) + "'");
	}

	private static String root() throws Exception {
		return instance().oidc().single("SELECT id::text FROM tree_nodes WHERE parent_id IS NULL");
	}

	private static String quoted(String text) {
		return text.replace("'", "''");
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

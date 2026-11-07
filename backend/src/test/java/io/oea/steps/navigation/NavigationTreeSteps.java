package io.oea.steps.navigation;

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
 * Steps for features/navigation/navigation-tree.feature (us-054-catalog-navigation-tree), on the
 * instance of {@link CatalogSteps}. Folders and catalogs are addressed by their names, which are
 * unique in the feature.
 */
public class NavigationTreeSteps {

	private static final String TREE = "/api/v1/views/explorer";

	private static final String NODES = "/api/v1/tree-nodes";

	private HttpResponse<String> response;

	// folders

	@When("the {role} creates the folder {string} under the root")
	public void create(Session session, String name) throws Exception {
		this.response = instance().post(NODES, folder(name, root()).toString(), session, Map.of());
	}

	@Given("the enterprise architect has created the folder {string} under the root")
	public void created(String name) throws Exception {
		createdUnder(name, root());
	}

	@Given("the enterprise architect has created the folders {string} and {string} under the root")
	public void createdBoth(String first, String second) throws Exception {
		created(first);
		created(second);
	}

	@Given("the enterprise architect has created the folder {string} under {string}")
	public void createdUnderParent(String name, String parent) throws Exception {
		createdUnder(name, folderId(parent));
	}

	@When("the enterprise architect deletes the folder {string}")
	public void delete(String name) throws Exception {
		this.response = instance().send("DELETE", NODES + "/" + folderId(name), null, instance().architect());
		ok(this.response, 200);
	}

	@When("the enterprise architect deletes the root folder")
	public void deleteRoot() throws Exception {
		this.response = instance().send("DELETE", NODES + "/" + root(), null, instance().architect());
	}

	// entries

	@Given("the enterprise architect places {string} in {string}")
	public void place(String catalog, String folder) throws Exception {
		placeWithLabel(catalog, folder, null);
	}

	@When("the enterprise architect places {string} in {string} with the display label {string}")
	public void placeWithLabel(String catalog, String folder, String label) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("itemType", "catalog")
			.put("referenceId", catalogId(catalog));
		if (label != null) {
			body.put("displayLabel", label);
		}
		this.response = instance().post(NODES + "/" + folderId(folder) + "/items", body.toString(),
				instance().architect(), Map.of());
		ok(this.response, 201);
	}

	@When("the enterprise architect removes {string} from {string}")
	public void remove(String catalog, String folder) throws Exception {
		String item = instance().oidc()
			.single("SELECT i.id::text FROM tree_node_items i JOIN tree_nodes n ON n.id = i.tree_node_id WHERE n.name = '"
					+ quoted(folder) + "' AND i.reference_id = '" + catalogId(catalog) + "'");
		this.response = instance().send("DELETE", NODES + "/" + folderId(folder) + "/items/" + item, null,
				instance().architect());
		ok(this.response, 200);
	}

	// outcomes

	@Then("the tree of the {role} shows {string} under {string}")
	public void shows(Session session, String name, String parent) throws Exception {
		JsonNode folder = find(tree(session), parent);
		List<String> names = new ArrayList<>();
		folder.path("children").forEach((child) -> names.add(child.path("name").asString()));
		assertThat(names).contains(name);
	}

	@Then("the tree of the {role} lists {string} under {string}")
	public void lists(Session session, String label, String folder) throws Exception {
		assertThat(labels(find(tree(session), folder))).contains(label);
	}

	@Then("the tree of the {role} lists {string} under {string} and under {string}")
	public void listsTwice(Session session, String label, String first, String second) throws Exception {
		JsonNode tree = tree(session);
		assertThat(labels(find(tree, first))).contains(label);
		assertThat(labels(find(tree, second))).contains(label);
	}

	@Then("the tree of the {role} lists {string} under {string} only")
	public void listsOnlyUnder(Session session, String label, String folder) throws Exception {
		List<String> folders = new ArrayList<>();
		collect(tree(session), label, folders);
		assertThat(folders).containsExactly(folder);
	}

	@Then("the tree of the {role} does not list {string} under {string}")
	public void doesNotList(Session session, String label, String folder) throws Exception {
		assertThat(labels(find(tree(session), folder))).doesNotContain(label);
	}

	@Then("the tree has no folder {string}")
	public void noFolder(String name) throws Exception {
		assertThat(find(tree(instance().architect()), name)).isNull();
	}

	@Then("the tree change is rejected with 422 naming {string}")
	public void rejectedNaming(String field) throws Exception {
		List<String> fields = new ArrayList<>();
		ok(this.response, 422).path("invalidFields").forEach((value) -> fields.add(value.asString()));
		assertThat(fields).containsExactly(field);
	}

	@Then("the tree change is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	// helpers

	private void createdUnder(String name, String parentId) throws Exception {
		ok(instance().post(NODES, folder(name, parentId).toString(), instance().architect(), Map.of()), 201);
	}

	private static ObjectNode folder(String name, String parentId) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("parentId", parentId);
	}

	private static JsonNode tree(Session session) throws Exception {
		return ok(instance().oidc().get(TREE, session.token()), 200);
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

	private static void collect(JsonNode node, String label, List<String> folders) {
		if (labels(node).contains(label)) {
			folders.add(node.path("name").asString());
		}
		node.path("children").forEach((child) -> collect(child, label, folders));
	}

	private static List<String> labels(JsonNode folder) {
		assertThat(folder).isNotNull();
		List<String> labels = new ArrayList<>();
		folder.path("items").forEach((item) -> labels.add(item.path("displayLabel").isNull()
				? item.path("name").asString() : item.path("displayLabel").asString()));
		return labels;
	}

	private static String root() throws Exception {
		return instance().oidc().single("SELECT id::text FROM tree_nodes WHERE parent_id IS NULL");
	}

	private static String folderId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM tree_nodes WHERE name = '" + quoted(name) + "'");
	}

	private static String catalogId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM catalogs WHERE name = '" + quoted(name) + "'");
	}

	private static String quoted(String text) {
		return text.replace("'", "''");
	}

	private static JsonNode ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return JsonMapper.shared().readTree(response.body());
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

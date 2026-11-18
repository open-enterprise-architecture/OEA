package io.oea.steps.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ParameterType;
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

	// entities, solutions and moves (us-095-navigation-tree-items)

	@Given("the entity {string} exists")
	public void entityExists(String name) throws Exception {
		entity(name, false);
	}

	@Given("the connection {string} exists")
	public void connectionExists(String name) throws Exception {
		entity(name, true);
	}

	@Given("the solution {string} exists")
	public void solutionExists(String name) throws Exception {
		instance().oidc()
			.update("INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), '"
					+ quoted(name) + "', (SELECT min(id) FROM persons), (SELECT min(id) FROM persons))");
	}

	@Given("the entities {stringList} are placed in {string}")
	public void entitiesPlaced(List<String> names, String folder) throws Exception {
		for (String name : names) {
			entity(name, false);
			placeEntityIn(name, folder);
			ok(this.response, 201);
		}
	}

	@Given("the enterprise architect has placed the entity {string} in {string}")
	public void placedEntity(String name, String folder) throws Exception {
		placeEntityIn(name, folder);
		ok(this.response, 201);
	}

	@When("the enterprise architect places the entity {string} in {string}")
	public void placeEntity(String name, String folder) throws Exception {
		placeEntityIn(name, folder);
	}

	@When("the enterprise architect places the solution {string} in {string}")
	public void placeSolution(String name, String folder) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("itemType", "solution")
			.put("referenceId", instance().oidc()
				.single("SELECT id::text FROM solutions WHERE name = '" + quoted(name) + "'"));
		this.response = instance().post(NODES + "/" + folderId(folder) + "/items", body.toString(),
				instance().architect(), Map.of());
	}

	@When("the entity {string} is deleted")
	public void entityDeleted(String name) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) WHERE name = '"
					+ quoted(name) + "'");
	}

	@When("the entity {string} is restored")
	public void entityRestored(String name) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET deleted_at = NULL, deleted_by = NULL WHERE name = '" + quoted(name) + "'");
	}

	@When("the enterprise architect removes the entity {string} from {string}")
	public void removeEntity(String name, String folder) throws Exception {
		String item = instance().oidc()
			.single("SELECT i.id::text FROM tree_node_items i JOIN tree_nodes n ON n.id = i.tree_node_id WHERE n.name = '"
					+ quoted(folder) + "' AND i.entity_id = " + entityId(name));
		this.response = instance().send("DELETE", NODES + "/" + folderId(folder) + "/items/" + item, null,
				instance().architect());
		ok(this.response, 200);
	}

	@When("the {role} moves the folder {string} under {string} at position {int}")
	public void moveFolder(Session session, String name, String parent, int position) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("parentId", folderId(parent))
			.put("position", position);
		this.response = instance().send("PUT", NODES + "/" + folderId(name) + "/position", body.toString(), session);
	}

	@When("the enterprise architect moves the entry {string} of {string} to {string} at position {int}")
	public void moveEntry(String name, String folder, String target, int position) throws Exception {
		String item = instance().oidc()
			.single("SELECT i.id::text FROM tree_node_items i JOIN tree_nodes n ON n.id = i.tree_node_id WHERE n.name = '"
					+ quoted(folder) + "' AND i.entity_id = " + entityId(name));
		ObjectNode body = JsonMapper.shared().createObjectNode().put("nodeId", folderId(target)).put("position",
				position);
		this.response = instance().send("PUT", NODES + "/" + folderId(folder) + "/items/" + item + "/position",
				body.toString(), instance().architect());
	}

	@Then("the tree change is accepted")
	public void accepted() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Then("the folders under {string} in the tree of the {role} are {string}")
	public void foldersAre(String parent, Session session, String names) throws Exception {
		List<String> children = new ArrayList<>();
		find(tree(session), parent).path("children").forEach((child) -> children.add(child.path("name").asString()));
		assertThat(children).containsExactlyElementsOf(List.of(names.split(", ")));
	}

	@Then("the entries of {string} in the tree of the {role} are {string}")
	public void entriesAre(String folder, Session session, String names) throws Exception {
		assertThat(labels(find(tree(session), folder))).containsExactlyElementsOf(List.of(names.split(", ")));
	}

	@Then("the entry {string} under {string} is marked as deleted in the tree of the {role}")
	public void marked(String name, String folder, Session session) throws Exception {
		assertThat(deleted(name, folder, session)).isTrue();
	}

	@Then("the entry {string} under {string} is not marked as deleted in the tree of the {role}")
	public void notMarked(String name, String folder, Session session) throws Exception {
		assertThat(deleted(name, folder, session)).isFalse();
	}

	@Then("the entity {string} is still at version {int}")
	public void entityVersion(String name, int version) throws Exception {
		assertThat(instance().oidc().single("SELECT version::text FROM entities WHERE name = '" + quoted(name) + "'"))
			.isEqualTo(String.valueOf(version));
	}

	@Then("one {string} audit event names {string} as its entity and no name")
	public void entityAudit(String eventType, String name) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*)::text FROM audit.events WHERE event_type = '" + eventType
					+ "' AND details ->> 'itemType' = 'entity' AND details ->> 'entityId' = '" + entityId(name)
					+ "' AND details::text NOT LIKE '%" + quoted(name) + "%'"))
			.isEqualTo("1");
	}

	@Then("one {string} audit event moves from {string} to {string}")
	public void moveAudit(String eventType, String from, String to) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*)::text FROM audit.events WHERE event_type = '" + eventType + "' AND target_id = '"
					+ folderId(from) + "' AND details ->> 'fromNodeId' = '" + folderId(from)
					+ "' AND details ->> 'toNodeId' = '" + folderId(to) + "'"))
			.isEqualTo("1");
	}

	@Then("no {string} audit event exists for {string}")
	public void noAudit(String eventType, String folder) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*)::text FROM audit.events WHERE event_type = '" + eventType + "' AND target_id = '"
					+ folderId(folder) + "'"))
			.isEqualTo("0");
	}

	@ParameterType("\"[^\"]*\"(?:, \"[^\"]*\")*(?: and \"[^\"]*\")?")
	public List<String> stringList(String text) {
		List<String> names = new ArrayList<>();
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"([^\"]*)\"").matcher(text);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return names;
	}

	private void placeEntityIn(String name, String folder) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("itemType", "entity")
			.put("entityId", Integer.parseInt(entityId(name)));
		this.response = instance().post(NODES + "/" + folderId(folder) + "/items", body.toString(),
				instance().architect(), Map.of());
	}

	private static void entity(String name, boolean connection) throws Exception {
		String type = connection ? "Tree Connection Type" : "Tree Element Type";
		instance().oidc()
			.update("INSERT INTO metatypes (config_id, name, is_connection) SELECT '00000000-0000-0000-0000-000000000001', '"
					+ type + "', " + connection + " WHERE NOT EXISTS (SELECT 1 FROM metatypes WHERE name = '" + type
					+ "')");
		instance().oidc()
			.update("INSERT INTO entities (metatype_id, name, created_by) VALUES ((SELECT id FROM metatypes WHERE name = '"
					+ type + "'), '" + quoted(name) + "', (SELECT min(id) FROM persons))");
	}

	private static String entityId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM entities WHERE name = '" + quoted(name) + "'");
	}

	private static boolean deleted(String name, String folder, Session session) throws Exception {
		for (JsonNode item : find(tree(session), folder).path("items")) {
			if (name.equals(item.path("name").asString())) {
				return item.path("deleted").asBoolean();
			}
		}
		throw new AssertionError("no entry " + name + " under " + folder);
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

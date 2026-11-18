package io.oea.steps.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
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
 * Steps for features/repository/entity-hierarchy.feature (us-135-component-hierarchy), on the
 * instance of {@link CatalogSteps} with the starter package, whose metatype "Composition" is the
 * marked composition. The instance is shared, so every scenario names its entities with a unique
 * suffix; the architect and the viewer sessions are the ones of the instance.
 */
public class EntityHierarchySteps {

	private static final String BATCH = "/api/v1/entities/batch";

	private final String suffix = " " + UUID.randomUUID().toString().substring(0, 8);

	/** The IDs of the entities of the scenario by the name in the feature. */
	private final Map<String, Integer> ids = new HashMap<>();

	private final List<Integer> chain = new ArrayList<>();

	private HttpResponse<String> response;

	private HttpResponse<String> second;

	private JsonNode lastResult;

	private int createdAid;

	// Given

	@Given("the application components {string} and {string} without a parent")
	public void twoComponents(String first, String second) throws Exception {
		component(first);
		component(second);
	}

	@Given("the application components {string}, {string} and {string} without a parent")
	public void threeComponents(String first, String second, String third) throws Exception {
		component(first);
		component(second);
		component(third);
	}

	@Given("the application component {string} without a parent")
	public void oneComponent(String name) throws Exception {
		component(name);
	}

	@Given("an application component {string} without a parent")
	public void anComponent(String name) throws Exception {
		component(name);
	}

	@Given("a chain of {int} application components")
	public void chain(int levels) throws Exception {
		int parent = 0;
		for (int level = 1; level <= levels; level++) {
			int id = component("Chain " + level);
			this.chain.add(id);
			if (level > 1) {
				ok(parent(id, 1, parent));
			}
			parent = id;
		}
	}

	@Given("the data flow {string} between {string} and {string}")
	public void flow(String name, String source, String target) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.put("connectionMetatypeId", instance().oidc().single("SELECT id::text FROM metatypes WHERE name = 'Composition'"));
		body.put("name", name + this.suffix).put("sourceId", id(source)).put("targetId", id(target));
		HttpResponse<String> created = instance().post("/api/v1/connections", body.toString(), instance().architect(),
				Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.ids.put(name, JsonMapper.shared().readTree(created.body()).path("id").asInt());
	}

	@Given("the structuring aid {string}")
	public void aid(String name) throws Exception {
		this.ids.put(name, newAid(name + this.suffix, null));
	}

	@Given("the structuring aid {string} with the application components {string}, {string} and {string}")
	public void aidWithChildren(String aid, String a, String b, String c) throws Exception {
		int id = newAid(aid + this.suffix, null);
		this.ids.put(aid, id);
		for (String child : new String[] { a, b, c }) {
			int childId = component(child);
			ok(parent(childId, 1, id));
		}
	}

	@Given("the architect has set {string} as parent of {string}")
	public void hasSet(String parent, String child) throws Exception {
		ok(parent(id(child), version(id(child)), id(parent)));
	}

	// When

	@When("the architect sets {string} as parent of {string}")
	public void sets(String parent, String child) throws Exception {
		this.response = parent(id(child), version(id(child)), id(parent));
		this.lastResult = (this.response.statusCode() == 200) ? JsonMapper.shared().readTree(this.response.body()) : null;
	}

	@When("the viewer sets {string} as parent of {string}")
	public void viewerSets(String parent, String child) throws Exception {
		this.response = send("PUT", id(child), version(id(child)), body(id(parent)), instance().viewer());
	}

	@When("the architect sets the connection {string} as parent of {string}")
	public void setsConnection(String connection, String child) throws Exception {
		sets(connection, child);
	}

	@When("the architect sets the deepest of the chain as parent of {string}")
	public void setsDeepest(String child) throws Exception {
		this.response = parent(id(child), version(id(child)), this.chain.get(this.chain.size() - 1));
	}

	@When("the architect clears the parent of {string}")
	public void clears(String child) throws Exception {
		this.response = send("PUT", id(child), version(id(child)), "{\"parentEntityId\": null}", instance().architect());
		this.lastResult = JsonMapper.shared().readTree(this.response.body());
		ok(this.response);
	}

	@When("the architect reads the diff of {string} from version {int} to {int}")
	public void diff(String child, int from, int to) throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/entities/" + id(child) + "/diff?from=" + from + "&to=" + to, instance().architect().token());
	}

	@When("the architect sets the parent of {string} with the version {int} and then without If-Match")
	public void staleAndMissing(String child, int version) throws Exception {
		int other = id("SAP S/4HANA");
		this.response = send("PUT", id(child), version, body(other), instance().architect());
		this.second = send("PUT", id(child), null, body(other), instance().architect());
	}

	@When("the architect creates the structuring aid {string}")
	public void createAid(String name) throws Exception {
		this.response = instance().post("/api/v1/structuring-aids",
				JsonMapper.shared().createObjectNode().put("name", name + this.suffix).toString(), instance().architect(),
				Map.of());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
		this.createdAid = JsonMapper.shared().readTree(this.response.body()).path("id").asInt();
	}

	@When("the architect creates a structuring aid with a name of {int} characters")
	public void createAidOfLength(int length) throws Exception {
		this.response = instance().post("/api/v1/structuring-aids",
				JsonMapper.shared().createObjectNode().put("name", "a".repeat(length)).toString(),
				instance().architect(), Map.of());
	}

	@When("the architect deletes the structuring aid {string}")
	public void deleteAid(String name) throws Exception {
		this.response = delete(id(name), version(id(name)));
	}

	@When("the architect deletes {string} as structuring aid")
	public void deleteOther(String name) throws Exception {
		this.response = delete(id(name), version(id(name)));
	}

	@When("the architect writes an entity of the metatype {string} through the batch")
	public void batchAid(String metatype) throws Exception {
		String id = instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + metatype + "'");
		ObjectNode request = JsonMapper.shared().createObjectNode();
		request.putArray("entities").addObject().put("name", "Batch aid" + this.suffix).put("metatypeId", id);
		this.response = instance().post(BATCH, request.toString(), instance().architect(), Map.of());
	}

	// Then

	@Then("{string} has the parent {string} and the version {int}")
	public void hasParent(String child, String parent, int version) throws Exception {
		assertThat(parentOf(id(child))).isEqualTo(String.valueOf(id(parent)));
		assertThat(version(id(child))).isEqualTo(version);
	}

	@Then("{string} has no parent and the version {int}")
	public void hasNoParent(String child, int version) throws Exception {
		assertThat(parentOf(id(child))).isNull();
		assertThat(version(id(child))).isEqualTo(version);
	}

	@Then("{string} keeps the version {int}")
	public void keeps(String name, int version) throws Exception {
		assertThat(version(id(name))).isEqualTo(version);
	}

	@Then("the history of {string} names the field {string} in version {int}")
	public void history(String name, String label, int version) throws Exception {
		HttpResponse<String> history = instance().oidc()
			.get("/api/v1/entities/" + id(name) + "/history", instance().architect().token());
		JsonNode versions = JsonMapper.shared().readTree(history.body()).path("versions");
		JsonNode wanted = null;
		for (JsonNode candidate : versions) {
			if (candidate.path("version").asInt() == version) {
				wanted = candidate;
			}
		}
		assertThat(wanted).isNotNull();
		List<String> labels = new ArrayList<>();
		wanted.path("changes").forEach((change) -> labels.add(change.path("label").asString()));
		assertThat(labels).contains(label);
	}

	@Then("the diff shows the parent changing from none to {string}")
	public void diffShows(String parent) {
		assertThat(this.response.statusCode()).isEqualTo(200);
		JsonNode change = null;
		for (JsonNode candidate : JsonMapper.shared().readTree(this.response.body()).path("changes")) {
			if ("parentEntityId".equals(candidate.path("field").asString())) {
				change = candidate;
			}
		}
		assertThat(change).isNotNull();
		assertThat(change.path("oldValue").isNull()).isTrue();
		assertThat(change.path("newValue").asString()).isEqualTo(parent + this.suffix);
	}

	@Then("the hierarchy answer is {int}")
	public void answer(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the hierarchy answer is 422 naming {string}")
	public void invalid(String field) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		List<String> fields = new ArrayList<>();
		JsonMapper.shared().readTree(this.response.body()).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	@Then("the creation answer is {int}")
	public void creation(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the first request is answered with 412 and the second with 428")
	public void staleChecked() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(412);
		assertThat(this.second.statusCode()).as(this.second.body()).isEqualTo(428);
	}

	@Then("a composition connection from {string} to {string} with autoCreated exists")
	public void compositionExists(String parent, String child) throws Exception {
		assertThat(connections(id(parent), id(child), "AND e.auto_created AND e.deleted_at IS NULL")).hasSize(1);
	}

	@Then("the answer names the connection")
	public void namesConnection() {
		assertThat(this.lastResult.path("composition").path("connectionId").asInt()).isPositive();
		assertThat(this.lastResult.path("composition").path("reason").asString()).isEqualTo("created");
	}

	@Then("the automatically created composition connection is deleted")
	public void removed() throws Exception {
		assertThat(this.lastResult.path("removedConnectionId").asInt()).isPositive();
		String deleted = instance().oidc()
			.single("SELECT (deleted_at IS NOT NULL)::text FROM entities WHERE id = " + this.lastResult.path("removedConnectionId").asInt());
		assertThat(deleted).isEqualTo("true");
	}

	@Then("the old composition connection is deleted and one from {string} to {string} exists")
	public void moved(String parent, String child) throws Exception {
		assertThat(this.lastResult.path("removedConnectionId").asInt()).isPositive();
		assertThat(connections(id(parent), id(child), "AND e.auto_created AND e.deleted_at IS NULL")).hasSize(1);
	}

	@Then("the answer gives the reason {string} and no connection")
	public void reason(String reason) {
		assertThat(this.lastResult.path("composition").path("reason").asString()).isEqualTo(reason);
		assertThat(this.lastResult.path("composition").path("created").asBoolean()).isFalse();
	}

	@Then("it is an entity of the metatype {string} at the root level")
	public void isAid(String metatype) throws Exception {
		String row = instance().oidc()
			.single("SELECT m.name || '/' || e.is_structural_grouper || '/' || coalesce(e.parent_entity_id::text, '') FROM entities e JOIN metatypes m ON m.id = e.metatype_id WHERE e.id = "
					+ this.createdAid);
		assertThat(row).isEqualTo(metatype + "/true/");
	}

	@Then("it is deleted and {string}, {string} and {string} are at the root level with the version {int} and not deleted")
	public void aidDeleted(String a, String b, String c, int version) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(204);
		for (String child : new String[] { a, b, c }) {
			assertThat(parentOf(id(child))).isNull();
			assertThat(version(id(child))).isEqualTo(version);
			assertThat(instance().oidc().single("SELECT (deleted_at IS NULL)::text FROM entities WHERE id = " + id(child)))
				.isEqualTo("true");
		}
	}

	@Then("the audit records entity.parent-changed for {string} with the parent of {string}")
	public void audited(String child, String parent) throws Exception {
		String details = instance().oidc()
			.single("SELECT details::text FROM audit.events WHERE event_type = 'entity.parent-changed' AND entity_id = "
					+ id(child) + " ORDER BY id DESC LIMIT 1");
		JsonNode node = JsonMapper.shared().readTree(details);
		assertThat(node.path("toParentId").asInt()).isEqualTo(id(parent));
		assertThat(node.path("version").asInt()).isEqualTo(2);
		assertThat(details).doesNotContain(child);
	}

	@Then("the batch item is rejected naming {string}")
	public void batchRejected(String field) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		JsonNode item = JsonMapper.shared().readTree(this.response.body()).path("items").get(0);
		assertThat(item.path("status").asString()).isEqualTo("error");
		assertThat(item.path("errors").get(0).path("field").asString()).isEqualTo(field);
	}

	// helpers

	private int component(String name) throws Exception {
		ObjectNode request = JsonMapper.shared().createObjectNode();
		ArrayNode entities = request.putArray("entities");
		entities.addObject().put("name", name + this.suffix).put("externalId", "hier:" + name + this.suffix).put("metatypeId",
				instance().oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'"));
		HttpResponse<String> written = instance().post(BATCH, request.toString(), instance().architect(), Map.of());
		assertThat(written.statusCode()).as(written.body()).isEqualTo(200);
		int id = JsonMapper.shared().readTree(written.body()).path("items").get(0).path("id").asInt();
		this.ids.put(name, id);
		return id;
	}

	private int newAid(String name, Integer parent) throws Exception {
		HttpResponse<String> created = instance().post("/api/v1/structuring-aids",
				JsonMapper.shared().createObjectNode().put("name", name).toString(), instance().architect(), Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		return JsonMapper.shared().readTree(created.body()).path("id").asInt();
	}

	private HttpResponse<String> parent(int child, int version, int parent) throws Exception {
		return send("PUT", child, version, body(parent), instance().architect());
	}

	private HttpResponse<String> send(String method, int child, Integer version, String body, Session session)
			throws Exception {
		return instance().send(method, "/api/v1/entities/" + child + "/parent", body, session,
				(version != null) ? Map.of("If-Match", "\"" + version + "\"") : Map.of());
	}

	private HttpResponse<String> delete(int aid, int version) throws Exception {
		return instance().send("DELETE", "/api/v1/structuring-aids/" + aid, null, instance().architect(),
				Map.of("If-Match", "\"" + version + "\""));
	}

	private static String body(int parent) {
		return "{\"parentEntityId\": " + parent + "}";
	}

	private int id(String name) {
		Integer id = this.ids.get(name);
		assertThat(id).as("entity " + name).isNotNull();
		return id;
	}

	private int version(int id) throws Exception {
		return Integer.parseInt(instance().oidc().single("SELECT version FROM entities WHERE id = " + id));
	}

	private String parentOf(int id) throws Exception {
		return instance().oidc().single("SELECT parent_entity_id::text FROM entities WHERE id = " + id);
	}

	private List<String> connections(int source, int target, String condition) throws Exception {
		String ids = instance().oidc()
			.single("SELECT coalesce(string_agg(e.id::text, ','), '') FROM entity_connections c JOIN entities e ON e.id = c.entity_id WHERE c.source_entity_id = "
					+ source + " AND c.target_entity_id = " + target + " " + condition);
		return ids.isEmpty() ? List.of() : List.of(ids.split(","));
	}

	private static HttpResponse<String> ok(HttpResponse<String> response) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return response;
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

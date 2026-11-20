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
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/repository/entity-delete.feature (s17-entity-delete), on the instance of
 * {@link CatalogSteps} with the starter package. The instance is shared, so every scenario
 * names its entities with a unique suffix and asserts only on the entities it creates. The
 * children get their parent through the hierarchy operation, which also creates the automatic
 * composition connection that the cascade takes along.
 */
public class EntityDeleteSteps {

	private static final String BATCH = "/api/v1/entities/batch";

	private final String suffix = " " + UUID.randomUUID().toString().substring(0, 8);

	private final Map<String, Integer> ids = new HashMap<>();

	/** The versions of everything the architect deleted, before the deletion. */
	private final Map<Integer, Integer> versionsBefore = new HashMap<>();

	private HttpResponse<String> response;

	private HttpResponse<String> second;

	private List<Integer> deleted = new ArrayList<>();

	private JsonNode preview;

	// Given

	@Given("the deletion entities {string}, {string}, {string}, {string} and {string}")
	public void entities(String a, String b, String c, String d, String e) throws Exception {
		for (String name : new String[] { a, b, c, d, e }) {
			component(name);
		}
	}

	@Given("{string} and {string} are children of {string}")
	public void children(String a, String b, String parent) throws Exception {
		setParent(a, parent);
		setParent(b, parent);
	}

	@Given("{string} is a child of {string}")
	public void child(String child, String parent) throws Exception {
		setParent(child, parent);
	}

	@Given("{string} is connected to {string}")
	public void connected(String source, String target) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.put("connectionMetatypeId", instance().oidc().single("SELECT id::text FROM metatypes WHERE name = 'Composition'"));
		body.put("name", source + " to " + target + this.suffix).put("sourceId", id(source)).put("targetId", id(target));
		HttpResponse<String> created = instance().post("/api/v1/connections", body.toString(), instance().architect(),
				Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.ids.put(source + " to " + target, JsonMapper.shared().readTree(created.body()).path("id").asInt());
	}

	@Given("the deletion structuring aid {string}")
	public void aid(String name) throws Exception {
		HttpResponse<String> created = instance().post("/api/v1/structuring-aids",
				JsonMapper.shared().createObjectNode().put("name", name + this.suffix).toString(), instance().architect(),
				Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.ids.put(name, JsonMapper.shared().readTree(created.body()).path("id").asInt());
	}

	@Given("the architect has deleted {string}")
	public void hasDeleted(String name) throws Exception {
		HttpResponse<String> deletion = delete(id(name), version(id(name)), instance().architect());
		assertThat(deletion.statusCode()).as(deletion.body()).isEqualTo(200);
		this.deleted = ids(JsonMapper.shared().readTree(deletion.body()).path("deleted"));
		// a deletion does not change versions
		for (int id : this.deleted) {
			this.versionsBefore.put(id, version(id));
		}
	}

	@Given("the architect has restored the deleted {string}")
	public void hasRestored(String name) throws Exception {
		restore(name, instance().architect());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Given("the architect has changed the description of {string}")
	public void changed(String name) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("entities")
			.addObject()
			.put("externalId", "del:" + name + this.suffix)
			.put("name", name + this.suffix)
			.put("description", "Changed")
			.put("metatypeId", instance().oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'"));
		HttpResponse<String> written = instance().post(BATCH, body.toString(), instance().architect(), Map.of());
		assertThat(written.statusCode()).as(written.body()).isEqualTo(200);
		assertThat(version(id(name))).isEqualTo(2);
	}

	// When

	@When("the architect reads the deletion preview of {string}")
	public void preview(String name) throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/entities/" + id(name) + "/deletion", instance().architect().token());
		this.preview = (this.response.statusCode() == 200) ? JsonMapper.shared().readTree(this.response.body()) : null;
	}

	@When("the architect deletes {string} with its current version")
	public void deletes(String name) throws Exception {
		deleteWith(name, instance().architect());
	}

	@When("the architect deletes the connection {string} with its current version")
	public void deletesConnection(String name) throws Exception {
		deleteWith(name, instance().architect());
	}

	@When("the viewer deletes {string} with its current version")
	public void viewerDeletes(String name) throws Exception {
		deleteWith(name, instance().viewer());
	}

	@When("the architect deletes {string} with the version {int}")
	public void deletesWithVersion(String name, int version) throws Exception {
		this.response = delete(id(name), version, instance().architect());
	}

	@When("the architect deletes {string} with the version {int} and then without If-Match")
	public void staleAndMissing(String name, int version) throws Exception {
		this.response = delete(id(name), version, instance().architect());
		this.second = instance().send("DELETE", "/api/v1/entities/" + id(name), null, instance().architect());
	}

	@When("the architect deletes the structuring aid {string} through the entity operation")
	public void deletesAid(String name) throws Exception {
		this.response = delete(id(name), version(id(name)), instance().architect());
	}

	@When("the architect restores the deleted {string}")
	public void restores(String name) throws Exception {
		restore(name, instance().architect());
	}

	@When("the viewer restores the deleted {string}")
	public void viewerRestores(String name) throws Exception {
		restore(name, instance().viewer());
	}

	@When("the architect restores the version {int} of {string}")
	public void restoresVersion(int target, String name) throws Exception {
		this.response = instance().send("POST", "/api/v1/entities/" + id(name) + "/restore",
				"{\"targetVersion\": " + target + "}", instance().architect(),
				Map.of("If-Match", "\"" + version(id(name)) + "\""));
	}

	// Then

	@Then("the preview names {int} children and {int} connections")
	public void previewNames(int children, int connections) {
		assertThat(this.preview).as(this.response.body()).isNotNull();
		assertThat(this.preview.path("childCount").asInt()).isEqualTo(children);
		assertThat(this.preview.path("connectionCount").asInt()).isEqualTo(connections);
	}

	@Then("nothing is deleted")
	public void nothingDeleted() throws Exception {
		for (Map.Entry<String, Integer> entry : this.ids.entrySet()) {
			assertThat(isDeleted(entry.getValue())).as(entry.getKey()).isFalse();
		}
	}

	@Then("the deletion answer is {int}")
	public void answer(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the deletion answer is {int} of type {string}")
	public void answerWithType(int status, String type) {
		answer(status);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("type").asString())
			.isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the deletion answer is {int} listing {int} IDs")
	public void answerListing(int status, int count) {
		answer(status);
		this.deleted = ids(JsonMapper.shared().readTree(this.response.body()).path("deleted"));
		assertThat(this.deleted).hasSize(count);
		assertThat(this.deleted.get(0)).as("the entity comes first").isEqualTo(this.ids.get("ERP"));
	}

	@Then("{string}, its 3 children and its 4 connections are deleted")
	public void cascadeDeleted(String name) throws Exception {
		assertThat(this.deleted).hasSize(8);
		for (int id : this.deleted) {
			assertThat(isDeleted(id)).as("entity " + id).isTrue();
		}
		assertThat(isDeleted(id(name))).isTrue();
	}

	@Then("{string} is not deleted")
	public void notDeleted(String name) throws Exception {
		assertThat(isDeleted(id(name))).isFalse();
	}

	@Then("the structuring aid {string} is not deleted")
	public void aidNotDeleted(String name) throws Exception {
		notDeleted(name);
	}

	@Then("the audit records entity.deleted for {string} and entity.cascade-deleted naming {string} for the 7 others")
	public void audited(String name, String origin) throws Exception {
		int erp = id(name);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.deleted' AND entity_id = " + erp))
			.isOne();
		for (int id : this.deleted.subList(1, this.deleted.size())) {
			String details = instance().oidc()
				.single("SELECT details::text FROM audit.events WHERE event_type = 'entity.cascade-deleted' AND entity_id = "
						+ id);
			assertThat(JsonMapper.shared().readTree(details).path("triggeredByEntityId").asInt()).isEqualTo(id(origin));
		}
	}

	@Then("only the connection {string} is deleted")
	public void onlyConnection(String name) throws Exception {
		for (Map.Entry<String, Integer> entry : this.ids.entrySet()) {
			assertThat(isDeleted(entry.getValue())).as(entry.getKey()).isEqualTo(entry.getKey().equals(name));
		}
	}

	@Then("the first deletion is answered with 412 and the second with 428")
	public void staleChecked() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(412);
		assertThat(this.second.statusCode()).as(this.second.body()).isEqualTo(428);
	}

	@Then("{string}, its 3 children and its 4 connections are active again with their versions unchanged")
	public void cascadeRestored(String name) throws Exception {
		assertThat(this.deleted).hasSize(8);
		for (int id : this.deleted) {
			assertThat(isDeleted(id)).as("entity " + id).isFalse();
			assertThat(version(id)).as("version of " + id).isEqualTo(this.versionsBefore.get(id));
		}
	}

	@Then("the audit records entity.undeleted for each of the 8")
	public void undeletedAudited() throws Exception {
		for (int id : this.deleted) {
			assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'entity.undeleted' AND entity_id = " + id))
				.as("entity " + id)
				.isOne();
		}
	}

	@Then("the connection {string} stays deleted and is named with the reason {string}")
	public void stays(String connection, String reason) throws Exception {
		assertThat(isDeleted(id(connection))).isTrue();
		JsonNode named = null;
		for (JsonNode entry : JsonMapper.shared().readTree(this.response.body()).path("notRestored")) {
			if (entry.path("entityId").asInt() == id(connection)) {
				named = entry;
			}
		}
		assertThat(named).as(this.response.body()).isNotNull();
		assertThat(named.path("reason").asString()).isEqualTo(reason);
	}

	@Then("{string} is still deleted")
	public void isDeletedStep(String name) throws Exception {
		assertThat(isDeleted(id(name))).isTrue();
	}

	@Then("{string} and {string} are active again")
	public void activeAgain(String a, String b) throws Exception {
		assertThat(isDeleted(id(a))).isFalse();
		assertThat(isDeleted(id(b))).isFalse();
	}

	@Then("{string} is at the top level as a new version and named as moved")
	public void movedToTopLevel(String name) throws Exception {
		assertThat(instance().oidc().single("SELECT parent_entity_id IS NULL FROM entities WHERE id = " + id(name)))
			.isEqualTo("t");
		assertThat(version(id(name))).isEqualTo(this.versionsBefore.get(id(name)) + 1);
		List<Integer> moved = ids(JsonMapper.shared().readTree(this.response.body()).path("movedToTopLevel"));
		assertThat(moved).containsExactly(id(name));
	}

	@Then("{string} and {string} stay deleted")
	public void stayDeleted(String a, String b) throws Exception {
		assertThat(isDeleted(id(a))).isTrue();
		assertThat(isDeleted(id(b))).isTrue();
	}

	@Then("{string} has the version {int}")
	public void hasVersion(String name, int version) throws Exception {
		assertThat(version(id(name))).isEqualTo(version);
	}

	// helpers

	private int component(String name) throws Exception {
		ObjectNode request = JsonMapper.shared().createObjectNode();
		request.putArray("entities")
			.addObject()
			.put("name", name + this.suffix)
			.put("externalId", "del:" + name + this.suffix)
			.put("metatypeId", instance().oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'"));
		HttpResponse<String> written = instance().post(BATCH, request.toString(), instance().architect(), Map.of());
		assertThat(written.statusCode()).as(written.body()).isEqualTo(200);
		int id = JsonMapper.shared().readTree(written.body()).path("items").get(0).path("id").asInt();
		this.ids.put(name, id);
		return id;
	}

	private void setParent(String child, String parent) throws Exception {
		HttpResponse<String> set = instance().send("PUT", "/api/v1/entities/" + id(child) + "/parent",
				"{\"parentEntityId\": " + id(parent) + "}", instance().architect(),
				Map.of("If-Match", "\"" + version(id(child)) + "\""));
		assertThat(set.statusCode()).as(set.body()).isEqualTo(200);
	}

	private void deleteWith(String name, Session session) throws Exception {
		this.response = delete(id(name), version(id(name)), session);
		if (this.response.statusCode() == 200) {
			this.deleted = ids(JsonMapper.shared().readTree(this.response.body()).path("deleted"));
		}
	}

	private HttpResponse<String> delete(int id, int version, Session session) throws Exception {
		return instance().send("DELETE", "/api/v1/entities/" + id, null, session,
				Map.of("If-Match", "\"" + version + "\""));
	}

	private void restore(String name, Session session) throws Exception {
		this.response = instance().send("POST", "/api/v1/entities/" + id(name) + "/undelete", null, session,
				Map.of("If-Match", "\"" + version(id(name)) + "\""));
	}

	private static List<Integer> ids(JsonNode nodes) {
		List<Integer> ids = new ArrayList<>();
		nodes.forEach((node) -> ids.add(node.asInt()));
		return ids;
	}

	private int id(String name) {
		Integer id = this.ids.get(name);
		assertThat(id).as("entity " + name).isNotNull();
		return id;
	}

	private int version(int id) throws Exception {
		return Integer.parseInt(instance().oidc().single("SELECT version FROM entities WHERE id = " + id));
	}

	private boolean isDeleted(int id) throws Exception {
		return Boolean.parseBoolean(instance().oidc().single("SELECT (deleted_at IS NOT NULL)::text FROM entities WHERE id = " + id));
	}

	private int count(String sql) throws Exception {
		return Integer.parseInt(instance().oidc().single(sql));
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

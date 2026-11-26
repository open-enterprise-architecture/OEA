package io.oea.steps.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/repository/deleted-entities.feature (s17-deleted-entities), on the instance of
 * {@link CatalogSteps}. The instance is shared, so every scenario works with metatypes of its own and
 * lists only by them; it never asserts on global counts.
 */
public class DeletedEntitiesSteps {

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private static final String BATCH = "/api/v1/entities/batch";

	private final String suffix = UUID.randomUUID().toString().substring(0, 8);

	private final Map<String, Integer> ids = new HashMap<>();

	private String elementType;

	private String flowType;

	private HttpResponse<String> response;

	// Given

	@Given("the listed elements {string}, {string} and {string} where {string} is a child of {string}")
	public void elements(String first, String second, String third, String child, String parent) throws Exception {
		this.elementType = instance().oidc()
			.single("INSERT INTO metatypes (config_id, name) VALUES ('%s', 'Listed Element %s') RETURNING id::text"
				.formatted(CONFIG, this.suffix));
		this.flowType = instance().oidc()
			.single("INSERT INTO metatypes (config_id, name, is_connection) VALUES ('%s', 'Listed Flow %s', true) RETURNING id::text"
				.formatted(CONFIG, this.suffix));
		for (String name : new String[] { first, second, third }) {
			ObjectNode request = JsonMapper.shared().createObjectNode();
			request.putArray("entities")
				.addObject()
				.put("name", name)
				.put("externalId", "listed:" + name + ":" + this.suffix)
				.put("metatypeId", this.elementType);
			HttpResponse<String> written = instance().post(BATCH, request.toString(), instance().architect(), Map.of());
			assertThat(written.statusCode()).as(written.body()).isEqualTo(200);
			this.ids.put(name, JsonMapper.shared().readTree(written.body()).path("items").get(0).path("id").asInt());
		}
		HttpResponse<String> set = instance().send("PUT", "/api/v1/entities/" + id(child) + "/parent",
				"{\"parentEntityId\": " + id(parent) + "}", instance().architect(),
				Map.of("If-Match", "\"" + version(id(child)) + "\""));
		assertThat(set.statusCode()).as(set.body()).isEqualTo(200);
	}

	@Given("the listed connection {string} from {string} to {string}")
	public void connection(String name, String source, String target) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.put("connectionMetatypeId", this.flowType).put("name", name).put("sourceId", id(source)).put("targetId", id(target));
		HttpResponse<String> created = instance().post("/api/v1/connections", body.toString(), instance().architect(),
				Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.ids.put(name, JsonMapper.shared().readTree(created.body()).path("id").asInt());
	}

	@Given("the enterprise architect deleted the listed {string}")
	public void deleted(String name) throws Exception {
		HttpResponse<String> deletion = instance().send("DELETE", "/api/v1/entities/" + id(name), null,
				instance().architect(), Map.of("If-Match", "\"" + version(id(name)) + "\""));
		assertThat(deletion.statusCode()).as(deletion.body()).isEqualTo(200);
	}

	// When

	@When("the {role} lists the deleted entities")
	public void list(Session session) throws Exception {
		getBoth(session);
	}

	@When("the {role} lists the deleted entities with the name part {string} and the metatype of the elements")
	public void listFiltered(Session session, String part) throws Exception {
		get(session, "q=" + encode(part) + "&metatypeId=" + this.elementType);
	}

	@When("the {role} lists the deleted entities with the page size {int}")
	public void listPageSize(Session session, int size) throws Exception {
		get(session, "pageSize=" + size + "&metatypeId=" + this.elementType);
	}

	@When("the {role} lists the deleted entities with a name part of {int} characters")
	public void listNamePart(Session session, int length) throws Exception {
		get(session, "q=" + "a".repeat(length) + "&metatypeId=" + this.elementType);
	}

	@When("the {role} lists the deleted entities with the page size {int} and then asks for the next page with the name part {string}")
	public void listWithForeignCursor(Session session, int size, String part) throws Exception {
		get(session, "pageSize=" + size + "&metatypeId=" + this.elementType);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		String cursor = JsonMapper.shared().readTree(this.response.body()).path("nextCursor").asString();
		assertThat(cursor).isNotBlank();
		get(session, "pageSize=" + size + "&page=1&q=" + encode(part) + "&metatypeId=" + this.elementType + "&cursor="
				+ encode(cursor));
	}

	// Then

	@Then("{string} and {string} are listed with the enterprise architect and the deletion time")
	public void listedWithActor(String first, String second) throws Exception {
		for (String name : new String[] { first, second }) {
			JsonNode item = item(name);
			assertThat(item.path("deletedBy").path("displayName").asString()).isNotBlank();
			assertThat(item.path("deletedBy").path("principalType").asString()).isEqualTo("person");
			assertThat(item.path("deletedAt").asString()).isNotBlank();
		}
	}

	@Then("{string} names {string} as the entity it was deleted with")
	public void deletedWith(String name, String origin) throws Exception {
		assertThat(item(name).path("deletedWith").path("id").asInt()).isEqualTo(id(origin));
		assertThat(item("ERP").path("deletedWith").isNull()).isTrue();
	}

	@Then("the connection {string} is listed with the endpoints {string} and {string}")
	public void listedConnection(String name, String source, String target) throws Exception {
		JsonNode item = item(name);
		assertThat(item.path("isConnection").asBoolean()).isTrue();
		assertThat(item.path("source").path("id").asInt()).isEqualTo(id(source));
		assertThat(item.path("target").path("id").asInt()).isEqualTo(id(target));
	}

	@Then("{string} is not listed")
	public void notListed(String name) throws Exception {
		assertThat(listedIds()).doesNotContain(id(name));
	}

	@Then("only {string} is listed")
	public void onlyListed(String name) throws Exception {
		assertThat(listedIds()).containsExactly(id(name));
	}

	@Then("no entity is listed")
	public void noneListed() throws Exception {
		assertThat(listedIds()).isEmpty();
	}

	@Then("the list answer is {int}")
	public void answer(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the list answer is {int} naming {string}")
	public void answerNaming(int status, String field) throws Exception {
		answer(status);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("invalidFields").get(0).asString())
			.isEqualTo(field);
	}

	// helpers

	/** Lists the deleted elements and then the deleted connections of the scenario; stops at the first rejection. */
	private void getBoth(Session session) throws Exception {
		List<JsonNode> items = new ArrayList<>();
		for (String type : new String[] { this.elementType, this.flowType }) {
			this.response = instance().oidc().get("/api/v1/entities/deleted?pageSize=200&metatypeId=" + type, session.token());
			if (this.response.statusCode() != 200) {
				this.merged = null;
				return;
			}
			JsonMapper.shared().readTree(this.response.body()).path("items").forEach(items::add);
		}
		this.merged = items;
	}

	private void get(Session session, String query) throws Exception {
		this.merged = null;
		this.response = instance().oidc().get("/api/v1/entities/deleted?" + query, session.token());
	}

	private List<JsonNode> merged;

	private List<JsonNode> items() throws Exception {
		if (this.merged != null) {
			return this.merged;
		}
		List<JsonNode> items = new ArrayList<>();
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		JsonMapper.shared().readTree(this.response.body()).path("items").forEach(items::add);
		return items;
	}

	private JsonNode item(String name) throws Exception {
		for (JsonNode item : items()) {
			if (item.path("id").asInt() == id(name)) {
				return item;
			}
		}
		throw new AssertionError("\"" + name + "\" is not listed");
	}

	private List<Integer> listedIds() throws Exception {
		List<Integer> listed = new ArrayList<>();
		items().forEach((item) -> listed.add(item.path("id").asInt()));
		return listed;
	}

	private int id(String name) {
		Integer id = this.ids.get(name);
		assertThat(id).as("entity " + name).isNotNull();
		return id;
	}

	private int version(int id) throws Exception {
		return Integer.parseInt(instance().oidc().single("SELECT version FROM entities WHERE id = " + id));
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

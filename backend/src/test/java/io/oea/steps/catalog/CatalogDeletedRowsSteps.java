package io.oea.steps.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/catalog/catalog-deleted-rows.feature (s17-deleted-entities) on the instance of
 * {@link CatalogSteps}. The instance is shared, so every scenario has metatypes, entities and catalogs
 * of its own and queries only them; it never asserts on global counts.
 */
public class CatalogDeletedRowsSteps {

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private static final String BATCH = "/api/v1/entities/batch";

	private final String suffix = UUID.randomUUID().toString().substring(0, 8);

	private final Map<String, Integer> ids = new HashMap<>();

	private String application;

	private String iface;

	private String serves;

	private String catalog;

	private String joinId;

	private HttpResponse<String> response;

	private HttpResponse<String> first;

	// Given

	@Given("a catalog {string} of {int} active deleted-rows applications and the deleted application {string}")
	public void applications(String name, int active, String deleted) throws Exception {
		this.application = metatype("Deleted Rows Application", false);
		for (int i = 1; i <= active; i++) {
			entity(this.application, "Application %02d".formatted(i));
		}
		entity(this.application, deleted);
		delete(deleted);
		this.catalog = createCatalog(name + " " + this.suffix, this.application);
	}

	@Given("{string} is restored")
	public void restored(String name) throws Exception {
		HttpResponse<String> result = instance().send("POST", "/api/v1/entities/" + id(name) + "/undelete", null,
				instance().architect(), Map.of("If-Match", "\"" + version(id(name)) + "\""));
		assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
	}

	@Given("a connection catalog of {string} connections, and {string} deleted with {string}")
	public void connectionCatalog(String type, String connection, String endpoint) throws Exception {
		this.serves = metatype("Deleted Rows " + type, true);
		int erp = entity(this.application, endpoint);
		int billing = entity(this.application, "Billing");
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.put("connectionMetatypeId", this.serves).put("name", connection).put("sourceId", erp).put("targetId", billing);
		HttpResponse<String> created = instance().post("/api/v1/connections", body.toString(), instance().architect(),
				Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.ids.put(connection, JsonMapper.shared().readTree(created.body()).path("id").asInt());
		delete(endpoint);
		this.catalog = createCatalog("Serves " + this.suffix, this.serves);
	}

	@Given("the application {string} is connected to the interface {string}, and {string} was connected to it")
	public void joined(String active, String interfaceName, String legacy) throws Exception {
		this.iface = metatype("Deleted Rows Interface", false);
		this.serves = metatype("Deleted Rows Flow", true);
		int activeId = entity(this.application, active);
		int target = entity(this.iface, interfaceName);
		connect(activeId, target, active + " to " + interfaceName);
		// the connection of the deleted application was deleted with it
		String flow = instance().oidc()
			.single("""
					INSERT INTO entities (metatype_id, name, created_by, deleted_at, deleted_by)
					SELECT '%s', '%s', created_by, now(), created_by FROM entities WHERE id = %d RETURNING id::text"""
				.formatted(this.serves, legacy + " to " + interfaceName, id(legacy)));
		instance().oidc()
			.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (%s, %d, %d)"
				.formatted(flow, id(legacy), target));
		this.ids.put(active, activeId);
		ObjectNode join = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Interfaces")
			.put("connectionMetatypeId", this.serves)
			.put("joinDirection", "outbound")
			.put("targetMetatypeId", this.iface);
		join.putArray("targetColumns").addObject().put("field", "name");
		HttpResponse<String> added = instance().post("/api/v1/catalogs/" + this.catalog + "/joins", join.toString(),
				instance().architect(), Map.of());
		assertThat(added.statusCode()).as(added.body()).isEqualTo(201);
		this.joinId = JsonMapper.shared().readTree(added.body()).path("joinDefinitions").get(0).path("id").asString();
	}

	// When

	@When("the {role} queries the deleted-rows catalog with {string}")
	public void query(Session session, String parameters) throws Exception {
		this.response = get(session, parameters);
	}

	@When("the {role} sends its next cursor with {string}")
	public void withCursor(Session session, String parameters) throws Exception {
		this.first = this.response;
		String cursor = JsonMapper.shared().readTree(this.first.body()).path("nextCursor").asString();
		assertThat(cursor).isNotBlank();
		this.response = get(session, parameters + "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
	}

	@When("the {role} queries the deleted-rows catalog with a join in mode {string} and {string}")
	public void queryJoin(Session session, String mode, String parameters) throws Exception {
		this.response = get(session, parameters + "&joinMode%5B" + this.joinId + "%5D=" + mode);
	}

	// Then

	@Then("the deleted-rows catalog has {int} rows")
	public void rows(int count) throws Exception {
		assertThat(result().path("rows")).hasSize(count);
		assertThat(result().path("page").path("totalElements").asInt()).isEqualTo(count);
	}

	@Then("{string} is marked as deleted with its deletion time and version")
	public void marked(String name) throws Exception {
		JsonNode row = row(name);
		assertThat(row.path("deleted").asBoolean()).isTrue();
		assertThat(row.path("deletedAt").asString()).isNotBlank();
		assertThat(row.path("version").asInt()).isEqualTo(version(id(name)));
	}

	@Then("the other rows carry no deletion members")
	public void unmarked() throws Exception {
		for (JsonNode row : result().path("rows")) {
			if (!"Legacy CRM".equals(row.path("values").path("name").asString()) || !row.has("deleted")) {
				assertThat(row.has("deleted")).isFalse();
				assertThat(row.has("deletedAt")).isFalse();
				assertThat(row.has("version")).isFalse();
			}
		}
	}

	@Then("{string} is not a row of the deleted-rows catalog")
	public void notARow(String name) throws Exception {
		for (JsonNode row : result().path("rows")) {
			assertThat(row.path("values").path("name").asString()).isNotEqualTo(name);
		}
	}

	@Then("the deleted-rows answer is {int}")
	public void answer(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the deleted-rows answer is {int} naming {string}")
	public void answerNaming(int status, String field) throws Exception {
		answer(status);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("invalidFields").get(0).asString())
			.isEqualTo(field);
	}

	@Then("the connection {string} is a row marked as deleted whose source column shows {string}")
	public void connectionRow(String name, String source) throws Exception {
		JsonNode row = row(name);
		assertThat(row.path("deleted").asBoolean()).isTrue();
		assertThat(row.path("values").path("source.name").asString()).isEqualTo(source);
	}

	@Then("{string} is one row without join results")
	public void withoutJoinResults(String name) throws Exception {
		int count = 0;
		for (JsonNode row : result().path("rows")) {
			if (name.equals(row.path("values").path("name").asString())) {
				count++;
				assertThat(row.path("deleted").asBoolean()).isTrue();
				assertThat(row.path("joinResults").path(this.joinId)).isEmpty();
			}
		}
		assertThat(count).isEqualTo(1);
	}

	// helpers

	private HttpResponse<String> get(Session session, String parameters) throws Exception {
		String query = parameters.isEmpty() ? "" : "?" + parameters;
		return instance().oidc().get("/api/v1/catalogs/" + this.catalog + "/query" + query, session.token());
	}

	private JsonNode result() throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(this.response.body());
	}

	private JsonNode row(String name) throws Exception {
		for (JsonNode row : result().path("rows")) {
			if (name.equals(row.path("values").path("name").asString())) {
				return row;
			}
		}
		throw new AssertionError("\"" + name + "\" is not a row");
	}

	private String metatype(String name, boolean connection) throws Exception {
		return instance().oidc()
			.single("INSERT INTO metatypes (config_id, name, is_connection) VALUES ('%s', '%s %s', %b) RETURNING id::text"
				.formatted(CONFIG, name, this.suffix, connection));
	}

	private int entity(String metatype, String name) throws Exception {
		ObjectNode request = JsonMapper.shared().createObjectNode();
		request.putArray("entities")
			.addObject()
			.put("name", name)
			.put("externalId", "rows:" + name + ":" + this.suffix)
			.put("metatypeId", metatype);
		HttpResponse<String> written = instance().post(BATCH, request.toString(), instance().architect(), Map.of());
		assertThat(written.statusCode()).as(written.body()).isEqualTo(200);
		int id = JsonMapper.shared().readTree(written.body()).path("items").get(0).path("id").asInt();
		this.ids.put(name, id);
		return id;
	}

	private void connect(int source, int target, String name) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.put("connectionMetatypeId", this.serves).put("name", name).put("sourceId", source).put("targetId", target);
		HttpResponse<String> created = instance().post("/api/v1/connections", body.toString(), instance().architect(),
				Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.ids.put(name, JsonMapper.shared().readTree(created.body()).path("id").asInt());
	}

	private void delete(String name) throws Exception {
		HttpResponse<String> deletion = instance().send("DELETE", "/api/v1/entities/" + id(name), null,
				instance().architect(), Map.of("If-Match", "\"" + version(id(name)) + "\""));
		assertThat(deletion.statusCode()).as(deletion.body()).isEqualTo(200);
	}

	private String createCatalog(String name, String primary) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("primaryMetatypeId", primary)
			.put("scope", "instance");
		HttpResponse<String> created = instance().post("/api/v1/catalogs", body.toString(), instance().architect(),
				Map.of());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		return JsonMapper.shared().readTree(created.body()).path("id").asString();
	}

	private int id(String name) {
		Integer id = this.ids.get(name);
		assertThat(id).as("entity " + name).isNotNull();
		return id;
	}

	private int version(int id) throws Exception {
		return Integer.parseInt(instance().oidc().single("SELECT version FROM entities WHERE id = " + id));
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

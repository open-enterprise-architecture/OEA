package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.metamodel.TestPackages;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for the setting "Show parallel changes in the baseline" in
 * features/metamodel/metatype-editing.feature and features/metamodel/lock-mode.feature
 * (us-044-conflict-warning D3). Every scenario uses its own metatypes; a scenario that turns the
 * setting on for a shared metatype turns it off afterwards.
 */
public class ParallelChangeSteps {

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private final String suffix = "pc" + UUID.randomUUID().toString().substring(0, 8);

	private final Map<String, String> metatypes = new HashMap<>();

	private final Map<String, Integer> versions = new HashMap<>();

	private String shared;

	private HttpResponse<String> response;

	private String baselineEntry;

	@After
	public void reset() throws Exception {
		if (this.shared != null) {
			instance().oidc()
				.update("UPDATE metatypes SET show_parallel_changes = false WHERE id = '" + this.shared + "'");
		}
	}

	@Given("the setting of the shared metatype {string} is off")
	public void sharedOff(String name) throws Exception {
		this.shared = instance().oidc().single("SELECT id FROM metatypes WHERE name = '" + name + "'");
		instance().oidc().update("UPDATE metatypes SET show_parallel_changes = false WHERE id = '" + this.shared + "'");
		this.metatypes.put(name, this.shared);
		this.versions.put(name, version(name));
	}

	@Given("the organization metatype {string}")
	public void organizationMetatype(String name) throws Exception {
		HttpResponse<String> created = instance().send("POST", "/api/v1/metamodel/metatypes",
				JsonMapper.shared().createObjectNode().put("name", name + " " + this.suffix).put("configId", CONFIG)
					.toString(),
				instance().metamodelAdmin());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.metatypes.put(name, JsonMapper.shared().readTree(created.body()).path("id").asString());
		this.versions.put(name, version(name));
	}

	@When("the metamodel administrator turns on parallel changes in the baseline for {string}")
	@Given("the metamodel administrator turned on parallel changes in the baseline for {string}")
	public void turnOn(String name) throws Exception {
		this.response = set(name, version(name), instance().metamodelAdmin());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@When("the architect turns on parallel changes in the baseline for {string}")
	public void architectTurnsOn(String name) throws Exception {
		this.response = set(name, version(name), instance().architect());
	}

	@When("the metamodel administrator turns on parallel changes in the baseline for {string} with an outdated version")
	public void outdated(String name) throws Exception {
		this.response = set(name, version(name) + 1, instance().metamodelAdmin());
	}

	@Then("{string} shows parallel changes in the baseline with a version raised by 1")
	public void shown(String name) throws Exception {
		JsonNode metatype = metatype(name);
		assertThat(metatype.path("showParallelChanges").asBoolean()).isTrue();
		assertThat(metatype.path("version").asInt()).isEqualTo(this.versions.get(name) + 1);
	}

	@Then("{string} does not show parallel changes in the baseline")
	public void notShown(String name) throws Exception {
		assertThat(metatype(name).path("showParallelChanges").asBoolean(true)).isFalse();
	}

	@Then("the metatype {string} does not show parallel changes in the baseline")
	public void createdNotShown(String name) throws Exception {
		// the editing steps add a scenario suffix to the name
		String id = instance().oidc().single("SELECT id FROM metatypes WHERE name LIKE '" + name + "%' LIMIT 1");
		this.metatypes.put(name, id);
		notShown(name);
	}

	@Then("the setting is rejected with {int} and {string} is unchanged")
	public void rejected(int status, String name) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		notShown(name);
		assertThat(version(name)).isEqualTo(this.versions.get(name));
	}

	@Then("the metamodel changes list the setting of {string} under the kind {string} by the metamodel administrator")
	public void listed(String name, String kind) throws Exception {
		HttpResponse<String> changes = instance().oidc()
			.get("/api/v1/metamodel/changes?kind=" + kind, instance().metamodelAdmin().token());
		JsonNode first = JsonMapper.shared().readTree(changes.body()).path("items").get(0);
		assertThat(first.path("eventType").asString()).isEqualTo("metamodel.metatype-parallel-changes-changed");
		assertThat(first.path("targetId").asString()).isEqualTo(this.metatypes.get(name));
		HttpResponse<String> me = instance().send("GET", "/api/v1/me", null, instance().metamodelAdmin());
		assertThat(first.path("actor").path("actorRef").asString())
			.isEqualTo(JsonMapper.shared().readTree(me.body()).path("principal").path("id").asString());
	}

	@Given("the package {string} with one metatype")
	public void imported(String packageId) throws Exception {
		importPackage(TestPackages.pkg(packageId + this.suffix, 1).toString());
		String name = packageId + this.suffix + " Type 1";
		this.metatypes.put(packageId, instance().oidc().single("SELECT id FROM metatypes WHERE name = '" + name + "'"));
		this.versions.put(packageId, version(packageId));
	}

	@When("the metamodel administrator imports the package {string} again")
	public void reimport(String packageId) throws Exception {
		importPackage(TestPackages.pkg(packageId + this.suffix, 1).put("packageVersion", "1.1.0").toString());
	}

	@Then("{string} still shows parallel changes in the baseline")
	public void stillShown(String name) throws Exception {
		assertThat(metatype(name).path("showParallelChanges").asBoolean()).isTrue();
	}

	@Given("the subtype {string} of {string} with the entity {string} that another solution retires")
	public void subtype(String name, String base, String entity) throws Exception {
		String id = instance().oidc().single("""
				INSERT INTO metatypes (config_id, name, base_metatype_id) VALUES ('%s', '%s %s', '%s') RETURNING id"""
			.formatted(CONFIG, name, this.suffix, this.metatypes.get(base)));
		this.metatypes.put(name, id);
		String entityId = instance().oidc().single("""
				INSERT INTO entities (metatype_id, name, created_by) SELECT '%s', '%s %s', min(id) FROM persons
				RETURNING id""".formatted(id, entity, this.suffix));
		String other = solution("Other");
		HttpResponse<String> retired = instance().send("POST", "/api/v1/solutions/" + other + "/deltas",
				JsonMapper.shared().createObjectNode().put("deltaType", "retiring").put("entityId",
						Integer.parseInt(entityId)).toString(),
				instance().sharedLogin("dev-architect-metamodel"));
		assertThat(retired.statusCode()).as(retired.body()).isEqualTo(201);
	}

	@When("the architect reads the entry {string} in the baseline of a solution")
	public void readBaseline(String entity) throws Exception {
		HttpResponse<String> page = instance().oidc()
			.get("/api/v1/solutions/" + solution("Mine") + "/landscape?q=" + this.suffix, instance().architect().token());
		assertThat(page.statusCode()).as(page.body()).isEqualTo(200);
		for (JsonNode item : JsonMapper.shared().readTree(page.body()).path("items")) {
			if (item.path("name").asString().equals(entity + " " + this.suffix)) {
				this.baselineEntry = item.path("parallelChanges").toString();
			}
		}
	}

	@Then("the entry names no other solution")
	public void noOther() {
		assertThat(this.baselineEntry).isEqualTo("[]");
	}

	private String solution(String prefix) throws Exception {
		HttpResponse<String> created = instance().send("POST", "/api/v1/solutions",
				JsonMapper.shared().createObjectNode().put("name", prefix + " " + this.suffix).toString(),
				prefix.equals("Other") ? instance().sharedLogin("dev-architect-metamodel") : instance().architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		return JsonMapper.shared().readTree(created.body()).path("id").asString();
	}

	private void importPackage(String body) throws Exception {
		HttpResponse<String> imported = instance().post("/api/v1/admin/metamodel/import", body,
				instance().metamodelAdmin(), Map.of());
		assertThat(imported.statusCode()).as(imported.body()).isEqualTo(200);
	}

	private HttpResponse<String> set(String name, int version, Session session) throws Exception {
		return instance().send("PUT", "/api/v1/metamodel/metatypes/" + this.metatypes.get(name) + "/parallel-changes",
				JsonMapper.shared().createObjectNode().put("showInBaseline", true).toString(), session,
				Map.of("If-Match", "\"" + version + "\""));
	}

	private JsonNode metatype(String name) throws Exception {
		HttpResponse<String> read = instance().oidc()
			.get("/api/v1/metamodel/metatypes/" + this.metatypes.get(name), instance().metamodelAdmin().token());
		assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(read.body());
	}

	private int version(String name) throws Exception {
		return Integer.parseInt(
				instance().oidc().single("SELECT version FROM metatypes WHERE id = '" + this.metatypes.get(name) + "'"));
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

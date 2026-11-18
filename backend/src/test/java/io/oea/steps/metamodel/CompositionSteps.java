package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for the composition scenarios of features/metamodel/connection-metatypes.feature
 * (us-135-component-hierarchy D4), on the instance of {@link MetatypeEditingSteps}. The starter
 * package marks its metatype "Composition"; a scenario that needs no mark removes it and the
 * hook gives it back, because at most one metatype of the instance may be marked.
 */
public class CompositionSteps {

	private static final String METATYPES = "/api/v1/metamodel/metatypes";

	private final String suffix = " " + UUID.randomUUID().toString().substring(0, 8);

	private String created;

	private HttpResponse<String> response;

	@After("@composition")
	public void restoreTheStarterMark() throws Exception {
		instance().oidc()
			.update("UPDATE metatypes SET is_composition = (name = 'Composition' AND source_package_id IS NOT NULL)");
	}

	@Given("no connection metatype is marked as composition")
	public void unmarked() throws Exception {
		instance().oidc().update("UPDATE metatypes SET is_composition = false");
	}

	@Given("the starter package has marked its connection metatype {string} as composition")
	public void starterMarked(String name) throws Exception {
		assertThat(instance().oidc().single("SELECT id::text FROM metatypes WHERE is_composition AND name = '" + name + "'"))
			.isNotNull();
	}

	@When("the metamodel administrator creates the connection metatype {string} and marks it as composition")
	public void createMarked(String name) throws Exception {
		this.response = write("POST", METATYPES, name + this.suffix, true, true, null);
		this.created = (this.response.statusCode() == 201) ? JsonMapper.shared().readTree(this.response.body()).path("id").asString()
				: null;
	}

	@When("the metamodel administrator creates the connection metatype {string} without a mark and marks it as composition")
	public void createThenMark(String name) throws Exception {
		HttpResponse<String> plain = write("POST", METATYPES, name + this.suffix, true, false, null);
		assertThat(plain.statusCode()).as(plain.body()).isEqualTo(201);
		this.created = JsonMapper.shared().readTree(plain.body()).path("id").asString();
		this.response = write("PUT", METATYPES + "/" + this.created, name + this.suffix, true, true, this.created);
	}

	// an organization metatype: an imported one is read-only and answers 409 before the mark is checked
	@When("the metamodel administrator creates the element metatype {string} and marks it as composition")
	public void createElementMarked(String name) throws Exception {
		this.response = write("POST", METATYPES, name + this.suffix, false, true, null);
	}

	@Then("the metatype {string} is marked as composition and the change is audited")
	public void markedAndAudited(String name) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isIn(200, 201);
		JsonNode read = JsonMapper.shared()
			.readTree(instance().send("GET", METATYPES + "/" + this.created, null, instance().architect()).body());
		assertThat(read.path("isComposition").asBoolean()).isTrue();
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type LIKE 'metamodel.metatype-%' AND target_id = '"
					+ this.created + "' AND details::text LIKE '%isComposition%'")).isEqualTo("1");
	}

	@Then("the composition mark is rejected with 422 naming {string}")
	public void rejected(String field) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		List<String> fields = new ArrayList<>();
		JsonMapper.shared().readTree(this.response.body()).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).contains(field);
	}

	@Then("the connection metatype {string} is marked as composition and allows every element metatype except {string}")
	public void starterComposition(String name, String excluded) throws Exception {
		String id = instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name + "'");
		JsonNode metatype = JsonMapper.shared()
			.readTree(instance().send("GET", METATYPES + "/" + id, null, instance().architect()).body());
		assertThat(metatype.path("isComposition").asBoolean()).isTrue();
		assertThat(metatype.path("connectionConstraints").size()).isZero();
		String application = instance().oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'");
		String aid = instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + excluded + "'");
		String allowed = "/api/v1/metamodel/allowed-connections?sourceMetatypeId=%s&targetMetatypeId=%s";
		JsonNode withApplications = JsonMapper.shared()
			.readTree(instance().send("GET", allowed.formatted(application, application), null, instance().architect()).body());
		assertThat(withApplications.path("options").toString()).contains(id);
		JsonNode withAid = JsonMapper.shared()
			.readTree(instance().send("GET", allowed.formatted(aid, application), null, instance().architect()).body());
		assertThat(withAid.path("allowed").asBoolean()).isFalse();
	}

	private HttpResponse<String> write(String method, String path, String name, boolean connection, boolean composition,
			String id) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("configId", "00000000-0000-0000-0000-000000000001")
			.put("isConnection", connection)
			.put("isComposition", composition);
		return instance().send(method, path, body.toString(), instance().metamodelAdmin());
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

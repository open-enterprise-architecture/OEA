package io.oea.steps.solution;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.metamodel.MetatypeEditingSteps;
import io.oea.steps.metamodel.PropertyVisibilitySteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/solution/solution-creation.feature and solution-overview.feature
 * (us-038-create-solution D1–D3), on the instance of the metamodel features. The architect
 * (`dev-architect`, `enterprise-architect`) holds `solution:write`; the viewer does not. The
 * features run once per instance, so the names carry a run suffix where they must be unique.
 */
public class SolutionSteps {

	private static final String SOLUTIONS = "/api/v1/solutions";

	private static Session portalReader;

	private HttpResponse<String> response;

	@When("the architect creates the solution {string} with the vision {string}")
	public void createWithVision(String name, String vision) throws Exception {
		this.response = create(JsonMapper.shared().createObjectNode().put("name", name).put("description", vision),
				instance().architect());
	}

	@When("the architect creates the solution {string}")
	public void create(String name) throws Exception {
		this.response = create(JsonMapper.shared().createObjectNode().put("name", name), instance().architect());
	}

	@Given("the architect created the solution {string}")
	public void created(String name) throws Exception {
		create(name);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@When("the viewer creates the solution {string}")
	public void viewerCreates(String name) throws Exception {
		this.response = create(JsonMapper.shared().createObjectNode().put("name", name), instance().viewer());
	}

	@When("the architect creates a solution with a name of {int} characters and a vision of {int} characters")
	public void createWithLengths(int name, int vision) throws Exception {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", prefix + "n".repeat(name - prefix.length()));
		if (vision > 0) {
			body.put("description", "v".repeat(vision));
		}
		this.response = create(body, instance().architect());
	}

	@Then("the solution {string} is a draft owned by the architect without plateaus")
	public void draft(String name) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
		JsonNode solution = JsonMapper.shared().readTree(this.response.body());
		assertThat(solution.path("name").asString()).isEqualTo(name);
		assertThat(solution.path("status").asString()).isEqualTo("draft");
		assertThat(solution.path("fromPlateauId").isNull()).isTrue();
		assertThat(solution.path("toPlateauId").isNull()).isTrue();
		assertThat(solution.path("ownerName").asString()).isEqualTo("dev-architect");
		assertThat(solution.path("createdBy").path("id").asLong()).isEqualTo(solution.path("ownerId").asLong());
	}

	@Then("its creation is audited as {string}")
	public void audited(String eventType) throws Exception {
		String id = JsonMapper.shared().readTree(this.response.body()).path("id").asString();
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '" + eventType + "' AND target_id = '" + id
					+ "'"))
			.isEqualTo("1");
	}

	@Then("the creation is rejected with 422 naming {string} and the detail {string}")
	public void rejectedWithDetail(String field, String detail) throws Exception {
		rejected(field);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("detail").asString()).isEqualTo(detail);
	}

	@Then("the creation is rejected with 422 naming {string}")
	public void rejected(String field) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("invalidFields").toString())
			.isEqualTo("[\"" + field + "\"]");
	}

	@Then("exactly one solution is named {string}")
	public void exactlyOne(String name) throws Exception {
		assertThat(count(name)).isEqualTo("1");
	}

	@Then("no solution is named {string}")
	public void none(String name) throws Exception {
		assertThat(count(name)).isEqualTo("0");
	}

	@Then("the response status is {int}")
	public void status(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@When("the authorization roles are listed")
	public void listRoles() throws Exception {
		this.response = instance().oidc().get("/api/v1/admin/roles", PropertyVisibilitySteps.securityAdmin().token());
	}

	@Then("{string} holds exactly {string} and is not privileged")
	public void holdsExactly(String role, String permissions) throws Exception {
		JsonNode entry = role(role);
		assertThat(permissions(entry)).containsExactlyInAnyOrderElementsOf(Arrays.asList(permissions.split(", ")));
		assertThat(entry.path("privileged").asBoolean()).isFalse();
	}

	@Then("{string} holds {string}")
	public void holds(String role, String permission) throws Exception {
		assertThat(permissions(role(role))).contains(permission);
	}

	@When("the viewer lists the solutions")
	public void viewerLists() throws Exception {
		this.response = instance().oidc().get(SOLUTIONS, instance().viewer().token());
	}

	@When("a portal reader lists the solutions")
	public void portalReaderLists() throws Exception {
		this.response = instance().oidc().get(SOLUTIONS, portalReaderSession().token());
	}

	@When("the viewer reads a solution with an unknown ID")
	public void unknown() throws Exception {
		this.response = instance().oidc().get(SOLUTIONS + "/" + UUID.randomUUID(), instance().viewer().token());
	}

	@Then("{string} is listed before {string} with status {string} and the owner's name")
	public void listedBefore(String first, String second, String status) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		List<String> names = new ArrayList<>();
		for (JsonNode solution : JsonMapper.shared().readTree(this.response.body())) {
			names.add(solution.path("name").asString());
			if (solution.path("name").asString().equals(first)) {
				assertThat(solution.path("status").asString()).isEqualTo(status);
				assertThat(solution.path("ownerName").asString()).isNotBlank();
			}
		}
		assertThat(names).containsSubsequence(first, second);
	}

	private HttpResponse<String> create(ObjectNode body, Session session) throws Exception {
		return instance().send("POST", SOLUTIONS, body.toString(), session);
	}

	private JsonNode role(String id) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		for (JsonNode role : JsonMapper.shared().readTree(this.response.body())) {
			if (role.path("id").asString().equals(id)) {
				return role;
			}
		}
		throw new AssertionError("not listed: " + id);
	}

	private static List<String> permissions(JsonNode role) {
		List<String> permissions = new ArrayList<>();
		role.path("permissions").forEach((permission) -> permissions.add(permission.asString()));
		return permissions;
	}

	private static String count(String name) throws Exception {
		return instance().oidc()
			.single("SELECT count(*) FROM solutions WHERE lower(name) = lower('" + name.replace("'", "''") + "')");
	}

	/** The session of `dev-portal-reader`, mapped to `portal-reader` (no `entity:read`). */
	public static Session portalReaderSession() throws Exception {
		synchronized (SolutionSteps.class) {
			if (portalReader == null) {
				instance().oidc().mapping("oea-portal-readers", "portal-reader");
				portalReader = instance().login("dev-portal-reader");
			}
			return portalReader;
		}
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

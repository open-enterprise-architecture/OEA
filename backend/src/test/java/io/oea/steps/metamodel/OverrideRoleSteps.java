package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/identity/authorization-roles.feature and
 * features/metamodel/visibility-override.feature (us-128-override-role-list D1, D4), on the
 * instance of the metamodel features; the security administrator is `dev-security-admin`.
 */
public class OverrideRoleSteps {

	private static final String ROLES = "/api/v1/admin/roles";

	private static final String OVERRIDE = "/api/v1/admin/property-visibility/override-roles";

	private HttpResponse<String> response;

	private boolean changedOverride;

	@After
	public void resetOverride() throws Exception {
		if (this.changedOverride) {
			instance().oidc().update("UPDATE authorization_roles SET overrides_property_visibility = false");
		}
	}

	@When("the security administrator creates the role {string} named {string} without permissions")
	public void createWithoutPermissions(String id, String name) throws Exception {
		ObjectNode role = JsonMapper.shared().createObjectNode().put("id", id).put("name", name);
		role.putArray("permissions");
		this.response = instance().send("POST", ROLES, role.toString(), PropertyVisibilitySteps.securityAdmin());
	}

	@When("the security administrator creates the role {string} with the permissions {string} and {string}")
	public void createWithPermissions(String id, String first, String second) throws Exception {
		ObjectNode role = JsonMapper.shared().createObjectNode().put("id", id).put("name", id);
		role.putArray("permissions").add(first).add(second);
		this.response = instance().send("POST", ROLES, role.toString(), PropertyVisibilitySteps.securityAdmin());
	}

	@Then("the role {string} is listed without permissions and its creation is audited")
	public void listed(String id) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
		HttpResponse<String> list = instance().oidc().get(ROLES, PropertyVisibilitySteps.securityAdmin().token());
		JsonNode role = null;
		for (JsonNode entry : JsonMapper.shared().readTree(list.body())) {
			if (entry.path("id").asString().equals(id)) {
				role = entry;
			}
		}
		assertThat(role).as("role %s listed", id).isNotNull();
		assertThat(role.path("isBuiltin").asBoolean()).isFalse();
		assertThat(role.path("permissions")).isEmpty();
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = 'role.created' AND target_id = '" + id + "'"))
			.isEqualTo("1");
	}

	@Then("the role is rejected with 422 and {string} does not exist")
	public void rejected(String id) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		assertThat(instance().oidc().single("SELECT count(*) FROM authorization_roles WHERE id = '" + id + "'"))
			.isEqualTo("0");
	}

	@Given("the custom role {string}")
	public void customRole(String id) throws Exception {
		instance().oidc().update("INSERT INTO authorization_roles (id, name) VALUES ('%1$s', '%1$s') ON CONFLICT DO NOTHING"
			.formatted(id));
	}

	@When("the security administrator sets the override role list to {string}")
	public void setBySecurityAdministrator(String role) throws Exception {
		this.changedOverride = true;
		this.response = instance().send("PUT", OVERRIDE, list(role), PropertyVisibilitySteps.securityAdmin());
	}

	@When("the metamodel administrator sets the override role list to {string}")
	public void setByMetamodelAdministrator(String role) throws Exception {
		this.changedOverride = true;
		this.response = instance().send("PUT", OVERRIDE, list(role), instance().metamodelAdmin());
	}

	@Then("the override role list contains {string} and the change is audited")
	public void contains(String role) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		HttpResponse<String> read = instance().oidc().get(OVERRIDE, PropertyVisibilitySteps.securityAdmin().token());
		assertThat(JsonMapper.shared().readTree(read.body()).path("roles").toString()).isEqualTo("[\"" + role + "\"]");
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = 'visibility.override-changed' AND details ->> 'newRoles' LIKE '%"
					+ role + "%'"))
			.isNotEqualTo("0");
	}

	@Then("the change of the override role list is rejected with 403")
	public void forbidden() throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(403);
		assertThat(instance().oidc().single("SELECT count(*) FROM authorization_roles WHERE overrides_property_visibility"))
			.isEqualTo("0");
	}

	private static String list(String role) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("roles").add(role);
		return body.toString();
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

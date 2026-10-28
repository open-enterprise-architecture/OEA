package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.auth.DevRealm;
import io.oea.testsupport.auth.KeycloakAdmin;
import io.oea.testsupport.auth.OidcTestInstance;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/idp-roles.feature; instance and login come from
 * {@link OidcLoginSteps} of the same scenario.
 */
public class IdpRolesSteps {

	private final OidcLoginSteps oidc;

	private final Map<String, UUID> mappings = new HashMap<>();

	/** Realm group memberships removed by the scenario, restored afterwards (shared container). */
	private final List<String[]> removedMemberships = new ArrayList<>();

	private HttpResponse<String> response;

	public IdpRolesSteps(OidcLoginSteps oidc) {
		this.oidc = oidc;
	}

	@Given("the realm group {string} is mapped to the role {string}")
	public void mapped(String group, String role) throws Exception {
		this.mappings.put(group, instance().mapping(group, role));
	}

	@Given("the realm group {string} is mapped to the role {string} only for another issuer")
	public void mappedForAnotherIssuer(String group, String role) throws Exception {
		String issuer = "http://localhost:8180/realms/other";
		UUID other = instance().provider("Other realm", issuer, true);
		instance().mapping(other, issuer, DevRealm.groupId(group), role);
	}

	@Given("the setup phase of the instance has ended")
	public void setupPhaseEnded() throws Exception {
		instance().update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'idp-group-mapping')""");
	}

	@When("{string} is removed from the realm group {string}")
	public void removedFromGroup(String username, String group) {
		KeycloakAdmin.removeFromGroup(username, group);
		this.removedMemberships.add(new String[] { username, group });
	}

	@When("the IdP ends all sessions of {string}")
	public void idpLogout(String username) {
		KeycloakAdmin.logout(username);
	}

	@When("the provider cannot be reached")
	public void providerUnreachable() throws Exception {
		instance().update("UPDATE auth_providers SET issuer = 'http://127.0.0.1:9/realms/oea-dev' WHERE id = '"
				+ instance().providerId() + "'");
	}

	@When("15 minutes have passed since the last evaluation")
	public void evaluationDue() throws Exception {
		// the IdP's tokens follow real time, so the evaluation time is moved back instead of the clock
		instance().update("UPDATE sessions SET idp_claims_evaluated_at = idp_claims_evaluated_at - interval "
				+ "'16 minutes' WHERE " + sessionWhere());
	}

	@When("the mapping of {string} is deleted")
	public void mappingDeleted(String group) throws Exception {
		instance().update("UPDATE idp_group_mappings SET deleted_at = now() WHERE id = '" + this.mappings.get(group)
				+ "'");
	}

	@When("61 seconds have passed")
	public void minutePassed() {
		instance().clock().advance(Duration.ofSeconds(61));
	}

	@When("the IdP refresh token of the session has expired")
	public void refreshTokenExpired() throws Exception {
		instance().update("UPDATE sessions SET idp_token_expires_at = now() - interval '1 minute' WHERE "
				+ sessionWhere());
	}

	@When("the person maps the realm group {string} to the role {string}")
	public void personMaps(String group, String role) throws Exception {
		JsonNode me = me();
		String body = JsonMapper.shared()
			.createObjectNode()
			.put("authProviderId", instance().providerId().toString())
			.put("groupIdentifier", DevRealm.groupId(group))
			.put("roleId", role)
			.toString();
		this.response = instance().send(HttpRequest
			.newBuilder(URI.create(instance().url() + "/api/v1/admin/idp-group-mappings"))
			.header("Cookie", "__Host-OEA_SESSION=" + this.oidc.lastLogin().sessionToken())
			.header("X-XSRF-TOKEN", me.path("csrfToken").asString())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	@Then("the person holds the role {string} from the IdP, recorded with the session")
	public void holdsRole(String role) throws Exception {
		JsonNode grant = me().path("roles").get(0);
		assertThat(grant.path("role").path("id").asString()).isEqualTo(role);
		assertThat(grant.path("source").asString()).isEqualTo("idp");
		assertThat(grant.path("effective").asBoolean()).isTrue();
		assertThat(instance().single("SELECT role_id FROM session_idp_roles r JOIN sessions s ON s.id = r.session_id "
				+ "WHERE " + sessionWhere())).isEqualTo(role);
	}

	@Then("only the permissions of effective roles are listed")
	public void onlyEffectivePermissions() throws Exception {
		JsonNode me = me();
		List<String> effectiveRoles = me.path("roles")
			.valueStream()
			.filter((grant) -> grant.path("effective").asBoolean())
			.map((grant) -> grant.path("role").path("id").asString())
			.toList();
		assertThat(me.path("permissions")).isNotEmpty().allSatisfy((permission) -> assertThat(permission
			.path("grantedBy")
			.valueStream()
			.map((grantedBy) -> grantedBy.path("roleId").asString())).isSubsetOf(effectiveRoles));
	}

	@Then("the person holds no role and no permission")
	public void noRole() throws Exception {
		JsonNode me = me();
		assertThat(me.path("roles")).isEmpty();
		assertThat(me.path("permissions")).isEmpty();
	}

	@Then("the session records the groups claim as {string}")
	public void claimStatus(String status) throws Exception {
		assertThat(instance().single("SELECT idp_groups_claim_status FROM sessions WHERE " + sessionWhere()))
			.isEqualTo(status);
	}

	@Then("the role {string} is listed as not effective with the reason {string}")
	public void notEffective(String role, String reason) throws Exception {
		JsonNode grant = me().path("roles").get(0);
		assertThat(grant.path("role").path("id").asString()).isEqualTo(role);
		assertThat(grant.path("effective").asBoolean()).isFalse();
		assertThat(grant.path("notEffectiveReason").asString()).isEqualTo(reason);
	}

	@Then("a request needing {string} is rejected with 403")
	public void rejectedWithout(String permission) throws Exception {
		// GET /admin/permissions requires role:assign (contract)
		assertThat(permission).isEqualTo("role:assign");
		assertThat(instance().get("/api/v1/admin/permissions", this.oidc.lastLogin().sessionToken()).statusCode())
			.isEqualTo(403);
	}

	@Then("the role {string} is effective and the session is second-factor-verified")
	public void effective(String role) throws Exception {
		JsonNode me = me();
		assertThat(me.path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(me.path("roles").get(0).path("role").path("id").asString()).isEqualTo(role);
		assertThat(me.path("roles").get(0).path("effective").asBoolean()).isTrue();
	}

	@Then("the session is rejected with 401 and ended with the reason {string}")
	public void sessionEnded(String reason) throws Exception {
		assertThat(instance().get("/api/v1/me", this.oidc.lastLogin().sessionToken()).statusCode()).isEqualTo(401);
		assertThat(instance().single("SELECT revoked_reason FROM sessions WHERE " + sessionWhere())).isEqualTo(reason);
		assertThat(instance().audit("session.revoked", reason)).isPositive();
	}

	@Then("the request is rejected with 403 and the reason {string}")
	public void rejectedWithReason(String reason) {
		assertThat(this.response.statusCode()).isEqualTo(403);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("reason").asString()).isEqualTo(reason);
	}

	@Then("listing the IdP group mappings is answered with {int}")
	public void listMappings(int status) throws Exception {
		// the fixture mappings have no creator; the listing shows one
		instance().update("UPDATE idp_group_mappings SET created_by_person_id = (SELECT min(id) FROM persons)");
		assertThat(instance().get("/api/v1/admin/idp-group-mappings", this.oidc.lastLogin().sessionToken())
			.statusCode()).isEqualTo(status);
	}

	@After(value = "@oidc", order = 15000)
	public void restoreRealm() {
		for (String[] membership : this.removedMemberships) {
			KeycloakAdmin.addToGroup(membership[0], membership[1]);
		}
	}

	private JsonNode me() throws Exception {
		HttpResponse<String> me = instance().get("/api/v1/me", this.oidc.lastLogin().sessionToken());
		assertThat(me.statusCode()).isEqualTo(200);
		return JsonMapper.shared().readTree(me.body());
	}

	private String sessionWhere() {
		return "token_hash = sha256(convert_to('" + this.oidc.lastLogin().sessionToken() + "', 'UTF8'))";
	}

	private OidcTestInstance instance() {
		return this.oidc.oidcInstance();
	}

}

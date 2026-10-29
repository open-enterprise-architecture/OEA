package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.app.access.RoleAssignmentExpiry;
import io.oea.app.security.Sessions;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/role-assignments.feature, person-lifecycle.feature and the SoD-7
 * scenarios of access-administration.feature; the instance and the system admin session come from
 * {@link LocalLoginSteps} of the same scenario. Persons get sessions as the OIDC login stores
 * them.
 */
public class RoleAssignmentSteps {

	private final LocalLoginSteps instance;

	private final Map<String, Long> persons = new HashMap<>();

	private final Map<String, String> sessions = new HashMap<>();

	/** Assignment IDs by person and role. */
	private final Map<String, String> assignments = new HashMap<>();

	private String lastAssignment;

	public RoleAssignmentSteps(LocalLoginSteps instance) {
		this.instance = instance;
	}

	@Given("an active person {string} without any role has a session")
	public void person(String name) throws Exception {
		this.persons.put(name, Long.parseLong(this.instance.single("INSERT INTO persons (name, email, status) VALUES ('"
				+ name + "', '" + name.toLowerCase() + "-" + UUID.randomUUID() + "@example.org', 'active') RETURNING id::text")));
		this.sessions.put(name, session(name, false));
	}

	@Given("an active person {string} holding {string} has a session confirmed with a second factor")
	public void personWithRole(String name, String role) throws Exception {
		person(name);
		systemAdminAssigns(role, name);
		this.sessions.put(name, session(name, true));
	}

	@When("the system admin assigns {string} to {string} until the end of her contract with the reason {string}")
	public void assignUntil(String role, String name, String reason) throws Exception {
		this.instance.response = assign(null, name, "{\"roleId\":\"" + role + "\",\"validTo\":\""
				+ Instant.now().plus(180, ChronoUnit.DAYS) + "\",\"reason\":\"" + reason + "\"}");
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(201);
		this.lastAssignment = LocalLoginSteps.json(this.instance.response).path("id").asString();
	}

	@Given("the system admin has assigned {string} to {string}")
	@When("the system admin assigns {string} to {string}")
	public void systemAdminAssigns(String role, String name) throws Exception {
		HttpResponse<String> response = assign(null, name, "{\"roleId\":\"" + role + "\"}");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		this.assignments.put(name + "/" + role, LocalLoginSteps.json(response).path("id").asString());
	}

	@Given("the system admin has assigned {string} and {string} to {string}")
	public void systemAdminAssignsTwo(String first, String second, String name) throws Exception {
		systemAdminAssigns(first, name);
		systemAdminAssigns(second, name);
	}

	@Given("the system admin has assigned {string} to {string} until a time that passed more than 60 seconds ago")
	public void expired(String role, String name) throws Exception {
		HttpResponse<String> response = assign(null, name,
				"{\"roleId\":\"" + role + "\",\"validTo\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}");
		assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
		this.lastAssignment = LocalLoginSteps.json(response).path("id").asString();
		// the person made no request since, so no cached permission set exists
		this.instance.superuser("UPDATE role_assignments SET valid_from = now() - interval '2 hours', "
				+ "valid_to = now() - interval '61 seconds' WHERE id = '" + this.lastAssignment + "'");
	}

	@When("{string} assigns {string} to {string}")
	public void personAssigns(String actor, String role, String name) throws Exception {
		this.instance.response = assign(actor, name, "{\"roleId\":\"" + role + "\"}");
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(201);
	}

	@When("the system admin revokes the assignment of {string} to {string}")
	public void systemAdminRevokes(String role, String name) throws Exception {
		revoke(null, role, name);
	}

	@When("{string} revokes the assignment of {string} to {string}")
	public void personRevokes(String actor, String role, String name) throws Exception {
		revoke(actor, role, name);
	}

	@Given("the local system admin account is deactivated")
	public void localAccountDeactivated() throws Exception {
		this.instance.superuser("UPDATE system_admin_accounts SET status = 'deactivated', deactivated_at = now()");
	}

	@Given("a custom role {string} with only the permission {string}")
	public void customRole(String role, String permission) throws Exception {
		this.instance.superuser("INSERT INTO authorization_roles (id, name, is_builtin) VALUES ('" + role + "', '" + role
				+ "', false); INSERT INTO role_permissions (role_id, permission_id) VALUES ('" + role + "', '"
				+ permission + "')");
	}

	@When("the system admin sets {string} to {string}")
	public void systemAdminSetsStatus(String name, String status) throws Exception {
		this.instance.response = status(null, name, status);
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(200);
	}

	@When("{string} sets {string} to {string}")
	public void personSetsStatus(String actor, String name, String status) throws Exception {
		this.instance.response = status(actor, name, status);
	}

	@Then("{string} is still {string}")
	public void stillStatus(String name, String status) throws Exception {
		assertThat(this.instance.single("SELECT status FROM persons WHERE id = " + this.persons.get(name)))
			.isEqualTo(status);
	}

	@Then("the assignment of {string} to {string} was revoked by the system admin with the reason {string}")
	public void revokedBySystemAdmin(String role, String name, String reason) throws Exception {
		assertThat(this.instance.single("SELECT status || '|' || (revoked_by_admin_id IS NOT NULL) || '|' || "
				+ "revocation_reason FROM role_assignments WHERE id = '" + this.assignments.get(name + "/" + role) + "'"))
			.isEqualTo("revoked|true|" + reason);
	}

	@Then("the assignment is stored with source {string}, the granting system admin, the validity and the reason")
	public void stored(String source) {
		JsonNode body = LocalLoginSteps.json(this.instance.response);
		assertThat(body.path("source").asString()).isEqualTo(source);
		assertThat(body.path("grantedBy").path("principalType").asString()).isEqualTo("system-admin-account");
		assertThat(body.path("grantedAt").asString()).isNotBlank();
		assertThat(body.path("validFrom").asString()).isNotBlank();
		assertThat(body.path("validTo").asString()).isNotBlank();
		assertThat(body.path("reason").asString()).isEqualTo("TICKET-123");
	}

	@Then("{string} may call an operation requiring {string}")
	public void mayCall(String name, String permission) throws Exception {
		assertThat(permissions(name)).contains(permission);
	}

	@Then("{string} may not call an operation requiring {string}")
	public void mayNotCall(String name, String permission) throws Exception {
		assertThat(permissions(name)).doesNotContain(permission);
	}

	@Then("after the expiry job ran twice the audit trail contains the event {string} {int} times")
	public void expiryRecorded(String eventType, int times) throws Exception {
		RoleAssignmentExpiry expiry = this.instance.application().getBean(RoleAssignmentExpiry.class);
		expiry.recordExpiries();
		expiry.recordExpiries();
		assertThat(this.instance.single("SELECT count(*)::text FROM audit.events WHERE event_type = '" + eventType
				+ "' AND target_id = '" + this.lastAssignment + "'")).isEqualTo(Integer.toString(times));
	}

	@Then("the assignment shows when and by whom it was revoked")
	public void revoked() {
		JsonNode body = LocalLoginSteps.json(this.instance.response);
		assertThat(this.instance.response.statusCode()).isEqualTo(200);
		assertThat(body.path("status").asString()).isEqualTo("revoked");
		assertThat(body.path("revokedAt").asString()).isNotBlank();
		assertThat(body.path("revokedBy").path("principalType").asString()).isEqualTo("system-admin-account");
	}

	@Then("the session of {string} is rejected with 401")
	public void sessionRejected(String name) throws Exception {
		assertThat(me(this.sessions.get(name)).statusCode()).isEqualTo(401);
	}

	@Then("after a new login {string} holds only {string}")
	public void onlyRole(String name, String role) throws Exception {
		this.sessions.put(name, session(name, false));
		JsonNode roles = LocalLoginSteps.json(me(this.sessions.get(name))).path("roles");
		assertThat(roles).hasSize(1);
		assertThat(roles.get(0).path("role").path("id").asString()).isEqualTo(role);
	}

	@Then("the revocation succeeds")
	public void revocationSucceeds() {
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(200);
	}

	private String session(String name, boolean secondFactor) throws Exception {
		String token = UUID.randomUUID().toString() + UUID.randomUUID();
		this.instance.superuser("""
				INSERT INTO sessions (token_hash, person_id, client_type, auth_method, second_factor_verified,
				                      authenticated_at, last_activity_at, absolute_expires_at)
				VALUES (sha256(convert_to('%s', 'UTF8')), %d, 'web-portal', 'password', %s, now(), now(),
				        now() + interval '12 hours')""".formatted(token, this.persons.get(name), secondFactor));
		return token;
	}

	private HttpResponse<String> status(String actor, String name, String status) throws Exception {
		return request(actor, "/api/v1/admin/persons/" + this.persons.get(name) + "/status",
				"{\"status\":\"" + status + "\"}");
	}

	private HttpResponse<String> assign(String actor, String name, String body) throws Exception {
		return request(actor, "/api/v1/admin/persons/" + this.persons.get(name) + "/role-assignments", body);
	}

	private void revoke(String actor, String role, String name) throws Exception {
		this.instance.response = request(actor, "/api/v1/admin/persons/" + this.persons.get(name)
				+ "/role-assignments/" + this.assignments.get(name + "/" + role) + "/revoke", "{}");
	}

	/** A request of the system admin ({@code actor == null}) or of the named person. */
	private HttpResponse<String> request(String actor, String path, String body) throws Exception {
		if (actor == null) {
			return this.instance.post(path, body, true);
		}
		String token = this.sessions.get(actor);
		return this.instance.send(HttpRequest.newBuilder(this.instance.uri(path))
			.header("Content-Type", "application/json")
			.header("Cookie", "__Host-OEA_SESSION=" + token)
			.header("X-XSRF-TOKEN", Sessions.csrfToken(token))
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	private HttpResponse<String> me(String token) throws Exception {
		return this.instance.send(
				HttpRequest.newBuilder(this.instance.uri("/api/v1/me")).header("Cookie", "__Host-OEA_SESSION=" + token));
	}

	private String permissions(String name) throws Exception {
		HttpResponse<String> me = me(this.sessions.get(name));
		assertThat(me.statusCode()).isEqualTo(200);
		return JsonMapper.shared().readTree(me.body()).path("permissions").toString();
	}

}

package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.http.HttpRequest;
import java.sql.SQLException;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/access-administration.feature; the instance, the HTTP client
 * and the session come from {@link LocalLoginSteps} of the same scenario.
 */
public class AccessAdministrationSteps {

	private final LocalLoginSteps instance;

	private String providerId;

	private String issuer;

	private String clientSecret;

	private String group;

	private String mappingId;

	private String firstPersonId;

	public AccessAdministrationSteps(LocalLoginSteps instance) {
		this.instance = instance;
	}

	@Given("the provider {string} is configured")
	public void providerConfigured(String issuer) throws Exception {
		saveProvider(issuer, null);
		assertThat(this.instance.response.statusCode()).isEqualTo(201);
		this.providerId = LocalLoginSteps.json(this.instance.response).path("id").asString();
		this.issuer = issuer;
	}

	@Given("the session is confirmed with a second factor")
	public void secondFactor() throws Exception {
		this.instance.startEnrollment();
		this.instance.confirmEnrollment();
		assertThat(this.instance.response.statusCode()).isEqualTo(200);
	}

	@When("the system admin saves a provider with the issuer {string}")
	public void saveProvider(String issuer) throws Exception {
		saveProvider(issuer, null);
	}

	@When("the system admin saves a provider with a client secret")
	public void saveProviderWithSecret() throws Exception {
		this.clientSecret = "client-secret-" + UUID.randomUUID();
		saveProvider("https://login.example.org/tenant", this.clientSecret);
		assertThat(this.instance.response.statusCode()).isEqualTo(201);
		this.providerId = LocalLoginSteps.json(this.instance.response).path("id").asString();
	}

	@When("the system admin reads the provider again")
	public void readProvider() throws Exception {
		this.instance.response = this.instance
			.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/admin/auth/providers/" + this.providerId))
				.header("Cookie", cookie()));
	}

	@Given("the system admin maps a group to the role {string}")
	public void mapGroup(String role) throws Exception {
		mapGroup(UUID.randomUUID().toString(), role);
	}

	@When("the system admin maps the group {string} to the role {string}")
	public void mapGroup(String group, String role) throws Exception {
		this.group = group;
		this.instance.response = this.instance.post("/api/v1/admin/idp-group-mappings", mapping(group, role), true);
		if (this.instance.response.statusCode() == 201) {
			this.mappingId = LocalLoginSteps.json(this.instance.response).path("id").asString();
		}
	}

	@When("the system admin maps the same group to the role {string} again")
	public void mapSameGroup(String role) throws Exception {
		mapGroup(this.group, role);
	}

	@Given("the system admin creates a person and binds it to the subject {string}")
	public void createAndBind(String subject) throws Exception {
		this.firstPersonId = createPerson("Ada Lovelace", "ada@example.org");
		bind(this.firstPersonId, subject);
	}

	@When("the system admin creates a second person and binds it to the subject {string}")
	public void createSecondAndBind(String subject) throws Exception {
		bind(createPerson("Grace Hopper", "grace@example.org"), subject);
	}

	@When("the system admin deletes the mapping")
	public void deleteMapping() throws Exception {
		this.instance.response = this.instance
			.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/admin/idp-group-mappings/" + this.mappingId))
				.header("Cookie", cookie())
				.header("X-XSRF-TOKEN", this.instance.csrfToken())
				.DELETE());
		assertThat(this.instance.response.statusCode()).isEqualTo(204);
	}

	@Then("the problem names the issuer {string}")
	public void problemNamesIssuer(String issuer) {
		JsonNode problem = LocalLoginSteps.json(this.instance.response);
		assertThat(problem.path("detail").asString()).contains(issuer);
		assertThat(problem.path("invalidFields").get(0).asString()).isEqualTo("issuerUrl");
	}

	@Then("the response states that a client secret is set but does not contain it")
	public void secretNotReturned() throws SQLException {
		assertThat(this.instance.response.statusCode()).isEqualTo(200);
		assertThat(LocalLoginSteps.json(this.instance.response).path("clientSecretSet").asBoolean()).isTrue();
		assertThat(this.instance.response.body()).doesNotContain(this.clientSecret);
		assertThat(this.instance.single("SELECT count(*)::text FROM audit.events WHERE strpos(details::text, '"
				+ this.clientSecret + "') > 0")).isEqualTo("0");
	}

	@Then("no mapping is stored")
	public void noMapping() throws SQLException {
		assertThat(this.instance.single("SELECT count(*)::text FROM idp_group_mappings")).isEqualTo("0");
	}

	@Then("the setup phase continues")
	public void setupPhaseContinues() throws SQLException {
		assertThat(this.instance.single("SELECT (setup_phase_ended_at IS NULL)::text FROM system_settings"))
			.isEqualTo("true");
	}

	@Then("the response carries the warning {string}")
	public void warning(String code) {
		JsonNode warnings = LocalLoginSteps.json(this.instance.response).path("warnings");
		assertThat(warnings).hasSize(1);
		assertThat(warnings.get(0).path("code").asString()).isEqualTo(code);
	}

	@When("the system admin deactivates the local system admin account")
	public void deactivateLocalAccount() throws Exception {
		this.instance.response = this.instance.post("/api/v1/admin/system-admin-account/deactivate", "", true);
	}

	@Then("the local system admin account is still usable")
	public void localAccountUsable() throws Exception {
		assertThat(this.instance.single("SELECT status FROM system_admin_accounts")).isNotEqualTo("deactivated");
		assertThat(this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me")).header("Cookie", cookie()))
			.statusCode()).isEqualTo(200);
	}

	@Then("the mapping is stored")
	public void mappingStored() throws SQLException {
		assertThat(this.instance.response.statusCode()).isEqualTo(201);
		assertThat(this.instance.single("SELECT count(*)::text FROM idp_group_mappings WHERE id = '" + this.mappingId
				+ "'")).isEqualTo("1");
	}

	@Then("the setup phase has ended with the action {string}")
	public void setupPhaseEnded(String action) throws SQLException {
		assertThat(this.instance
			.single("SELECT setup_phase_ended_by_action FROM system_settings WHERE setup_phase_ended_at IS NOT NULL"))
			.isEqualTo(action);
		assertThat(this.instance.single(
				"SELECT details ->> 'triggeringAction' FROM audit.events WHERE event_type = 'system.setup-phase-ended'"))
			.isEqualTo(action);
	}

	@Then("the person exists in status {string}")
	public void personStatus(String status) throws SQLException {
		assertThat(this.instance.single("SELECT status FROM persons WHERE id = " + this.firstPersonId))
			.isEqualTo(status);
	}

	@Then("the binding stores provider, issuer, subject and the method {string}")
	public void bindingStored(String method) throws SQLException {
		assertThat(this.instance.response.statusCode()).isEqualTo(201);
		String row = this.instance.single("""
				SELECT auth_provider_id || '|' || issuer || '|' || subject || '|' || link_method
				FROM person_identities WHERE person_id = %s""".formatted(this.firstPersonId));
		assertThat(row).isEqualTo(this.providerId + "|" + this.issuer + "|a1b2c3|" + method);
	}

	@Then("the subject {string} is still bound to the first person")
	public void stillBound(String subject) throws SQLException {
		assertThat(this.instance.single("SELECT string_agg(person_id::text, ',') FROM person_identities WHERE subject = '"
				+ subject + "'")).isEqualTo(this.firstPersonId);
	}

	@Then("the mapping is stored with issuer, group identifier, role, creator and creation time")
	public void mappingDetails() throws SQLException {
		mappingStored();
		JsonNode mapping = LocalLoginSteps.json(this.instance.response);
		assertThat(mapping.path("issuer").asString()).isEqualTo(this.issuer);
		assertThat(mapping.path("groupIdentifier").asString()).isEqualTo(this.group);
		assertThat(mapping.path("roleId").asString()).isEqualTo("viewer");
		assertThat(mapping.path("createdBy").path("displayName").asString()).isEqualTo("admin");
		assertThat(mapping.path("createdAt").asString()).isNotBlank();
		assertThat(this.instance.single("SELECT (created_by_admin_id IS NOT NULL)::text FROM idp_group_mappings"))
			.isEqualTo("true");
	}

	@Then("the mapping is no longer listed")
	public void mappingNotListed() throws Exception {
		this.instance.response = this.instance.send(HttpRequest
			.newBuilder(this.instance.uri("/api/v1/admin/idp-group-mappings"))
			.header("Cookie", cookie()));
		assertThat(this.instance.response.statusCode()).isEqualTo(200);
		assertThat(this.instance.response.body()).doesNotContain(this.mappingId);
		assertThat(this.instance.single("SELECT (deleted_at IS NOT NULL)::text FROM idp_group_mappings WHERE id = '"
				+ this.mappingId + "'")).isEqualTo("true");
	}

	private void saveProvider(String issuer, String secret) throws Exception {
		var body = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Corporate IdP")
			.put("type", "oidc")
			.put("issuerUrl", issuer)
			.put("clientId", "oea");
		if (secret != null) {
			body.put("clientSecret", secret);
		}
		this.instance.response = this.instance.post("/api/v1/admin/auth/providers", body.toString(), true);
	}

	private String createPerson(String name, String email) throws Exception {
		var body = JsonMapper.shared().createObjectNode().put("name", name).put("email", email);
		this.instance.response = this.instance.post("/api/v1/admin/persons", body.toString(), true);
		assertThat(this.instance.response.statusCode()).isEqualTo(201);
		return LocalLoginSteps.json(this.instance.response).path("id").asString();
	}

	private void bind(String personId, String subject) throws Exception {
		var body = JsonMapper.shared()
			.createObjectNode()
			.put("authProviderId", this.providerId)
			.put("subject", subject);
		this.instance.response = this.instance.post("/api/v1/admin/persons/" + personId + "/identities",
				body.toString(), true);
	}

	private String mapping(String group, String role) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("authProviderId", this.providerId)
			.put("groupIdentifier", group)
			.put("roleId", role)
			.toString();
	}

	private String cookie() {
		return "__Host-OEA_SESSION=" + this.instance.sessionToken();
	}

}

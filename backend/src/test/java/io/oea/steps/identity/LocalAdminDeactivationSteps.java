package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.app.login.InvalidCredentialsException;
import io.oea.app.login.LocalLogin;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.service.PasswordHasher;
import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/local-admin-deactivation.feature; the instance and the IdP login
 * come from {@link OidcLoginSteps} of the same scenario.
 */
public class LocalAdminDeactivationSteps {

	private static final String PASSWORD = "local-admin-password-1";

	private final OidcLoginSteps oidc;

	private String loginName;

	private Sessions.NewSession localSession;

	private HttpResponse<String> response;

	public LocalAdminDeactivationSteps(OidcLoginSteps oidc) {
		this.oidc = oidc;
	}

	@Given("the instance has the local system admin {string} with a session")
	public void localAdmin(String loginName) throws Exception {
		this.loginName = loginName;
		String hash = instance().application().getBean(PasswordHasher.class).hash(PASSWORD);
		instance().update("""
				WITH account AS (INSERT INTO system_admin_accounts (login_name, status) VALUES ('%s', 'active')
				                 RETURNING id),
				     credential AS (INSERT INTO local_credentials (system_admin_account_id, type)
				                    SELECT id, 'password' FROM account RETURNING id)
				INSERT INTO password_credentials (credential_id, password_hash) SELECT id, '%s' FROM credential"""
			.formatted(loginName, hash));
		instance().update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'idp-group-mapping')""");
		var account = instance().application().getBean(SystemAdminAccountRepository.class)
			.findByLoginName(loginName)
			.orElseThrow();
		this.localSession = instance().application().getBean(Sessions.class).createForSystemAdmin(account, true,
				"192.0.2.1", "cucumber");
	}

	@When("the person deactivates the local system admin account")
	public void deactivate() throws Exception {
		ApplicationLogin login = this.oidc.lastLogin();
		String csrf = JsonMapper.shared().readTree(instance().get("/api/v1/me", login.sessionToken()).body())
			.path("csrfToken").asString();
		this.response = instance().send(HttpRequest
			.newBuilder(URI.create(instance().url() + "/api/v1/admin/system-admin-account/deactivate"))
			.header("Cookie", "__Host-OEA_SESSION=" + login.sessionToken())
			.header("X-XSRF-TOKEN", csrf)
			.POST(HttpRequest.BodyPublishers.noBody()));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Then("the local system admin account has the status {string}")
	public void status(String status) throws Exception {
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("status").asString()).isEqualTo(status);
		assertThat(instance().single("SELECT status FROM system_admin_accounts WHERE login_name = '" + this.loginName
				+ "'")).isEqualTo(status);
	}

	@Then("the session of the local system admin is rejected with 401")
	public void sessionRejected() throws Exception {
		assertThat(instance().get("/api/v1/me", this.localSession.token()).statusCode()).isEqualTo(401);
	}

	@Then("a login with the password of the local system admin fails")
	public void passwordLoginFails() {
		LocalLogin login = instance().application().getBean(LocalLogin.class);
		assertThatExceptionOfType(InvalidCredentialsException.class)
			.isThrownBy(() -> login.login(this.loginName, PASSWORD, new LocalLogin.Client("192.0.2.1", "cucumber")));
	}

	@Then("{string} still administers the instance after a new login")
	public void remoteStillWorks(String username) throws Exception {
		ApplicationLogin login = instance().login(username);
		assertThat(instance().get("/api/v1/admin/system-admin-account", login.sessionToken()).statusCode())
			.isEqualTo(200);
	}

	@Then("the audit trail contains the deactivation with the outcome {string}")
	public void audited(String outcome) throws Exception {
		assertThat(instance().single("SELECT count(*)::text FROM audit.events WHERE event_type = "
				+ "'system-admin-account.deactivated' AND outcome = '" + outcome + "'")).isEqualTo("1");
	}

	private OidcTestInstance instance() {
		return this.oidc.oidcInstance();
	}

}

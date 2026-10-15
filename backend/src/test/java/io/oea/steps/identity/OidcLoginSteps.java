package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.auth.DevRealm;
import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
import io.oea.testsupport.logging.LogCapture;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/oidc-login.feature: the running application on a fresh database
 * with the Keycloak Testcontainer as stored provider; logins run through the real flow.
 */
public class OidcLoginSteps {

	private final List<String> secrets = new ArrayList<>();

	private OidcTestInstance instance;

	private LogCapture logs;

	private ApplicationLogin login;

	private HttpResponse<String> response;

	private UUID disabledProvider;

	@Given("an OEA instance with the development realm as enabled OIDC provider")
	public void instance() throws Exception {
		this.logs = LogCapture.attach();
		this.instance = OidcTestInstance.start();
		this.secrets.add(DevRealm.clientSecret(OidcTestInstance.CLIENT));
	}

	/** Exposed for other identity steps of the same scenario. */
	OidcTestInstance oidcInstance() {
		return this.instance;
	}

	@Given("the person bound to {string} has the status {string}")
	public void person(String username, String status) throws Exception {
		this.instance.person(username, status);
	}

	@Given("a disabled provider {string}")
	public void disabledProvider(String name) throws Exception {
		this.disabledProvider = this.instance.provider(name, "http://localhost:8180/realms/old", false);
	}

	@Given("{string} has logged in through the provider")
	@When("{string} logs in through the provider")
	public void login(String username) {
		this.login = remember(this.instance.login(username));
	}

	@When("{string} logs in through the provider with an authorization request without PKCE")
	public void loginWithoutPkce(String username) {
		this.login = remember(this.instance.login(username, (request) -> UriComponentsBuilder.fromUri(request)
			.replaceQueryParam("code_challenge")
			.replaceQueryParam("code_challenge_method")
			.build(true)
			.toUri()));
	}

	@When("the current principal is requested with that session")
	public void me() throws Exception {
		this.response = this.instance.get("/api/v1/me", this.login.sessionToken());
	}

	@When("the login page lists the providers")
	public void providers() throws Exception {
		this.response = this.instance.get("/api/v1/auth/oidc/providers", null);
	}

	@Then("the browser is sent to {string} with only the session cookie")
	public void sentTo(String path) {
		assertThat(this.login.status()).isEqualTo(302);
		assertThat(this.login.location()).isEqualTo(OidcTestInstance.PUBLIC_URL + path);
		assertThat(this.login.sessionToken()).isNotBlank();
		assertThat(this.login.location()).doesNotContain("code=").doesNotContain("token");
	}

	@Then("the browser is sent to the login page with the generic failure")
	public void genericFailure() {
		assertThat(this.login.location()).isEqualTo(OidcTestInstance.PUBLIC_URL + "/login?error=login-failed");
		assertThat(this.login.sessionToken()).isNull();
	}

	@Then("no session exists")
	public void noSession() throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM sessions")).isEqualTo("0");
	}

	@Then("it names the person {string} without a redirect to the provider")
	public void namesPerson(String username) {
		assertThat(this.response.statusCode()).isEqualTo(200);
		JsonNode me = JsonMapper.shared().readTree(this.response.body());
		assertThat(me.path("principal").path("principalType").asString()).isEqualTo("person");
		assertThat(me.path("principal").path("displayName").asString()).isEqualTo(username);
	}

	@Then("only the enabled provider is listed, with identifier and name only")
	public void onlyEnabled() {
		JsonNode providers = JsonMapper.shared().readTree(this.response.body());
		assertThat(providers).hasSize(1);
		assertThat(providers.get(0).path("id").asString()).isEqualTo(this.instance.providerId().toString())
			.isNotEqualTo(this.disabledProvider.toString());
		assertThat(providers.get(0).propertyNames()).containsExactlyInAnyOrder("id", "name");
	}

	@Then("the person {string} has the status {string}")
	public void personStatus(String username, String status) throws Exception {
		assertThat(this.instance.single("SELECT status FROM persons WHERE name = '" + username + "'"))
			.isEqualTo(status);
	}

	@Then("^the audit trail contains \"([^\"]*)\" with the (?:method|trigger|reason) \"([^\"]*)\"$")
	public void audit(String eventType, String value) throws Exception {
		assertThat(this.instance.audit(eventType, "\"" + value + "\"")).isPositive();
	}

	/** REQ-175 AC3: no password, client secret, code, state, login or session cookie in the logs. */
	@After(value = "@oidc", order = 20000)
	public void logsWithoutSecrets() {
		if (this.logs == null) {
			return;
		}
		String output = this.logs.text();
		assertThat(output).as("captured log output").isNotEmpty();
		for (String secret : this.secrets) {
			assertThat(output.contains(secret)).as("log contains a secret of length " + secret.length()).isFalse();
		}
	}

	@After(value = "@oidc", order = 10000)
	public void stop() {
		if (this.instance != null) {
			this.instance.close();
		}
		if (this.logs != null) {
			this.logs.close();
		}
	}

	private ApplicationLogin remember(ApplicationLogin login) {
		this.secrets.addAll(login.secrets());
		return login;
	}

}

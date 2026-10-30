package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.app.security.Sessions;
import io.oea.testsupport.webauthn.SoftwarePasskey;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/identity/passkeys.feature with software passkeys (design D7 of
 * us-022-passkeys); the instance and the persons come from {@link LocalLoginSteps} and
 * {@link PersonPasswordSteps} of the same scenario.
 */
public class PasskeySteps {

	private static final String ORIGIN = "https://oea.example.org";

	private final LocalLoginSteps instance;

	private final PersonPasswordSteps persons;

	/** Software passkeys by device. */
	private final Map<String, SoftwarePasskey> devices = new HashMap<>();

	private HttpResponse<String> passwordStep;

	public PasskeySteps(LocalLoginSteps instance, PersonPasswordSteps persons) {
		this.instance = instance;
		this.persons = persons;
	}

	@Given("{string} has registered a passkey on the device {string}")
	@When("{string} registers a passkey on the device {string}")
	public void registers(String name, String device) throws Exception {
		this.instance.response = registration(device, ORIGIN);
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(200);
	}

	@When("{string} registers a passkey on the device {string} on the site {string}")
	public void registerElsewhere(String name, String device, String site) throws Exception {
		this.instance.response = registration(device, site);
	}

	@Then("{string} logs in without password with the passkey of {string} and her session is confirmed with a second factor")
	public void passwordless(String name, String device) throws Exception {
		HttpResponse<String> login = passkeyLogin(device);
		assertThat(login.statusCode()).as(login.body()).isEqualTo(201);
		confirmed(name, login, "passkey");
	}

	@Then("the audit trail contains {int} added passkeys")
	public void addedPasskeys(int count) throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM audit.events WHERE event_type = 'auth.factor-added' "
				+ "AND details ->> 'type' = 'passkey'")).isEqualTo(Integer.toString(count));
	}

	@When("the passkey of {string} is removed at the instance and a login is attempted with it")
	public void removedPasskey(String device) throws Exception {
		this.instance.superuser("UPDATE local_credentials SET status = 'revoked' WHERE type = 'passkey'");
		this.instance.response = passkeyLogin(device);
	}

	@Then("the login is rejected like a wrong password")
	public void rejectedLikeWrongPassword() throws Exception {
		HttpResponse<String> wrong = this.instance.post("/api/v1/auth/local/login",
				"{\"loginName\":\"nobody@example.org\",\"password\":\"wrong-password-000\"}", false);
		assertThat(this.instance.response.statusCode()).isEqualTo(401).isEqualTo(wrong.statusCode());
		assertThat(withoutInstance(this.instance.response)).isEqualTo(withoutInstance(wrong));
	}

	@When("{string} logs in with her password")
	public void passwordStep(String name) throws Exception {
		this.passwordStep = this.persons.login(this.persons.email(name), PersonPasswordSteps.CHOSEN);
	}

	/** The answer of the last password step of the scenario. */
	HttpResponse<String> lastPasswordStep() {
		return this.passwordStep;
	}

	@Then("the login asks for the second factor {string}")
	public void asksFor(String method) {
		assertThat(this.passwordStep.statusCode()).isEqualTo(401);
		JsonNode problem = LocalLoginSteps.json(this.passwordStep);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:second-factor-required");
		assertThat(problem.path("secondFactors").toString()).isEqualTo("[\"" + method + "\"]");
	}

	@Then("{string} confirms the login with the passkey of {string} and her session is confirmed with a second factor")
	public void secondStep(String name, String device) throws Exception {
		String transaction = LocalLoginSteps.json(this.passwordStep).path("loginTransaction").asString();
		HttpResponse<String> options = this.instance.post("/api/v1/auth/local/login/passkey/options",
				"{\"loginTransaction\":\"" + transaction + "\"}", false);
		assertThat(options.statusCode()).as(options.body()).isEqualTo(200);
		JsonNode challenge = LocalLoginSteps.json(options);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.put("loginTransaction", transaction);
		body.put("challengeId", challenge.path("challengeId").asString());
		body.set("credential", JsonMapper.shared()
			.readTree(this.devices.get(device).authenticate(challenge.path("options"), true)));
		HttpResponse<String> login = this.instance.post("/api/v1/auth/local/login/passkey", body.toString(), false);
		assertThat(login.statusCode()).as(login.body()).isEqualTo(201);
		confirmed(name, login, "password-passkey");
	}

	private HttpResponse<String> registration(String device, String site) throws Exception {
		SoftwarePasskey passkey = this.devices.computeIfAbsent(device, (key) -> new SoftwarePasskey(ORIGIN));
		passkey.moveTo(site);
		String token = this.persons.personToken();
		HttpResponse<String> started = this.instance.send(personRequest("/api/v1/me/second-factors/passkey", token)
			.POST(HttpRequest.BodyPublishers.ofString("{\"label\":\"" + device + "\"}")));
		assertThat(started.statusCode()).as(started.body()).isEqualTo(201);
		JsonNode registration = LocalLoginSteps.json(started);
		HttpResponse<String> confirmed = this.instance.send(personRequest(
				"/api/v1/me/second-factors/passkey/" + registration.path("registrationId").asString() + "/confirm", token)
			.POST(HttpRequest.BodyPublishers.ofString(passkey.register(registration.path("options"), true))));
		passkey.moveTo(ORIGIN);
		return confirmed;
	}

	private HttpResponse<String> passkeyLogin(String device) throws Exception {
		JsonNode challenge = LocalLoginSteps.json(this.instance.post("/api/v1/auth/passkey/options", "", false));
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.put("challengeId", challenge.path("challengeId").asString());
		body.set("credential", JsonMapper.shared()
			.readTree(this.devices.get(device).authenticate(challenge.path("options"), true)));
		return this.instance.post("/api/v1/auth/passkey/login", body.toString(), false);
	}

	private void confirmed(String name, HttpResponse<String> login, String method) throws Exception {
		assertThat(LocalLoginSteps.json(login).path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(this.instance.single("SELECT details ->> 'method' FROM audit.events WHERE event_type = 'auth.login' "
				+ "AND actor_person_id = " + this.persons.id(name) + " ORDER BY id DESC LIMIT 1")).isEqualTo(method);
	}

	private HttpRequest.Builder personRequest(String path, String token) {
		return HttpRequest.newBuilder(this.instance.uri(path))
			.header("Content-Type", "application/json")
			.header("Cookie", PersonPasswordSteps.cookie(token))
			.header("X-XSRF-TOKEN", Sessions.csrfToken(token));
	}

	private static JsonNode withoutInstance(HttpResponse<String> response) {
		var copy = LocalLoginSteps.json(response).deepCopy();
		((ObjectNode) copy).remove("instance");
		return copy;
	}

}

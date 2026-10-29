package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.app.security.Sessions;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/person-password.feature; the instance and the system admin session
 * come from {@link LocalLoginSteps} of the same scenario.
 */
public class PersonPasswordSteps {

	static final String INITIAL = "initial-password-123";

	static final String CHOSEN = "chosen-password-456";

	private final LocalLoginSteps instance;

	private final Map<String, String> ids = new HashMap<>();

	private final Map<String, String> emails = new HashMap<>();

	private String personToken;

	private HttpResponse<String> first;

	private HttpResponse<String> second;

	public PersonPasswordSteps(LocalLoginSteps instance) {
		this.instance = instance;
	}

	@Given("the system admin has invited the person {string} with the e-mail address {string}")
	public void invited(String name, String email) throws Exception {
		HttpResponse<String> created = this.instance.post("/api/v1/admin/persons",
				"{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}", true);
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.ids.put(name, LocalLoginSteps.json(created).path("id").asString());
		this.emails.put(name, email);
	}

	@Given("the system admin has set the initial password of {string}")
	@When("the system admin sets the initial password of {string}")
	public void setInitial(String name) throws Exception {
		this.instance.response = this.instance.post("/api/v1/admin/persons/" + this.ids.get(name) + "/password",
				"{\"password\":\"" + INITIAL + "\"}", true);
	}

	@Then("the answer names when the initial password expires but not the password")
	public void expiryOnly() {
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(200);
		assertThat(LocalLoginSteps.json(this.instance.response).path("expiresAt").asString()).isNotBlank();
		assertThat(this.instance.response.body()).doesNotContain(INITIAL);
	}

	@When("{string} logs in with her e-mail address and the initial password")
	public void loginInitial(String name) throws Exception {
		this.instance.response = login(this.emails.get(name), INITIAL);
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(201);
		this.personToken = token(this.instance.response);
	}

	@Then("{string} is active and must change her password before anything else")
	public void mustChange(String name) throws Exception {
		assertThat(LocalLoginSteps.json(this.instance.response).path("requiredAction").asString())
			.isEqualTo("change-password");
		assertThat(this.instance.single("SELECT status FROM persons WHERE id = " + this.ids.get(name))).isEqualTo("active");
		HttpResponse<String> blocked = this.instance
			.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/catalogs")).header("Cookie", cookie(this.personToken)));
		assertThat(blocked.statusCode()).isEqualTo(403);
		assertThat(JsonMapper.shared().readTree(blocked.body()).path("type").asString())
			.isEqualTo("urn:oea:problem:password-change-required");
	}

	@When("{string} changes her password")
	public void change(String name) throws Exception {
		HttpResponse<String> changed = this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me/password"))
			.header("Content-Type", "application/json")
			.header("Cookie", cookie(this.personToken))
			.header("X-XSRF-TOKEN", Sessions.csrfToken(this.personToken))
			.POST(HttpRequest.BodyPublishers
				.ofString("{\"currentPassword\":\"" + INITIAL + "\",\"newPassword\":\"" + CHOSEN + "\"}")));
		assertThat(changed.statusCode()).as(changed.body()).isEqualTo(204);
	}

	@Then("{string} logs in with her e-mail address and the new password without a second factor")
	public void loginChosen(String name) throws Exception {
		assertThat(login(this.emails.get(name), INITIAL).statusCode()).isEqualTo(401);
		HttpResponse<String> login = login(this.emails.get(name), CHOSEN);
		assertThat(login.statusCode()).as(login.body()).isEqualTo(201);
		JsonNode body = LocalLoginSteps.json(login);
		assertThat(body.path("requiredAction").isNull()).isTrue();
		assertThat(body.path("mfaSatisfied").asBoolean()).isFalse();
		assertThat(this.instance.single("SELECT details ->> 'method' || '|' || (details ->> 'secondFactor') "
				+ "FROM audit.events WHERE event_type = 'auth.login' AND actor_person_id = " + this.ids.get(name)
				+ " ORDER BY id DESC LIMIT 1")).isEqualTo("password|false");
	}

	@Then("no log line contains a password of {string}")
	public void noPasswordLogged(String name) {
		assertThat(this.instance.logText()).doesNotContain(INITIAL).doesNotContain(CHOSEN);
	}

	@When("a login is attempted with the unknown e-mail address {string} and with a wrong password for {string}")
	public void failedLogins(String unknown, String name) throws Exception {
		this.first = login(unknown, INITIAL);
		this.second = login(this.emails.get(name), "wrong-password-000");
	}

	@Then("both logins are rejected with the same status, problem type and body")
	public void sameAnswer() {
		assertThat(this.first.statusCode()).isEqualTo(401).isEqualTo(this.second.statusCode());
		assertThat(withoutInstance(this.first)).isEqualTo(withoutInstance(this.second));
	}

	String id(String name) {
		return this.ids.get(name);
	}

	String email(String name) {
		return this.emails.get(name);
	}

	/** Session token of the person's last login in the scenario. */
	String personToken() {
		return this.personToken;
	}

	HttpResponse<String> login(String email, String password) throws Exception {
		// generated passwords may contain quotes and backslashes
		return this.instance.post("/api/v1/auth/local/login",
				JsonMapper.shared().createObjectNode().put("loginName", email).put("password", password).toString(), false);
	}

	static String token(HttpResponse<String> login) {
		String cookie = login.headers().firstValue("Set-Cookie").orElseThrow();
		return cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
	}

	static String cookie(String token) {
		return "__Host-OEA_SESSION=" + token;
	}

	private static JsonNode withoutInstance(HttpResponse<String> response) {
		var copy = LocalLoginSteps.json(response).deepCopy();
		((tools.jackson.databind.node.ObjectNode) copy).remove("instance");
		return copy;
	}

}

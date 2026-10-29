package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.app.security.Sessions;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/password-policy.feature and password-reset.feature; the instance,
 * the system admin session and the persons come from {@link LocalLoginSteps} and
 * {@link PersonPasswordSteps} of the same scenario.
 */
public class PasswordAdministrationSteps {

	private static final String POLICY = "/api/v1/admin/settings/password-policy";

	private final LocalLoginSteps instance;

	private final PersonPasswordSteps persons;

	/** Generated passwords of the scenario: never in a log line or an audit event. */
	private final List<String> generated = new ArrayList<>();

	private String personToken;

	public PasswordAdministrationSteps(LocalLoginSteps instance, PersonPasswordSteps persons) {
		this.instance = instance;
		this.persons = persons;
	}

	@When("the system admin sets a password policy with minimum length 2 and all four character types")
	public void inconsistentPolicy() throws Exception {
		this.instance.response = this.instance.put(POLICY, policy(2, true, true, true, true, 0, 0));
	}

	@When("the system admin sets a password policy with minimum length 16 that requires uppercase letters, digits and special characters")
	public void strictPolicy() throws Exception {
		setPolicy(policy(16, true, false, true, true, 0, 0));
	}

	@Given("the system admin has set a password policy with an expiry of {int} days and a history of {int}")
	public void expiryAndHistory(int expiryDays, int historyCount) throws Exception {
		setPolicy(policy(12, false, false, false, false, expiryDays, historyCount));
	}

	@Given("the system admin has set a password policy that requires special characters")
	public void specialCharacters() throws Exception {
		setPolicy(policy(12, false, false, false, true, 0, 0));
	}

	@When("the system admin sets the initial password {string} for {string}")
	public void setInitial(String password, String name) throws Exception {
		this.instance.response = this.instance.post("/api/v1/admin/persons/" + this.persons.id(name) + "/password",
				JsonMapper.shared().createObjectNode().put("password", password).toString(), true);
	}

	@Then("the rejection names the rules {string}")
	public void rulesNamed(String rules) {
		List<String> named = new ArrayList<>();
		LocalLoginSteps.json(this.instance.response).path("invalidFields").forEach((rule) -> named.add(rule.asString()));
		assertThat(String.join(", ", named)).isEqualTo(rules);
	}

	@Given("the person {string} with the e-mail address {string} has chosen her own password")
	public void ownPassword(String name, String email) throws Exception {
		this.persons.invited(name, email);
		this.persons.setInitial(name);
		this.persons.loginInitial(name);
		this.persons.change(name);
		this.personToken = this.persons.personToken();
	}

	@When("{string} sets a password she used before")
	public void reuse(String name) throws Exception {
		this.instance.response = change(PersonPasswordSteps.CHOSEN, PersonPasswordSteps.INITIAL);
	}

	@When("{int} days have passed since {string} chose her password")
	public void passwordAged(int days, String name) throws Exception {
		this.instance.superuser("UPDATE local_credentials SET created_at = created_at - interval '" + days
				+ " days' WHERE person_id = " + this.persons.id(name) + " AND status = 'active'");
	}

	@Then("{string} must change her password after logging in with her own password")
	public void expired(String name) throws Exception {
		mustChange(this.persons.login(this.persons.email(name), PersonPasswordSteps.CHOSEN));
	}

	@When("the system admin has the initial passwords of {string} and {string} generated")
	public void generate(String first, String second) throws Exception {
		for (String name : List.of(first, second)) {
			HttpResponse<String> set = this.instance.post("/api/v1/admin/persons/" + this.persons.id(name) + "/password",
					"{\"generate\":true}", true);
			assertThat(set.statusCode()).as(set.body()).isEqualTo(200);
			this.generated.add(LocalLoginSteps.json(set).path("password").asString());
		}
	}

	@Then("each answer contains a password that contains a special character, and the two passwords differ")
	public void generatedPasswords() {
		assertThat(this.generated).hasSize(2).allSatisfy((password) -> {
			assertThat(password).hasSizeGreaterThanOrEqualTo(16);
			assertThat(password.chars().anyMatch((c) -> c >= 0x21 && c <= 0x7E && !Character.isLetterOrDigit(c)))
				.isTrue();
		});
		assertThat(this.generated.get(0)).isNotEqualTo(this.generated.get(1));
	}

	@Then("no log line contains a generated password")
	public void noGeneratedPasswordLogged() {
		String logs = this.instance.logText();
		assertThat(this.generated).isNotEmpty().allSatisfy((password) -> assertThat(logs).doesNotContain(password));
	}

	@When("{string} asks for a password suggestion")
	public void suggestion(String name) throws Exception {
		this.personToken = this.persons.personToken();
		HttpResponse<String> suggested = this.instance
			.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me/password/suggestion"))
				.header("Cookie", PersonPasswordSteps.cookie(this.personToken))
				.header("X-XSRF-TOKEN", Sessions.csrfToken(this.personToken))
				.POST(HttpRequest.BodyPublishers.noBody()));
		assertThat(suggested.statusCode()).as(suggested.body()).isEqualTo(200);
		this.generated.add(LocalLoginSteps.json(suggested).path("password").asString());
	}

	@Then("the suggestion contains a special character, and {string} changes her password to it")
	public void changeToSuggestion(String name) throws Exception {
		String suggestion = this.generated.getLast();
		assertThat(suggestion.chars().anyMatch((c) -> !Character.isLetterOrDigit(c))).isTrue();
		HttpResponse<String> changed = change(PersonPasswordSteps.INITIAL, suggestion);
		assertThat(changed.statusCode()).as(changed.body()).isEqualTo(204);
		HttpResponse<String> login = this.persons.login(this.persons.email(name), suggestion);
		assertThat(login.statusCode()).isEqualTo(201);
		assertThat(LocalLoginSteps.json(login).path("requiredAction").isNull()).isTrue();
	}

	@When("the system admin resets the password of {string}")
	public void reset(String name) throws Exception {
		this.instance.response = this.instance.post(
				"/api/v1/admin/persons/" + this.persons.id(name) + "/password/reset", "{\"reason\":\"forgotten\"}",
				true);
	}

	@Then("the answer contains the new password once, and the session of {string} has ended")
	public void resetAnswer(String name) throws Exception {
		assertThat(this.instance.response.statusCode()).as(this.instance.response.body()).isEqualTo(200);
		JsonNode body = LocalLoginSteps.json(this.instance.response);
		this.generated.add(body.path("password").asString());
		assertThat(body.path("expiresAt").asString()).isNotBlank();
		HttpResponse<String> me = this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me"))
			.header("Cookie", PersonPasswordSteps.cookie(this.personToken)));
		assertThat(me.statusCode()).isEqualTo(401);
	}

	@Then("a login of {string} with her own password fails")
	public void oldPasswordFails(String name) throws Exception {
		assertThat(this.persons.login(this.persons.email(name), PersonPasswordSteps.CHOSEN).statusCode()).isEqualTo(401);
	}

	@Then("{string} must change her password after logging in with the new password")
	public void newPasswordRequiresChange(String name) throws Exception {
		mustChange(this.persons.login(this.persons.email(name), this.generated.getLast()));
	}

	@Then("the audit trail contains the event {string} without the password")
	public void auditedWithoutPassword(String eventType) throws Exception {
		assertThat(this.instance.single("SELECT count(*)::text FROM audit.events WHERE event_type = '" + eventType + "'"))
			.isEqualTo("1");
		assertThat(this.instance.single("SELECT count(*)::text FROM audit.events WHERE strpos(details::text, '"
				+ this.generated.getLast().replace("'", "''") + "') > 0")).isEqualTo("0");
	}

	private void mustChange(HttpResponse<String> login) throws Exception {
		assertThat(login.statusCode()).as(login.body()).isEqualTo(201);
		assertThat(LocalLoginSteps.json(login).path("requiredAction").asString()).isEqualTo("change-password");
		String token = PersonPasswordSteps.token(login);
		HttpResponse<String> blocked = this.instance
			.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/catalogs")).header("Cookie", PersonPasswordSteps.cookie(token)));
		assertThat(blocked.statusCode()).isEqualTo(403);
		assertThat(JsonMapper.shared().readTree(blocked.body()).path("type").asString())
			.isEqualTo("urn:oea:problem:password-change-required");
	}

	private void setPolicy(String policy) throws Exception {
		HttpResponse<String> set = this.instance.put(POLICY, policy);
		assertThat(set.statusCode()).as(set.body()).isEqualTo(200);
	}

	private HttpResponse<String> change(String current, String next) throws Exception {
		return this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me/password"))
			.header("Content-Type", "application/json")
			.header("Cookie", PersonPasswordSteps.cookie(this.personToken))
			.header("X-XSRF-TOKEN", Sessions.csrfToken(this.personToken))
			.POST(HttpRequest.BodyPublishers.ofString(JsonMapper.shared()
				.createObjectNode()
				.put("currentPassword", current)
				.put("newPassword", next)
				.toString())));
	}

	private static String policy(int minLength, boolean uppercase, boolean lowercase, boolean digits, boolean special,
			int expiryDays, int historyCount) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("minLength", minLength)
			.put("requireUppercase", uppercase)
			.put("requireLowercase", lowercase)
			.put("requireDigits", digits)
			.put("requireSpecialChars", special)
			.put("expiryDays", expiryDays)
			.put("historyCount", historyCount)
			.put("personPasswordLogin", true)
			.toString();
	}

}

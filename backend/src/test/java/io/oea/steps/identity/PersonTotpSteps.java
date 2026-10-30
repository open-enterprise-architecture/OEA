package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.app.security.Sessions;
import io.oea.core.service.TotpAlgorithm;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/person-totp.feature and second-factor-management.feature; the
 * instance, the system admin session and the persons come from {@link LocalLoginSteps} and
 * {@link PersonPasswordSteps} of the same scenario. Codes use the real clock: every code of an
 * authenticator gets a new time step within the accepted tolerance, waiting if necessary.
 */
public class PersonTotpSteps {

	private final LocalLoginSteps instance;

	private final PersonPasswordSteps persons;

	/** Secrets of the authenticators by label. */
	private final Map<String, String> secrets = new HashMap<>();

	/** Last used time step per authenticator, so that no code is used twice (REQ-170). */
	private final Map<String, Long> lastSteps = new HashMap<>();

	/** Secrets of the scenario: never in a log line or an audit event. */
	private final Set<String> sensitive = new HashSet<>();

	private String token;

	private String pendingCredential;

	private JsonNode enrollment;

	private HttpResponse<String> unknownTransaction;

	public PersonTotpSteps(LocalLoginSteps instance, PersonPasswordSteps persons) {
		this.instance = instance;
		this.persons = persons;
	}

	@Given("{string} has set up an authenticator app with the label {string}")
	@When("{string} sets up an authenticator app with the label {string}")
	public void setUp(String name, String label) throws Exception {
		start(label);
		HttpResponse<String> confirmed = confirm(code(label));
		assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
	}

	@When("{string} starts setting up an authenticator app with the label {string} and enters a wrong code")
	public void wrongEnrollmentCode(String name, String label) throws Exception {
		start(label);
		this.instance.response = confirm(wrongCode(label));
	}

	@When("{string} enters a valid code of {string}")
	public void validEnrollmentCode(String name, String label) throws Exception {
		HttpResponse<String> confirmed = confirm(code(label));
		assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
	}

	@Then("the enrollment showed the secret and a key URI for {string} once, and {string} is active")
	public void enrollmentShown(String email, String label) throws Exception {
		assertThat(this.enrollment.path("secret").asString()).isEqualTo(this.secrets.get(label));
		assertThat(this.enrollment.path("otpauthUri").asString()).startsWith("otpauth://totp/")
			.contains(java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8));
		active(label);
	}

	@Then("{string} is active")
	public void active(String label) throws Exception {
		assertThat(factorLabels()).contains(label);
	}

	@When("{string} logs in with her password and a code of {string}")
	public void loginWithCode(String name, String label) throws Exception {
		HttpResponse<String> loggedIn = loginTotp(transaction(name), code(label));
		assertThat(loggedIn.statusCode()).as(loggedIn.body()).isEqualTo(201);
		this.token = PersonPasswordSteps.token(loggedIn);
	}

	@Then("the session of {string} is confirmed with a second factor")
	public void confirmedSession(String name) throws Exception {
		JsonNode me = LocalLoginSteps.json(this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me"))
			.header("Cookie", PersonPasswordSteps.cookie(this.token))));
		assertThat(me.path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(this.instance.single("SELECT details ->> 'method' FROM audit.events WHERE event_type = 'auth.login' "
				+ "AND actor_person_id = " + this.persons.id(name) + " ORDER BY id DESC LIMIT 1")).isEqualTo("password-totp");
	}

	@Then("no log line or audit event contains a secret of {string}")
	public void nothingLeaked(String name) throws Exception {
		String logs = this.instance.logText();
		for (String value : this.sensitive) {
			assertThat(logs).doesNotContain(value);
			assertThat(this.instance.single("SELECT count(*)::text FROM audit.events WHERE strpos(details::text, '"
					+ value + "') > 0")).isEqualTo("0");
		}
	}

	@When("{string} logs in with her password and a wrong code")
	public void loginWithWrongCode(String name) throws Exception {
		String code = wrongCode(this.secrets.keySet().iterator().next());
		this.instance.response = loginTotp(transaction(name), code);
		this.unknownTransaction = loginTotp("unknown-transaction", code);
	}

	@Then("the login is rejected with the same answer as an unknown login transaction")
	public void sameAnswer() {
		assertThat(this.instance.response.statusCode()).isEqualTo(401).isEqualTo(this.unknownTransaction.statusCode());
		assertThat(withoutInstance(this.instance.response)).isEqualTo(withoutInstance(this.unknownTransaction));
	}

	@Then("the second factors of {string} are {string}")
	public void factors(String name, String labels) throws Exception {
		assertThat(String.join(", ", factorLabels())).isEqualTo(labels);
	}

	@When("{string} removes the second factor {string}")
	public void remove(String name, String label) throws Exception {
		String credential = null;
		for (JsonNode factor : factorList()) {
			if (label.equals(factor.path("label").asString())) {
				credential = factor.path("credentialId").asString();
			}
		}
		HttpResponse<String> removed = this.instance.send(personRequest("/api/v1/me/second-factors/" + credential).DELETE());
		assertThat(removed.statusCode()).as(removed.body()).isEqualTo(204);
	}

	@When("the system admin resets the second factors of {string}")
	public void reset(String name) throws Exception {
		HttpResponse<String> reset = this.instance.post(
				"/api/v1/admin/persons/" + this.persons.id(name) + "/second-factors/reset", "{\"reason\":\"phone lost\"}",
				true);
		assertThat(reset.statusCode()).as(reset.body()).isEqualTo(200);
	}

	@Then("the session of {string} has ended")
	public void sessionEnded(String name) throws Exception {
		HttpResponse<String> me = this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me"))
			.header("Cookie", PersonPasswordSteps.cookie(this.token)));
		assertThat(me.statusCode()).isEqualTo(401);
	}

	@Then("{string} logs in with her password alone")
	public void passwordAlone(String name) throws Exception {
		HttpResponse<String> login = this.persons.login(this.persons.email(name), PersonPasswordSteps.CHOSEN);
		assertThat(login.statusCode()).as(login.body()).isEqualTo(201);
		assertThat(LocalLoginSteps.json(login).path("mfaSatisfied").asBoolean()).isFalse();
	}

	private void start(String label) throws Exception {
		if (this.token == null) {
			this.token = this.persons.personToken();
		}
		HttpResponse<String> started = this.instance.send(personRequest("/api/v1/me/second-factors/totp")
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers
				.ofString(JsonMapper.shared().createObjectNode().put("label", label).toString())));
		assertThat(started.statusCode()).as(started.body()).isEqualTo(201);
		this.enrollment = LocalLoginSteps.json(started);
		this.pendingCredential = this.enrollment.path("credentialId").asString();
		this.secrets.put(label, this.enrollment.path("secret").asString());
		this.sensitive.add(this.enrollment.path("secret").asString());
	}

	private HttpResponse<String> confirm(String code) throws Exception {
		return this.instance
			.send(personRequest("/api/v1/me/second-factors/totp/" + this.pendingCredential + "/confirm")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"" + code + "\"}")));
	}

	private String transaction(String name) throws Exception {
		HttpResponse<String> password = this.persons.login(this.persons.email(name), PersonPasswordSteps.CHOSEN);
		assertThat(password.statusCode()).as(password.body()).isEqualTo(401);
		JsonNode problem = LocalLoginSteps.json(password);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:second-factor-required");
		return problem.path("loginTransaction").asString();
	}

	private HttpResponse<String> loginTotp(String transaction, String code) throws Exception {
		return this.instance.post("/api/v1/auth/local/login/totp", JsonMapper.shared()
			.createObjectNode()
			.put("loginTransaction", transaction)
			.put("code", code)
			.toString(), false);
	}

	private List<JsonNode> factorList() throws Exception {
		JsonNode factors = LocalLoginSteps
			.json(this.instance.send(personRequest("/api/v1/me/second-factors").GET()));
		List<JsonNode> list = new ArrayList<>();
		factors.forEach(list::add);
		return list;
	}

	private List<String> factorLabels() throws Exception {
		return factorList().stream().map((factor) -> factor.path("label").asString()).toList();
	}

	private HttpRequest.Builder personRequest(String path) {
		return HttpRequest.newBuilder(this.instance.uri(path))
			.header("Cookie", PersonPasswordSteps.cookie(this.token))
			.header("X-XSRF-TOKEN", Sessions.csrfToken(this.token));
	}

	/** A code of a new time step within the tolerance of ±1 step, waiting if necessary. */
	private String code(String label) throws InterruptedException {
		long now = Instant.now().getEpochSecond() / TotpAlgorithm.PERIOD_SECONDS;
		long last = this.lastSteps.getOrDefault(label, -1L);
		while (last + 1 > now + 1) {
			Thread.sleep(1000);
			now = Instant.now().getEpochSecond() / TotpAlgorithm.PERIOD_SECONDS;
		}
		long step = Math.max(now, last + 1);
		this.lastSteps.put(label, step);
		return TotpAlgorithm.code(this.secrets.get(label), step);
	}

	/** A code that is valid for none of the steps around now. */
	private String wrongCode(String label) {
		long now = Instant.now().getEpochSecond() / TotpAlgorithm.PERIOD_SECONDS;
		Set<String> valid = new HashSet<>();
		for (long step = now - 2; step <= now + 2; step++) {
			valid.add(TotpAlgorithm.code(this.secrets.get(label), step));
		}
		for (int candidate = 0;; candidate++) {
			String code = String.format("%06d", candidate);
			if (!valid.contains(code)) {
				return code;
			}
		}
	}

	private static JsonNode withoutInstance(HttpResponse<String> response) {
		var copy = LocalLoginSteps.json(response).deepCopy();
		((tools.jackson.databind.node.ObjectNode) copy).remove("instance");
		return copy;
	}

}

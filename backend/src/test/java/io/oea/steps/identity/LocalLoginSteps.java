package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.core.service.TotpAlgorithm;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.logging.LogCapture;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/local-login.feature: the real application with its web server
 * on a fresh, bootstrapped database per scenario, driven over HTTP like the login page does.
 * Other identity steps reuse its instance and session.
 */
public class LocalLoginSteps {

	private static final String COOKIE = "__Host-OEA_SESSION";

	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	private final HttpClient http = HttpClient.newHttpClient();

	/** Secrets of the scenario that must never appear in the log output (REQ-175 AC3). */
	private final List<String> secrets = new ArrayList<>();

	/** TOTP codes submitted in the scenario; checked separately because they are short digit strings. */
	private final Set<String> codes = new HashSet<>();

	private OeaDatabase postgres;

	private ConfigurableApplicationContext application;

	private LogCapture logs;

	private String loginName;

	private String password;

	HttpResponse<String> response;

	private final List<HttpResponse<String>> attempts = new ArrayList<>();

	private String sessionToken;

	private String csrfToken;

	private String previousSessionToken;

	private String totpSecret;

	private String credentialId;

	private String loginTransaction;

	private long lastTimeStep = -1;

	private String lastCode;

	@Given("a bootstrapped OEA instance in its setup phase with the system admin {string}")
	public void bootstrappedInstance(String loginName) throws Exception {
		bootstrappedInstance(loginName, new String[0]);
	}

	@Given("a bootstrapped OEA instance in its setup phase with the system admin {string} and password login of persons switched on")
	public void bootstrappedInstanceWithPersonPasswords(String loginName) throws Exception {
		bootstrappedInstance(loginName, new String[] { "--oea.auth.person-password.enabled=true" });
	}

	@Given("a bootstrapped OEA instance in its setup phase with the system admin {string}, password login of persons switched on and the public URL {string}")
	public void bootstrappedInstanceWithPublicUrl(String loginName, String publicUrl) throws Exception {
		bootstrappedInstance(loginName,
				new String[] { "--oea.auth.person-password.enabled=true", "--oea.web.public-url=" + publicUrl });
	}

	private void bootstrappedInstance(String loginName, String[] arguments) throws Exception {
		this.postgres = OeaDatabases.freshMigrated();
		Path tokenFile = Files.createTempDirectory("oea-run").resolve("setup-token");
		this.logs = LogCapture.attach();
		this.application = OeaApplications.start(this.postgres, tokenFile, WebApplicationType.SERVLET, arguments);
		String token = Files.readString(tokenFile).strip();
		this.loginName = loginName;
		this.password = secret(24);
		var body = JsonMapper.shared()
			.createObjectNode()
			.put("setupToken", token)
			.put("loginName", loginName)
			.put("password", this.password)
			.put("instanceSlug", "acme-corp");
		HttpResponse<String> setup = post("/api/v1/setup", body.toString(), false);
		assertThat(setup.statusCode()).isEqualTo(201);
	}

	@Given("the system admin is logged in")
	@When("the system admin logs in with the password chosen during the bootstrapping")
	public void login() throws Exception {
		this.response = login(this.loginName, this.password);
		if (this.response.statusCode() == 201) {
			startSession(this.response);
		}
		else if (this.response.statusCode() == 401) {
			this.loginTransaction = json(this.response).path("loginTransaction").asString(null);
		}
	}

	@Given("the system admin logged in more than 5 minutes ago")
	public void oldLogin() throws Exception {
		login();
		superuser("UPDATE sessions SET authenticated_at = now() - interval '6 minutes'");
	}

	@Given("the system admin has enrolled a TOTP credential")
	public void enrolled() throws Exception {
		login();
		startEnrollment();
		confirmEnrollment();
		assertThat(this.response.statusCode()).isEqualTo(200);
	}

	@Given("the setup phase has ended")
	public void setupPhaseEnded() throws SQLException {
		superuser("UPDATE system_settings SET setup_phase_ended_at = now(), "
				+ "setup_phase_ended_by_action = 'idp-group-mapping'");
	}

	@Given("^the session (has been idle for more than 30 minutes|is older than 12 hours)$")
	public void sessionAged(String state) throws SQLException {
		if (state.startsWith("has been idle")) {
			superuser("UPDATE sessions SET last_activity_at = now() - interval '31 minutes'");
		}
		else {
			superuser("UPDATE sessions SET created_at = now() - interval '12 hours 1 minute', "
					+ "authenticated_at = now() - interval '12 hours 1 minute', "
					+ "absolute_expires_at = now() - interval '1 minute'");
		}
	}

	@When("a login is attempted with the unknown login name {string}")
	public void unknownLogin(String loginName) throws Exception {
		this.attempts.add(login(loginName, secret(24)));
	}

	@When("a login is attempted for {string} with a wrong password")
	public void wrongPassword(String loginName) throws Exception {
		this.attempts.add(login(loginName, secret(24)));
	}

	@When("{int} logins with a wrong password fail for {string} and for the unknown login name {string}")
	public void failedLogins(int count, String existing, String unknown) throws Exception {
		for (int i = 0; i < count; i++) {
			assertThat(login(existing, secret(24)).statusCode()).isEqualTo(401);
			assertThat(login(unknown, secret(24)).statusCode()).isEqualTo(401);
		}
		this.attempts.clear();
		this.attempts.add(login(existing, secret(24)));
		this.attempts.add(login(unknown, secret(24)));
	}

	@When("{int} logins with different unknown login names fail from one address")
	public void manyNames(int count) throws Exception {
		for (int i = 0; i < count; i++) {
			login("unknown-" + i, secret(16));
		}
	}

	@When("the lock period has passed")
	public void lockPassed() throws InterruptedException {
		long seconds = Long.parseLong(this.response.headers().firstValue("Retry-After").orElseThrow());
		Thread.sleep(seconds * 1000 + 500);
	}

	@When("the system admin enters a valid TOTP code")
	public void validCode() throws Exception {
		this.response = loginTotp(this.loginTransaction, nextCode());
		if (this.response.statusCode() == 201) {
			startSession(this.response);
		}
	}

	@When("the system admin enters {int} wrong TOTP codes")
	public void wrongCodes(int count) throws Exception {
		for (int i = 0; i < count; i++) {
			assertThat(loginTotp(this.loginTransaction, wrongCode()).statusCode()).isEqualTo(401);
		}
	}

	@When("the system admin starts the TOTP enrollment")
	public void startEnrollment() throws Exception {
		this.response = post("/api/v1/me/second-factors/totp", "{\"label\":\"phone\"}", true);
		if (this.response.statusCode() == 201) {
			JsonNode enrollment = json(this.response);
			this.totpSecret = enrollment.path("secret").asString();
			this.credentialId = enrollment.path("credentialId").asString();
			this.secrets.add(this.totpSecret);
		}
	}

	@When("^the system admin starts the TOTP enrollment (without CSRF token|with a wrong CSRF token|with the origin \".*\")$")
	public void startEnrollmentForged(String variant) throws Exception {
		String origin = variant.startsWith("with the origin") ? variant.substring(variant.indexOf('"') + 1,
				variant.lastIndexOf('"')) : null;
		HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/v1/me/second-factors/totp"))
			.header("Content-Type", "application/json")
			.header("Cookie", COOKIE + "=" + this.sessionToken)
			.POST(HttpRequest.BodyPublishers.ofString("{}"));
		if (variant.startsWith("with a wrong")) {
			request.header("X-XSRF-TOKEN", secret(43));
		}
		else if (origin != null) {
			request.header("X-XSRF-TOKEN", this.csrfToken).header("Origin", origin);
		}
		this.response = send(request);
	}

	@When("the system admin confirms the enrollment with a valid code")
	public void confirmEnrollment() throws Exception {
		this.response = post("/api/v1/me/second-factors/totp/" + this.credentialId + "/confirm",
				"{\"code\":\"" + nextCode() + "\"}", true);
	}

	@When("the system admin logs in with the password and a valid TOTP code")
	public void loginWithCode() throws Exception {
		login();
		validCode();
		assertThat(this.response.statusCode()).isEqualTo(201);
	}

	@When("the system admin logs in again with the password and the same TOTP code")
	public void loginWithSameCode() throws Exception {
		login();
		this.response = loginTotp(this.loginTransaction, this.lastCode);
	}

	@When("the system admin logs out")
	public void logout() throws Exception {
		this.response = post("/api/v1/auth/logout", "", true);
		assertThat(this.response.statusCode()).isEqualTo(200);
		this.previousSessionToken = this.sessionToken;
	}

	@When("the current principal is requested")
	public void me() throws Exception {
		this.response = send(HttpRequest.newBuilder(uri("/api/v1/me")).header("Cookie", COOKIE + "=" + this.sessionToken));
	}

	@When("the current principal is requested with the previous session cookie")
	public void meWithPreviousCookie() throws Exception {
		this.response = send(
				HttpRequest.newBuilder(uri("/api/v1/me")).header("Cookie", COOKIE + "=" + this.previousSessionToken));
	}

	@Then("a session with the role {string} is created")
	public void sessionWithRole(String role) throws Exception {
		assertThat(this.response.statusCode()).isEqualTo(201);
		me();
		assertThat(json(this.response).path("roles").valueStream().map((grant) -> grant.path("role").path("id").asString()))
			.containsExactly(role);
	}

	@Then("the system admin account {string} has status {string}")
	public void accountStatus(String loginName, String status) throws SQLException {
		assertThat(single("SELECT status FROM system_admin_accounts WHERE login_name = '" + loginName + "'"))
			.isEqualTo(status);
	}

	@Then("the audit trail contains the event {string}")
	public void auditEvent(String eventType) throws SQLException {
		assertThat(Integer.parseInt(auditCount(eventType))).isPositive();
	}

	@Then("the audit trail contains the event {string} {int} times")
	public void auditEvents(String eventType, int count) throws SQLException {
		assertThat(auditCount(eventType)).isEqualTo(String.valueOf(count));
	}

	@Then("both attempts are rejected with the same status, problem type and body")
	public void sameRejection() {
		assertThat(this.attempts).hasSize(2);
		assertThat(this.attempts.get(0).statusCode()).isEqualTo(401).isEqualTo(this.attempts.get(1).statusCode());
		assertThat(withoutInstance(json(this.attempts.get(0)))).isEqualTo(withoutInstance(json(this.attempts.get(1))));
		assertThat(json(this.attempts.get(0)).path("type").asString()).isEqualTo("urn:oea:problem:invalid-credentials");
	}

	@Then("the next attempts for both names are rejected with the same response and a Retry-After header")
	public void bothThrottled() {
		sameRejection();
		assertThat(this.attempts).allSatisfy(
				(attempt) -> assertThat(attempt.headers().firstValue("Retry-After")).isPresent());
	}

	@Then("a login of {string} with the correct password is rejected as well")
	public void correctPasswordRejected(String loginName) throws Exception {
		this.response = login(loginName, this.password);
		assertThat(this.response.statusCode()).isEqualTo(401);
		assertThat(this.response.headers().firstValue("Retry-After")).isPresent();
	}

	@Then("a login of {string} with the correct password from that address is throttled")
	public void addressThrottled(String loginName) throws Exception {
		correctPasswordRejected(loginName);
	}

	@Then("the system admin logs in with the correct password")
	public void loginSucceeds() throws Exception {
		login();
		assertThat(this.response.statusCode()).isEqualTo(201);
	}

	@Then("the session is not second-factor-verified")
	public void notVerified() throws Exception {
		assertThat(json(this.response).path("mfaSatisfied").asBoolean()).isFalse();
		me();
		assertThat(json(this.response).path("mfaSatisfied").asBoolean()).isFalse();
	}

	@Then("the session is second-factor-verified")
	public void verified() throws Exception {
		me();
		assertThat(this.response.statusCode()).isEqualTo(200);
		assertThat(json(this.response).path("mfaSatisfied").asBoolean()).isTrue();
	}

	@Then("the second factor is requested without a session cookie")
	public void secondFactorRequested() {
		assertThat(this.response.statusCode()).isEqualTo(401);
		assertThat(json(this.response).path("type").asString()).isEqualTo("urn:oea:problem:second-factor-required");
		assertThat(this.loginTransaction).isNotBlank();
		assertThat(this.response.headers().firstValue("Set-Cookie")).isEmpty();
	}

	@Then("a valid TOTP code is rejected in the same login transaction")
	public void transactionInvalidated() throws Exception {
		assertThat(loginTotp(this.loginTransaction, nextCode()).statusCode()).isEqualTo(401);
	}

	@Then("the TOTP credential is active")
	public void credentialActive() throws SQLException {
		assertThat(this.response.statusCode()).isEqualTo(200);
		assertThat(single("SELECT status FROM local_credentials WHERE id = '" + this.credentialId + "'"))
			.isEqualTo("active");
	}

	@Then("the second login is rejected")
	public void secondLoginRejected() {
		assertThat(this.response.statusCode()).isEqualTo(401);
	}

	@Then("the request is rejected with status {int} and problem type {string}")
	public void rejected(int status, String type) {
		rejected(status);
		assertThat(json(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the request is rejected with status {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).isEqualTo(status);
		assertThat(this.response.headers().firstValue("Content-Type")).hasValueSatisfying(
				(contentType) -> assertThat(contentType).startsWith("application/problem+json"));
	}

	@Then("the session is ended")
	public void sessionEnded() throws SQLException {
		assertThat(single("SELECT revoked_reason FROM sessions")).isEqualTo("expired");
	}

	@Then("no TOTP credential exists")
	public void noTotpCredential() throws SQLException {
		assertThat(single("SELECT count(*)::text FROM local_credentials WHERE type = 'totp'")).isEqualTo("0");
	}

	@Then("the response sets the cookie {string} with HttpOnly, Secure, Path=\\/ and SameSite=Strict")
	public void cookieAttributes(String name) {
		String cookie = this.response.headers().firstValue("Set-Cookie").orElseThrow();
		assertThat(cookie).startsWith(name + "=");
		assertThat(cookie.split(";\\s*")).contains("HttpOnly", "Secure", "Path=/", "SameSite=Strict");
	}

	@Then("the response body contains no session token")
	public void bodyWithoutToken() {
		assertThat(this.response.body()).doesNotContain(this.sessionToken);
	}

	@Then("the response carries the security headers of REQ-187 and {string}")
	public void securityHeaders(String cacheControl) {
		assertThat(this.response.statusCode()).isEqualTo(200);
		var headers = this.response.headers();
		assertThat(headers.firstValue("X-Content-Type-Options")).hasValue("nosniff");
		assertThat(headers.firstValue("Referrer-Policy")).hasValue("no-referrer");
		assertThat(headers.firstValue("Content-Security-Policy")).hasValueSatisfying(
				(csp) -> assertThat(csp).contains("frame-ancestors 'none'"));
		assertThat(headers.firstValue("Strict-Transport-Security")).isPresent();
		String[] expected = cacheControl.split(":\\s*", 2);
		assertThat(headers.allValues(expected[0]).toString()).contains(expected[1]);
	}

	@Then("the response names the system admin account {string}")
	public void namesAccount(String loginName) {
		assertThat(this.response.statusCode()).isEqualTo(200);
		JsonNode principal = json(this.response).path("principal");
		assertThat(principal.path("principalType").asString()).isEqualTo("system-admin-account");
		assertThat(principal.path("displayName").asString()).isEqualTo(loginName);
		assertThat(json(this.response).has("mfaSatisfied")).isTrue();
	}

	@Then("it lists the permissions of the role {string}")
	public void listsPermissions(String role) throws SQLException {
		List<String> listed = json(this.response).path("permissions")
			.valueStream()
			.map((permission) -> permission.path("permission").asString())
			.toList();
		String expected = single(
				"SELECT string_agg(permission_id, ',' ORDER BY permission_id) FROM role_permissions WHERE role_id = '"
						+ role + "'");
		assertThat(listed).containsExactlyInAnyOrder(expected.split(","));
	}

	/** Captured application log of the scenario, for other identity steps. */
	String logText() {
		return this.logs.text();
	}

	/** The application of the scenario, for other identity steps. */
	ConfigurableApplicationContext application() {
		return this.application;
	}

	String sessionToken() {
		return this.sessionToken;
	}

	String csrfToken() {
		return this.csrfToken;
	}

	/** REQ-175 AC3: the scenario's log output contains no password, session token, TOTP secret or code. */
	@After(value = "@UC-01 or @UC-03 or @UC-23", order = 20000)
	public void logsWithoutSecrets() {
		if (this.logs == null) {
			return;
		}
		String output = this.logs.text();
		assertThat(output).as("captured log output").isNotEmpty();
		for (String secret : this.secrets) {
			assertThat(output.contains(secret)).as("log contains a secret of length " + secret.length()).isFalse();
		}
		for (String code : this.codes) {
			// a code is a standalone six-digit number in the log, never part of a longer one
			assertThat(Pattern.compile("(?<!\\d)" + code + "(?!\\d)").matcher(output).find())
				.as("log contains a TOTP code")
				.isFalse();
		}
	}

	@After(value = "@UC-01 or @UC-03 or @UC-23", order = 10000)
	public void stop() {
		if (this.application != null) {
			this.application.close();
		}
		if (this.logs != null) {
			this.logs.close();
		}
		if (this.postgres != null) {
			this.postgres.stop();
		}
	}

	private HttpResponse<String> login(String loginName, String password) throws Exception {
		this.secrets.add(password);
		var body = JsonMapper.shared().createObjectNode().put("loginName", loginName).put("password", password);
		return post("/api/v1/auth/local/login", body.toString(), false);
	}

	private HttpResponse<String> loginTotp(String transaction, String code) throws Exception {
		return post("/api/v1/auth/local/login/totp",
				"{\"loginTransaction\":\"" + transaction + "\",\"code\":\"" + code + "\"}", false);
	}

	private void startSession(HttpResponse<String> login) {
		String cookie = login.headers().firstValue("Set-Cookie").orElseThrow();
		this.sessionToken = cookie.substring((COOKIE + "=").length(), cookie.indexOf(';'));
		this.csrfToken = json(login).path("csrfToken").asString();
		this.secrets.add(this.sessionToken);
		this.secrets.add(this.csrfToken);
	}

	/**
	 * A valid code that has not been used yet: codes of the current and the next time step are
	 * accepted (window ±1), and every accepted step must be newer than the last one (REQ-170).
	 */
	private String nextCode() throws InterruptedException {
		long now = Instant.now().getEpochSecond() / 30;
		while (this.lastTimeStep + 1 > now + 1) {
			Thread.sleep(1000);
			now = Instant.now().getEpochSecond() / 30;
		}
		this.lastTimeStep = Math.max(now, this.lastTimeStep + 1);
		this.lastCode = TotpAlgorithm.code(this.totpSecret, this.lastTimeStep);
		this.codes.add(this.lastCode);
		return this.lastCode;
	}

	private String wrongCode() {
		long now = Instant.now().getEpochSecond() / 30;
		Set<String> valid = new HashSet<>();
		for (long step = now - 2; step <= now + 2; step++) {
			valid.add(TotpAlgorithm.code(this.totpSecret, step));
		}
		SecureRandom random = new SecureRandom();
		String code;
		do {
			code = "%06d".formatted(random.nextInt(1_000_000));
		}
		while (valid.contains(code));
		this.codes.add(code);
		return code;
	}

	HttpResponse<String> post(String path, String body, boolean withSession) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body));
		if (withSession) {
			request.header("Cookie", COOKIE + "=" + this.sessionToken).header("X-XSRF-TOKEN", this.csrfToken);
		}
		return send(request);
	}

	HttpResponse<String> put(String path, String body) throws Exception {
		return send(HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "application/json")
			.header("Cookie", COOKIE + "=" + this.sessionToken)
			.header("X-XSRF-TOKEN", this.csrfToken)
			.PUT(HttpRequest.BodyPublishers.ofString(body)));
	}

	HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return this.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	URI uri(String path) {
		return URI.create("http://localhost:" + this.application.getEnvironment().getProperty("local.server.port") + path);
	}

	private String auditCount(String eventType) throws SQLException {
		return single("SELECT count(*)::text FROM audit.events WHERE event_type = '" + eventType + "'");
	}

	private String secret(int length) {
		SecureRandom random = new SecureRandom();
		StringBuilder value = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return value.toString();
	}

	static JsonNode json(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

	private static JsonNode withoutInstance(JsonNode problem) {
		var copy = problem.deepCopy();
		((tools.jackson.databind.node.ObjectNode) copy).remove("instance");
		return copy;
	}

	String single(String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			assertThat(rs.next()).as(sql).isTrue();
			return rs.getString(1);
		}
	}

	void superuser(String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

}

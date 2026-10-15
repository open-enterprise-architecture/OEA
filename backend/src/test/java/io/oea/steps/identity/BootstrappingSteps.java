package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.logging.LogCapture;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/bootstrapping.feature: the real application with its web
 * server on a fresh database per scenario, driven over HTTP like the setup page does.
 */
public class BootstrappingSteps {

	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	private final HttpClient http = HttpClient.newHttpClient();

	private final List<String> responseBodies = new ArrayList<>();

	private final List<String> passwords = new ArrayList<>();

	private PostgreSQLContainer postgres;

	private ConfigurableApplicationContext application;

	private LogCapture logs;

	private Path tokenFile;

	private String token;

	private HttpResponse<String> response;

	private List<Integer> statuses;

	@Given("a freshly installed OEA instance without a system admin account")
	public void freshInstance() throws IOException {
		this.postgres = OeaDatabases.freshMigrated();
		this.tokenFile = Files.createTempDirectory("oea-run").resolve("setup-token");
		this.logs = LogCapture.attach();
		this.application = OeaApplications.start(this.postgres, this.tokenFile, WebApplicationType.SERVLET);
		this.token = Files.readString(this.tokenFile).strip();
	}

	@Given("the instance has been bootstrapped as {string}")
	public void bootstrapped(String loginName) throws Exception {
		submit(this.token, loginName, password(24), "acme-corp");
		assertThat(this.response.statusCode()).isEqualTo(201);
	}

	@When("the setup status is requested")
	public void requestStatus() throws Exception {
		this.response = send(HttpRequest.newBuilder(uri("/api/v1/setup/status")).GET());
	}

	@When("^the operator submits (the setup token|no setup token|a wrong setup token|an expired token), login name \"(.*)\", a (\\d+)-character password and instance slug \"(.*)\"$")
	public void submitSetup(String tokenChoice, String loginName, int passwordLength, String slug) throws Exception {
		String presented = switch (tokenChoice) {
			case "the setup token" -> this.token;
			case "no setup token" -> null;
			case "a wrong setup token" -> "wrong-" + this.token.substring(6);
			case "an expired token" -> {
				superuser("UPDATE bootstrap_setup_tokens SET expires_at = now() - interval '1 second'");
				yield this.token;
			}
			default -> throw new IllegalArgumentException(tokenChoice);
		};
		submit(presented, loginName, password(passwordLength), slug);
	}

	@When("two bootstrapping requests with the valid setup token are submitted at the same time")
	public void twoAttempts() {
		List<CompletableFuture<HttpResponse<String>>> attempts = List.of(asyncSubmit(), asyncSubmit());
		this.statuses = attempts.stream().map(CompletableFuture::join).map(HttpResponse::statusCode).toList();
	}

	@Then("setup is reported as required")
	public void setupRequired() {
		assertThat(this.response.statusCode()).isEqualTo(200);
		assertThat(json(this.response).path("setupRequired").asBoolean()).isTrue();
	}

	@Then("no identity provider is configured")
	public void noIdentityProvider() {
		assertThat(this.application.getEnvironment().getProperty("oea.security.oidc.providers[0].issuer")).isNull();
	}

	@Then("the setup succeeds")
	public void setupSucceeds() {
		assertThat(this.response.statusCode()).isEqualTo(201);
	}

	@Then("the system admin account {string} exists with status {string}")
	public void accountExists(String loginName, String status) throws SQLException {
		assertThat(single("SELECT status FROM system_admin_accounts WHERE login_name = '" + loginName + "'"))
			.isEqualTo(status);
	}

	@Then("the instance slug is {string}")
	public void instanceSlug(String slug) throws SQLException {
		assertThat(single("SELECT instance_slug FROM system_settings")).isEqualTo(slug);
	}

	@Then("the password is stored only as an argon2id hash")
	public void passwordHashed() throws SQLException {
		String hash = single("SELECT password_hash FROM password_credentials");
		assertThat(hash).startsWith("$argon2id$").doesNotContain(this.passwords.getLast());
	}

	@Then("the setup status reports that no setup is required")
	public void noSetupRequired() throws Exception {
		requestStatus();
		assertThat(json(this.response).path("setupRequired").asBoolean()).isFalse();
	}

	@Then("the setup is rejected with status {int} and problem type {string}")
	public void rejected(int status, String type) {
		assertThat(this.response.statusCode()).isEqualTo(status);
		assertThat(this.response.headers().firstValue("Content-Type")).hasValueSatisfying(
				(contentType) -> assertThat(contentType).startsWith("application/problem+json"));
		assertThat(json(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + type);
	}

	@Then("no system admin account exists")
	public void noAccount() throws SQLException {
		assertThat(single("SELECT count(*)::text FROM system_admin_accounts")).isEqualTo("0");
	}

	@Then("the system admin account {string} is the only account")
	public void onlyAccount(String loginName) throws SQLException {
		assertThat(single("SELECT string_agg(login_name, ',') FROM system_admin_accounts")).isEqualTo(loginName);
	}

	@Then("the setup token file is readable only by the OEA user")
	public void tokenFileOwnerOnly() throws IOException {
		assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(this.tokenFile))).isEqualTo("rw-------");
		assertThat(Files.getOwner(this.tokenFile).getName()).isEqualTo(System.getProperty("user.name"));
	}

	@Then("the application log names the token file but does not contain the token")
	public void logWithoutToken() {
		assertThat(this.logs.text()).contains(this.tokenFile.toString()).doesNotContain(this.token);
	}

	@Then("no API response contains the token")
	public void noResponseWithToken() throws Exception {
		requestStatus();
		submit("wrong-token", "admin", password(24), "acme-corp");
		submit(this.token, "Not Valid", password(5), "x");
		assertThat(this.responseBodies).isNotEmpty().noneMatch((body) -> body.contains(this.token));
	}

	@Then("exactly one succeeds and the other is rejected with status {int}")
	public void exactlyOne(int status) throws SQLException {
		assertThat(this.statuses).containsExactlyInAnyOrder(201, status);
		assertThat(single("SELECT count(*)::text FROM system_admin_accounts")).isEqualTo("1");
	}

	@Then("^the audit trail contains \"(.*)\" with mode \"(.*)\" and outcome \"(.*)\" by (the new system admin account|an unauthenticated actor)$")
	public void auditEvent(String eventType, String mode, String outcome, String actor) throws SQLException {
		String row = single("""
				SELECT details ->> 'mode', outcome, actor_type, coalesce(actor_admin_id::text, ''),
				       to_char(occurred_at, 'YYYY') FROM audit.events WHERE event_type = '%s'""".formatted(eventType),
				5);
		String[] values = row.split("\\|", -1);
		assertThat(values[0]).isEqualTo(mode);
		assertThat(values[1]).isEqualTo(outcome);
		assertThat(values[4]).as("timestamp").isNotEmpty();
		if (actor.equals("the new system admin account")) {
			assertThat(values[2]).isEqualTo("system-admin-account");
			assertThat(values[3]).isEqualTo(single("SELECT id::text FROM system_admin_accounts"));
		}
		else {
			assertThat(values[2]).isEqualTo("unauthenticated");
			assertThat(values[3]).isEmpty();
		}
	}

	@Then("no audit record contains the setup token or the password")
	public void auditWithoutSecrets() throws SQLException {
		List<String> secrets = new ArrayList<>(this.passwords);
		secrets.add(this.token);
		for (String secret : secrets) {
			try (Connection su = OeaDatabases.asSuperuser(this.postgres); PreparedStatement select = su
				.prepareStatement("SELECT count(*) FROM audit.events WHERE strpos(coalesce(details::text, ''), ?) > 0")) {
				select.setString(1, secret);
				try (ResultSet rs = select.executeQuery()) {
					rs.next();
					assertThat(rs.getInt(1)).isZero();
				}
			}
		}
	}

	/** REQ-175 AC3: the scenario's log output contains no setup token, password or password hash. */
	@After(value = "@UC-02", order = 20000)
	public void logsWithoutSecrets() throws SQLException {
		if (this.logs == null) {
			return;
		}
		List<String> secrets = new ArrayList<>(this.passwords);
		secrets.add(this.token);
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("SELECT password_hash FROM password_credentials")) {
			while (rs.next()) {
				secrets.add(rs.getString(1));
				// the hash part of the PHC string alone must not appear either
				secrets.add(rs.getString(1).substring(rs.getString(1).lastIndexOf('$') + 1));
			}
		}
		String output = this.logs.text();
		assertThat(output).as("captured log output").isNotEmpty();
		for (String secret : secrets) {
			assertThat(output.contains(secret)).as("log contains a secret of length " + secret.length()).isFalse();
		}
	}

	@After(value = "@UC-02", order = 10000)
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

	private void submit(String token, String loginName, String password, String slug) throws Exception {
		this.response = send(HttpRequest.newBuilder(uri("/api/v1/setup"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body(token, loginName, password, slug))));
	}

	private CompletableFuture<HttpResponse<String>> asyncSubmit() {
		return this.http.sendAsync(HttpRequest.newBuilder(uri("/api/v1/setup"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body(this.token, "admin", password(24), "acme-corp")))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		HttpResponse<String> result = this.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		this.responseBodies.add(result.body());
		return result;
	}

	private static String body(String token, String loginName, String password, String slug) {
		var node = JsonMapper.shared().createObjectNode();
		if (token != null) {
			node.put("setupToken", token);
		}
		node.put("loginName", loginName);
		node.put("password", password);
		node.put("instanceSlug", slug);
		return node.toString();
	}

	private String password(int length) {
		SecureRandom random = new SecureRandom();
		StringBuilder password = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		this.passwords.add(password.toString());
		return password.toString();
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.application.getEnvironment().getProperty("local.server.port") + path);
	}

	private static JsonNode json(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

	private String single(String sql) throws SQLException {
		return single(sql, 1);
	}

	private String single(String sql, int columns) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			assertThat(rs.next()).as(sql).isTrue();
			List<String> values = new ArrayList<>();
			for (int i = 1; i <= columns; i++) {
				values.add(String.valueOf(rs.getString(i)));
			}
			return String.join("|", values);
		}
	}

	private void superuser(String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres); Statement statement = su.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

}

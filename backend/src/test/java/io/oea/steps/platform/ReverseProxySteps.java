package io.oea.steps.platform;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/platform/reverse-proxy.feature (AA-01; design D3 of s11-review-hardening):
 * an instance with its web server per scenario, driven over real HTTP so that the requests
 * pass Tomcat's {@code RemoteIpValve}. The step client connects from loopback, which the
 * default list trusts, and plays the proxy, with client addresses from {@code 203.0.113.0/24}.
 * The instance is closed after each scenario, so that it holds no database connections of the
 * shared PostgreSQL server for the rest of the run.
 */
public class ReverseProxySteps {

	private static final String COOKIE = "__Host-OEA_SESSION";

	private static final String ADMIN = "admin";

	private static final String PASSWORD = "reverse proxy " + UUID.randomUUID();

	private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger(1);

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private OeaDatabase postgres;

	private ConfigurableApplicationContext application;

	private String firstClient;

	private String secondClient;

	private HttpResponse<String> firstLogin;

	private HttpResponse<String> secondLogin;

	private HttpResponse<String> logout;

	private String sessionToken;

	private String csrfToken;

	@Given("an instance behind a trusted reverse proxy with a local system admin")
	public void instance() throws Exception {
		this.firstClient = nextAddress();
		this.secondClient = nextAddress();
		this.postgres = OeaDatabases.freshMigrated();
		Path tokenFile = Files.createTempDirectory("oea-proxy").resolve("setup-token");
		this.application = OeaApplications.start(this.postgres, tokenFile, WebApplicationType.SERVLET);
		String body = JsonMapper.shared()
			.createObjectNode()
			.put("setupToken", Files.readString(tokenFile).strip())
			.put("loginName", ADMIN)
			.put("password", PASSWORD)
			.put("instanceSlug", "acme-corp")
			.toString();
		HttpResponse<String> setup = send(request("/api/v1/setup").header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
		assertThat(setup.statusCode()).as(setup.body()).isEqualTo(201);
	}

	/** Releases the connections of the instance as soon as the scenario ends. */
	@After
	public void stop() {
		if (this.application != null) {
			this.application.close();
			this.postgres.stop();
			this.application = null;
		}
	}

	@When("{int} logins with different unknown login names fail through the proxy from a first client address")
	public void failedLogins(int count) throws Exception {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		for (int i = 0; i < count; i++) {
			assertThat(login("unknown-" + prefix + "-" + i, "wrong", this.firstClient).statusCode()).isEqualTo(401);
		}
	}

	@When("a login with an unknown login name fails through the proxy from a first client address")
	public void failedLogin() throws Exception {
		failedLogins(1);
	}

	@When("the system admin logs in through the proxy from the first client address")
	public void loginFromFirst() throws Exception {
		this.firstLogin = login(ADMIN, PASSWORD, this.firstClient);
	}

	@When("the system admin logs in through the proxy from a second client address")
	public void loginFromSecond() throws Exception {
		this.secondLogin = login(ADMIN, PASSWORD, this.secondClient);
	}

	@Then("the login from the first client address is answered with 401 and a waiting time")
	public void firstThrottled() {
		assertThat(this.firstLogin.statusCode()).isEqualTo(401);
		assertThat(this.firstLogin.headers().firstValue("Retry-After")).isPresent();
	}

	@Then("the login from the first client address succeeds")
	public void firstSucceeds() {
		assertThat(this.firstLogin.statusCode()).as(this.firstLogin.body()).isEqualTo(201);
	}

	@Then("the login from the second client address succeeds")
	public void secondSucceeds() {
		assertThat(this.secondLogin.statusCode()).as(this.secondLogin.body()).isEqualTo(201);
	}

	@Then("the audit event {string} records the first client address as source address")
	public void auditSource(String eventType) throws SQLException {
		assertThat(auditEvents(eventType, this.firstClient)).isEqualTo(1);
	}

	@Given("the system admin is logged in through the proxy at {string}")
	public void loggedIn(String origin) throws Exception {
		HttpResponse<String> login = send(proxied(request("/api/v1/auth/local/login"), origin, this.firstClient)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(JsonMapper.shared()
				.createObjectNode()
				.put("loginName", ADMIN)
				.put("password", PASSWORD)
				.toString())));
		assertThat(login.statusCode()).as(login.body()).isEqualTo(201);
		String cookie = login.headers().firstValue("Set-Cookie").orElseThrow();
		this.sessionToken = cookie.substring((COOKIE + "=").length(), cookie.indexOf(';'));
		this.csrfToken = JsonMapper.shared().readTree(login.body()).path("csrfToken").asString();
	}

	@When("the system admin logs out through the proxy with the origin {string}")
	public void logout(String origin) throws Exception {
		this.logout = send(proxied(request("/api/v1/auth/logout"), "https://oea.example.org", this.firstClient)
			.header("Cookie", COOKIE + "=" + this.sessionToken)
			.header(CsrfOriginFilter.HEADER, this.csrfToken)
			.header("Origin", origin)
			.POST(HttpRequest.BodyPublishers.noBody()));
	}

	@Then("the logout is answered with {int}")
	public void logoutAnswered(int status) {
		assertThat(this.logout.statusCode()).as(this.logout.body()).isEqualTo(status);
	}

	@Then("the session has ended")
	public void sessionEnded() throws Exception {
		assertThat(me().statusCode()).isEqualTo(401);
	}

	@Then("the session is still valid")
	public void sessionValid() throws Exception {
		assertThat(me().statusCode()).isEqualTo(200);
	}

	private HttpResponse<String> me() throws Exception {
		return send(request("/api/v1/me").header("Cookie", COOKIE + "=" + this.sessionToken).GET());
	}

	private HttpResponse<String> login(String loginName, String password, String client) throws Exception {
		String body = JsonMapper.shared()
			.createObjectNode()
			.put("loginName", loginName)
			.put("password", password)
			.toString();
		return send(request("/api/v1/auth/local/login").header("Content-Type", "application/json")
			.header("X-Forwarded-For", client)
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	/** Headers of a TLS proxy serving {@code publicOrigin}, e.g. {@code https://oea.example.org}. */
	private static HttpRequest.Builder proxied(HttpRequest.Builder request, String publicOrigin, String client) {
		URI origin = URI.create(publicOrigin);
		return request.header("X-Forwarded-For", client)
			.header("X-Forwarded-Proto", origin.getScheme())
			.header("X-Forwarded-Host", origin.getHost());
	}

	private int auditEvents(String eventType, String address) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(this.postgres);
				PreparedStatement statement = su.prepareStatement(
						"SELECT count(*) FROM audit.events WHERE event_type = ? AND host(source_ip) = ?")) {
			statement.setString(1, eventType);
			statement.setString(2, address);
			try (ResultSet rs = statement.executeQuery()) {
				rs.next();
				return rs.getInt(1);
			}
		}
	}

	private static String nextAddress() {
		// 203.0.113.0/24 (TEST-NET-3); the instance lives for one run, so the addresses are unique per run
		return "203.0.113." + NEXT_ADDRESS.getAndIncrement();
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(
				URI.create("http://localhost:" + this.application.getEnvironment().getProperty("local.server.port") + path));
	}

	private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
		return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

}

package io.oea.app.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.audit.verify.AuditVerifyCommand;
import io.oea.core.service.TotpAlgorithm;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.logging.LogCapture;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec identity/admin-recovery (UC-02 E4, REQ-173 AC4; design D5, D6 of us-019-admin-safeguards)
 * on an own database. One flow, because the steps build on the state of the instance.
 */
class AdminRecoverCommandTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String FIRST = "first-password-123";

	private static final String SECOND = "second-password-123";

	private static final String THIRD = "third-password-1234";

	/** Answer that is replaced by a valid code of the secret shown by the command. */
	private static final String VALID_CODE = "<valid code>";

	private static final Pattern SECRET = Pattern.compile("authenticator app: ([A-Z2-7]+)");

	private final HttpClient http = HttpClient.newHttpClient();

	private ConfigurableApplicationContext application;

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void recoveryRestoresTheLocalAccountAndIsAudited() throws Exception {
		Path key = OeaTestKeys.newKeyFile();
		Script notBootstrapped = new Script(List.of());
		assertThat(run(notBootstrapped, key)).isEqualTo(1);
		assertThat(notBootstrapped.output()).contains("not bootstrapped").contains("/setup");

		Path tokenFile = Files.createTempDirectory("oea-recovery").resolve("setup-token");
		this.application = OeaApplications.start(postgres, tokenFile, WebApplicationType.SERVLET,
				"--oea.security.kek.current=" + key, "--oea.web.public-url=https://oea.example.org");
		try (LogCapture logs = LogCapture.attach()) {
			assertThat(post("/api/v1/setup", JsonMapper.shared()
				.createObjectNode()
				.put("setupToken", Files.readString(tokenFile).strip())
				.put("loginName", "admin")
				.put("password", FIRST)
				.put("instanceSlug", "acme-corp")
				.toString(), null).statusCode()).isEqualTo(201);

			// during the setup phase: invalid passwords are asked again, no second factor is asked
			Script setupPhase = new Script(List.of("too-short", "valid-password-1", "other-password-1", SECOND, SECOND));
			assertThat(run(setupPhase, key)).isEqualTo(0);
			assertThat(setupPhase.output()).contains("12 to 128 characters").contains("The passwords differ.")
				.doesNotContain("authenticator");
			assertThat(login(FIRST).statusCode()).isEqualTo(401);
			HttpResponse<String> setupLogin = login(SECOND);
			assertThat(setupLogin.statusCode()).isEqualTo(201);

			// the admin enrolls a factor, the setup phase ends, the account is deactivated
			String[] session = { setupLogin.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0],
					json(setupLogin).path("csrfToken").asString() };
			JsonNode enrollment = json(post("/api/v1/me/second-factors/totp", "{}", session));
			String oldSecret = enrollment.path("secret").asString();
			assertThat(post("/api/v1/me/second-factors/totp/" + enrollment.path("credentialId").asString() + "/confirm",
					"{\"code\":\"" + TotpAlgorithm.code(oldSecret, Instant.now().getEpochSecond() / 30) + "\"}", session)
				.statusCode()).isEqualTo(200);
			superuser("""
					UPDATE system_settings SET setup_phase_ended_at = now(), setup_phase_ended_by_action = 'identity-binding';
					UPDATE system_admin_accounts SET status = 'deactivated', deactivated_at = now()""");
			String before = snapshot();

			// three wrong codes: nothing changes
			Script wrongCodes = new Script(List.of(THIRD, THIRD, "abcdef", "abcdef", "abcdef"));
			assertThat(run(wrongCodes, key)).isEqualTo(1);
			assertThat(wrongCodes.output()).contains("nothing was changed");
			assertThat(snapshot()).isEqualTo(before);

			// recovery after the setup phase
			Script recovery = new Script(List.of(THIRD, THIRD, VALID_CODE));
			assertThat(run(recovery, key)).isEqualTo(0);
			assertThat(recovery.output()).contains("OK").contains("1 session(s) revoked");
			assertThat(single("SELECT status FROM system_admin_accounts")).isEqualTo("active");
			assertThat(get("/api/v1/me", session[0]).statusCode()).isEqualTo(401);
			assertThat(single("SELECT count(*)::text FROM local_credentials WHERE type = 'totp' AND status = 'active'"))
				.isEqualTo("1");
			assertThat(login(SECOND).statusCode()).isEqualTo(401);
			assertThat(json(login(SECOND)).path("type").asString()).isEqualTo("urn:oea:problem:invalid-credentials");
			HttpResponse<String> first = login(THIRD);
			assertThat(first.statusCode()).isEqualTo(401);
			assertThat(json(first).path("loginTransaction").asString()).isNotBlank();
			long step = Math.max(Instant.now().getEpochSecond() / 30, recovery.acceptedStep + 1);
			assertThat(post("/api/v1/auth/local/login/totp", "{\"loginTransaction\":\""
					+ json(first).path("loginTransaction").asString() + "\",\"code\":\""
					+ TotpAlgorithm.code(recovery.secret, step) + "\"}", null).statusCode()).isEqualTo(201);
			// the old factor no longer works
			HttpResponse<String> again = login(THIRD);
			assertThat(post("/api/v1/auth/local/login/totp", "{\"loginTransaction\":\""
					+ json(again).path("loginTransaction").asString() + "\",\"code\":\""
					+ TotpAlgorithm.code(oldSecret, Instant.now().getEpochSecond() / 30) + "\"}", null).statusCode())
				.isNotEqualTo(201);

			// audit: two recoveries with source CLI, an intact chain, no password or secret anywhere
			assertThat(single("SELECT count(*)::text FROM audit.events WHERE event_type = 'system.admin-recovered' "
					+ "AND source = 'CLI' AND outcome = 'success'")).isEqualTo("2");
			assertThat(single("SELECT details ->> 'secondFactorReplaced' FROM audit.events "
					+ "WHERE event_type = 'system.admin-recovered' ORDER BY id DESC LIMIT 1")).isEqualTo("true");
			String audit = single("SELECT string_agg(details::text, ' ') FROM audit.events");
			for (String secret : List.of(SECOND, THIRD, recovery.secret)) {
				assertThat(audit).doesNotContain(secret);
				assertThat(logs.text()).doesNotContain(secret);
			}
			ByteArrayOutputStream verify = new ByteArrayOutputStream();
			assertThat(AuditVerifyCommand.run(new PrintStream(verify, true, StandardCharsets.UTF_8),
					"--oea.audit-verify.url=" + postgres.getJdbcUrl(),
					"--oea.audit-verify.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_auditor")))
				.as(verify.toString(StandardCharsets.UTF_8))
				.isZero();
		}
		finally {
			this.application.close();
		}
	}

	private static int run(Script script, Path key) {
		return AdminRecoverCommand.run(script, "--spring.datasource.url=" + postgres.getJdbcUrl(),
				"--spring.datasource.username=oea_app",
				"--spring.datasource.password=" + OeaPostgres.ROLE_PASSWORDS.get("oea_app"),
				"--oea.security.kek.current=" + key);
	}

	/** State the failed recovery must leave unchanged. */
	private static String snapshot() throws Exception {
		return single("SELECT (SELECT status FROM system_admin_accounts) || '|' || (SELECT string_agg(type || ':' "
				+ "|| status, ',' ORDER BY created_at, type) FROM local_credentials) || '|' || (SELECT count(*) FROM "
				+ "sessions WHERE revoked_at IS NULL)");
	}

	private HttpResponse<String> login(String password) throws Exception {
		return post("/api/v1/auth/local/login", "{\"loginName\":\"admin\",\"password\":\"" + password + "\"}", null);
	}

	private HttpResponse<String> get(String path, String cookie) throws Exception {
		return this.http.send(HttpRequest.newBuilder(uri(path)).header("Cookie", cookie).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String body, String[] session) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body));
		if (session != null) {
			request.header("Cookie", session[0]).header("X-XSRF-TOKEN", session[1]);
		}
		return this.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.application.getEnvironment().getProperty("local.server.port") + path);
	}

	private static JsonNode json(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

	private static String single(String sql) throws Exception {
		try (Connection connection = OeaDatabases.asSuperuser(postgres); Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

	private static void superuser(String sql) throws Exception {
		try (Connection connection = OeaDatabases.asSuperuser(postgres); Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	/** Scripted operator: answers in order; {@link #VALID_CODE} is computed from the shown secret. */
	private static final class Script implements AdminRecoverCommand.Terminal {

		private final Deque<String> answers;

		private final List<String> lines = new ArrayList<>();

		private String secret;

		private long acceptedStep;

		Script(List<String> answers) {
			this.answers = new ArrayDeque<>(answers);
		}

		@Override
		public String readSecret(String prompt) {
			return this.answers.poll();
		}

		@Override
		public String readLine(String prompt) {
			String answer = this.answers.poll();
			if (VALID_CODE.equals(answer)) {
				this.acceptedStep = Instant.now().getEpochSecond() / 30;
				return TotpAlgorithm.code(this.secret, this.acceptedStep);
			}
			return answer;
		}

		@Override
		public void println(String line) {
			this.lines.add(line);
			Matcher matcher = SECRET.matcher(line);
			if (matcher.find()) {
				this.secret = matcher.group(1);
			}
		}

		String output() {
			return String.join("\n", this.lines);
		}

	}

}

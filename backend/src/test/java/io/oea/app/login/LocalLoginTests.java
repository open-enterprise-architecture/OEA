package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.bootstrapping.BootstrapCommand;
import io.oea.app.bootstrapping.Bootstrapping;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.service.TotpAlgorithm;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.time.MutableClock;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Specs identity/local-login and identity/sessions (cookie) on an own database. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(LocalLoginTests.ClockConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalLoginTests {

	private static final PostgreSQLContainer postgres = OeaDatabases.freshMigrated();

	private static final Path tokenFile = tempFile();

	private static final String PASSWORD = "correct horse battery staple";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", tokenFile::toString);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private Bootstrapping bootstrapping;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MutableClock clock;

	private String loginName;

	@BeforeEach
	void systemAdmin() throws Exception {
		// every test starts from a bootstrapped instance in the setup phase with its own admin
		this.jdbc.update("DELETE FROM auth_throttle");
		if (this.bootstrapping.setupRequired()) {
			this.bootstrapping.bootstrap(new BootstrapCommand(Files.readString(tokenFile).strip(), "admin", PASSWORD,
					"acme-corp"));
		}
		this.loginName = "admin";
		this.clock.advance(Duration.ofHours(1));
	}

	@Test
	void loginAfterBootstrappingDuringTheSetupPhase() {
		MvcTestResult result = login(this.loginName, PASSWORD, "10.0.0.1");
		assertThat(result).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"principal": {"principalType": "system-admin-account", "displayName": "admin"}, "mfaSatisfied": false}""");
		String cookie = result.getResponse().getHeader("Set-Cookie");
		assertThat(cookie).startsWith("__Host-OEA_SESSION=")
			.contains("Path=/")
			.contains("Secure")
			.contains("HttpOnly")
			.contains("SameSite=Strict");
		String token = cookie.substring("__Host-OEA_SESSION=".length(), cookie.indexOf(';'));
		assertThat(body(result).toString()).doesNotContain(token);
		assertThat(get("/api/v1/me", session(result))).bodyJson()
			.extractingPath("$.roles[*].role.id")
			.asArray()
			.containsExactly("system-admin");
		assertThat(this.jdbc.queryForObject("SELECT status FROM system_admin_accounts WHERE login_name = 'admin'",
				String.class))
			.isEqualTo("active");
		assertThat(count("auth.login")).isPositive();
	}

	@Test
	void unknownLoginNameAndWrongPasswordGetTheSameResponse() throws Exception {
		int before = count("auth.login-failed");
		MvcTestResult unknown = login("nobody", PASSWORD, "10.0.0.2");
		MvcTestResult wrong = login(this.loginName, "wrong password value", "10.0.0.2");
		assertThat(unknown).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(wrong).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(withoutInstance(body(unknown))).isEqualTo(withoutInstance(body(wrong)));
		assertThat(body(wrong).path("type").asString()).isEqualTo("urn:oea:problem:invalid-credentials");
		assertThat(count("auth.login-failed")).isEqualTo(before + 2);
	}

	@Test
	void repeatedWrongPasswordsAreThrottledTemporarily() {
		for (int i = 0; i < 5; i++) {
			assertThat(login(this.loginName, "wrong password " + i, "10.0.0.3")).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		MvcTestResult throttled = login(this.loginName, PASSWORD, "10.0.0.4");
		assertThat(throttled).hasStatus(HttpStatus.UNAUTHORIZED).headers().containsHeader("Retry-After");
		MvcTestResult unknownThrottled = login("nobody-else", "x", "10.0.0.4");
		assertThat(withoutInstance(body(unknownThrottled))).isEqualTo(withoutInstance(body(throttled)));

		this.clock.advance(Duration.ofSeconds(2));
		assertThat(login(this.loginName, PASSWORD, "10.0.0.4")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void outdatedHashParametersAreUpgradedAtLogin() {
		String weak = new Argon2PasswordEncoder(16, 32, 1, 4096, 1).encode(PASSWORD);
		this.jdbc.update("UPDATE password_credentials SET password_hash = ?", weak);
		assertThat(login(this.loginName, PASSWORD, "10.0.0.5")).hasStatus(HttpStatus.CREATED);
		assertThat(this.jdbc.queryForObject("SELECT password_hash FROM password_credentials", String.class))
			.startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
	}

	@Test
	void manyNamesFromOneAddressAreThrottled() {
		int before = count("auth.throttled");
		for (int i = 0; i < 100; i++) {
			login("unknown-" + i, "x", "10.9.9.9");
		}
		assertThat(login(this.loginName, PASSWORD, "10.9.9.9")).hasStatus(HttpStatus.UNAUTHORIZED)
			.headers()
			.containsHeader("Retry-After");
		assertThat(count("auth.throttled")).isGreaterThan(before);
		assertThat(login(this.loginName, PASSWORD, "10.9.9.10")).hasStatus(HttpStatus.CREATED);
	}

	// ends the setup phase irreversibly, so it runs last
	@Test
	@Order(Integer.MAX_VALUE)
	void enrollmentConfirmationAndReplay() {
		Session session = session(login(this.loginName, PASSWORD, "10.0.1.1"));
		MvcTestResult started = post("/api/v1/me/second-factors/totp", session, "{\"label\":\"phone\"}");
		assertThat(started).hasStatus(HttpStatus.CREATED).headers().hasValue("Cache-Control", "no-store");
		JsonNode enrollment = body(started);
		String secret = enrollment.path("secret").asString();
		assertThat(enrollment.path("otpauthUri").asString()).startsWith("otpauth://totp/OEA%3Aadmin?secret=" + secret);

		String code = TotpAlgorithm.code(secret, this.clock.instant().getEpochSecond() / 30);
		String confirm = "/api/v1/me/second-factors/totp/" + enrollment.path("credentialId").asString() + "/confirm";
		assertThat(post(confirm, session, "{\"code\":\"" + code + "\"}")).hasStatus(HttpStatus.OK);
		assertThat(get("/api/v1/me", session)).bodyJson().extractingPath("$.mfaSatisfied").isEqualTo(true);
		assertThat(count("auth.factor-added")).isPositive();
		assertThat(post(confirm, session, "{\"code\":\"" + code + "\"}")).hasStatus(HttpStatus.NOT_FOUND);

		// after the setup phase the TOTP step is required, and an accepted code is never accepted again
		this.jdbc.update("UPDATE system_settings SET setup_phase_ended_at = now(), "
				+ "setup_phase_ended_by_action = 'idp-group-mapping' WHERE setup_phase_ended_at IS NULL");
		MvcTestResult first = login(this.loginName, PASSWORD, "10.0.1.2");
		assertThat(first).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(first).path("type").asString()).isEqualTo("urn:oea:problem:second-factor-required");
		assertThat(first.getResponse().getHeader("Set-Cookie")).isNull();
		String transaction = body(first).path("loginTransaction").asString();
		this.clock.advance(Duration.ofSeconds(30));
		String fresh = TotpAlgorithm.code(secret, this.clock.instant().getEpochSecond() / 30);
		MvcTestResult second = loginTotp(transaction, fresh);
		assertThat(second).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.mfaSatisfied").isEqualTo(true);

		String replayTransaction = body(login(this.loginName, PASSWORD, "10.0.1.2")).path("loginTransaction").asString();
		assertThat(loginTotp(replayTransaction, fresh)).hasStatus(HttpStatus.UNAUTHORIZED);

		// five wrong codes invalidate the transaction
		for (int i = 0; i < 5; i++) {
			assertThat(loginTotp(replayTransaction, "000000".equals(fresh) ? "111111" : "000000"))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		}
		this.clock.advance(Duration.ofSeconds(30));
		String later = TotpAlgorithm.code(secret, this.clock.instant().getEpochSecond() / 30);
		assertThat(loginTotp(replayTransaction, later)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void enrollmentInAnOldSessionNeedsReauthentication() {
		Session session = session(login(this.loginName, PASSWORD, "10.0.2.1"));
		this.clock.advance(Duration.ofMinutes(6));
		MvcTestResult result = post("/api/v1/me/second-factors/totp", session, "{}");
		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:reauthentication-required");
	}

	record Session(String token, String csrfToken) {
	}

	private Session session(MvcTestResult login) {
		assertThat(login).hasStatus(HttpStatus.CREATED);
		String cookie = login.getResponse().getHeader("Set-Cookie");
		return new Session(cookie.substring("__Host-OEA_SESSION=".length(), cookie.indexOf(';')),
				body(login).path("csrfToken").asString());
	}

	private MvcTestResult login(String loginName, String password, String address) {
		return this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.with((request) -> {
				request.setRemoteAddr(address);
				return request;
			})
			.contentType("application/json")
			.content(JsonMapper.shared()
				.createObjectNode()
				.put("loginName", loginName)
				.put("password", password)
				.toString())
			.exchange();
	}

	private MvcTestResult loginTotp(String transaction, String code) {
		return this.mvc.post()
			.uri("/api/v1/auth/local/login/totp")
			.contentType("application/json")
			.content("{\"loginTransaction\":\"" + transaction + "\",\"code\":\"" + code + "\"}")
			.exchange();
	}

	private MvcTestResult post(String uri, Session session, String body) {
		return this.mvc.post()
			.uri(uri)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body)
			.exchange();
	}

	private MvcTestResult get(String uri, Session session) {
		return this.mvc.get().uri(uri).cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
	}

	private int count(String eventType) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ?", Integer.class,
				eventType);
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static JsonNode withoutInstance(JsonNode problem) {
		var copy = problem.deepCopy();
		((tools.jackson.databind.node.ObjectNode) copy).remove("instance");
		return copy;
	}

	private static Path tempFile() {
		try {
			return Files.createTempDirectory("oea-login").resolve("setup-token-" + UUID.randomUUID());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}

	}

}

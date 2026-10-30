package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.service.PasswordHasher;
import io.oea.core.service.TotpAlgorithm;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.time.MutableClock;
import io.oea.testsupport.webauthn.SoftwarePasskey;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec identity/passkeys and the passkey parts of identity/person-totp (US-022, US-009, US-025
 * AC2; design D1 to D5 of us-022-passkeys) with a software authenticator, the public URL
 * {@code https://oea.example.org} and password login of persons switched on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PasskeyTests.ClockConfiguration.class)
class PasskeyTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String ORIGIN = "https://oea.example.org";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-passkeys").resolve("setup-token");
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", tokenFile::toString);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
		registry.add("oea.auth.person-password.enabled", () -> "true");
		registry.add("oea.web.public-url", () -> ORIGIN);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static final String PASSWORD = "chosen-password-456";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@Autowired
	private PasswordHasher hasher;

	@Autowired
	private MutableClock clock;

	private TestSession personAdmin;

	@BeforeEach
	void administrator() {
		this.jdbc.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'identity-binding') ON CONFLICT (id) DO NOTHING""");
		this.jdbc.update("UPDATE system_settings SET person_password_login = true");
		this.personAdmin = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("security-admin");
	}

	@Test
	void registeredPasskeyLogsInWithoutPassword() {
		long anna = active("anna");
		this.jdbc.update("""
				INSERT INTO role_assignments (person_id, role_id, granted_by_person_id, valid_from)
				VALUES (?, 'metamodel-admin', ?, now() - interval '1 hour')""", anna, personId(this.personAdmin));
		TestSession session = session(login("anna@example.org"));
		SoftwarePasskey laptop = new SoftwarePasskey(ORIGIN);
		JsonNode started = body(post("/api/v1/me/second-factors/passkey", session, "{\"label\":\"Laptop\"}"));
		JsonNode options = started.path("options");
		assertThat(options.path("rp").path("id").asString()).isEqualTo("oea.example.org");
		assertThat(options.path("authenticatorSelection").path("userVerification").asString()).isEqualTo("required");
		assertThat(options.path("authenticatorSelection").path("residentKey").asString()).isEqualTo("required");
		assertThat(options.path("attestation").asString()).isEqualTo("none");
		assertThat(options.path("user").path("id").asString()).doesNotContain("anna");
		MvcTestResult confirmed = post("/api/v1/me/second-factors/passkey/" + started.path("registrationId").asString()
				+ "/confirm", session, laptop.register(options, true));
		assertThat(confirmed).hasStatus(HttpStatus.OK);
		assertThat(body(confirmed).path("type").asString()).isEqualTo("passkey");
		assertThat(body(confirmed).path("label").asString()).isEqualTo("Laptop");
		assertThat(body(get("/api/v1/me", session)).path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(audit("auth.factor-added", "type")).isEqualTo("passkey");
		assertThat(body(get("/api/v1/me/second-factors", session)).get(0).path("type").asString()).isEqualTo("passkey");

		MvcTestResult loggedIn = passkeyLogin(laptop, true);
		assertThat(loggedIn).hasStatus(HttpStatus.CREATED);
		assertThat(body(loggedIn).path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(effective(session(loggedIn), "metamodel-admin")).isTrue();
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'method' FROM audit.events WHERE event_type = 'auth.login' AND actor_person_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, anna)).isEqualTo("passkey");
	}

	@Test
	void registrationsThatFailTheVerification() {
		active("ben");
		TestSession session = session(login("ben@example.org"));
		SoftwarePasskey passkey = new SoftwarePasskey(ORIGIN);

		passkey.moveTo("https://evil.example");
		assertRejected(session, (options) -> passkey.register(options, true));
		passkey.moveTo(ORIGIN);
		assertRejected(session, (options) -> passkey.register(options, true, "evil.example"));
		assertRejected(session, (options) -> passkey.register(options, false));
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM local_credentials c JOIN persons p ON p.id = c.person_id
				WHERE p.email = 'ben@example.org' AND c.type = 'passkey'""", Integer.class)).isZero();

		// expired registration
		JsonNode started = body(post("/api/v1/me/second-factors/passkey", session, "{}"));
		this.clock.advance(Duration.ofMinutes(11));
		try {
			assertThat(post("/api/v1/me/second-factors/passkey/" + started.path("registrationId").asString()
					+ "/confirm", session, passkey.register(started.path("options"), true)))
				.hasStatus(HttpStatus.NOT_FOUND);
			assertThat(audit("auth.factor-enrollment-failed", "reason")).isEqualTo("enrollment-expired");
		}
		finally {
			this.clock.advance(Duration.ofMinutes(-11));
		}
		// only persons with a local password
		assertThat(post("/api/v1/me/second-factors/passkey", this.personAdmin, "{}")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void secondDeviceAndLabelDefault() {
		active("carla");
		TestSession session = session(login("carla@example.org"));
		SoftwarePasskey laptop = new SoftwarePasskey(ORIGIN);
		SoftwarePasskey phone = new SoftwarePasskey(ORIGIN);
		register(session, laptop, "{}");
		register(session, phone, "{\"label\":\"Phone\"}");
		JsonNode factors = body(get("/api/v1/me/second-factors", session));
		assertThat(factors).hasSize(2);
		assertThat(factors.toString()).contains("\"label\":\"Phone\"").contains("\"label\":\"Passkey (");
		assertThat(passkeyLogin(laptop, true)).hasStatus(HttpStatus.CREATED);
		assertThat(passkeyLogin(phone, true)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void failedPasskeyLoginsGiveTheAnswerOfAWrongPassword() {
		long dora = active("dora");
		TestSession session = session(login("dora@example.org"));
		SoftwarePasskey passkey = new SoftwarePasskey(ORIGIN);
		register(session, passkey, "{}");
		JsonNode reference = withoutInstance(body(login("dora@example.org", "wrong password 1")));

		// missing user verification
		assertThat(withoutInstance(body(passkeyLogin(passkey, false)))).isEqualTo(reference);
		// wrong signature
		JsonNode challenge = body(post("/api/v1/auth/passkey/options", null, ""));
		ObjectNode tampered = (ObjectNode) JsonMapper.shared()
			.readTree(passkey.authenticate(challenge.path("options"), true));
		((ObjectNode) tampered.path("response")).put("signature", "AAAA");
		assertThat(withoutInstance(body(post("/api/v1/auth/passkey/login", null, loginBody(challenge, tampered.toString())))))
			.isEqualTo(reference);
		// signature counter going backwards
		this.jdbc.update("""
				UPDATE passkey_credentials SET sign_count = 1000000
				WHERE credential_id IN (SELECT id FROM local_credentials WHERE person_id = ? AND type = 'passkey')""", dora);
		assertThat(withoutInstance(body(passkeyLogin(passkey, true)))).isEqualTo(reference);
		this.jdbc.update("""
				UPDATE passkey_credentials SET sign_count = 0
				WHERE credential_id IN (SELECT id FROM local_credentials WHERE person_id = ? AND type = 'passkey')""", dora);
		// person not allowed to log in
		this.jdbc.update("UPDATE persons SET status = 'inactive' WHERE id = ?", dora);
		assertThat(withoutInstance(body(passkeyLogin(passkey, true)))).isEqualTo(reference);
		this.jdbc.update("UPDATE persons SET status = 'active' WHERE id = ?", dora);
		// password login of persons switched off
		this.jdbc.update("UPDATE system_settings SET person_password_login = false");
		assertThat(withoutInstance(body(passkeyLogin(passkey, true)))).isEqualTo(reference);
		this.jdbc.update("UPDATE system_settings SET person_password_login = true");
		// removed passkey (unknown at the instance)
		this.jdbc.update("UPDATE local_credentials SET status = 'revoked' WHERE person_id = ? AND type = 'passkey'", dora);
		assertThat(withoutInstance(body(passkeyLogin(passkey, true)))).isEqualTo(reference);

		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events WHERE event_type = 'auth.login-failed' AND details ->> 'method' = 'passkey'""",
				Integer.class)).isGreaterThanOrEqualTo(6);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM auth_throttle WHERE failures > 0", Integer.class))
			.isPositive();
	}

	@Test
	void activationAndRequiredPasswordChange() {
		long emil = active("emil");
		TestSession session = session(login("emil@example.org"));
		SoftwarePasskey passkey = new SoftwarePasskey(ORIGIN);
		register(session, passkey, "{}");
		this.jdbc.update("UPDATE persons SET status = 'invited' WHERE id = ?", emil);
		assertThat(post("/api/v1/admin/persons/" + emil + "/password/reset", this.personAdmin, "{}"))
			.hasStatus(HttpStatus.OK);
		MvcTestResult loggedIn = passkeyLogin(passkey, true);
		assertThat(loggedIn).hasStatus(HttpStatus.CREATED);
		assertThat(body(loggedIn).path("requiredAction").asString()).isEqualTo("change-password");
		assertThat(this.jdbc.queryForObject("SELECT status FROM persons WHERE id = ?", String.class, emil))
			.isEqualTo("active");
	}

	@Test
	void passkeyAsSecondStepAfterThePassword() {
		long fritz = active("fritz");
		TestSession session = session(login("fritz@example.org"));
		SoftwarePasskey passkey = new SoftwarePasskey(ORIGIN);
		register(session, passkey, "{}");

		MvcTestResult password = login("fritz@example.org");
		assertThat(body(password).path("type").asString()).isEqualTo("urn:oea:problem:second-factor-required");
		assertThat(body(password).path("secondFactors").toString()).isEqualTo("[\"passkey\"]");
		String transaction = body(password).path("loginTransaction").asString();
		JsonNode challenge = body(post("/api/v1/auth/local/login/passkey/options", null,
				"{\"loginTransaction\":\"" + transaction + "\"}"));
		assertThat(challenge.path("options").path("allowCredentials")).hasSize(1);
		MvcTestResult loggedIn = post("/api/v1/auth/local/login/passkey", null,
				secondStepBody(transaction, challenge, passkey.authenticate(challenge.path("options"), true)));
		assertThat(loggedIn).hasStatus(HttpStatus.CREATED);
		assertThat(body(loggedIn).path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'method' FROM audit.events WHERE event_type = 'auth.login' AND actor_person_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, fritz)).isEqualTo("password-passkey");

		// with a TOTP credential as well, both methods are named
		TestSession second = session(loggedIn);
		JsonNode totp = body(post("/api/v1/me/second-factors/totp", second, "{}"));
		String code = TotpAlgorithm.code(totp.path("secret").asString(),
				this.clock.instant().getEpochSecond() / TotpAlgorithm.PERIOD_SECONDS);
		assertThat(post("/api/v1/me/second-factors/totp/" + totp.path("credentialId").asString() + "/confirm", second,
				"{\"code\":\"" + code + "\"}")).hasStatus(HttpStatus.OK);
		assertThat(body(login("fritz@example.org")).path("secondFactors").toString())
			.isEqualTo("[\"totp\",\"passkey\"]");
	}

	@Test
	void failuresOfTheSecondStep() {
		active("gina");
		active("hanna");
		SoftwarePasskey gina = new SoftwarePasskey(ORIGIN);
		SoftwarePasskey hanna = new SoftwarePasskey(ORIGIN);
		register(session(login("gina@example.org")), gina, "{}");
		register(session(login("hanna@example.org")), hanna, "{}");

		String transaction = body(login("gina@example.org")).path("loginTransaction").asString();
		// a passkey of another person
		JsonNode challenge = challengeFor(transaction);
		ObjectNode withoutAllowList = (ObjectNode) challenge.path("options").deepCopy();
		withoutAllowList.putArray("allowCredentials");
		assertThat(post("/api/v1/auth/local/login/passkey", null,
				secondStepBody(transaction, challenge, hanna.authenticate(withoutAllowList, true))))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		// four more failures invalidate the transaction
		for (int i = 0; i < 4; i++) {
			JsonNode next = challengeFor(transaction);
			assertThat(post("/api/v1/auth/local/login/passkey", null,
					secondStepBody(transaction, next, gina.authenticate(next.path("options"), false))))
				.hasStatus(HttpStatus.UNAUTHORIZED);

		}
		assertThat(post("/api/v1/auth/local/login/passkey/options", null,
				"{\"loginTransaction\":\"" + transaction + "\"}")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private JsonNode challengeFor(String transaction) {
		MvcTestResult options = post("/api/v1/auth/local/login/passkey/options", null,
				"{\"loginTransaction\":\"" + transaction + "\"}");
		assertThat(options).hasStatus(HttpStatus.OK);
		return body(options);
	}

	private void assertRejected(TestSession session, java.util.function.Function<JsonNode, String> response) {
		JsonNode started = body(post("/api/v1/me/second-factors/passkey", session, "{}"));
		MvcTestResult rejected = post("/api/v1/me/second-factors/passkey/" + started.path("registrationId").asString()
				+ "/confirm", session, response.apply(started.path("options")));
		assertThat(rejected).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(body(rejected).path("type").asString()).isEqualTo("urn:oea:problem:invalid-passkey");
		assertThat(audit("auth.factor-enrollment-failed", "reason")).isEqualTo("verification-failed");
	}

	private void register(TestSession session, SoftwarePasskey passkey, String start) {
		JsonNode started = body(post("/api/v1/me/second-factors/passkey", session, start));
		assertThat(post("/api/v1/me/second-factors/passkey/" + started.path("registrationId").asString() + "/confirm",
				session, passkey.register(started.path("options"), true))).hasStatus(HttpStatus.OK);
	}

	private MvcTestResult passkeyLogin(SoftwarePasskey passkey, boolean userVerification) {
		JsonNode challenge = body(post("/api/v1/auth/passkey/options", null, ""));
		return post("/api/v1/auth/passkey/login", null,
				loginBody(challenge, passkey.authenticate(challenge.path("options"), userVerification)));
	}

	private static String loginBody(JsonNode challenge, String credential) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.put("challengeId", challenge.path("challengeId").asString());
		body.set("credential", JsonMapper.shared().readTree(credential));
		return body.toString();
	}

	private static String secondStepBody(String transaction, JsonNode challenge, String credential) {
		ObjectNode body = (ObjectNode) JsonMapper.shared().readTree(loginBody(challenge, credential));
		body.put("loginTransaction", transaction);
		return body.toString();
	}

	private boolean effective(TestSession session, String roleId) {
		for (JsonNode role : body(get("/api/v1/me", session)).path("roles")) {
			if (roleId.equals(role.path("role").path("id").asString())) {
				return role.path("effective").asBoolean();
			}
		}
		throw new AssertionError("role " + roleId + " not granted");
	}

	private long active(String name) {
		Long id = this.jdbc.queryForObject("INSERT INTO persons (name, email, status) VALUES (?, ?, 'active') RETURNING id",
				Long.class, name, name + "@example.org");
		UUID credential = UUID.randomUUID();
		this.jdbc.update("INSERT INTO local_credentials (id, person_id, type, created_at) VALUES (?, ?, 'password', ?)",
				credential, id, Timestamp.from(this.clock.instant()));
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credential,
				this.hasher.hash(PASSWORD));
		return (id != null) ? id : 0;
	}

	private String audit(String eventType, String detail) {
		return this.jdbc.queryForObject("SELECT details ->> ? FROM audit.events WHERE event_type = ? ORDER BY id DESC LIMIT 1",
				String.class, detail, eventType);
	}

	private long personId(TestSession session) {
		Long id = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
		return (id != null) ? id : 0;
	}

	private MvcTestResult login(String loginName) {
		return login(loginName, PASSWORD);
	}

	private MvcTestResult login(String loginName, String password) {
		return this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.with((request) -> {
				request.setRemoteAddr("10.11.0." + Math.abs(loginName.hashCode() % 200));
				return request;
			})
			.contentType("application/json")
			.content(JsonMapper.shared().createObjectNode().put("loginName", loginName).put("password", password).toString())
			.exchange();
	}

	private static TestSession session(MvcTestResult login) {
		assertThat(login).hasStatus(HttpStatus.CREATED);
		String cookie = login.getResponse().getHeader("Set-Cookie");
		String token = cookie.substring("__Host-OEA_SESSION=".length(), cookie.indexOf(';'));
		return new TestSession(token, body(login).path("csrfToken").asString(), null);
	}

	private static JsonNode withoutInstance(JsonNode problem) {
		var copy = problem.deepCopy();
		((ObjectNode) copy).remove("instance");
		return copy;
	}

	private MvcTestResult post(String uri, TestSession session, String body) {
		var request = this.mvc.post().uri(uri).contentType("application/json").content(body);
		if (session != null) {
			request = request.cookie(new Cookie(SessionCookie.NAME, session.token()))
				.header(CsrfOriginFilter.HEADER, session.csrfToken());
		}
		return request.exchange();
	}

	private MvcTestResult get(String uri, TestSession session) {
		return this.mvc.get().uri(uri).cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
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

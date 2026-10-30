package io.oea.app.access;

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

/**
 * Spec identity/second-factor-management (REQ-173 AC5, US-029 AC3; design D5, D6 of
 * us-021-person-totp). The person administrator holds `security-admin` through an IdP group
 * mapping.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecondFactorManagementTests.ClockConfiguration.class)
class SecondFactorManagementTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-second-factors").resolve("setup-token");
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", tokenFile::toString);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
		registry.add("oea.auth.person-password.enabled", () -> "true");
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
		this.personAdmin = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("security-admin");
	}

	@Test
	void twoAuthenticatorsAndALostPhone() {
		active("anna");
		TestSession session = session(login("anna@example.org", PASSWORD));
		String phone = enroll(session, "Phone");
		String backup = enroll(session, "Backup phone");
		TestSession other = session(loginTotp("anna@example.org", backup));

		JsonNode factors = body(get("/api/v1/me/second-factors", session));
		assertThat(factors).hasSize(2);
		assertThat(factors.get(0).path("label").asString()).isEqualTo("Phone");
		assertThat(factors.get(1).path("label").asString()).isEqualTo("Backup phone");
		assertThat(factors.get(0).path("createdAt").asString()).isNotBlank();
		assertThat(factors.toString()).doesNotContain(phone).doesNotContain("secret");

		String lost = factors.get(0).path("credentialId").asString();
		assertThat(delete("/api/v1/me/second-factors/" + lost, session)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(get("/api/v1/me", other)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(get("/api/v1/me/second-factors", session))).hasSize(1);
		assertThat(this.jdbc.queryForObject("SELECT revoked_reason FROM local_credentials WHERE id = ?::uuid",
				String.class, lost)).isEqualTo("person_request");
		assertThat(audit("auth.factor-removed", "label")).isEqualTo("Phone");
		// codes of the removed authenticator no longer work
		this.clock.advance(Duration.ofSeconds(30));
		MvcTestResult password = login("anna@example.org", PASSWORD);
		assertThat(this.mvc.post()
			.uri("/api/v1/auth/local/login/totp")
			.contentType("application/json")
			.content(JsonMapper.shared()
				.createObjectNode()
				.put("loginTransaction", body(password).path("loginTransaction").asString())
				.put("code", code(phone))
				.toString())
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void removalRules() {
		active("ben");
		TestSession session = session(login("ben@example.org", PASSWORD));
		enroll(session, "Phone");
		String credential = body(get("/api/v1/me/second-factors", session)).get(0).path("credentialId").asString();
		// someone else's credential is not found
		active("carla");
		TestSession carla = session(login("carla@example.org", PASSWORD));
		assertThat(delete("/api/v1/me/second-factors/" + credential, carla)).hasStatus(HttpStatus.NOT_FOUND);
		// an old authentication is not enough
		this.clock.advance(Duration.ofMinutes(6));
		MvcTestResult old = delete("/api/v1/me/second-factors/" + credential, session);
		assertThat(old).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(old).path("type").asString()).isEqualTo("urn:oea:problem:reauthentication-required");
		// a principal without local credentials has none
		assertThat(body(get("/api/v1/me/second-factors", this.personAdmin))).isEmpty();
	}

	@Test
	void theLastFactorOfTheSystemAdminAccountStays() {
		UUID account = UUID.randomUUID();
		this.jdbc.update("INSERT INTO system_admin_accounts (id, login_name, status) VALUES (?, ?, 'active')", account,
				"admin-" + account.toString().substring(0, 8));
		Sessions.NewSession created = this.sessions.createForSystemAdmin(this.accounts.findById(account).orElseThrow(),
				true, "10.8.0.1", "test");
		TestSession admin = new TestSession(created.token(), created.csrfToken(), created.principal().sessionId());
		String first = enroll(admin, "Admin phone");
		String only = body(get("/api/v1/me/second-factors", admin)).get(0).path("credentialId").asString();
		MvcTestResult rejected = delete("/api/v1/me/second-factors/" + only, admin);
		assertThat(rejected).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(rejected).path("type").asString()).isEqualTo("urn:oea:problem:last-second-factor");
		enroll(admin, "Admin backup");
		assertThat(delete("/api/v1/me/second-factors/" + only, admin)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(first).isNotBlank();
	}

	@Test
	void everyDeviceLost() {
		long dora = active("dora");
		TestSession session = session(login("dora@example.org", PASSWORD));
		enroll(session, "Phone");
		MvcTestResult reset = post("/api/v1/admin/persons/" + dora + "/second-factors/reset", this.personAdmin,
				"{\"reason\":\"phone lost\"}");
		assertThat(reset).hasStatus(HttpStatus.OK);
		assertThat(body(reset).path("credentialsRevoked").asInt()).isEqualTo(1);
		assertThat(get("/api/v1/me", session)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.jdbc.queryForObject("""
				SELECT revoked_reason FROM local_credentials WHERE person_id = ? AND type = 'totp'""", String.class, dora))
			.isEqualTo("factor_reset");
		assertThat(this.jdbc.queryForObject("""
				SELECT (details ->> 'reason') || '|' || (details ->> 'sessionsRevoked') FROM audit.events
				WHERE event_type = 'person.second-factors-reset' AND target_id = ?""", String.class, Long.toString(dora)))
			.isEqualTo("phone lost|1");
		// the next login works with the password alone
		MvcTestResult again = login("dora@example.org", PASSWORD);
		assertThat(again).hasStatus(HttpStatus.CREATED);
		assertThat(body(again).path("mfaSatisfied").asBoolean()).isFalse();
		// nothing left to reset
		assertThat(post("/api/v1/admin/persons/" + dora + "/second-factors/reset", this.personAdmin, "{}"))
			.hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void resetOfAnAdministrator() {
		long emil = active("emil");
		enroll(session(login("emil@example.org", PASSWORD)), "Phone");
		this.jdbc.update("""
				INSERT INTO role_assignments (person_id, role_id, granted_by_person_id, valid_from)
				VALUES (?, 'metamodel-admin', ?, now())""", emil, personId(this.personAdmin));
		assertThat(post("/api/v1/admin/persons/" + emil + "/second-factors/reset", this.personAdmin, "{}"))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'reason' FROM audit.events WHERE event_type = 'authz.denied' AND target_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, Long.toString(emil))).isEqualTo("admin-reset-restricted");
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM local_credentials WHERE person_id = ? AND type = 'totp' AND status = 'active'""",
				Integer.class, emil)).isOne();
		UUID account = UUID.randomUUID();
		this.jdbc.update("INSERT INTO system_admin_accounts (id, login_name, status) VALUES (?, ?, 'active')", account,
				"admin-" + account.toString().substring(0, 8));
		Sessions.NewSession created = this.sessions.createForSystemAdmin(this.accounts.findById(account).orElseThrow(),
				true, "10.8.0.2", "test");
		TestSession systemAdmin = new TestSession(created.token(), created.csrfToken(), created.principal().sessionId());
		assertThat(post("/api/v1/admin/persons/" + emil + "/second-factors/reset", systemAdmin, "{}"))
			.hasStatus(HttpStatus.OK);
	}

	@Test
	void rulesOfAFactorReset() {
		MvcTestResult own = post("/api/v1/admin/persons/" + personId(this.personAdmin) + "/second-factors/reset",
				this.personAdmin, "{}");
		assertThat(own).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(own).path("reason").asString()).isEqualTo("self-administration");
		Long serviceAccount = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', 'CMDB sync', 'active')
				RETURNING id""", Long.class);
		assertThat(post("/api/v1/admin/persons/" + serviceAccount + "/second-factors/reset", this.personAdmin, "{}"))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(post("/api/v1/admin/persons/999999/second-factors/reset", this.personAdmin, "{}"))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	/** Enrolls a TOTP credential and moves past the time step of the confirmation. */
	private String enroll(TestSession session, String label) {
		JsonNode started = body(post("/api/v1/me/second-factors/totp", session, "{\"label\":\"" + label + "\"}"));
		String secret = started.path("secret").asString();
		assertThat(post("/api/v1/me/second-factors/totp/" + started.path("credentialId").asString() + "/confirm",
				session, "{\"code\":\"" + code(secret) + "\"}")).hasStatus(HttpStatus.OK);
		this.clock.advance(Duration.ofSeconds(30));
		return secret;
	}

	private MvcTestResult loginTotp(String email, String secret) {
		MvcTestResult password = login(email, PASSWORD);
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/auth/local/login/totp")
			.contentType("application/json")
			.content(JsonMapper.shared()
				.createObjectNode()
				.put("loginTransaction", body(password).path("loginTransaction").asString())
				.put("code", code(secret))
				.toString())
			.exchange();
		this.clock.advance(Duration.ofSeconds(30));
		return result;
	}

	private String code(String secret) {
		return TotpAlgorithm.code(secret, this.clock.instant().getEpochSecond() / TotpAlgorithm.PERIOD_SECONDS);
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

	private MvcTestResult login(String loginName, String password) {
		return this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.with((request) -> {
				request.setRemoteAddr("10.9.0." + Math.abs(loginName.hashCode() % 200));
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

	private MvcTestResult post(String uri, TestSession session, String body) {
		return this.mvc.post()
			.uri(uri)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body)
			.exchange();
	}

	private MvcTestResult delete(String uri, TestSession session) {
		return this.mvc.delete()
			.uri(uri)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
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

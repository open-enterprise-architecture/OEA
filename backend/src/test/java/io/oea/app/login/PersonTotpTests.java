package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import io.oea.testsupport.logging.LogCapture;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec identity/person-totp and the TOTP parts of identity/person-password and
 * identity/password-reset (US-021, US-029, US-010, US-024; design D1 to D4 of us-021-person-totp)
 * with password login of persons switched on. The person administrator holds `security-admin`
 * through an IdP group mapping. The password hasher is a spy, so that the hash verifications per
 * failed login can be counted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PersonTotpTests.ClockConfiguration.class)
class PersonTotpTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-person-totp").resolve("setup-token");
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
	private MutableClock clock;

	@MockitoSpyBean
	private PasswordHasher hasher;

	private TestSession personAdmin;

	@BeforeEach
	void administrator() {
		this.jdbc.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'identity-binding') ON CONFLICT (id) DO NOTHING""");
		this.personAdmin = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("security-admin");
	}

	@Test
	void enrollmentByAPerson() {
		LogCapture logs = LogCapture.attach();
		active("anna");
		TestSession session = session(login("anna@example.org", PASSWORD));
		JsonNode started = body(post("/api/v1/me/second-factors/totp", session, "{\"label\":\"Phone\"}"));
		String secret = started.path("secret").asString();
		assertThat(started.path("otpauthUri").asString()).contains("OEA%3Aanna%40example.org").contains("issuer=OEA");
		String credential = started.path("credentialId").asString();

		// wrong code: rejected, still pending, audited
		MvcTestResult wrong = confirm(session, credential, wrongCode(secret));
		assertThat(wrong).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(audit("auth.factor-enrollment-failed", "reason")).isEqualTo("invalid-code");
		MvcTestResult confirmed = confirm(session, credential, code(secret));
		assertThat(confirmed).hasStatus(HttpStatus.OK);
		assertThat(body(confirmed).path("label").asString()).isEqualTo("Phone");
		assertThat(body(get("/api/v1/me", session)).path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(audit("auth.factor-added", "type")).isEqualTo("totp");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE strpos(details::text, ?) > 0",
				Integer.class, secret)).isZero();
		assertThat(logs.text()).doesNotContain(secret);
		logs.close();
	}

	@Test
	void expiredEnrollmentAndLabelDefault() {
		active("ben");
		TestSession session = session(login("ben@example.org", PASSWORD));
		JsonNode started = body(post("/api/v1/me/second-factors/totp", session, "{}"));
		assertThat(this.jdbc.queryForObject("SELECT label FROM totp_credentials WHERE credential_id = ?::uuid",
				String.class, started.path("credentialId").asString()))
			.isEqualTo(TotpEnrollment.defaultLabel(this.clock.instant()))
			.startsWith("Authenticator app (");
		this.clock.advance(Duration.ofMinutes(11));
		MvcTestResult expired = confirm(session, started.path("credentialId").asString(),
				code(started.path("secret").asString()));
		assertThat(expired).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(audit("auth.factor-enrollment-failed", "reason")).isEqualTo("enrollment-expired");
	}

	@Test
	void aPersonWithoutLocalPasswordEnrollsNoTotp() {
		assertThat(post("/api/v1/me/second-factors/totp", this.personAdmin, "{}")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void loginWithPasswordAndCodeMakesPrivilegedRolesEffective() {
		long carla = active("carla");
		this.jdbc.update("""
				INSERT INTO role_assignments (person_id, role_id, granted_by_person_id, valid_from)
				VALUES (?, 'metamodel-admin', ?, now() - interval '1 hour')""", carla, personId(this.personAdmin));
		TestSession first = session(login("carla@example.org", PASSWORD));
		assertThat(effective(first, "metamodel-admin")).isFalse();
		String secret = enroll(first, "Phone");

		MvcTestResult password = login("carla@example.org", PASSWORD);
		assertThat(password).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(password).path("type").asString()).isEqualTo("urn:oea:problem:second-factor-required");
		String transaction = body(password).path("loginTransaction").asString();
		MvcTestResult wrong = loginTotp(transaction, wrongCode(secret));
		MvcTestResult unknown = loginTotp("unknown-transaction", code(secret));
		assertThat(withoutInstance(body(wrong))).isEqualTo(withoutInstance(body(unknown)));
		MvcTestResult loggedIn = loginTotp(transaction, code(secret));
		assertThat(loggedIn).hasStatus(HttpStatus.CREATED);
		assertThat(body(loggedIn).path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(effective(session(loggedIn), "metamodel-admin")).isTrue();
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'method' FROM audit.events WHERE event_type = 'auth.login' AND actor_person_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, carla)).isEqualTo("password-totp");

		// the same code is never accepted twice (REQ-170)
		String replay = body(login("carla@example.org", PASSWORD)).path("loginTransaction").asString();
		assertThat(loginTotp(replay, code(secret))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void secondAuthenticatorAndFiveWrongCodes() {
		active("dora");
		TestSession session = session(login("dora@example.org", PASSWORD));
		String first = enroll(session, "Phone");
		session = session(loginTotp(body(login("dora@example.org", PASSWORD)).path("loginTransaction").asString(),
				code(first)));
		this.clock.advance(Duration.ofSeconds(30));
		String second = enroll(session, "Backup phone");
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM local_credentials c JOIN persons p ON p.id = c.person_id
				WHERE p.email = 'dora@example.org' AND c.type = 'totp' AND c.status = 'active'""", Integer.class))
			.isEqualTo(2);
		MvcTestResult backup = loginTotp(body(login("dora@example.org", PASSWORD)).path("loginTransaction").asString(),
				code(second));
		assertThat(backup).hasStatus(HttpStatus.CREATED);
		assertThat(this.jdbc.queryForObject("""
				SELECT c.last_used_at IS NOT NULL FROM local_credentials c JOIN totp_credentials t ON t.credential_id = c.id
				WHERE t.label = 'Backup phone'""", Boolean.class)).isTrue();

		this.clock.advance(Duration.ofSeconds(30));
		String transaction = body(login("dora@example.org", PASSWORD)).path("loginTransaction").asString();
		for (int i = 0; i < 5; i++) {
			assertThat(loginTotp(transaction, wrongCode(first))).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		assertThat(loginTotp(transaction, code(first))).as("invalidated").hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anInvitedPersonIsActivatedOnlyAfterTheTotpStep() {
		long emil = active("emil");
		TestSession session = session(login("emil@example.org", PASSWORD));
		String secret = enroll(session, null);
		this.jdbc.update("UPDATE persons SET status = 'invited' WHERE id = ?", emil);
		String transaction = body(login("emil@example.org", PASSWORD)).path("loginTransaction").asString();
		assertThat(status(emil)).isEqualTo("invited");
		assertThat(loginTotp(transaction, code(secret))).hasStatus(HttpStatus.CREATED);
		assertThat(status(emil)).isEqualTo("active");
	}

	@Test
	void theSecondFactorStaysRequiredAfterAPasswordReset() {
		long fritz = active("fritz");
		String secret = enroll(session(login("fritz@example.org", PASSWORD)), "Phone");
		MvcTestResult reset = post("/api/v1/admin/persons/" + fritz + "/password/reset", this.personAdmin, "{}");
		assertThat(reset).hasStatus(HttpStatus.OK);
		String temporary = body(reset).path("password").asString();
		MvcTestResult password = login("fritz@example.org", temporary);
		assertThat(body(password).path("type").asString()).isEqualTo("urn:oea:problem:second-factor-required");
		MvcTestResult loggedIn = loginTotp(body(password).path("loginTransaction").asString(), code(secret));
		assertThat(loggedIn).hasStatus(HttpStatus.CREATED);
		assertThat(body(loggedIn).path("requiredAction").asString()).isEqualTo("change-password");
	}

	@Test
	void aFailedPasswordStepOfAPersonWithTotpVerifiesExactlyOneHash() {
		active("gina");
		enroll(session(login("gina@example.org", PASSWORD)), "Phone");
		clearInvocations(this.hasher);
		MvcTestResult failed = login("gina@example.org", "wrong password 1");
		assertThat(failed).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(failed).path("type").asString()).isEqualTo("urn:oea:problem:invalid-credentials");
		verify(this.hasher, times(1)).matches(anyString(), anyString());
	}

	/** Enrolls a TOTP credential in the session and moves past the time step of the confirmation. */
	private String enroll(TestSession session, String label) {
		JsonNode started = body(post("/api/v1/me/second-factors/totp", session,
				(label != null) ? "{\"label\":\"" + label + "\"}" : "{}"));
		String secret = started.path("secret").asString();
		assertThat(confirm(session, started.path("credentialId").asString(), code(secret))).hasStatus(HttpStatus.OK);
		this.clock.advance(Duration.ofSeconds(30));
		return secret;
	}

	private MvcTestResult confirm(TestSession session, String credentialId, String code) {
		return post("/api/v1/me/second-factors/totp/" + credentialId + "/confirm", session,
				"{\"code\":\"" + code + "\"}");
	}

	private String code(String secret) {
		return TotpAlgorithm.code(secret, this.clock.instant().getEpochSecond() / TotpAlgorithm.PERIOD_SECONDS);
	}

	/** A code that is valid for none of the accepted time steps. */
	private String wrongCode(String secret) {
		long step = this.clock.instant().getEpochSecond() / TotpAlgorithm.PERIOD_SECONDS;
		for (int candidate = 0;; candidate++) {
			String code = String.format("%06d", candidate);
			if (!code.equals(TotpAlgorithm.code(secret, step - 1)) && !code.equals(TotpAlgorithm.code(secret, step))
					&& !code.equals(TotpAlgorithm.code(secret, step + 1))) {
				return code;
			}
		}
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

	private String status(long personId) {
		return this.jdbc.queryForObject("SELECT status FROM persons WHERE id = ?", String.class, personId);
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
				request.setRemoteAddr("10.7.0." + Math.abs(loginName.hashCode() % 200));
				return request;
			})
			.contentType("application/json")
			.content(JsonMapper.shared().createObjectNode().put("loginName", loginName).put("password", password).toString())
			.exchange();
	}

	private MvcTestResult loginTotp(String transaction, String code) {
		return this.mvc.post()
			.uri("/api/v1/auth/local/login/totp")
			.contentType("application/json")
			.content(JsonMapper.shared().createObjectNode().put("loginTransaction", transaction).put("code", code).toString())
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
		((tools.jackson.databind.node.ObjectNode) copy).remove("instance");
		return copy;
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

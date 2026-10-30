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

/**
 * Spec identity/second-factor-enforcement and the enforcement parts of identity/person-totp and
 * identity/second-factor-management (US-031, US-028; design D1 to D5 of
 * us-031-second-factor-enforcement). The policy administrator holds `system-admin`, the person
 * administrator `security-admin`, both through IdP group mappings.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecondFactorEnforcementTests.ClockConfiguration.class)
class SecondFactorEnforcementTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String ORIGIN = "https://oea.example.org";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-enforcement").resolve("setup-token");
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

	private static final String POLICY = "/api/v1/admin/settings/second-factor-policy";

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

	private TestSession settingsAdmin;

	private TestSession personAdmin;

	@BeforeEach
	void administrators() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.jdbc.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'identity-binding') ON CONFLICT (id) DO NOTHING""");
		this.jdbc.update("UPDATE system_settings SET second_factor_enforced = false, second_factor_exempt_roles = '{}'");
		this.settingsAdmin = principals.personWithIdpRole("system-admin");
		this.personAdmin = principals.personWithIdpRole("security-admin");
	}

	@Test
	void enforcementSwitchedOn() {
		JsonNode defaults = body(get(POLICY, this.settingsAdmin));
		assertThat(defaults.path("enforced").asBoolean()).isFalse();
		assertThat(defaults.path("enforcedByOperator").asBoolean()).isFalse();
		assertThat(put(POLICY, this.settingsAdmin, "{\"enforced\":true,\"exemptRoles\":[\"viewer\"]}"))
			.hasStatus(HttpStatus.OK);
		JsonNode changed = body(get(POLICY, this.settingsAdmin));
		assertThat(changed.path("enforced").asBoolean()).isTrue();
		assertThat(changed.path("exemptRoles").toString()).isEqualTo("[\"viewer\"]");
		assertThat(this.jdbc.queryForObject("""
				SELECT (details -> 'old' ->> 'enforced') || '|' || (details -> 'new' ->> 'enforced') FROM audit.events
				WHERE event_type = 'settings.second-factor-policy-changed' ORDER BY id DESC LIMIT 1""", String.class))
			.isEqualTo("false|true");
		assertThat(get(POLICY, this.personAdmin)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void adminRoleOrUnknownRoleAsExemption() {
		for (String role : new String[] { "system-admin", "security-admin", "no-such-role" }) {
			MvcTestResult rejected = put(POLICY, this.settingsAdmin,
					"{\"enforced\":true,\"exemptRoles\":[\"viewer\",\"" + role + "\"]}");
			assertThat(rejected).as(role).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
			assertThat(body(rejected).path("invalidFields").toString()).isEqualTo("[\"exemptRoles\"]");
		}
		assertThat(body(get(POLICY, this.settingsAdmin)).path("enforced").asBoolean()).isFalse();
	}

	@Test
	void firstLoginUnderEnforcement() {
		enforce("[]");
		long anna = invited("anna");
		assertThat(post("/api/v1/admin/persons/" + anna + "/password", this.personAdmin,
				"{\"password\":\"initial-password-123\"}")).hasStatus(HttpStatus.OK);
		MvcTestResult first = login("anna@example.org", "initial-password-123");
		assertThat(body(first).path("requiredAction").asString()).isEqualTo("change-password");
		TestSession session = session(first);
		assertThat(post("/api/v1/me/password", session,
				"{\"currentPassword\":\"initial-password-123\",\"newPassword\":\"" + PASSWORD + "\"}"))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(body(get("/api/v1/me", session)).path("requiredAction").asString())
			.isEqualTo("enroll-second-factor");
		MvcTestResult blocked = get("/api/v1/catalogs", session);
		assertThat(blocked).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(blocked).path("type").asString())
			.isEqualTo("urn:oea:problem:second-factor-enrollment-required");
		assertThat(get("/api/v1/me/second-factors", session)).hasStatus(HttpStatus.OK);

		JsonNode totp = body(post("/api/v1/me/second-factors/totp", session, "{}"));
		assertThat(post("/api/v1/me/second-factors/totp/" + totp.path("credentialId").asString() + "/confirm", session,
				"{\"code\":\"" + code(totp.path("secret").asString()) + "\"}")).hasStatus(HttpStatus.OK);
		JsonNode me = body(get("/api/v1/me", session));
		assertThat(me.path("requiredAction").isNull()).isTrue();
		assertThat(me.path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(body(get("/api/v1/catalogs", session)).path("type").asString())
			.isNotEqualTo("urn:oea:problem:second-factor-enrollment-required");
	}

	@Test
	void aPasskeyLiftsTheRequiredEnrollment() {
		enforce("[]");
		active("ben");
		MvcTestResult login = login("ben@example.org", PASSWORD);
		assertThat(body(login).path("requiredAction").asString()).isEqualTo("enroll-second-factor");
		TestSession session = session(login);
		JsonNode started = body(post("/api/v1/me/second-factors/passkey", session, "{}"));
		assertThat(post("/api/v1/me/second-factors/passkey/" + started.path("registrationId").asString() + "/confirm",
				session, new SoftwarePasskey(ORIGIN).register(started.path("options"), true)))
			.hasStatus(HttpStatus.OK);
		assertThat(body(get("/api/v1/me", session)).path("requiredAction").isNull()).isTrue();
	}

	@Test
	void exemptRoles() {
		enforce("[\"viewer\"]");
		long carla = active("carla");
		long dora = active("dora");
		active("emil");
		assign(carla, "viewer");
		assign(dora, "viewer");
		assign(dora, "enterprise-architect");
		assertThat(body(login("carla@example.org", PASSWORD)).path("requiredAction").isNull()).isTrue();
		assertThat(body(login("dora@example.org", PASSWORD)).path("requiredAction").asString())
			.isEqualTo("enroll-second-factor");
		// a person without roles is not exempt
		assertThat(body(login("emil@example.org", PASSWORD)).path("requiredAction").asString())
			.isEqualTo("enroll-second-factor");
	}

	@Test
	void outsideTheScopeOfTheEnforcement() {
		active("fritz");
		// no enforcement: no required action
		assertThat(body(login("fritz@example.org", PASSWORD)).path("requiredAction").isNull()).isTrue();
		// a login with second factor is never restricted
		TestSession session = session(login("fritz@example.org", PASSWORD));
		String secret = enroll(session);
		enforce("[]");
		MvcTestResult password = login("fritz@example.org", PASSWORD);
		MvcTestResult loggedIn = this.mvc.post()
			.uri("/api/v1/auth/local/login/totp")
			.contentType("application/json")
			.content("{\"loginTransaction\":\"" + body(password).path("loginTransaction").asString() + "\",\"code\":\""
					+ code(secret) + "\"}")
			.exchange();
		assertThat(loggedIn).hasStatus(HttpStatus.CREATED);
		assertThat(body(loggedIn).path("requiredAction").isNull()).isTrue();
		// an IdP session is not restricted
		assertThat(body(get("/api/v1/me", this.personAdmin)).path("requiredAction").isNull()).isTrue();
		assertThat(body(get("/api/v1/catalogs", this.personAdmin)).path("type").asString())
			.isNotEqualTo("urn:oea:problem:second-factor-enrollment-required");
	}

	@Test
	void theLastFactorStaysUnderEnforcement() {
		long gina = active("gina");
		long hanna = active("hanna");
		assign(hanna, "viewer");
		TestSession ginaSession = session(login("gina@example.org", PASSWORD));
		enroll(ginaSession);
		TestSession hannaSession = session(login("hanna@example.org", PASSWORD));
		enroll(hannaSession);
		enforce("[\"viewer\"]");

		String ginaFactor = body(get("/api/v1/me/second-factors", ginaSession)).get(0).path("credentialId").asString();
		MvcTestResult rejected = delete("/api/v1/me/second-factors/" + ginaFactor, ginaSession);
		assertThat(rejected).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(rejected).path("type").asString()).isEqualTo("urn:oea:problem:last-second-factor");
		// an exempt person may remove the last one
		String hannaFactor = body(get("/api/v1/me/second-factors", hannaSession)).get(0).path("credentialId").asString();
		assertThat(delete("/api/v1/me/second-factors/" + hannaFactor, hannaSession)).hasStatus(HttpStatus.NO_CONTENT);
		// the factor reset stays possible; the next login requires the enrollment
		assertThat(post("/api/v1/admin/persons/" + gina + "/second-factors/reset", this.personAdmin, "{}"))
			.hasStatus(HttpStatus.OK);
		assertThat(body(login("gina@example.org", PASSWORD)).path("requiredAction").asString())
			.isEqualTo("enroll-second-factor");
	}

	private void enforce(String exemptRoles) {
		assertThat(put(POLICY, this.settingsAdmin, "{\"enforced\":true,\"exemptRoles\":" + exemptRoles + "}"))
			.hasStatus(HttpStatus.OK);
	}

	private void assign(long personId, String role) {
		this.jdbc.update("""
				INSERT INTO role_assignments (person_id, role_id, granted_by_person_id, valid_from)
				VALUES (?, ?, ?, now() - interval '1 hour')""", personId, role, personId(this.personAdmin));
	}

	/** Enrolls a TOTP credential and moves past the time step of the confirmation. */
	private String enroll(TestSession session) {
		JsonNode started = body(post("/api/v1/me/second-factors/totp", session, "{}"));
		String secret = started.path("secret").asString();
		assertThat(post("/api/v1/me/second-factors/totp/" + started.path("credentialId").asString() + "/confirm",
				session, "{\"code\":\"" + code(secret) + "\"}")).hasStatus(HttpStatus.OK);
		this.clock.advance(Duration.ofSeconds(30));
		return secret;
	}

	private String code(String secret) {
		return TotpAlgorithm.code(secret, this.clock.instant().getEpochSecond() / TotpAlgorithm.PERIOD_SECONDS);
	}

	private long invited(String name) {
		Long id = this.jdbc.queryForObject("INSERT INTO persons (name, email) VALUES (?, ?) RETURNING id", Long.class,
				name, name + "@example.org");
		return (id != null) ? id : 0;
	}

	private long active(String name) {
		long id = invited(name);
		this.jdbc.update("UPDATE persons SET status = 'active' WHERE id = ?", id);
		UUID credential = UUID.randomUUID();
		this.jdbc.update("INSERT INTO local_credentials (id, person_id, type, created_at) VALUES (?, ?, 'password', ?)",
				credential, id, Timestamp.from(this.clock.instant()));
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credential,
				this.hasher.hash(PASSWORD));
		return id;
	}

	private long personId(TestSession session) {
		Long id = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
		return (id != null) ? id : 0;
	}

	private MvcTestResult login(String loginName, String password) {
		return this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.with((request) -> {
				request.setRemoteAddr("10.12.0." + Math.abs(loginName.hashCode() % 200));
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

	private MvcTestResult put(String uri, TestSession session, String body) {
		return this.mvc.put()
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

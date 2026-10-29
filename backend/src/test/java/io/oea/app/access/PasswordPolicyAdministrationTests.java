package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.service.PasswordHasher;
import io.oea.core.service.PasswordPolicy;
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
import java.util.ArrayList;
import java.util.List;
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
 * Specs identity/password-policy and identity/person-password (US-027, US-026, US-011; design D1
 * to D5 of us-027-password-administration) with the default operator floors and password login of
 * persons permitted. The policy administrator holds `system-admin` through an IdP group mapping,
 * the person administrator `security-admin`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PasswordPolicyAdministrationTests.ClockConfiguration.class)
class PasswordPolicyAdministrationTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-password-policy").resolve("setup-token");
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

	private static final String STRICT = """
			{"minLength":16,"requireUppercase":true,"requireLowercase":true,"requireDigits":true,
			 "requireSpecialChars":true,"expiryDays":90,"historyCount":5,"personPasswordLogin":true}""";

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
		// every test starts with the default policy
		this.jdbc.update("""
				UPDATE system_settings SET password_min_length = 12, password_require_uppercase = false,
				       password_require_lowercase = false, password_require_digits = false,
				       password_require_special_chars = false, password_expiry_days = 0, password_history_count = 0,
				       person_password_login = true""");
		this.settingsAdmin = principals.personWithIdpRole("system-admin");
		this.personAdmin = principals.personWithIdpRole("security-admin");
	}

	@Test
	void policyChanged() {
		JsonNode defaults = body(get("/api/v1/admin/settings/password-policy", this.settingsAdmin));
		assertThat(defaults.path("minLength").asInt()).isEqualTo(12);
		assertThat(defaults.path("historyCount").asInt()).isZero();
		assertThat(defaults.path("personPasswordLogin").asBoolean()).isTrue();
		assertThat(defaults.path("personPasswordLoginPermitted").asBoolean()).isTrue();
		assertThat(defaults.path("floors").path("minLength").asInt()).isEqualTo(12);

		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin, STRICT)).hasStatus(HttpStatus.OK);
		JsonNode changed = body(get("/api/v1/admin/settings/password-policy", this.settingsAdmin));
		assertThat(changed.path("minLength").asInt()).isEqualTo(16);
		assertThat(changed.path("requireSpecialChars").asBoolean()).isTrue();
		assertThat(changed.path("expiryDays").asInt()).isEqualTo(90);
		assertThat(changed.path("historyCount").asInt()).isEqualTo(5);
		assertThat(changed.path("floors").isObject()).isTrue();
		assertThat(this.jdbc.queryForObject("""
				SELECT (details -> 'old' ->> 'minLength') || '|' || (details -> 'new' ->> 'minLength') FROM audit.events
				WHERE event_type = 'settings.password-policy-changed' ORDER BY id DESC LIMIT 1""", String.class))
			.isEqualTo("12|16");
		// settings:manage is required
		assertThat(get("/api/v1/admin/settings/password-policy", this.personAdmin)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void belowTheMinimumOfTwelve() {
		MvcTestResult rejected = put("/api/v1/admin/settings/password-policy", this.settingsAdmin,
				STRICT.replace("\"minLength\":16", "\"minLength\":10"));
		assertThat(rejected).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(rejected).path("invalidFields").toString()).contains("minLength");
		assertThat(body(get("/api/v1/admin/settings/password-policy", this.settingsAdmin)).path("minLength").asInt())
			.isEqualTo(12);
	}

	@Test
	void severalUnmetRulesAndTheDefaultPolicy() {
		long anna = active("anna", "chosen-password-456");
		TestSession session = session(login("anna@example.org", "chosen-password-456"));
		// default policy: twelve lowercase letters are accepted
		assertThat(change(session, "chosen-password-456", "abcdefghijkl")).hasStatus(HttpStatus.NO_CONTENT);
		session = session(login("anna@example.org", "abcdefghijkl"));

		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin, STRICT)).hasStatus(HttpStatus.OK);
		MvcTestResult rejected = change(session, "abcdefghijkl", "abcdefghijklmn");
		assertThat(rejected).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(rejected).path("type").asString()).isEqualTo("urn:oea:problem:password-policy-violation");
		assertThat(strings(body(rejected).path("invalidFields"))).containsExactly("minLength", "requireUppercase",
				"requireDigits", "requireSpecialChars");
		// an entered initial password follows the policy as well
		long ben = invited("ben");
		MvcTestResult initial = post("/api/v1/admin/persons/" + ben + "/password", this.personAdmin,
				"{\"password\":\"abcdefghijklmnop\"}");
		assertThat(initial).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(initial).path("type").asString()).isEqualTo("urn:oea:problem:password-policy-violation");
		assertThat(anna).isPositive();
	}

	@Test
	void reusedPassword() {
		active("carla", "First-password-1!");
		TestSession session = session(login("carla@example.org", "First-password-1!"));
		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin, STRICT)).hasStatus(HttpStatus.OK);
		assertThat(change(session, "First-password-1!", "Second-password-2!")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(change(session, "Second-password-2!", "Third-password-3!")).hasStatus(HttpStatus.NO_CONTENT);
		// used two changes ago
		MvcTestResult reused = change(session, "Third-password-3!", "First-password-1!");
		assertThat(reused).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(strings(body(reused).path("invalidFields"))).containsExactly("historyCount");
		assertThat(change(session, "Third-password-3!", "Fourth-password-4!")).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void expiredPassword() {
		active("dora", "First-password-1!");
		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin, STRICT)).hasStatus(HttpStatus.OK);
		TestSession session = session(login("dora@example.org", "First-password-1!"));
		assertThat(change(session, "First-password-1!", "Second-password-2!")).hasStatus(HttpStatus.NO_CONTENT);
		this.clock.advance(Duration.ofDays(91));
		try {
			MvcTestResult login = login("dora@example.org", "Second-password-2!");
			assertThat(login).hasStatus(HttpStatus.CREATED);
			assertThat(body(login).path("requiredAction").asString()).isEqualTo("change-password");
			TestSession expired = session(login);
			assertThat(body(get("/api/v1/catalogs", expired)).path("type").asString())
				.isEqualTo("urn:oea:problem:password-change-required");
			assertThat(change(expired, "Second-password-2!", "Third-password-3!")).hasStatus(HttpStatus.NO_CONTENT);
			assertThat(body(login("dora@example.org", "Third-password-3!")).path("requiredAction").isNull()).isTrue();
		}
		finally {
			this.clock.advance(Duration.ofDays(-91));
		}
	}

	@Test
	void generatedInitialPassword() {
		LogCapture logs = LogCapture.attach();
		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin, STRICT)).hasStatus(HttpStatus.OK);
		long emil = invited("emil");
		String path = "/api/v1/admin/persons/" + emil + "/password";
		assertThat(post(path, this.personAdmin, "{}")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(post(path, this.personAdmin, "{\"password\":\"Entered-password-1!\",\"generate\":true}"))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		MvcTestResult generated = post(path, this.personAdmin, "{\"generate\":true}");
		assertThat(generated).hasStatus(HttpStatus.OK);
		String password = body(generated).path("password").asString();
		assertThat(body(generated).path("expiresAt").asString()).isNotBlank();
		assertThat(new PasswordPolicy(16, true, true, true, true, 0, 0).violations(password)).isEmpty();
		assertThat(this.hasher.matches(password, this.jdbc.queryForObject("""
				SELECT pc.password_hash FROM password_credentials pc JOIN local_credentials c ON c.id = pc.credential_id
				WHERE c.person_id = ? AND c.status = 'active'""", String.class, emil))).isTrue();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE strpos(details::text, ?) > 0",
				Integer.class, password)).isZero();
		assertThat(logs.text()).doesNotContain(password);
		logs.close();
		assertThat(body(login("emil@example.org", password)).path("requiredAction").asString())
			.isEqualTo("change-password");
	}

	@Test
	void suggestionForTheOwnChange() {
		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin, STRICT)).hasStatus(HttpStatus.OK);
		long fritz = invited("fritz");
		assertThat(post("/api/v1/admin/persons/" + fritz + "/password", this.personAdmin,
				"{\"password\":\"Initial-password-1!\"}")).hasStatus(HttpStatus.OK);
		// allowed while the password change is required
		TestSession session = session(login("fritz@example.org", "Initial-password-1!"));
		MvcTestResult suggestion = post("/api/v1/me/password/suggestion", session, "");
		assertThat(suggestion).hasStatus(HttpStatus.OK);
		String password = body(suggestion).path("password").asString();
		assertThat(new PasswordPolicy(16, true, true, true, true, 0, 0).violations(password)).isEmpty();
		assertThat(change(session, "Initial-password-1!", password)).hasStatus(HttpStatus.NO_CONTENT);
		// a principal without local password gets none
		assertThat(post("/api/v1/me/password/suggestion", this.personAdmin, "")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void switchedOffByAnAdministrator() {
		active("gina", "chosen-password-456");
		long hanna = invited("hanna");
		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin,
				STRICT.replace("\"personPasswordLogin\":true", "\"personPasswordLogin\":false")))
			.hasStatus(HttpStatus.OK);
		MvcTestResult login = login("gina@example.org", "chosen-password-456");
		assertThat(login).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(body(login).path("type").asString()).isEqualTo("urn:oea:problem:invalid-credentials");
		assertThat(post("/api/v1/admin/persons/" + hanna + "/password", this.personAdmin, "{\"generate\":true}"))
			.hasStatus(HttpStatus.CONFLICT);
		// switched on again within the permission of the operator
		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin, STRICT)).hasStatus(HttpStatus.OK);
		assertThat(login("gina@example.org", "chosen-password-456")).hasStatus(HttpStatus.CREATED);
	}

	private long invited(String name) {
		Long id = this.jdbc.queryForObject("INSERT INTO persons (name, email) VALUES (?, ?) RETURNING id", Long.class,
				name, name + "@example.org");
		return (id != null) ? id : 0;
	}

	private long active(String name, String password) {
		long id = invited(name);
		this.jdbc.update("UPDATE persons SET status = 'active' WHERE id = ?", id);
		UUID credential = UUID.randomUUID();
		this.jdbc.update("INSERT INTO local_credentials (id, person_id, type, created_at) VALUES (?, ?, 'password', ?)",
				credential, id, Timestamp.from(this.clock.instant()));
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credential,
				this.hasher.hash(password));
		return id;
	}

	private MvcTestResult change(TestSession session, String current, String next) {
		return post("/api/v1/me/password", session, JsonMapper.shared()
			.createObjectNode()
			.put("currentPassword", current)
			.put("newPassword", next)
			.toString());
	}

	private MvcTestResult login(String loginName, String password) {
		return this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.with((request) -> {
				request.setRemoteAddr("10.3.0." + Math.abs(loginName.hashCode() % 200));
				return request;
			})
			.contentType("application/json")
			.content(JsonMapper.shared().createObjectNode().put("loginName", loginName).put("password", password).toString())
			.exchange();
	}

	private static TestSession session(MvcTestResult login) {
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

	private MvcTestResult get(String uri, TestSession session) {
		return this.mvc.get().uri(uri).cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach((value) -> values.add(value.asString()));
		return values;
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

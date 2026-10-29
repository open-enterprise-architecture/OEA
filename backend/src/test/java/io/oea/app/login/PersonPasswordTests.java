package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.oea.app.security.Sessions;
import io.oea.core.service.PasswordHasher;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
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
 * Spec identity/person-password (US-011, US-023, US-006; design D2–D7 of us-011-local-password)
 * on an own database with password login of persons switched on. The administrator holds
 * `security-admin` (with `person:manage`) through an IdP group mapping. The password hasher is a
 * spy, so that the hash verifications per failed login can be counted (D6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PersonPasswordTests.ClockConfiguration.class)
class PersonPasswordTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-person-passwords").resolve("setup-token");
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

	private TestPrincipals principals;

	private TestSession admin;

	@BeforeEach
	void administrator() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		// the setup phase has ended (ADR-042); the administrator holds role:assign
		this.jdbc.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'identity-binding') ON CONFLICT (id) DO NOTHING""");
		this.admin = this.principals.personWithIdpRole("security-admin");
	}

	private static final String INITIAL = "initial-password-123";

	private static final String CHOSEN = "chosen-password-456";

	@Test
	void initialPasswordFirstLoginAndChange() throws Exception {
		LogCapture logs = LogCapture.attach();
		long anna = invited("anna");
		MvcTestResult set = post("/api/v1/admin/persons/" + anna + "/password", this.admin,
				"{\"password\":\"" + INITIAL + "\"}");
		assertThat(set).hasStatus(HttpStatus.OK);
		assertThat(body(set).path("expiresAt").asString()).isNotBlank();
		assertThat(set.getResponse().getContentAsString()).doesNotContain(INITIAL);
		assertThat(this.jdbc.queryForObject("""
				SELECT pc.origin || '|' || (pc.password_hash LIKE '$argon2id$%') || '|'
				       || (pc.expires_at BETWEEN now() + interval '71 hours' AND now() + interval '73 hours')
				FROM password_credentials pc JOIN local_credentials c ON c.id = pc.credential_id WHERE c.person_id = ?""",
				String.class, anna)).isEqualTo("initial|true|true");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'person.password-set' AND target_id = '"
				+ anna + "'")).isOne();
		assertThat(count("SELECT count(*) FROM audit.events WHERE details::text LIKE '%" + INITIAL + "%'")).isZero();

		// first login: active, only the password change is allowed
		MvcTestResult first = login("Anna@Example.org", INITIAL);
		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(body(first).path("requiredAction").asString()).isEqualTo("change-password");
		assertThat(body(first).path("mfaSatisfied").asBoolean()).isFalse();
		assertThat(this.jdbc.queryForObject("SELECT status FROM persons WHERE id = ?", String.class, anna))
			.isEqualTo("active");
		TestSession session = session(first);
		TestSession other = session(login("anna@example.org", INITIAL));
		MvcTestResult blocked = get("/api/v1/catalogs", session);
		assertThat(blocked).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(blocked).path("type").asString()).isEqualTo("urn:oea:problem:password-change-required");
		assertThat(body(get("/api/v1/me", session)).path("requiredAction").asString()).isEqualTo("change-password");

		// change
		assertThat(post("/api/v1/me/password", session, "{\"currentPassword\":\"" + INITIAL + "\",\"newPassword\":\""
				+ INITIAL + "\"}")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(post("/api/v1/me/password", session,
				"{\"currentPassword\":\"wrong-password-000\",\"newPassword\":\"" + CHOSEN + "\"}"))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post("/api/v1/me/password", session, "{\"currentPassword\":\"" + INITIAL + "\",\"newPassword\":\""
				+ CHOSEN + "\"}")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(body(get("/api/v1/catalogs", session)).path("type").asString())
			.isNotEqualTo("urn:oea:problem:password-change-required");
		assertThat(body(get("/api/v1/me", session)).path("requiredAction").isNull()).isTrue();
		assertThat(get("/api/v1/me", other)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'auth.password-changed'")).isPositive();
		assertThat(login("anna@example.org", INITIAL)).hasStatus(HttpStatus.UNAUTHORIZED);
		MvcTestResult again = login("anna@example.org", CHOSEN);
		assertThat(again).hasStatus(HttpStatus.CREATED);
		assertThat(body(again).path("requiredAction").isNull()).isTrue();
		assertThat(logs.text()).doesNotContain(INITIAL).doesNotContain(CHOSEN);
		logs.close();
	}

	@Test
	void rulesForSettingAnInitialPassword() {
		long ben = invited("ben");
		String path = "/api/v1/admin/persons/" + ben + "/password";
		assertThat(post(path, this.admin, "{\"password\":\"short\"}")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(post(path, this.admin, "{\"password\":\"" + INITIAL + "\"}")).hasStatus(HttpStatus.OK);
		MvcTestResult twice = post(path, this.admin, "{\"password\":\"" + INITIAL + "\"}");
		assertThat(twice).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(twice).path("type").asString()).isEqualTo("urn:oea:problem:password-already-set");

		MvcTestResult own = post("/api/v1/admin/persons/" + personId(this.admin) + "/password", this.admin,
				"{\"password\":\"" + INITIAL + "\"}");
		assertThat(own).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(own).path("reason").asString()).isEqualTo("self-administration");
		Long serviceAccount = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', 'CMDB sync', 'active')
				RETURNING id""", Long.class);
		assertThat(post("/api/v1/admin/persons/" + serviceAccount + "/password", this.admin,
				"{\"password\":\"" + INITIAL + "\"}")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
	}

	@Test
	void loginWithASelfChosenPasswordHoldsTheRolesWithoutSecondFactor() {
		long carla = active("carla", CHOSEN);
		this.jdbc.update("""
				INSERT INTO role_assignments (person_id, role_id, granted_by_person_id, valid_from)
				VALUES (?, 'viewer', ?, now() - interval '1 hour')""", carla,
				personId(this.admin));
		MvcTestResult login = login("carla@example.org", CHOSEN);
		assertThat(login).hasStatus(HttpStatus.CREATED);
		assertThat(body(login).path("requiredAction").isNull()).isTrue();
		assertThat(body(get("/api/v1/me", session(login))).path("roles").toString()).contains("viewer");
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'method' || '|' || (details ->> 'secondFactor') FROM audit.events
				WHERE event_type = 'auth.login' AND actor_person_id = ? ORDER BY id DESC LIMIT 1""", String.class, carla))
			.isEqualTo("password|false");
	}

	@Test
	void expiredInitialPasswordAndThrottling() {
		long dora = invited("dora");
		assertThat(post("/api/v1/admin/persons/" + dora + "/password", this.admin, "{\"password\":\"" + INITIAL + "\"}"))
			.hasStatus(HttpStatus.OK);
		this.jdbc.update("""
				UPDATE password_credentials SET expires_at = now() - interval '1 minute'
				WHERE credential_id = (SELECT id FROM local_credentials WHERE person_id = ?)""", dora);
		assertThat(login("dora@example.org", INITIAL)).hasStatus(HttpStatus.UNAUTHORIZED);

		long emil = active("emil", CHOSEN);
		for (int i = 0; i < 5; i++) {
			assertThat(login("emil@example.org", "wrong password " + i)).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		assertThat(login("emil@example.org", CHOSEN)).hasStatus(HttpStatus.UNAUTHORIZED)
			.headers()
			.containsHeader("Retry-After");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'auth.login-failed'")).isGreaterThan(5);
		assertThat(emil).isPositive();
	}

	@Test
	void everyFailedLoginVerifiesExactlyOneHash() {
		active("fritz", CHOSEN);
		long inactive = active("gina", CHOSEN);
		this.jdbc.update("UPDATE persons SET status = 'inactive' WHERE id = ?", inactive);
		MvcTestResult reference = null;
		invited("hanna");
		for (String[] attempt : new String[][] { { "nobody@example.org", CHOSEN }, { "fritz@example.org", "wrong pass 1" },
				{ "gina@example.org", CHOSEN }, { "hanna@example.org", CHOSEN } }) {
			clearInvocations(this.hasher);
			MvcTestResult failed = login(attempt[0], attempt[1]);
			assertThat(failed).as(attempt[0]).hasStatus(HttpStatus.UNAUTHORIZED);
			verify(this.hasher, times(1)).matches(anyString(), anyString());
			if (reference == null) {
				reference = failed;
			}
			assertThat(withoutInstance(body(failed))).isEqualTo(withoutInstance(body(reference)));
		}
	}

	@Test
	void theSystemAdminAccountChangesItsPassword() {
		UUID account = UUID.randomUUID();
		this.jdbc.update("INSERT INTO system_admin_accounts (id, login_name, status) VALUES (?, ?, 'active')", account,
				"admin-" + account.toString().substring(0, 8));
		UUID credential = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO local_credentials (id, system_admin_account_id, type) VALUES (?, ?, 'password')""", credential,
				account);
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credential,
				this.hasher.hash(INITIAL));
		Sessions.NewSession created = this.sessions.createForSystemAdmin(this.accounts.findById(account).orElseThrow(),
				true, "10.2.0.1", "test");
		TestSession session = new TestSession(created.token(), created.csrfToken(), created.principal().sessionId());
		assertThat(post("/api/v1/me/password", session, "{\"currentPassword\":\"" + INITIAL + "\",\"newPassword\":\""
				+ CHOSEN + "\"}")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM local_credentials WHERE system_admin_account_id = ? AND type = 'password'
				AND status = 'active'""", Integer.class, account)).isOne();
		// a person session without a local password cannot use the operation
		assertThat(post("/api/v1/me/password", this.admin, "{\"currentPassword\":\"" + INITIAL + "\",\"newPassword\":\""
				+ CHOSEN + "\"}")).hasStatus(HttpStatus.FORBIDDEN);
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
		this.jdbc.update("INSERT INTO local_credentials (id, person_id, type) VALUES (?, ?, 'password')", credential, id);
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credential,
				this.hasher.hash(password));
		return id;
	}

	private MvcTestResult login(String loginName, String password) {
		return this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.with((request) -> {
				request.setRemoteAddr("10.1.0." + Math.abs(loginName.hashCode() % 200));
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

	private static JsonNode withoutInstance(JsonNode problem) {
		var copy = problem.deepCopy();
		((tools.jackson.databind.node.ObjectNode) copy).remove("instance");
		return copy;
	}

	private long personId(TestSession session) {
		Long id = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
		return (id != null) ? id : 0;
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

	private int count(String sql) {
		Integer count = this.jdbc.queryForObject(sql, Integer.class);
		return (count != null) ? count : 0;
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

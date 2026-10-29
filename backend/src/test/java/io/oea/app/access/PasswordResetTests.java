package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.service.PasswordHasher;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.logging.LogCapture;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * Spec identity/password-reset (US-030, REQ-031, REQ-173 AC3; design D6 of
 * us-027-password-administration). The person administrator holds `security-admin` through an
 * IdP group mapping.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-password-reset").resolve("setup-token");
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

	private static final String CHOSEN = "chosen-password-456";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@MockitoSpyBean
	private PasswordHasher hasher;

	private TestPrincipals principals;

	private TestSession personAdmin;

	@BeforeEach
	void administrator() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.jdbc.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'identity-binding') ON CONFLICT (id) DO NOTHING""");
		this.personAdmin = this.principals.personWithIdpRole("security-admin");
	}

	@Test
	void passwordReset() {
		LogCapture logs = LogCapture.attach();
		long anna = active("anna");
		TestSession first = session(login("anna@example.org", CHOSEN));
		TestSession second = session(login("anna@example.org", CHOSEN));
		MvcTestResult reset = post("/api/v1/admin/persons/" + anna + "/password/reset", this.personAdmin,
				"{\"reason\":\"forgotten\"}");
		assertThat(reset).hasStatus(HttpStatus.OK);
		String password = body(reset).path("password").asString();
		assertThat(password).hasSizeGreaterThanOrEqualTo(16);
		assertThat(body(reset).path("expiresAt").asString()).isNotBlank();

		assertThat(get("/api/v1/me", first)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(get("/api/v1/me", second)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(login("anna@example.org", CHOSEN)).hasStatus(HttpStatus.UNAUTHORIZED);
		MvcTestResult again = login("anna@example.org", password);
		assertThat(again).hasStatus(HttpStatus.CREATED);
		assertThat(body(again).path("requiredAction").asString()).isEqualTo("change-password");

		assertThat(this.jdbc.queryForObject("""
				SELECT pc.origin FROM password_credentials pc JOIN local_credentials c ON c.id = pc.credential_id
				WHERE c.person_id = ? AND c.status = 'active'""", String.class, anna)).isEqualTo("admin-reset");
		assertThat(this.jdbc.queryForObject("""
				SELECT revoked_reason || '|' || (revoked_by_person_id IS NOT NULL) FROM local_credentials
				WHERE person_id = ? AND status = 'revoked'""", String.class, anna)).isEqualTo("admin_action|true");
		assertThat(this.jdbc.queryForObject("""
				SELECT (details ->> 'reason') || '|' || (details ->> 'sessionsRevoked') FROM audit.events
				WHERE event_type = 'person.password-reset' AND target_id = ?""", String.class, Long.toString(anna)))
			.isEqualTo("forgotten|2");
		assertThat(count("SELECT count(*) FROM sessions WHERE person_id = " + anna
				+ " AND revoked_reason = 'password-reset'")).isEqualTo(2);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE strpos(details::text, ?) > 0",
				Integer.class, password)).isZero();
		assertThat(logs.text()).doesNotContain(password);
		logs.close();
	}

	@Test
	void resetOfAnAdministratorByAnotherAdministrator() {
		long ben = active("ben");
		this.jdbc.update("""
				INSERT INTO role_assignments (person_id, role_id, granted_by_person_id, valid_from)
				VALUES (?, 'metamodel-admin', ?, now())""", ben, personId(this.personAdmin));
		String hash = activeHash(ben);
		MvcTestResult denied = post("/api/v1/admin/persons/" + ben + "/password/reset", this.personAdmin, "");
		assertThat(denied).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(activeHash(ben)).isEqualTo(hash);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'reason' FROM audit.events WHERE event_type = 'authz.denied' AND target_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, Long.toString(ben))).isEqualTo("admin-reset-restricted");

		// the system admin account may (with a second factor, ADR-042)
		UUID account = UUID.randomUUID();
		this.jdbc.update("INSERT INTO system_admin_accounts (id, login_name, status) VALUES (?, ?, 'active')", account,
				"admin-" + account.toString().substring(0, 8));
		Sessions.NewSession created = this.sessions.createForSystemAdmin(this.accounts.findById(account).orElseThrow(),
				true, "10.5.0.1", "test");
		TestSession systemAdmin = new TestSession(created.token(), created.csrfToken(), created.principal().sessionId());
		assertThat(post("/api/v1/admin/persons/" + ben + "/password/reset", systemAdmin, "")).hasStatus(HttpStatus.OK);
		assertThat(activeHash(ben)).isNotEqualTo(hash);
	}

	@Test
	void rulesOfAReset() {
		MvcTestResult own = post("/api/v1/admin/persons/" + personId(this.personAdmin) + "/password/reset",
				this.personAdmin, "");
		assertThat(own).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(own).path("reason").asString()).isEqualTo("self-administration");
		Long withoutPassword = this.jdbc.queryForObject(
				"INSERT INTO persons (name, email, status) VALUES ('Carla', 'carla@example.org', 'active') RETURNING id",
				Long.class);
		assertThat(post("/api/v1/admin/persons/" + withoutPassword + "/password/reset", this.personAdmin, ""))
			.hasStatus(HttpStatus.CONFLICT);
		Long serviceAccount = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', 'CMDB sync', 'active')
				RETURNING id""", Long.class);
		assertThat(post("/api/v1/admin/persons/" + serviceAccount + "/password/reset", this.personAdmin, ""))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(post("/api/v1/admin/persons/999999/password/reset", this.personAdmin, ""))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void aFailedResetChangesNothing() {
		long dora = active("dora");
		TestSession session = session(login("dora@example.org", CHOSEN));
		String hash = activeHash(dora);
		doThrow(new IllegalStateException("storage failed")).when(this.hasher).hash(anyString());
		try {
			MvcTestResult failed = post("/api/v1/admin/persons/" + dora + "/password/reset", this.personAdmin, "");
			assertThat(failed).hasFailed();
		}
		finally {
			reset(this.hasher);
		}
		assertThat(activeHash(dora)).isEqualTo(hash);
		assertThat(get("/api/v1/me", session)).hasStatus(HttpStatus.OK);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'person.password-reset' AND target_id = '"
				+ dora + "'")).isZero();
	}

	private long active(String name) {
		Long id = this.jdbc.queryForObject("INSERT INTO persons (name, email, status) VALUES (?, ?, 'active') RETURNING id",
				Long.class, name, name + "@example.org");
		UUID credential = UUID.randomUUID();
		this.jdbc.update("INSERT INTO local_credentials (id, person_id, type) VALUES (?, ?, 'password')", credential, id);
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credential,
				this.hasher.hash(CHOSEN));
		return (id != null) ? id : 0;
	}

	private String activeHash(long personId) {
		return this.jdbc.queryForObject("""
				SELECT pc.password_hash FROM password_credentials pc JOIN local_credentials c ON c.id = pc.credential_id
				WHERE c.person_id = ? AND c.status = 'active'""", String.class, personId);
	}

	private long personId(TestSession session) {
		Long id = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
		return (id != null) ? id : 0;
	}

	private MvcTestResult login(String loginName, String password) {
		return this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.with((request) -> {
				request.setRemoteAddr("10.6.0." + Math.abs(loginName.hashCode() % 200));
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
		var request = this.mvc.post()
			.uri(uri)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		return body.isEmpty() ? request.exchange() : request.contentType("application/json").content(body).exchange();
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

}

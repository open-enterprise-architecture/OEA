package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
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
 * Spec identity/person-lifecycle (US-148; design of us-148-person-lifecycle) and the status
 * scenario of "The last path to system administration is kept" (US-146 AC5) on an own
 * database. The administrator holds `security-admin` (with `person:manage`) through an IdP group
 * mapping and logged in with a second factor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PersonLifecycleTests.ClockConfiguration.class)
class PersonLifecycleTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-lifecycle").resolve("setup-token");
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
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@Autowired
	private MutableClock clock;

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

	@Test
	void inactiveAndBackIsStoredAndAudited() {
		long anna = personId(this.principals.personWithoutRole());
		MvcTestResult inactive = status(this.admin, anna, "inactive", "parental leave");
		assertThat(inactive).hasStatus(HttpStatus.OK);
		assertThat(body(inactive).path("status").asString()).isEqualTo("inactive");
		assertThat(count("SELECT count(*) FROM persons WHERE id = " + anna + " AND status_changed_by_person_id = "
				+ personId(this.admin))).isOne();
		assertThat(status(this.admin, anna, "active", null)).hasStatus(HttpStatus.OK);
		assertThat(audit("person.status-changed", Long.toString(anna))).isEqualTo(2);
	}

	@Test
	void transitionsOutsideTheTableAreRejected() {
		long anna = personId(this.principals.personWithoutRole());
		MvcTestResult same = status(this.admin, anna, "active", null);
		assertThat(same).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(same).path("type").asString()).isEqualTo("urn:oea:problem:invalid-status-transition");
		Long invited = this.jdbc.queryForObject("INSERT INTO persons (name, email) VALUES ('Grace', ?) RETURNING id",
				Long.class, "grace-" + UUID.randomUUID() + "@example.org");
		assertThat(status(this.admin, invited, "active", null)).hasStatus(HttpStatus.CONFLICT);
		assertThat(status(this.admin, anna, "offboarded", null)).hasStatus(HttpStatus.OK);
		assertThat(status(this.admin, anna, "active", null)).hasStatus(HttpStatus.CONFLICT);
		assertThat(count("SELECT count(*) FROM persons WHERE id = " + anna + " AND status = 'offboarded'")).isOne();
	}

	@Test
	void missingPermissionOwnStatusAndServiceAccountsAreRejected() {
		this.jdbc.update("""
				INSERT INTO authorization_roles (id, name, is_builtin) VALUES ('assigner', 'Assigner', false)
				ON CONFLICT (id) DO NOTHING""");
		this.jdbc.update("""
				INSERT INTO role_permissions (role_id, permission_id) VALUES ('assigner', 'role:assign')
				ON CONFLICT DO NOTHING""");
		TestSession assigner = this.principals.personWithIdpRole("assigner");
		long anna = personId(this.principals.personWithoutRole());
		assertThat(status(assigner, anna, "offboarded", null)).hasStatus(HttpStatus.FORBIDDEN);

		MvcTestResult own = status(this.admin, personId(this.admin), "inactive", null);
		assertThat(own).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(own).path("reason").asString()).isEqualTo("self-administration");

		Long serviceAccount = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', 'CMDB sync', 'active')
				RETURNING id""", Long.class);
		assertThat(status(this.admin, serviceAccount, "inactive", null)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(count("SELECT count(*) FROM persons WHERE status = 'active' AND id IN (" + anna + ", "
				+ serviceAccount + ")")).isEqualTo(2);
	}

	@Test
	void offboardingEndsSessionsAndLocalAssignmentsAndKeepsHistory() {
		TestSession anna = this.principals.personWithIdpRole("viewer");
		long annaId = personId(anna);
		String architect = body(assign(this.admin, annaId, "{\"roleId\":\"enterprise-architect\"}")).path("id").asString();

		assertThat(status(this.admin, annaId, "offboarded", "left the company")).hasStatus(HttpStatus.OK);
		assertThat(get("/api/v1/me", anna)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'session.revoked' AND target_id = '"
				+ anna.sessionId() + "' AND details ->> 'reason' = 'person-status-change'")).isOne();
		assertThat(count("SELECT count(*) FROM role_assignments WHERE id = '" + architect + "' AND status = 'revoked' "
				+ "AND revoked_by_person_id = " + personId(this.admin) + " AND revocation_reason = 'offboarding'")).isOne();
		assertThat(audit("role.revoked", architect)).isOne();
		// a new session of the offboarded person is refused (REQ-004)
		assertThat(get("/api/v1/me", session(annaId))).hasStatus(HttpStatus.UNAUTHORIZED);

		// history retained
		MvcTestResult person = get("/api/v1/admin/persons/" + annaId, this.admin);
		assertThat(body(person).path("status").asString()).isEqualTo("offboarded");
		assertThat(audit("role.assigned", architect)).isOne();
	}

	@Test
	void assignmentsAreKeptWhileInactive() {
		long anna = personId(this.principals.personWithoutRole());
		String architect = body(assign(this.admin, anna, "{\"roleId\":\"enterprise-architect\"}")).path("id").asString();
		assertThat(status(this.admin, anna, "inactive", null)).hasStatus(HttpStatus.OK);
		assertThat(status(this.admin, anna, "active", null)).hasStatus(HttpStatus.OK);
		assertThat(count("SELECT count(*) FROM role_assignments WHERE id = '" + architect + "' AND status = 'granted'"))
			.isOne();
		assertThat(me(session(anna)).path("permissions").toString()).contains("entity:write");
	}

	@Test
	void theLastHolderOfSystemAdministrationKeepsTheStatus() {
		long erika = personId(this.principals.personWithoutRole());
		assertThat(assign(this.admin, erika, "{\"roleId\":\"system-admin\"}")).hasStatus(HttpStatus.CREATED);
		for (String status : new String[] { "inactive", "offboarded" }) {
			MvcTestResult last = status(this.admin, erika, status, null);
			assertThat(last).as(status).hasStatus(HttpStatus.CONFLICT);
			assertThat(body(last).path("type").asString()).isEqualTo("urn:oea:problem:last-admin-path");
		}
		assertThat(count("SELECT count(*) FROM persons WHERE id = " + erika + " AND status = 'active'")).isOne();
		long paul = personId(this.principals.personWithoutRole());
		assertThat(assign(this.admin, paul, "{\"roleId\":\"system-admin\"}")).hasStatus(HttpStatus.CREATED);
		assertThat(status(this.admin, erika, "offboarded", null)).hasStatus(HttpStatus.OK);
	}

	private MvcTestResult status(TestSession session, long personId, String status, String reason) {
		return post("/api/v1/admin/persons/" + personId + "/status", session, "{\"status\":\"" + status + "\""
				+ ((reason != null) ? ",\"reason\":\"" + reason + "\"" : "") + "}");
	}

	private TestSession session(long personId) {
		String token = UUID.randomUUID().toString() + UUID.randomUUID();
		UUID sessionId = this.jdbc.queryForObject("""
				INSERT INTO sessions (token_hash, person_id, client_type, auth_method, authenticated_at,
				                      last_activity_at, absolute_expires_at)
				VALUES (sha256(convert_to(?, 'UTF8')), ?, 'web-portal', 'password', now(), now(),
				        now() + interval '12 hours') RETURNING id""", UUID.class, token, personId);
		return new TestSession(token, Sessions.csrfToken(token), sessionId);
	}

	private long personId(TestSession session) {
		Long id = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
		return (id != null) ? id : 0;
	}

	private MvcTestResult assign(TestSession session, long personId, String body) {
		return post("/api/v1/admin/persons/" + personId + "/role-assignments", session, body);
	}

	private JsonNode me(TestSession session) {
		MvcTestResult me = get("/api/v1/me", session);
		assertThat(me).hasStatus(HttpStatus.OK);
		return body(me);
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

	private int audit(String eventType, String targetId) {
		return count("SELECT count(*) FROM audit.events WHERE event_type = '" + eventType + "' AND target_id = '"
				+ targetId + "'");
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

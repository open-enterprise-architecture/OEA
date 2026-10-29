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
import java.time.Duration;
import java.time.Instant;
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
 * Spec identity/role-assignments (US-144; design D1–D5, D7 of us-144-local-role-assignments) on
 * an own database. The administrator holds `security-admin` through an IdP group mapping and
 * logged in with a second factor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RoleAssignmentTests.ClockConfiguration.class)
class RoleAssignmentTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-assignments").resolve("setup-token");
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

	@Autowired
	private RoleAssignmentExpiry expiry;

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
	void assignmentStoredAuditedAndEffective() {
		TestSession anna = this.principals.personWithoutRole();
		long annaId = personId(anna);
		assertThat(me(anna).path("permissions").toString()).doesNotContain("entity:write");

		Instant validTo = this.clock.instant().plus(Duration.ofDays(30));
		MvcTestResult created = assign(this.admin, annaId, "{\"roleId\":\"enterprise-architect\",\"validTo\":\""
				+ validTo + "\",\"reason\":\"TICKET-123\"}");
		assertThat(created).hasStatus(HttpStatus.CREATED);
		JsonNode body = body(created);
		assertThat(body.path("source").asString()).isEqualTo("local");
		assertThat(body.path("status").asString()).isEqualTo("active");
		assertThat(body.path("role").path("id").asString()).isEqualTo("enterprise-architect");
		assertThat(body.path("grantedBy").path("principalType").asString()).isEqualTo("person");
		assertThat(body.path("validTo").asString()).isNotBlank();
		assertThat(body.path("reason").asString()).isEqualTo("TICKET-123");
		assertThat(body.path("sodConflict").asBoolean()).isFalse();
		assertThat(audit("role.assigned", body.path("id").asString())).isOne();

		// the change takes effect for the existing session
		JsonNode me = me(anna);
		assertThat(me.path("roles").toString()).contains("\"source\":\"local\"").contains("enterprise-architect");
		assertThat(me.path("permissions").toString()).contains("entity:write");
		assertThat(get("/api/v1/admin/persons/" + annaId + "/role-assignments", this.admin)).hasStatus(HttpStatus.OK);
		assertThat(body(get("/api/v1/admin/persons/" + annaId + "/role-assignments", this.admin))).hasSize(1);
	}

	@Test
	void invalidValidityUnknownRoleAndDuplicate() {
		long anna = personId(this.principals.personWithoutRole());
		Instant now = this.clock.instant();
		assertThat(assign(this.admin, anna, "{\"roleId\":\"viewer\",\"validFrom\":\"" + now + "\",\"validTo\":\"" + now
				+ "\"}")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(assign(this.admin, anna, "{\"roleId\":\"no-such-role\"}")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(count("SELECT count(*) FROM role_assignments WHERE person_id = " + anna)).isZero();
		assertThat(assign(this.admin, anna, "{\"roleId\":\"viewer\"}")).hasStatus(HttpStatus.CREATED);
		MvcTestResult duplicate = assign(this.admin, anna, "{\"roleId\":\"viewer\"}");
		assertThat(duplicate).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(duplicate).path("type").asString()).isEqualTo("urn:oea:problem:conflict");
	}

	@Test
	void missingPermissionAndOwnAssignmentAreDenied() {
		TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
		long anna = personId(this.principals.personWithoutRole());
		assertThat(assign(metamodelAdmin, anna, "{\"roleId\":\"viewer\"}")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'authz.denied' "
				+ "AND details ->> 'operation' = 'createRoleAssignment'")).isPositive();

		MvcTestResult own = assign(this.admin, personId(this.admin), "{\"roleId\":\"viewer\"}");
		assertThat(own).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(own).path("reason").asString()).isEqualTo("self-administration");
		assertThat(count("SELECT count(*) FROM role_assignments WHERE person_id = " + personId(this.admin))).isZero();
	}

	@Test
	void serviceAccountsGetOnlyAssignableRoles() {
		Long serviceAccount = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', 'CMDB sync', 'active')
				RETURNING id""", Long.class);
		assertThat(assign(this.admin, serviceAccount, "{\"roleId\":\"system-admin\"}"))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(assign(this.admin, serviceAccount, "{\"roleId\":\"integration-writer\"}")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void combinedMetamodelAndVisibilityAdministrationNeedsAcknowledgement() {
		long anna = personId(this.principals.personWithoutRole());
		assertThat(assign(this.admin, anna, "{\"roleId\":\"metamodel-admin\"}")).hasStatus(HttpStatus.CREATED);
		MvcTestResult conflict = assign(this.admin, anna, "{\"roleId\":\"security-admin\"}");
		assertThat(conflict).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(conflict).path("type").asString()).isEqualTo("urn:oea:problem:sod-conflict");
		MvcTestResult acknowledged = assign(this.admin, anna,
				"{\"roleId\":\"security-admin\",\"acknowledgeSodConflict\":true}");
		assertThat(acknowledged).hasStatus(HttpStatus.CREATED);
		assertThat(body(acknowledged).path("sodConflict").asBoolean()).isTrue();
	}

	@Test
	void revocationKeepsHistoryAndEndsSessions() {
		TestSession anna = this.principals.personWithoutRole();
		long annaId = personId(anna);
		String architect = body(assign(this.admin, annaId, "{\"roleId\":\"enterprise-architect\"}")).path("id").asString();
		assertThat(assign(this.admin, annaId, "{\"roleId\":\"viewer\"}")).hasStatus(HttpStatus.CREATED);
		assertThat(me(anna).path("roles")).hasSize(2);

		MvcTestResult revoked = revoke(this.admin, annaId, architect);
		assertThat(revoked).hasStatus(HttpStatus.OK);
		assertThat(body(revoked).path("status").asString()).isEqualTo("revoked");
		assertThat(body(revoked).path("revokedAt").asString()).isNotBlank();
		assertThat(body(revoked).path("revokedBy").path("principalType").asString()).isEqualTo("person");
		assertThat(count("SELECT count(*) FROM role_assignments WHERE id = '" + architect + "'")).isOne();
		assertThat(audit("role.revoked", architect)).isOne();
		assertThat(get("/api/v1/me", anna)).hasStatus(HttpStatus.UNAUTHORIZED);

		// the next login holds only the remaining role
		TestSession again = session(annaId);
		assertThat(me(again).path("roles")).hasSize(1);
		assertThat(me(again).path("roles").get(0).path("role").path("id").asString()).isEqualTo("viewer");
		MvcTestResult twice = revoke(this.admin, annaId, architect);
		assertThat(twice).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(twice).path("type").asString()).isEqualTo("urn:oea:problem:conflict");
	}

	@Test
	void preProvisioningAndPrivilegedRolesWithoutSecondFactor() {
		Long invited = this.jdbc.queryForObject("""
				INSERT INTO persons (name, email, status) VALUES ('Grace', ?, 'invited') RETURNING id""", Long.class,
				"grace-" + UUID.randomUUID() + "@example.org");
		assertThat(assign(this.admin, invited, "{\"roleId\":\"viewer\"}")).hasStatus(HttpStatus.CREATED);
		// the first login sets the person active (identity/oidc-login)
		this.jdbc.update("UPDATE persons SET status = 'active' WHERE id = ?", invited);
		assertThat(me(session(invited)).path("roles").get(0).path("role").path("id").asString()).isEqualTo("viewer");

		TestSession anna = this.principals.personWithoutRole();
		assertThat(assign(this.admin, personId(anna), "{\"roleId\":\"metamodel-admin\"}")).hasStatus(HttpStatus.CREATED);
		JsonNode role = me(anna).path("roles").get(0);
		assertThat(role.path("effective").asBoolean()).isFalse();
		assertThat(role.path("notEffectiveReason").asString()).isEqualTo("mfa-required");
	}

	@Test
	void expiredAssignmentStopsApplyingAndIsRecordedOnce() {
		TestSession anna = this.principals.personWithoutRole();
		long annaId = personId(anna);
		String id = body(assign(this.admin, annaId, "{\"roleId\":\"enterprise-architect\",\"validTo\":\""
				+ this.clock.instant().plus(Duration.ofMinutes(10)) + "\"}")).path("id").asString();
		assertThat(me(anna).path("permissions").toString()).contains("entity:write");

		this.clock.advance(Duration.ofMinutes(11));
		assertThat(me(anna).path("permissions").toString()).doesNotContain("entity:write");
		this.expiry.recordExpiries();
		this.expiry.recordExpiries();
		assertThat(audit("role.assignment-expired", id)).isOne();
		assertThat(body(get("/api/v1/admin/persons/" + annaId + "/role-assignments?status=expired", this.admin))
			.get(0)
			.path("status")
			.asString()).isEqualTo("expired");
	}

	@Test
	void rolePreview() {
		MvcTestResult role = get("/api/v1/admin/roles/metamodel-admin", this.admin);
		assertThat(role).hasStatus(HttpStatus.OK);
		assertThat(body(role).path("permissions").toString()).contains("metamodel:write");
		assertThat(body(role).path("privileged").asBoolean()).isTrue();
		assertThat(get("/api/v1/admin/roles/no-such-role", this.admin)).hasStatus(HttpStatus.NOT_FOUND);
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

	private MvcTestResult revoke(TestSession session, long personId, String assignmentId) {
		return post("/api/v1/admin/persons/" + personId + "/role-assignments/" + assignmentId + "/revoke", session, "{}");
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

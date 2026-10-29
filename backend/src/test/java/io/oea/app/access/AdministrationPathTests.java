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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
 * Spec identity/access-administration "The last path to system administration is kept" (US-146,
 * ADR-032 SoD-7; design D6 of us-144-local-role-assignments) on an own database, in one flow
 * because each step builds on the paths of the previous one. The administrator holds
 * `security-admin` through an IdP group mapping; it is no path to `system-admin`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdministrationPathTests.ClockConfiguration.class)
class AdministrationPathTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-admin-paths").resolve("setup-token");
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
	void everyRevocationKeepsAPathToSystemAdministration() throws Exception {
		// no local system admin account and no usable mapping on this instance
		assertThat(count("SELECT count(*) FROM system_admin_accounts")).isZero();
		long erika = personId(this.principals.personWithoutRole());
		long paul = personId(this.principals.personWithoutRole());
		String erikaAdmin = body(assign(this.admin, erika, "{\"roleId\":\"system-admin\"}")).path("id").asString();

		// Revoking the last holder
		MvcTestResult last = revoke(this.admin, erika, erikaAdmin);
		assertThat(last).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(last).path("type").asString()).isEqualTo("urn:oea:problem:last-admin-path");
		assertThat(count("SELECT count(*) FROM role_assignments WHERE id = '" + erikaAdmin + "' AND status = 'granted'"))
			.isOne();

		// Another path exists
		String paulAdmin = body(assign(this.admin, paul, "{\"roleId\":\"system-admin\"}")).path("id").asString();
		assertThat(revoke(this.admin, erika, erikaAdmin)).hasStatus(HttpStatus.OK);
		assertThat(revoke(this.admin, paul, paulAdmin)).hasStatus(HttpStatus.CONFLICT);

		// an assignment with validTo is no path: it would expire
		long temporary = personId(this.principals.personWithoutRole());
		assertThat(assign(this.admin, temporary, "{\"roleId\":\"system-admin\",\"validTo\":\""
				+ this.clock.instant().plus(Duration.ofDays(1)) + "\"}")).hasStatus(HttpStatus.CREATED);
		assertThat(revoke(this.admin, paul, paulAdmin)).hasStatus(HttpStatus.CONFLICT);

		// Local account with a person as the other path
		this.jdbc.update("INSERT INTO system_admin_accounts (login_name, status) VALUES ('admin', 'active')");
		TestSession systemAdmin = this.principals.personWithIdpRole("system-admin");
		assertThat(post("/api/v1/admin/system-admin-account/deactivate", systemAdmin, "{}")).hasStatus(HttpStatus.OK);

		// Deleting the last usable mapping
		UUID mapping = usableMapping();
		assertThat(revoke(this.admin, paul, paulAdmin)).hasStatus(HttpStatus.OK);
		for (String change : List.of("{\"active\":false}", "{\"roleId\":\"viewer\"}")) {
			MvcTestResult changed = put("/api/v1/admin/idp-group-mappings/" + mapping, this.admin, mappingWrite(mapping, change));
			assertThat(changed).as(change).hasStatus(HttpStatus.CONFLICT);
			assertThat(body(changed).path("type").asString()).isEqualTo("urn:oea:problem:last-admin-path");
		}
		MvcTestResult deleted = this.mvc.delete()
			.uri("/api/v1/admin/idp-group-mappings/" + mapping)
			.cookie(new Cookie(SessionCookie.NAME, this.admin.token()))
			.header(CsrfOriginFilter.HEADER, this.admin.csrfToken())
			.exchange();
		assertThat(deleted).hasStatus(HttpStatus.CONFLICT);
		assertThat(count("SELECT count(*) FROM idp_group_mappings WHERE id = '" + mapping
				+ "' AND active AND deleted_at IS NULL AND role_id = 'system-admin'")).isOne();
		// a change that keeps the mapping a usable path passes
		assertThat(put("/api/v1/admin/idp-group-mappings/" + mapping, this.admin, mappingWrite(mapping, "{}")))
			.hasStatus(HttpStatus.OK);

		// Parallel revocations: two persons are the only paths
		long x = personId(this.principals.personWithoutRole());
		long y = personId(this.principals.personWithoutRole());
		String xAdmin = body(assign(this.admin, x, "{\"roleId\":\"system-admin\"}")).path("id").asString();
		String yAdmin = body(assign(this.admin, y, "{\"roleId\":\"system-admin\"}")).path("id").asString();
		this.jdbc.update("UPDATE idp_group_mappings SET last_matched_at = NULL WHERE id = ?", mapping);
		CompletableFuture<MvcTestResult> first = CompletableFuture.supplyAsync(() -> revoke(this.admin, x, xAdmin));
		CompletableFuture<MvcTestResult> second = CompletableFuture.supplyAsync(() -> revoke(this.admin, y, yAdmin));
		List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
	}

	/** A mapping to system-admin that has matched in a login (us-019-admin-safeguards D3). */
	private UUID usableMapping() {
		UUID provider = this.jdbc.queryForObject("""
				INSERT INTO auth_providers (name, issuer, client_id) VALUES ('Admins IdP', 'https://admins.example.org', 'oea')
				RETURNING id""", UUID.class);
		return this.jdbc.queryForObject("""
				INSERT INTO idp_group_mappings (auth_provider_id, issuer, group_identifier, role_id, last_matched_at,
				                                created_by_admin_id)
				VALUES (?, 'https://admins.example.org', ?, 'system-admin', now(),
				        (SELECT id FROM system_admin_accounts LIMIT 1))
				RETURNING id""", UUID.class, provider, UUID.randomUUID().toString());
	}

	/** The mapping's current write form with the given fields replaced. */
	private String mappingWrite(UUID mapping, String change) {
		Map<String, Object> current = this.jdbc.queryForMap(
				"SELECT auth_provider_id::text AS provider, group_identifier, role_id, active FROM idp_group_mappings WHERE id = ?",
				mapping);
		ObjectNode write = JsonMapper.shared().createObjectNode();
		write.put("authProviderId", (String) current.get("provider"));
		write.put("groupIdentifier", (String) current.get("group_identifier"));
		write.put("roleId", (String) current.get("role_id"));
		write.put("active", (Boolean) current.get("active"));
		JsonNode replacement = JsonMapper.shared().readTree(change);
		replacement.properties().forEach((entry) -> write.set(entry.getKey(), entry.getValue()));
		return write.toString();
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

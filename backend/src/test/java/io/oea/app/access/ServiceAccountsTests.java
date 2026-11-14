package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.access.ServiceAccountStore;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.service.PasswordHasher;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Spec identity/service-accounts (US-142, US-149, REQ-155, REQ-176; design D1–D7 of
 * us-142-service-accounts) through the API on an own database, after the setup phase. The
 * administrator holds `system-admin` through an IdP group mapping and logged in with a second
 * factor; the maximum secret validity is 365 days.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ServiceAccountsTests.ClockConfiguration.class)
class ServiceAccountsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String BASE = "/api/v1/admin/service-accounts";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-service-accounts").resolve("setup-token");
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
	private PasswordHasher hasher;

	@Autowired
	private PermissionResolver permissions;

	@Autowired
	private ServiceAccountStore store;

	private TestPrincipals principals;

	private TestSession admin;

	@BeforeEach
	void administrator() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.jdbc.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'identity-binding') ON CONFLICT (id) DO NOTHING""");
		this.admin = this.principals.personWithIdpRole("system-admin");
		authenticatedAgo(this.admin, Duration.ZERO);
	}

	// Create a service account

	@Test
	void createdWithSecretShownOnce() throws Exception {
		MvcTestResult created = create(this.admin, "{\"name\":\"ci-scanner\",\"roles\":[\"integration-writer\"]}");
		assertThat(created).hasStatus(HttpStatus.CREATED);
		JsonNode body = body(created);
		String clientId = body.path("id").asString();
		String secret = body.path("clientSecret").asString();
		assertThat(secret).startsWith(ServiceAccounts.SECRET_PREFIX).hasSize(50);
		assertThat(body.path("status").asString()).isEqualTo("active");
		assertThat(body.path("roles").toString()).isEqualTo("[\"integration-writer\"]");
		String hash = this.jdbc.queryForObject("SELECT client_secret_hash FROM machine_credentials WHERE client_id = ?::uuid",
				String.class, clientId);
		assertThat(hash).startsWith("$argon2id$").isNotEqualTo(secret);
		assertThat(this.hasher.matches(secret, hash)).isTrue();
		assertThat(count("SELECT count(*) FROM persons WHERE person_type = 'service-account' AND name = 'ci-scanner'"))
			.isOne();
		assertThat(audit("service-account.created", clientId)).isOne();

		MvcTestResult read = get(BASE + "/" + clientId, this.admin);
		assertThat(read).hasStatus(HttpStatus.OK);
		assertThat(read.getResponse().getContentAsString()).doesNotContain("clientSecret").doesNotContain(secret);
		assertThat(get(BASE, this.admin).getResponse().getContentAsString()).doesNotContain(secret)
			.doesNotContain("$argon2id$");
		// no audit event contains the secret or its hash
		assertThat(count("SELECT count(*) FROM audit.events WHERE strpos(details::text, '" + secret
				+ "') > 0 OR strpos(details::text, '" + hash + "') > 0")).isZero();
	}

	@Test
	void defaultRoleIsIntegrationWriter() {
		JsonNode body = body(create(this.admin, "{\"name\":\"default-role\"}"));
		assertThat(body.path("roles").toString()).isEqualTo("[\"integration-writer\"]");
	}

	@Test
	void assignableRolesAreAccepted() {
		JsonNode body = body(create(this.admin, "{\"name\":\"three-roles\",\"roles\":[\"viewer\",\"integration-writer\",\"auditor\"]}"));
		assertThat(body.path("roles").toString()).isEqualTo("[\"auditor\",\"integration-writer\",\"viewer\"]");
	}

	@Test
	void enterpriseArchitectIsForbidden() {
		TestSession architect = this.principals.personWithIdpRole("enterprise-architect");
		assertThat(create(architect, "{\"name\":\"forbidden\"}")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(get(BASE, architect)).hasStatus(HttpStatus.FORBIDDEN);
		String clientId = clientId(create(this.admin, "{\"name\":\"target\"}"));
		assertThat(get(BASE + "/" + clientId, architect)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(put(clientId, architect, "{\"name\":\"renamed\"}")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(rotate(clientId, architect, null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(delete(clientId, architect)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM persons WHERE name = 'forbidden'")).isZero();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'authz.denied'")).isGreaterThanOrEqualTo(6);
	}

	// Name of a service account

	@ParameterizedTest
	@CsvSource({ "100, 201", "101, 400", "0, 400" })
	void nameLengthBoundaries(int length, int status) {
		String name = (length == 0) ? "" : "n".repeat(length - 8) + UUID.randomUUID().toString().substring(0, 8);
		assertThat(create(this.admin, "{\"name\":\"" + name + "\"}").getResponse().getStatus()).isEqualTo(status);
	}

	@Test
	void blankNameIsRejected() {
		assertThat(create(this.admin, "{\"name\":\"   \"}")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void nameTakenIgnoringCase() {
		assertThat(create(this.admin, "{\"name\":\"taken-scanner\"}")).hasStatus(HttpStatus.CREATED);
		MvcTestResult second = create(this.admin, "{\"name\":\"Taken-Scanner\"}");
		assertThat(second).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(count("SELECT count(*) FROM persons WHERE lower(name) = 'taken-scanner'")).isOne();
	}

	// Secret expiry

	@Test
	void defaultExpiryIs90Days() {
		JsonNode body = body(create(this.admin, "{\"name\":\"default-expiry\"}"));
		Instant created = Instant.parse(body.path("createdAt").asString());
		assertThat(Instant.parse(body.path("secretExpiresAt").asString()))
			.isEqualTo(this.clock.instant().truncatedTo(ChronoUnit.MICROS).plus(Duration.ofDays(90)));
		assertThat(created).isNotNull();
	}

	@ParameterizedTest
	@CsvSource({ "1, 201", "0, 400", "365, 201", "366, 400" })
	void validityBoundaries(int days, int status) {
		MvcTestResult result = create(this.admin,
				"{\"name\":\"validity-" + UUID.randomUUID() + "\",\"secretValidityDays\":" + days + "}");
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
	}

	@ParameterizedTest
	@CsvSource({ "PT0S, true", "PT1M, false" })
	void expiringSoonBoundary(Duration beyond, boolean soon) {
		String clientId = clientId(create(this.admin, "{\"name\":\"soon-" + UUID.randomUUID() + "\"}"));
		// PostgreSQL rounds to microseconds
		Instant expiry = this.clock.instant().truncatedTo(ChronoUnit.MICROS).plus(Duration.ofDays(14)).plus(beyond);
		this.jdbc.update("UPDATE machine_credentials SET expires_at = ? WHERE client_id = ?::uuid AND status = 'active'",
				Timestamp.from(expiry), clientId);
		assertThat(body(get(BASE + "/" + clientId, this.admin)).path("secretExpiresSoon").asBoolean()).isEqualTo(soon);
	}

	// Rotate the secret

	@Test
	void rotationKeepsTheClientId() {
		JsonNode created = body(create(this.admin, "{\"name\":\"rotated\"}"));
		String clientId = created.path("id").asString();
		this.clock.advance(Duration.ofSeconds(1));
		authenticatedAgo(this.admin, Duration.ZERO);
		MvcTestResult rotated = rotate(clientId, this.admin, "{\"secretValidityDays\":30}");
		assertThat(rotated).hasStatus(HttpStatus.OK);
		JsonNode body = body(rotated);
		assertThat(body.path("id").asString()).isEqualTo(clientId);
		assertThat(body.path("clientSecret").asString()).startsWith("oea_cs_")
			.isNotEqualTo(created.path("clientSecret").asString());
		assertThat(body.path("rotatedAt").isNull()).isFalse();
		assertThat(this.store.secretStatuses(UUID.fromString(clientId))).containsExactly("rotated", "active");
		assertThat(audit("service-account.secret-rotated", clientId)).isOne();
	}

	@Test
	void rotationWithoutBodyUsesTheDefault() {
		String clientId = clientId(create(this.admin, "{\"name\":\"rotated-default\"}"));
		assertThat(rotate(clientId, this.admin, null)).hasStatus(HttpStatus.OK);
	}

	@Test
	void unknownServiceAccount() {
		String unknown = UUID.randomUUID().toString();
		assertThat(rotate(unknown, this.admin, null)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(BASE + "/" + unknown, this.admin)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(put(unknown, this.admin, "{\"name\":\"x\"}")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(delete(unknown, this.admin)).hasStatus(HttpStatus.NOT_FOUND);
	}

	// Roles of a service account

	@ParameterizedTest
	@ValueSource(strings = { "system-admin", "enterprise-architect" })
	void nonAssignableRoleIsRejected(String role) {
		String name = "privileged-" + UUID.randomUUID();
		int denials = count("SELECT count(*) FROM audit.events WHERE event_type = 'authz.denied' AND details ->> 'reason' = 'sod-4'");
		MvcTestResult result = create(this.admin, "{\"name\":\"" + name + "\",\"roles\":[\"" + role + "\"]}");
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("detail").asString()).contains(role);
		assertThat(count("SELECT count(*) FROM persons WHERE name = '" + name + "'")).isZero();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'authz.denied' AND details ->> 'reason' = 'sod-4'"))
			.isEqualTo(denials + 1);
	}

	@Test
	void unknownRoleIsRejected() {
		MvcTestResult result = create(this.admin, "{\"name\":\"unknown-role\",\"roles\":[\"no-such-role\"]}");
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("detail").asString()).contains("no-such-role");
		assertThat(count("SELECT count(*) FROM persons WHERE name = 'unknown-role'")).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "20, 201", "21, 400" })
	void roleCountBoundaries(int roles, int status) {
		for (int n = 1; n <= roles; n++) {
			this.jdbc.update("""
					INSERT INTO authorization_roles (id, name, is_builtin, assignable_to_service_accounts)
					VALUES (?, ?, false, true) ON CONFLICT (id) DO NOTHING""", "reader-" + n, "Reader " + n);
		}
		String list = IntStream.rangeClosed(1, roles).mapToObj((n) -> "\"reader-" + n + "\"").collect(Collectors.joining(","));
		MvcTestResult result = create(this.admin, "{\"name\":\"many-" + roles + "\",\"roles\":[" + list + "]}");
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
	}

	@Test
	void rolesNeedRoleAssign() {
		TestSession manager = accountManager();
		MvcTestResult withRoles = create(manager, "{\"name\":\"manager-roles\",\"roles\":[\"viewer\"]}");
		assertThat(withRoles).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM persons WHERE name = 'manager-roles'")).isZero();
		MvcTestResult withoutRoles = create(manager, "{\"name\":\"manager-plain\",\"roles\":[]}");
		assertThat(withoutRoles).hasStatus(HttpStatus.CREATED);
		// an unchanged role list needs no role:assign
		String clientId = clientId(withoutRoles);
		assertThat(put(clientId, manager, "{\"name\":\"manager-renamed\",\"roles\":[]}")).hasStatus(HttpStatus.OK);
		assertThat(put(clientId, manager, "{\"name\":\"manager-renamed\",\"roles\":[\"viewer\"]}"))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// Change a service account

	@Test
	void roleReplaced() {
		String clientId = clientId(create(this.admin, "{\"name\":\"cmdb-importer\",\"roles\":[\"viewer\"]}"));
		long personId = personOf(clientId);
		assertThat(effective(personId)).contains("entity:read").doesNotContain("entity:write");
		MvcTestResult updated = put(clientId, this.admin,
				"{\"name\":\"cmdb-importer\",\"description\":\"Nightly import\",\"roles\":[\"integration-writer\"]}");
		assertThat(updated).hasStatus(HttpStatus.OK);
		assertThat(body(updated).path("roles").toString()).isEqualTo("[\"integration-writer\"]");
		assertThat(body(updated).path("description").asString()).isEqualTo("Nightly import");
		assertThat(count("SELECT count(*) FROM role_assignments WHERE person_id = " + personId
				+ " AND role_id = 'viewer' AND status = 'revoked'")).isOne();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type IN ('role.assigned', 'role.revoked') "
				+ "AND details ->> 'personId' = '" + personId + "'")).isEqualTo(3);
		assertThat(audit("service-account.updated", clientId)).isOne();
		assertThat(effective(personId)).contains("entity:write");
	}

	// Deactivate a service account

	@Test
	void deactivationIsFinal() {
		String clientId = clientId(create(this.admin, "{\"name\":\"deactivated\",\"roles\":[\"viewer\"]}"));
		assertThat(delete(clientId, this.admin)).hasStatus(HttpStatus.NO_CONTENT);
		JsonNode account = body(get(BASE + "/" + clientId, this.admin));
		assertThat(account.path("status").asString()).isEqualTo("inactive");
		assertThat(account.path("roles").toString()).isEqualTo("[]");
		assertThat(account.path("secretExpiresAt").isNull()).isTrue();
		assertThat(this.store.secretStatuses(UUID.fromString(clientId))).containsExactly("revoked");
		assertThat(audit("service-account.deactivated", clientId)).isOne();

		assertThat(rotate(clientId, this.admin, null)).hasStatus(HttpStatus.CONFLICT);
		assertThat(put(clientId, this.admin, "{\"name\":\"deactivated\",\"roles\":[\"viewer\"]}"))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(delete(clientId, this.admin)).hasStatus(HttpStatus.CONFLICT);
		assertThat(this.store.secretStatuses(UUID.fromString(clientId))).containsExactly("revoked");
		assertThat(body(get(BASE + "/" + clientId, this.admin)).path("status").asString()).isEqualTo("inactive");
	}

	// Re-authentication

	@ParameterizedTest
	@CsvSource({ "PT4M59S, 200", "PT5M1S, 401" })
	void rotationNeedsARecentAuthentication(Duration ago, int status) {
		String clientId = clientId(create(this.admin, "{\"name\":\"reauth-" + UUID.randomUUID() + "\"}"));
		authenticatedAgo(this.admin, ago);
		MvcTestResult result = rotate(clientId, this.admin, null);
		assertThat(result.getResponse().getStatus()).isEqualTo(status);
		if (status == 401) {
			assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:reauthentication-required");
			assertThat(this.store.secretStatuses(UUID.fromString(clientId))).containsExactly("active");
		}
	}

	@Test
	void createAndDeactivateNeedARecentAuthentication() {
		String clientId = clientId(create(this.admin, "{\"name\":\"reauth-deactivate\"}"));
		authenticatedAgo(this.admin, Duration.ofMinutes(5).plusSeconds(1));
		assertThat(create(this.admin, "{\"name\":\"reauth-create\"}")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(delete(clientId, this.admin)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("SELECT count(*) FROM persons WHERE name = 'reauth-create'")).isZero();
		assertThat(count("SELECT count(*) FROM persons WHERE name = 'reauth-deactivate' AND status = 'active'")).isOne();
		// a change hands out no secret and needs no recent authentication
		assertThat(put(clientId, this.admin, "{\"name\":\"reauth-deactivate\"}")).hasStatus(HttpStatus.OK);
	}

	// List service accounts

	@Test
	void activeFilter() throws Exception {
		String active = clientId(create(this.admin, "{\"name\":\"filter-active\"}"));
		String inactive = clientId(create(this.admin, "{\"name\":\"filter-inactive\"}"));
		delete(inactive, this.admin);
		String onlyActive = get(BASE + "?active=true", this.admin).getResponse().getContentAsString();
		assertThat(onlyActive).contains(active).doesNotContain(inactive);
		String onlyInactive = get(BASE + "?active=false", this.admin).getResponse().getContentAsString();
		assertThat(onlyInactive).contains(inactive).doesNotContain(active);
	}

	/** A person with `service-account:manage` without `role:assign` (stored directly, as no API allows such a role). */
	private TestSession accountManager() {
		this.jdbc.update("""
				INSERT INTO authorization_roles (id, name, is_builtin) VALUES ('account-manager', 'Account manager', false)
				ON CONFLICT (id) DO NOTHING""");
		this.jdbc.update("""
				INSERT INTO role_permissions (role_id, permission_id) VALUES ('account-manager', 'service-account:manage')
				ON CONFLICT DO NOTHING""");
		TestSession manager = this.principals.personWithIdpRole("account-manager");
		authenticatedAgo(manager, Duration.ZERO);
		return manager;
	}

	private List<String> effective(long personId) {
		OeaPrincipal principal = new OeaPrincipal(OeaPrincipal.Type.PERSON, null, personId, "service account", null,
				false, this.clock.instant());
		return List.copyOf(this.permissions.permissions(principal));
	}

	private void authenticatedAgo(TestSession session, Duration ago) {
		this.jdbc.update("UPDATE sessions SET authenticated_at = ? WHERE id = ?",
				Timestamp.from(this.clock.instant().minus(ago)), session.sessionId());
		this.permissions.evictSession(session.sessionId());
	}

	private long personOf(String clientId) {
		Long id = this.jdbc.queryForObject("SELECT person_id FROM machine_credentials WHERE client_id = ?::uuid LIMIT 1",
				Long.class, clientId);
		return (id != null) ? id : 0;
	}

	private MvcTestResult create(TestSession session, String body) {
		return send(this.mvc.post().uri(BASE), session, body);
	}

	private MvcTestResult put(String clientId, TestSession session, String body) {
		return send(this.mvc.put().uri(BASE + "/" + clientId), session, body);
	}

	private MvcTestResult rotate(String clientId, TestSession session, String body) {
		return send(this.mvc.post().uri(BASE + "/" + clientId + "/credentials/rotate"), session, body);
	}

	private MvcTestResult delete(String clientId, TestSession session) {
		return send(this.mvc.delete().uri(BASE + "/" + clientId), session, null);
	}

	private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder request, TestSession session, String body) {
		request.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request.contentType("application/json").content(body);
		}
		return request.exchange();
	}

	private MvcTestResult get(String uri, TestSession session) {
		return this.mvc.get().uri(uri).cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
	}

	private static String clientId(MvcTestResult created) {
		assertThat(created).hasStatus(HttpStatus.CREATED);
		return body(created).path("id").asString();
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

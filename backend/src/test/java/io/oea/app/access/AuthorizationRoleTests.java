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
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec identity/authorization-roles (US-151, UC-23 A5, ADR-032 SoD-2, SoD-4; design D4 of
 * us-128-override-role-list).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationRoleTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String ROLES = "/api/v1/admin/roles";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> TOKEN_FILE);
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

	private TestPrincipals principals;

	private TestSession max;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.max = this.principals.personWithIdpRole("security-admin");
	}

	// Requirement: Create a custom role

	@Test
	void roleWithoutPermissions() {
		String id = id("iso");
		JsonNode created = created(create(role(id, "Information Security Officer"), this.max));
		assertThat(created.path("isBuiltin").asBoolean()).isFalse();
		assertThat(created.path("privileged").asBoolean()).isFalse();
		assertThat(created.path("permissions")).isEmpty();
		JsonNode listed = listed(id);
		assertThat(listed.path("name").asString()).isEqualTo("Information Security Officer");
		assertThat(listed.path("overridesPropertyVisibility").asBoolean()).isFalse();
		assertThat(this.jdbc.queryForObject("""
				SELECT severity FROM audit.events WHERE event_type = 'role.created' AND target_type = 'authorization-role'
				AND target_id = ?""", String.class, id)).isEqualTo("info");
		// usable as a read role of a property
		UUID property = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, 'string') RETURNING id""",
				UUID.class, "Classification " + UUID.randomUUID(), "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
		ArrayNode change = JsonMapper.shared().createArrayNode();
		ObjectNode entry = change.addObject().put("propertyDefId", property.toString()).put("visibilityMode", "role-restricted");
		entry.putArray("readRoles").add(id);
		entry.putArray("writeRoles");
		assertThat(call(HttpMethod.PUT, "/api/v1/admin/property-visibility", change, this.max)).hasStatus(HttpStatus.OK);
	}

	@Test
	void roleWithPermissions() {
		String id = id("migration-support");
		ObjectNode role = role(id, "Migration support");
		role.putArray("permissions").add("entity:read").add("portal:read").add("entity:read");
		// a repeated permission counts once
		assertThat(created(create(role, this.max)).path("permissions").toString())
			.isEqualTo("[\"entity:read\",\"portal:read\"]");
		TestSession holder = this.principals.personWithIdpRole(id);
		MvcTestResult me = call(HttpMethod.GET, "/api/v1/me", null, holder);
		assertThat(me).hasStatus(HttpStatus.OK);
		List<String> permissions = body(me).path("permissions").valueStream()
			.map((granted) -> granted.path("permission").asString())
			.distinct()
			.sorted()
			.toList();
		assertThat(permissions).containsExactly("entity:read", "portal:read");
	}

	@Test
	void privilegedRoleIsAuditedHigh() {
		String id = id("people-office");
		ObjectNode role = role(id, "People office");
		role.putArray("permissions").add("person:manage");
		assertThat(created(create(role, this.max)).path("privileged").asBoolean()).isTrue();
		assertThat(this.jdbc.queryForObject(
				"SELECT severity FROM audit.events WHERE event_type = 'role.created' AND target_id = ?", String.class, id))
			.isEqualTo("high");
	}

	// Requirement: Invalid role definitions

	@Test
	void separationOfDuties() {
		for (String visibility : new String[] { "visibility:configure", "visibility-scope:assign" }) {
			String id = id("combined");
			ObjectNode role = role(id, "Combined");
			role.putArray("permissions").add("metamodel:write").add(visibility);
			assertInvalid(create(role, this.max), "permissions");
			assertNotStored(id);
		}
	}

	@Test
	void idTaken() {
		assertThat(create(role("viewer", "Viewer"), this.max)).hasStatus(HttpStatus.CONFLICT);
		String id = id("twice");
		created(create(role(id, "Twice"), this.max));
		assertThat(create(role(id, "Again"), this.max)).hasStatus(HttpStatus.CONFLICT);
		assertThat(listed(id).path("name").asString()).isEqualTo("Twice");
	}

	@Test
	void unknownPermission() {
		String id = id("flyer");
		ObjectNode role = role(id, "Flyer");
		role.putArray("permissions").add("entity:fly");
		assertInvalid(create(role, this.max), "permissions");
		assertNotStored(id);
	}

	@Test
	void adminClassPermissionForServiceAccounts() {
		String id = id("service-people");
		ObjectNode role = role(id, "Service people").put("assignableToServiceAccounts", true);
		role.putArray("permissions").add("person:manage");
		assertInvalid(create(role, this.max), "assignableToServiceAccounts");
		assertNotStored(id);
		// a role without an admin-class permission may be assignable
		String reader = id("service-reader");
		ObjectNode allowed = role(reader, "Service reader").put("assignableToServiceAccounts", true);
		allowed.putArray("permissions").add("entity:read");
		assertThat(created(create(allowed, this.max)).path("assignableToServiceAccounts").asBoolean()).isTrue();
	}

	// Requirement: Creating roles needs role:assign

	@Test
	void metamodelAdministrator() {
		TestSession kurt = this.principals.personWithIdpRole("metamodel-admin");
		String id = id("kurts");
		assertThat(create(role(id, "Kurt's"), kurt)).hasStatus(HttpStatus.FORBIDDEN);
		assertNotStored(id);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events e JOIN sessions s ON s.person_id = e.actor_person_id
				WHERE e.event_type = 'authz.denied' AND s.id = ? AND e.details ->> 'operation' = 'createAuthorizationRole'""",
				Integer.class, kurt.sessionId())).isEqualTo(1);
	}

	// Limits: name 1–100, ID 2–63 characters, at most 50 permissions

	@ParameterizedTest
	@CsvSource({ "100, 10, 0, 201", "101, 10, 0, 400", "0, 10, 0, 400", "10, 63, 0, 201", "10, 2, 0, 201",
			"10, 64, 0, 400", "10, 1, 0, 400", "10, 10, 50, 422", "10, 10, 51, 400" })
	void limits(int nameLength, int idLength, int permissionCount, int status) {
		String id = ("l" + UUID.randomUUID().toString().replace("-", "") + "x".repeat(64)).substring(0, idLength);
		ObjectNode role = role(id, "n".repeat(nameLength));
		ArrayNode permissions = role.putArray("permissions");
		// distinct permission IDs that pass the contract but are unknown: 50 reach the catalog check (422)
		IntStream.range(0, permissionCount).forEach((i) -> permissions.add("unknown:p" + (char) ('a' + i % 26)
				+ (char) ('a' + i / 26)));
		assertThat(create(role, this.max)).hasStatus(status);
	}

	private String id(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	private static ObjectNode role(String id, String name) {
		ObjectNode role = JsonMapper.shared().createObjectNode().put("id", id).put("name", name);
		role.putArray("permissions");
		return role;
	}

	private MvcTestResult create(ObjectNode role, TestSession session) {
		return call(HttpMethod.POST, ROLES, role, session);
	}

	private JsonNode listed(String id) {
		MvcTestResult list = call(HttpMethod.GET, ROLES, null, this.max);
		assertThat(list).hasStatus(HttpStatus.OK);
		return body(list).valueStream()
			.filter((role) -> role.path("id").asString().equals(id))
			.findFirst()
			.orElseThrow(() -> new AssertionError("not listed: " + id));
	}

	private void assertNotStored(String id) {
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM authorization_roles WHERE id = ?", Integer.class, id))
			.isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM role_permissions WHERE role_id = ?", Integer.class, id))
			.isZero();
	}

	private MvcTestResult call(HttpMethod method, String path, tools.jackson.databind.JsonNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static JsonNode body(MvcTestResult result) {
		return JsonMapper.shared().readTree(content(result));
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-authorization-roles").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

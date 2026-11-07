package io.oea.app.metamodel;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Spec metamodel/property-visibility (US-124, REQ-124, REQ-161; design D1–D3 of
 * us-124-property-visibility). Each test restricts its own property definitions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PropertyVisibilityTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String VISIBILITY = "/api/v1/admin/property-visibility";

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

	private TestSession security;

	private UUID connection;

	private UUID element;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.security = this.principals.personWithIdpRole("security-admin");
		this.connection = metatype("Domain Assignment", true);
		this.element = metatype("Application", false);
	}

	// Requirement: Visibility modes of a property definition

	@Test
	void newPropertyIsPublic() {
		TestSession metamodel = this.principals.personWithIdpRole("metamodel-admin");
		MvcTestResult created = call(HttpMethod.POST, "/api/v1/metamodel/property-definitions", JsonMapper.shared()
			.createObjectNode()
			.put("name", "Created " + suffix())
			.put("dataType", "string"), metamodel);
		assertThat(created).hasStatus(HttpStatus.CREATED);
		UUID id = UUID.fromString(body(created).path("id").asString());
		JsonNode entry = entry(id);
		assertThat(entry.path("visibilityMode").asString()).isEqualTo("public");
		assertThat(entry.path("readRoles")).isEmpty();
		assertThat(entry.path("writeRoles")).isEmpty();
		assertThat(entry.path("scopingConnectionType").isNull() || entry.path("scopingConnectionType").isMissingNode())
			.isTrue();
	}

	@Test
	void existingPropertiesArePublicWithTheirMetatypes() {
		UUID id = property("Existing");
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", this.element, id);
		JsonNode entry = entry(id);
		assertThat(entry.path("visibilityMode").asString()).isEqualTo("public");
		assertThat(entry.path("propertyName").asString()).startsWith("Existing");
		assertThat(strings(entry.path("metatypes"))).containsExactly(name(this.element));
	}

	@Test
	void roleRestrictedWithoutReadRole() {
		UUID id = property("Investment Cost");
		assertInvalid(update(this.security, change(id, "role-restricted", List.of(), List.of(), null)), "[0].readRoles");
		assertThat(entry(id).path("visibilityMode").asString()).isEqualTo("public");
	}

	@Test
	void writeRoleThatIsNoReadRole() {
		UUID id = property("Investment Cost");
		assertInvalid(update(this.security,
				change(id, "role-restricted", List.of("enterprise-architect"), List.of("metamodel-admin"), null)),
				"[0].writeRoles");
	}

	@Test
	void connectionScopedWithoutConnectionMetatype() {
		UUID id = property("Security Classification");
		assertInvalid(update(this.security, change(id, "connection-scoped", List.of(), List.of(), this.element)),
				"[0].scopingConnectionType");
		assertInvalid(update(this.security, change(id, "connection-scoped", List.of(), List.of(), null)),
				"[0].scopingConnectionType");
		assertThat(ok(update(this.security, change(id, "connection-scoped", List.of(), List.of(), this.connection))))
			.hasSize(1);
		assertThat(entry(id).path("scopingConnectionType").asString()).isEqualTo(this.connection.toString());
	}

	@Test
	void fieldsOfOtherModesAndUnknownReferences() {
		UUID id = property("Mixed");
		UUID retired = property("Retired");
		this.jdbc.update("UPDATE property_definitions SET retired_at = now() WHERE id = ?", retired);
		assertInvalid(update(this.security, change(id, "public", List.of("auditor"), List.of(), null)), "[0].readRoles");
		assertInvalid(update(this.security, change(id, "public", List.of(), List.of(), this.connection)),
				"[0].scopingConnectionType");
		assertInvalid(update(this.security, change(id, "connection-scoped", List.of("auditor"), List.of(), this.connection)),
				"[0].readRoles");
		assertInvalid(update(this.security, change(id, "role-restricted", List.of("no-such-role"), List.of(), null)),
				"[0].readRoles");
		assertInvalid(update(this.security, change(UUID.randomUUID(), "public", List.of(), List.of(), null)),
				"[0].propertyDefId");
		assertInvalid(update(this.security, change(retired, "public", List.of(), List.of(), null)), "[0].propertyDefId");
		ArrayNode twice = JsonMapper.shared().createArrayNode();
		twice.add(change(id, "public", List.of(), List.of(), null).get(0));
		twice.add(change(id, "role-restricted", List.of("auditor"), List.of(), null).get(0));
		assertInvalid(update(this.security, twice), "[1].propertyDefId");
		assertThat(this.jdbc.queryForList("SELECT id FROM property_definitions WHERE id = ?", UUID.class, retired)).hasSize(1);
		assertThat(strings(list())).contains(id.toString()).doesNotContain(retired.toString());
	}

	// Boundaries of the role lists

	@Test
	void roleListsAtTheirLimits() {
		UUID id = property("Bounded");
		// read roles: 1 valid, 0 rejected
		assertThat(ok(update(this.security, change(id, "role-restricted", List.of("auditor"), List.of(), null)))).hasSize(1);
		assertInvalid(update(this.security, change(id, "role-restricted", List.of(), List.of(), null)), "[0].readRoles");
		// write roles: equal to the read roles valid, one more rejected
		List<String> read = List.of("auditor", "enterprise-architect");
		assertThat(ok(update(this.security, change(id, "role-restricted", read, read, null)))).hasSize(1);
		assertInvalid(update(this.security,
				change(id, "role-restricted", read, List.of("auditor", "enterprise-architect", "viewer"), null)),
				"[0].writeRoles");
		// 50 roles valid, 51 rejected by the contract
		List<String> roles = IntStream.rangeClosed(1, 51).mapToObj("bound-role-%02d"::formatted).toList();
		roles.forEach((role) -> this.jdbc.update(
				"INSERT INTO authorization_roles (id, name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING", role, role));
		assertThat(ok(update(this.security, change(id, "role-restricted", roles.subList(0, 50), List.of(), null))))
			.hasSize(1);
		assertThat(entry(id).path("readRoles")).hasSize(50);
		MvcTestResult tooMany = update(this.security, change(id, "role-restricted", roles, List.of(), null));
		assertThat(tooMany).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(entry(id).path("readRoles")).hasSize(50);
	}

	// Requirement: Reading and changing the visibility

	@Test
	void restrictAProperty() {
		UUID id = property("Investment Cost");
		int version = version(id);
		JsonNode changed = ok(update(this.security,
				change(id, "role-restricted", List.of("enterprise-architect", "auditor"), List.of("enterprise-architect"), null)));
		assertThat(changed).hasSize(1);
		JsonNode entry = entry(id);
		assertThat(entry.path("visibilityMode").asString()).isEqualTo("role-restricted");
		assertThat(strings(entry.path("readRoles"))).containsExactly("auditor", "enterprise-architect");
		assertThat(strings(entry.path("writeRoles"))).containsExactly("enterprise-architect");
		assertThat(version(id)).isEqualTo(version + 1);
	}

	@Test
	void oneInvalidEntryAmongSeveral() {
		UUID first = property("First");
		UUID second = property("Second");
		ArrayNode both = JsonMapper.shared().createArrayNode();
		both.add(change(first, "role-restricted", List.of("auditor"), List.of(), null).get(0));
		both.add(change(second, "role-restricted", List.of(), List.of(), null).get(0));
		assertInvalid(update(this.security, both), "[1].readRoles");
		assertThat(entry(first).path("visibilityMode").asString()).isEqualTo("public");
		assertThat(entry(second).path("visibilityMode").asString()).isEqualTo("public");
	}

	@Test
	void backToPublic() {
		UUID id = property("Back");
		ok(update(this.security, change(id, "role-restricted", List.of("auditor"), List.of("auditor"), null)));
		ok(update(this.security, change(id, "public", List.of(), List.of(), null)));
		assertThat(entry(id).path("readRoles")).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM property_visibility_roles WHERE property_def_id = ?",
				Integer.class, id)).isZero();
	}

	// Requirement: Visibility only through security administration

	@Test
	void metamodelAdministratorAndArchitectAreDenied() {
		UUID id = property("Guarded");
		for (String role : List.of("metamodel-admin", "enterprise-architect")) {
			TestSession session = this.principals.personWithIdpRole(role);
			assertThat(this.mvc.get().uri(VISIBILITY).cookie(cookie(session)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(update(session, change(id, "role-restricted", List.of("auditor"), List.of(), null)))
				.hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(entry(id).path("visibilityMode").asString()).isEqualTo("public");
	}

	@Test
	void securityAdministratorListsTheMetatypes() {
		MvcTestResult listed = this.mvc.get().uri("/api/v1/metamodel/metatypes").cookie(cookie(this.security)).exchange();
		assertThat(listed).hasStatus(HttpStatus.OK);
		List<String> connections = new ArrayList<>();
		body(listed).forEach((metatype) -> {
			if (metatype.path("isConnection").asBoolean()) {
				connections.add(metatype.path("id").asString());
			}
		});
		assertThat(connections).contains(this.connection.toString()).doesNotContain(this.element.toString());
		TestSession reader = this.principals.personWithIdpRole("portal-reader");
		assertThat(this.mvc.get().uri("/api/v1/metamodel/metatypes").cookie(cookie(reader)).exchange())
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Audit of visibility changes

	@Test
	void changeAudited() {
		UUID id = property("Audited");
		ok(update(this.security, change(id, "role-restricted", List.of("auditor"), List.of(), null)));
		Map<String, Object> event = event(id);
		assertThat(event).containsEntry("event_category", "security").containsEntry("severity", "warning");
		JsonNode details = JsonMapper.shared().readTree((String) event.get("details"));
		assertThat(details.path("oldMode").asString()).isEqualTo("public");
		assertThat(details.path("newMode").asString()).isEqualTo("role-restricted");
		assertThat(strings(details.path("newReadRoles"))).containsExactly("auditor");
		assertThat(details.path("selfBenefiting").asBoolean()).isFalse();
		// an unchanged entry writes no event and keeps the version
		int version = version(id);
		assertThat(ok(update(this.security, change(id, "role-restricted", List.of("auditor"), List.of(), null)))).isEmpty();
		assertThat(events(id)).isEqualTo(1);
		assertThat(version(id)).isEqualTo(version);
	}

	@Test
	void selfBenefitingChange() {
		UUID id = property("Self");
		TestSession both = this.principals.personWithIdpRoles("security-admin", "enterprise-architect");
		ok(update(both, change(id, "role-restricted", List.of("enterprise-architect"), List.of(), null)));
		JsonNode details = JsonMapper.shared().readTree((String) event(id).get("details"));
		assertThat(details.path("selfBenefiting").asBoolean()).isTrue();
	}

	private ArrayNode change(UUID id, String mode, List<String> read, List<String> write, UUID scoping) {
		ArrayNode list = JsonMapper.shared().createArrayNode();
		ObjectNode entry = list.addObject().put("propertyDefId", id.toString()).put("visibilityMode", mode);
		ArrayNode readRoles = entry.putArray("readRoles");
		read.forEach(readRoles::add);
		ArrayNode writeRoles = entry.putArray("writeRoles");
		write.forEach(writeRoles::add);
		if (scoping != null) {
			entry.put("scopingConnectionType", scoping.toString());
		}
		return list;
	}

	private MvcTestResult update(TestSession session, ArrayNode entries) {
		return call(HttpMethod.PUT, VISIBILITY, entries, session);
	}

	private JsonNode entry(UUID id) {
		for (JsonNode entry : list()) {
			if (entry.path("propertyDefId").asString().equals(id.toString())) {
				return entry;
			}
		}
		throw new AssertionError("no entry for " + id);
	}

	private JsonNode list() {
		return ok(this.mvc.get().uri(VISIBILITY).cookie(cookie(this.security)).exchange());
	}

	private Map<String, Object> event(UUID id) {
		return this.jdbc.queryForMap("""
				SELECT event_category, severity, details::text AS details FROM audit.events
				WHERE event_type = 'visibility.changed' AND target_id = ? ORDER BY id DESC LIMIT 1""", id.toString());
	}

	private int events(UUID id) {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'visibility.changed' AND target_id = ?",
				Integer.class, id.toString());
	}

	private int version(UUID id) {
		return this.jdbc.queryForObject("SELECT version FROM property_definitions WHERE id = ?", Integer.class, id);
	}

	private UUID property(String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, 'string') RETURNING id""",
				UUID.class, name + " " + suffix(), "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, ?)
				RETURNING id""", UUID.class, name + " " + suffix(), connection);
	}

	private String name(UUID metatype) {
		return this.jdbc.queryForObject("SELECT name FROM metatypes WHERE id = ?", String.class, metatype);
	}

	private MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private static Cookie cookie(TestSession session) {
		return new Cookie(SessionCookie.NAME, session.token());
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:validation-failed");
		assertThat(strings(problem.path("invalidFields"))).containsExactly(field);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		if (array != null) {
			array.forEach((node) -> values.add(node.isObject() ? node.path("propertyDefId").asString() : node.asString()));
		}
		return values;
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
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
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-property-visibility").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

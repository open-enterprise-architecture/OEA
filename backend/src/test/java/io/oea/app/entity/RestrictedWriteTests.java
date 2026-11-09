package io.oea.app.entity;

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
import java.util.Map;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec repository/entity-write "Writing restricted properties" and "Necessity of properties"
 * (US-126 AC3, REQ-126, REQ-160 AC4; design D3 of us-126-role-restricted-enforcement).
 * `Investment Cost` is readable by `enterprise-architect` and `integration-writer` and writable by
 * `enterprise-architect` only; Kurt is an enterprise architect, the integration writes with
 * `integration-writer`.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RestrictedWriteTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String BATCH = "/api/v1/entities/batch";

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

	private TestSession kurt;

	private TestSession integration;

	private UUID application;

	private UUID cost;

	private UUID scoped;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = principals.personWithIdpRole("enterprise-architect");
		this.integration = principals.personWithIdpRole("integration-writer");
		this.application = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?) RETURNING id""",
				UUID.class, "Application " + suffix());
		this.cost = property("Investment Cost", "integer", "role-restricted", null);
		this.jdbc.update("""
				INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES
				    (?, 'enterprise-architect', 'read'), (?, 'integration-writer', 'read'), (?, 'enterprise-architect', 'write')""",
				this.cost, this.cost, this.cost);
		UUID assignment = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, true)
				RETURNING id""", UUID.class, "Domain Assignment " + suffix());
		this.scoped = property("Security Classification", "string", "connection-scoped", assignment);
		map(this.cost, "optional");
		map(this.scoped, "optional");
	}

	@Test
	void writeAttemptWithoutWriteRole() {
		String external = "app-" + suffix();
		ok(batch(this.kurt, entity(external, "CRM System", "Integration of CRM", this.cost, "100")));
		int denials = denials();
		// a read role without the write role is not enough; nothing of the batch is written
		ObjectNode other = entity("other-" + suffix(), "Other", null, null, null);
		assertThat(batch(this.integration, entity(external, "CRM System", null, this.cost, "999"), other))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(value(external)).isEqualTo(100L);
		assertThat(count("SELECT count(*) FROM entities WHERE external_id = '" + other.path("externalId").asString() + "'"))
			.isZero();
		assertThat(denials()).isEqualTo(denials + 1);
		// removing the value is a write as well
		assertThat(batch(this.integration, entity(external, "CRM System", null, this.cost, ""))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(value(external)).isEqualTo(100L);
	}

	@Test
	void writeWithWriteRoleIsEncryptedInTheAuditTrail() {
		String external = "app-" + suffix();
		ok(batch(this.kurt, entity(external, "ERP", null, this.cost, "100")));
		ok(batch(this.kurt, entity(external, "ERP", null, this.cost, "120")));
		assertThat(value(external)).isEqualTo(120L);
		Map<String, Object> change = this.jdbc.queryForMap("""
				SELECT c.value_protection, c.old_value, c.new_value, c.key_id FROM audit.event_changes c
				JOIN audit.events e ON e.id = c.event_id
				WHERE e.event_type = 'entity.updated' AND c.property_def_id = ? ORDER BY c.id DESC LIMIT 1""", this.cost);
		assertThat(change).containsEntry("value_protection", "encrypted")
			.containsEntry("old_value", null)
			.containsEntry("new_value", null);
		assertThat(change.get("key_id")).isNotNull();
		// the value can be removed by the writer
		ok(batch(this.kurt, entity(external, "ERP", null, this.cost, "")));
		assertThat(value(external)).isNull();
	}

	@Test
	void restrictedValueKept() {
		String external = "app-" + suffix();
		ok(batch(this.kurt, entity(external, "Intranet", null, this.cost, "300")));
		JsonNode written = ok(batch(this.integration, entity(external, "Intranet", "New description", null, null)));
		assertThat(written.path("items").get(0).path("status").asString()).isEqualTo("updated");
		assertThat(value(external)).isEqualTo(300L);
	}

	@Test
	void connectionScopedProperty() {
		JsonNode result = ok(batch(this.kurt, entity("app-" + suffix(), "Portal", null, this.scoped, "secret")));
		JsonNode item = result.path("items").get(0);
		assertThat(item.path("status").asString()).isEqualTo("error");
		assertThat(item.path("errors").get(0).path("code").asString()).isEqualTo("RESTRICTED_PROPERTY_NOT_SUPPORTED");
	}

	@Test
	void mandatoryRestrictedProperty() {
		this.jdbc.update("UPDATE metatype_property_mappings SET necessity = 'mandatory' WHERE metatype_id = ? AND property_def_id = ?",
				this.application, this.cost);
		try {
			// a writer of the property must give it
			JsonNode missing = ok(batch(this.kurt, entity("app-" + suffix(), "Billing", null, null, null))).path("items").get(0);
			assertThat(missing.path("status").asString()).isEqualTo("error");
			assertThat(missing.path("errors").get(0).path("code").asString()).isEqualTo("MANDATORY_MISSING");
			// a writer who cannot change it is not asked for it
			JsonNode written = ok(batch(this.integration, entity("app-" + suffix(), "Billing", null, null, null)))
				.path("items").get(0);
			assertThat(written.path("status").asString()).isEqualTo("created");
		}
		finally {
			this.jdbc.update("UPDATE metatype_property_mappings SET necessity = 'optional' WHERE metatype_id = ? AND property_def_id = ?",
					this.application, this.cost);
		}
	}

	@Test
	void connectionWithARestrictedProperty() {
		UUID link = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, true)
				RETURNING id""", UUID.class, "Link " + suffix());
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", link, this.cost);
		String a = "a-" + suffix();
		String b = "b-" + suffix();
		ok(batch(this.kurt, entity(a, "A", null, null, null), entity(b, "B", null, null, null)));
		ObjectNode connection = JsonMapper.shared()
			.createObjectNode()
			.put("connectionMetatypeId", link.toString())
			.put("sourceExternalId", a)
			.put("targetExternalId", b);
		connection.putArray("properties").addObject().put("propertyDefId", this.cost.toString()).put("value", "5");
		assertThat(post("/api/v1/connections", connection, this.integration)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post("/api/v1/connections", connection, this.kurt)).hasStatus(HttpStatus.CREATED);
	}

	private ObjectNode entity(String externalId, String name, String description, UUID property, String value) {
		ObjectNode entity = JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("metatypeId", this.application.toString())
			.put("externalId", externalId);
		if (description != null) {
			entity.put("description", description);
		}
		if (property != null) {
			entity.putArray("properties").addObject().put("propertyDefId", property.toString()).put("value", value);
		}
		return entity;
	}

	private MvcTestResult batch(TestSession session, ObjectNode... entities) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("strategy", "upsert");
		ArrayNode list = body.putArray("entities");
		for (ObjectNode entity : entities) {
			list.add(entity);
		}
		return post(BATCH, body, session);
	}

	private MvcTestResult post(String path, ObjectNode body, TestSession session) {
		return this.mvc.post()
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
	}

	private Long value(String externalId) {
		return this.jdbc.queryForList("""
				SELECT v.value_int FROM entity_property_values v JOIN entities e ON e.id = v.entity_id
				WHERE e.external_id = ? AND v.property_def_id = ?""", Long.class, externalId, this.cost)
			.stream()
			.findFirst()
			.orElse(null);
	}

	private int denials() {
		return count("""
				SELECT count(*) FROM audit.events e JOIN sessions s ON s.person_id = e.actor_person_id
				WHERE e.event_type = 'authz.denied' AND s.id = '%s'""".formatted(this.integration.sessionId()));
	}

	private UUID property(String name, String dataType, String mode, UUID scopingType) {
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, ?, ?, ?, ?) RETURNING id""", UUID.class, name + " " + suffix(),
				"p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12), dataType, mode, scopingType);
	}

	private void map(UUID property, String necessity) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, ?, DATE '2026-01-01', 'organization')""", this.application, property, necessity);
	}

	private int count(String sql) {
		Integer count = this.jdbc.queryForObject(sql, Integer.class);
		return (count != null) ? count : 0;
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-restricted-write").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

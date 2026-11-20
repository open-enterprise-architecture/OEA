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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec repository/entity-batch-write, requirement "External ID of a deleted entity" (#70,
 * s17-entity-delete): an {@code upsert} restores the deleted entity of its external ID when the
 * writer holds {@code entity:restore}; every other item is rejected with {@code EXTERNAL_ID_DELETED}.
 * Kurt is an enterprise architect ({@code entity:write}, {@code entity:restore}), Ida holds
 * {@code integration-writer} ({@code entity:write} only).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntityBatchRestoreTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

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

	private TestSession kurt;

	private TestSession ida;

	private long kurtId;

	private UUID application;

	private UUID flow;

	private String externalId;

	private int erp;

	private int module;

	private int crm;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.ida = this.principals.personWithIdpRole("integration-writer");
		this.kurtId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.kurt.sessionId());
		this.application = metatype("Application " + suffix(), false);
		this.flow = metatype("Flow " + suffix(), true);
		this.jdbc.update("INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id) "
				+ "VALUES (?, 'source', ?), (?, 'target', ?)", this.flow, this.application, this.flow, this.application);
		this.externalId = "sap:erp-" + suffix();
		this.erp = entity("ERP " + suffix(), this.externalId);
		this.module = child("ERP module " + suffix(), this.erp);
		this.crm = entity("CRM " + suffix(), "sap:crm-" + suffix());
		ok(delete(this.kurt, this.erp));
	}

	@Test
	void restoredByTheBatchAndUpdated() {
		JsonNode result = ok(batch(this.kurt, "upsert", false, entityItem(this.externalId, "ERP", "New description")));

		JsonNode item = result.path("items").get(0);
		assertThat(item.path("status").asString()).isEqualTo("updated");
		assertThat(item.path("restored").asBoolean()).isTrue();
		assertThat(item.path("id").asInt()).isEqualTo(this.erp);
		assertThat(result.path("summary").path("updated").asInt()).isOne();
		assertThat(deleted(this.erp)).isFalse();
		assertThat(deleted(this.module)).as("the cascade is restored with it").isFalse();
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, this.erp))
			.isEqualTo("New description");
		assertThat(version(this.erp)).isEqualTo(2);
		assertThat(version(this.module)).isEqualTo(1);
		assertThat(events("entity.undeleted", this.erp)).isOne();
		assertThat(events("entity.updated", this.erp)).isOne();
		JsonNode undeleted = JsonMapper.shared()
			.readTree(this.jdbc.queryForObject(
					"SELECT details::text FROM audit.events WHERE event_type = 'entity.undeleted' AND entity_id = ?",
					String.class, this.erp));
		assertThat(undeleted.path("batchId").asString()).isEqualTo(JsonMapper.shared()
			.readTree(this.jdbc.queryForObject(
					"SELECT details::text FROM audit.events WHERE event_type = 'entity.updated' AND entity_id = ?",
					String.class, this.erp))
			.path("batchId")
			.asString());
	}

	@Test
	void restoredWithoutAChangeIsUnchangedButRestored() {
		JsonNode item = ok(batch(this.kurt, "upsert", false, entityItem(this.externalId, nameOf(this.erp), "")))
			.path("items")
			.get(0);

		assertThat(item.path("status").asString()).isEqualTo("unchanged");
		assertThat(item.path("restored").asBoolean()).isTrue();
		assertThat(deleted(this.erp)).isFalse();
		assertThat(version(this.erp)).isEqualTo(1);
		assertThat(events("entity.updated", this.erp)).isZero();
	}

	@Test
	void anActiveEntityIsNotReportedAsRestored() {
		ok(undelete(this.kurt, this.erp));

		JsonNode item = ok(batch(this.kurt, "upsert", false, entityItem(this.externalId, "ERP", "Again")))
			.path("items")
			.get(0);

		assertThat(item.path("status").asString()).isEqualTo("updated");
		assertThat(item.path("restored").asBoolean()).isFalse();
	}

	@Test
	void withoutEntityRestoreAnAtomicBatchChangesNothing() {
		String other = "Second " + suffix();

		MvcTestResult result = batch(this.ida, "upsert", true, entityItem(this.externalId, "ERP", "New"),
				entityItem("sap:second-" + suffix(), other, ""));

		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(content(result)).contains("EXTERNAL_ID_DELETED");
		assertThat(deleted(this.erp)).isTrue();
		assertThat(count("SELECT count(*) FROM entities WHERE name = '" + other + "'")).isZero();
	}

	@Test
	void withoutEntityRestoreANonAtomicBatchRejectsTheItem() {
		String other = "Second " + suffix();

		JsonNode result = ok(batch(this.ida, "upsert", false, entityItem(this.externalId, "ERP", "New"),
				entityItem("sap:second-" + suffix(), other, "")));

		assertThat(result.path("items").get(0).path("status").asString()).isEqualTo("error");
		assertThat(result.path("items").get(0).path("errors").get(0).path("code").asString())
			.isEqualTo("EXTERNAL_ID_DELETED");
		assertThat(result.path("items").get(0).path("restored").asBoolean()).isFalse();
		assertThat(result.path("items").get(1).path("status").asString()).isEqualTo("created");
		assertThat(deleted(this.erp)).isTrue();
		assertThat(count("SELECT count(*) FROM entities WHERE name = '" + other + "'")).isOne();
	}

	@ParameterizedTest
	@ValueSource(strings = { "create-only", "skip-existing" })
	void otherStrategiesNeverRestore(String strategy) {
		JsonNode item = ok(batch(this.kurt, strategy, false, entityItem(this.externalId, "ERP", "New")))
			.path("items")
			.get(0);

		assertThat(item.path("status").asString()).isEqualTo("error");
		assertThat(item.path("errors").get(0).path("code").asString()).isEqualTo("EXTERNAL_ID_DELETED");
		assertThat(item.path("restored").asBoolean()).isFalse();
		assertThat(deleted(this.erp)).isTrue();
		assertThat(events("entity.undeleted", this.erp)).isZero();
	}

	@Test
	void anInvalidItemRestoresNothing() {
		JsonNode item = ok(batch(this.kurt, "upsert", false, entityItem(this.externalId, "x".repeat(256), "")))
			.path("items")
			.get(0);

		assertThat(item.path("status").asString()).isEqualTo("error");
		assertThat(deleted(this.erp)).isTrue();
		assertThat(deleted(this.module)).isTrue();
		assertThat(events("entity.undeleted", this.erp)).isZero();
	}

	@Test
	void theLongestValidNameRestoresAndTheNextOneDoesNot() {
		JsonNode longest = ok(batch(this.kurt, "upsert", false, entityItem(this.externalId, "y".repeat(255), "")))
			.path("items")
			.get(0);

		assertThat(longest.path("status").asString()).isEqualTo("updated");
		assertThat(longest.path("restored").asBoolean()).isTrue();
		assertThat(deleted(this.erp)).isFalse();
	}

	@Test
	void theExternalIdIsNeverTakenByANewEntity() {
		ok(batch(this.kurt, "upsert", false, entityItem(this.externalId, "ERP", "New")));

		assertThat(count("SELECT count(*) FROM entities WHERE external_id = '" + this.externalId + "'")).isOne();
		assertThat(this.jdbc.queryForObject("SELECT id FROM entities WHERE external_id = ?", Integer.class,
				this.externalId))
			.isEqualTo(this.erp);
	}

	@Test
	void aDeletedConnectionIsRestoredWithActiveEndpoints() {
		int other = entity("Other " + suffix(), "sap:other-" + suffix());
		String connectionId = "sap:flow-" + suffix();
		int connection = connection(this.flow, "Other to CRM", other, this.crm, connectionId);
		ok(delete(this.kurt, connection));

		JsonNode result = ok(batch(this.kurt, "upsert", false, new ObjectNode[0],
				connectionItem(connectionId, other, this.crm, "Renamed flow")));

		JsonNode item = result.path("items").get(0);
		assertThat(item.path("itemType").asString()).isEqualTo("connection");
		assertThat(item.path("status").asString()).isEqualTo("updated");
		assertThat(item.path("restored").asBoolean()).isTrue();
		assertThat(deleted(connection)).isFalse();
		assertThat(events("entity.undeleted", connection)).isOne();
	}

	@Test
	void aDeletedConnectionWithADeletedEndpointStaysDeleted() {
		int other = entity("Other " + suffix(), "sap:other-" + suffix());
		String connectionId = "sap:flow-" + suffix();
		int connection = connection(this.flow, "Other to CRM", other, this.crm, connectionId);
		ok(delete(this.kurt, connection));
		ok(delete(this.kurt, this.crm));

		JsonNode item = ok(batch(this.kurt, "upsert", false, new ObjectNode[0],
				connectionItem(connectionId, other, this.crm, "Renamed flow")))
			.path("items")
			.get(0);

		assertThat(item.path("status").asString()).isEqualTo("error");
		assertThat(item.path("restored").asBoolean()).isFalse();
		assertThat(deleted(connection)).isTrue();
	}

	@Test
	void aRestoredSetWithAScopingConnectionNeedsVisibilityScopeAssign() {
		UUID scoping = metatype("Domain Assignment " + suffix(), true);
		this.jdbc.update("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, ?, 'string', 'connection-scoped', ?)""", "Classification " + suffix(),
				"p_" + suffix().replace("-", ""), scoping);
		TestSession both = this.principals.personWithIdpRoles("enterprise-architect", "security-admin");
		ok(undelete(this.kurt, this.erp));
		int assignment = connection(scoping, "Assignment", this.module, this.crm, null);
		ok(delete(both, this.erp));

		assertThat(batch(this.kurt, "upsert", true, entityItem(this.externalId, "ERP", "New")))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deleted(this.erp)).isTrue();
		assertThat(deleted(assignment)).isTrue();
		JsonNode item = ok(batch(both, "upsert", true, entityItem(this.externalId, "ERP", "New"))).path("items")
			.get(0);
		assertThat(item.path("restored").asBoolean()).isTrue();
		assertThat(deleted(assignment)).isFalse();
	}

	// Helpers

	private MvcTestResult batch(TestSession session, String strategy, boolean atomic, ObjectNode... entities) {
		return batch(session, strategy, atomic, entities, new ObjectNode[0]);
	}

	private MvcTestResult batch(TestSession session, String strategy, boolean atomic, ObjectNode[] entities,
			ObjectNode... connections) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("strategy", strategy).put("atomic", atomic);
		var entityArray = body.putArray("entities");
		for (ObjectNode entity : entities) {
			entityArray.add(entity);
		}
		var connectionArray = body.putArray("connections");
		for (ObjectNode connection : connections) {
			connectionArray.add(connection);
		}
		return this.mvc.post()
			.uri("/api/v1/entities/batch")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
	}

	private ObjectNode entityItem(String externalId, String name, String description) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("externalId", externalId)
			.put("name", name)
			.put("description", description)
			.put("metatypeId", this.application.toString());
	}

	private ObjectNode connectionItem(String externalId, int source, int target, String name) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("externalId", externalId)
			.put("name", name)
			.put("connectionMetatypeId", this.flow.toString())
			.put("sourceId", source)
			.put("targetId", target);
	}

	private MvcTestResult delete(TestSession session, int id) {
		int version = version(id);
		return this.mvc.delete()
			.uri("/api/v1/entities/" + id)
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.header("If-Match", "\"" + version + "\"")
			.exchange();
	}

	private MvcTestResult undelete(TestSession session, int id) {
		return this.mvc.post()
			.uri("/api/v1/entities/" + id + "/undelete")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.header("If-Match", "\"" + version(id) + "\"")
			.exchange();
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?) RETURNING id""", UUID.class, name, connection);
	}

	private int entity(String name, String externalId) {
		return entity(this.application, name, externalId);
	}

	private int entity(UUID metatype, String name, String externalId) {
		Integer id = this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, external_id, created_by) VALUES (?, ?, ?, ?) RETURNING id""",
				Integer.class, metatype, name, externalId, this.kurtId);
		this.jdbc.update("INSERT INTO entity_version_headers (entity_id, version, changed_by) VALUES (?, 1, ?)", id,
				this.kurtId);
		return id;
	}

	private int child(String name, int parent) {
		int id = entity(name, null);
		this.jdbc.update("UPDATE entities SET parent_entity_id = ? WHERE id = ?", parent, id);
		return id;
	}

	private int connection(UUID metatype, String name, int source, int target, String externalId) {
		int id = entity(metatype, name, externalId);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private boolean deleted(int id) {
		return Boolean.TRUE.equals(
				this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM entities WHERE id = ?", Boolean.class, id));
	}

	private int version(int id) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, id);
	}

	private String nameOf(int id) {
		return this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, id);
	}

	private int events(String type, int entityId) {
		return count("SELECT count(*) FROM audit.events WHERE event_type = '" + type + "' AND entity_id = " + entityId);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return JsonMapper.shared().readTree(content(result));
	}

	private static Cookie cookie(TestSession session) {
		return new Cookie(SessionCookie.NAME, session.token());
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
			return Files.createTempDirectory("oea-batch-restore").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

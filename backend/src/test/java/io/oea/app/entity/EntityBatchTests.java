package io.oea.app.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.audit.verify.ChainVerifier;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.List;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Specs repository/entity-write and repository/entity-batch-write on an own database with the
 * starter package imported (US-141 AC1, REQ-149, REQ-153, REQ-154).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntityBatchTests {

	private static final PostgreSQLContainer postgres = OeaDatabases.freshMigrated();

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

	private TestSession architect;

	private UUID component;

	private UUID status;

	/** A metatype with the mandatory property Version and the property Owner with necessity warning. */
	private UUID checkedType;

	private UUID version;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = principals.personWithIdpRole("enterprise-architect");
		if (count("SELECT count(*) FROM metatypes") == 0) {
			TestSession metamodelAdmin = principals.personWithIdpRole("metamodel-admin");
			assertThat(this.mvc.post()
				.uri("/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import")
				.cookie(new Cookie(SessionCookie.NAME, metamodelAdmin.token()))
				.header(CsrfOriginFilter.HEADER, metamodelAdmin.csrfToken())
				.exchange()).hasStatus(HttpStatus.OK);
			UUID type = this.jdbc.queryForObject("""
					INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Checked Type')
					RETURNING id""", UUID.class);
			UUID versionProperty = this.jdbc.queryForObject("""
					INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('Version', 'version', 'string')
					RETURNING id""", UUID.class);
			UUID owner = this.jdbc.queryForObject("""
					INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('Owner', 'owner', 'string')
					RETURNING id""", UUID.class);
			this.jdbc.update("""
					INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, sort_order)
					VALUES (?, ?, 'mandatory', DATE '2026-01-01', 0), (?, ?, 'warning', DATE '2026-01-01', 1)""", type,
					versionProperty, type, owner);
		}
		this.component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
		this.status = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Status'", UUID.class);
		this.checkedType = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Checked Type'", UUID.class);
		this.version = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Version'",
				UUID.class);
	}

	// repository/entity-write

	@Test
	void entityChanged() {
		String externalId = unique("crm");
		JsonNode created = ok(post(batch(component(externalId, "CRM System", "active"))));
		int id = created.path("items").get(0).path("id").asInt();
		JsonNode changed = ok(post(batch(component(externalId, "CRM", "retired"))));
		assertThat(changed.path("summary").path("updated").asInt()).isOne();
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, id))
			.isEqualTo(2);
		assertThat(this.jdbc.queryForList("""
				SELECT field_name || '=' || old_value || '>' || new_value FROM entity_system_field_history
				WHERE entity_id = ? AND entity_version = 2""", String.class, id))
			.containsExactly("name=CRM System>CRM");
		assertThat(this.jdbc.queryForList("""
				SELECT value_enum FROM entity_property_value_history WHERE entity_id = ? AND entity_version = 2""",
				String.class, id))
			.containsExactly("active");
		assertThat(count("SELECT count(*) FROM entity_version_headers WHERE entity_id = " + id)).isEqualTo(2);
	}

	@Test
	void unchangedWrite() {
		String externalId = unique("erp");
		ok(post(batch(component(externalId, "ERP", "active"))));
		int events = count("SELECT count(*) FROM audit.events WHERE event_type LIKE 'entity.%'");
		JsonNode again = ok(post(batch(component(externalId, "ERP", "active"))));
		assertThat(again.path("items").get(0).path("status").asString()).isEqualTo("unchanged");
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE external_id = ?", Integer.class,
				externalId))
			.isOne();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type LIKE 'entity.%'")).isEqualTo(events);
	}

	@Test
	void createdEntityAuditedWithAgentHeaders() throws Exception {
		String externalId = unique("audited");
		MvcTestResult result = this.mvc.post()
			.uri(BATCH)
			.cookie(new Cookie(SessionCookie.NAME, this.architect.token()))
			.header(CsrfOriginFilter.HEADER, this.architect.csrfToken())
			.header("X-Agent-Id", "repo-scanner")
			.header("X-Agent-Run", "run-42")
			.contentType("application/json")
			.content(batch(component(externalId, "CRM System", "active")).toString())
			.exchange();
		int id = ok(result).path("items").get(0).path("id").asInt();
		Map<String, Object> event = this.jdbc.queryForMap("""
				SELECT id, actor_type, actor_person_id IS NOT NULL AS person, target_type, target_id, entity_id,
				       entity_name, details ->> 'batchId' AS batch, agent_id, agent_label, agent_run
				FROM audit.events WHERE event_type = 'entity.created' AND entity_id = ?""", id);
		assertThat(event).containsEntry("actor_type", "person")
			.containsEntry("person", true)
			.containsEntry("target_type", "entity")
			.containsEntry("target_id", Integer.toString(id))
			.containsEntry("entity_name", "CRM System")
			.containsEntry("agent_label", "repo-scanner")
			.containsEntry("agent_run", "run-42");
		assertThat(event.get("agent_id")).isNull();
		assertThat(event.get("batch")).isNotNull();
		assertThat(this.jdbc.queryForList("""
				SELECT property_name || '=' || new_value FROM audit.event_changes WHERE event_id = ? ORDER BY id""",
				String.class, event.get("id")))
			.contains("name=CRM System", "Status=active");
		try (Connection connection = OeaDatabases.asSuperuser(postgres)) {
			assertThat(new ChainVerifier().verify(connection, List.of()).problems()).isEmpty();
		}
	}

	// repository/entity-batch-write

	@Test
	void batchWithoutPermission() {
		TestSession viewer = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("viewer");
		String externalId = unique("forbidden");
		assertThat(post(batch(component(externalId, "Forbidden", null)), viewer)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM entities WHERE external_id = '" + externalId + "'")).isZero();
	}

	@Test
	void batchAboveTheLimit() {
		String prefix = unique("limit");
		ObjectNode request = batch();
		for (int i = 0; i < 501; i++) {
			request.withArray("entities").add(component(prefix + "-" + i, "Entity " + i, null));
		}
		MvcTestResult result = post(request);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(count("SELECT count(*) FROM entities WHERE external_id LIKE '" + prefix + "-%'")).isZero();
	}

	@Test
	void upsertOfARepositoryScan() {
		String prefix = unique("scan");
		ObjectNode existing = batch();
		for (int i = 0; i < 50; i++) {
			existing.withArray("entities").add(component(prefix + "-" + i, "Old " + i, null));
		}
		ok(post(existing));
		ObjectNode scan = batch();
		for (int i = 0; i < 200; i++) {
			scan.withArray("entities").add(component(prefix + "-" + i, "Component " + i, null));
		}
		JsonNode result = ok(post(scan));
		assertThat(result.path("summary").path("created").asInt()).isEqualTo(150);
		assertThat(result.path("summary").path("updated").asInt()).isEqualTo(50);
		assertThat(count("SELECT count(*) FROM entities WHERE external_id LIKE '" + prefix + "-%'")).isEqualTo(200);
		assertThat(count("SELECT count(*) FROM entities WHERE external_id LIKE '" + prefix + "-%' AND name LIKE 'Old%'"))
			.isZero();
		JsonNode repeated = ok(post(scan));
		assertThat(repeated.path("summary").path("unchanged").asInt()).isEqualTo(200);
		assertThat(repeated.path("summary").path("created").asInt()).isZero();
	}

	@Test
	void duplicateExternalIdInABatch() {
		String externalId = unique("twice");
		JsonNode result = ok(post(batch(component(externalId, "First", null), component(externalId, "Second", null))));
		assertThat(result.path("items").get(0).path("status").asString()).isEqualTo("created");
		assertThat(result.path("items").get(1).path("status").asString()).isEqualTo("error");
		assertThat(result.path("items").get(1).path("errors").get(0).path("code").asString())
			.isEqualTo("DUPLICATE_EXTERNAL_ID");
		assertThat(count("SELECT count(*) FROM entities WHERE external_id = '" + externalId + "'")).isOne();
	}

	@Test
	void partialUpdateByASource() {
		String externalId = unique("partial");
		ok(post(batch(component(externalId, "Intranet", "active"))));
		ok(post(batch(component(externalId, "Intranet Portal", null))));
		assertThat(this.jdbc.queryForObject("""
				SELECT e.name || ':' || v.value_enum FROM entities e JOIN entity_property_values v ON v.entity_id = e.id
				WHERE e.external_id = ?""", String.class, externalId))
			.isEqualTo("Intranet Portal:active");
	}

	@Test
	void createOnlyWithAnExistingExternalId() {
		String externalId = unique("create-only");
		ok(post(batch(component(externalId, "Original", null))));
		ObjectNode request = batch(component(externalId, "Changed", null)).put("strategy", "create-only");
		JsonNode item = ok(post(request)).path("items").get(0);
		assertThat(item.path("status").asString()).isEqualTo("error");
		assertThat(item.path("errors").get(0).path("code").asString()).isEqualTo("EXTERNAL_ID_EXISTS");
		assertThat(this.jdbc.queryForObject("SELECT name FROM entities WHERE external_id = ?", String.class,
				externalId))
			.isEqualTo("Original");
	}

	@Test
	void skipExisting() {
		String existing = unique("skip");
		String fresh = unique("skip-new");
		ok(post(batch(component(existing, "Existing", null))));
		ObjectNode request = batch(component(existing, "Changed", null), component(fresh, "New", null))
			.put("strategy", "skip-existing");
		JsonNode result = ok(post(request));
		assertThat(result.path("items").get(0).path("status").asString()).isEqualTo("skipped");
		assertThat(result.path("items").get(1).path("status").asString()).isEqualTo("created");
		assertThat(this.jdbc.queryForObject("SELECT name FROM entities WHERE external_id = ?", String.class, existing))
			.isEqualTo("Existing");
	}

	@Test
	void partialErrorsWithoutAtomicity() {
		String prefix = unique("partial-errors");
		JsonNode result = ok(post(checkedBatch(prefix, false)));
		assertThat(result.path("summary").path("created").asInt()).isEqualTo(95);
		assertThat(result.path("summary").path("errors").asInt()).isEqualTo(5);
		for (int i = 0; i < 5; i++) {
			JsonNode item = result.path("items").get(i * 20);
			assertThat(item.path("status").asString()).isEqualTo("error");
			assertThat(item.path("errors").get(0).path("code").asString()).isEqualTo("MANDATORY_MISSING");
		}
		assertThat(result.path("items").get(1).path("warnings").get(0).path("field").asString())
			.isEqualTo("properties[Owner]");
	}

	@Test
	void errorInAnAtomicBatch() {
		String prefix = unique("atomic");
		MvcTestResult result = post(checkedBatch(prefix, true));
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		JsonNode errors = body(result).path("errors");
		assertThat(errors).hasSize(5);
		assertThat(errors.get(0).path("field").asString()).isEqualTo("entities[0].properties");
		assertThat(errors.get(4).path("field").asString()).isEqualTo("entities[80].properties");
		assertThat(count("SELECT count(*) FROM entities WHERE external_id LIKE '" + prefix + "-%'")).isZero();
	}

	@Test
	void batchWithConnectionsOrDryRun() {
		String externalId = unique("connections");
		ObjectNode withConnection = batch(component(externalId, "Source", null));
		withConnection.putArray("connections").addObject().put("connectionMetatypeId", UUID.randomUUID().toString());
		assertThat(post(withConnection)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		MvcTestResult dryRun = this.mvc.post()
			.uri(BATCH + "?dryRun=true")
			.cookie(new Cookie(SessionCookie.NAME, this.architect.token()))
			.header(CsrfOriginFilter.HEADER, this.architect.csrfToken())
			.contentType("application/json")
			.content(batch(component(externalId, "Source", null)).toString())
			.exchange();
		assertThat(dryRun).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(count("SELECT count(*) FROM entities WHERE external_id = '" + externalId + "'")).isZero();
	}

	/** US-141 definition of done: 500 entities in less than 5 s on PostgreSQL, in both modes. */
	@Test
	void fiveHundredEntitiesWithinFiveSeconds() {
		// warm-up: the target applies to a running instance, not to the first request after startup
		ObjectNode warmUp = batch();
		for (int i = 0; i < 50; i++) {
			warmUp.withArray("entities").add(component(unique("warm-up"), "Warm-up " + i, "active"));
		}
		ok(post(warmUp));
		for (boolean atomic : List.of(false, true)) {
			String prefix = unique("performance-" + atomic);
			ObjectNode request = batch().put("atomic", atomic);
			for (int i = 0; i < 500; i++) {
				request.withArray("entities").add(component(prefix + "-" + i, "Component " + i, "active"));
			}
			long started = System.nanoTime();
			JsonNode result = ok(post(request));
			long millis = (System.nanoTime() - started) / 1_000_000;
			System.out.println("batch of 500 entities, atomic=" + atomic + ": " + millis + " ms");
			assertThat(result.path("summary").path("created").asInt()).isEqualTo(500);
			assertThat(millis).as("atomic=" + atomic).isLessThan(5000);
		}
	}

	/** 100 entities of the checked type; every 20th lacks the mandatory Version. */
	private ObjectNode checkedBatch(String prefix, boolean atomic) {
		ObjectNode request = batch().put("atomic", atomic);
		for (int i = 0; i < 100; i++) {
			ObjectNode entity = request.withArray("entities").addObject();
			entity.put("name", "Checked " + i).put("metatypeId", this.checkedType.toString())
				.put("externalId", prefix + "-" + i);
			if (i % 20 != 0) {
				entity.putArray("properties").addObject().put("propertyDefId", this.version.toString()).put("value", "1.0");
			}
		}
		return request;
	}

	private ObjectNode component(String externalId, String name, String statusValue) {
		ObjectNode entity = JsonMapper.shared().createObjectNode();
		entity.put("name", name).put("metatypeId", this.component.toString()).put("externalId", externalId);
		if (statusValue != null) {
			entity.putArray("properties").addObject().put("propertyDefId", this.status.toString())
				.put("value", statusValue);
		}
		return entity;
	}

	private static ObjectNode batch(ObjectNode... entities) {
		ObjectNode request = JsonMapper.shared().createObjectNode();
		ArrayNode items = request.putArray("entities");
		for (ObjectNode entity : entities) {
			items.add(entity);
		}
		return request;
	}

	private MvcTestResult post(ObjectNode request) {
		return post(request, this.architect);
	}

	private MvcTestResult post(ObjectNode request, TestSession session) {
		return this.mvc.post()
			.uri(BATCH)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(request.toString())
			.exchange();
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private static String unique(String name) {
		return "test:" + name + "-" + UUID.randomUUID();
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-entities").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

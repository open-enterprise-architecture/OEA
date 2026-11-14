package io.oea.app.history;

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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/**
 * Spec repository/entity-history: the timeline with the visibility rule of design D2 of
 * us-098-entity-history and the errors and limits of {@code getEntityHistory} (D4). The entity
 * "CRM System" has five versions: 1 the creation, 2 the name and {@code investmentCost}, 3 only
 * {@code investmentCost}, 4 {@code securityClassification}, 5 {@code lifecycleStatus}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntityHistoryTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final Instant START = Instant.parse("2026-11-02T08:00:00Z");

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

	private UUID metatype;

	private UUID investmentCost;

	private UUID securityClassification;

	private UUID assignment;

	private int entity;

	private long creator;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		this.metatype = metatype("Application Component " + suffix, false);
		this.assignment = metatype("Assignment " + suffix, true);
		UUID lifecycleStatus = property("lifecycleStatus", "public", null);
		this.investmentCost = property("investmentCost", "role-restricted", null);
		this.jdbc.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?, 'enterprise-architect', 'read')",
				this.investmentCost);
		this.securityClassification = property("securityClassification", "connection-scoped", this.assignment);
		this.creator = person("Kurt");
		this.entity = entity("CRM System", this.metatype, 5, this.creator);
		systemChange(2, "name");
		propertyChange(2, this.investmentCost);
		propertyChange(3, this.investmentCost);
		propertyChange(4, this.securityClassification);
		propertyChange(5, lifecycleStatus);
	}

	// Requirement: Version history of an entity; Creation and errors of the history read

	@Test
	void timelineNewestFirstWithChangedFields() {
		JsonNode history = ok(read(architect(), ""));
		assertThat(versions(history)).containsExactly(5, 4, 3, 2, 1);
		assertThat(history.path("entityName").asString()).isEqualTo("CRM System");
		JsonNode second = version(history, 2);
		assertThat(second.path("changedBy").path("name").asString()).isEqualTo("Kurt");
		assertThat(fields(second)).containsExactly("name", this.investmentCost.toString());
		assertThat(second.path("changes").get(0).path("isSystemField").asBoolean()).isTrue();
		assertThat(second.path("changes").get(1).path("label").asString()).startsWith("investmentCost ");
		assertThat(second.path("changes").get(1).path("restricted").asBoolean()).isFalse();
		assertThat(second.path("changes").get(1).has("oldValue")).isFalse();
		assertThat(version(history, 1).path("changes")).isEmpty();
		assertThat(history.path("created").path("changedAt").asString()).isEqualTo("2026-11-02T09:00:00Z");
		assertThat(history.path("created").path("changedBy").path("name").asString()).isEqualTo("Kurt");
	}

	@Test
	void labelInTheReadersLanguage() {
		this.jdbc.update("""
				INSERT INTO i18n_entries (entity_type, entity_id, locale, field, value, scope)
				VALUES ('property_def', ?, 'de', 'label', 'Investitionskosten', 'organization')""", this.investmentCost);
		TestSession reader = architect();
		this.jdbc.update("UPDATE persons SET ui_language = 'de' WHERE id = ?", personOf(reader));
		assertThat(version(ok(read(reader, "")), 3).path("changes").get(0).path("label").asString())
			.isEqualTo("Investitionskosten");
	}

	@Test
	void unknownEntityAndMissingPermission() {
		assertThat(this.mvc.get()
			.uri("/api/v1/entities/" + Integer.MAX_VALUE + "/history")
			.cookie(cookie(architect()))
			.exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(read(this.principals.personWithIdpRole("portal-reader"), "")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void reversedPeriodNamesFrom() {
		MvcTestResult result = read(architect(), "?from=2026-11-02T12:00:00Z&to=2026-11-02T11:00:00Z");
		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"from\"]");
	}

	@Test
	void periodAndActorFilters() {
		long michael = person("Michael");
		int shared = entity("Shared System", this.metatype, 2, this.creator);
		for (int version = 3; version <= 5; version++) {
			this.jdbc.update(
					"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, ?, ?, ?)",
					shared, version, michael, Timestamp.from(START.plus(Duration.ofHours(version))));
		}
		JsonNode history = ok(read(architect(), shared, "?actorId=" + michael));
		assertThat(versions(history)).containsExactly(5, 4, 3);
		assertThat(history.path("page").path("totalElements").asInt()).isEqualTo(3);
		List<String> actors = new ArrayList<>();
		history.path("actors").forEach((actor) -> actors.add(actor.path("name").asString()));
		assertThat(actors).containsExactly("Kurt", "Michael");
		// the creation ignores the filters
		assertThat(history.path("created").path("changedBy").path("name").asString()).isEqualTo("Kurt");
		assertThat(versions(ok(read(architect(), "?from=2026-11-02T10:00:00Z&to=2026-11-02T12:00:00Z"))))
			.containsExactly(4, 3, 2);
	}

	// Requirement: Restricted fields in the history; Hiding restricted fields

	@Test
	void restrictedWithoutReadRoleAndReadableWithIt() {
		JsonNode change = version(ok(read(this.principals.personWithIdpRole("viewer"), "")), 3).path("changes").get(0);
		assertThat(change.path("field").asString()).isEqualTo(this.investmentCost.toString());
		assertThat(change.path("restricted").asBoolean()).isTrue();
		assertThat(version(ok(read(architect(), "")), 3).path("changes").get(0).path("restricted").asBoolean())
			.isFalse();
	}

	@Test
	void systemFieldsAreNeverRestricted() {
		JsonNode second = version(ok(read(this.principals.personWithIdpRole("viewer"), "")), 2);
		assertThat(second.path("changes").get(0).path("field").asString()).isEqualTo("name");
		assertThat(second.path("changes").get(0).path("restricted").asBoolean()).isFalse();
	}

	@Test
	void hiddenWhenEveryRoleHidesAndVersionStaysListed() {
		hide("viewer", this.metatype);
		JsonNode history = ok(read(this.principals.personWithIdpRole("viewer"), ""));
		assertThat(fields(version(history, 2))).containsExactly("name");
		// only hidden changes: the version stays without fields
		assertThat(versions(history)).containsExactly(5, 4, 3, 2, 1);
		assertThat(version(history, 3).path("changes")).isEmpty();
		assertThat(history.path("page").path("totalElements").asInt()).isEqualTo(5);
	}

	@Test
	void theMoreOpenSettingOfSeveralRolesWins() {
		hide("viewer", this.metatype);
		JsonNode change = version(ok(read(this.principals.personWithIdpRoles("viewer", "auditor"), "")), 3)
			.path("changes")
			.get(0);
		assertThat(change.path("restricted").asBoolean()).isTrue();
		hide("auditor", this.metatype);
		assertThat(version(ok(read(this.principals.personWithIdpRoles("viewer", "auditor"), "")), 3).path("changes"))
			.isEmpty();
	}

	@Test
	void hidingOfAnotherMetatypeDoesNotApply() {
		hide("viewer", this.assignment);
		assertThat(version(ok(read(this.principals.personWithIdpRole("viewer"), "")), 3).path("changes").get(0)
			.path("restricted")
			.asBoolean()).isTrue();
	}

	@Test
	void connectionScopedInsideAndOutsideTheScope() {
		UUID personType = metatype("Person " + UUID.randomUUID().toString().substring(0, 8), false);
		this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = ? WHERE id = 1", personType);
		try {
			TestSession inside = this.principals.personWithIdpRole("viewer");
			TestSession outside = this.principals.personWithIdpRole("viewer");
			int insideElement = entity("Inside Person", personType, 1, this.creator);
			int outsideElement = entity("Outside Person", personType, 1, this.creator);
			this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", insideElement, personOf(inside));
			this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", outsideElement, personOf(outside));
			int connection = entity("Assigned", this.assignment, 1, this.creator);
			this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
					connection, insideElement, this.entity);
			assertThat(version(ok(read(inside, "")), 4).path("changes").get(0).path("restricted").asBoolean()).isFalse();
			assertThat(version(ok(read(outside, "")), 4).path("changes").get(0).path("restricted").asBoolean()).isTrue();
		}
		finally {
			this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = NULL WHERE id = 1");
		}
	}

	@Test
	void overrideRoleReadsEveryField() {
		String role = "history-override-" + UUID.randomUUID().toString().substring(0, 8);
		this.jdbc.update("INSERT INTO authorization_roles (id, name, overrides_property_visibility) VALUES (?, ?, true)",
				role, role);
		JsonNode history = ok(read(this.principals.personWithIdpRoles("viewer", role), ""));
		assertThat(version(history, 3).path("changes").get(0).path("restricted").asBoolean()).isFalse();
		assertThat(version(history, 4).path("changes").get(0).path("restricted").asBoolean()).isFalse();
	}

	// Requirement: Paging of the history

	@ParameterizedTest
	@CsvSource({ "1, 200", "200, 200", "0, 400", "201, 400" })
	void pageSizeLimits(int pageSize, int status) {
		assertThat(read(architect(), "?pageSize=" + pageSize)).hasStatus(status);
	}

	@Test
	void cursorToTheNextPage() {
		JsonNode first = ok(read(architect(), "?pageSize=2"));
		assertThat(versions(first)).containsExactly(5, 4);
		assertThat(first.path("page").path("totalPages").asInt()).isEqualTo(3);
		String cursor = first.path("nextCursor").asString();
		JsonNode second = ok(read(architect(), "?pageSize=2&page=1&cursor=" + cursor));
		assertThat(versions(second)).containsExactly(3, 2);
		JsonNode last = ok(read(architect(), "?pageSize=2&page=2&cursor=" + second.path("nextCursor").asString()));
		assertThat(versions(last)).containsExactly(1);
		assertThat(last.path("nextCursor").isNull()).isTrue();
		// a cursor of another query
		MvcTestResult invalid = read(architect(), "?pageSize=3&page=1&cursor=" + cursor);
		assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(body(invalid).path("invalidFields").toString()).isEqualTo("[\"cursor\"]");
	}

	private TestSession architect() {
		return this.principals.personWithIdpRole("enterprise-architect");
	}

	private void hide(String role, UUID metatypeId) {
		this.jdbc.update("INSERT INTO history_visibility_settings (role_id, metatype_id, mode) VALUES (?, ?, 'hide')",
				role, metatypeId);
	}

	private MvcTestResult read(TestSession session, String query) {
		return read(session, this.entity, query);
	}

	private MvcTestResult read(TestSession session, int entityId, String query) {
		return this.mvc.get().uri("/api/v1/entities/" + entityId + "/history" + query).cookie(cookie(session)).exchange();
	}

	private static Cookie cookie(TestSession session) {
		return new Cookie(SessionCookie.NAME, session.token());
	}

	private long personOf(TestSession session) {
		return this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?) RETURNING id""", UUID.class, name, connection);
	}

	private UUID property(String name, String mode, UUID scopingType) {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String column = name.toLowerCase() + "_" + suffix;
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, ?, 'string', ?, ?) RETURNING id""", UUID.class, name + " " + suffix, column, mode, scopingType);
	}

	private long person(String name) {
		return this.jdbc.queryForObject("INSERT INTO persons (name, email) VALUES (?, ?) RETURNING id", Long.class, name,
				"p-" + UUID.randomUUID() + "@example.org");
	}

	/** An entity with {@code versions} version headers; version n is at START + n hours. */
	private int entity(String name, UUID metatypeId, int versions, long person) {
		Integer id = this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, version, created_by) VALUES (?, ?, ?, ?) RETURNING id",
				Integer.class, metatypeId, name, versions, person);
		for (int version = 1; version <= versions; version++) {
			this.jdbc.update(
					"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, ?, ?, ?)",
					id, version, person, Timestamp.from(START.plus(Duration.ofHours(version))));
		}
		return id;
	}

	private void systemChange(int version, String field) {
		this.jdbc.update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (?, ?, ?, 'old', 'new', ?)""", this.entity, version, field, this.creator);
	}

	private void propertyChange(int version, UUID property) {
		this.jdbc.update("""
				INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_text, changed_by)
				VALUES (?, ?, ?, 'before', ?)""", this.entity, property, version, this.creator);
	}

	private static List<Integer> versions(JsonNode history) {
		List<Integer> versions = new ArrayList<>();
		history.path("versions").forEach((version) -> versions.add(version.path("version").asInt()));
		return versions;
	}

	private static JsonNode version(JsonNode history, int number) {
		for (JsonNode version : history.path("versions")) {
			if (version.path("version").asInt() == number) {
				return version;
			}
		}
		throw new AssertionError("no version " + number + " in " + history);
	}

	private static List<String> fields(JsonNode version) {
		List<String> fields = new ArrayList<>();
		version.path("changes").forEach((change) -> fields.add(change.path("field").asString()));
		return fields;
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
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
			return Files.createTempDirectory("oea-entity-history").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

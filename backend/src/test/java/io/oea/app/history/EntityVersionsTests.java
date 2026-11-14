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
 * Spec repository/entity-versions: states, diffs and comparisons with the visibility rule and
 * the override audit (design D2 of us-099-version-diff), and the immutability of versions (D3).
 * The entity "CRM System" has seven versions:
 * <ul>
 * <li>2 sets the description "Core CRM";</li>
 * <li>3 changes {@code investmentCost} from 50 to 100;</li>
 * <li>4 changes {@code lifecycleStatus} from planned to active;</li>
 * <li>5 changes {@code owner} from Kurt to Michael;</li>
 * <li>6 changes {@code lifecycleStatus} back to planned;</li>
 * <li>7 removes {@code retiredNote} ("obsolete").</li>
 * </ul>
 * {@code contact} is mapped to the metatype and never has a value.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntityVersionsTests {

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

	private UUID owner;

	private UUID contact;

	private UUID lifecycleStatus;

	private UUID investmentCost;

	private UUID retiredNote;

	private UUID critical;

	private UUID goLive;

	private UUID successor;

	private int entity;

	private int erp;

	private long creator;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.metatype = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?)
				RETURNING id""", UUID.class, "Application Component " + suffix());
		this.owner = property("owner", "string", "public");
		this.contact = property("contact", "string", "public");
		this.lifecycleStatus = property("lifecycleStatus", "string", "public");
		this.investmentCost = property("investmentCost", "string", "role-restricted");
		this.jdbc.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?, 'enterprise-architect', 'read')",
				this.investmentCost);
		this.retiredNote = property("retiredNote", "string", "public");
		this.critical = property("critical", "boolean", "public");
		this.goLive = property("goLive", "date", "public");
		this.successor = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, ref_metatype_id)
				VALUES (?, ?, 'reference', ?) RETURNING id""", UUID.class, "successor " + suffix(),
				"successor_" + suffix(), this.metatype);
		map(this.owner, 1);
		map(this.contact, 2);
		this.creator = this.jdbc.queryForObject("INSERT INTO persons (name, email) VALUES ('Kurt', ?) RETURNING id",
				Long.class, "kurt-" + UUID.randomUUID() + "@example.org");
		this.erp = entity("ERP", 1);
		this.entity = entity("CRM System", 7);
		this.jdbc.update("UPDATE entities SET description = 'Core CRM' WHERE id = ?", this.entity);
		this.jdbc.update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (?, 2, 'description', '', 'Core CRM', ?)""", this.entity, this.creator);
		value(this.investmentCost, "value_text", "100");
		history(this.investmentCost, 3, "50");
		value(this.lifecycleStatus, "value_text", "planned");
		history(this.lifecycleStatus, 4, "planned");
		history(this.lifecycleStatus, 6, "active");
		value(this.owner, "value_text", "Michael");
		history(this.owner, 5, "Kurt");
		history(this.retiredNote, 7, "obsolete");
		value(this.critical, "value_bool", true);
		value(this.goLive, "value_date", java.sql.Date.valueOf("2026-12-01"));
		value(this.successor, "value_ref", this.erp);
	}

	// Requirement: Complete state of a version; Properties of a state

	@Test
	void completeStateWithEmptyProperties() {
		JsonNode state = ok(state(architect(), 4));
		assertThat(state.path("version").asInt()).isEqualTo(4);
		assertThat(state.path("entityName").asString()).isEqualTo("CRM System");
		assertThat(state.path("changedBy").path("name").asString()).isEqualTo("Kurt");
		assertThat(fieldKeys(state)).startsWith("name", "description", "isLogical", "metatypeId")
			.contains(this.owner.toString(), this.contact.toString(), this.lifecycleStatus.toString());
		// mapped properties first, in mapping order
		assertThat(fieldKeys(state).get(4)).isEqualTo(this.owner.toString());
		assertThat(field(state, this.owner.toString()).path("value").asString()).isEqualTo("Kurt");
		assertThat(field(state, this.contact.toString()).path("value").isNull()).isTrue();
		assertThat(field(state, this.lifecycleStatus.toString()).path("value").asString()).isEqualTo("active");
		assertThat(field(state, "name").path("dataType").asString()).isEqualTo("system");
	}

	@Test
	void valueOfAnOlderVersion() {
		assertThat(field(ok(state(architect(), 3)), this.lifecycleStatus.toString()).path("value").asString())
			.isEqualTo("planned");
		assertThat(field(ok(state(architect(), 4)), this.lifecycleStatus.toString()).path("value").asString())
			.isEqualTo("active");
	}

	@Test
	void valuesAsText() {
		JsonNode state = ok(state(architect(), 7));
		assertThat(field(state, this.critical.toString()).path("value").asString()).isEqualTo("true");
		assertThat(field(state, this.critical.toString()).path("dataType").asString()).isEqualTo("boolean");
		assertThat(field(state, this.goLive.toString()).path("value").asString()).isEqualTo("2026-12-01");
		assertThat(field(state, this.successor.toString()).path("value").asString()).isEqualTo("ERP");
		assertThat(field(state, "metatypeId").path("value").asString()).startsWith("Application Component ");
		assertThat(field(state, "isLogical").path("value").asString()).isEqualTo("true");
	}

	@ParameterizedTest
	@CsvSource({ "1, 200", "7, 200", "8, 404", "0, 400" })
	void versionBoundaries(int version, int status) {
		assertThat(state(architect(), version)).hasStatus(status);
	}

	// Requirement: Diff of a version; Comparison of two versions

	@Test
	void onlyChangedFields() {
		JsonNode diff = ok(compare(architect(), 4, 5));
		assertThat(changeKeys(diff)).containsExactly(this.owner.toString());
		JsonNode change = diff.path("changes").get(0);
		assertThat(change.path("oldValue").asString()).isEqualTo("Kurt");
		assertThat(change.path("newValue").asString()).isEqualTo("Michael");
		assertThat(change.path("label").asString()).startsWith("owner ");
		assertThat(change.path("redacted").asBoolean()).isFalse();
	}

	@Test
	void addedAndRemovedValues() {
		JsonNode added = ok(compare(architect(), 1, 2)).path("changes").get(0);
		assertThat(added.path("field").asString()).isEqualTo("description");
		assertThat(added.path("oldValue").isNull()).isTrue();
		assertThat(added.path("newValue").asString()).isEqualTo("Core CRM");
		JsonNode removed = ok(compare(architect(), 6, 7)).path("changes").get(0);
		assertThat(removed.path("field").asString()).isEqualTo(this.retiredNote.toString());
		assertThat(removed.path("oldValue").asString()).isEqualTo("obsolete");
		assertThat(removed.path("newValue").isNull()).isTrue();
	}

	@Test
	void netDiffLeavesOutAFieldChangedBack() {
		JsonNode diff = ok(compare(architect(), 2, 7));
		assertThat(changeKeys(diff)).containsExactlyInAnyOrder(this.owner.toString(), this.investmentCost.toString(),
				this.retiredNote.toString());
		assertThat(diff.path("from").asInt()).isEqualTo(2);
		assertThat(diff.path("to").asInt()).isEqualTo(7);
	}

	@ParameterizedTest
	@CsvSource({ "1, 7, 200", "6, 7, 200", "7, 7, 400", "0, 7, 400", "1, 8, 404" })
	void versionRangeBoundaries(int from, int to, int status) {
		MvcTestResult result = compare(architect(), from, to);
		assertThat(result).hasStatus(status);
		if (status == 400) {
			assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"from\"]");
		}
	}

	@Test
	void unknownEntityAndMissingPermission() {
		assertThat(this.mvc.get()
			.uri("/api/v1/entities/" + Integer.MAX_VALUE + "/diff?from=1&to=2")
			.cookie(cookie(architect()))
			.exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get()
			.uri("/api/v1/entities/" + Integer.MAX_VALUE + "/versions/1")
			.cookie(cookie(architect()))
			.exchange()).hasStatus(HttpStatus.NOT_FOUND);
		TestSession portal = this.principals.personWithIdpRole("portal-reader");
		assertThat(state(portal, 1)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(compare(portal, 1, 2)).hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Values the reader may not read

	@Test
	void restrictedValuesAreRedacted() {
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		JsonNode change = ok(compare(viewer, 2, 3)).path("changes").get(0);
		assertThat(change.path("field").asString()).isEqualTo(this.investmentCost.toString());
		assertThat(change.path("redacted").asBoolean()).isTrue();
		assertThat(change.path("oldValue").isNull()).isTrue();
		assertThat(change.path("newValue").isNull()).isTrue();
		JsonNode field = field(ok(state(viewer, 3)), this.investmentCost.toString());
		assertThat(field.path("restricted").asBoolean()).isTrue();
		assertThat(field.path("value").isNull()).isTrue();
	}

	@Test
	void hiddenFromStateAndDiff() {
		this.jdbc.update("INSERT INTO history_visibility_settings (role_id, metatype_id, mode) VALUES ('viewer', ?, 'hide')",
				this.metatype);
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		assertThat(fieldKeys(ok(state(viewer, 3)))).doesNotContain(this.investmentCost.toString())
			.contains(this.owner.toString());
		assertThat(ok(compare(viewer, 2, 3)).path("changes")).isEmpty();
	}

	// Requirement: Override reads of version values

	@Test
	void overrideReadIsAudited() {
		String role = "versions-override-" + suffix();
		this.jdbc.update("INSERT INTO authorization_roles (id, name, overrides_property_visibility) VALUES (?, ?, true)",
				role, role);
		int before = events();
		JsonNode change = ok(compare(this.principals.personWithIdpRoles("viewer", role), 2, 3)).path("changes").get(0);
		assertThat(change.path("oldValue").asString()).isEqualTo("50");
		assertThat(change.path("newValue").asString()).isEqualTo("100");
		assertThat(events()).isEqualTo(before + 1);
		JsonNode details = JsonMapper.shared().readTree(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'property-override-access'
				AND target_type = 'entity' AND target_id = ? ORDER BY chain_seq DESC LIMIT 1""", String.class,
				Integer.toString(this.entity)));
		assertThat(details.path("channel").asString()).isEqualTo("history");
		assertThat(details.path("properties").get(0).path("propertyDefId").asString())
			.isEqualTo(this.investmentCost.toString());
		assertThat(details.path("properties").get(0).path("entityIds").get(0).asInt()).isEqualTo(this.entity);
		// a read role needs no override
		ok(compare(architect(), 2, 3));
		ok(state(architect(), 3));
		assertThat(events()).isEqualTo(before + 1);
	}

	// Requirement: Immutable versions

	@ParameterizedTest
	@CsvSource({ "PUT", "DELETE" })
	void versionsAreImmutable(String method) {
		TestSession architect = architect();
		JsonNode before = ok(state(architect, 4));
		MvcTestResult result = this.mvc.method(org.springframework.http.HttpMethod.valueOf(method))
			.uri("/api/v1/entities/" + this.entity + "/versions/4")
			.cookie(cookie(architect))
			.header(CsrfOriginFilter.HEADER, architect.csrfToken())
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{}")
			.exchange();
		assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(result.getResponse().getHeader("Allow")).isEqualTo("GET");
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:method-not-allowed");
		assertThat(ok(state(architect, 4))).isEqualTo(before);
	}

	private int events() {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'property-override-access' AND target_id = ?",
				Integer.class, Integer.toString(this.entity));
	}

	private TestSession architect() {
		return this.principals.personWithIdpRole("enterprise-architect");
	}

	private MvcTestResult state(TestSession session, int version) {
		return this.mvc.get()
			.uri("/api/v1/entities/" + this.entity + "/versions/" + version)
			.cookie(cookie(session))
			.exchange();
	}

	private MvcTestResult compare(TestSession session, int from, int to) {
		return this.mvc.get()
			.uri("/api/v1/entities/" + this.entity + "/diff?from=" + from + "&to=" + to)
			.cookie(cookie(session))
			.exchange();
	}

	private static Cookie cookie(TestSession session) {
		return new Cookie(SessionCookie.NAME, session.token());
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private UUID property(String name, String dataType, String mode) {
		String suffix = suffix();
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				VALUES (?, ?, ?, ?) RETURNING id""", UUID.class, name + " " + suffix,
				name.toLowerCase() + "_" + suffix, dataType, mode);
	}

	private void map(UUID property, int order) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope, sort_order)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization', ?)""", this.metatype, property, order);
	}

	/** An entity with {@code versions} version headers; version n is at START + n hours. */
	private int entity(String name, int versions) {
		Integer id = this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, version, created_by) VALUES (?, ?, ?, ?) RETURNING id",
				Integer.class, this.metatype, name, versions, this.creator);
		for (int version = 1; version <= versions; version++) {
			this.jdbc.update(
					"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, ?, ?, ?)",
					id, version, this.creator, Timestamp.from(START.plus(Duration.ofHours(version))));
		}
		return id;
	}

	private void value(UUID property, String column, Object value) {
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, " + column + ") VALUES (?, ?, ?)",
				this.entity, property, value);
	}

	private void history(UUID property, int version, String before) {
		this.jdbc.update("""
				INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_text, changed_by)
				VALUES (?, ?, ?, ?, ?)""", this.entity, property, version, before, this.creator);
	}

	private static List<String> fieldKeys(JsonNode state) {
		List<String> keys = new ArrayList<>();
		state.path("fields").forEach((field) -> keys.add(field.path("field").asString()));
		return keys;
	}

	private static List<String> changeKeys(JsonNode diff) {
		List<String> keys = new ArrayList<>();
		diff.path("changes").forEach((change) -> keys.add(change.path("field").asString()));
		return keys;
	}

	private static JsonNode field(JsonNode state, String key) {
		for (JsonNode field : state.path("fields")) {
			if (field.path("field").asString().equals(key)) {
				return field;
			}
		}
		throw new AssertionError("no field " + key + " in " + state);
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
			return Files.createTempDirectory("oea-entity-versions").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

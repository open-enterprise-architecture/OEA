package io.oea.app.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import io.oea.app.security.Sessions;
import io.oea.audit.AuditWriter;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec repository/entity-restore: preview, classification, full restore, {@code If-Match},
 * boundaries and the atomic write (design D2 to D4 of us-101-entity-restore). The entity
 * "CRM System" is at version 5; version 3 differs from the current state in:
 * <ul>
 * <li>the name "CRM" (renamed in 4) and the description "Old text" (changed in 5);</li>
 * <li>{@code owner} Kurt (Michael since 4) and no {@code website} (set in 5);</li>
 * <li>{@code investmentCost} 50 (100 since 4): readable, not writable for the architect;</li>
 * <li>{@code secretNote} a (b since 4): neither readable nor writable for the architect;</li>
 * <li>{@code legacyCode} L-1 (removed in 4), no longer mapped;</li>
 * <li>{@code lifecycleStatus} retired (active since 4), no longer an enumeration value;</li>
 * <li>no {@code contact} (set in 5), mandatory today.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class EntityRestoreTests {

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

	@MockitoSpyBean
	private AuditWriter audit;

	private TestPrincipals principals;

	private UUID metatype;

	private UUID owner;

	private UUID website;

	private UUID investmentCost;

	private UUID secretNote;

	private UUID legacyCode;

	private UUID lifecycleStatus;

	private UUID contact;

	private long creator;

	private int entity;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.metatype = metatype("Application", false);
		this.owner = property("owner", "string", "public");
		this.website = property("website", "url", "public");
		this.investmentCost = property("investmentCost", "string", "role-restricted");
		this.jdbc.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?, 'enterprise-architect', 'read')",
				this.investmentCost);
		this.secretNote = property("secretNote", "string", "role-restricted");
		this.legacyCode = property("legacyCode", "string", "public");
		this.lifecycleStatus = property("lifecycleStatus", "enum", "public");
		this.jdbc.update("INSERT INTO property_enum_values (property_def_id, value) VALUES (?, 'planned'), (?, 'active')",
				this.lifecycleStatus, this.lifecycleStatus);
		this.contact = property("contact", "string", "public");
		map(this.owner, "optional", 1);
		map(this.website, "optional", 2);
		map(this.investmentCost, "optional", 3);
		map(this.secretNote, "optional", 4);
		map(this.lifecycleStatus, "optional", 5);
		map(this.contact, "mandatory", 6);
		this.creator = this.jdbc.queryForObject("INSERT INTO persons (name, email) VALUES ('Kurt', ?) RETURNING id",
				Long.class, "kurt-" + UUID.randomUUID() + "@example.org");

		this.entity = entity(this.metatype, "CRM System", 5);
		this.jdbc.update("UPDATE entities SET description = 'New text' WHERE id = ?", this.entity);
		systemHistory(this.entity, 4, "name", "CRM", "CRM System");
		systemHistory(this.entity, 5, "description", "Old text", "New text");
		value(this.entity, this.owner, "value_text", "Michael");
		history(this.entity, this.owner, 4, "value_text", "Kurt");
		value(this.entity, this.website, "value_text", "https://crm.example.org");
		history(this.entity, this.website, 5, "value_text", null);
		value(this.entity, this.investmentCost, "value_text", "100");
		history(this.entity, this.investmentCost, 4, "value_text", "50");
		value(this.entity, this.secretNote, "value_text", "b");
		history(this.entity, this.secretNote, 4, "value_text", "a");
		history(this.entity, this.legacyCode, 4, "value_text", "L-1");
		value(this.entity, this.lifecycleStatus, "value_enum", "active");
		history(this.entity, this.lifecycleStatus, 4, "value_enum", "retired");
		value(this.entity, this.contact, "value_text", "crm-team");
		history(this.entity, this.contact, 5, "value_text", null);
	}

	@AfterEach
	void resetAudit() {
		reset(this.audit);
	}

	// Requirement: Preview of a restore; Fields not restored; Warnings and redaction in the preview

	@Test
	void previewListsTheDifferingFieldsAndTheirClassification() {
		JsonNode preview = ok(preview(architect(), this.entity, 3));

		assertThat(preview.path("currentVersion").asInt()).isEqualTo(5);
		assertThat(preview.path("targetVersion").asInt()).isEqualTo(3);
		assertThat(keys(preview.path("fields"))).containsExactlyInAnyOrder("name", "description", key(this.owner),
				key(this.website), key(this.investmentCost), key(this.secretNote), key(this.legacyCode),
				key(this.lifecycleStatus), key(this.contact));
		JsonNode description = field(preview, "description");
		assertThat(description.path("currentValue").asString()).isEqualTo("New text");
		assertThat(description.path("targetValue").asString()).isEqualTo("Old text");
		assertThat(description.path("restore").asBoolean()).isTrue();
		assertThat(description.path("reason").isNull()).isTrue();
		assertThat(field(preview, key(this.owner)).path("targetValue").asString()).isEqualTo("Kurt");
		assertThat(field(preview, key(this.website)).path("targetValue").isNull()).isTrue();
		assertThat(field(preview, key(this.website)).path("restore").asBoolean()).isTrue();
		assertThat(reason(preview, this.investmentCost)).isEqualTo("write-protected");
		assertThat(field(preview, key(this.investmentCost)).path("targetValue").asString()).isEqualTo("50");
		assertThat(reason(preview, this.legacyCode)).isEqualTo("not-mapped");
		assertThat(reason(preview, this.lifecycleStatus)).isEqualTo("invalid-value");
		assertThat(field(preview, key(this.contact)).path("restore").asBoolean()).isTrue();
		assertThat(preview.path("warnings")).extracting((warning) -> warning.path("code").asString())
			.containsExactly("MANDATORY_MISSING");
	}

	@Test
	void previewRedactsValuesTheCallerMayNotRead() {
		JsonNode secret = field(ok(preview(architect(), this.entity, 3)), key(this.secretNote));

		assertThat(secret.path("redacted").asBoolean()).isTrue();
		assertThat(secret.path("currentValue").isNull()).isTrue();
		assertThat(secret.path("targetValue").isNull()).isTrue();
		assertThat(secret.path("reason").asString()).isEqualTo("write-protected");
	}

	@Test
	void previewLeavesOutHiddenProperties() {
		this.jdbc.update("""
				INSERT INTO history_visibility_settings (role_id, metatype_id, mode)
				VALUES ('enterprise-architect', ?, 'hide')""", this.metatype);

		assertThat(keys(ok(preview(architect(), this.entity, 3)).path("fields"))).doesNotContain(key(this.secretNote))
			.contains(key(this.owner));
	}

	@Test
	void previewChangesNothing() {
		ok(preview(architect(), this.entity, 3));

		assertThat(version(this.entity)).isEqualTo(5);
	}

	// Requirement: Full restore of an entity; Version and result of a restore

	@Test
	void restoreToAnEarlierVersion() {
		TestSession architect = architect();
		JsonNode before = ok(state(architect, 5)).path("fields");

		JsonNode result = ok(restore(architect, this.entity, "\"5\"", 3, "Wrong owner"));

		assertThat(result.path("version").asInt()).isEqualTo(6);
		assertThat(result.path("restoredFromVersion").asInt()).isEqualTo(3);
		assertThat(keys(result.path("restored"))).containsExactlyInAnyOrder("name", "description", key(this.owner),
				key(this.website), key(this.contact));
		assertThat(result.path("notRestored")).extracting((field) -> field.path("reason").asString())
			.containsExactlyInAnyOrder("write-protected", "write-protected", "not-mapped", "invalid-value");
		Map<String, Object> row = this.jdbc.queryForMap(
				"SELECT name, description, version, metatype_id FROM entities WHERE id = ?", this.entity);
		assertThat(row).containsEntry("name", "CRM").containsEntry("description", "Old text").containsEntry("version", 6)
			.containsEntry("metatype_id", this.metatype);
		assertThat(text(this.entity, this.owner)).isEqualTo("Kurt");
		assertThat(text(this.entity, this.website)).isNull();
		assertThat(text(this.entity, this.investmentCost)).isEqualTo("100");
		assertThat(text(this.entity, this.secretNote)).isEqualTo("b");
		assertThat(this.jdbc.queryForObject("SELECT value_enum FROM entity_property_values WHERE entity_id = ? AND property_def_id = ?",
				String.class, this.entity, this.lifecycleStatus)).isEqualTo("active");
		assertThat(this.jdbc.queryForMap(
				"SELECT restored_from_version, change_reason FROM entity_version_headers WHERE entity_id = ? AND version = 6",
				this.entity)).containsEntry("restored_from_version", 3).containsEntry("change_reason", "Wrong owner");
		// the state before the restore is version 5, unchanged
		assertThat(ok(state(architect, 5)).path("fields")).isEqualTo(before);
	}

	@Test
	void missingMandatoryValueIsAWarningAndAMark() {
		JsonNode result = ok(restore(architect(), this.entity, "\"5\"", 3, null));

		assertThat(result.path("warnings")).extracting((warning) -> warning.path("code").asString())
			.containsExactly("MANDATORY_MISSING");
		assertThat(this.jdbc.queryForList("SELECT property_def_id FROM entity_mandatory_marks WHERE entity_id = ?",
				UUID.class, this.entity)).containsExactly(this.contact);
	}

	@Test
	void restoreIsAuditedAndShownInTheHistory() {
		TestSession architect = architect();
		ok(restore(architect, this.entity, "\"5\"", 3, "Wrong owner"));

		Map<String, Object> event = this.jdbc.queryForMap("""
				SELECT details->>'version' AS version, details->>'restoredFromVersion' AS target, details->>'reason' AS reason
				FROM audit.events WHERE event_type = 'entity.restored' AND target_id = ?""", Integer.toString(this.entity));
		assertThat(event).containsEntry("version", "6").containsEntry("target", "3").containsEntry("reason", "Wrong owner");
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.event_changes c JOIN audit.events e ON e.id = c.event_id
				WHERE e.event_type = 'entity.restored' AND e.target_id = ?""", Integer.class, Integer.toString(this.entity)))
			.isEqualTo(5);
		JsonNode history = ok(this.mvc.get().uri("/api/v1/entities/" + this.entity + "/history").cookie(cookie(architect)).exchange());
		assertThat(history.path("currentVersion").asInt()).isEqualTo(6);
		JsonNode newest = history.path("versions").get(0);
		assertThat(newest.path("version").asInt()).isEqualTo(6);
		assertThat(newest.path("restoredFromVersion").asInt()).isEqualTo(3);
		assertThat(newest.path("reason").asString()).isEqualTo("Wrong owner");
		assertThat(history.path("versions").get(1).path("restoredFromVersion").isNull()).isTrue();
	}

	@Test
	void currentVersionIndependentOfFilters() {
		JsonNode history = ok(this.mvc.get()
			.uri("/api/v1/entities/" + this.entity + "/history?to=" + START.plus(Duration.ofHours(2)))
			.cookie(cookie(architect()))
			.exchange());

		assertThat(history.path("currentVersion").asInt()).isEqualTo(5);
		assertThat(history.path("versions")).extracting((version) -> version.path("version").asInt())
			.containsExactly(2, 1);
	}

	@Test
	void endpointsOfAConnectionAreUnchanged() {
		UUID flow = metatype("Flow", true);
		int source = entity(this.metatype, "Source", 1);
		int target = entity(this.metatype, "Target", 1);
		int connection = entity(flow, "Flow B", 2);
		systemHistory(connection, 2, "name", "Flow A", "Flow B");
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				connection, source, target);

		ok(restore(architect(), connection, "\"2\"", 1, null));

		assertThat(this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, connection))
			.isEqualTo("Flow A");
		assertThat(this.jdbc.queryForMap("SELECT source_entity_id, target_entity_id FROM entity_connections WHERE entity_id = ?",
				connection)).containsEntry("source_entity_id", source).containsEntry("target_entity_id", target);
	}

	// Requirement: Protection against lost updates

	@ParameterizedTest
	@CsvSource({ "'\"4\"', 412", "'\"6\"', 412", "'abc', 412" })
	void staleIfMatchIsRejected(String ifMatch, int status) {
		MvcTestResult result = restore(architect(), this.entity, ifMatch, 3, null);

		assertThat(result).hasStatus(status);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:stale-version");
		assertThat(body(result).path("currentVersion").asInt()).isEqualTo(5);
		assertThat(version(this.entity)).isEqualTo(5);
	}

	@Test
	void missingIfMatchIsRejected() {
		MvcTestResult result = restore(architect(), this.entity, null, 3, null);

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:precondition-required");
		assertThat(version(this.entity)).isEqualTo(5);
	}

	// Requirement: Target version of a restore

	@ParameterizedTest
	@CsvSource({ "1, 200", "4, 200", "0, 400", "5, 400", "6, 404" })
	void targetVersionBoundaries(int target, int status) {
		MvcTestResult preview = preview(architect(), this.entity, target);
		assertThat(preview).hasStatus(status);
		if (status == 400) {
			assertThat(body(preview).path("invalidFields")).extracting(JsonNode::asString)
				.containsExactly("targetVersion");
			assertThat(body(restore(architect(), this.entity, "\"5\"", target, null)).path("invalidFields"))
				.extracting(JsonNode::asString)
				.containsExactly("targetVersion");
		}
		if (status != 200) {
			assertThat(restore(architect(), this.entity, "\"5\"", target, null)).hasStatus(status);
			assertThat(version(this.entity)).isEqualTo(5);
		}
	}

	@Test
	void unknownEntity() {
		assertThat(preview(architect(), Integer.MAX_VALUE, 1)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(restore(architect(), Integer.MAX_VALUE, "\"2\"", 1, null)).hasStatus(HttpStatus.NOT_FOUND);
	}

	// Requirement: Reason, permission and empty restores

	@Test
	void reasonOf1000CharactersIsAccepted() {
		assertThat(restore(architect(), this.entity, "\"5\"", 3, "r".repeat(1000))).hasStatus(HttpStatus.OK);
	}

	@Test
	void reasonOf1001CharactersIsRejected() {
		MvcTestResult result = restore(architect(), this.entity, "\"5\"", 3, "r".repeat(1001));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(body(result).path("invalidFields")).extracting(JsonNode::asString).containsExactly("reason");
		assertThat(version(this.entity)).isEqualTo(5);
	}

	@Test
	void onlyWriteProtectedDifferences() {
		int other = entity(this.metatype, "Billing", 2);
		value(other, this.contact, "value_text", "billing-team");
		value(other, this.investmentCost, "value_text", "100");
		history(other, this.investmentCost, 2, "value_text", "50");

		MvcTestResult result = restore(architect(), other, "\"2\"", 1, null);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:nothing-to-restore");
		assertThat(version(other)).isEqualTo(2);
	}

	@Test
	void withoutRestorePermission() {
		TestSession writer = this.principals.personWithIdpRole("integration-writer");

		assertThat(restore(writer, this.entity, "\"5\"", 3, null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(preview(writer, this.entity, 3)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(preview(this.principals.personWithIdpRole("viewer"), this.entity, 3)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(version(this.entity)).isEqualTo(5);
	}

	// Requirement: Atomic restore with audit

	@Test
	void failedAuditRollsTheRestoreBack() {
		doThrow(new IllegalStateException("audit unavailable")).when(this.audit)
			.append(argThat((event) -> event != null && "entity.restored".equals(event.eventType())), any(), any(),
					any());
		int headers = headers(this.entity);
		String description = this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class,
				this.entity);

		assertThat(restore(architect(), this.entity, "\"5\"", 3, null)).hasFailed()
			.failure()
			.hasRootCauseMessage("audit unavailable");

		assertThat(version(this.entity)).isEqualTo(5);
		assertThat(headers(this.entity)).isEqualTo(headers);
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, this.entity))
			.isEqualTo(description);
		assertThat(text(this.entity, this.owner)).isEqualTo("Michael");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_property_value_history WHERE entity_id = ? AND entity_version = 6",
				Integer.class, this.entity)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'entity.restored' AND target_id = ?",
				Integer.class, Integer.toString(this.entity))).isZero();
	}

	// us-104-partial-restore: Partial restore; Rejections of a partial restore; Restored fields

	@Test
	void partialRestoreOfTheSelectedFields() {
		JsonNode result = ok(partial(architect(), List.of("description", key(this.owner)), null));

		assertThat(keys(result.path("restored"))).containsExactly("description", key(this.owner));
		assertThat(result.path("notRestored")).isEmpty();
		assertThat(this.jdbc.queryForMap("SELECT name, description FROM entities WHERE id = ?", this.entity))
			.containsEntry("name", "CRM System")
			.containsEntry("description", "Old text");
		assertThat(text(this.entity, this.owner)).isEqualTo("Kurt");
		assertThat(text(this.entity, this.website)).isEqualTo("https://crm.example.org");
		assertThat(text(this.entity, this.contact)).isEqualTo("crm-team");
		assertThat(this.jdbc.queryForObject(
				"SELECT array_to_string(restored_fields, ',') FROM entity_version_headers WHERE entity_id = ? AND version = 6",
				String.class, this.entity)).isEqualTo("description," + this.owner);
		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'restoredFields' FROM audit.events WHERE event_type = 'entity.restored' AND target_id = ?""",
				String.class, Integer.toString(this.entity))).contains("description").contains(this.owner.toString());
		// warnings only for restored fields: contact stays set
		assertThat(result.path("warnings")).isEmpty();
	}

	@Test
	void selectedWriteProtectedPropertyIsKeptAndListed() {
		JsonNode result = ok(partial(architect(), List.of("description", key(this.investmentCost)), null));

		assertThat(keys(result.path("restored"))).containsExactly("description");
		assertThat(result.path("notRestored")).hasSize(1);
		assertThat(result.path("notRestored").get(0).path("field").asString()).isEqualTo(key(this.investmentCost));
		assertThat(result.path("notRestored").get(0).path("reason").asString()).isEqualTo("write-protected");
		assertThat(text(this.entity, this.investmentCost)).isEqualTo("100");
	}

	@Test
	void oneOfSeveralDifferingFieldsIsAccepted() {
		assertThat(keys(ok(partial(architect(), List.of(key(this.website)), null)).path("restored")))
			.containsExactly(key(this.website));
		assertThat(text(this.entity, this.website)).isNull();
		assertThat(text(this.entity, this.owner)).isEqualTo("Michael");
	}

	@Test
	void allRestorableFieldsSelected() {
		JsonNode result = ok(partial(architect(),
				List.of("name", "description", key(this.owner), key(this.website), key(this.contact)), null));

		assertThat(keys(result.path("restored"))).hasSize(5);
		assertThat(result.path("warnings")).extracting((warning) -> warning.path("field").asString())
			.containsExactly(key(this.contact));
	}

	@ParameterizedTest
	@CsvSource({ "''", "isLogical", "unknown-field" })
	void invalidSelectionIsRejected(String selected) {
		List<String> fields = selected.isEmpty() ? List.of() : List.of(selected);

		MvcTestResult result = partial(architect(), fields, null);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:validation-failed");
		assertThat(body(result).path("invalidFields")).extracting(JsonNode::asString).containsExactly("fields");
		assertThat(version(this.entity)).isEqualTo(5);
	}

	@Test
	void onlyNotRestorableFieldsSelected() {
		MvcTestResult result = partial(architect(), List.of(key(this.investmentCost), key(this.legacyCode)), null);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:nothing-to-restore");
		assertThat(version(this.entity)).isEqualTo(5);
	}

	@Test
	void fullRestoreRecordsNoFields() {
		ok(restore(architect(), this.entity, "\"5\"", 3, null));

		assertThat(this.jdbc.queryForObject(
				"SELECT restored_fields IS NULL FROM entity_version_headers WHERE entity_id = ? AND version = 6",
				Boolean.class, this.entity)).isTrue();
		JsonNode history = ok(this.mvc.get().uri("/api/v1/entities/" + this.entity + "/history").cookie(cookie(architect())).exchange());
		assertThat(history.path("versions").get(0).path("restoredFields").isNull()).isTrue();
	}

	@Test
	void historyListsTheRestoredFields() {
		this.jdbc.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?, 'enterprise-architect', 'write')",
				this.secretNote);
		ok(partial(architect(), List.of("description", key(this.owner), key(this.secretNote)), null));

		JsonNode fields = ok(this.mvc.get().uri("/api/v1/entities/" + this.entity + "/history").cookie(cookie(architect())).exchange())
			.path("versions")
			.get(0)
			.path("restoredFields");
		assertThat(keys(fields)).containsExactly("description", key(this.owner), key(this.secretNote));
		assertThat(fields.get(0).path("label").asString()).isEqualTo("Description");
		assertThat(fields.get(1).path("label").asString()).startsWith("owner ");
		assertThat(fields.get(1).path("restricted").asBoolean()).isFalse();
		// written but not readable: restricted in the history of the architect
		assertThat(fields.get(2).path("restricted").asBoolean()).isTrue();
		this.jdbc.update("""
				INSERT INTO history_visibility_settings (role_id, metatype_id, mode)
				VALUES ('enterprise-architect', ?, 'hide')""", this.metatype);
		JsonNode hidden = ok(this.mvc.get().uri("/api/v1/entities/" + this.entity + "/history").cookie(cookie(architect())).exchange())
			.path("versions")
			.get(0)
			.path("restoredFields");
		assertThat(keys(hidden)).containsExactly("description", key(this.owner));
	}

	// us-104-partial-restore: Fields not restored (not-in-source)

	@Test
	void propertyAddedAfterTheTargetVersionIsKept() {
		UUID costCenter = property("costCenter", "string", "public");
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope, sort_order)
				VALUES (?, ?, 'optional', DATE '2026-11-10', 'organization', 9)""", this.metatype, costCenter);
		value(this.entity, costCenter, "value_text", "4711");
		history(this.entity, costCenter, 5, "value_text", null);

		JsonNode preview = ok(preview(architect(), this.entity, 3));
		assertThat(reason(preview, costCenter)).isEqualTo("not-in-source");

		ok(restore(architect(), this.entity, "\"5\"", 3, null));
		assertThat(text(this.entity, costCenter)).isEqualTo("4711");
	}

	@Test
	void warningsAreKeyedByProperty() {
		assertThat(ok(preview(architect(), this.entity, 3)).path("warnings"))
			.extracting((warning) -> warning.path("field").asString())
			.containsExactly(key(this.contact));
	}

	// us-104-partial-restore: Connections in the preview

	@Test
	void previewListsChangedAndAddedConnections() {
		UUID flow = metatype("Flow", true);
		int other = entity(this.metatype, "ERP", 1);
		// the target version 3 is at START + 3 hours: one second later counts, the same instant does not
		Instant target = START.plus(Duration.ofHours(3));
		connection(flow, "CRM to ERP", this.entity, other, START, target.plusSeconds(1));
		connection(flow, "CRM to Mail", this.entity, other, START, target);
		connection(flow, "CRM to Wiki", this.entity, other, START);
		connection(flow, "ERP to CRM", other, this.entity, target.plusSeconds(3600));

		JsonNode connections = ok(preview(architect(), this.entity, 3)).path("connections");

		assertThat(connections).extracting((connection) -> connection.path("name").asString() + " "
				+ connection.path("change").asString() + " " + connection.path("direction").asString() + " "
				+ connection.path("otherEndpoint").asString())
			.containsExactly("ERP to CRM added incoming ERP", "CRM to ERP changed outgoing ERP");
	}

	private MvcTestResult partial(TestSession session, List<String> fields, String reason) {
		Map<String, Object> body = new java.util.LinkedHashMap<>();
		body.put("targetVersion", 3);
		body.put("fields", fields);
		if (reason != null) {
			body.put("reason", reason);
		}
		return this.mvc.post()
			.uri("/api/v1/entities/" + this.entity + "/restore")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.header("If-Match", "\"5\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content(JsonMapper.shared().writeValueAsString(body))
			.exchange();
	}

	/** A connection with one version per given time. */
	private int connection(UUID type, String name, int source, int target, Instant... versions) {
		Integer id = this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, version, created_by) VALUES (?, ?, ?, ?) RETURNING id",
				Integer.class, type, name, versions.length, this.creator);
		for (int version = 1; version <= versions.length; version++) {
			this.jdbc.update(
					"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, ?, ?, ?)",
					id, version, this.creator, Timestamp.from(versions[version - 1]));
		}
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private TestSession architect() {
		return this.principals.personWithIdpRole("enterprise-architect");
	}

	private MvcTestResult preview(TestSession session, int id, int target) {
		return this.mvc.get()
			.uri("/api/v1/entities/" + id + "/restore?targetVersion=" + target)
			.cookie(cookie(session))
			.exchange();
	}

	private MvcTestResult restore(TestSession session, int id, String ifMatch, int target, String reason) {
		var request = this.mvc.post()
			.uri("/api/v1/entities/" + id + "/restore")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(JsonMapper.shared()
				.writeValueAsString((reason != null) ? Map.of("targetVersion", target, "reason", reason)
						: Map.of("targetVersion", target)));
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		return request.exchange();
	}

	private MvcTestResult state(TestSession session, int version) {
		return this.mvc.get()
			.uri("/api/v1/entities/" + this.entity + "/versions/" + version)
			.cookie(cookie(session))
			.exchange();
	}

	private int version(int id) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, id);
	}

	private int headers(int id) {
		return this.jdbc.queryForObject("SELECT count(*) FROM entity_version_headers WHERE entity_id = ?", Integer.class,
				id);
	}

	private String text(int id, UUID property) {
		List<String> values = this.jdbc.queryForList(
				"SELECT value_text FROM entity_property_values WHERE entity_id = ? AND property_def_id = ?", String.class,
				id, property);
		return values.isEmpty() ? null : values.get(0);
	}

	private static Cookie cookie(TestSession session) {
		return new Cookie(SessionCookie.NAME, session.token());
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String key(UUID property) {
		return property.toString();
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, ?)
				RETURNING id""", UUID.class, name + " " + suffix(), connection);
	}

	private UUID property(String name, String dataType, String mode) {
		String suffix = suffix();
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				VALUES (?, ?, ?, ?) RETURNING id""", UUID.class, name + " " + suffix,
				name.toLowerCase() + "_" + suffix, dataType, mode);
	}

	private void map(UUID property, String necessity, int order) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope, sort_order)
				VALUES (?, ?, ?, DATE '2026-01-01', 'organization', ?)""", this.metatype, property, necessity, order);
	}

	/** An entity with {@code versions} version headers; version n is at START + n hours. */
	private int entity(UUID type, String name, int versions) {
		Integer id = this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, version, created_by) VALUES (?, ?, ?, ?) RETURNING id",
				Integer.class, type, name, versions, this.creator);
		for (int version = 1; version <= versions; version++) {
			this.jdbc.update(
					"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, ?, ?, ?)",
					id, version, this.creator, Timestamp.from(START.plus(Duration.ofHours(version))));
		}
		return id;
	}

	private void systemHistory(int id, int version, String field, String before, String after) {
		this.jdbc.update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (?, ?, ?, ?, ?, ?)""", id, version, field, before, after, this.creator);
	}

	private void value(int id, UUID property, String column, Object value) {
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, " + column + ") VALUES (?, ?, ?)",
				id, property, value);
	}

	private void history(int id, UUID property, int version, String column, String before) {
		this.jdbc.update("INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, " + column
				+ ", changed_by) VALUES (?, ?, ?, ?, ?)", id, property, version, before, this.creator);
	}

	private static List<String> keys(JsonNode fields) {
		List<String> keys = new ArrayList<>();
		fields.forEach((field) -> keys.add(field.path("field").asString()));
		return keys;
	}

	private static JsonNode field(JsonNode preview, String key) {
		for (JsonNode field : preview.path("fields")) {
			if (field.path("field").asString().equals(key)) {
				return field;
			}
		}
		throw new AssertionError("no field " + key + " in " + preview);
	}

	private static String reason(JsonNode preview, UUID property) {
		JsonNode field = field(preview, key(property));
		assertThat(field.path("restore").asBoolean()).isFalse();
		return field.path("reason").asString();
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
			return Files.createTempDirectory("oea-entity-restore").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

package io.oea.app.diagramview;

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
import java.math.BigDecimal;
import java.nio.file.Files;
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
import org.springframework.http.HttpMethod;
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
 * Spec diagram/diagram-entity-details (US-074, UC-10, issue #69; design D1 to D2 of
 * us-074-diagram-entity-details): the details of a shown entity, the reference targets, the entity
 * changes saved with the layout, the version check and the limits, on an own database. Kurt holds
 * {@code entity:write} and the write role of the restricted property, Anna {@code entity:write}
 * and its read role, Franz only {@code entity:read}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramEntityDetailsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final String DIAGRAMS = "/api/v1/diagrams";

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

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

	private TestSession anna;

	private TestSession franz;

	private long kurtId;

	private long annaId;

	private UUID component;

	private UUID process;

	private UUID viewpoint;

	private String diagram;

	/** Optional public string "Status" and a restricted "Investment" on the component. */
	private UUID status;

	private UUID investment;

	/** Mandatory "Process Owner" of the process. */
	private UUID owner;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.anna = this.principals.personWithIdpRole("solution-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.kurtId = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		this.annaId = personOf(this.anna);
		this.component = metatype("Application Component " + suffix(), "AC");
		this.process = metatype("Business Process " + suffix(), "BP");
		this.viewpoint = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, 'bpmn2', ?)
				RETURNING id""", UUID.class, unique("Process Model"), this.kurtId);
		for (UUID type : List.of(this.component, this.process)) {
			this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", this.viewpoint,
					type);
		}
		this.status = property("Status", "string", "public", null);
		this.investment = property("Investment", "integer", "role-restricted", null);
		this.jdbc.update("""
				INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES
				    (?, 'enterprise-architect', 'read'), (?, 'enterprise-architect', 'write'),
				    (?, 'solution-architect', 'read')""", this.investment, this.investment, this.investment);
		map(this.component, this.status, "optional");
		map(this.component, this.investment, "optional");
		this.owner = property("Process Owner", "string", "public", null);
		map(this.process, this.owner, "mandatory");
		this.diagram = diagram();
	}

	// Requirement: Read the details of a shown entity

	@Test
	void detailsOfAShownEntityWithValuesAccessAndMissingMandatory() {
		int erp = entity("SAP ERP", this.component);
		this.jdbc.update("UPDATE entities SET description = 'The ERP' WHERE id = ?", erp);
		value(erp, this.status, "active");
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES (?, ?, 5000000)",
				erp, this.investment);
		saveOk(1, shapes(shape(id(), erp, 1, 1, 90, 30)));
		JsonNode details = ok(get(erp, this.anna));
		assertThat(details.path("id").asInt()).isEqualTo(erp);
		assertThat(details.path("name").asString()).isEqualTo("SAP ERP");
		assertThat(details.path("description").asString()).isEqualTo("The ERP");
		assertThat(details.path("metatype").path("id").asString()).isEqualTo(this.component.toString());
		assertThat(details.path("metatype").path("abbreviation").asString()).isEqualTo("AC");
		assertThat(details.path("version").asInt()).isOne();
		assertThat(details.path("missingMandatory")).isEmpty();
		JsonNode values = byKey(details.path("properties"), "propertyName");
		assertThat(firstWithPrefix(values, "Status").path("valueText").asString()).isEqualTo("active");
		assertThat(firstWithPrefix(values, "Status").path("redacted").asBoolean()).isFalse();
		assertThat(firstWithPrefix(values, "Investment").path("valueInt").asLong()).isEqualTo(5000000L);
		// the write permission and the property decide: Anna reads Investment but does not write it
		JsonNode fields = byKey(details.path("fields"), "propertyDefId");
		assertThat(fields.path(this.status.toString()).path("readable").asBoolean()).isTrue();
		assertThat(fields.path(this.status.toString()).path("writable").asBoolean()).isTrue();
		assertThat(fields.path(this.investment.toString()).path("readable").asBoolean()).isTrue();
		assertThat(fields.path(this.investment.toString()).path("writable").asBoolean()).isFalse();
		assertThat(details.path("fields")).hasSize(2);
	}

	@Test
	void aReaderWithoutEntityWriteMayNotWriteAnyProperty() {
		int erp = entity("SAP ERP", this.component);
		saveOk(1, shapes(shape(id(), erp, 1, 1, 90, 30)));
		JsonNode fields = ok(get(erp, this.franz)).path("fields");
		assertThat(fields).hasSize(2);
		fields.forEach((field) -> assertThat(field.path("writable").asBoolean()).isFalse());
		assertThat(byKey(fields, "propertyDefId").path(this.status.toString()).path("readable").asBoolean()).isTrue();
	}

	@Test
	void aRestrictedValueIsRedactedForAReaderWithoutTheRoleAndShownWithIt() {
		int erp = entity("SAP ERP", this.component);
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES (?, ?, 5000000)",
				erp, this.investment);
		saveOk(1, shapes(shape(id(), erp, 1, 1, 90, 30)));
		JsonNode redacted = ok(get(erp, this.franz));
		JsonNode investment = firstWithPrefix(byKey(redacted.path("properties"), "propertyName"), "Investment");
		assertThat(investment.path("redacted").asBoolean()).isTrue();
		assertThat(investment.path("valueInt").isNull()).isTrue();
		assertThat(byKey(redacted.path("fields"), "propertyDefId").path(this.investment.toString())
			.path("readable")
			.asBoolean()).isFalse();
		JsonNode shown = ok(get(erp, this.kurt));
		assertThat(firstWithPrefix(byKey(shown.path("properties"), "propertyName"), "Investment").path("valueInt")
			.asLong()).isEqualTo(5000000L);
		// a role with the write role may write
		assertThat(byKey(shown.path("fields"), "propertyDefId").path(this.investment.toString())
			.path("writable")
			.asBoolean()).isTrue();
	}

	@Test
	void aReferenceValueCarriesTheNameAndAbbreviationOfItsTarget() {
		UUID hosting = property("Hosted on", "reference", "public", this.component);
		map(this.component, hosting, "optional");
		int linux = entity("Linux Cluster", this.component);
		int erp = entity("SAP ERP", this.component);
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_ref) VALUES (?, ?, ?)",
				erp, hosting, linux);
		saveOk(1, shapes(shape(id(), erp, 1, 1, 90, 30)));
		JsonNode ref = firstWithPrefix(byKey(ok(get(erp, this.franz)).path("properties"), "propertyName"), "Hosted on")
			.path("valueRef");
		assertThat(ref.path("id").asInt()).isEqualTo(linux);
		assertThat(ref.path("name").asString()).isEqualTo("Linux Cluster");
		assertThat(ref.path("metatypeAbbr").asString()).isEqualTo("AC");
	}

	@Test
	void anIncompleteEntityNamesItsMissingMandatoryProperties() {
		int approval = entity("Invoice Approval", this.process);
		saveOk(1, shapes(shape(id(), approval, 1, 1, 90, 30)));
		JsonNode details = ok(get(approval, this.franz));
		assertThat(details.path("missingMandatory")).hasSize(1);
		assertThat(details.path("missingMandatory").get(0).asString()).startsWith("Process Owner");
	}

	@Test
	void aConnectionScopedPropertyIsWritableOnlyForAnEntityInTheScopeOfTheCaller() {
		UUID scoping = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES (?::uuid, ?, true) RETURNING id""",
				UUID.class, INSTANCE, "Assignment " + suffix());
		UUID scoped = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, 'p_' || substr(md5(?), 1, 12), 'string', 'connection-scoped', ?) RETURNING id""", UUID.class,
				"Salary Band " + suffix(), suffix(), scoping);
		map(this.component, scoped, "optional");
		UUID personType = metatype("Person " + suffix(), "PE");
		int self = entity("Anna", personType);
		int inScope = entity("In Scope", this.component);
		int outOfScope = entity("Out of Scope", this.component);
		int link = entity("Assignment", scoping);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				link, self, inScope);
		saveOk(1, shapes(shape(id(), inScope, 1, 1, 90, 30), shape(id(), outOfScope, 2, 2, 90, 30)));
		String before = this.jdbc.queryForObject("SELECT person_metatype_id::text FROM visibility_settings WHERE id = 1",
				String.class);
		this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = ? WHERE id = 1", personType);
		this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", self, this.annaId);
		try {
			JsonNode inside = byKey(ok(get(inScope, this.anna)).path("fields"), "propertyDefId").path(scoped.toString());
			JsonNode outside = byKey(ok(get(outOfScope, this.anna)).path("fields"), "propertyDefId")
				.path(scoped.toString());
			assertThat(inside.path("writable").asBoolean()).isTrue();
			assertThat(inside.path("readable").asBoolean()).isTrue();
			assertThat(outside.path("writable").asBoolean()).isFalse();
			assertThat(outside.path("readable").asBoolean()).isFalse();
		}
		finally {
			this.jdbc.update("UPDATE persons SET entity_id = NULL WHERE id = ?", this.annaId);
			this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = ?::uuid WHERE id = 1", before);
		}
	}

	@Test
	void anEntityWithoutAShapeADeletedEntityAndUnknownDiagramsAnswer404() {
		int shown = entity("Shown", this.component);
		int hidden = entity("Hidden", this.component);
		int gone = entity("Gone", this.component);
		saveOk(1, shapes(shape(id(), shown, 1, 1, 90, 30), shape(id(), gone, 2, 2, 90, 30)));
		assertThat(status(get(shown, this.franz))).isEqualTo(200);
		assertThat(status(get(hidden, this.franz))).isEqualTo(404);
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, gone);
		assertThat(status(get(gone, this.franz))).isEqualTo(404);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + UUID.randomUUID() + "/entities/" + shown, null, this.franz,
				null)))
			.isEqualTo(404);
		String other = diagram();
		assertThat(status(call(HttpMethod.DELETE, DIAGRAMS + "/" + other, null, this.anna, "\"1\""))).isEqualTo(204);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + other + "/entities/" + shown, null, this.franz, null)))
			.isEqualTo(404);
	}

	@Test
	void theDetailsNeedEntityRead() {
		int erp = entity("SAP ERP", this.component);
		saveOk(1, shapes(shape(id(), erp, 1, 1, 90, 30)));
		TestSession nobody = this.principals.personWithoutRole();
		assertThat(status(get(erp, nobody))).isEqualTo(403);
	}

	/** entity ID, status: 0 is outside the contract, 1 passes it and is not shown. */
	@ParameterizedTest
	@CsvSource({ "0, 400", "-1, 400", "1, 404" })
	void theEntityIdOfTheDetailsHasALowerBoundOfOne(int entityId, int expected) {
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/entities/" + entityId, null, this.franz,
				null)))
			.isEqualTo(expected);
	}

	@Test
	void aValueReadThroughAnOverrideRoleIsAudited() {
		int erp = entity("SAP ERP", this.component);
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_int) VALUES (?, ?, 7)", erp,
				this.investment);
		saveOk(1, shapes(shape(id(), erp, 1, 1, 90, 30)));
		String role = "diagram-override-" + suffix();
		this.jdbc.update("INSERT INTO authorization_roles (id, name, overrides_property_visibility) VALUES (?, ?, true)",
				role, role);
		TestSession overrider = this.principals.personWithIdpRoles("viewer", role);
		int before = auditCount("property-override-access");
		JsonNode investment = firstWithPrefix(byKey(ok(get(erp, overrider)).path("properties"), "propertyName"),
				"Investment");
		assertThat(investment.path("valueInt").asLong()).isEqualTo(7L);
		assertThat(auditCount("property-override-access")).isEqualTo(before + 1);
		// a reader through a read role writes no event
		ok(get(erp, this.anna));
		assertThat(auditCount("property-override-access")).isEqualTo(before + 1);
	}

	// Requirement: Reference targets for the details

	@Test
	void referenceTargetsAreTheActiveEntitiesOfExactlyTheReferencedMetatypeFilteredByName() {
		UUID hosting = property("Hosted on", "reference", "public", this.component);
		map(this.component, hosting, "optional");
		entity("Linux Cluster", this.component);
		entity("Windows Cluster", this.component);
		int deleted = entity("Linux Retired", this.component);
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, deleted);
		entity("Linux Process", this.process);
		assertThat(names(ok(references(hosting, "Lin", this.franz)))).containsExactly("Linux Cluster");
		assertThat(names(ok(references(hosting, null, this.franz)))).containsExactly("Linux Cluster",
				"Windows Cluster");
		assertThat(names(ok(references(hosting, "LINUX c", this.franz)))).containsExactly("Linux Cluster");
	}

	@Test
	void referenceTargetsMatchWildcardsLiterally() {
		UUID hosting = property("Hosted on", "reference", "public", this.component);
		map(this.component, hosting, "optional");
		entity("100% Cloud", this.component);
		entity("1000 Cloud", this.component);
		entity("a_b", this.component);
		entity("axb", this.component);
		assertThat(names(ok(references(hosting, "0%", this.franz)))).containsExactly("100% Cloud");
		assertThat(names(ok(references(hosting, "a_b", this.franz)))).containsExactly("a_b");
	}

	@Test
	void aPropertyThatIsNoReferenceOrUnknownAnswers422NamingIt() {
		MvcTestResult notReference = references(this.status, null, this.franz);
		assertThat(status(notReference)).isEqualTo(422);
		assertThat(body(notReference).path("invalidFields").get(0).asString()).isEqualTo("propertyDefId");
		MvcTestResult unknown = references(UUID.randomUUID(), null, this.franz);
		assertThat(status(unknown)).isEqualTo(422);
		assertThat(body(unknown).path("invalidFields").get(0).asString()).isEqualTo("propertyDefId");
	}

	@Test
	void referenceTargetsOfAnUnknownDiagramAreNotFoundAndNeedEntityRead() {
		UUID hosting = property("Hosted on", "reference", "public", this.component);
		assertThat(status(call(HttpMethod.GET,
				VIEWS + "/" + UUID.randomUUID() + "/reference-targets?propertyDefId=" + hosting, null, this.franz, null)))
			.isEqualTo(404);
		assertThat(status(references(hosting, null, this.principals.personWithoutRole()))).isEqualTo(403);
	}

	/** query parameter, value, status: both sides of q 255, pageSize 1 and 200, page 0. */
	@ParameterizedTest
	@CsvSource({ "q, 255, 200", "q, 256, 400", "pageSize, 1, 200", "pageSize, 200, 200", "pageSize, 0, 400",
			"pageSize, 201, 400", "page, 0, 200", "page, -1, 400" })
	void referenceTargetLimits(String parameter, int value, int expected) {
		UUID hosting = property("Hosted on", "reference", "public", this.component);
		String argument = "q".equals(parameter) ? "a".repeat(value) : Integer.toString(value);
		MvcTestResult result = call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/reference-targets?propertyDefId="
				+ hosting + "&" + parameter + "=" + argument, null, this.franz, null);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(expected);
	}

	// Requirement: Change entities with the diagram

	@Test
	void aNameDescriptionAndValueChangeIsVersionedAndAuditedLikeABatchUpdateAndAtomicWithTheLayout() {
		int erp = entity("SAP ERP", this.component);
		String shape = id();
		saveOk(1, shapes(shape(shape, erp, 1, 1, 90, 30)));
		ObjectNode body = shapes(shape(shape, erp, 50, 60, 90, 30));
		update(body, erp, 1).put("name", "SAP S/4").put("description", "Renamed ERP").withArray("properties")
			.add(prop(this.status, "retiring"));
		JsonNode view = saveOk(2, body);
		assertThat(view.path("diagram").path("version").asInt()).isEqualTo(3);
		assertThat(view.path("shapes").get(0).path("entity").path("name").asString()).isEqualTo("SAP S/4");
		assertThat(view.path("shapes").get(0).path("x").decimalValue()).isEqualByComparingTo("50");
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, erp)).isEqualTo(2);
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, erp))
			.isEqualTo("Renamed ERP");
		assertThat(this.jdbc.queryForObject(
				"SELECT value_text FROM entity_property_values WHERE entity_id = ? AND property_def_id = ?", String.class,
				erp, this.status))
			.isEqualTo("retiring");
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM entity_version_headers WHERE entity_id = ? AND version = 2", Integer.class, erp))
			.isOne();
		// the audit event has the details of a batch update
		JsonNode event = auditDetails("entity.updated", erp);
		assertThat(event.path("version").asInt()).isEqualTo(2);
		assertThat(event.path("batchId").asString()).isNotBlank();
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.event_changes c JOIN audit.events e ON e.id = c.event_id
				WHERE e.event_type = 'entity.updated' AND e.entity_id = ?""", Integer.class, erp)).isEqualTo(3);
		// a batch update has the same detail keys
		String externalId = "batch-" + suffix();
		this.jdbc.update("UPDATE entities SET external_id = ? WHERE id = ?", externalId, erp);
		ObjectNode batch = JsonMapper.shared().createObjectNode().put("strategy", "upsert").put("atomic", true);
		batch.putArray("entities")
			.addObject()
			.put("name", "SAP S/4 B")
			.put("externalId", externalId)
			.put("metatypeId", this.component.toString());
		assertThat(status(call(HttpMethod.POST, "/api/v1/entities/batch", batch, this.anna, null))).isEqualTo(200);
		// the batch entity has an external ID, which the canvas entity had not
		assertThat(fieldNames(auditDetails("entity.updated", erp))).containsExactlyElementsOf(
				java.util.stream.Stream.concat(fieldNames(event).stream(), java.util.stream.Stream.of("externalId"))
					.sorted()
					.toList());
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isEqualTo(2);
	}

	@Test
	void anUpdateWithoutAnEffectiveChangeWritesNoVersionAndNoAudit() {
		int erp = entity("SAP ERP", this.component);
		String shape = id();
		saveOk(1, shapes(shape(shape, erp, 1, 1, 90, 30)));
		ObjectNode body = shapes(shape(shape, erp, 1, 1, 90, 30));
		update(body, erp, 1).put("name", "SAP ERP");
		saveOk(2, body);
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, erp)).isOne();
		assertThat(auditCount("entity.updated", erp)).isZero();
		assertThat(version(this.diagram)).isEqualTo(3);
	}

	@Test
	void anIncompleteEntityMustBeCompletedWhenItIsChangedAndCompletingRemovesTheMark() {
		int approval = entity("Invoice Approval", this.process);
		String shape = id();
		saveOk(1, shapes(shape(shape, approval, 1, 1, 90, 30)));
		ObjectNode body = shapes(shape(shape, approval, 20, 20, 90, 30));
		update(body, approval, 1).put("description", "Only a description");
		MvcTestResult rejected = save(2, body, this.anna);
		assertThat(status(rejected)).as(() -> content(rejected)).isEqualTo(422);
		assertThat(body(rejected).path("errors").get(0).path("field").asString())
			.isEqualTo("entityUpdates[0].properties");
		assertThat(body(rejected).path("errors").get(0).path("code").asString()).isEqualTo("MANDATORY_MISSING");
		// nothing changed, the layout included
		assertThat(version(this.diagram)).isEqualTo(2);
		assertThat(view().path("shapes").get(0).path("x").decimalValue()).isEqualByComparingTo("1");
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, approval)).isOne();
		// completing it
		ObjectNode completing = shapes(shape(shape, approval, 20, 20, 90, 30));
		update(completing, approval, 1).withArray("properties").add(prop(this.owner, "Anna"));
		JsonNode view = saveOk(2, completing);
		assertThat(view.path("shapes").get(0).path("missingMandatory")).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, approval))
			.isEqualTo(2);
		assertThat(auditCount("entity.updated", approval)).isOne();
	}

	@Test
	void aNewEntityIsCreatedWithItsDescriptionAndValuesAndIsNotIncomplete() {
		String key = id();
		ObjectNode body = shapes(newShape(id(), key, 1, 1, 90, 30));
		ObjectNode created = newEntity(key, this.process, "Invoice Approval").put("description", "Approves invoices");
		created.putArray("properties").add(prop(this.owner, "Anna"));
		body.withArray("newEntities").add(created);
		JsonNode shape = saveOk(1, body).path("shapes").get(0);
		int id = shape.path("entity").path("id").asInt();
		assertThat(shape.path("missingMandatory")).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, id))
			.isEqualTo("Approves invoices");
		assertThat(this.jdbc.queryForObject("SELECT value_text FROM entity_property_values WHERE entity_id = ?",
				String.class, id))
			.isEqualTo("Anna");
	}

	@Test
	void anInvalidValueOfANewEntityAnswers422OnItsPathAndChangesNothing() {
		String key = id();
		ObjectNode body = shapes(newShape(id(), key, 1, 1, 90, 30));
		ObjectNode created = newEntity(key, this.component, "Broken Value");
		created.putArray("properties").add(prop(this.investment, "not a number"));
		body.withArray("newEntities").add(created);
		MvcTestResult result = save(1, body, this.kurt);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		assertThat(body(result).path("errors").get(0).path("field").asString())
			.isEqualTo("newEntities[0].properties[0].value");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = 'Broken Value'", Integer.class))
			.isZero();
		assertThat(version(this.diagram)).isOne();
	}

	@Test
	void aRestrictedPropertyWithoutTheWriteRoleAnswers403ForAnUpdateAndANewEntityAndWithItIsWritten() {
		int erp = entity("SAP ERP", this.component);
		String shape = id();
		saveOk(1, shapes(shape(shape, erp, 1, 1, 90, 30)));
		ObjectNode body = shapes(shape(shape, erp, 1, 1, 90, 30));
		update(body, erp, 1).withArray("properties").add(prop(this.investment, "42"));
		int denied = auditCount("authz.denied");
		assertThat(status(save(2, body, this.anna))).isEqualTo(403);
		assertThat(auditCount("authz.denied")).isEqualTo(denied + 1);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_property_values WHERE entity_id = ?",
				Integer.class, erp))
			.isZero();
		assertThat(version(this.diagram)).isEqualTo(2);
		// a new entity
		String key = id();
		ObjectNode fresh = shapes(shape(shape, erp, 1, 1, 90, 30), newShape(id(), key, 5, 5, 90, 30));
		ObjectNode created = newEntity(key, this.component, "Restricted New");
		created.putArray("properties").add(prop(this.investment, "42"));
		fresh.withArray("newEntities").add(created);
		assertThat(status(save(2, fresh, this.anna))).isEqualTo(403);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = 'Restricted New'", Integer.class))
			.isZero();
		// the write role
		assertThat(status(save(2, body, this.kurt))).isEqualTo(200);
		assertThat(this.jdbc.queryForObject("SELECT value_int FROM entity_property_values WHERE entity_id = ?",
				Integer.class, erp))
			.isEqualTo(42);
	}

	@Test
	void anEntityWithAnOpenIssueAnswers409NamingItAndChangesNothing() {
		int erp = entity("SAP ERP", this.component);
		String shape = id();
		saveOk(1, shapes(shape(shape, erp, 1, 1, 90, 30)));
		block(erp);
		ObjectNode body = shapes(shape(shape, erp, 9, 9, 90, 30));
		update(body, erp, 1).put("description", "Blocked change");
		MvcTestResult result = save(2, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(409);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:entity-blocked");
		assertThat(problem.path("entities").get(0).path("entityId").asInt()).isEqualTo(erp);
		assertThat(problem.path("entities").get(0).path("issues")).hasSize(1);
		assertThat(version(this.diagram)).isEqualTo(2);
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, erp)).isOne();
	}

	@Test
	void anEntityWithoutAShapeInTheRequestAndAnEntityNamedTwiceAnswer422() {
		int shown = entity("Shown", this.component);
		int other = entity("Other", this.component);
		ObjectNode body = shapes(shape(id(), shown, 1, 1, 90, 30));
		update(body, other, 1).put("description", "x");
		update(body, shown, 1).put("description", "a");
		update(body, shown, 1).put("description", "b");
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		assertThat(body(result).path("invalidFields").toString()).contains("entityUpdates[0].entityId")
			.contains("entityUpdates[2].entityId")
			.doesNotContain("entityUpdates[1]");
		assertThat(body(result).toString()).contains("ENTITY_NOT_SHOWN").contains("DUPLICATE_ENTITY");
		assertThat(version(this.diagram)).isOne();
	}

	@Test
	void aDeletedEntityNamedByARequestAnswers422() {
		int gone = entity("Gone", this.component);
		String shape = id();
		saveOk(1, shapes(shape(shape, gone, 1, 1, 90, 30)));
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, gone);
		ObjectNode body = shapes(shape(shape, gone, 1, 1, 90, 30));
		update(body, gone, 1).put("description", "x");
		MvcTestResult result = save(2, body, this.anna);
		assertThat(status(result)).isEqualTo(422);
		assertThat(version(this.diagram)).isEqualTo(2);
	}

	// Requirement: Entity version check for interactive edits

	@Test
	void aStaleVersionAnswers409NamingEveryStaleEntityAndTheCurrentOneIsAccepted() {
		int erp = entity("SAP ERP", this.component);
		int crm = entity("Salesforce", this.component);
		int hr = entity("Workday", this.component);
		String first = id();
		String second = id();
		String third = id();
		saveOk(1, shapes(shape(first, erp, 1, 1, 90, 30), shape(second, crm, 2, 2, 90, 30),
				shape(third, hr, 3, 3, 90, 30)));
		// ERP and Workday changed meanwhile
		this.jdbc.update("UPDATE entities SET version = 5 WHERE id = ?", erp);
		this.jdbc.update("UPDATE entities SET version = 2 WHERE id = ?", hr);
		ObjectNode body = shapes(shape(first, erp, 9, 9, 90, 30), shape(second, crm, 2, 2, 90, 30),
				shape(third, hr, 3, 3, 90, 30));
		update(body, erp, 4).put("description", "mine");
		update(body, crm, 1).put("description", "mine");
		update(body, hr, 1).put("description", "mine");
		MvcTestResult result = save(2, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(409);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:entity-version-conflict");
		assertThat(problem.path("conflicts")).hasSize(2);
		assertThat(problem.path("conflicts").get(0).path("entityId").asInt()).isEqualTo(erp);
		assertThat(problem.path("conflicts").get(0).path("currentVersion").asInt()).isEqualTo(5);
		assertThat(problem.path("conflicts").get(1).path("entityId").asInt()).isEqualTo(hr);
		assertThat(problem.path("conflicts").get(1).path("currentVersion").asInt()).isEqualTo(2);
		assertThat(problem.path("invalidFields").toString()).contains("entityUpdates[0]").contains("entityUpdates[2]");
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, crm)).isEmpty();
		assertThat(version(this.diagram)).isEqualTo(2);
		// the expected version equal to the current one is accepted
		ObjectNode accepted = shapes(shape(first, erp, 9, 9, 90, 30));
		update(accepted, erp, 5).put("description", "mine");
		saveOk(2, accepted);
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, erp)).isEqualTo(6);
	}

	@Test
	void theBatchStillWritesWithoutAVersion() {
		int erp = entity("SAP ERP", this.component);
		String externalId = "batch-" + suffix();
		this.jdbc.update("UPDATE entities SET external_id = ?, version = 5 WHERE id = ?", externalId, erp);
		ObjectNode batch = JsonMapper.shared().createObjectNode().put("strategy", "upsert").put("atomic", true);
		batch.putArray("entities")
			.addObject()
			.put("name", "SAP ERP 2")
			.put("externalId", externalId)
			.put("metatypeId", this.component.toString());
		assertThat(status(call(HttpMethod.POST, "/api/v1/entities/batch", batch, this.anna, null))).isEqualTo(200);
		assertThat(this.jdbc.queryForObject("SELECT name FROM entities WHERE id = ?", String.class, erp))
			.isEqualTo("SAP ERP 2");
	}

	// Limits

	/** updates, status */
	@ParameterizedTest
	@CsvSource({ "500, 200", "501, 400" })
	void updateCountLimit(int count, int expected) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		for (int i = 0; i < count; i++) {
			int entity = entity("Counted " + i, this.component);
			shapes.add(shape(id(), entity, i, 0, 90, 30));
			update(body, entity, 1).put("description", "d");
		}
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
			.isEqualTo(expected);
		assertThat(view().path("shapes")).hasSize((expected == 200) ? count : 0);
	}

	/** field, length, status */
	@ParameterizedTest
	@CsvSource({ "name, 255, 200", "name, 256, 400", "name, 1, 200", "description, 2000, 200",
			"description, 2001, 400" })
	void nameAndDescriptionLimitsOfAnUpdate(String field, int length, int expected) {
		int erp = entity("SAP ERP", this.component);
		ObjectNode body = shapes(shape(id(), erp, 1, 1, 90, 30));
		update(body, erp, 1).put(field, "n".repeat(length));
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(expected);
	}

	@Test
	void aBlankNameOfAnUpdateAnswers422() {
		int erp = entity("SAP ERP", this.component);
		ObjectNode body = shapes(shape(id(), erp, 1, 1, 90, 30));
		update(body, erp, 1).put("name", "   ");
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		assertThat(body(result).path("errors").get(0).path("field").asString()).isEqualTo("entityUpdates[0].name");
		assertThat(body(result).path("errors").get(0).path("code").asString()).isEqualTo("REQUIRED");
	}

	/** description length, status */
	@ParameterizedTest
	@CsvSource({ "2000, 200", "2001, 400" })
	void descriptionLimitOfANewEntity(int length, int expected) {
		String key = id();
		ObjectNode body = shapes(newShape(id(), key, 1, 1, 90, 30));
		body.withArray("newEntities")
			.add(newEntity(key, this.component, "Described").put("description", "d".repeat(length)));
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(expected);
	}

	/** expected version, entity ID, status: both lower bounds of the contract are 1. */
	@ParameterizedTest
	@CsvSource({ "1, 1, 422", "0, 5, 400", "1, 0, 400" })
	void expectedVersionAndEntityIdHaveALowerBoundOfOne(int version, int entityId, int expected) {
		entity("Filler", this.component);
		int erp = entity("SAP ERP", this.component);
		ObjectNode body = shapes(shape(id(), erp, 1, 1, 90, 30));
		body.withArray("entityUpdates").addObject().put("entityId", entityId).put("expectedVersion", version);
		MvcTestResult result = save(1, body, this.anna);
		// entity 1 passes the contract and is not shown by a shape of the request
		assertThat(status(result)).as(() -> content(result)).isEqualTo(expected);
		if (expected == 422) {
			assertThat(body(result).path("errors").get(0).path("code").asString()).isEqualTo("ENTITY_NOT_SHOWN");
		}
	}

	@Test
	void theLargestSaveWithEntityChangesIsAcceptedAndOneAboveFourMegabytesAnswers413() {
		UUID wide = metatype("Wide " + suffix(), "WI");
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", this.viewpoint, wide);
		List<UUID> properties = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			UUID property = property("Wide " + i, "string", "public", null);
			map(wide, property, "optional");
			properties.add(property);
		}
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		for (int i = 0; i < 500; i++) {
			int entity = entity("Wide " + i, wide);
			shapes.add(shape(id(), entity, i, 0, 90, 30));
			ObjectNode update = update(body, entity, 1).put("name", "N".repeat(255))
				.put("description", "d".repeat(2000));
			for (UUID property : properties) {
				update.withArray("properties").add(prop(property, "v".repeat(50)));
			}
		}
		String text = body.toString();
		assertThat(text.length()).isGreaterThan(1024 * 1024).isLessThan(2 * 1024 * 1024);
		MvcTestResult accepted = save(1, body, this.anna);
		assertThat(status(accepted)).as(() -> content(accepted).substring(0, Math.min(300, content(accepted).length())))
			.isEqualTo(200);
		MvcTestResult tooLarge = this.mvc.put()
			.uri(VIEWS + "/" + this.diagram)
			.cookie(new Cookie(SessionCookie.NAME, this.anna.token()))
			.header(CsrfOriginFilter.HEADER, this.anna.csrfToken())
			.header("If-Match", "\"2\"")
			.contentType("application/json")
			.content(text + " ".repeat(4 * 1024 * 1024 - text.length() + 1))
			.exchange();
		assertThat(status(tooLarge)).isEqualTo(413);
	}

	// helpers

	private MvcTestResult get(int entityId, TestSession session) {
		return call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/entities/" + entityId, null, session, null);
	}

	private MvcTestResult references(UUID property, String q, TestSession session) {
		String query = "propertyDefId=" + property
				+ ((q != null) ? "&q=" + q.replace("%", "%25").replace(" ", "%20") : "");
		return call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/reference-targets?" + query, null, session, null);
	}

	private static JsonNode byKey(JsonNode array, String key) {
		ObjectNode map = JsonMapper.shared().createObjectNode();
		array.forEach((item) -> map.set(item.path(key).asString(), item));
		return map;
	}

	/** The entry of the map whose key starts with the prefix (property names carry a unique suffix). */
	private static JsonNode firstWithPrefix(JsonNode map, String prefix) {
		for (String name : map.propertyNames()) {
			if (name.startsWith(prefix)) {
				return map.path(name);
			}
		}
		throw new AssertionError("no " + prefix + " in " + map);
	}

	private ObjectNode shapes(ObjectNode... shapes) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode array = body.putArray("shapes");
		for (ObjectNode shape : shapes) {
			array.add(shape);
		}
		return body;
	}

	/** Adds an entity change to the body and returns it. */
	private static ObjectNode update(ObjectNode body, int entityId, int version) {
		return body.withArray("entityUpdates").addObject().put("entityId", entityId).put("expectedVersion", version);
	}

	private static ObjectNode prop(UUID property, String value) {
		return JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()).put("value", value);
	}

	private static ObjectNode shape(String id, int entityId, double x, double y, double width, double height) {
		return geometry(JsonMapper.shared().createObjectNode().put("id", id).put("entityId", entityId), x, y, width,
				height);
	}

	private static ObjectNode newShape(String id, String key, double x, double y, double width, double height) {
		return geometry(JsonMapper.shared().createObjectNode().put("id", id).put("newEntityKey", key), x, y, width,
				height);
	}

	private static ObjectNode geometry(ObjectNode shape, double x, double y, double width, double height) {
		return shape.put("x", BigDecimal.valueOf(x))
			.put("y", BigDecimal.valueOf(y))
			.put("width", BigDecimal.valueOf(width))
			.put("height", BigDecimal.valueOf(height));
	}

	private static ObjectNode newEntity(String key, UUID metatype, String name) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("key", key)
			.put("metatypeId", metatype.toString())
			.put("name", name);
	}

	private MvcTestResult save(int version, ObjectNode body, TestSession session) {
		return call(HttpMethod.PUT, VIEWS + "/" + this.diagram, body, session, "\"" + version + "\"");
	}

	private JsonNode saveOk(int version, ObjectNode body) {
		return ok(save(version, body, this.anna));
	}

	private JsonNode view() {
		return ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram, null, this.franz, null));
	}

	private UUID metatype(String name, String abbreviation) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, abbreviation) VALUES (?::uuid, ?, ?) RETURNING id""", UUID.class,
				INSTANCE, name, abbreviation);
	}

	private UUID property(String name, String dataType, String mode, UUID refMetatype) {
		String unique = name + " " + suffix();
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, ref_metatype_id)
				VALUES (?, 'p_' || substr(md5(?), 1, 12), ?, ?, ?) RETURNING id""", UUID.class, unique, unique, dataType,
				mode, refMetatype);
	}

	private void map(UUID metatype, UUID property, String necessity) {
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, ?, DATE '2026-01-01', 'organization')""", metatype, property, necessity);
	}

	private int entity(String name, UUID metatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class,
				metatype, name, this.kurtId);
	}

	private void value(int entity, UUID property, String text) {
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, ?)",
				entity, property, text);
	}

	/** An unresolved open issue of a solution blocks the entity (US-153). */
	private void block(int entity) {
		UUID solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES (gen_random_uuid(), ?, ?, ?, 'draft')
				RETURNING id""", UUID.class, unique("Blocking"), this.kurtId, this.kurtId);
		UUID gone = property("Gone", "string", "public", null);
		this.jdbc.update("""
				INSERT INTO solution_open_issues (id, solution_id, entity_id, field, property_def_id, kind, kept_value)
				VALUES (gen_random_uuid(), ?, ?, ?, ?, 'not-in-metamodel', 'kept')""", solution, entity, gone.toString(),
				gone);
	}

	private String diagram() {
		MvcTestResult result = call(HttpMethod.POST, DIAGRAMS,
				JsonMapper.shared()
					.createObjectNode()
					.put("name", unique("Order to Cash"))
					.put("viewpointId", this.viewpoint.toString()),
				this.anna, null);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(201);
		return body(result).path("id").asString();
	}

	private long personOf(TestSession session) {
		return this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
	}

	private int version(String id) {
		return this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, id);
	}

	private int auditCount(String type) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ?", Integer.class, type);
	}

	private int auditCount(String type, Object target) {
		String column = (target instanceof Integer) ? "entity_id" : "target_id";
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ? AND " + column + " = ?",
				Integer.class, type, (target instanceof Integer) ? target : target.toString());
	}

	private JsonNode auditDetails(String type, int entity) {
		String json = this.jdbc.queryForObject("SELECT details::text FROM audit.events WHERE event_type = ? AND "
				+ "entity_id = ? ORDER BY id DESC LIMIT 1", String.class, type, entity);
		return JsonMapper.shared().readTree(json);
	}

	private static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.propertyNames().forEach(names::add);
		return names.stream().sorted().toList();
	}

	private static List<String> names(JsonNode page) {
		List<String> names = new ArrayList<>();
		page.path("items").forEach((item) -> names.add(item.path("name").asString()));
		return names;
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		return body(result);
	}

	private static int status(MvcTestResult result) {
		return result.getResponse().getStatus();
	}

	private MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session, String ifMatch) {
		var request = this.mvc.method(method)
			.uri(java.net.URI.create(path.replace(" ", "%20")))
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
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

	private static String id() {
		return UUID.randomUUID().toString();
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String unique(String name) {
		return name + " " + suffix();
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-diagram-entity-details").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

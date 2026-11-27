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
import org.junit.jupiter.params.provider.ValueSource;
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
 * Specs diagram/diagram-viewpoint-change and metamodel/viewpoints (US-074, UC-10, UC-12; design D1 to D4 of
 * us-074-diagram-viewpoint-change): the change of the viewpoint, the computed conflicts, the blocked save
 * and the count of the diagrams a viewpoint edit affects, on an own database. Kurt holds
 * {@code entity:write} and {@code viewpoint:manage}, Anna {@code entity:write}, Franz only
 * {@code entity:read}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramViewpointChangeTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String DIAGRAMS = "/api/v1/diagrams";

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final String VIEWPOINTS = "/api/v1/admin/viewpoints";

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

	private UUID component;

	private UUID data;

	private UUID saas;

	private UUID flow;

	private UUID dataFlow;

	/** "Landscape" allows everything, "Integration" only the component and the flow, "Plain" no data flow. */
	private UUID landscape;

	private UUID integration;

	private UUID plain;

	private String diagram;

	private int erp;

	private int crm;

	private int db;

	private int saasApp;

	private int flowConnection;

	private int dataConnection;

	private String erpShape;

	private String crmShape;

	private String dbShape;

	private String saasShape;

	private String flowEdge;

	private String dataEdge;

	/**
	 * A diagram of "Landscape" with four shapes (component, component, data object, SaaS application) and two
	 * edges (flow between the components, data flow from the first component to the data object), saved at
	 * version 2.
	 */
	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.anna = this.principals.personWithIdpRole("solution-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.kurtId = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		this.component = metatype("Application Component " + suffix(), "AC", false);
		this.data = metatype("Data Object " + suffix(), "DO", false);
		this.saas = metatype("SaaS Application " + suffix(), "SA", false);
		this.flow = metatype("Flow " + suffix(), "FL", true);
		this.dataFlow = metatype("Data Flow " + suffix(), "DF", true);
		this.landscape = viewpoint("Landscape", List.of(this.component, this.data, this.saas),
				List.of(this.flow, this.dataFlow));
		this.integration = viewpoint("Integration", List.of(this.component), List.of(this.flow));
		this.plain = viewpoint("Plain", List.of(this.component, this.data, this.saas), List.of(this.flow));
		this.diagram = diagram(unique("Order to Cash"), this.landscape);
		this.erp = entity("SAP ERP", this.component);
		this.crm = entity("CRM", this.component);
		this.db = entity("Customer DB", this.data);
		this.saasApp = entity("Salesforce", this.saas);
		this.flowConnection = connection(this.flow, this.erp, this.crm);
		this.dataConnection = connection(this.dataFlow, this.erp, this.db);
		this.erpShape = id();
		this.crmShape = id();
		this.dbShape = id();
		this.saasShape = id();
		this.flowEdge = id();
		this.dataEdge = id();
		ObjectNode body = layout(true, true, true);
		MvcTestResult saved = save(this.diagram, 1, body, this.anna);
		assertThat(status(saved)).as(() -> content(saved)).isEqualTo(200);
	}

	// Requirement: Change the viewpoint of a diagram

	@Test
	void theViewpointIsChangedAtOnceAndEveryShapeEdgeAndLineIsKept() {
		int auditBefore = auditCount("diagram.viewpoint-changed", this.diagram);
		MvcTestResult result = change(this.diagram, 2, this.integration, this.anna);
		JsonNode changed = ok(result);
		assertThat(changed.path("viewpoint").path("id").asString()).isEqualTo(this.integration.toString());
		assertThat(changed.path("version").asInt()).isEqualTo(3);
		assertThat(changed.path("conflictCount").asInt()).isEqualTo(3);
		assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"3\"");
		assertThat(auditCount("diagram.viewpoint-changed", this.diagram)).isEqualTo(auditBefore + 1);
		JsonNode details = auditDetails("diagram.viewpoint-changed", this.diagram);
		assertThat(details.path("fromViewpointId").asString()).isEqualTo(this.landscape.toString());
		assertThat(details.path("toViewpointId").asString()).isEqualTo(this.integration.toString());
		assertThat(details.path("conflicts").asInt()).isEqualTo(3);
		JsonNode view = view(this.diagram);
		assertThat(view.path("shapes")).hasSize(4);
		assertThat(view.path("edges")).hasSize(2);
		assertThat(view.path("viewpoint").path("id").asString()).isEqualTo(this.integration.toString());
	}

	@Test
	void theSameViewpointChangesNothing() {
		JsonNode unchanged = ok(change(this.diagram, 2, this.landscape, this.anna));
		assertThat(unchanged.path("version").asInt()).isEqualTo(2);
		assertThat(unchanged.path("conflictCount").asInt()).isZero();
		assertThat(version(this.diagram)).isEqualTo(2);
		assertThat(auditCount("diagram.viewpoint-changed", this.diagram)).isZero();
	}

	/** the reason of the rejection */
	@ParameterizedTest
	@CsvSource({ "unknown-viewpoint, 422", "stale, 412", "no-version, 428", "reader, 403", "system-admin, 403",
			"deleted-diagram, 404", "unknown-diagram, 404" })
	void aRejectedChangeChangesNothing(String reason, int status) {
		String target = this.diagram;
		TestSession session = this.anna;
		String ifMatch = "\"2\"";
		UUID viewpoint = this.integration;
		switch (reason) {
			case "unknown-viewpoint" -> viewpoint = UUID.randomUUID();
			case "stale" -> ifMatch = "\"1\"";
			case "no-version" -> ifMatch = null;
			case "reader" -> session = this.franz;
			case "system-admin" -> session = this.principals.systemAdmin();
			case "deleted-diagram" -> this.jdbc.update("UPDATE diagrams SET deleted_at = now(), deleted_by = ? WHERE id = ?::uuid",
					this.kurtId, this.diagram);
			default -> target = id();
		}
		MvcTestResult result = call(HttpMethod.PUT, DIAGRAMS + "/" + target + "/viewpoint",
				JsonMapper.shared().createObjectNode().put("viewpointId", viewpoint.toString()), session, ifMatch);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(status);
		if (status == 422) {
			assertThat(body(result).path("invalidFields").get(0).asString()).isEqualTo("viewpointId");
		}
		assertThat(version(this.diagram)).isEqualTo(2);
		assertThat(viewpointOf(this.diagram)).isEqualTo(this.landscape);
		assertThat(auditCount("diagram.viewpoint-changed", this.diagram)).isZero();
	}

	@Test
	void aRequestWithoutAViewpointIsRejected() {
		MvcTestResult result = call(HttpMethod.PUT, DIAGRAMS + "/" + this.diagram + "/viewpoint",
				JsonMapper.shared().createObjectNode(), this.anna, "\"2\"");
		assertThat(status(result)).isEqualTo(400);
		assertThat(version(this.diagram)).isEqualTo(2);
	}

	@Test
	void updateDiagramKeepsTheViewpoint() {
		MvcTestResult result = call(HttpMethod.PUT, DIAGRAMS + "/" + this.diagram,
				JsonMapper.shared()
					.createObjectNode()
					.put("name", unique("Renamed"))
					.put("viewpointId", this.integration.toString()),
				this.anna, "\"2\"");
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		assertThat(viewpointOf(this.diagram)).isEqualTo(this.landscape);
	}

	// Requirement: Conflicts of a diagram

	@Test
	void aDiagramWithoutConflictsCarriesNoMark() {
		JsonNode view = view(this.diagram);
		assertThat(view.path("conflictCount").asInt()).isZero();
		view.path("shapes").forEach((shape) -> assertThat(shape.path("conflict").asBoolean()).isFalse());
		view.path("edges").forEach((edge) -> assertThat(edge.path("conflict").asBoolean()).isFalse());
	}

	@Test
	void shapesAndEdgesOfATypeTheViewpointDoesNotAllowAreConflicts() {
		ok(change(this.diagram, 2, this.integration, this.anna));
		JsonNode view = view(this.diagram);
		assertThat(view.path("conflictCount").asInt()).isEqualTo(3);
		assertThat(conflictShapes(view)).containsExactlyInAnyOrder(this.dbShape, this.saasShape);
		assertThat(conflictEdges(view)).containsExactly(this.dataEdge);
	}

	@Test
	void anEdgeOfATypeNotAllowedIsAConflictAndItsShapesAreNot() {
		ok(change(this.diagram, 2, this.plain, this.anna));
		JsonNode view = view(this.diagram);
		assertThat(view.path("conflictCount").asInt()).isOne();
		assertThat(conflictShapes(view)).isEmpty();
		assertThat(conflictEdges(view)).containsExactly(this.dataEdge);
	}

	@Test
	void aTypeThatIsNotExactlyAllowedIsAConflictEvenWhenAnotherTypeOfTheKindIs() {
		// the viewpoint allows the "base" component but not the "SaaS application" next to it
		ok(change(this.diagram, 2, this.integration, this.anna));
		assertThat(conflictShapes(view(this.diagram))).contains(this.saasShape).doesNotContain(this.erpShape);
	}

	@Test
	void editingTheViewpointCreatesAndResolvesConflicts() {
		JsonNode current = ok(call(HttpMethod.GET, VIEWPOINTS + "/" + this.landscape, null, this.kurt, null));
		assertThat(conflictShapes(view(this.diagram))).isEmpty();
		// remove the data object
		MvcTestResult removed = call(HttpMethod.PUT, VIEWPOINTS + "/" + this.landscape,
				write(current, List.of(this.component, this.saas)), this.kurt, "\"" + current.path("version").asInt() + "\"");
		assertThat(status(removed)).as(() -> content(removed)).isEqualTo(200);
		assertThat(conflictShapes(view(this.diagram))).containsExactly(this.dbShape);
		// allow it again
		JsonNode edited = body(removed);
		MvcTestResult added = call(HttpMethod.PUT, VIEWPOINTS + "/" + this.landscape,
				write(edited, List.of(this.component, this.saas, this.data)), this.kurt,
				"\"" + edited.path("version").asInt() + "\"");
		assertThat(status(added)).as(() -> content(added)).isEqualTo(200);
		assertThat(view(this.diagram).path("conflictCount").asInt()).isZero();
	}

	@Test
	void hiddenShapesAndEdgesAreNoConflictsUntilTheEntityIsRestored() {
		ok(change(this.diagram, 2, this.integration, this.anna));
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id IN (?, ?)", this.kurtId, this.db,
				this.saasApp);
		JsonNode hidden = view(this.diagram);
		assertThat(hidden.path("conflictCount").asInt()).isZero();
		assertThat(conflictShapes(hidden)).isEmpty();
		assertThat(conflictEdges(hidden)).isEmpty();
		this.jdbc.update("UPDATE entities SET deleted_at = NULL, deleted_by = NULL WHERE id IN (?, ?)", this.db,
				this.saasApp);
		JsonNode restored = view(this.diagram);
		assertThat(restored.path("conflictCount").asInt()).isEqualTo(3);
		assertThat(conflictShapes(restored)).containsExactlyInAnyOrder(this.dbShape, this.saasShape);
	}

	@Test
	void theCountOfTheChangeIsTheCountOfTheView() {
		for (UUID viewpoint : List.of(this.integration, this.plain, this.landscape)) {
			JsonNode changed = ok(change(this.diagram, version(this.diagram), viewpoint, this.anna));
			assertThat(changed.path("conflictCount").asInt()).isEqualTo(view(this.diagram).path("conflictCount").asInt());
		}
	}

	// Requirement: Saving only without conflicts

	/** the shape of the save that carries the conflicts */
	@ParameterizedTest
	@ValueSource(strings = { "moved shape", "no change", "entity update only", "parent change only" })
	void aSaveWithConflictsIsBlockedWhateverElseItChangesAndChangesNothing(String kind) {
		ok(change(this.diagram, 2, this.integration, this.anna));
		ObjectNode body = layout(true, true, true);
		switch (kind) {
			case "moved shape" -> ((ObjectNode) body.path("shapes").get(1)).put("x", BigDecimal.valueOf(500));
			case "entity update only" -> body.putArray("entityUpdates")
				.add(JsonMapper.shared()
					.createObjectNode()
					.put("entityId", this.erp)
					.put("expectedVersion", entityVersion(this.erp))
					.put("description", "changed"));
			case "parent change only" -> body.putArray("parentChanges")
				.add(JsonMapper.shared()
					.createObjectNode()
					.put("entityId", this.crm)
					.put("expectedVersion", entityVersion(this.crm))
					.put("parentEntityId", this.erp));
			default -> {
			}
		}
		int layoutSaved = auditCount("diagram.layout-saved", this.diagram);
		MvcTestResult result = save(this.diagram, 3, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(409);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:diagram-conflicts");
		List<String> fields = new ArrayList<>();
		body(result).path("invalidFields").forEach((field) -> fields.add(field.asString()));
		assertThat(fields).containsExactlyInAnyOrder("shapes[2]", "shapes[3]", "edges[1]");
		assertThat(version(this.diagram)).isEqualTo(3);
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isEqualTo(layoutSaved);
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, this.erp))
			.isNullOrEmpty();
		assertThat(this.jdbc.queryForObject("SELECT parent_entity_id FROM entities WHERE id = ?", Integer.class,
				this.crm))
			.isNull();
		assertThat(view(this.diagram).path("shapes").get(1).path("x").decimalValue()).isEqualByComparingTo("200");
	}

	@Test
	void allConflictsRemovedInTheSameSaveAreAccepted() {
		ok(change(this.diagram, 2, this.integration, this.anna));
		ObjectNode body = layout(false, false, false);
		((ObjectNode) body.path("shapes").get(1)).put("x", BigDecimal.valueOf(300));
		MvcTestResult result = save(this.diagram, 3, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		JsonNode saved = body(result);
		assertThat(saved.path("conflictCount").asInt()).isZero();
		assertThat(saved.path("shapes")).hasSize(2);
		assertThat(saved.path("diagram").path("version").asInt()).isEqualTo(4);
	}

	@Test
	void oneConflictLeftBlocksTheSaveAndNamesOnlyThatOne() {
		ok(change(this.diagram, 2, this.integration, this.anna));
		ObjectNode body = layout(false, true, false);
		MvcTestResult result = save(this.diagram, 3, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(409);
		assertThat(body(result).path("invalidFields")).hasSize(1);
		assertThat(body(result).path("invalidFields").get(0).asString()).isEqualTo("shapes[2]");
		assertThat(view(this.diagram).path("shapes")).hasSize(4);
		assertThat(version(this.diagram)).isEqualTo(3);
	}

	@Test
	void aNewShapeOrAnotherEntityOfATypeNotAllowedKeepsItsLayoutError() {
		ok(change(this.diagram, 2, this.integration, this.anna));
		int other = entity("Other DB", this.data);
		ObjectNode body = layout(false, false, false);
		body.withArray("shapes").add(shape(id(), other, 5, 5, 90, 30));
		MvcTestResult shape = save(this.diagram, 3, body, this.anna);
		assertThat(status(shape)).as(() -> content(shape)).isEqualTo(422);
		assertThat(body(shape).path("errors").get(0).path("code").asString()).isEqualTo("METATYPE_NOT_ALLOWED");
		assertThat(body(shape).path("errors").get(0).path("field").asString()).isEqualTo("shapes[2].entityId");
		// a stored shape that now shows another entity of a type not allowed
		ObjectNode swapped = layout(false, false, false);
		((ObjectNode) swapped.path("shapes").get(0)).put("entityId", other);
		MvcTestResult another = save(this.diagram, 3, swapped, this.anna);
		assertThat(status(another)).as(() -> content(another)).isEqualTo(422);
		assertThat(body(another).path("errors").get(0).path("code").asString()).isEqualTo("METATYPE_NOT_ALLOWED");
		assertThat(version(this.diagram)).isEqualTo(3);
	}

	@Test
	void aContainerThatIsAConflictIsRemovedWithItsNestedShapes() {
		String container = id();
		String child = id();
		String grandchild = id();
		ObjectNode body = layout(true, true, true);
		body.withArray("shapes").add(shape(container, this.db, 0, 400, 400, 300));
		body.withArray("shapes").add(shape(child, this.erp, 10, 410, 90, 30).put("parentShapeId", container));
		body.withArray("shapes").add(shape(grandchild, this.crm, 20, 420, 40, 20).put("parentShapeId", child));
		ok(save(this.diagram, 2, body, this.anna));
		ok(change(this.diagram, 3, this.integration, this.anna));
		MvcTestResult result = save(this.diagram, 4, layout(false, false, false), this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		assertThat(body(result).path("shapes")).hasSize(2);
		assertThat(body(result).path("conflictCount").asInt()).isZero();
	}

	/** which answer comes before the 409 */
	@ParameterizedTest
	@CsvSource({ "stale-version, 412", "no-version, 428", "layout-error, 422", "invalid-number, 400" })
	void theEarlierAnswersComeBeforeTheConflictOfTheSave(String reason, int status) {
		ok(change(this.diagram, 2, this.integration, this.anna));
		ObjectNode body = layout(true, true, true);
		String ifMatch = "\"3\"";
		switch (reason) {
			case "stale-version" -> ifMatch = "\"2\"";
			case "no-version" -> ifMatch = null;
			case "layout-error" -> body.withArray("shapes").add(shape(id(), 999999999, 1, 1, 90, 30));
			default -> ((ObjectNode) body.path("shapes").get(0)).put("x", new BigDecimal("1.234"));
		}
		MvcTestResult result = call(HttpMethod.PUT, VIEWS + "/" + this.diagram, body, this.anna, ifMatch);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(status);
		assertThat(version(this.diagram)).isEqualTo(3);
	}

	@Test
	void theConflictsComeBeforeTheVersionConflictOfAnEntityAndTheVisibilityOfAProperty() {
		ok(change(this.diagram, 2, this.integration, this.anna));
		UUID restricted = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				VALUES (?, 'p_' || substr(md5(?), 1, 12), 'integer', 'role-restricted') RETURNING id""", UUID.class,
				unique("Investment"), UUID.randomUUID().toString());
		this.jdbc.update("""
				INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES
				    (?, 'enterprise-architect', 'read'), (?, 'enterprise-architect', 'write'),
				    (?, 'solution-architect', 'read')""", restricted, restricted, restricted);
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", this.component, restricted);
		// a stale entity version
		ObjectNode stale = layout(true, true, true);
		stale.putArray("entityUpdates")
			.add(JsonMapper.shared()
				.createObjectNode()
				.put("entityId", this.erp)
				.put("expectedVersion", entityVersion(this.erp) + 5)
				.put("description", "changed"));
		MvcTestResult version = save(this.diagram, 3, stale, this.anna);
		assertThat(status(version)).as(() -> content(version)).isEqualTo(409);
		assertThat(body(version).path("type").asString()).isEqualTo("urn:oea:problem:diagram-conflicts");
		// a value of a property Anna may not write
		ObjectNode denied = layout(true, true, true);
		ObjectNode update = denied.putArray("entityUpdates")
			.addObject()
			.put("entityId", this.erp)
			.put("expectedVersion", entityVersion(this.erp));
		update.putArray("properties").addObject().put("propertyDefId", restricted.toString()).put("value", "42");
		MvcTestResult visibility = save(this.diagram, 3, denied, this.anna);
		assertThat(status(visibility)).as(() -> content(visibility)).isEqualTo(409);
		assertThat(body(visibility).path("type").asString()).isEqualTo("urn:oea:problem:diagram-conflicts");
		// without the conflicts the 403 applies again
		ObjectNode clean = layout(false, false, false);
		ObjectNode cleanUpdate = clean.putArray("entityUpdates")
			.addObject()
			.put("entityId", this.erp)
			.put("expectedVersion", entityVersion(this.erp));
		cleanUpdate.putArray("properties").addObject().put("propertyDefId", restricted.toString()).put("value", "42");
		assertThat(status(save(this.diagram, 3, clean, this.anna))).isEqualTo(403);
	}

	@Test
	void aReaderCannotSaveEvenWithoutConflictsAndSeesTheMarks() {
		ok(change(this.diagram, 2, this.integration, this.anna));
		assertThat(status(save(this.diagram, 3, layout(true, true, true), this.franz))).isEqualTo(403);
		assertThat(view(this.diagram).path("conflictCount").asInt()).isEqualTo(3);
	}

	// Requirement: Diagrams affected by a viewpoint edit

	@Test
	void theAffectedDiagramsAreThoseWithAShownShapeOrEdgeOfARemovedType() {
		UUID viewpoint = viewpoint("Process Model", List.of(this.component, this.data), List.of(this.flow, this.dataFlow));
		saveOk(diagram(unique("With data"), viewpoint), 1, shapes(shape(id(), this.db, 0, 0, 90, 30)));
		String withOtherData = diagram(unique("With other data"), viewpoint);
		String withDataFlow = diagram(unique("With data flow"), viewpoint);
		String plainDiagram = diagram(unique("Plain"), viewpoint);
		String deleted = diagram(unique("Deleted"), viewpoint);
		String hidden = diagram(unique("Hidden"), viewpoint);
		int secondDb = entity("Second DB", this.data);
		int hiddenDb = entity("Hidden DB", this.data);
		int link = connection(this.dataFlow, this.erp, this.crm);
		saveOk(withOtherData, 1, shapes(shape(id(), secondDb, 0, 0, 90, 30)));
		String a = id();
		String b = id();
		ObjectNode edged = shapes(shape(a, this.erp, 0, 0, 90, 30), shape(b, this.crm, 200, 0, 90, 30));
		edged.putArray("edges").add(edge(id(), link, a, b));
		saveOk(withDataFlow, 1, edged);
		saveOk(plainDiagram, 1, shapes(shape(id(), this.erp, 0, 0, 90, 30)));
		saveOk(deleted, 1, shapes(shape(id(), this.db, 0, 0, 90, 30)));
		this.jdbc.update("UPDATE diagrams SET deleted_at = now(), deleted_by = ? WHERE id = ?::uuid", this.kurtId, deleted);
		saveOk(hidden, 1, shapes(shape(id(), hiddenDb, 0, 0, 90, 30)));
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id IN (?, ?, ?)", this.kurtId,
				hiddenDb, this.db, secondDb);
		// the two shapes of data objects are hidden now: only the edge counts
		assertThat(affected(viewpoint, List.of(this.component), List.of(this.flow), this.kurt)).isOne();
		this.jdbc.update("UPDATE entities SET deleted_at = NULL, deleted_by = NULL WHERE id IN (?, ?)", this.db, secondDb);
		// removing the data object and the data flow: two diagrams with data, one with the data flow
		assertThat(affected(viewpoint, List.of(this.component), List.of(this.flow), this.kurt)).isEqualTo(3);
		// removing only the data object
		assertThat(affected(viewpoint, List.of(this.component), List.of(this.flow, this.dataFlow), this.kurt)).isEqualTo(2);
		// nothing removed
		assertThat(affected(viewpoint, List.of(this.component, this.data), List.of(this.flow, this.dataFlow), this.kurt))
			.isZero();
		// IDs that name no type are ignored
		assertThat(affected(viewpoint, List.of(this.component, UUID.randomUUID()), List.of(this.flow, UUID.randomUUID()),
				this.kurt))
			.isEqualTo(3);
	}

	@Test
	void theAffectedDiagramsNeedViewpointManageAndAKnownViewpoint() {
		String path = VIEWPOINTS + "/" + this.landscape + "/affected-diagrams/query";
		ObjectNode body = affectedBody(List.of(), List.of());
		assertThat(status(call(HttpMethod.POST, path, body, this.anna, null))).isEqualTo(403);
		assertThat(status(call(HttpMethod.POST, path, body, this.franz, null))).isEqualTo(403);
		assertThat(status(call(HttpMethod.POST, VIEWPOINTS + "/" + UUID.randomUUID() + "/affected-diagrams/query", body,
				this.kurt, null)))
			.isEqualTo(404);
		assertThat(status(call(HttpMethod.POST, path, body, this.kurt, null))).isEqualTo(200);
	}

	/** metatype IDs, connection type IDs, accepted */
	@ParameterizedTest
	@CsvSource({ "0, 0, true", "500, 500, true", "501, 0, false", "0, 501, false", "501, 501, false" })
	void theAffectedDiagramsAreLimitedTo500IdsPerList(int metatypes, int connectionTypes, boolean accepted) {
		List<UUID> types = new ArrayList<>();
		for (int i = 0; i < metatypes; i++) {
			types.add(UUID.randomUUID());
		}
		List<UUID> connections = new ArrayList<>();
		for (int i = 0; i < connectionTypes; i++) {
			connections.add(UUID.randomUUID());
		}
		MvcTestResult result = call(HttpMethod.POST, VIEWPOINTS + "/" + this.landscape + "/affected-diagrams/query",
				affectedBody(types, connections), this.kurt, null);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(accepted ? 200 : 400);
	}

	// helpers

	private ObjectNode affectedBody(List<UUID> metatypes, List<UUID> connections) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode types = body.putArray("metatypeIds");
		metatypes.forEach((id) -> types.add(id.toString()));
		ArrayNode connectionTypes = body.putArray("connectionTypeIds");
		connections.forEach((id) -> connectionTypes.add(id.toString()));
		return body;
	}

	private int affected(UUID viewpoint, List<UUID> metatypes, List<UUID> connections, TestSession session) {
		MvcTestResult result = call(HttpMethod.POST, VIEWPOINTS + "/" + viewpoint + "/affected-diagrams/query",
				affectedBody(metatypes, connections), session, null);
		return ok(result).path("count").asInt();
	}

	private ObjectNode write(JsonNode viewpoint, List<UUID> metatypes) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", viewpoint.path("name").asString());
		body.put("notation", viewpoint.path("notation").asString());
		ArrayNode types = body.putArray("metatypeIds");
		metatypes.forEach((id) -> types.add(id.toString()));
		ArrayNode connections = body.putArray("connectionTypeIds");
		viewpoint.path("allowedRelations").forEach((type) -> connections.add(type.path("id").asString()));
		return body;
	}

	private List<String> conflictShapes(JsonNode view) {
		List<String> ids = new ArrayList<>();
		view.path("shapes").forEach((shape) -> {
			if (shape.path("conflict").asBoolean()) {
				ids.add(shape.path("id").asString());
			}
		});
		return ids;
	}

	private List<String> conflictEdges(JsonNode view) {
		List<String> ids = new ArrayList<>();
		view.path("edges").forEach((edge) -> {
			if (edge.path("conflict").asBoolean()) {
				ids.add(edge.path("id").asString());
			}
		});
		return ids;
	}

	/** The full layout of the diagram with the optional data object, SaaS application and data flow. */
	private ObjectNode layout(boolean withData, boolean withSaas, boolean withDataEdge) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		shapes.add(shape(this.erpShape, this.erp, 0, 0, 90, 30));
		shapes.add(shape(this.crmShape, this.crm, 200, 0, 90, 30));
		if (withData) {
			shapes.add(shape(this.dbShape, this.db, 0, 100, 90, 30));
		}
		if (withSaas) {
			shapes.add(shape(this.saasShape, this.saasApp, 200, 100, 90, 30));
		}
		ArrayNode edges = body.putArray("edges");
		edges.add(edge(this.flowEdge, this.flowConnection, this.erpShape, this.crmShape));
		if (withDataEdge) {
			edges.add(edge(this.dataEdge, this.dataConnection, this.erpShape, this.dbShape));
		}
		return body;
	}

	private static ObjectNode edge(String id, int connection, String source, String target) {
		ObjectNode edge = JsonMapper.shared()
			.createObjectNode()
			.put("id", id)
			.put("connectionId", connection)
			.put("sourceShapeId", source)
			.put("targetShapeId", target);
		edge.putArray("points");
		return edge;
	}

	private static ObjectNode shapes(ObjectNode... shapes) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode array = body.putArray("shapes");
		for (ObjectNode shape : shapes) {
			array.add(shape);
		}
		return body;
	}

	private static ObjectNode shape(String id, int entityId, double x, double y, double width, double height) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("id", id)
			.put("entityId", entityId)
			.put("x", BigDecimal.valueOf(x))
			.put("y", BigDecimal.valueOf(y))
			.put("width", BigDecimal.valueOf(width))
			.put("height", BigDecimal.valueOf(height));
	}

	private MvcTestResult change(String diagram, int version, UUID viewpoint, TestSession session) {
		return call(HttpMethod.PUT, DIAGRAMS + "/" + diagram + "/viewpoint",
				JsonMapper.shared().createObjectNode().put("viewpointId", viewpoint.toString()), session,
				"\"" + version + "\"");
	}

	private MvcTestResult save(String diagram, int version, ObjectNode body, TestSession session) {
		return call(HttpMethod.PUT, VIEWS + "/" + diagram, body, session, "\"" + version + "\"");
	}

	private JsonNode saveOk(String diagram, int version, ObjectNode body) {
		MvcTestResult result = save(diagram, version, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		return body(result);
	}

	private JsonNode view(String diagram) {
		return ok(call(HttpMethod.GET, VIEWS + "/" + diagram, null, this.franz, null));
	}

	private UUID metatype(String name, String abbreviation, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, abbreviation, is_connection) VALUES (?::uuid, ?, ?, ?)
				RETURNING id""", UUID.class, INSTANCE, name, abbreviation, connection);
	}

	private UUID viewpoint(String name, List<UUID> metatypes, List<UUID> connections) {
		UUID id = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, 'generic', ?)
				RETURNING id""", UUID.class, unique(name), this.kurtId);
		for (UUID metatype : metatypes) {
			this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", id, metatype);
		}
		for (UUID type : connections) {
			this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?)", id, type);
		}
		return id;
	}

	private int entity(String name, UUID metatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class,
				metatype, name, this.kurtId);
	}

	private int connection(UUID type, int source, int target) {
		int id = entity("Connection " + suffix(), type);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private String diagram(String name, UUID viewpoint) {
		MvcTestResult result = call(HttpMethod.POST, DIAGRAMS,
				JsonMapper.shared().createObjectNode().put("name", name).put("viewpointId", viewpoint.toString()),
				this.anna, null);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(201);
		return body(result).path("id").asString();
	}

	private int entityVersion(int entity) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, entity);
	}

	private int version(String id) {
		return this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, id);
	}

	private UUID viewpointOf(String id) {
		return this.jdbc.queryForObject("SELECT viewpoint_id FROM diagrams WHERE id = ?::uuid", UUID.class, id);
	}

	private int auditCount(String type, String target) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ? AND target_id = ?",
				Integer.class, type, target);
	}

	private JsonNode auditDetails(String type, String target) {
		String json = this.jdbc.queryForObject("SELECT details::text FROM audit.events WHERE event_type = ? AND "
				+ "target_id = ? ORDER BY id DESC LIMIT 1", String.class, type, target);
		return JsonMapper.shared().readTree(json);
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
			return Files.createTempDirectory("oea-diagram-viewpoint-change").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

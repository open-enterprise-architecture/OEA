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
 * Spec diagram/diagram-connections (US-074 AC6, UC-10; design D1 to D4 of us-074-diagram-connections):
 * the edges of the view, the save with edges and new connections, the scoping check, the details and
 * changes of a connection shown by an edge, the search of the connections to offer and the limits,
 * on an own database. Kurt holds {@code entity:write}, Anna {@code entity:write}, Franz only
 * {@code entity:read}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramConnectionTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String DIAGRAMS = "/api/v1/diagrams";

	private static final String VIEWS = "/api/v1/views/diagrams";

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

	private UUID process;

	/** A connection metatype the viewpoint allows (component to component or process), and one it does not. */
	private UUID flow;

	private UUID foreignFlow;

	private UUID viewpoint;

	private String diagram;

	private final List<UUID> connectionTypes = new ArrayList<>();

	private int erp;

	private int crm;

	private int approval;

	private String erpShape;

	private String crmShape;

	private String approvalShape;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.anna = this.principals.personWithIdpRole("solution-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.kurtId = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		this.component = metatype("Application Component " + suffix(), false);
		this.process = metatype("Business Process " + suffix(), false);
		this.flow = connectionType("Flow " + suffix());
		this.foreignFlow = connectionType("Foreign Flow " + suffix());
		this.viewpoint = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, 'bpmn2', ?)
				RETURNING id""", UUID.class, unique("Process Model"), this.kurtId);
		for (UUID type : List.of(this.component, this.process)) {
			this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", this.viewpoint,
					type);
		}
		this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?)",
				this.viewpoint, this.flow);
		this.diagram = diagram(unique("Order to Cash"));
		this.erp = entity("SAP ERP", this.component);
		this.crm = entity("CRM Cloud", this.component);
		this.approval = entity("Invoice Approval", this.process);
		this.erpShape = id();
		this.crmShape = id();
		this.approvalShape = id();
	}

	// Requirement: Edges of a diagram

	@Test
	void aSavedEdgeIsReadWithItsConnectionShapesAndBendPoints() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		String edge = id();
		ObjectNode body = layout();
		body.withArray("edges").add(edge(edge, link, this.erpShape, this.crmShape, point(120.5, -3), point(200, 40)));
		JsonNode saved = saveOk(1, body);
		assertThat(saved.path("edges")).hasSize(1);
		JsonNode view = ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram, null, this.franz, null));
		JsonNode read = view.path("edges").get(0);
		assertThat(read.path("id").asString()).isEqualTo(edge);
		assertThat(read.path("connection").path("id").asInt()).isEqualTo(link);
		assertThat(read.path("connection").path("name").asString()).isEqualTo("SAP ERP → CRM Cloud");
		assertThat(read.path("connection").path("metatypeId").asString()).isEqualTo(this.flow.toString());
		assertThat(read.path("sourceShapeId").asString()).isEqualTo(this.erpShape);
		assertThat(read.path("targetShapeId").asString()).isEqualTo(this.crmShape);
		assertThat(read.path("points")).hasSize(2);
		assertThat(read.path("points").get(0).path("x").decimalValue()).isEqualByComparingTo("120.5");
		assertThat(read.path("points").get(0).path("y").decimalValue()).isEqualByComparingTo("-3");
		assertThat(read.path("points").get(1).path("x").decimalValue()).isEqualByComparingTo("200");
		assertThat(read.path("missingMandatory")).isEmpty();
	}

	@Test
	void aDiagramWithoutEdgesHasAnEmptyList() {
		assertThat(view().path("edges")).isEmpty();
	}

	@Test
	void aConnectionMayHaveSeveralEdges() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		String second = id();
		ObjectNode body = layout();
		ArrayNode shapes = body.withArray("shapes");
		shapes.add(shape(second, this.erp, 300, 0, 90, 30));
		body.withArray("edges")
			.add(edge(id(), link, this.erpShape, this.crmShape))
			.add(edge(id(), link, second, this.crmShape));
		JsonNode saved = saveOk(1, body);
		assertThat(saved.path("edges")).hasSize(2);
		assertThat(edgeCount()).isEqualTo(2);
	}

	@Test
	void edgesAreReplacedByTheNextSaveAndAnEdgeKeepsItsId() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		String kept = id();
		String removed = id();
		ObjectNode first = layout();
		first.withArray("edges")
			.add(edge(kept, link, this.erpShape, this.crmShape))
			.add(edge(removed, link, this.erpShape, this.crmShape));
		saveOk(1, first);
		ObjectNode second = layout();
		second.withArray("edges").add(edge(kept, link, this.erpShape, this.crmShape, point(5, 5)));
		JsonNode view = saveOk(2, second);
		assertThat(edgeIds(view)).containsExactly(kept);
		assertThat(view.path("edges").get(0).path("points")).hasSize(1);
		ObjectNode third = layout();
		assertThat(saveOk(3, third).path("edges")).isEmpty();
		// the connection stays in the repository
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE id = ? AND deleted_at IS NULL",
				Integer.class, link)).isOne();
	}

	@Test
	void anEdgeOfADeletedConnectionIsHiddenKeptOnSaveAndShownAgainAfterTheRestore() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		String edge = id();
		ObjectNode body = layout();
		body.withArray("edges").add(edge(edge, link, this.erpShape, this.crmShape));
		saveOk(1, body);
		deleteEntity(link);
		assertThat(view().path("edges")).isEmpty();
		// the client never received the hidden edge, so its save does not contain it and keeps it
		assertThat(saveOk(2, layout()).path("edges")).isEmpty();
		assertThat(edgeCount()).isOne();
		restoreEntity(link);
		assertThat(edgeIds(view())).containsExactly(edge);
	}

	@Test
	void anEdgeOfAShapeOfADeletedEntityIsHiddenAndKeptWithTheShape() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		String edge = id();
		ObjectNode body = layout();
		body.withArray("edges").add(edge(edge, link, this.erpShape, this.crmShape));
		saveOk(1, body);
		deleteEntity(this.crm);
		JsonNode view = view();
		assertThat(view.path("edges")).isEmpty();
		// the save of the client contains the visible shape only
		ObjectNode again = JsonMapper.shared().createObjectNode();
		again.putArray("shapes").add(shape(this.erpShape, this.erp, 0, 0, 90, 30));
		saveOk(2, again);
		assertThat(edgeCount()).isOne();
		restoreEntity(this.crm);
		assertThat(edgeIds(view())).containsExactly(edge);
	}

	@Test
	void removingAShapeRemovesItsEdgesAndKeepsTheConnections() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		int other = connection(this.flow, "CRM Cloud → Invoice Approval", this.crm, this.approval);
		ObjectNode body = layout();
		body.withArray("edges")
			.add(edge(id(), link, this.erpShape, this.crmShape))
			.add(edge(id(), other, this.crmShape, this.approvalShape));
		saveOk(1, body);
		ObjectNode without = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = without.putArray("shapes");
		shapes.add(shape(this.erpShape, this.erp, 0, 0, 90, 30));
		shapes.add(shape(this.approvalShape, this.approval, 200, 0, 90, 30));
		assertThat(saveOk(2, without).path("edges")).isEmpty();
		assertThat(edgeCount()).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE id IN (?, ?) AND deleted_at IS NULL",
				Integer.class, link, other)).isEqualTo(2);
	}

	@Test
	void aStoredEdgeWhoseTypeTheViewpointNoLongerAllowsIsKeptButANewOneIsRejected() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		String edge = id();
		ObjectNode body = layout();
		body.withArray("edges").add(edge(edge, link, this.erpShape, this.crmShape));
		saveOk(1, body);
		this.jdbc.update("DELETE FROM viewpoint_connection_types WHERE viewpoint_id = ?", this.viewpoint);
		ObjectNode kept = layout();
		kept.withArray("edges").add(edge(edge, link, this.erpShape, this.crmShape, point(1, 1)));
		assertThat(edgeIds(saveOk(2, kept))).containsExactly(edge);
		ObjectNode added = layout();
		added.withArray("edges")
			.add(edge(edge, link, this.erpShape, this.crmShape))
			.add(edge(id(), link, this.erpShape, this.crmShape));
		MvcTestResult rejected = save(3, added, this.anna);
		assertThat(firstField(rejected)).isEqualTo("edges[1].connectionId");
		assertThat(firstCode(rejected)).isEqualTo("METATYPE_NOT_ALLOWED");
		assertThat(edgeCount()).isOne();
	}

	// Requirement: Edges of a diagram - rejections

	/** kind, field, code */
	@ParameterizedTest
	@CsvSource({ "wrongSource, edges[0].sourceShapeId, SOURCE_SHAPE_MISMATCH",
			"wrongTarget, edges[0].targetShapeId, TARGET_SHAPE_MISMATCH",
			"reversed, edges[0].sourceShapeId, SOURCE_SHAPE_MISMATCH",
			"unknownSourceShape, edges[0].sourceShapeId, SHAPE_NOT_IN_LAYOUT",
			"unknownTargetShape, edges[0].targetShapeId, SHAPE_NOT_IN_LAYOUT",
			"typeNotAllowed, edges[0].connectionId, METATYPE_NOT_ALLOWED",
			"deletedConnection, edges[0].connectionId, CONNECTION_NOT_FOUND",
			"unknownConnection, edges[0].connectionId, CONNECTION_NOT_FOUND",
			"notAConnection, edges[0].connectionId, ENTITY_NOT_CONNECTION",
			"bothReferences, edges[0], CONNECTION_REFERENCE_INVALID",
			"noReference, edges[0], CONNECTION_REFERENCE_INVALID",
			"unknownKey, edges[0].newConnectionKey, NEW_CONNECTION_UNKNOWN",
			"duplicateEdgeId, edges[1].id, DUPLICATE_EDGE_ID",
			"edgeOfAnotherDiagram, edges[0].id, EDGE_ID_IN_USE" })
	void aFaultyEdgeIsRejectedNamingItAndNothingChanges(String kind, String field, String code) {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		int foreign = connection(this.foreignFlow, "Foreign", this.erp, this.crm);
		int gone = connection(this.flow, "Gone", this.erp, this.crm);
		deleteEntity(gone);
		ObjectNode edge = edge(id(), link, this.erpShape, this.crmShape);
		ObjectNode second = null;
		switch (kind) {
			case "wrongSource" -> edge.put("sourceShapeId", this.approvalShape);
			case "wrongTarget" -> edge.put("targetShapeId", this.approvalShape);
			case "reversed" -> edge.put("sourceShapeId", this.crmShape).put("targetShapeId", this.erpShape);
			case "unknownSourceShape" -> edge.put("sourceShapeId", id());
			case "unknownTargetShape" -> edge.put("targetShapeId", id());
			case "typeNotAllowed" -> edge.put("connectionId", foreign);
			case "deletedConnection" -> edge.put("connectionId", gone);
			case "unknownConnection" -> edge.put("connectionId", 999_999_999);
			case "notAConnection" -> edge.put("connectionId", this.erp);
			case "bothReferences" -> edge.put("newConnectionKey", id());
			case "noReference" -> edge.remove("connectionId");
			case "unknownKey" -> {
				edge.remove("connectionId");
				edge.put("newConnectionKey", id());
			}
			case "duplicateEdgeId" -> second = edge(edge.path("id").asString(), link, this.erpShape, this.crmShape);
			case "edgeOfAnotherDiagram" -> {
				String other = diagram(unique("Other Diagram"));
				String otherErp = id();
				String otherCrm = id();
				ObjectNode otherBody = JsonMapper.shared().createObjectNode();
				otherBody.putArray("shapes")
					.add(shape(otherErp, this.erp, 0, 0, 90, 30))
					.add(shape(otherCrm, this.crm, 200, 0, 90, 30));
				otherBody.putArray("edges").add(edge(edge.path("id").asString(), link, otherErp, otherCrm));
				assertThat(status(call(HttpMethod.PUT, VIEWS + "/" + other, otherBody, this.anna, "\"1\"")))
					.isEqualTo(200);
			}
			default -> throw new IllegalArgumentException(kind);
		}
		ObjectNode body = layout();
		body.withArray("edges").add(edge);
		if (second != null) {
			body.withArray("edges").add(second);
		}
		MvcTestResult result = save(1, body, this.anna);
		assertThat(firstField(result)).isEqualTo(field);
		assertThat(firstCode(result)).isEqualTo(code);
		assertThat(edgeCountOf(this.diagram)).isZero();
		assertThat(view().path("shapes")).isEmpty();
		assertThat(version(this.diagram)).isOne();
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isZero();
	}

	// Requirement: Draw a new connection

	@Test
	void aNewConnectionBetweenExistingEntitiesIsCreatedAuditedAndShownByItsEdge() {
		String key = id();
		ObjectNode body = layout();
		body.withArray("newConnections").add(newConnection(key, this.flow));
		body.withArray("edges").add(newEdge(id(), key, this.erpShape, this.crmShape, point(10, 20)));
		JsonNode view = saveOk(1, body);
		assertThat(view.path("edges")).hasSize(1);
		JsonNode edge = view.path("edges").get(0);
		int created = edge.path("connection").path("id").asInt();
		assertThat(edge.path("connection").path("name").asString()).isEqualTo("SAP ERP → CRM Cloud");
		assertThat(edge.path("connection").path("metatypeId").asString()).isEqualTo(this.flow.toString());
		assertThat(this.jdbc.queryForObject(
				"SELECT source_entity_id || '>' || target_entity_id FROM entity_connections WHERE entity_id = ?",
				String.class, created)).isEqualTo(this.erp + ">" + this.crm);
		JsonNode audit = auditDetails("entity.created", created);
		assertThat(audit.path("sourceEntityId").asInt()).isEqualTo(this.erp);
		assertThat(audit.path("targetEntityId").asInt()).isEqualTo(this.crm);
		assertThat(audit.path("batchId").asString()).isNotBlank();
		assertThat(auditDetails("diagram.layout-saved", this.diagram).path("connectionsCreated").asInt()).isOne();
	}

	@Test
	void aNewConnectionKeepsItsNameAndDescriptionAndSeveralEdgesShowIt() {
		String key = id();
		String second = id();
		ObjectNode body = layout();
		body.withArray("shapes").add(shape(second, this.erp, 300, 0, 90, 30));
		ObjectNode connection = newConnection(key, this.flow).put("name", "Order flow").put("description", "Orders");
		body.withArray("newConnections").add(connection);
		body.withArray("edges")
			.add(newEdge(id(), key, this.erpShape, this.crmShape))
			.add(newEdge(id(), key, second, this.crmShape));
		JsonNode view = saveOk(1, body);
		assertThat(view.path("edges")).hasSize(2);
		int created = view.path("edges").get(0).path("connection").path("id").asInt();
		assertThat(view.path("edges").get(1).path("connection").path("id").asInt()).isEqualTo(created);
		assertThat(view.path("edges").get(0).path("connection").path("name").asString()).isEqualTo("Order flow");
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, created))
			.isEqualTo("Orders");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_connections WHERE source_entity_id = ?",
				Integer.class, this.erp)).isOne();
	}

	@Test
	void aNewConnectionBetweenNewEntitiesIsCreatedInTheSameSave() {
		String keyA = id();
		String keyB = id();
		String shapeA = id();
		String shapeB = id();
		String connectionKey = id();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("shapes")
			.add(newShape(shapeA, keyA, 0, 0, 90, 30))
			.add(newShape(shapeB, keyB, 200, 0, 90, 30));
		body.putArray("newEntities")
			.add(newEntity(keyA, this.component, "Billing"))
			.add(newEntity(keyB, this.process, "Dunning"));
		body.putArray("newConnections").add(newConnection(connectionKey, this.flow));
		body.putArray("edges").add(newEdge(id(), connectionKey, shapeA, shapeB));
		JsonNode view = saveOk(1, body);
		assertThat(view.path("edges").get(0).path("connection").path("name").asString()).isEqualTo("Billing → Dunning");
		assertThat(auditDetails("diagram.layout-saved", this.diagram).path("entitiesCreated").asInt()).isEqualTo(2);
		assertThat(auditDetails("diagram.layout-saved", this.diagram).path("connectionsCreated").asInt()).isOne();
	}

	@Test
	void aNewConnectionWithAMissingMandatoryValueIsCreatedIncompleteAndItsEdgeNamesTheProperty() {
		String name = "Protocol " + suffix();
		UUID property = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES (?, 'p_' || substr(md5(?), 1, 12), 'string') RETURNING id""", UUID.class, name, name);
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'mandatory', DATE '2026-01-01', 'organization')""", this.flow, property);
		String key = id();
		ObjectNode body = layout();
		body.withArray("newConnections").add(newConnection(key, this.flow));
		body.withArray("edges").add(newEdge(id(), key, this.erpShape, this.crmShape));
		JsonNode edge = saveOk(1, body).path("edges").get(0);
		assertThat(edge.path("missingMandatory")).hasSize(1);
		assertThat(edge.path("missingMandatory").get(0).asString()).isEqualTo(name);
	}

	/** kind, field, code */
	@ParameterizedTest
	@CsvSource({ "sourceNotAllowed, newConnections[0], SOURCE_NOT_ALLOWED",
			"targetNotAllowed, newConnections[0], TARGET_NOT_ALLOWED",
			"typeNotAllowed, newConnections[0].metatypeId, METATYPE_NOT_ALLOWED",
			"withoutEdge, newConnections[0], NEW_CONNECTION_UNUSED",
			"duplicateKey, newConnections[1].key, DUPLICATE_KEY",
			"differentEntities, edges[1], NEW_CONNECTION_ENDPOINTS_DIFFER",
			"nameTooLong, newConnections[0].name, INVALID_VALUE" })
	void aFaultyNewConnectionIsRejectedNamingItAndNothingChanges(String kind, String field, String code) {
		UUID narrow = connectionType("Narrow " + suffix());
		this.jdbc.update("DELETE FROM metatype_connection_constraints WHERE metatype_id = ?", narrow);
		this.jdbc.update("""
				INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
				VALUES (?, 'source', ?), (?, 'target', ?)""", narrow, this.component, narrow, this.component);
		this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?)",
				this.viewpoint, narrow);
		String key = id();
		ObjectNode body = layout();
		ArrayNode connections = body.withArray("newConnections");
		ArrayNode edges = body.withArray("edges");
		switch (kind) {
			case "sourceNotAllowed" -> {
				connections.add(newConnection(key, narrow));
				edges.add(newEdge(id(), key, this.approvalShape, this.crmShape));
			}
			case "targetNotAllowed" -> {
				connections.add(newConnection(key, narrow));
				edges.add(newEdge(id(), key, this.erpShape, this.approvalShape));
			}
			case "typeNotAllowed" -> {
				connections.add(newConnection(key, this.foreignFlow));
				edges.add(newEdge(id(), key, this.erpShape, this.crmShape));
			}
			case "withoutEdge" -> connections.add(newConnection(key, this.flow));
			case "duplicateKey" -> {
				connections.add(newConnection(key, this.flow)).add(newConnection(key, this.flow));
				edges.add(newEdge(id(), key, this.erpShape, this.crmShape));
			}
			case "differentEntities" -> {
				connections.add(newConnection(key, this.flow));
				edges.add(newEdge(id(), key, this.erpShape, this.crmShape))
					.add(newEdge(id(), key, this.erpShape, this.approvalShape));
			}
			case "nameTooLong" -> {
				connections.add(newConnection(key, this.flow).put("name", "n".repeat(256)));
				edges.add(newEdge(id(), key, this.erpShape, this.crmShape));
			}
			default -> throw new IllegalArgumentException(kind);
		}
		MvcTestResult result = save(1, body, this.anna);
		if (kind.equals("nameTooLong")) {
			assertThat(status(result)).as(() -> content(result)).isEqualTo(400);
		}
		else {
			assertThat(firstField(result)).isEqualTo(field);
			assertThat(firstCode(result)).isEqualTo(code);
		}
		assertThat(edgeCount()).isZero();
		assertThat(connectionCount()).isZero();
		assertThat(view().path("shapes")).isEmpty();
		assertThat(version(this.diagram)).isOne();
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isZero();
	}

	@Test
	void aFaultyEntityOfTheSaveRollsBackTheNewConnectionsToo() {
		String key = id();
		ObjectNode body = layout();
		body.withArray("newConnections").add(newConnection(key, this.flow));
		body.withArray("edges").add(newEdge(id(), key, this.erpShape, this.crmShape));
		String entityKey = id();
		body.withArray("shapes").add(newShape(id(), entityKey, 1, 1, 90, 30));
		body.withArray("newEntities").add(newEntity(entityKey, this.component, "  "));
		assertThat(status(save(1, body, this.anna))).isEqualTo(422);
		assertThat(connectionCount()).isZero();
		assertThat(edgeCount()).isZero();
	}

	@Test
	void aScopingConnectionTypeNeedsTheScopeAssignmentBeforeEverythingElse() {
		UUID owned = connectionType("Owned by " + suffix());
		this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?)",
				this.viewpoint, owned);
		this.jdbc.update("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, ?, 'string', 'connection-scoped', ?)""", "Classification " + suffix(),
				"p_" + suffix().replace("-", ""), owned);
		String key = id();
		ObjectNode body = layout();
		body.withArray("newConnections").add(newConnection(key, owned));
		body.withArray("edges").add(newEdge(id(), key, this.erpShape, this.crmShape));
		// 403 comes before the version check and before a 422 of the layout
		assertThat(status(call(HttpMethod.PUT, VIEWS + "/" + this.diagram, body, this.anna, "\"99\""))).isEqualTo(403);
		ObjectNode faulty = layout();
		faulty.withArray("newConnections").add(newConnection(key, owned));
		faulty.withArray("edges").add(newEdge(id(), key, id(), this.crmShape));
		assertThat(status(save(1, faulty, this.anna))).isEqualTo(403);
		assertThat(connectionCount()).isZero();
		assertThat(view().path("shapes")).isEmpty();
		assertThat(version(this.diagram)).isOne();
		// with the permission the save is accepted
		TestSession assigner = this.principals.personWithIdpRoles("enterprise-architect", "security-admin");
		MvcTestResult accepted = save(1, body, assigner);
		assertThat(status(accepted)).as(() -> content(accepted)).isEqualTo(200);
		assertThat(connectionCount()).isOne();
	}

	@Test
	void anotherTypeNeedsNoScopeAssignmentAndAReaderCannotSave() {
		String key = id();
		ObjectNode body = layout();
		body.withArray("newConnections").add(newConnection(key, this.flow));
		body.withArray("edges").add(newEdge(id(), key, this.erpShape, this.crmShape));
		assertThat(status(save(1, body, this.franz))).isEqualTo(403);
		assertThat(connectionCount()).isZero();
		assertThat(status(save(1, body, this.anna))).isEqualTo(200);
	}

	// Requirement: Details of a connection shown by an edge

	@Test
	void theDetailsOfAConnectionShownByAnEdgeAreReadAndHiddenOrUnshownOnesAnswer404() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		int unshown = connection(this.flow, "Not on the diagram", this.erp, this.crm);
		ObjectNode body = layout();
		body.withArray("edges").add(edge(id(), link, this.erpShape, this.crmShape));
		saveOk(1, body);
		JsonNode details = ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/entities/" + link, null, this.franz,
				null));
		assertThat(details.path("id").asInt()).isEqualTo(link);
		assertThat(details.path("name").asString()).isEqualTo("SAP ERP → CRM Cloud");
		assertThat(details.path("metatype").path("id").asString()).isEqualTo(this.flow.toString());
		assertThat(details.path("version").asInt()).isOne();
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/entities/" + unshown, null, this.franz,
				null))).isEqualTo(404);
		deleteEntity(link);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/entities/" + link, null, this.franz,
				null))).isEqualTo(404);
		restoreEntity(link);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/entities/" + link, null, this.franz,
				null))).isEqualTo(200);
		deleteEntity(this.crm);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/entities/" + link, null, this.franz,
				null))).isEqualTo(404);
	}

	@Test
	void aConnectionShownByAnEdgeIsChangedWithTheDiagramAndOneWithoutAnEdgeIsRejected() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		int unshown = connection(this.flow, "Not on the diagram", this.erp, this.crm);
		ObjectNode body = layout();
		body.withArray("edges").add(edge(id(), link, this.erpShape, this.crmShape));
		body.withArray("entityUpdates")
			.add(JsonMapper.shared()
				.createObjectNode()
				.put("entityId", link)
				.put("expectedVersion", 1)
				.put("description", "Orders to the CRM"));
		saveOk(1, body);
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, link))
			.isEqualTo("Orders to the CRM");
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, link)).isEqualTo(2);
		assertThat(auditCount("entity.updated", String.valueOf(link))).isOne();
		// an update of a connection that no edge of the request shows
		ObjectNode other = layout();
		other.withArray("edges").add(edge(id(), link, this.erpShape, this.crmShape));
		other.withArray("entityUpdates")
			.add(JsonMapper.shared()
				.createObjectNode()
				.put("entityId", unshown)
				.put("expectedVersion", 1)
				.put("description", "x"));
		MvcTestResult rejected = save(2, other, this.anna);
		assertThat(firstField(rejected)).isEqualTo("entityUpdates[0].entityId");
		assertThat(firstCode(rejected)).isEqualTo("ENTITY_NOT_SHOWN");
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, unshown))
			.isOne();
	}

	// Requirement: Edge audit

	@Test
	void theAuditCountsEdgesAndCreatedConnectionsAndARejectedSaveIsNotAudited() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		int other = connection(this.flow, "CRM Cloud → Invoice Approval", this.crm, this.approval);
		String keep = id();
		String move = id();
		String gone = id();
		ObjectNode first = layout();
		first.withArray("edges")
			.add(edge(keep, link, this.erpShape, this.crmShape))
			.add(edge(move, other, this.crmShape, this.approvalShape))
			.add(edge(gone, link, this.erpShape, this.crmShape));
		saveOk(1, first);
		JsonNode one = auditDetails("diagram.layout-saved", this.diagram);
		assertThat(one.path("edgesAdded").asInt()).isEqualTo(3);
		assertThat(one.path("edgesChanged").asInt()).isZero();
		assertThat(one.path("edgesRemoved").asInt()).isZero();
		assertThat(one.path("connectionsCreated").asInt()).isZero();
		String key = id();
		ObjectNode second = layout();
		second.withArray("newConnections").add(newConnection(key, this.flow));
		second.withArray("edges")
			.add(edge(keep, link, this.erpShape, this.crmShape))
			.add(edge(move, other, this.crmShape, this.approvalShape, point(3, 3)))
			.add(newEdge(id(), key, this.erpShape, this.approvalShape));
		saveOk(2, second);
		JsonNode two = auditDetails("diagram.layout-saved", this.diagram);
		assertThat(two.path("edgesAdded").asInt()).isOne();
		assertThat(two.path("edgesChanged").asInt()).isOne();
		assertThat(two.path("edgesRemoved").asInt()).isOne();
		assertThat(two.path("connectionsCreated").asInt()).isOne();
		assertThat(two.path("added").asInt()).isZero();
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isEqualTo(2);
		ObjectNode faulty = layout();
		faulty.withArray("edges").add(edge(id(), link, this.crmShape, this.erpShape));
		assertThat(status(save(3, faulty, this.anna))).isEqualTo(422);
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isEqualTo(2);
	}

	// Requirement: Remove an edge and limits of edges

	/** edges, status */
	@ParameterizedTest
	@CsvSource({ "1, 200", "4000, 200", "4001, 400" })
	void theNumberOfEdgesIsLimited(int count, int status) {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		ObjectNode body = layout();
		ArrayNode edges = body.withArray("edges");
		for (int i = 0; i < count; i++) {
			edges.add(edge(id(), link, this.erpShape, this.crmShape));
		}
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
			.isEqualTo(status);
		assertThat(edgeCount()).isEqualTo((status == 200) ? count : 0);
	}

	/** bend points, status */
	@ParameterizedTest
	@CsvSource({ "0, 200", "1, 200", "50, 200", "51, 400" })
	void theBendPointsOfAnEdgeAreLimited(int count, int status) {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		ObjectNode body = layout();
		ObjectNode edge = edge(id(), link, this.erpShape, this.crmShape);
		for (int i = 0; i < count; i++) {
			((ArrayNode) edge.get("points")).add(point(i, i));
		}
		body.withArray("edges").add(edge);
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(status);
		assertThat(edgeCount()).isEqualTo((status == 200) ? 1 : 0);
	}

	/** coordinate, status */
	@ParameterizedTest
	@CsvSource({ "-100000, 200", "100000, 200", "-100000.01, 400", "100000.01, 400", "0.01, 200", "0.001, 400",
			"12.34, 200", "12.345, 400", "-0.01, 200" })
	void theBendPointCoordinatesAreLimited(String value, int status) {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		for (String axis : List.of("x", "y")) {
			ObjectNode body = layout();
			ObjectNode point = point(1, 1);
			point.put(axis, new BigDecimal(value));
			body.withArray("edges").add(edge(id(), link, this.erpShape, this.crmShape, point));
			MvcTestResult result = save(version(this.diagram), body, this.anna);
			assertThat(status(result)).as(() -> axis + " " + content(result)).isEqualTo(status);
		}
		assertThat(edgeCount()).isEqualTo((status == 200) ? 1 : 0);
	}

	/** new connections, status */
	@ParameterizedTest
	@CsvSource({ "1, 200", "500, 200", "501, 400" })
	void theNumberOfNewConnectionsIsLimited(int count, int status) {
		ObjectNode body = layout();
		ArrayNode connections = body.withArray("newConnections");
		ArrayNode edges = body.withArray("edges");
		for (int i = 0; i < count; i++) {
			String key = id();
			connections.add(newConnection(key, this.flow));
			edges.add(newEdge(id(), key, this.erpShape, this.crmShape));
		}
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
			.isEqualTo(status);
		assertThat(connectionCount()).isEqualTo((status == 200) ? count : 0);
		assertThat(edgeCount()).isEqualTo((status == 200) ? count : 0);
	}

	@Test
	void aBodyOfFourMegabytesIsAcceptedAndOneByteMoreAnswers413() {
		int link = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		ObjectNode body = layout();
		ArrayNode edges = body.withArray("edges");
		for (int i = 0; i < 4000; i++) {
			edges.add(edge(id(), link, this.erpShape, this.crmShape, point(1, 1), point(2, 2), point(3, 3), point(4, 4)));
		}
		String text = body.toString();
		int limit = 4 * 1024 * 1024;
		assertThat(text.length()).isLessThan(limit);
		MvcTestResult accepted = saveRaw(1, text + " ".repeat(limit - text.length()));
		assertThat(status(accepted)).as(() -> content(accepted).substring(0, Math.min(300, content(accepted).length())))
			.isEqualTo(200);
		assertThat(edgeCount()).isEqualTo(4000);
		MvcTestResult tooLarge = saveRaw(2, text + " ".repeat(limit - text.length() + 1));
		assertThat(status(tooLarge)).isEqualTo(413);
		assertThat(edgeCount()).isEqualTo(4000);
	}

	// Requirement: Show existing connections on request

	@Test
	void onlyConnectionsBetweenThePlacedAndTheShownEntitiesAreOffered() {
		int between = connection(this.flow, "SAP ERP → CRM Cloud", this.erp, this.crm);
		int reverse = connection(this.flow, "CRM Cloud → SAP ERP", this.crm, this.erp);
		int toApproval = connection(this.flow, "CRM Cloud → Invoice Approval", this.crm, this.approval);
		int foreign = connection(this.foreignFlow, "Foreign", this.erp, this.crm);
		int gone = connection(this.flow, "Gone", this.erp, this.crm);
		deleteEntity(gone);
		int elsewhere = entity("Elsewhere", this.component);
		connection(this.flow, "Elsewhere → SAP ERP", elsewhere, this.erp);
		JsonNode offered = ok(find(List.of(this.crm), List.of(this.erp), this.franz));
		assertThat(connectionIds(offered)).containsExactly(between, reverse);
		JsonNode first = offered.path("items").get(0);
		assertThat(first.path("connection").path("name").asString()).isEqualTo("SAP ERP → CRM Cloud");
		assertThat(first.path("connection").path("metatypeId").asString()).isEqualTo(this.flow.toString());
		assertThat(first.path("sourceEntityId").asInt()).isEqualTo(this.erp);
		assertThat(first.path("targetEntityId").asInt()).isEqualTo(this.crm);
		assertThat(offered.path("truncated").asBoolean()).isFalse();
		// two entities placed together are also joined with each other
		assertThat(connectionIds(ok(find(List.of(this.erp, this.crm), List.of(), this.franz))))
			.containsExactly(between, reverse);
		assertThat(connectionIds(ok(find(List.of(this.crm), List.of(this.erp, this.approval), this.franz))))
			.containsExactly(between, reverse, toApproval);
		assertThat(connectionIds(ok(find(List.of(this.crm), List.of(), this.franz)))).isEmpty();
		assertThat(foreign).isPositive();
	}

	@Test
	void noMatchingConnectionAnswersAnEmptyList() {
		JsonNode offered = ok(find(List.of(this.erp), List.of(this.crm), this.franz));
		assertThat(offered.path("items")).isEmpty();
		assertThat(offered.path("truncated").asBoolean()).isFalse();
	}

	/** placed, shown, status */
	@ParameterizedTest
	@CsvSource({ "1, 0, 200", "2000, 2000, 200", "0, 0, 400", "2001, 0, 400", "1, 2001, 400" })
	void theRequestOfTheOfferIsLimited(int placed, int shown, int status) {
		List<Integer> placedIds = new ArrayList<>();
		for (int i = 0; i < placed; i++) {
			placedIds.add(this.erp);
		}
		List<Integer> shownIds = new ArrayList<>();
		for (int i = 0; i < shown; i++) {
			shownIds.add(this.crm);
		}
		MvcTestResult result = find(placedIds, shownIds, this.franz);
		assertThat(status(result)).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
			.isEqualTo(status);
	}

	/** matches, items, truncated */
	@ParameterizedTest
	@CsvSource({ "4999, 4999, false", "5000, 5000, false", "5001, 5000, true" })
	void theOfferIsLimitedTo5000Connections(int matches, int items, boolean truncated) {
		this.jdbc.update("""
				WITH created AS (
				    INSERT INTO entities (metatype_id, name, created_by)
				    SELECT ?, 'Bulk ' || n, ? FROM generate_series(1, ?) n RETURNING id)
				INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id)
				SELECT id, ?, ? FROM created""", this.flow, this.kurtId, matches, this.erp, this.crm);
		JsonNode offered = ok(find(List.of(this.crm), List.of(this.erp), this.franz));
		assertThat(offered.path("items")).hasSize(items);
		assertThat(offered.path("truncated").asBoolean()).isEqualTo(truncated);
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE metatype_id = ?", this.kurtId,
				this.flow);
	}

	@Test
	void theOfferNeedsEntityReadAndAnExistingDiagram() {
		TestSession nobody = this.principals.personWithoutRole();
		assertThat(status(find(List.of(this.erp), List.of(), nobody))).isEqualTo(403);
		String unknown = UUID.randomUUID().toString();
		assertThat(status(call(HttpMethod.POST, VIEWS + "/" + unknown + "/connections/query", query(List.of(this.erp),
				List.of()), this.franz, null))).isEqualTo(404);
		String gone = diagram(unique("Old Process"));
		assertThat(status(call(HttpMethod.DELETE, DIAGRAMS + "/" + gone, null, this.anna, "\"1\""))).isEqualTo(204);
		assertThat(status(call(HttpMethod.POST, VIEWS + "/" + gone + "/connections/query", query(List.of(this.erp),
				List.of()), this.franz, null))).isEqualTo(404);
		assertThat(status(find(List.of(this.erp), List.of(), this.franz))).isEqualTo(200);
	}

	// helpers

	private ObjectNode layout() {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		shapes.add(shape(this.erpShape, this.erp, 0, 0, 90, 30));
		shapes.add(shape(this.crmShape, this.crm, 200, 0, 90, 30));
		shapes.add(shape(this.approvalShape, this.approval, 400, 0, 90, 30));
		return body;
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

	private static ObjectNode point(double x, double y) {
		return JsonMapper.shared().createObjectNode().put("x", BigDecimal.valueOf(x)).put("y", BigDecimal.valueOf(y));
	}

	private static ObjectNode edge(String id, int connectionId, String source, String target, ObjectNode... points) {
		ObjectNode edge = JsonMapper.shared()
			.createObjectNode()
			.put("id", id)
			.put("connectionId", connectionId)
			.put("sourceShapeId", source)
			.put("targetShapeId", target);
		ArrayNode array = edge.putArray("points");
		for (ObjectNode point : points) {
			array.add(point);
		}
		return edge;
	}

	private static ObjectNode newEdge(String id, String key, String source, String target, ObjectNode... points) {
		ObjectNode edge = edge(id, 1, source, target, points);
		edge.remove("connectionId");
		return edge.put("newConnectionKey", key);
	}

	private static ObjectNode newConnection(String key, UUID metatype) {
		return JsonMapper.shared().createObjectNode().put("key", key).put("metatypeId", metatype.toString());
	}

	private MvcTestResult save(int version, ObjectNode body, TestSession session) {
		return call(HttpMethod.PUT, VIEWS + "/" + this.diagram, body, session, "\"" + version + "\"");
	}

	private JsonNode saveOk(int version, ObjectNode body) {
		return ok(save(version, body, this.anna));
	}

	private MvcTestResult saveRaw(int version, String text) {
		return this.mvc.put()
			.uri(VIEWS + "/" + this.diagram)
			.cookie(new Cookie(SessionCookie.NAME, this.anna.token()))
			.header(CsrfOriginFilter.HEADER, this.anna.csrfToken())
			.header("If-Match", "\"" + version + "\"")
			.contentType("application/json")
			.content(text)
			.exchange();
	}

	private JsonNode view() {
		return ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram, null, this.franz, null));
	}

	private static ObjectNode query(List<Integer> placed, List<Integer> shown) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode entityIds = body.putArray("entityIds");
		placed.forEach(entityIds::add);
		ArrayNode shownEntityIds = body.putArray("shownEntityIds");
		shown.forEach(shownEntityIds::add);
		return body;
	}

	private MvcTestResult find(List<Integer> placed, List<Integer> shown, TestSession session) {
		return call(HttpMethod.POST, VIEWS + "/" + this.diagram + "/connections/query", query(placed, shown), session,
				null);
	}

	private static List<Integer> connectionIds(JsonNode offered) {
		List<Integer> ids = new ArrayList<>();
		offered.path("items").forEach((item) -> ids.add(item.path("connection").path("id").asInt()));
		return ids;
	}

	private static List<String> edgeIds(JsonNode view) {
		List<String> ids = new ArrayList<>();
		view.path("edges").forEach((edge) -> ids.add(edge.path("id").asString()));
		return ids;
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES (?::uuid, ?, ?) RETURNING id""", UUID.class,
				INSTANCE, name, connection);
	}

	/** A connection metatype from a component to a component or a process. */
	private UUID connectionType(String name) {
		UUID type = metatype(name, true);
		this.connectionTypes.add(type);
		this.jdbc.update("""
				INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
				VALUES (?, 'source', ?), (?, 'source', ?), (?, 'target', ?), (?, 'target', ?)""", type, this.component,
				type, this.process, type, this.component, type, this.process);
		return type;
	}

	private int entity(String name, UUID metatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class,
				metatype, name, this.kurtId);
	}

	private int connection(UUID metatype, String name, int source, int target) {
		int id = entity(name, metatype);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private void deleteEntity(int id) {
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, id);
	}

	private void restoreEntity(int id) {
		this.jdbc.update("UPDATE entities SET deleted_at = NULL, deleted_by = NULL WHERE id = ?", id);
	}

	private String diagram(String name) {
		MvcTestResult result = call(HttpMethod.POST, DIAGRAMS,
				JsonMapper.shared()
					.createObjectNode()
					.put("name", name)
					.put("viewpointId", this.viewpoint.toString()),
				this.anna, null);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(201);
		return body(result).path("id").asString();
	}

	private int version(String id) {
		return this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, id);
	}

	private int edgeCount() {
		return edgeCountOf(this.diagram);
	}

	private int edgeCountOf(String diagram) {
		return this.jdbc.queryForObject("SELECT count(*) FROM diagram_edges WHERE diagram_id = ?::uuid", Integer.class,
				diagram);
	}

	/** The connections of the metatypes of this test that are not deleted. */
	private int connectionCount() {
		return this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE deleted_at IS NULL AND metatype_id = ANY (?::uuid[])",
				Integer.class, (Object) this.connectionTypes.stream().map(UUID::toString).toArray(String[]::new));
	}

	private int auditCount(String type, String target) {
		return this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events WHERE event_type = ? AND (target_id = ? OR entity_id::text = ?)""",
				Integer.class, type, target, target);
	}

	private JsonNode auditDetails(String type, Object target) {
		String column = (target instanceof Integer) ? "entity_id" : "target_id";
		String json = this.jdbc.queryForObject("SELECT details::text FROM audit.events WHERE event_type = ? AND "
				+ column + " = ? ORDER BY id DESC LIMIT 1", String.class, type,
				(target instanceof Integer) ? target : target.toString());
		return JsonMapper.shared().readTree(json);
	}

	private static String firstField(MvcTestResult result) {
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		return body(result).path("errors").get(0).path("field").asString();
	}

	private static String firstCode(MvcTestResult result) {
		return body(result).path("errors").get(0).path("code").asString();
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
			return Files.createTempDirectory("oea-diagram-connections").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

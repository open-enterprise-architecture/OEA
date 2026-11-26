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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Spec diagram/diagram-nesting (US-074, US-135; design D1 to D3 of us-074-diagram-nesting): nested shapes,
 * the children of an entity, hierarchy lines, parent changes with the save and the setting "Ask for a new
 * parent" in the view, on an own database. Kurt holds {@code entity:write} and {@code viewpoint:manage},
 * Anna {@code entity:write}, Franz only {@code entity:read}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramNestingTests {

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

	private UUID outsider;

	private UUID composition;

	private UUID viewpoint;

	private String diagram;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.anna = this.principals.personWithIdpRole("solution-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.kurtId = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		this.component = metatype("Application Component " + suffix(), "AC", false);
		this.outsider = metatype("Technology " + suffix(), "TE", false);
		this.jdbc.update("UPDATE metatypes SET is_composition = false WHERE is_composition");
		this.composition = metatype("Composition " + suffix(), "CO", true);
		this.jdbc.update("UPDATE metatypes SET is_composition = true WHERE id = ?", this.composition);
		this.viewpoint = viewpoint(this.component);
		this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?)",
				this.viewpoint, this.composition);
		this.diagram = diagram(unique("Order to Cash"));
	}

	// Requirement: Nested shapes

	@Test
	void aNestedShapeIsSavedAndReturnedAndTheRepositoryStaysUnchanged() {
		int erp = entity("SAP ERP");
		int billing = entity("Billing");
		String container = id();
		String nested = id();
		JsonNode saved = saveOk(1, shapes(shape(container, erp, 0, 0, 300, 200),
				nested(shape(nested, billing, 10, 10, 90, 30), container)));
		assertThat(saved.path("shapes").get(0).path("parentShapeId").isNull()).isTrue();
		assertThat(saved.path("shapes").get(1).path("parentShapeId").asString()).isEqualTo(container);
		JsonNode view = view();
		assertThat(view.path("shapes").get(1).path("parentShapeId").asString()).isEqualTo(container);
		assertThat(view.path("shapes").get(1).path("x").decimalValue()).isEqualByComparingTo("10");
		assertThat(parentOf(billing)).isNull();
		assertThat(entityVersion(billing)).isOne();
		assertThat(auditCount("entity.parent-changed", billing)).isZero();
	}

	/** which kind of invalid container */
	@ParameterizedTest
	@ValueSource(strings = { "unknown", "later", "itself", "cycle" })
	void anInvalidContainerIsRejectedNamingTheShape(String kind) {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		String first = id();
		String second = id();
		ObjectNode a = shape(first, erp, 0, 0, 300, 200);
		ObjectNode b = shape(second, crm, 10, 10, 90, 30);
		String field;
		switch (kind) {
			case "unknown" -> {
				a.put("parentShapeId", id());
				field = "shapes[0].parentShapeId";
			}
			case "later" -> {
				a.put("parentShapeId", second);
				field = "shapes[0].parentShapeId";
			}
			case "itself" -> {
				b.put("parentShapeId", second);
				field = "shapes[1].parentShapeId";
			}
			default -> {
				a.put("parentShapeId", second);
				b.put("parentShapeId", first);
				field = "shapes[0].parentShapeId";
			}
		}
		MvcTestResult result = save(1, shapes(a, b), this.anna);
		assertThat(firstField(result)).isEqualTo(field);
		assertNothingChanged();
	}

	/** x, y, width, height of the nested shape within a container at 0, 0, 100 x 100; accepted */
	@ParameterizedTest
	@CsvSource({ "0, 0, 100, 100, true", "10, 20, 90, 80, true", "0, 0, 100.01, 100, false", "0, 0, 100, 100.01, false",
			"-0.01, 0, 50, 50, false", "0, -0.01, 50, 50, false", "10.01, 0, 90, 50, false", "0, 20.01, 50, 80, false" })
	void aNestedShapeLiesWithinItsContainerToTheEdge(String x, String y, String width, String height,
			boolean accepted) {
		int erp = entity("SAP ERP");
		int billing = entity("Billing");
		String container = id();
		ObjectNode inner = nested(shape(id(), billing, 0, 0, 1, 1), container);
		inner.put("x", new BigDecimal(x)).put("y", new BigDecimal(y)).put("width", new BigDecimal(width))
			.put("height", new BigDecimal(height));
		MvcTestResult result = save(1, shapes(shape(container, erp, 0, 0, 100, 100), inner), this.anna);
		if (accepted) {
			assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
			assertThat(view().path("shapes").get(1).path("parentShapeId").asString()).isEqualTo(container);
		}
		else {
			assertThat(firstField(result)).isEqualTo("shapes[1].parentShapeId");
			assertNothingChanged();
		}
	}

	/** the number of shapes in the chain, accepted */
	@ParameterizedTest
	@CsvSource({ "1, true", "10, true", "11, false" })
	void shapesAreNestedAtMostTenLevelsDeep(int levels, boolean accepted) {
		int erp = entity("SAP ERP");
		List<ObjectNode> chain = new ArrayList<>();
		String previous = null;
		for (int i = 0; i < levels; i++) {
			String id = id();
			ObjectNode shape = shape(id, erp, i * 5, i * 5, 2000 - i * 10, 2000 - i * 10);
			chain.add((previous == null) ? shape : nested(shape, previous));
			previous = id;
		}
		MvcTestResult result = save(1, shapes(chain.toArray(ObjectNode[]::new)), this.anna);
		if (accepted) {
			assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
			assertThat(view().path("shapes")).hasSize(levels);
		}
		else {
			assertThat(firstField(result)).isEqualTo("shapes[10].parentShapeId");
			assertNothingChanged();
		}
	}

	@Test
	void aContainerOfTenThousandPtIsStoredAndReadBack() {
		int erp = entity("SAP ERP");
		String container = id();
		saveOk(1, shapes(shape(container, erp, 0, 0, 10000, 10000)));
		assertThat(view().path("shapes").get(0).path("width").decimalValue()).isEqualByComparingTo("10000");
		assertThat(this.jdbc.queryForObject("SELECT width_pt FROM diagram_shapes WHERE id = ?::uuid", BigDecimal.class,
				container)).isEqualByComparingTo("10000.00");
	}

	@Test
	void aHiddenContainerReturnsItsNestedShapesWithoutContainer() {
		int erp = entity("SAP ERP");
		int billing = entity("Billing");
		String container = id();
		String nested = id();
		saveOk(1, shapes(shape(container, erp, 0, 0, 300, 200), nested(shape(nested, billing, 10, 10, 90, 30), container)));
		deleteEntity(erp);
		JsonNode view = view();
		assertThat(view.path("shapes")).hasSize(1);
		assertThat(view.path("shapes").get(0).path("id").asString()).isEqualTo(nested);
		assertThat(view.path("shapes").get(0).path("parentShapeId").isNull()).isTrue();
		assertThat(storedParent(nested)).isEqualTo(container);
		this.jdbc.update("UPDATE entities SET deleted_at = NULL, deleted_by = NULL WHERE id = ?", erp);
		assertThat(view().path("shapes").get(1).path("parentShapeId").asString()).isEqualTo(container);
	}

	@Test
	void aStoredNestingThatNoLongerContainsTheShapeIsReturnedWithoutContainer() {
		int erp = entity("SAP ERP");
		int billing = entity("Billing");
		String container = id();
		String nested = id();
		saveOk(1, shapes(shape(container, erp, 0, 0, 300, 200), nested(shape(nested, billing, 10, 10, 90, 30), container)));
		this.jdbc.update("UPDATE diagram_shapes SET x_pt = 500 WHERE id = ?::uuid", container);
		assertThat(view().path("shapes").get(1).path("parentShapeId").isNull()).isTrue();
	}

	@Test
	void removingAContainerRemovesItsNestedShapesAndKeepsTheEntities() {
		int erp = entity("SAP ERP");
		int billing = entity("Billing");
		int invoicing = entity("Invoicing");
		String container = id();
		saveOk(1, shapes(shape(container, erp, 0, 0, 400, 200),
				nested(shape(id(), billing, 10, 10, 90, 30), container),
				nested(shape(id(), invoicing, 120, 10, 90, 30), container)));
		saveOk(2, shapes());
		assertThat(view().path("shapes")).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid",
				Integer.class, this.diagram)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE id IN (?, ?, ?) AND deleted_at IS NULL",
				Integer.class, erp, billing, invoicing)).isEqualTo(3);
	}

	@Test
	void aRemovedContainerKeepsAShapeThatTheRequestMovesToAnotherContainerWithItsEdgesAndHiddenShapes() {
		int erp = entity("SAP ERP");
		int cloud = entity("Cloud");
		int billing = entity("Billing");
		int legacy = entity("Legacy");
		int ledger = entity("Ledger");
		String first = id();
		String second = id();
		String movable = id();
		String hidden = id();
		String peer = id();
		saveOk(1, shapes(shape(first, erp, 0, 0, 400, 200), shape(second, cloud, 500, 0, 400, 200),
				nested(shape(movable, billing, 10, 10, 90, 30), first),
				nested(shape(hidden, legacy, 120, 10, 90, 30), first), shape(peer, ledger, 0, 400, 90, 30)));
		int connection = connection("Billing to Ledger", billing, ledger);
		String edge = id();
		ObjectNode body = shapes(shape(first, erp, 0, 0, 400, 200), shape(second, cloud, 500, 0, 400, 200),
				nested(shape(movable, billing, 10, 10, 90, 30), first),
				nested(shape(hidden, legacy, 120, 10, 90, 30), first), shape(peer, ledger, 0, 400, 90, 30));
		body.putArray("edges").add(edge(edge, connection, movable, peer));
		saveOk(2, body);
		deleteEntity(legacy);
		deleteEntity(connection);
		// the editor removes the first container and moves "Billing" into the second; the hidden shape and edge are not in its view
		saveOk(3, shapes(shape(second, cloud, 500, 0, 400, 200), nested(shape(movable, billing, 510, 10, 90, 30), second),
				shape(peer, ledger, 0, 400, 90, 30)));
		assertThat(storedParent(movable)).isEqualTo(second);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE id = ?::uuid", Integer.class,
				first)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE id = ?::uuid", Integer.class,
				hidden)).as("the hidden shape of a deleted entity stays").isOne();
		assertThat(storedParent(hidden)).isNull();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_edges WHERE id = ?::uuid", Integer.class,
				edge)).as("the hidden edge of the kept shapes stays").isOne();
	}

	// Requirement: Children of a placed entity

	@Test
	void childrenAreListedByNameWithAllowedAndCompositionConnection() {
		int erp = entity("SAP ERP");
		int billing = child("Billing", erp);
		int invoicing = child("invoicing", erp);
		int database = child("ERP Database", erp, this.outsider);
		int gone = child("Archive", erp);
		child("Dunning", billing);
		deleteEntity(gone);
		setParent(billing, erp);
		JsonNode list = ok(call(HttpMethod.GET, children(erp), null, this.franz, null));
		assertThat(list.path("truncated").asBoolean()).isFalse();
		List<String> names = new ArrayList<>();
		list.path("items").forEach((item) -> names.add(item.path("entity").path("name").asString()));
		assertThat(names).containsExactly("Billing", "ERP Database", "invoicing");
		JsonNode first = list.path("items").get(0);
		assertThat(first.path("entity").path("id").asInt()).isEqualTo(billing);
		assertThat(first.path("entity").path("childCount").asInt()).as("Dunning").isOne();
		assertThat(first.path("entity").path("parentEntityId").asInt()).isEqualTo(erp);
		assertThat(first.path("allowed").asBoolean()).isTrue();
		assertThat(first.path("compositionConnection").path("allowed").asBoolean()).isTrue();
		assertThat(first.path("compositionConnection").path("connection").path("metatypeId").asString())
			.isEqualTo(this.composition.toString());
		assertThat(list.path("items").get(1).path("allowed").asBoolean()).as("type not allowed").isFalse();
		assertThat(list.path("items").get(1).path("compositionConnection").isNull()).isTrue();
		assertThat(list.path("items").get(2).path("entity").path("id").asInt()).isEqualTo(invoicing);
		assertThat(list.path("items").get(2).path("compositionConnection").isNull()).isTrue();
		assertThat(database).isPositive();
	}

	@Test
	void aDeletedCompositionConnectionIsNotOfferedAndOneOfAnotherTypeIsNotAllowed() {
		int erp = entity("SAP ERP");
		int billing = child("Billing", erp);
		setParent(billing, erp);
		int connection = this.jdbc.queryForObject("""
				SELECT e.id FROM entities e JOIN entity_connections c ON c.entity_id = e.id
				WHERE c.source_entity_id = ? AND c.target_entity_id = ?""", Integer.class, erp, billing);
		this.jdbc.update("DELETE FROM viewpoint_connection_types WHERE viewpoint_id = ?", this.viewpoint);
		JsonNode notAllowed = ok(call(HttpMethod.GET, children(erp), null, this.franz, null)).path("items").get(0);
		assertThat(notAllowed.path("compositionConnection").path("connection").path("id").asInt()).isEqualTo(connection);
		assertThat(notAllowed.path("compositionConnection").path("allowed").asBoolean()).isFalse();
		deleteEntity(connection);
		assertThat(ok(call(HttpMethod.GET, children(erp), null, this.franz, null)).path("items").get(0)
			.path("compositionConnection").isNull()).isTrue();
	}

	/** the number of children, the items returned, truncated */
	@ParameterizedTest
	@CsvSource({ "0, 0, false", "500, 500, false", "501, 500, true" })
	void childrenAreLimitedTo500(int count, int returned, boolean truncated) {
		int erp = entity("SAP ERP");
		this.jdbc.update("""
				INSERT INTO entities (metatype_id, name, created_by, parent_entity_id)
				SELECT ?, 'Child ' || lpad(g::text, 4, '0'), ?, ? FROM generate_series(1, ?) g""", this.component,
				this.kurtId, erp, count);
		JsonNode list = ok(call(HttpMethod.GET, children(erp), null, this.franz, null));
		assertThat(list.path("items")).hasSize(returned);
		assertThat(list.path("truncated").asBoolean()).isEqualTo(truncated);
		if (returned > 0) {
			assertThat(list.path("items").get(0).path("entity").path("name").asString()).isEqualTo("Child 0001");
			assertThat(list.path("items").get(returned - 1).path("entity").path("name").asString())
				.isEqualTo("Child " + String.format("%04d", returned));
		}
	}

	@Test
	void childrenOfAnUnknownDeletedOrConnectionEntityOrInAnUnknownDiagramAnswer404() {
		int erp = entity("SAP ERP");
		int billing = child("Billing", erp);
		int connection = connection("Flow", erp, billing);
		int gone = entity("Gone");
		deleteEntity(gone);
		assertThat(status(call(HttpMethod.GET, children(999_999_999), null, this.franz, null))).isEqualTo(404);
		assertThat(status(call(HttpMethod.GET, children(gone), null, this.franz, null))).isEqualTo(404);
		assertThat(status(call(HttpMethod.GET, children(connection), null, this.franz, null))).isEqualTo(404);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + UUID.randomUUID() + "/entities/" + erp + "/children", null,
				this.franz, null))).isEqualTo(404);
		String deleted = diagram(unique("Old"));
		assertThat(status(call(HttpMethod.DELETE, DIAGRAMS + "/" + deleted, null, this.anna, "\"1\""))).isEqualTo(204);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + deleted + "/entities/" + erp + "/children", null,
				this.franz, null))).isEqualTo(404);
	}

	@Test
	void withoutEntityReadTheChildrenAreForbidden() {
		int erp = entity("SAP ERP");
		assertThat(status(call(HttpMethod.GET, children(erp), null, this.principals.personWithoutRole(), null)))
			.isEqualTo(403);
	}

	@Test
	void shapesAndSearchResultsCarryTheChildCountAndTheParent() {
		String tag = suffix();
		int erp = entity("SAP ERP " + tag);
		int billing = child("Billing " + tag, erp);
		child("Dunning " + tag, billing);
		int gone = child("Archive " + tag, billing);
		deleteEntity(gone);
		JsonNode found = ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/entities?q=" + tag + "&pageSize=200",
				null, this.franz, null));
		JsonNode byName = found.path("items");
		assertThat(byName.get(0).path("name").asString()).isEqualTo("Billing " + tag);
		assertThat(byName.get(0).path("childCount").asInt()).isOne();
		assertThat(byName.get(0).path("parentEntityId").asInt()).isEqualTo(erp);
		assertThat(byName.get(2).path("name").asString()).isEqualTo("SAP ERP " + tag);
		assertThat(byName.get(2).path("childCount").asInt()).isOne();
		assertThat(byName.get(2).path("parentEntityId").isNull()).isTrue();
		saveOk(1, shapes(shape(id(), billing, 0, 0, 90, 30)));
		JsonNode entity = view().path("shapes").get(0).path("entity");
		assertThat(entity.path("childCount").asInt()).isOne();
		assertThat(entity.path("parentEntityId").asInt()).isEqualTo(erp);
	}

	// Requirement: Hierarchy line to a child placed elsewhere

	@Test
	void aHierarchyLineIsSavedReturnedAndHiddenWhenTheHierarchyChanges() {
		int erp = entity("SAP ERP");
		int cloud = entity("Cloud");
		int billing = child("Billing", erp);
		String parent = id();
		String childShape = id();
		String line = id();
		ObjectNode body = shapes(shape(parent, erp, 0, 0, 90, 30), shape(childShape, billing, 300, 0, 90, 30));
		body.putArray("hierarchyLines").add(line(line, parent, childShape));
		JsonNode saved = saveOk(1, body);
		assertThat(saved.path("hierarchyLines")).hasSize(1);
		assertThat(saved.path("hierarchyLines").get(0).path("id").asString()).isEqualTo(line);
		assertThat(saved.path("hierarchyLines").get(0).path("parentShapeId").asString()).isEqualTo(parent);
		assertThat(saved.path("hierarchyLines").get(0).path("childShapeId").asString()).isEqualTo(childShape);
		assertThat(view().path("hierarchyLines")).hasSize(1);
		setParent(billing, cloud);
		assertThat(view().path("hierarchyLines")).isEmpty();
		// the next save replaces the stored set
		saveOk(2, shapes(shape(parent, erp, 0, 0, 90, 30), shape(childShape, billing, 300, 0, 90, 30)));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_hierarchy_lines WHERE diagram_id = ?::uuid",
				Integer.class, this.diagram)).isZero();
	}

	@Test
	void aLineWithoutHierarchyIsRejectedNamingTheLine() {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		String parent = id();
		String childShape = id();
		ObjectNode body = shapes(shape(parent, erp, 0, 0, 90, 30), shape(childShape, crm, 300, 0, 90, 30));
		body.putArray("hierarchyLines").add(line(id(), parent, childShape));
		assertThat(firstField(save(1, body, this.anna))).isEqualTo("hierarchyLines[0]");
		assertNothingChanged();
	}

	@Test
	void aLineThatIsValidThroughAParentChangeOfTheSameSaveIsAccepted() {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		String parent = id();
		String childShape = id();
		ObjectNode body = shapes(shape(parent, erp, 0, 0, 90, 30), shape(childShape, crm, 300, 0, 90, 30));
		body.putArray("hierarchyLines").add(line(id(), parent, childShape));
		body.putArray("parentChanges").add(change(crm, 1, erp));
		JsonNode saved = saveOk(1, body);
		assertThat(saved.path("hierarchyLines")).hasSize(1);
		assertThat(parentOf(crm)).isEqualTo(erp);
	}

	@Test
	void aLineBetweenTheShapesOfAnEntityAndItsChildNeedsTheParentWrittenFirstEvenForNewEntities() {
		String parentKey = id();
		String childKey = id();
		String parent = id();
		String childShape = id();
		ObjectNode body = shapes(newShape(parent, parentKey, 0, 0, 90, 30), newShape(childShape, childKey, 300, 0, 90, 30));
		body.putArray("newEntities").add(newEntity(parentKey, "Shop")).add(newEntity(childKey, "Cart").put("parentNewEntityKey", parentKey));
		body.putArray("hierarchyLines").add(line(id(), parent, childShape));
		assertThat(saveOk(1, body).path("hierarchyLines")).hasSize(1);
	}

	/** which structural error */
	@ParameterizedTest
	@ValueSource(strings = { "same-shape", "pair-twice", "unknown-shape", "id-twice" })
	void structuralErrorsOfLinesAreRejected(String kind) {
		int erp = entity("SAP ERP");
		int billing = child("Billing", erp);
		String parent = id();
		String childShape = id();
		ObjectNode body = shapes(shape(parent, erp, 0, 0, 90, 30), shape(childShape, billing, 300, 0, 90, 30));
		ArrayNode lines = body.putArray("hierarchyLines");
		String field;
		switch (kind) {
			case "same-shape" -> {
				lines.add(line(id(), parent, parent));
				field = "hierarchyLines[0]";
			}
			case "pair-twice" -> {
				lines.add(line(id(), parent, childShape)).add(line(id(), parent, childShape));
				field = "hierarchyLines[1]";
			}
			case "unknown-shape" -> {
				lines.add(line(id(), parent, id()));
				field = "hierarchyLines[0].childShapeId";
			}
			default -> {
				String same = id();
				lines.add(line(same, parent, childShape)).add(line(same, childShape, parent));
				field = "hierarchyLines[1].id";
			}
		}
		assertThat(firstField(save(1, body, this.anna))).isEqualTo(field);
		assertNothingChanged();
	}

	@Test
	void aLineIdOfAnotherDiagramIsRejected() {
		int erp = entity("SAP ERP");
		int billing = child("Billing", erp);
		String other = diagram(unique("Other"));
		String line = id();
		ObjectNode first = shapes(shape(id(), erp, 0, 0, 90, 30), shape(id(), billing, 300, 0, 90, 30));
		first.putArray("hierarchyLines").add(line(line, first.path("shapes").get(0).path("id").asString(),
				first.path("shapes").get(1).path("id").asString()));
		assertThat(status(call(HttpMethod.PUT, VIEWS + "/" + other, first, this.anna, "\"1\""))).isEqualTo(200);
		ObjectNode second = shapes(shape(id(), erp, 0, 0, 90, 30), shape(id(), billing, 300, 0, 90, 30));
		second.putArray("hierarchyLines").add(line(line, second.path("shapes").get(0).path("id").asString(),
				second.path("shapes").get(1).path("id").asString()));
		assertThat(firstField(save(1, second, this.anna))).isEqualTo("hierarchyLines[0].id");
	}

	// Requirement: New parent on moving a shape (parent changes)

	@Test
	void aParentChangeSetsTheParentCreatesTheCompositionConnectionAndAuditsWithTheAgent() {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		String container = id();
		ObjectNode body = shapes(shape(container, erp, 0, 0, 300, 200),
				nested(shape(id(), crm, 10, 10, 90, 30), container));
		body.putArray("parentChanges").add(change(crm, 1, erp));
		MvcTestResult result = call(HttpMethod.PUT, VIEWS + "/" + this.diagram, body, this.anna, "\"1\"",
				"X-Agent-Id", "copilot", "X-Agent-Run", "run-7");
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		assertThat(parentOf(crm)).isEqualTo(erp);
		assertThat(entityVersion(crm)).isEqualTo(2);
		assertThat(entityVersion(erp)).isOne();
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM entity_connections c JOIN entities e ON e.id = c.entity_id
				WHERE c.source_entity_id = ? AND c.target_entity_id = ? AND e.auto_created AND e.deleted_at IS NULL""",
				Integer.class, erp, crm)).isOne();
		assertThat(auditCount("entity.parent-changed", crm)).isOne();
		assertThat(this.jdbc.queryForObject("""
				SELECT agent_label || '|' || agent_run FROM audit.events
				WHERE event_type = 'entity.parent-changed' AND entity_id = ?""", String.class, crm))
			.isEqualTo("copilot|run-7");
		assertThat(auditDetails("diagram.layout-saved", this.diagram).path("parentsChanged").asInt()).isOne();
	}

	@Test
	void aParentChangeWithoutParentClearsItAndRemovesTheCompositionConnection() {
		int erp = entity("SAP ERP");
		int billing = child("Billing", erp);
		setParent(billing, erp);
		ObjectNode body = shapes(shape(id(), billing, 0, 0, 90, 30));
		body.putArray("parentChanges").add(change(billing, 2, null));
		saveOk(1, body);
		assertThat(parentOf(billing)).isNull();
		assertThat(entityVersion(billing)).isEqualTo(3);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM entity_connections c JOIN entities e ON e.id = c.entity_id
				WHERE c.source_entity_id = ? AND c.target_entity_id = ? AND e.deleted_at IS NULL""", Integer.class, erp,
				billing)).isZero();
	}

	@Test
	void aChangeToTheCurrentParentWritesNothingAndIsNotCounted() {
		int erp = entity("SAP ERP");
		int billing = child("Billing", erp);
		ObjectNode body = shapes(shape(id(), billing, 0, 0, 90, 30));
		body.putArray("parentChanges").add(change(billing, 1, erp));
		saveOk(1, body);
		assertThat(entityVersion(billing)).isOne();
		assertThat(auditCount("entity.parent-changed", billing)).isZero();
		assertThat(auditDetails("diagram.layout-saved", this.diagram).path("parentsChanged").asInt()).isZero();
	}

	/** the level of the new parent, accepted: a leaf below a chain of that many entities */
	@ParameterizedTest
	@CsvSource({ "9, true", "10, false" })
	void aParentChangeKeepsTheHierarchyWithinTenLevels(int chain, boolean accepted) {
		int top = entity("Level 1");
		int parent = top;
		for (int i = 2; i <= chain; i++) {
			parent = child("Level " + i, parent);
		}
		int leaf = entity("Leaf");
		ObjectNode body = shapes(shape(id(), leaf, 0, 0, 90, 30));
		body.putArray("parentChanges").add(change(leaf, 1, parent));
		MvcTestResult result = save(1, body, this.anna);
		if (accepted) {
			assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
			assertThat(parentOf(leaf)).isEqualTo(parent);
		}
		else {
			assertThat(firstField(result)).isEqualTo("parentChanges[0].parentEntityId");
			assertThat(parentOf(leaf)).isNull();
			assertNothingChanged();
		}
	}

	@Test
	void aParentChangeThatCreatesACycleIsRejected() {
		int erp = entity("SAP ERP");
		int billing = child("Billing", erp);
		ObjectNode body = shapes(shape(id(), erp, 0, 0, 90, 30), shape(id(), billing, 100, 0, 90, 30));
		body.putArray("parentChanges").add(change(erp, 1, billing));
		assertThat(firstField(save(1, body, this.anna))).isEqualTo("parentChanges[0].parentEntityId");
		assertThat(parentOf(erp)).isNull();
		assertNothingChanged();
	}

	@Test
	void anUnknownDeletedOrConnectionParentIsRejected() {
		int erp = entity("SAP ERP");
		int gone = entity("Gone");
		int crm = entity("CRM");
		int connection = connection("Flow", erp, crm);
		deleteEntity(gone);
		for (int parent : new int[] { 999_999_999, gone, connection }) {
			ObjectNode body = shapes(shape(id(), crm, 0, 0, 90, 30));
			body.putArray("parentChanges").add(change(crm, 1, parent));
			assertThat(firstField(save(1, body, this.anna))).as("parent " + parent).isEqualTo("parentChanges[0].parentEntityId");
		}
		assertThat(parentOf(crm)).isNull();
	}

	@Test
	void aStaleVersionOfAParentChangeAnswers409AndNamesTheEntityOnceTogetherWithEntityUpdates() {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		ObjectNode body = shapes(shape(id(), erp, 0, 0, 90, 30), shape(id(), crm, 100, 0, 90, 30));
		body.putArray("entityUpdates").add(JsonMapper.shared().createObjectNode().put("entityId", crm)
			.put("expectedVersion", 5).put("description", "changed"));
		body.putArray("parentChanges").add(change(crm, 7, erp));
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(409);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).endsWith("entity-version-conflict");
		assertThat(problem.path("conflicts")).hasSize(1);
		assertThat(problem.path("invalidFields").toString()).isEqualTo("[\"entityUpdates[0]\"]");
		ObjectNode only = shapes(shape(id(), crm, 100, 0, 90, 30));
		only.putArray("parentChanges").add(change(crm, 7, erp));
		MvcTestResult second = save(1, only, this.anna);
		assertThat(status(second)).isEqualTo(409);
		assertThat(body(second).path("invalidFields").toString()).isEqualTo("[\"parentChanges[0]\"]");
		assertThat(body(second).path("conflicts").get(0).path("currentVersion").asInt()).isOne();
		assertThat(parentOf(crm)).isNull();
		assertNothingChanged();
	}

	@Test
	void anEntityWithAnOpenIssueCannotChangeItsParent() {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		block(crm);
		ObjectNode body = shapes(shape(id(), crm, 100, 0, 90, 30));
		body.putArray("parentChanges").add(change(crm, 1, erp));
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(409);
		assertThat(body(result).path("type").asString()).endsWith("entity-blocked");
		assertThat(parentOf(crm)).isNull();
	}

	@Test
	void aParentChangeAndAnEntityUpdateOfTheSameEntityWriteTwoVersions() {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		ObjectNode body = shapes(shape(id(), erp, 0, 0, 90, 30), shape(id(), crm, 100, 0, 90, 30));
		body.putArray("entityUpdates").add(JsonMapper.shared().createObjectNode().put("entityId", crm)
			.put("expectedVersion", 1).put("description", "now with a parent"));
		body.putArray("parentChanges").add(change(crm, 1, erp));
		saveOk(1, body);
		assertThat(entityVersion(crm)).isEqualTo(3);
		assertThat(parentOf(crm)).isEqualTo(erp);
	}

	/** which structural error of the parent changes, the field */
	@ParameterizedTest
	@CsvSource({ "twice, parentChanges[1].entityId", "not-shown, parentChanges[0].entityId",
			"both-parents, parentChanges[0]", "unknown-key, parentChanges[0].parentNewEntityKey" })
	void structuralErrorsOfParentChangesAreRejectedAndNothingChanges(String kind, String field) {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		int hidden = entity("Not shown");
		ObjectNode body = shapes(shape(id(), erp, 0, 0, 90, 30), shape(id(), crm, 100, 0, 90, 30));
		ArrayNode changes = body.putArray("parentChanges");
		switch (kind) {
			case "twice" -> changes.add(change(crm, 1, erp)).add(change(crm, 1, null));
			case "not-shown" -> changes.add(change(hidden, 1, erp));
			case "both-parents" -> changes.add(change(crm, 1, erp).put("parentNewEntityKey", id()));
			default -> changes.add(change(crm, 1, null).put("parentNewEntityKey", id()));
		}
		assertThat(firstField(save(1, body, this.anna))).isEqualTo(field);
		assertThat(parentOf(crm)).isNull();
		assertNothingChanged();
	}

	@Test
	void aNewEntityWithAParentEndsAtVersionTwoWithBothAuditEvents() {
		int erp = entity("SAP ERP");
		String key = id();
		ObjectNode body = shapes(shape(id(), erp, 0, 0, 300, 200), newShape(id(), key, 10, 10, 90, 30));
		body.putArray("newEntities").add(newEntity(key, "Payments").put("parentEntityId", erp));
		saveOk(1, body);
		int payments = this.jdbc.queryForObject("SELECT id FROM entities WHERE name = 'Payments' AND parent_entity_id = ?",
				Integer.class, erp);
		assertThat(entityVersion(payments)).isEqualTo(2);
		assertThat(auditCount("entity.created", payments)).isOne();
		assertThat(auditCount("entity.parent-changed", payments)).isOne();
	}

	@Test
	void aNewEntityInANewContainerGetsTheNewParentOfTheSameSave() {
		String shopKey = id();
		String cartKey = id();
		ObjectNode body = shapes(newShape(id(), shopKey, 0, 0, 300, 200), newShape(id(), cartKey, 10, 10, 90, 30));
		body.putArray("newEntities").add(newEntity(shopKey, "Shop"))
			.add(newEntity(cartKey, "Cart").put("parentNewEntityKey", shopKey));
		saveOk(1, body);
		int shop = this.jdbc.queryForObject("SELECT id FROM entities WHERE name = 'Shop' ORDER BY id DESC LIMIT 1",
				Integer.class);
		int cart = this.jdbc.queryForObject("SELECT id FROM entities WHERE name = 'Cart' ORDER BY id DESC LIMIT 1",
				Integer.class);
		assertThat(parentOf(cart)).isEqualTo(shop);
		assertThat(auditCount("entity.parent-changed", cart)).isOne();
		assertThat(auditDetails("diagram.layout-saved", this.diagram).path("parentsChanged").asInt()).isOne();
	}

	@Test
	void aNewEntityNamingBothParentsOrItselfOrAnUnknownKeyIsRejected() {
		int erp = entity("SAP ERP");
		for (String kind : new String[] { "both", "self", "unknown" }) {
			String key = id();
			ObjectNode newEntity = newEntity(key, "Payments " + kind);
			String field = "newEntities[0]";
			switch (kind) {
				case "both" -> newEntity.put("parentEntityId", erp).put("parentNewEntityKey", id());
				case "self" -> {
					newEntity.put("parentNewEntityKey", key);
					field = "newEntities[0].parentNewEntityKey";
				}
				default -> {
					newEntity.put("parentNewEntityKey", id());
					field = "newEntities[0].parentNewEntityKey";
				}
			}
			ObjectNode body = shapes(shape(id(), erp, 0, 0, 300, 200), newShape(id(), key, 10, 10, 90, 30));
			body.putArray("newEntities").add(newEntity);
			assertThat(firstField(save(1, body, this.anna))).as(kind).isEqualTo(field);
		}
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name LIKE 'Payments %'", Integer.class))
			.isZero();
	}

	@Test
	void aNewEntityWithAnInvalidParentIsRejectedAndNothingIsCreated() {
		String key = id();
		ObjectNode body = shapes(newShape(id(), key, 10, 10, 90, 30));
		body.putArray("newEntities").add(newEntity(key, "Orphan").put("parentEntityId", 999_999_999));
		assertThat(firstField(save(1, body, this.anna))).isEqualTo("newEntities[0].parentEntityId");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = 'Orphan'", Integer.class))
			.isZero();
		assertNothingChanged();
	}

	@Test
	void anInvalidShapeWithAValidParentChangeIsRejectedAndTheParentIsUnchanged() {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		ObjectNode bad = shape(id(), crm, 100, 0, 90, 30);
		bad.put("parentShapeId", id());
		ObjectNode body = shapes(shape(id(), erp, 0, 0, 90, 30), bad);
		body.putArray("parentChanges").add(change(crm, 1, erp));
		assertThat(firstField(save(1, body, this.anna))).isEqualTo("shapes[1].parentShapeId");
		assertThat(parentOf(crm)).isNull();
		assertThat(entityVersion(crm)).isOne();
		assertThat(auditCount("entity.parent-changed", crm)).isZero();
	}

	// the order of the answers

	@Test
	void aStructuralErrorComesBeforeAVersionConflict() {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		ObjectNode bad = shape(id(), crm, 100, 0, 90, 30);
		bad.put("parentShapeId", id());
		ObjectNode body = shapes(shape(id(), erp, 0, 0, 90, 30), bad);
		body.putArray("parentChanges").add(change(crm, 9, erp));
		assertThat(status(save(1, body, this.anna))).isEqualTo(422);
	}

	@Test
	void aVersionConflictComesBeforeAParentRuleError() {
		int erp = entity("SAP ERP");
		int billing = child("Billing", erp);
		ObjectNode body = shapes(shape(id(), erp, 0, 0, 90, 30), shape(id(), billing, 100, 0, 90, 30));
		// the cycle would be a 422 of the parents, the stale version is a 409 first
		body.putArray("parentChanges").add(change(erp, 7, billing));
		assertThat(status(save(1, body, this.anna))).isEqualTo(409);
	}

	@Test
	void aHierarchyLineErrorComesAfterTheParents() {
		int erp = entity("SAP ERP");
		int crm = entity("CRM");
		String parent = id();
		String childShape = id();
		ObjectNode body = shapes(shape(parent, erp, 0, 0, 90, 30), shape(childShape, crm, 300, 0, 90, 30));
		body.putArray("hierarchyLines").add(line(id(), parent, childShape));
		// the parent is invalid (a 422 of the parents) and the line has no hierarchy: the parents come first
		body.putArray("parentChanges").add(change(crm, 1, 999_999_999));
		assertThat(firstField(save(1, body, this.anna))).isEqualTo("parentChanges[0].parentEntityId");
	}

	// limits

	/** hierarchy lines, parent changes, status */
	@ParameterizedTest
	@CsvSource({ "4000, 0, 200", "4001, 0, 400", "0, 500, 200", "0, 501, 400" })
	void linesAndParentChangesAreLimited(int lines, int parentChanges, int status) {
		this.jdbc.update("UPDATE metatypes SET is_composition = false WHERE id = ?", this.composition);
		int erp = entity("SAP ERP");
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		if (lines > 0) {
			// 20 shapes of the parent and 201 shapes of its child give 4020 pairs, of which the first 4000 or 4001 are used
			int child = child("Billing", erp);
			List<String> parents = new ArrayList<>();
			List<String> children = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				String shape = id();
				parents.add(shape);
				shapes.add(shape(shape, erp, i, 0, 90, 30));
			}
			for (int i = 0; i < 201; i++) {
				String shape = id();
				children.add(shape);
				shapes.add(shape(shape, child, i, 100, 90, 30));
			}
			ArrayNode array = body.putArray("hierarchyLines");
			for (int i = 0; i < lines; i++) {
				array.add(line(id(), parents.get(i / 201), children.get(i % 201)));
			}
		}
		else {
			ArrayNode changes = body.putArray("parentChanges");
			shapes.add(shape(id(), erp, 0, 0, 90, 30));
			for (int i = 0; i < parentChanges; i++) {
				int entity = entity("Limit " + i);
				shapes.add(shape(id(), entity, i, 100, 90, 30));
				changes.add(change(entity, 1, erp));
			}
		}
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result).substring(0, Math.min(300, content(result).length())))
			.isEqualTo(status);
		if (status == 200) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_hierarchy_lines WHERE diagram_id = ?::uuid",
					Integer.class, this.diagram)).isEqualTo(lines);
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE parent_entity_id = ?", Integer.class,
					erp)).isEqualTo((lines > 0) ? 1 : parentChanges);
		}
		else {
			assertThat(version(this.diagram)).isOne();
		}
	}

	// concurrency

	@Test
	void aSaveWithParentChangesAndAConcurrentSetEntityParentBothComplete() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 5; round++) {
				int erp = entity("SAP ERP " + round);
				int crm = entity("CRM " + round);
				int billing = entity("Billing " + round);
				String target = diagram(unique("Concurrent"));
				ObjectNode body = shapes(shape(id(), erp, 0, 0, 90, 30), shape(id(), crm, 100, 0, 90, 30),
						shape(id(), billing, 200, 0, 90, 30));
				body.putArray("entityUpdates").add(JsonMapper.shared().createObjectNode().put("entityId", crm)
					.put("expectedVersion", 1).put("description", "round " + round));
				body.putArray("parentChanges").add(change(billing, 1, erp));
				Future<Integer> save = pool.submit(() -> status(call(HttpMethod.PUT, VIEWS + "/" + target, body, this.anna,
						"\"1\"")));
				Future<Integer> parent = pool.submit(() -> status(call(HttpMethod.PUT, "/api/v1/entities/" + crm + "/parent",
						JsonMapper.shared().createObjectNode().put("parentEntityId", billing), this.kurt, "\"1\"")));
				int first = save.get(60, TimeUnit.SECONDS);
				int second = parent.get(60, TimeUnit.SECONDS);
				assertThat(first).isIn(200, 409, 422);
				assertThat(second).isIn(200, 412, 422);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	// helpers

	private static ObjectNode nested(ObjectNode shape, String container) {
		return shape.put("parentShapeId", container);
	}

	private static ObjectNode line(String id, String parent, String child) {
		return JsonMapper.shared().createObjectNode().put("id", id).put("parentShapeId", parent).put("childShapeId",
				child);
	}

	private static ObjectNode change(int entityId, int expectedVersion, Integer parent) {
		ObjectNode change = JsonMapper.shared().createObjectNode().put("entityId", entityId).put("expectedVersion",
				expectedVersion);
		if (parent != null) {
			change.put("parentEntityId", parent);
		}
		return change;
	}

	private static ObjectNode edge(String id, int connection, String source, String target) {
		ObjectNode edge = JsonMapper.shared().createObjectNode().put("id", id).put("connectionId", connection)
			.put("sourceShapeId", source).put("targetShapeId", target);
		edge.putArray("points");
		return edge;
	}

	private String children(int entityId) {
		return VIEWS + "/" + this.diagram + "/entities/" + entityId + "/children";
	}

	private ObjectNode shapes(ObjectNode... shapes) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode array = body.putArray("shapes");
		for (ObjectNode shape : shapes) {
			array.add(shape);
		}
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

	private static ObjectNode newEntity(String key, String name) {
		return JsonMapper.shared().createObjectNode().put("key", key).put("metatypeId", "00000000-0000-0000-0000-000000000000")
			.put("name", name);
	}

	private MvcTestResult save(int version, ObjectNode body, TestSession session) {
		fixMetatypes(body);
		return call(HttpMethod.PUT, VIEWS + "/" + this.diagram, body, session, "\"" + version + "\"");
	}

	/** The placeholder metatype of {@link #newEntity} is the component metatype of the viewpoint. */
	private void fixMetatypes(ObjectNode body) {
		if (body.has("newEntities")) {
			body.path("newEntities").forEach((entity) -> ((ObjectNode) entity).put("metatypeId", this.component.toString()));
		}
	}

	private JsonNode saveOk(int version, ObjectNode body) {
		MvcTestResult result = save(version, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		return body(result);
	}

	private JsonNode view() {
		return ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram, null, this.franz, null));
	}

	private void assertNothingChanged() {
		assertThat(view().path("shapes")).isEmpty();
		assertThat(version(this.diagram)).isOne();
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_hierarchy_lines WHERE diagram_id = ?::uuid",
				Integer.class, this.diagram)).isZero();
	}

	private UUID metatype(String name, String abbreviation, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, abbreviation, is_connection) VALUES (?::uuid, ?, ?, ?)
				RETURNING id""", UUID.class, INSTANCE, name, abbreviation, connection);
	}

	private UUID viewpoint(UUID... metatypes) {
		UUID id = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, 'generic', ?)
				RETURNING id""", UUID.class, unique("Landscape"), this.kurtId);
		for (UUID metatype : metatypes) {
			this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", id, metatype);
		}
		return id;
	}

	private int entity(String name) {
		return entity(name, this.component);
	}

	private int entity(String name, UUID metatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class,
				metatype, name, this.kurtId);
	}

	private int child(String name, int parent) {
		return child(name, parent, this.component);
	}

	private int child(String name, int parent, UUID metatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by, parent_entity_id) VALUES (?, ?, ?, ?) RETURNING id""",
				Integer.class, metatype, name, this.kurtId, parent);
	}

	private int connection(String name, int source, int target) {
		UUID type = metatype("Flow " + suffix(), "FL", true);
		int id = this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class, type,
				name, this.kurtId);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?)", this.viewpoint,
				type);
		return id;
	}

	/** Sets the parent through the API of the hierarchy, so that the composition connection is created. */
	private void setParent(int entity, int parent) {
		this.jdbc.update("UPDATE entities SET parent_entity_id = NULL WHERE id = ?", entity);
		MvcTestResult result = call(HttpMethod.PUT, "/api/v1/entities/" + entity + "/parent",
				JsonMapper.shared().createObjectNode().put("parentEntityId", parent), this.kurt,
				"\"" + entityVersion(entity) + "\"");
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
	}

	private void deleteEntity(int id) {
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, id);
	}

	/** An unresolved open issue of a solution blocks the entity (US-153). */
	private void block(int entity) {
		UUID solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES (gen_random_uuid(), ?, ?, ?, 'draft')
				RETURNING id""", UUID.class, unique("Blocking"), this.kurtId, this.kurtId);
		UUID gone = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES (?, 'p_' || substr(md5(?), 1, 12), 'string') RETURNING id""", UUID.class, unique("Gone"),
				UUID.randomUUID().toString());
		this.jdbc.update("""
				INSERT INTO solution_open_issues (id, solution_id, entity_id, field, property_def_id, kind, kept_value)
				VALUES (gen_random_uuid(), ?, ?, ?, ?, 'not-in-metamodel', 'kept')""", solution, entity, gone.toString(),
				gone);
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

	private Integer parentOf(int entity) {
		return this.jdbc.queryForObject("SELECT parent_entity_id FROM entities WHERE id = ?", Integer.class, entity);
	}

	private String storedParent(String shape) {
		return this.jdbc.queryForObject("SELECT parent_shape_id::text FROM diagram_shapes WHERE id = ?::uuid",
				String.class, shape);
	}

	private int entityVersion(int entity) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, entity);
	}

	private int version(String id) {
		return this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, id);
	}

	private int auditCount(String type, Object target) {
		String column = (target instanceof Integer) ? "entity_id" : "target_id";
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ? AND " + column + " = ?",
				Integer.class, type, (target instanceof Integer) ? target : target.toString());
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

	private static JsonNode ok(MvcTestResult result) {
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		return body(result);
	}

	private static int status(MvcTestResult result) {
		return result.getResponse().getStatus();
	}

	private MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session, String ifMatch,
			String... headers) {
		var request = this.mvc.method(method)
			.uri(java.net.URI.create(path.replace(" ", "%20")))
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		for (int i = 0; i + 1 < headers.length; i += 2) {
			request = request.header(headers[i], headers[i + 1]);
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
			return Files.createTempDirectory("oea-diagram-nesting").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

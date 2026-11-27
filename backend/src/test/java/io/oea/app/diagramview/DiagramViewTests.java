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
 * Spec diagram/diagram-canvas (US-074, UC-10; design D1 to D5 of us-074-diagram-canvas): the view,
 * the entity search, the atomic save with new entities, the shape limits, incomplete entities and
 * the audit of the layout, on an own database. Kurt holds {@code entity:write} and
 * {@code entity:restore}, Anna {@code entity:write}, Franz only {@code entity:read}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramViewTests {

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

	/** The element metatypes of the viewpoint of the test, and one it does not allow. */
	private UUID component;

	private UUID process;

	private UUID outsider;

	private UUID viewpoint;

	private String diagram;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.anna = this.principals.personWithIdpRole("solution-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.kurtId = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		this.component = metatype("Application Component " + suffix(), "AC");
		this.process = metatype("Business Process " + suffix(), "BP");
		this.outsider = metatype("Technology " + suffix(), "TE");
		this.viewpoint = viewpoint(this.component, this.process);
		this.jdbc.update("INSERT INTO viewpoint_sizes (viewpoint_id, metatype_id, width_pt, height_pt) VALUES (?, ?, 120, 40)",
				this.viewpoint, this.component);
		this.diagram = diagram(unique("Order to Cash"));
	}

	// Requirement: Read the diagram view

	@Test
	void savedLayoutIsReadInPtInDrawingOrder() {
		int erp = entity("SAP ERP", this.component);
		int crm = entity("Salesforce", this.component);
		String first = id();
		String second = id();
		String third = id();
		JsonNode saved = saveOk(1, shapes(shape(first, erp, 100, 50, 90, 30), shape(second, crm, 10.5, -20.25, 120, 40),
				shape(third, erp, 0, 0, 1, 2000)));
		assertThat(saved.path("diagram").path("version").asInt()).isEqualTo(2);
		JsonNode view = ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram, null, this.franz, null));
		assertThat(view.path("diagram").path("id").asString()).isEqualTo(this.diagram);
		assertThat(view.path("diagram").path("version").asInt()).isEqualTo(2);
		assertThat(view.path("viewpoint").path("id").asString()).isEqualTo(this.viewpoint.toString());
		assertThat(view.path("viewpoint").path("effectiveMappings")).hasSize(2);
		JsonNode shapes = view.path("shapes");
		assertThat(shapes).hasSize(3);
		assertThat(shapes.get(0).path("id").asString()).isEqualTo(first);
		assertThat(shapes.get(0).path("entity").path("name").asString()).isEqualTo("SAP ERP");
		assertThat(shapes.get(0).path("entity").path("id").asInt()).isEqualTo(erp);
		assertThat(shapes.get(0).path("entity").path("metatypeId").asString()).isEqualTo(this.component.toString());
		assertThat(shapes.get(0).path("x").decimalValue()).isEqualByComparingTo("100");
		assertThat(shapes.get(0).path("y").decimalValue()).isEqualByComparingTo("50");
		assertThat(shapes.get(0).path("width").decimalValue()).isEqualByComparingTo("90");
		assertThat(shapes.get(0).path("height").decimalValue()).isEqualByComparingTo("30");
		assertThat(shapes.get(1).path("id").asString()).isEqualTo(second);
		assertThat(shapes.get(1).path("x").decimalValue()).isEqualByComparingTo("10.5");
		assertThat(shapes.get(1).path("y").decimalValue()).isEqualByComparingTo("-20.25");
		assertThat(shapes.get(2).path("id").asString()).isEqualTo(third);
		assertThat(shapes.get(2).path("entity").path("id").asInt()).isEqualTo(erp);
	}

	@Test
	void anEmptyDiagramHasNoShapes() {
		JsonNode view = ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram, null, this.franz, null));
		assertThat(view.path("shapes")).isEmpty();
		assertThat(view.path("diagram").path("version").asInt()).isOne();
	}

	@Test
	void shapesOfADeletedEntityAreHiddenKeptOnSaveAndShownAfterARestore() {
		int erp = entity("SAP ERP", this.component);
		int legacy = entity("Legacy CRM", this.component);
		String erpShape = id();
		String legacyShape = id();
		saveOk(1, shapes(shape(erpShape, erp, 1, 1, 90, 30), shape(legacyShape, legacy, 2, 2, 90, 30)));
		deleteEntity(legacy);
		JsonNode view = ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram, null, this.franz, null));
		assertThat(view.path("shapes")).hasSize(1);
		assertThat(view.path("shapes").get(0).path("id").asString()).isEqualTo(erpShape);
		// the client never received the hidden shape, so its save does not contain it and keeps it
		saveOk(2, shapes(shape(erpShape, erp, 5, 5, 90, 30)));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM diagram_shapes WHERE diagram_id = ?::uuid", Integer.class,
				this.diagram)).isEqualTo(2);
		this.jdbc.update("UPDATE entities SET deleted_at = NULL, deleted_by = NULL WHERE id = ?", legacy);
		view = ok(call(HttpMethod.GET, VIEWS + "/" + this.diagram, null, this.franz, null));
		assertThat(view.path("shapes")).hasSize(2);
		assertThat(shapeIds(view)).containsExactlyInAnyOrder(erpShape, legacyShape);
	}

	@Test
	void unknownOrDeletedDiagramAnswers404() {
		String unknown = UUID.randomUUID().toString();
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + unknown, null, this.franz, null))).isEqualTo(404);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + unknown + "/entities", null, this.franz, null)))
			.isEqualTo(404);
		assertThat(status(call(HttpMethod.PUT, VIEWS + "/" + unknown, shapes(), this.anna, "\"1\""))).isEqualTo(404);
		String gone = diagram(unique("Old Process"));
		assertThat(status(call(HttpMethod.DELETE, DIAGRAMS + "/" + gone, null, this.anna, "\"1\""))).isEqualTo(204);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + gone, null, this.franz, null))).isEqualTo(404);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + gone + "/entities", null, this.franz, null)))
			.isEqualTo(404);
		assertThat(status(call(HttpMethod.PUT, VIEWS + "/" + gone, shapes(), this.anna, "\"2\""))).isEqualTo(404);
	}

	@Test
	void withoutEntityReadTheViewAndTheSearchAreForbidden() {
		TestSession nobody = this.principals.personWithoutRole();
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + this.diagram, null, nobody, null))).isEqualTo(403);
		assertThat(status(call(HttpMethod.GET, VIEWS + "/" + this.diagram + "/entities", null, nobody, null)))
			.isEqualTo(403);
	}

	// Requirement: Search entities for a diagram

	@Test
	void searchFindsAllowedEntitiesByNameInOrder() {
		String tag = suffix();
		entity("SAP ERP " + tag, this.component);
		entity("sap BW " + tag, this.process);
		entity("Salesforce " + tag, this.component);
		entity("SAP Process " + tag, this.outsider);
		JsonNode found = ok(call(HttpMethod.GET, search("q=sap&pageSize=200"), null, this.anna, null));
		List<String> names = names(found).stream().filter((name) -> name.endsWith(tag)).toList();
		assertThat(names).containsExactly("sap BW " + tag, "SAP ERP " + tag);
		JsonNode first = found.path("items").get(0);
		assertThat(first.path("metatype").path("abbreviation").asString()).isEqualTo("BP");
		assertThat(first.path("metatype").path("id").asString()).isEqualTo(this.process.toString());
		assertThat(names(ok(call(HttpMethod.GET, search("q=" + tag + "&pageSize=200"), null, this.franz, null))))
			.doesNotContain("SAP Process " + tag);
	}

	@Test
	void searchNarrowsToOneMetatypeAndRejectsOneTheViewpointDoesNotAllow() {
		String tag = suffix();
		entity("Billing " + tag, this.component);
		entity("Invoicing " + tag, this.process);
		JsonNode narrowed = ok(call(HttpMethod.GET, search("q=" + tag + "&metatypeId=" + this.process), null, this.anna,
				null));
		assertThat(names(narrowed)).containsExactly("Invoicing " + tag);
		MvcTestResult rejected = call(HttpMethod.GET, search("metatypeId=" + this.outsider), null, this.anna, null);
		assertThat(status(rejected)).isEqualTo(422);
		assertThat(body(rejected).path("invalidFields").toString()).isEqualTo("[\"metatypeId\"]");
	}

	@Test
	void searchSkipsDeletedEntitiesAndMatchesWildcardsLiterally() {
		String tag = suffix();
		int live = entity("100% Digital " + tag, this.component);
		entity("1000 Days " + tag, this.component);
		entity("a_b " + tag, this.component);
		entity("axb " + tag, this.component);
		int gone = entity("Deleted Digital " + tag, this.component);
		deleteEntity(gone);
		assertThat(names(ok(call(HttpMethod.GET, search("q=0%25%20Digital&pageSize=200"), null, this.franz, null))))
			.contains("100% Digital " + tag)
			.doesNotContain("1000 Days " + tag);
		assertThat(names(ok(call(HttpMethod.GET, search("q=a_b&pageSize=200"), null, this.franz, null))))
			.contains("a_b " + tag)
			.doesNotContain("axb " + tag);
		assertThat(names(ok(call(HttpMethod.GET, search("q=Digital%20" + tag + "&pageSize=200"), null, this.franz, null))))
			.containsExactly("100% Digital " + tag);
		assertThat(live).isPositive();
	}

	@Test
	void searchPagesWithTheirMeta() {
		String tag = suffix();
		for (int i = 0; i < 3; i++) {
			entity("Paged " + i + " " + tag, this.component);
		}
		JsonNode second = ok(call(HttpMethod.GET, search("q=" + tag + "&page=1&pageSize=2"), null, this.franz, null));
		assertThat(names(second)).containsExactly("Paged 2 " + tag);
		assertThat(second.path("page").path("totalElements").asInt()).isEqualTo(3);
		assertThat(second.path("page").path("totalPages").asInt()).isEqualTo(2);
	}

	/** parameter, value length or value, status */
	@ParameterizedTest
	@CsvSource({ "q, 255, 200", "q, 256, 400", "pageSize, 1, 200", "pageSize, 200, 200", "pageSize, 0, 400",
			"pageSize, 201, 400", "page, 0, 200", "page, -1, 400" })
	void searchLimits(String parameter, int value, int status) {
		String query = "q".equals(parameter) ? "q=" + "q".repeat(value) : parameter + "=" + value;
		assertThat(status(call(HttpMethod.GET, search(query), null, this.franz, null))).isEqualTo(status);
	}

	// Requirement: Save the diagram layout

	@Test
	void layoutSavedWithTheNextVersionAndAnEtag() {
		int erp = entity("SAP ERP", this.component);
		String shape = id();
		saveOk(1, shapes(shape(shape, erp, 100, 50, 90, 30)));
		MvcTestResult result = save(2, shapes(shape(shape, erp, 300, 80, 120, 40)), this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"3\"");
		JsonNode view = body(result);
		assertThat(view.path("diagram").path("version").asInt()).isEqualTo(3);
		assertThat(view.path("shapes").get(0).path("x").decimalValue()).isEqualByComparingTo("300");
		assertThat(view.path("shapes").get(0).path("width").decimalValue()).isEqualByComparingTo("120");
		assertThat(version(this.diagram)).isEqualTo(3);
	}

	@Test
	void everyAcceptedSaveRaisesTheVersionAlsoWithoutADifference() {
		int erp = entity("SAP ERP", this.component);
		String shape = id();
		saveOk(1, shapes(shape(shape, erp, 1, 1, 90, 30)));
		saveOk(2, shapes(shape(shape, erp, 1, 1, 90, 30)));
		assertThat(version(this.diagram)).isEqualTo(3);
	}

	@Test
	void anEntityMayHaveSeveralShapesAndTheDrawingOrderIsTheRequestOrder() {
		int erp = entity("SAP ERP", this.component);
		int crm = entity("Salesforce", this.component);
		String a = id();
		String b = id();
		String c = id();
		saveOk(1, shapes(shape(a, erp, 1, 1, 90, 30), shape(b, erp, 2, 2, 90, 30), shape(c, crm, 3, 3, 90, 30)));
		assertThat(shapeIds(view())).containsExactly(a, b, c);
		saveOk(2, shapes(shape(c, crm, 3, 3, 90, 30), shape(a, erp, 1, 1, 90, 30), shape(b, erp, 2, 2, 90, 30)));
		assertThat(shapeIds(view())).containsExactly(c, a, b);
	}

	@Test
	void aShapeRemovedFromTheDiagramLeavesTheEntity() {
		int erp = entity("SAP ERP", this.component);
		String shape = id();
		saveOk(1, shapes(shape(shape, erp, 1, 1, 90, 30)));
		saveOk(2, shapes());
		assertThat(view().path("shapes")).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE id = ? AND deleted_at IS NULL",
				Integer.class, erp)).isOne();
	}

	@Test
	void aNewEntityIsCreatedAndAuditedLikeABatchCreate() {
		String key = id();
		String shape = id();
		ObjectNode body = shapes(newShape(shape, key, 10, 20, 120, 40));
		((ArrayNode) body.withArray("newEntities")).add(newEntity(key, this.component, "CRM Cloud"));
		MvcTestResult result = call(HttpMethod.PUT, VIEWS + "/" + this.diagram, body, this.anna, "\"1\"", "X-Agent-Id",
				"canvas-test");
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		JsonNode view = body(result);
		assertThat(view.path("shapes")).hasSize(1);
		int created = view.path("shapes").get(0).path("entity").path("id").asInt();
		assertThat(view.path("shapes").get(0).path("entity").path("name").asString()).isEqualTo("CRM Cloud");
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, created))
			.isOne();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_version_headers WHERE entity_id = ?",
				Integer.class, created)).isOne();
		JsonNode canvas = auditDetails("entity.created", created);
		assertThat(this.jdbc.queryForObject(
				"SELECT agent_label FROM audit.events WHERE event_type = 'entity.created' AND entity_id = ?",
				String.class, created)).isEqualTo("canvas-test");
		// the same event as the batch writes
		ObjectNode batch = JsonMapper.shared().createObjectNode().put("strategy", "create-only").put("atomic", true);
		batch.putArray("entities")
			.addObject()
			.put("name", "CRM Batch")
			.put("metatypeId", this.component.toString());
		MvcTestResult batched = call(HttpMethod.POST, "/api/v1/entities/batch", batch, this.anna, null);
		assertThat(status(batched)).as(() -> content(batched)).isEqualTo(200);
		int viaBatch = body(batched).path("items").get(0).path("id").asInt();
		JsonNode reference = auditDetails("entity.created", viaBatch);
		assertThat(fieldNames(canvas)).isEqualTo(fieldNames(reference));
		assertThat(canvas.path("version").asInt()).isEqualTo(reference.path("version").asInt());
		assertThat(canvas.path("metatypeId").asString()).isEqualTo(this.component.toString());
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_version_headers WHERE entity_id = ?",
				Integer.class, viaBatch)).isOne();
	}

	@Test
	void aNewEntityOfAMetatypeTheViewpointDoesNotAllowRejectsTheWholeSave() {
		String good = id();
		String bad = id();
		String shapeGood = id();
		String shapeBad = id();
		ObjectNode body = shapes(newShape(shapeGood, good, 1, 1, 90, 30), newShape(shapeBad, bad, 2, 2, 90, 30));
		body.withArray("newEntities").add(newEntity(good, this.component, "Atomic Good"));
		body.withArray("newEntities").add(newEntity(bad, this.outsider, "Atomic Bad"));
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:validation-failed");
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"newEntities[1].metatypeId\"]");
		assertThat(body(result).path("errors").get(0).path("code").asString()).isEqualTo("METATYPE_NOT_ALLOWED");
		assertNothingChanged("Atomic Good", "Atomic Bad");
	}

	@Test
	void aNewEntityWithABlankNameFailsInTheEntityWriteAndRollsBackTheOthers() {
		String good = id();
		String blank = id();
		ObjectNode body = shapes(newShape(id(), good, 1, 1, 90, 30), newShape(id(), blank, 2, 2, 90, 30));
		body.withArray("newEntities").add(newEntity(good, this.component, "Rolled Back"));
		body.withArray("newEntities").add(newEntity(blank, this.component, "   "));
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"newEntities[1].name\"]");
		assertThat(body(result).path("errors").get(0).path("code").asString()).isEqualTo("REQUIRED");
		assertNothingChanged("Rolled Back");
	}

	/** entity kind, expected field of the first error */
	@ParameterizedTest
	@CsvSource({ "outsider, shapes[0].entityId", "connection, shapes[0].entityId", "unknown, shapes[0].entityId",
			"deleted, shapes[0].entityId" })
	void aShapeOfAnEntityThatIsNotAllowedUnknownOrDeletedIsRejected(String kind, String field) {
		int entity = switch (kind) {
			case "outsider" -> entity("Not allowed", this.outsider);
			case "connection" -> entity("A connection", connectionMetatype());
			case "deleted" -> {
				int id = entity("Deleted entity", this.component);
				deleteEntity(id);
				yield id;
			}
			default -> 999_999_999;
		};
		MvcTestResult result = save(1, shapes(shape(id(), entity, 1, 1, 90, 30)), this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
		assertThat(view().path("shapes")).isEmpty();
		assertThat(version(this.diagram)).isOne();
	}

	@Test
	void keysAndEntityReferencesAreChecked() {
		int erp = entity("SAP ERP", this.component);
		String key = id();
		ObjectNode unknownKey = shapes(newShape(id(), id(), 1, 1, 90, 30));
		assertThat(firstField(save(1, unknownKey, this.anna))).isEqualTo("shapes[0].newEntityKey");
		ObjectNode duplicate = shapes(newShape(id(), key, 1, 1, 90, 30));
		duplicate.withArray("newEntities").add(newEntity(key, this.component, "Dup 1"));
		duplicate.withArray("newEntities").add(newEntity(key, this.component, "Dup 2"));
		assertThat(firstField(save(1, duplicate, this.anna))).isEqualTo("newEntities[1].key");
		ObjectNode unused = shapes(shape(id(), erp, 1, 1, 90, 30));
		unused.withArray("newEntities").add(newEntity(key, this.component, "Unused"));
		assertThat(firstField(save(1, unused, this.anna))).isEqualTo("newEntities[0]");
		ObjectNode both = shapes(shape(id(), erp, 1, 1, 90, 30));
		((ObjectNode) both.withArray("shapes").get(0)).put("newEntityKey", key);
		assertThat(firstField(save(1, both, this.anna))).isEqualTo("shapes[0]");
		ObjectNode neither = shapes(shape(id(), erp, 1, 1, 90, 30));
		((ObjectNode) neither.withArray("shapes").get(0)).remove("entityId");
		assertThat(firstField(save(1, neither, this.anna))).isEqualTo("shapes[0]");
		assertNothingChanged("Dup 1", "Dup 2", "Unused");
	}

	@Test
	void aStoredShapeThatKeepsItsEntityIsAConflictWhenTheViewpointNoLongerAllowsItsMetatype() {
		int erp = entity("SAP ERP", this.component);
		String shape = id();
		saveOk(1, shapes(shape(shape, erp, 1, 1, 90, 30)));
		this.jdbc.update("DELETE FROM viewpoint_metatypes WHERE viewpoint_id = ? AND metatype_id = ?", this.viewpoint,
				this.component);
		// the shape that stays is a conflict and blocks the save (change us-074-diagram-viewpoint-change)
		MvcTestResult blocked = save(2, shapes(shape(shape, erp, 7, 7, 90, 30)), this.anna);
		assertThat(status(blocked)).isEqualTo(409);
		// a new shape of the same entity is not accepted
		MvcTestResult added = save(2, shapes(shape(shape, erp, 7, 7, 90, 30), shape(id(), erp, 8, 8, 90, 30)), this.anna);
		assertThat(status(added)).isEqualTo(422);
		assertThat(firstField(added)).isEqualTo("shapes[1].entityId");
		// removing the conflict is accepted
		saveOk(2, shapes());
	}

	@Test
	void aShapeIdOfAnotherDiagramOrADuplicateIsRejected() {
		int erp = entity("SAP ERP", this.component);
		String other = diagram(unique("Other Diagram"));
		String shared = id();
		MvcTestResult otherSave = call(HttpMethod.PUT, VIEWS + "/" + other, shapes(shape(shared, erp, 1, 1, 90, 30)),
				this.anna, "\"1\"");
		assertThat(status(otherSave)).isEqualTo(200);
		MvcTestResult foreign = save(1, shapes(shape(shared, erp, 1, 1, 90, 30)), this.anna);
		assertThat(status(foreign)).isEqualTo(422);
		assertThat(firstField(foreign)).isEqualTo("shapes[0].id");
		String twice = id();
		MvcTestResult duplicate = save(1, shapes(shape(twice, erp, 1, 1, 90, 30), shape(twice, erp, 2, 2, 90, 30)),
				this.anna);
		assertThat(status(duplicate)).isEqualTo(422);
		assertThat(firstField(duplicate)).isEqualTo("shapes[1].id");
		assertThat(view().path("shapes")).isEmpty();
	}

	@Test
	void staleMissingAndForbiddenSavesChangeNothing() {
		int erp = entity("SAP ERP", this.component);
		ObjectNode body = shapes(shape(id(), erp, 1, 1, 90, 30));
		// a rename after the canvas was opened makes the save stale
		MvcTestResult rename = call(HttpMethod.PUT, DIAGRAMS + "/" + this.diagram,
				JsonMapper.shared().createObjectNode().put("name", unique("Renamed")), this.kurt, "\"1\"");
		assertThat(status(rename)).isEqualTo(200);
		assertThat(status(save(1, body, this.anna))).isEqualTo(412);
		assertThat(status(call(HttpMethod.PUT, VIEWS + "/" + this.diagram, body, this.anna, null))).isEqualTo(428);
		assertThat(status(save(2, body, this.franz))).isEqualTo(403);
		assertThat(view().path("shapes")).isEmpty();
		assertThat(version(this.diagram)).isEqualTo(2);
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isZero();
	}

	// Requirement: Shapes of a diagram - limits

	/** field, value, status */
	@ParameterizedTest
	@CsvSource({ "width, 1, 200", "width, 10000, 200", "width, 0.99, 400", "width, 10000.01, 400", "height, 1, 200",
			"height, 10000, 200", "height, 0.99, 400", "height, 10000.01, 400", "x, 100000, 200", "x, -100000, 200",
			"x, 100000.01, 400", "x, -100000.01, 400", "y, 100000, 200", "y, -100000, 200", "y, 100000.01, 400",
			"y, -100000.01, 400", "x, 12.34, 200", "x, 12.345, 400", "width, 90.125, 400", "y, 0.001, 400" })
	void positionAndSizeLimits(String field, String value, int status) {
		int erp = entity("SAP ERP", this.component);
		ObjectNode shape = shape(id(), erp, 10, 10, 90, 30);
		shape.put(field, new BigDecimal(value));
		MvcTestResult result = save(1, shapes(shape), this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(status);
		assertThat(view().path("shapes")).hasSize((status == 200) ? 1 : 0);
	}

	/** shapes, new entities, status */
	@ParameterizedTest
	@CsvSource({ "2000, 0, 200", "2001, 0, 400", "500, 500, 200", "1, 501, 400" })
	void countLimits(int shapeCount, int newEntityCount, int status) {
		int erp = entity("SAP ERP", this.component);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		ArrayNode newEntities = body.putArray("newEntities");
		for (int i = 0; i < newEntityCount; i++) {
			String key = id();
			newEntities.add(newEntity(key, this.component, "Counted " + i));
			shapes.add(newShape(id(), key, i, 0, 90, 30));
		}
		while (shapes.size() < shapeCount) {
			shapes.add(shape(id(), erp, shapes.size(), 0, 90, 30));
		}
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result).substring(0, Math.min(400, content(result).length())))
			.isEqualTo(status);
		assertThat(view().path("shapes")).hasSize((status == 200) ? shapeCount : 0);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name LIKE 'Counted %' AND deleted_at IS NULL", Integer.class))
			.isEqualTo((status == 200) ? newEntityCount : 0);
		if (status == 200) {
			this.jdbc.update("DELETE FROM diagram_shapes WHERE diagram_id = ?::uuid", this.diagram);
			this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE name LIKE 'Counted %'",
					this.kurtId);
		}
	}

	/** name length, status */
	@ParameterizedTest
	@CsvSource({ "255, 200", "256, 400", "1, 200" })
	void newEntityNameLimit(int length, int status) {
		String key = id();
		ObjectNode body = shapes(newShape(id(), key, 1, 1, 90, 30));
		body.withArray("newEntities").add(newEntity(key, this.component, "n".repeat(length)));
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(status);
		assertThat(view().path("shapes")).hasSize((status == 200) ? 1 : 0);
	}

	// Requirement: Incomplete entities

	@Test
	void aNewEntityWithAMissingMandatoryPropertyIsCreatedIncompleteAndMarked() {
		UUID owner = mandatory(this.process, "Process Owner " + suffix());
		String ownerName = this.jdbc.queryForObject("SELECT name FROM property_definitions WHERE id = ?", String.class,
				owner);
		String key = id();
		ObjectNode body = shapes(newShape(id(), key, 1, 1, 90, 30));
		body.withArray("newEntities").add(newEntity(key, this.process, "Invoice Approval"));
		MvcTestResult result = save(1, body, this.anna);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(200);
		JsonNode shape = body(result).path("shapes").get(0);
		int created = shape.path("entity").path("id").asInt();
		assertThat(shape.path("missingMandatory")).hasSize(1);
		assertThat(shape.path("missingMandatory").get(0).asString()).isEqualTo(ownerName);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_property_values WHERE entity_id = ?",
				Integer.class, created)).isZero();
		// completed by a value
		this.jdbc.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, 'Anna')",
				created, owner);
		assertThat(view().path("shapes").get(0).path("missingMandatory")).isEmpty();
	}

	@Test
	void everyOtherWriteOfAnIncompleteEntityStaysStrict() {
		UUID owner = mandatory(this.process, "Process Owner " + suffix());
		String key = id();
		ObjectNode body = shapes(newShape(id(), key, 1, 1, 90, 30));
		body.withArray("newEntities").add(newEntity(key, this.process, "Strict Approval"));
		int created = body(save(1, body, this.anna)).path("shapes").get(0).path("entity").path("id").asInt();
		String externalId = "strict-" + suffix();
		this.jdbc.update("UPDATE entities SET external_id = ? WHERE id = ?", externalId, created);
		ObjectNode batch = JsonMapper.shared().createObjectNode().put("strategy", "upsert").put("atomic", true);
		batch.putArray("entities")
			.addObject()
			.put("name", "Strict Approval")
			.put("externalId", externalId)
			.put("description", "A new description")
			.put("metatypeId", this.process.toString());
		MvcTestResult result = call(HttpMethod.POST, "/api/v1/entities/batch", batch, this.anna, null);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(422);
		assertThat(content(result)).contains("MANDATORY_MISSING");
		assertThat(owner).isNotNull();
	}

	@Test
	void anEntityOfAnotherMetatypeWithAMandatoryPropertyWithADefaultIsComplete() {
		UUID withDefault = mandatory(this.component, "Hosting " + suffix());
		this.jdbc.update("UPDATE metatype_property_mappings SET default_value_text = 'cloud' WHERE property_def_id = ?",
				withDefault);
		String key = id();
		ObjectNode body = shapes(newShape(id(), key, 1, 1, 90, 30));
		body.withArray("newEntities").add(newEntity(key, this.component, "Hosted"));
		JsonNode shape = ok(save(1, body, this.anna)).path("shapes").get(0);
		assertThat(shape.path("missingMandatory")).isEmpty();
	}

	// Requirement: Audit of the layout

	@Test
	void theSaveIsAuditedWithTheCountsAndARejectedOneIsNot() {
		int erp = entity("SAP ERP", this.component);
		int crm = entity("Salesforce", this.component);
		String keep = id();
		String move = id();
		String gone = id();
		saveOk(1, shapes(shape(keep, erp, 1, 1, 90, 30), shape(move, erp, 2, 2, 90, 30), shape(gone, crm, 3, 3, 90, 30)));
		JsonNode first = auditDetails("diagram.layout-saved", this.diagram);
		assertThat(first.path("added").asInt()).isEqualTo(3);
		assertThat(first.path("changed").asInt()).isZero();
		assertThat(first.path("removed").asInt()).isZero();
		assertThat(first.path("entitiesCreated").asInt()).isZero();
		assertThat(first.path("version").asInt()).isEqualTo(2);
		String key = id();
		ObjectNode body = shapes(shape(keep, erp, 1, 1, 90, 30), shape(move, erp, 20, 20, 90, 30),
				newShape(id(), key, 5, 5, 90, 30), newShape(id(), key, 6, 6, 90, 30));
		body.withArray("newEntities").add(newEntity(key, this.component, "Audited New"));
		saveOk(2, body);
		JsonNode second = auditDetails("diagram.layout-saved", this.diagram);
		assertThat(second.path("added").asInt()).isEqualTo(2);
		assertThat(second.path("changed").asInt()).isOne();
		assertThat(second.path("removed").asInt()).isOne();
		assertThat(second.path("entitiesCreated").asInt()).isOne();
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isEqualTo(2);
		// a rejected save is not audited
		assertThat(status(save(3, shapes(shape(id(), 999_999_999, 1, 1, 90, 30)), this.anna))).isEqualTo(422);
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isEqualTo(2);
	}

	@Test
	void aChangedDrawingOrderCountsAsChangedAndSoDoesAnEntityChange() {
		int erp = entity("SAP ERP", this.component);
		int crm = entity("Salesforce", this.component);
		String a = id();
		String b = id();
		saveOk(1, shapes(shape(a, erp, 1, 1, 90, 30), shape(b, erp, 2, 2, 90, 30)));
		saveOk(2, shapes(shape(b, erp, 2, 2, 90, 30), shape(a, erp, 1, 1, 90, 30)));
		assertThat(auditDetails("diagram.layout-saved", this.diagram).path("changed").asInt()).isEqualTo(2);
		saveOk(3, shapes(shape(b, crm, 2, 2, 90, 30), shape(a, erp, 1, 1, 90, 30)));
		assertThat(auditDetails("diagram.layout-saved", this.diagram).path("changed").asInt()).isOne();
	}

	// Requirement: Request size (design D5)

	@Test
	void theLargestSaveWithEscapedNamesIsAcceptedAndOneAboveFourMegabytesAnswers413() {
		int erp = entity("SAP ERP", this.component);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		ArrayNode newEntities = body.putArray("newEntities");
		// control characters are escaped to six bytes each: the body exceeds the default of 1 MB
		String name = "\u0007".repeat(255);
		for (int i = 0; i < 500; i++) {
			String key = id();
			newEntities.add(newEntity(key, this.component, name));
			shapes.add(newShape(id(), key, i, 0, 90, 30));
		}
		while (shapes.size() < 2000) {
			shapes.add(shape(id(), erp, shapes.size(), 0, 90, 30));
		}
		String text = body.toString();
		assertThat(text.length()).isGreaterThan(1024 * 1024).isLessThan(2 * 1024 * 1024);
		MvcTestResult accepted = save(1, body, this.anna);
		assertThat(status(accepted)).as(() -> content(accepted).substring(0, Math.min(300, content(accepted).length())))
			.isEqualTo(200);
		this.jdbc.update("DELETE FROM diagram_shapes WHERE diagram_id = ?::uuid", this.diagram);
		// above the limit
		String padded = text + " ".repeat(4 * 1024 * 1024 - text.length() + 1);
		MvcTestResult tooLarge = this.mvc.put()
			.uri(VIEWS + "/" + this.diagram)
			.cookie(new Cookie(SessionCookie.NAME, this.anna.token()))
			.header(CsrfOriginFilter.HEADER, this.anna.csrfToken())
			.header("If-Match", "\"2\"")
			.contentType("application/json")
			.content(padded)
			.exchange();
		assertThat(status(tooLarge)).isEqualTo(413);
		// the other paths keep their limit of 1 MB
		MvcTestResult other = this.mvc.put()
			.uri(DIAGRAMS + "/" + this.diagram)
			.cookie(new Cookie(SessionCookie.NAME, this.anna.token()))
			.header(CsrfOriginFilter.HEADER, this.anna.csrfToken())
			.header("If-Match", "\"2\"")
			.contentType("application/json")
			.content("{\"name\":\"x\"}" + " ".repeat(1024 * 1024 + 1))
			.exchange();
		assertThat(status(other)).isEqualTo(413);
	}

	// helpers

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

	private String search(String query) {
		return VIEWS + "/" + this.diagram + "/entities?" + query;
	}

	private void assertNothingChanged(String... entityNames) {
		assertThat(view().path("shapes")).isEmpty();
		assertThat(version(this.diagram)).isOne();
		assertThat(auditCount("diagram.layout-saved", this.diagram)).isZero();
		for (String name : entityNames) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = ?", Integer.class, name))
				.as(name)
				.isZero();
		}
	}

	private UUID metatype(String name, String abbreviation) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, abbreviation) VALUES (?::uuid, ?, ?) RETURNING id""", UUID.class,
				INSTANCE, name, abbreviation);
	}

	private UUID connectionMetatype() {
		UUID connection = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES (?::uuid, ?, true) RETURNING id""",
				UUID.class, INSTANCE, "Flow " + suffix());
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", this.viewpoint,
				connection);
		return connection;
	}

	private UUID viewpoint(UUID... metatypes) {
		UUID id = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, 'bpmn2', ?)
				RETURNING id""", UUID.class, unique("Process Model"), this.kurtId);
		for (UUID metatype : metatypes) {
			this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", id, metatype);
		}
		return id;
	}

	private UUID mandatory(UUID metatype, String name) {
		UUID property = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type)
				VALUES (?, 'p_' || substr(md5(?), 1, 12), 'string') RETURNING id""", UUID.class, name, name);
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'mandatory', DATE '2026-01-01', 'organization')""", metatype, property);
		return property;
	}

	private int entity(String name, UUID metatype) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class,
				metatype, name, this.kurtId);
	}

	private void deleteEntity(int id) {
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, id);
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

	private int auditCount(String type, String id) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ? AND target_id = ?",
				Integer.class, type, id);
	}

	private JsonNode auditDetails(String type, Object target) {
		String column = (target instanceof Integer) ? "entity_id" : "target_id";
		String json = this.jdbc.queryForObject("SELECT details::text FROM audit.events WHERE event_type = ? AND "
				+ column + " = ? ORDER BY id DESC LIMIT 1", String.class, type,
				(target instanceof Integer) ? target : target.toString());
		return JsonMapper.shared().readTree(json);
	}

	private static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.propertyNames().forEach(names::add);
		return names.stream().sorted().toList();
	}

	private static List<String> shapeIds(JsonNode view) {
		List<String> ids = new ArrayList<>();
		view.path("shapes").forEach((shape) -> ids.add(shape.path("id").asString()));
		return ids;
	}

	private static List<String> names(JsonNode page) {
		List<String> names = new ArrayList<>();
		page.path("items").forEach((item) -> names.add(item.path("name").asString()));
		return names;
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
			return Files.createTempDirectory("oea-diagram-view").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

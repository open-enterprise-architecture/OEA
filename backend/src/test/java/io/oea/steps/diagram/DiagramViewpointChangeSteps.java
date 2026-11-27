package io.oea.steps.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/diagram/diagram-viewpoint-change.feature (us-074-diagram-viewpoint-change), on the
 * instance of {@link CatalogSteps}. The instance is shared, so every scenario names its viewpoints,
 * diagrams and entities with a token of its own: the feature uses plain names, the steps append the
 * token. The three metatypes "VPC SaaS Application", "VPC Flow" and "VPC Data Flow" are created once
 * for the instance (the starter package has no such types); the scenario removes its diagrams and
 * viewpoints and deletes its entities, so that the shared catalogs and their paging stay as other
 * features expect them. A save always starts from the current view of the diagram.
 */
public class DiagramViewpointChangeSteps {

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final String DIAGRAMS = "/api/v1/diagrams";

	private static final String VIEWPOINTS = "/api/v1/admin/viewpoints";

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private final String token = UUID.randomUUID().toString().substring(0, 8);

	private HttpResponse<String> response;

	private int versionBefore;

	private String viewpointBefore;

	private int auditBefore;

	/** The entity names of the shapes and the kinds of the edges of the last save request, by index. */
	private List<String> sentShapes = new ArrayList<>();

	private List<String> sentEdges = new ArrayList<>();

	/** The data objects of the viewpoints of the count scenarios. */
	private final List<Integer> dataObjects = new ArrayList<>();

	/** Whether the scenario used a name of this class; other scenarios need no clean-up. */
	private boolean used;

	/** The diagrams and viewpoints go for good, the entities are deleted: the shared instance keeps nothing of the scenario. */
	@After
	public void clean() throws Exception {
		if (!this.used || instance() == null) {
			return;
		}
		String like = "'% " + this.token + "'";
		instance().oidc().update("DELETE FROM diagrams WHERE name LIKE " + like);
		for (String table : List.of("viewpoint_metatypes", "viewpoint_connection_types", "viewpoint_mappings",
				"viewpoint_sizes")) {
			instance().oidc()
				.update("DELETE FROM " + table + " WHERE viewpoint_id IN (SELECT id FROM viewpoints WHERE name LIKE "
						+ like + ")");
		}
		instance().oidc().update("DELETE FROM viewpoints WHERE name LIKE " + like);
		instance().oidc()
			.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) WHERE name LIKE "
					+ like + " AND deleted_at IS NULL");
	}

	@ParameterType("with its current version|with a stale version|without a version")
	public String vpcVersion(String version) {
		return version;
	}

	// Given

	@Given("the viewpoint-change viewpoint {string} allows {string} and the connections {string}")
	public void viewpoint(String name, String metatypes, String connections) throws Exception {
		ensureMetatypes();
		String id = instance().oidc()
			.single("INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), '" + n(name)
					+ "', 'generic', (SELECT min(id) FROM persons)) RETURNING id::text");
		for (String metatype : names(metatypes)) {
			instance().oidc()
				.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) SELECT '" + id
						+ "', id FROM metatypes WHERE name = '" + metatype + "'");
		}
		for (String connection : names(connections)) {
			instance().oidc()
				.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) SELECT '" + id
						+ "', id FROM metatypes WHERE name = '" + connection + "'");
		}
	}

	@Given("the viewpoint-change diagram {string} in the viewpoint {string} shows {string} and {string} of {string}, {string} of {string} and {string} of {string} with a {string} from {string} to {string} and a {string} from {string} to {string}")
	public void diagram(String name, String viewpoint, String first, String second, String firstType, String third,
			String thirdType, String fourth, String fourthType, String flow, String flowSource, String flowTarget,
			String dataFlow, String dataSource, String dataTarget) throws Exception {
		createDiagram(name, viewpoint);
		for (String[] entity : new String[][] { { first, firstType }, { second, firstType }, { third, thirdType },
				{ fourth, fourthType } }) {
			insertEntity(entity[0], entity[1]);
		}
		int firstFlow = connection(flow, flowSource, flowTarget);
		int secondFlow = connection(dataFlow, dataSource, dataTarget);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		Map<String, String> ids = new java.util.LinkedHashMap<>();
		double[][] positions = { { 0, 0 }, { 200, 0 }, { 0, 100 }, { 200, 100 } };
		List<String> entities = List.of(first, second, third, fourth);
		for (int i = 0; i < entities.size(); i++) {
			String id = UUID.randomUUID().toString();
			ids.put(entities.get(i), id);
			shapes.add(shape(id, entityId(entities.get(i)), positions[i][0], positions[i][1], 90, 30));
		}
		ArrayNode edges = body.putArray("edges");
		edges.add(edge(UUID.randomUUID().toString(), firstFlow, ids.get(flowSource), ids.get(flowTarget)));
		edges.add(edge(UUID.randomUUID().toString(), secondFlow, ids.get(dataSource), ids.get(dataTarget)));
		put(name, body, instance().architect(), "\"1\"");
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Given("the viewpoint-change diagram {string} is {word}")
	public void state(String name, String state) throws Exception {
		if (state.equals("deleted")) {
			instance().oidc()
				.update("UPDATE diagrams SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) WHERE id = '"
						+ id(name) + "'");
		}
	}

	@Given("{string} has the viewpoint {string}")
	public void hasViewpoint(String name, String viewpoint) throws Exception {
		change(name, viewpointId(viewpoint), "\"" + version(name) + "\"", instance().architect());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Given("the viewpoint-change entities {string} and {string} are deleted")
	public void deleted(String first, String second) throws Exception {
		setDeleted(first, true);
		setDeleted(second, true);
	}

	@Given("the shapes {string} and {string} of {string} are nested in the shape of {string} of {string}")
	public void nested(String first, String second, String type, String container, String diagram) throws Exception {
		insertEntity(first, type);
		insertEntity(second, type);
		JsonNode view = view(diagram, instance().viewer());
		ObjectNode body = draft(view);
		String containerId = null;
		for (JsonNode shape : body.path("shapes")) {
			if (shape.path("entityId").asInt() == entityId(container)) {
				((ObjectNode) shape).put("width", BigDecimal.valueOf(400)).put("height", BigDecimal.valueOf(300));
				containerId = shape.path("id").asString();
			}
		}
		ArrayNode shapes = (ArrayNode) body.path("shapes");
		shapes.add(shape(UUID.randomUUID().toString(), entityId(first), 10, 110, 90, 30).put("parentShapeId", containerId));
		shapes.add(shape(UUID.randomUUID().toString(), entityId(second), 120, 110, 90, 30).put("parentShapeId",
				containerId));
		put(diagram, body, instance().architect(), "\"" + version(diagram) + "\"");
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Given("the viewpoint-change viewpoint {string} is used by {int} diagrams, {int} of which show {string} and {int} an edge of {string}")
	public void used(String viewpoint, int diagrams, int withData, String data, int withEdge, String flow)
			throws Exception {
		viewpoint(viewpoint, "Application Component, " + data, flow);
		for (int i = 1; i <= diagrams; i++) {
			String name = viewpoint + " " + i;
			createDiagram(name, viewpoint);
			ObjectNode body = JsonMapper.shared().createObjectNode();
			ArrayNode shapes = body.putArray("shapes");
			if (i <= withData) {
				insertEntity("Process DB " + i, data);
				this.dataObjects.add(entityId("Process DB " + i));
				shapes.add(shape(UUID.randomUUID().toString(), entityId("Process DB " + i), 0, 0, 90, 30));
			}
			else if (i <= withData + withEdge) {
				insertEntity("Process A " + i, "Application Component");
				insertEntity("Process B " + i, "Application Component");
				int connection = connection(flow, "Process A " + i, "Process B " + i);
				String source = UUID.randomUUID().toString();
				String target = UUID.randomUUID().toString();
				shapes.add(shape(source, entityId("Process A " + i), 0, 0, 90, 30));
				shapes.add(shape(target, entityId("Process B " + i), 200, 0, 90, 30));
				body.putArray("edges").add(edge(UUID.randomUUID().toString(), connection, source, target));
			}
			else {
				insertEntity("Process C " + i, "Application Component");
				shapes.add(shape(UUID.randomUUID().toString(), entityId("Process C " + i), 0, 0, 90, 30));
			}
			put(name, body, instance().architect(), "\"1\"");
			assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		}
	}

	@Given("the entities of the data objects of {string} are deleted")
	public void dataObjectsDeleted(String viewpoint) throws Exception {
		for (int entity : this.dataObjects) {
			instance().oidc()
				.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) WHERE id = "
						+ entity);
		}
	}

	// When

	@When("the {role} changes {string} to {string} {vpcVersion}")
	public void changes(Session session, String name, String viewpoint, String version) throws Exception {
		snapshot(name);
		String target = viewpoint.equals("unknown") ? UUID.randomUUID().toString() : viewpointId(viewpoint);
		String ifMatch = switch (version) {
			case "with a stale version" -> "\"" + (version(name) - 1) + "\"";
			case "without a version" -> null;
			default -> "\"" + version(name) + "\"";
		};
		change(name, target, ifMatch, session);
	}

	@When("the enterprise architect renames {string} with the viewpoint {string} in the request")
	public void renames(String name, String viewpoint) throws Exception {
		snapshot(name);
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", n(name))
			.put("viewpointId", viewpointId(viewpoint));
		this.response = instance().send("PUT", DIAGRAMS + "/" + id(name), body.toString(), instance().architect(),
				Map.of("If-Match", "\"" + version(name) + "\""));
	}

	@When("the enterprise architect removes {string} from the viewpoint {string}")
	public void removes(String metatype, String viewpoint) throws Exception {
		editViewpoint(viewpoint, Set.of(), Set.copyOf(names(metatype)));
	}

	@When("the enterprise architect allows {string} in the viewpoint {string}")
	public void allows(String metatypes, String viewpoint) throws Exception {
		editViewpoint(viewpoint, Set.copyOf(names(metatypes)), Set.of());
	}

	@When("the viewpoint-change entities {string} and {string} are restored")
	public void restored(String first, String second) throws Exception {
		setDeleted(first, false);
		setDeleted(second, false);
	}

	@When("the {role} saves {string} moving {string} by {int} pt")
	public void savesMoving(Session session, String name, String entity, int by) throws Exception {
		save(name, session, (body) -> move(body, entity, by));
	}

	@When("the {role} saves {string} without any change")
	public void savesUnchanged(Session session, String name) throws Exception {
		save(name, session, (body) -> {
		});
	}

	@When("the {role} saves {string} without {string}")
	public void savesWithout(Session session, String name, String entities) throws Exception {
		save(name, session, (body) -> remove(body, names(entities), false));
	}

	@When("the {role} saves {string} without {string} and the shapes nested in them")
	public void savesWithoutNested(Session session, String name, String entities) throws Exception {
		save(name, session, (body) -> remove(body, names(entities), true));
	}

	@When("the {role} saves {string} without {string} moving {string} by {int} pt")
	public void savesWithoutMoving(Session session, String name, String entities, String moved, int by)
			throws Exception {
		save(name, session, (body) -> {
			remove(body, names(entities), false);
			move(body, moved, by);
		});
	}

	@When("the {role} saves {string} with a new shape of an unknown entity")
	public void savesUnknown(Session session, String name) throws Exception {
		save(name, session, (body) -> ((ArrayNode) body.path("shapes"))
			.add(shape(UUID.randomUUID().toString(), 2_000_000_000, 5, 5, 90, 30)));
	}

	@When("the {role} saves {string} changing the description of {string}")
	public void savesDescription(Session session, String name, String entity) throws Exception {
		save(name, session, (body) -> body.putArray("entityUpdates")
			.addObject()
			.put("entityId", entityIdUnchecked(entity))
			.put("expectedVersion", entityVersion(entity))
			.put("description", "changed"));
	}

	@When("the {role} saves {string} without {string} adding a shape of the new entity {string} of {string}")
	public void savesNew(Session session, String name, String entities, String entity, String type) throws Exception {
		save(name, session, (body) -> {
			remove(body, names(entities), false);
			String key = UUID.randomUUID().toString();
			try {
				body.putArray("newEntities")
					.addObject()
					.put("key", key)
					.put("metatypeId", instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + type + "'"))
					.put("name", n(entity));
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
			((ArrayNode) body.path("shapes")).add(JsonMapper.shared()
				.createObjectNode()
				.put("id", UUID.randomUUID().toString())
				.put("newEntityKey", key)
				.put("x", BigDecimal.valueOf(5))
				.put("y", BigDecimal.valueOf(5))
				.put("width", BigDecimal.valueOf(90))
				.put("height", BigDecimal.valueOf(30)));
		});
	}

	@When("the {role} asks how many diagrams of {string} are affected by removing {string}")
	public void asks(Session session, String viewpoint, String removed) throws Exception {
		String id = viewpointId(viewpoint);
		Set<String> removedNames = new HashSet<>(names(removed));
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode types = body.putArray("metatypeIds");
		for (String type : instance().oidc()
			.column("SELECT m.id::text FROM viewpoint_metatypes l JOIN metatypes m ON m.id = l.metatype_id WHERE l.viewpoint_id = '"
					+ id + "' AND m.name <> ALL ('{" + String.join(",", removedNames.stream().map((x) -> "\"" + x + "\"").toList()) + "}')")) {
			types.add(type);
		}
		ArrayNode connections = body.putArray("connectionTypeIds");
		for (String type : instance().oidc()
			.column("SELECT m.id::text FROM viewpoint_connection_types l JOIN metatypes m ON m.id = l.metatype_id WHERE l.viewpoint_id = '"
					+ id + "' AND m.name <> ALL ('{" + String.join(",", removedNames.stream().map((x) -> "\"" + x + "\"").toList()) + "}')")) {
			connections.add(type);
		}
		this.response = instance().send("POST", VIEWPOINTS + "/" + id + "/affected-diagrams/query", body.toString(),
				session);
	}

	@When("the {role} asks how many diagrams of {string} are affected with {int} metatype IDs and {int} connection type IDs")
	public void asksLimits(Session session, String viewpoint, int types, int connections) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode metatypeIds = body.putArray("metatypeIds");
		for (int i = 0; i < types; i++) {
			metatypeIds.add(UUID.randomUUID().toString());
		}
		ArrayNode connectionIds = body.putArray("connectionTypeIds");
		for (int i = 0; i < connections; i++) {
			connectionIds.add(UUID.randomUUID().toString());
		}
		this.response = instance().send("POST", VIEWPOINTS + "/" + viewpointId(viewpoint) + "/affected-diagrams/query",
				body.toString(), session);
	}

	// Then

	@Then("the viewpoint-change answer is {int}")
	public void answer(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the viewpoint-change answer is 409 diagram-conflicts")
	public void conflicts() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(409);
		assertThat(json().path("type").asString()).endsWith("diagram-conflicts");
	}

	@Then("the viewpoint-change answer reports {int} conflicts")
	public void reports(int count) {
		assertThat(json().path("conflictCount").asInt()).isEqualTo(count);
	}

	@Then("the conflicts name the shapes {string} and {int} edge(s)")
	public void conflictNames(String entities, int edges) {
		Set<String> shapes = new LinkedHashSet<>();
		int edgeCount = 0;
		for (JsonNode field : json().path("invalidFields")) {
			String text = field.asString();
			int index = Integer.parseInt(text.replaceAll("\\D", ""));
			if (text.startsWith("shapes[")) {
				shapes.add(this.sentShapes.get(index));
			}
			else {
				edgeCount++;
			}
		}
		assertThat(shapes).containsExactlyInAnyOrderElementsOf(names(entities));
		assertThat(edgeCount).isEqualTo(edges);
	}

	@Then("{string} has the viewpoint {string} at the next version")
	public void viewpointAtNext(String name, String viewpoint) throws Exception {
		assertThat(instance().oidc().single("SELECT viewpoint_id::text FROM diagrams WHERE id = '" + id(name) + "'"))
			.isEqualTo(viewpointId(viewpoint));
		assertThat(version(name)).isEqualTo(this.versionBefore + 1);
	}

	@Then("{string} is unchanged")
	public void unchanged(String name) throws Exception {
		assertThat(version(name)).isEqualTo(this.versionBefore);
		assertThat(instance().oidc().single("SELECT viewpoint_id::text FROM diagrams WHERE id = '" + id(name) + "'"))
			.isEqualTo(this.viewpointBefore);
		assertThat(auditCount(name)).isEqualTo(this.auditBefore);
	}

	@Then("{string} still shows {int} shapes and {int} edges")
	public void shows(String name, int shapes, int edges) throws Exception {
		JsonNode view = view(name, instance().viewer());
		assertThat(view.path("shapes")).hasSize(shapes);
		assertThat(view.path("edges")).hasSize(edges);
	}

	@Then("the audit trail has one event {string} for {string} with the conflicts {int}")
	public void audit(String type, String name, int conflicts) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '" + type + "' AND target_id = '" + id(name)
					+ "'"))
			.isEqualTo("1");
		assertThat(instance().oidc()
			.single("SELECT details ->> 'conflicts' FROM audit.events WHERE event_type = '" + type + "' AND target_id = '"
					+ id(name) + "'"))
			.isEqualTo(Integer.toString(conflicts));
	}

	@Then("the view of {string} marks the shapes {string} as conflicts")
	public void marks(String name, String entities) throws Exception {
		Set<String> found = new LinkedHashSet<>();
		for (JsonNode shape : view(name, instance().viewer()).path("shapes")) {
			if (shape.path("conflict").asBoolean()) {
				found.add(plain(shape.path("entity").path("name").asString()));
			}
		}
		assertThat(found).containsExactlyInAnyOrderElementsOf(names(entities));
	}

	@Then("the view of {string} marks {int} edge(s) as a conflict")
	public void marksEdges(String name, int count) throws Exception {
		int found = 0;
		for (JsonNode edge : view(name, instance().viewer()).path("edges")) {
			if (edge.path("conflict").asBoolean()) {
				found++;
			}
		}
		assertThat(found).isEqualTo(count);
	}

	@Then("the view of {string} has the conflict count {int}")
	public void count(String name, int count) throws Exception {
		assertThat(view(name, instance().viewer()).path("conflictCount").asInt()).isEqualTo(count);
	}

	@Then("the viewer sees the view of {string} with the conflict count {int}")
	public void viewerSees(String name, int count) throws Exception {
		JsonNode view = view(name, instance().viewer());
		assertThat(view.path("conflictCount").asInt()).isEqualTo(count);
		assertThat(view.path("shapes").findValuesAsString("conflict")).isNotNull();
	}

	@Then("the shape of {string} in {string} is no conflict")
	public void noConflict(String entity, String name) throws Exception {
		for (JsonNode shape : view(name, instance().viewer()).path("shapes")) {
			if (plain(shape.path("entity").path("name").asString()).equals(entity)) {
				assertThat(shape.path("conflict").asBoolean()).isFalse();
				return;
			}
		}
		throw new AssertionError("no shape of " + entity);
	}

	@Then("the entity {string} has no description")
	public void noDescription(String entity) throws Exception {
		assertThat(instance().oidc().single("SELECT coalesce(description, '') FROM entities WHERE id = " + entityId(entity)))
			.isEmpty();
	}

	@Then("the affected count is {int}")
	public void affected(int count) {
		assertThat(json().path("count").asInt()).as(this.response.body()).isEqualTo(count);
	}

	// helpers

	private void save(String name, Session session, Consumer<ObjectNode> change) throws Exception {
		snapshot(name);
		ObjectNode body = draft(view(name, instance().viewer()));
		change.accept(body);
		this.sentShapes = new ArrayList<>();
		for (JsonNode shape : body.path("shapes")) {
			this.sentShapes.add(shape.has("entityId") ? plain(entityName(shape.path("entityId").asInt())) : "new");
		}
		put(name, body, session, "\"" + version(name) + "\"");
	}

	/** The save request of the current view: the shapes with their containers and the edges. */
	private ObjectNode draft(JsonNode view) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		for (JsonNode shape : view.path("shapes")) {
			ObjectNode kept = shape(shape.path("id").asString(), shape.path("entity").path("id").asInt(),
					shape.path("x").doubleValue(), shape.path("y").doubleValue(), shape.path("width").doubleValue(),
					shape.path("height").doubleValue());
			if (!shape.path("parentShapeId").isNull()) {
				kept.put("parentShapeId", shape.path("parentShapeId").asString());
			}
			shapes.add(kept);
		}
		ArrayNode edges = body.putArray("edges");
		for (JsonNode edge : view.path("edges")) {
			ObjectNode kept = edge(edge.path("id").asString(), edge.path("connection").path("id").asInt(),
					edge.path("sourceShapeId").asString(), edge.path("targetShapeId").asString());
			kept.set("points", edge.path("points"));
			edges.add(kept);
		}
		return body;
	}

	private void move(ObjectNode body, String entity, int by) {
		for (JsonNode shape : body.path("shapes")) {
			if (shape.path("entityId").asInt() == entityIdUnchecked(entity)) {
				((ObjectNode) shape).put("x", shape.path("x").decimalValue().add(BigDecimal.valueOf(by)));
			}
		}
	}

	/** Removes the shapes of the entities, the edges of removed shapes and, if asked, the nested shapes. */
	private void remove(ObjectNode body, List<String> entities, boolean nested) {
		Set<String> removed = new HashSet<>();
		Set<Integer> ids = new HashSet<>();
		entities.forEach((entity) -> ids.add(entityIdUnchecked(entity)));
		for (JsonNode shape : body.path("shapes")) {
			if (ids.contains(shape.path("entityId").asInt())) {
				removed.add(shape.path("id").asString());
			}
		}
		boolean grew = nested;
		while (grew) {
			grew = false;
			for (JsonNode shape : body.path("shapes")) {
				if (shape.has("parentShapeId") && removed.contains(shape.path("parentShapeId").asString())
						&& removed.add(shape.path("id").asString())) {
					grew = true;
				}
			}
		}
		ArrayNode shapes = JsonMapper.shared().createArrayNode();
		body.path("shapes").forEach((shape) -> {
			if (!removed.contains(shape.path("id").asString())) {
				shapes.add(shape);
			}
		});
		ArrayNode edges = JsonMapper.shared().createArrayNode();
		body.path("edges").forEach((edge) -> {
			if (!removed.contains(edge.path("sourceShapeId").asString())
					&& !removed.contains(edge.path("targetShapeId").asString())) {
				edges.add(edge);
			}
		});
		body.set("shapes", shapes);
		body.set("edges", edges);
	}

	private void editViewpoint(String viewpoint, Set<String> add, Set<String> remove) throws Exception {
		String id = viewpointId(viewpoint);
		HttpResponse<String> read = instance().send("GET", VIEWPOINTS + "/" + id, null, instance().architect());
		assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
		JsonNode current = JsonMapper.shared().readTree(read.body());
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", current.path("name").asString())
			.put("notation", current.path("notation").asString());
		Set<String> types = new LinkedHashSet<>();
		current.path("allowedMetatypes").forEach((type) -> types.add(type.path("name").asString()));
		types.addAll(add);
		types.removeAll(remove);
		ArrayNode ids = body.putArray("metatypeIds");
		for (String type : types) {
			ids.add(instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + type + "'"));
		}
		ArrayNode connections = body.putArray("connectionTypeIds");
		current.path("allowedRelations").forEach((type) -> connections.add(type.path("id").asString()));
		this.response = instance().send("PUT", VIEWPOINTS + "/" + id, body.toString(), instance().architect(),
				Map.of("If-Match", "\"" + current.path("version").asInt() + "\""));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	private void change(String name, String viewpointId, String ifMatch, Session session) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("viewpointId", viewpointId);
		this.response = instance().send("PUT", DIAGRAMS + "/" + id(name) + "/viewpoint", body.toString(), session,
				(ifMatch != null) ? Map.of("If-Match", ifMatch) : Map.of());
	}

	private void put(String name, ObjectNode body, Session session, String ifMatch) throws Exception {
		this.response = instance().send("PUT", VIEWS + "/" + id(name), body.toString(), session,
				(ifMatch != null) ? Map.of("If-Match", ifMatch) : Map.of());
	}

	private void createDiagram(String name, String viewpoint) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", n(name)).put("viewpointId", viewpointId(viewpoint));
		this.response = instance().send("POST", DIAGRAMS, body.toString(), instance().architect());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	private void snapshot(String name) throws Exception {
		this.versionBefore = version(name);
		this.viewpointBefore = instance().oidc().single("SELECT viewpoint_id::text FROM diagrams WHERE id = '" + id(name) + "'");
		this.auditBefore = auditCount(name);
	}

	private int auditCount(String name) throws Exception {
		return Integer.parseInt(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE target_id = '" + id(name)
					+ "' AND event_type IN ('diagram.viewpoint-changed', 'diagram.layout-saved', 'diagram.updated')"));
	}

	private JsonNode view(String name, Session session) throws Exception {
		HttpResponse<String> read = instance().send("GET", VIEWS + "/" + id(name), null, session);
		assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(read.body());
	}

	private void ensureMetatypes() throws Exception {
		for (String[] type : new String[][] { { "VPC SaaS Application", "VPCSA", "false" },
				{ "VPC Flow", "VPCFL", "true" }, { "VPC Data Flow", "VPCDF", "true" } }) {
			instance().oidc()
				.update("INSERT INTO metatypes (config_id, name, abbreviation, is_connection) VALUES ('" + INSTANCE + "', '"
						+ type[0] + "', '" + type[1] + "', " + type[2] + ") ON CONFLICT (config_id, name) DO NOTHING");
		}
	}

	private void insertEntity(String name, String metatype) throws Exception {
		instance().oidc()
			.update("INSERT INTO entities (metatype_id, name, created_by) SELECT m.id, '" + n(name)
					+ "', (SELECT min(id) FROM persons) FROM metatypes m WHERE m.name = '" + metatype
					+ "' AND NOT EXISTS (SELECT 1 FROM entities WHERE name = '" + n(name) + "')");
	}

	/** Creates a connection of the type between two entities of the scenario; returns its entity ID. */
	private int connection(String type, String source, String target) throws Exception {
		String name = type + " " + source + " to " + target;
		insertEntity(name, type);
		int id = entityId(name);
		instance().oidc()
			.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (" + id + ", "
					+ entityId(source) + ", " + entityId(target) + ")");
		return id;
	}

	private void setDeleted(String name, boolean deleted) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET deleted_at = " + (deleted ? "now()" : "NULL") + ", deleted_by = "
					+ (deleted ? "(SELECT min(id) FROM persons)" : "NULL") + " WHERE id = " + entityId(name));
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

	private int entityId(String name) throws Exception {
		return Integer.parseInt(instance().oidc()
			.single("SELECT id::text FROM entities WHERE name = '" + n(name) + "' ORDER BY id DESC LIMIT 1"));
	}

	private int entityIdUnchecked(String name) {
		try {
			return entityId(name);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String entityName(int id) {
		try {
			return instance().oidc().single("SELECT name FROM entities WHERE id = " + id);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private int entityVersion(String entity) {
		try {
			return Integer.parseInt(instance().oidc().single("SELECT version::text FROM entities WHERE id = " + entityId(entity)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String id(String diagram) throws Exception {
		return instance().oidc().single("SELECT id::text FROM diagrams WHERE name = '" + n(diagram) + "'");
	}

	private int version(String diagram) throws Exception {
		return Integer.parseInt(instance().oidc().single("SELECT version::text FROM diagrams WHERE name = '" + n(diagram) + "'"));
	}

	private String viewpointId(String viewpoint) throws Exception {
		return instance().oidc().single("SELECT id::text FROM viewpoints WHERE name = '" + n(viewpoint) + "'");
	}

	/** The comma separated plain names of a step argument; empty for none. */
	private static List<String> names(String list) {
		return list.isBlank() ? List.of() : java.util.Arrays.stream(list.split(",")).map(String::strip).toList();
	}

	/** The name with the token of the scenario, so that the shared instance keeps the scenarios apart. */
	private String n(String name) {
		this.used = true;
		return name + " " + this.token;
	}

	/** The plain name of an entity of the scenario. */
	private String plain(String name) {
		return (name != null && name.endsWith(" " + this.token)) ? name.substring(0, name.length() - this.token.length() - 1) : name;
	}

	private JsonNode json() {
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

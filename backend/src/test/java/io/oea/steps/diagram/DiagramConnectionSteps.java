package io.oea.steps.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/diagram/diagram-connections.feature (us-074-diagram-connections), on the
 * instance of {@link CatalogSteps}. The shapes and entities are those of the canvas steps; the
 * connection types, connections and diagrams carry the prefix "Dcn". A save reads the shapes and
 * edges of the diagram from its view and changes them, so that the steps keep no layout of their own.
 */
public class DiagramConnectionSteps {

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private HttpResponse<String> response;

	// Given

	@Given("the diagram element metatypes {stringList} exist")
	public void elementMetatypes(List<String> names) throws Exception {
		for (String name : names) {
			instance().oidc()
				.update("INSERT INTO metatypes (config_id, name, abbreviation) VALUES ('" + INSTANCE + "', '"
						+ quoted(name) + "', 'DC') ON CONFLICT DO NOTHING");
		}
	}

	@Given("the connection type {string} joins {string} and {string}")
	public void connectionType(String name, String source, String target) throws Exception {
		instance().oidc()
			.update("INSERT INTO metatypes (config_id, name, is_connection) VALUES ('" + INSTANCE + "', '" + quoted(name)
					+ "', true) ON CONFLICT DO NOTHING");
		instance().oidc()
			.update("INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id) "
					+ "SELECT c.id, 'source', m.id FROM metatypes c, metatypes m WHERE c.name = '" + quoted(name)
					+ "' AND m.name = '" + quoted(source) + "' ON CONFLICT DO NOTHING");
		instance().oidc()
			.update("INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id) "
					+ "SELECT c.id, 'target', m.id FROM metatypes c, metatypes m WHERE c.name = '" + quoted(name)
					+ "' AND m.name = '" + quoted(target) + "' ON CONFLICT DO NOTHING");
	}

	@Given("the diagram viewpoint {string} allows the connection types {stringList}")
	public void allowsConnections(String viewpoint, List<String> types) throws Exception {
		for (String type : types) {
			instance().oidc()
				.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) SELECT v.id, m.id FROM "
						+ "viewpoints v, metatypes m WHERE v.name = '" + quoted(viewpoint) + "' AND m.name = '"
						+ quoted(type) + "' ON CONFLICT DO NOTHING");
		}
	}

	@Given("the connection {string} of the type {string} runs from {string} to {string}")
	public void connection(String name, String type, String source, String target) throws Exception {
		instance().oidc()
			.update("INSERT INTO entities (metatype_id, name, created_by) SELECT m.id, '" + quoted(name)
					+ "', (SELECT min(id) FROM persons) FROM metatypes m WHERE m.name = '" + quoted(type)
					+ "' AND NOT EXISTS (SELECT 1 FROM entities WHERE name = '" + quoted(name)
					+ "' AND deleted_at IS NULL)");
		instance().oidc()
			.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES ("
					+ entityId(name) + ", " + entityId(source) + ", " + entityId(target) + ") ON CONFLICT DO NOTHING");
	}

	@Given("the diagram {string} shows the connection {string}")
	public void shows(String diagram, String connection) throws Exception {
		draw(diagram, endsOf(diagram, connection));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Given("the connection type {string} is the scoping type of the property {string}")
	public void scoping(String type, String property) throws Exception {
		instance().oidc()
			.update("INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, "
					+ "scoping_connection_type_id) SELECT '" + quoted(property) + "', 'p_' || substr(md5('"
					+ quoted(property) + "'), 1, 12), 'string', 'connection-scoped', id FROM metatypes WHERE name = '"
					+ quoted(type) + "' ON CONFLICT DO NOTHING");
	}

	// When

	@When("the connection {string} is deleted")
	public void deleted(String name) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) WHERE name = '"
					+ quoted(name) + "'");
	}

	@When("the connection {string} is restored")
	public void restored(String name) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET deleted_at = NULL, deleted_by = NULL WHERE name = '" + quoted(name) + "'");
	}

	@When("the enterprise architect draws an edge of {string} from shape {int} of {string} to shape {int} of {string} in {string} and saves")
	public void drawsEdge(String connection, int sourceShape, String source, int targetShape, String target,
			String diagram) throws Exception {
		draw(diagram, new Ends(entityId(connection), shapeId(diagram, source, sourceShape),
				shapeId(diagram, target, targetShape)));
	}

	@When("an API call saves an edge of {string} from the shape of {string} to the shape of {string} in {string}")
	public void savesEdge(String connection, String source, String target, String diagram) throws Exception {
		draw(diagram, new Ends(entityId(connection), shapeId(diagram, source, 1), shapeId(diagram, target, 1)));
	}

	@When("an API call saves an edge of {string} between unknown shapes in {string}")
	public void savesUnknown(String connection, String diagram) throws Exception {
		draw(diagram, new Ends(entityId(connection), UUID.randomUUID().toString(), UUID.randomUUID().toString()));
	}

	@When("an API call saves an edge in {string} with the ID of an edge of {string}")
	public void foreignId(String diagram, String owner) throws Exception {
		JsonNode edge = edgesOf(owner).get(0);
		Ends ends = endsOf(diagram, edge.path("connection").path("name").asString());
		ObjectNode body = baseBody(diagram, false);
		body.withArray("edges")
			.add(edge(edge.path("id").asString(), ends.connectionId(), ends.sourceShapeId(), ends.targetShapeId()));
		put(diagram, body);
	}

	@When("an API call saves two edges of {string} with the same ID in {string}")
	public void twoEdges(String connection, String diagram) throws Exception {
		Ends ends = endsOf(diagram, connection);
		String id = UUID.randomUUID().toString();
		ObjectNode body = baseBody(diagram, false);
		body.withArray("edges")
			.add(edge(id, ends.connectionId(), ends.sourceShapeId(), ends.targetShapeId()))
			.add(edge(id, ends.connectionId(), ends.sourceShapeId(), ends.targetShapeId()));
		put(diagram, body);
	}

	@When("the enterprise architect draws a new connection of the type {string} from the shape of {string} to the shape of {string} in {string} and saves")
	public void drawsNew(String type, String source, String target, String diagram) throws Exception {
		newConnection(type, source, target, diagram);
	}

	@When("an API call saves a new connection of the type {string} from the shape of {string} to the shape of {string} in {string}")
	public void savesNew(String type, String source, String target, String diagram) throws Exception {
		newConnection(type, source, target, diagram);
	}

	@When("the enterprise architect draws a new connection of the type {string} from the shape of {string} to the shape of {string} in {string}, removes the edge of {string} and saves")
	public void drawsAndRemoves(String type, String source, String target, String diagram, String removed)
			throws Exception {
		ObjectNode body = baseBody(diagram, true);
		ArrayNode kept = JsonMapper.shared().createArrayNode();
		for (JsonNode edge : body.withArray("edges")) {
			if (entityId(removed) != edge.path("connectionId").asInt()) {
				kept.add(edge);
			}
		}
		body.set("edges", kept);
		String key = UUID.randomUUID().toString();
		body.withArray("newConnections").addObject().put("key", key).put("metatypeId", metatypeId(type));
		ObjectNode edge = edge(UUID.randomUUID().toString(), null, shapeId(diagram, source, 1),
				shapeId(diagram, target, 1));
		edge.put("newConnectionKey", key);
		body.withArray("edges").add(edge);
		put(diagram, body);
	}

	@When("an API call saves a new connection of the type {string} without an edge in {string}")
	public void withoutEdge(String type, String diagram) throws Exception {
		ObjectNode body = baseBody(diagram, true);
		body.withArray("newConnections").addObject().put("key", UUID.randomUUID().toString()).put("metatypeId",
				metatypeId(type));
		put(diagram, body);
	}

	@When("the enterprise architect places two new {string} entities {string} and {string} connected with {string} in {string} and saves")
	public void betweenNew(String metatype, String first, String second, String type, String diagram) throws Exception {
		ObjectNode body = baseBody(diagram, true);
		String keyA = UUID.randomUUID().toString();
		String keyB = UUID.randomUUID().toString();
		String shapeA = UUID.randomUUID().toString();
		String shapeB = UUID.randomUUID().toString();
		body.withArray("shapes").add(newShape(shapeA, keyA, 10)).add(newShape(shapeB, keyB, 300));
		body.withArray("newEntities")
			.addObject()
			.put("key", keyA)
			.put("metatypeId", metatypeId(metatype))
			.put("name", first);
		body.withArray("newEntities")
			.addObject()
			.put("key", keyB)
			.put("metatypeId", metatypeId(metatype))
			.put("name", second);
		String key = UUID.randomUUID().toString();
		body.withArray("newConnections").addObject().put("key", key).put("metatypeId", metatypeId(type));
		ObjectNode edge = edge(UUID.randomUUID().toString(), null, shapeA, shapeB);
		edge.put("newConnectionKey", key);
		body.withArray("edges").add(edge);
		put(diagram, body);
	}

	@When("the enterprise architect saves the layout of {string} keeping its edges")
	public void keeping(String diagram) throws Exception {
		put(diagram, baseBody(diagram, true));
	}

	@When("the enterprise architect removes the edges from {string} and saves")
	public void removesEdges(String diagram) throws Exception {
		put(diagram, baseBody(diagram, false));
	}

	@When("the enterprise architect removes the shape of {string} from {string} and its edges and saves")
	public void removesShape(String entity, String diagram) throws Exception {
		ObjectNode body = baseBody(diagram, false);
		String removed = shapeId(diagram, entity, 1);
		ArrayNode kept = JsonMapper.shared().createArrayNode();
		body.withArray("shapes").forEach((shape) -> {
			if (!removed.equals(shape.path("id").asString())) {
				kept.add(shape);
			}
		});
		body.set("shapes", kept);
		ArrayNode edges = JsonMapper.shared().createArrayNode();
		for (JsonNode edge : edgesOf(diagram)) {
			if (!removed.equals(edge.path("sourceShapeId").asString())
					&& !removed.equals(edge.path("targetShapeId").asString())) {
				edges.add(edge(edge.path("id").asString(), edge.path("connection").path("id").asInt(),
						edge.path("sourceShapeId").asString(), edge.path("targetShapeId").asString()));
			}
		}
		body.set("edges", edges);
		put(diagram, body);
	}

	@When("the enterprise architect saves {string} with the description of the connection {string} changed to {string}")
	public void changesConnection(String diagram, String connection, String description) throws Exception {
		ObjectNode body = baseBody(diagram, true);
		body.putArray("entityUpdates")
			.addObject()
			.put("entityId", entityId(connection))
			.put("expectedVersion", entityVersion(connection))
			.put("description", description);
		put(diagram, body);
	}

	@When("the viewer asks {string} for the connections of {string} among {string}")
	public void asks(String diagram, String placed, String shown) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("entityIds").add(entityId(placed));
		body.putArray("shownEntityIds").add(entityId(shown));
		this.response = instance().send("POST", VIEWS + "/" + id(diagram) + "/connections/query", body.toString(),
				instance().viewer(), Map.of());
	}

	// Then

	@Then("the connection answer is {int}")
	public void status(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the connection answer is {int} naming {string}")
	public void statusNaming(int status, String field) {
		status(status);
		List<String> fields = new ArrayList<>();
		json().path("invalidFields").forEach((name) -> fields.add(name.asString()));
		assertThat(fields).anyMatch((name) -> name.equals(field) || name.startsWith(field + "."));
	}

	@Then("the connection answer names the code {string}")
	public void code(String code) {
		assertThat(json().path("errors").toString()).contains("\"" + code + "\"");
	}

	@Then("the view of {string} has an edge of {string} from the shape of {string} to the shape of {string}")
	public void hasEdge(String diagram, String connection, String source, String target) throws Exception {
		List<JsonNode> shapes = new ArrayList<>();
		viewOf(diagram).path("shapes").forEach(shapes::add);
		boolean found = false;
		for (JsonNode edge : edgesOf(diagram)) {
			if (connection.equals(edge.path("connection").path("name").asString())
					&& edge.path("sourceShapeId").asString().equals(shapeOfName(shapes, source))
					&& edge.path("targetShapeId").asString().equals(shapeOfName(shapes, target))) {
				found = true;
			}
		}
		assertThat(found).as("edge of " + connection + " in " + edgesOf(diagram)).isTrue();
	}

	@Then("the view of {string} has {int} edges of {string}")
	public void countEdges(String diagram, int count, String connection) throws Exception {
		assertThat(edgesOf(diagram).stream()
			.filter((edge) -> connection.equals(edge.path("connection").path("name").asString()))
			.count()).isEqualTo(count);
	}

	@Then("the view of {string} has no edge of {string}")
	public void noEdge(String diagram, String connection) throws Exception {
		countEdges(diagram, 0, connection);
	}

	@Then("the view of {string} marks the edge of {string} as incomplete: {string}")
	public void incomplete(String diagram, String connection, String property) throws Exception {
		JsonNode edge = edgesOf(diagram).stream()
			.filter((e) -> connection.equals(e.path("connection").path("name").asString()))
			.findFirst()
			.orElseThrow();
		assertThat(edge.path("missingMandatory").toString()).isEqualTo("[\"" + property + "\"]");
	}

	@Then("the connection {string} exists with the type {string} from {string} to {string}")
	public void exists(String name, String type, String source, String target) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT m.name || '|' || s.name || '|' || t.name FROM entities e JOIN metatypes m ON m.id = "
					+ "e.metatype_id JOIN entity_connections c ON c.entity_id = e.id JOIN entities s ON s.id = "
					+ "c.source_entity_id JOIN entities t ON t.id = c.target_entity_id WHERE e.name = '" + quoted(name)
					+ "' AND e.deleted_at IS NULL ORDER BY e.id DESC LIMIT 1")).isEqualTo(type + "|" + source + "|" + target);
	}

	@Then("no connection {string} exists")
	public void notExists(String name) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*)::text FROM entities WHERE name = '" + quoted(name) + "' AND deleted_at IS NULL"))
			.isEqualTo("0");
	}

	@Then("the creation of the connection {string} is audited")
	public void created(String name) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*)::text FROM audit.events WHERE event_type = 'entity.created' AND entity_name = '"
					+ quoted(name) + "'")).isEqualTo("1");
	}

	@Then("the layout of {string} is audited with {int} added edges, {int} removed edges and {int} created connections")
	public void audited(String diagram, int added, int removed, int created) throws Exception {
		String details = "SELECT details ->> '%s' FROM audit.events WHERE event_type = 'diagram.layout-saved' AND "
				+ "target_id = '" + id(diagram) + "' ORDER BY id DESC LIMIT 1";
		assertThat(instance().oidc().single(details.formatted("edgesAdded"))).isEqualTo(String.valueOf(added));
		assertThat(instance().oidc().single(details.formatted("edgesRemoved"))).isEqualTo(String.valueOf(removed));
		assertThat(instance().oidc().single(details.formatted("connectionsCreated"))).isEqualTo(String.valueOf(created));
	}

	@Then("the offered connections are {stringList}")
	public void offered(List<String> names) {
		status(200);
		List<String> found = new ArrayList<>();
		json().path("items").forEach((item) -> found.add(item.path("connection").path("name").asString()));
		assertThat(found).containsExactlyElementsOf(names);
	}

	@Then("no connection is offered")
	public void nothingOffered() {
		assertThat(json().path("items")).isEmpty();
		assertThat(json().path("truncated").asBoolean()).isFalse();
	}

	@Then("the connection {string} has the description {string} and the version {int}")
	public void described(String name, String description, int version) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT coalesce(description, '') || '|' || version FROM entities WHERE name = '" + quoted(name)
					+ "' ORDER BY id DESC LIMIT 1")).isEqualTo(description + "|" + version);
	}

	// helpers

	private record Ends(int connectionId, String sourceShapeId, String targetShapeId) {
	}

	/** The connection with the first shapes of its source and target entity in the diagram. */
	private Ends endsOf(String diagram, String connection) throws Exception {
		int connectionId = entityId(connection);
		int[] ends = endpointsOf(connectionId);
		List<JsonNode> shapes = new ArrayList<>();
		viewOf(diagram).path("shapes").forEach(shapes::add);
		return new Ends(connectionId, shapeOfEntity(shapes, ends[0]), shapeOfEntity(shapes, ends[1]));
	}

	private void draw(String diagram, Ends ends) throws Exception {
		ObjectNode body = baseBody(diagram, true);
		body.withArray("edges")
			.add(edge(UUID.randomUUID().toString(), ends.connectionId(), ends.sourceShapeId(), ends.targetShapeId()));
		put(diagram, body);
	}

	private void newConnection(String type, String source, String target, String diagram) throws Exception {
		ObjectNode body = baseBody(diagram, true);
		String key = UUID.randomUUID().toString();
		body.withArray("newConnections").addObject().put("key", key).put("metatypeId", metatypeId(type));
		ObjectNode edge = edge(UUID.randomUUID().toString(), null, shapeId(diagram, source, 1),
				shapeId(diagram, target, 1));
		edge.put("newConnectionKey", key);
		body.withArray("edges").add(edge);
		put(diagram, body);
	}

	private static ObjectNode edge(String id, Integer connectionId, String source, String target) {
		ObjectNode edge = JsonMapper.shared()
			.createObjectNode()
			.put("id", id)
			.put("sourceShapeId", source)
			.put("targetShapeId", target);
		if (connectionId != null) {
			edge.put("connectionId", connectionId);
		}
		edge.putArray("points");
		return edge;
	}

	private static ObjectNode newShape(String id, String key, int x) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("id", id)
			.put("newEntityKey", key)
			.put("x", x)
			.put("y", 1)
			.put("width", 90)
			.put("height", 30);
	}

	/** The saved shapes of the diagram and, with {@code keepEdges}, its shown edges, as the body of a save. */
	private ObjectNode baseBody(String diagram, boolean keepEdges) throws Exception {
		JsonNode view = viewOf(diagram);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		for (JsonNode shape : view.path("shapes")) {
			shapes.add(JsonMapper.shared()
				.createObjectNode()
				.put("id", shape.path("id").asString())
				.put("entityId", shape.path("entity").path("id").asInt())
				.put("x", shape.path("x").decimalValue())
				.put("y", shape.path("y").decimalValue())
				.put("width", shape.path("width").decimalValue())
				.put("height", shape.path("height").decimalValue()));
		}
		ArrayNode edges = body.putArray("edges");
		if (keepEdges) {
			for (JsonNode edge : view.path("edges")) {
				edges.add(edge(edge.path("id").asString(), edge.path("connection").path("id").asInt(),
						edge.path("sourceShapeId").asString(), edge.path("targetShapeId").asString()));
			}
		}
		return body;
	}

	private void put(String diagram, ObjectNode body) throws Exception {
		this.response = instance().send("PUT", VIEWS + "/" + id(diagram), body.toString(), instance().architect(),
				Map.of("If-Match", "\"" + diagramVersion(diagram) + "\""));
	}

	private JsonNode viewOf(String diagram) throws Exception {
		HttpResponse<String> view = instance().oidc().get(VIEWS + "/" + id(diagram), instance().viewer().token());
		assertThat(view.statusCode()).as(view.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(view.body());
	}

	private List<JsonNode> edgesOf(String diagram) throws Exception {
		List<JsonNode> edges = new ArrayList<>();
		viewOf(diagram).path("edges").forEach(edges::add);
		return edges;
	}

	/** The ID of the {@code number}th shape (from 1) of the entity in the diagram. */
	private String shapeId(String diagram, String entity, int number) throws Exception {
		List<String> ids = new ArrayList<>();
		for (JsonNode shape : viewOf(diagram).path("shapes")) {
			if (entity.equals(shape.path("entity").path("name").asString())) {
				ids.add(shape.path("id").asString());
			}
		}
		assertThat(ids).as("shapes of " + entity + " in " + diagram).hasSizeGreaterThanOrEqualTo(number);
		return ids.get(number - 1);
	}

	private static String shapeOfName(List<JsonNode> shapes, String entity) {
		return shapes.stream()
			.filter((shape) -> entity.equals(shape.path("entity").path("name").asString()))
			.map((shape) -> shape.path("id").asString())
			.findFirst()
			.orElseThrow();
	}

	private static String shapeOfEntity(List<JsonNode> shapes, int entityId) {
		return shapes.stream()
			.filter((shape) -> entityId == shape.path("entity").path("id").asInt())
			.map((shape) -> shape.path("id").asString())
			.findFirst()
			.orElseThrow();
	}

	private JsonNode json() {
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static int[] endpointsOf(int connectionId) throws Exception {
		String ends = instance().oidc()
			.single("SELECT source_entity_id || ',' || target_entity_id FROM entity_connections WHERE entity_id = "
					+ connectionId);
		return new int[] { Integer.parseInt(ends.split(",")[0]), Integer.parseInt(ends.split(",")[1]) };
	}

	private static String id(String diagram) throws Exception {
		return instance().oidc().single("SELECT id::text FROM diagrams WHERE name = '" + quoted(diagram) + "'");
	}

	private static int diagramVersion(String diagram) throws Exception {
		return Integer.parseInt(
				instance().oidc().single("SELECT version::text FROM diagrams WHERE name = '" + quoted(diagram) + "'"));
	}

	private static int entityVersion(String name) throws Exception {
		return Integer.parseInt(instance().oidc()
			.single("SELECT version::text FROM entities WHERE name = '" + quoted(name) + "' ORDER BY id DESC LIMIT 1"));
	}

	private static String metatypeId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + quoted(name) + "'");
	}

	private static int entityId(String name) {
		try {
			return Integer.parseInt(instance().oidc()
				.single("SELECT id::text FROM entities WHERE name = '" + quoted(name) + "' ORDER BY id DESC LIMIT 1"));
		}
		catch (java.sql.SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String quoted(String text) {
		return text.replace("'", "''");
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

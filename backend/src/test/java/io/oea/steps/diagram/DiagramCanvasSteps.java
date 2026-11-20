package io.oea.steps.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/diagram/diagram-canvas.feature (us-074-diagram-canvas), on the instance of
 * {@link CatalogSteps}. The architect holds entity:write, the viewer only entity:read. The steps
 * keep the layout of each diagram they saved, so that a later step changes it and saves it again;
 * a saved layout is read back from the view. Diagrams, entities and metatypes are addressed by
 * their names, which are unique in the feature.
 */
public class DiagramCanvasSteps {

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final String DIAGRAMS = "/api/v1/diagrams";

	private static final String BATCH = "/api/v1/entities/batch";

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private HttpResponse<String> response;

	/** The layout of each diagram as last saved or read: the shapes in drawing order. */
	private final Map<String, List<ObjectNode>> layouts = new HashMap<>();

	// Given

	@Given("the diagram viewpoint {string} allows the metatypes {stringList}")
	public void allows(String viewpoint, List<String> metatypes) throws Exception {
		for (String metatype : metatypes) {
			instance().oidc()
				.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) SELECT v.id, m.id FROM viewpoints v, "
						+ "metatypes m WHERE v.name = '" + quoted(viewpoint) + "' AND m.name = '" + quoted(metatype)
						+ "' ON CONFLICT DO NOTHING");
		}
	}

	@Given("the diagram viewpoint {string} stores the size {int} x {int} pt for {string}")
	public void stores(String viewpoint, int width, int height, String metatype) throws Exception {
		instance().oidc()
			.update("INSERT INTO viewpoint_sizes (viewpoint_id, metatype_id, width_pt, height_pt) SELECT v.id, m.id, "
					+ width + ", " + height + " FROM viewpoints v, metatypes m WHERE v.name = '" + quoted(viewpoint)
					+ "' AND m.name = '" + quoted(metatype) + "' ON CONFLICT DO NOTHING");
	}

	@Given("the canvas entity {string} of the metatype {string} exists")
	public void entity(String name, String metatype) throws Exception {
		instance().oidc()
			.update("INSERT INTO entities (metatype_id, name, created_by) SELECT m.id, '" + quoted(name)
					+ "', (SELECT min(id) FROM persons) FROM metatypes m WHERE m.name = '" + quoted(metatype)
					+ "' AND NOT EXISTS (SELECT 1 FROM entities WHERE name = '" + quoted(name)
					+ "' AND deleted_at IS NULL)");
	}

	@Given("the canvas entity {string} is deleted")
	public void deleted(String name) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) WHERE name = '"
					+ quoted(name) + "'");
	}

	@Given("the metatype {string} has the mandatory property {string} without default value")
	public void mandatory(String metatype, String property) throws Exception {
		instance().oidc()
			.update("INSERT INTO metatypes (config_id, name, abbreviation) VALUES ('" + INSTANCE + "', '"
					+ quoted(metatype) + "', 'BP') ON CONFLICT DO NOTHING");
		instance().oidc()
			.update("INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('" + quoted(property)
					+ "', 'p_' || substr(md5('" + quoted(property) + "'), 1, 12), 'string') ON CONFLICT DO NOTHING");
		instance().oidc()
			.update("INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope) "
					+ "SELECT m.id, p.id, 'mandatory', DATE '2026-01-01', 'organization' FROM metatypes m, "
					+ "property_definitions p WHERE m.name = '" + quoted(metatype) + "' AND p.name = '"
					+ quoted(property) + "' ON CONFLICT DO NOTHING");
	}

	@Given("the diagram {string} shows the entity {string} at {bigdecimal}, {bigdecimal} with {bigdecimal} x {bigdecimal} pt")
	public void shows(String diagram, String entity, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height)
			throws Exception {
		layout(diagram).add(shape(UUID.randomUUID().toString(), entityId(entity), null, x, y, width, height));
		save(diagram, version(diagram), instance().architect(), null);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Given("the diagram {string} is renamed to {string} by another user")
	public void renamed(String diagram, String newName) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", newName);
		HttpResponse<String> renamed = instance().send("PUT", DIAGRAMS + "/" + id(diagram), body.toString(),
				instance().architect(), Map.of("If-Match", "\"" + version(diagram) + "\""));
		assertThat(renamed.statusCode()).as(renamed.body()).isEqualTo(200);
	}

	@Given("the enterprise architect has placed a new {string} named {string} in {string}")
	public void placedGiven(String metatype, String name, String diagram) throws Exception {
		place(metatype, name, diagram);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	// When

	@When("the viewer reads the view of {string}")
	public void read(String diagram) throws Exception {
		this.response = instance().oidc().get(VIEWS + "/" + id(diagram), instance().viewer().token());
	}

	@When("the enterprise architect searches the entities of {string} for {string}")
	public void search(String diagram, String name) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/entities?pageSize=200&q="
					+ URLEncoder.encode(name, StandardCharsets.UTF_8), instance().architect().token());
	}

	@When("the viewer searches the entities of {string} for {string}")
	public void viewerSearch(String diagram, String name) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/entities?q=" + URLEncoder.encode(name, StandardCharsets.UTF_8),
					instance().viewer().token());
	}

	@When("the viewer searches the entities of {string} with a name part of {int} characters")
	public void searchLength(String diagram, int length) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/entities?q=" + "q".repeat(length), instance().viewer().token());
	}

	@When("the viewer searches the entities of {string} of the metatype {string}")
	public void searchMetatype(String diagram, String metatype) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/entities?metatypeId=" + metatypeId(metatype),
					instance().viewer().token());
	}

	@When("the enterprise architect saves the layout of {string} with its version")
	public void saveWithVersion(String diagram) throws Exception {
		save(diagram, version(diagram), instance().architect(), null);
	}

	@When("the enterprise architect saves the layout of {string} with the version {int}")
	public void saveWithGivenVersion(String diagram, int version) throws Exception {
		save(diagram, version, instance().architect(), null);
	}

	@When("the enterprise architect saves the layout of {string} without a version")
	public void saveWithoutVersion(String diagram) throws Exception {
		save(diagram, null, instance().architect(), null);
	}

	@When("the viewer saves the layout of {string} with its version")
	public void viewerSaves(String diagram) throws Exception {
		save(diagram, version(diagram), instance().viewer(), null);
	}

	@When("the enterprise architect moves the shape of {string} in {string} to {bigdecimal}, {bigdecimal} and resizes it to {bigdecimal} x {bigdecimal} pt and saves")
	public void moves(String entity, String diagram, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height)
			throws Exception {
		ObjectNode shape = layout(diagram).stream()
			.filter((candidate) -> candidate.path("entityId").asInt() == entityId(entity))
			.findFirst()
			.orElseThrow();
		geometry(shape, x, y, width, height);
		save(diagram, version(diagram), instance().architect(), null);
	}

	@When("the enterprise architect places a new {string} named {string} in {string} and saves")
	public void place(String metatype, String name, String diagram) throws Exception {
		String key = UUID.randomUUID().toString();
		layout(diagram).add(shape(UUID.randomUUID().toString(), null, key, BigDecimal.TEN, BigDecimal.valueOf(20),
				BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		ArrayNode created = JsonMapper.shared().createArrayNode();
		created.add(newEntity(key, metatype, name));
		save(diagram, version(diagram), instance().architect(), created);
	}

	@When("the enterprise architect saves the new entities {string} of {string} and {string} of {string} in {string}")
	public void placeTwo(String first, String firstType, String second, String secondType, String diagram)
			throws Exception {
		ArrayNode created = JsonMapper.shared().createArrayNode();
		String firstKey = UUID.randomUUID().toString();
		String secondKey = UUID.randomUUID().toString();
		created.add(newEntity(firstKey, firstType, first));
		created.add(newEntity(secondKey, secondType, second));
		layout(diagram).add(shape(UUID.randomUUID().toString(), null, firstKey, BigDecimal.ONE, BigDecimal.ONE,
				BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		layout(diagram).add(shape(UUID.randomUUID().toString(), null, secondKey, BigDecimal.TWO, BigDecimal.TWO,
				BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		save(diagram, version(diagram), instance().architect(), created);
	}

	@When("the enterprise architect places {string} twice in {string} and saves")
	public void twice(String entity, String diagram) throws Exception {
		for (int i = 0; i < 2; i++) {
			layout(diagram).add(shape(UUID.randomUUID().toString(), entityId(entity), null, BigDecimal.valueOf(i * 100),
					BigDecimal.ZERO, BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		}
		save(diagram, version(diagram), instance().architect(), null);
	}

	@When("the enterprise architect removes the shape of {string} from {string} and saves")
	public void removes(String entity, String diagram) throws Exception {
		layout(diagram).removeIf((shape) -> shape.path("entityId").asInt() == entityId(entity));
		save(diagram, version(diagram), instance().architect(), null);
	}

	@When("the enterprise architect saves the layout of {string} with {string} added twice and {string} removed")
	public void addedTwiceRemoved(String diagram, String added, String removed) throws Exception {
		layout(diagram).removeIf((shape) -> shape.path("entityId").asInt() == entityId(removed));
		for (int i = 0; i < 2; i++) {
			layout(diagram).add(shape(UUID.randomUUID().toString(), entityId(added), null, BigDecimal.valueOf(i * 10),
					BigDecimal.ZERO, BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		}
		save(diagram, version(diagram), instance().architect(), null);
	}

	@When("an API call saves a shape of the entity {string} in {string}")
	public void shapeOf(String entity, String diagram) throws Exception {
		layout(diagram).add(shape(UUID.randomUUID().toString(), entityId(entity), null, BigDecimal.ONE, BigDecimal.ONE,
				BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		save(diagram, version(diagram), instance().architect(), null);
	}

	@When("an API call saves a shape of an unknown entity in {string}")
	public void shapeOfUnknown(String diagram) throws Exception {
		layout(diagram).add(shape(UUID.randomUUID().toString(), 999_999_999, null, BigDecimal.ONE, BigDecimal.ONE,
				BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		save(diagram, version(diagram), instance().architect(), null);
	}

	@When("an API call saves the shape ID of {string} in {string}")
	public void foreignShape(String source, String diagram) throws Exception {
		HttpResponse<String> view = instance().oidc().get(VIEWS + "/" + id(source), instance().viewer().token());
		JsonNode first = JsonMapper.shared().readTree(view.body()).path("shapes").get(0);
		layout(diagram).add(shape(first.path("id").asString(), first.path("entity").path("id").asInt(), null,
				BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		save(diagram, version(diagram), instance().architect(), null);
	}

	@When("an API call saves a shape of the entity {string} with the {word} {string} in {string}")
	public void shapeWith(String entity, String field, String value, String diagram) throws Exception {
		ObjectNode shape = shape(UUID.randomUUID().toString(), entityId(entity), null, BigDecimal.ONE, BigDecimal.ONE,
				BigDecimal.valueOf(90), BigDecimal.valueOf(30));
		shape.put(field, new BigDecimal(value));
		layout(diagram).add(shape);
		save(diagram, version(diagram), instance().architect(), null);
	}

	@When("an API call saves {int} shapes of {string} and {int} new entities in {string}")
	public void counts(int shapes, String entity, int entities, String diagram) throws Exception {
		ArrayNode created = JsonMapper.shared().createArrayNode();
		for (int i = 0; i < entities; i++) {
			String key = UUID.randomUUID().toString();
			created.add(newEntity(key, "Application Component", "Dgc Bulk " + i));
			layout(diagram).add(shape(UUID.randomUUID().toString(), null, key, BigDecimal.valueOf(i), BigDecimal.ZERO,
					BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		}
		while (layout(diagram).size() < shapes) {
			layout(diagram).add(shape(UUID.randomUUID().toString(), entityId(entity), null,
					BigDecimal.valueOf(layout(diagram).size()), BigDecimal.ZERO, BigDecimal.valueOf(90),
					BigDecimal.valueOf(30)));
		}
		save(diagram, version(diagram), instance().architect(), created);
		if (this.response.statusCode() == 200) {
			// the shared instance keeps no bulk entities of the scenario
			instance().oidc()
				.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) "
						+ "WHERE name LIKE 'Dgc Bulk %' AND deleted_at IS NULL");
		}
	}

	@When("an API call saves a new entity with a name of {int} characters in {string}")
	public void nameLength(int length, String diagram) throws Exception {
		String key = UUID.randomUUID().toString();
		ArrayNode created = JsonMapper.shared().createArrayNode();
		created.add(newEntity(key, "Application Component", "n".repeat(length)));
		layout(diagram).add(shape(UUID.randomUUID().toString(), null, key, BigDecimal.ONE, BigDecimal.ONE,
				BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
		save(diagram, version(diagram), instance().architect(), created);
		if (this.response.statusCode() == 200) {
			instance().oidc()
				.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) "
						+ "WHERE name = '" + "n".repeat(length) + "' AND deleted_at IS NULL");
		}
	}

	@When("a batch changes the description of {string} without a value of {string}")
	public void batch(String entity, String property) throws Exception {
		String externalId = "dgc-" + entityId(entity);
		instance().oidc()
			.update("UPDATE entities SET external_id = '" + externalId + "' WHERE id = " + entityId(entity));
		String metatype = instance().oidc().single("SELECT metatype_id::text FROM entities WHERE id = " + entityId(entity));
		ObjectNode body = JsonMapper.shared().createObjectNode().put("strategy", "upsert").put("atomic", false);
		body.putArray("entities")
			.addObject()
			.put("name", entity)
			.put("externalId", externalId)
			.put("description", "A changed description")
			.put("metatypeId", metatype);
		this.response = instance().post(BATCH, body.toString(), instance().architect(), Map.of());
	}

	@When("a value of {string} is written for {string}")
	public void value(String property, String entity) throws Exception {
		instance().oidc()
			.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) SELECT "
					+ entityId(entity) + ", id, 'Anna' FROM property_definitions WHERE name = '" + quoted(property) + "'");
	}

	// Then

	@Then("the canvas answer is {int}")
	public void status(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the canvas answer is {int} naming {string}")
	public void statusNaming(int status, String field) {
		status(status);
		List<String> fields = new ArrayList<>();
		json().path("invalidFields").forEach((name) -> fields.add(name.asString()));
		assertThat(fields).anyMatch((name) -> name.equals(field) || name.startsWith(field + "."));
	}

	@Then("the diagram {string} has the canvas version {int}")
	public void hasVersion(String diagram, int version) throws Exception {
		assertThat(version(diagram)).isEqualTo(version);
	}

	@Then("the found entities are {stringList}")
	public void found(List<String> names) {
		List<String> found = new ArrayList<>();
		json().path("items").forEach((item) -> found.add(item.path("name").asString()));
		assertThat(found).containsExactlyElementsOf(names);
	}

	@Then("the view of {string} has a shape of {string} at {bigdecimal}, {bigdecimal} with {bigdecimal} x {bigdecimal} pt")
	public void hasShape(String diagram, String entity, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height)
			throws Exception {
		JsonNode shape = shapesOf(diagram, entity).stream().findFirst().orElseThrow(
				() -> new AssertionError("no shape of " + entity));
		assertThat(shape.path("x").decimalValue()).isEqualByComparingTo(x);
		assertThat(shape.path("y").decimalValue()).isEqualByComparingTo(y);
		assertThat(shape.path("width").decimalValue()).isEqualByComparingTo(width);
		assertThat(shape.path("height").decimalValue()).isEqualByComparingTo(height);
	}

	@Then("the view of {string} offers the default size {int} x {int} pt for {string}")
	public void offersSize(String diagram, int width, int height, String metatype) throws Exception {
		String id = metatypeId(metatype);
		JsonNode mapping = null;
		for (JsonNode candidate : json().path("viewpoint").path("effectiveMappings")) {
			if (id.equals(candidate.path("metatypeId").asString())) {
				mapping = candidate;
			}
		}
		assertThat(mapping).isNotNull();
		assertThat(mapping.path("width").asInt()).isEqualTo(width);
		assertThat(mapping.path("height").asInt()).isEqualTo(height);
	}

	@Then("the view of {string} has no shape of {string}")
	public void hasNoShape(String diagram, String entity) throws Exception {
		assertThat(shapesOf(diagram, entity)).isEmpty();
	}

	@Then("the view of {string} has {int} shapes of {string}")
	public void hasShapes(String diagram, int count, String entity) throws Exception {
		assertThat(shapesOf(diagram, entity)).hasSize(count);
	}

	@Then("the view of {string} marks the shape of {string} as incomplete: {string}")
	public void incomplete(String diagram, String entity, String property) throws Exception {
		List<JsonNode> shapes = shapesOf(diagram, entity);
		assertThat(shapes).hasSize(1);
		List<String> missing = new ArrayList<>();
		shapes.get(0).path("missingMandatory").forEach((name) -> missing.add(name.asString()));
		assertThat(missing).containsExactly(property);
	}

	@Then("the view of {string} does not mark the shape of {string} as incomplete")
	public void complete(String diagram, String entity) throws Exception {
		List<JsonNode> shapes = shapesOf(diagram, entity);
		assertThat(shapes).hasSize(1);
		assertThat(shapes.get(0).path("missingMandatory")).isEmpty();
	}

	@Then("the entity {string} exists with the version {int}")
	public void entityVersion(String entity, int version) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT version::text FROM entities WHERE name = '" + quoted(entity) + "' AND deleted_at IS NULL"))
			.isEqualTo(String.valueOf(version));
	}

	@Then("no canvas entity {string} exists")
	public void noEntity(String entity) throws Exception {
		assertThat(instance().oidc().single("SELECT count(*) FROM entities WHERE name = '" + quoted(entity) + "'"))
			.isEqualTo("0");
	}

	@Then("one {string} audit event names the entity {string}")
	public void audited(String type, String entity) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '" + type + "' AND entity_name = '"
					+ quoted(entity) + "'")).isEqualTo("1");
	}

	@Then("the batch item is rejected with {string}")
	public void batchRejected(String code) {
		status(200);
		JsonNode item = json().path("items").get(0);
		assertThat(item.path("status").asString()).isEqualTo("error");
		assertThat(item.path("errors").toString()).contains(code);
	}

	@Then("{string} is audited for {string} with {int} added, {int} changed, {int} removed and {int} created entities")
	public void layoutAudited(String type, String diagram, int added, int changed, int removed, int created)
			throws Exception {
		String details = "SELECT details ->> '%s' FROM audit.events WHERE event_type = '" + type + "' AND target_id = '"
				+ id(diagram) + "' ORDER BY id DESC LIMIT 1";
		assertThat(instance().oidc().single(details.formatted("added"))).isEqualTo(String.valueOf(added));
		assertThat(instance().oidc().single(details.formatted("changed"))).isEqualTo(String.valueOf(changed));
		assertThat(instance().oidc().single(details.formatted("removed"))).isEqualTo(String.valueOf(removed));
		assertThat(instance().oidc().single(details.formatted("entitiesCreated"))).isEqualTo(String.valueOf(created));
	}

	// helpers

	private void save(String diagram, Integer version, Session session, ArrayNode newEntities) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		layout(diagram).forEach(shapes::add);
		if (newEntities != null) {
			body.set("newEntities", newEntities);
		}
		this.response = instance().send("PUT", VIEWS + "/" + id(diagram), body.toString(), session,
				(version != null) ? Map.of("If-Match", "\"" + version + "\"") : Map.of());
		if (this.response.statusCode() == 200) {
			// continue from the saved layout, in which new entities are existing ones
			List<ObjectNode> saved = new ArrayList<>();
			for (JsonNode shape : json().path("shapes")) {
				saved.add(shape(shape.path("id").asString(), shape.path("entity").path("id").asInt(), null,
						shape.path("x").decimalValue(), shape.path("y").decimalValue(),
						shape.path("width").decimalValue(), shape.path("height").decimalValue()));
			}
			this.layouts.put(diagram, saved);
		}
	}

	private List<ObjectNode> layout(String diagram) {
		return this.layouts.computeIfAbsent(diagram, (name) -> new ArrayList<>());
	}

	private static ObjectNode shape(String id, Integer entityId, String key, BigDecimal x, BigDecimal y,
			BigDecimal width, BigDecimal height) {
		ObjectNode shape = JsonMapper.shared().createObjectNode().put("id", id);
		if (entityId != null) {
			shape.put("entityId", entityId);
		}
		if (key != null) {
			shape.put("newEntityKey", key);
		}
		return geometry(shape, x, y, width, height);
	}

	private static ObjectNode geometry(ObjectNode shape, BigDecimal x, BigDecimal y, BigDecimal width,
			BigDecimal height) {
		return shape.put("x", x).put("y", y).put("width", width).put("height", height);
	}

	private static ObjectNode newEntity(String key, String metatype, String name) throws Exception {
		return JsonMapper.shared()
			.createObjectNode()
			.put("key", key)
			.put("metatypeId", metatypeId(metatype))
			.put("name", name);
	}

	private List<JsonNode> shapesOf(String diagram, String entity) throws Exception {
		this.response = instance().oidc().get(VIEWS + "/" + id(diagram), instance().viewer().token());
		status(200);
		List<JsonNode> shapes = new ArrayList<>();
		for (JsonNode shape : json().path("shapes")) {
			if (entity.equals(shape.path("entity").path("name").asString())) {
				shapes.add(shape);
			}
		}
		return shapes;
	}

	private JsonNode json() {
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static String id(String diagram) throws Exception {
		return instance().oidc().single("SELECT id::text FROM diagrams WHERE name = '" + quoted(diagram) + "'");
	}

	private static int version(String diagram) throws Exception {
		return Integer.parseInt(
				instance().oidc().single("SELECT version::text FROM diagrams WHERE name = '" + quoted(diagram) + "'"));
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

	private static String metatypeId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + quoted(name) + "'");
	}

	private static String quoted(String text) {
		return text.replace("'", "''");
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

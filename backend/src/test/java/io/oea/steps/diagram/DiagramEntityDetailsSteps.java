package io.oea.steps.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/diagram/diagram-entity-details.feature (us-074-diagram-entity-details), on the
 * instance of {@link CatalogSteps}. The metatypes and properties carry the prefix "Ded", so that no
 * other feature sees a mandatory or restricted property. A save reads the shapes of the diagram
 * from its view and adds the entity change, so that the steps keep no layout of their own.
 */
public class DiagramEntityDetailsSteps {

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final String BATCH = "/api/v1/entities/batch";

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private HttpResponse<String> response;

	// Given

	@Given("the details metatype {string} exists with the optional property {string}")
	public void optional(String metatype, String property) throws Exception {
		metatype(metatype);
		property(property, "string", "public", null, null);
		map(metatype, property, "optional");
	}

	@Given("the details metatype {string} has the role-restricted property {string} that {string} reads and {string} writes")
	public void restricted(String metatype, String property, String reader, String writer) throws Exception {
		metatype(metatype);
		property(property, "string", "role-restricted", null, null);
		instance().oidc()
			.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) SELECT id, '" + reader
					+ "', 'read' FROM property_definitions WHERE name = '" + quoted(property)
					+ "' ON CONFLICT DO NOTHING");
		instance().oidc()
			.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) SELECT id, '" + writer
					+ "', 'write' FROM property_definitions WHERE name = '" + quoted(property)
					+ "' ON CONFLICT DO NOTHING");
		map(metatype, property, "optional");
	}

	@Given("the details metatype {string} has the reference property {string} to {string}")
	public void reference(String metatype, String property, String target) throws Exception {
		metatype(metatype);
		property(property, "reference", "public", target, null);
		map(metatype, property, "optional");
	}

	@Given("the entity {string} was given the value {string} for {string}")
	public void value(String entity, String value, String property) throws Exception {
		instance().oidc()
			.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) SELECT "
					+ entityId(entity) + ", id, '" + quoted(value) + "' FROM property_definitions WHERE name = '"
					+ quoted(property) + "'");
	}

	@Given("the entity {string} has an open issue of a solution")
	public void openIssue(String entity) throws Exception {
		String solution = UUID.randomUUID().toString();
		String field = "ded-gone-" + solution.substring(0, 8);
		instance().oidc()
			.update("INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES ('" + solution + "', 'Ded "
					+ solution.substring(0, 8) + "', (SELECT min(id) FROM persons), (SELECT min(id) FROM persons), 'draft')");
		instance().oidc()
			.update("INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('" + field
					+ "', 'p_' || substr(md5('" + field + "'), 1, 12), 'string')");
		instance().oidc()
			.update("INSERT INTO solution_open_issues (id, solution_id, entity_id, field, property_def_id, kind, kept_value) "
					+ "SELECT gen_random_uuid(), '" + solution + "', " + entityId(entity) + ", id::text, id, "
					+ "'not-in-metamodel', 'kept' FROM property_definitions WHERE name = '" + field + "'");
	}

	@Given("a batch has changed the description of {string} to {string}")
	public void batchGiven(String entity, String description) throws Exception {
		batch(entity, description);
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	// When

	@When("the viewer reads the details of {string} in {string}")
	public void viewerReads(String entity, String diagram) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/entities/" + entityId(entity), instance().viewer().token());
	}

	@When("the enterprise architect reads the details of {string} in {string}")
	public void architectReads(String entity, String diagram) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/entities/" + entityId(entity), instance().architect().token());
	}

	@When("the enterprise architect searches the reference targets of {string} in {string} for {string}")
	public void targets(String property, String diagram, String name) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/reference-targets?pageSize=200&propertyDefId="
					+ instance().oidc().single("SELECT id::text FROM property_definitions WHERE name = '"
							+ quoted(property) + "'")
					+ "&q=" + URLEncoder.encode(name, StandardCharsets.UTF_8), instance().architect().token());
	}

	@When("the enterprise architect saves {string} with the value of {string} of {string} set to {string}")
	public void saveValue(String diagram, String property, String entity, String value) throws Exception {
		saveUpdate(diagram, entity, currentVersion(entity), null, property, value);
	}

	@When("the enterprise architect saves {string} with the description of {string} changed to {string}")
	public void saveDescription(String diagram, String entity, String description) throws Exception {
		saveUpdate(diagram, entity, currentVersion(entity), description, null, null);
	}

	@When("the enterprise architect saves {string} with the description of {string} changed to {string} read at the version {int}")
	public void saveStale(String diagram, String entity, String description, int version) throws Exception {
		saveUpdate(diagram, entity, version, description, null, null);
	}

	@When("an API call changes {string} without a shape in {string}")
	public void unshown(String entity, String diagram) throws Exception {
		ObjectNode body = viewBody(diagram);
		body.putArray("entityUpdates")
			.addObject()
			.put("entityId", entityId(entity))
			.put("expectedVersion", currentVersion(entity))
			.put("description", "x");
		put(diagram, body);
	}

	@When("the enterprise architect places a new {string} named {string} with the value {string} for {string} in {string} and saves")
	public void placeWithValue(String metatype, String name, String value, String property, String diagram)
			throws Exception {
		String key = UUID.randomUUID().toString();
		ObjectNode body = viewBody(diagram);
		body.withArray("shapes")
			.add(shape(UUID.randomUUID().toString(), null, key, BigDecimal.TEN, BigDecimal.valueOf(20)));
		ObjectNode created = body.withArray("newEntities")
			.addObject()
			.put("key", key)
			.put("metatypeId", metatypeId(metatype))
			.put("name", name);
		created.putArray("properties")
			.addObject()
			.put("propertyDefId", propertyId(property))
			.put("value", value);
		put(diagram, body);
	}

	@When("a batch changes the description of {string} to {string} without a version")
	public void batchWhen(String entity, String description) throws Exception {
		batch(entity, description);
	}

	// Then

	@Then("the details answer is {int}")
	public void status(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the details answer is {int} naming {string}")
	public void statusNaming(int status, String field) {
		status(status);
		List<String> fields = new ArrayList<>();
		json().path("invalidFields").forEach((name) -> fields.add(name.asString()));
		assertThat(fields).contains(field);
	}

	@Then("the details answer is 409 with the problem {string} naming {string}")
	public void conflictBlocked(String problem, String entity) throws Exception {
		status(409);
		assertThat(json().path("type").asString()).isEqualTo("urn:oea:problem:" + problem);
		assertThat(json().path("entities").get(0).path("entityName").asString()).isEqualTo(entity);
	}

	@Then("the details answer is 409 with the problem {string} naming {string} with the current version {int}")
	public void conflictVersion(String problem, String entity, int version) throws Exception {
		status(409);
		assertThat(json().path("type").asString()).isEqualTo("urn:oea:problem:" + problem);
		assertThat(json().path("conflicts").get(0).path("entityId").asInt()).isEqualTo(entityId(entity));
		assertThat(json().path("conflicts").get(0).path("currentVersion").asInt()).isEqualTo(version);
	}

	@Then("the details have the name {string} and the entity version {int}")
	public void nameAndVersion(String name, int version) {
		assertThat(json().path("name").asString()).isEqualTo(name);
		assertThat(json().path("version").asInt()).isEqualTo(version);
	}

	@Then("the details show the value {string} for {string}")
	public void shows(String value, String property) {
		JsonNode shown = valueOf(property);
		assertThat(shown.path("redacted").asBoolean()).isFalse();
		assertThat(shown.path("valueText").asString()).isEqualTo(value);
	}

	@Then("the details redact {string}")
	public void redacts(String property) {
		JsonNode shown = valueOf(property);
		assertThat(shown.path("redacted").asBoolean()).isTrue();
		assertThat(shown.path("valueText").isNull()).isTrue();
	}

	@Then("the details mark {string} as not changeable")
	public void notChangeable(String property) throws Exception {
		assertThat(field(property).path("writable").asBoolean()).isFalse();
	}

	@Then("the details mark {string} as changeable")
	public void changeable(String property) throws Exception {
		assertThat(field(property).path("writable").asBoolean()).isTrue();
	}

	@Then("the reference targets are {stringList}")
	public void referenceTargets(List<String> names) {
		List<String> found = new ArrayList<>();
		json().path("items").forEach((item) -> found.add(item.path("name").asString()));
		assertThat(found).containsExactlyElementsOf(names);
	}

	@Then("the entity {string} has the value {string} for {string}")
	public void entityValue(String entity, String value, String property) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT value_text FROM entity_property_values WHERE entity_id = " + entityId(entity)
					+ " AND property_def_id = '" + propertyId(property) + "'")).isEqualTo(value);
	}

	@Then("the entity {string} has the description {string}")
	public void description(String entity, String description) throws Exception {
		assertThat(instance().oidc().single("SELECT description FROM entities WHERE id = " + entityId(entity)))
			.isEqualTo(description);
	}

	// helpers

	private void saveUpdate(String diagram, String entity, int version, String description, String property,
			String value) throws Exception {
		ObjectNode body = viewBody(diagram);
		ObjectNode update = body.putArray("entityUpdates")
			.addObject()
			.put("entityId", entityId(entity))
			.put("expectedVersion", version);
		if (description != null) {
			update.put("description", description);
		}
		if (property != null) {
			update.putArray("properties").addObject().put("propertyDefId", propertyId(property)).put("value", value);
		}
		put(diagram, body);
	}

	/** The saved shapes of the diagram as the body of a save. */
	private ObjectNode viewBody(String diagram) throws Exception {
		HttpResponse<String> view = instance().oidc().get(VIEWS + "/" + id(diagram), instance().viewer().token());
		assertThat(view.statusCode()).as(view.body()).isEqualTo(200);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode shapes = body.putArray("shapes");
		for (JsonNode shape : JsonMapper.shared().readTree(view.body()).path("shapes")) {
			shapes.add(shape(shape.path("id").asString(), shape.path("entity").path("id").asInt(), null,
					shape.path("x").decimalValue(), shape.path("y").decimalValue(), shape.path("width").decimalValue(),
					shape.path("height").decimalValue()));
		}
		return body;
	}

	private void put(String diagram, ObjectNode body) throws Exception {
		this.response = instance().send("PUT", VIEWS + "/" + id(diagram), body.toString(), instance().architect(),
				Map.of("If-Match", "\"" + diagramVersion(diagram) + "\""));
	}

	private void batch(String entity, String description) throws Exception {
		String externalId = "ded-" + entityId(entity);
		instance().oidc()
			.update("UPDATE entities SET external_id = '" + externalId + "' WHERE id = " + entityId(entity));
		String metatype = instance().oidc().single("SELECT metatype_id::text FROM entities WHERE id = " + entityId(entity));
		ObjectNode body = JsonMapper.shared().createObjectNode().put("strategy", "upsert").put("atomic", true);
		body.putArray("entities")
			.addObject()
			.put("name", entity)
			.put("externalId", externalId)
			.put("description", description)
			.put("metatypeId", metatype);
		this.response = instance().post(BATCH, body.toString(), instance().architect(), Map.of());
	}

	private static ObjectNode shape(String id, Integer entityId, String key, BigDecimal x, BigDecimal y) {
		return shape(id, entityId, key, x, y, BigDecimal.valueOf(90), BigDecimal.valueOf(30));
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
		return shape.put("x", x).put("y", y).put("width", width).put("height", height);
	}

	private JsonNode valueOf(String property) {
		for (JsonNode value : json().path("properties")) {
			if (property.equals(value.path("propertyName").asString())) {
				return value;
			}
		}
		throw new AssertionError("no value of " + property + " in " + this.response.body());
	}

	private JsonNode field(String property) throws Exception {
		String id = propertyId(property);
		for (JsonNode field : json().path("fields")) {
			if (id.equals(field.path("propertyDefId").asString())) {
				return field;
			}
		}
		throw new AssertionError("no field of " + property + " in " + this.response.body());
	}

	private JsonNode json() {
		return JsonMapper.shared().readTree(this.response.body());
	}

	private void metatype(String name) throws Exception {
		instance().oidc()
			.update("INSERT INTO metatypes (config_id, name, abbreviation) VALUES ('" + INSTANCE + "', '" + quoted(name)
					+ "', 'DC') ON CONFLICT DO NOTHING");
	}

	private void property(String name, String dataType, String mode, String refMetatype, String unused)
			throws Exception {
		instance().oidc()
			.update("INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, ref_metatype_id) "
					+ "VALUES ('" + quoted(name) + "', 'p_' || substr(md5('" + quoted(name) + "'), 1, 12), '" + dataType
					+ "', '" + mode + "', "
					+ ((refMetatype != null) ? "(SELECT id FROM metatypes WHERE name = '" + quoted(refMetatype) + "')"
							: "NULL")
					+ ") ON CONFLICT DO NOTHING");
	}

	private void map(String metatype, String property, String necessity) throws Exception {
		instance().oidc()
			.update("INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope) "
					+ "SELECT m.id, p.id, '" + necessity + "', DATE '2026-01-01', 'organization' FROM metatypes m, "
					+ "property_definitions p WHERE m.name = '" + quoted(metatype) + "' AND p.name = '"
					+ quoted(property) + "' ON CONFLICT DO NOTHING");
	}

	private static String propertyId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM property_definitions WHERE name = '" + quoted(name) + "'");
	}

	private static String metatypeId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + quoted(name) + "'");
	}

	private static String id(String diagram) throws Exception {
		return instance().oidc().single("SELECT id::text FROM diagrams WHERE name = '" + quoted(diagram) + "'");
	}

	private static int diagramVersion(String diagram) throws Exception {
		return Integer.parseInt(
				instance().oidc().single("SELECT version::text FROM diagrams WHERE name = '" + quoted(diagram) + "'"));
	}

	private static int currentVersion(String entity) {
		try {
			return Integer.parseInt(instance().oidc()
				.single("SELECT version::text FROM entities WHERE id = " + entityId(entity)));
		}
		catch (java.sql.SQLException ex) {
			throw new IllegalStateException(ex);
		}
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

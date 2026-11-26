package io.oea.steps.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.catalog.CatalogSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/diagram/diagram-nesting.feature (us-074-diagram-nesting), on the instance of
 * {@link CatalogSteps}. The instance is shared, so every scenario names its viewpoint, diagram and
 * entities with a token of its own: the feature uses plain names, the steps append the token. A draft
 * is the layout under construction; saving it sends the whole request, and after a save it continues
 * from the view that came back.
 */
public class DiagramNestingSteps {

	private static final String VIEWS = "/api/v1/views/diagrams";

	private static final String DIAGRAMS = "/api/v1/diagrams";

	private static final String SETTINGS = "/api/v1/admin/diagram-settings";

	private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

	private final String token = UUID.randomUUID().toString().substring(0, 8);

	private HttpResponse<String> response;

	private int auditBefore;

	private String draftDiagram;

	private List<ObjectNode> shapes = new ArrayList<>();

	private ArrayNode newEntities = JsonMapper.shared().createArrayNode();

	private ArrayNode lines = JsonMapper.shared().createArrayNode();

	private ArrayNode parentChanges = JsonMapper.shared().createArrayNode();

	/** The shape of each entity name (the plain one), as far as the scenario placed one. */
	private final Map<String, String> shapeIds = new HashMap<>();

	private final Map<String, String> newKeys = new HashMap<>();

	private int lastOfChain;

	/** Whether the scenario used a name of this class; other scenarios need no clean-up. */
	private boolean used;

	/** The shared instance keeps no entities of the scenario: its catalogs and their paging stay as other features expect them. */
	@After
	public void clean() throws Exception {
		if (!this.used || instance() == null) {
			return;
		}
		instance().oidc()
			.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) WHERE name LIKE '% "
					+ this.token + "' AND deleted_at IS NULL");
	}

	// Given

	@Given("a nesting viewpoint {string} allowing {string} and {string} and the connection type {string}")
	public void viewpoint(String name, String first, String second, String connection) throws Exception {
		String id = instance().oidc()
			.single("INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), '" + n(name)
					+ "', 'generic', (SELECT min(id) FROM persons)) RETURNING id::text");
		for (String metatype : List.of(first, second)) {
			instance().oidc()
				.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) SELECT '" + id
						+ "', id FROM metatypes WHERE name = '" + metatype + "'");
		}
		instance().oidc()
			.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) SELECT '" + id
					+ "', id FROM metatypes WHERE name = '" + connection + "'");
	}

	@Given("a nesting diagram {string} in the nesting viewpoint {string}")
	public void diagram(String name, String viewpoint) throws Exception {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", n(name))
			.put("viewpointId", instance().oidc().single("SELECT id::text FROM viewpoints WHERE name = '" + n(viewpoint) + "'"));
		this.response = instance().send("POST", DIAGRAMS, body.toString(), instance().architect());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	/** {@code reset} sets the default, {@code true} and {@code false} set the value; both start at version 1. */
	@Given("the nesting setting {string} is {word}")
	public void setting(String name, String value) throws Exception {
		instance().oidc()
			.update("UPDATE diagram_settings SET ask_new_parent = " + (value.equals("false") ? "false" : "true")
					+ ", version = 1, updated_at = NULL, updated_by = NULL");
		this.auditBefore = auditCount("diagram.settings-updated");
	}

	@Given("the nesting viewpoint {string} stores the nesting setting {word}")
	public void viewpointSetting(String viewpoint, String value) throws Exception {
		instance().oidc()
			.update("UPDATE viewpoints SET ask_new_parent = " + (value.equals("none") ? "NULL" : value) + " WHERE name = '"
					+ n(viewpoint) + "'");
	}

	@Given("the nesting entity {string} of the metatype {string} exists")
	public void entity(String name, String metatype) throws Exception {
		insertEntity(name, metatype);
	}

	@Given("the nesting entity {string} is deleted")
	public void deleted(String name) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET deleted_at = now(), deleted_by = (SELECT min(id) FROM persons) WHERE name = '"
					+ n(name) + "'");
	}

	@Given("the nesting entity {string} of the metatype {string} is a child of {string}")
	public void child(String name, String metatype, String parent) throws Exception {
		insertEntity(name, metatype);
		setParent(entityId(name), entityId(parent));
	}

	@Given("the nesting entity {string} has {int} children")
	public void children(String parent, int count) throws Exception {
		instance().oidc()
			.update("INSERT INTO entities (metatype_id, name, created_by, parent_entity_id) SELECT (SELECT id FROM metatypes "
					+ "WHERE name = 'Application Component'), 'Child ' || lpad(g::text, 4, '0') || ' " + this.token
					+ "', (SELECT min(id) FROM persons), " + entityId(parent) + " FROM generate_series(1, " + count
					+ ") g");
	}

	@Given("the nesting entity {string} has an open issue of a solution")
	public void blocked(String name) throws Exception {
		String solution = instance().oidc()
			.single("INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES (gen_random_uuid(), 'Blocking "
					+ this.token + "', (SELECT min(id) FROM persons), (SELECT min(id) FROM persons), 'draft') RETURNING id::text");
		String property = instance().oidc()
			.single("INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('Gone " + this.token
					+ "', 'p_' || substr(md5('" + this.token + "'), 1, 12), 'string') RETURNING id::text");
		instance().oidc()
			.update("INSERT INTO solution_open_issues (id, solution_id, entity_id, field, property_def_id, kind, kept_value) "
					+ "VALUES (gen_random_uuid(), '" + solution + "', " + entityId(name) + ", '" + property + "', '"
					+ property + "', 'not-in-metamodel', 'kept')");
	}

	@Given("a chain of {int} nesting entities named {string} exists")
	public void chain(int levels, String name) throws Exception {
		int parent = 0;
		for (int i = 1; i <= levels; i++) {
			insertEntity(name + " " + i, "Application Component");
			int id = entityId(name + " " + i);
			if (parent != 0) {
				instance().oidc().update("UPDATE entities SET parent_entity_id = " + parent + " WHERE id = " + id);
			}
			parent = id;
		}
		this.lastOfChain = parent;
	}

	@Given("the draft of {string} shows {string} at {bigdecimal}, {bigdecimal} with {bigdecimal} x {bigdecimal} pt")
	public void draftShows(String diagram, String entity, BigDecimal x, BigDecimal y, BigDecimal width,
			BigDecimal height) throws Exception {
		newDraft(diagram);
		addShape(entity, x, y, width, height, null);
	}

	@Given("the draft shows {string} at {bigdecimal}, {bigdecimal} with {bigdecimal} x {bigdecimal} pt")
	public void shows(String entity, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) throws Exception {
		addShape(entity, x, y, width, height, null);
	}

	@Given("the draft nests {string} in {string} at {bigdecimal}, {bigdecimal} with {bigdecimal} x {bigdecimal} pt")
	public void nests(String entity, String container, BigDecimal x, BigDecimal y, BigDecimal width,
			BigDecimal height) throws Exception {
		addShape(entity, x, y, width, height, this.shapeIds.get(container));
	}

	@Given("the draft names the container {string} for the shape of {string}")
	public void names(String container, String entity) {
		String id = container.equals("unknown") ? UUID.randomUUID().toString() : this.shapeIds.get(container);
		this.shapes.stream()
			.filter((shape) -> shape.path("id").asString().equals(this.shapeIds.get(entity)))
			.findFirst()
			.orElseThrow()
			.put("parentShapeId", id);
	}

	@Given("the draft adds a hierarchy line from the shape of {string} to the shape of {string}")
	public void line(String parent, String child) {
		this.lines.addObject()
			.put("id", UUID.randomUUID().toString())
			.put("parentShapeId", this.shapeIds.get(parent))
			.put("childShapeId", this.shapeIds.get(child));
	}

	@Given("the draft changes the parent of {string} to {string}")
	public void changeParent(String entity, String parent) throws Exception {
		change(entity, entityId(parent), version(entity));
	}

	@Given("the draft changes the parent of {string} to {string} with the version {int}")
	public void changeParentVersion(String entity, String parent, int version) throws Exception {
		change(entity, entityId(parent), version);
	}

	@Given("the draft changes the parent of {string} to the last of the chain")
	public void changeParentToChain(String entity) throws Exception {
		change(entity, this.lastOfChain, version(entity));
	}

	@Given("the draft clears the parent of {string}")
	public void clearParent(String entity) throws Exception {
		change(entity, null, version(entity));
	}

	@Given("the draft of {string} is a chain of {int} shapes of {string}, each nested in the one before")
	public void shapeChain(String diagram, int levels, String entity) throws Exception {
		newDraft(diagram);
		String previous = null;
		for (int i = 0; i < levels; i++) {
			String id = UUID.randomUUID().toString();
			ObjectNode shape = shape(id, entityId(entity), null, BigDecimal.valueOf(i * 5L), BigDecimal.valueOf(i * 5L),
					BigDecimal.valueOf(2000 - i * 10L), BigDecimal.valueOf(2000 - i * 10L));
			if (previous != null) {
				shape.put("parentShapeId", previous);
			}
			this.shapes.add(shape);
			previous = id;
		}
	}

	@Given("the draft of {string} shows a new {string} named {string}")
	public void shownNew(String diagram, String metatype, String name) throws Exception {
		newDraft(diagram);
		addNew(metatype, name, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(300), BigDecimal.valueOf(200), null,
				null, null);
	}

	@Given("the draft nests a new {string} named {string} in {string} with the parent {string}")
	public void nestsNew(String metatype, String name, String container, String parent) throws Exception {
		addNew(metatype, name, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.valueOf(90), BigDecimal.valueOf(30),
				this.shapeIds.get(container), entityId(parent), null);
	}

	@Given("the draft nests a new {string} named {string} in {string} with the new parent {string}")
	public void nestsNewUnderNew(String metatype, String name, String container, String parent) throws Exception {
		addNew(metatype, name, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.valueOf(90), BigDecimal.valueOf(30),
				this.shapeIds.get(container), null, this.newKeys.get(parent));
	}

	@Given("the enterprise architect has saved the draft")
	public void saved() throws Exception {
		save(instance().architect());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	// When

	@When("the enterprise architect saves the draft")
	public void architectSaves() throws Exception {
		save(instance().architect());
	}

	@When("the viewer saves the draft")
	public void viewerSaves() throws Exception {
		save(instance().viewer());
	}

	@When("the draft of {string} is emptied and saved")
	public void emptied(String diagram) throws Exception {
		this.shapes = new ArrayList<>();
		this.lines = JsonMapper.shared().createArrayNode();
		save(instance().architect());
	}

	@When("the viewer opens the nesting view of {string}")
	public void open(String diagram) throws Exception {
		this.response = instance().oidc().get(VIEWS + "/" + id(diagram), instance().viewer().token());
	}

	@When("the nesting entity {string} is moved under {string} in the browser")
	public void moved(String entity, String parent) throws Exception {
		instance().oidc().update("UPDATE entities SET parent_entity_id = NULL WHERE id = " + entityId(entity));
		setParent(entityId(entity), entityId(parent));
	}

	@When("the viewer lists the children of {string} in {string}")
	public void list(String entity, String diagram) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/entities/" + entityId(entity) + "/children", instance().viewer().token());
	}

	@When("^the viewer lists the children of (an unknown entity|a deleted entity|a connection entity) in \"([^\"]*)\"$")
	public void listOther(String kind, String diagram) throws Exception {
		int entity = switch (kind) {
			case "an unknown entity" -> 999_999_999;
			case "a deleted entity" -> {
				insertEntity("Gone", "Application Component");
				deleted("Gone");
				yield entityId("Gone");
			}
			default -> {
				int source = entityId("SAP ERP");
				int connection = Integer.parseInt(instance().oidc()
					.single("INSERT INTO entities (metatype_id, name, created_by) SELECT id, 'Flow " + this.token
							+ "', (SELECT min(id) FROM persons) FROM metatypes WHERE name = 'Composition' RETURNING id"));
				instance().oidc()
					.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES ("
							+ connection + ", " + source + ", " + source + ")");
				yield connection;
			}
		};
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/entities/" + entity + "/children", instance().viewer().token());
	}

	@When("the viewer lists the children of {string} in an unknown diagram")
	public void listUnknown(String entity) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + UUID.randomUUID() + "/entities/" + entityId(entity) + "/children",
					instance().viewer().token());
	}

	@When("the metamodel administrator lists the children of {string} in {string}")
	public void listForbidden(String entity, String diagram) throws Exception {
		this.response = instance().oidc()
			.get(VIEWS + "/" + id(diagram) + "/entities/" + entityId(entity) + "/children",
					instance().metamodelAdmin().token());
	}

	@When("^(the viewer|the enterprise architect) sets the nesting setting to (true|false) (with the version read|with the version \\d+|without a version)$")
	public void sets(String who, String value, String version) throws Exception {
		Session session = who.equals("the viewer") ? instance().viewer() : instance().architect();
		int current = Integer.parseInt(instance().oidc().single("SELECT version::text FROM diagram_settings"));
		String ifMatch = version.equals("with the version read") ? "\"" + current + "\""
				: version.equals("without a version") ? null : "\"" + version.substring("with the version ".length()) + "\"";
		this.response = instance().send("PUT", SETTINGS, "{\"askNewParent\": " + value + "}", session,
				(ifMatch != null) ? Map.of("If-Match", ifMatch) : Map.of());
	}

	@When("the viewer reads the nesting setting")
	public void readSetting() throws Exception {
		this.response = instance().oidc().get(SETTINGS, instance().viewer().token());
	}

	@When("the metamodel administrator reads the nesting setting")
	public void readSettingForbidden() throws Exception {
		this.response = instance().oidc().get(SETTINGS, instance().metamodelAdmin().token());
	}

	@When("an API call saves {int} hierarchy lines and {int} parent changes in {string}")
	public void limits(int lineCount, int changeCount, String diagram) throws Exception {
		newDraft(diagram);
		if (lineCount > 0) {
			// 20 shapes of a parent and 201 shapes of its child give 4020 pairs, of which the first lines are used
			insertEntity("Limit parent", "Application Component");
			child("Limit child", "Application Component", "Limit parent");
			List<String> parents = new ArrayList<>();
			List<String> children = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				String id = UUID.randomUUID().toString();
				parents.add(id);
				this.shapes.add(shape(id, entityId("Limit parent"), null, BigDecimal.valueOf(i), BigDecimal.ZERO,
						BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
			}
			for (int i = 0; i < 201; i++) {
				String id = UUID.randomUUID().toString();
				children.add(id);
				this.shapes.add(shape(id, entityId("Limit child"), null, BigDecimal.valueOf(i), BigDecimal.valueOf(100),
						BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
			}
			for (int i = 0; i < lineCount; i++) {
				this.lines.addObject()
					.put("id", UUID.randomUUID().toString())
					.put("parentShapeId", parents.get(i / 201))
					.put("childShapeId", children.get(i % 201));
			}
		}
		else {
			// the parent is a structuring aid, which gets no composition connection
			String aid = instance().oidc()
				.single("INSERT INTO entities (metatype_id, name, created_by, is_structural_grouper) SELECT id, 'Limit aid "
						+ this.token + "', (SELECT min(id) FROM persons), true FROM metatypes WHERE name = 'Structuring aid' "
						+ "RETURNING id::text");
			instance().oidc()
				.update("INSERT INTO entities (metatype_id, name, created_by) SELECT (SELECT id FROM metatypes WHERE name = "
						+ "'Application Component'), 'Limit ' || g || ' " + this.token
						+ "', (SELECT min(id) FROM persons) FROM generate_series(1, " + changeCount + ") g");
			JsonNode ids = JsonMapper.shared()
				.readTree(instance().oidc()
					.single("SELECT json_agg(id ORDER BY id)::text FROM entities WHERE name LIKE 'Limit % " + this.token
							+ "' AND name NOT LIKE 'Limit aid%'"));
			for (JsonNode id : ids) {
				this.shapes.add(shape(UUID.randomUUID().toString(), id.asInt(), null, BigDecimal.valueOf(this.shapes.size()),
						BigDecimal.ZERO, BigDecimal.valueOf(90), BigDecimal.valueOf(30)));
				this.parentChanges.addObject()
					.put("entityId", id.asInt())
					.put("expectedVersion", 1)
					.put("parentEntityId", Integer.parseInt(aid));
			}
		}
		save(instance().architect());
	}

	@When("an API call saves a container of {string} of {bigdecimal} x {int} pt in {string}")
	public void container(String entity, BigDecimal width, int height, String diagram) throws Exception {
		newDraft(diagram);
		addShape(entity, BigDecimal.ZERO, BigDecimal.ZERO, width, BigDecimal.valueOf(height), null);
		save(instance().architect());
	}

	// Then

	@Then("the nesting answer is {int}")
	public void status(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the nesting answer is {int} naming {string}")
	public void statusNaming(int status, String field) {
		status(status);
		List<String> fields = new ArrayList<>();
		json().path("invalidFields").forEach((name) -> fields.add(name.asString()));
		assertThat(fields).anyMatch((name) -> name.equals(field) || name.startsWith(field + "."));
	}

	@Then("the nesting view of {string} has no shapes")
	public void noShapes(String diagram) throws Exception {
		assertThat(view(diagram).path("shapes")).isEmpty();
	}

	@Then("the nesting view of {string} shows {string} nested in {string}")
	public void nested(String diagram, String entity, String container) throws Exception {
		JsonNode view = view(diagram);
		assertThat(shape(view, entity).path("parentShapeId").asString()).isEqualTo(shape(view, container).path("id").asString());
	}

	@Then("the nesting view of {string} shows {string} without a container")
	public void notNested(String diagram, String entity) throws Exception {
		assertThat(shape(view(diagram), entity).path("parentShapeId").isNull()).isTrue();
	}

	@Then("the nesting view of {string} has no shape of {string}")
	public void noShapeOf(String diagram, String entity) throws Exception {
		assertThat(shapesOf(view(diagram), entity)).isEmpty();
	}

	@Then("the nesting view of {string} has {int} shape of {string}")
	public void shapeCount(String diagram, int count, String entity) throws Exception {
		assertThat(shapesOf(view(diagram), entity)).hasSize(count);
	}

	@Then("the nesting view of {string} has a hierarchy line from {string} to {string}")
	public void hasLine(String diagram, String parent, String child) throws Exception {
		JsonNode view = view(diagram);
		String from = shape(view, parent).path("id").asString();
		String to = shape(view, child).path("id").asString();
		boolean found = false;
		for (JsonNode line : view.path("hierarchyLines")) {
			found |= from.equals(line.path("parentShapeId").asString()) && to.equals(line.path("childShapeId").asString());
		}
		assertThat(found).as(view.path("hierarchyLines").toString()).isTrue();
	}

	@Then("the nesting view of {string} has no hierarchy line")
	public void noLine(String diagram) throws Exception {
		assertThat(view(diagram).path("hierarchyLines")).isEmpty();
	}

	@Then("the view carries askNewParent {word}")
	public void carries(String value) {
		assertThat(json().path("askNewParent").asBoolean()).isEqualTo(Boolean.parseBoolean(value));
	}

	@Then("the nesting entities {string}, {string} and {string} exist")
	public void exist(String first, String second, String third) throws Exception {
		for (String name : List.of(first, second, third)) {
			assertThat(instance().oidc()
				.single("SELECT count(*) FROM entities WHERE name = '" + n(name) + "' AND deleted_at IS NULL")).isEqualTo("1");
		}
	}

	@Then("the nesting entity {string} has no parent")
	public void noParent(String entity) throws Exception {
		assertThat(instance().oidc().single("SELECT parent_entity_id IS NULL FROM entities WHERE id = " + entityId(entity)))
			.isEqualTo("t");
	}

	@Then("the nesting entity {string} has the version {int}")
	public void entityVersion(String entity, int version) throws Exception {
		assertThat(version(entity)).isEqualTo(version);
	}

	@Then("the nesting entity {string} has the parent {string} and the version {int}")
	public void hasParent(String entity, String parent, int version) throws Exception {
		assertThat(instance().oidc().single("SELECT parent_entity_id::text FROM entities WHERE id = " + entityId(entity)))
			.isEqualTo(String.valueOf(entityId(parent)));
		assertThat(version(entity)).isEqualTo(version);
	}

	@Then("the audit log holds {string} for the nesting entity {string}")
	public void audited(String type, String entity) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '" + type + "' AND entity_id = " + entityId(entity)))
			.isEqualTo("1");
	}

	@Then("the audit log holds {string} with the old value {word} and the new value {word}")
	public void auditedSetting(String type, String before, String after) throws Exception {
		assertThat(auditCount(type)).isEqualTo(this.auditBefore + 1);
		assertThat(instance().oidc()
			.single("SELECT details ->> 'before' || '>' || (details ->> 'after') FROM audit.events WHERE event_type = '"
					+ type + "' ORDER BY id DESC LIMIT 1")).isEqualTo(before + ">" + after);
	}

	@Then("the audit log holds no new {string} event")
	public void noNewEvent(String type) throws Exception {
		assertThat(auditCount(type)).isEqualTo(this.auditBefore);
	}

	@Then("the nesting setting is {word} with the version {int}")
	public void settingIs(String value, int version) throws Exception {
		assertThat(instance().oidc().single("SELECT ask_new_parent::text || '/' || version FROM diagram_settings"))
			.isEqualTo(("true".equals(value) ? "true" : "false") + "/" + version);
		assertThat(instance().oidc().single("SELECT ask_new_parent::text FROM diagram_settings"))
			.isEqualTo(value);
	}

	@Then("the nesting setting read is {word} with the version {int}")
	public void settingRead(String value, int version) {
		assertThat(json().path("askNewParent").asBoolean()).isEqualTo(Boolean.parseBoolean(value));
		assertThat(json().path("version").asInt()).isEqualTo(version);
		assertThat(this.response.headers().firstValue("ETag")).hasValue("\"" + version + "\"");
	}

	@Then("^the children are (.+), all allowed, not truncated$")
	public void childrenAre(String quoted) {
		List<String> names = new ArrayList<>();
		Matcher matcher = QUOTED.matcher(quoted);
		while (matcher.find()) {
			names.add(n(matcher.group(1)));
		}
		List<String> found = new ArrayList<>();
		json().path("items").forEach((item) -> {
			found.add(item.path("entity").path("name").asString());
			assertThat(item.path("allowed").asBoolean()).isTrue();
		});
		assertThat(found).containsExactlyElementsOf(names);
		assertThat(json().path("truncated").asBoolean()).isFalse();
	}

	@Then("the child {string} is not allowed")
	public void notAllowed(String name) {
		assertThat(child(name).path("allowed").asBoolean()).isFalse();
	}

	@Then("the child {string} has {int} child")
	public void childCount(String name, int count) {
		assertThat(child(name).path("entity").path("childCount").asInt()).isEqualTo(count);
	}

	@Then("the child {string} has the composition connection of the pair, allowed by the viewpoint")
	public void composition(String name) {
		JsonNode connection = child(name).path("compositionConnection");
		assertThat(connection.isNull()).isFalse();
		assertThat(connection.path("allowed").asBoolean()).isTrue();
		assertThat(connection.path("connection").path("id").asInt()).isPositive();
	}

	@Then("the list has {int} items and truncated is {word}")
	public void listHas(int items, String truncated) {
		assertThat(json().path("items")).hasSize(items);
		assertThat(json().path("truncated").asBoolean()).isEqualTo(Boolean.parseBoolean(truncated));
	}

	// helpers

	private void newDraft(String diagram) {
		this.draftDiagram = diagram;
		this.shapes = new ArrayList<>();
		this.newEntities = JsonMapper.shared().createArrayNode();
		this.lines = JsonMapper.shared().createArrayNode();
		this.parentChanges = JsonMapper.shared().createArrayNode();
	}

	private void addShape(String entity, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height,
			String container) throws Exception {
		String id = UUID.randomUUID().toString();
		ObjectNode shape = shape(id, entityId(entity), null, x, y, width, height);
		if (container != null) {
			shape.put("parentShapeId", container);
		}
		this.shapes.add(shape);
		this.shapeIds.put(entity, id);
	}

	private void addNew(String metatype, String name, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height,
			String container, Integer parent, String parentKey) throws Exception {
		String key = UUID.randomUUID().toString();
		String id = UUID.randomUUID().toString();
		ObjectNode entity = this.newEntities.addObject()
			.put("key", key)
			.put("metatypeId", instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + metatype + "'"))
			.put("name", n(name));
		if (parent != null) {
			entity.put("parentEntityId", parent);
		}
		if (parentKey != null) {
			entity.put("parentNewEntityKey", parentKey);
		}
		ObjectNode shape = shape(id, null, key, x, y, width, height);
		if (container != null) {
			shape.put("parentShapeId", container);
		}
		this.shapes.add(shape);
		this.shapeIds.put(name, id);
		this.newKeys.put(name, key);
	}

	private void change(String entity, Integer parent, int version) throws Exception {
		ObjectNode change = this.parentChanges.addObject().put("entityId", entityId(entity)).put("expectedVersion", version);
		if (parent != null) {
			change.put("parentEntityId", parent);
		}
	}

	private void save(Session session) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode array = body.putArray("shapes");
		this.shapes.forEach(array::add);
		body.set("newEntities", this.newEntities);
		body.set("hierarchyLines", this.lines);
		body.set("parentChanges", this.parentChanges);
		int version = Integer.parseInt(
				instance().oidc().single("SELECT version::text FROM diagrams WHERE name = '" + n(this.draftDiagram) + "'"));
		this.response = instance().send("PUT", VIEWS + "/" + id(this.draftDiagram), body.toString(), session,
				Map.of("If-Match", "\"" + version + "\""));
		if (this.response.statusCode() == 200) {
			// continue from the saved layout, in which new entities are existing ones
			List<ObjectNode> saved = new ArrayList<>();
			for (JsonNode shape : json().path("shapes")) {
				ObjectNode kept = shape(shape.path("id").asString(), shape.path("entity").path("id").asInt(), null,
						shape.path("x").decimalValue(), shape.path("y").decimalValue(), shape.path("width").decimalValue(),
						shape.path("height").decimalValue());
				if (!shape.path("parentShapeId").isNull()) {
					kept.put("parentShapeId", shape.path("parentShapeId").asString());
				}
				saved.add(kept);
			}
			this.shapes = saved;
			this.newEntities = JsonMapper.shared().createArrayNode();
			this.lines = JsonMapper.shared().createArrayNode();
			json().path("hierarchyLines").forEach((line) -> this.lines.addObject()
				.put("id", line.path("id").asString())
				.put("parentShapeId", line.path("parentShapeId").asString())
				.put("childShapeId", line.path("childShapeId").asString()));
			this.parentChanges = JsonMapper.shared().createArrayNode();
		}
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

	private JsonNode view(String diagram) throws Exception {
		HttpResponse<String> read = instance().oidc().get(VIEWS + "/" + id(diagram), instance().viewer().token());
		assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(read.body());
	}

	private List<JsonNode> shapesOf(JsonNode view, String entity) {
		List<JsonNode> found = new ArrayList<>();
		for (JsonNode shape : view.path("shapes")) {
			if (n(entity).equals(shape.path("entity").path("name").asString())) {
				found.add(shape);
			}
		}
		return found;
	}

	private JsonNode shape(JsonNode view, String entity) {
		List<JsonNode> found = shapesOf(view, entity);
		assertThat(found).as("shapes of " + entity).hasSize(1);
		return found.get(0);
	}

	private JsonNode child(String name) {
		for (JsonNode item : json().path("items")) {
			if (n(name).equals(item.path("entity").path("name").asString())) {
				return item;
			}
		}
		throw new AssertionError("no child " + name + " in " + this.response.body());
	}

	private void insertEntity(String name, String metatype) throws Exception {
		instance().oidc()
			.update("INSERT INTO entities (metatype_id, name, created_by) SELECT m.id, '" + n(name)
					+ "', (SELECT min(id) FROM persons) FROM metatypes m WHERE m.name = '" + metatype
					+ "' AND NOT EXISTS (SELECT 1 FROM entities WHERE name = '" + n(name) + "')");
	}

	/** Sets the parent through the hierarchy operation, so that the composition connection is created. */
	private void setParent(int entity, int parent) throws Exception {
		HttpResponse<String> result = instance().send("PUT", "/api/v1/entities/" + entity + "/parent",
				"{\"parentEntityId\": " + parent + "}", instance().architect(),
				Map.of("If-Match", "\"" + versionOf(entity) + "\""));
		assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
	}

	private int auditCount(String type) throws Exception {
		return Integer.parseInt(instance().oidc().single("SELECT count(*) FROM audit.events WHERE event_type = '" + type + "'"));
	}

	private int version(String entity) throws Exception {
		return versionOf(entityId(entity));
	}

	private int versionOf(int entity) throws Exception {
		return Integer.parseInt(instance().oidc().single("SELECT version::text FROM entities WHERE id = " + entity));
	}

	private int entityId(String name) throws Exception {
		return Integer.parseInt(instance().oidc()
			.single("SELECT id::text FROM entities WHERE name = '" + n(name) + "' ORDER BY id DESC LIMIT 1"));
	}

	private String id(String diagram) throws Exception {
		return instance().oidc().single("SELECT id::text FROM diagrams WHERE name = '" + n(diagram) + "'");
	}

	/** The name with the token of the scenario, so that the shared instance keeps the scenarios apart. */
	private String n(String name) {
		this.used = true;
		return name + " " + this.token;
	}

	private JsonNode json() {
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

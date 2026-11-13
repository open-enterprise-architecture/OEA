package io.oea.steps.solution;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.metamodel.MetatypeEditingSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/solution/solution-deltas.feature (us-040-entity-deltas D1–D8), on the shared
 * instance of the metamodel features. Every scenario creates its own metatypes, properties and
 * solutions; entity names end with a scenario suffix.
 */
public class DeltaSteps {

	private final String suffix = "dl" + UUID.randomUUID().toString().substring(0, 8);

	private String solution;

	private String otherSolution;

	private UUID applications;

	private UUID components;

	private UUID dataFlows;

	private final Map<String, UUID> properties = new HashMap<>();

	private final Map<String, Integer> entities = new HashMap<>();

	private HttpResponse<String> capture;

	private HttpResponse<String> removal;

	private HttpResponse<String> read;

	private String otherDelta;

	private HttpResponse<String> edit;

	@Given("the architect created a solution for the deltas")
	public void solution() throws Exception {
		this.solution = createSolution("Deltas");
		this.applications = metatype("Application", false);
		this.components = metatype("Component", false);
		this.dataFlows = metatype("Data Flow", true);
	}

	@Given("the applications have the mandatory property {string} and the integer property {string}")
	public void applicationProperties(String mandatory, String integer) throws Exception {
		map(property(mandatory, "string", "public", null), "mandatory");
		map(property(integer, "integer", "public", null), "optional");
	}

	@Given("the property {string} of the applications is role-restricted to the role {string}")
	public void roleRestricted(String name, String role) throws Exception {
		UUID property = property(name, "integer", "role-restricted", null);
		instance().oidc().update("""
				INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES
				    ('%1$s', 'enterprise-architect', 'read'), ('%1$s', '%2$s', 'write')""".formatted(property, role));
		map(property, "optional");
	}

	@Given("the property {string} of the applications is connection-scoped")
	public void connectionScoped(String name) throws Exception {
		map(property(name, "string", "connection-scoped", metatype("Assignment", true)), "optional");
	}

	@Given("the landscape holds the application {string}")
	public void landscape(String name) throws Exception {
		this.entities.put(name, Integer.parseInt(instance().oidc().single("""
				INSERT INTO entities (metatype_id, name, created_by)
				SELECT '%s', '%s', min(id) FROM persons RETURNING id""".formatted(this.applications, named(name)))));
	}

	@Given("the solution holds {int} deltas")
	public void filled(int count) throws Exception {
		instance().oidc().update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, metatype_id, name, created_by)
				SELECT gen_random_uuid(), '%s', 'new', '%s', 'Filler ' || n, (SELECT min(id) FROM persons)
				FROM generate_series(1, %d) n""".formatted(this.solution, this.components, count));
	}

	@Given("the solution is in status {string}")
	public void status(String status) throws Exception {
		instance().oidc().update("UPDATE solutions SET status = '" + status + "' WHERE id = '" + this.solution + "'");
	}

	@When("the architect captures the new application {string} with {string} {string}")
	@Given("the architect captured the new application {string} with {string} {string}")
	public void captureNew(String name, String property, String value) throws Exception {
		this.capture = create(this.solution, newEntity(this.applications, named(name), null, Map.of(property, value)),
				instance().architect());
	}

	@When("the architect captures the new application {string} with {string} {string} and {string} {string}")
	@Given("the architect captured the new application {string} with {string} {string} and {string} {string}")
	public void captureNewWithTwo(String name, String first, String firstValue, String second, String secondValue)
			throws Exception {
		Map<String, String> values = new LinkedHashMap<>();
		values.put(first, firstValue);
		values.put(second, secondValue);
		this.capture = create(this.solution, newEntity(this.applications, named(name), null, values),
				instance().architect());
	}

	@When("the architect captures the new application {string} without properties")
	public void captureWithoutProperties(String name) throws Exception {
		this.capture = create(this.solution, newEntity(this.applications, named(name), null, Map.of()),
				instance().architect());
	}

	@When("the architect captures the new component {string}")
	public void captureComponent(String name) throws Exception {
		this.capture = create(this.solution, newEntity(this.components, named(name), null, Map.of()),
				instance().architect());
	}

	@When("the architect captures a new entity {string} of an unknown metatype")
	public void unknownMetatype(String name) throws Exception {
		this.capture = create(this.solution, newEntity(UUID.randomUUID(), named(name), null, Map.of()),
				instance().architect());
	}

	@When("the architect captures a new entity {string} of the data flow metatype")
	public void connectionMetatype(String name) throws Exception {
		this.capture = create(this.solution, newEntity(this.dataFlows, named(name), null, Map.of()),
				instance().architect());
	}

	@When("the architect captures a new application with a name of {int} characters")
	public void nameLength(int length) throws Exception {
		String name = (length == 0) ? "   " : "n".repeat(length);
		this.capture = create(this.solution, newEntity(this.applications, name, null, Map.of("Owner", "Sales")),
				instance().architect());
	}

	@When("the architect captures a new application with a description of {int} characters")
	public void descriptionLength(int length) throws Exception {
		this.capture = create(this.solution,
				newEntity(this.applications, named("Described"), "d".repeat(length), Map.of("Owner", "Sales")),
				instance().architect());
	}

	@When("the architect retires {string}")
	@Given("the architect retired {string}")
	public void retire(String name) throws Exception {
		this.capture = create(this.solution, retiring(this.entities.get(name)), instance().architect());
	}

	@When("the architect retires {string} in another solution")
	@Given("the architect retired {string} in another solution")
	public void retireElsewhere(String name) throws Exception {
		this.otherSolution = createSolution("Other");
		this.capture = create(this.otherSolution, retiring(this.entities.get(name)), instance().architect());
		this.otherDelta = JsonMapper.shared().readTree(this.capture.body()).path("id").asString();
	}

	@When("the architect retires an entity ID that does not exist")
	public void retireUnknown() throws Exception {
		this.capture = create(this.solution, retiring(Integer.MAX_VALUE), instance().architect());
	}

	@When("the architect retires an entity ID that does not exist in an unknown solution")
	public void retireInUnknownSolution() throws Exception {
		this.capture = create(UUID.randomUUID().toString(), retiring(Integer.MAX_VALUE), instance().architect());
	}

	@When("another holder of solution:write retires {string}")
	public void otherArchitect(String name) throws Exception {
		this.capture = create(this.solution, retiring(this.entities.get(name)),
				instance().sharedLogin("dev-architect-metamodel"));
	}

	@When("the viewer retires {string}")
	public void viewerRetires(String name) throws Exception {
		this.capture = create(this.solution, retiring(this.entities.get(name)), instance().viewer());
	}

	@When("the architect removes the delta of {string}")
	public void remove(String name) throws Exception {
		this.removal = delete(this.solution, deltaId(name), instance().architect());
	}

	@When("the viewer removes the delta of {string}")
	public void viewerRemoves(String name) throws Exception {
		this.removal = delete(this.solution, deltaId(name), instance().viewer());
	}

	@When("the architect removes that delta through the first solution")
	public void removeThroughFirst() throws Exception {
		this.removal = delete(this.solution, this.otherDelta, instance().architect());
	}

	@When("the architect reads the deltas of an unknown solution")
	public void readUnknown() throws Exception {
		this.read = instance().oidc()
			.get("/api/v1/solutions/" + UUID.randomUUID() + "/deltas", instance().architect().token());
	}

	@When("a portal reader reads the deltas")
	public void portalReader() throws Exception {
		this.read = instance().oidc()
			.get("/api/v1/solutions/" + this.solution + "/deltas", SolutionSteps.portalReaderSession().token());
	}

	@Then("the capture answers {int}")
	public void captureAnswers(int status) {
		assertThat(this.capture.statusCode()).as(this.capture.body()).isEqualTo(status);
	}

	@Then("the capture answers 422 naming {string}")
	public void captureNaming(String field) throws Exception {
		captureAnswers(422);
		List<String> fields = new ArrayList<>();
		JsonMapper.shared().readTree(this.capture.body()).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).contains(field);
	}

	@Then("the capture answers 422 naming {string} with the detail {string}")
	public void captureNamingWithDetail(String field, String detail) throws Exception {
		captureNaming(field);
		assertThat(JsonMapper.shared().readTree(this.capture.body()).path("detail").asString()).isEqualTo(detail);
	}

	@Then("the capture answers 409 with the detail {string}")
	public void captureConflict(String detail) throws Exception {
		captureAnswers(409);
		assertThat(JsonMapper.shared().readTree(this.capture.body()).path("detail").asString()).isEqualTo(detail);
	}

	@Then("the removal answers {int}")
	public void removalAnswers(int status) {
		assertThat(this.removal.statusCode()).as(this.removal.body()).isEqualTo(status);
	}

	@Then("the read answers {int}")
	public void readAnswers(int status) {
		assertThat(this.read.statusCode()).as(this.read.body()).isEqualTo(status);
	}

	@Then("the deltas list exactly {string}")
	public void listsExactly(String expected) throws Exception {
		List<String> listed = new ArrayList<>();
		deltas(instance().architect()).forEach((delta) -> listed.add(
				delta.path("deltaType").asString() + " " + delta.path("name").asString().replace(" " + this.suffix, "")));
		assertThat(String.join(", ", listed)).isEqualTo(expected);
	}

	@Then("the solution holds no delta")
	public void none() throws Exception {
		assertThat(deltas(instance().architect())).isEmpty();
	}

	@Then("the baseline does not list {string}")
	public void notInBaseline(String name) throws Exception {
		HttpResponse<String> page = instance().oidc()
			.get("/api/v1/solutions/" + this.solution + "/landscape?q="
					+ URLEncoder.encode(named(name), StandardCharsets.UTF_8), instance().architect().token());
		assertThat(page.statusCode()).as(page.body()).isEqualTo(200);
		assertThat(JsonMapper.shared().readTree(page.body()).path("items")).isEmpty();
	}

	@Then("the delta {string} holds {string} {string} and {string} {string}")
	public void holds(String name, String first, String firstValue, String second, String secondValue)
			throws Exception {
		JsonNode delta = delta(name, instance().architect());
		assertThat(text(value(delta, first))).isEqualTo(firstValue);
		assertThat(text(value(delta, second))).isEqualTo(secondValue);
	}

	@Then("the viewer reads {string} of {string} as redacted")
	public void redacted(String property, String name) throws Exception {
		JsonNode value = value(delta(name, instance().viewer()), property);
		assertThat(value.path("redacted").asBoolean()).isTrue();
		assertThat(value.path("valueInt").isNull()).isTrue();
	}

	@Then("the architect reads {string} of {string} as {string}")
	public void shown(String property, String name, String expected) throws Exception {
		JsonNode value = value(delta(name, instance().architect()), property);
		assertThat(value.path("redacted").asBoolean()).isFalse();
		assertThat(text(value)).isEqualTo(expected);
	}

	@Then("the delta {string} carries its entity ID")
	public void carriesEntityId(String name) throws Exception {
		assertThat(delta(name, instance().architect()).path("entityId").asInt()).isEqualTo(this.entities.get(name));
	}

	@Then("the other solution still holds {int} delta")
	public void otherHolds(int count) throws Exception {
		HttpResponse<String> response = instance().oidc()
			.get("/api/v1/solutions/" + this.otherSolution + "/deltas", instance().architect().token());
		assertThat(JsonMapper.shared().readTree(response.body()).path("items")).hasSize(count);
	}

	@Then("the audit log holds {string} of type {string} naming {string} without the values")
	public void auditedNew(String event, String type, String name) throws Exception {
		String details = latest(event);
		JsonNode node = JsonMapper.shared().readTree(details);
		assertThat(node.path("deltaType").asString()).isEqualTo(type);
		assertThat(node.path("name").asString()).isEqualTo(named(name));
		assertThat(node.path("metatypeId").asString()).isEqualTo(this.applications.toString());
		assertThat(details).doesNotContain("250").doesNotContain("\"Sales\"");
	}

	@Then("the audit log holds {string} of type {string} naming the entity of {string}")
	public void auditedRetiring(String event, String type, String name) throws Exception {
		JsonNode node = JsonMapper.shared().readTree(latest(event));
		assertThat(node.path("deltaType").asString()).isEqualTo(type);
		assertThat(node.path("entityId").asInt()).isEqualTo(this.entities.get(name));
	}

	@Then("the audit log holds no {string}")
	public void notAudited(String event) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '" + event + "' AND target_id = '"
					+ this.solution + "'"))
			.isEqualTo("0");
	}

	// us-041-modified-delta

	@Given("the landscape holds {string} with {string} {string} and {string} {string}")
	public void landscapeWithValues(String name, String first, String firstValue, String second, String secondValue)
			throws Exception {
		landscape(name);
		int entity = this.entities.get(name);
		catalogValue(entity, "Owner", "IT");
		catalogValue(entity, first, firstValue);
		catalogValue(entity, second, secondValue);
	}

	@Given("the catalog value of {string} of {string} changes to {string}")
	public void catalogChanges(String property, String name, String value) throws Exception {
		instance().oidc().update("UPDATE entity_property_values SET value_text = '" + value + "' WHERE entity_id = "
				+ this.entities.get(name) + " AND property_def_id = '" + this.properties.get(property) + "'");
	}

	@When("the architect modifies {string} with {string} {string}")
	@Given("the architect modified {string} with {string} {string}")
	public void modify(String name, String property, String value) throws Exception {
		this.capture = create(this.solution, modified(this.entities.get(name), null, null, Map.of(property, value)),
				instance().architect());
	}

	@When("the architect modifies {string} with the name {string} and the description {string}")
	public void renameAndDescribe(String name, String newName, String description) throws Exception {
		this.capture = create(this.solution,
				modified(this.entities.get(name), named(newName), description, Map.of()), instance().architect());
	}

	@When("the architect modifies {string} with an empty {string}")
	public void removeValue(String name, String property) throws Exception {
		modify(name, property, "");
	}

	@When("the architect modifies an entity ID that does not exist")
	public void modifyUnknown() throws Exception {
		this.capture = create(this.solution, modified(Integer.MAX_VALUE, "Other", null, Map.of()),
				instance().architect());
	}

	@When("the architect modifies {string} with a name of {int} characters")
	public void modifyNameLength(String name, int length) throws Exception {
		this.capture = create(this.solution,
				modified(this.entities.get(name), (length == 0) ? "   " : "n".repeat(length), null, Map.of()),
				instance().architect());
	}

	@When("the architect modifies {string} with a description of {int} characters")
	public void modifyDescriptionLength(String name, int length) throws Exception {
		this.capture = create(this.solution, modified(this.entities.get(name), null, "d".repeat(length), Map.of()),
				instance().architect());
	}

	@When("the architect edits the delta of {string} to {string} {string} and {string} {string}")
	public void editTwo(String name, String first, String firstValue, String second, String secondValue)
			throws Exception {
		Map<String, String> values = new LinkedHashMap<>();
		values.put(first, firstValue);
		values.put(second, secondValue);
		this.edit = put(deltaId(name), modification(null, null, values), instance().architect());
	}

	@When("the architect edits the delta of {string} to {string} {string}")
	public void editOne(String name, String property, String value) throws Exception {
		this.edit = put(deltaId(name), modification(null, null, Map.of(property, value)), instance().architect());
	}

	@When("the viewer edits the delta of {string} to {string} {string}")
	public void viewerEdits(String name, String property, String value) throws Exception {
		this.edit = put(deltaId(name), modification(null, null, Map.of(property, value)), instance().viewer());
	}

	@Then("the edit answers {int}")
	public void editAnswers(int status) {
		assertThat(this.edit.statusCode()).as(this.edit.body()).isEqualTo(status);
	}

	@Then("the edit answers 422 naming {string}")
	public void editNaming(String field) throws Exception {
		editAnswers(422);
		List<String> fields = new ArrayList<>();
		JsonMapper.shared().readTree(this.edit.body()).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).contains(field);
	}

	@Then("the delta of {string} changes {string} from {string} to {string}")
	public void changes(String name, String field, String before, String after) throws Exception {
		JsonNode change = change(delta(name, instance().architect()), field);
		assertThat(changeText(change.path("before"))).isEqualTo(before);
		assertThat(changeText(change.path("after"))).isEqualTo(after);
	}

	@Then("the delta of {string} changes the name to {string} and the description")
	public void renamed(String name, String newName) throws Exception {
		JsonNode delta = delta(name, instance().architect());
		assertThat(change(delta, "name").path("after").path("valueText").asString()).isEqualTo(named(newName));
		assertThat(change(delta, "name").path("before").path("valueText").asString()).isEqualTo(named(name));
		assertThat(change(delta, "description").path("after").isNull()).isFalse();
	}

	@Then("the catalog still holds {string} {string} for {string}")
	public void catalogHolds(String property, String value, String name) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT coalesce(value_text, value_int::text) FROM entity_property_values WHERE entity_id = "
					+ this.entities.get(name) + " AND property_def_id = '" + this.properties.get(property) + "'"))
			.isEqualTo(value);
	}

	@Then("the viewer reads the change of {string} of {string} as redacted")
	public void changeRedacted(String property, String name) throws Exception {
		JsonNode change = change(delta(name, instance().viewer()), property);
		assertThat(change.path("redacted").asBoolean()).isTrue();
		assertThat(change.path("before").isNull()).isTrue();
		assertThat(change.path("after").isNull()).isTrue();
	}

	@Given("the property {string} of the applications is writable by the role {string} but readable only by the role {string}")
	public void writableNotReadable(String name, String writer, String reader) throws Exception {
		UUID property = property(name, "integer", "role-restricted", null);
		instance().oidc().update("""
				INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES
				    ('%1$s', '%2$s', 'read'), ('%1$s', '%3$s', 'write')""".formatted(property, reader, writer));
		map(property, "optional");
	}

	@When("the architect modifies {string} with {string} {string} and {string} {string}")
	@Given("the architect modified {string} with {string} {string} and {string} {string}")
	public void modifyTwo(String name, String first, String firstValue, String second, String secondValue)
			throws Exception {
		Map<String, String> values = new LinkedHashMap<>();
		values.put(first, firstValue);
		values.put(second, secondValue);
		this.capture = create(this.solution, modified(this.entities.get(name), null, null, values),
				instance().architect());
		captureAnswers(201);
	}

	@Then("the delta of {string} still records {string} with the after value {string}")
	public void stillRecords(String name, String property, String value) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT after_value_int::text FROM entity_delta_property_values v JOIN entity_deltas d ON d.id = v.delta_id"
					+ " WHERE d.solution_id = '" + this.solution + "' AND d.entity_id = " + this.entities.get(name)
					+ " AND v.property_def_id = '" + this.properties.get(property) + "'"))
			.isEqualTo(value);
	}

	@Then("the audit log holds {string} naming the field {string} without {string}")
	public void auditedFields(String event, String property, String value) throws Exception {
		String details = latest(event);
		assertThat(JsonMapper.shared().readTree(details).path("fields").toString()).contains(property);
		assertThat(details).doesNotContain(value);
	}

	private String latest(String event) throws Exception {
		return instance().oidc()
			.single("SELECT details::text FROM audit.events WHERE event_type = '" + event + "' AND target_id = '"
					+ this.solution + "' ORDER BY id DESC LIMIT 1");
	}

	private String createSolution(String prefix) throws Exception {
		HttpResponse<String> created = instance().send("POST", "/api/v1/solutions",
				JsonMapper.shared().createObjectNode().put("name", prefix + " " + this.suffix + " " + UUID.randomUUID())
					.toString(),
				instance().architect());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		return JsonMapper.shared().readTree(created.body()).path("id").asString();
	}

	private ObjectNode newEntity(UUID metatype, String name, String description, Map<String, String> values) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("deltaType", "new");
		body.put("metatypeId", metatype.toString()).put("name", name);
		if (description != null) {
			body.put("description", description);
		}
		ArrayNode list = body.putArray("properties");
		values.forEach((property, value) -> list.add(JsonMapper.shared()
			.createObjectNode()
			.put("propertyDefId", this.properties.get(property).toString())
			.put("value", value)));
		return body;
	}

	private static ObjectNode retiring(int entityId) {
		return JsonMapper.shared().createObjectNode().put("deltaType", "retiring").put("entityId", entityId);
	}

	private HttpResponse<String> create(String solutionId, ObjectNode body, Session session) throws Exception {
		return instance().send("POST", "/api/v1/solutions/" + solutionId + "/deltas", body.toString(), session);
	}

	private HttpResponse<String> delete(String solutionId, String deltaId, Session session) throws Exception {
		return instance().send("DELETE", "/api/v1/solutions/" + solutionId + "/deltas/" + deltaId, null, session);
	}

	private JsonNode deltas(Session session) throws Exception {
		HttpResponse<String> response = instance().oidc()
			.get("/api/v1/solutions/" + this.solution + "/deltas", session.token());
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(response.body()).path("items");
	}

	private JsonNode delta(String name, Session session) throws Exception {
		for (JsonNode delta : deltas(session)) {
			if (delta.path("name").asString().equals(named(name))) {
				return delta;
			}
		}
		throw new AssertionError("no delta " + name);
	}

	private String deltaId(String name) throws Exception {
		return delta(name, instance().architect()).path("id").asString();
	}

	private JsonNode value(JsonNode delta, String property) {
		for (JsonNode value : delta.path("properties")) {
			if (value.path("propertyDefId").asString().equals(this.properties.get(property).toString())) {
				return value;
			}
		}
		throw new AssertionError("no value " + property);
	}

	private static String text(JsonNode value) {
		return value.path("valueText").isNull() || value.path("valueText").isMissingNode()
				? value.path("valueInt").asString() : value.path("valueText").asString();
	}

	private void catalogValue(int entity, String property, String value) throws Exception {
		UUID id = this.properties.get(property);
		if (id == null) {
			id = property(property, "string", "public", null);
			map(id, "optional");
		}
		boolean integer = "Users".equals(property);
		instance().oidc().update("INSERT INTO entity_property_values (entity_id, property_def_id, "
				+ (integer ? "value_int" : "value_text") + ") VALUES (" + entity + ", '" + id + "', "
				+ (integer ? value : "'" + value + "'") + ")");
	}

	private ObjectNode modified(int entityId, String name, String description, Map<String, String> values) {
		ObjectNode body = modification(name, description, values);
		body.put("deltaType", "modified").put("entityId", entityId);
		return body;
	}

	private ObjectNode modification(String name, String description, Map<String, String> values) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		if (name != null) {
			body.put("name", name);
		}
		if (description != null) {
			body.put("description", description);
		}
		ArrayNode list = body.putArray("properties");
		values.forEach((property, value) -> list.add(JsonMapper.shared()
			.createObjectNode()
			.put("propertyDefId", this.properties.get(property).toString())
			.put("value", value)));
		return body;
	}

	private HttpResponse<String> put(String deltaId, ObjectNode body, Session session) throws Exception {
		return instance().send("PUT", "/api/v1/solutions/" + this.solution + "/deltas/" + deltaId, body.toString(),
				session);
	}

	private JsonNode change(JsonNode delta, String field) {
		for (JsonNode change : delta.path("changes")) {
			boolean matches = ("name".equals(field) || "description".equals(field))
					? field.equals(change.path("field").asString())
					: change.path("propertyDefId").asString().equals(String.valueOf(this.properties.get(field)));
			if (matches) {
				return change;
			}
		}
		throw new AssertionError("no change of " + field + " in " + delta);
	}

	private static String changeText(JsonNode value) {
		if (value.isNull() || value.isMissingNode()) {
			return "no value";
		}
		return value.path("valueText").isNull() || value.path("valueText").isMissingNode()
				? value.path("valueInt").asString() : value.path("valueText").asString();
	}

	private UUID metatype(String name, boolean connection) throws Exception {
		return UUID.fromString(instance().oidc().single("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', '%s %s', %s) RETURNING id"""
			.formatted(name, this.suffix, connection)));
	}

	private UUID property(String name, String dataType, String mode, UUID scopingType) throws Exception {
		UUID id = UUID.fromString(instance().oidc().single("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES ('%s %s', 'p_%s', '%s', '%s', %s) RETURNING id""".formatted(name, this.suffix,
				UUID.randomUUID().toString().replace("-", "").substring(0, 12), dataType, mode,
				(scopingType != null) ? "'" + scopingType + "'" : "NULL")));
		this.properties.put(name, id);
		return id;
	}

	private void map(UUID property, String necessity) throws Exception {
		instance().oidc().update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES ('%s', '%s', '%s', DATE '2026-01-01', 'organization')""".formatted(this.applications, property,
				necessity));
	}

	private String named(String name) {
		return (name + " " + this.suffix).replace("'", "''");
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

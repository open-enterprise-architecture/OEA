package io.oea.steps.solution;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.metamodel.MetatypeEditingSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/solution/solution-go-live.feature (us-152-solution-go-live), on the
 * solution, entities and properties of {@link DeltaSteps}. Catalog changes after the capture are
 * written directly, as an entity edit would leave them.
 */
public class GoLiveSteps {

	private final DeltaSteps deltas;

	private HttpResponse<String> preview;

	private HttpResponse<String> resolution;

	private HttpResponse<String> goLive;

	private UUID contact;

	public GoLiveSteps(DeltaSteps deltas) {
		this.deltas = deltas;
	}

	// Preconditions

	@Given("the catalog value {string} of {string} is {string}")
	public void catalogValue(String property, String entity, String value) throws Exception {
		this.deltas.setCatalogValue(this.deltas.entityId(entity), property, value);
	}

	@Given("the catalog value {string} of {string} is changed to {string}")
	public void catalogValueChanged(String property, String entity, String value) throws Exception {
		String column = "Users".equals(property) ? "value_int" : "value_text";
		String literal = "Users".equals(property) ? value : "'" + value + "'";
		instance().oidc()
			.update("UPDATE entity_property_values SET %s = %s WHERE entity_id = %d AND property_def_id = '%s'".formatted(
					column, literal, this.deltas.entityId(entity), this.deltas.propertyId(property)));
	}

	@Given("the connection {string} leads from {string} to {string}")
	public void connection(String name, String source, String target) throws Exception {
		int id = Integer.parseInt(instance().oidc().single("""
				INSERT INTO entities (metatype_id, name, created_by) SELECT '%s', '%s', min(id) FROM persons RETURNING id"""
			.formatted(this.deltas.dataFlowsMetatype(), this.deltas.scenarioName(name))));
		instance().oidc()
			.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (%d, %d, %d)"
				.formatted(id, this.deltas.entityId(source), this.deltas.entityId(target)));
		this.deltas.registerEntity(name, id);
	}

	@Given("the applications get the mandatory property {string} after the capture")
	public void mandatoryAfterCapture(String name) throws Exception {
		UUID property = UUID.fromString(instance().oidc().single("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('%s', 'p_%s', 'string')
				RETURNING id""".formatted(this.deltas.scenarioName(name),
				UUID.randomUUID().toString().replace("-", "").substring(0, 12))));
		instance().oidc().update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES ('%s', '%s', 'mandatory', DATE '2026-01-01', 'organization')"""
			.formatted(this.deltas.applicationsMetatype(), property));
		this.contact = property;
	}

	@Given("{string} accepts at most {int} after the capture")
	public void maximum(String property, int maximum) throws Exception {
		instance().oidc()
			.update("UPDATE property_definitions SET max_value = %d WHERE id = '%s'".formatted(maximum,
					this.deltas.propertyId(property)));
	}

	@Given("the architect took the solution live")
	public void tookLive() throws Exception {
		goLive();
		assertThat(this.goLive.statusCode()).as(this.goLive.body()).isEqualTo(200);
	}

	// Actions

	@When("the architect previews the go-live")
	public void preview() throws Exception {
		this.preview = instance().oidc()
			.get("/api/v1/solutions/" + this.deltas.solutionId() + "/go-live", instance().architect().token());
	}

	@When("the architect takes the solution live")
	public void goLive() throws Exception {
		this.goLive = instance().send("POST", "/api/v1/solutions/" + this.deltas.solutionId() + "/go-live", null,
				instance().architect());
	}

	@When("the viewer takes the solution live")
	public void viewerGoesLive() throws Exception {
		this.goLive = instance().send("POST", "/api/v1/solutions/" + this.deltas.solutionId() + "/go-live", null,
				instance().viewer());
	}

	@When("the architect resolves {string} of {string} with the choice {string} and the value {string}")
	public void resolve(String property, String entity, String choice, String value) throws Exception {
		preview();
		String delta = deltaOf(entity);
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ObjectNode field = body.putArray("fields")
			.addObject()
			.put("field", this.deltas.propertyId(property).toString())
			.put("choice", choice);
		if (!value.isEmpty()) {
			field.put("value", value);
		}
		this.resolution = instance().send("POST",
				"/api/v1/solutions/" + this.deltas.solutionId() + "/deltas/" + delta + "/resolution", body.toString(),
				instance().architect());
	}

	// Outcomes

	@Then("the preview counts {int} new, {int} modified, {int} retired entities and {int} retired connection")
	public void counts(int created, int modified, int retired, int connections) throws Exception {
		JsonNode counts = ok(this.preview).path("counts");
		assertThat(counts.path("created").asInt()).isEqualTo(created);
		assertThat(counts.path("modified").asInt()).isEqualTo(modified);
		assertThat(counts.path("retired").asInt()).isEqualTo(retired);
		assertThat(counts.path("retiredConnections").asInt()).isEqualTo(connections);
	}

	@Then("the preview lists a conflict for {string} with before {string}, current {string} and delta {string}")
	public void conflict(String property, String before, String current, String after) throws Exception {
		JsonNode conflict = ok(this.preview).path("conflicts").get(0);
		assertThat(conflict.path("field").asString()).isEqualTo(this.deltas.propertyId(property).toString());
		assertThat(conflict.path("before").asString()).isEqualTo(before);
		assertThat(conflict.path("current").asString()).isEqualTo(current);
		assertThat(conflict.path("after").asString()).isEqualTo(after);
	}

	@Then("the preview lists no conflict")
	public void noConflict() throws Exception {
		preview();
		assertThat(ok(this.preview).path("conflicts")).isEmpty();
	}

	@Given("the architect undeletes {string}")
	public void undeletes(String entity) throws Exception {
		int id = this.deltas.entityId(entity);
		HttpResponse<String> restored = instance().send("POST", "/api/v1/entities/" + id + "/undelete", null,
				instance().architect(),
				java.util.Map.of("If-Match", "\"" + instance().oidc().single("SELECT version FROM entities WHERE id = " + id) + "\""));
		assertThat(restored.statusCode()).as(restored.body()).isEqualTo(200);
	}

	@Then("the preview lists a deletion conflict of type {string} for {string}")
	public void deletionConflict(String type, String entity) throws Exception {
		JsonNode conflicts = ok(this.preview).path("deletionConflicts");
		assertThat(conflicts).hasSize(1);
		assertThat(conflicts.get(0).path("deltaType").asString()).isEqualTo(type);
		assertThat(conflicts.get(0).path("entityId").asInt()).isEqualTo(this.deltas.entityId(entity));
	}

	@Then("the preview lists no deletion conflict")
	public void noDeletionConflict() throws Exception {
		preview();
		assertThat(ok(this.preview).path("deletionConflicts")).isEmpty();
	}

	@Then("the resolution answers {int}")
	public void resolutionAnswers(int status) {
		assertThat(this.resolution.statusCode()).as(this.resolution.body()).isEqualTo(status);
	}

	@Then("the resolution answers 422 naming {string}")
	public void resolutionNaming(String property) throws Exception {
		resolutionAnswers(422);
		assertThat(JsonMapper.shared().readTree(this.resolution.body()).path("invalidFields").get(0).asString())
			.isEqualTo(this.deltas.propertyId(property).toString());
	}

	@Then("the go-live answers {int}")
	public void goLiveAnswers(int status) {
		assertThat(this.goLive.statusCode()).as(this.goLive.body()).isEqualTo(status);
	}

	@Then("the go-live answers {int} of type {string}")
	public void goLiveAnswersType(int status, String type) throws Exception {
		goLiveAnswers(status);
		assertThat(JsonMapper.shared().readTree(this.goLive.body()).path("type").asString())
			.isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the catalog value {string} of {string} reads {string}")
	public void value(String property, String entity, String expected) throws Exception {
		assertThat(instance().oidc().single("""
				SELECT coalesce(value_text, value_int::text) FROM entity_property_values
				WHERE entity_id = %d AND property_def_id = '%s'""".formatted(this.deltas.entityId(entity),
				this.deltas.propertyId(property)))).isEqualTo(expected);
	}

	@Then("the solution is in status {string} after the go-live")
	public void status(String status) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT status FROM solutions WHERE id = '%s'".formatted(this.deltas.solutionId())))
			.isEqualTo(status);
	}

	@Then("the new application {string} exists with {string} {string}")
	public void created(String name, String property, String value) throws Exception {
		String entity = instance().oidc().single("""
				SELECT entity_id FROM entity_deltas WHERE solution_id = '%s' AND delta_type = 'new'"""
			.formatted(this.deltas.solutionId()));
		assertThat(entity).isNotNull();
		assertThat(instance().oidc().single("SELECT name FROM entities WHERE id = " + entity))
			.isEqualTo(this.deltas.scenarioName(name).replace("''", "'"));
		assertThat(instance().oidc().single("""
				SELECT value_text FROM entity_property_values WHERE entity_id = %s AND property_def_id = '%s'"""
			.formatted(entity, this.deltas.propertyId(property)))).isEqualTo(value);
	}

	@Then("{string} and the connection {string} are deleted")
	public void deleted(String entity, String connection) throws Exception {
		for (String name : List.of(entity, connection)) {
			assertThat(instance().oidc()
				.single("SELECT (deleted_at IS NOT NULL)::text FROM entities WHERE id = " + this.deltas.entityId(name)))
				.as(name)
				.isEqualTo("true");
		}
	}

	@Then("the solution lists the open issue {string} for {string} of {string}")
	public void openIssue(String kind, String property, String entity) throws Exception {
		HttpResponse<String> read = instance().oidc()
			.get("/api/v1/solutions/" + this.deltas.solutionId() + "/open-issues", instance().architect().token());
		List<String> issues = new ArrayList<>();
		ok(read).forEach((issue) -> issues.add(issue.path("kind").asString() + " " + issue.path("field").asString()
				+ " " + issue.path("entityName").asString()));
		UUID field = "Contact".equals(property) ? this.contact : this.deltas.propertyId(property);
		assertThat(issues).contains(kind + " " + field + " " + this.deltas.scenarioName(entity).replace("''", "'"));
	}

	@Then("the audit log holds {string} with {int} new, {int} modified and {int} retired")
	public void audit(String type, int created, int modified, int retired) throws Exception {
		JsonNode details = JsonMapper.shared().readTree(instance().oidc().single("""
				SELECT details::text FROM audit.events WHERE event_type = '%s' AND target_id = '%s'"""
			.formatted(type, this.deltas.solutionId())));
		assertThat(details.path("created").asInt()).isEqualTo(created);
		assertThat(details.path("modified").asInt()).isEqualTo(modified);
		assertThat(details.path("retired").asInt()).isEqualTo(retired);
	}

	private String deltaOf(String entity) throws Exception {
		return instance().oidc().single("SELECT id::text FROM entity_deltas WHERE solution_id = '%s' AND entity_id = %d"
			.formatted(this.deltas.solutionId(), this.deltas.entityId(entity)));
	}

	private static JsonNode ok(HttpResponse<String> response) throws Exception {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(response.body());
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

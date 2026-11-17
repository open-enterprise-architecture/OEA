package io.oea.steps.solution;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.metamodel.MetatypeEditingSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/solution/solution-open-issues.feature and
 * features/solution/metamodel-solution-impact.feature (us-153-open-issues), on the solution,
 * entities and properties of {@link DeltaSteps} and the go-live of {@link GoLiveSteps}. Metamodel
 * changes go through the API as the metamodel administrator; their preconditions are written
 * directly.
 */
public class OpenIssueSteps {

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private final DeltaSteps deltas;

	private final GoLiveSteps goLive;

	private final Map<String, String> solutions = new LinkedHashMap<>();

	private final Map<String, String> enumValues = new LinkedHashMap<>();

	private final Map<String, UUID> metatypes = new LinkedHashMap<>();

	private HttpResponse<String> response;

	private UUID extension;

	public OpenIssueSteps(DeltaSteps deltas, GoLiveSteps goLive) {
		this.deltas = deltas;
		this.goLive = goLive;
	}

	// Preconditions

	@Given("the solution went live with its open issues")
	public void wentLive() throws Exception {
		this.goLive.tookLive();
		registerCreated("Salesforce");
	}

	@Given("the applications have the optional property {string}")
	public void optionalProperty(String name) throws Exception {
		this.deltas.registerProperty(name, property(name, "string"));
		map(this.deltas.applicationsMetatype(), this.deltas.propertyId(name));
	}

	@Given("the applications have the enumeration property {string} with the values {string} and {string}")
	public void enumeration(String name, String first, String second) throws Exception {
		UUID property = property(name, "enum");
		this.deltas.registerProperty(name, property);
		instance().oidc()
			.update("INSERT INTO property_enum_values (property_def_id, value) VALUES ('%1$s', '%2$s'), ('%1$s', '%3$s')"
				.formatted(property, first, second));
		map(this.deltas.applicationsMetatype(), property);
	}

	@Given("the architect captured the new application {string} with the cost center {string} and the tier {string}")
	public void captureCostCenter(String name, String costCenter, String tier) throws Exception {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("Owner", "Sales");
		values.put("Cost Center", costCenter);
		values.put("Tier", tier);
		ok(this.deltas.capture(this.deltas.solutionId(), this.deltas.newApplication(name, values)), 201);
	}

	@Given("the solution {string} captured the new application {string} with {string} {string}")
	public void otherCaptures(String solution, String name, String property, String value) throws Exception {
		ok(this.deltas.capture(solution(solution), this.deltas.newApplication(name, Map.of("Owner", "Sales", property, value))),
				201);
	}

	@Given("the solution {string} retired {string} before the go-live")
	public void retiredEarlier(String solution, String entity) throws Exception {
		instance().oidc().update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				SELECT gen_random_uuid(), '%s', 'retiring', %d, min(id) FROM persons""".formatted(solution(solution),
				this.deltas.entityId(entity)));
	}

	@Given("the metatype {string} is based on the applications")
	public void basedOnApplications(String name) throws Exception {
		this.metatypes.put(name, UUID.fromString(instance().oidc().single("""
				INSERT INTO metatypes (config_id, name, is_connection, base_metatype_id)
				VALUES ('%s', '%s', false, '%s') RETURNING id""".formatted(INSTANCE, this.deltas.scenarioName(name),
				this.deltas.applicationsMetatype()))));
	}

	@Given("the architect captured the new {string} {string} with {string} {string}")
	public void captureOfMetatype(String metatype, String name, String property, String value) throws Exception {
		ObjectNode body = this.deltas.newApplication(name, Map.of("Owner", "Sales", property, value));
		body.put("metatypeId", this.metatypes.get(metatype).toString());
		ok(this.deltas.capture(this.deltas.solutionId(), body), 201);
	}

	@Given("the solution has a metamodel extension")
	public void extension() throws Exception {
		this.extension = UUID.fromString(instance().oidc().single("""
				INSERT INTO metamodel_configurations (id, scope, parent_id, solution_id, name)
				VALUES (gen_random_uuid(), 'solution', '%s', '%s', '%s') RETURNING id""".formatted(INSTANCE,
				this.deltas.solutionId(), this.deltas.scenarioName("Extension"))));
	}

	@Given("{string} has an external ID")
	public void externalId(String entity) throws Exception {
		instance().oidc()
			.update("UPDATE entities SET external_id = '%s' WHERE id = %d".formatted(this.deltas.scenarioName(entity),
					this.deltas.entityId(entity)));
	}

	@Given("{string} of the applications becomes optional")
	@When("{string} of the applications is made optional")
	public void optional(String property) throws Exception {
		instance().oidc()
			.update("UPDATE metatype_property_mappings SET necessity = 'optional' WHERE property_def_id = '%s'"
				.formatted(contactOr(property)));
	}

	@Given("the metamodel administrator mapped {string} to the applications again")
	public void mappedAgain(String property) throws Exception {
		map(this.deltas.applicationsMetatype(), this.deltas.propertyId(property));
	}

	// Actions: open issues

	@When("the architect resolves the issue {string} of {string} with {string}")
	public void resolveWith(String field, String entity, String value) throws Exception {
		this.response = resolve(instance().architect(), issue(field, entity), value, false);
	}

	@When("the architect removes the value of the issue {string} of {string}")
	@Given("the architect removed the value of the issue {string} of {string}")
	public void removeValue(String field, String entity) throws Exception {
		this.response = resolve(instance().architect(), issue(field, entity), null, true);
	}

	@When("the viewer resolves the issue {string} of {string} with {string}")
	public void viewerResolves(String field, String entity, String value) throws Exception {
		this.response = resolve(instance().viewer(), issue(field, entity), value, false);
	}

	@When("the architect takes the solution {string} live")
	public void otherGoesLive(String solution) throws Exception {
		this.response = instance().send("POST", "/api/v1/solutions/" + solution(solution) + "/go-live", null,
				instance().architect());
	}

	@When("the architect modifies {string} in the solution {string}")
	public void modifyInOther(String entity, String solution) throws Exception {
		this.response = this.deltas.capture(solution(solution), this.deltas.modificationOf(entity, Map.of("Users", "5")));
	}

	@When("the architect retires {string} in the solution {string}")
	public void retireInOther(String entity, String solution) throws Exception {
		this.response = this.deltas.capture(solution(solution), JsonMapper.shared()
			.createObjectNode()
			.put("deltaType", "retiring")
			.put("entityId", this.deltas.entityId(entity)));
	}

	@When("the architect writes {string} and {string} in a batch with atomic {word}")
	public void batch(String first, String second, String atomic) throws Exception {
		ObjectNode request = JsonMapper.shared().createObjectNode().put("atomic", Boolean.parseBoolean(atomic));
		ArrayNode entities = request.putArray("entities");
		for (String name : List.of(first, second)) {
			entities.addObject()
				.put("externalId", this.deltas.scenarioName(name))
				.put("name", this.deltas.scenarioName(name))
				.put("metatypeId", this.deltas.applicationsMetatype().toString())
				.put("description", "Changed by the batch")
				.putArray("properties")
				.addObject()
				.put("propertyDefId", this.deltas.propertyId("Owner").toString())
				.put("value", "IT");
		}
		this.response = instance().send("POST", "/api/v1/entities/batch", request.toString(), instance().architect());
	}

	@When("the architect restores {string} to version 1")
	public void restore(String entity) throws Exception {
		int id = this.deltas.entityId(entity);
		String version = instance().oidc().single("SELECT version::text FROM entities WHERE id = " + id);
		this.response = instance().send("POST", "/api/v1/entities/" + id + "/restore", "{\"targetVersion\": 1}",
				instance().architect(), Map.of("If-Match", "\"" + version + "\""));
	}

	@When("the architect queries a catalog of the applications")
	public void queryCatalog() throws Exception {
		String catalog = instance().oidc().single("""
				INSERT INTO catalogs (name, primary_metatype_id, created_by, last_modified_by)
				SELECT '%s', '%s', min(id), min(id) FROM persons RETURNING id""".formatted(
				this.deltas.scenarioName("Catalog " + UUID.randomUUID()), this.deltas.applicationsMetatype()));
		this.response = instance().oidc().get("/api/v1/catalogs/" + catalog + "/query?pageSize=200",
				instance().architect().token());
	}

	@When("the architect reads the history of {string}")
	public void history(String entity) throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/entities/" + this.deltas.entityId(entity) + "/history", instance().architect().token());
	}

	// Actions: metamodel

	@When("the metamodel administrator removes {string} from the applications")
	public void removeMapping(String property) throws Exception {
		removeMappingWith(property, "");
	}

	@When("the metamodel administrator removes {string} from the applications applying it to the solution")
	public void removeApplying(String property) throws Exception {
		removeMappingWith(property, "?decision=" + this.deltas.solutionId() + ":apply");
	}

	@When("the metamodel administrator removes {string} from the applications keeping it in the solution")
	@Given("the metamodel administrator removed {string} from the applications keeping it in the solution")
	public void removeKeeping(String property) throws Exception {
		removeMappingWith(property, "?decision=" + this.deltas.solutionId() + ":keep");
	}

	@When("the metamodel administrator removes {string} from the applications deciding only for the solution")
	public void removeDecidingOne(String property) throws Exception {
		removeKeeping(property);
	}

	@When("the metamodel administrator deletes the enumeration value {string} of {string}")
	public void deleteEnumValue(String value, String property) throws Exception {
		deleteEnumValueWith(value, property, "");
	}

	@When("the metamodel administrator deletes the enumeration value {string} of {string} keeping it in the solution")
	public void deleteEnumValueKeeping(String value, String property) throws Exception {
		deleteEnumValueWith(value, property, "?decision=" + this.deltas.solutionId() + ":keep");
	}

	@When("the metamodel administrator deletes the definition {string}")
	public void deleteDefinition(String property) throws Exception {
		this.response = instance().send("DELETE",
				"/api/v1/metamodel/property-definitions/" + this.deltas.propertyId(property), null,
				instance().metamodelAdmin());
	}

	@When("the metamodel administrator removes the base type of {string}")
	public void removeBase(String metatype) throws Exception {
		changeBase(metatype, null, null);
	}

	@When("the metamodel administrator removes the base type of {string} applying it to the solution")
	public void removeBaseApplying(String metatype) throws Exception {
		changeBase(metatype, null, "apply");
	}

	@When("the metamodel administrator bases {string} on {string}")
	public void rebase(String metatype, String base) throws Exception {
		changeBase(metatype, this.metatypes.get(base), null);
	}

	@When("the architect captures the new application {string} with the cost center {string} in the solution {string}")
	public void captureInSolution(String name, String costCenter, String solution) throws Exception {
		this.response = this.deltas.capture(solution(solution),
				this.deltas.newApplication(name, Map.of("Owner", "Sales", "Cost Center", costCenter)));
	}

	@When("the architect discards the cost center of {string} in the merge")
	public void discard(String name) throws Exception {
		String delta = instance().oidc().single("""
				SELECT id::text FROM entity_deltas WHERE solution_id = '%s' AND name = '%s'"""
			.formatted(this.deltas.solutionId(), this.deltas.scenarioName(name)));
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("fields")
			.addObject()
			.put("field", this.deltas.propertyId("Cost Center").toString())
			.put("choice", "discard");
		this.response = instance().send("POST",
				"/api/v1/solutions/" + this.deltas.solutionId() + "/deltas/" + delta + "/resolution", body.toString(),
				instance().architect());
	}

	@When("the architect reads the metatypes of the extension")
	public void readExtension() throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/metamodel/metatypes?configId=" + this.extension, instance().architect().token());
	}

	// Outcomes

	@Then("the answer is {int}")
	public void answer(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the answer is not {int}")
	public void notAnswer(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isNotEqualTo(status);
	}

	@Then("the answer is {int} of type {string}")
	public void answerType(int status, String type) throws Exception {
		answer(status);
		assertThat(json(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the answer names the issue {string} of {string}")
	public void namesIssue(String field, String entity) throws Exception {
		assertThat(this.response.body()).contains(issue(field, entity));
	}

	@Then("the answer names the delta of {string} in the solution {string}")
	public void namesDelta(String entity, String solution) throws Exception {
		String delta = instance().oidc().single("SELECT id::text FROM entity_deltas WHERE solution_id = '%s' AND entity_id = %d"
			.formatted(solution(solution), this.deltas.entityId(entity)));
		assertThat(json(this.response).path("deltaIds").toString()).contains(delta);
	}

	@Then("the answer lists the solutions {string}")
	public void listsSolutions(String names) throws Exception {
		List<String> expected = new ArrayList<>();
		for (String name : names.split(", ")) {
			expected.add("main".equals(name) ? this.deltas.solutionId() : solution(name));
		}
		List<String> listed = new ArrayList<>();
		json(this.response).path("solutions").forEach((entry) -> listed.add(entry.path("id").asString()));
		assertThat(listed).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Then("the open issue {string} of {string} is {string}")
	public void issueStatus(String field, String entity, String status) throws Exception {
		assertThat(issueNode(field, entity).path("status").asString()).isEqualTo(status);
	}

	@Then("the open issue {string} of {string} is resolved with {string}")
	public void issueResolution(String field, String entity, String resolution) throws Exception {
		JsonNode issue = issueNode(field, entity);
		assertThat(issue.path("status").asString()).isEqualTo("resolved");
		assertThat(issue.path("resolution").asString()).isEqualTo(resolution);
	}

	@Then("the solution counts {int} open issue(s)")
	public void count(int expected) throws Exception {
		HttpResponse<String> read = instance().oidc()
			.get("/api/v1/solutions/" + this.deltas.solutionId(), instance().architect().token());
		assertThat(json(read).path("openIssueCount").asInt()).isEqualTo(expected);
	}

	@Then("the value of {string} of {string} is {string}")
	public void catalogValue(String property, String entity, String expected) throws Exception {
		assertThat(instance().oidc().single("""
				SELECT coalesce(value_text, value_int::text, value_enum) FROM entity_property_values
				WHERE entity_id = %d AND property_def_id = '%s'""".formatted(this.deltas.entityId(entity),
				propertyOrContact(property)))).isEqualTo(expected);
	}

	@Then("the catalog holds no value of {string} for {string}")
	public void noCatalogValue(String property, String entity) throws Exception {
		assertThat(instance().oidc().single("""
				SELECT count(*)::text FROM entity_property_values WHERE entity_id = %d AND property_def_id = '%s'"""
			.formatted(this.deltas.entityId(entity), propertyOrContact(property)))).isEqualTo("0");
	}

	@Then("the solution {string} has no deltas")
	public void noDeltas(String solution) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*)::text FROM entity_deltas WHERE solution_id = '%s'".formatted(solution(solution))))
			.isEqualTo("0");
	}

	@Then("the batch item of {string} fails with {string} and the item of {string} is {word}")
	public void batchItems(String blocked, String code, String other, String outcome) throws Exception {
		assertThat(this.response.body()).contains(code);
		assertThat(description(blocked)).isNotEqualTo("Changed by the batch");
		assertThat("Changed by the batch".equals(description(other))).isEqualTo("written".equals(outcome));
	}

	@Then("the row of {string} is marked {word}")
	public void rowMarked(String entity, String marked) throws Exception {
		answer(200);
		int id = this.deltas.entityId(entity);
		for (JsonNode row : json(this.response).path("rows")) {
			if (row.path("entityId").asInt() == id) {
				assertThat(row.path("openIssues").asBoolean()).isEqualTo(Boolean.parseBoolean(marked));
				return;
			}
		}
		throw new AssertionError("no row of " + entity + " in " + this.response.body());
	}

	@Then("the history names the open issue {string} of the solution")
	public void historyNames(String field) throws Exception {
		answer(200);
		JsonNode issues = json(this.response).path("openIssues");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).path("solutionId").asString()).isEqualTo(this.deltas.solutionId());
		assertThat(issues.get(0).path("field").asString()).isEqualTo(propertyOrContact(field).toString());
	}

	@Then("the history names no open issue")
	public void historyNamesNone() throws Exception {
		answer(200);
		assertThat(json(this.response).path("openIssues")).isEmpty();
	}

	@Then("the mapping of {string} to the applications is {word}")
	public void mappingState(String property, String state) throws Exception {
		assertThat(instance().oidc().single("""
				SELECT count(*)::text FROM metatype_property_mappings WHERE metatype_id = '%s' AND property_def_id = '%s'"""
			.formatted(this.deltas.applicationsMetatype(), this.deltas.propertyId(property))))
			.isEqualTo("kept".equals(state) ? "1" : "0");
	}

	@Then("the delta of {string} has the cost center {string}")
	public void deltaCostCenter(String name, String value) throws Exception {
		assertThat(deltaValue(name, "Cost Center")).isEqualTo(value);
	}

	@Then("the delta of {string} has no cost center")
	public void deltaWithoutCostCenter(String name) throws Exception {
		assertThat(deltaValue(name, "Cost Center")).isNull();
	}

	@Then("the delta of {string} has the tier {string}")
	public void deltaTier(String name, String value) throws Exception {
		assertThat(deltaValue(name, "Tier")).isEqualTo(value);
	}

	@Then("the modification of {string} is removed")
	public void modificationRemoved(String entity) throws Exception {
		assertThat(instance().oidc().single("""
				SELECT count(*)::text FROM entity_deltas WHERE solution_id = '%s' AND entity_id = %d"""
			.formatted(this.deltas.solutionId(), this.deltas.entityId(entity)))).isEqualTo("0");
	}

	@Then("the solution keeps a snapshot of {string}")
	public void snapshotKept(String property) throws Exception {
		assertThat(snapshots(property)).isNotEqualTo("0");
	}

	@Then("the solution keeps no snapshot")
	public void noSnapshot() throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*)::text FROM solution_metamodel_snapshots WHERE solution_id = '%s'"
				.formatted(this.deltas.solutionId())))
			.isEqualTo("0");
	}

	@Then("the audit names the decision {string}")
	public void auditDecision(String decision) throws Exception {
		assertThat(instance().oidc().single("""
				SELECT details->'solutionDecisions'->0->>'decision' FROM audit.events
				WHERE event_type = 'metamodel.mapping-deleted' AND target_id = '%s'"""
			.formatted(this.deltas.applicationsMetatype()))).isEqualTo(decision);
	}

	@Then("the applications list {string} as a snapshot")
	public void listedAsSnapshot(String property) throws Exception {
		answer(200);
		for (JsonNode metatype : json(this.response)) {
			if (!metatype.path("id").asString().equals(this.deltas.applicationsMetatype().toString())) {
				continue;
			}
			for (JsonNode mapping : metatype.path("propertyMappings")) {
				if (mapping.path("propertyDefinition").path("id").asString()
					.equals(this.deltas.propertyId(property).toString())) {
					assertThat(mapping.path("snapshot").asBoolean()).isTrue();
					return;
				}
			}
		}
		throw new AssertionError(property + " is not listed: " + this.response.body());
	}

	@Then("the preview lists the cost center of {string} as not in the metamodel")
	public void previewNotInMetamodel(String name) throws Exception {
		HttpResponse<String> preview = instance().oidc()
			.get("/api/v1/solutions/" + this.deltas.solutionId() + "/go-live", instance().architect().token());
		List<String> kinds = new ArrayList<>();
		json(preview).path("conflicts").forEach((conflict) -> {
			if (conflict.path("field").asString().equals(this.deltas.propertyId("Cost Center").toString())
					&& conflict.path("entityName").asString().equals(this.deltas.scenarioName(name).replace("''", "'"))) {
				kinds.add(conflict.path("kind").asString());
			}
		});
		assertThat(kinds).containsExactly("not-in-metamodel");
	}

	@Then("the solution lists the open issue not in the metamodel keeping {string}")
	public void keptIssue(String value) throws Exception {
		assertThat(keptIssueNode(value).path("kind").asString()).isEqualTo("not-in-metamodel");
	}

	@Then("the solution lists no open issue keeping {string}")
	public void noKeptIssue(String value) throws Exception {
		HttpResponse<String> read = instance().oidc()
			.get("/api/v1/solutions/" + this.deltas.solutionId() + "/open-issues", instance().architect().token());
		assertThat(read.body()).doesNotContain("\"keptValue\":\"" + value + "\"");
	}

	@When("the architect writes {string} for the issue keeping {string}")
	public void writeKept(String value, String kept) throws Exception {
		this.response = resolve(instance().architect(), keptIssueNode(kept).path("id").asString(), value, false);
	}

	@When("the architect discards the issue keeping {string}")
	public void discardKept(String kept) throws Exception {
		this.response = resolve(instance().architect(), keptIssueNode(kept).path("id").asString(), null, true);
	}

	@Then("the issue keeping {string} is resolved with {string}")
	public void keptResolved(String kept, String resolution) throws Exception {
		JsonNode issue = keptIssueNode(kept);
		assertThat(issue.path("status").asString()).isEqualTo("resolved");
		assertThat(issue.path("resolution").asString()).isEqualTo(resolution);
	}

	// Helpers

	// us-084-plateaus D6: the question per plateau (US-156)

	private String plateau;

	@Given("the solutions {string} and {string} target the plateau {string}")
	public void solutionsTargetAPlateau(String first, String second, String name) throws Exception {
		instance().oidc().update("""
				INSERT INTO plateaus (id, name, status, created_by)
				SELECT gen_random_uuid(), 'Baseline', 'baseline', min(id) FROM persons ON CONFLICT DO NOTHING""");
		this.plateau = instance().oidc().single("""
				INSERT INTO plateaus (id, name, status, succeeds, basis, created_by)
				SELECT gen_random_uuid(), '%s', 'target', b.id, 'catalog', min(p.id)
				FROM plateaus b, persons p WHERE b.status = 'baseline' GROUP BY b.id RETURNING id::text"""
			.formatted(this.deltas.scenarioName(name)));
		for (String solution : List.of(first, second)) {
			instance().oidc().update("""
					UPDATE solutions SET from_plateau_id = (SELECT succeeds FROM plateaus WHERE id = '%1$s'),
					                     to_plateau_id = '%1$s' WHERE id = '%2$s'""".formatted(this.plateau,
					solution(solution)));
		}
	}

	@When("the metamodel administrator removes {string} from the applications keeping it for the plateau and applying it to the solution")
	public void removeKeepingForThePlateau(String property) throws Exception {
		removeMappingWith(property, "?decision=plateau:" + this.plateau + ":keep&decision=" + this.deltas.solutionId()
				+ ":apply");
	}

	@When("the metamodel administrator removes {string} from the applications deciding for the solution {string} of the plateau")
	public void removeDecidingForASolutionOfThePlateau(String property, String solution) throws Exception {
		removeMappingWith(property, "?decision=plateau:" + this.plateau + ":keep&decision=" + this.deltas.solutionId()
				+ ":apply&decision=" + solution(solution) + ":keep");
	}

	@Then("the answer names the plateau for {string} and {string}")
	public void answerNamesThePlateau(String first, String second) throws Exception {
		List<String> named = new ArrayList<>();
		json(this.response).path("solutions").forEach((entry) -> {
			if (!entry.path("plateau").isNull()) {
				assertThat(entry.path("plateau").path("id").asString()).isEqualTo(this.plateau);
				named.add(entry.path("id").asString());
			}
		});
		assertThat(named).containsExactlyInAnyOrder(solution(first), solution(second));
	}

	@Then("the solution {string} keeps a snapshot of {string}")
	public void solutionKeepsASnapshot(String solution, String property) throws Exception {
		assertThat(instance().oidc().single("""
				SELECT count(*)::text FROM solution_metamodel_snapshots WHERE solution_id = '%s' AND property_def_id = '%s'"""
			.formatted(solution(solution), this.deltas.propertyId(property)))).isNotEqualTo("0");
	}

	private void removeMappingWith(String property, String query) throws Exception {
		String mapping = instance().oidc().single("""
				SELECT id::text FROM metatype_property_mappings WHERE metatype_id = '%s' AND property_def_id = '%s'"""
			.formatted(this.deltas.applicationsMetatype(), this.deltas.propertyId(property)));
		this.response = instance().send("DELETE", "/api/v1/metamodel/metatypes/" + this.deltas.applicationsMetatype()
				+ "/property-mappings/" + mapping + query, null, instance().metamodelAdmin());
	}

	private void deleteEnumValueWith(String value, String property, String query) throws Exception {
		String id = instance().oidc().single("""
				SELECT id::text FROM property_enum_values WHERE property_def_id = '%s' AND value = '%s'"""
			.formatted(this.deltas.propertyId(property), value));
		this.response = instance().send("DELETE", "/api/v1/metamodel/property-definitions/"
				+ this.deltas.propertyId(property) + "/enum-values/" + id + query, null, instance().metamodelAdmin());
	}

	private void changeBase(String metatype, UUID base, String decision) throws Exception {
		UUID id = this.metatypes.get(metatype);
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", this.deltas.scenarioName(metatype).replace("''", "'"))
			.put("configId", INSTANCE);
		if (base != null) {
			body.put("baseMetatypeId", base.toString());
		}
		else {
			body.putNull("baseMetatypeId");
		}
		if (decision != null) {
			body.putArray("solutionDecisions")
				.addObject()
				.put("solutionId", this.deltas.solutionId())
				.put("decision", decision);
		}
		this.response = instance().send("PUT", "/api/v1/metamodel/metatypes/" + id, body.toString(),
				instance().metamodelAdmin());
	}

	private HttpResponse<String> resolve(RepositoryInstance.Session session, String issue, String value, boolean remove)
			throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ObjectNode entry = body.putArray("resolutions").addObject().put("issueId", issue);
		if (remove) {
			entry.put("remove", true);
		}
		else {
			entry.put("value", value);
		}
		return instance().send("POST", "/api/v1/solutions/" + this.deltas.solutionId() + "/open-issues/resolution",
				body.toString(), session);
	}

	private String issue(String field, String entity) throws Exception {
		return instance().oidc().single("""
				SELECT id::text FROM solution_open_issues WHERE solution_id = '%s' AND entity_id = %d AND property_def_id = '%s'"""
			.formatted(this.deltas.solutionId(), this.deltas.entityId(entity), propertyOrContact(field)));
	}

	private JsonNode issueNode(String field, String entity) throws Exception {
		String id = issue(field, entity);
		for (JsonNode issue : openIssues()) {
			if (issue.path("id").asString().equals(id)) {
				return issue;
			}
		}
		throw new AssertionError("no issue " + field + " of " + entity);
	}

	private JsonNode keptIssueNode(String value) throws Exception {
		for (JsonNode issue : openIssues()) {
			if (value.equals(issue.path("keptValue").asString())) {
				return issue;
			}
		}
		throw new AssertionError("no issue keeping " + value);
	}

	private JsonNode openIssues() throws Exception {
		HttpResponse<String> read = instance().oidc()
			.get("/api/v1/solutions/" + this.deltas.solutionId() + "/open-issues", instance().architect().token());
		assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
		return json(read);
	}

	/** The registered property, or the "Contact" that GoLiveSteps adds after the capture. */
	private UUID propertyOrContact(String name) throws Exception {
		UUID id = this.deltas.propertyId(name);
		return (id != null) ? id : contactOr(name);
	}

	private UUID contactOr(String name) throws Exception {
		UUID id = this.deltas.propertyId(name);
		if (id != null) {
			return id;
		}
		return UUID.fromString(instance().oidc().single("SELECT id::text FROM property_definitions WHERE name = '%s'"
			.formatted(this.deltas.scenarioName(name))));
	}

	private void registerCreated(String name) throws Exception {
		String id = instance().oidc().single("""
				SELECT d.entity_id::text FROM entity_deltas d WHERE d.solution_id = '%s' AND d.name = '%s'"""
			.formatted(this.deltas.solutionId(), this.deltas.scenarioName(name)));
		if (id != null) {
			this.deltas.registerEntity(name, Integer.parseInt(id));
		}
	}

	private String solution(String name) throws Exception {
		if ("main".equals(name)) {
			return this.deltas.solutionId();
		}
		String id = this.solutions.get(name);
		if (id == null) {
			id = this.deltas.newSolution(name);
			this.solutions.put(name, id);
		}
		return id;
	}

	private String description(String entity) throws Exception {
		return instance().oidc().single("SELECT description FROM entities WHERE id = " + this.deltas.entityId(entity));
	}

	private String deltaValue(String name, String property) throws Exception {
		return instance().oidc().single("""
				SELECT coalesce(v.after_value_text, v.after_value_enum) FROM entity_delta_property_values v
				JOIN entity_deltas d ON d.id = v.delta_id
				WHERE d.solution_id = '%s' AND d.name = '%s' AND v.property_def_id = '%s'""".formatted(
				this.deltas.solutionId(), this.deltas.scenarioName(name), this.deltas.propertyId(property)));
	}

	private String snapshots(String property) throws Exception {
		return instance().oidc().single("""
				SELECT count(*)::text FROM solution_metamodel_snapshots WHERE solution_id = '%s' AND property_def_id = '%s'"""
			.formatted(this.deltas.solutionId(), this.deltas.propertyId(property)));
	}

	private UUID property(String name, String dataType) throws Exception {
		return UUID.fromString(instance().oidc().single("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('%s', 'p_%s', '%s')
				RETURNING id""".formatted(this.deltas.scenarioName(name),
				UUID.randomUUID().toString().replace("-", "").substring(0, 12), dataType)));
	}

	private void map(UUID metatype, UUID property) throws Exception {
		instance().oidc().update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES ('%s', '%s', 'optional', DATE '2026-01-01', 'organization')""".formatted(metatype, property));
	}

	private static void ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
	}

	private static JsonNode json(HttpResponse<String> response) throws Exception {
		return JsonMapper.shared().readTree(response.body());
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

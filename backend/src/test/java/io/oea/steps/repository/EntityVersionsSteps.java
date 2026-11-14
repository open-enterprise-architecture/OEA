package io.oea.steps.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.metamodel.MetatypeEditingSteps;
import io.oea.steps.solution.SolutionSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/repository/entity-versions.feature (us-099-version-diff), with the
 * entities, persons and properties of {@link EntityHistorySteps}. Values are written directly
 * into the current tables and the history tables: a history row holds the value before the
 * change of its version (ADR-022).
 */
public class EntityVersionsSteps {

	private static final String NONE = "-";

	private final EntityHistorySteps history;

	private HttpResponse<String> response;

	private String stateBefore;

	public EntityVersionsSteps(EntityHistorySteps history) {
		this.history = history;
	}

	// Preconditions

	@Given("{string} and {string} are mapped to the metatype of {string} from {string}")
	public void mapTwo(String first, String second, String entity, String validFrom) throws Exception {
		map(first, entity, validFrom, 1);
		map(second, entity, validFrom, 2);
	}

	@Given("{string} is mapped to the metatype of {string} from {string}")
	public void mapOne(String property, String entity, String validFrom) throws Exception {
		map(property, entity, validFrom, 1);
	}

	/**
	 * The value of a field at each version, comma-separated from version 1, {@code -} for no
	 * value: the last one is the current value, and every version whose value differs from the
	 * one before gets a history row. {@code description} is the system field.
	 */
	@Given("the values of {string} of {string} by version are {string}")
	public void values(String field, String entity, String list) throws Exception {
		List<String> values = Arrays.stream(list.split(",")).map(String::strip).toList();
		int id = this.history.entityId(entity);
		long person = this.history.personId("Kurt");
		String current = values.get(values.size() - 1);
		boolean description = "description".equals(field);
		if (description) {
			instance().oidc()
				.update("UPDATE entities SET description = %s WHERE id = %d".formatted(text(current, "''"), id));
		}
		else if (!NONE.equals(current)) {
			instance().oidc()
				.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (%d, '%s', %s)"
					.formatted(id, this.history.propertyId(field), text(current, "NULL")));
		}
		for (int version = 2; version <= values.size(); version++) {
			String before = values.get(version - 2);
			String after = values.get(version - 1);
			if (before.equals(after)) {
				continue;
			}
			if (description) {
				instance().oidc().update("""
						INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
						VALUES (%d, %d, 'description', %s, %s, %d)""".formatted(id, version, text(before, "''"),
						text(after, "''"), person));
			}
			else {
				instance().oidc().update("""
						INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_text, changed_by)
						VALUES (%d, '%s', %d, %s, %d)""".formatted(id, this.history.propertyId(field), version,
						text(before, "NULL"), person));
			}
		}
	}

	// Reading versions

	@When("the enterprise architect reads the state of version {int} of {string}")
	public void architectReadsState(int version, String entity) throws Exception {
		state(instance().architect(), version, entity);
	}

	@When("the viewer reads the state of version {int} of {string}")
	public void viewerReadsState(int version, String entity) throws Exception {
		state(instance().viewer(), version, entity);
	}

	@When("the portal reader reads the state of version {int} of {string}")
	public void portalReaderReadsState(int version, String entity) throws Exception {
		state(SolutionSteps.portalReaderSession(), version, entity);
	}

	@When("the enterprise architect reads the diff of version {int} of {string}")
	public void architectReadsDiff(int version, String entity) throws Exception {
		compare(instance().architect(), version - 1, version, entity);
	}

	@When("the viewer reads the diff of version {int} of {string}")
	public void viewerReadsDiff(int version, String entity) throws Exception {
		compare(instance().viewer(), version - 1, version, entity);
	}

	@When("the enterprise architect compares version {int} with version {int} of {string}")
	public void architectCompares(int from, int to, String entity) throws Exception {
		compare(instance().architect(), from, to, entity);
	}

	@When("the enterprise architect sends {string} to version {int} of {string}")
	public void write(String method, int version, String entity) throws Exception {
		state(instance().architect(), version, entity);
		this.stateBefore = ok().toString();
		this.response = instance().send(method, path(entity, version), "{}", instance().architect());
	}

	@Then("the state lists the fields {string}")
	public void fields(String expected) throws Exception {
		List<String> fields = new ArrayList<>();
		ok().path("fields").forEach((field) -> fields.add(name(field)));
		assertThat(String.join(", ", fields)).isEqualTo(expected);
	}

	@Then("the state lists {string} as {string}")
	public void stateValue(String field, String value) throws Exception {
		assertThat(field(field).path("value").asString()).isEqualTo(value);
	}

	@Then("the state lists {string} as {string} and {string} without value")
	public void stateValues(String field, String value, String empty) throws Exception {
		stateValue(field, value);
		assertThat(field(empty).path("value").isNull()).isTrue();
	}

	@Then("the state does not list {string}")
	public void notListed(String field) throws Exception {
		List<String> fields = new ArrayList<>();
		ok().path("fields").forEach((candidate) -> fields.add(name(candidate)));
		assertThat(fields).doesNotContain(field);
	}

	@Then("the diff lists {string}")
	public void diff(String expected) throws Exception {
		List<String> changes = new ArrayList<>();
		for (JsonNode change : ok().path("changes")) {
			if (change.path("redacted").asBoolean()) {
				assertThat(change.path("oldValue").isNull() && change.path("newValue").isNull()).isTrue();
				changes.add(name(change) + " (restricted)");
			}
			else {
				changes.add(name(change) + ": " + shown(change.path("oldValue")) + " → " + shown(change.path("newValue")));
			}
		}
		assertThat(String.join(", ", changes)).isEqualTo(expected);
	}

	@Then("one {string} event with the channel {string} names {string} and {string}")
	public void event(String type, String channel, String property, String entity) throws Exception {
		String id = Integer.toString(this.history.entityId(entity));
		assertThat(events(type, id)).isEqualTo(1);
		JsonNode details = JsonMapper.shared().readTree(instance().oidc().single("""
				SELECT details::text FROM audit.events WHERE event_type = '%s' AND target_type = 'entity' AND target_id = '%s'"""
			.formatted(type, id)));
		assertThat(details.path("channel").asString()).isEqualTo(channel);
		JsonNode entry = details.path("properties").get(0);
		assertThat(entry.path("propertyDefId").asString()).isEqualTo(this.history.propertyId(property));
		assertThat(entry.path("entityIds").get(0).asInt()).isEqualTo(this.history.entityId(entity));
	}

	@Then("no {string} event names {string}")
	public void noEvent(String type, String entity) throws Exception {
		assertThat(events(type, Integer.toString(this.history.entityId(entity)))).isZero();
	}

	@Then("the version request is answered with {int}")
	public void answered(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the version request is answered with {int} allowing {string}")
	public void answeredAllowing(int status, String allowed) {
		answered(status);
		assertThat(this.response.headers().firstValue("Allow")).hasValue(allowed);
	}

	@Then("the state of version {int} of {string} is unchanged")
	public void unchanged(int version, String entity) throws Exception {
		state(instance().architect(), version, entity);
		assertThat(ok().toString()).isEqualTo(this.stateBefore);
	}

	private void map(String property, String entity, String validFrom, int order) throws Exception {
		instance().oidc().update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope, sort_order)
				SELECT metatype_id, '%s', 'optional', DATE '%s', 'organization', %d FROM entities WHERE id = %d"""
			.formatted(this.history.propertyId(property), validFrom, order, this.history.entityId(entity)));
	}

	private void state(Session session, int version, String entity) throws Exception {
		this.response = instance().oidc().get(path(entity, version), session.token());
	}

	private void compare(Session session, int from, int to, String entity) throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/entities/%d/diff?from=%d&to=%d".formatted(this.history.entityId(entity), from, to),
					session.token());
	}

	private String path(String entity, int version) {
		return "/api/v1/entities/%d/versions/%d".formatted(this.history.entityId(entity), version);
	}

	private JsonNode field(String name) throws Exception {
		for (JsonNode field : ok().path("fields")) {
			if (name(field).equals(name)) {
				return field;
			}
		}
		throw new AssertionError("no field " + name + " in " + this.response.body());
	}

	private String name(JsonNode field) {
		return field.path("isSystemField").asBoolean() ? field.path("field").asString()
				: this.history.propertyName(field.path("field").asString());
	}

	private static String shown(JsonNode value) {
		return value.isNull() ? NONE : value.asString();
	}

	private static String text(String value, String none) {
		return NONE.equals(value) ? none : "'" + value.replace("'", "''") + "'";
	}

	private static int events(String type, String entityId) throws Exception {
		return Integer.parseInt(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '%s' AND target_type = 'entity' AND target_id = '%s'"
				.formatted(type, entityId)));
	}

	private JsonNode ok() throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

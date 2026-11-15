package io.oea.steps.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.metamodel.MetatypeEditingSteps;
import io.oea.testsupport.auth.DevRealm;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/repository/entity-restore.feature and the restore scenarios of
 * features/repository/entity-history.feature (us-101-entity-restore), with the persons,
 * entities and properties of {@link EntityHistorySteps}. Each restore entity has a metatype of
 * its own, so mandatory mappings never reach other scenarios. A person with {@code entity:write}
 * but without {@code entity:restore} is {@code dev-analyst} with {@code integration-writer}
 * mapped to {@code oea-analysts} for the scenario.
 */
public class EntityRestoreSteps {

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private static final String NONE = "-";

	private final EntityHistorySteps history;

	private final Map<String, String> metatypes = new HashMap<>();

	private final Map<String, int[]> endpoints = new HashMap<>();

	private HttpResponse<String> response;

	private String stateBefore;

	private boolean mappedWriter;

	public EntityRestoreSteps(EntityHistorySteps history) {
		this.history = history;
	}

	@After
	public void reset() throws Exception {
		if (this.mappedWriter) {
			// the session of the scenario keeps no role of the removed mapping
			String mappings = """
					SELECT id FROM idp_group_mappings WHERE role_id = 'integration-writer'
					AND group_identifier = '%s'""".formatted(DevRealm.groupId("oea-analysts"));
			instance().oidc().update("DELETE FROM session_idp_roles WHERE idp_group_mapping_id IN (" + mappings + ")");
			instance().oidc().update("DELETE FROM idp_group_mappings WHERE id IN (" + mappings + ")");
		}
	}

	// Preconditions

	@Given("the restore entity {string} with {int} versions by {string}")
	public void entity(String name, int versions, String person) throws Exception {
		String metatype = instance().oidc().single("""
				INSERT INTO metatypes (config_id, name) VALUES ('%s', 'Restore Component %s') RETURNING id::text"""
			.formatted(CONFIG, UUID.randomUUID().toString().substring(0, 8)));
		this.metatypes.put(name, metatype);
		this.history.register(name, create(metatype, name, versions, person));
	}

	@Given("the restore connection {string} with {int} versions by {string} whose name was {string}")
	public void connection(String name, int versions, String person, String oldName) throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String flow = instance().oidc().single("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('%s', 'Restore Flow %s', true)
				RETURNING id::text""".formatted(CONFIG, suffix));
		String element = this.metatypes.values().iterator().next();
		int source = create(element, "Source " + suffix, 1, person);
		int target = create(element, "Target " + suffix, 1, person);
		int id = create(flow, name, versions, person);
		instance().oidc()
			.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (%d, %d, %d)"
				.formatted(id, source, target));
		instance().oidc().update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (%d, %d, 'name', '%s', '%s', %d)""".formatted(id, versions, oldName, name,
				this.history.personId(person)));
		this.history.register(name, id);
		this.endpoints.put(name, new int[] { source, target });
	}

	@Given("the restore property {string} mapped as {string}")
	public void mappedProperty(String name, String necessity) throws Exception {
		map(this.history.typedProperty(name, "public", "string"), necessity);
	}

	@Given("the restore enumeration {string} with the values {string}")
	public void enumeration(String name, String values) throws Exception {
		String id = this.history.typedProperty(name, "public", "enum");
		for (String value : values.split(",")) {
			instance().oidc()
				.update("INSERT INTO property_enum_values (property_def_id, value) VALUES ('%s', '%s')".formatted(id,
						value.strip()));
		}
		map(id, "optional");
	}

	/** As the values step of {@link EntityVersionsSteps}, in the enumeration column. */
	@Given("the enumeration values of {string} of {string} by version are {string}")
	public void enumerationValues(String property, String entity, String list) throws Exception {
		List<String> values = Arrays.stream(list.split(",")).map(String::strip).toList();
		int id = this.history.entityId(entity);
		String definition = this.history.propertyId(property);
		String current = values.get(values.size() - 1);
		if (!NONE.equals(current)) {
			instance().oidc()
				.update("INSERT INTO entity_property_values (entity_id, property_def_id, value_enum) VALUES (%d, '%s', '%s')"
					.formatted(id, definition, current));
		}
		for (int version = 2; version <= values.size(); version++) {
			String before = values.get(version - 2);
			if (before.equals(values.get(version - 1))) {
				continue;
			}
			instance().oidc().update("""
					INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_enum, changed_by)
					VALUES (%d, '%s', %d, %s, %d)""".formatted(id, definition, version,
					NONE.equals(before) ? "NULL" : "'" + before + "'", this.history.personId("Kurt")));
		}
	}

	@Given("the enterprise architect has read the state of version {int} of {string}")
	public void readState(int version, String entity) throws Exception {
		this.stateBefore = state(version, entity);
	}

	@Given("version {int} of {string} was restored from version {int} with the reason {string}")
	public void restoredVersion(int version, String entity, int target, String reason) throws Exception {
		instance().oidc().update("""
				UPDATE entity_version_headers SET restored_from_version = %d, change_reason = '%s'
				WHERE entity_id = %d AND version = %d""".formatted(target, reason, this.history.entityId(entity), version));
	}

	// Preview and restore

	@When("the enterprise architect previews the restore of {string} to version {int}")
	public void preview(String entity, int target) throws Exception {
		this.response = instance().oidc()
			.get(path(entity) + "?targetVersion=" + target, instance().architect().token());
	}

	@When("the enterprise architect restores {string} to version {int}")
	public void restore(String entity, int target) throws Exception {
		restore(instance().architect(), entity, target, null, current(entity));
	}

	@When("the enterprise architect restores {string} to version {int} with the reason {string}")
	public void restoreWithReason(String entity, int target, String reason) throws Exception {
		restore(instance().architect(), entity, target, reason, current(entity));
	}

	@When("the enterprise architect restores {string} to version {int} with a reason of {int} characters")
	public void restoreWithLongReason(String entity, int target, int length) throws Exception {
		restore(instance().architect(), entity, target, "r".repeat(length), current(entity));
	}

	@When("the enterprise architect restores {string} to version {int} with If-Match {string}")
	public void restoreWithIfMatch(String entity, int target, String ifMatch) throws Exception {
		restore(instance().architect(), entity, target, null, ifMatch);
	}

	@When("the enterprise architect restores {string} to version {int} without If-Match")
	public void restoreWithoutIfMatch(String entity, int target) throws Exception {
		restore(instance().architect(), entity, target, null, null);
	}

	@When("a person with {string} but without {string} restores {string} to version {int}")
	public void restoreWithoutPermission(String held, String missing, String entity, int target) throws Exception {
		assertThat(held).isEqualTo("entity:write");
		assertThat(missing).isEqualTo("entity:restore");
		instance().oidc().mapping("oea-analysts", "integration-writer");
		this.mappedWriter = true;
		restore(instance().login("dev-analyst"), entity, target, null, current(entity));
	}

	@When("the enterprise architect reads the restore history of {string} up to version {int}")
	public void readHistory(String entity, int version) throws Exception {
		String to = java.time.Instant.parse("2026-11-02T08:00:00Z").plus(java.time.Duration.ofHours(version)).toString();
		this.response = instance().oidc()
			.get("/api/v1/entities/" + this.history.entityId(entity) + "/history?to=" + to,
					instance().architect().token());
	}

	@When("the enterprise architect reads the restore history of {string}")
	public void readFullHistory(String entity) throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/entities/" + this.history.entityId(entity) + "/history", instance().architect().token());
	}

	// Outcomes

	@Then("the restore is answered with {int}")
	public void answered(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the restore is answered with {int} of type {string}")
	public void answeredWithType(int status, String type) throws Exception {
		answered(status);
		assertThat(body().path("type").asString()).isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the restore is answered with {int} of type {string} naming version {int}")
	public void answeredWithCurrent(int status, String type, int current) throws Exception {
		answeredWithType(status, type);
		assertThat(body().path("currentVersion").asInt()).isEqualTo(current);
	}

	@Then("the preview names version {int} as current")
	public void current(int version) throws Exception {
		assertThat(ok().path("currentVersion").asInt()).isEqualTo(version);
	}

	@Then("the preview lists only {string}")
	public void only(String expected) throws Exception {
		List<String> names = new ArrayList<>();
		ok().path("fields").forEach((field) -> names.add(name(field.path("field").asString())));
		assertThat(names).containsExactlyInAnyOrderElementsOf(Arrays.stream(expected.split(",")).map(String::strip).toList());
	}

	@Then("the preview lists {string} from {string} to {string} as restored")
	public void restored(String field, String current, String target) throws Exception {
		JsonNode entry = field(field);
		assertThat(shown(entry.path("currentValue"))).isEqualTo(current);
		assertThat(shown(entry.path("targetValue"))).isEqualTo(target);
		assertThat(entry.path("restore").asBoolean()).isTrue();
	}

	@Then("the preview lists {string} as not restored because {string}")
	public void notRestored(String field, String reason) throws Exception {
		JsonNode entry = field(field);
		assertThat(entry.path("restore").asBoolean()).isFalse();
		assertThat(entry.path("reason").asString()).isEqualTo(reason);
	}

	@Then("the preview lists {string} as redacted")
	public void redacted(String field) throws Exception {
		JsonNode entry = field(field);
		assertThat(entry.path("redacted").asBoolean()).isTrue();
		assertThat(entry.path("currentValue").isNull()).isTrue();
		assertThat(entry.path("targetValue").isNull()).isTrue();
	}

	@Then("the preview lists a warning for {string}")
	@Then("the restore lists a warning for {string}")
	public void warning(String property) throws Exception {
		List<String> fields = new ArrayList<>();
		ok().path("warnings").forEach((warning) -> fields.add(warning.path("field").asString()));
		assertThat(fields).anyMatch((field) -> field.contains(property));
	}

	@Then("the restore lists {string} as not restored because {string}")
	public void resultNotRestored(String field, String reason) throws Exception {
		List<String> entries = new ArrayList<>();
		ok().path("notRestored")
			.forEach((entry) -> entries.add(name(entry.path("field").asString()) + " " + entry.path("reason").asString()));
		assertThat(entries).contains(field + " " + reason);
	}

	@Then("{string} of {string} is {string}")
	public void value(String field, String entity, String expected) throws Exception {
		assertThat(current(field, entity)).isEqualTo(expected);
	}

	@Then("{string} of {string} has no value")
	public void noValue(String field, String entity) throws Exception {
		assertThat(current(field, entity)).isNull();
	}

	@Then("{string} keeps its ID and metatype")
	public void keepsIdAndMetatype(String entity) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT metatype_id::text FROM entities WHERE id = %d".formatted(this.history.entityId(entity))))
			.isEqualTo(this.metatypes.get(entity));
		assertThat(ok().path("entityId").asInt()).isEqualTo(this.history.entityId(entity));
	}

	@Then("the name of {string} is {string} and its endpoints are unchanged")
	public void connectionRestored(String entity, String name) throws Exception {
		int id = this.history.entityId(entity);
		assertThat(instance().oidc().single("SELECT name FROM entities WHERE id = %d".formatted(id))).isEqualTo(name);
		assertThat(instance().oidc()
			.single("SELECT source_entity_id || ',' || target_entity_id FROM entity_connections WHERE entity_id = %d"
				.formatted(id)))
			.isEqualTo(this.endpoints.get(entity)[0] + "," + this.endpoints.get(entity)[1]);
	}

	@Then("version {int} of {string} records the restore from version {int} with the reason {string}")
	public void recorded(int version, String entity, int target, String reason) throws Exception {
		assertThat(instance().oidc().single("""
				SELECT restored_from_version || ' ' || change_reason FROM entity_version_headers
				WHERE entity_id = %d AND version = %d""".formatted(this.history.entityId(entity), version)))
			.isEqualTo(target + " " + reason);
	}

	@Then("version {int} of {string} still has its state from before the restore")
	public void stateUnchanged(int version, String entity) throws Exception {
		assertThat(state(version, entity)).isEqualTo(this.stateBefore);
	}

	@Then("{string} is marked as missing {string}")
	public void marked(String entity, String property) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT string_agg(property_def_id::text, ',') FROM entity_mandatory_marks WHERE entity_id = %d"
				.formatted(this.history.entityId(entity))))
			.isEqualTo(this.history.propertyId(property));
	}

	@Then("{string} is still at version {int}")
	public void stillAt(String entity, int version) throws Exception {
		assertThat(current(entity)).isEqualTo("\"" + version + "\"");
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM entity_version_headers WHERE entity_id = %d".formatted(this.history.entityId(entity))))
			.isEqualTo(Integer.toString(version));
	}

	@Then("an {string} event names version {int} of {string}, the target version {int} and the changed field {string}")
	public void event(String type, int version, String entity, int target, String field) throws Exception {
		String entityId = Integer.toString(this.history.entityId(entity));
		JsonNode details = JsonMapper.shared().readTree(instance().oidc().single("""
				SELECT details::text FROM audit.events WHERE event_type = '%s' AND target_id = '%s'
				ORDER BY chain_seq DESC LIMIT 1""".formatted(type, entityId)));
		assertThat(details.path("version").asInt()).isEqualTo(version);
		assertThat(details.path("restoredFromVersion").asInt()).isEqualTo(target);
		assertThat(instance().oidc().single("""
				SELECT string_agg(c.property_def_id::text, ',') FROM audit.event_changes c
				JOIN audit.events e ON e.id = c.event_id
				WHERE e.event_type = '%s' AND e.target_id = '%s'""".formatted(type, entityId)))
			.isEqualTo(this.history.propertyId(field));
	}

	@Then("version {int} in the restore history carries the restore from version {int} with the reason {string}")
	public void historyCarries(int version, int target, String reason) throws Exception {
		JsonNode entry = version(ok(), version);
		assertThat(entry.path("restoredFromVersion").asInt()).isEqualTo(target);
		assertThat(entry.path("reason").asString()).isEqualTo(reason);
	}

	@Then("versions {int} to {int} in the restore history carry no restore")
	public void historyCarriesNone(int from, int to) throws Exception {
		JsonNode history = ok();
		for (int version = from; version <= to; version++) {
			JsonNode entry = version(history, version);
			assertThat(entry.path("restoredFromVersion").isNull()).as("version " + version).isTrue();
			assertThat(entry.path("reason").isNull()).as("version " + version).isTrue();
		}
	}

	@Then("the restore history lists the versions {string} and names {int} as the current version")
	public void historyCurrent(String versions, int current) throws Exception {
		JsonNode history = ok();
		List<String> listed = new ArrayList<>();
		history.path("versions").forEach((entry) -> listed.add(entry.path("version").asString()));
		assertThat(String.join(", ", listed)).isEqualTo(versions);
		assertThat(history.path("currentVersion").asInt()).isEqualTo(current);
	}

	private int create(String metatype, String name, int versions, String person) throws Exception {
		long personId = this.history.personId(person);
		int id = Integer.parseInt(instance().oidc().single("""
				INSERT INTO entities (metatype_id, name, version, created_by) VALUES ('%s', '%s', %d, %d) RETURNING id::text"""
			.formatted(metatype, name, versions, personId)));
		StringBuilder headers = new StringBuilder();
		for (int version = 1; version <= versions; version++) {
			headers.append((version > 1) ? ", " : "")
				.append("(%d, %d, %d, TIMESTAMPTZ '2026-11-02T08:00:00Z' + interval '%d hours')".formatted(id, version,
						personId, version));
		}
		instance().oidc()
			.update("INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES " + headers);
		return id;
	}

	private void map(String property, String necessity) throws Exception {
		instance().oidc().update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope, sort_order)
				VALUES ('%s', '%s', '%s', DATE '2026-01-01', 'organization', 1)"""
			.formatted(this.metatypes.values().iterator().next(), property, necessity));
	}

	private void restore(Session session, String entity, int target, String reason, String ifMatch) throws Exception {
		var body = JsonMapper.shared().createObjectNode().put("targetVersion", target);
		if (reason != null) {
			body.put("reason", reason);
		}
		this.response = instance().send("POST", path(entity), body.toString(), session,
				(ifMatch != null) ? Map.of("If-Match", ifMatch) : Map.of());
	}

	/** The current version of the entity as a strong ETag. */
	private String current(String entity) throws Exception {
		return "\"" + instance().oidc()
			.single("SELECT version FROM entities WHERE id = %d".formatted(this.history.entityId(entity))) + "\"";
	}

	private String current(String field, String entity) throws Exception {
		int id = this.history.entityId(entity);
		if ("description".equals(field)) {
			return instance().oidc().single("SELECT description FROM entities WHERE id = %d".formatted(id));
		}
		return instance().oidc().single("""
				SELECT coalesce(value_text, value_enum) FROM entity_property_values
				WHERE entity_id = %d AND property_def_id = '%s'""".formatted(id, this.history.propertyId(field)));
	}

	private String state(int version, String entity) throws Exception {
		HttpResponse<String> read = instance().oidc()
			.get("/api/v1/entities/" + this.history.entityId(entity) + "/versions/" + version,
					instance().architect().token());
		assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(read.body()).path("fields").toString();
	}

	private String path(String entity) {
		return "/api/v1/entities/" + this.history.entityId(entity) + "/restore";
	}

	private JsonNode field(String name) throws Exception {
		String key = "description".equals(name) || "name".equals(name) ? name : this.history.propertyId(name);
		for (JsonNode field : ok().path("fields")) {
			if (field.path("field").asString().equals(key)) {
				return field;
			}
		}
		throw new AssertionError("no field " + name + " in " + this.response.body());
	}

	private String name(String key) {
		return this.history.propertyName(key);
	}

	private static JsonNode version(JsonNode history, int version) {
		for (JsonNode entry : history.path("versions")) {
			if (entry.path("version").asInt() == version) {
				return entry;
			}
		}
		throw new AssertionError("no version " + version + " in " + history);
	}

	private static String shown(JsonNode value) {
		return value.isNull() ? NONE : value.asString();
	}

	private JsonNode body() throws Exception {
		return JsonMapper.shared().readTree(this.response.body());
	}

	private JsonNode ok() throws Exception {
		answered(200);
		return body();
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

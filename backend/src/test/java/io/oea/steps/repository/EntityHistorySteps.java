package io.oea.steps.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.metamodel.MetatypeEditingSteps;
import io.oea.steps.metamodel.PropertyVisibilitySteps;
import io.oea.testsupport.auth.DevRealm;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
 * Steps for features/repository/entity-history.feature and
 * features/metamodel/history-visibility.feature (us-098-entity-history), on the instance of the
 * metamodel features. The history is written directly into the history tables, version n at
 * 2026-11-02T08:00Z + n hours. `dev-auditor` holds `viewer` and `auditor`; the security
 * administrator is `dev-security-admin`. Every scenario resets the history visibility, the
 * override role list and the account link it changed.
 */
public class EntityHistorySteps {

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private static final String VISIBILITY = "/api/v1/admin/history-visibility";

	private static final Instant START = Instant.parse("2026-11-02T08:00:00Z");

	private final Map<String, Long> persons = new HashMap<>();

	private final Map<String, Integer> entities = new HashMap<>();

	/** Property definition IDs by the name in the scenario, and back. */
	private final Map<String, String> properties = new HashMap<>();

	private final Map<String, String> propertyNames = new HashMap<>();

	private final List<String> limitRoles = new ArrayList<>();

	private HttpResponse<String> response;

	private int eventsBefore;

	private boolean changedOverride;

	private String previousPersonMetatype;

	private boolean changedLink;

	@After
	public void reset() throws Exception {
		if (this.persons.isEmpty() && this.limitRoles.isEmpty()) {
			return;
		}
		instance().oidc().update("DELETE FROM history_visibility_settings");
		if (this.changedOverride) {
			instance().oidc().update("UPDATE authorization_roles SET overrides_property_visibility = false");
		}
		if (this.changedLink) {
			instance().oidc().update("UPDATE persons SET entity_id = NULL WHERE email LIKE 'dev-viewer@%'");
			instance().oidc()
				.update("UPDATE visibility_settings SET person_metatype_id = %s WHERE id = 1"
					.formatted((this.previousPersonMetatype != null) ? "'" + this.previousPersonMetatype + "'" : "NULL"));
		}
		for (String role : this.limitRoles) {
			instance().oidc().update("DELETE FROM authorization_roles WHERE id = '%s'".formatted(role));
		}
	}

	// Preconditions

	@Given("the history persons {string} and {string}")
	public void persons(String first, String second) throws Exception {
		for (String name : List.of(first, second)) {
			this.persons.put(name, Long.parseLong(instance().oidc()
				.single("INSERT INTO persons (name, email) VALUES ('%s', 'history-%s@example.org') RETURNING id::text"
					.formatted(name, UUID.randomUUID()))));
		}
	}

	@Given("the history entity {string} with {int} versions by {string}")
	public void entity(String name, int versions, String person) throws Exception {
		entity(name, metatype("Application Component"), versions, person, 0, null);
	}

	@Given("the history entity {string} with {int} versions by {string} and {int} by {string}")
	public void entity(String name, int first, String firstPerson, int second, String secondPerson) throws Exception {
		entity(name, metatype("Application Component"), first, firstPerson, second, secondPerson);
	}

	@Given("the history entity {string} of the subtype {string} of {string} with {int} versions by {string}")
	public void subtypeEntity(String name, String subtype, String base, int versions, String person) throws Exception {
		String id = instance().oidc().single("""
				INSERT INTO metatypes (config_id, name, base_metatype_id) VALUES ('%s', '%s %s', '%s') RETURNING id::text"""
			.formatted(CONFIG, subtype, UUID.randomUUID().toString().substring(0, 8), metatype(base)));
		entity(name, id, versions, person, 0, null);
	}

	@Given("the public property {string}")
	public void publicProperty(String name) throws Exception {
		property(name, "public", null);
	}

	@Given("the property {string} readable only by {string}")
	public void restrictedProperty(String name, String role) throws Exception {
		String id = property(name, "role-restricted", null);
		instance().oidc()
			.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES ('%s', '%s', 'read')"
				.formatted(id, role));
	}

	@Given("the connection-scoped property {string} with the viewer linked to an element outside its scope")
	public void scopedProperty(String name) throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String assignment = instance().oidc().single("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('%s', 'History Assignment %s', true)
				RETURNING id::text""".formatted(CONFIG, suffix));
		property(name, "connection-scoped", assignment);
		String personType = instance().oidc().single("""
				INSERT INTO metatypes (config_id, name) VALUES ('%s', 'History Person %s') RETURNING id::text"""
			.formatted(CONFIG, suffix));
		this.previousPersonMetatype = instance().oidc()
			.single("SELECT person_metatype_id::text FROM visibility_settings WHERE id = 1");
		this.changedLink = true;
		instance().oidc()
			.update("UPDATE visibility_settings SET person_metatype_id = '%s' WHERE id = 1".formatted(personType));
		String element = instance().oidc().single("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES ('%s', 'History Viewer', %d) RETURNING id::text"""
			.formatted(personType, this.persons.get("Kurt")));
		instance().oidc().update("UPDATE persons SET entity_id = %s WHERE email LIKE 'dev-viewer@%%'".formatted(element));
	}

	@Given("version {int} of {string} changed the name and {string}")
	public void changedNameAnd(int version, String entity, String property) throws Exception {
		instance().oidc().update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value, changed_by)
				VALUES (%d, %d, 'name', 'old', 'new', %d)""".formatted(this.entities.get(entity), version,
				this.persons.get("Kurt")));
		changed(version, entity, property);
	}

	@Given("version {int} of {string} changed {string}")
	public void changed(int version, String entity, String property) throws Exception {
		instance().oidc().update("""
				INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_text, changed_by)
				VALUES (%d, '%s', %d, 'before', %d)""".formatted(this.entities.get(entity), this.properties.get(property),
				version, this.persons.get("Kurt")));
	}

	@Given("the history visibility of {string} for {string} is {string}")
	public void configured(String role, String metatype, String mode) throws Exception {
		instance().oidc()
			.update("INSERT INTO history_visibility_settings (role_id, metatype_id, mode) VALUES ('%s', '%s', '%s')"
				.formatted(role, metatype(metatype), mode));
	}

	@Given("the role {string} is on the override role list")
	public void override(String role) throws Exception {
		this.changedOverride = true;
		instance().oidc()
			.update("UPDATE authorization_roles SET overrides_property_visibility = true WHERE id = '%s'".formatted(role));
	}

	// Reading the history

	@When("the enterprise architect reads the history of {string}")
	public void architectReads(String entity) throws Exception {
		read(instance().architect(), entity, "");
	}

	@When("the viewer reads the history of {string}")
	public void viewerReads(String entity) throws Exception {
		read(instance().viewer(), entity, "");
	}

	@When("the portal reader reads the history of {string}")
	public void portalReaderReads(String entity) throws Exception {
		mapping("oea-portal-readers", "portal-reader");
		read(instance().sharedLogin("dev-portal-reader"), entity, "");
	}

	@When("the person holding {string} and {string} reads the history of {string}")
	public void twoRolesRead(String first, String second, String entity) throws Exception {
		// dev-auditor is the only member of oea-auditors
		mapping("oea-auditors", first);
		mapping("oea-auditors", second);
		read(instance().sharedLogin("dev-auditor"), entity, "");
	}

	@When("the enterprise architect reads the history of an entity that does not exist")
	public void readUnknown() throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/entities/" + Integer.MAX_VALUE + "/history", instance().architect().token());
	}

	@When("the enterprise architect reads the history of {string} filtered by {string}")
	public void readByActor(String entity, String person) throws Exception {
		read(instance().architect(), entity, "?actorId=" + this.persons.get(person));
	}

	@When("the enterprise architect reads the history of {string} from version {int} to version {int} shifted by {int} ms")
	public void readPeriod(String entity, int from, int to, int shift) throws Exception {
		Instant start = time(from).plusMillis(shift);
		Instant end = time(to).minusMillis(shift);
		read(instance().architect(), entity, "?from=" + encode(start.toString()) + "&to=" + encode(end.toString()));
	}

	@When("the enterprise architect reads the history of {string} with the page size {int}")
	public void readPageSize(String entity, int pageSize) throws Exception {
		read(instance().architect(), entity, "?pageSize=" + pageSize);
	}

	@Then("the history lists the versions {string}")
	public void versions(String expected) throws Exception {
		assertThat(String.join(", ", versions(ok()).stream().map(String::valueOf).toList())).isEqualTo(expected);
	}

	@Then("version {int} lists {string}")
	public void versionLists(int number, String expected) throws Exception {
		JsonNode version = null;
		for (JsonNode candidate : ok().path("versions")) {
			if (candidate.path("version").asInt() == number) {
				version = candidate;
			}
		}
		assertThat(version).as("version %d", number).isNotNull();
		List<String> fields = new ArrayList<>();
		for (JsonNode change : version.path("changes")) {
			String field = change.path("isSystemField").asBoolean() ? change.path("field").asString()
					: this.propertyNames.getOrDefault(change.path("field").asString(), change.path("field").asString());
			fields.add(field + (change.path("restricted").asBoolean() ? " (restricted)" : ""));
			assertThat(change.has("oldValue") || change.has("newValue")).isFalse();
		}
		assertThat(String.join(", ", fields)).isEqualTo(expected);
	}

	@Then("the creation names {string}")
	public void creation(String person) throws Exception {
		assertThat(ok().path("created").path("changedBy").path("name").asString()).isEqualTo(person);
	}

	@Then("the actors of the history are {string}")
	public void actors(String expected) throws Exception {
		List<String> names = new ArrayList<>();
		ok().path("actors").forEach((actor) -> names.add(actor.path("name").asString()));
		assertThat(String.join(", ", names)).isEqualTo(expected);
	}

	@Then("the history lists {int} versions and has no next page")
	public void onePage(int count) throws Exception {
		JsonNode history = ok();
		assertThat(versions(history)).hasSize(count);
		assertThat(history.path("nextCursor").isNull()).isTrue();
		assertThat(history.path("page").path("totalPages").asInt()).isEqualTo(1);
	}

	@Then("the history lists {int} versions from {int} to {int}")
	public void pageRange(int count, int first, int last) throws Exception {
		List<Integer> versions = versions(ok());
		assertThat(versions).hasSize(count);
		assertThat(versions.get(0)).isEqualTo(first);
		assertThat(versions.get(versions.size() - 1)).isEqualTo(last);
	}

	@Then("the next page of the history lists the versions {string}")
	public void nextPage(String expected) throws Exception {
		JsonNode history = ok();
		String entity = String.valueOf(history.path("entityId").asInt());
		String cursor = history.path("nextCursor").asString();
		this.response = instance().oidc()
			.get("/api/v1/entities/" + entity + "/history?page=1&cursor=" + encode(cursor), instance().architect().token());
		versions(expected);
	}

	@Then("the history request is answered with {int}")
	public void answered(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the history request is answered with {int} naming {string}")
	public void answeredNaming(int status, String field) throws Exception {
		answered(status);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("invalidFields").toString())
			.isEqualTo("[\"" + field + "\"]");
	}

	// History visibility

	@When("the security administrator reads the history visibility")
	public void readVisibility() throws Exception {
		this.response = instance().oidc().get(VISIBILITY, PropertyVisibilitySteps.securityAdmin().token());
	}

	@When("the security administrator sets {string} to {string} for {string}")
	public void set(String role, String mode, String metatype) throws Exception {
		send(PropertyVisibilitySteps.securityAdmin(), entry(role, metatype(metatype), mode));
	}

	@When("the security administrator sets {string} to {string} and {string} to {string} for {string}")
	public void setTwo(String first, String firstMode, String second, String secondMode, String metatype)
			throws Exception {
		send(PropertyVisibilitySteps.securityAdmin(), entry(first, metatype(metatype), firstMode),
				entry(second, metatype(metatype), secondMode));
	}

	@When("the metamodel administrator sets {string} to {string} for {string}")
	public void setAsMetamodelAdministrator(String role, String mode, String metatype) throws Exception {
		send(instance().metamodelAdmin(), entry(role, metatype(metatype), mode));
	}

	@When("the security administrator sends a history visibility change with {int} entries")
	public void sendMany(int count) throws Exception {
		List<ObjectNode> entries = new ArrayList<>();
		List<String> metatypes = instance().oidc()
			.column("SELECT id::text FROM metatypes WHERE config_id = '%s' ORDER BY id LIMIT 10".formatted(CONFIG));
		int rolesNeeded = (count > 500) ? 0 : (count + metatypes.size() - 1) / metatypes.size();
		for (int i = 0; i < rolesNeeded; i++) {
			String role = "cucumber-limit-" + i + "-" + UUID.randomUUID().toString().substring(0, 6);
			instance().oidc().update("INSERT INTO authorization_roles (id, name) VALUES ('%1$s', '%1$s')".formatted(role));
			this.limitRoles.add(role);
		}
		for (int i = 0; i < count; i++) {
			// above the limit the size is checked first, so the pairs may repeat
			String role = this.limitRoles.isEmpty() ? "viewer" : this.limitRoles.get(i / metatypes.size());
			entries.add(entry(role, metatypes.get(i % metatypes.size()), "show-restricted"));
		}
		send(PropertyVisibilitySteps.securityAdmin(), entries.toArray(ObjectNode[]::new));
	}

	@When("the metamodel administrator imports the starter package again")
	public void reimport() throws Exception {
		this.response = instance().post("/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import", "",
				instance().metamodelAdmin(), Map.of());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@Then("the history visibility has no entries")
	public void noEntries() throws Exception {
		assertThat(visibility()).isEmpty();
	}

	@Then("the history visibility lists {string} for {string} as {string}")
	public void lists(String role, String metatype, String mode) throws Exception {
		assertThat(visibility()).contains(role + " " + metatype(metatype) + " " + mode);
	}

	@Then("the history visibility change is rejected with 422 naming {string}")
	public void rejected(String field) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("invalidFields").toString())
			.isEqualTo("[\"" + field + "\"]");
	}

	@Then("the history visibility change is answered with {int}")
	public void changeAnswered(int status) {
		answered(status);
	}

	@Then("a {string} event names {string}, {string}, {string} and {string}")
	public void eventNames(String type, String role, String metatype, String oldMode, String newMode) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		JsonNode details = lastEvent(type);
		assertThat(details.path("roleId").asString()).isEqualTo(role);
		assertThat(details.path("metatype").asString()).isEqualTo(metatype);
		assertThat(details.path("oldMode").asString()).isEqualTo(oldMode);
		assertThat(details.path("newMode").asString()).isEqualTo(newMode);
	}

	@Then("no further {string} event is written")
	public void noFurtherEvent(String type) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		assertThat(events(type)).isEqualTo(this.eventsBefore);
	}

	@Then("the last {string} event is flagged as self-benefiting")
	public void selfBenefiting(String type) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		assertThat(events(type)).isEqualTo(this.eventsBefore + 1);
		assertThat(lastEvent(type).path("selfBenefiting").asBoolean()).isTrue();
	}

	private void entity(String name, String metatypeId, int first, String firstPerson, int second, String secondPerson)
			throws Exception {
		int id = Integer.parseInt(instance().oidc().single("""
				INSERT INTO entities (metatype_id, name, version, created_by) VALUES ('%s', '%s', %d, %d) RETURNING id::text"""
			.formatted(metatypeId, name, first + second, this.persons.get(firstPerson))));
		this.entities.put(name, id);
		StringBuilder headers = new StringBuilder();
		for (int version = 1; version <= first + second; version++) {
			long person = this.persons.get((version <= first) ? firstPerson : secondPerson);
			headers.append((version > 1) ? ", " : "")
				.append("(%d, %d, %d, '%s')".formatted(id, version, person, time(version)));
		}
		instance().oidc()
			.update("INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES " + headers);
	}

	private String property(String name, String mode, String scopingType) throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String id = instance().oidc().single("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES ('%s %s', 'h_%s', 'string', '%s', %s) RETURNING id::text""".formatted(name, suffix, suffix, mode,
				(scopingType != null) ? "'" + scopingType + "'" : "NULL"));
		this.properties.put(name, id);
		this.propertyNames.put(id, name);
		return id;
	}

	private static String metatype(String name) throws Exception {
		return instance().oidc()
			.single("SELECT id::text FROM metatypes WHERE name = '%s' AND config_id = '%s'".formatted(name, CONFIG));
	}

	private static void mapping(String group, String role) throws Exception {
		if ("0".equals(instance().oidc()
			.single("SELECT count(*) FROM idp_group_mappings WHERE group_identifier = '%s' AND role_id = '%s'"
				.formatted(DevRealm.groupId(group), role)))) {
			instance().oidc().mapping(group, role);
		}
	}

	private void read(Session session, String entity, String query) throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/entities/" + this.entities.get(entity) + "/history" + query, session.token());
	}

	private void send(Session session, ObjectNode... entries) throws Exception {
		this.eventsBefore = events("visibility.history-changed");
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode list = body.putArray("entries");
		for (ObjectNode entry : entries) {
			list.add(entry);
		}
		this.response = instance().send("PUT", VISIBILITY, body.toString(), session);
	}

	private static ObjectNode entry(String role, String metatypeId, String mode) {
		return JsonMapper.shared().createObjectNode().put("roleId", role).put("metatypeId", metatypeId).put("mode", mode);
	}

	private List<String> visibility() throws Exception {
		HttpResponse<String> read = instance().oidc().get(VISIBILITY, PropertyVisibilitySteps.securityAdmin().token());
		assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
		List<String> entries = new ArrayList<>();
		JsonMapper.shared()
			.readTree(read.body())
			.path("entries")
			.forEach((entry) -> entries.add(entry.path("roleId").asString() + " " + entry.path("metatypeId").asString()
					+ " " + entry.path("mode").asString()));
		return entries;
	}

	private static int events(String type) throws Exception {
		return Integer.parseInt(
				instance().oidc().single("SELECT count(*) FROM audit.events WHERE event_type = '%s'".formatted(type)));
	}

	private static JsonNode lastEvent(String type) throws Exception {
		return JsonMapper.shared().readTree(instance().oidc().single("""
				SELECT details::text FROM audit.events WHERE event_type = '%s' ORDER BY chain_seq DESC LIMIT 1"""
			.formatted(type)));
	}

	private JsonNode ok() throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		return JsonMapper.shared().readTree(this.response.body());
	}

	private static List<Integer> versions(JsonNode history) {
		List<Integer> versions = new ArrayList<>();
		history.path("versions").forEach((version) -> versions.add(version.path("version").asInt()));
		return versions;
	}

	private static Instant time(int version) {
		return START.plus(Duration.ofHours(version));
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

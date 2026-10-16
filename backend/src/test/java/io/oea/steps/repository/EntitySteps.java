package io.oea.steps.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.audit.verify.ChainVerifier;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/repository/entity-write.feature and entity-batch-write.feature. The
 * scenarios share one instance with the starter package; each scenario uses its own external
 * IDs and names.
 */
public class EntitySteps {

	private static final String BATCH = "/api/v1/entities/batch";

	private static RepositoryInstance shared;

	/** IDs of metatypes and property definitions by name. */
	private static final Map<String, String> ids = new ConcurrentHashMap<>();

	private HttpResponse<String> response;

	@Given("an instance with the starter package, an enterprise architect and a viewer logged in through the IdP")
	public void instance() throws Exception {
		synchronized (EntitySteps.class) {
			if (shared == null) {
				shared = RepositoryInstance.start();
			}
		}
	}

	@AfterAll
	public static void stop() {
		if (shared != null) {
			shared.close();
			shared = null;
			ids.clear();
		}
	}

	// Preconditions

	@Given("an {string} {string} with external ID {string} exists")
	public void exists(String metatype, String name, String externalId) throws Exception {
		send(batch(entity(metatype, name, externalId)));
		assertCreated();
	}

	@Given("an {string} {string} with external ID {string} and {string} {string} exists")
	public void existsWithValue(String metatype, String name, String externalId, String property, String value)
			throws Exception {
		send(batch(entity(metatype, name, externalId, property, value)));
		assertCreated();
	}

	@Given("{int} {string} entities with the prefix {string} exist")
	public void manyExistAsSent(int count, String metatype, String prefix) throws Exception {
		send(many(count, metatype, prefix));
		assertThat(body().path("summary").path("created").asInt()).isEqualTo(count);
	}

	@Given("{int} {string} entities with the prefix {string} and the name {string} exist")
	public void manyExist(int count, String metatype, String prefix, String name) throws Exception {
		ObjectNode request = batch();
		for (int i = 0; i < count; i++) {
			request.withArray("entities").add(entity(metatype, name, prefix + ":" + i));
		}
		send(request);
		assertThat(body().path("summary").path("created").asInt()).isEqualTo(count);
	}

	// Writes

	@When("the enterprise architect writes an entity {string} with an unknown metatype")
	public void unknownMetatype(String name) throws Exception {
		ObjectNode entity = JsonMapper.shared().createObjectNode();
		entity.put("name", name).put("metatypeId", UUID.randomUUID().toString());
		send(batch(entity));
	}

	@When("the enterprise architect writes {string} with external ID {string} as {string}")
	public void writeAs(String name, String externalId, String metatype) throws Exception {
		send(batch(entity(metatype, name, externalId)));
	}

	@When("the enterprise architect writes a {string} {string} without the property {string}")
	public void writeWithout(String metatype, String name, String property) throws Exception {
		send(batch(entity(metatype, name, null)));
	}

	@When("the enterprise architect writes a/an {string} {string} with the property {string} {string}")
	public void writeWith(String metatype, String name, String property, String value) throws Exception {
		send(batch(entity(metatype, name, null, property, value)));
	}

	@When("the enterprise architect writes a {string} {string} with the properties {string} {string} and {string} {string}")
	public void writeWithTwo(String metatype, String name, String first, String firstValue, String second,
			String secondValue) throws Exception {
		send(batch(entity(metatype, name, null, first, firstValue, second, secondValue)));
	}

	@When("the enterprise architect writes {string} with external ID {string} and {string} {string}")
	public void writeComponent(String name, String externalId, String property, String value) throws Exception {
		send(batch(entity("Application Component", name, externalId, property, value)));
	}

	@When("the enterprise architect writes {string} with external ID {string} without properties")
	public void writeWithoutProperties(String name, String externalId) throws Exception {
		send(batch(entity("Application Component", name, externalId)));
	}

	@When("the enterprise architect writes {string} with external ID {string} sending {string} {string} and {string} {string}")
	public void writeWithHeaders(String name, String externalId, String firstHeader, String firstValue,
			String secondHeader, String secondValue) throws Exception {
		this.response = shared.post(BATCH, batch(entity("Application Component", name, externalId)).toString(),
				shared.architect(), Map.of(firstHeader, firstValue, secondHeader, secondValue));
	}

	@When("the enterprise architect writes {string} with external ID {string} with strategy {string}")
	public void writeWithStrategy(String name, String externalId, String strategy) throws Exception {
		send(batch(entity("Application Component", name, externalId)).put("strategy", strategy));
	}

	@When("the viewer sends a batch with {int} {string} entities with the prefix {string}")
	public void viewerSends(int count, String metatype, String prefix) throws Exception {
		this.response = shared.post(BATCH, many(count, metatype, prefix).toString(), shared.viewer(), Map.of());
	}

	@When("the enterprise architect sends a batch with {int} {string} entities with the prefix {string}")
	public void architectSends(int count, String metatype, String prefix) throws Exception {
		send(many(count, metatype, prefix));
	}

	@When("the enterprise architect sends a batch with the external ID {string} twice")
	public void twice(String externalId) throws Exception {
		send(batch(entity("Application Component", "First", externalId),
				entity("Application Component", "Second", externalId)));
	}

	@When("the enterprise architect sends {string} and the new {string} with strategy {string}")
	public void sendTwo(String existing, String fresh, String strategy) throws Exception {
		send(batch(entity("Application Component", "Changed", existing), entity("Application Component", "New", fresh))
			.put("strategy", strategy));
	}

	@When("the enterprise architect sends {int} new {string} entities with the prefix {string}, {int} of them without {string}")
	public void sendChecked(int count, String metatype, String prefix, int faulty, String property) throws Exception {
		send(checked(count, metatype, prefix, faulty, property));
	}

	@When("the enterprise architect sends {int} new {string} entities with the prefix {string}, {int} of them without {string}, atomically")
	public void sendCheckedAtomically(int count, String metatype, String prefix, int faulty, String property)
			throws Exception {
		send(checked(count, metatype, prefix, faulty, property).put("atomic", true));
	}

	@When("the enterprise architect sends a batch with one entity {string} and one connection")
	public void withConnection(String externalId) throws Exception {
		ObjectNode request = batch(entity("Application Component", "Source", externalId));
		request.putArray("connections").addObject().put("connectionMetatypeId", UUID.randomUUID().toString());
		send(request);
	}

	// Outcomes

	@Then("the item is rejected with the code {string} for the field {string}")
	public void itemRejected(String code, String field) {
		JsonNode item = item(0);
		assertThat(item.path("status").asString()).isEqualTo("error");
		assertThat(item.path("errors").valueStream()
			.anyMatch((error) -> code.equals(error.path("code").asString())
					&& field.equals(error.path("field").asString())))
			.as(item.toString())
			.isTrue();
	}

	@Then("the item is created with a warning for the field {string}")
	public void createdWithWarningField(String field) {
		JsonNode item = item(0);
		assertThat(item.path("status").asString()).isEqualTo("created");
		assertThat(item.path("warnings").valueStream().map((w) -> w.path("field").asString())).contains(field);
	}

	@Then("the item is created with a warning with the code {string}")
	public void createdWithWarningCode(String code) {
		JsonNode item = item(0);
		assertThat(item.path("status").asString()).isEqualTo("created");
		assertThat(item.path("warnings").valueStream().map((w) -> w.path("code").asString())).contains(code);
	}

	@Then("{string} has no value for {string}")
	public void noValue(String name, String property) throws Exception {
		assertThat(shared.oidc().single("""
				SELECT count(*)::text FROM entity_property_values v JOIN entities e ON e.id = v.entity_id
				JOIN property_definitions d ON d.id = v.property_def_id WHERE e.name = '%s' AND d.name = '%s'"""
			.formatted(name, property))).isEqualTo("0");
	}

	@Then("the entity {string} has version {int}")
	public void version(String externalId, int version) throws Exception {
		assertThat(shared.oidc().single("SELECT version::text FROM entities WHERE external_id = '" + externalId + "'"))
			.isEqualTo(String.valueOf(version));
	}

	@Then("the history of version {int} of {string} contains the previous name {string} and the previous {string} {string} and nothing else")
	public void history(int version, String externalId, String name, String property, String value) throws Exception {
		String entity = "(SELECT id FROM entities WHERE external_id = '" + externalId + "')";
		assertThat(shared.oidc().column("SELECT field_name || '=' || old_value FROM entity_system_field_history "
				+ "WHERE entity_id = " + entity + " AND entity_version = " + version))
			.containsExactly("name=" + name);
		assertThat(shared.oidc().column("""
				SELECT d.name || '=' || h.value_enum FROM entity_property_value_history h
				JOIN property_definitions d ON d.id = h.property_def_id
				WHERE h.entity_id = %s AND h.entity_version = %d""".formatted(entity, version)))
			.containsExactly(property + "=" + value);
	}

	@Then("the item is reported as {string}")
	public void reportedAs(String status) {
		assertThat(item(0).path("status").asString()).isEqualTo(status);
	}

	@Then("the entity {string} has version {int} and one audit event")
	public void versionAndOneEvent(String externalId, int version) throws Exception {
		version(externalId, version);
		assertThat(shared.oidc().single("SELECT count(*)::text FROM audit.events WHERE event_type LIKE 'entity.%' "
				+ "AND entity_id = (SELECT id FROM entities WHERE external_id = '" + externalId + "')"))
			.isEqualTo("1");
	}

	@Then("the audit trail contains {string} for {string} with the architect as actor, the new values {string} and {string} and a batch ID")
	public void audited(String eventType, String externalId, String first, String second) throws Exception {
		String event = shared.oidc().single("""
				SELECT a.id::text FROM audit.events a JOIN persons p ON p.id = a.actor_person_id
				WHERE a.event_type = '%s' AND a.actor_type = 'person' AND p.name = 'dev-architect'
				  AND a.entity_id = (SELECT id FROM entities WHERE external_id = '%s')
				  AND a.details ->> 'batchId' IS NOT NULL""".formatted(eventType, externalId));
		assertThat(event).isNotNull();
		assertThat(shared.oidc().column("SELECT property_name || '=' || new_value FROM audit.event_changes "
				+ "WHERE event_id = " + event))
			.contains(first, second);
	}

	@Then("the audit chain verifies")
	public void chainVerifies() throws Exception {
		try (Connection connection = OeaDatabases.asSuperuser(shared.oidc().postgres())) {
			assertThat(new ChainVerifier().verify(connection, List.of()).problems()).isEmpty();
		}
	}

	@Then("the audit event of {string} has the agent label {string}, the agent run {string} and no agent ID")
	public void agentFields(String externalId, String label, String run) throws Exception {
		assertThat(this.response.statusCode()).isEqualTo(200);
		assertThat(shared.oidc().single("""
				SELECT agent_label || '|' || agent_run || '|' || coalesce(agent_id, 'none') FROM audit.events
				WHERE entity_id = (SELECT id FROM entities WHERE external_id = '%s')""".formatted(externalId)))
			.isEqualTo(label + "|" + run + "|none");
	}

	@Then("the batch is rejected with {int}")
	public void batchRejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("no entity with the prefix {string} exists")
	public void noEntity(String prefix) throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM entities WHERE external_id LIKE '" + prefix + ":%'"))
			.isEqualTo("0");
	}

	@Then("the summary reports {int} created and {int} updated")
	public void createdAndUpdated(int created, int updated) {
		JsonNode summary = body().path("summary");
		assertThat(summary.path("created").asInt()).isEqualTo(created);
		assertThat(summary.path("updated").asInt()).isEqualTo(updated);
	}

	@Then("{int} entities with the prefix {string} exist, none named {string}")
	public void entitiesExist(int count, String prefix, String name) throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM entities WHERE external_id LIKE '" + prefix + ":%'"))
			.isEqualTo(String.valueOf(count));
		assertThat(shared.oidc().single("SELECT count(*)::text FROM entities WHERE external_id LIKE '" + prefix
				+ ":%' AND name = '" + name + "'"))
			.isEqualTo("0");
	}

	@Then("the summary reports {int} unchanged and {int} created")
	public void unchangedAndCreated(int unchanged, int created) {
		JsonNode summary = body().path("summary");
		assertThat(summary.path("unchanged").asInt()).isEqualTo(unchanged);
		assertThat(summary.path("created").asInt()).isEqualTo(created);
	}

	@Then("{string} is named {string} and keeps the {string} {string}")
	public void namedAndKeeps(String externalId, String name, String property, String value) throws Exception {
		assertThat(shared.oidc().single("""
				SELECT e.name || '|' || v.value_enum FROM entities e JOIN entity_property_values v ON v.entity_id = e.id
				JOIN property_definitions d ON d.id = v.property_def_id
				WHERE e.external_id = '%s' AND d.name = '%s'""".formatted(externalId, property)))
			.isEqualTo(name + "|" + value);
	}

	@Then("{string} is named {string}")
	public void named(String externalId, String name) throws Exception {
		assertThat(shared.oidc().single("SELECT name FROM entities WHERE external_id = '" + externalId + "'"))
			.isEqualTo(name);
	}

	@Then("the first item is created and the second is rejected with the code {string}")
	public void firstCreatedSecondRejected(String code) {
		assertThat(item(0).path("status").asString()).isEqualTo("created");
		assertThat(item(1).path("status").asString()).isEqualTo("error");
		assertThat(item(1).path("errors").get(0).path("code").asString()).isEqualTo(code);
	}

	@Then("{string} is reported as {string} and {string} as {string}")
	public void reportedAsBoth(String first, String firstStatus, String second, String secondStatus) {
		assertThat(item(0).path("externalId").asString()).isEqualTo(first);
		assertThat(item(0).path("status").asString()).isEqualTo(firstStatus);
		assertThat(item(1).path("externalId").asString()).isEqualTo(second);
		assertThat(item(1).path("status").asString()).isEqualTo(secondStatus);
	}

	@Then("the batch is answered with {int}, {int} created and {int} errors with the code {string}")
	public void answered(int status, int created, int errors, String code) {
		assertThat(this.response.statusCode()).isEqualTo(status);
		JsonNode summary = body().path("summary");
		assertThat(summary.path("created").asInt()).isEqualTo(created);
		assertThat(summary.path("errors").asInt()).isEqualTo(errors);
		assertThat(body().path("items").valueStream().filter((i) -> "error".equals(i.path("status").asString())))
			.hasSize(errors)
			.allSatisfy((i) -> assertThat(i.path("errors").get(0).path("code").asString()).isEqualTo(code));
	}

	@Then("the batch is rejected with {int} listing {int} errors with their item index")
	public void rejectedListing(int status, int errors) {
		assertThat(this.response.statusCode()).isEqualTo(status);
		assertThat(body().path("errors")).hasSize(errors)
			.allSatisfy((error) -> assertThat(error.path("field").asString()).matches("entities\\[\\d+]\\..+"));
	}

	private void send(ObjectNode request) throws Exception {
		this.response = shared.post(BATCH, request.toString(), shared.architect(), Map.of());
	}

	private void assertCreated() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		assertThat(item(0).path("status").asString()).as(this.response.body()).isEqualTo("created");
	}

	private ObjectNode many(int count, String metatype, String prefix) throws Exception {
		ObjectNode request = batch();
		for (int i = 0; i < count; i++) {
			request.withArray("entities").add(entity(metatype, metatype + " " + i, prefix + ":" + i));
		}
		return request;
	}

	/** Entities of the metatype; every n-th lacks the property so that {@code faulty} items fail. */
	private ObjectNode checked(int count, String metatype, String prefix, int faulty, String property)
			throws Exception {
		ObjectNode request = batch();
		int step = count / faulty;
		for (int i = 0; i < count; i++) {
			request.withArray("entities")
				.add((i % step == 0) ? entity(metatype, metatype + " " + i, prefix + ":" + i)
						: entity(metatype, metatype + " " + i, prefix + ":" + i, property, "1.0"));
		}
		return request;
	}

	private static ObjectNode batch(ObjectNode... entities) {
		ObjectNode request = JsonMapper.shared().createObjectNode();
		ArrayNode items = request.putArray("entities");
		for (ObjectNode entity : entities) {
			items.add(entity);
		}
		return request;
	}

	/** An entity with property name/value pairs. */
	private static ObjectNode entity(String metatype, String name, String externalId, String... values)
			throws Exception {
		ObjectNode entity = JsonMapper.shared().createObjectNode();
		entity.put("name", name).put("metatypeId", id("metatypes", metatype));
		if (externalId != null) {
			entity.put("externalId", externalId);
		}
		ArrayNode properties = entity.putArray("properties");
		for (int i = 0; i < values.length; i += 2) {
			properties.addObject()
				.put("propertyDefId", id("property_definitions", values[i]))
				.put("value", values[i + 1]);
		}
		return entity;
	}

	private static String id(String table, String name) throws Exception {
		String key = table + "/" + name;
		String id = ids.get(key);
		if (id == null) {
			id = shared.oidc().single("SELECT id::text FROM " + table + " WHERE name = '" + name + "'");
			ids.put(key, id);
		}
		return id;
	}

	private JsonNode item(int index) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		return body().path("items").get(index);
	}

	private JsonNode body() {
		return JsonMapper.shared().readTree(this.response.body());
	}

}

package io.oea.steps.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/catalog/catalog-joins.feature and the join scenarios of
 * features/catalog/catalog-query.feature (us-048-catalog-joins), on the instance of the catalog
 * features. The metatype "Interface" maps the property "Protocol"; an interface's protocol is the
 * first word of its name ("REST API" → "REST"). Joins show the name and, for interfaces, the
 * protocol.
 */
public class CatalogJoinSteps {

	private static final String CATALOGS = "/api/v1/catalogs";

	private static final String BATCH = "/api/v1/entities/batch";

	private HttpResponse<String> response;

	// metamodel and repository

	@Given("the metamodel has the connection metatype {string} from {string} to {string}")
	public void connectionMetatype(String connection, String source, String target) throws Exception {
		metatype(target, false);
		if ("Interface".equals(target)) {
			String protocol = idOrNull("SELECT id::text FROM property_definitions WHERE name = 'Protocol'");
			if (protocol == null) {
				protocol = instance().oidc().single("""
						INSERT INTO property_definitions (name, sql_column_name, data_type)
						VALUES ('Protocol', 'protocol', 'string') RETURNING id::text""");
				instance().oidc().update("""
						INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
						VALUES ('%s', '%s', 'optional', DATE '2026-01-01', 'organization')"""
					.formatted(metatypeId(target), protocol));
			}
		}
		if (metatype(connection, true)) {
			instance().oidc().update("""
					INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
					VALUES ('%1$s', 'source', '%2$s'), ('%1$s', 'target', '%3$s')"""
				.formatted(metatypeId(connection), metatypeId(source), metatypeId(target)));
		}
	}

	@Given("the application {string} reaches the interfaces {string} and {string} via {string}")
	public void reachesInterfaces(String application, String first, String second, String connection)
			throws Exception {
		connect(application, "Interface", List.of(first, second), connection);
	}

	@Given("the application {string} reaches the interface {string} via {string}")
	public void reachesInterface(String application, String target, String connection) throws Exception {
		connect(application, "Interface", List.of(target), connection);
	}

	@Given("the application {string} runs on the technologies {string} and {string} via {string}")
	public void runsOn(String application, String first, String second, String connection) throws Exception {
		connect(application, "Technology Component", List.of(first, second), connection);
	}

	// configuring

	@When("the {role} adds the join {string} via {string} {word} to {string} to {string}")
	public void addJoin(Session session, String name, String connection, String direction, String target,
			String catalog) throws Exception {
		add(session, catalog, join(name, connection, direction, target));
	}

	@When("the enterprise architect adds the join {string} via {string} {word} to {string} in mode {string} to {string}")
	public void addJoinInMode(String name, String connection, String direction, String target, String mode,
			String catalog) throws Exception {
		add(instance().architect(), catalog, join(name, connection, direction, target).put("defaultJoinMode", mode));
		ok(this.response, 201);
	}

	@When("the enterprise architect adds a join to {string} with a property that {string} does not map to {string}")
	public void unmappedColumn(String target, String metatype, String catalog) throws Exception {
		String property = instance().oidc().single("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES ('%s', '%s', 'string')
				RETURNING id::text""".formatted("Unmapped " + UUID.randomUUID().toString().substring(0, 8),
				"p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
		ObjectNode join = join("Unknown", "Data Flow", "outbound", target);
		ArrayNode columns = join.putArray("targetColumns");
		columns.addObject().put("field", "name");
		columns.addObject().put("propertyDefId", property);
		add(instance().architect(), catalog, join);
	}

	@When("the enterprise architect removes the join {string} from {string}")
	public void remove(String name, String catalog) throws Exception {
		this.response = instance().send("DELETE",
				CATALOGS + "/" + catalogId(catalog) + "/joins/" + joinId(catalog, name), null, instance().architect());
		ok(this.response, 200);
	}

	// querying

	@When("the {role} queries the catalog {string} with the join {string} in mode {string}")
	public void queryInMode(Session session, String catalog, String join, String mode) throws Exception {
		query(session, catalog, mode(joinId(catalog, join), mode));
	}

	@When("the {role} queries the catalog {string} with an unknown join in mode {string}")
	public void queryUnknownJoin(Session session, String catalog, String mode) throws Exception {
		query(session, catalog, mode(UUID.randomUUID().toString(), mode));
	}

	@When("the {role} queries the catalog {string} with the join {string} in mode {string} sorted by the protocol")
	public void querySortedByProtocol(Session session, String catalog, String join, String mode) throws Exception {
		String id = joinId(catalog, join);
		query(session, catalog, mode(id, mode) + "&sort=" + id + ".protocol:asc");
	}

	@When("the {role} queries the catalog {string} sorted by the protocol of the join {string}")
	public void querySortedAggregated(Session session, String catalog, String join) throws Exception {
		query(session, catalog, "sort=" + joinId(catalog, join) + ".protocol:asc");
	}

	// outcomes

	@Then("the catalog {string} contains the join {string} with a new ID")
	public void containsJoin(String catalog, String name) throws Exception {
		ok(this.response, 201);
		JsonNode join = catalog(catalog).path("joinDefinitions").get(0);
		assertThat(join.path("name").asString()).isEqualTo(name);
		assertThat(UUID.fromString(join.path("id").asString())).isNotNull();
		assertThat(join.path("available").asBoolean()).isTrue();
	}

	@Then("the viewer sees {string} in one row of {string} with the interfaces {string} and {string}")
	public void seesAggregated(String application, String catalog, String first, String second) throws Exception {
		query(instance().viewer(), catalog, "");
		List<JsonNode> rows = rowsOf(application);
		assertThat(rows).hasSize(1);
		assertThat(targetNames(rows.get(0), joinId(catalog, "Interfaces"))).containsExactly(first, second);
	}

	@Then("the viewer sees {string} in one row of {string} without interfaces")
	public void seesWithout(String application, String catalog) throws Exception {
		query(instance().viewer(), catalog, "");
		List<JsonNode> rows = rowsOf(application);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).path("joinResults").path(joinId(catalog, "Interfaces")).isArray()).isTrue();
		assertThat(rows.get(0).path("joinResults").path(joinId(catalog, "Interfaces"))).isEmpty();
	}

	@Then("the viewer sees {int} rows for {string} in the catalog {string}")
	public void seesRows(int count, String application, String catalog) throws Exception {
		query(instance().viewer(), catalog, "");
		assertThat(rowsOf(application)).hasSize(count);
	}

	@Then("the catalog shows {int} rows for {string}")
	public void showsRows(int count, String application) throws Exception {
		assertThat(rowsOf(application)).hasSize(count);
	}

	@Then("every row of {string} lists {int} results of the join {string}")
	public void everyRowLists(String application, int count, String join) throws Exception {
		String catalog = instance().oidc().single("""
				SELECT c.name FROM catalogs c, jsonb_array_elements(c.config -> 'joinDefinitions') j
				WHERE j ->> 'name' = '%s' ORDER BY c.created_at DESC LIMIT 1""".formatted(join));
		for (JsonNode row : rowsOf(application)) {
			assertThat(row.path("joinResults").path(joinId(catalog, join))).hasSize(count);
		}
	}

	@Then("the catalog shows a total of {int} rows")
	public void totalRows(int total) throws Exception {
		assertThat(result().path("page").path("totalElements").asInt()).isEqualTo(total);
	}

	@Then("the interface protocols are {string}, {string} and none in this order")
	public void protocols(String first, String second) throws Exception {
		List<String> protocols = new ArrayList<>();
		JsonNode result = result();
		String join = result.path("joins").get(0).path("id").asString();
		result.path("rows").forEach((row) -> {
			JsonNode results = row.path("joinResults").path(join);
			protocols.add(results.isEmpty() ? null : results.get(0).path("values").path("protocol").asString(null));
		});
		// ERP and Intranet have no interface and come last
		assertThat(protocols).containsExactly(first, second, null, null);
	}

	@Then("the catalog {string} keeps the default join mode {string}")
	public void keepsDefault(String catalog, String mode) throws Exception {
		assertThat(catalog(catalog).path("defaultJoinMode").asString()).isEqualTo(mode);
	}

	@Then("the catalog {string} keeps the join {string} without default mode")
	public void keepsJoinDefault(String catalog, String join) throws Exception {
		JsonNode definition = catalog(catalog).path("joinDefinitions").get(0);
		assertThat(definition.path("name").asString()).isEqualTo(join);
		assertThat(definition.path("defaultJoinMode").isNull()).isTrue();
	}

	@Then("the catalog {string} has no joins")
	public void noJoins(String catalog) throws Exception {
		assertThat(catalog(catalog).path("joinDefinitions")).isEmpty();
	}

	@Then("the viewer sees no join results in the catalog {string}")
	public void noJoinResults(String catalog) throws Exception {
		query(instance().viewer(), catalog, "");
		JsonNode result = result();
		assertThat(result.path("joins")).isEmpty();
		result.path("rows").forEach((row) -> assertThat(row.path("joinResults").isEmpty()).isTrue());
	}

	@Then("the join change is rejected with 422 naming {string}")
	public void rejected422(String field) throws Exception {
		assertNaming(422, field);
	}

	@Then("the join change is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the query is rejected with 400 naming {string}")
	public void queryRejected(String field) throws Exception {
		assertNaming(400, field);
	}

	// helpers

	private void assertNaming(int status, String field) throws Exception {
		JsonNode problem = ok(this.response, status);
		List<String> fields = new ArrayList<>();
		problem.path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	/** Creates the metatype in the instance configuration if missing; returns whether it was created. */
	private static boolean metatype(String name, boolean connection) throws Exception {
		if (idOrNull("SELECT id::text FROM metatypes WHERE name = '" + name.replace("'", "''") + "'") != null) {
			return false;
		}
		instance().oidc().update("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', '%s', %s)""".formatted(name.replace("'", "''"),
				connection));
		return true;
	}

	/** Writes the targets and the connections from the application to them through the batch API. */
	private void connect(String application, String targetMetatype, List<String> targets, String connection)
			throws Exception {
		ObjectNode request = object();
		ArrayNode entities = request.putArray("entities");
		String protocol = "Interface".equals(targetMetatype)
				? idOrNull("SELECT id::text FROM property_definitions WHERE name = 'Protocol'") : null;
		List<String> refs = new ArrayList<>();
		for (String target : targets) {
			String ref = "t" + UUID.randomUUID().toString().substring(0, 8);
			refs.add(ref);
			ObjectNode entity = entities.addObject()
				.put("name", target)
				.put("metatypeId", metatypeId(targetMetatype))
				.put("externalId", ref);
			if (protocol != null) {
				entity.putArray("properties")
					.addObject()
					.put("propertyDefId", protocol)
					.put("value", target.split(" ")[0]);
			}
		}
		// applications of the query steps have no external ID; the batch names endpoints by it
		String source = instance().oidc().single("""
				UPDATE entities SET external_id = coalesce(external_id, 'app-' || id)
				WHERE name = '%s' AND deleted_at IS NULL AND metatype_id = '%s' RETURNING external_id"""
			.formatted(application.replace("'", "''"), metatypeId("Application Component")));
		ArrayNode connections = request.putArray("connections");
		for (String ref : refs) {
			connections.addObject()
				.put("connectionMetatypeId", metatypeId(connection))
				.put("name", application + " to " + ref)
				.put("sourceExternalId", source)
				.put("targetExternalId", ref);
		}
		ok(instance().post(BATCH, request.toString(), instance().architect(), Map.of()), 200);
	}

	private ObjectNode join(String name, String connection, String direction, String target) throws Exception {
		ObjectNode join = object()
			.put("name", name)
			.put("connectionMetatypeId", metatypeId(connection))
			.put("joinDirection", direction)
			.put("targetMetatypeId", metatypeId(target));
		ArrayNode columns = join.putArray("targetColumns");
		columns.addObject().put("field", "name");
		String protocol = idOrNull("SELECT id::text FROM property_definitions WHERE name = 'Protocol'");
		if ("Interface".equals(target) && protocol != null) {
			columns.addObject().put("propertyDefId", protocol);
		}
		return join;
	}

	private void add(Session session, String catalog, ObjectNode join) throws Exception {
		this.response = instance().send("POST", CATALOGS + "/" + catalogId(catalog) + "/joins", join.toString(), session);
	}

	private void query(Session session, String catalog, String parameters) throws Exception {
		this.response = instance().oidc()
			.get(CATALOGS + "/" + catalogId(catalog) + "/query" + (parameters.isEmpty() ? "" : "?" + parameters),
					session.token());
	}

	private JsonNode result() throws Exception {
		return ok(this.response, 200);
	}

	private List<JsonNode> rowsOf(String application) throws Exception {
		List<JsonNode> rows = new ArrayList<>();
		result().path("rows").forEach((row) -> {
			if (application.equals(row.path("values").path("name").asString())) {
				rows.add(row);
			}
		});
		return rows;
	}

	private static List<String> targetNames(JsonNode row, String joinId) {
		List<String> names = new ArrayList<>();
		row.path("joinResults").path(joinId).forEach((result) -> names.add(result.path("values").path("name").asString()));
		return names;
	}

	/** The deep-object parameter, encoded for the HTTP client. */
	private static String mode(String joinId, String mode) {
		return "joinMode%5B" + joinId + "%5D=" + mode;
	}

	private JsonNode catalog(String name) throws Exception {
		return ok(instance().send("GET", CATALOGS + "/" + catalogId(name), null, instance().architect()), 200);
	}

	private static String joinId(String catalog, String join) throws Exception {
		return instance().oidc().single("""
				SELECT j ->> 'id' FROM catalogs c, jsonb_array_elements(c.config -> 'joinDefinitions') j
				WHERE c.name = '%s' AND j ->> 'name' = '%s'""".formatted(catalog.replace("'", "''"),
				join.replace("'", "''")));
	}

	private static String catalogId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM catalogs WHERE name = '" + name.replace("'", "''") + "'");
	}

	private static String metatypeId(String name) throws Exception {
		return instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name.replace("'", "''") + "'");
	}

	private static String idOrNull(String sql) throws Exception {
		List<String> ids = instance().oidc().column(sql);
		return ids.isEmpty() ? null : ids.get(0);
	}

	private static ObjectNode object() {
		return JsonMapper.shared().createObjectNode();
	}

	private static JsonNode ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return JsonMapper.shared().readTree(response.body());
	}

	private static RepositoryInstance instance() {
		return CatalogSteps.shared();
	}

}

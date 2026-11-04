package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.metamodel.TestPackages;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/metamodel/connection-metatypes.feature and
 * features/repository/connection-write.feature (us-036-connection-metatypes). The scenarios use the
 * instance of the metamodel editing features; every scenario creates its own metatypes and entities
 * under unique names and external IDs, and the feature names map to them.
 */
public class ConnectionSteps {

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private static final String METATYPES = "/api/v1/metamodel/metatypes";

	private static final String BATCH = "/api/v1/entities/batch";

	private final String suffix = "-" + UUID.randomUUID().toString().substring(0, 8);

	/** Metatype IDs by the name in the feature. */
	private final Map<String, String> metatypes = new HashMap<>();

	/** External IDs of entities and connections by the name in the feature. */
	private final Map<String, String> entities = new HashMap<>();

	private String protocol;

	private String importedConnection;

	private String importedSource;

	private HttpResponse<String> response;

	// metamodel/connection-metatypes

	@Given("the connection metatype {string} with {string} and {string} as allowed sources and targets")
	public void connectionWithEndpoints(String name, String endpoint, String self) throws Exception {
		String id = createMetatype(name, true, null, List.of(metatype(endpoint)), List.of(metatype(endpoint)));
		assertThat(name).isEqualTo(self);
		// the connection metatype itself is added once it exists
		ObjectNode update = metatypeBody(name, true, null, List.of(metatype(endpoint), id),
				List.of(metatype(endpoint), id));
		ok(send("PUT", METATYPES + "/" + id, update, instance().metamodelAdmin()), 200);
		// an enum property for the protocol of a data flow
		ObjectNode property = object().put("name", "Protocol" + this.suffix).put("dataType", "enum");
		this.protocol = id(ok(send("POST", "/api/v1/metamodel/property-definitions", property,
				instance().metamodelAdmin()), 201));
		for (String value : new String[] { "http", "sftp" }) {
			ok(send("POST", "/api/v1/metamodel/property-definitions/" + this.protocol + "/enum-values",
					object().put("value", value), instance().metamodelAdmin()), 201);
		}
		ok(send("POST", METATYPES + "/" + id + "/property-mappings",
				object().put("propertyDefId", this.protocol).put("necessity", "optional"), instance().metamodelAdmin()),
				201);
	}

	@When("the metamodel administrator creates the connection metatype {string} with {string} and {string} as allowed sources and targets")
	public void createConnectionWithEndpoints(String name, String endpoint, String self) throws Exception {
		connectionWithEndpoints(name, endpoint, self);
	}

	@Then("{string} is a connection metatype with {string} and {string} as allowed sources and targets")
	public void isConnectionWithEndpoints(String name, String endpoint, String self) throws Exception {
		JsonNode metatype = body(send("GET", METATYPES + "/" + metatype(name), null, instance().architect()));
		assertThat(metatype.path("isConnection").asBoolean()).isTrue();
		List<String> expected = List.of(metatype(endpoint), metatype(self));
		for (String direction : new String[] { "source", "target" }) {
			assertThat(metatype.path("connectionConstraints")
				.valueStream()
				.filter((constraint) -> direction.equals(constraint.path("direction").asString()))
				.map((constraint) -> constraint.path("allowedMetatype").path("id").asString())
				.toList()).containsExactlyInAnyOrderElementsOf(expected);
		}
	}

	@Given("the metatype {string}")
	public void elementMetatype(String name) throws Exception {
		createMetatype(name, false, null, List.of(), List.of());
	}

	@Given("the metatype {string} and the connection metatype {string} from {string} to {string}")
	public void relation(String element, String connection, String source, String target) throws Exception {
		elementMetatype(element);
		createMetatype(connection, true, null, List.of(metatype(source)), List.of(metatype(target)));
	}

	@Given("the metatype {string} with the base type {string}")
	public void subtype(String name, String base) throws Exception {
		createMetatype(name, false, metatype(base), List.of(), List.of());
	}

	@When("the architect connects a {string} to a {string} with {string}")
	public void connect(String source, String target, String connection) throws Exception {
		entityOf(source, "Source");
		entityOf(target, "Target");
		ObjectNode write = object().put("connectionMetatypeId", metatype(connection))
			.put("sourceExternalId", this.entities.get("Source"))
			.put("targetExternalId", this.entities.get("Target"));
		this.response = send("POST", "/api/v1/connections", write, instance().architect());
	}

	@Then("the connection is created")
	public void connectionCreated() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@When("the metamodel administrator makes {string} a connection metatype")
	public void makeConnection(String name) throws Exception {
		this.response = send("PUT", METATYPES + "/" + metatype(name),
				metatypeBody(name, true, null, List.of(), List.of()), instance().metamodelAdmin());
	}

	@When("the metamodel administrator creates the connection metatype {string} with the base type {string}")
	public void connectionWithBase(String name, String base) throws Exception {
		this.response = send("POST", METATYPES, metatypeBody(name, true, metatype(base), List.of(), List.of()),
				instance().metamodelAdmin());
	}

	@Then("the connection metatype change is rejected with {int}")
	public void changeRejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@When("the metamodel administrator imports a package with a connection metatype that allows one of its metatypes as source")
	public void importConnection() throws Exception {
		String packageId = "connections" + this.suffix.toLowerCase();
		ObjectNode pkg = TestPackages.pkg(packageId, 2);
		ObjectNode link = (ObjectNode) pkg.withArray("metatypes").get(1);
		this.importedSource = pkg.withArray("metatypes").get(0).path("id").asString();
		this.importedConnection = link.path("id").asString();
		link.put("isConnection", true).remove("propertyMappings");
		link.putArray("connectionConstraints").addObject().put("direction", "source").putObject("allowedMetatype")
			.put("id", this.importedSource).put("name", "source").put("abbreviation", "S");
		ok(send("POST", "/api/v1/admin/metamodel/import", pkg, instance().metamodelAdmin()), 200);
	}

	@Then("the imported metatype is a read-only connection metatype with that source")
	public void importedConnection() throws Exception {
		JsonNode metatype = body(send("GET", METATYPES + "/" + this.importedConnection, null, instance().architect()));
		assertThat(metatype.path("isConnection").asBoolean()).isTrue();
		assertThat(metatype.path("scope").asString()).isEqualTo("imported");
		assertThat(metatype.path("connectionConstraints").get(0).path("allowedMetatype").path("id").asString())
			.isEqualTo(this.importedSource);
		HttpResponse<String> change = send("PUT", METATYPES + "/" + this.importedConnection,
				metatypeBody("Changed" + this.suffix, true, null, List.of(), List.of()), instance().metamodelAdmin());
		assertThat(change.statusCode()).isEqualTo(409);
	}

	@When("a client asks for the allowed connections from {string} to {string}")
	public void askAllowed(String source, String target) throws Exception {
		this.response = send("GET", "/api/v1/metamodel/allowed-connections?sourceMetatypeId=" + metatype(source)
				+ "&targetMetatypeId=" + metatype(target), null, instance().architect());
	}

	@Then("the answer allows {string} with the direction {string}")
	public void answerAllows(String connection, String direction) {
		JsonNode answer = ok(this.response, 200);
		assertThat(answer.path("allowed").asBoolean()).isTrue();
		assertThat(answer.path("options")
			.valueStream()
			.filter((option) -> metatypeOrNull(connection).equals(option.path("connectionMetatype").path("id").asString()))
			.map((option) -> option.path("direction").asString())
			.toList()).containsExactly(direction);
	}

	// repository/connection-write

	@Given("the applications {string}")
	public void application(String name) throws Exception {
		applications(name);
	}

	@Given("the applications {string} and {string}")
	public void twoApplications(String first, String second) throws Exception {
		applications(first, second);
	}

	@Given("the applications {string}, {string} and {string} and the data flow {string} from {string} to {string}")
	public void applicationsWithFlow(String a, String b, String c, String flow, String source, String target)
			throws Exception {
		applications(a, b, c);
		ObjectNode request = batch();
		dataFlow(request, "DataFlow", flow, source, target);
		ok(send("POST", BATCH, request, instance().architect()), 200);
	}

	@Given("the connection metatype {string} that allows only {string} as source")
	public void sourceRestricted(String name, String source) throws Exception {
		createMetatype(name, true, null, List.of(metatype(source)), List.of());
	}

	@When("the architect writes a {string} to {string} without a source")
	public void withoutSource(String connection, String target) throws Exception {
		ObjectNode request = batch();
		request.withArray("connections").addObject().put("connectionMetatypeId", metatype(connection))
			.put("targetExternalId", this.entities.get(target));
		this.response = send("POST", BATCH, request, instance().architect());
	}

	@When("the architect writes an {string} from {string} to {string}")
	public void writeLink(String connection, String source, String target) throws Exception {
		ObjectNode request = batch();
		dataFlow(request, connection, "Link", source, target);
		this.response = send("POST", BATCH, request, instance().architect());
	}

	@When("the architect writes a {string} from {string} to an unknown target")
	public void unknownTarget(String connection, String source) throws Exception {
		ObjectNode request = batch();
		request.withArray("connections").addObject().put("connectionMetatypeId", metatype(connection))
			.put("sourceExternalId", this.entities.get(source))
			.put("targetExternalId", "unknown" + this.suffix);
		this.response = send("POST", BATCH, request, instance().architect());
	}

	@When("the architect writes the data flow {string} from {string} to {string}")
	public void writeFlow(String flow, String source, String target) throws Exception {
		ObjectNode request = batch();
		dataFlow(request, "DataFlow", flow, source, target);
		this.response = send("POST", BATCH, request, instance().architect());
		ok(this.response, 200);
	}

	@Then("{string} is stored with the source {string}")
	public void storedWithSource(String flow, String source) throws Exception {
		assertThat(endpoint(flow, "source")).isEqualTo(this.entities.get(source));
	}

	@When("the architect sends a batch with the applications {string} and {string} and a data flow between them")
	public void mixedBatch(String source, String target) throws Exception {
		ObjectNode request = batch();
		for (String name : new String[] { source, target }) {
			this.entities.put(name, name + this.suffix);
			request.withArray("entities").addObject().put("name", name).put("metatypeId", metatype("Application Component"))
				.put("externalId", name + this.suffix);
		}
		dataFlow(request, "DataFlow", "Flow", source, target);
		this.response = send("POST", BATCH, request, instance().architect());
	}

	@Then("the batch creates {int} items and the connection has the source {string}")
	public void mixedCreated(int count, String source) throws Exception {
		JsonNode items = ok(this.response, 200).path("items");
		assertThat(items).hasSize(count);
		items.forEach((item) -> assertThat(item.path("status").asString()).isEqualTo("created"));
		assertThat(items.get(count - 1).path("itemType").asString()).isEqualTo("connection");
		assertThat(endpoint("Flow", "source")).isEqualTo(this.entities.get(source));
	}

	@When("the data flow {string} is sent again with the protocol {string}")
	public void sendAgainWithProtocol(String flow, String protocol) throws Exception {
		String[] ends = endpoints(flow);
		ObjectNode request = batch();
		request.withArray("connections").addObject().put("connectionMetatypeId", metatype("DataFlow"))
			.put("externalId", this.entities.get(flow)).put("name", flow)
			.put("sourceExternalId", ends[0]).put("targetExternalId", ends[1])
			.putArray("properties").addObject().put("propertyDefId", this.protocol).put("value", protocol);
		this.response = send("POST", BATCH, request, instance().architect());
		ok(this.response, 200);
	}

	@Then("{string} has version {int}")
	public void hasVersion(String flow, int version) throws Exception {
		assertThat(instance().oidc().single("SELECT version::text FROM entities WHERE external_id = '"
				+ this.entities.get(flow) + "'")).isEqualTo(Integer.toString(version));
	}

	@When("the architect creates a {string} from {string} to {string} without a name")
	public void createWithoutName(String connection, String source, String target) throws Exception {
		ObjectNode write = object().put("connectionMetatypeId", metatype(connection))
			.put("sourceExternalId", this.entities.get(source))
			.put("targetExternalId", this.entities.get(target));
		this.response = send("POST", "/api/v1/connections", write, instance().architect());
	}

	@Then("the connection is named {string}")
	public void named(String name) {
		assertThat(ok(this.response, 201).path("name").asString()).isEqualTo(name);
	}

	@When("the data flow {string} is sent again with the target {string}")
	public void sendAgainWithTarget(String flow, String target) throws Exception {
		String[] ends = endpoints(flow);
		ObjectNode request = batch();
		request.withArray("connections").addObject().put("connectionMetatypeId", metatype("DataFlow"))
			.put("externalId", this.entities.get(flow)).put("name", flow)
			.put("sourceExternalId", ends[0]).put("targetExternalId", this.entities.get(target));
		this.response = send("POST", BATCH, request, instance().architect());
	}

	@Then("the connection is rejected with the code {string}")
	public void rejected(String code) {
		JsonNode item = ok(this.response, 200).path("items").get(0);
		assertThat(item.path("status").asString()).as(item.toString()).isEqualTo("error");
		assertThat(item.path("errors").get(0).path("code").asString()).isEqualTo(code);
	}

	@Then("{string} still has the target {string}")
	public void stillTarget(String flow, String target) throws Exception {
		assertThat(endpoint(flow, "target")).isEqualTo(this.entities.get(target));
	}

	// helpers

	private String createMetatype(String name, boolean connection, String base, List<String> sources,
			List<String> targets) throws Exception {
		JsonNode created = ok(send("POST", METATYPES, metatypeBody(name, connection, base, sources, targets),
				instance().metamodelAdmin()), 201);
		this.metatypes.put(name, created.path("id").asString());
		return created.path("id").asString();
	}

	private ObjectNode metatypeBody(String name, boolean connection, String base, List<String> sources,
			List<String> targets) {
		ObjectNode body = object().put("name", name + this.suffix).put("configId", CONFIG)
			.put("isConnection", connection);
		if (base != null) {
			body.put("baseMetatypeId", base);
		}
		ArrayNode allowedSources = body.putArray("allowedSourceMetatypeIds");
		sources.forEach(allowedSources::add);
		ArrayNode allowedTargets = body.putArray("allowedTargetMetatypeIds");
		targets.forEach(allowedTargets::add);
		return body;
	}

	/** A metatype of this scenario, else a metatype of the instance by name. */
	private String metatype(String name) throws Exception {
		String id = metatypeOrNull(name);
		if (id == null) {
			id = instance().oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name + "'");
			this.metatypes.put(name, id);
		}
		return id;
	}

	private String metatypeOrNull(String name) {
		return this.metatypes.get(name);
	}

	/** Entities of the named metatypes, or applications, with the names as given. */
	private void applications(String... names) throws Exception {
		ObjectNode request = batch();
		for (String name : names) {
			this.entities.put(name, name + this.suffix);
			request.withArray("entities").addObject().put("name", name)
				.put("metatypeId", metatype("Application Component")).put("externalId", name + this.suffix);
		}
		ok(send("POST", BATCH, request, instance().architect()), 200);
	}

	/** An entity of the metatype under the given name. */
	private void entityOf(String type, String name) throws Exception {
		ObjectNode request = batch();
		String externalId = name + this.suffix;
		this.entities.put(name, externalId);
		request.withArray("entities").addObject().put("name", name).put("metatypeId", metatype(type))
			.put("externalId", externalId);
		JsonNode items = ok(send("POST", BATCH, request, instance().architect()), 200).path("items");
		assertThat(items.get(0).path("status").asString()).as(items.toString()).isEqualTo("created");
	}

	private void dataFlow(ObjectNode request, String connection, String name, String source, String target)
			throws Exception {
		this.entities.put(name, name + this.suffix);
		request.withArray("connections").addObject().put("connectionMetatypeId", metatype(connection))
			.put("name", name).put("externalId", name + this.suffix)
			.put("sourceExternalId", this.entities.get(source))
			.put("targetExternalId", this.entities.get(target));
	}

	/** External ID of the source or target of a connection. */
	private String endpoint(String connection, String role) throws Exception {
		return instance().oidc().single("SELECT e.external_id FROM entity_connections c JOIN entities e ON e.id = c."
				+ role + "_entity_id JOIN entities x ON x.id = c.entity_id WHERE x.external_id = '"
				+ this.entities.get(connection) + "'");
	}

	private String[] endpoints(String connection) throws Exception {
		return new String[] { endpoint(connection, "source"), endpoint(connection, "target") };
	}

	private HttpResponse<String> send(String method, String path, ObjectNode body, RepositoryInstance.Session session)
			throws Exception {
		return instance().send(method, path, (body != null) ? body.toString() : null, session);
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

	private static ObjectNode batch() {
		ObjectNode request = JsonMapper.shared().createObjectNode();
		request.putArray("entities");
		return request;
	}

	private static ObjectNode object() {
		return JsonMapper.shared().createObjectNode();
	}

	private static JsonNode ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return body(response);
	}

	private static String id(JsonNode node) {
		return node.path("id").asString();
	}

	private static JsonNode body(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

}

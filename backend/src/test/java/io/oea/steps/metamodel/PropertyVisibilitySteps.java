package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/metamodel/property-visibility.feature and the scoping scenario of
 * features/repository/connection-write.feature (us-124-property-visibility). They share the
 * instance of the metamodel editing features; `dev-security-admin` holds `security-admin`
 * through the group `oea-security-admins`.
 */
public class PropertyVisibilitySteps {

	private static final String VISIBILITY = "/api/v1/admin/property-visibility";

	private static Session securityAdmin;

	private HttpResponse<String> response;

	private final Map<String, String> ids = new HashMap<>();

	@Given("the property {string}")
	public void property(String name) throws Exception {
		HttpResponse<String> created = instance().send("POST", "/api/v1/metamodel/property-definitions",
				JsonMapper.shared().createObjectNode().put("name", name).put("dataType", "string").toString(),
				instance().metamodelAdmin());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.ids.put(name, JsonMapper.shared().readTree(created.body()).path("id").asString());
	}

	@When("the security administrator restricts {string} to the read roles {string} and {string} and the write role {string}")
	public void restrict(String property, String first, String second, String write) throws Exception {
		this.response = change(securityAdmin(), property, List.of(first, second), List.of(write));
	}

	@When("the security administrator restricts {string} without a read role")
	public void restrictWithoutRole(String property) throws Exception {
		this.response = change(securityAdmin(), property, List.of(), List.of());
	}

	@When("the metamodel administrator restricts {string} to the read role {string}")
	public void restrictAsMetamodelAdmin(String property, String role) throws Exception {
		this.response = change(instance().metamodelAdmin(), property, List.of(role), List.of());
	}

	@Then("the visibility of {string} is {string} with the read roles {string}")
	public void visibility(String property, String mode, String roles) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		JsonNode entry = entry(property);
		assertThat(entry.path("visibilityMode").asString()).isEqualTo(mode);
		List<String> read = new ArrayList<>();
		entry.path("readRoles").forEach((role) -> read.add(role.asString()));
		assertThat(String.join(", ", read)).isEqualTo(roles);
	}

	@Then("the visibility of {string} is {string}")
	public void visibility(String property, String mode) throws Exception {
		assertThat(entry(property).path("visibilityMode").asString()).isEqualTo(mode);
	}

	@Then("the audit trail has a {string} event for {string}")
	public void audited(String eventType, String property) throws Exception {
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM audit.events WHERE event_type = '%s' AND target_id = '%s'"
				.formatted(eventType, this.ids.get(property)))).isEqualTo("1");
	}

	@Then("the visibility change is rejected with 422 naming {string}")
	public void rejected422(String field) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(422);
		List<String> fields = new ArrayList<>();
		JsonMapper.shared().readTree(this.response.body()).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	@Then("the visibility change is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	// repository/connection-write: "Connections of a scoping type"

	@Given("the connection metatype {string} is the scoping type of the property {string}")
	public void scopingType(String metatype, String property) throws Exception {
		HttpResponse<String> created = instance().send("POST", "/api/v1/metamodel/metatypes",
				JsonMapper.shared()
					.createObjectNode()
					.put("name", metatype)
					.put("configId", "00000000-0000-0000-0000-000000000001")
					.put("isConnection", true)
					.toString(),
				instance().metamodelAdmin());
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.ids.put(metatype, JsonMapper.shared().readTree(created.body()).path("id").asString());
		property(property);
		ArrayNode entries = JsonMapper.shared().createArrayNode();
		ObjectNode entry = entries.addObject()
			.put("propertyDefId", this.ids.get(property))
			.put("visibilityMode", "connection-scoped")
			.put("scopingConnectionType", this.ids.get(metatype));
		entry.putArray("readRoles");
		entry.putArray("writeRoles");
		HttpResponse<String> changed = instance().send("PUT", VISIBILITY, entries.toString(), securityAdmin());
		assertThat(changed.statusCode()).as(changed.body()).isEqualTo(200);
	}

	@Given("the elements {string} and {string}")
	public void elements(String first, String second) throws Exception {
		for (String name : List.of(first, second)) {
			this.ids.put(name, instance().oidc().single("""
					INSERT INTO entities (metatype_id, name, created_by)
					SELECT (SELECT id FROM metatypes WHERE name = 'Application Component'), '%s', (SELECT min(id) FROM persons)
					RETURNING id""".formatted(name.replace("'", "''"))));
		}
	}

	@When("the architect creates a {string} connection from {string} to {string}")
	public void createConnection(String metatype, String source, String target) throws Exception {
		this.response = instance().send("POST", "/api/v1/connections",
				JsonMapper.shared()
					.createObjectNode()
					.put("connectionMetatypeId", this.ids.get(metatype))
					.put("sourceId", Integer.parseInt(this.ids.get(source)))
					.put("targetId", Integer.parseInt(this.ids.get(target)))
					.toString(),
				instance().architect());
	}

	@Then("the connection is rejected with 403 and no {string} connection exists")
	public void connectionRejected(String metatype) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(403);
		assertThat(instance().oidc()
			.single("SELECT count(*) FROM entities WHERE metatype_id = '%s'".formatted(this.ids.get(metatype))))
			.isEqualTo("0");
	}

	private HttpResponse<String> change(Session session, String property, List<String> read, List<String> write)
			throws Exception {
		ArrayNode entries = JsonMapper.shared().createArrayNode();
		ObjectNode entry = entries.addObject()
			.put("propertyDefId", this.ids.get(property))
			.put("visibilityMode", "role-restricted");
		ArrayNode readRoles = entry.putArray("readRoles");
		read.forEach(readRoles::add);
		ArrayNode writeRoles = entry.putArray("writeRoles");
		write.forEach(writeRoles::add);
		return instance().send("PUT", VISIBILITY, entries.toString(), session);
	}

	private JsonNode entry(String property) throws Exception {
		HttpResponse<String> listed = instance().oidc().get(VISIBILITY, securityAdmin().token());
		assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
		for (JsonNode entry : JsonMapper.shared().readTree(listed.body())) {
			if (entry.path("propertyDefId").asString().equals(this.ids.get(property))) {
				return entry;
			}
		}
		throw new AssertionError("no visibility entry for " + property);
	}

	private static Session securityAdmin() throws Exception {
		synchronized (PropertyVisibilitySteps.class) {
			if (securityAdmin == null) {
				instance().oidc().mapping("oea-security-admins", "security-admin");
				securityAdmin = instance().login("dev-security-admin");
			}
			return securityAdmin;
		}
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

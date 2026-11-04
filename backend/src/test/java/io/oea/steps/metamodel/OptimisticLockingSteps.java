package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/metamodel/optimistic-locking.feature (scr-030-metamodel-configuration). All
 * scenarios use the instance of the metamodel editing features; each creates its own elements. The two administrators of a
 * collision are two requests based on the same read version; who sends them does not matter for
 * the version check.
 */
public class OptimisticLockingSteps {

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private RepositoryInstance shared;

	private String element;

	private HttpResponse<String> response;

	private HttpResponse<String> first;

	private String firstName;

	@Given("a running repository instance for optimistic locking")
	public void instance() throws Exception {
		// the instance of the metamodel editing features, closed by MetatypeEditingSteps
		this.shared = MetatypeEditingSteps.sharedInstance();
	}

	@Given("an organization metatype in version {int}")
	public void metatype(int version) throws Exception {
		this.element = "/api/v1/metamodel/metatypes/" + create("/api/v1/metamodel/metatypes", metatypeBody(), version);
	}

	@Given("an organization enum property in version {int}")
	public void property(int version) throws Exception {
		this.element = "/api/v1/metamodel/property-definitions/" + create("/api/v1/metamodel/property-definitions",
				object().put("name", unique("Trust")).put("dataType", "enum"), version);
	}

	@Given("an organization property category in version {int}")
	public void category(int version) throws Exception {
		this.element = "/api/v1/metamodel/property-categories/"
				+ create("/api/v1/metamodel/property-categories", object().put("name", unique("Category")), version);
	}

	@Given("two administrators have read the metatype")
	public void bothRead() throws Exception {
		assertThat(send("GET", this.element, null, null).headers().firstValue("ETag")).hasValue("\"1\"");
	}

	@Given("the administrator has added an enum value")
	public void enumValueAdded() throws Exception {
		assertThat(send("POST", this.element + "/enum-values", object().put("value", "high"), null).statusCode())
			.isEqualTo(201);
	}

	@When("the administrator changes its abbreviation with If-Match {string}")
	public void changeAbbreviation(String version) throws Exception {
		this.response = send("PUT", this.element, metatypeBody().put("abbreviation", "ZN"), quoted(version));
	}

	@When("the administrator maps a property to the metatype with If-Match {string}")
	public void map(String version) throws Exception {
		String property = body(send("POST", "/api/v1/metamodel/property-definitions",
				object().put("name", unique("Owner")).put("dataType", "string"), null)).path("id").asString();
		this.response = send("POST", this.element + "/property-mappings",
				object().put("propertyDefId", property).put("necessity", "optional"), quoted(version));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@When("the administrator adds an enum value with If-Match {string}")
	public void addValue(String version) throws Exception {
		this.response = send("POST", this.element + "/enum-values", object().put("value", "low"), quoted(version));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@When("the first saves a new name with If-Match {string}")
	public void firstSaves(String version) throws Exception {
		this.firstName = unique("First");
		this.first = send("PUT", this.element, object().put("name", this.firstName).put("configId", CONFIG),
				quoted(version));
	}

	@When("the second saves another name with If-Match {string}")
	public void secondSaves(String version) throws Exception {
		this.response = send("PUT", this.element, object().put("name", unique("Second")).put("configId", CONFIG),
				quoted(version));
	}

	@When("the administrator deletes the property definition with If-Match {string}")
	public void deleteProperty(String version) throws Exception {
		this.response = send("DELETE", this.element, null, quoted(version));
	}

	@When("an API client renames the category without If-Match")
	public void renameCategory() throws Exception {
		this.response = send("PUT", this.element, object().put("name", unique("Category")), null);
	}

	@Then("the response contains version {int} and the ETag {string}")
	public void responseVersion(int version, String etag) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		assertThat(body(this.response).path("version").asInt()).isEqualTo(version);
		assertThat(this.response.headers().firstValue("ETag")).hasValue(quoted(etag));
	}

	@Then("the metatype is read in version {int} with the ETag {string}")
	public void readVersion(int version, String etag) throws Exception {
		HttpResponse<String> read = send("GET", this.element, null, null);
		assertThat(body(read).path("version").asInt()).isEqualTo(version);
		assertThat(read.headers().firstValue("ETag")).hasValue(quoted(etag));
	}

	@Then("the property definition has version {int}")
	public void propertyVersion(int version) throws Exception {
		String id = this.element.substring(this.element.lastIndexOf('/') + 1);
		JsonNode definitions = body(send("GET", "/api/v1/metamodel/property-definitions", null, null));
		for (JsonNode definition : definitions) {
			if (id.equals(definition.path("id").asString())) {
				assertThat(definition.path("version").asInt()).isEqualTo(version);
				return;
			}
		}
		throw new AssertionError("no property definition " + id);
	}

	@Then("the first write succeeded with version {int}")
	public void firstSucceeded(int version) {
		assertThat(this.first.statusCode()).as(this.first.body()).isEqualTo(200);
		assertThat(body(this.first).path("version").asInt()).isEqualTo(version);
	}

	@Then("the second write is rejected with {int} {string}")
	public void secondRejected(int status, String type) {
		rejected(status, type);
	}

	@Then("the write is rejected with {int} {string}")
	public void rejected(int status, String type) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
		assertThat(body(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the metatype keeps the first name")
	public void keepsFirstName() throws Exception {
		assertThat(body(send("GET", this.element, null, null)).path("name").asString()).isEqualTo(this.firstName);
	}

	@Then("the property definition still exists")
	public void stillExists() throws Exception {
		propertyVersion(2);
	}

	@Then("the change succeeds with version {int}")
	public void succeeds(int version) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		assertThat(body(this.response).path("version").asInt()).isEqualTo(version);
	}

	private String create(String path, ObjectNode body, int version) throws Exception {
		HttpResponse<String> created = send("POST", path, body, null);
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		assertThat(body(created).path("version").asInt()).isEqualTo(version);
		return body(created).path("id").asString();
	}

	private HttpResponse<String> send(String method, String path, ObjectNode body, String ifMatch) throws Exception {
		return this.shared.send(method, path, (body != null) ? body.toString() : null, this.shared.metamodelAdmin(),
				(ifMatch != null) ? Map.of("If-Match", ifMatch) : Map.of());
	}

	private static ObjectNode metatypeBody() {
		return object().put("name", unique("Zone")).put("configId", CONFIG);
	}

	private static ObjectNode object() {
		return JsonMapper.shared().createObjectNode();
	}

	private static String quoted(String version) {
		return "\"" + version + "\"";
	}

	private static String unique(String name) {
		return "locking " + name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static JsonNode body(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

}

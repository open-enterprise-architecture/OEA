package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.metamodel.MetamodelInstance;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/metamodel/starter-packages.feature. The scenarios share one new instance
 * and its logins and run in file order: the package list is read before the import.
 */
public class StarterPackageSteps {

	private static MetamodelInstance shared;

	private static String lastBatchId;

	private HttpResponse<String> response;

	@Given("a new instance with a metamodel administrator, a viewer and a person without role logged in through the IdP")
	public void instance() throws Exception {
		synchronized (StarterPackageSteps.class) {
			if (shared == null) {
				shared = MetamodelInstance.start();
			}
		}
	}

	@AfterAll
	public static void stop() {
		if (shared != null) {
			shared.close();
			shared = null;
		}
	}

	@When("the metamodel administrator lists the starter packages")
	public void list() throws Exception {
		this.response = shared.get("/api/v1/admin/metamodel/starter-packages", shared.metamodelAdmin());
		assertThat(this.response.statusCode()).isEqualTo(200);
	}

	@Given("the starter package {string} has been imported")
	@When("the metamodel administrator imports the starter package {string}")
	public void importStarter(String packageId) throws Exception {
		this.response = shared.post("/api/v1/admin/metamodel/starter-packages/" + packageId + "/import", "",
				shared.metamodelAdmin());
		if (this.response.statusCode() == 200) {
			lastBatchId = body().path("batchId").asString();
		}
	}

	@When("the viewer lists the metatypes")
	public void viewerLists() throws Exception {
		this.response = shared.get("/api/v1/metamodel/metatypes", shared.viewer());
	}

	@When("the person without role lists the metatypes")
	public void noRoleLists() throws Exception {
		this.response = shared.get("/api/v1/metamodel/metatypes", shared.noRole());
	}

	@Then("{string} is listed with {int} metatypes, among them {string}, {string} and {string}")
	public void listed(String packageId, int count, String first, String second, String third) {
		JsonNode entry = starter(packageId);
		assertThat(entry.path("metatypeCount").asInt()).isEqualTo(count);
		assertThat(entry.path("metatypeNames").valueStream().map(JsonNode::asString)).contains(first, second, third);
	}

	@Then("{string} is listed as not imported")
	public void notImported(String packageId) {
		assertThat(starter(packageId).path("imported").asBoolean()).isFalse();
	}

	@Then("{string} is listed as imported")
	public void imported(String packageId) throws Exception {
		list();
		assertThat(starter(packageId).path("imported").asBoolean()).isTrue();
	}

	@Then("{int} metatypes exist with scope {string}, each with the property {string} and the values {string}")
	public void metatypesWithStatus(int count, String scope, String property, String values) throws Exception {
		assertThat(this.response.statusCode()).isEqualTo(200);
		assertThat(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE scope = '" + scope + "'"))
			.isEqualTo(String.valueOf(count));
		assertThat(shared.oidc().single("""
				SELECT count(*)::text FROM metatype_property_mappings m JOIN property_definitions d
				ON d.id = m.property_def_id WHERE d.name = '%s'""".formatted(property))).isEqualTo(String.valueOf(count));
		assertThat(shared.oidc().column("""
				SELECT v.value FROM property_enum_values v JOIN property_definitions d ON d.id = v.property_def_id
				WHERE d.name = '%s' ORDER BY v.sort_order""".formatted(property)))
			.containsExactlyElementsOf(Arrays.stream(values.split(",\\s*")).toList());
	}

	@Then("the audit trail contains the starter import with {int} metatype imports under one batch ID")
	public void batch(int metatypes) throws Exception {
		assertThat(shared.oidc().audit("metamodel.package-imported", lastBatchId)).isOne();
		assertThat(shared.oidc().audit("metamodel.metatype-imported", lastBatchId)).isEqualTo(metatypes);
	}

	@Then("{int} metatypes are returned with scope {string} and origin {string}")
	public void returned(int count, String scope, String packageId) {
		assertThat(this.response.statusCode()).isEqualTo(200);
		JsonNode metatypes = body();
		assertThat(metatypes).hasSize(count);
		assertThat(metatypes).allSatisfy((metatype) -> {
			assertThat(metatype.path("scope").asString()).isEqualTo(scope);
			assertThat(metatype.path("sourcePackage").path("packageId").asString()).isEqualTo(packageId);
		});
	}

	@Then("{string} maps the property {string} with {int} values")
	public void maps(String metatypeName, String property, int values) {
		JsonNode metatype = body().valueStream()
			.filter((m) -> metatypeName.equals(m.path("name").asString()))
			.findFirst()
			.orElseThrow();
		List<JsonNode> definitions = metatype.path("propertyMappings")
			.valueStream()
			.map((m) -> m.path("propertyDefinition"))
			.filter((d) -> property.equals(d.path("name").asString()))
			.toList();
		assertThat(definitions).hasSize(1);
		assertThat(definitions.get(0).path("enumValues")).hasSize(values);
	}

	@Then("the request is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).isEqualTo(status);
	}

	private JsonNode starter(String packageId) {
		return body().valueStream()
			.filter((entry) -> packageId.equals(entry.path("packageId").asString()))
			.findFirst()
			.orElseThrow();
	}

	private JsonNode body() {
		return JsonMapper.shared().readTree(this.response.body());
	}

}

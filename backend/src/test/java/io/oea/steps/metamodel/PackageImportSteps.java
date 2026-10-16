package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.metamodel.MetamodelInstance;
import io.oea.testsupport.metamodel.TestPackages;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/metamodel/package-import.feature. All scenarios share one instance and one
 * login (the IdP accepts a TOTP code only once); each scenario uses its own package identifiers.
 */
public class PackageImportSteps {

	private static final String IMPORT = "/api/v1/admin/metamodel/import";

	private static MetamodelInstance shared;

	private HttpResponse<String> response;

	@Given("a running instance with a metamodel administrator logged in through the IdP")
	public void instance() throws Exception {
		synchronized (PackageImportSteps.class) {
			if (shared == null) {
				shared = MetamodelInstance.start("--oea.metamodel.import.max-size=64KB");
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

	@When("the metamodel administrator imports a file that is not valid JSON")
	public void faultyJson() throws Exception {
		this.response = shared.post(IMPORT, "{\n  \"packageId\": \"faulty\",\n  oops\n}", shared.metamodelAdmin());
	}

	@When("the metamodel administrator imports the package {string} with a metatype without a name")
	public void withoutName(String packageId) throws Exception {
		ObjectNode pkg = TestPackages.pkg(packageId, 1);
		((ObjectNode) pkg.withArray("metatypes").get(0)).remove("name");
		this.response = shared.post(IMPORT, pkg.toString(), shared.metamodelAdmin());
	}

	@When("the metamodel administrator imports the package {string} with more than 64 KB")
	public void tooLarge(String packageId) throws Exception {
		ObjectNode pkg = TestPackages.pkg(packageId, 1).put("sourceInstanceId", "x".repeat(70 * 1024));
		this.response = shared.post(IMPORT, pkg.toString(), shared.metamodelAdmin());
	}

	@When("the metamodel administrator imports the package {string} with translation bundles")
	public void withTranslations(String packageId) throws Exception {
		ObjectNode pkg = TestPackages.pkg(packageId, 1);
		pkg.withObject("i18nBundles").withObject("de").put("metatype.x.name", "Typ");
		this.response = shared.post(IMPORT, pkg.toString(), shared.metamodelAdmin());
	}

	@Given("the metamodel administrator has imported the package {string} with {int} metatypes")
	@When("the metamodel administrator imports the package {string} with {int} metatypes")
	public void importPackage(String packageId, int metatypes) throws Exception {
		this.response = shared.post(IMPORT, TestPackages.pkg(packageId, metatypes).toString(), shared.metamodelAdmin());
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
	}

	@When("the metamodel administrator imports the package {string} again with one metatype renamed")
	public void reimport(String packageId) throws Exception {
		ObjectNode pkg = TestPackages.pkg(packageId, 3);
		((ObjectNode) pkg.withArray("metatypes").get(1)).put("name", packageId + " Renamed");
		this.response = shared.post(IMPORT, pkg.toString(), shared.metamodelAdmin());
	}

	@When("a person without role imports the package {string} with {int} metatypes")
	public void importWithoutRole(String packageId, int metatypes) throws Exception {
		this.response = shared.post(IMPORT, TestPackages.pkg(packageId, metatypes).toString(), shared.noRole());
	}

	@When("the metamodel administrator imports the package {string} whose second metatype is named {string}")
	public void conflictingName(String packageId, String name) throws Exception {
		ObjectNode pkg = TestPackages.pkg(packageId, 2);
		((ObjectNode) pkg.withArray("metatypes").get(1)).put("name", name);
		this.response = shared.post(IMPORT, pkg.toString(), shared.metamodelAdmin());
	}

	@Then("the import is rejected with 422 naming line and column")
	public void rejectedWithLocation() {
		assertThat(this.response.statusCode()).isEqualTo(422);
		JsonNode problem = body();
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:invalid-package");
		assertThat(problem.path("location").path("line").asInt()).isEqualTo(3);
		assertThat(problem.path("location").path("column").asInt()).isPositive();
	}

	@Then("the import is rejected with 422 pointing to {string}")
	public void rejectedWithPointer(String pointer) {
		assertThat(this.response.statusCode()).isEqualTo(422);
		assertThat(body().path("pointer").asString()).isEqualTo(pointer);
	}

	@Then("the import is rejected with 422 as unsupported content")
	public void rejectedAsUnsupported() {
		assertThat(this.response.statusCode()).isEqualTo(422);
		assertThat(body().path("type").asString()).isEqualTo("urn:oea:problem:unsupported-package-content");
	}

	@Then("the import is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).isEqualTo(status);
	}

	@Then("no metatype of the package exists")
	public void noMetatypeOfFaultyPackage() throws Exception {
		noMetatype("faulty");
	}

	@Then("no metatype of the package {string} exists")
	public void noMetatype(String packageId) throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '"
				+ packageId + "'")).isEqualTo("0");
	}

	@Then("{int} metatypes are created with scope {string} and the origin {string} {word}")
	public void created(int count, String scope, String packageId, String version) throws Exception {
		assertThat(body().path("created").asInt()).isEqualTo(count);
		assertThat(shared.oidc().column("SELECT scope || ':' || source_package_version FROM metatypes WHERE "
				+ "source_package_id = '" + packageId + "'")).hasSize(count).allMatch((scope + ":" + version)::equals);
	}

	@Then("the audit trail contains the package import and {int} metatype imports with the same batch ID")
	public void batch(int metatypes) throws Exception {
		String batchId = body().path("batchId").asString();
		assertThat(shared.oidc().audit("metamodel.package-imported", batchId)).isOne();
		assertThat(shared.oidc().audit("metamodel.metatype-imported", batchId)).isEqualTo(metatypes);
	}

	@Then("the result reports {int} created and {int} updated metatypes")
	public void counts(int created, int updated) {
		assertThat(this.response.statusCode()).isEqualTo(200);
		assertThat(body().path("created").asInt()).isEqualTo(created);
		assertThat(body().path("updated").asInt()).isEqualTo(updated);
	}

	@Then("the package {string} has {int} metatypes")
	public void metatypeCount(String packageId, int count) throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM metatypes WHERE source_package_id = '"
				+ packageId + "'")).isEqualTo(String.valueOf(count));
	}

	@Then("the import is rejected with 409 listing {string} as conflict with another package")
	public void conflict(String element) {
		assertThat(this.response.statusCode()).isEqualTo(409);
		JsonNode conflict = body().path("conflictList").get(0);
		assertThat(conflict.path("element").asString()).isEqualTo(element);
		assertThat(conflict.path("reason").asString()).isEqualTo("other-package");
	}

	private JsonNode body() {
		return JsonMapper.shared().readTree(this.response.body());
	}

}

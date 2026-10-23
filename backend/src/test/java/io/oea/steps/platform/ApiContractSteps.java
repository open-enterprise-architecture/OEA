package io.oea.steps.platform;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/platform/api-contract.feature: {@code createEntity}, {@code getExplorerView}
 * and {@code runAnalyticsQuery} are planned operations of the contract.
 */
public class ApiContractSteps {

	private static RepositoryInstance shared;

	private HttpResponse<String> response;

	private String marker;

	@Given("an instance with planned contract operations, an enterprise architect and a viewer logged in through the IdP")
	public void instance() throws Exception {
		synchronized (ApiContractSteps.class) {
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
		}
	}

	@When("the enterprise architect creates an entity through the planned single-entity operation")
	public void createEntity() throws Exception {
		String metatype = shared.oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'");
		this.marker = "Planned " + System.nanoTime();
		String body = JsonMapper.shared().createObjectNode().put("name", this.marker).put("metatypeId", metatype).toString();
		this.response = shared.post("/api/v1/entities", body, shared.architect(), Map.of());
	}

	@When("a caller without session requests the planned explorer view")
	public void explorerWithoutSession() throws Exception {
		this.response = shared.oidc()
			.send(HttpRequest.newBuilder(URI.create(shared.oidc().url() + "/api/v1/views/explorer")).GET());
	}

	@When("the viewer runs a query through the planned analytics operation")
	public void analyticsAsViewer() throws Exception {
		this.response = shared.post("/api/v1/analytics/query", "{}", shared.viewer(), Map.of());
	}

	@Then("the planned operation is answered with {int} and the problem type {string}")
	public void answeredWithType(int status, String type) throws Exception {
		answered(status);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("type").asString()).isEqualTo(type);
	}

	@Then("the planned operation is answered with {int}")
	public void answered(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("no entity is created")
	public void nothingCreated() throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM entities WHERE name = '" + this.marker + "'"))
			.isEqualTo("0");
	}

}

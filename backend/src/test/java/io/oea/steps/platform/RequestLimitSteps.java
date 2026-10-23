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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/platform/request-limits.feature: an own instance whose entity batch is
 * limited to 1 MB, so that the rejected bodies stay below the amount Tomcat reads after an
 * early response.
 */
public class RequestLimitSteps {

	private static final String BATCH = "/api/v1/entities/batch";

	private static RepositoryInstance shared;

	private HttpResponse<String> response;

	private String marker;

	@Given("an instance with the starter package, a batch limit of 1 MB and an enterprise architect logged in through the IdP")
	public void instance() throws Exception {
		synchronized (RequestLimitSteps.class) {
			if (shared == null) {
				shared = RepositoryInstance.start("--oea.web.request-size.entity-batch=1MB");
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

	@When("the enterprise architect sends an entity batch of {int} MB")
	public void oversized(int megabytes) throws Exception {
		this.response = shared.post(BATCH, batch(megabytes * 1024 * 1024), shared.architect(), Map.of());
	}

	@When("the enterprise architect sends an entity batch of {int} applications below 1 MB")
	public void withinLimit(int count) throws Exception {
		String metatype = shared.oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'");
		ObjectNode request = JsonMapper.shared().createObjectNode();
		ArrayNode entities = request.putArray("entities");
		this.marker = "Limited " + System.nanoTime();
		for (int i = 0; i < count; i++) {
			entities.addObject().put("name", this.marker + " " + i).put("metatypeId", metatype);
		}
		this.response = shared.post(BATCH, request.toString(), shared.architect(), Map.of());
	}

	@When("a caller without session sends an entity batch of {int} MB")
	public void withoutSession(int megabytes) throws Exception {
		this.response = shared.oidc()
			.send(HttpRequest.newBuilder(URI.create(shared.oidc().url() + BATCH))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(batch(megabytes * 1024 * 1024))));
	}

	@Then("the oversized request is rejected with {int} and the problem type {string}")
	public void rejectedWithType(int status, String type) throws Exception {
		rejected(status);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("type").asString()).isEqualTo(type);
	}

	@Then("the oversized request is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("no entity of the batch is written")
	public void nothingWritten() throws Exception {
		assertThat(shared.oidc().single("SELECT count(*)::text FROM entities WHERE name LIKE 'Oversized %'"))
			.isEqualTo("0");
	}

	@Then("the batch is processed and {int} entities are created")
	public void processed(int count) throws Exception {
		rejected(200);
		assertThat(JsonMapper.shared().readTree(this.response.body()).path("summary").path("created").asInt())
			.isEqualTo(count);
	}

	/** A syntactically valid batch document of the given size; its entities are named "Oversized …". */
	private static String batch(int bytes) {
		StringBuilder body = new StringBuilder("{\"entities\": [");
		int i = 0;
		while (body.length() < bytes) {
			body.append((i > 0) ? "," : "").append("{\"name\": \"Oversized ").append(i++).append("\"}");
		}
		return body.append("]}").toString();
	}

}

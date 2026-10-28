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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/platform/request-limits.feature: an own instance whose entity batch is
 * limited to 1 MB, so that the rejected bodies stay below the amount Tomcat reads after an
 * early response, and whose request limit is 30 per minute. The batch-limit scenario uses a
 * second enterprise architect, whose batch window is not used by the size scenarios.
 */
public class RequestLimitSteps {

	private static final String BATCH = "/api/v1/entities/batch";

	private static RepositoryInstance shared;

	private HttpResponse<String> response;

	private final List<HttpResponse<String>> responses = new ArrayList<>();

	private String marker;

	@Given("an instance with the starter package, a batch limit of 1 MB, a request limit of 30 per minute and an enterprise architect logged in through the IdP")
	public void instance() throws Exception {
		synchronized (RequestLimitSteps.class) {
			if (shared == null) {
				shared = RepositoryInstance.start("--oea.web.request-size.entity-batch=1MB",
						"--oea.web.rate-limit.requests-per-minute=30");
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

	@When("an analyst logs in and lists the catalogs until a request is rejected")
	public void listCatalogs() throws Exception {
		shared.oidc().mapping("oea-analysts", "analyst");
		RepositoryInstance.Session analyst = shared.login("dev-analyst");
		for (int i = 0; i < 100; i++) {
			HttpResponse<String> listed = shared.oidc().get("/api/v1/catalogs", analyst.token());
			this.responses.add(listed);
			if (listed.statusCode() != 200) {
				return;
			}
		}
	}

	@Then("{int} requests of the analyst were accepted within the minute, including the request of the login")
	public void accepted(int count) {
		// the login reads /api/v1/me once (RepositoryInstance#login)
		assertThat(this.responses.stream().filter((listed) -> listed.statusCode() == 200).count() + 1)
			.isEqualTo(count);
	}

	@When("another enterprise architect sends {int} entity batches with one application each within a minute")
	public void batches(int count) throws Exception {
		RepositoryInstance.Session architect = shared.login("dev-architect-metamodel");
		String metatype = shared.oidc().single("SELECT id::text FROM metatypes WHERE name = 'Application Component'");
		this.marker = "Rate limited " + System.nanoTime();
		for (int i = 1; i <= count; i++) {
			ObjectNode request = JsonMapper.shared().createObjectNode();
			request.putArray("entities").addObject().put("name", this.marker + " " + i).put("metatypeId", metatype);
			this.responses.add(shared.post(BATCH, request.toString(), architect, Map.of()));
		}
	}

	@Then("the first {int} requests are answered with {int}")
	public void firstAnswered(int count, int status) {
		assertThat(this.responses.subList(0, count)).allSatisfy(
				(response) -> assertThat(response.statusCode()).as(response.body()).isEqualTo(status));
	}

	@Then("the first {int} batches are processed")
	public void firstProcessed(int count) {
		firstAnswered(count, 200);
	}

	@Then("the last request is rejected with {int}, the problem type {string} and a waiting time")
	public void lastRejected(int status, String type) throws Exception {
		HttpResponse<String> last = this.responses.getLast();
		assertThat(last.statusCode()).as(last.body()).isEqualTo(status);
		assertThat(JsonMapper.shared().readTree(last.body()).path("type").asString()).isEqualTo(type);
		assertThat(last.headers().firstValueAsLong("Retry-After").orElse(0)).isBetween(1L, 60L);
	}

	@Then("the application of the eleventh batch is not written")
	public void eleventhNotWritten() throws Exception {
		assertThat(shared.oidc()
			.single("SELECT count(*)::text FROM entities WHERE name = '" + this.marker + " 11'")).isEqualTo("0");
		assertThat(shared.oidc()
			.single("SELECT count(*)::text FROM entities WHERE name LIKE '" + this.marker + " %'")).isEqualTo("10");
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

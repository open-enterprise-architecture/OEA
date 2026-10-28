package io.oea.steps.platform;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Steps for features/platform/authorization.feature: an own instance whose summary window of
 * suppressed denials is 10 seconds, so that the summary event appears within the scenario.
 */
public class AuthorizationSteps {

	private static RepositoryInstance shared;

	private final List<HttpResponse<String>> responses = new ArrayList<>();

	@Given("an instance with the starter package, a denial summary window of 10 seconds and a viewer logged in through the IdP")
	public void instance() throws Exception {
		synchronized (AuthorizationSteps.class) {
			if (shared == null) {
				shared = RepositoryInstance.start("--oea.audit.flood-suppression.window=PT10S",
						"--oea.audit.flood-suppression.flush-interval=PT1S");
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

	@When("the viewer lists the IdP group mappings")
	public void listMappings() throws Exception {
		this.responses.add(shared.oidc().get("/api/v1/admin/idp-group-mappings", shared.viewer().token()));
	}

	@When("the viewer lists the persons {int} times")
	public void listPersons(int times) throws Exception {
		for (int i = 0; i < times; i++) {
			this.responses.add(shared.oidc().get("/api/v1/admin/persons", shared.viewer().token()));
		}
	}

	@Then("the request is refused with {int}")
	@Then("every request is refused with {int}")
	public void refused(int status) {
		assertThat(this.responses).isNotEmpty().allSatisfy((response) -> assertThat(response.statusCode())
			.as(response.body())
			.isEqualTo(status));
	}

	@Then("the audit trail contains {int} event(s) {string} of the viewer for the operation {string}")
	public void events(int count, String eventType, String operation) throws Exception {
		assertThat(individual(eventType, operation)).isEqualTo(count);
	}

	@Then("after the window has closed the audit trail contains a summary event for {string} with the count {int}")
	public void summary(String operation, int count) throws Exception {
		String sql = viewerEvents("e.details ->> 'suppressedCount'", "authz.denied", operation)
				+ " AND e.details ->> 'summary' = 'true'";
		Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
		String found = shared.oidc().single(sql);
		while (found == null && Instant.now().isBefore(deadline)) {
			Thread.sleep(500);
			found = shared.oidc().single(sql);
		}
		assertThat(found).as("summary event").isEqualTo(Integer.toString(count));
	}

	private int individual(String eventType, String operation) throws Exception {
		return Integer.parseInt(shared.oidc()
			.single(viewerEvents("count(*)::text", eventType, operation) + " AND e.details ->> 'summary' IS NULL"));
	}

	private static String viewerEvents(String select, String eventType, String operation) {
		return """
				SELECT %s FROM audit.events e JOIN persons p ON p.id = e.actor_person_id
				WHERE p.name = 'dev-viewer' AND e.event_type = '%s' AND e.details ->> 'operation' = '%s'"""
			.formatted(select, eventType, operation);
	}

}

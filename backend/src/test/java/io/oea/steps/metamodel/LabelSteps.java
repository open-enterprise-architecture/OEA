package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/metamodel/labels.feature and features/identity/language-setting.feature
 * (us-140-dynamic-labels). All scenarios share one instance; each scenario starts without
 * organization labels.
 */
public class LabelSteps {

	private static final String BUNDLE = "/api/v1/i18n/de";

	private static RepositoryInstance shared;

	private HttpResponse<String> response;

	private String etag;

	@Given("a running repository instance with the German labels of the starter package")
	public void instance() throws Exception {
		synchronized (LabelSteps.class) {
			if (shared == null) {
				shared = RepositoryInstance.start();
			}
		}
		// labels of the organization left by an earlier scenario
		String key = key("Application Component");
		ObjectNode removal = JsonMapper.shared().createObjectNode();
		removal.putObject("labels").putNull(key);
		ok(shared.send("PUT", "/api/v1/metamodel/labels/de", removal.toString(), shared.metamodelAdmin()), 200);
	}

	@AfterAll
	public static void stop() {
		if (shared != null) {
			shared.close();
			shared = null;
		}
	}

	@When("a person reads the German labels")
	@Given("the person has read the German labels")
	public void read() throws Exception {
		this.response = shared.send("GET", BUNDLE, null, shared.architect());
		ok(this.response, 200);
		this.etag = this.response.headers().firstValue("ETag").orElseThrow();
	}

	@When("the person reads the German labels again with the received ETag")
	public void readAgain() throws Exception {
		this.response = shared.send("GET", BUNDLE, null, shared.architect(), Map.of("If-None-Match", this.etag));
	}

	@When("the metamodel administrator labels {string} in German as {string}")
	public void label(String metatype, String text) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putObject("labels").put(key(metatype), text);
		ok(shared.send("PUT", "/api/v1/metamodel/labels/de", body.toString(), shared.metamodelAdmin()), 200);
	}

	@Then("{string} is labelled {string}")
	public void labelled(String metatype, String text) throws Exception {
		assertThat(body(this.response).path(key(metatype)).asString()).isEqualTo(text);
	}

	@Then("the value {string} of {string} is labelled {string}")
	public void valueLabelled(String value, String property, String text) throws Exception {
		JsonNode properties = ok(shared.send("GET", "/api/v1/metamodel/property-definitions", null, shared.architect()),
				200);
		String id = null;
		for (JsonNode definition : properties) {
			if (property.equals(definition.path("name").asString())) {
				for (JsonNode enumValue : definition.path("enumValues")) {
					if (value.equals(enumValue.path("value").asString())) {
						id = enumValue.path("id").asString();
					}
				}
			}
		}
		assertThat(id).as("enum value " + value).isNotNull();
		assertThat(body(this.response).path("enum-value." + id + ".label").asString()).isEqualTo(text);
	}

	@Then("the labels are answered with 304 and no body")
	public void notModified() {
		assertThat(this.response.statusCode()).isEqualTo(304);
		assertThat(this.response.body()).isEmpty();
	}

	@Then("the labels are answered with 200 and a new ETag")
	public void changed() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		assertThat(this.response.headers().firstValue("ETag").orElseThrow()).isNotEqualTo(this.etag);
	}

	@Given("the person has chosen no language")
	public void noLanguage() throws Exception {
		ok(shared.send("PUT", "/api/v1/me/preferences", "{\"uiLanguage\":null}", shared.architect()), 204);
	}

	@When("the person chooses the language {string}")
	public void choose(String language) throws Exception {
		ok(shared.send("PUT", "/api/v1/me/preferences", "{\"uiLanguage\":\"" + language + "\"}", shared.architect()),
				204);
	}

	@Then("the current principal reports the language {string}")
	public void reports(String language) throws Exception {
		assertThat(ok(shared.send("GET", "/api/v1/me", null, shared.architect()), 200).path("uiLanguage").asString())
			.isEqualTo(language);
	}

	private static String key(String metatype) throws Exception {
		for (JsonNode type : ok(shared.send("GET", "/api/v1/metamodel/metatypes", null, shared.architect()), 200)) {
			if (metatype.equals(type.path("name").asString())) {
				return "metatype." + type.path("id").asString() + ".name";
			}
		}
		throw new AssertionError("no metatype " + metatype);
	}

	private static JsonNode ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		return (response.body() == null || response.body().isEmpty()) ? JsonMapper.shared().createObjectNode()
				: JsonMapper.shared().readTree(response.body());
	}

	private static JsonNode body(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

}

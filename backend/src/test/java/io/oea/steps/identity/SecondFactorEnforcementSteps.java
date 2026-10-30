package io.oea.steps.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/identity/second-factor-enforcement.feature; the instance, the system admin
 * session and the persons come from {@link LocalLoginSteps}, {@link PersonPasswordSteps} and
 * {@link PasskeySteps} of the same scenario.
 */
public class SecondFactorEnforcementSteps {

	private final LocalLoginSteps instance;

	private final PersonPasswordSteps persons;

	private final PasskeySteps passkeys;

	public SecondFactorEnforcementSteps(LocalLoginSteps instance, PersonPasswordSteps persons, PasskeySteps passkeys) {
		this.instance = instance;
		this.persons = persons;
		this.passkeys = passkeys;
	}

	@Given("the system admin enforces a second factor with the exempt roles {string}")
	public void enforce(String roles) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("enforced", true);
		var exempt = body.putArray("exemptRoles");
		Arrays.stream(roles.split(",")).map(String::strip).filter((role) -> !role.isEmpty()).forEach(exempt::add);
		this.instance.response = this.instance.put("/api/v1/admin/settings/second-factor-policy", body.toString());
	}

	@Then("{string} must set up a second factor before anything else")
	public void mustEnroll(String name) throws Exception {
		String token = this.persons.personToken();
		JsonNode me = LocalLoginSteps.json(this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me"))
			.header("Cookie", PersonPasswordSteps.cookie(token))));
		assertThat(me.path("requiredAction").asString()).isEqualTo("enroll-second-factor");
		HttpResponse<String> blocked = this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/catalogs"))
			.header("Cookie", PersonPasswordSteps.cookie(token)));
		assertThat(blocked.statusCode()).isEqualTo(403);
		assertThat(LocalLoginSteps.json(blocked).path("type").asString())
			.isEqualTo("urn:oea:problem:second-factor-enrollment-required");
	}

	@Then("the session of {string} is no longer restricted")
	public void unrestricted(String name) throws Exception {
		JsonNode me = LocalLoginSteps.json(this.instance.send(HttpRequest.newBuilder(this.instance.uri("/api/v1/me"))
			.header("Cookie", PersonPasswordSteps.cookie(this.persons.personToken()))));
		assertThat(me.path("requiredAction").isNull()).isTrue();
		assertThat(me.path("mfaSatisfied").asBoolean()).isTrue();
	}

	@Given("{string} holds the role {string} through a local role assignment")
	public void holds(String name, String role) throws Exception {
		this.instance.superuser("INSERT INTO role_assignments (person_id, role_id, granted_by_admin_id, valid_from) "
				+ "SELECT " + this.persons.id(name) + ", '" + role + "', id, now() - interval '1 hour' "
				+ "FROM system_admin_accounts LIMIT 1");
	}

	@Then("the login of {string} has no required action")
	public void noAction(String name) {
		HttpResponse<String> login = this.passkeys.lastPasswordStep();
		assertThat(login.statusCode()).as(login.body()).isEqualTo(201);
		assertThat(LocalLoginSteps.json(login).path("requiredAction").isNull()).isTrue();
	}

	@Then("the login of {string} requires the enrollment of a second factor")
	public void enrollmentRequired(String name) {
		HttpResponse<String> login = this.passkeys.lastPasswordStep();
		assertThat(login.statusCode()).as(login.body()).isEqualTo(201);
		assertThat(LocalLoginSteps.json(login).path("requiredAction").asString()).isEqualTo("enroll-second-factor");
	}

}

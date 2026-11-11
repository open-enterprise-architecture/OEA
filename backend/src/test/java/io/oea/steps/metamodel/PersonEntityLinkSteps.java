package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Steps for features/identity/person-entity-link.feature (us-127-connection-scoped-visibility),
 * on the instance of the metamodel features; the security administrator is `dev-security-admin`.
 */
public class PersonEntityLinkSteps {

	private HttpResponse<String> response;

	private String element;

	@Given("the person metatype {string} with the element {string}")
	public void personMetatype(String metatype, String element) throws Exception {
		RepositoryInstance instance = MetatypeEditingSteps.sharedInstance();
		String metatypeId = instance.oidc().single("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', '%s')
				RETURNING id::text""".formatted(metatype));
		HttpResponse<String> set = instance.send("PUT", "/api/v1/admin/property-visibility/person-metatype",
				JsonMapper.shared().createObjectNode().put("metatypeId", metatypeId).toString(),
				PropertyVisibilitySteps.securityAdmin());
		assertThat(set.statusCode()).as(set.body()).isEqualTo(200);
		this.element = instance.oidc().single("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES ('%s', '%s', (SELECT min(id) FROM persons))
				RETURNING id::text""".formatted(metatypeId, element));
	}

	@When("the security administrator links the account of {string} to that element")
	public void link(String username) throws Exception {
		this.response = sendLink(MetatypeEditingSteps.sharedInstance().oidc().single(
				"SELECT p.id::text FROM persons p WHERE p.email LIKE '%s@%%'".formatted(username)));
	}

	@When("the security administrator links their own account to that element")
	public void linkOwn() throws Exception {
		this.response = sendLink(MetatypeEditingSteps.sharedInstance().oidc().single(
				"SELECT p.id::text FROM persons p WHERE p.email LIKE 'dev-security-admin@%'"));
	}

	@Then("the account of {string} is linked to {string} and the link is audited")
	public void linked(String username, String element) throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(200);
		RepositoryInstance instance = MetatypeEditingSteps.sharedInstance();
		String personId = instance.oidc().single("SELECT p.id::text FROM persons p WHERE p.email LIKE '%s@%%'".formatted(username));
		assertThat(instance.oidc().single("SELECT e.name FROM persons p JOIN entities e ON e.id = p.entity_id WHERE p.id = "
				+ personId)).isEqualTo(element);
		assertThat(instance.oidc().single(
				"SELECT count(*) FROM audit.events WHERE event_type = 'person.account-linked' AND target_id = '" + personId + "'"))
			.isNotEqualTo("0");
	}

	@Then("the link is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	private HttpResponse<String> sendLink(String personId) throws Exception {
		return MetatypeEditingSteps.sharedInstance()
			.send("PUT", "/api/v1/admin/persons/" + personId + "/entity-link",
					JsonMapper.shared().createObjectNode().put("entityId", Integer.parseInt(this.element)).toString(),
					PropertyVisibilitySteps.securityAdmin());
	}

}

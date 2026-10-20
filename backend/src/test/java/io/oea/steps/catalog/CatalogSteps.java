package io.oea.steps.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.AfterAll;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.audit.verify.ChainVerifier;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.repository.RepositoryInstance;
import io.oea.testsupport.repository.RepositoryInstance.Session;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/catalog/catalog-definition.feature. The scenarios share one instance with
 * the starter package; `dev-architect` holds `enterprise-architect`, `dev-viewer` holds `viewer`.
 * Each scenario uses its own catalog names.
 */
public class CatalogSteps {

	private static final String CATALOGS = "/api/v1/catalogs";

	private static RepositoryInstance shared;

	private HttpResponse<String> response;

	@Given("an instance with the starter package and catalog users logged in through the IdP")
	public void instance() throws Exception {
		synchronized (CatalogSteps.class) {
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

	@Given("the enterprise architect has created the catalog {string} with scope {string}")
	public void created(String name, String scope) throws Exception {
		create(shared.architect(), catalog(name, metatype("Application Component"), scope));
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@When("the {role} creates the catalog {string} for {string} with scope {string}")
	public void create(Session session, String name, String metatype, String scope) throws Exception {
		create(session, catalog(name, metatype(metatype), scope));
	}

	@When("the {role} creates the catalog {string} for {string} with scope {string} and join mode {string}")
	public void createWithJoinMode(Session session, String name, String metatype, String scope, String joinMode)
			throws Exception {
		create(session, catalog(name, metatype(metatype), scope).put("defaultJoinMode", joinMode));
	}

	@When("the enterprise architect creates the catalog {string} for an unknown metatype")
	public void unknownMetatype(String name) throws Exception {
		create(shared.architect(), catalog(name, UUID.randomUUID().toString(), "instance"));
	}

	@When("the enterprise architect creates a catalog with a name of {int} characters")
	public void longName(int length) throws Exception {
		create(shared.architect(), catalog("n".repeat(length), metatype("Application Component"), "instance"));
	}

	@Then("the catalog is created")
	public void isCreated() {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(201);
	}

	@Then("the catalog is created with empty columns, joins, saved filters and saved views")
	public void createdEmpty() throws Exception {
		isCreated();
		JsonNode created = body();
		assertThat(created.path("primaryMetatypeName").asString()).isEqualTo("Application Component");
		for (String list : List.of("columns", "joinDefinitions", "savedFilters", "savedViews")) {
			assertThat(created.path(list).isArray()).as(list).isTrue();
			assertThat(created.path(list).size()).as(list).isZero();
		}
	}

	@Then("the catalog is created with the default join mode {string}")
	public void createdWithJoinMode(String joinMode) throws Exception {
		isCreated();
		assertThat(body().path("defaultJoinMode").asString()).isEqualTo(joinMode);
	}

	@Then("the creation is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the creation is rejected with 422 and the message {string}")
	public void rejectedWithMessage(String message) throws Exception {
		rejected(422);
		assertThat(body().path("detail").asString()).isEqualTo(message);
	}

	@Then("the creation is rejected with 422 naming the field {string}")
	public void rejectedNaming(String field) throws Exception {
		rejected(422);
		List<String> fields = new ArrayList<>();
		body().path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).contains(field);
	}

	@Then("no catalog is named {string}")
	public void noCatalog(String name) throws Exception {
		assertThat(catalogsNamed(name)).isZero();
	}

	@Then("exactly one catalog is named {string} ignoring case")
	public void exactlyOne(String name) throws Exception {
		assertThat(catalogsNamed(name)).isEqualTo(1);
	}

	@Then("the catalog overview of the {role} lists {string}")
	public void lists(Session session, String name) throws Exception {
		assertThat(overview(session)).contains(name);
	}

	@Then("the catalog overview of the {role} does not list {string}")
	public void doesNotList(Session session, String name) throws Exception {
		assertThat(overview(session)).doesNotContain(name);
	}

	@Then("the catalog overview of the {role} lists {string}, {string} and {string} in this order")
	public void listsInOrder(Session session, String first, String second, String third) throws Exception {
		assertThat(overview(session)).containsSubsequence(first, second, third);
	}

	@Then("the {role} receives {int} for the catalog {string}")
	public void read(Session session, int status, String name) throws Exception {
		String id = shared.oidc()
			.single("SELECT id::text FROM catalogs WHERE name = '" + name.replace("'", "''") + "'");
		assertThat(shared.oidc().get(CATALOGS + "/" + id, session.token()).statusCode()).isEqualTo(status);
	}

	@Then("one {string} audit event names {string}")
	public void audited(String eventType, String name) throws Exception {
		assertThat(shared.oidc().audit(eventType, name)).isEqualTo(1);
	}

	@Then("the audit chain is intact")
	public void chainIntact() throws Exception {
		try (Connection connection = OeaDatabases.asSuperuser(shared.oidc().postgres())) {
			assertThat(new ChainVerifier().verify(connection, List.of()).problems()).isEmpty();
		}
	}

	@ParameterType("enterprise architect|viewer")
	public Session role(String role) {
		return "viewer".equals(role) ? shared.viewer() : shared.architect();
	}

	private void create(Session session, ObjectNode request) throws Exception {
		this.response = shared.post(CATALOGS, request.toString(), session, Map.of());
	}

	private static ObjectNode catalog(String name, String metatypeId, String scope) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("primaryMetatypeId", metatypeId)
			.put("scope", scope);
	}

	private static String metatype(String name) throws Exception {
		return shared.oidc().single("SELECT id::text FROM metatypes WHERE name = '" + name.replace("'", "''") + "'");
	}

	private static int catalogsNamed(String name) throws Exception {
		return Integer.parseInt(shared.oidc()
			.single("SELECT count(*)::text FROM catalogs WHERE lower(btrim(name)) = lower(btrim('"
					+ name.replace("'", "''") + "'))"));
	}

	private static List<String> overview(Session session) throws Exception {
		HttpResponse<String> listed = shared.oidc().get(CATALOGS, session.token());
		assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
		List<String> names = new ArrayList<>();
		JsonMapper.shared().readTree(listed.body()).forEach((catalog) -> names.add(catalog.path("name").asString()));
		return names;
	}

	private JsonNode body() throws Exception {
		return JsonMapper.shared().readTree(this.response.body());
	}

}

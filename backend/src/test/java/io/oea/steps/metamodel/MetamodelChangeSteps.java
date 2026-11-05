package io.oea.steps.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.metamodel.TestPackages;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/metamodel/change-history.feature (us-034-metamodel-changes). Kurt is
 * `dev-metamodel-admin`, Lukas `dev-architect-metamodel`, both metamodel administrators of the
 * instance of the metamodel editing features. Every scenario creates its metatypes under unique
 * names; a scenario that makes the audit trail unwritable restores it afterwards.
 */
public class MetamodelChangeSteps {

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	private static final String CHANGES = "/api/v1/metamodel/changes";

	private static final String METATYPES = "/api/v1/metamodel/metatypes";

	private static RepositoryInstance.Session lukas;

	private final String suffix = "-" + UUID.randomUUID().toString().substring(0, 8);

	private String created;

	private String deleted;

	private String batchId;

	private String packageId;

	private boolean revoked;

	private int metatypesBefore;

	private HttpResponse<String> response;

	@After
	public void restoreAuditTrail() throws Exception {
		if (this.revoked) {
			grant("GRANT INSERT ON audit.events TO oea_app");
		}
	}

	// Reading metamodel changes

	@Given("Kurt created the metatype {string} and Lukas deleted another metatype")
	public void twoAdministrators(String name) throws Exception {
		this.created = id(ok(send("POST", METATYPES, metatype(name + this.suffix), kurt()), 201));
		this.deleted = id(ok(send("POST", METATYPES, metatype("Other" + this.suffix), lukas()), 201));
		ok(send("DELETE", METATYPES + "/" + this.deleted, null, lukas()), 204);
	}

	@When("Kurt lists the metamodel changes")
	public void kurtLists() throws Exception {
		this.response = send("GET", CHANGES + "?kind=metatype", null, kurt());
	}

	@Then("he sees both entries with the names of Kurt and Lukas, their times and the element names")
	public void bothEntries() throws Exception {
		JsonNode page = ok(this.response, 200);
		JsonNode creation = event(page, "metamodel.metatype-created", this.created);
		JsonNode deletion = event(page, "metamodel.metatype-deleted", this.deleted);
		assertThat(creation.path("actor").path("actorName").asString()).isEqualTo(name(kurt()));
		assertThat(deletion.path("actor").path("actorName").asString()).isEqualTo(name(lukas()));
		assertThat(creation.path("elementName").asString()).isEqualTo("SecurityZone" + this.suffix);
		assertThat(deletion.path("elementName").asString()).isEqualTo("Other" + this.suffix);
		assertThat(creation.path("occurredAt").asString()).isNotEmpty();
		assertThat(deletion.path("occurredAt").asString()).isNotEmpty();
	}

	@When("a person holding only viewer lists the metamodel changes")
	public void viewerLists() throws Exception {
		this.response = send("GET", CHANGES, null, instance().viewer());
	}

	@Then("the change list is rejected with {int}")
	public void rejected(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	// Filtering and import batches

	@Given("a package with {int} metatypes was imported")
	public void imported(int metatypes) throws Exception {
		this.packageId = "test-history" + this.suffix;
		this.batchId = ok(send("POST", "/api/v1/admin/metamodel/import", TestPackages.pkg(this.packageId, metatypes),
				kurt()), 200).path("batchId").asString();
	}

	@When("an administrator lists the metamodel changes of its batch")
	public void listBatch() throws Exception {
		this.response = send("GET", CHANGES + "?batchId=" + this.batchId + "&pageSize=200", null, kurt());
	}

	@Then("the list shows the {int} imported metatypes with the same batch ID")
	public void batchListed(int metatypes) {
		List<String> imported = new ArrayList<>();
		for (JsonNode item : ok(this.response, 200).path("items")) {
			assertThat(item.path("batchId").asString()).isEqualTo(this.batchId);
			if ("metamodel.metatype-imported".equals(item.path("eventType").asString())) {
				imported.add(item.path("elementName").asString());
			}
		}
		assertThat(imported).hasSize(metatypes).allMatch((name) -> name.startsWith(this.packageId + " Type "));
	}

	@When("an administrator filters the metamodel changes by Lukas")
	public void filterByLukas() throws Exception {
		this.response = send("GET", CHANGES + "?actorRef=" + personId(lukas()), null, kurt());
	}

	@Then("only the changes made by Lukas are listed")
	public void onlyLukas() throws Exception {
		JsonNode items = ok(this.response, 200).path("items");
		String ref = personId(lukas());
		assertThat(items).isNotEmpty().allMatch((item) -> item.path("actor").path("actorRef").asString().equals(ref));
		event(ok(this.response, 200), "metamodel.metatype-deleted", this.deleted);
	}

	// Change and audit event are atomic

	@Given("the audit event of a change cannot be written")
	public void auditNotWritable() throws Exception {
		this.revoked = true;
		grant("REVOKE INSERT ON audit.events FROM oea_app");
	}

	@When("an administrator creates a metatype")
	public void createWithoutAudit() throws Exception {
		this.metatypesBefore = Integer.parseInt(instance().oidc().single("SELECT count(*)::text FROM metatypes"));
		this.response = send("POST", METATYPES, metatype("Unaudited" + this.suffix), kurt());
	}

	@Then("the request fails and no metatype is created")
	public void failedAndNothingCreated() throws Exception {
		assertThat(this.response.statusCode()).as(this.response.body()).isGreaterThanOrEqualTo(500);
		assertThat(Integer.parseInt(instance().oidc().single("SELECT count(*)::text FROM metatypes")))
			.isEqualTo(this.metatypesBefore);
	}

	// helpers

	private static void grant(String statement) throws Exception {
		try (Connection su = OeaDatabases.asSuperuser(instance().oidc().postgres());
				Statement sql = su.createStatement()) {
			sql.execute(statement);
		}
	}

	private static RepositoryInstance.Session kurt() throws Exception {
		return instance().metamodelAdmin();
	}

	private static RepositoryInstance.Session lukas() throws Exception {
		synchronized (MetamodelChangeSteps.class) {
			if (lukas == null) {
				lukas = instance().login("dev-architect-metamodel");
			}
			return lukas;
		}
	}

	private static String personId(RepositoryInstance.Session session) throws Exception {
		return ok(instance().send("GET", "/api/v1/me", null, session), 200).path("principal").path("id").asString();
	}

	private static String name(RepositoryInstance.Session session) throws Exception {
		return instance().oidc().single("SELECT name FROM persons WHERE id = " + personId(session));
	}

	private static ObjectNode metatype(String name) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("configId", INSTANCE);
	}

	private static JsonNode event(JsonNode page, String eventType, String target) {
		for (JsonNode item : page.path("items")) {
			if (item.path("eventType").asString().equals(eventType) && item.path("targetId").asString().equals(target)) {
				return item;
			}
		}
		throw new AssertionError("no " + eventType + " of " + target);
	}

	private static HttpResponse<String> send(String method, String path, ObjectNode body,
			RepositoryInstance.Session session) throws Exception {
		return instance().send(method, path, (body != null) ? body.toString() : null, session);
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

	private static String id(JsonNode node) {
		return node.path("id").asString();
	}

	private static JsonNode ok(HttpResponse<String> response, int status) {
		assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
		if (response.body() == null || response.body().isBlank()) {
			return JsonMapper.shared().missingNode();
		}
		try {
			return JsonMapper.shared().readTree(response.body());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}

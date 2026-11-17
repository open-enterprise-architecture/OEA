package io.oea.steps.solution;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.oea.steps.metamodel.MetatypeEditingSteps;
import io.oea.testsupport.repository.RepositoryInstance;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Steps for features/solution/plateaus.feature (us-084-plateaus D2–D5), on the instance of the
 * metamodel features. The architect (`enterprise-architect`) holds `plateau:write`; the viewer
 * does not. The instance has one baseline for every feature, so the plateaus and solutions carry a
 * run suffix, and the steps name them without it.
 */
public class PlateauSteps {

	private static final String PLATEAUS = "/api/v1/plateaus";

	private final String suffix = " " + UUID.randomUUID().toString().substring(0, 8);

	private final Map<String, String> plateaus = new HashMap<>();

	private final Map<String, String> solutions = new HashMap<>();

	private final Map<String, Integer> entities = new HashMap<>();

	private HttpResponse<String> response;

	private UUID application;

	private long clock;

	@Given("the instance has a baseline plateau")
	public void baseline() throws Exception {
		String found = instance().oidc().single("SELECT id::text FROM plateaus WHERE status = 'baseline'");
		if (found == null) {
			HttpResponse<String> created = send("POST", PLATEAUS, plateau("Baseline", "baseline"));
			assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
			found = json(created).path("id").asString();
		}
		this.plateaus.put("baseline", found);
	}

	@Given("the target plateau {string} succeeds {string} with the basis {string}")
	public void givenTarget(String name, String predecessor, String basis) throws Exception {
		HttpResponse<String> created = send("POST", PLATEAUS, targetBody(name, predecessor, basis));
		assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
		this.plateaus.put(name, json(created).path("id").asString());
	}

	@Given("the plateau {string} is realized")
	public void realized(String name) throws Exception {
		this.plateaus.put(name, instance().oidc().single("""
				INSERT INTO plateaus (id, name, status, valid_to, created_by)
				SELECT gen_random_uuid(), '%s', 'realized', DATE '2026-01-01', min(id) FROM persons
				RETURNING id::text""".formatted(name + this.suffix)));
	}

	@Given("the solution {string} targets {string}")
	public void solutionTargets(String name, String plateau) throws Exception {
		this.solutions.put(name, instance().oidc().single("""
				INSERT INTO solutions (id, name, owner_id, created_by, from_plateau_id, to_plateau_id)
				SELECT gen_random_uuid(), '%s', min(p.id), min(p.id), pl.succeeds, pl.id
				FROM persons p, plateaus pl WHERE pl.id = '%s' GROUP BY pl.id, pl.succeeds
				RETURNING id::text""".formatted(name + this.suffix, id(plateau))));
	}

	@Given("the landscape holds the entity {string}")
	public void entity(String name) throws Exception {
		if (this.application == null) {
			this.application = UUID.fromString(instance().oidc().single("""
					INSERT INTO metatypes (config_id, name, is_connection)
					VALUES ('00000000-0000-0000-0000-000000000001', 'Plateau Application%s', false)
					RETURNING id::text""".formatted(this.suffix)));
		}
		this.entities.put(name, Integer.parseInt(instance().oidc().single("""
				INSERT INTO entities (metatype_id, name, description, created_by)
				SELECT '%s', '%s', '', min(id) FROM persons RETURNING id::text""".formatted(this.application,
				name + this.suffix))));
	}

	@Given("{string} retires {string}")
	public void retires(String solution, String entity) throws Exception {
		delta(solution, "retiring", this.entities.get(entity), null);
	}

	@Given("{string} proposes the new entity {string}")
	public void proposes(String solution, String name) throws Exception {
		delta(solution, "new", null, name + this.suffix);
	}

	@When("the architect creates the plateau {string} with the status {string}")
	public void createWithStatus(String name, String status) throws Exception {
		this.response = send("POST", PLATEAUS, plateau(name, status));
	}

	@When("the architect creates the target plateau {string} succeeding {string} with the basis {string}")
	public void createTarget(String name, String predecessor, String basis) throws Exception {
		this.response = send("POST", PLATEAUS, targetBody(name, predecessor, basis));
		if (this.response.statusCode() == 201) {
			this.plateaus.put(name, json(this.response).path("id").asString());
		}
	}

	@When("the architect creates the transition plateau {string} succeeding {string}")
	public void createTransition(String name, String predecessor) throws Exception {
		this.response = send("POST", PLATEAUS,
				targetBody(name, predecessor, "catalog").put("status", "transition"));
	}

	@When("the architect creates a target plateau with a name of {int} characters and a description of {int} characters")
	public void createWithLengths(int name, int description) throws Exception {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		ObjectNode body = targetBody("", "baseline", "catalog").put("name", prefix + "n".repeat(name - prefix.length()));
		if (description > 0) {
			body.put("description", "d".repeat(description));
		}
		this.response = send("POST", PLATEAUS, body);
	}

	@When("the viewer creates the target plateau {string} succeeding {string}")
	public void viewerCreates(String name, String predecessor) throws Exception {
		this.response = instance().send("POST", PLATEAUS, targetBody(name, predecessor, "catalog").toString(),
				instance().viewer());
	}

	@When("the viewer lists the plateaus")
	public void viewerLists() throws Exception {
		this.response = instance().oidc().get(PLATEAUS, instance().viewer().token());
	}

	@When("the architect lists the plateaus")
	public void architectLists() throws Exception {
		this.response = instance().oidc().get(PLATEAUS, instance().architect().token());
	}

	@When("the architect changes the predecessor of {string} to {string}")
	public void changePredecessor(String name, String predecessor) throws Exception {
		this.response = send("PUT", PLATEAUS + "/" + id(name), update(name).put("succeeds", id(predecessor)));
	}

	@When("the architect changes the basis of {string} to {string}")
	public void changeBasis(String name, String basis) throws Exception {
		this.response = send("PUT", PLATEAUS + "/" + id(name), update(name).put("basis", basis));
	}

	@When("the architect renames {string} to {string}")
	public void rename(String name, String newName) throws Exception {
		this.response = send("PUT", PLATEAUS + "/" + id(name), update(name).put("name", newName + this.suffix));
	}

	@When("the architect sends the status {string} for {string}")
	public void sendStatus(String status, String name) throws Exception {
		this.response = send("PUT", PLATEAUS + "/" + id(name), update(name).put("status", status));
	}

	@When("the architect creates the solution {string} from {string} to {string}")
	public void createSolution(String name, String from, String to) throws Exception {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name + this.suffix);
		if (from != null && !from.isEmpty()) {
			body.put("fromPlateauId", id(from));
		}
		if (to != null && !to.isEmpty()) {
			body.put("toPlateauId", id(to));
		}
		this.response = instance().send("POST", "/api/v1/solutions", body.toString(), instance().architect());
	}

	@When("the architect loads the baseline of {string}")
	public void loadBaseline(String solution) throws Exception {
		this.response = instance().oidc()
			.get("/api/v1/solutions/" + this.solutions.get(solution) + "/landscape?pageSize=200&metatypeId="
					+ this.application, instance().architect().token());
	}

	@Then("the plateau answer is {int}")
	public void status(int status) {
		assertThat(this.response.statusCode()).as(this.response.body()).isEqualTo(status);
	}

	@Then("the plateau answer is 422 naming {string}")
	public void invalid(String field) throws Exception {
		status(422);
		assertThat(json(this.response).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
	}

	@Then("the plateau answer has the detail {string}")
	public void detail(String detail) throws Exception {
		assertThat(json(this.response).path("detail").asString()).isEqualTo(detail);
	}

	@Then("the plateau answer is 409 of type {string}")
	public void conflict(String type) throws Exception {
		status(409);
		assertThat(json(this.response).path("type").asString()).isEqualTo("urn:oea:problem:" + type);
	}

	@Then("the created plateau has the status {string}, the predecessor {string} and the basis {string}")
	public void createdPlateau(String status, String predecessor, String basis) throws Exception {
		status(201);
		JsonNode plateau = json(this.response);
		assertThat(plateau.path("status").asString()).isEqualTo(status);
		assertThat(plateau.path("succeeds").asString()).isEqualTo(id(predecessor));
		assertThat(plateau.path("basis").asString()).isEqualTo(basis);
	}

	@Then("exactly one baseline plateau exists")
	public void oneBaseline() throws Exception {
		assertThat(instance().oidc().single("SELECT count(*)::text FROM plateaus WHERE status = 'baseline'"))
			.isEqualTo("1");
	}

	@Then("the plateau {string} has the predecessor {string}")
	public void hasPredecessor(String name, String predecessor) throws Exception {
		assertThat(read(name).path("succeeds").asString()).isEqualTo(id(predecessor));
	}

	@Then("the plateau {string} has the basis {string}")
	public void hasBasis(String name, String basis) throws Exception {
		assertThat(read(name).path("basis").asString()).isEqualTo(basis);
	}

	@Then("the plateau {string} is still named {string}")
	public void stillNamed(String name, String expected) throws Exception {
		assertThat(read(name).path("name").asString()).isEqualTo(expected + this.suffix);
	}

	@Then("the change of {string} is audited as {string} naming {string}")
	public void audited(String name, String event, String field) throws Exception {
		assertThat(instance().oidc().single("""
				SELECT details->'fields'->>0 FROM audit.events WHERE event_type = '%s' AND target_id = '%s'
				ORDER BY id DESC LIMIT 1""".formatted(event, id(name)))).isEqualTo(field);
	}

	@Then("the baseline plateau comes first")
	public void baselineFirst() throws Exception {
		status(200);
		assertThat(json(this.response).get(0).path("id").asString()).isEqualTo(id("baseline"));
	}

	@Then("the solution is created from {string} to {string}")
	public void solutionCreated(String from, String to) throws Exception {
		status(201);
		JsonNode solution = json(this.response);
		assertThat(solution.path("fromPlateauId").asString()).isEqualTo(id(from));
		assertThat(solution.path("toPlateauId").asString()).isEqualTo(id(to));
	}

	@Then("{string} is {string} by {string}")
	public void lifecycle(String entity, String lifecycle, String solution) throws Exception {
		status(200);
		for (JsonNode item : json(this.response).path("items")) {
			if (item.path("name").asString().equals(entity + this.suffix)) {
				assertThat(item.path("lifecycle").asString()).isEqualTo(lifecycle);
				assertThat(item.path("changedBy").path("solutionId").asString()).isEqualTo(this.solutions.get(solution));
				if ("proposed".equals(lifecycle)) {
					assertThat(item.path("id").isNull()).isTrue();
				}
				return;
			}
		}
		throw new AssertionError("not in the baseline: " + entity);
	}

	@Then("{string} is active in the baseline")
	public void active(String entity) throws Exception {
		status(200);
		for (JsonNode item : json(this.response).path("items")) {
			if (item.path("name").asString().equals(entity + this.suffix)) {
				assertThat(item.path("lifecycle").asString()).isEqualTo("active");
				return;
			}
		}
		throw new AssertionError("not in the baseline: " + entity);
	}

	@Then("the baseline holds no {string}")
	public void notListed(String entity) throws Exception {
		status(200);
		json(this.response).path("items")
			.forEach((item) -> assertThat(item.path("name").asString()).isNotEqualTo(entity + this.suffix));
	}

	// us-086-plateau-go-live

	@Given("the plateau {string} succeeds the current baseline")
	public void nextPlateau(String name) throws Exception {
		baseline();
		givenTarget(name, "baseline", "catalog");
	}

	@Given("the solution {string} with status {string} targets {string}")
	public void solutionWithStatus(String name, String status, String plateau) throws Exception {
		solutionTargets(name, plateau);
		instance().oidc().update("UPDATE solutions SET status = '%s' WHERE id = '%s'".formatted(status,
				this.solutions.get(name)));
	}

	@When("the architect reads the go-live preview of {string}")
	public void readPreview(String plateau) throws Exception {
		this.response = instance().oidc().get(PLATEAUS + "/" + id(plateau) + "/go-live", instance().architect().token());
	}

	@When("the architect takes {string} live with the name {string}")
	public void goLiveWithName(String plateau, String name) throws Exception {
		this.response = send("POST", PLATEAUS + "/" + id(plateau) + "/go-live",
				JsonMapper.shared().createObjectNode().put("name", name + this.suffix));
	}

	@When("the architect takes {string} live")
	public void goLive(String plateau) throws Exception {
		goLiveWithName(plateau, plateau);
	}

	@When("the viewer takes {string} live")
	public void viewerGoesLive(String plateau) throws Exception {
		this.response = instance().send("POST", PLATEAUS + "/" + id(plateau) + "/go-live",
				JsonMapper.shared().createObjectNode().put("name", plateau + this.suffix).toString(), instance().viewer());
	}

	@When("the architect takes the solution {string} live on its own")
	public void singleGoLive(String solution) throws Exception {
		this.response = instance().send("POST", "/api/v1/solutions/" + this.solutions.get(solution) + "/go-live", null,
				instance().architect());
	}

	@Then("the preview warns that {string} is not yet approved")
	public void warnsNotApproved(String solution) throws Exception {
		status(200);
		assertThat(json(this.response).path("warnings").path("notApproved").toString())
			.isEqualTo("[\"" + solution + this.suffix + "\"]");
	}

	@Then("the preview lists the solutions {string} and {string}")
	public void previewLists(String first, String second) throws Exception {
		status(200);
		java.util.List<String> names = new java.util.ArrayList<>();
		json(this.response).path("solutions").forEach((entry) -> names.add(entry.path("name").asString()));
		assertThat(names).containsExactlyInAnyOrder(first + this.suffix, second + this.suffix);
	}

	@Then("{string} is the baseline and the old baseline is realized")
	public void newBaseline(String plateau) throws Exception {
		status(200);
		assertThat(read(plateau).path("status").asString()).isEqualTo("baseline");
		assertThat(json(this.response).path("realized").path("status").asString()).isEqualTo("realized");
	}

	@Then("{string} is still a target plateau")
	public void stillTarget(String plateau) throws Exception {
		assertThat(read(plateau).path("status").asString()).isEqualTo("target");
	}

	@Then("the solution {string} is {string}")
	public void solutionStatus(String solution, String status) throws Exception {
		assertThat(instance().oidc().single("SELECT status FROM solutions WHERE id = '%s'".formatted(
				this.solutions.get(solution)))).isEqualTo(status);
	}

	@Then("the plateau go-live deleted {string}")
	public void deletedFromCatalog(String entity) throws Exception {
		assertThat(instance().oidc().single("SELECT (deleted_at IS NOT NULL)::text FROM entities WHERE id = "
				+ this.entities.get(entity))).isEqualTo("true");
	}

	private void delta(String solution, String type, Integer entity, String name) throws Exception {
		this.clock++;
		instance().oidc().update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, metatype_id, name, created_by, created_at)
				SELECT gen_random_uuid(), '%s', '%s', %s, %s, %s, min(id), now() + interval '%d seconds' FROM persons"""
			.formatted(this.solutions.get(solution), type, (entity != null) ? entity.toString() : "NULL",
					(name != null) ? "'" + this.application + "'" : "NULL", (name != null) ? "'" + name + "'" : "NULL",
					this.clock));
	}

	private JsonNode read(String name) throws Exception {
		HttpResponse<String> read = instance().oidc().get(PLATEAUS + "/" + id(name), instance().architect().token());
		assertThat(read.statusCode()).as(read.body()).isEqualTo(200);
		return json(read);
	}

	private ObjectNode update(String name) throws Exception {
		JsonNode plateau = read(name);
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", plateau.path("name").asString());
		for (String field : new String[] { "description", "succeeds", "basis", "validFrom" }) {
			if (!plateau.path(field).isNull() && !plateau.path(field).isMissingNode()) {
				body.put(field, plateau.path(field).asString());
			}
		}
		return body;
	}

	private ObjectNode plateau(String name, String status) {
		return JsonMapper.shared().createObjectNode().put("name", name + this.suffix).put("status", status);
	}

	private ObjectNode targetBody(String name, String predecessor, String basis) throws Exception {
		return plateau(name, "target").put("succeeds", id(predecessor)).put("basis", basis);
	}

	private String id(String name) throws Exception {
		if ("baseline".equals(name) && !this.plateaus.containsKey(name)) {
			baseline();
		}
		String id = this.plateaus.get(name);
		assertThat(id).as("plateau " + name).isNotNull();
		return id;
	}

	private HttpResponse<String> send(String method, String path, ObjectNode body) throws Exception {
		return instance().send(method, path, body.toString(), instance().architect());
	}

	private static JsonNode json(HttpResponse<String> response) {
		return JsonMapper.shared().readTree(response.body());
	}

	private static RepositoryInstance instance() throws Exception {
		return MetatypeEditingSteps.sharedInstance();
	}

}

package io.oea.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Specs metamodel/property-mappings, metamodel/property-definitions, metamodel/metatype-editing,
 * metamodel/solution-extensions, solution/solution-deltas and solution/solution-go-live of
 * us-153-open-issues (US-154): the question per affected active solution, the decisions apply and
 * keep, snapshots in checks and the effective metamodel, and snapshot values at the go-live. The
 * draft solution "CRM Replacement" captured a new application with "Cost Center" CC-100 and "Tier"
 * high, and a modification of "ERP-Core" that changes only "Cost Center".
 */
@SpringBootTest
@AutoConfigureMockMvc
class SolutionImpactTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String INSTANCE = "00000000-0000-0000-0000-000000000001";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> TOKEN_FILE);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	private TestPrincipals principals;

	private TestSession kurt;

	private TestSession michael;

	private long michaelId;

	private UUID application;

	private UUID costCenter;

	private UUID tier;

	private UUID mapping;

	private int erp;

	private String solution;

	private String created;

	private String modified;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("metamodel-admin");
		this.michael = this.principals.personWithIdpRoles("solution-architect", "enterprise-architect");
		this.michaelId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.michael.sessionId());
		this.application = metatype("Application", INSTANCE, null);
		this.costCenter = property("Cost Center", "string");
		this.tier = property("Tier", "enum");
		this.jdbc.update("INSERT INTO property_enum_values (property_def_id, value) VALUES (?, 'high'), (?, 'low')",
				this.tier, this.tier);
		this.mapping = map(this.application, this.costCenter);
		map(this.application, this.tier);
		this.erp = this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, 'ERP-Core', ?) RETURNING id""",
				Integer.class, this.application, this.michaelId);
		this.jdbc.update("INSERT INTO entity_version_headers (entity_id, version, changed_by) VALUES (?, 1, ?)", this.erp,
				this.michaelId);
		this.jdbc.update(
				"INSERT INTO entity_property_values (entity_id, property_def_id, value_text) VALUES (?, ?, 'CC-1')",
				this.erp, this.costCenter);
		this.solution = solution("CRM Replacement", "draft");
		this.created = capture(this.solution, newEntity("Salesforce", value(this.costCenter, "CC-100"),
				value(this.tier, "high")))
			.path("id")
			.asString();
		this.modified = capture(this.solution, modification(this.erp, value(this.costCenter, "CC-2"))).path("id")
			.asString();
	}

	// Requirement: Metamodel changes in active solutions

	@Test
	void askedForAnAffectedSolution() {
		MvcTestResult result = removeMapping();

		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CONFLICT);
		JsonNode problem = json(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:solution-decision-required");
		assertThat(ids(problem.path("solutions"))).containsExactly(this.solution);
		assertThat(problem.path("solutions").get(0).path("deltaValues").asInt()).isEqualTo(2);
		assertThat(mappingExists()).isTrue();
	}

	@Test
	void noAffectedSolution() {
		this.jdbc.update("DELETE FROM entity_deltas WHERE solution_id = ?::uuid", this.solution);

		assertThat(removeMapping()).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mappingExists()).isFalse();
	}

	@Test
	void implementedSolutionIsNotAsked() {
		this.jdbc.update("UPDATE solutions SET status = 'implemented' WHERE id = ?::uuid", this.solution);

		assertThat(removeMapping()).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void decisionMissingForOneSolution() {
		String other = solution("ERP Extension", "draft");
		capture(other, newEntity("Workday", value(this.costCenter, "CC-7")));

		MvcTestResult result = removeMapping(this.solution + ":apply");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(ids(json(result).path("solutions"))).containsExactlyInAnyOrder(this.solution, other);
		assertThat(mappingExists()).isTrue();
		assertThat(deltaValue(this.created, this.costCenter)).isEqualTo("CC-100");
	}

	@Test
	void decisionForAnUnaffectedSolution() {
		String other = solution("ERP Extension", "draft");

		MvcTestResult result = removeMapping(this.solution + ":apply", other + ":keep");

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(mappingExists()).isTrue();
	}

	@Test
	void affectedThroughAnExtensionMetatype() {
		String other = solution("Cloud Migration", "draft");
		UUID extension = extension(other);
		metatype("Cloud Service", extension.toString(), this.application);

		MvcTestResult result = removeMapping(this.solution + ":apply");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		JsonNode solutions = json(result).path("solutions");
		assertThat(ids(solutions)).containsExactlyInAnyOrder(this.solution, other);
		for (JsonNode affected : solutions) {
			if (affected.path("id").asString().equals(other)) {
				assertThat(affected.path("extensionMetatypes").asInt()).isEqualTo(1);
				assertThat(affected.path("deltaValues").asInt()).isZero();
			}
		}
	}

	@Test
	void changeOfTheOwnExtensionIsNotAsked() {
		UUID extension = extension(this.solution);
		UUID cloud = metatype("Cloud Service", extension.toString(), null);
		UUID own = map(cloud, this.costCenter);

		MvcTestResult result = call(HttpMethod.DELETE, "/metamodel/metatypes/" + cloud + "/property-mappings/" + own,
				null, this.principals.personWithIdpRole("solution-architect"));

		assertThat(result.getResponse().getStatus()).as(() -> content(result)).isNotEqualTo(409);
	}

	// Requirement: Apply a metamodel change to a solution

	@Test
	void applyRemovesTheDeltaValues() {
		assertThat(removeMapping(this.solution + ":apply")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(deltaValue(this.created, this.costCenter)).isNull();
		assertThat(deltaValue(this.created, this.tier)).isEqualTo("high");
		assertThat(count("SELECT count(*) FROM entity_deltas WHERE id = '" + this.modified + "'")).isZero();
		assertThat(this.jdbc.queryForObject("""
				SELECT details->'solutionDecisions'->0->>'decision' FROM audit.events
				WHERE event_type = 'metamodel.mapping-deleted' AND target_id = ?""", String.class,
				this.application.toString())).isEqualTo("apply");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'solution.delta-removed' AND target_id = '"
				+ this.solution + "'")).isEqualTo(1);
	}

	// Requirement: Keep a snapshot in a solution

	@Test
	void keepStoresASnapshot() {
		assertThat(removeMapping(this.solution + ":keep")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(deltaValue(this.created, this.costCenter)).isEqualTo("CC-100");
		assertThat(count("SELECT count(*) FROM entity_property_values WHERE entity_id = " + this.erp
				+ " AND property_def_id = '" + this.costCenter + "'")).isZero();
		assertThat(count("SELECT count(*) FROM solution_metamodel_snapshots WHERE solution_id = '" + this.solution
				+ "' AND property_def_id = '" + this.costCenter + "'")).isEqualTo(1);
	}

	// Requirement: Snapshots in the checks of deltas

	@Test
	void snapshotPropertyOnlyInTheOwnSolution() {
		removeMapping(this.solution + ":keep");
		String other = solution("ERP Extension", "draft");

		String own = capture(this.solution, newEntity("Workday", value(this.costCenter, "CC-7"))).path("id").asString();
		MvcTestResult foreign = post("/solutions/" + other + "/deltas",
				newEntity("Workday", value(this.costCenter, "CC-7")));

		assertThat(deltaValue(own, this.costCenter)).isEqualTo("CC-7");
		assertThat(foreign).hasStatus(HttpStatus.CREATED);
		assertThat(deltaValue(json(foreign).path("id").asString(), this.costCenter)).isNull();
	}

	// Requirement: Snapshots in the effective metamodel of an extension

	@Test
	void snapshotInTheEffectiveMetamodel() {
		UUID extension = extension(this.solution);
		removeMapping(this.solution + ":keep");

		JsonNode metatypes = json(call(HttpMethod.GET, "/metamodel/metatypes?configId=" + extension, null, this.kurt));
		JsonNode instance = json(call(HttpMethod.GET, "/metamodel/metatypes", null, this.kurt));

		assertThat(snapshotFlags(metatypes)).containsExactly("true");
		assertThat(snapshotFlags(instance)).isEmpty();
	}

	// Requirement: Deleted enumeration values in active solutions

	@Test
	void enumerationValueUsedInADelta() {
		UUID high = this.jdbc.queryForObject(
				"SELECT id FROM property_enum_values WHERE property_def_id = ? AND value = 'high'", UUID.class, this.tier);
		String path = "/metamodel/property-definitions/" + this.tier + "/enum-values/" + high;

		assertThat(call(HttpMethod.DELETE, path, null, this.kurt)).hasStatus(HttpStatus.CONFLICT);
		assertThat(call(HttpMethod.DELETE, path + "?decision=" + this.solution + ":keep", null, this.kurt))
			.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(deltaValue(this.created, this.tier)).isEqualTo("high");
		assertThat(count("SELECT count(*) FROM solution_metamodel_snapshots WHERE enum_value = 'high' AND solution_id = '"
				+ this.solution + "'")).isEqualTo(1);
	}

	@Test
	void definitionWithASnapshot() {
		removeMapping(this.solution + ":keep");

		MvcTestResult result = call(HttpMethod.DELETE, "/metamodel/property-definitions/" + this.costCenter, null,
				this.kurt);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(json(result).path("type").asString()).isEqualTo("urn:oea:problem:in-use");
	}

	// Requirement: Base type changes in active solutions

	@Test
	void baseTypeChangeLosesAnInheritedProperty() {
		UUID cloud = metatype("Cloud Service", INSTANCE, this.application);
		capture(this.solution, JsonMapper.shared()
			.createObjectNode()
			.put("deltaType", "new")
			.put("metatypeId", cloud.toString())
			.put("name", "AWS")
			.set("properties", JsonMapper.shared().createArrayNode().add(value(this.costCenter, "CC-9"))));
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name(cloud))
			.put("configId", INSTANCE)
			.putNull("baseMetatypeId");

		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + cloud, body, this.kurt))
			.hasStatus(HttpStatus.CONFLICT);
		body.putArray("solutionDecisions").addObject().put("solutionId", this.solution).put("decision", "apply");
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + cloud, body, this.kurt)).hasStatus(HttpStatus.OK);
	}

	@Test
	void baseTypeChangeWithoutLostProperties() {
		UUID cloud = metatype("Cloud Service", INSTANCE, this.application);
		UUID platform = metatype("Platform", INSTANCE, this.application);
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name(cloud))
			.put("configId", INSTANCE)
			.put("baseMetatypeId", platform.toString());

		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + cloud, body, this.kurt)).hasStatus(HttpStatus.OK);
	}

	// Requirements: Snapshot values at the go-live, Resolve a value not in the metamodel

	@Test
	void snapshotValueBecomesAnOpenIssue() {
		removeMapping(this.solution + ":keep");
		JsonNode preview = json(call(HttpMethod.GET, "/solutions/" + this.solution + "/go-live", null, this.michael));
		assertThat(kinds(preview.path("conflicts"))).containsExactlyInAnyOrder("not-in-metamodel", "not-in-metamodel");

		JsonNode result = json(call(HttpMethod.POST, "/solutions/" + this.solution + "/go-live", null, this.michael));

		assertThat(result.path("counts").path("notInMetamodel").asInt()).isEqualTo(2);
		JsonNode issue = issueOf(result.path("openIssues"), "CC-100");
		assertThat(issue.path("kind").asString()).isEqualTo("not-in-metamodel");
		assertThat(issue.path("status").asString()).isEqualTo("open");
		assertThat(count("SELECT count(*) FROM solution_metamodel_snapshots WHERE solution_id = '" + this.solution + "'"))
			.isZero();
		int salesforce = this.jdbc.queryForObject("SELECT entity_id FROM entity_deltas WHERE id = ?::uuid",
				Integer.class, this.created);
		assertThat(count("SELECT count(*) FROM entity_property_values WHERE entity_id = " + salesforce
				+ " AND property_def_id = '" + this.costCenter + "'")).isZero();

		// never resolved automatically; a value needs the property in the metamodel again
		MvcTestResult early = resolveIssue(issue.path("id").asString(), "CC-100");
		assertThat(early).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		map(this.application, this.costCenter);
		JsonNode issues = json(resolveIssue(issue.path("id").asString(), "CC-100"));
		assertThat(issueOf(issues, "CC-100").path("resolution").asString()).isEqualTo("value");
		assertThat(this.jdbc.queryForObject(
				"SELECT value_text FROM entity_property_values WHERE entity_id = ? AND property_def_id = ?", String.class,
				salesforce, this.costCenter)).isEqualTo("CC-100");
	}

	@Test
	void snapshotValueDiscarded() {
		removeMapping(this.solution + ":keep");
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("fields").addObject().put("field", this.costCenter.toString()).put("choice", "delta");

		assertThat(post("/solutions/" + this.solution + "/deltas/" + this.created + "/resolution", body))
			.hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		((ObjectNode) body.path("fields").get(0)).put("choice", "discard");
		assertThat(post("/solutions/" + this.solution + "/deltas/" + this.created + "/resolution", body))
			.hasStatus(HttpStatus.OK);

		assertThat(deltaValue(this.created, this.costCenter)).isNull();
	}

	@Test
	void keptValueDiscardedAfterTheGoLive() {
		removeMapping(this.solution + ":keep");
		JsonNode result = json(call(HttpMethod.POST, "/solutions/" + this.solution + "/go-live", null, this.michael));
		String issue = issueOf(result.path("openIssues"), "CC-100").path("id").asString();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("resolutions").addObject().put("issueId", issue).put("remove", true);

		JsonNode issues = json(post("/solutions/" + this.solution + "/open-issues/resolution", body));

		assertThat(issueOf(issues, "CC-100").path("resolution").asString()).isEqualTo("removed");
	}

	// us-084-plateaus – Requirements: Metamodel question per plateau, Decisions by plateau only

	@Test
	void askedOncePerPlateau() {
		String plateau = targetPlateau();
		String crm = plateauSolution("CRM Plateau", plateau);
		String erpExtension = plateauSolution("ERP Plateau", plateau);
		capture(crm, newEntity("Workday", value(this.costCenter, "CC-7")));
		capture(erpExtension, newEntity("SAP", value(this.costCenter, "CC-8")));

		MvcTestResult asked = removeMapping();
		assertThat(asked).hasStatus(HttpStatus.CONFLICT);
		JsonNode solutions = json(asked).path("solutions");
		assertThat(ids(solutions)).containsExactlyInAnyOrder(this.solution, crm, erpExtension);
		solutions.forEach((solution) -> {
			if (solution.path("id").asString().equals(this.solution)) {
				assertThat(solution.path("plateau").isNull()).isTrue();
			}
			else {
				assertThat(solution.path("plateau").path("id").asString()).isEqualTo(plateau);
				assertThat(solution.path("plateau").path("name").asString()).startsWith("Target 2027");
			}
		});

		MvcTestResult decided = removeMapping("plateau:" + plateau + ":keep", this.solution + ":apply");
		assertThat(decided).as(() -> content(decided)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count("SELECT count(*) FROM solution_metamodel_snapshots WHERE solution_id IN ('" + crm + "', '"
				+ erpExtension + "')")).isEqualTo(2);
		assertThat(deltaValue(this.created, this.costCenter)).isNull();
		assertThat(this.jdbc.queryForObject("""
				SELECT details->'solutionDecisions' @> ?::jsonb FROM audit.events
				WHERE event_type = 'metamodel.mapping-deleted' ORDER BY id DESC LIMIT 1""", Boolean.class,
				"[{\"plateauId\": \"" + plateau + "\", \"decision\": \"keep\"}]")).isTrue();
	}

	@Test
	void solutionWithoutPlateauStillAsked() {
		String plateau = targetPlateau();
		capture(plateauSolution("CRM Plateau", plateau), newEntity("Workday", value(this.costCenter, "CC-7")));

		MvcTestResult result = removeMapping("plateau:" + plateau + ":apply");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(mappingExists()).isTrue();
	}

	@Test
	void decisionForASolutionOfAPlateau() {
		String plateau = targetPlateau();
		String crm = plateauSolution("CRM Plateau", plateau);
		capture(crm, newEntity("Workday", value(this.costCenter, "CC-7")));

		MvcTestResult result = removeMapping(this.solution + ":apply", crm + ":apply", "plateau:" + plateau + ":apply");

		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(json(result).path("detail").asString()).contains("decide for the plateau");
		assertThat(mappingExists()).isTrue();
	}

	@Test
	void plateauWithoutAffectedSolutions() {
		String plateau = targetPlateau();

		MvcTestResult result = removeMapping(this.solution + ":apply", "plateau:" + plateau + ":keep");

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(mappingExists()).isTrue();
	}

	@Test
	void enumerationValueDecidedByPlateau() {
		String plateau = targetPlateau();
		String crm = plateauSolution("CRM Plateau", plateau);
		String delta = capture(crm, newEntity("Workday", value(this.tier, "high"))).path("id").asString();
		UUID high = this.jdbc.queryForObject(
				"SELECT id FROM property_enum_values WHERE property_def_id = ? AND value = 'high'", UUID.class, this.tier);
		String path = "/metamodel/property-definitions/" + this.tier + "/enum-values/" + high;

		assertThat(call(HttpMethod.DELETE, path + "?decision=" + this.solution + ":keep", null, this.kurt))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(call(HttpMethod.DELETE,
				path + "?decision=" + this.solution + ":keep&decision=plateau:" + plateau + ":apply", null, this.kurt))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(deltaValue(delta, this.tier)).isNull();
	}

	@Test
	void baseTypeChangeDecidedByPlateau() {
		String plateau = targetPlateau();
		String crm = plateauSolution("CRM Plateau", plateau);
		UUID cloud = metatype("Cloud Service", INSTANCE, this.application);
		capture(crm, JsonMapper.shared()
			.createObjectNode()
			.put("deltaType", "new")
			.put("metatypeId", cloud.toString())
			.put("name", "AWS")
			.set("properties", JsonMapper.shared().createArrayNode().add(value(this.costCenter, "CC-9"))));
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", name(cloud))
			.put("configId", INSTANCE)
			.putNull("baseMetatypeId");
		var decisions = body.putArray("solutionDecisions");
		ObjectNode both = decisions.addObject().put("solutionId", crm).put("plateauId", plateau).put("decision", "apply");

		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + cloud, body, this.kurt))
			.hasStatus(HttpStatus.BAD_REQUEST);
		both.remove("solutionId");
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + cloud, body, this.kurt)).hasStatus(HttpStatus.OK);
	}

	private String targetPlateau() {
		this.jdbc.update("""
				INSERT INTO plateaus (id, name, status, created_by) VALUES (gen_random_uuid(), 'Baseline', 'baseline', ?)
				ON CONFLICT DO NOTHING""", this.michaelId);
		return this.jdbc.queryForObject("""
				INSERT INTO plateaus (id, name, status, succeeds, basis, created_by)
				SELECT gen_random_uuid(), ?, 'target', id, 'catalog', ? FROM plateaus WHERE status = 'baseline'
				RETURNING id::text""", String.class, "Target 2027 " + suffix(), this.michaelId);
	}

	private String plateauSolution(String name, String plateau) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, from_plateau_id, to_plateau_id)
				SELECT gen_random_uuid(), ?, ?, ?, succeeds, id FROM plateaus WHERE id = ?::uuid RETURNING id::text""",
				String.class, name + " " + suffix(), this.michaelId, this.michaelId, plateau);
	}

	private MvcTestResult removeMapping(String... decisions) {
		StringBuilder query = new StringBuilder();
		for (String decision : decisions) {
			query.append(query.isEmpty() ? "?" : "&").append("decision=").append(decision);
		}
		return call(HttpMethod.DELETE, "/metamodel/metatypes/" + this.application + "/property-mappings/" + this.mapping
				+ query, null, this.kurt);
	}

	private MvcTestResult resolveIssue(String issue, String value) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("resolutions").addObject().put("issueId", issue).put("value", value);
		return post("/solutions/" + this.solution + "/open-issues/resolution", body);
	}

	private JsonNode capture(String solution, ObjectNode body) {
		MvcTestResult result = post("/solutions/" + solution + "/deltas", body);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return json(result);
	}

	private MvcTestResult post(String path, ObjectNode body) {
		return call(HttpMethod.POST, path, body, this.michael);
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri("/api/v1" + path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private ObjectNode newEntity(String name, ObjectNode... values) {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("deltaType", "new")
			.put("metatypeId", this.application.toString())
			.put("name", name);
		var properties = body.putArray("properties");
		for (ObjectNode value : values) {
			properties.add(value);
		}
		return body;
	}

	private static ObjectNode modification(int entity, ObjectNode... values) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("deltaType", "modified").put("entityId", entity);
		var properties = body.putArray("properties");
		for (ObjectNode value : values) {
			properties.add(value);
		}
		return body;
	}

	private static ObjectNode value(UUID property, String value) {
		return JsonMapper.shared().createObjectNode().put("propertyDefId", property.toString()).put("value", value);
	}

	private static List<String> ids(JsonNode solutions) {
		List<String> ids = new ArrayList<>();
		solutions.forEach((solution) -> ids.add(solution.path("id").asString()));
		return ids;
	}

	private static List<String> kinds(JsonNode conflicts) {
		List<String> kinds = new ArrayList<>();
		conflicts.forEach((conflict) -> kinds.add(conflict.path("kind").asString()));
		return kinds;
	}

	private List<String> snapshotFlags(JsonNode metatypes) {
		List<String> flags = new ArrayList<>();
		for (JsonNode metatype : metatypes) {
			if (!metatype.path("id").asString().equals(this.application.toString())) {
				continue;
			}
			for (JsonNode mapping : metatype.path("propertyMappings")) {
				if (mapping.path("snapshot").asBoolean()) {
					flags.add("true");
				}
			}
		}
		return flags;
	}

	private static JsonNode issueOf(JsonNode issues, String keptValue) {
		for (JsonNode issue : issues) {
			if (keptValue.equals(issue.path("keptValue").asString())) {
				return issue;
			}
		}
		throw new AssertionError("no issue keeping " + keptValue + " in " + issues);
	}

	private boolean mappingExists() {
		return count("SELECT count(*) FROM metatype_property_mappings WHERE id = '" + this.mapping + "'") == 1;
	}

	private String deltaValue(String delta, UUID property) {
		List<String> values = this.jdbc.queryForList("""
				SELECT coalesce(after_value_text, after_value_enum) FROM entity_delta_property_values
				WHERE delta_id = ?::uuid AND property_def_id = ?""", String.class, delta, property);
		return values.isEmpty() ? null : values.get(0);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private String name(UUID metatype) {
		return this.jdbc.queryForObject("SELECT name FROM metatypes WHERE id = ?", String.class, metatype);
	}

	private String solution(String name, String status) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, status) VALUES (gen_random_uuid(), ?, ?, ?, ?)
				RETURNING id::text""", String.class, name + " " + suffix(), this.michaelId, this.michaelId, status);
	}

	private UUID extension(String solution) {
		return this.jdbc.queryForObject("""
				INSERT INTO metamodel_configurations (id, scope, parent_id, solution_id, name)
				VALUES (gen_random_uuid(), 'solution', ?::uuid, ?::uuid, ?) RETURNING id""", UUID.class, INSTANCE,
				solution, "Extension " + suffix());
	}

	private UUID metatype(String name, String config, UUID base) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection, base_metatype_id)
				VALUES (?::uuid, ?, false, ?) RETURNING id""", UUID.class, config, name + " " + suffix(), base);
	}

	private UUID property(String name, String dataType) {
		return this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, ?) RETURNING id""",
				UUID.class, name + " " + suffix(), "p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
				dataType);
	}

	private UUID map(UUID metatype, UUID property) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization') RETURNING id""", UUID.class, metatype,
				property);
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static JsonNode json(MvcTestResult result) {
		return JsonMapper.shared().readTree(content(result));
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-solution-impact").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

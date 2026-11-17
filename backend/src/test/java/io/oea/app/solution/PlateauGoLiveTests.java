package io.oea.app.solution;

import static io.oea.app.solution.PlateausTests.body;
import static io.oea.app.solution.PlateausTests.content;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Specs solution/plateau-go-live and the plateau parts of solution/solution-go-live (US-086,
 * US-087, US-088, US-157; design D2–D4 of us-086-plateau-go-live). Every successful go-live makes
 * its plateau the baseline of the instance, so each test starts from the current baseline.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlateauGoLiveTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = PlateausTests.tempDirectory() + "/setup-token";

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

	private long kurtId;

	private UUID application;

	private UUID owner;

	private String baseline;

	private String target;

	private int erp;

	private int crm;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.kurtId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.kurt.sessionId());
		this.jdbc.update("""
				INSERT INTO plateaus (id, name, status, created_by) VALUES (gen_random_uuid(), 'Baseline 2026', 'baseline', ?)
				ON CONFLICT DO NOTHING""", this.kurtId);
		this.baseline = this.jdbc.queryForObject("SELECT id::text FROM plateaus WHERE status = 'baseline'",
				String.class);
		this.target = plateau("Target 2027", this.baseline, "target");
		this.application = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, false) RETURNING id""", UUID.class,
				"Application " + suffix());
		this.owner = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type) VALUES (?, ?, 'string') RETURNING id""",
				UUID.class, "Owner " + suffix(), "p_" + suffix() + suffix());
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", this.application, this.owner);
		this.erp = entity("ERP-Core", "ERP");
		this.crm = entity("Legacy CRM", "");
	}

	// Requirement: Preview of a plateau go-live

	@Test
	void preview() {
		String crmSolution = solution("CRM Replacement", this.target, "draft");
		String erpSolution = solution("ERP Extension", this.target, "approved");
		capture(crmSolution, retiring(this.crm));
		capture(erpSolution, modified(this.erp, "Core ERP"));

		JsonNode preview = ok(call(HttpMethod.GET, path(this.target), null, this.kurt));

		assertThat(preview.path("plateauId").asString()).isEqualTo(this.target);
		assertThat(preview.path("baselineId").asString()).isEqualTo(this.baseline);
		assertThat(preview.path("counts").path("modified").asInt()).isOne();
		assertThat(preview.path("counts").path("retired").asInt()).isOne();
		assertThat(names(preview.path("solutions"))).containsExactly(name(crmSolution), name(erpSolution));
		assertThat(preview.path("warnings").path("notApproved").toString()).isEqualTo("[\"" + name(crmSolution) + "\"]");
		assertThat(preview.path("warnings").path("openIssues").asInt()).isZero();
		assertThat(status(crmSolution)).isEqualTo("draft");
	}

	@Test
	void noWarnings() {
		capture(solution("ERP Extension", this.target, "approved"), modified(this.erp, "Core ERP"));

		JsonNode warnings = ok(call(HttpMethod.GET, path(this.target), null, this.kurt)).path("warnings");

		assertThat(warnings.path("notApproved")).isEmpty();
		assertThat(warnings.path("openIssues").asInt()).isZero();
	}

	// Requirement: Only the next plateau goes live

	@Test
	void laterPlateauIsNotNext() {
		String later = plateau("Target 2028", this.target, "target");
		for (String candidate : List.of(later, this.baseline)) {
			MvcTestResult result = call(HttpMethod.POST, path(candidate), confirm("anything"), this.kurt);
			assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CONFLICT);
			assertThat(body(result).path("type").asString()).endsWith("plateau-not-next");
			assertThat(call(HttpMethod.GET, path(candidate), null, this.kurt)).hasStatus(HttpStatus.CONFLICT);
		}
		assertThat(plateauStatus(this.baseline)).isEqualTo("baseline");
	}

	// Requirement: Confirmation by name

	@ParameterizedTest
	@ValueSource(strings = { "lower", "trailing", "other" })
	void wrongName(String variant) {
		String name = plateauName(this.target);
		String sent = switch (variant) {
			case "lower" -> name.toLowerCase();
			case "trailing" -> name + " ";
			default -> "Target 2026";
		};
		MvcTestResult result = call(HttpMethod.POST, path(this.target), confirm(sent), this.kurt);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"name\"]");
		assertThat(plateauStatus(this.target)).isEqualTo("target");
	}

	// Requirements: Solutions go live with their plateau, Effects on plateaus, Audit

	@Test
	void solutionsGoLiveAndThePlateauBecomesTheBaseline() {
		String crmSolution = solution("CRM Replacement", this.target, "draft");
		String erpSolution = solution("ERP Extension", this.target, "approved");
		String archived = solution("Old Plan", this.target, "archived");
		capture(crmSolution, retiring(this.crm));
		capture(erpSolution, modified(this.erp, "Core ERP"));
		this.jdbc.update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, created_by)
				VALUES (gen_random_uuid(), ?::uuid, 'retiring', ?, ?)""", archived, this.erp, this.kurtId);
		String sibling = plateau("Phase 1", this.baseline, "transition");
		String website = solution("Website Relaunch", sibling, "draft");
		long went = events("solution.went-live");

		JsonNode result = ok(call(HttpMethod.POST, path(this.target), confirm(plateauName(this.target)), this.kurt));

		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		assertThat(result.path("baseline").path("status").asString()).isEqualTo("baseline");
		assertThat(result.path("baseline").path("validFrom").asString()).isEqualTo(today.toString());
		assertThat(result.path("realized").path("validTo").asString()).isEqualTo(today.toString());
		assertThat(result.path("solutions")).hasSize(2);
		assertThat(deleted(this.crm)).isTrue();
		assertThat(deleted(this.erp)).isFalse();
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, this.erp))
			.isEqualTo("Core ERP");
		assertThat(status(crmSolution)).isEqualTo("implemented");
		assertThat(status(erpSolution)).isEqualTo("implemented");
		assertThat(status(archived)).isEqualTo("archived");
		assertThat(plateauStatus(this.target)).isEqualTo("baseline");
		assertThat(plateauStatus(this.baseline)).isEqualTo("realized");
		assertThat(this.jdbc.queryForObject("SELECT succeeds::text FROM plateaus WHERE id = ?::uuid", String.class,
				sibling)).isEqualTo(this.target);
		assertThat(this.jdbc.queryForObject("SELECT from_plateau_id::text FROM solutions WHERE id = ?::uuid",
				String.class, website)).isEqualTo(this.target);
		// the realized plateau is read-only
		ObjectNode rename = JsonMapper.shared().createObjectNode().put("name", "Renamed " + suffix());
		assertThat(call(HttpMethod.PUT, "/api/v1/plateaus/" + this.baseline, rename, this.kurt))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		// the audit
		assertThat(events("solution.went-live")).isEqualTo(went + 2);
		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'realizedId' FROM audit.events WHERE event_type = 'plateau.went-live' AND target_id = ?""",
				String.class, this.target)).isEqualTo(this.baseline);
		assertThat(this.jdbc.queryForObject("""
				SELECT details->'fields'->>0 FROM audit.events WHERE event_type = 'plateau.updated' AND target_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, sibling)).isEqualTo("succeeds");
	}

	// Requirement: Field conflicts stop a plateau go-live

	@Test
	void conflictWithTheCatalog() {
		String erpSolution = solution("ERP Extension", this.target, "approved");
		capture(erpSolution, modified(this.erp, "Core ERP"));
		this.jdbc.update("UPDATE entities SET description = 'Changed' WHERE id = ?", this.erp);

		MvcTestResult result = call(HttpMethod.POST, path(this.target), confirm(plateauName(this.target)), this.kurt);

		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CONFLICT);
		JsonNode conflict = body(result).path("conflicts").get(0);
		assertThat(conflict.path("solutionId").asString()).isEqualTo(erpSolution);
		assertThat(conflict.path("current").asString()).isEqualTo("Changed");
		assertThat(status(erpSolution)).isEqualTo("approved");
		assertThat(plateauStatus(this.target)).isEqualTo("target");
	}

	@Test
	void conflictWithAnEarlierSolutionResolved() {
		String first = solution("CRM Replacement", this.target, "approved");
		String second = solution("ERP Extension", this.target, "approved");
		capture(first, modified(this.erp, "Core ERP"));
		String delta = capture(second, modified(this.erp, "Analytics ERP")).path("id").asString();

		JsonNode preview = ok(call(HttpMethod.GET, path(this.target), null, this.kurt));
		assertThat(preview.path("solutions").get(0).path("conflicts")).isEmpty();
		JsonNode conflict = preview.path("solutions").get(1).path("conflicts").get(0);
		assertThat(conflict.path("current").asString()).isEqualTo("Core ERP");
		assertThat(call(HttpMethod.POST, path(this.target), confirm(plateauName(this.target)), this.kurt))
			.hasStatus(HttpStatus.CONFLICT);

		// the resolution records the value at its turn as the before value
		ObjectNode resolution = JsonMapper.shared().createObjectNode();
		resolution.putArray("fields").addObject().put("field", "description").put("choice", "delta");
		ok(call(HttpMethod.POST, "/api/v1/solutions/" + second + "/deltas/" + delta + "/resolution", resolution,
				this.kurt));
		ok(call(HttpMethod.POST, path(this.target), confirm(plateauName(this.target)), this.kurt));
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, this.erp))
			.isEqualTo("Analytics ERP");
	}

	// Requirement: Atomic plateau go-live

	@Test
	void failureRollsEverythingBack() throws SQLException {
		String erpSolution = solution("ERP Extension", this.target, "approved");
		capture(erpSolution, modified(this.erp, "Core ERP"));
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			statement.execute("""
					CREATE FUNCTION fail_realize() RETURNS trigger LANGUAGE plpgsql AS $$
					BEGIN RAISE EXCEPTION 'simulated failure'; END $$""");
			statement.execute("""
					CREATE TRIGGER fail_realize BEFORE UPDATE OF status ON plateaus
					FOR EACH ROW WHEN (NEW.status = 'realized') EXECUTE FUNCTION fail_realize()""");
		}
		try {
			MvcTestResult result = call(HttpMethod.POST, path(this.target), confirm(plateauName(this.target)),
					this.kurt);
			assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(500);
		}
		catch (RuntimeException ex) {
			// the failure may surface as an unhandled exception of the request
		}
		finally {
			try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
				statement.execute("DROP TRIGGER fail_realize ON plateaus");
				statement.execute("DROP FUNCTION fail_realize()");
			}
		}
		assertThat(plateauStatus(this.baseline)).isEqualTo("baseline");
		assertThat(plateauStatus(this.target)).isEqualTo("target");
		assertThat(status(erpSolution)).isEqualTo("approved");
		assertThat(this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, this.erp))
			.isEqualTo("ERP");
	}

	// Requirement: Permission for a plateau go-live

	@Test
	void withoutPlateauGoLive() {
		this.jdbc.update("""
				INSERT INTO authorization_roles (id, name, is_builtin) VALUES ('plateau-writer', 'Plateau writer', false)
				ON CONFLICT DO NOTHING""");
		this.jdbc.update("""
				INSERT INTO role_permissions (role_id, permission_id) VALUES ('plateau-writer', 'plateau:write')
				ON CONFLICT DO NOTHING""");
		TestSession writer = this.principals.personWithIdpRole("plateau-writer");
		assertThat(call(HttpMethod.GET, path(this.target), null, writer)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.POST, path(this.target), confirm(plateauName(this.target)), writer))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(plateauStatus(this.target)).isEqualTo("target");
	}

	// Requirement: A plateau solution goes live with its plateau

	@Test
	void singleGoLiveOfAPlateauSolution() {
		String erpSolution = solution("ERP Extension", this.target, "approved");
		capture(erpSolution, modified(this.erp, "Core ERP"));
		assertThat(call(HttpMethod.GET, "/api/v1/solutions/" + erpSolution + "/go-live", null, this.kurt))
			.hasStatus(HttpStatus.OK);
		MvcTestResult result = call(HttpMethod.POST, "/api/v1/solutions/" + erpSolution + "/go-live", null, this.kurt);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(result).path("type").asString()).endsWith("plateau-solution");
		assertThat(status(erpSolution)).isEqualTo("approved");
		// a project solution still goes live on its own
		String project = solution("Website Relaunch", null, "draft");
		ok(call(HttpMethod.POST, "/api/v1/solutions/" + project + "/go-live", null, this.kurt));
	}

	// helpers

	private String path(String plateau) {
		return "/api/v1/plateaus/" + plateau + "/go-live";
	}

	private static ObjectNode confirm(String name) {
		return JsonMapper.shared().createObjectNode().put("name", name);
	}

	private JsonNode capture(String solution, ObjectNode body) {
		MvcTestResult result = call(HttpMethod.POST, "/api/v1/solutions/" + solution + "/deltas", body, this.kurt);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static ObjectNode modified(int entityId, String description) {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("deltaType", "modified")
			.put("entityId", entityId)
			.put("description", description);
		body.putArray("properties");
		return body;
	}

	private static ObjectNode retiring(int entityId) {
		return JsonMapper.shared().createObjectNode().put("deltaType", "retiring").put("entityId", entityId);
	}

	private String plateau(String name, String succeeds, String status) {
		return this.jdbc.queryForObject("""
				INSERT INTO plateaus (id, name, status, succeeds, basis, created_by)
				VALUES (gen_random_uuid(), ?, ?, ?::uuid, 'catalog', ?) RETURNING id::text""", String.class,
				name + " " + suffix(), status, succeeds, this.kurtId);
	}

	private String solution(String name, String plateau, String status) {
		return this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by, status, from_plateau_id, to_plateau_id)
				VALUES (gen_random_uuid(), ?, ?, ?, ?, (SELECT succeeds FROM plateaus WHERE id = ?::uuid), ?::uuid)
				RETURNING id::text""", String.class, name + " " + suffix(), this.kurtId, this.kurtId, status, plateau,
				plateau);
	}

	private int entity(String name, String description) {
		int id = this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, description, created_by) VALUES (?, ?, ?, ?) RETURNING id""",
				Integer.class, this.application, name + " " + suffix(), description, this.kurtId);
		this.jdbc.update("INSERT INTO entity_version_headers (entity_id, version, changed_by) VALUES (?, 1, ?)", id,
				this.kurtId);
		return id;
	}

	private String name(String solution) {
		return this.jdbc.queryForObject("SELECT name FROM solutions WHERE id = ?::uuid", String.class, solution);
	}

	private String status(String solution) {
		return this.jdbc.queryForObject("SELECT status FROM solutions WHERE id = ?::uuid", String.class, solution);
	}

	private String plateauName(String plateau) {
		return this.jdbc.queryForObject("SELECT name FROM plateaus WHERE id = ?::uuid", String.class, plateau);
	}

	private String plateauStatus(String plateau) {
		return this.jdbc.queryForObject("SELECT status FROM plateaus WHERE id = ?::uuid", String.class, plateau);
	}

	private boolean deleted(int entity) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM entities WHERE id = ?",
				Boolean.class, entity));
	}

	private long events(String type) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ?", Long.class, type);
	}

	private static List<String> names(JsonNode solutions) {
		List<String> names = new ArrayList<>();
		solutions.forEach((solution) -> names.add(solution.path("name").asString()));
		return names;
	}

	private MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static String suffix() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
	}

}

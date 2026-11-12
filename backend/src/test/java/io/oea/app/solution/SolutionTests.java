package io.oea.app.solution;

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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
 * Specs solution/solution-creation and solution/solution-overview (US-038, REQ-038; design D1–D3
 * of us-038-create-solution).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SolutionTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String SOLUTIONS = "/api/v1/solutions";

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

	private TestSession michael;

	private TestSession anna;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = this.principals.personWithIdpRole("solution-architect");
		this.anna = this.principals.personWithIdpRole("viewer");
	}

	// Requirement: Create a solution in project mode

	@Test
	void solutionCreated() {
		String name = unique("ERP Extension Logistics");
		MvcTestResult result = create(solution(name).put("description", "Goal: logistics in the ERP.\nScope: DACH."),
				this.michael);
		JsonNode created = created(result);
		String id = created.path("id").asString();
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(SOLUTIONS + "/" + id);
		assertThat(created.path("name").asString()).isEqualTo(name);
		assertThat(created.path("status").asString()).isEqualTo("draft");
		assertThat(created.path("description").asString()).isEqualTo("Goal: logistics in the ERP.\nScope: DACH.");
		assertThat(created.path("fromPlateauId").isNull()).isTrue();
		assertThat(created.path("toPlateauId").isNull()).isTrue();
		assertThat(created.path("ownerName").asString()).isEqualTo("Holder of solution-architect");
		assertThat(created.path("createdBy").path("id").asLong()).isEqualTo(created.path("ownerId").asLong());
		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'name' FROM audit.events WHERE event_type = 'solution.created' AND target_type = 'solution'
				AND target_id = ? AND event_category = 'change' AND severity = 'info'""", String.class, id)).isEqualTo(name);
		assertThat(listed(name).path("id").asString()).isEqualTo(id);
	}

	@Test
	void enterpriseArchitectCreates() {
		created(create(solution(unique("Architect")), this.principals.personWithIdpRole("enterprise-architect")));
	}

	@Test
	void nameStoredTrimmed() {
		String name = unique("CRM Replacement");
		assertThat(created(create(solution("  " + name + "  "), this.michael)).path("name").asString()).isEqualTo(name);
	}

	// Requirement: Unique solution name

	@Test
	void nameTaken() {
		String name = unique("Taken");
		created(create(solution(name), this.michael));
		long events = events();
		MvcTestResult result = create(solution(name.toLowerCase() + " "), this.michael);
		assertInvalid(result, "name");
		assertThat(body(result).path("detail").asString()).isEqualTo("Name '" + name.toLowerCase() + "' is already taken");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM solutions WHERE lower(name) = lower(?)", Integer.class,
				name))
			.isOne();
		assertThat(events()).isEqualTo(events);
	}

	// Requirement: Valid name and architecture vision

	@ParameterizedTest
	@CsvSource({ "255, 0, 201", "256, 0, 400", "0, 0, 400", "10, 2000, 201", "10, 2001, 400" })
	void limits(int nameLength, int visionLength, int status) {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		String name = (nameLength == 0) ? "" : prefix + "n".repeat(nameLength - prefix.length());
		ObjectNode body = solution(name);
		if (visionLength > 0) {
			body.put("description", "v".repeat(visionLength));
		}
		MvcTestResult result = create(body, this.michael);
		assertThat(result).as(() -> content(result)).hasStatus(status);
		if (status != 201) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM solutions WHERE name LIKE ?", Integer.class,
					prefix + "%"))
				.isZero();
		}
	}

	@Test
	void blankName() {
		long events = events();
		assertInvalid(create(solution("   "), this.michael), "name");
		assertThat(events()).isEqualTo(events);
	}

	// Requirement: Creating solutions needs solution:write

	@Test
	void withoutPermission() {
		String name = unique("Forbidden");
		assertThat(create(solution(name), this.anna)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM solutions WHERE name = ?", Integer.class, name))
			.isZero();
	}

	@Test
	void localSystemAdministrator() {
		String name = unique("Admin");
		assertThat(create(solution(name), this.principals.systemAdmin())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM solutions WHERE name = ?", Integer.class, name))
			.isZero();
	}

	// Requirement: Permission and role for solution architects

	@Test
	void builtInRoles() {
		JsonNode roles = body(call(HttpMethod.GET, "/api/v1/admin/roles", null,
				this.principals.personWithIdpRole("security-admin")));
		JsonNode architect = role(roles, "solution-architect");
		assertThat(architect.path("isBuiltin").asBoolean()).isTrue();
		assertThat(architect.path("privileged").asBoolean()).isFalse();
		assertThat(architect.path("assignableToServiceAccounts").asBoolean()).isFalse();
		assertThat(permissions(architect)).containsExactlyInAnyOrder("portal:read", "entity:read", "solution:write",
				"solution-metamodel:write");
		assertThat(permissions(role(roles, "enterprise-architect"))).contains("solution:write");
	}

	// Requirement: Read solutions

	@Test
	void listedSolutions() {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		created(create(solution(prefix + " erp Extension Logistics"), this.michael));
		created(create(solution(prefix + " CRM Replacement"), this.michael));
		MvcTestResult list = call(HttpMethod.GET, SOLUTIONS, null, this.anna);
		assertThat(list).hasStatus(HttpStatus.OK);
		List<String> names = body(list).valueStream()
			.map((solution) -> solution.path("name").asString())
			.filter((name) -> name.startsWith(prefix))
			.toList();
		assertThat(names).containsExactly(prefix + " CRM Replacement", prefix + " erp Extension Logistics");
		JsonNode listed = listed(prefix + " CRM Replacement");
		assertThat(listed.path("status").asString()).isEqualTo("draft");
		assertThat(listed.path("ownerName").asString()).isEqualTo("Holder of solution-architect");
		assertThat(listed.has("createdAt")).isTrue();
	}

	@Test
	void readSolution() {
		String id = created(create(solution(unique("Read")).put("description", "Vision"), this.michael)).path("id")
			.asString();
		MvcTestResult read = call(HttpMethod.GET, SOLUTIONS + "/" + id, null, this.anna);
		assertThat(read).hasStatus(HttpStatus.OK);
		assertThat(body(read).path("description").asString()).isEqualTo("Vision");
		assertThat(body(read).path("createdBy").path("displayName").asString()).isEqualTo("Holder of solution-architect");
	}

	@Test
	void unknownSolution() {
		assertThat(call(HttpMethod.GET, SOLUTIONS + "/" + UUID.randomUUID(), null, this.anna))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void withoutEntityRead() {
		TestSession reader = this.principals.personWithIdpRole("portal-reader");
		assertThat(call(HttpMethod.GET, SOLUTIONS, null, reader)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.GET, SOLUTIONS + "/" + UUID.randomUUID(), null, reader))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// BR-01 in the schema (design D2)

	@Test
	void plateauReferencesBothOrNone() {
		Long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		String insert = """
				INSERT INTO solutions (id, name, owner_id, created_by, from_plateau_id, to_plateau_id)
				VALUES (gen_random_uuid(), ?, ?, ?, ?, ?)""";
		org.assertj.core.api.Assertions
			.assertThatThrownBy(() -> this.jdbc.update(insert, unique("Half"), person, person, UUID.randomUUID(), null))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThat(this.jdbc.update(insert, unique("Both"), person, person, UUID.randomUUID(), UUID.randomUUID()))
			.isOne();
	}

	private MvcTestResult create(ObjectNode body, TestSession session) {
		return call(HttpMethod.POST, SOLUTIONS, body, session);
	}

	private JsonNode listed(String name) {
		MvcTestResult list = call(HttpMethod.GET, SOLUTIONS, null, this.anna);
		assertThat(list).hasStatus(HttpStatus.OK);
		return body(list).valueStream()
			.filter((solution) -> solution.path("name").asString().equals(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("not listed: " + name));
	}

	private long events() {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'solution.created'",
				Long.class);
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

	private static JsonNode role(JsonNode roles, String id) {
		return roles.valueStream()
			.filter((role) -> role.path("id").asString().equals(id))
			.findFirst()
			.orElseThrow(() -> new AssertionError("not listed: " + id));
	}

	private static List<String> permissions(JsonNode role) {
		return role.path("permissions").valueStream().map(JsonNode::asString).toList();
	}

	private static ObjectNode solution(String name) {
		return JsonMapper.shared().createObjectNode().put("name", name);
	}

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static JsonNode body(MvcTestResult result) {
		return JsonMapper.shared().readTree(content(result));
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-solutions").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

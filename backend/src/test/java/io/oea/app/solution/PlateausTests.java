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
 * Spec solution/plateaus with a baseline in place (US-084, US-085, US-089, US-155; design D2 of
 * us-084-plateaus). The states without a baseline are in {@link PlateauModeTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlateausTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	static final String PLATEAUS = "/api/v1/plateaus";

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

	private String baseline;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.michael = this.principals.personWithIdpRole("solution-architect");
		List<String> found = this.jdbc.queryForList("SELECT id::text FROM plateaus WHERE status = 'baseline'",
				String.class);
		this.baseline = found.isEmpty()
				? created(call(HttpMethod.POST, PLATEAUS, plateau("Baseline 2026-Q2", "baseline"), this.kurt)).path("id")
					.asString()
				: found.get(0);
	}

	// Requirement: Create the baseline plateau, Only one baseline plateau

	@Test
	void secondBaseline() {
		long events = events("plateau.created");
		MvcTestResult result = call(HttpMethod.POST, PLATEAUS, plateau(unique("Second"), "baseline"), this.kurt);
		assertInvalid(result, "status");
		assertThat(body(result).path("detail").asString()).isEqualTo("A baseline plateau already exists");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM plateaus WHERE status = 'baseline'", Integer.class))
			.isOne();
		assertThat(events("plateau.created")).isEqualTo(events);
	}

	@Test
	void baselineReadBack() {
		JsonNode read = body(ok(call(HttpMethod.GET, PLATEAUS + "/" + this.baseline, null, this.kurt)));
		assertThat(read.path("status").asString()).isEqualTo("baseline");
		assertThat(read.path("succeeds").isNull()).isTrue();
		assertThat(read.path("basis").isNull()).isTrue();
		assertThat(read.path("createdBy").path("name").asString()).isNotBlank();
	}

	// Requirement: Create a target or transition plateau

	@Test
	void targetCreated() {
		String name = unique("Target 2027");
		MvcTestResult result = call(HttpMethod.POST, PLATEAUS,
				target(name, this.baseline, "catalog").put("validFrom", "2027-01-01").put("description", "Cloud"),
				this.kurt);
		JsonNode created = created(result);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(PLATEAUS + "/" + created.path("id").asString());
		assertThat(created.path("status").asString()).isEqualTo("target");
		assertThat(created.path("succeeds").asString()).isEqualTo(this.baseline);
		assertThat(created.path("basis").asString()).isEqualTo("catalog");
		assertThat(created.path("validFrom").asString()).isEqualTo("2027-01-01");
		assertThat(created.path("solutionCount").asInt()).isZero();
		assertThat(this.jdbc.queryForObject("""
				SELECT details->>'status' FROM audit.events WHERE event_type = 'plateau.created' AND target_id = ?""",
				String.class, created.path("id").asString())).isEqualTo("target");
	}

	@Test
	void transitionCreated() {
		String target = id(target(unique("Target"), this.baseline, "catalog"));
		JsonNode created = created(call(HttpMethod.POST, PLATEAUS,
				target(unique("Phase 1"), target, "predecessor").put("status", "transition"), this.kurt));
		assertThat(created.path("status").asString()).isEqualTo("transition");
		assertThat(created.path("basis").asString()).isEqualTo("predecessor");
	}

	@Test
	void baselineWithPredecessor() {
		assertInvalid(call(HttpMethod.POST, PLATEAUS, plateau(unique("B"), "baseline").put("succeeds", this.baseline),
				this.kurt), "succeeds");
	}

	@ParameterizedTest
	@ValueSource(strings = { "succeeds", "basis" })
	void targetWithoutPredecessorOrBasis(String missing) {
		String name = unique("Missing");
		ObjectNode body = target(name, this.baseline, "catalog");
		body.remove(missing);
		long events = events("plateau.created");
		assertInvalid(call(HttpMethod.POST, PLATEAUS, body, this.kurt), missing);
		assertThat(count(name)).isZero();
		assertThat(events("plateau.created")).isEqualTo(events);
	}

	// Requirement: Valid plateau fields

	@ParameterizedTest
	@CsvSource({ "255, 0, 201", "256, 0, 400", "10, 2000, 201", "10, 2001, 400" })
	void limits(int nameLength, int descriptionLength, int status) {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		ObjectNode body = target(prefix + "n".repeat(nameLength - prefix.length()), this.baseline, "catalog");
		if (descriptionLength > 0) {
			body.put("description", "d".repeat(descriptionLength));
		}
		MvcTestResult result = call(HttpMethod.POST, PLATEAUS, body, this.kurt);
		assertThat(result).as(() -> content(result)).hasStatus(status);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM plateaus WHERE name LIKE ?", Integer.class,
				prefix + "%"))
			.isEqualTo((status == 201) ? 1 : 0);
	}

	@Test
	void nameTaken() {
		String name = unique("Target 2027");
		id(target(name, this.baseline, "catalog"));
		MvcTestResult result = call(HttpMethod.POST, PLATEAUS, target(" " + name.toLowerCase() + " ", this.baseline,
				"catalog"), this.kurt);
		assertInvalid(result, "name");
		assertThat(count(name)).isOne();
	}

	@Test
	void blankName() {
		assertInvalid(call(HttpMethod.POST, PLATEAUS, target("   ", this.baseline, "catalog"), this.kurt), "name");
	}

	// Requirement: Predecessor of a plateau

	@Test
	void realizedPredecessor() {
		String realized = realized(unique("Baseline 2025"));
		assertInvalid(call(HttpMethod.POST, PLATEAUS, target(unique("T"), realized, "catalog"), this.kurt),
				"succeeds");
	}

	@Test
	void unknownPredecessor() {
		assertInvalid(call(HttpMethod.POST, PLATEAUS, target(unique("T"), UUID.randomUUID().toString(), "catalog"),
				this.kurt), "succeeds");
	}

	@Test
	void cycleOfTwo() {
		String target = id(target(unique("Target 2027"), this.baseline, "catalog"));
		String phase = id(target(unique("Phase 1"), target, "catalog"));
		JsonNode before = read(target);
		long events = events("plateau.updated");
		assertInvalid(call(HttpMethod.PUT, PLATEAUS + "/" + target, update(before).put("succeeds", phase), this.kurt),
				"succeeds");
		assertThat(read(target).path("succeeds").asString()).isEqualTo(this.baseline);
		assertThat(events("plateau.updated")).isEqualTo(events);
	}

	@Test
	void cycleOfThree() {
		String first = id(target(unique("First"), this.baseline, "catalog"));
		String second = id(target(unique("Second"), first, "catalog"));
		String third = id(target(unique("Third"), second, "catalog"));
		assertInvalid(call(HttpMethod.PUT, PLATEAUS + "/" + first, update(read(first)).put("succeeds", third),
				this.kurt), "succeeds");
	}

	@Test
	void ownPredecessor() {
		String target = id(target(unique("Self"), this.baseline, "catalog"));
		assertInvalid(call(HttpMethod.PUT, PLATEAUS + "/" + target, update(read(target)).put("succeeds", target),
				this.kurt), "succeeds");
	}

	@Test
	void predecessorOfPlateauInUse() {
		String target = id(target(unique("Target 2027"), this.baseline, "catalog"));
		String other = id(target(unique("Other"), this.baseline, "catalog"));
		solutionTargeting(target);
		MvcTestResult result = call(HttpMethod.PUT, PLATEAUS + "/" + target, update(read(target)).put("succeeds", other),
				this.kurt);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(result).path("type").asString()).endsWith("plateau-in-use");
		assertThat(read(target).path("succeeds").asString()).isEqualTo(this.baseline);
		// the other fields stay editable
		ok(call(HttpMethod.PUT, PLATEAUS + "/" + target, update(read(target)).put("basis", "predecessor"), this.kurt));
	}

	@Test
	void predecessorOfUnusedPlateau() {
		String target = id(target(unique("Target 2027"), this.baseline, "catalog"));
		String phase = id(target(unique("Phase 1"), target, "catalog"));
		JsonNode changed = body(ok(call(HttpMethod.PUT, PLATEAUS + "/" + phase,
				update(read(phase)).put("succeeds", this.baseline), this.kurt)));
		assertThat(changed.path("succeeds").asString()).isEqualTo(this.baseline);
	}

	// Requirement: Basis of a plateau

	@Test
	void basisChanged() {
		String target = id(target(unique("Target 2028"), this.baseline, "predecessor"));
		JsonNode changed = body(
				ok(call(HttpMethod.PUT, PLATEAUS + "/" + target, update(read(target)).put("basis", "catalog"), this.kurt)));
		assertThat(changed.path("basis").asString()).isEqualTo("catalog");
		assertThat(changed.path("updatedAt").isNull()).isFalse();
		assertThat(this.jdbc.queryForObject("""
				SELECT details->'fields'->>0 FROM audit.events WHERE event_type = 'plateau.updated' AND target_id = ?""",
				String.class, target)).isEqualTo("basis");
	}

	@Test
	void basisRemovedOnTarget() {
		String target = id(target(unique("Target"), this.baseline, "catalog"));
		ObjectNode body = update(read(target));
		body.putNull("basis");
		assertInvalid(call(HttpMethod.PUT, PLATEAUS + "/" + target, body, this.kurt), "basis");
	}

	@Test
	void basisOnBaseline() {
		assertInvalid(call(HttpMethod.PUT, PLATEAUS + "/" + this.baseline, update(read(this.baseline)).put("basis",
				"catalog"), this.kurt), "basis");
	}

	// Requirement: Edit a plateau, Realized plateau is read-only

	@Test
	void renamed() {
		String target = id(target(unique("Target 2027"), this.baseline, "catalog"));
		String name = unique("Target 2027 Cloud");
		assertThat(body(ok(call(HttpMethod.PUT, PLATEAUS + "/" + target, update(read(target)).put("name", name),
				this.kurt))).path("name").asString()).isEqualTo(name);
	}

	@Test
	void statusChangedByEdit() {
		String target = id(target(unique("Target"), this.baseline, "catalog"));
		assertInvalid(call(HttpMethod.PUT, PLATEAUS + "/" + target, update(read(target)).put("status", "baseline"),
				this.kurt), "status");
		assertThat(read(target).path("status").asString()).isEqualTo("target");
	}

	@Test
	void realizedReadOnly() {
		String name = unique("Baseline 2025");
		String realized = realized(name);
		long events = events("plateau.updated");
		MvcTestResult result = call(HttpMethod.PUT, PLATEAUS + "/" + realized,
				update(read(realized)).put("name", unique("Renamed")), this.kurt);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("detail").asString()).isEqualTo("Realized plateau is read-only");
		assertThat(read(realized).path("name").asString()).isEqualTo(name);
		assertThat(events("plateau.updated")).isEqualTo(events);
	}

	@Test
	void unknownPlateau() {
		assertThat(call(HttpMethod.GET, PLATEAUS + "/" + UUID.randomUUID(), null, this.kurt))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(call(HttpMethod.PUT, PLATEAUS + "/" + UUID.randomUUID(), plateau("x", "target"), this.kurt))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	// Requirement: Read plateaus

	@Test
	void order() {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		String late = id(target(prefix + " Late", this.baseline, "catalog").put("validFrom", "2028-01-01"));
		String early = id(target(prefix + " Early", this.baseline, "catalog").put("validFrom", "2027-01-01")
			.put("status", "transition"));
		String undated = id(target(prefix + " Undated", this.baseline, "catalog"));
		String realized = realized(prefix + " Realized");
		String older = this.jdbc.queryForObject("""
				INSERT INTO plateaus (id, name, status, valid_to, created_by)
				VALUES (gen_random_uuid(), ?, 'realized', DATE '2020-01-01', (SELECT min(id) FROM persons))
				RETURNING id::text""", String.class, prefix + " Older");
		List<String> ids = body(ok(call(HttpMethod.GET, PLATEAUS, null, this.michael))).valueStream()
			.map((plateau) -> plateau.path("id").asString())
			.toList();
		assertThat(ids.get(0)).isEqualTo(this.baseline);
		assertThat(ids.stream().filter(List.of(late, early, undated, realized, older)::contains).toList())
			.containsExactly(early, late, undated, realized, older);
	}

	// Requirement: Permissions for plateaus

	@Test
	void creationWithoutPlateauWrite() {
		String name = unique("Forbidden");
		assertThat(call(HttpMethod.POST, PLATEAUS, target(name, this.baseline, "catalog"), this.michael))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count(name)).isZero();
		assertThat(call(HttpMethod.PUT, PLATEAUS + "/" + this.baseline, update(read(this.baseline)), this.michael))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void readingWithEachPermission() {
		this.jdbc.update("""
				INSERT INTO authorization_roles (id, name, is_builtin) VALUES ('plateau-go-live-only', 'Go-live only', false)
				ON CONFLICT DO NOTHING""");
		this.jdbc.update("""
				INSERT INTO role_permissions (role_id, permission_id) VALUES ('plateau-go-live-only', 'plateau:go-live')
				ON CONFLICT DO NOTHING""");
		for (TestSession reader : List.of(this.kurt, this.michael,
				this.principals.personWithIdpRole("plateau-go-live-only"))) {
			ok(call(HttpMethod.GET, PLATEAUS, null, reader));
			ok(call(HttpMethod.GET, PLATEAUS + "/" + this.baseline, null, reader));
		}
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		assertThat(call(HttpMethod.GET, PLATEAUS, null, viewer)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call(HttpMethod.GET, PLATEAUS + "/" + this.baseline, null, viewer)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void enterpriseArchitectHoldsPlateauWrite() {
		assertThat(this.jdbc.queryForList(
				"SELECT permission_id FROM role_permissions WHERE role_id = 'enterprise-architect'", String.class))
			.contains("plateau:write", "plateau:go-live");
		assertThat(this.jdbc.queryForList(
				"SELECT permission_id FROM role_permissions WHERE role_id = 'solution-architect'", String.class))
			.doesNotContain("plateau:write");
	}

	// helpers

	private void solutionTargeting(String plateau) {
		Long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		this.jdbc.update("""
				INSERT INTO solutions (id, name, owner_id, created_by, from_plateau_id, to_plateau_id)
				VALUES (gen_random_uuid(), ?, ?, ?, (SELECT succeeds FROM plateaus WHERE id = ?::uuid), ?::uuid)""",
				unique("Targeting"), person, person, plateau, plateau);
	}

	private String realized(String name) {
		Long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		return this.jdbc.queryForObject("""
				INSERT INTO plateaus (id, name, status, valid_to, created_by)
				VALUES (gen_random_uuid(), ?, 'realized', DATE '2026-01-01', ?) RETURNING id::text""", String.class,
				name, person);
	}

	private JsonNode read(String id) {
		return body(ok(call(HttpMethod.GET, PLATEAUS + "/" + id, null, this.kurt)));
	}

	private String id(ObjectNode body) {
		return created(call(HttpMethod.POST, PLATEAUS, body, this.kurt)).path("id").asString();
	}

	private int count(String name) {
		return this.jdbc.queryForObject("SELECT count(*) FROM plateaus WHERE lower(name) = lower(?)", Integer.class,
				name);
	}

	private long events(String type) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ?", Long.class, type);
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

	/** The fields of a plateau as an update body. */
	private static ObjectNode update(JsonNode plateau) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", plateau.path("name").asString());
		for (String field : List.of("description", "succeeds", "basis", "validFrom")) {
			if (!plateau.path(field).isNull() && !plateau.path(field).isMissingNode()) {
				body.put(field, plateau.path(field).asString());
			}
		}
		return body;
	}

	static ObjectNode plateau(String name, String status) {
		return JsonMapper.shared().createObjectNode().put("name", name).put("status", status);
	}

	static ObjectNode target(String name, String succeeds, String basis) {
		return plateau(name, "target").put("succeeds", succeeds).put("basis", basis);
	}

	static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	static MvcTestResult ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return result;
	}

	static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
	}

	static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static JsonNode body(MvcTestResult result) {
		return JsonMapper.shared().readTree(content(result));
	}

	static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-plateaus").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

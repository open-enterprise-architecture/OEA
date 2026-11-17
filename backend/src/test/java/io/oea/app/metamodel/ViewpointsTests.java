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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Spec metamodel/viewpoints (US-090, US-093, UC-12; design D1–D3 of us-090-viewpoints). */
@SpringBootTest
@AutoConfigureMockMvc
class ViewpointsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String VIEWPOINTS = "/api/v1/admin/viewpoints";

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

	private TestSession anna;

	private UUID application;

	private UUID dataObject;

	private UUID dataFlow;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.anna = this.principals.personWithIdpRole("viewer");
		this.application = metatype("Application", false, INSTANCE);
		this.dataObject = metatype("Data Object", false, INSTANCE);
		this.dataFlow = metatype("Data Flow", true, INSTANCE);
	}

	// Requirement: Create a viewpoint

	@Test
	void viewpointCreated() {
		String name = unique("Application Landscape");
		MvcTestResult result = call(HttpMethod.POST, VIEWPOINTS,
				write(name, List.of(this.application, this.dataObject), List.of(this.dataFlow)).put("description", "Apps"),
				this.kurt, null);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		JsonNode created = body(result);
		assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"1\"");
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(VIEWPOINTS + "/" + created.path("id").asString());
		assertThat(created.path("version").asInt()).isOne();
		assertThat(created.path("description").asString()).isEqualTo("Apps");
		assertThat(ids(created.path("allowedMetatypes"))).containsExactlyInAnyOrder(this.application.toString(),
				this.dataObject.toString());
		assertThat(ids(created.path("allowedRelations"))).containsExactly(this.dataFlow.toString());
		assertThat(event("viewpoint.created", created.path("id").asString())).isOne();
	}

	// Requirement: Valid viewpoint

	@ParameterizedTest
	@CsvSource({ "255, 0, 201", "256, 0, 400", "10, 1000, 201", "10, 1001, 400" })
	void limits(int nameLength, int descriptionLength, int status) {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		ObjectNode body = write(prefix + "n".repeat(nameLength - prefix.length()), List.of(this.application), List.of());
		if (descriptionLength > 0) {
			body.put("description", "d".repeat(descriptionLength));
		}
		MvcTestResult result = call(HttpMethod.POST, VIEWPOINTS, body, this.kurt, null);
		assertThat(result).as(() -> content(result)).hasStatus(status);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE name LIKE ?", Integer.class,
				prefix + "%"))
			.isEqualTo((status == 201) ? 1 : 0);
	}

	@Test
	void nameTakenAndBlank() {
		String name = unique("Taken");
		created(write(name, List.of(this.application), List.of()));
		long events = events();
		assertInvalid(call(HttpMethod.POST, VIEWPOINTS, write(" " + name.toLowerCase() + " ", List.of(this.application),
				List.of()), this.kurt, null), "name");
		assertInvalid(call(HttpMethod.POST, VIEWPOINTS, write("   ", List.of(this.application), List.of()), this.kurt,
				null), "name");
		assertThat(events()).isEqualTo(events);
	}

	@Test
	void invalidTypes() {
		UUID extension = extensionMetatype();
		String name = unique("Invalid");
		assertInvalid(post(write(name, List.of(), List.of())), "metatypeIds");
		assertInvalid(post(write(name, List.of(UUID.randomUUID()), List.of())), "metatypeIds");
		assertInvalid(post(write(name, List.of(this.dataFlow), List.of())), "metatypeIds");
		assertInvalid(post(write(name, List.of(extension), List.of())), "metatypeIds");
		assertInvalid(post(write(name, List.of(this.application), List.of(this.application))), "connectionTypeIds");
		assertInvalid(post(write(name, List.of(this.application), List.of(UUID.randomUUID()))), "connectionTypeIds");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE name = ?", Integer.class, name))
			.isZero();
	}

	// Requirement: Read viewpoints

	@Test
	void listedByNameAndRead() {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		String later = created(write(prefix + " integration View", List.of(this.application), List.of())).path("id")
			.asString();
		String earlier = created(write(prefix + " Application Landscape", List.of(this.application), List.of()))
			.path("id")
			.asString();
		JsonNode list = ok(call(HttpMethod.GET, VIEWPOINTS, null, this.anna, null));
		List<String> ids = list.valueStream()
			.map((viewpoint) -> viewpoint.path("id").asString())
			.filter(List.of(later, earlier)::contains)
			.toList();
		assertThat(ids).containsExactly(earlier, later);
		MvcTestResult read = call(HttpMethod.GET, VIEWPOINTS + "/" + earlier, null, this.anna, null);
		assertThat(read).hasStatus(HttpStatus.OK);
		assertThat(read.getResponse().getHeader("ETag")).isEqualTo("\"1\"");
		assertThat(call(HttpMethod.GET, VIEWPOINTS + "/" + UUID.randomUUID(), null, this.anna, null))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	// Requirement: Edit a viewpoint

	@Test
	void editedWithItsVersion() {
		JsonNode created = created(write(unique("Edit"), List.of(this.application), List.of()));
		String path = VIEWPOINTS + "/" + created.path("id").asString();
		ObjectNode change = write(created.path("name").asString(), List.of(this.application, this.dataObject),
				List.of(this.dataFlow))
			.put("description", "New");
		MvcTestResult updated = call(HttpMethod.PUT, path, change, this.kurt, "\"1\"");
		assertThat(updated).as(() -> content(updated)).hasStatus(HttpStatus.OK);
		assertThat(body(updated).path("version").asInt()).isEqualTo(2);
		assertThat(updated.getResponse().getHeader("ETag")).isEqualTo("\"2\"");
		assertThat(this.jdbc.queryForObject("""
				SELECT details->'fields' FROM audit.events WHERE event_type = 'viewpoint.updated' AND target_id = ?""",
				String.class, created.path("id").asString())).contains("description", "metatypeIds", "connectionTypeIds")
			.doesNotContain("\"name\"");
		// a stale version and a missing If-Match change nothing
		MvcTestResult stale = call(HttpMethod.PUT, path, change.put("description", "Other"), this.kurt, "\"1\"");
		assertThat(stale).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(body(stale).path("currentVersion").asInt()).isEqualTo(2);
		assertThat(call(HttpMethod.PUT, path, change, this.kurt, null)).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(ok(call(HttpMethod.GET, path, null, this.kurt, null)).path("description").asString()).isEqualTo("New");
	}

	// Requirement: Delete a viewpoint

	@Test
	void deletedWithItsVersion() {
		JsonNode created = created(write(unique("Delete"), List.of(this.application), List.of()));
		String path = VIEWPOINTS + "/" + created.path("id").asString();
		assertThat(call(HttpMethod.DELETE, path, null, this.kurt, "\"7\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(call(HttpMethod.DELETE, path, null, this.kurt, null)).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(call(HttpMethod.DELETE, path, null, this.kurt, "\"1\"")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(call(HttpMethod.GET, path, null, this.kurt, null)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(event("viewpoint.deleted", created.path("id").asString())).isOne();
	}

	// Requirement: Deleted metatype leaves viewpoints

	@Test
	void deletedMetatypeLeavesTheViewpoint() {
		UUID legacy = metatype("Legacy System", false, INSTANCE);
		JsonNode created = created(write(unique("Legacy"), List.of(this.application, legacy), List.of()));
		this.jdbc.update("DELETE FROM metatypes WHERE id = ?", legacy);
		JsonNode read = ok(call(HttpMethod.GET, VIEWPOINTS + "/" + created.path("id").asString(), null, this.kurt, null));
		assertThat(ids(read.path("allowedMetatypes"))).containsExactly(this.application.toString());
	}

	// Requirement: Permissions for viewpoints

	@Test
	void permissions() {
		String name = unique("Forbidden");
		assertThat(call(HttpMethod.POST, VIEWPOINTS, write(name, List.of(this.application), List.of()), this.anna, null))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoints WHERE name = ?", Integer.class, name))
			.isZero();
		TestSession reader = this.principals.personWithIdpRole("portal-reader");
		assertThat(call(HttpMethod.GET, VIEWPOINTS, null, reader, null)).hasStatus(HttpStatus.FORBIDDEN);
		ok(call(HttpMethod.GET, VIEWPOINTS, null, this.kurt, null));
	}

	// helpers

	private MvcTestResult post(ObjectNode body) {
		return call(HttpMethod.POST, VIEWPOINTS, body, this.kurt, null);
	}

	private JsonNode created(ObjectNode body) {
		MvcTestResult result = post(body);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static ObjectNode write(String name, List<UUID> metatypes, List<UUID> connections) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name);
		ArrayNode types = body.putArray("metatypeIds");
		metatypes.forEach((id) -> types.add(id.toString()));
		ArrayNode relations = body.putArray("connectionTypeIds");
		connections.forEach((id) -> relations.add(id.toString()));
		return body;
	}

	private static List<String> ids(JsonNode types) {
		return types.valueStream().map((type) -> type.path("id").asString()).toList();
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private long events() {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type LIKE 'viewpoint.%'",
				Long.class);
	}

	private int event(String type, String id) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ? AND target_id = ?",
				Integer.class, type, id);
	}

	private UUID metatype(String name, boolean connection, String config) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES (?::uuid, ?, ?) RETURNING id""", UUID.class,
				config, unique(name), connection);
	}

	private UUID extensionMetatype() {
		Long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		String solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), ?, ?, ?)
				RETURNING id::text""", String.class, unique("Extension owner"), person, person);
		String config = this.jdbc.queryForObject("""
				INSERT INTO metamodel_configurations (id, scope, parent_id, solution_id, name)
				VALUES (gen_random_uuid(), 'solution', ?::uuid, ?::uuid, ?) RETURNING id::text""", String.class,
				INSTANCE, solution, unique("Extension"));
		return metatype("Cloud Service", false, config);
	}

	private MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session, String ifMatch) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header("If-Match", ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
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

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-viewpoints").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

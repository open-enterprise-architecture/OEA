package io.oea.app.diagramview;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Specs diagram/diagram-nesting (setting "Ask for a new parent") and metamodel/viewpoints (the setting per
 * viewpoint; design D1, D2 of us-074-diagram-nesting), on an own database: the global value with its version,
 * the override of a viewpoint, the effective value in the diagram view and the audit. Kurt holds
 * {@code viewpoint:manage}, Franz only {@code entity:read}, a person without role neither.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagramSettingsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String SETTINGS = "/api/v1/admin/diagram-settings";

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

	private TestSession franz;

	private UUID component;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.anna = this.principals.personWithIdpRole("solution-architect");
		this.franz = this.principals.personWithIdpRole("viewer");
		this.component = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, abbreviation) VALUES (?::uuid, ?, 'AC') RETURNING id""",
				UUID.class, INSTANCE, "Application Component " + suffix());
		this.jdbc.update("UPDATE diagram_settings SET ask_new_parent = true, version = 1, updated_at = NULL, updated_by = NULL");
	}

	// Requirement: Setting "Ask for a new parent"

	@Test
	void theGlobalValueIsTrueByDefaultWithItsVersion() {
		for (TestSession reader : new TestSession[] { this.franz, this.kurt }) {
			MvcTestResult result = call(HttpMethod.GET, SETTINGS, null, reader, null);
			JsonNode settings = ok(result);
			assertThat(settings.path("askNewParent").asBoolean()).isTrue();
			assertThat(settings.path("version").asInt()).isOne();
			assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"1\"");
		}
	}

	@Test
	void withNeitherEntityReadNorViewpointManageTheGlobalValueIsForbidden() {
		TestSession nobody = this.principals.personWithoutRole();
		assertThat(status(call(HttpMethod.GET, SETTINGS, null, nobody, null))).isEqualTo(403);
		assertThat(status(call(HttpMethod.PUT, SETTINGS, write(false), nobody, "\"1\""))).isEqualTo(403);
	}

	@Test
	void theGlobalValueIsChangedWithTheVersionAndAudited() {
		MvcTestResult result = call(HttpMethod.PUT, SETTINGS, write(false), this.kurt, "\"1\"");
		JsonNode settings = ok(result);
		assertThat(settings.path("askNewParent").asBoolean()).isFalse();
		assertThat(settings.path("version").asInt()).isEqualTo(2);
		assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"2\"");
		assertThat(ok(call(HttpMethod.GET, SETTINGS, null, this.franz, null)).path("askNewParent").asBoolean()).isFalse();
		assertThat(events("diagram.settings-updated")).isOne();
		JsonNode details = auditDetails();
		assertThat(details.path("before").asBoolean()).isTrue();
		assertThat(details.path("after").asBoolean()).isFalse();
	}

	@Test
	void theSameValueChangesNothingAndAuditsNothing() {
		JsonNode settings = ok(call(HttpMethod.PUT, SETTINGS, write(true), this.kurt, "\"1\""));
		assertThat(settings.path("askNewParent").asBoolean()).isTrue();
		assertThat(settings.path("version").asInt()).isOne();
		assertThat(events("diagram.settings-updated")).isZero();
	}

	@Test
	void aWriteWithoutPermissionWithAStaleVersionOrWithoutOneIsRejectedAndChangesNothing() {
		assertThat(status(call(HttpMethod.PUT, SETTINGS, write(false), this.anna, "\"1\""))).isEqualTo(403);
		assertThat(status(call(HttpMethod.PUT, SETTINGS, write(false), this.franz, "\"1\""))).isEqualTo(403);
		assertThat(status(call(HttpMethod.PUT, SETTINGS, write(false), this.kurt, "\"7\""))).isEqualTo(412);
		assertThat(status(call(HttpMethod.PUT, SETTINGS, write(false), this.kurt, null))).isEqualTo(428);
		JsonNode settings = ok(call(HttpMethod.GET, SETTINGS, null, this.kurt, null));
		assertThat(settings.path("askNewParent").asBoolean()).isTrue();
		assertThat(settings.path("version").asInt()).isOne();
		assertThat(events("diagram.settings-updated")).isZero();
	}

	@Test
	void aStaleVersionWithTheSameValueIsStillRejected() {
		assertThat(status(call(HttpMethod.PUT, SETTINGS, write(true), this.kurt, "\"3\""))).isEqualTo(412);
	}

	/** global, viewpoint, effective */
	@ParameterizedTest
	@CsvSource({ "true, null, true", "false, null, false", "true, true, true", "false, true, true", "true, false, false",
			"false, false, false" })
	void theViewCarriesTheEffectiveValue(boolean global, String viewpointValue, boolean effective) {
		this.jdbc.update("UPDATE diagram_settings SET ask_new_parent = ?", global);
		UUID viewpoint = viewpoint("Cloud Security View " + suffix(), "null".equals(viewpointValue) ? null
				: Boolean.valueOf(viewpointValue));
		String diagram = diagram(viewpoint);
		JsonNode view = ok(call(HttpMethod.GET, "/api/v1/views/diagrams/" + diagram, null, this.franz, null));
		assertThat(view.path("askNewParent").asBoolean()).isEqualTo(effective);
		JsonNode stored = view.path("viewpoint").path("askNewParent");
		assertThat(stored.isNull() || stored.isMissingNode() ? "null" : stored.asString())
			.isEqualTo(viewpointValue);
	}

	// Requirement: Ask for a new parent per viewpoint

	@Test
	void anOverrideIsStoredAndNamedInTheAudit() {
		JsonNode created = ok(call(HttpMethod.POST, VIEWPOINTS, writeViewpoint("Cloud " + suffix(), null), this.kurt, null),
				201);
		String id = created.path("id").asString();
		assertThat(created.path("askNewParent").isNull() || created.path("askNewParent").isMissingNode()).isTrue();
		JsonNode updated = ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id,
				writeViewpoint(created.path("name").asString(), true), this.kurt, "\"1\""));
		assertThat(updated.path("askNewParent").asBoolean()).isTrue();
		assertThat(updated.path("version").asInt()).isEqualTo(2);
		assertThat(fields(id)).contains("askNewParent");
		JsonNode back = ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id,
				writeViewpoint(created.path("name").asString(), null), this.kurt, "\"2\""));
		assertThat(back.path("askNewParent").isNull() || back.path("askNewParent").isMissingNode()).isTrue();
		assertThat(fields(id)).contains("askNewParent");
	}

	@Test
	void anUnchangedOverrideIsNotNamedAndFalseDiffersFromNone() {
		JsonNode created = ok(call(HttpMethod.POST, VIEWPOINTS, writeViewpoint("Cloud " + suffix(), false), this.kurt, null),
				201);
		String id = created.path("id").asString();
		assertThat(created.path("askNewParent").asBoolean(true)).isFalse();
		ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, writeViewpoint(created.path("name").asString(), false),
				this.kurt, "\"1\""));
		assertThat(fields(id)).doesNotContain("askNewParent");
		// an omitted value replaces the stored one by none
		ObjectNode omitted = writeViewpoint(created.path("name").asString(), null);
		omitted.remove("askNewParent");
		JsonNode replaced = ok(call(HttpMethod.PUT, VIEWPOINTS + "/" + id, omitted, this.kurt, "\"2\""));
		assertThat(replaced.path("askNewParent").isNull() || replaced.path("askNewParent").isMissingNode()).isTrue();
		assertThat(fields(id)).contains("askNewParent");
	}

	// helpers

	private UUID viewpoint(String name, Boolean askNewParent) {
		long person = this.jdbc.queryForObject("SELECT min(id) FROM persons", Long.class);
		UUID id = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by, ask_new_parent)
				VALUES (gen_random_uuid(), ?, 'generic', ?, ?) RETURNING id""", UUID.class, name, person, askNewParent);
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", id, this.component);
		return id;
	}

	private String diagram(UUID viewpoint) {
		MvcTestResult result = call(HttpMethod.POST, "/api/v1/diagrams", JsonMapper.shared()
			.createObjectNode()
			.put("name", "Order to Cash " + suffix())
			.put("viewpointId", viewpoint.toString()), this.anna, null);
		assertThat(status(result)).as(() -> content(result)).isEqualTo(201);
		return body(result).path("id").asString();
	}

	private static ObjectNode write(boolean askNewParent) {
		return JsonMapper.shared().createObjectNode().put("askNewParent", askNewParent);
	}

	private ObjectNode writeViewpoint(String name, Boolean askNewParent) {
		ObjectNode body = JsonMapper.shared().createObjectNode().put("name", name);
		ArrayNode types = body.putArray("metatypeIds");
		types.add(this.component.toString());
		if (askNewParent == null) {
			body.putNull("askNewParent");
		}
		else {
			body.put("askNewParent", askNewParent);
		}
		return body;
	}

	private int events(String type) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ?", Integer.class, type);
	}

	private JsonNode auditDetails() {
		return JsonMapper.shared().readTree(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'diagram.settings-updated'
				ORDER BY id DESC LIMIT 1""", String.class));
	}

	private String fields(String viewpoint) {
		return this.jdbc.queryForObject("""
				SELECT details -> 'fields' FROM audit.events WHERE event_type = 'viewpoint.updated' AND target_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, viewpoint);
	}

	private static JsonNode ok(MvcTestResult result) {
		return ok(result, 200);
	}

	private static JsonNode ok(MvcTestResult result, int status) {
		assertThat(status(result)).as(() -> content(result)).isEqualTo(status);
		return body(result);
	}

	private static int status(MvcTestResult result) {
		return result.getResponse().getStatus();
	}

	private MvcTestResult call(HttpMethod method, String path, JsonNode body, TestSession session, String ifMatch) {
		var request = this.mvc.method(method)
			.uri(java.net.URI.create(path))
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

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-diagram-settings").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

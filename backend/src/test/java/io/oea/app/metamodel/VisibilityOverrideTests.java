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
import java.util.stream.IntStream;
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

/**
 * Spec metamodel/visibility-override, requirements "Override role list" and "Audit of override
 * list changes" (US-128, REQ-128, REQ-161, ADR-032 SoD-6; design D1 of us-128-override-role-list).
 * The list is shared state; every test resets it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VisibilityOverrideTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String OVERRIDE = "/api/v1/admin/property-visibility/override-roles";

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

	private TestSession max;

	private String migration;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.max = this.principals.personWithIdpRole("security-admin");
		this.jdbc.update("UPDATE authorization_roles SET overrides_property_visibility = false");
		this.migration = role("migration-support");
	}

	@Test
	void emptyByDefault() {
		assertThat(roles(ok(call(HttpMethod.GET, OVERRIDE, null, this.max)))).isEmpty();
	}

	@Test
	void addARoleAndRemoveIt() {
		assertThat(roles(ok(set(this.max, this.migration)))).containsExactly(this.migration);
		assertThat(roles(ok(call(HttpMethod.GET, OVERRIDE, null, this.max)))).containsExactly(this.migration);
		assertThat(overrides(this.migration)).isTrue();
		assertThat(overrides("viewer")).isFalse();
		// the empty list removes it
		assertThat(roles(ok(set(this.max)))).isEmpty();
		assertThat(overrides(this.migration)).isFalse();
	}

	@Test
	void withoutVisibilityConfigure() {
		ok(set(this.max, this.migration));
		for (String role : new String[] { "metamodel-admin", "system-admin" }) {
			TestSession other = this.principals.personWithIdpRole(role);
			assertThat(set(other, "viewer")).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(call(HttpMethod.GET, OVERRIDE, null, other)).hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(roles(ok(call(HttpMethod.GET, OVERRIDE, null, this.max)))).containsExactly(this.migration);
	}

	@Test
	void unknownRole() {
		ok(set(this.max, this.migration));
		MvcTestResult result = set(this.max, this.migration, "no-such-role");
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"roles\"]");
		assertThat(roles(ok(call(HttpMethod.GET, OVERRIDE, null, this.max)))).containsExactly(this.migration);
	}

	@Test
	void changeAuditedAndSelfBenefiting() {
		ok(set(this.max, this.migration));
		JsonNode first = lastEvent();
		assertThat(first.path("oldRoles")).isEmpty();
		assertThat(first.path("newRoles").toString()).isEqualTo("[\"" + this.migration + "\"]");
		assertThat(first.path("selfBenefiting").asBoolean()).isFalse();
		// Max holds security-admin
		ok(set(this.max, this.migration, "security-admin"));
		JsonNode second = lastEvent();
		assertThat(second.path("oldRoles").toString()).isEqualTo("[\"" + this.migration + "\"]");
		assertThat(second.path("selfBenefiting").asBoolean()).isTrue();
		// the same list again writes no event
		int events = events();
		ok(set(this.max, "security-admin", this.migration, this.migration));
		assertThat(events()).isEqualTo(events);
	}

	@ParameterizedTest
	@CsvSource({ "0, 200", "20, 200", "21, 400" })
	void limits(int count, int status) {
		String[] roles = IntStream.range(0, count).mapToObj((i) -> role("bulk")).toArray(String[]::new);
		assertThat(set(this.max, roles)).hasStatus(status);
	}

	private String role(String prefix) {
		String id = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
		this.jdbc.update("INSERT INTO authorization_roles (id, name) VALUES (?, ?)", id, id);
		return id;
	}

	private boolean overrides(String role) {
		JsonNode list = ok(call(HttpMethod.GET, "/api/v1/admin/roles", null, this.max));
		for (JsonNode entry : list) {
			if (entry.path("id").asString().equals(role)) {
				return entry.path("overridesPropertyVisibility").asBoolean();
			}
		}
		throw new AssertionError("no role " + role);
	}

	private int events() {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'visibility.override-changed'",
				Integer.class);
	}

	private JsonNode lastEvent() {
		return JsonMapper.shared().readTree(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'visibility.override-changed'
				ORDER BY chain_seq DESC LIMIT 1""", String.class));
	}

	private MvcTestResult set(TestSession session, String... roles) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode list = body.putArray("roles");
		for (String role : roles) {
			list.add(role);
		}
		return call(HttpMethod.PUT, OVERRIDE, body, session);
	}

	private static List<String> roles(JsonNode override) {
		List<String> roles = new java.util.ArrayList<>();
		override.path("roles").forEach((role) -> roles.add(role.asString()));
		return roles;
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private MvcTestResult call(HttpMethod method, String path, tools.jackson.databind.JsonNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
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
			return Files.createTempDirectory("oea-visibility-override").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

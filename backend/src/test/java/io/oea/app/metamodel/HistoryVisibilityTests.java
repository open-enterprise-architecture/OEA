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
 * Spec metamodel/history-visibility: reading and changing the history visibility per role and
 * metatype (design D3 of us-098-entity-history), its validation, limits, permission and audit,
 * and the more-open rule of {@link HistoryVisibility#hidden}. The settings are shared state;
 * every test resets them.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HistoryVisibilityTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String PATH = "/api/v1/admin/history-visibility";

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

	@Autowired
	private HistoryVisibility visibility;

	private TestPrincipals principals;

	private TestSession securityAdmin;

	private UUID component;

	private UUID application;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.securityAdmin = this.principals.personWithIdpRoles("security-admin", "auditor");
		this.jdbc.update("DELETE FROM history_visibility_settings");
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		this.component = metatype("Application Component " + suffix, null);
		this.application = metatype("Business Application " + suffix, this.component);
	}

	// Requirement: History visibility per role and metatype; Scope of a history visibility setting

	@Test
	void nothingConfigured() {
		assertThat(entries(ok(call("GET", null, this.securityAdmin)))).isEmpty();
		assertThat(this.visibility.hidden(List.of("viewer"), this.component)).isFalse();
	}

	@ParameterizedTest
	@CsvSource({ "'', false", "viewer, false", "'viewer,auditor', true" })
	void moreOpenSettingWins(String hiding, boolean hidden) {
		for (String role : hiding.isEmpty() ? new String[0] : hiding.split(",")) {
			ok(set(this.securityAdmin, entry(role, this.component, "hide")));
		}
		assertThat(this.visibility.hidden(List.of("viewer", "auditor"), this.component)).isEqualTo(hidden);
	}

	@Test
	void noRolesNeverHide() {
		ok(set(this.securityAdmin, entry("viewer", this.component, "hide")));
		assertThat(this.visibility.hidden(List.of(), this.component)).isFalse();
	}

	@Test
	void noInheritanceToASubtype() {
		ok(set(this.securityAdmin, entry("viewer", this.component, "hide")));
		assertThat(this.visibility.hidden(List.of("viewer"), this.component)).isTrue();
		assertThat(this.visibility.hidden(List.of("viewer"), this.application)).isFalse();
	}

	@Test
	void entriesOfADeletedRoleCountAsAbsent() {
		String role = "history-deleted-" + UUID.randomUUID().toString().substring(0, 8);
		this.jdbc.update("INSERT INTO authorization_roles (id, name) VALUES (?, ?)", role, role);
		ok(set(this.securityAdmin, entry(role, this.component, "hide")));
		assertThat(this.visibility.hidden(List.of(role), this.component)).isTrue();
		this.jdbc.update("UPDATE authorization_roles SET deleted_at = now() WHERE id = ?", role);
		assertThat(this.visibility.hidden(List.of(role), this.component)).isFalse();
		assertThat(entries(ok(call("GET", null, this.securityAdmin)))).isEmpty();
	}

	// Requirement: Reading and changing the history visibility

	@Test
	void hideAndBackToTheDefault() {
		JsonNode hidden = ok(set(this.securityAdmin, entry("viewer", this.component, "hide")));
		assertThat(entries(hidden)).containsExactly("viewer " + this.component + " hide");
		assertThat(entries(ok(call("GET", null, this.securityAdmin)))).containsExactly("viewer " + this.component + " hide");
		assertThat(entries(ok(set(this.securityAdmin, entry("viewer", this.component, "show-restricted"))))).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM history_visibility_settings", Integer.class)).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "no-such-role, , hide, entries[1].roleId", "viewer, unknown, hide, entries[1].metatypeId",
			"viewer, , invisible, entries[1].mode" })
	void invalidEntryChangesNothing(String role, String metatype, String mode, String field) {
		UUID metatypeId = "unknown".equals(metatype) ? UUID.randomUUID() : this.component;
		MvcTestResult result = set(this.securityAdmin, entry("auditor", this.component, "hide"),
				entry(role, metatypeId, mode));
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"" + field + "\"]");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM history_visibility_settings", Integer.class)).isZero();
	}

	@Test
	void aPairListedTwiceIsInvalid() {
		MvcTestResult result = set(this.securityAdmin, entry("viewer", this.component, "hide"),
				entry("viewer", this.component, "show-restricted"));
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).isEqualTo("[\"entries[1].roleId\"]");
	}

	@Test
	void withoutVisibilityConfigure() {
		TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
		assertThat(set(metamodelAdmin, entry("viewer", this.component, "hide"))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(call("GET", null, metamodelAdmin)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM history_visibility_settings", Integer.class)).isZero();
	}

	// Requirement: Size of a history visibility change

	@ParameterizedTest
	@CsvSource({ "1, 200", "500, 200", "0, 400", "501, 400" })
	void entryLimits(int count, int status) {
		List<ObjectNode> entries = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			// distinct pairs: one metatype per entry
			entries.add(entry("viewer", metatype("Limit " + UUID.randomUUID(), null), "hide"));
		}
		MvcTestResult result = set(this.securityAdmin, entries.toArray(ObjectNode[]::new));
		assertThat(result).as(() -> content(result)).hasStatus(status);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM history_visibility_settings", Integer.class))
			.isEqualTo((status == 200) ? count : 0);
	}

	// Requirement: Audit of history visibility changes

	@Test
	void changeAuditedOnlyWhenTheModeChanges() {
		int before = events();
		ok(set(this.securityAdmin, entry("viewer", this.component, "hide")));
		JsonNode event = lastEvent();
		assertThat(event.path("roleId").asString()).isEqualTo("viewer");
		assertThat(event.path("metatypeId").asString()).isEqualTo(this.component.toString());
		assertThat(event.path("metatype").asString()).startsWith("Application Component ");
		assertThat(event.path("oldMode").asString()).isEqualTo("show-restricted");
		assertThat(event.path("newMode").asString()).isEqualTo("hide");
		assertThat(event.path("selfBenefiting").asBoolean()).isFalse();
		assertThat(events()).isEqualTo(before + 1);
		// the same mode again writes no event
		ok(set(this.securityAdmin, entry("viewer", this.component, "hide"),
				entry("analyst", this.component, "show-restricted")));
		assertThat(events()).isEqualTo(before + 1);
	}

	@Test
	void selfBenefitingWhenAHeldRoleBecomesShowRestricted() {
		ok(set(this.securityAdmin, entry("auditor", this.component, "hide")));
		assertThat(lastEvent().path("selfBenefiting").asBoolean()).isFalse();
		ok(set(this.securityAdmin, entry("auditor", this.component, "show-restricted")));
		assertThat(lastEvent().path("selfBenefiting").asBoolean()).isTrue();
		// a role the actor does not hold
		ok(set(this.securityAdmin, entry("viewer", this.component, "hide")));
		ok(set(this.securityAdmin, entry("viewer", this.component, "show-restricted")));
		assertThat(lastEvent().path("selfBenefiting").asBoolean()).isFalse();
	}

	private UUID metatype(String name, UUID base) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, base_metatype_id)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?) RETURNING id""", UUID.class, name, base);
	}

	private static ObjectNode entry(String role, UUID metatypeId, String mode) {
		return JsonMapper.shared().createObjectNode().put("roleId", role).put("metatypeId", metatypeId.toString())
			.put("mode", mode);
	}

	private MvcTestResult set(TestSession session, ObjectNode... entries) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		ArrayNode list = body.putArray("entries");
		for (ObjectNode entry : entries) {
			list.add(entry);
		}
		return call("PUT", body, session);
	}

	private MvcTestResult call(String method, JsonNode body, TestSession session) {
		var request = this.mvc.method(HttpMethod.valueOf(method))
			.uri(PATH)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private static List<String> entries(JsonNode visibility) {
		List<String> entries = new ArrayList<>();
		visibility.path("entries")
			.forEach((entry) -> entries.add(entry.path("roleId").asString() + " " + entry.path("metatypeId").asString()
					+ " " + entry.path("mode").asString()));
		return entries;
	}

	private int events() {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = 'visibility.history-changed'",
				Integer.class);
	}

	private JsonNode lastEvent() {
		return JsonMapper.shared().readTree(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'visibility.history-changed'
				ORDER BY chain_seq DESC LIMIT 1""", String.class));
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
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
			return Files.createTempDirectory("oea-entity-history").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

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
import io.oea.testsupport.metamodel.TestPackages;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
 * Spec metamodel/metatype-editing "Parallel-change setting of a metatype" and "Parallel-change
 * setting kept and not inherited", and metamodel/lock-mode "Writes rejected in import-only" (US-044,
 * owner decision 2026-11-13; design D3 of us-044-conflict-warning).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ParallelChangeSettingsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CONFIG = PackageImporter.INSTANCE_CONFIGURATION.toString();

	private static final String EVENT = "metamodel.metatype-parallel-changes-changed";

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

	private TestSession admin;

	private TestSession solutionArchitect;

	private TestSession viewer;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.solutionArchitect = principals.personWithIdpRole("solution-architect");
		this.viewer = principals.personWithIdpRole("viewer");
	}

	@Test
	void everyMetatypeStartsOff() {
		UUID created = UUID.fromString(body(call(HttpMethod.POST, "/metamodel/metatypes",
				JsonMapper.shared().createObjectNode().put("name", unique("SecurityZone")).put("configId", CONFIG), null,
				this.admin)).path("id").asString());
		assertThat(body(call(HttpMethod.GET, "/metamodel/metatypes/" + created, null, null, this.admin))
			.path("showParallelChanges")
			.asBoolean(true)).isFalse();
		assertThat(showParallelChanges(metatype("imported"))).isFalse();
		assertThat(showParallelChanges(metatype("built-in"))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "built-in", "imported", "organization" })
	void administratorTurnsTheSettingOnAndOff(String scope) {
		UUID metatype = metatype(scope);
		int version = version(metatype);

		MvcTestResult on = set(metatype, true, version, this.admin);

		assertThat(on).as(() -> content(on)).hasStatus(HttpStatus.OK);
		assertThat(body(on).path("showParallelChanges").asBoolean()).isTrue();
		assertThat(on.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"" + (version + 1) + "\"");
		assertThat(version(metatype)).isEqualTo(version + 1);
		assertThat(this.jdbc.queryForObject("""
				SELECT details ->> 'before' || '>' || (details ->> 'after') FROM audit.events
				WHERE event_type = ? AND target_id = ?""", String.class, EVENT, metatype.toString()))
			.isEqualTo("false>true");

		// turning it off again is saved
		assertThat(set(metatype, false, version + 1, this.admin)).hasStatus(HttpStatus.OK);
		assertThat(showParallelChanges(metatype)).isFalse();
		assertThat(audits(metatype)).isEqualTo(2);
	}

	@Test
	void sendingTheCurrentValueIsAccepted() {
		UUID metatype = metatype("organization");
		assertThat(set(metatype, false, version(metatype), this.admin)).hasStatus(HttpStatus.OK);
		assertThat(audits(metatype)).isOne();
	}

	@Test
	void listedAsAMetatypeChange() {
		UUID metatype = metatype("organization");
		set(metatype, true, version(metatype), this.admin);
		JsonNode page = body(call(HttpMethod.GET, "/metamodel/changes?kind=metatype", null, null, this.admin));
		assertThat(page.path("items").get(0).path("eventType").asString()).isEqualTo(EVENT);
		assertThat(page.path("items").get(0).path("targetId").asString()).isEqualTo(metatype.toString());
	}

	@Test
	void withoutMetamodelAdministration() {
		UUID metatype = metatype("organization");
		int version = version(metatype);
		for (TestSession session : new TestSession[] { this.solutionArchitect, this.viewer }) {
			assertThat(set(metatype, true, version, session)).hasStatus(HttpStatus.FORBIDDEN);
		}
		assertUnchanged(metatype, version);
	}

	@Test
	void outdatedVersion() {
		UUID metatype = metatype("organization");
		int version = version(metatype);
		assertThat(set(metatype, true, version + 1, this.admin)).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(set(metatype, true, version - 1, this.admin)).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertUnchanged(metatype, version);
	}

	@Test
	void unknownMetatype() {
		assertThat(set(UUID.randomUUID(), true, 1, this.admin)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void missingValue() {
		UUID metatype = metatype("organization");
		int version = version(metatype);
		MvcTestResult result = call(HttpMethod.PUT, "/metamodel/metatypes/" + metatype + "/parallel-changes",
				JsonMapper.shared().createObjectNode(), "\"" + version + "\"", this.admin);
		assertThat(result.getResponse().getStatus()).isIn(400, 422);
		assertUnchanged(metatype, version);
	}

	@Test
	void savedWhileTheInstanceIsLocked() {
		UUID metatype = metatype("organization");
		lock("import-only");
		try {
			assertThat(set(metatype, true, version(metatype), this.admin)).hasStatus(HttpStatus.OK);
			assertThat(showParallelChanges(metatype)).isTrue();
		}
		finally {
			lock("gui-and-import");
		}
	}

	@Test
	void reimportKeepsTheSetting() {
		String packageId = "parallel-" + UUID.randomUUID().toString().substring(0, 8);
		importPackage(TestPackages.pkg(packageId, 1));
		UUID metatype = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = ?", UUID.class,
				packageId + " Type 1");
		set(metatype, true, version(metatype), this.admin);
		importPackage(TestPackages.pkg(packageId, 1).put("packageVersion", "1.1.0"));
		assertThat(showParallelChanges(metatype)).isTrue();
	}

	private UUID metatype(String scope) {
		return switch (scope) {
			case "built-in" -> this.jdbc.queryForObject("""
					INSERT INTO metatypes (config_id, name, scope) VALUES (?::uuid, ?, 'built-in') RETURNING id""",
					UUID.class, CONFIG, unique("Built-in"));
			case "imported" -> {
				String packageId = "imported-" + UUID.randomUUID().toString().substring(0, 8);
				importPackage(TestPackages.pkg(packageId, 1));
				yield this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = ?", UUID.class,
						packageId + " Type 1");
			}
			default -> UUID.fromString(body(call(HttpMethod.POST, "/metamodel/metatypes",
					JsonMapper.shared().createObjectNode().put("name", unique("Zone")).put("configId", CONFIG), null,
					this.admin)).path("id").asString());
		};
	}

	private MvcTestResult set(UUID metatype, boolean show, int version, TestSession session) {
		return call(HttpMethod.PUT, "/metamodel/metatypes/" + metatype + "/parallel-changes",
				JsonMapper.shared().createObjectNode().put("showInBaseline", show), "\"" + version + "\"", session);
	}

	private void assertUnchanged(UUID metatype, int version) {
		assertThat(showParallelChanges(metatype)).isFalse();
		assertThat(version(metatype)).isEqualTo(version);
		assertThat(audits(metatype)).isZero();
	}

	private void lock(String editMode) {
		MvcTestResult result = call(HttpMethod.PUT, "/metamodel/configurations/" + CONFIG,
				JsonMapper.shared().createObjectNode().put("name", "Instance metamodel").put("editMode", editMode), null,
				this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
	}

	private void importPackage(ObjectNode pkg) {
		MvcTestResult result = call(HttpMethod.POST, "/admin/metamodel/import", pkg, null, this.admin);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
	}

	private boolean showParallelChanges(UUID metatype) {
		return this.jdbc.queryForObject("SELECT show_parallel_changes FROM metatypes WHERE id = ?", Boolean.class,
				metatype);
	}

	private int version(UUID metatype) {
		return this.jdbc.queryForObject("SELECT version FROM metatypes WHERE id = ?", Integer.class, metatype);
	}

	private int audits(UUID metatype) {
		return count("SELECT count(*) FROM audit.events WHERE event_type = '" + EVENT + "' AND target_id = '" + metatype
				+ "'");
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, String ifMatch, TestSession session) {
		var request = this.mvc.method(method)
			.uri("/api/v1" + path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (ifMatch != null) {
			request = request.header(HttpHeaders.IF_MATCH, ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-parallel-changes").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

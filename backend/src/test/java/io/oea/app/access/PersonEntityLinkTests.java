package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
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
 * Spec identity/person-entity-link (REQ-127, ADR-032 SoD-1, DM-7; design D1–D3 of
 * us-127-connection-scoped-visibility) and migration V24. The person metatype is shared state,
 * so every test sets it itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PersonEntityLinkTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String PERSON_METATYPE = "/api/v1/admin/property-visibility/person-metatype";

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

	private TestSession security;

	private long creator;

	private UUID actor;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.security = this.principals.personWithIdpRole("security-admin");
		this.creator = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.security.sessionId());
		this.actor = metatype("Business Actor", false, null);
		ok(setPersonMetatype(this.actor, this.security));
	}

	// Migration V24

	@Test
	void uniqueLinkAndForeignKeys() {
		long first = person("first");
		long second = person("second");
		int element = entity(this.actor, "Element");
		this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", element, first);
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.jdbc.update("UPDATE persons SET entity_id = ? WHERE id = ?", element, second));
		assertThat(this.jdbc.update("UPDATE persons SET entity_id = NULL WHERE id IN (?, ?)", first, second)).isEqualTo(2);
		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.jdbc.update("UPDATE persons SET entity_id = 99999999 WHERE id = ?", first));
		assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> this.jdbc
			.update("UPDATE visibility_settings SET person_metatype_id = ?::uuid", UUID.randomUUID().toString()));
	}

	// Requirement: Person metatype

	@Test
	void setThePersonMetatype() {
		UUID team = metatype("Team", false, null);
		JsonNode set = ok(setPersonMetatype(team, this.security));
		assertThat(set.path("metatypeId").asString()).isEqualTo(team.toString());
		assertThat(set.path("metatypeName").asString()).startsWith("Team");
		assertThat(ok(get(PERSON_METATYPE, this.security)).path("metatypeId").asString()).isEqualTo(team.toString());
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'visibility.person-metatype-changed' AND details ->> 'newMetatypeId' = ?",
				Integer.class, team.toString())).isEqualTo(1);
		// unset with null
		ObjectNode none = JsonMapper.shared().createObjectNode().putNull("metatypeId");
		assertThat(ok(call(HttpMethod.PUT, PERSON_METATYPE, none, this.security)).path("metatypeId").isNull()).isTrue();
	}

	@Test
	void connectionOrUnknownMetatype() {
		assertInvalid(setPersonMetatype(metatype("Domain Assignment", true, null), this.security), "metatypeId");
		assertInvalid(setPersonMetatype(UUID.randomUUID(), this.security), "metatypeId");
	}

	@Test
	void withoutVisibilityConfigure() {
		for (String role : new String[] { "system-admin", "metamodel-admin" }) {
			TestSession other = this.principals.personWithIdpRole(role);
			assertThat(setPersonMetatype(this.actor, other)).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(get(PERSON_METATYPE, other)).hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	@Test
	void personMetatypeCannotBeDeleted() {
		UUID team = metatype("Team", false, null);
		ok(setPersonMetatype(team, this.security));
		TestSession metamodel = this.principals.personWithIdpRole("metamodel-admin");
		assertThat(call(HttpMethod.DELETE, "/api/v1/metamodel/metatypes/" + team, null, metamodel))
			.hasStatus(HttpStatus.CONFLICT);
		ok(setPersonMetatype(this.actor, this.security));
		assertThat(call(HttpMethod.DELETE, "/api/v1/metamodel/metatypes/" + team, null, metamodel))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	// Requirement: Account link

	@Test
	void linkAnAccount() {
		long michael = person("michael");
		int element = entity(this.actor, "Michael");
		JsonNode linked = ok(link(michael, element, this.security));
		assertThat(linked.path("entityId").asInt()).isEqualTo(element);
		assertThat(linked.path("entityName").asString()).isEqualTo("Michael");
		assertThat(ok(get("/api/v1/admin/persons/" + michael, this.security)).path("entityId").asInt()).isEqualTo(element);
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'person.account-linked' AND target_id = ?",
				Integer.class, Long.toString(michael))).isEqualTo(1);
		// remove the link
		assertThat(ok(link(michael, null, this.security)).path("entityId").isNull()).isTrue();
		assertThat(this.jdbc.queryForObject("SELECT entity_id FROM persons WHERE id = ?", Integer.class, michael)).isNull();
	}

	@Test
	void elementOfAnotherTypeASubtypeAndADeletedElement() {
		long michael = person("michael");
		UUID application = metatype("Application", false, null);
		assertInvalid(link(michael, entity(application, "ERP"), this.security), "entityId");
		UUID architect = metatype("Architect", false, this.actor);
		assertThat(link(michael, entity(architect, "Michael Architect"), this.security)).hasStatus(HttpStatus.OK);
		int deleted = entity(this.actor, "Gone");
		this.jdbc.update("UPDATE entities SET deleted_at = now() WHERE id = ?", deleted);
		assertInvalid(link(person("other"), deleted, this.security), "entityId");
	}

	@Test
	void elementAlreadyLinked() {
		int element = entity(this.actor, "Shared");
		ok(link(person("first"), element, this.security));
		assertThat(link(person("second"), element, this.security)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void ownAccountAndServiceAccount() {
		int element = entity(this.actor, "Self");
		assertThat(link(this.creator, element, this.security)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.jdbc.queryForObject("SELECT entity_id FROM persons WHERE id = ?", Integer.class, this.creator))
			.isNull();
		long service = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, status) VALUES ('service-account', 'Scanner', 'active') RETURNING id""",
				Long.class);
		assertInvalid(link(service, element, this.security), "personId");
	}

	@Test
	void withoutPersonMetatypeAndAsSystemAdministrator() {
		long michael = person("michael");
		int element = entity(this.actor, "Michael");
		ObjectNode none = JsonMapper.shared().createObjectNode().putNull("metatypeId");
		ok(call(HttpMethod.PUT, PERSON_METATYPE, none, this.security));
		assertInvalid(link(michael, element, this.security), "entityId");
		ok(setPersonMetatype(this.actor, this.security));
		// person:manage of the system administration suffices
		assertThat(link(michael, element, this.principals.personWithIdpRole("system-admin"))).hasStatus(HttpStatus.OK);
		assertThat(link(person("other"), entity(this.actor, "Other"), this.principals.personWithIdpRole("enterprise-architect")))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	private MvcTestResult setPersonMetatype(UUID metatype, TestSession session) {
		return call(HttpMethod.PUT, PERSON_METATYPE,
				JsonMapper.shared().createObjectNode().put("metatypeId", metatype.toString()), session);
	}

	private MvcTestResult link(long person, Integer element, TestSession session) {
		ObjectNode body = JsonMapper.shared().createObjectNode();
		if (element == null) {
			body.putNull("entityId");
		}
		else {
			body.put("entityId", element);
		}
		return call(HttpMethod.PUT, "/api/v1/admin/persons/" + person + "/entity-link", body, session);
	}

	private long person(String name) {
		return this.jdbc.queryForObject("INSERT INTO persons (name, email, status) VALUES (?, ?, 'active') RETURNING id",
				Long.class, name, name + "-" + UUID.randomUUID() + "@example.org");
	}

	private UUID metatype(String name, boolean connection, UUID base) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection, base_metatype_id)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?, ?) RETURNING id""", UUID.class,
				name + " " + UUID.randomUUID().toString().substring(0, 8), connection, base);
	}

	private int entity(UUID metatype, String name) {
		return this.jdbc.queryForObject("INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id",
				Integer.class, metatype, name, this.creator);
	}

	private MvcTestResult get(String path, TestSession session) {
		return call(HttpMethod.GET, path, null, session);
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(result).path("invalidFields").toString()).contains("\"" + field + "\"");
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
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-person-entity-link").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

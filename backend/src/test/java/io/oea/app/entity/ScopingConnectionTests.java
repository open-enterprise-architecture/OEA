package io.oea.app.entity;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec repository/connection-write, requirement "Connections of a scoping type" (REQ-161 AC1,
 * AC4; design D4 of us-124-property-visibility). Each test uses its own metatypes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScopingConnectionTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

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

	private TestSession architect;

	private UUID assignment;

	private UUID dataFlow;

	private UUID classification;

	private int person;

	private int application;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = this.principals.personWithIdpRole("enterprise-architect");
		long architectId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.architect.sessionId());
		UUID element = metatype("Element", false);
		this.assignment = metatype("Domain Assignment", true);
		this.dataFlow = metatype("Data Flow", true);
		this.person = entity(element, "Michael", architectId);
		this.application = entity(element, "ERP Warehouse", architectId);
		this.classification = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, ?, 'string', 'connection-scoped', ?) RETURNING id""", UUID.class,
				"Security Classification " + suffix(), "p_" + suffix().replace("-", ""), this.assignment);
	}

	@Test
	void architectCreatesAScopingConnection() {
		int denials = denials(this.architect);
		assertThat(createConnection(this.assignment, this.architect)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(connections(this.assignment)).isZero();
		assertThat(denials(this.architect)).isEqualTo(denials + 1);
	}

	@Test
	void scopingConnectionInABatch() {
		TestSession writer = this.principals.personWithIdpRole("integration-writer");
		String element = this.jdbc.queryForObject("SELECT metatype_id::text FROM entities WHERE id = ?", String.class,
				this.person);
		String name = "Batch Entity " + suffix();
		ObjectNode body = JsonMapper.shared().createObjectNode();
		body.putArray("entities").addObject().put("name", name).put("metatypeId", element);
		body.putArray("connections").add(connection(this.assignment));
		MvcTestResult result = this.mvc.post()
			.uri("/api/v1/entities/batch")
			.cookie(new Cookie(SessionCookie.NAME, writer.token()))
			.header(CsrfOriginFilter.HEADER, writer.csrfToken())
			.contentType("application/json")
			.content(body.toString())
			.exchange();
		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE name = ?", Integer.class, name)).isZero();
		assertThat(connections(this.assignment)).isZero();
	}

	@Test
	void withThePermission() {
		TestSession both = this.principals.personWithIdpRoles("enterprise-architect", "security-admin");
		assertThat(createConnection(this.assignment, both)).hasStatus(HttpStatus.CREATED);
		assertThat(connections(this.assignment)).isEqualTo(1);
	}

	@Test
	void typeNoLongerUsedForScoping() {
		this.jdbc.update("""
				UPDATE property_definitions SET visibility_mode = 'public', scoping_connection_type_id = NULL WHERE id = ?""",
				this.classification);
		assertThat(createConnection(this.assignment, this.architect)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void connectionOfAnotherMetatype() {
		assertThat(createConnection(this.dataFlow, this.architect)).hasStatus(HttpStatus.CREATED);
		assertThat(connections(this.dataFlow)).isEqualTo(1);
	}

	private MvcTestResult createConnection(UUID metatype, TestSession session) {
		return this.mvc.post()
			.uri("/api/v1/connections")
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(connection(metatype).toString())
			.exchange();
	}

	private ObjectNode connection(UUID metatype) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("connectionMetatypeId", metatype.toString())
			.put("sourceId", this.person)
			.put("targetId", this.application);
	}

	private int connections(UUID metatype) {
		return this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE metatype_id = ?", Integer.class, metatype);
	}

	private int denials(TestSession session) {
		return this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events e JOIN sessions s ON s.person_id = e.actor_person_id
				WHERE e.event_type = 'authz.denied' AND s.id = ?""", Integer.class, session.sessionId());
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, ?)
				RETURNING id""", UUID.class, name + " " + suffix(), connection);
	}

	private int entity(UUID metatype, String name, long createdBy) {
		return this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class,
				metatype, name, createdBy);
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-scoping-connections").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

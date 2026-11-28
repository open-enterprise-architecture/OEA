package io.oea.app.organization;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/**
 * Spec repository/connection-write, requirement "Connections of connections one level deep"
 * (us-065-lineage-model-and-query D1), through {@code createConnection} and the batch: a connection at a connection
 * that itself connects a connection is rejected whatever the endpoint lists allow, one level stays allowed. Own
 * database with the TOGAF and the BPMN package; the tests rely on their order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConnectionDepthTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static int carries;

	private static int flow;

	private static int object;

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

	private Api api;

	private TestSession admin;

	private TestSession anna;

	@BeforeEach
	void instance() {
		this.api = new Api(this.mvc);
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.anna = principals.personWithIdpRole("enterprise-architect");
	}

	@Test
	@Order(1)
	void theFixtureIsADataFlowThatCarriesADataObject() {
		for (String id : List.of("oea-starter-togaf-classic", "oea-starter-bpmn")) {
			this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + id + "/import", null, this.admin));
		}
		int source = this.api.entity(this.anna, metatype("Application Component"), "SAP ERP");
		int target = this.api.entity(this.anna, metatype("Application Component"), "DWH");
		object = this.api.entity(this.anna, metatype("Data Object"), "Customer Master");
		flow = this.api.connection(this.anna, metatype("Data Flow"), source, target);
		// one level: "Carries Data" starts at the data flow between two elements
		carries = this.api.connection(this.anna, metatype("Carries Data"), flow, object);
		assertThat(this.jdbc.queryForObject("SELECT source_entity_id FROM entity_connections WHERE entity_id = ?",
				Integer.class, carries)).isEqualTo(flow);
	}

	/** channel, the side of the carries-data connection that is used, the field named in the error */
	@ParameterizedTest
	@CsvSource({ "createConnection, source, sourceId", "createConnection, target, targetId", "batch, source, sourceId",
			"batch, target, targetId" })
	@Order(2)
	void aConnectionAtACarryingConnectionIsRejectedNamingTheEndpointAndStoresNothing(String channel, String side,
			String field) {
		int element = this.api.entity(this.anna, metatype("Application Component"), "Element " + Api.id());
		int from = "source".equals(side) ? carries : element;
		int to = "source".equals(side) ? element : carries;
		int connections = count("SELECT count(*) FROM entity_connections");
		int entities = count("SELECT count(*) FROM entities");
		MvcTestResult result = write(channel, metatype("Realized By"), from, to);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		JsonNode errors = Api.body(result).path("errors");
		assertThat(errors.valueStream().map((error) -> error.path("code").asString() + "@" + error.path("field").asString()))
			.contains("CONNECTION_DEPTH_EXCEEDED@" + (("batch".equals(channel)) ? "connections[0]." + field : field));
		assertThat(count("SELECT count(*) FROM entity_connections")).isEqualTo(connections);
		assertThat(count("SELECT count(*) FROM entities")).isEqualTo(entities);
	}

	/** channel, the source and target types of the connection, between elements or at a data flow */
	@ParameterizedTest
	@CsvSource({ "createConnection", "batch" })
	@Order(3)
	void oneLevelStaysAllowedForEveryConnectionTypeWhoseListsAllowIt(String channel) {
		// "Realized By", whose lists are empty, at a data flow between two elements (a connection of a connection)
		int element = this.api.entity(this.anna, metatype("Application Component"), "Realizer " + Api.id());
		MvcTestResult atFlow = write(channel, metatype("Realized By"), flow, element);
		assertThat(Api.status(atFlow)).as(() -> Api.content(atFlow)).isIn(200, 201);
		// "Carries Data" from the data flow between elements to another data object
		int other = this.api.entity(this.anna, metatype("Data Object"), "Invoice " + Api.id());
		MvcTestResult carries = write(channel, metatype("Carries Data"), flow, other);
		assertThat(Api.status(carries)).as(() -> Api.content(carries)).isIn(200, 201);
	}

	@Test
	@Order(4)
	void anExistingConnectionAtAnotherConnectionIsNotCheckedAgain() {
		// a stored connection that is already two levels deep (created before the rule) can be read and deleted
		int element = this.api.entity(this.anna, metatype("Application Component"), "Old " + Api.id());
		int deep = this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?::uuid, 'Deep', (SELECT min(id) FROM persons))
				RETURNING id""", Integer.class, metatype("Realized By"));
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				deep, carries, element);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_connections WHERE entity_id = ?", Integer.class,
				deep)).isOne();
		// a new connection at the old one is rejected: it connects a connection that connects a connection
		MvcTestResult result = write("createConnection", metatype("Realized By"), deep, element);
		assertThat(Api.status(result)).isEqualTo(422);
		assertThat(Api.content(result)).contains("CONNECTION_DEPTH_EXCEEDED");
	}

	// helpers

	private MvcTestResult write(String channel, String type, int source, int target) {
		if ("batch".equals(channel)) {
			return this.api.writeConnection(this.anna, type, source, target);
		}
		return this.api.post("/api/v1/connections",
				Api.object().put("connectionMetatypeId", type).put("sourceId", source).put("targetId", target), this.anna);
	}

	private String metatype(String name) {
		return this.jdbc.queryForObject("SELECT id::text FROM metatypes WHERE name = ?", String.class, name);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-connection-depth").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

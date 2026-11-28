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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Specs metamodel/starter-packages (BPMN starter package 1.2.0, re-import of 1.0.0, update) at API level
 * (us-083-bpmn-data-objects D1, D7): the version 1.0.0 is rebuilt from the shipped package, imported, used by a
 * diagram and updated; the instance has a database of its own and the tests rely on their order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BpmnPackageUpdateTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String BPMN = "oea-starter-bpmn";

	private static final String TOGAF = "oea-starter-togaf-classic";

	private static final Set<String> NEW_METATYPES = Set.of("BPMN Data Object", "BPMN Data Store",
			"Data Input Association", "Data Output Association", "Realized By");

	private static final Set<String> NEW_PROPERTIES = Set.of("Collection", "Transformation type", "Affected attributes",
			"Condition");

	private static final Map<String, String> oldIds = new HashMap<>();

	private static String viewpoint;

	private static String diagram;

	private static int diagramVersion;

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

	private TestSession franz;

	@BeforeEach
	void instance() {
		this.api = new Api(this.mvc);
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.admin = principals.personWithIdpRole("metamodel-admin");
		this.anna = principals.personWithIdpRole("enterprise-architect");
		this.franz = principals.personWithIdpRole("viewer");
	}

	@Test
	@Order(1)
	void theShippedPackageHas28ElementTypes8ConnectionTypesAnd6PropertiesAndIsNotImported() {
		JsonNode entry = shipped();
		assertThat(entry.path("version").asString()).isEqualTo("1.2.0");
		assertThat(entry.path("metatypeCount").asInt()).isEqualTo(36);
		assertThat(entry.path("propertyDefinitionCount").asInt()).isEqualTo(6);
		assertThat(entry.path("imported").asBoolean()).isFalse();
		assertThat(entry.path("importedVersion").isNull()).isTrue();
		assertThat(entry.path("metatypeNames").valueStream().map(JsonNode::asString)).containsAll(NEW_METATYPES);
	}

	@Test
	@Order(2)
	void theImportedVersionIs100AfterImportingTheOldPackage() {
		this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + TOGAF + "/import", null, this.admin));
		JsonNode outcome = this.api.ok(this.api.post("/api/v1/metamodel/import", oldPackage(), this.admin));
		assertThat(outcome.path("created").asInt()).isEqualTo(31);
		JsonNode entry = shipped();
		assertThat(entry.path("imported").asBoolean()).isTrue();
		assertThat(entry.path("importedVersion").asString()).isEqualTo("1.0.0");
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = ?")).isEqualTo(31);
		this.jdbc.query("SELECT name, id::text FROM metatypes WHERE source_package_id = ?", (rs) -> {
			oldIds.put(rs.getString(1), rs.getString(2));
		}, BPMN);
		viewpoint = this.jdbc.queryForObject("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'",
				String.class);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM viewpoint_metatypes WHERE viewpoint_id = ?::uuid",
				Integer.class, viewpoint)).isEqualTo(26);
	}

	@Test
	@Order(3)
	void aDiagramOfTheOldViewpointHasShapes() {
		diagram = this.api.created(this.api.post("/api/v1/diagrams",
				Api.object().put("name", "Order handling").put("viewpointId", viewpoint), this.anna)).path("id").asString();
		ObjectNode body = Api.object();
		ArrayNode shapes = body.putArray("shapes");
		for (String type : List.of("User Task", "Exclusive Gateway")) {
			int entity = this.api.entity(this.anna, oldIds.get(type), type + " one");
			shapes.add(Api.object().put("id", Api.id()).put("entityId", entity).put("x", 10).put("y", 10).put("width", 90)
				.put("height", 60));
		}
		this.api.ok(this.api.put("/api/v1/views/diagrams/" + diagram, body, this.anna, "\"1\""));
		diagramVersion = this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, diagram);
	}

	@Test
	@Order(4)
	void theUpdateAddsTheNewTypesAndKeepsTheOldOnesTheViewpointAndTheDiagram() {
		int entities = count("SELECT count(*) FROM entities");
		int entityVersions = this.jdbc.queryForObject("SELECT coalesce(sum(version), 0) FROM entities", Integer.class);
		JsonNode outcome = this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + BPMN + "/import", null,
				this.admin));
		assertThat(outcome.path("created").asInt()).isEqualTo(5);
		// the IDs of 1.0.0 are unchanged
		this.jdbc.query("SELECT name, id::text FROM metatypes WHERE source_package_id = ?", (rs) -> {
			if (!NEW_METATYPES.contains(rs.getString(1))) {
				assertThat(rs.getString(2)).as(rs.getString(1)).isEqualTo(oldIds.get(rs.getString(1)));
			}
		}, BPMN);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = ?")).isEqualTo(36);
		assertThat(this.jdbc.queryForObject("SELECT id::text FROM viewpoints WHERE name = 'Process Model (BPMN 2.0)'",
				String.class)).isEqualTo(viewpoint);
		JsonNode vp = this.api.ok(this.api.get("/api/v1/admin/viewpoints/" + viewpoint, this.anna));
		assertThat(vp.path("allowedMetatypes")).hasSize(28);
		assertThat(vp.path("allowedRelations")).hasSize(8);
		assertThat(vp.path("allowedMetatypes").valueStream().map((type) -> type.path("name").asString()))
			.contains("BPMN Data Object", "BPMN Data Store");
		for (String name : NEW_PROPERTIES) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM property_definitions WHERE name = ?", Integer.class,
					name)).isOne();
		}
		// the diagram, its shapes and the entities are unchanged
		assertThat(this.jdbc.queryForObject("SELECT version FROM diagrams WHERE id = ?::uuid", Integer.class, diagram))
			.isEqualTo(diagramVersion);
		JsonNode view = this.api.ok(this.api.get("/api/v1/views/diagrams/" + diagram, this.franz));
		assertThat(view.path("shapes")).hasSize(2);
		assertThat(view.path("conflictCount").asInt()).isZero();
		assertThat(count("SELECT count(*) FROM entities")).isEqualTo(entities);
		assertThat(this.jdbc.queryForObject("SELECT coalesce(sum(version), 0) FROM entities", Integer.class))
			.isEqualTo(entityVersions);
		JsonNode entry = shipped();
		assertThat(entry.path("importedVersion").asString()).isEqualTo("1.2.0");
		assertThat(entry.path("imported").asBoolean()).isTrue();
	}

	@Test
	@Order(5)
	void anImportOfTheSameVersionAgainCreatesNothing() {
		assertThat(this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + BPMN + "/import", null, this.admin))
			.path("created")
			.asInt()).isZero();
		assertThat(shipped().path("importedVersion").asString()).isEqualTo("1.2.0");
	}

	@Test
	@Order(6)
	void theListNeedsMetamodelWrite() {
		assertThat(Api.status(this.api.get("/api/v1/metamodel/starter-packages", this.franz))).isEqualTo(403);
	}

	/** association, source type, target type, expected status */
	@ParameterizedTest
	@CsvSource({ "Data Input Association, Data Object, User Task, 200",
			"Data Input Association, Data Object, Sub-Process, 422",
			"Data Input Association, Data Object, Exclusive Gateway, 422",
			"Data Input Association, BPMN Data Store, Pool, 422",
			"Data Output Association, User Task, Data Object, 200",
			"Data Output Association, Task, BPMN Data Object, 200",
			"Data Output Association, Exclusive Gateway, Data Object, 422",
			"Data Output Association, Sub-Process, BPMN Data Object, 422",
			"Realized By, BPMN Data Store, System Software, 200", "Realized By, User Task, Network Node, 200",
			"Realized By, Pool, Exclusive Gateway, 200" })
	@Order(7)
	void theAssociationsHaveOpenDataEndsAndTaskEnds(String type, String source, String target, int expected) {
		int from = this.api.entity(this.anna, metatype(source), "From " + Api.id());
		int to = this.api.entity(this.anna, metatype(target), "To " + Api.id());
		MvcTestResult result = this.api.writeConnection(this.anna, metatype(type), from, to);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
		if (expected == 422) {
			assertThat(Api.content(result)).contains(expected(type));
		}
	}

	private static String expected(String type) {
		return "Data Input Association".equals(type) ? "TARGET_NOT_ALLOWED" : "SOURCE_NOT_ALLOWED";
	}

	private String metatype(String name) {
		return this.jdbc.queryForObject("SELECT id::text FROM metatypes WHERE name = ?", String.class, name);
	}

	private JsonNode shipped() {
		JsonNode list = this.api.ok(this.api.get("/api/v1/metamodel/starter-packages", this.admin));
		return list.valueStream().filter((entry) -> BPMN.equals(entry.path("packageId").asString())).findFirst().orElseThrow();
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class, sql.contains("?") ? new Object[] { BPMN } : new Object[0]);
	}

	/** The shipped package without what 1.1.0 added, as version 1.0.0. */
	private static ObjectNode oldPackage() {
		try (InputStream in = BpmnPackageUpdateTests.class.getResourceAsStream("/packages/oea-starter-bpmn.json")) {
			ObjectNode pkg = (ObjectNode) JsonMapper.shared().readTree(in);
			Set<String> removed = new java.util.HashSet<>();
			ArrayNode metatypes = JsonMapper.shared().createArrayNode();
			for (JsonNode metatype : pkg.path("metatypes")) {
				if (NEW_METATYPES.contains(metatype.path("name").asString())) {
					removed.add(metatype.path("id").asString());
				}
				else {
					metatypes.add(metatype);
				}
			}
			ArrayNode definitions = JsonMapper.shared().createArrayNode();
			for (JsonNode definition : pkg.path("propertyDefinitions")) {
				if (NEW_PROPERTIES.contains(definition.path("name").asString())) {
					removed.add(definition.path("id").asString());
					definition.path("enumValues").forEach((value) -> removed.add(value.path("id").asString()));
				}
				else {
					definitions.add(definition);
				}
			}
			pkg.set("metatypes", metatypes);
			pkg.set("propertyDefinitions", definitions);
			pkg.put("packageVersion", "1.0.0");
			pkg.remove("lineageModel");
			ObjectNode vp = (ObjectNode) pkg.path("viewpoints").get(0);
			for (String field : List.of("metatypeIds", "connectionTypeIds")) {
				ArrayNode ids = JsonMapper.shared().createArrayNode();
				vp.path(field).forEach((id) -> {
					if (!removed.contains(id.asString())) {
						ids.add(id);
					}
				});
				vp.set(field, ids);
			}
			for (String field : List.of("mappings", "sizes")) {
				ArrayNode entries = JsonMapper.shared().createArrayNode();
				vp.path(field).forEach((entry) -> {
					if (!removed.contains(entry.path("metatypeId").asString())) {
						entries.add(entry);
					}
				});
				vp.set(field, entries);
			}
			for (JsonNode bundle : pkg.path("i18nBundles")) {
				ObjectNode entries = (ObjectNode) bundle;
				List<String> drop = entries.propertyNames().stream().filter((key) -> removed.stream().anyMatch(key::contains)).toList();
				drop.forEach(entries::remove);
			}
			return pkg;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-bpmn-update").toString();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

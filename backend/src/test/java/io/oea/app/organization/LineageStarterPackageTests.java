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
import java.util.HashSet;
import java.util.List;
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
 * Specs metamodel/starter-packages (TOGAF 1.5.0 lineage types, BPMN 1.2.0) and lineage/lineage-model (package
 * section) at API level (us-065-lineage-model-and-query D1, D2, D3): the versions 1.4.0 and 1.1.0 are rebuilt from the
 * shipped packages, imported with data and updated. The imports change the shared metamodel, so the class has a
 * database of its own and relies on the order of its tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LineageStarterPackageTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String TOGAF = "oea-starter-togaf-classic";

	private static final String BPMN = "oea-starter-bpmn";

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

	private static final String MODEL = "/api/v1/metamodel/lineage-model";

	private static final Set<String> NEW_METATYPES = Set.of("Data Flow", "Carries Data");

	private static final Set<String> NEW_PROPERTIES = Set.of("Data classification", "Personal data categories",
			"Data owner", "Protocol", "Frequency", "Transformation description");

	private static int objectId;

	private static int objectVersion;

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
	void theShippedPackagesAreVersion150WithTenMetatypesAndVersion120() {
		JsonNode togaf = shipped(TOGAF);
		assertThat(togaf.path("version").asString()).isEqualTo("1.5.0");
		assertThat(togaf.path("metatypeCount").asInt()).isEqualTo(10);
		assertThat(togaf.path("propertyDefinitionCount").asInt()).isEqualTo(7);
		assertThat(togaf.path("metatypeNames").valueStream().map(JsonNode::asString)).contains("Data Object", "Data Flow",
				"Carries Data");
		assertThat(shipped(BPMN).path("version").asString()).isEqualTo("1.2.0");
	}

	@Test
	@Order(2)
	void theOldVersionsAreImportedWithDataAndLeaveTheLineageModelUnset() {
		this.api.ok(this.api.post("/api/v1/metamodel/import", oldTogaf(), this.admin));
		this.api.ok(this.api.post("/api/v1/metamodel/import", oldBpmn(), this.admin));
		assertThat(shipped(TOGAF).path("importedVersion").asString()).isEqualTo("1.4.0");
		assertThat(shipped(BPMN).path("importedVersion").asString()).isEqualTo("1.1.0");
		JsonNode model = this.api.ok(this.api.get(MODEL, this.franz));
		for (String part : List.of("dataObject", "dataFlow", "carriesData", "dataInput", "dataOutput")) {
			assertThat(model.path(part).path("metatypeId").isNull()).as(part).isTrue();
		}
		objectId = this.api.entity(this.anna, metatype("Data Object"), "Customer Master");
		objectVersion = this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, objectId);
	}

	@Test
	@Order(3)
	void theUpdateToTogaf150AddsTheTypesFillsThreePartsAndKeepsTheData() {
		int entities = count("SELECT count(*) FROM entities");
		JsonNode outcome = this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + TOGAF + "/import", null,
				this.admin));
		assertThat(outcome.path("created").asInt()).isEqualTo(2);
		assertThat(outcome.path("lineageModelFilled").valueStream().map(JsonNode::asString))
			.containsExactly("dataObject", "dataFlow", "carriesData");
		JsonNode model = this.api.ok(this.api.get(MODEL, this.franz));
		assertThat(model.path("dataObject").path("metatypeName").asString()).isEqualTo("Data Object");
		assertThat(model.path("dataFlow").path("metatypeName").asString()).isEqualTo("Data Flow");
		assertThat(model.path("carriesData").path("metatypeName").asString()).isEqualTo("Carries Data");
		assertThat(model.path("dataInput").path("metatypeId").isNull()).isTrue();
		JsonNode audit = auditDetails();
		assertThat(audit.path("cause").asString()).isEqualTo("package-import");
		assertThat(audit.path("packageId").asString()).isEqualTo(TOGAF);
		assertThat(audit.path("parts").size()).isEqualTo(3);
		// the entity and its version are unchanged, and no new property is mandatory
		assertThat(count("SELECT count(*) FROM entities")).isEqualTo(entities);
		assertThat(this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, objectId))
			.isEqualTo(objectVersion);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM metatype_property_mappings m JOIN property_definitions p ON p.id = m.property_def_id
				WHERE p.name = ANY (?) AND m.necessity <> 'optional'""", Integer.class,
				(Object) NEW_PROPERTIES.toArray(String[]::new))).isZero();
		assertThat(shipped(TOGAF).path("importedVersion").asString()).isEqualTo("1.5.0");
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = '" + TOGAF + "'")).isEqualTo(10);
	}

	@Test
	@Order(4)
	void theDataFlowIsAllowedInBothLandscapeViewpoints() {
		for (String name : List.of("Application Landscape", "Technology Landscape")) {
			String viewpoint = this.jdbc.queryForObject("SELECT id::text FROM viewpoints WHERE name = ?", String.class, name);
			JsonNode vp = this.api.ok(this.api.get("/api/v1/admin/viewpoints/" + viewpoint, this.anna));
			assertThat(vp.path("allowedRelations").valueStream().map((type) -> type.path("name").asString()))
				.as(name)
				.contains("Data Flow")
				.doesNotContain("Carries Data");
		}
	}

	@Test
	@Order(5)
	void theUpdateToBpmn120FillsDataInputAndDataOutputAndKeepsTheTypes() {
		int types = count("SELECT count(*) FROM metatypes WHERE source_package_id = '" + BPMN + "'");
		JsonNode outcome = this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + BPMN + "/import", null,
				this.admin));
		assertThat(outcome.path("created").asInt()).isZero();
		assertThat(outcome.path("lineageModelFilled").valueStream().map(JsonNode::asString))
			.containsExactly("dataInput", "dataOutput");
		JsonNode model = this.api.ok(this.api.get(MODEL, this.franz));
		assertThat(model.path("dataInput").path("metatypeName").asString()).isEqualTo("Data Input Association");
		assertThat(model.path("dataOutput").path("metatypeName").asString()).isEqualTo("Data Output Association");
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = '" + BPMN + "'")).isEqualTo(types);
		assertThat(shipped(BPMN).path("importedVersion").asString()).isEqualTo("1.2.0");
	}

	@Test
	@Order(6)
	void aReimportChangesNothingAndAuditsNothing() {
		int version = this.jdbc.queryForObject("SELECT version FROM lineage_model", Integer.class);
		int audits = audits();
		for (String id : List.of(TOGAF, BPMN)) {
			JsonNode outcome = this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + id + "/import", null,
					this.admin));
			assertThat(outcome.path("lineageModelFilled")).isEmpty();
		}
		assertThat(this.jdbc.queryForObject("SELECT version FROM lineage_model", Integer.class)).isEqualTo(version);
		assertThat(audits()).isEqualTo(audits);
	}

	@Test
	@Order(7)
	void aSetPartIsKeptAndTheOthersAreFilled() {
		String interfaceType = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES (?::uuid, 'Interface', true) RETURNING id::text""",
				String.class, CONFIG);
		this.api.ok(this.api.put(MODEL, Api.object().put("dataFlow", interfaceType), this.admin,
				"\"" + this.jdbc.queryForObject("SELECT version FROM lineage_model", Integer.class) + "\""));
		JsonNode outcome = this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/" + TOGAF + "/import", null,
				this.admin));
		assertThat(outcome.path("lineageModelFilled").valueStream().map(JsonNode::asString))
			.containsExactly("dataObject", "carriesData");
		JsonNode model = this.api.ok(this.api.get(MODEL, this.franz));
		assertThat(model.path("dataFlow").path("metatypeName").asString()).isEqualTo("Interface");
		assertThat(model.path("dataObject").path("metatypeName").asString()).isEqualTo("Data Object");
		assertThat(model.path("carriesData").path("metatypeName").asString()).isEqualTo("Carries Data");
		// restore the types of the package for the following tests
		this.jdbc.update("""
				UPDATE lineage_model SET data_flow_metatype_id = (SELECT id FROM metatypes WHERE name = 'Data Flow'),
				data_input_metatype_id = (SELECT id FROM metatypes WHERE name = 'Data Input Association'),
				data_output_metatype_id = (SELECT id FROM metatypes WHERE name = 'Data Output Association')""");
	}

	/** the part, a metatype outside the package or of the wrong kind */
	@ParameterizedTest
	@CsvSource({ "dataFlow, other connection", "dataObject, other element", "carriesData, own element",
			"dataInput, own element", "dataOutput, other element", "dataObject, own connection" })
	@Order(8)
	void aPackageNamingAMetatypeOutsideItselfOrOfTheWrongKindIsRejected(String part, String what) {
		int metatypes = this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Integer.class);
		ObjectNode pkg = Api.object();
		String packageId = "test-lineage-" + part.toLowerCase(java.util.Locale.ROOT) + what.replace(' ', '-');
		pkg.put("formatVersion", "1.0").put("exportedAt", "2026-11-28T00:00:00Z").put("packageId", packageId)
			.put("packageVersion", "1.0.0").put("publisher", "Test");
		pkg.putArray("propertyCategories");
		pkg.putArray("propertyDefinitions");
		String own = java.util.UUID.nameUUIDFromBytes((packageId + "/element").getBytes()).toString();
		String ownConnection = java.util.UUID.nameUUIDFromBytes((packageId + "/connection").getBytes()).toString();
		pkg.putArray("metatypes")
			.add(Api.object().put("id", own).put("name", packageId + " Element").put("abbreviation", "TE").put("sortOrder", 1)
				.put("isConnection", false))
			.add(Api.object().put("id", ownConnection).put("name", packageId + " Connection").put("abbreviation", "TC")
				.put("sortOrder", 2).put("isConnection", true));
		String foreign = switch (what) {
			case "other element" -> metatype("Application Component");
			case "other connection" -> metatype("Data Flow");
			case "own connection" -> ownConnection;
			default -> own;
		};
		pkg.putObject("lineageModel").put(part, foreign);
		MvcTestResult result = this.api.post("/api/v1/metamodel/import", pkg, this.admin);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(Api.content(result)).contains("/lineageModel/" + part);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Integer.class)).isEqualTo(metatypes);
	}

	// the types of the TOGAF package

	/** source type, target type, connection type, expected status, expected code */
	@ParameterizedTest
	@CsvSource({ "Application Component, Application Component, Data Flow, 200, -",
			"System Software, System Software, Data Flow, 200, -",
			"Application Service, Technology Component, Data Flow, 200, -",
			"Technology Component, Application Component, Data Flow, 200, -",
			"Data Object, Application Component, Data Flow, 422, SOURCE_NOT_ALLOWED",
			"Application Component, Network Node, Data Flow, 422, TARGET_NOT_ALLOWED" })
	@Order(9)
	void theDataFlowRunsBetweenApplicationAndTechnologyElements(String source, String target, String type, int status,
			String code) {
		int from = this.api.entity(this.anna, metatype(source), "From " + Api.id());
		int to = this.api.entity(this.anna, metatype(target), "To " + Api.id());
		MvcTestResult result = this.api.writeConnection(this.anna, metatype(type), from, to);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(status);
		if (status == 422) {
			assertThat(Api.content(result)).contains(code);
		}
	}

	@Test
	@Order(10)
	void carriesDataFromADataFlowToADataObjectIsAccepted() {
		int flow = flow();
		MvcTestResult result = this.api.writeConnection(this.anna, metatype("Carries Data"), flow, objectId);
		JsonNode item = this.api.ok(result).path("items").get(0);
		assertThat(item.path("id").asInt()).isPositive().isNotEqualTo(flow);
		assertThat(this.jdbc.queryForObject("SELECT source_entity_id FROM entity_connections WHERE entity_id = ?",
				Integer.class, item.path("id").asInt())).isEqualTo(flow);
		assertThat(this.jdbc.queryForObject("SELECT target_entity_id FROM entity_connections WHERE entity_id = ?",
				Integer.class, item.path("id").asInt())).isEqualTo(objectId);
	}

	@Test
	@Order(11)
	void carriesDataFromAnApplicationComponentGives422SourceNotAllowed() {
		int component = this.api.entity(this.anna, metatype("Application Component"), "Component " + Api.id());
		int connections = count("SELECT count(*) FROM entity_connections");
		MvcTestResult result = this.api.writeConnection(this.anna, metatype("Carries Data"), component, objectId);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(Api.content(result)).contains("SOURCE_NOT_ALLOWED");
		assertThat(count("SELECT count(*) FROM entity_connections")).isEqualTo(connections);
	}

	@Test
	@Order(12)
	void carriesDataToADataFlowGives422TargetNotAllowed() {
		int source = flow();
		int target = flow();
		int connections = count("SELECT count(*) FROM entity_connections");
		MvcTestResult result = this.api.writeConnection(this.anna, metatype("Carries Data"), source, target);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(Api.content(result)).contains("TARGET_NOT_ALLOWED");
		assertThat(count("SELECT count(*) FROM entity_connections")).isEqualTo(connections);
	}

	/** metatype, property, length, expected status; the last valid value and the first invalid one of each limit */
	@ParameterizedTest
	@CsvSource({ "Data Object, Personal data categories, 500, 200", "Data Object, Personal data categories, 501, 422",
			"Data Flow, Personal data categories, 500, 200", "Data Flow, Personal data categories, 501, 422",
			"Data Flow, Transformation description, 1000, 200", "Data Flow, Transformation description, 1001, 422",
			"Data Object, Data owner, 255, 200", "Data Object, Data owner, 256, 422" })
	@Order(13)
	void theTextLimitsAcceptTheLastValidLengthAndRejectTheFirstInvalidOne(String type, String property, int length,
			int expected) {
		String propertyId = this.jdbc.queryForObject("SELECT id::text FROM property_definitions WHERE name = ?",
				String.class, property);
		MvcTestResult result;
		if ("Data Flow".equals(type)) {
			ObjectNode body = Api.object().put("atomic", true);
			ObjectNode connection = Api.object().put("connectionMetatypeId", metatype("Data Flow"))
				.put("sourceId", this.api.entity(this.anna, metatype("Application Component"), "A " + Api.id()))
				.put("targetId", this.api.entity(this.anna, metatype("Application Component"), "B " + Api.id()));
			connection.putArray("properties").add(Api.object().put("propertyDefId", propertyId).put("value", "x".repeat(length)));
			body.putArray("connections").add(connection);
			result = this.api.post("/api/v1/entities/batch", body, this.anna);
		}
		else {
			result = this.api.writeEntity(this.anna, metatype(type), "Object " + Api.id(), propertyId, "x".repeat(length));
		}
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
		if (expected == 422) {
			assertThat(Api.content(result)).contains("properties[0]");
		}
	}

	@Test
	@Order(14)
	void theEnumPropertiesTakeTheirValuesOnly() {
		int source = this.api.entity(this.anna, metatype("Application Component"), "A " + Api.id());
		int target = this.api.entity(this.anna, metatype("Application Component"), "B " + Api.id());
		for (String property : List.of("Protocol", "Frequency")) {
			String propertyId = this.jdbc.queryForObject("SELECT id::text FROM property_definitions WHERE name = ?",
					String.class, property);
			String valid = this.jdbc.queryForObject("""
					SELECT value FROM property_enum_values WHERE property_def_id = ?::uuid ORDER BY sort_order LIMIT 1""",
					String.class, propertyId);
			for (String value : List.of(valid, "not-a-value")) {
				ObjectNode body = Api.object().put("atomic", true);
				ObjectNode connection = Api.object().put("connectionMetatypeId", metatype("Data Flow"))
					.put("sourceId", source).put("targetId", target);
				connection.putArray("properties").add(Api.object().put("propertyDefId", propertyId).put("value", value));
				body.putArray("connections").add(connection);
				MvcTestResult result = this.api.post("/api/v1/entities/batch", body, this.anna);
				assertThat(Api.status(result)).as(property + " " + value).isEqualTo(valid.equals(value) ? 200 : 422);
			}
		}
	}

	// helpers

	private int flow() {
		return this.api.connection(this.anna, metatype("Data Flow"),
				this.api.entity(this.anna, metatype("Application Component"), "Source " + Api.id()),
				this.api.entity(this.anna, metatype("Application Component"), "Target " + Api.id()));
	}

	private String metatype(String name) {
		return this.jdbc.queryForObject("SELECT id::text FROM metatypes WHERE name = ?", String.class, name);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private int audits() {
		return count("SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.lineage-model-updated'");
	}

	private JsonNode auditDetails() {
		return JsonMapper.shared().readTree(this.jdbc.queryForObject(
				"SELECT details::text FROM audit.events WHERE event_type = 'metamodel.lineage-model-updated' "
						+ "ORDER BY id DESC LIMIT 1", String.class));
	}

	private JsonNode shipped(String packageId) {
		JsonNode list = this.api.ok(this.api.get("/api/v1/metamodel/starter-packages", this.admin));
		return list.valueStream()
			.filter((entry) -> packageId.equals(entry.path("packageId").asString()))
			.findFirst()
			.orElseThrow();
	}

	/** The shipped TOGAF package without what 1.5.0 added, as version 1.4.0. */
	private static ObjectNode oldTogaf() {
		ObjectNode pkg = shippedPackage(TOGAF);
		Set<String> removed = new HashSet<>();
		ArrayNode metatypes = JsonMapper.shared().createArrayNode();
		for (JsonNode metatype : pkg.path("metatypes")) {
			if (NEW_METATYPES.contains(metatype.path("name").asString())) {
				removed.add(metatype.path("id").asString());
			}
			else {
				ArrayNode mappings = JsonMapper.shared().createArrayNode();
				for (JsonNode mapping : metatype.path("propertyMappings")) {
					if (!NEW_PROPERTIES.contains(mapping.path("propertyDefinition").path("name").asString())) {
						mappings.add(mapping);
					}
				}
				((ObjectNode) metatype).set("propertyMappings", mappings);
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
		ArrayNode categories = JsonMapper.shared().createArrayNode();
		for (JsonNode category : pkg.path("propertyCategories")) {
			if ("Data".equals(category.path("name").asString())) {
				removed.add(category.path("id").asString());
			}
			else {
				categories.add(category);
			}
		}
		pkg.set("metatypes", metatypes);
		pkg.set("propertyDefinitions", definitions);
		pkg.set("propertyCategories", categories);
		pkg.put("packageVersion", "1.4.0");
		pkg.remove("lineageModel");
		stripViewpoints(pkg, removed);
		return pkg;
	}

	/** The shipped BPMN package without the section lineageModel, as version 1.1.0. */
	private static ObjectNode oldBpmn() {
		ObjectNode pkg = shippedPackage(BPMN);
		pkg.put("packageVersion", "1.1.0");
		pkg.remove("lineageModel");
		return pkg;
	}

	private static void stripViewpoints(ObjectNode pkg, Set<String> removed) {
		for (JsonNode viewpoint : pkg.path("viewpoints")) {
			ObjectNode vp = (ObjectNode) viewpoint;
			for (String field : List.of("metatypeIds", "connectionTypeIds")) {
				ArrayNode ids = JsonMapper.shared().createArrayNode();
				vp.path(field).forEach((id) -> {
					if (!removed.contains(id.asString())) {
						ids.add(id);
					}
				});
				vp.set(field, ids);
			}
			ArrayNode mappings = JsonMapper.shared().createArrayNode();
			vp.path("mappings").forEach((entry) -> {
				if (!removed.contains(entry.path("metatypeId").asString())) {
					mappings.add(entry);
				}
			});
			vp.set("mappings", mappings);
		}
		for (JsonNode bundle : pkg.path("i18nBundles")) {
			ObjectNode entries = (ObjectNode) bundle;
			List<String> drop = entries.propertyNames().stream().filter((key) -> removed.stream().anyMatch(key::contains)).toList();
			drop.forEach(entries::remove);
		}
	}

	private static ObjectNode shippedPackage(String packageId) {
		try (InputStream in = LineageStarterPackageTests.class.getResourceAsStream("/packages/" + packageId + ".json")) {
			return (ObjectNode) JsonMapper.shared().readTree(in);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-lineage-package").toString();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

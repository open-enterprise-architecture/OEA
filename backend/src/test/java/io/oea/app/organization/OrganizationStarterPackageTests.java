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
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec metamodel/starter-packages (organization starter package) and metamodel/organization-model (package
 * section) at API level (us-075-lane-assignments D1, D2). The imports change the shared metamodel, so the
 * class has a database of its own and relies on the order of its tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrganizationStarterPackageTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String ORGANIZATION = "oea-starter-organization";

	private static final String CONFIG = "00000000-0000-0000-0000-000000000001";

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
	void theListShowsThePackageAsNotImported() {
		JsonNode entry = shipped(this.api.ok(this.api.get("/api/v1/metamodel/starter-packages", this.admin)));
		assertThat(entry.path("name").asString()).isEqualTo("Organization model");
		assertThat(entry.path("version").asString()).isEqualTo("1.0.0");
		assertThat(entry.path("metatypeCount").asInt()).isEqualTo(6);
		assertThat(entry.path("propertyDefinitionCount").asInt()).isEqualTo(1);
		assertThat(entry.path("metatypeNames").valueStream().map(JsonNode::asString)).containsExactlyInAnyOrder(
				"Organizational Unit", "Role", "Person", "Belongs To", "Performs Role", "Responsible");
		assertThat(entry.path("imported").asBoolean()).isFalse();
	}

	@Test
	@Order(2)
	void importNeedsMetamodelWrite() {
		assertThat(Api.status(this.api.post(importPath(), null, this.franz))).isEqualTo(403);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes WHERE source_package_id = ?", Integer.class,
				ORGANIZATION)).isZero();
	}

	@Test
	@Order(3)
	void importedAloneItFillsTheOrganizationModel() {
		JsonNode before = this.api.ok(this.api.get("/api/v1/metamodel/organization-model", this.franz));
		assertThat(before.path("orgUnit").path("metatypeId").isNull()).isTrue();
		JsonNode outcome = this.api.ok(this.api.post(importPath(), null, this.admin));
		assertThat(outcome.path("created").asInt()).isEqualTo(6);
		assertThat(outcome.path("organizationModelFilled").valueStream().map(JsonNode::asString))
			.containsExactly("orgUnit", "role", "person", "belongsTo", "performsRole", "responsible");
		JsonNode model = this.api.ok(this.api.get("/api/v1/metamodel/organization-model", this.franz));
		assertThat(model.path("orgUnit").path("metatypeName").asString()).isEqualTo("Organizational Unit");
		assertThat(model.path("role").path("metatypeName").asString()).isEqualTo("Role");
		assertThat(model.path("person").path("metatypeName").asString()).isEqualTo("Person");
		assertThat(model.path("belongsTo").path("metatypeName").asString()).isEqualTo("Belongs To");
		assertThat(model.path("performsRole").path("metatypeName").asString()).isEqualTo("Performs Role");
		assertThat(model.path("responsible").path("metatypeName").asString()).isEqualTo("Responsible");
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = ? AND NOT is_connection")).isEqualTo(3);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = ? AND is_connection")).isEqualTo(3);
		assertThat(count("SELECT count(*) FROM viewpoints WHERE source_package_id = ?")).isZero();
		JsonNode audit = auditDetails("metamodel.organization-model-updated");
		assertThat(audit.path("cause").asString()).isEqualTo("package-import");
		assertThat(audit.path("parts").size()).isEqualTo(6);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM i18n_entries WHERE locale = 'de' AND entity_type = 'metatype'
				AND value IN ('Organisationseinheit', 'Rolle', 'Person', 'Gehört zu', 'Übernimmt Rolle', 'Verantwortlich')""",
				Integer.class)).isEqualTo(6);
	}

	@Test
	@Order(4)
	void aReimportChangesNothing() {
		int version = this.jdbc.queryForObject("SELECT version FROM organization_model", Integer.class);
		int audits = this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.organization-model-updated'",
				Integer.class);
		JsonNode outcome = this.api.ok(this.api.post(importPath(), null, this.admin));
		assertThat(outcome.path("created").asInt()).isZero();
		assertThat(outcome.path("updated").asInt()).isZero();
		assertThat(outcome.path("organizationModelFilled")).isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT version FROM organization_model", Integer.class)).isEqualTo(version);
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.organization-model-updated'",
				Integer.class)).isEqualTo(audits);
		assertThat(shipped(this.api.ok(this.api.get("/api/v1/metamodel/starter-packages", this.admin)))
			.path("imported")
			.asBoolean()).isTrue();
	}

	@Test
	@Order(5)
	void aSetPartIsKeptAndTheOthersAreFilled() {
		// the instance has its own type "Department" as organizational unit and no other part
		String department = metatype("Department", false);
		String body = ("{\"orgUnit\": \"%s\"}").formatted(department);
		MvcTestResult updated = this.api.put("/api/v1/metamodel/organization-model", json(body), this.admin,
				"\"" + version() + "\"");
		this.api.ok(updated);
		this.jdbc.update("""
				UPDATE organization_model SET role_metatype_id = NULL, person_metatype_id = NULL,
				belongs_to_metatype_id = NULL, performs_role_metatype_id = NULL, responsible_metatype_id = NULL""");
		JsonNode outcome = this.api.ok(this.api.post(importPath(), null, this.admin));
		assertThat(outcome.path("organizationModelFilled").valueStream().map(JsonNode::asString))
			.containsExactly("role", "person", "belongsTo", "performsRole", "responsible");
		JsonNode model = this.api.ok(this.api.get("/api/v1/metamodel/organization-model", this.franz));
		assertThat(model.path("orgUnit").path("metatypeName").asString()).isEqualTo("Department");
		assertThat(model.path("role").path("metatypeName").asString()).isEqualTo("Role");
		// restore the package's own types for the following tests
		this.jdbc.update("UPDATE organization_model SET org_unit_metatype_id = (SELECT id FROM metatypes WHERE name = "
				+ "'Organizational Unit' AND source_package_id = ?)", ORGANIZATION);
	}

	/** the part, a metatype outside the package or of the wrong kind */
	@ParameterizedTest
	@CsvSource({ "role, other element", "orgUnit, other connection", "person, own connection", "belongsTo, own element",
			"performsRole, other element", "responsible, other connection" })
	@Order(6)
	void aPackageNamingAMetatypeOutsideItselfOrOfTheWrongKindIsRejected(String part, String what) {
		int metatypes = this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Integer.class);
		ObjectNode pkg = Api.object();
		String packageId = "test-organization-" + part.toLowerCase(java.util.Locale.ROOT);
		pkg.put("formatVersion", "1.0").put("exportedAt", "2026-11-27T00:00:00Z").put("packageId", packageId)
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
			case "other element" -> metatype("Role", false, ORGANIZATION);
			case "other connection" -> metatype("Belongs To", true, ORGANIZATION);
			case "own connection" -> ownConnection;
			default -> own;
		};
		pkg.putObject("organizationModel").put(part, foreign);
		MvcTestResult result = this.api.post("/api/v1/metamodel/import", pkg, this.admin);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(Api.content(result)).contains("/organizationModel/" + part);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Integer.class)).isEqualTo(metatypes);
	}

	@Test
	@Order(7)
	void aPackageThatNamesItsOwnMetatypesOfTheRightKindIsAccepted() {
		// the parts of the instance are set, so nothing is filled, but the section is valid
		ObjectNode pkg = Api.object();
		String packageId = "test-organization-valid";
		pkg.put("formatVersion", "1.0").put("exportedAt", "2026-11-27T00:00:00Z").put("packageId", packageId)
			.put("packageVersion", "1.0.0").put("publisher", "Test");
		pkg.putArray("propertyCategories");
		pkg.putArray("propertyDefinitions");
		String element = java.util.UUID.nameUUIDFromBytes((packageId + "/element").getBytes()).toString();
		String connection = java.util.UUID.nameUUIDFromBytes((packageId + "/connection").getBytes()).toString();
		pkg.putArray("metatypes")
			.add(Api.object().put("id", element).put("name", packageId + " Element").put("abbreviation", "TE")
				.put("sortOrder", 1).put("isConnection", false))
			.add(Api.object().put("id", connection).put("name", packageId + " Connection").put("abbreviation", "TC")
				.put("sortOrder", 2).put("isConnection", true));
		pkg.putObject("organizationModel").put("role", element).put("responsible", connection);
		JsonNode outcome = this.api.ok(this.api.post("/api/v1/metamodel/import", pkg, this.admin));
		assertThat(outcome.path("organizationModelFilled")).isEmpty();
	}

	// the package next to the others: TOGAF and BPMN

	@Test
	@Order(8)
	void itImportsNextToTheTogafAndTheBpmnPackage() {
		this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/oea-starter-togaf-classic/import", null, this.admin));
		this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/oea-starter-bpmn/import", null, this.admin));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes WHERE source_package_id IN "
				+ "('oea-starter-togaf-classic', 'oea-starter-bpmn', ?)", Integer.class, ORGANIZATION)).isEqualTo(8 + 36 + 6);
		// a re-import of the organization package after the others changes nothing
		JsonNode outcome = this.api.ok(this.api.post(importPath(), null, this.admin));
		assertThat(outcome.path("created").asInt()).isZero();
	}

	/** source type, target type, connection type, expected status */
	@ParameterizedTest
	@CsvSource({ "Lane, Organizational Unit, Belongs To, 200", "Pool, Organizational Unit, Belongs To, 200",
			"Application Component, Organizational Unit, Belongs To, 200",
			"Application Component, Person, Responsible, 200", "User Task, Person, Responsible, 200",
			"User Task, Role, Performs Role, 200", "Lane, Role, Performs Role, 200",
			"Application Component, Role, Responsible, 422", "Lane, Role, Responsible, 422",
			"User Task, Organizational Unit, Performs Role, 422", "User Task, Person, Belongs To, 422",
			"Application Component, Person, Performs Role, 422" })
	@Order(9)
	void connectionsAreAcceptedFromAnyElementToTheirTargetOnly(String source, String target, String connection,
			int expected) {
		int from = entity(source, "Source " + source + " " + Api.id());
		int to = entity(target, "Target " + target + " " + Api.id());
		MvcTestResult result = this.api.writeConnection(this.anna, metatype(connection, true, ORGANIZATION), from, to);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
		if (expected == 422) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM entity_connections WHERE source_entity_id = ?",
					Integer.class, from)).isZero();
		}
	}

	/** length of the value, expected status */
	@ParameterizedTest
	@CsvSource({ "1, 200", "255, 200", "256, 422" })
	@Order(10)
	void theAccountIdAcceptsAtMost255Characters(int length, int expected) {
		MvcTestResult result = this.api.writeEntity(this.anna, metatype("Person", false, ORGANIZATION), "Maria " + Api.id(),
				this.jdbc.queryForObject("SELECT id::text FROM property_definitions WHERE name = 'Account ID'", String.class),
				"a".repeat(length));
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(expected);
	}

	// helpers

	private int entity(String type, String name) {
		String metatype = switch (type) {
			case "Organizational Unit", "Role", "Person" -> metatype(type, false, ORGANIZATION);
			case "Lane", "Pool", "User Task" -> metatype(type, false, "oea-starter-bpmn");
			default -> metatype(type, false, "oea-starter-togaf-classic");
		};
		return this.api.entity(this.anna, metatype, name);
	}

	private String metatype(String name, boolean connection, String packageId) {
		return this.jdbc.queryForObject("SELECT id::text FROM metatypes WHERE name = ? AND source_package_id = ?",
				String.class, name, packageId);
	}

	private String metatype(String name, boolean connection) {
		List<String> existing = this.jdbc.queryForList("SELECT id::text FROM metatypes WHERE name = ?", String.class, name);
		if (!existing.isEmpty()) {
			return existing.getFirst();
		}
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES (?::uuid, ?, ?) RETURNING id::text""",
				String.class, CONFIG, name, connection);
	}

	private int version() {
		return this.jdbc.queryForObject("SELECT version FROM organization_model", Integer.class);
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class, ORGANIZATION);
	}

	private JsonNode auditDetails(String eventType) {
		return tools.jackson.databind.json.JsonMapper.shared().readTree(this.jdbc.queryForObject(
				"SELECT details::text FROM audit.events WHERE event_type = ? ORDER BY id DESC LIMIT 1", String.class,
				eventType));
	}

	private static String importPath() {
		return "/api/v1/metamodel/starter-packages/" + ORGANIZATION + "/import";
	}

	private static JsonNode shipped(JsonNode packages) {
		return packages.valueStream()
			.filter((candidate) -> ORGANIZATION.equals(candidate.path("packageId").asString()))
			.findFirst()
			.orElseThrow();
	}

	private static JsonNode json(String text) {
		return tools.jackson.databind.json.JsonMapper.shared().readTree(text);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-organization-package").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

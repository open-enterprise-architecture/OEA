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
 * Spec metamodel/organization-model, requirement "Organization model setting" (us-075-lane-assignments D2):
 * the parts, their kind, the target rule of the connection parts, the version, the permissions, the lock mode
 * and the deletion of a metatype. Own database; the tests rely on their order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrganizationModelTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String PATH = "/api/v1/metamodel/organization-model";

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
	void theSettingStartsEmptyAndAnEntityReaderReadsIt() {
		MvcTestResult result = this.api.get(PATH, this.franz);
		JsonNode model = this.api.ok(result);
		for (String part : new String[] { "orgUnit", "role", "person", "belongsTo", "performsRole", "responsible" }) {
			assertThat(model.path(part).path("metatypeId").isNull()).as(part).isTrue();
		}
		assertThat(model.path("version").asInt()).isEqualTo(1);
		assertThat(Api.etag(result)).isEqualTo("\"1\"");
	}

	@Test
	@Order(2)
	void aMetamodelAdministratorReadsItToo() {
		assertThat(Api.status(this.api.get(PATH, this.admin))).isEqualTo(200);
	}

	@Test
	@Order(3)
	void aPersonWithoutEntityReadOrMetamodelWriteCannotReadIt() {
		TestSession none = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithoutRole();
		assertThat(Api.status(this.api.get(PATH, none))).isEqualTo(403);
	}

	/** part, kind of the metatype (element or connection) */
	@ParameterizedTest
	@CsvSource({ "orgUnit, element", "role, element", "person, element", "belongsTo, connection",
			"performsRole, connection", "responsible, connection" })
	@Order(4)
	void eachPartTakesAMetatypeOfItsKind(String part, String kind) {
		String metatype = metatype(kind + " for " + part, "connection".equals(kind));
		ObjectNode body = Api.object().put(part, metatype);
		JsonNode model = this.api.ok(this.api.put(PATH, body, this.admin, "\"" + version() + "\""));
		assertThat(model.path(part).path("metatypeId").asString()).isEqualTo(metatype);
		assertThat(model.path(part).path("metatypeName").asString()).isEqualTo(kind + " for " + part);
		JsonNode audit = auditDetails();
		assertThat(audit.path("parts").path(part).path("new").asString()).isEqualTo(metatype);
		// the other parts are unset by a whole-setting write
		this.jdbc.update("UPDATE organization_model SET org_unit_metatype_id = NULL, role_metatype_id = NULL, "
				+ "person_metatype_id = NULL, belongs_to_metatype_id = NULL, performs_role_metatype_id = NULL, "
				+ "responsible_metatype_id = NULL");
	}

	/** part, kind of the metatype that is set (the wrong one) */
	@ParameterizedTest
	@CsvSource({ "orgUnit, connection", "role, connection", "person, connection", "belongsTo, element",
			"performsRole, element", "responsible, element" })
	@Order(5)
	void aMetatypeOfTheWrongKindIsRejectedNamingThePart(String part, String kind) {
		int version = version();
		MvcTestResult result = this.api.put(PATH, Api.object().put(part, metatype("wrong " + kind + " " + part,
				"connection".equals(kind))), this.admin, "\"" + version + "\"");
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString)).contains(part);
		assertThat(version()).isEqualTo(version);
	}

	@Test
	@Order(6)
	void anUnknownMetatypeIsRejectedNamingThePart() {
		int version = version();
		MvcTestResult result = this.api.put(PATH, Api.object().put("role", Api.id()), this.admin, "\"" + version + "\"");
		assertThat(Api.status(result)).isEqualTo(422);
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString)).contains("role");
		assertThat(version()).isEqualTo(version);
	}

	@Test
	@Order(7)
	void aConnectionThatDoesNotReachTheOrganizationalUnitIsRejectedUntilItsTargetListIsEmptyOrABaseType() {
		String department = metatype("Department", false);
		String unit = metatype("Unit of the instance", false);
		String base = metatype("Group", false);
		this.jdbc.update("UPDATE metatypes SET base_metatype_id = ?::uuid WHERE id = ?::uuid", base, department);
		String excluding = metatype("Belongs to excluding", true);
		String reaching = metatype("Belongs to a group", true);
		String open = metatype("Belongs to anything", true);
		this.jdbc.update("INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id) VALUES "
				+ "(?::uuid, 'target', ?::uuid), (?::uuid, 'target', ?::uuid)", excluding, unit, reaching, base);
		int version = version();
		ObjectNode body = Api.object().put("orgUnit", department).put("belongsTo", excluding);
		MvcTestResult rejected = this.api.put(PATH, body, this.admin, "\"" + version + "\"");
		assertThat(Api.status(rejected)).as(() -> Api.content(rejected)).isEqualTo(422);
		assertThat(Api.body(rejected).path("invalidFields").valueStream().map(JsonNode::asString)).contains("belongsTo");
		assertThat(version()).isEqualTo(version);
		// a target that is a base type of the organizational unit reaches it
		this.api.ok(this.api.put(PATH, Api.object().put("orgUnit", department).put("belongsTo", reaching), this.admin,
				"\"" + version + "\""));
		// an empty target list reaches every type
		this.api.ok(this.api.put(PATH, Api.object().put("orgUnit", department).put("belongsTo", open), this.admin,
				"\"" + version() + "\""));
	}

	@Test
	@Order(8)
	void aWriteThatChangesNothingKeepsTheVersionAndWritesNoAudit() {
		int version = version();
		int audits = audits();
		String department = this.jdbc.queryForObject("SELECT org_unit_metatype_id::text FROM organization_model",
				String.class);
		String open = this.jdbc.queryForObject("SELECT belongs_to_metatype_id::text FROM organization_model", String.class);
		JsonNode model = this.api.ok(this.api.put(PATH, Api.object().put("orgUnit", department).put("belongsTo", open),
				this.admin, "\"" + version + "\""));
		assertThat(model.path("version").asInt()).isEqualTo(version);
		assertThat(audits()).isEqualTo(audits);
	}

	@Test
	@Order(9)
	void aStaleVersionIsRejectedWith412AndNoVersionWith428() {
		int version = version();
		MvcTestResult stale = this.api.put(PATH, Api.object(), this.admin, "\"" + (version - 1) + "\"");
		assertThat(Api.status(stale)).isEqualTo(412);
		MvcTestResult missing = this.api.put(PATH, Api.object(), this.admin, null);
		assertThat(Api.status(missing)).isEqualTo(428);
		assertThat(version()).isEqualTo(version);
		assertThat(this.jdbc.queryForObject("SELECT org_unit_metatype_id IS NOT NULL FROM organization_model",
				Boolean.class)).isTrue();
	}

	@Test
	@Order(10)
	void aHolderOfEntityWriteOnlyCannotChangeItButReadsIt() {
		int version = version();
		MvcTestResult result = this.api.put(PATH, Api.object(), this.anna, "\"" + version + "\"");
		assertThat(Api.status(result)).isEqualTo(403);
		assertThat(version()).isEqualTo(version);
		assertThat(Api.status(this.api.get(PATH, this.anna))).isEqualTo(200);
	}

	@Test
	@Order(11)
	void anImportOnlyInstanceRejectsAChangeWithMetamodelLocked() {
		int version = version();
		this.jdbc.update("UPDATE metamodel_configurations SET edit_mode = 'import-only' WHERE id = ?::uuid", INSTANCE);
		try {
			MvcTestResult result = this.api.put(PATH, Api.object(), this.admin, "\"" + version + "\"");
			assertThat(Api.status(result)).isEqualTo(403);
			assertThat(Api.content(result)).contains("metamodel-locked");
			assertThat(version()).isEqualTo(version);
			assertThat(Api.status(this.api.get(PATH, this.franz))).isEqualTo(200);
		}
		finally {
			this.jdbc.update("UPDATE metamodel_configurations SET edit_mode = 'gui-and-import' WHERE id = ?::uuid", INSTANCE);
		}
	}

	@Test
	@Order(12)
	void thePartBecomesUnsetWhenItsMetatypeIsDeleted() {
		String role = metatype("Short-lived role", false);
		this.api.ok(this.api.put(PATH, Api.object().put("role", role), this.admin, "\"" + version() + "\""));
		MvcTestResult deleted = this.api.call(org.springframework.http.HttpMethod.DELETE, "/api/v1/metamodel/metatypes/" + role,
				null, this.admin, "\"1\"");
		assertThat(Api.status(deleted)).as(() -> Api.content(deleted)).isEqualTo(204);
		JsonNode model = this.api.ok(this.api.get(PATH, this.franz));
		assertThat(model.path("role").path("metatypeId").isNull()).isTrue();
	}

	// helpers

	private String metatype(String name, boolean connection) {
		return this.jdbc.queryForList("SELECT id::text FROM metatypes WHERE name = ?", String.class, name)
			.stream()
			.findFirst()
			.orElseGet(() -> this.jdbc.queryForObject("""
					INSERT INTO metatypes (config_id, name, is_connection) VALUES (?::uuid, ?, ?) RETURNING id::text""",
					String.class, INSTANCE, name, connection));
	}

	private int version() {
		return this.jdbc.queryForObject("SELECT version FROM organization_model", Integer.class);
	}

	private int audits() {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.organization-model-updated'",
				Integer.class);
	}

	private JsonNode auditDetails() {
		return tools.jackson.databind.json.JsonMapper.shared().readTree(this.jdbc.queryForObject(
				"SELECT details::text FROM audit.events WHERE event_type = 'metamodel.organization-model-updated' "
						+ "ORDER BY id DESC LIMIT 1", String.class));
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-organization-model").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

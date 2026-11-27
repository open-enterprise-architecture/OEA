package io.oea.app.organization;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.core.access.PersonStore;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.nio.file.Files;
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
 * Spec identity/person-entity-link, requirements "Account mapping", "Account link through the mapping" and
 * "No link of the own account through the mapping" (us-075-lane-assignments D5). The instance has the
 * organization package, the person metatype "Person" and the mapping property "Account ID" restricted to
 * the enterprise architect; every test uses values of its own, so that the tests do not depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PersonLinkMappingTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String MAPPING = "/api/v1/admin/property-visibility/person-link-mapping";

	private static final String VISIBILITY = "/api/v1/admin/property-visibility";

	private static boolean prepared;

	private static String propertyId;

	private static String provider;

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
	private PersonStore personStore;

	private Api api;

	private TestPrincipals principals;

	private TestSession metamodelAdmin;

	private TestSession security;

	private TestSession anna;

	private TestSession personManager;

	@BeforeEach
	void instance() {
		this.api = new Api(this.mvc);
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
		this.security = this.principals.personWithIdpRole("security-admin");
		this.anna = this.principals.personWithIdpRole("enterprise-architect");
		this.personManager = this.principals.personWithIdpRole("system-admin");
		if (!prepared) {
			prepare();
			prepared = true;
		}
	}

	private void prepare() {
		try (java.sql.Connection connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(),
				postgres.getUsername(), postgres.getPassword()); java.sql.Statement statement = connection.createStatement()) {
			statement.executeUpdate("INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action) "
					+ "VALUES (1, 'acme-corp', now(), 'idp-group-mapping') ON CONFLICT (id) DO UPDATE SET "
					+ "setup_phase_ended_at = now(), setup_phase_ended_by_action = 'idp-group-mapping'");
		}
		catch (java.sql.SQLException ex) {
			throw new IllegalStateException(ex);
		}
		this.api.ok(this.api.post("/api/v1/metamodel/starter-packages/oea-starter-organization/import", null,
				this.metamodelAdmin));
		this.api.ok(this.api.put("/api/v1/admin/property-visibility/person-metatype",
				Api.object().put("metatypeId", this.jdbc.queryForObject(
						"SELECT person_metatype_id::text FROM organization_model", String.class)),
				this.security, null));
		propertyId = this.jdbc.queryForObject("SELECT id::text FROM property_definitions WHERE name = 'Account ID'",
				String.class);
		restrict(propertyId);
		provider = this.jdbc.queryForObject("""
				INSERT INTO auth_providers (name, issuer, client_id) VALUES ('Mapping IdP', 'https://mapping.example.org', 'oea')
				RETURNING id::text""", String.class);
	}

	// Requirement: Account mapping

	@Test
	void aSecurityAdministratorSetsTheMappingAndItIsAudited() {
		JsonNode mapping = this.api.ok(this.api.put(MAPPING,
				Api.object().put("propertyDefId", propertyId).put("accountAttribute", "idp-subject"), this.security, null));
		assertThat(mapping.path("propertyName").asString()).isEqualTo("Account ID");
		assertThat(mapping.path("accountAttribute").asString()).isEqualTo("idp-subject");
		assertThat(this.api.ok(this.api.get(MAPPING, this.security)).path("propertyDefId").asString()).isEqualTo(propertyId);
		JsonNode audit = JsonMapper.shared().readTree(auditJson("visibility.person-link-mapping-changed"));
		assertThat(audit.path("newPropertyDefId").asString()).isEqualTo(propertyId);
		assertThat(audit.path("newAccountAttribute").asString()).isEqualTo("idp-subject");
	}

	@Test
	void anUnknownAccountAttributeIsRejected() {
		MvcTestResult result = this.api.put(MAPPING,
				Api.object().put("propertyDefId", propertyId).put("accountAttribute", "employee-id"), this.security, null);
		assertThat(Api.status(result)).isEqualTo(422);
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString)).contains("accountAttribute");
	}

	/** data type, mode, mapped to the person metatype, expected invalid field */
	@ParameterizedTest
	@CsvSource({ "string, role-restricted, true, none", "enum, role-restricted, true, propertyDefId",
			"string, role-restricted, false, propertyDefId", "string, public, true, propertyDefId",
			"string, connection-scoped, true, propertyDefId" })
	void thePropertyIsAStringOfThePersonMetatypeAndWriteRestricted(String dataType, String mode, boolean mapped,
			String invalid) {
		String id = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				VALUES (?, ?, ?, ?, CASE WHEN ? = 'connection-scoped' THEN (SELECT belongs_to_metatype_id FROM organization_model) END)
				RETURNING id::text""", String.class, "Candidate " + Api.id(), "c_" + Api.id().replace("-", "").substring(0, 12),
				dataType, mode, mode);
		if (mapped) {
			this.jdbc.update("""
					INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
					VALUES ((SELECT person_metatype_id FROM organization_model), ?::uuid, 'optional', DATE '2026-01-01', 'organization')""",
					id);
		}
		String before = this.jdbc.queryForObject("SELECT property_def_id::text FROM person_link_mapping", String.class);
		MvcTestResult result = this.api.put(MAPPING,
				Api.object().put("propertyDefId", id).put("accountAttribute", "email"), this.security, null);
		if ("none".equals(invalid)) {
			assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
			this.api.ok(this.api.put(MAPPING, Api.object().put("propertyDefId", before).put("accountAttribute", "idp-subject"),
					this.security, null));
		}
		else {
			assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
			assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString)).contains(invalid);
			assertThat(this.jdbc.queryForObject("SELECT property_def_id::text FROM person_link_mapping", String.class))
				.isEqualTo(before);
		}
	}

	@Test
	void aHolderOfPersonManageOnlyReadsTheMappingButCannotSetIt() {
		select("idp-subject");
		assertThat(Api.status(this.api.get(MAPPING, this.personManager))).isEqualTo(200);
		MvcTestResult result = this.api.put(MAPPING,
				Api.object().put("propertyDefId", propertyId).put("accountAttribute", "email"), this.personManager, null);
		assertThat(Api.status(result)).isEqualTo(403);
		assertThat(this.jdbc.queryForObject("SELECT account_attribute FROM person_link_mapping", String.class))
			.isEqualTo("idp-subject");
		assertThat(Api.status(this.api.get(MAPPING, this.anna))).isEqualTo(403);
	}

	@Test
	void theRestrictionCannotBeLiftedWhileThePropertyIsTheMappingProperty() {
		select("idp-subject");
		ArrayNode body = JsonMapper.shared().createArrayNode();
		body.add(Api.object().put("propertyDefId", propertyId).put("visibilityMode", "public")
			.<ObjectNode>set("readRoles", JsonMapper.shared().createArrayNode())
			.<ObjectNode>set("writeRoles", JsonMapper.shared().createArrayNode()));
		MvcTestResult result = this.api.put(VISIBILITY, body, this.security, null);
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(422);
		assertThat(Api.body(result).path("invalidFields").valueStream().map(JsonNode::asString))
			.contains("[0].visibilityMode");
		assertThat(this.jdbc.queryForObject("SELECT visibility_mode FROM property_definitions WHERE id = ?::uuid",
				String.class, propertyId)).isEqualTo("role-restricted");
		// the roles of the restriction can change
		ArrayNode other = JsonMapper.shared().createArrayNode();
		other.add(Api.object().put("propertyDefId", propertyId).put("visibilityMode", "role-restricted")
			.<ObjectNode>set("readRoles", JsonMapper.shared().createArrayNode().add("enterprise-architect").add("viewer"))
			.<ObjectNode>set("writeRoles", JsonMapper.shared().createArrayNode().add("enterprise-architect")));
		assertThat(Api.status(this.api.put(VISIBILITY, other, this.security, null))).isEqualTo(200);
	}

	// Requirement: Account link through the mapping

	@Test
	void anImportLinksTheAccountAndAuditsTheSource() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		bind(maria, subject);
		assertThat(mapped(maria)).isNull();
		int element = person("Maria Müller", subject);
		assertThat(mapped(maria)).isEqualTo(element);
		JsonNode view = view(maria);
		assertThat(view.path("mappedEntityId").asInt()).isEqualTo(element);
		assertThat(view.path("linkSource").asString()).isEqualTo("mapping");
		assertThat(view.path("entityId").isNull()).isTrue();
		assertThat(this.personStore.anchor(maria)).isEqualTo(element);
		JsonNode audit = linkedAudit(maria);
		assertThat(audit.path("source").asString()).isEqualTo("mapping");
		assertThat(audit.path("newEntityId").asInt()).isEqualTo(element);
	}

	@Test
	void aDiagramSaveLinksTheAccount() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		bind(maria, subject);
		String viewpoint = this.jdbc.queryForObject("""
				INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), ?, 'generic',
				(SELECT min(id) FROM persons)) RETURNING id::text""", String.class, "People " + Api.id());
		this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?::uuid, (SELECT person_metatype_id FROM organization_model))",
				viewpoint);
		String diagram = this.api.created(this.api.post("/api/v1/diagrams",
				Api.object().put("name", "People " + Api.id()).put("viewpointId", viewpoint), this.anna)).path("id").asString();
		ObjectNode body = Api.object();
		String key = Api.id();
		ObjectNode entity = Api.object().put("key", key).put("metatypeId", personMetatype()).put("name", "Maria Müller");
		entity.putArray("properties").add(Api.object().put("propertyDefId", propertyId).put("value", subject));
		body.putArray("newEntities").add(entity);
		body.putArray("shapes")
			.add(Api.object().put("id", Api.id()).put("newEntityKey", key).put("x", 0).put("y", 0).put("width", 90)
				.put("height", 60));
		JsonNode saved = this.api.ok(this.api.put("/api/v1/views/diagrams/" + diagram, body, this.anna, "\"1\""));
		int element = saved.path("shapes").get(0).path("entity").path("id").asInt();
		assertThat(mapped(maria)).isEqualTo(element);
	}

	@Test
	void aGoLiveLinksTheAccount() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		bind(maria, subject);
		long owner = personId(this.anna);
		String solution = Api.id();
		this.jdbc.update("INSERT INTO solutions (id, name, status, owner_id, created_by) VALUES (?::uuid, ?, 'approved', ?, ?)",
				solution, "Go-live " + Api.id(), owner, owner);
		String delta = Api.id();
		this.jdbc.update("INSERT INTO entity_deltas (id, solution_id, delta_type, metatype_id, name, created_by) "
				+ "VALUES (?::uuid, ?::uuid, 'new', ?::uuid, 'Maria Go-live', ?)", delta, solution, personMetatype(), owner);
		this.jdbc.update("INSERT INTO entity_delta_property_values (id, delta_id, property_def_id, after_value_text) "
				+ "VALUES (gen_random_uuid(), ?::uuid, ?::uuid, ?)", delta, propertyId, subject);
		this.api.ok(this.api.post("/api/v1/solutions/" + solution + "/go-live", null, this.anna));
		Integer element = this.jdbc.queryForObject("SELECT id FROM entities WHERE name = 'Maria Go-live' ORDER BY id DESC LIMIT 1",
				Integer.class);
		assertThat(mapped(maria)).isEqualTo(element);
	}

	@Test
	void aManualLinkTakesPrecedenceAndAManualLinkToAnElementOfTheMappingIsRefused() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		bind(maria, subject);
		int manual = person("M. Müller", "other-" + Api.id());
		this.api.ok(this.api.put("/api/v1/admin/persons/" + maria + "/entity-link", Api.object().put("entityId", manual),
				this.personManager, null));
		int imported = person("Maria Müller", subject);
		assertThat(mapped(maria)).isNull();
		assertThat(view(maria).path("entityId").asInt()).isEqualTo(manual);
		assertThat(view(maria).path("linkSource").asString()).isEqualTo("manual");
		assertThat(this.personStore.anchor(maria)).isEqualTo(manual);
		assertThat(linkedAudit(maria).path("source").asString()).isEqualTo("manual");
		// another account holds the element through the mapping: a manual link to it is a conflict
		int lena = account("Lena", "lena-" + Api.id() + "@example.org");
		String lenaSubject = "sub-" + Api.id();
		bind(lena, lenaSubject);
		int lenaElement = person("Lena Lang", lenaSubject);
		assertThat(mapped(lena)).isEqualTo(lenaElement);
		MvcTestResult refused = this.api.put("/api/v1/admin/persons/" + maria + "/entity-link",
				Api.object().put("entityId", lenaElement), this.personManager, null);
		assertThat(Api.status(refused)).isEqualTo(409);
		assertThat(imported).isPositive();
	}

	@Test
	void severalElementsWithTheSameValueGiveNoLinkAndAConflict() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		bind(maria, subject);
		int first = person("Maria Müller", subject);
		int second = person("Maria Mueller", subject);
		assertThat(mapped(maria)).isNull();
		JsonNode conflict = view(maria).path("mappingConflict");
		assertThat(conflict.path("entityIds").valueStream().map(JsonNode::asInt)).containsExactlyInAnyOrder(first, second);
		assertThat(conflict.path("accountCount").asInt()).isEqualTo(1);
		assertThat(view(maria).path("linkSource").isNull()).isTrue();
		// one of the elements is deleted: the other links
		delete(second);
		assertThat(mapped(maria)).isEqualTo(first);
		assertThat(view(maria).path("mappingConflict").isNull()).isTrue();
	}

	@Test
	void severalAccountsWithTheSameValueGiveNoLinkAndAConflict() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		int mia = account("Mia", "mia-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		bind(maria, subject);
		String otherProvider = this.jdbc.queryForObject("""
				INSERT INTO auth_providers (name, issuer, client_id) VALUES (?, ?, 'oea') RETURNING id::text""", String.class,
				"Second " + Api.id(), "https://second-" + Api.id() + ".example.org");
		this.api.created(this.api.post("/api/v1/admin/persons/" + mia + "/identities",
				Api.object().put("authProviderId", otherProvider).put("subject", subject), this.security));
		int element = person("Maria Müller", subject);
		assertThat(mapped(maria)).isNull();
		assertThat(mapped(mia)).isNull();
		JsonNode conflict = view(maria).path("mappingConflict");
		assertThat(conflict.path("accountCount").asInt()).isEqualTo(2);
		assertThat(conflict.path("entityIds").get(0).asInt()).isEqualTo(element);
	}

	@Test
	void theDeletionAndTheRestoreOfTheElementEndAndRestoreTheLinkAlsoByCascade() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		bind(maria, subject);
		int unit = this.api.entity(this.anna, this.jdbc.queryForObject("SELECT org_unit_metatype_id::text FROM organization_model",
				String.class), "Purchasing " + Api.id());
		int element = person("Maria Müller", subject);
		this.api.ok(this.api.put("/api/v1/entities/" + element + "/parent", Api.object().put("parentEntityId", unit),
				this.anna, "\"" + version(element) + "\""));
		assertThat(mapped(maria)).isEqualTo(element);
		// the unit is deleted, and the element with it
		delete(unit);
		assertThat(mapped(maria)).isNull();
		assertThat(this.personStore.anchor(maria)).isNull();
		undelete(unit);
		assertThat(mapped(maria)).isEqualTo(element);
		// the element alone
		delete(element);
		assertThat(mapped(maria)).isNull();
		undelete(element);
		assertThat(mapped(maria)).isEqualTo(element);
	}

	@Test
	void theEmailIsMatchedIgnoringCase() {
		select("email");
		String email = "Anna.Beck-" + Api.id() + "@Example.org";
		int account = account("Anna Beck", email);
		int element = person("Anna Beck", email.toLowerCase());
		assertThat(mapped(account)).isEqualTo(element);
		select("idp-subject");
		assertThat(mapped(account)).isNull();
	}

	@Test
	void theSubjectIsMatchedThroughAnAddedIdentityBindingAndEndsWithItsRemoval() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		int element = person("Maria Müller", subject);
		assertThat(mapped(maria)).isNull();
		String identity = bind(maria, subject);
		assertThat(mapped(maria)).isEqualTo(element);
		MvcTestResult removed = this.api.call(HttpMethod.DELETE, "/api/v1/admin/persons/" + maria + "/identities/" + identity,
				null, this.security, null);
		assertThat(Api.status(removed)).isEqualTo(204);
		assertThat(mapped(maria)).isNull();
	}

	@Test
	void clearingTheMappingRemovesTheLinksThroughItAndKeepsManualOnes() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		bind(maria, subject);
		int element = person("Maria Müller", subject);
		int lena = account("Lena", "lena-" + Api.id() + "@example.org");
		int manual = person("Lena Lang", "x-" + Api.id());
		this.api.ok(this.api.put("/api/v1/admin/persons/" + lena + "/entity-link", Api.object().put("entityId", manual),
				this.personManager, null));
		assertThat(mapped(maria)).isEqualTo(element);
		this.api.ok(this.api.put(MAPPING, JsonMapper.shared().readTree("{\"propertyDefId\": null, \"accountAttribute\": null}"),
				this.security, null));
		assertThat(mapped(maria)).isNull();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM persons WHERE mapped_entity_id IS NOT NULL", Integer.class))
			.isZero();
		assertThat(this.personStore.anchor(lena)).isEqualTo(manual);
		assertThat(this.api.ok(this.api.get(MAPPING, this.security)).path("propertyDefId").isNull()).isTrue();
	}

	@Test
	void changingThePersonMetatypeEndsTheMappingAndItsLinks() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		bind(maria, subject);
		int element = person("Maria Müller", subject);
		assertThat(mapped(maria)).isEqualTo(element);
		String person = this.jdbc.queryForObject("SELECT person_metatype_id::text FROM organization_model", String.class);
		String other = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?) RETURNING id::text""",
				String.class, "Other people " + Api.id());
		try {
			this.api.ok(this.api.put("/api/v1/admin/property-visibility/person-metatype", Api.object().put("metatypeId", other),
					this.security, null));
			assertThat(mapped(maria)).isNull();
			assertThat(this.jdbc.queryForObject("SELECT property_def_id FROM person_link_mapping", UUID.class)).isNull();
			assertThat(auditJson("visibility.person-link-mapping-changed")).contains("mapping-invalid");
		}
		finally {
			this.api.ok(this.api.put("/api/v1/admin/property-visibility/person-metatype", Api.object().put("metatypeId", person),
					this.security, null));
		}
	}

	@Test
	void aServiceAccountStaysUnlinkedAndAnInvitedAccountIsLinked() {
		select("email");
		String email = "svc-" + Api.id() + "@example.org";
		Long service = this.jdbc.queryForObject("""
				INSERT INTO persons (person_type, name, email, status) VALUES ('service-account', 'svc', ?, 'active') RETURNING id""",
				Long.class, email);
		int element = person("Service", email);
		assertThat(mapped(service)).isNull();
		String invitedEmail = "invited-" + Api.id() + "@example.org";
		int invited = account("Invited", invitedEmail);
		person("Invited person", invitedEmail);
		assertThat(mapped(invited)).isNotNull();
		assertThat(element).isPositive();
		select("idp-subject");
	}

	@Test
	void deactivatingOffboardingOrUnbindingAnAccountKeepsTheElementAndItsResponsibleConnections() {
		select("idp-subject");
		int maria = account("Maria", "maria-" + Api.id() + "@example.org");
		String subject = "sub-" + Api.id();
		String identity = bind(maria, subject);
		int element = person("Maria Müller", subject);
		String application = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?) RETURNING id::text""",
				String.class, "Application " + Api.id());
		int sap = this.api.entity(this.anna, application, "SAP ERP " + Api.id());
		int responsible = this.api.connection(this.anna, this.jdbc.queryForObject(
				"SELECT responsible_metatype_id::text FROM organization_model", String.class), sap, element);
		// the account must be active to be deactivated
		this.jdbc.update("UPDATE persons SET status = 'active' WHERE id = ?", maria);
		assertThat(mapped(maria)).isEqualTo(element);
		this.api.ok(this.api.call(HttpMethod.POST, "/api/v1/admin/persons/" + maria + "/status",
				Api.object().put("status", "inactive"), this.personManager, null));
		assertThat(mapped(maria)).isNull();
		assertThat(active(element)).isTrue();
		assertThat(active(responsible)).isTrue();
		this.api.ok(this.api.call(HttpMethod.POST, "/api/v1/admin/persons/" + maria + "/status",
				Api.object().put("status", "active"), this.personManager, null));
		assertThat(mapped(maria)).isEqualTo(element);
		this.api.ok(this.api.call(HttpMethod.POST, "/api/v1/admin/persons/" + maria + "/status",
				Api.object().put("status", "offboarded"), this.personManager, null));
		assertThat(mapped(maria)).isNull();
		assertThat(active(element)).isTrue();
		assertThat(active(responsible)).isTrue();
		// unbinding ends the link as well
		int lena = account("Lena", "lena-" + Api.id() + "@example.org");
		String lenaSubject = "sub-" + Api.id();
		String lenaIdentity = bind(lena, lenaSubject);
		int lenaElement = person("Lena Lang", lenaSubject);
		assertThat(mapped(lena)).isEqualTo(lenaElement);
		this.api.call(HttpMethod.DELETE, "/api/v1/admin/persons/" + lena + "/identities/" + lenaIdentity, null, this.security,
				null);
		assertThat(mapped(lena)).isNull();
		assertThat(active(lenaElement)).isTrue();
		assertThat(identity).isNotNull();
	}

	// Requirement: No link of the own account through the mapping

	@Test
	void aWriterCannotLinkTheOwnAccountButCanLinkAnother() {
		select("email");
		String own = ownEmail(this.anna);
		int before = count("SELECT count(*) FROM entities");
		MvcTestResult denied = this.api.writeEntity(this.anna, personMetatype(), "Anna " + Api.id(), propertyId, own);
		assertThat(Api.status(denied)).as(() -> Api.content(denied)).isEqualTo(403);
		assertThat(count("SELECT count(*) FROM entities")).isEqualTo(before);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events WHERE event_type = 'authz.denied' AND details ->> 'reason' = 'self-administration'""",
				Integer.class)).isPositive();
		String otherEmail = "other-" + Api.id() + "@example.org";
		int other = account("Other", otherEmail);
		int element = person("Other", otherEmail);
		assertThat(mapped(other)).isEqualTo(element);
		select("idp-subject");
	}

	@Test
	void aHolderOfPersonManageCannotBindAnElementsSubjectToTheOwnAccount() {
		select("idp-subject");
		String subject = "own-" + Api.id();
		person("Somebody", subject);
		long own = personId(this.security);
		MvcTestResult denied = this.api.post("/api/v1/admin/persons/" + own + "/identities",
				Api.object().put("authProviderId", provider).put("subject", subject), this.security);
		assertThat(Api.status(denied)).as(() -> Api.content(denied)).isEqualTo(403);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM person_identities WHERE subject = ?", Integer.class,
				subject)).isZero();
		assertThat(mapped(own)).isNull();
	}

	// helpers

	private void select(String attribute) {
		this.api.ok(this.api.put(MAPPING, Api.object().put("propertyDefId", propertyId).put("accountAttribute", attribute),
				this.security, null));
	}

	private void restrict(String property) {
		ArrayNode body = JsonMapper.shared().createArrayNode();
		body.add(Api.object().put("propertyDefId", property).put("visibilityMode", "role-restricted")
			.<ObjectNode>set("readRoles", JsonMapper.shared().createArrayNode().add("enterprise-architect"))
			.<ObjectNode>set("writeRoles", JsonMapper.shared().createArrayNode().add("enterprise-architect")));
		this.api.ok(this.api.put(VISIBILITY, body, this.security, null));
	}

	private int account(String name, String email) {
		JsonNode created = this.api.created(this.api.post("/api/v1/admin/persons",
				Api.object().put("name", name).put("email", email), this.personManager));
		return created.path("id").asInt();
	}

	private String bind(int personId, String subject) {
		return this.api.created(this.api.post("/api/v1/admin/persons/" + personId + "/identities",
				Api.object().put("authProviderId", provider).put("subject", subject), this.security)).path("id").asString();
	}

	private int person(String name, String accountId) {
		return this.api.ok(this.api.writeEntity(this.anna, personMetatype(), name, propertyId, accountId))
			.path("items")
			.get(0)
			.path("id")
			.asInt();
	}

	private String personMetatype() {
		return this.jdbc.queryForObject("SELECT person_metatype_id::text FROM organization_model", String.class);
	}

	private Integer mapped(long personId) {
		return this.jdbc.queryForObject("SELECT mapped_entity_id FROM persons WHERE id = ?", Integer.class, personId);
	}

	private JsonNode view(int personId) {
		return this.api.ok(this.api.get("/api/v1/admin/persons/" + personId, this.personManager));
	}

	private JsonNode linkedAudit(long personId) {
		return JsonMapper.shared().readTree(this.jdbc.queryForObject("""
				SELECT details::text FROM audit.events WHERE event_type = 'person.account-linked' AND target_id = ?
				ORDER BY id DESC LIMIT 1""", String.class, Long.toString(personId)));
	}

	private String auditJson(String eventType) {
		return this.jdbc.queryForObject("SELECT details::text FROM audit.events WHERE event_type = ? ORDER BY id DESC LIMIT 1",
				String.class, eventType);
	}

	private int version(int entity) {
		return this.jdbc.queryForObject("SELECT version FROM entities WHERE id = ?", Integer.class, entity);
	}

	private boolean active(int entity) {
		return this.jdbc.queryForObject("SELECT deleted_at IS NULL FROM entities WHERE id = ?", Boolean.class, entity);
	}

	private void delete(int entity) {
		MvcTestResult result = this.api.call(HttpMethod.DELETE, "/api/v1/entities/" + entity, null, this.anna,
				"\"" + version(entity) + "\"");
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
	}

	private void undelete(int entity) {
		MvcTestResult result = this.api.call(HttpMethod.POST, "/api/v1/entities/" + entity + "/undelete", null, this.anna,
				"\"" + version(entity) + "\"");
		assertThat(Api.status(result)).as(() -> Api.content(result)).isEqualTo(200);
	}

	private long personId(TestSession session) {
		return this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class, session.sessionId());
	}

	private String ownEmail(TestSession session) {
		return this.jdbc.queryForObject("SELECT email FROM persons WHERE id = ?", String.class, personId(session));
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-person-link-mapping").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

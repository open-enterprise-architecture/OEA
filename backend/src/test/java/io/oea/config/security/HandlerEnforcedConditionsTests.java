package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.api.admin.AdminController;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.security.AuthorizationMatrix.Operation;
import io.oea.testsupport.security.AuthorizationMatrix;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Contract conditions enforced by the handler deny a principal holding only the base permissions
 * (ADR-032 §8, design D3 of us-046-create-catalog). Runs on an own database: the probe roles come
 * from IdP group mappings, whose stored providers must not reach the shared test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HandlerEnforcedConditionsTests {

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
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Autowired
	private ContractAuthorizationRules rules;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@Autowired
	private JdbcTemplate jdbc;

	/**
	 * The scoping type of the probes of {@code createConnection} and {@code batchWriteEntities}:
	 * a connection metatype that a property definition uses as {@code scopingConnectionType}
	 * (design D4 of us-124-property-visibility).
	 */
	static final String PROBE_SCOPING_TYPE = "00000000-0000-4000-8000-0000000c0de1";

	@BeforeEach
	void scopingType() {
		this.jdbc.update("""
				INSERT INTO metatypes (id, config_id, name, is_connection)
				VALUES (?::uuid, '00000000-0000-0000-0000-000000000001', 'Probe Domain Assignment', true)
				ON CONFLICT (id) DO NOTHING""", PROBE_SCOPING_TYPE);
		this.jdbc.update("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode, scoping_connection_type_id)
				SELECT 'Probe Classification', 'probe_classification', 'string', 'connection-scoped', ?::uuid
				WHERE NOT EXISTS (SELECT 1 FROM property_definitions WHERE name = 'Probe Classification')""",
				PROBE_SCOPING_TYPE);
		// the role conditions of the service account operations (us-142-service-accounts)
		this.jdbc.update("""
				INSERT INTO authorization_roles (id, name, is_builtin) VALUES (?, 'Probe account manager', false)
				ON CONFLICT (id) DO NOTHING""", AdminController.PROBE_ACCOUNT_MANAGER);
		this.jdbc.update("""
				INSERT INTO role_permissions (role_id, permission_id) VALUES (?, 'service-account:manage')
				ON CONFLICT DO NOTHING""", AdminController.PROBE_ACCOUNT_MANAGER);
		// the shared dashboard that the probes of the dashboard writes address (us-055-dashboards)
		if (this.jdbc.queryForObject("SELECT count(*) FROM dashboards WHERE id = ?::uuid", Integer.class,
				"00000000-0000-4000-8000-0000000d0001") == 0) {
			Long owner = this.jdbc.queryForObject("""
					INSERT INTO persons (name, email, status) VALUES ('Probe dashboard owner', 'probe-dashboards@example.org',
					'active') RETURNING id""", Long.class);
			this.jdbc.update("""
					INSERT INTO dashboards (id, name, scope, owner_id, created_by, updated_by)
					VALUES (?::uuid, 'Probe dashboard', 'instance', ?, ?, ?)""", "00000000-0000-4000-8000-0000000d0001",
					owner, owner, owner);
		}
		// the diagram that the probe of the layout save addresses (us-074-diagram-connections)
		if (this.jdbc.queryForObject("SELECT count(*) FROM diagrams WHERE id = ?::uuid", Integer.class,
				"00000000-0000-4000-8000-0000000d1a01") == 0) {
			Long owner = this.jdbc.queryForObject("""
					INSERT INTO persons (name, email, status) VALUES ('Probe diagram owner', 'probe-diagrams@example.org',
					'active') RETURNING id""", Long.class);
			String viewpoint = this.jdbc.queryForObject("""
					INSERT INTO viewpoints (id, name, notation, created_by) VALUES (gen_random_uuid(), 'Probe viewpoint',
					'bpmn2', ?) RETURNING id::text""", String.class, owner);
			this.jdbc.update("""
					INSERT INTO diagrams (id, name, viewpoint_id, created_by, updated_by)
					VALUES (?::uuid, 'Probe diagram', ?::uuid, ?, ?)""", "00000000-0000-4000-8000-0000000d1a01", viewpoint,
					owner, owner);
		}
		// the connections of the scoping type that the probes of the deletion address (s17-entity-delete): an active one
		// to delete and a deleted one to restore, between two elements
		if (this.jdbc.queryForObject("SELECT count(*) FROM entities WHERE id = 2000000001", Integer.class) == 0) {
			Long owner = this.jdbc.queryForObject("""
					INSERT INTO persons (name, email, status) VALUES ('Probe deletion owner', 'probe-deletion@example.org',
					'active') RETURNING id""", Long.class);
			UUID element = this.jdbc.queryForObject("""
					INSERT INTO metatypes (config_id, name, is_connection)
					VALUES ('00000000-0000-0000-0000-000000000001', 'Probe Deletion Element', false) RETURNING id""",
					UUID.class);
			this.jdbc.update("""
					INSERT INTO entities (id, metatype_id, name, created_by) VALUES
					(2000000003, ?, 'Probe source', ?), (2000000004, ?, 'Probe target', ?)""", element, owner, element,
					owner);
			this.jdbc.update("""
					INSERT INTO entities (id, metatype_id, name, created_by) VALUES (2000000001, ?::uuid, 'Probe assignment', ?)""",
					PROBE_SCOPING_TYPE, owner);
			this.jdbc.update("""
					INSERT INTO entities (id, metatype_id, name, created_by, deleted_at, deleted_by)
					VALUES (2000000002, ?::uuid, 'Probe deleted assignment', ?, now(), ?)""", PROBE_SCOPING_TYPE, owner,
					owner);
			this.jdbc.update("""
					INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES
					(2000000001, 2000000003, 2000000004), (2000000002, 2000000003, 2000000004)""");
		}
		if (this.jdbc.queryForObject("SELECT count(*) FROM machine_credentials WHERE client_id = ?::uuid", Integer.class,
				AdminController.PROBE_SERVICE_ACCOUNT) == 0) {
			Long person = this.jdbc.queryForObject("""
					INSERT INTO persons (person_type, name, status) VALUES ('service-account', 'probe-account', 'active')
					RETURNING id""", Long.class);
			this.jdbc.update("""
					INSERT INTO machine_credentials (client_id, person_id, client_secret_hash, expires_at)
					VALUES (?::uuid, ?, 'probe', now() + interval '90 days')""", AdminController.PROBE_SERVICE_ACCOUNT,
					person);
		}
	}

	@Test
	void conditionsEnforcedByTheHandlerDenyHoldersOfTheBasePermissionsOnly() throws Exception {
		List<Operation> enforced = AuthorizationMatrix.operations(this.requestMappingHandlerMapping, this.rules)
			.stream()
			.filter((operation) -> operation.conditions() != null)
			.toList();
		assertThat(enforced).isNotEmpty();
		List<String> permissionIds = this.jdbc.queryForList("SELECT id FROM permissions", String.class);
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		List<String> violations = new ArrayList<>();
		for (Operation operation : enforced) {
			TestSession probe = principals.personWithIdpRole(operation.conditions().probeRole());
			MvcTestResult result = this.mvc.method(HttpMethod.valueOf(operation.method()))
				.uri(operation.conditions().probePath().isEmpty() ? operation.path() : operation.conditions().probePath())
				.cookie(new Cookie(SessionCookie.NAME, probe.token()))
				.header(CsrfOriginFilter.HEADER, probe.csrfToken())
				// the writes of versioned elements need the header before the handler runs
				.header("If-Match", "\"1\"")
				.contentType(operation.contentType())
				.content(operation.conditions().probeBody())
				.exchange();
			int status = result.getResponse().getStatus();
			String body = result.getResponse().getContentAsString();
			// the generic problem of the filter, naming no permission (REQ-158)
			if (status != 403 || !body.contains("urn:oea:problem:forbidden")
					|| permissionIds.stream().anyMatch(body::contains)) {
				violations.add(operation + " as " + operation.conditions().probeRole() + ": " + status + " " + body);
			}
		}
		assertThat(violations).isEmpty();
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-conditions").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

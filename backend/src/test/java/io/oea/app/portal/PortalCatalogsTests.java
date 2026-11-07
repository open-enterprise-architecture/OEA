package io.oea.app.portal;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shared catalogs of the portal (s9-portal-catalogs): portal/portal-catalogs on an own
 * database. Kurt is an enterprise architect, Petra holds only the role {@code portal-reader}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PortalCatalogsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String PORTAL = "/api/v1/portal/catalogs";

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

	private TestSession kurt;

	private TestSession petra;

	private long kurtId;

	private UUID status;

	private UUID type;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = this.principals.personWithIdpRole("enterprise-architect");
		this.petra = this.principals.personWithIdpRole("portal-reader");
		this.kurtId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.kurt.sessionId());
		if (this.jdbc.queryForObject("SELECT count(*) FROM metatypes", Long.class) == 0) {
			TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
			assertThat(this.mvc.post()
				.uri("/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import")
				.cookie(new Cookie(SessionCookie.NAME, metamodelAdmin.token()))
				.header(CsrfOriginFilter.HEADER, metamodelAdmin.csrfToken())
				.exchange()).hasStatus(HttpStatus.OK);
		}
		this.status = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Status'", UUID.class);
		this.type = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?)
				RETURNING id""", UUID.class, "Portal Type " + suffix());
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'imported')""", this.type, this.status);
	}

	// Requirement: List the shared catalogs

	@Test
	void sharedCatalogsOnly() {
		String tag = suffix();
		String inventory = catalog("Application Inventory " + tag, "instance");
		String interfaces = catalog("interfaces " + tag, "instance");
		String candidates = catalog("Kurt's Candidates " + tag, "personal");
		JsonNode list = ok(get(PORTAL, this.petra));
		List<String> ids = new ArrayList<>();
		List<String> names = new ArrayList<>();
		list.forEach((catalog) -> {
			ids.add(catalog.path("id").asString());
			names.add(catalog.path("name").asString());
			assertThat(catalog.path("scope").asString()).isEqualTo("instance");
		});
		assertThat(ids).contains(inventory, interfaces).doesNotContain(candidates);
		assertThat(ids.indexOf(inventory)).isLessThan(ids.indexOf(interfaces));
		assertThat(names).isSortedAccordingTo(Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT)));
	}

	@Test
	void ownPersonalCatalogNotInThePortal() {
		String candidates = catalog("Kurt's Candidates " + suffix(), "personal");
		List<String> ids = new ArrayList<>();
		ok(get(PORTAL, this.kurt)).forEach((catalog) -> ids.add(catalog.path("id").asString()));
		assertThat(ids).doesNotContain(candidates);
	}

	@Test
	void withoutPortalPermission() {
		TestSession nobody = this.principals.personWithoutRole();
		assertThat(get(PORTAL, nobody)).hasStatus(HttpStatus.FORBIDDEN);
		String inventory = catalog("Application Inventory " + suffix(), "instance");
		assertThat(get(PORTAL + "/" + inventory + "/query", nobody)).hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Query a shared catalog read-only

	@Test
	void rowsOfASharedCatalogEqualTheCatalogQuery() {
		entity("Intranet", "active");
		entity("CRM System", "active");
		entity("ERP", "planned");
		String inventory = catalog("Application Inventory " + suffix(), "instance");
		JsonNode portal = ok(get(PORTAL + "/" + inventory + "/query", this.petra));
		JsonNode work = ok(get("/api/v1/catalogs/" + inventory + "/query", this.kurt));
		assertThat(portal).isEqualTo(work);
		List<String> keys = new ArrayList<>();
		portal.path("columns").forEach((column) -> keys.add(column.path("key").asString()));
		assertThat(keys).containsExactly("name", "status");
		assertThat(names(portal)).containsExactly("CRM System", "ERP", "Intranet");
	}

	@Test
	void sortingAndPaging() {
		for (int i = 0; i < 12; i++) {
			entity("App %02d".formatted(i), null);
		}
		String inventory = catalog("Application Inventory " + suffix(), "instance");
		JsonNode result = ok(get(PORTAL + "/" + inventory + "/query?sort=name:desc&pageSize=10", this.petra));
		assertThat(result.path("rows").size()).isEqualTo(10);
		assertThat(names(result).get(0)).isEqualTo("App 11");
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(12);
		assertThat(result.path("page").path("totalPages").asInt()).isEqualTo(2);
		assertThat(result.path("sort").path("column").asString()).isEqualTo("name");
		assertThat(result.path("sort").path("direction").asString()).isEqualTo("desc");
		assertThat(names(ok(get(PORTAL + "/" + inventory + "/query?sort=name:desc&pageSize=10&page=1", this.petra))))
			.containsExactly("App 01", "App 00");
	}

	@Test
	void personalCatalogIsNotFoundAlsoForItsCreator() {
		String candidates = catalog("Kurt's Candidates " + suffix(), "personal");
		assertThat(get(PORTAL + "/" + candidates + "/query", this.petra)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(PORTAL + "/" + candidates + "/query", this.kurt)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(PORTAL + "/" + UUID.randomUUID() + "/query", this.petra)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void invalidParameters() {
		String inventory = catalog("Application Inventory " + suffix(), "instance");
		assertInvalid(get(PORTAL + "/" + inventory + "/query?sort=unknown:asc", this.petra), "sort");
		assertInvalid(get(PORTAL + "/" + inventory + "/query?pageSize=201", this.petra), "pageSize");
		assertInvalid(get(PORTAL + "/" + inventory + "/query?page=-1", this.petra), "page");
	}

	// Requirement: Visibility in the portal

	@Test
	void restrictedPropertyIsNoColumnAndNotSortable() {
		String column = "secret_" + suffix();
		UUID secret = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				VALUES (?, ?, 'string', 'role-restricted') RETURNING id""", UUID.class, "Secret " + column, column);
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from, scope)
				VALUES (?, ?, 'optional', DATE '2026-01-01', 'organization')""", this.type, secret);
		String inventory = catalog("Application Inventory " + suffix(), "instance");
		List<String> keys = new ArrayList<>();
		ok(get(PORTAL + "/" + inventory + "/query", this.petra)).path("columns")
			.forEach((node) -> keys.add(node.path("key").asString()));
		assertThat(keys).containsExactly("name", "status");
		assertInvalid(get(PORTAL + "/" + inventory + "/query?sort=" + column + ":asc", this.petra), "sort");
	}

	@Test
	void deletedEntity() {
		entity("Kept", null);
		int deleted = entity("Deleted", null);
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.kurtId, deleted);
		JsonNode result = ok(get(PORTAL + "/" + catalog("Application Inventory " + suffix(), "instance") + "/query",
				this.petra));
		assertThat(names(result)).containsExactly("Kept");
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(1);
	}

	private int entity(String name, String statusValue) {
		Integer id = this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class,
				this.type, name, this.kurtId);
		if (statusValue != null) {
			this.jdbc.update(
					"INSERT INTO entity_property_values (entity_id, property_def_id, value_enum) VALUES (?, ?, ?)", id,
					this.status, statusValue);
		}
		return id;
	}

	private String catalog(String name, String scope) {
		MvcTestResult created = this.mvc.post()
			.uri("/api/v1/catalogs")
			.cookie(new Cookie(SessionCookie.NAME, this.kurt.token()))
			.header(CsrfOriginFilter.HEADER, this.kurt.csrfToken())
			.contentType("application/json")
			.content(JsonMapper.shared()
				.createObjectNode()
				.put("name", name)
				.put("primaryMetatypeId", this.type.toString())
				.put("scope", scope)
				.toString())
			.exchange();
		assertThat(created).as(() -> content(created)).hasStatus(HttpStatus.CREATED);
		return body(created).path("id").asString();
	}

	private MvcTestResult get(String path, TestSession session) {
		return this.mvc.get().uri(path).cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
	}

	private static List<String> names(JsonNode result) {
		List<String> names = new ArrayList<>();
		result.path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		return names;
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.BAD_REQUEST);
		List<String> fields = new ArrayList<>();
		body(result).path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
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
		return JsonMapper.shared().readTree(content(result));
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-portal").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

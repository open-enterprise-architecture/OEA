package io.oea.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec catalog/catalog-query on an own database with the starter package imported (US-049,
 * REQ-046; design D1–D4 of us-049-show-catalog-data). Each test queries its own metatype, which
 * maps the starter property "Status", so that the rows of one test do not appear in another.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogQueryTests {

	private static final PostgreSQLContainer postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CATALOGS = "/api/v1/catalogs";

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

	private long architectId;

	private UUID status;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = this.principals.personWithIdpRole("enterprise-architect");
		this.architectId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.architect.sessionId());
		if (count("SELECT count(*) FROM metatypes") == 0) {
			TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
			assertThat(this.mvc.post()
				.uri("/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import")
				.cookie(new Cookie(SessionCookie.NAME, metamodelAdmin.token()))
				.header(CsrfOriginFilter.HEADER, metamodelAdmin.csrfToken())
				.exchange()).hasStatus(HttpStatus.OK);
		}
		this.status = this.jdbc.queryForObject("SELECT id FROM property_definitions WHERE name = 'Status'", UUID.class);
	}

	// Requirement: Rows of the primary metatype

	@Test
	void catalogWithThreeApplications() {
		UUID type = metatype("2026-01-01");
		entity(type, "Intranet", "active");
		entity(type, "CRM System", "active");
		entity(type, "ERP", "planned");
		JsonNode result = ok(query(catalog(type, "instance", this.architect), "", this.architect));
		assertThat(names(result)).containsExactly("CRM System", "ERP", "Intranet");
		assertThat(result.path("rows").get(1).path("values").path("status").asString()).isEqualTo("planned");
		assertThat(result.path("rows").get(0).path("entityId").isInt()).isTrue();
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(3);
		assertThat(result.path("page").path("totalPages").asInt()).isEqualTo(1);
		assertThat(result.path("sort").isNull() || result.path("sort").isMissingNode()).isTrue();
	}

	@Test
	void emptyRepository() {
		JsonNode result = ok(query(catalog(metatype("2026-01-01"), "instance", this.architect), "", this.architect));
		assertThat(result.path("rows").size()).isZero();
		assertThat(result.path("page").path("totalElements").asInt()).isZero();
		assertThat(result.path("page").path("totalPages").asInt()).isZero();
	}

	// Requirement: Default columns

	@Test
	void defaultColumnsOfAnApplicationCatalog() {
		UUID component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
		JsonNode result = ok(query(catalog(component, "instance", this.architect), "", this.architect));
		assertThat(keys(result)).containsExactly("name", "status");
		assertThat(result.path("columns").get(0).path("label").asString()).isEqualTo("Name");
		assertThat(result.path("columns").get(1).path("label").asString()).isEqualTo("Status");
		assertThat(result.path("columns").get(1).path("sortable").asBoolean()).isTrue();
	}

	@Test
	void applicationWithoutStatusShowsAnEmptyCell() {
		UUID type = metatype("2026-01-01");
		entity(type, "Without Status", null);
		JsonNode row = ok(query(catalog(type, "instance", this.architect), "", this.architect)).path("rows").get(0);
		assertThat(row.path("values").has("status")).isTrue();
		assertThat(row.path("values").path("status").isNull()).isTrue();
	}

	@Test
	void statusMappedOnlyInTheFutureIsNoColumn() {
		JsonNode result = ok(query(catalog(metatype("2999-01-01"), "instance", this.architect), "", this.architect));
		assertThat(keys(result)).containsExactly("name");
	}

	// Requirement: Sorting

	@Test
	void sortByNameDescending() {
		UUID type = metatype("2026-01-01");
		entity(type, "beta", null);
		entity(type, "Alpha", null);
		entity(type, "Gamma", null);
		JsonNode result = ok(query(catalog(type, "instance", this.architect), "sort=name:desc", this.architect));
		assertThat(names(result)).containsExactly("Gamma", "beta", "Alpha");
		assertThat(result.path("sort").path("column").asString()).isEqualTo("name");
		assertThat(result.path("sort").path("direction").asString()).isEqualTo("desc");
	}

	@Test
	void sortByStatusWithMissingValuesLast() {
		UUID type = metatype("2026-01-01");
		entity(type, "A without", null);
		entity(type, "B planned", "planned");
		entity(type, "C active", "active");
		String catalog = catalog(type, "instance", this.architect);
		assertThat(names(ok(query(catalog, "sort=status:asc", this.architect))))
			.containsExactly("C active", "B planned", "A without");
		assertThat(names(ok(query(catalog, "sort=status:desc", this.architect))))
			.containsExactly("B planned", "C active", "A without");
	}

	@Test
	void unknownSortColumn() {
		String catalog = catalog(metatype("2026-01-01"), "instance", this.architect);
		assertInvalid(query(catalog, "sort=owner:asc", this.architect), "sort");
		assertInvalid(query(catalog, "sort=name:up", this.architect), "sort");
	}

	@Test
	void severalSortPairs() {
		assertInvalid(query(catalog(metatype("2026-01-01"), "instance", this.architect), "sort=name:asc,status:desc",
				this.architect), "sort");
	}

	// Requirement: Pagination

	@Test
	void secondPageSortedByName() {
		UUID type = metatype("2026-01-01");
		for (int i = 1; i <= 60; i++) {
			entity(type, "Application %02d".formatted(i), null);
		}
		JsonNode result = ok(query(catalog(type, "instance", this.architect), "page=1&pageSize=20&sort=name:asc",
				this.architect));
		List<String> expected = new ArrayList<>();
		for (int i = 21; i <= 40; i++) {
			expected.add("Application %02d".formatted(i));
		}
		assertThat(names(result)).containsExactlyElementsOf(expected);
		assertThat(result.path("page").path("page").asInt()).isEqualTo(1);
		assertThat(result.path("page").path("pageSize").asInt()).isEqualTo(20);
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(60);
		assertThat(result.path("page").path("totalPages").asInt()).isEqualTo(3);
	}

	@Test
	void defaultPageSize() {
		UUID type = metatype("2026-01-01");
		for (int i = 1; i <= 120; i++) {
			entity(type, "Application %03d".formatted(i), null);
		}
		JsonNode result = ok(query(catalog(type, "instance", this.architect), "", this.architect));
		assertThat(result.path("rows").size()).isEqualTo(50);
		assertThat(result.path("page").path("pageSize").asInt()).isEqualTo(50);
		assertThat(result.path("page").path("totalPages").asInt()).isEqualTo(3);
	}

	@Test
	void pageBeyondTheLastOne() {
		UUID type = metatype("2026-01-01");
		entity(type, "Only", null);
		JsonNode result = ok(query(catalog(type, "instance", this.architect), "page=5", this.architect));
		assertThat(result.path("rows").size()).isZero();
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(1);
	}

	@Test
	void invalidPagingParameters() {
		String catalog = catalog(metatype("2026-01-01"), "instance", this.architect);
		assertInvalid(query(catalog, "pageSize=201", this.architect), "pageSize");
		assertInvalid(query(catalog, "pageSize=0", this.architect), "pageSize");
		assertInvalid(query(catalog, "page=-1", this.architect), "page");
		assertInvalid(query(catalog, "page=abc", this.architect), "page");
	}

	// Requirement: Visibility of the query result

	@Test
	void personalCatalogOfAnotherPerson() {
		String catalog = catalog(metatype("2026-01-01"), "personal", this.architect);
		TestSession other = this.principals.personWithIdpRole("enterprise-architect");
		assertThat(query(catalog, "", other)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(query(catalog, "", this.architect)).hasStatus(HttpStatus.OK);
	}

	@Test
	void foreignPersonalCatalogIsNotFoundBeforeInvalidParameters() {
		String catalog = catalog(metatype("2026-01-01"), "personal", this.architect);
		TestSession other = this.principals.personWithIdpRole("viewer");
		assertThat(query(catalog, "sort=owner:asc", other)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void unknownCatalog() {
		assertThat(query(UUID.randomUUID().toString(), "", this.architect)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void viewerReadsASharedCatalog() {
		UUID type = metatype("2026-01-01");
		entity(type, "Visible", "active");
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		assertThat(names(ok(query(catalog(type, "instance", this.architect), "", viewer)))).containsExactly("Visible");
	}

	@Test
	void principalWithoutReadPermission() {
		String catalog = catalog(metatype("2026-01-01"), "instance", this.architect);
		assertThat(query(catalog, "", this.principals.personWithoutRole())).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void deletedEntity() {
		UUID type = metatype("2026-01-01");
		entity(type, "Kept", null);
		int deleted = entity(type, "Deleted", null);
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.architectId,
				deleted);
		JsonNode result = ok(query(catalog(type, "instance", this.architect), "", this.architect));
		assertThat(names(result)).containsExactly("Kept");
		assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(1);
	}

	@Test
	void restrictedPropertyIsNoColumn() {
		UUID type = metatype("2026-01-01");
		UUID secret = this.jdbc.queryForObject("""
				INSERT INTO property_definitions (name, sql_column_name, data_type, visibility_mode)
				VALUES (?, ?, 'string', 'role-restricted') RETURNING id""", UUID.class, "Secret " + type,
				"secret_" + type.toString().substring(0, 8));
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from)
				VALUES (?, ?, 'optional', DATE '2026-01-01')""", type, secret);
		assertThat(keys(ok(query(catalog(type, "instance", this.architect), "", this.architect))))
			.containsExactly("name", "status");
	}

	/** A metatype of the instance configuration that maps "Status" from the given date. */
	private UUID metatype(String statusValidFrom) {
		UUID type = this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', ?)
				RETURNING id""", UUID.class, "Query Type " + UUID.randomUUID().toString().substring(0, 8));
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (metatype_id, property_def_id, necessity, valid_from)
				VALUES (?, ?, 'optional', ?::date)""", type, this.status, statusValidFrom);
		return type;
	}

	private int entity(UUID type, String name, String statusValue) {
		Integer id = this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class, type,
				name, this.architectId);
		if (statusValue != null) {
			this.jdbc.update(
					"INSERT INTO entity_property_values (entity_id, property_def_id, value_enum) VALUES (?, ?, ?)", id,
					this.status, statusValue);
		}
		return id;
	}

	private String catalog(UUID type, String scope, TestSession session) {
		MvcTestResult created = this.mvc.post()
			.uri(CATALOGS)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(JsonMapper.shared()
				.createObjectNode()
				.put("name", "Catalog " + UUID.randomUUID())
				.put("primaryMetatypeId", type.toString())
				.put("scope", scope)
				.toString())
			.exchange();
		assertThat(created).hasStatus(HttpStatus.CREATED);
		return body(created).path("id").asString();
	}

	private MvcTestResult query(String catalogId, String parameters, TestSession session) {
		return this.mvc.get()
			.uri(CATALOGS + "/" + catalogId + "/query" + (parameters.isEmpty() ? "" : "?" + parameters))
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.exchange();
	}

	private static void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:validation-failed");
		List<String> fields = new ArrayList<>();
		problem.path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).containsExactly(field);
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static List<String> names(JsonNode result) {
		List<String> names = new ArrayList<>();
		result.path("rows").forEach((row) -> names.add(row.path("values").path("name").asString()));
		return names;
	}

	private static List<String> keys(JsonNode result) {
		List<String> keys = new ArrayList<>();
		result.path("columns").forEach((column) -> keys.add(column.path("key").asString()));
		return keys;
	}

	private int count(String sql) {
		Integer count = this.jdbc.queryForObject(sql, Integer.class);
		return (count != null) ? count : 0;
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
			return Files.createTempDirectory("oea-catalog-query").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

package io.oea.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.audit.verify.ChainVerifier;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.sql.Connection;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec catalog/catalog-definition on an own database with the starter package imported (US-046,
 * REQ-043; design D3–D6 of us-046-create-catalog).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

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

	private UUID component;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = this.principals.personWithIdpRole("enterprise-architect");
		if (count("SELECT count(*) FROM metatypes") == 0) {
			TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
			assertThat(this.mvc.post()
				.uri("/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import")
				.cookie(new Cookie(SessionCookie.NAME, metamodelAdmin.token()))
				.header(CsrfOriginFilter.HEADER, metamodelAdmin.csrfToken())
				.exchange()).hasStatus(HttpStatus.OK);
		}
		this.component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
	}

	// Requirement: Create a catalog

	@Test
	void catalogCreated() {
		String name = unique("Application Inventory");
		MvcTestResult result = create(catalog(name, "instance").put("defaultJoinMode", "aggregate"), this.architect);
		assertThat(result).hasStatus(HttpStatus.CREATED);
		JsonNode created = body(result);
		assertThat(created.path("name").asString()).isEqualTo(name);
		assertThat(created.path("primaryMetatypeId").asString()).isEqualTo(this.component.toString());
		assertThat(created.path("primaryMetatypeName").asString()).isEqualTo("Application Component");
		assertThat(created.path("scope").asString()).isEqualTo("instance");
		assertThat(created.path("defaultJoinMode").asString()).isEqualTo("aggregate");
		assertThat(created.path("createdBy").path("principalType").asString()).isEqualTo("person");
		assertThat(created.path("createdBy").path("displayName").asString()).isEqualTo("Holder of enterprise-architect");
		for (String list : List.of("columns", "joinDefinitions", "savedFilters", "savedViews")) {
			assertThat(created.path(list).isArray()).as(list).isTrue();
			assertThat(created.path(list).size()).as(list).isZero();
		}
		assertThat(result.getResponse().getHeader("Location"))
			.isEqualTo(CATALOGS + "/" + created.path("id").asString());
		assertThat(names(list(this.architect))).contains(name);
	}

	@Test
	void defaultJoinModeOmitted() {
		MvcTestResult result = create(catalog(unique("Without Join Mode"), "instance"), this.architect);
		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(body(result).path("defaultJoinMode").asString()).isEqualTo("aggregate");
	}

	@Test
	void nameAndDescriptionAreStoredTrimmedAndAsGiven() {
		String name = unique("Padded");
		JsonNode created = body(create(catalog("  " + name + "  ", "instance").put("description", "For audits"),
				this.architect));
		assertThat(created.path("name").asString()).isEqualTo(name);
		assertThat(created.path("description").asString()).isEqualTo("For audits");
	}

	// Requirement: Catalog names are unique

	@Test
	void sameNameAgain() {
		String name = unique("Application Inventory");
		assertThat(create(catalog(name, "instance"), this.architect)).hasStatus(HttpStatus.CREATED);
		int before = count("SELECT count(*) FROM catalogs");
		MvcTestResult result = create(catalog(" " + name.toLowerCase() + " ", "personal"), this.architect);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:catalog-name-taken");
		assertThat(problem.path("detail").asString()).isEqualTo("A catalog with this name already exists");
		assertThat(problem.path("invalidFields").toString()).isEqualTo("[\"name\"]");
		assertThat(count("SELECT count(*) FROM catalogs")).isEqualTo(before);
	}

	// Requirement: Catalog input is validated

	@Test
	void unknownPrimaryMetatype() {
		ObjectNode request = catalog(unique("Unknown Type"), "instance").put("primaryMetatypeId",
				UUID.randomUUID().toString());
		int before = count("SELECT count(*) FROM catalogs");
		assertInvalid(create(request, this.architect), "primaryMetatypeId");
		assertThat(count("SELECT count(*) FROM catalogs")).isEqualTo(before);
	}

	@Test
	void nameTooLong() {
		assertInvalid(create(catalog("n".repeat(256), "instance"), this.architect), "name");
	}

	@Test
	void otherInvalidFields() {
		assertInvalid(create(catalog("   ", "instance"), this.architect), "name");
		assertInvalid(create(catalog(unique("Long Description"), "instance").put("description", "d".repeat(1001)),
				this.architect), "description");
		assertInvalid(create(catalog(unique("Team Scope"), "team"), this.architect), "scope");
		assertInvalid(create(catalog(unique("Column Mode"), "instance").put("defaultJoinMode", "columns"),
				this.architect), "defaultJoinMode");
		ObjectNode withoutType = catalog(unique("Without Type"), "instance");
		withoutType.remove("primaryMetatypeId");
		assertInvalid(create(withoutType, this.architect), "primaryMetatypeId");
	}

	// Requirement: Creation permission depends on the scope

	@Test
	void viewerCreatesASharedCatalog() throws Exception {
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		int before = count("SELECT count(*) FROM catalogs");
		MvcTestResult result = create(catalog(unique("Shared by Viewer"), "instance"), viewer);
		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:forbidden");
		assertThat(result.getResponse().getContentAsString()).doesNotContain("catalog:manage");
		assertThat(count("SELECT count(*) FROM catalogs")).isEqualTo(before);
	}

	@Test
	void viewerCreatesAPersonalCatalog() {
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		assertThat(create(catalog(unique("Personal of Viewer"), "personal"), viewer)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void principalWithoutReadPermission() {
		TestSession noRole = this.principals.personWithoutRole();
		assertThat(create(catalog(unique("Without Role"), "personal"), noRole)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.mvc.get()
			.uri(CATALOGS)
			.cookie(new Cookie(SessionCookie.NAME, noRole.token()))
			.exchange()).hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Personal catalogs are visible only to their creator

	@Test
	void personalCatalogOfAnotherPerson() {
		TestSession sabine = this.principals.personWithIdpRole("enterprise-architect");
		TestSession franz = this.principals.personWithIdpRole("viewer");
		String name = unique("Sabine's Processes");
		String id = body(create(catalog(name, "personal"), sabine)).path("id").asString();
		assertThat(names(list(franz))).doesNotContain(name);
		assertThat(get(id, franz)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(UUID.randomUUID().toString(), franz)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(id, sabine)).hasStatus(HttpStatus.OK);
	}

	@Test
	void ownPersonalCatalog() {
		TestSession sabine = this.principals.personWithIdpRole("enterprise-architect");
		String prefix = unique("Order");
		create(catalog(prefix + " b personal", "personal"), sabine);
		create(catalog(prefix + " A shared", "instance"), this.architect);
		create(catalog(prefix + " c shared", "instance"), this.architect);
		List<String> visible = names(list(sabine)).stream().filter((name) -> name.startsWith(prefix)).toList();
		assertThat(visible).containsExactly(prefix + " A shared", prefix + " b personal", prefix + " c shared");
		assertThat(names(list(this.architect))).doesNotContain(prefix + " b personal");
	}

	// Requirement: Catalog creation is audited

	@Test
	void createdCatalogAudited() throws Exception {
		String name = unique("Audited");
		String id = body(create(catalog(name, "personal"), this.architect)).path("id").asString();
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events
				WHERE event_type = 'catalog.created' AND target_type = 'catalog' AND target_id = ?
				  AND actor_type = 'person' AND details ->> 'name' = ? AND details ->> 'scope' = 'personal'
				  AND details ->> 'primaryMetatypeId' = ?""", Integer.class, id, name, this.component.toString()))
			.isEqualTo(1);
		try (Connection connection = OeaDatabases.asSuperuser(postgres)) {
			assertThat(new ChainVerifier().verify(connection, List.of()).problems()).isEmpty();
		}
	}

	@Test
	void rejectedCreationNotAudited() {
		String name = unique("Collision");
		create(catalog(name, "instance"), this.architect);
		int before = count("SELECT count(*) FROM audit.events WHERE event_type = 'catalog.created'");
		assertThat(create(catalog(name, "instance"), this.architect)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		TestSession viewer = this.principals.personWithIdpRole("viewer");
		assertThat(create(catalog(unique("Denied"), "instance"), viewer)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'catalog.created'")).isEqualTo(before);
	}

	private void assertInvalid(MvcTestResult result, String field) {
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:validation-failed");
		List<String> fields = new ArrayList<>();
		problem.path("invalidFields").forEach((node) -> fields.add(node.asString()));
		assertThat(fields).contains(field);
	}

	private ObjectNode catalog(String name, String scope) {
		return JsonMapper.shared()
			.createObjectNode()
			.put("name", name)
			.put("primaryMetatypeId", this.component.toString())
			.put("scope", scope);
	}

	private MvcTestResult create(ObjectNode request, TestSession session) {
		return this.mvc.post()
			.uri(CATALOGS)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(request.toString())
			.exchange();
	}

	private MvcTestResult list(TestSession session) {
		return this.mvc.get().uri(CATALOGS).cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
	}

	private MvcTestResult get(String id, TestSession session) {
		return this.mvc.get()
			.uri(CATALOGS + "/" + id)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.exchange();
	}

	private static List<String> names(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.OK);
		List<String> names = new ArrayList<>();
		body(result).forEach((catalog) -> names.add(catalog.path("name").asString()));
		return names;
	}

	private int count(String sql) {
		Integer count = this.jdbc.queryForObject(sql, Integer.class);
		return (count != null) ? count : 0;
	}

	private static String unique(String name) {
		return name + " " + UUID.randomUUID().toString().substring(0, 8);
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
			return Files.createTempDirectory("oea-catalogs").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

package io.oea.app.solution;

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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec solution/solution-baseline (US-039, REQ-039; design D1–D4 of us-039-solution-baseline). */
@SpringBootTest
@AutoConfigureMockMvc
class SolutionLandscapeTests {

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
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	private TestPrincipals principals;

	private TestSession michael;

	private long michaelId;

	private String solution;

	private UUID application;

	private UUID component;

	private UUID dataFlow;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.michael = this.principals.personWithIdpRole("solution-architect");
		this.michaelId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.michael.sessionId());
		// Every test starts from an empty landscape.
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE deleted_at IS NULL",
				this.michaelId);
		this.application = metatype("Application", false);
		this.component = metatype("Component", false);
		this.dataFlow = metatype("Data Flow", true);
		this.solution = this.jdbc.queryForObject("""
				INSERT INTO solutions (id, name, owner_id, created_by) VALUES (gen_random_uuid(), ?, ?, ?)
				RETURNING id::text""", String.class, "Baseline " + UUID.randomUUID(), this.michaelId,
				this.michaelId);
	}

	// Requirement: Baseline from the current catalog

	@Test
	void entitiesOfTheLandscape() {
		int crm = entity(this.application, "CRM-Legacy");
		int erp = entity(this.application, "ERP-Core");
		entity(this.dataFlow, "Orders");
		JsonNode page = ok(landscape(""));
		assertThat(names(page)).containsExactly("CRM-Legacy", "ERP-Core");
		JsonNode first = page.path("items").get(0);
		assertThat(first.path("id").asInt()).isEqualTo(crm);
		assertThat(first.path("metatypeId").asString()).isEqualTo(this.application.toString());
		assertThat(first.path("metatypeName").asString()).startsWith("Application");
		assertThat(first.path("version").asInt()).isEqualTo(1);
		assertThat(page.path("items").get(1).path("id").asInt()).isEqualTo(erp);
		assertThat(page.path("page").path("totalElements").asInt()).isEqualTo(2);
		assertThat(page.path("nextCursor").isNull()).isTrue();
	}

	@Test
	void deletedEntityNotListed() {
		int crm = entity(this.application, "CRM-Legacy");
		entity(this.application, "ERP-Core");
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.michaelId, crm);
		assertThat(names(ok(landscape("")))).containsExactly("ERP-Core");
	}

	@Test
	void currentState() {
		int erp = entity(this.application, "ERP-Core");
		this.jdbc.update("UPDATE entities SET name = 'ERP-Core 2', version = 2 WHERE id = ?", erp);
		JsonNode entry = ok(landscape("")).path("items").get(0);
		assertThat(entry.path("name").asString()).isEqualTo("ERP-Core 2");
		assertThat(entry.path("version").asInt()).isEqualTo(2);
	}

	@Test
	void orderedByNameCaseInsensitivelyThenById() {
		int second = entity(this.application, "beta");
		int third = entity(this.component, "Beta");
		entity(this.application, "Alpha");
		entity(this.component, "gamma");
		JsonNode page = ok(landscape(""));
		assertThat(names(page)).containsExactly("Alpha", "beta", "Beta", "gamma");
		assertThat(page.path("items").get(1).path("id").asInt()).isEqualTo(second);
		assertThat(page.path("items").get(2).path("id").asInt()).isEqualTo(third);
	}

	@Test
	void unknownSolution() {
		assertThat(call("/api/v1/solutions/" + UUID.randomUUID() + "/landscape", this.michael))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void withoutEntityRead() {
		assertThat(call(path(""), this.principals.personWithIdpRole("portal-reader")))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	// Requirement: Search and metatype filter

	@Test
	void filterByMetatype() {
		for (int i = 0; i < 12; i++) {
			entity(this.application, "App " + i);
		}
		for (int i = 0; i < 8; i++) {
			entity(this.component, "Component " + i);
		}
		JsonNode page = ok(landscape("&metatypeId=" + this.application));
		assertThat(page.path("items").size()).isEqualTo(12);
		assertThat(page.path("page").path("totalElements").asInt()).isEqualTo(12);
		assertThat(page.path("items").valueStream().map((e) -> e.path("metatypeId").asString()))
			.containsOnly(this.application.toString());
	}

	@Test
	void searchByPartOfTheName() {
		entity(this.application, "CRM-Legacy");
		entity(this.application, "ERP-Core");
		entity(this.component, "SAP ERP Connector");
		entity(this.application, "100%_done");
		entity(this.application, "100 done");
		assertThat(names(ok(landscape("&q=erp")))).containsExactly("ERP-Core", "SAP ERP Connector");
		assertThat(names(ok(landscape("&q=erp&metatypeId=" + this.application)))).containsExactly("ERP-Core");
		assertThat(names(ok(landscape("&q=%25_")))).containsExactly("100%_done");
		assertThat(names(ok(landscape("&q=%20%20")))).hasSize(5);
	}

	@Test
	void unknownMetatypeGivesEmptyPage() {
		entity(this.application, "CRM-Legacy");
		JsonNode page = ok(landscape("&metatypeId=" + UUID.randomUUID()));
		assertThat(page.path("items").size()).isZero();
		assertThat(page.path("page").path("totalElements").asInt()).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "q, 255, 200", "q, 256, 400", "pageSize, 1, 200", "pageSize, 200, 200", "pageSize, 0, 400",
			"pageSize, 201, 400" })
	void limits(String parameter, int value, int status) {
		entity(this.application, "CRM-Legacy");
		String query = parameter.equals("q") ? "&q=" + "a".repeat(value) : "&pageSize=" + value;
		assertThat(call(path(query), this.michael).getResponse().getStatus()).isEqualTo(status);
	}

	// Requirement: Order and paging of the baseline

	@Test
	void pagesByCursor() {
		List<Integer> expected = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			expected.add(entity((i % 2 == 0) ? this.application : this.component, "Entity " + (i % 25)));
		}
		List<Integer> byPage = new ArrayList<>();
		for (int page = 0; page < 3; page++) {
			byPage.addAll(ids(ok(landscape("&pageSize=20&page=" + page))));
		}
		List<Integer> byCursor = new ArrayList<>();
		String cursor = null;
		for (int page = 0; page < 3; page++) {
			JsonNode result = ok(landscape("&pageSize=20&page=" + page + ((cursor != null) ? "&cursor=" + cursor : "")));
			assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(60);
			byCursor.addAll(ids(result));
			cursor = result.path("nextCursor").isNull() ? null : result.path("nextCursor").asString();
			assertThat(cursor == null).isEqualTo(page == 2);
		}
		assertThat(byCursor).isEqualTo(byPage).containsExactlyInAnyOrderElementsOf(expected).doesNotHaveDuplicates();
	}

	@Test
	void cursorOfAnotherQuery() {
		for (int i = 0; i < 5; i++) {
			entity(this.application, "App " + i);
		}
		String cursor = ok(landscape("&pageSize=2")).path("nextCursor").asString();
		assertInvalidCursor(call(path("&pageSize=2&page=1&cursor=" + cursor + "&metatypeId=" + this.application),
				this.michael));
		assertInvalidCursor(call(path("&pageSize=2&page=1&cursor=" + changed(cursor)), this.michael));
		assertInvalidCursor(call(path("&pageSize=2&cursor=" + cursor), this.michael));
		assertThat(ok(landscape("&pageSize=2&page=1&cursor=" + cursor)).path("items").size()).isEqualTo(2);
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?) RETURNING id""", UUID.class,
				name + " " + UUID.randomUUID().toString().substring(0, 8), connection);
	}

	private int entity(UUID type, String name) {
		return this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class, type,
				name, this.michaelId);
	}

	private String path(String query) {
		return "/api/v1/solutions/" + this.solution + "/landscape?" + (query.isEmpty() ? "" : query.substring(1));
	}

	private MvcTestResult landscape(String query) {
		return call(path(query), this.michael);
	}

	private MvcTestResult call(String path, TestSession session) {
		return this.mvc.get()
			.uri(UriComponentsBuilder.fromUriString(path).build(true).toUri())
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return JsonMapper.shared().readTree(content(result));
	}

	private static List<String> names(JsonNode page) {
		return page.path("items").valueStream().map((entry) -> entry.path("name").asString()).toList();
	}

	private static List<Integer> ids(JsonNode page) {
		return page.path("items").valueStream().map((entry) -> entry.path("id").asInt()).toList();
	}

	private static String changed(String cursor) {
		char first = cursor.charAt(0);
		return ((first == 'A') ? 'B' : 'A') + cursor.substring(1);
	}

	private static void assertInvalidCursor(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(JsonMapper.shared().readTree(content(result)).path("invalidFields").toString())
			.isEqualTo("[\"cursor\"]");
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-solution-landscape").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

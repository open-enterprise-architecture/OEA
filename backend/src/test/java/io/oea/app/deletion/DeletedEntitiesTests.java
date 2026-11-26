package io.oea.app.deletion;

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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Spec repository/deleted-entities, the list (s17-deleted-entities; design D1): order, cascade origin,
 * connections with their endpoints, filters, paging with cursors and the limits of their values, on an
 * own database. Kurt is an enterprise architect ({@code entity:restore}, {@code entity:delete}), Franz a
 * viewer and Ida holds {@code integration-writer} ({@code entity:write} only). Every test works with its
 * own metatypes and filters by them, so that other tests' rows do not count.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeletedEntitiesTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String LIST = "/api/v1/entities/deleted";

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

	private TestSession kurt;

	private TestSession franz;

	private TestSession ida;

	private long kurtId;

	private UUID application;

	private UUID flow;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.kurt = principals.personWithIdpRole("enterprise-architect");
		this.franz = principals.personWithIdpRole("viewer");
		this.ida = principals.personWithIdpRole("integration-writer");
		this.kurtId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.kurt.sessionId());
		this.application = metatype("Application " + suffix(), false);
		this.flow = metatype("Flow " + suffix(), true);
	}

	// Requirement: List deleted entities

	@Test
	void deletedWithACascadeNamesItsOrigin() {
		int erp = entity("ERP", this.application);
		int finance = child("ERP Finance", erp);
		int crm = entity("CRM", this.application);
		int erpToCrm = connection("ERP to CRM", erp, crm);
		assertThat(delete(erp)).hasStatus(HttpStatus.OK);

		JsonNode items = ok(list("metatypeId=" + this.application)).path("items");

		assertThat(ids(items)).containsExactlyInAnyOrder(erp, finance);
		JsonNode erpItem = item(items, erp);
		JsonNode financeItem = item(items, finance);
		assertThat(erpItem.path("deletedBy").path("principalType").asString()).isEqualTo("person");
		assertThat(erpItem.path("deletedBy").path("id").asLong()).isEqualTo(this.kurtId);
		assertThat(erpItem.path("deletedBy").path("displayName").asString()).isNotBlank();
		assertThat(erpItem.path("deletedAt").asString()).isNotBlank();
		assertThat(erpItem.path("version").asInt()).isEqualTo(1);
		assertThat(erpItem.path("isConnection").asBoolean()).isFalse();
		assertThat(erpItem.path("metatype").path("id").asString()).isEqualTo(this.application.toString());
		assertThat(erpItem.has("source")).isFalse();
		// the entity deleted itself has no origin, what it took along names it
		assertThat(erpItem.path("deletedWith").isNull()).isTrue();
		assertThat(financeItem.path("deletedWith").path("id").asInt()).isEqualTo(erp);
		assertThat(financeItem.path("deletedWith").path("name").asString()).isEqualTo("ERP");
		// the connection of the cascade is listed with its endpoints, the active one is not
		JsonNode connections = ok(list("metatypeId=" + this.flow)).path("items");
		assertThat(ids(connections)).containsExactly(erpToCrm);
		JsonNode connectionItem = item(connections, erpToCrm);
		assertThat(connectionItem.path("isConnection").asBoolean()).isTrue();
		assertThat(connectionItem.path("source").path("id").asInt()).isEqualTo(erp);
		assertThat(connectionItem.path("source").path("name").asString()).isEqualTo("ERP");
		assertThat(connectionItem.path("target").path("id").asInt()).isEqualTo(crm);
		assertThat(connectionItem.path("target").path("name").asString()).isEqualTo("CRM");
		assertThat(connectionItem.path("deletedWith").path("id").asInt()).isEqualTo(erp);
	}

	@Test
	void anEntityDeletedWithoutAnActorHasNoDeletedBy() {
		int orphan = entity("Orphan", this.application);
		this.jdbc.update("UPDATE entities SET deleted_at = now() WHERE id = ?", orphan);

		JsonNode orphanItem = item(ok(list("metatypeId=" + this.application)).path("items"), orphan);

		assertThat(orphanItem.has("deletedBy")).isFalse();
	}

	@Test
	void anActiveEntityIsNotListed() {
		int active = entity("Active", this.application);
		int gone = entity("Gone", this.application);
		deleteAlone(gone, Instant.now());

		assertThat(ids(ok(list("metatypeId=" + this.application)).path("items"))).containsExactly(gone)
			.doesNotContain(active);
	}

	@Test
	void newestDeletionFirstThenByDescendingId() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		int oldest = entity("Oldest", this.application);
		int tieLow = entity("Tie low", this.application);
		int tieHigh = entity("Tie high", this.application);
		int newest = entity("Newest", this.application);
		deleteAlone(oldest, now.minusSeconds(30));
		deleteAlone(tieLow, now.minusSeconds(10));
		deleteAlone(tieHigh, now.minusSeconds(10));
		deleteAlone(newest, now);

		assertThat(ids(ok(list("metatypeId=" + this.application)).path("items")))
			.containsExactly(newest, tieHigh, tieLow, oldest);
	}

	@Test
	void anEmptyListHasNoItems() {
		JsonNode page = ok(list("metatypeId=" + this.application));

		assertThat(page.path("items")).isEmpty();
		assertThat(page.path("nextCursor").isNull()).isTrue();
		assertThat(page.path("page").path("totalElements").asInt()).isZero();
	}

	// Filters

	@Test
	void filtersByNamePartAndMetatype() {
		UUID other = metatype("Other " + suffix(), false);
		int finance = entity("ERP Finance", this.application);
		int logistics = entity("ERP Logistics", this.application);
		int financeOther = entity("Finance Tool", other);
		Instant now = Instant.now();
		deleteAlone(finance, now);
		deleteAlone(logistics, now.minusSeconds(1));
		deleteAlone(financeOther, now.minusSeconds(2));

		assertThat(ids(ok(list("q=fin&metatypeId=" + this.application)).path("items"))).containsExactly(finance);
		assertThat(ids(ok(list("q=FIN&metatypeId=" + other)).path("items"))).containsExactly(financeOther);
		assertThat(ids(ok(list("q=nothing&metatypeId=" + this.application)).path("items"))).isEmpty();
		// a blank name part is no filter
		assertThat(ids(ok(list("q=  &metatypeId=" + this.application)).path("items")))
			.containsExactly(finance, logistics);
	}

	@ParameterizedTest
	@CsvSource({ "100%, 100% done", "a_c, a_c", "back\\slash, back\\slash" })
	void wildcardsOfTheNamePartMatchLiterally(String q, String matching) {
		int literal = entity(matching, this.application);
		int plain1 = entity("1000 done", this.application);
		int plain2 = entity("abc", this.application);
		int plain3 = entity("backXslash", this.application);
		Instant now = Instant.now();
		deleteAlone(literal, now);
		deleteAlone(plain1, now.minusSeconds(1));
		deleteAlone(plain2, now.minusSeconds(2));
		deleteAlone(plain3, now.minusSeconds(3));

		assertThat(ids(ok(list("q=" + q + "&metatypeId=" + this.application)).path("items"))).containsExactly(literal);
	}

	// Limits

	@ParameterizedTest
	@ValueSource(ints = { 1, 200 })
	void pageSizesOfTheLimitsAreAccepted(int pageSize) {
		JsonNode page = ok(list("pageSize=" + pageSize + "&metatypeId=" + this.application));

		assertThat(page.path("page").path("pageSize").asInt()).isEqualTo(pageSize);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 201 })
	void pageSizesBeyondTheLimitsAreRejected(int pageSize) {
		assertThat(list("pageSize=" + pageSize)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void theDefaultPageSizeIs50AndANegativePageIsRejected() {
		assertThat(ok(list("metatypeId=" + this.application)).path("page").path("pageSize").asInt()).isEqualTo(50);
		assertThat(list("page=-1")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(list("page=0&metatypeId=" + this.application)).hasStatus(HttpStatus.OK);
	}

	@ParameterizedTest
	@CsvSource({ "255, 200", "256, 400" })
	void aNamePartOf255CharactersIsAcceptedAndOf256Rejected(int length, int status) {
		assertThat(list("q=" + "a".repeat(length) + "&metatypeId=" + this.application))
			.hasStatus(HttpStatus.valueOf(status));
	}

	@Test
	void anInvalidMetatypeIdIsRejected() {
		assertThat(list("metatypeId=nope")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// Paging

	@Test
	void cursorPagingLeadsThroughAllEntriesWithoutRepeating() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		List<Integer> expected = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			int id = entity("Paged " + i, this.application);
			// the last two share their time: the ID breaks the tie
			deleteAlone(id, now.minusSeconds((i < 3) ? i : 3));
			expected.add(id);
		}
		List<Integer> seen = new ArrayList<>();
		String cursor = null;
		for (int page = 0; page < 3; page++) {
			JsonNode result = ok(list("pageSize=2&page=" + page + "&metatypeId=" + this.application
					+ ((cursor != null) ? "&cursor=" + cursor : "")));
			assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(5);
			assertThat(result.path("page").path("totalPages").asInt()).isEqualTo(3);
			result.path("items").forEach((item) -> seen.add(item.path("id").asInt()));
			cursor = result.path("nextCursor").isNull() ? null : result.path("nextCursor").asString();
			assertThat(cursor == null).isEqualTo(page == 2);
		}
		// the same order as without a cursor, by page offset
		List<Integer> byOffset = new ArrayList<>();
		for (int page = 0; page < 3; page++) {
			ok(list("pageSize=2&page=" + page + "&metatypeId=" + this.application)).path("items")
				.forEach((item) -> byOffset.add(item.path("id").asInt()));
		}
		assertThat(seen).hasSize(5).doesNotHaveDuplicates().containsExactlyElementsOf(byOffset);
		assertThat(seen).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void aCursorOfAnotherQueryIsRejected() {
		for (int i = 0; i < 3; i++) {
			deleteAlone(entity("Cursor " + i, this.application), Instant.now().minusSeconds(i));
		}
		String cursor = ok(list("pageSize=1&metatypeId=" + this.application)).path("nextCursor").asString();
		assertThat(cursor).isNotBlank();

		// the same query with the next page is accepted
		assertThat(list("pageSize=1&page=1&cursor=" + cursor + "&metatypeId=" + this.application))
			.hasStatus(HttpStatus.OK);
		// another page size, filter, metatype, page number or an altered cursor is not
		assertCursorInvalid(list("pageSize=2&page=1&cursor=" + cursor + "&metatypeId=" + this.application));
		assertCursorInvalid(list("pageSize=1&page=1&q=cursor&cursor=" + cursor + "&metatypeId=" + this.application));
		assertCursorInvalid(list("pageSize=1&page=1&cursor=" + cursor + "&metatypeId=" + this.flow));
		assertCursorInvalid(list("pageSize=1&page=2&cursor=" + cursor + "&metatypeId=" + this.application));
		assertCursorInvalid(list("pageSize=1&page=1&cursor=garbage&metatypeId=" + this.application));
	}

	@Test
	void aCursorOfMoreThan2000CharactersIsRejectedAsInvalidInput() {
		assertCursorInvalid(list("page=1&cursor=" + "a".repeat(2000) + "&metatypeId=" + this.application));
		assertThat(list("page=1&cursor=" + "a".repeat(2001))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// Authorization

	@Test
	void withoutEntityRestoreTheListIsForbidden() {
		assertThat(this.mvc.get().uri(LIST).cookie(cookie(this.franz)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
		// entity:write alone does not suffice either
		assertThat(this.mvc.get().uri(LIST).cookie(cookie(this.ida)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.mvc.get().uri(LIST).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// Helpers

	private MvcTestResult list(String query) {
		return this.mvc.get().uri(LIST + "?" + query).cookie(cookie(this.kurt)).exchange();
	}

	private MvcTestResult delete(int id) {
		return this.mvc.delete()
			.uri("/api/v1/entities/" + id)
			.cookie(cookie(this.kurt))
			.header(CsrfOriginFilter.HEADER, this.kurt.csrfToken())
			.header("If-Match", "\"1\"")
			.exchange();
	}

	private void deleteAlone(int id, Instant at) {
		this.jdbc.update("UPDATE entities SET deleted_at = ?, deleted_by = ? WHERE id = ?", Timestamp.from(at),
				this.kurtId, id);
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection)
				VALUES ('00000000-0000-0000-0000-000000000001', ?, ?) RETURNING id""", UUID.class, name, connection);
	}

	private int entity(String name, UUID metatype) {
		Integer id = this.jdbc.queryForObject(
				"INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id", Integer.class,
				metatype, name, this.kurtId);
		this.jdbc.update(
				"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, 1, ?, ?)", id,
				this.kurtId, Timestamp.from(Instant.now()));
		return id;
	}

	private int child(String name, int parent) {
		int id = entity(name, this.application);
		this.jdbc.update("UPDATE entities SET parent_entity_id = ? WHERE id = ?", parent, id);
		return id;
	}

	private int connection(String name, int source, int target) {
		int id = entity(name, this.flow);
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private static JsonNode item(JsonNode items, int id) {
		for (JsonNode item : items) {
			if (item.path("id").asInt() == id) {
				return item;
			}
		}
		throw new AssertionError("entity " + id + " is not listed");
	}

	private static List<Integer> ids(JsonNode items) {
		List<Integer> ids = new ArrayList<>();
		items.forEach((item) -> ids.add(item.path("id").asInt()));
		return ids;
	}

	private static void assertCursorInvalid(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(body(result).path("invalidFields").get(0).asString()).isEqualTo("cursor");
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static Cookie cookie(TestSession session) {
		return new Cookie(SessionCookie.NAME, session.token());
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
			return Files.createTempDirectory("oea-deleted-entities").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

package io.oea.app.catalog;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
 * Spec catalog/catalog-query, "Show deleted rows" (s17-deleted-entities; design D2): the query with
 * {@code includeDeleted}, on an own database. The architect holds {@code entity:restore}, the viewer
 * only {@code entity:read}. Every test uses its own metatypes and catalogs.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogDeletedRowsTests {

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

	private TestSession architect;

	private TestSession viewer;

	private long architectId;

	private UUID application;

	private UUID iface;

	private UUID dataFlow;

	@BeforeEach
	void instance() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = principals.personWithIdpRole("enterprise-architect");
		this.viewer = principals.personWithIdpRole("viewer");
		this.architectId = this.jdbc.queryForObject("SELECT person_id FROM sessions WHERE id = ?", Long.class,
				this.architect.sessionId());
		this.application = metatype("Application", false);
		this.iface = metatype("Interface", false);
		this.dataFlow = metatype("Data Flow", true);
		constraint(this.dataFlow, "source", this.application);
		constraint(this.dataFlow, "target", this.iface);
	}

	@Test
	void deletedRowsAreMarkedAndCounted() {
		int crm = entity(this.application, "CRM");
		int erp = entity(this.application, "ERP");
		int legacy = entity(this.application, "Legacy CRM");
		delete(legacy);
		String catalog = catalog(this.application);

		JsonNode withDeleted = ok(query(catalog, "includeDeleted=true", this.architect));

		assertThat(names(withDeleted)).containsExactly("CRM", "ERP", "Legacy CRM");
		assertThat(withDeleted.path("page").path("totalElements").asInt()).isEqualTo(3);
		JsonNode deleted = row(withDeleted, legacy);
		assertThat(deleted.path("deleted").asBoolean()).isTrue();
		assertThat(deleted.path("deletedAt").asString()).isNotBlank();
		assertThat(deleted.path("version").asInt()).isEqualTo(1);
		assertThat(deleted.path("values").path("name").asString()).isEqualTo("Legacy CRM");
		// the columns are the same as without the flag
		assertThat(keys(withDeleted)).isEqualTo(keys(ok(query(catalog, "", this.architect))));
		// active rows carry no deletion members
		assertThat(row(withDeleted, crm).has("deleted")).isFalse();
		assertThat(row(withDeleted, erp).has("deletedAt")).isFalse();
		assertThat(row(withDeleted, erp).has("version")).isFalse();
	}

	@Test
	void theVersionOfADeletedRowIsTheOneOfTheEntity() {
		int legacy = entity(this.application, "Legacy");
		this.jdbc.update("UPDATE entities SET version = 7 WHERE id = ?", legacy);
		delete(legacy);

		JsonNode result = ok(query(catalog(this.application), "includeDeleted=true", this.architect));

		assertThat(row(result, legacy).path("version").asInt()).isEqualTo(7);
	}

	@Test
	void theDefaultQueryIsUnchanged() {
		int crm = entity(this.application, "CRM");
		int legacy = entity(this.application, "Legacy CRM");
		delete(legacy);
		String catalog = catalog(this.application);

		for (String parameters : List.of("", "includeDeleted=false")) {
			JsonNode result = ok(query(catalog, parameters, this.architect));
			assertThat(names(result)).containsExactly("CRM");
			assertThat(result.path("page").path("totalElements").asInt()).isEqualTo(1);
			assertThat(row(result, crm).has("deleted")).isFalse();
			assertThat(row(result, crm).has("deletedAt")).isFalse();
			assertThat(row(result, crm).has("version")).isFalse();
		}
	}

	@Test
	void sortingAndFiltersApplyToDeletedRows() {
		entity(this.application, "Alpha CRM");
		int beta = entity(this.application, "Beta CRM");
		int gamma = entity(this.application, "Gamma ERP");
		delete(beta);
		delete(gamma);
		String catalog = catalog(this.application);

		assertThat(names(ok(query(catalog, "includeDeleted=true&sort=name:desc", this.architect))))
			.containsExactly("Gamma ERP", "Beta CRM", "Alpha CRM");
		JsonNode filtered = ok(query(catalog, "includeDeleted=true&filter=name:contains:crm", this.architect));
		assertThat(names(filtered)).containsExactly("Alpha CRM", "Beta CRM");
		assertThat(filtered.path("page").path("totalElements").asInt()).isEqualTo(2);
		assertThat(names(ok(query(catalog, "includeDeleted=true&filter=name:contains:erp", this.architect))))
			.containsExactly("Gamma ERP");
		// without the flag the deleted ones do not pass either
		assertThat(names(ok(query(catalog, "filter=name:contains:crm", this.architect)))).containsExactly("Alpha CRM");
	}

	// Authorization

	@Test
	void withoutEntityRestoreTheFlagIsForbidden() {
		entity(this.application, "CRM");
		String catalog = catalog(this.application);

		assertThat(query(catalog, "includeDeleted=true", this.viewer)).hasStatus(HttpStatus.FORBIDDEN);
		// the check comes before the catalog is looked up
		assertThat(query(UUID.randomUUID().toString(), "includeDeleted=true", this.viewer))
			.hasStatus(HttpStatus.FORBIDDEN);
		// without the flag, or with it switched off, the viewer reads as before
		assertThat(names(ok(query(catalog, "", this.viewer)))).containsExactly("CRM");
		assertThat(names(ok(query(catalog, "includeDeleted=false", this.viewer)))).containsExactly("CRM");
		// an architect gets 404 for an unknown catalog
		assertThat(query(UUID.randomUUID().toString(), "includeDeleted=true", this.architect))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	// Paging and cursor

	@ParameterizedTest
	@CsvSource({ "1, 200", "200, 200", "0, 400", "201, 400" })
	void pageSizesOfTheLimitsWithTheFlag(int pageSize, int status) {
		entity(this.application, "CRM");
		String catalog = catalog(this.application);

		assertThat(query(catalog, "includeDeleted=true&pageSize=" + pageSize, this.architect))
			.hasStatus(HttpStatus.valueOf(status));
	}

	@Test
	void pagingWithTheFlagLeadsThroughAllRows() {
		entity(this.application, "A one");
		delete(entity(this.application, "B two"));
		entity(this.application, "C three");
		String catalog = catalog(this.application);

		JsonNode first = ok(query(catalog, "includeDeleted=true&pageSize=2", this.architect));
		assertThat(names(first)).containsExactly("A one", "B two");
		String cursor = first.path("nextCursor").asString();
		assertThat(cursor).isNotBlank();
		JsonNode second = ok(query(catalog, "includeDeleted=true&pageSize=2&page=1&cursor=" + cursor, this.architect));
		assertThat(names(second)).containsExactly("C three");
		assertThat(second.path("nextCursor").isNull()).isTrue();
		assertThat(second.path("page").path("totalElements").asInt()).isEqualTo(3);
	}

	@Test
	void aCursorOfTheOtherModeIsRejectedInBothDirections() {
		entity(this.application, "A one");
		entity(this.application, "B two");
		delete(entity(this.application, "C three"));
		String catalog = catalog(this.application);
		String active = ok(query(catalog, "pageSize=1", this.architect)).path("nextCursor").asString();
		String deleted = ok(query(catalog, "includeDeleted=true&pageSize=1", this.architect)).path("nextCursor")
			.asString();

		assertCursorInvalid(query(catalog, "includeDeleted=true&pageSize=1&page=1&cursor=" + active, this.architect));
		assertCursorInvalid(query(catalog, "pageSize=1&page=1&cursor=" + deleted, this.architect));
		assertCursorInvalid(query(catalog, "includeDeleted=false&pageSize=1&page=1&cursor=" + deleted, this.architect));
		// the same mode is accepted
		assertThat(query(catalog, "includeDeleted=true&pageSize=1&page=1&cursor=" + deleted, this.architect))
			.hasStatus(HttpStatus.OK);
		assertThat(query(catalog, "pageSize=1&page=1&cursor=" + active, this.architect)).hasStatus(HttpStatus.OK);
	}

	// Joins

	@Test
	void joinsShowOnlyActiveTargetsAndADeletedRowKeepsOneRowWithoutResults() {
		int active = entity(this.application, "Active App");
		int legacy = entity(this.application, "Legacy App");
		int sftp = entity(this.iface, "SFTP");
		int gone = entity(this.iface, "Gone Interface");
		connect(active, sftp);
		connect(active, gone);
		int legacyFlow = connect(legacy, sftp);
		delete(gone);
		delete(legacy);
		delete(legacyFlow);
		String catalog = catalog(this.application);
		String join = join(catalog);

		JsonNode rows = ok(query(catalog, "includeDeleted=true&joinMode[" + join + "]=rows", this.architect));

		assertThat(names(rows)).containsExactly("Active App", "Legacy App");
		assertThat(rows.path("page").path("totalElements").asInt()).isEqualTo(2);
		assertThat(joinNames(row(rows, active), join)).containsExactly("SFTP");
		assertThat(row(rows, legacy).path("deleted").asBoolean()).isTrue();
		assertThat(row(rows, legacy).path("joinResults").path(join)).isEmpty();
		// a filter on a join column excludes the deleted row
		JsonNode filtered = ok(query(catalog,
				"includeDeleted=true&joinMode[" + join + "]=rows&filter=" + join + ".name:eq:SFTP", this.architect));
		assertThat(names(filtered)).containsExactly("Active App");
		JsonNode aggregate = ok(query(catalog, "includeDeleted=true&joinMode[" + join + "]=aggregate", this.architect));
		assertThat(names(aggregate)).containsExactly("Active App", "Legacy App");
		assertThat(joinNames(row(aggregate, legacy), join)).isEmpty();
		assertThat(joinNames(row(aggregate, active), join)).containsExactly("SFTP");
	}

	// Connection catalogs

	@Test
	void aDeletedConnectionShowsItsDeletedEndpoints() {
		int erp = entity(this.application, "ERP");
		int billing = entity(this.iface, "Billing");
		int orders = entity(this.application, "Orders App");
		int keptFlow = connect(erp, billing);
		int ordersFlow = connect(orders, billing);
		delete(erp);
		delete(keptFlow);
		delete(billing);
		delete(ordersFlow);
		// an active connection whose endpoint was deleted meanwhile still shows an empty cell
		int active = entity(this.application, "Active");
		int target = entity(this.iface, "Soon gone");
		int activeFlow = connect(active, target);
		delete(target);
		String catalog = catalog(this.dataFlow);

		JsonNode result = ok(query(catalog, "includeDeleted=true", this.architect));

		JsonNode deletedRow = row(result, keptFlow);
		assertThat(deletedRow.path("deleted").asBoolean()).isTrue();
		assertThat(deletedRow.path("values").path("source.name").asString()).isEqualTo("ERP");
		assertThat(deletedRow.path("values").path("target.name").asString()).isEqualTo("Billing");
		assertThat(row(result, ordersFlow).path("values").path("source.name").asString()).isEqualTo("Orders App");
		assertThat(row(result, activeFlow).has("deleted")).isFalse();
		assertThat(row(result, activeFlow).path("values").path("source.name").asString()).isEqualTo("Active");
		assertThat(row(result, activeFlow).path("values").path("target.name").isNull()).isTrue();
		// filters and sorting use the same values
		JsonNode filtered = ok(query(catalog, "includeDeleted=true&filter=source.name:eq:ERP", this.architect));
		assertThat(rowIds(filtered)).containsExactly(keptFlow);
		JsonNode sorted = ok(query(catalog, "includeDeleted=true&sort=target.name:asc", this.architect));
		assertThat(rowIds(sorted).subList(0, 2)).containsExactlyInAnyOrder(keptFlow, ordersFlow);
		// without the flag only the active connection is a row, with its empty target
		JsonNode plain = ok(query(catalog, "", this.architect));
		assertThat(rowIds(plain)).containsExactly(activeFlow);
		assertThat(row(plain, activeFlow).path("values").path("target.name").isNull()).isTrue();
	}

	// Helpers

	private String join(String catalog) {
		ObjectNode body = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Interfaces")
			.put("connectionMetatypeId", this.dataFlow.toString())
			.put("joinDirection", "outbound")
			.put("targetMetatypeId", this.iface.toString());
		ArrayNode columns = body.putArray("targetColumns");
		columns.add(JsonMapper.shared().createObjectNode().put("field", "name"));
		MvcTestResult added = call(HttpMethod.POST, CATALOGS + "/" + catalog + "/joins", body, this.architect);
		assertThat(added).as(() -> content(added)).hasStatus(HttpStatus.CREATED);
		return body(added).path("joinDefinitions").get(0).path("id").asString();
	}

	private String catalog(UUID primary) {
		MvcTestResult created = call(HttpMethod.POST, CATALOGS, JsonMapper.shared()
			.createObjectNode()
			.put("name", "Catalog " + UUID.randomUUID())
			.put("primaryMetatypeId", primary.toString())
			.put("scope", "instance"), this.architect);
		assertThat(created).as(() -> content(created)).hasStatus(HttpStatus.CREATED);
		return body(created).path("id").asString();
	}

	private MvcTestResult query(String catalog, String parameters, TestSession session) {
		return call(HttpMethod.GET, CATALOGS + "/" + catalog + "/query" + (parameters.isEmpty() ? "" : "?" + parameters),
				null, session);
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, TestSession session) {
		var request = this.mvc.method(method)
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken());
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private UUID metatype(String name, boolean connection) {
		return this.jdbc.queryForObject("""
				INSERT INTO metatypes (config_id, name, is_connection) VALUES ('00000000-0000-0000-0000-000000000001', ?, ?)
				RETURNING id""", UUID.class, name + " " + suffix(), connection);
	}

	private void constraint(UUID connection, String direction, UUID allowed) {
		this.jdbc.update("""
				INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
				VALUES (?, ?, ?)""", connection, direction, allowed);
	}

	private int entity(UUID type, String name) {
		return this.jdbc.queryForObject("""
				INSERT INTO entities (metatype_id, name, created_by) VALUES (?, ?, ?) RETURNING id""", Integer.class, type,
				name, this.architectId);
	}

	private int connect(int source, int target) {
		int id = entity(this.dataFlow, "Flow " + suffix());
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				id, source, target);
		return id;
	}

	private void delete(int id) {
		this.jdbc.update("UPDATE entities SET deleted_at = now(), deleted_by = ? WHERE id = ?", this.architectId, id);
	}

	private static JsonNode row(JsonNode result, int entityId) {
		for (JsonNode row : result.path("rows")) {
			if (row.path("entityId").asInt() == entityId) {
				return row;
			}
		}
		throw new AssertionError("no row for entity " + entityId);
	}

	private static List<Integer> rowIds(JsonNode result) {
		List<Integer> ids = new ArrayList<>();
		result.path("rows").forEach((row) -> ids.add(row.path("entityId").asInt()));
		return ids;
	}

	private static List<String> joinNames(JsonNode row, String join) {
		List<String> names = new ArrayList<>();
		row.path("joinResults").path(join).forEach((result) -> names.add(result.path("values").path("name").asString()));
		return names;
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

	private static void assertCursorInvalid(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(body(result).path("invalidFields").get(0).asString()).isEqualTo("cursor");
	}

	private static JsonNode ok(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
		return body(result);
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
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

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-catalog-deleted").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

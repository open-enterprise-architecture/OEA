package io.oea.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import io.oea.testsupport.metamodel.TestPackages;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * Spec metamodel/optimistic-locking of scr-030-metamodel-configuration: versions of metatypes,
 * property definitions and categories, ETag and If-Match, on an own database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MetamodelVersionsTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN_FILE = tempDirectory() + "/setup-token";

	private static final String CONFIG = PackageImporter.INSTANCE_CONFIGURATION.toString();

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

	private TestSession admin;

	@BeforeEach
	void instance() {
		this.admin = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("metamodel-admin");
	}

	// Versions of metamodel elements

	@Test
	void versionAfterAChange() {
		JsonNode created = created(call(HttpMethod.POST, "/metamodel/metatypes", metatype("Zone"), null));
		UUID zone = id(created);
		assertThat(created.path("version").asInt()).isEqualTo(1);
		MvcTestResult read = call(HttpMethod.GET, "/metamodel/metatypes/" + zone, null, null);
		assertThat(read.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"1\"");

		MvcTestResult updated = call(HttpMethod.PUT, "/metamodel/metatypes/" + zone,
				metatype("Zone").put("abbreviation", "ZN"), "\"1\"");
		assertThat(updated).as(() -> content(updated)).hasStatus(HttpStatus.OK);
		assertThat(body(updated).path("version").asInt()).isEqualTo(2);
		assertThat(updated.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"2\"");
		assertThat(call(HttpMethod.GET, "/metamodel/metatypes/" + zone, null, null).getResponse()
			.getHeader(HttpHeaders.ETAG)).isEqualTo("\"2\"");
	}

	@Test
	void mappingsAndReimportSettingChangeTheMetatypeVersion() {
		UUID zone = id(created(call(HttpMethod.POST, "/metamodel/metatypes", metatype("Zone"), null)));
		UUID trust = property("Trust", "enum");
		MvcTestResult mapped = call(HttpMethod.POST, "/metamodel/metatypes/" + zone + "/property-mappings",
				object().put("propertyDefId", trust.toString()).put("necessity", "optional"), "\"1\"");
		UUID mapping = id(created(mapped));
		assertThat(version("metatypes", zone)).isEqualTo(2);

		MvcTestResult policy = call(HttpMethod.PUT, "/metamodel/metatypes/" + zone + "/reimport-policy",
				object().put("organizationMappings", "remove"), "\"2\"");
		assertThat(policy).as(() -> content(policy)).hasStatus(HttpStatus.OK);
		assertThat(policy.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"3\"");

		// a stale removal changes nothing
		assertThat(stale(call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone + "/property-mappings/" + mapping,
				null, "\"2\""))).isEqualTo(3);
		assertThat(count("SELECT count(*) FROM metatype_property_mappings WHERE id = '" + mapping + "'")).isOne();
		assertThat(call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone + "/property-mappings/" + mapping, null,
				"\"3\"")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(version("metatypes", zone)).isEqualTo(4);
	}

	@Test
	void enumValuesChangeTheDefinitionVersion() {
		UUID trust = property("Trust", "enum");
		MvcTestResult value = call(HttpMethod.POST, "/metamodel/property-definitions/" + trust + "/enum-values",
				object().put("value", "high"), "\"1\"");
		UUID high = id(created(value));
		assertThat(version("property_definitions", trust)).isEqualTo(2);
		assertThat(stale(call(HttpMethod.DELETE,
				"/metamodel/property-definitions/" + trust + "/enum-values/" + high, null, "\"1\""))).isEqualTo(2);
		assertThat(call(HttpMethod.DELETE, "/metamodel/property-definitions/" + trust + "/enum-values/" + high, null,
				"\"2\"")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(version("property_definitions", trust)).isEqualTo(3);
	}

	@Test
	void reimportChangesTheVersionOfAnUpdatedMetatype() {
		String packageId = "versions-" + UUID.randomUUID().toString().substring(0, 8);
		importPackage(TestPackages.pkg(packageId, 2));
		UUID first = UUID.fromString(TestPackages.pkg(packageId, 2).path("metatypes").get(0).path("id").asString());
		UUID second = UUID.fromString(TestPackages.pkg(packageId, 2).path("metatypes").get(1).path("id").asString());
		assertThat(version("metatypes", first)).isOne();
		ObjectNode renamed = TestPackages.pkg(packageId, 2);
		((ObjectNode) renamed.withArray("metatypes").get(0)).put("name", packageId + " Renamed");
		importPackage(renamed);
		assertThat(version("metatypes", first)).isEqualTo(2);
		assertThat(version("metatypes", second)).isOne();
	}

	// Rejecting a stale write

	@Test
	void twoAdministratorsChangeTheSameMetatype() {
		UUID zone = id(created(call(HttpMethod.POST, "/metamodel/metatypes", metatype("Zone"), null)));
		String firstName = unique("First");
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + zone,
				object().put("name", firstName).put("configId", CONFIG), "\"1\"")).hasStatus(HttpStatus.OK);
		MvcTestResult second = call(HttpMethod.PUT, "/metamodel/metatypes/" + zone,
				object().put("name", unique("Second")).put("configId", CONFIG), "\"1\"");
		assertThat(stale(second)).isEqualTo(2);
		assertThat(this.jdbc.queryForObject("SELECT name FROM metatypes WHERE id = ?", String.class, zone))
			.isEqualTo(firstName);
	}

	@Test
	void everyWriteRejectsAStaleVersion() {
		UUID zone = id(created(call(HttpMethod.POST, "/metamodel/metatypes", metatype("Zone"), null)));
		UUID trust = property("Trust", "enum");
		UUID high = id(created(call(HttpMethod.POST, "/metamodel/property-definitions/" + trust + "/enum-values",
				object().put("value", "high"), null)));
		UUID mapping = id(created(call(HttpMethod.POST, "/metamodel/metatypes/" + zone + "/property-mappings",
				object().put("propertyDefId", trust.toString()).put("necessity", "optional"), null)));
		UUID category = id(created(call(HttpMethod.POST, "/metamodel/property-categories",
				object().put("name", unique("Category")), null)));
		String old = "\"99\"";
		assertThat(stale(call(HttpMethod.PUT, "/metamodel/metatypes/" + zone, metatype("Zone"), old))).isEqualTo(2);
		assertThat(stale(call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone, null, old))).isEqualTo(2);
		assertThat(stale(call(HttpMethod.POST, "/metamodel/metatypes/" + zone + "/property-mappings",
				object().put("propertyDefId", trust.toString()).put("necessity", "mandatory").put("validFrom",
						"2099-01-01"), old))).isEqualTo(2);
		assertThat(stale(call(HttpMethod.DELETE, "/metamodel/metatypes/" + zone + "/property-mappings/" + mapping,
				null, old))).isEqualTo(2);
		assertThat(stale(call(HttpMethod.PUT, "/metamodel/metatypes/" + zone + "/reimport-policy",
				object().put("organizationMappings", "keep"), old))).isEqualTo(2);
		assertThat(stale(call(HttpMethod.PUT, "/metamodel/property-categories/" + category,
				object().put("name", unique("Category")), old))).isOne();
		assertThat(stale(call(HttpMethod.DELETE, "/metamodel/property-categories/" + category, null, old))).isOne();
		assertThat(stale(call(HttpMethod.PUT, "/metamodel/property-definitions/" + trust,
				object().put("name", unique("Trust")).put("dataType", "enum"), old))).isEqualTo(2);
		assertThat(stale(call(HttpMethod.DELETE, "/metamodel/property-definitions/" + trust, null, old))).isEqualTo(2);
		assertThat(stale(call(HttpMethod.POST, "/metamodel/property-definitions/" + trust + "/enum-values",
				object().put("value", "low"), old))).isEqualTo(2);
		assertThat(stale(call(HttpMethod.DELETE,
				"/metamodel/property-definitions/" + trust + "/enum-values/" + high, null, old))).isEqualTo(2);
		assertThat(version("metatypes", zone)).isEqualTo(2);
		assertThat(version("property_definitions", trust)).isEqualTo(2);
		assertThat(version("property_categories", category)).isOne();
	}

	@Test
	void withoutIfMatchOrWithAnyTheWriteProceeds() {
		UUID category = id(created(call(HttpMethod.POST, "/metamodel/property-categories",
				object().put("name", unique("Category")), null)));
		MvcTestResult without = call(HttpMethod.PUT, "/metamodel/property-categories/" + category,
				object().put("name", unique("Category")), null);
		assertThat(without).as(() -> content(without)).hasStatus(HttpStatus.OK);
		assertThat(body(without).path("version").asInt()).isEqualTo(2);
		assertThat(without.getResponse().getHeader(HttpHeaders.ETAG)).isEqualTo("\"2\"");
		assertThat(call(HttpMethod.PUT, "/metamodel/property-categories/" + category,
				object().put("name", unique("Category")), "*")).hasStatus(HttpStatus.OK);
		assertThat(version("property_categories", category)).isEqualTo(3);
		// a weak or malformed value never matches
		assertThat(stale(call(HttpMethod.PUT, "/metamodel/property-categories/" + category,
				object().put("name", unique("Category")), "W/\"3\""))).isEqualTo(3);
		assertThat(stale(call(HttpMethod.PUT, "/metamodel/property-categories/" + category,
				object().put("name", unique("Category")), "3"))).isEqualTo(3);
	}

	@Test
	void notFoundComesBeforeAndRulesAfterTheVersionCheck() {
		assertThat(call(HttpMethod.PUT, "/metamodel/metatypes/" + UUID.randomUUID(), metatype("Zone"), "\"1\""))
			.hasStatus(HttpStatus.NOT_FOUND);
		String packageId = "versions-" + UUID.randomUUID().toString().substring(0, 8);
		importPackage(TestPackages.pkg(packageId, 1));
		UUID imported = UUID.fromString(TestPackages.pkg(packageId, 1).path("metatypes").get(0).path("id").asString());
		assertThat(stale(call(HttpMethod.PUT, "/metamodel/metatypes/" + imported, metatype("Zone"), "\"7\"")))
			.isOne();
		MvcTestResult readOnly = call(HttpMethod.PUT, "/metamodel/metatypes/" + imported, metatype("Zone"), "\"1\"");
		assertThat(readOnly).hasStatus(HttpStatus.CONFLICT);
		assertThat(body(readOnly).path("type").asString()).isEqualTo("urn:oea:problem:read-only-metatype");
	}

	@Test
	void versionsStartAtOneAndStayPositive() {
		UUID category = id(created(call(HttpMethod.POST, "/metamodel/property-categories",
				object().put("name", unique("Category")), null)));
		assertThat(version("property_categories", category)).isOne();
		assertThatThrownBy(
				() -> this.jdbc.update("UPDATE property_categories SET version = 0 WHERE id = ?", category))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(count("""
				SELECT count(*) FROM pg_constraint WHERE conname IN ('metatypes_version', 'property_definitions_version',
				    'property_categories_version')""")).isEqualTo(3);
	}

	/** Asserts 412 {@code stale-version} and returns {@code currentVersion}. */
	private int stale(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:stale-version");
		return body(result).path("currentVersion").asInt();
	}

	private int version(String table, UUID id) {
		return this.jdbc.queryForObject("SELECT version FROM " + table + " WHERE id = ?", Integer.class, id);
	}

	private UUID property(String name, String dataType) {
		return id(created(call(HttpMethod.POST, "/metamodel/property-definitions",
				object().put("name", unique(name)).put("dataType", dataType), null)));
	}

	private static ObjectNode metatype(String name) {
		return object().put("name", unique(name)).put("configId", CONFIG);
	}

	private static ObjectNode object() {
		return JsonMapper.shared().createObjectNode();
	}

	private void importPackage(ObjectNode pkg) {
		MvcTestResult result = call(HttpMethod.POST, "/admin/metamodel/import", pkg, null);
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.OK);
	}

	private MvcTestResult call(HttpMethod method, String path, ObjectNode body, String ifMatch) {
		var request = this.mvc.method(method)
			.uri("/api/v1" + path)
			.cookie(new Cookie(SessionCookie.NAME, this.admin.token()))
			.header(CsrfOriginFilter.HEADER, this.admin.csrfToken());
		if (ifMatch != null) {
			request = request.header(HttpHeaders.IF_MATCH, ifMatch);
		}
		if (body != null) {
			request = request.contentType("application/json").content(body.toString());
		}
		return request.exchange();
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private static JsonNode created(MvcTestResult result) {
		assertThat(result).as(() -> content(result)).hasStatus(HttpStatus.CREATED);
		return body(result);
	}

	private static UUID id(JsonNode node) {
		return UUID.fromString(node.path("id").asString());
	}

	private static String type(MvcTestResult result) {
		return body(result).path("type").asString();
	}

	private static String unique(String name) {
		return "test " + name + " " + UUID.randomUUID().toString().substring(0, 8);
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
		try {
			return JsonMapper.shared().readTree(content(result));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-versions").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

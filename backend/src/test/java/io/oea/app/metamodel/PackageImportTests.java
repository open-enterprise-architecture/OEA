package io.oea.app.metamodel;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.auth.TestPrincipals;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Spec metamodel/package-import on an own database (REQ-033, REQ-114, REQ-184). */
@SpringBootTest(properties = "oea.metamodel.import.max-size=64KB")
@AutoConfigureMockMvc
class PackageImportTests {

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

	private TestSession metamodelAdmin;

	@BeforeEach
	void principal() {
		this.metamodelAdmin = new TestPrincipals(this.sessions, this.accounts, this.jdbc)
			.personWithIdpRole("metamodel-admin");
	}

	@Test
	void syntacticallyFaultyPackage() {
		int before = count("SELECT count(*) FROM metatypes");
		MvcTestResult result = post("{\n  \"packageId\": \"broken\",\n  oops\n}", this.metamodelAdmin);
		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		JsonNode problem = body(result);
		assertThat(problem.path("type").asString()).isEqualTo("urn:oea:problem:invalid-package");
		assertThat(problem.path("location").path("line").asInt()).isEqualTo(3);
		assertThat(problem.path("location").path("column").asInt()).isPositive();
		assertThat(count("SELECT count(*) FROM metatypes")).isEqualTo(before);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.package-rejected'"))
			.isPositive();
	}

	@Test
	void packageViolatingTheSchema() {
		ObjectNode withoutName = TestPackages.pkg("schema-test", 1);
		((ObjectNode) withoutName.withArray("metatypes").get(0)).remove("name");
		MvcTestResult missingName = post(withoutName.toString(), this.metamodelAdmin);
		assertThat(missingName).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(missingName).path("pointer").asString()).isEqualTo("/metatypes/0/name");

		ObjectNode unknownType = TestPackages.pkg("schema-test", 1);
		((ObjectNode) unknownType.withArray("propertyDefinitions").get(0)).put("dataType", "colour");
		MvcTestResult wrongType = post(unknownType.toString(), this.metamodelAdmin);
		assertThat(wrongType).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(wrongType).path("type").asString()).isEqualTo("urn:oea:problem:invalid-package");

		ObjectNode unknownProperty = TestPackages.pkg("schema-test", 1);
		((ObjectNode) unknownProperty.withArray("metatypes").get(0).withArray("propertyMappings").get(0))
			.withObject("propertyDefinition")
			.put("id", UUID.randomUUID().toString());
		MvcTestResult wrongReference = post(unknownProperty.toString(), this.metamodelAdmin);
		assertThat(wrongReference).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(wrongReference).path("pointer").asString())
			.isEqualTo("/metatypes/0/propertyMappings/0/propertyDefinition");
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = 'schema-test'")).isZero();
	}

	@Test
	void packageAboveTheSizeLimit() {
		ObjectNode large = TestPackages.pkg("large", 1);
		large.put("sourceInstanceId", "x".repeat(70 * 1024));
		assertThat(post(large.toString(), this.metamodelAdmin)).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = 'large'")).isZero();
	}

	// metamodel/connection-metatypes "Imported connection metatype" (US-036 AC4)
	@Test
	void connectionMetatypeWithConstraints() {
		ObjectNode pkg = TestPackages.pkg("connections", 2);
		ObjectNode link = (ObjectNode) pkg.withArray("metatypes").get(1);
		String source = pkg.withArray("metatypes").get(0).path("id").asString();
		link.put("isConnection", true).remove("propertyMappings");
		link.putArray("connectionConstraints").addObject().put("direction", "source").putObject("allowedMetatype")
			.put("id", source).put("name", "connections Type 1").put("abbreviation", "T1");
		MvcTestResult imported = post(pkg.toString(), this.metamodelAdmin);
		assertThat(imported).as(() -> content(imported)).hasStatus(HttpStatus.OK);
		String linkId = link.path("id").asString();
		assertThat(this.jdbc.queryForObject("SELECT is_connection FROM metatypes WHERE id = ?::uuid", Boolean.class,
				linkId)).isTrue();
		assertThat(this.jdbc.queryForObject("""
				SELECT allowed_metatype_id::text FROM metatype_connection_constraints
				WHERE metatype_id = ?::uuid AND direction = 'source'""", String.class, linkId)).isEqualTo(source);

		// an element metatype cannot become a connection metatype at a re-import
		ObjectNode changed = TestPackages.pkg("connections", 2);
		((ObjectNode) changed.withArray("metatypes").get(0)).put("isConnection", true);
		MvcTestResult kind = post(changed.toString(), this.metamodelAdmin);
		assertThat(kind).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(kind).path("pointer").asString()).isEqualTo("/metatypes/0/isConnection");

		// an endpoint of another package is rejected
		ObjectNode foreign = TestPackages.pkg("connections-foreign", 1);
		ObjectNode flow = (ObjectNode) foreign.withArray("metatypes").get(0);
		flow.put("isConnection", true).remove("propertyMappings");
		flow.putArray("connectionConstraints").addObject().put("direction", "target").putObject("allowedMetatype")
			.put("id", source).put("name", "connections Type 1").put("abbreviation", "T1");
		MvcTestResult other = post(foreign.toString(), this.metamodelAdmin);
		assertThat(other).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(other).path("pointer").asString()).isEqualTo("/metatypes/0/connectionConstraints");

		// element metatypes have no constraints
		ObjectNode element = TestPackages.pkg("connections-element", 1);
		((ObjectNode) element.withArray("metatypes").get(0)).putArray("connectionConstraints").addObject()
			.put("direction", "source").putObject("allowedMetatype").put("id", source).put("name", "x")
			.put("abbreviation", "x");
		assertThat(post(element.toString(), this.metamodelAdmin)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
	}

	@Test
	void unsupportedPackageSections() {
		ObjectNode replace = TestPackages.pkg("unsupported", 1).put("strategy", "replace");
		MvcTestResult strategy = post(replace.toString(), this.metamodelAdmin);
		assertThat(strategy).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(strategy).path("type").asString()).isEqualTo("urn:oea:problem:unsupported-package-content");

		ObjectNode viewpoints = TestPackages.pkg("unsupported", 1);
		viewpoints.putArray("viewpoints").addObject().put("id", "landscape").put("name", "Landscape");
		MvcTestResult sections = post(viewpoints.toString(), this.metamodelAdmin);
		assertThat(sections).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(sections).path("pointer").asString()).isEqualTo("/viewpoints");

		// translation bundles are supported since us-140-dynamic-labels; a malformed key is still rejected
		ObjectNode translations = TestPackages.pkg("unsupported", 1);
		translations.withObject("i18nBundles").withObject("de").put("metatype.x.name", "Typ");
		MvcTestResult bundles = post(translations.toString(), this.metamodelAdmin);
		assertThat(bundles).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(bundles).path("pointer").asString()).isEqualTo("/i18nBundles/de/metatype.x.name");
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = 'unsupported'")).isZero();
	}

	@Test
	void packageWithFiveMetatypesAndItsBatch() {
		MvcTestResult result = post(TestPackages.pkg("five-types", 5).toString(), this.metamodelAdmin);
		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode outcome = body(result);
		assertThat(outcome.path("status").asString()).isEqualTo("completed");
		assertThat(outcome.path("created").asInt()).isEqualTo(5);
		String batchId = outcome.path("batchId").asString();
		assertThat(this.jdbc.queryForList("""
				SELECT scope || ':' || source_package_id || ':' || source_package_version FROM metatypes
				WHERE source_package_id = 'five-types'""", String.class))
			.hasSize(5)
			.allMatch("imported:five-types:1.0.0"::equals);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.package-imported' "
				+ "AND details ->> 'batchId' = '" + batchId + "'")).isOne();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'metamodel.metatype-imported' "
				+ "AND details ->> 'batchId' = '" + batchId + "'")).isEqualTo(5);
	}

	@Test
	void reimportOfTheSamePackage() {
		ObjectNode first = TestPackages.pkg("reimport", 3);
		assertThat(post(first.toString(), this.metamodelAdmin)).hasStatus(HttpStatus.OK);
		((ObjectNode) first.withArray("metatypes").get(1)).put("name", "Renamed Type");
		MvcTestResult again = post(first.toString(), this.metamodelAdmin);
		assertThat(again).hasStatus(HttpStatus.OK);
		assertThat(body(again).path("created").asInt()).isZero();
		assertThat(body(again).path("updated").asInt()).isOne();
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = 'reimport'")).isEqualTo(3);
		assertThat(count("SELECT count(*) FROM metatypes WHERE name = 'Renamed Type'")).isOne();
	}

	@Test
	void importWithoutPermission() {
		TestSession noRole = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithoutRole();
		assertThat(post(TestPackages.pkg("no-permission", 1).toString(), noRole)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = 'no-permission'")).isZero();
	}

	@Test
	void nameAlreadyUsedByAnotherPackage() {
		assertThat(post(TestPackages.pkg("owner-package", 2).toString(), this.metamodelAdmin)).hasStatus(HttpStatus.OK);
		ObjectNode other = TestPackages.pkg("other-package", 2);
		// the second metatype takes a name of the first package
		((ObjectNode) other.withArray("metatypes").get(1)).put("name", "owner-package Type 2");
		MvcTestResult result = post(other.toString(), this.metamodelAdmin);
		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		JsonNode conflict = body(result).path("conflictList").get(0);
		assertThat(conflict.path("element").asString()).isEqualTo("metatype owner-package Type 2");
		assertThat(conflict.path("reason").asString()).isEqualTo("other-package");
		assertThat(count("SELECT count(*) FROM metatypes WHERE source_package_id = 'other-package'")).isZero();
		assertThat(count("SELECT count(*) FROM property_definitions WHERE source_package_id = 'other-package'"))
			.isZero();
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-import").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}


	private MvcTestResult post(String body, TestSession session) {
		return this.mvc.post()
			.uri("/api/v1/admin/metamodel/import")
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body)
			.exchange();
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
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
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}

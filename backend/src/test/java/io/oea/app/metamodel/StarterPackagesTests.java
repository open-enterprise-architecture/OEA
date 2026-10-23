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
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * Specs metamodel/starter-packages and metamodel/metatype-catalog on an own database; the
 * first test sees the instance before the starter import.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StarterPackagesTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOGAF = "oea-starter-togaf-classic";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", StarterPackagesTests::tokenFile);
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
	private StarterPackages starterPackages;

	@Test
	@Order(1)
	void packageListOfANewInstance() {
		MvcTestResult result = get("/api/v1/admin/metamodel/starter-packages", principals().personWithIdpRole("metamodel-admin"));
		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode togaf = body(result).get(0);
		assertThat(togaf.path("packageId").asString()).isEqualTo(TOGAF);
		assertThat(togaf.path("metatypeCount").asInt()).isEqualTo(7);
		assertThat(togaf.path("propertyDefinitionCount").asInt()).isOne();
		assertThat(togaf.path("metatypeNames").valueStream().map(JsonNode::asString))
			.contains("Application Component", "Data Object", "Technology Component");
		assertThat(togaf.path("imported").asBoolean()).isFalse();
	}

	@Test
	@Order(2)
	void starterConfigurationImported() {
		MvcTestResult result = post("/api/v1/admin/metamodel/starter-packages/" + TOGAF + "/import",
				principals().personWithIdpRole("metamodel-admin"));
		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode outcome = body(result);
		assertThat(outcome.path("created").asInt()).isEqualTo(7);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM metatypes WHERE scope = 'imported' "
				+ "AND source_package_id = ?", Integer.class, TOGAF)).isEqualTo(7);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM metatype_property_mappings m JOIN property_definitions d ON d.id = m.property_def_id
				WHERE d.name = 'Status'""", Integer.class)).isEqualTo(7);
		assertThat(this.jdbc.queryForList("""
				SELECT v.value FROM property_enum_values v JOIN property_definitions d ON d.id = v.property_def_id
				WHERE d.name = 'Status' ORDER BY v.sort_order""", String.class))
			.containsExactly("planned", "active", "phasing-out", "retired");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = "
				+ "'metamodel.metatype-imported' AND details ->> 'batchId' = ?", Integer.class,
				outcome.path("batchId").asString())).isEqualTo(7);
		assertThat(body(get("/api/v1/admin/metamodel/starter-packages",
				principals().personWithIdpRole("metamodel-admin"))).get(0).path("imported").asBoolean()).isTrue();
	}

	@Test
	@Order(3)
	void metatypesAfterTheStarterImport() {
		MvcTestResult result = get("/api/v1/metamodel/metatypes", principals().personWithIdpRole("viewer"));
		assertThat(result).hasStatus(HttpStatus.OK);
		JsonNode metatypes = body(result);
		assertThat(metatypes).hasSize(7);
		assertThat(metatypes).allSatisfy((metatype) -> {
			assertThat(metatype.path("scope").asString()).isEqualTo("imported");
			assertThat(metatype.path("sourcePackage").path("packageId").asString()).isEqualTo(TOGAF);
		});
		JsonNode component = metatypes.get(0);
		assertThat(component.path("name").asString()).isEqualTo("Application Component");
		JsonNode status = component.path("propertyMappings").get(0).path("propertyDefinition");
		assertThat(status.path("name").asString()).isEqualTo("Status");
		assertThat(status.path("enumValues")).hasSize(4);

		MvcTestResult one = get("/api/v1/metamodel/metatypes/" + component.path("id").asString(),
				principals().personWithIdpRole("viewer"));
		assertThat(one).hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.abbreviation").isEqualTo("AC");
	}

	@Test
	@Order(4)
	void readingWithoutPermissionAndUnknownPackage() {
		assertThat(get("/api/v1/metamodel/metatypes", principals().personWithoutRole())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post("/api/v1/admin/metamodel/starter-packages/unknown-package/import",
				principals().personWithIdpRole("metamodel-admin"))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void everyShippedPackageIsValid() {
		// the context started, so StarterPackageCheck accepted every shipped package
		assertThat(this.starterPackages.shipped()).extracting(StarterPackages.Shipped::packageId).contains(TOGAF);
	}

	private TestPrincipals principals() {
		return new TestPrincipals(this.sessions, this.accounts, this.jdbc);
	}

	private MvcTestResult get(String uri, TestSession session) {
		return this.mvc.get().uri(uri).cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
	}

	private MvcTestResult post(String uri, TestSession session) {
		return this.mvc.post()
			.uri(uri)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tokenFile() {
		try {
			return Files.createTempDirectory("oea-starter").resolve("setup-token").toString();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}

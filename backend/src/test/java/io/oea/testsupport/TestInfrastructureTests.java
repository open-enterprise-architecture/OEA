package io.oea.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.auth.DevRealm;
import io.oea.testsupport.auth.OidcLogin;
import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaKeycloak;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestDatabase;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;

/** The shared test infrastructure of design D7: container singletons and the real login flow. */
@SpringBootTest
@AutoConfigureMockMvc
class TestInfrastructureTests {

	@DynamicPropertySource
	static void containers(DynamicPropertyRegistry registry) {
		OeaTestDatabase.registerRuntimeDataSource(registry);
	}

	@Autowired
	private MockMvcTester mvc;

	@Test
	void applicationStartsAgainstPostgresAndKeycloakSingletons() {
		assertThat(OeaKeycloak.issuer()).endsWith("/realms/" + OeaKeycloak.REALM);
		assertThat(this.mvc.get().uri("/api/v1/health")).hasStatus(HttpStatus.OK);
	}

	@Test
	void clonedDatabaseIsProvisionedLikeProduction() throws Exception {
		OeaDatabase database = OeaDatabases.freshMigrated();
		try (Connection connection = OeaDatabases.asSuperuser(database); Statement statement = connection.createStatement()) {
			// database-level privileges of the provisioning script (ADR-037), not copied by the template
			assertThat(single(statement, "SELECT has_database_privilege('public', current_database(), 'CONNECT')"))
				.isEqualTo("f");
			for (String role : OeaPostgres.ROLE_PASSWORDS.keySet()) {
				assertThat(single(statement, "SELECT has_database_privilege('" + role + "', current_database(), 'CONNECT')"))
					.as(role)
					.isEqualTo("t");
			}
			assertThat(single(statement,
					"SELECT string_agg(nspname, ',' ORDER BY nspname) FROM pg_namespace WHERE nspname IN ('audit', 'reporting', 'reporting_ctx')"))
				.isEqualTo("audit,reporting,reporting_ctx");
			assertThat(single(statement, "SELECT max(version::int)::text FROM flyway_schema_history WHERE success"))
				.isEqualTo(single(statement, "SELECT max(version::int)::text FROM flyway_schema_history"));
			assertThat(single(statement, "SELECT count(*)::text FROM flyway_schema_history WHERE success")).isNotEqualTo("0");
		}
		finally {
			database.stop();
		}
	}

	@Test
	void clonedDatabasesAreIndependent() throws Exception {
		OeaDatabase first = OeaDatabases.freshMigrated();
		OeaDatabase second = OeaDatabases.freshMigrated();
		try {
			try (Connection connection = OeaDatabases.asSuperuser(first); Statement statement = connection.createStatement()) {
				statement.execute("INSERT INTO persons (name, email, status) VALUES ('Only First', 'first@example.org', 'active')");
			}
			try (Connection connection = OeaDatabases.asSuperuser(second); Statement statement = connection.createStatement()) {
				assertThat(single(statement, "SELECT count(*)::text FROM persons WHERE email = 'first@example.org'"))
					.isEqualTo("0");
			}
		}
		finally {
			first.stop();
			second.stop();
		}
	}

	@Test
	void applicationStartsAgainstAClonedDatabase() throws Exception {
		OeaDatabase database = OeaDatabases.freshMigrated();
		// the startup checks of DatabaseStartupChecks verify the runtime role's privileges
		try (ConfigurableApplicationContext application = OeaApplications.start(database,
				Files.createTempDirectory("oea-clone").resolve("setup-token"), WebApplicationType.NONE)) {
			assertThat(application.isRunning()).isTrue();
		}
		finally {
			database.stop();
		}
	}

	private static String single(Statement statement, String sql) throws Exception {
		try (ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

	@Test
	void viewerLoginWithPkceYieldsGroupIdentifiers() {
		JsonNode claims = OidcLogin.login(OeaKeycloak.issuer(), "dev-viewer").accessTokenClaims();
		assertThat(claims.path("preferred_username").asString()).isEqualTo("dev-viewer");
		assertThat(groups(claims)).containsExactly(DevRealm.groupId("oea-viewers"));
	}

	@Test
	void privilegedLoginPassesTheSecondFactor() {
		JsonNode claims = OidcLogin.login(OeaKeycloak.issuer(), "dev-system-admin").accessTokenClaims();
		assertThat(claims.path("preferred_username").asString()).isEqualTo("dev-system-admin");
		assertThat(groups(claims)).containsExactly(DevRealm.groupId("oea-system-admins"));
	}

	@Test
	void idTokensNameTheAuthenticationMethods() {
		// SoD-8 (ADR-032): privileged roles need a second factor, which OEA reads from amr
		assertThat(strings(OidcLogin.login(OeaKeycloak.issuer(), "dev-system-admin").idTokenClaims().path("amr")))
			.containsExactlyInAnyOrder("pwd", "otp");
		assertThat(strings(OidcLogin.login(OeaKeycloak.issuer(), "dev-admin-no-mfa").idTokenClaims().path("amr")))
			.containsExactly("pwd");
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach((value) -> values.add(value.asString()));
		return values;
	}

	private static List<String> groups(JsonNode claims) {
		List<String> groups = new ArrayList<>();
		claims.path("groups").forEach((group) -> groups.add(group.asString()));
		return groups;
	}

}

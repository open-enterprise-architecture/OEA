package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.security.AuthorizationMatrix.Operation;
import io.oea.testsupport.security.AuthorizationMatrix;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Contract conditions enforced by the handler deny a principal holding only the base permissions
 * (ADR-032 §8, design D3 of us-046-create-catalog). Runs on an own database: the probe roles come
 * from IdP group mappings, whose stored providers must not reach the shared test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HandlerEnforcedConditionsTests {

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
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Autowired
	private ContractAuthorizationRules rules;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void conditionsEnforcedByTheHandlerDenyHoldersOfTheBasePermissionsOnly() throws Exception {
		List<Operation> enforced = AuthorizationMatrix.operations(this.requestMappingHandlerMapping, this.rules)
			.stream()
			.filter((operation) -> operation.conditions() != null)
			.toList();
		assertThat(enforced).isNotEmpty();
		List<String> permissionIds = this.jdbc.queryForList("SELECT id FROM permissions", String.class);
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		List<String> violations = new ArrayList<>();
		for (Operation operation : enforced) {
			TestSession probe = principals.personWithIdpRole(operation.conditions().probeRole());
			MvcTestResult result = this.mvc.method(HttpMethod.valueOf(operation.method()))
				.uri(operation.path())
				.cookie(new Cookie(SessionCookie.NAME, probe.token()))
				.header(CsrfOriginFilter.HEADER, probe.csrfToken())
				.contentType(operation.contentType())
				.content(operation.conditions().probeBody())
				.exchange();
			int status = result.getResponse().getStatus();
			String body = result.getResponse().getContentAsString();
			// the generic problem of the filter, naming no permission (REQ-158)
			if (status != 403 || !body.contains("urn:oea:problem:forbidden")
					|| permissionIds.stream().anyMatch(body::contains)) {
				violations.add(operation + " as " + operation.conditions().probeRole() + ": " + status + " " + body);
			}
		}
		assertThat(violations).isEmpty();
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-conditions").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

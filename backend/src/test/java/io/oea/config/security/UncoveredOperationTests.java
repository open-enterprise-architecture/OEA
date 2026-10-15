package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.TestcontainersConfiguration;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.security.AuthorizationMatrix;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** A controller outside the contract is detected by the matrix and denied for everybody (D3). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, UncoveredOperationTests.UndeclaredController.class })
class UncoveredOperationTests {

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
	void undeclaredControllerIsReportedAndDenied() {
		assertThat(AuthorizationMatrix.uncovered(AuthorizationMatrix.operations(this.requestMappingHandlerMapping,
				this.rules)))
			.extracting(AuthorizationMatrix.Operation::handler)
			.containsExactly("UndeclaredController#secret");
		TestSession admin = new TestPrincipals(this.sessions, this.accounts, this.jdbc).systemAdmin();
		assertThat(this.mvc.get().uri("/api/v1/test-only/secret").cookie(new Cookie(SessionCookie.NAME, admin.token())))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@RestController
	@TestConfiguration
	static class UndeclaredController {

		@GetMapping("/api/v1/test-only/secret")
		String secret() {
			return "must never be reachable";
		}

		@Bean
		String marker() {
			return "undeclared";
		}

	}

}

package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.TestcontainersConfiguration;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.security.AuthorizationMatrix;
import io.oea.testsupport.security.AuthorizationMatrix.Operation;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spec platform/authorization: Every implemented operation is covered (REQ-158 AC3) and audits
 * its denial (REQ-175 AC1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationMatrixTests {

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
	void everyServedOperationIsDeclaredAndDeniedWithoutPermission() {
		List<Operation> operations = AuthorizationMatrix.operations(this.requestMappingHandlerMapping, this.rules);
		assertThat(operations).hasSizeGreaterThan(10);
		assertThat(AuthorizationMatrix.uncovered(operations)).isEmpty();

		TestSession noRole = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithoutRole();
		List<String> violations = new ArrayList<>();
		for (Operation operation : operations) {
			ContractAuthorizationRules.Rule.Access access = operation.rule().access();
			if (access == ContractAuthorizationRules.Rule.Access.PUBLIC) {
				continue;
			}
			int anonymous = call(operation, null).getResponse().getStatus();
			if (anonymous != 401) {
				violations.add(operation + " without session: " + anonymous);
			}
			if (access == ContractAuthorizationRules.Rule.Access.PERMISSION) {
				int denied = call(operation, noRole).getResponse().getStatus();
				if (denied != 403) {
					violations.add(operation + " without permission: " + denied);
				}
				else if (deniedEvents(noRole, operation.rule().operationId()) != 1) {
					violations.add(operation + " without permission: no authz.denied event (REQ-175 AC1)");
				}
			}
		}
		assertThat(violations).isEmpty();
	}

	private int deniedEvents(TestSession session, String operationId) {
		return this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events e JOIN sessions s ON s.person_id = e.actor_person_id
				WHERE e.event_type = 'authz.denied' AND s.id = ? AND e.details ->> 'operation' = ?""", Integer.class,
				session.sessionId(), operationId);
	}

	private MvcTestResult call(Operation operation, TestSession session) {
		var request = this.mvc.method(HttpMethod.valueOf(operation.method())).uri(operation.path());
		if (session != null) {
			request = request.cookie(new Cookie(SessionCookie.NAME, session.token()))
				.header(CsrfOriginFilter.HEADER, session.csrfToken());
		}
		if (!operation.method().equals("GET") && !operation.method().equals("DELETE")) {
			request = operation.contentType().startsWith("multipart/")
					? request.contentType(operation.contentType() + "; boundary=oea").content("--oea--\r\n")
					: request.contentType(operation.contentType()).content("{}");
		}
		return request.exchange();
	}

}

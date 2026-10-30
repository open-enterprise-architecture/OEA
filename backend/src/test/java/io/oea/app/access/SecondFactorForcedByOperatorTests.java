package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.service.PasswordHasher;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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

/** Spec identity/second-factor-enforcement, operator floor (design D1 of us-031-second-factor-enforcement). */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecondFactorForcedByOperatorTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-enforcement-forced").resolve("setup-token");
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", tokenFile::toString);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
		registry.add("oea.auth.second-factor.enforced", () -> "true");
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
	private PasswordHasher hasher;

	@Test
	@Order(1)
	void theSystemAdminAccountIsNotCovered() {
		// setup phase (ADR-042): the system admin account logs in with its password alone
		this.jdbc.update("INSERT INTO system_settings (id, instance_slug) VALUES (1, 'acme-corp')");
		UUID account = UUID.randomUUID();
		this.jdbc.update("INSERT INTO system_admin_accounts (id, login_name, status) VALUES (?, 'admin', 'active')",
				account);
		UUID credential = UUID.randomUUID();
		this.jdbc.update("INSERT INTO local_credentials (id, system_admin_account_id, type) VALUES (?, ?, 'password')",
				credential, account);
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credential,
				this.hasher.hash("admin-password-123"));
		MvcTestResult login = this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.contentType("application/json")
			.content("{\"loginName\":\"admin\",\"password\":\"admin-password-123\"}")
			.exchange();
		assertThat(login).hasStatus(HttpStatus.CREATED);
		assertThat(body(login).path("requiredAction").isNull()).isTrue();
	}

	@Test
	@Order(2)
	void forcedByTheOperator() {
		this.jdbc.update("""
				UPDATE system_settings SET setup_phase_ended_at = now(), setup_phase_ended_by_action = 'identity-binding'
				WHERE setup_phase_ended_at IS NULL""");
		TestSession admin = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("system-admin");
		JsonNode policy = body(this.mvc.get()
			.uri("/api/v1/admin/settings/second-factor-policy")
			.cookie(new Cookie(SessionCookie.NAME, admin.token()))
			.exchange());
		assertThat(policy.path("enforced").asBoolean()).isTrue();
		assertThat(policy.path("enforcedByOperator").asBoolean()).isTrue();
		MvcTestResult off = this.mvc.put()
			.uri("/api/v1/admin/settings/second-factor-policy")
			.cookie(new Cookie(SessionCookie.NAME, admin.token()))
			.header(CsrfOriginFilter.HEADER, admin.csrfToken())
			.contentType("application/json")
			.content("{\"enforced\":false,\"exemptRoles\":[]}")
			.exchange();
		assertThat(off).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(off).path("invalidFields").toString()).isEqualTo("[\"enforced\"]");
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

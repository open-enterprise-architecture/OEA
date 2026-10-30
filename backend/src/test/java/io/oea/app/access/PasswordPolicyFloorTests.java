package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.service.PasswordHasher;
import io.oea.core.service.PasswordPolicy;
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

/**
 * Spec identity/password-policy, operator floors (REQ-028; design D1, D2 of
 * us-027-password-administration): minimum length 16, digits required, expiry at most 90 days,
 * history at least 2; password login of persons is not permitted by the operator.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordPolicyFloorTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-password-floors").resolve("setup-token");
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", tokenFile::toString);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
		registry.add("oea.auth.password-policy.min-length", () -> "16");
		registry.add("oea.auth.password-policy.require-digits", () -> "true");
		registry.add("oea.auth.password-policy.max-expiry-days", () -> "90");
		registry.add("oea.auth.password-policy.min-history-count", () -> "2");
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static final String VALID = """
			{"minLength":16,"requireUppercase":false,"requireLowercase":false,"requireDigits":true,
			 "requireSpecialChars":false,"expiryDays":60,"historyCount":2,"personPasswordLogin":true}""";

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

	@Autowired
	private PasswordPolicies policies;

	private TestSession settingsAdmin;

	@BeforeEach
	void administrator() {
		this.jdbc.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'identity-binding') ON CONFLICT (id) DO NOTHING""");
		this.jdbc.update("UPDATE system_settings SET person_password_login = true");
		this.settingsAdmin = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithIdpRole("system-admin");
	}

	@Test
	void theEffectivePolicyIsTheStricterOfFloorAndStoredValue() {
		store(14, true, false, 0, 1);
		assertThat(this.policies.effective()).isEqualTo(new PasswordPolicy(16, true, false, true, false, 90, 2));
		store(20, false, false, 120, 5);
		assertThat(this.policies.effective()).isEqualTo(new PasswordPolicy(20, false, false, true, false, 90, 5));
		store(16, false, false, 30, 2);
		assertThat(this.policies.effective().expiryDays()).isEqualTo(30);
	}

	@Test
	void floorStricterThanTheStoredPolicy() {
		// stored before the operator raised the floor
		store(14, false, false, 0, 0);
		UUID account = UUID.randomUUID();
		this.jdbc.update("INSERT INTO system_admin_accounts (id, login_name, status) VALUES (?, ?, 'active')", account,
				"admin-" + account.toString().substring(0, 8));
		UUID credential = UUID.randomUUID();
		this.jdbc.update("INSERT INTO local_credentials (id, system_admin_account_id, type) VALUES (?, ?, 'password')",
				credential, account);
		this.jdbc.update("INSERT INTO password_credentials (credential_id, password_hash) VALUES (?, ?)", credential,
				this.hasher.hash("current-password-1"));
		Sessions.NewSession created = this.sessions.createForSystemAdmin(this.accounts.findById(account).orElseThrow(),
				true, "10.4.0.1", "test");
		TestSession session = new TestSession(created.token(), created.csrfToken(), created.principal().sessionId());
		MvcTestResult rejected = post("/api/v1/me/password", session,
				"{\"currentPassword\":\"current-password-1\",\"newPassword\":\"fourteen-char1\"}");
		assertThat(rejected).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(rejected).path("invalidFields").toString()).isEqualTo("[\"minLength\"]");
		assertThat(post("/api/v1/me/password", session,
				"{\"currentPassword\":\"current-password-1\",\"newPassword\":\"sixteen-chars-12\"}"))
			.hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void belowAFloor() {
		JsonNode policy = body(get("/api/v1/admin/settings/password-policy", this.settingsAdmin));
		assertThat(policy.path("floors").path("minLength").asInt()).isEqualTo(16);
		assertThat(policy.path("floors").path("maxExpiryDays").asInt()).isEqualTo(90);
		assertThat(policy.path("floors").path("minHistoryCount").asInt()).isEqualTo(2);
		assertThat(policy.path("personPasswordLoginPermitted").asBoolean()).isFalse();

		MvcTestResult rejected = put("/api/v1/admin/settings/password-policy", this.settingsAdmin,
				VALID.replace("\"minLength\":16", "\"minLength\":14")
					.replace("\"requireDigits\":true", "\"requireDigits\":false")
					.replace("\"expiryDays\":60", "\"expiryDays\":0")
					.replace("\"historyCount\":2", "\"historyCount\":1"));
		assertThat(rejected).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(rejected).path("invalidFields").toString())
			.isEqualTo("[\"minLength\",\"requireDigits\",\"expiryDays\",\"historyCount\"]");
		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin, VALID)).hasStatus(HttpStatus.OK);
		assertThat(body(get("/api/v1/admin/settings/password-policy", this.settingsAdmin)).path("expiryDays").asInt())
			.isEqualTo(60);
	}

	@Test
	void theSwitchIsNotSwitchedOnWithoutThePermissionOfTheOperator() {
		String off = VALID.replace("\"personPasswordLogin\":true", "\"personPasswordLogin\":false");
		assertThat(put("/api/v1/admin/settings/password-policy", this.settingsAdmin, off)).hasStatus(HttpStatus.OK);
		MvcTestResult on = put("/api/v1/admin/settings/password-policy", this.settingsAdmin, VALID);
		assertThat(on).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(body(on).path("invalidFields").toString()).isEqualTo("[\"personPasswordLogin\"]");
	}

	private void store(int minLength, boolean uppercase, boolean digits, int expiryDays, int historyCount) {
		this.jdbc.update("""
				UPDATE system_settings SET password_min_length = ?, password_require_uppercase = ?,
				       password_require_digits = ?, password_expiry_days = ?, password_history_count = ?""", minLength,
				uppercase, digits, expiryDays, historyCount);
	}

	private MvcTestResult post(String uri, TestSession session, String body) {
		return this.mvc.post()
			.uri(uri)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body)
			.exchange();
	}

	private MvcTestResult put(String uri, TestSession session, String body) {
		return this.mvc.put()
			.uri(uri)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body)
			.exchange();
	}

	private MvcTestResult get(String uri, TestSession session) {
		return this.mvc.get().uri(uri).cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
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

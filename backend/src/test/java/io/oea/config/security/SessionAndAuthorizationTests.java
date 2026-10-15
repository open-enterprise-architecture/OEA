package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.TestcontainersConfiguration;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Specs identity/sessions and platform/authorization (design D1–D4). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionAndAuthorizationTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@Autowired
	private JdbcTemplate jdbc;

	private TestPrincipals principals;

	@BeforeEach
	void principals() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
	}

	@Test
	void requestWithoutSessionIsUnauthorized() {
		assertThat(this.mvc.get().uri("/api/v1/me")).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:unauthenticated");
		assertThat(this.mvc.get().uri("/api/v1/no-such-operation")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void idleOrExpiredSessionIsRejected() {
		TestSession idle = this.principals.systemAdmin();
		this.jdbc.update("UPDATE sessions SET last_activity_at = now() - interval '31 minutes' WHERE id = ?",
				idle.sessionId());
		assertThat(get("/api/v1/me", idle)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.jdbc.queryForObject("SELECT revoked_reason FROM sessions WHERE id = ?", String.class,
				idle.sessionId()))
			.isEqualTo("expired");

		TestSession old = this.principals.systemAdmin();
		this.jdbc.update("UPDATE sessions SET absolute_expires_at = now() - interval '1 second' WHERE id = ?",
				old.sessionId());
		assertThat(get("/api/v1/me", old)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void responsesCarryTheSecurityHeaders() {
		MvcTestResult result = get("/api/v1/me", this.principals.systemAdmin());
		assertThat(result).hasStatus(HttpStatus.OK)
			.headers()
			.hasValue("X-Content-Type-Options", "nosniff")
			.hasValue("Referrer-Policy", "no-referrer")
			.containsHeader("Strict-Transport-Security");
		assertThat(result.getResponse().getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
		assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
	}

	@Test
	void stateChangingRequestsNeedTheCsrfTokenAndAnAllowedOrigin() {
		TestSession session = this.principals.systemAdmin();
		assertThat(this.mvc.post().uri("/api/v1/auth/logout").cookie(cookie(session)))
			.hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:csrf-rejected");
		assertThat(this.mvc.post()
			.uri("/api/v1/auth/logout")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.header("Origin", "https://evil.example")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.header("Origin", "https://evil.example")
			.contentType("application/json")
			.content("{\"loginName\":\"x\",\"password\":\"y\"}")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(this.mvc.post()
			.uri("/api/v1/auth/logout")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())).hasStatus(HttpStatus.OK);
	}

	@Test
	void currentPrincipalOfTheSystemAdmin() {
		TestSession session = this.principals.systemAdmin();
		assertThat(get("/api/v1/me", session)).hasStatus(HttpStatus.OK)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"principal": {"principalType": "system-admin-account"}, "mfaSatisfied": false,
					 "roles": [{"role": {"id": "system-admin"}, "source": "system-admin-account", "effective": true}],
					 "csrfToken": "%s"}""".formatted(session.csrfToken()));
		assertThat(get("/api/v1/me", session)).bodyJson()
			.extractingPath("$.permissions[*].permission")
			.asArray()
			.containsExactlyInAnyOrder("role:assign", "person:manage", "service-account:manage", "auth-config:manage",
					"settings:manage");
	}

	@Test
	void logoutEndsTheSession() {
		TestSession session = this.principals.systemAdmin();
		MvcTestResult logout = this.mvc.post()
			.uri("/api/v1/auth/logout")
			.cookie(cookie(session))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.exchange();
		assertThat(logout).hasStatus(HttpStatus.OK).headers().hasValue("OEA-Purge-Local-Data", "session-ended");
		assertThat(logout.getResponse().getHeader("Set-Cookie")).startsWith("__Host-OEA_SESSION=;").contains("Max-Age=0");
		assertThat(get("/api/v1/me", session)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM audit.events WHERE event_type = 'auth.logout' AND target_id = ?", Integer.class,
				session.sessionId().toString()))
			.isEqualTo(1);
	}

	@Test
	void principalWithoutPermissionGetsAGenericForbidden() throws Exception {
		TestSession noRole = this.principals.personWithoutRole();
		MvcTestResult result = get("/api/v1/admin/permissions", noRole);
		assertThat(result).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:forbidden");
		assertThat(result.getResponse().getContentAsString()).doesNotContain("role:assign");
		assertThat(get("/api/v1/admin/roles", noRole)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(get("/api/v1/me", noRole)).hasStatus(HttpStatus.OK);
	}

	@Test
	void catalogAndRolesOfAFreshInstance() {
		TestSession admin = this.principals.systemAdmin();
		assertThat(get("/api/v1/admin/permissions", admin)).hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(20);
		assertThat(get("/api/v1/admin/permissions", admin)).bodyJson()
			.extractingPath("$[?(@.adminClass == true)].id")
			.asArray()
			.containsExactlyInAnyOrder("metamodel:write", "visibility:configure", "visibility-scope:assign", "role:assign",
					"person:manage", "service-account:manage", "auth-config:manage", "settings:manage");
		assertThat(get("/api/v1/admin/roles", admin)).hasStatus(HttpStatus.OK)
			.bodyJson()
			.extractingPath("$[*].id")
			.asArray()
			.containsExactlyInAnyOrder("portal-reader", "viewer", "analyst", "enterprise-architect",
					"integration-writer", "auditor", "metamodel-admin", "security-admin", "system-admin");
	}

	private MvcTestResult get(String uri, TestSession session) {
		return this.mvc.get().uri(uri).cookie(cookie(session)).exchange();
	}

	private static Cookie cookie(TestSession session) {
		return new Cookie(SessionCookie.NAME, session.token());
	}

}

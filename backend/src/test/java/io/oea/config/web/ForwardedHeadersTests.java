package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.bootstrapping.Bootstrapping;
import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Requirements "Client address behind a trusted reverse proxy" and "Trusted proxies
 * configurable" of platform/deployment with the default list of trusted proxies (AA-01; design
 * D1-D3 of s11-review-hardening). The test client connects from {@code 127.0.0.1}, which the
 * default list trusts like the Compose gateway in {@code 172.16.0.0/12}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForwardedHeadersTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final Path TOKEN_FILE = ProxiedInstance.tokenFile();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		ProxiedInstance.register(registry, postgres, TOKEN_FILE);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@LocalServerPort
	private int port;

	@Autowired
	private Bootstrapping bootstrapping;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	private ProxiedInstance instance;

	@BeforeEach
	void prepare() {
		ProxiedInstance.prepare(this.bootstrapping, this.jdbc, TOKEN_FILE);
		this.instance = ProxiedInstance.on(this.port);
	}

	// Requirement: Client address behind a trusted reverse proxy

	@Test
	void twoClientsBehindTheTrustedProxyAreThrottledSeparately() {
		failLogins(100, "203.0.113.10");
		HttpResponse<String> throttled = this.instance.login(ProxiedInstance.ADMIN, ProxiedInstance.PASSWORD,
				"203.0.113.10");
		assertThat(throttled.statusCode()).isEqualTo(401);
		assertThat(throttled.headers().firstValue("Retry-After")).isPresent();
		assertThat(this.instance.login(ProxiedInstance.ADMIN, ProxiedInstance.PASSWORD, "203.0.113.20").statusCode())
			.isEqualTo(201);
	}

	@Test
	void throttlingJustBelowTheLimit() {
		failLogins(99, "203.0.113.10");
		assertThat(this.instance.login(ProxiedInstance.ADMIN, ProxiedInstance.PASSWORD, "203.0.113.10").statusCode())
			.isEqualTo(201);
	}

	@Test
	void auditSourceAddress() {
		String loginName = "unknown-" + UUID.randomUUID();
		assertThat(this.instance.login(loginName, "wrong", "203.0.113.40").statusCode()).isEqualTo(401);
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*) FROM audit.events
				WHERE event_type = 'auth.login-failed' AND host(source_ip) = '203.0.113.40'""", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void originBehindATlsProxy() {
		TestSession person = person();
		assertThat(setLanguage(person, "https://oea.example.org").statusCode()).isEqualTo(204);
		assertThat(language(person)).isEqualTo("en");
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://evil.example.org", "http://oea.example.org" })
	void foreignOriginBehindATlsProxy(String origin) {
		TestSession person = person();
		assertThat(setLanguage(person, origin).statusCode()).isEqualTo(403);
		assertThat(language(person)).isNull();
	}

	/**
	 * Boot 4.1.2 default (design D1): {@code X-Forwarded-Proto: https} without a port header
	 * yields port 443, so an origin with the explicit port 443 matches and one with another
	 * port does not.
	 */
	@Test
	void forwardedHttpsWithoutPortYieldsPort443() {
		TestSession person = person();
		assertThat(setLanguage(person, "https://oea.example.org:8443").statusCode()).isEqualTo(403);
		assertThat(language(person)).isNull();
		assertThat(setLanguage(person, "https://oea.example.org:443").statusCode()).isEqualTo(204);
		assertThat(language(person)).isEqualTo("en");
	}

	// Requirement: Trusted proxies configurable

	@Test
	void defaultTrustsTheComposeGateway() {
		String loginName = "unknown-" + UUID.randomUUID();
		this.instance.login(loginName, "wrong", "203.0.113.50");
		assertThat(this.jdbc.queryForList("SELECT throttle_key FROM auth_throttle WHERE throttle_key LIKE 'address:%'",
				String.class)).containsExactly("address:203.0.113.50");
	}

	@Test
	void requestWithoutForwardedHeaderKeepsThePeerAddress() {
		this.instance.login("unknown-" + UUID.randomUUID(), "wrong", null);
		assertThat(this.jdbc.queryForList("SELECT throttle_key FROM auth_throttle WHERE throttle_key LIKE 'address:%'",
				String.class)).containsExactly("address:127.0.0.1");
	}

	private void failLogins(int count, String address) {
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		for (int i = 0; i < count; i++) {
			// different login names, so that only the per-address limit applies
			assertThat(this.instance.login("unknown-" + prefix + "-" + i, "wrong", address).statusCode()).isEqualTo(401);
		}
	}

	private TestSession person() {
		TestSession person = new TestPrincipals(this.sessions, this.accounts, this.jdbc).personWithoutRole();
		this.jdbc.update("UPDATE persons SET ui_language = NULL WHERE id = (SELECT person_id FROM sessions WHERE id = ?)",
				person.sessionId());
		return person;
	}

	/** A cookie-authenticated state change through the TLS proxy at {@code https://oea.example.org}. */
	private HttpResponse<String> setLanguage(TestSession session, String origin) {
		return ProxiedInstance.send(this.instance.request("/api/v1/me/preferences")
			.header("Cookie", SessionCookie.NAME + "=" + session.token())
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.header("Content-Type", "application/json")
			.header("Origin", origin)
			.header("X-Forwarded-For", "203.0.113.60")
			.header("X-Forwarded-Proto", "https")
			.header("X-Forwarded-Host", "oea.example.org")
			.PUT(HttpRequest.BodyPublishers.ofString("{\"uiLanguage\": \"en\"}")));
	}

	private String language(TestSession session) {
		return this.jdbc.queryForObject(
				"SELECT p.ui_language FROM persons p JOIN sessions s ON s.person_id = p.id WHERE s.id = ?",
				String.class, session.sessionId());
	}

}

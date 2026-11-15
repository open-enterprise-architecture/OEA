package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.logging.LogCapture;
import io.oea.testsupport.time.MutableClock;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec identity/access-tokens (REQ-176, REQ-155, US-142, US-149; design D3–D7 of
 * s12-client-credentials-token) through the API on an own database after the setup phase: the
 * token endpoint with its refusals and limits, bearer authentication, revocation and cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AccessTokensTests.ClockConfiguration.class)
class AccessTokensTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String TOKEN = "/api/v1/oauth2/token";

	private static final String ACCOUNTS = "/api/v1/admin/service-accounts";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) throws Exception {
		Path tokenFile = Files.createTempDirectory("oea-access-tokens").resolve("setup-token");
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", tokenFile::toString);
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
	private SystemAdminAccountRepository adminAccounts;

	@Autowired
	private MutableClock clock;

	@Autowired
	private AccessTokens tokens;

	@Autowired
	private AccessTokenCleanup cleanup;

	private TestPrincipals principals;

	private TestSession admin;

	/** Client ID and secret of a service account created by the test. */
	private record Client(String id, String secret) {
	}

	@BeforeEach
	void administrator() {
		this.principals = new TestPrincipals(this.sessions, this.adminAccounts, this.jdbc);
		this.jdbc.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'identity-binding') ON CONFLICT (id) DO NOTHING""");
		this.jdbc.update("DELETE FROM auth_throttle");
		this.admin = this.principals.personWithIdpRole("system-admin");
		recentAuthentication(this.admin);
	}

	// Token endpoint

	@Test
	void tokenIssuedAndStoredAsItsHash() throws Exception {
		Client client = client("issued", "integration-writer");
		try (LogCapture logs = LogCapture.attach()) {
			MvcTestResult result = token(client.id(), client.secret());
			assertThat(result).hasStatus(HttpStatus.OK);
			assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
			assertThat(result.getResponse().getHeader("Pragma")).isEqualTo("no-cache");
			JsonNode body = body(result);
			String token = body.path("access_token").asString();
			assertThat(token).startsWith(AccessTokens.TOKEN_PREFIX);
			assertThat(body.path("token_type").asString()).isEqualTo("Bearer");
			assertThat(body.path("expires_in").asInt()).isEqualTo(900);
			assertThat(body.has("refresh_token")).isFalse();
			String hash = HexFormat.of().formatHex(AccessTokens.hash(token));
			assertThat(count("SELECT count(*) FROM access_tokens WHERE encode(token_hash, 'hex') = '" + hash
					+ "' AND client_id = '" + client.id() + "' AND expires_at = issued_at + interval '900 seconds'")).isOne();
			assertThat(count("SELECT count(*) FROM machine_credentials WHERE client_id = '" + client.id()
					+ "' AND status = 'active' AND last_used_at IS NOT NULL")).isOne();
			assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'token.issued' AND agent_id = '"
					+ client.id() + "'")).isOne();
			assertThat(count("SELECT count(*) FROM audit.events WHERE strpos(details::text, '" + token
					+ "') > 0 OR strpos(details::text, '" + client.secret() + "') > 0")).isZero();
			assertThat(logs.text()).doesNotContain(token).doesNotContain(client.secret());
		}
	}

	@Test
	void otherGrantTypeAndMissingGrantType() {
		Client client = client("grant", "viewer");
		MvcTestResult password = this.mvc.post()
			.uri(TOKEN)
			.header("Authorization", basic(client.id(), client.secret()))
			.contentType("application/x-www-form-urlencoded")
			.content("grant_type=password")
			.exchange();
		assertThat(password).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(body(password).path("error").asString()).isEqualTo("unsupported_grant_type");
		MvcTestResult missing = this.mvc.post()
			.uri(TOKEN)
			.header("Authorization", basic(client.id(), client.secret()))
			.contentType("application/x-www-form-urlencoded")
			.content("scope=read")
			.exchange();
		assertThat(missing).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(body(missing).path("error").asString()).isEqualTo("invalid_request");
		assertThat(count("SELECT count(*) FROM access_tokens WHERE client_id = '" + client.id() + "'")).isZero();
	}

	@Test
	void credentialsOutsideTheHeaderAreRefused() {
		Client client = client("outside", "viewer");
		MvcTestResult inBody = this.mvc.post()
			.uri(TOKEN)
			.contentType("application/x-www-form-urlencoded")
			.content("grant_type=client_credentials&client_id=" + client.id() + "&client_secret=" + client.secret())
			.exchange();
		MvcTestResult inQuery = this.mvc.post()
			.uri(TOKEN + "?client_id=" + client.id() + "&client_secret=" + client.secret())
			.contentType("application/x-www-form-urlencoded")
			.content("grant_type=client_credentials")
			.exchange();
		for (MvcTestResult result : List.of(inBody, inQuery)) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
			assertThat(body(result).path("error").asString()).isEqualTo("invalid_client");
			assertThat(result.getResponse().getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"oea\"");
		}
		assertThat(count("SELECT count(*) FROM access_tokens WHERE client_id = '" + client.id() + "'")).isZero();
	}

	// Refused client authentication

	@Test
	void sameAnswerForEveryRefusal() throws Exception {
		Client wrong = client("wrong", "viewer");
		Client expired = client("expired", "viewer");
		this.clock.advance(Duration.ofSeconds(2));
		this.jdbc.update("UPDATE machine_credentials SET expires_at = ? WHERE client_id = ?::uuid AND status = 'active'",
				Timestamp.from(this.clock.instant()), expired.id());
		Client deactivated = client("deactivated", "viewer");
		assertThat(delete(deactivated.id())).hasStatus(HttpStatus.NO_CONTENT);
		this.clock.advance(Duration.ofSeconds(2));
		int denied = count("SELECT count(*) FROM audit.events WHERE event_type = 'token.denied'");
		List<MvcTestResult> results = List.of(token(UUID.randomUUID().toString(), "oea_cs_unknown"),
				token(wrong.id(), "oea_cs_wrong"), token(expired.id(), expired.secret()),
				token(deactivated.id(), deactivated.secret()));
		MvcTestResult first = results.getFirst();
		for (MvcTestResult result : results) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
			assertThat(result.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
			assertThat(result.getResponse().getHeader("WWW-Authenticate"))
				.isEqualTo(first.getResponse().getHeader("WWW-Authenticate"));
		}
		assertThat(body(first).path("error").asString()).isEqualTo("invalid_client");
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'token.denied'")).isEqualTo(denied + 4);
	}

	@ParameterizedTest
	@CsvSource({ "1, 200", "0, 401" })
	void secretExpiryBoundary(int secondsLeft, int status) {
		Client client = client("expiry-" + secondsLeft, "viewer");
		// the expiry must stay after the creation (machine_credentials_expiry)
		this.clock.advance(Duration.ofSeconds(2));
		Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
		this.jdbc.update("UPDATE machine_credentials SET expires_at = ? WHERE client_id = ?::uuid AND status = 'active'",
				Timestamp.from(now.plusSeconds(secondsLeft)), client.id());
		assertThat(token(client.id(), client.secret()).getResponse().getStatus()).isEqualTo(status);
	}

	@Test
	void oldSecretAfterRotation() {
		Client client = client("rotated", "viewer");
		String secret = body(rotate(client.id())).path("clientSecret").asString();
		assertThat(token(client.id(), client.secret())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(token(client.id(), secret)).hasStatus(HttpStatus.OK);
	}

	// Limits

	@Test
	void backoffPerClient() {
		Client client = client("backoff", "viewer");
		for (int n = 1; n <= 4; n++) {
			assertThat(token(client.id(), "oea_cs_wrong-" + n)).as("failure " + n).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		// the fifth failure is still checked; it starts the backoff
		assertThat(token(client.id(), "oea_cs_wrong-5")).hasStatus(HttpStatus.UNAUTHORIZED);
		MvcTestResult throttled = token(client.id(), client.secret());
		assertThat(throttled).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(throttled.getResponse().getHeader("Retry-After")).isNotBlank();
	}

	@Test
	void limitPerAddress() {
		Client client = client("address", "viewer");
		for (int n = 1; n <= 99; n++) {
			assertThat(token(UUID.randomUUID().toString(), "oea_cs_x", "203.0.113.7")).hasStatus(HttpStatus.UNAUTHORIZED);
		}
		// the 100th failure is checked; afterwards the address is throttled
		assertThat(token(UUID.randomUUID().toString(), "oea_cs_x", "203.0.113.7")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(token(client.id(), client.secret(), "203.0.113.7")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(token(client.id(), client.secret(), "203.0.113.8")).hasStatus(HttpStatus.OK);
	}

	@Test
	void concurrentChecksAreLimited() throws Exception {
		Client client = client("busy", "viewer");
		this.tokens.checks().acquire(AccessTokens.CONCURRENT_CHECKS);
		int held = AccessTokens.CONCURRENT_CHECKS;
		try {
			long started = System.nanoTime();
			assertThat(token(client.id(), client.secret())).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(AccessTokens.CHECK_WAIT);
			// a slot that becomes free within 2 seconds is taken
			CompletableFuture<MvcTestResult> waiting = CompletableFuture.supplyAsync(() -> token(client.id(), client.secret()));
			TimeUnit.MILLISECONDS.sleep(500);
			this.tokens.checks().release();
			held--;
			assertThat(waiting.get(5, TimeUnit.SECONDS)).hasStatus(HttpStatus.OK);
		}
		finally {
			this.tokens.checks().release(held);
		}
	}

	// Bearer authentication

	@Test
	void writeWithIntegrationWriterWithoutCsrf() {
		Client client = client("writer", "integration-writer");
		String token = accessToken(client);
		MvcTestResult me = this.mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + token).exchange();
		assertThat(me).hasStatus(HttpStatus.OK);
		assertThat(body(me).path("principal").path("principalType").asString()).isEqualTo("service-account");
		assertThat(body(me).has("csrfToken") && !body(me).path("csrfToken").isNull()).isFalse();
		assertThat(body(me).toString()).contains("entity:write");
		// a state-changing request without CSRF token passes the CSRF filter and reaches the authorization
		MvcTestResult change = this.mvc.post()
			.uri("/api/v1/me/password")
			.header("Authorization", "Bearer " + token)
			.contentType("application/json")
			.content("{\"currentPassword\":\"x\",\"newPassword\":\"y\"}")
			.exchange();
		assertThat(change).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(change).path("type").asString()).isEqualTo("urn:oea:problem:forbidden");
	}

	@ParameterizedTest
	@CsvSource({ "899, 200", "900, 401" })
	void tokenLifetimeBoundary(int secondsAgo, int status) {
		Client client = client("lifetime-" + secondsAgo, "viewer");
		String token = accessToken(client);
		// the token was issued secondsAgo, so it expires in 1 second or now
		Instant issued = this.clock.instant().truncatedTo(ChronoUnit.MICROS).minusSeconds(secondsAgo);
		this.jdbc.update("UPDATE access_tokens SET issued_at = ?, expires_at = ? WHERE client_id = ?::uuid",
				Timestamp.from(issued), Timestamp.from(issued.plusSeconds(900)), client.id());
		assertThat(this.mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + token).exchange()
			.getResponse()
			.getStatus()).isEqualTo(status);
	}

	@Test
	void unknownTokenTransportAndMixedCredentials() {
		MvcTestResult unknown = this.mvc.get()
			.uri("/api/v1/me")
			.header("Authorization", "Bearer " + AccessTokens.TOKEN_PREFIX + "never-issued")
			.exchange();
		assertThat(unknown).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(unknown.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");

		Client client = client("transport", "viewer");
		String token = accessToken(client);
		assertThat(this.mvc.get().uri("/api/v1/me?access_token=" + token).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get().uri("/api/v1/me").cookie(new Cookie("access_token", token)).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get()
			.uri("/api/v1/me")
			.header("Authorization", "Bearer " + token)
			.cookie(new Cookie(SessionCookie.NAME, this.admin.token()))
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void administrationIsRefused() {
		Client client = client("no-admin", "integration-writer");
		int denials = count("SELECT count(*) FROM audit.events WHERE event_type = 'authz.denied' AND agent_id = '"
				+ client.id() + "'");
		MvcTestResult list = this.mvc.get().uri(ACCOUNTS).header("Authorization", "Bearer " + accessToken(client)).exchange();
		assertThat(list).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'authz.denied' AND agent_id = '"
				+ client.id() + "'")).isEqualTo(denials + 1);
	}

	// Revocation and cleanup

	@Test
	void rotationAndDeactivationRevokeTokens() {
		Client rotated = client("revoke-rotation", "viewer");
		String first = accessToken(rotated);
		rotate(rotated.id());
		assertThat(this.mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + first).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'token.revoked' AND details ->> 'clientId' = '"
				+ rotated.id() + "' AND details ->> 'reason' = 'rotation'")).isOne();

		Client deactivated = client("revoke-deactivation", "viewer");
		String second = accessToken(deactivated);
		assertThat(delete(deactivated.id())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(this.mvc.get().uri("/api/v1/me").header("Authorization", "Bearer " + second).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(count("SELECT count(*) FROM access_tokens WHERE client_id = '" + deactivated.id()
				+ "' AND revocation_reason = 'deactivation'")).isOne();
	}

	@Test
	void cleanupBoundary() {
		Client client = client("cleanup", "viewer");
		accessToken(client);
		accessToken(client);
		List<UUID> ids = this.jdbc.queryForList("SELECT id FROM access_tokens WHERE client_id = ?::uuid ORDER BY id",
				UUID.class, client.id());
		Instant now = this.clock.instant();
		// expired exactly one day ago, and one day minus one minute ago
		setExpiry(ids.get(0), now.minus(Duration.ofDays(1)));
		setExpiry(ids.get(1), now.minus(Duration.ofDays(1)).plusSeconds(60));
		this.cleanup.deleteExpired();
		assertThat(count("SELECT count(*) FROM access_tokens WHERE id = '" + ids.get(0) + "'")).isZero();
		assertThat(count("SELECT count(*) FROM access_tokens WHERE id = '" + ids.get(1) + "'")).isOne();
	}

	private void setExpiry(UUID id, Instant expiry) {
		this.jdbc.update("UPDATE access_tokens SET issued_at = ?, expires_at = ? WHERE id = ?",
				Timestamp.from(expiry.minusSeconds(900)), Timestamp.from(expiry), id);
	}

	private Client client(String name, String role) {
		MvcTestResult created = this.mvc.post()
			.uri(ACCOUNTS)
			.cookie(new Cookie(SessionCookie.NAME, this.admin.token()))
			.header(CsrfOriginFilter.HEADER, this.admin.csrfToken())
			.contentType("application/json")
			.content("{\"name\":\"" + name + "-" + UUID.randomUUID().toString().substring(0, 8) + "\",\"roles\":[\"" + role
					+ "\"]}")
			.exchange();
		assertThat(created).hasStatus(HttpStatus.CREATED);
		JsonNode body = body(created);
		return new Client(body.path("id").asString(), body.path("clientSecret").asString());
	}

	private MvcTestResult rotate(String clientId) {
		recentAuthentication(this.admin);
		MvcTestResult rotated = this.mvc.post()
			.uri(ACCOUNTS + "/" + clientId + "/credentials/rotate")
			.cookie(new Cookie(SessionCookie.NAME, this.admin.token()))
			.header(CsrfOriginFilter.HEADER, this.admin.csrfToken())
			.exchange();
		assertThat(rotated).hasStatus(HttpStatus.OK);
		return rotated;
	}

	private MvcTestResult delete(String clientId) {
		recentAuthentication(this.admin);
		return this.mvc.delete()
			.uri(ACCOUNTS + "/" + clientId)
			.cookie(new Cookie(SessionCookie.NAME, this.admin.token()))
			.header(CsrfOriginFilter.HEADER, this.admin.csrfToken())
			.exchange();
	}

	private String accessToken(Client client) {
		MvcTestResult result = token(client.id(), client.secret());
		assertThat(result).hasStatus(HttpStatus.OK);
		return body(result).path("access_token").asString();
	}

	private MvcTestResult token(String clientId, String secret) {
		return token(clientId, secret, "192.0.2.10");
	}

	private MvcTestResult token(String clientId, String secret, String address) {
		return this.mvc.post()
			.uri(TOKEN)
			.with((request) -> {
				request.setRemoteAddr(address);
				return request;
			})
			.header("Authorization", basic(clientId, secret))
			.contentType("application/x-www-form-urlencoded")
			.content("grant_type=client_credentials")
			.exchange();
	}

	private void recentAuthentication(TestSession session) {
		this.jdbc.update("UPDATE sessions SET authenticated_at = ? WHERE id = ?", Timestamp.from(this.clock.instant()),
				session.sessionId());
	}

	private static String basic(String clientId, String secret) {
		return "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
	}

	private int count(String sql) {
		Integer count = this.jdbc.queryForObject(sql, Integer.class);
		return (count != null) ? count : 0;
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}

	}

}

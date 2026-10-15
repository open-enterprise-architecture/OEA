package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.bootstrapping.BootstrapCommand;
import io.oea.app.bootstrapping.Bootstrapping;
import io.oea.config.security.CsrfOriginFilter;
import io.oea.config.security.SessionCookie;
import io.oea.core.service.TotpAlgorithm;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import io.oea.testsupport.time.MutableClock;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec identity/access-administration on an own database. The first two tests run in the
 * setup phase; the second ends it, and the others reuse its second-factor-verified session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AccessAdministrationTests.ClockConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccessAdministrationTests {

	private static final PostgreSQLContainer postgres = OeaDatabases.freshMigrated();

	private static final Path tokenFile = tempFile();

	private static final String PASSWORD = "correct horse battery staple";

	private static final String ISSUER = "https://idp.example.org/realms/oea";

	private static final String GROUP = "5b0c8f3e-2a41-4f7d-9b7e-3c1d2e4f5a6b";

	private static Session verified;

	private static String providerId;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
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
	private Bootstrapping bootstrapping;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void bootstrapped() throws Exception {
		if (this.bootstrapping.setupRequired()) {
			this.bootstrapping.bootstrap(new BootstrapCommand(Files.readString(tokenFile).strip(), "admin", PASSWORD,
					"acme-corp"));
		}
	}

	@Test
	@Order(1)
	void firstMappingWithoutSecondFactor() {
		Session session = login();
		providerId = body(post("/api/v1/admin/auth/providers", session, provider(ISSUER, "s3cr3t-client-value")))
			.path("id")
			.asString();
		MvcTestResult mapping = post("/api/v1/admin/idp-group-mappings", session, mapping(GROUP, "viewer"));
		assertThat(mapping).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(body(mapping).path("type").asString()).isEqualTo("urn:oea:problem:second-factor-required");
		String personId = body(post("/api/v1/admin/persons", session,
				"{\"name\":\"Ada Lovelace\",\"email\":\"ada@example.org\"}")).path("id").asString();
		assertThat(post("/api/v1/admin/persons/" + personId + "/identities", session, identity("subject-ada")))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(count("SELECT count(*) FROM idp_group_mappings")).isZero();
		assertThat(count("SELECT count(*) FROM person_identities")).isZero();
		assertThat(count("SELECT count(*) FROM system_settings WHERE setup_phase_ended_at IS NULL")).isOne();
	}

	@Test
	@Order(2)
	void firstMappingWithSecondFactor() {
		Session session = login();
		JsonNode enrollment = body(post("/api/v1/me/second-factors/totp", session, "{}"));
		String code = TotpAlgorithm.code(enrollment.path("secret").asString(), this.clock.instant().getEpochSecond() / 30);
		assertThat(post("/api/v1/me/second-factors/totp/" + enrollment.path("credentialId").asString() + "/confirm",
				session, "{\"code\":\"" + code + "\"}"))
			.hasStatus(HttpStatus.OK);
		assertThat(post("/api/v1/admin/idp-group-mappings", session, mapping(GROUP, "viewer")))
			.hasStatus(HttpStatus.CREATED);
		assertThat(this.jdbc.queryForObject("SELECT setup_phase_ended_by_action FROM system_settings", String.class))
			.isEqualTo("idp-group-mapping");
		assertThat(this.jdbc.queryForObject(
				"SELECT details ->> 'triggeringAction' FROM audit.events WHERE event_type = 'system.setup-phase-ended'",
				String.class))
			.isEqualTo("idp-group-mapping");
		verified = session;
	}

	@Test
	void providerWithAnUnsafeIssuer() {
		for (String issuer : new String[] { "http://idp.example.org/realms/oea", "https://localhost:8443/realms/oea" }) {
			MvcTestResult result = post("/api/v1/admin/auth/providers", verified, provider(issuer, null));
			assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
			assertThat(body(result).path("detail").asString()).contains(issuer);
			assertThat(body(result).path("invalidFields").get(0).asString()).isEqualTo("issuerUrl");
		}
	}

	@Test
	void clientSecretNeverReturned() {
		String secret = "client-secret-" + UUID.randomUUID();
		MvcTestResult created = post("/api/v1/admin/auth/providers", verified,
				provider("https://login.example.org/tenant", secret));
		assertThat(created).hasStatus(HttpStatus.CREATED);
		assertThat(body(created).path("clientSecretSet").asBoolean()).isTrue();
		MvcTestResult read = get("/api/v1/admin/auth/providers/" + body(created).path("id").asString(), verified);
		assertThat(read).hasStatus(HttpStatus.OK);
		assertThat(body(read).path("clientSecretSet").asBoolean()).isTrue();
		assertThat(get("/api/v1/admin/auth/providers", verified).getResponse().getContentAsByteArray())
			.asString()
			.doesNotContain(secret);
		assertThat(body(created).toString() + body(read)).doesNotContain(secret);
		assertThat(count("SELECT count(*) FROM auth_providers WHERE strpos(client_secret_encrypted, '" + secret
				+ "') > 0")).isZero();
		assertThat(count("SELECT count(*) FROM audit.events WHERE event_type = 'auth-provider.changed' "
				+ "AND strpos(details::text, '" + secret + "') = 0")).isGreaterThanOrEqualTo(2);
	}

	@Test
	void providerChangeInAnOldSessionNeedsReauthentication() {
		this.clock.advance(Duration.ofMinutes(6));
		try {
			MvcTestResult result = post("/api/v1/admin/auth/providers", verified,
					provider("https://other.example.org", null));
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
			assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:reauthentication-required");
		}
		finally {
			this.clock.advance(Duration.ofMinutes(-6));
		}
	}

	@Test
	void bindingCreatedAndPairAlreadyBound() {
		JsonNode person = body(post("/api/v1/admin/persons", verified,
				"{\"name\":\"Grace Hopper\",\"email\":\"grace@example.org\"}"));
		assertThat(person.path("status").asString()).isEqualTo("invited");
		String subject = "subject-" + UUID.randomUUID();
		MvcTestResult bound = post("/api/v1/admin/persons/" + person.path("id").asInt() + "/identities", verified,
				identity(subject));
		assertThat(bound).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{"authProviderId": "%s", "issuer": "%s", "subject": "%s", "linkMethod": "admin-entered"}"""
			.formatted(providerId, ISSUER, subject));
		assertThat(auditCount("person.created", person.path("id").asString())).isOne();
		assertThat(auditCount("person.identity-bound", person.path("id").asString())).isOne();

		JsonNode second = body(post("/api/v1/admin/persons", verified,
				"{\"name\":\"Second Person\",\"email\":\"second@example.org\"}"));
		MvcTestResult again = post("/api/v1/admin/persons/" + second.path("id").asInt() + "/identities", verified,
				identity(subject));
		assertThat(again).hasStatus(HttpStatus.CONFLICT);
		assertThat(this.jdbc.queryForObject("SELECT person_id FROM person_identities WHERE subject = ?", Long.class,
				subject))
			.isEqualTo(person.path("id").asLong());

		assertThat(post("/api/v1/admin/persons", verified, "{\"name\":\"Duplicate\",\"email\":\"GRACE@example.org\"}"))
			.hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void mappingCreatedDisplayNameRejectedAndDeleted() {
		String group = UUID.randomUUID().toString();
		MvcTestResult created = post("/api/v1/admin/idp-group-mappings", verified, mapping(group, "viewer"));
		assertThat(created).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{"issuer": "%s", "groupIdentifier": "%s", "roleId": "viewer", "active": true,
				 "createdBy": {"principalType": "system-admin-account", "displayName": "admin"}}"""
			.formatted(ISSUER, group));
		String id = body(created).path("id").asString();
		assertThat(body(created).path("createdAt").asString()).isNotBlank();
		assertThat(auditCount("idp-mapping.created", id)).isOne();

		assertThat(post("/api/v1/admin/idp-group-mappings", verified, mapping("EA-Viewers", "viewer")))
			.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(post("/api/v1/admin/idp-group-mappings", verified, mapping(group, "viewer")))
			.hasStatus(HttpStatus.CONFLICT);

		assertThat(this.mvc.delete()
			.uri("/api/v1/admin/idp-group-mappings/" + id)
			.cookie(new Cookie(SessionCookie.NAME, verified.token()))
			.header(CsrfOriginFilter.HEADER, verified.csrfToken())
			.exchange()).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(get("/api/v1/admin/idp-group-mappings", verified).getResponse().getContentAsByteArray()).asString()
			.doesNotContain(id);
		assertThat(count("SELECT count(*) FROM idp_group_mappings WHERE deleted_at IS NOT NULL AND id = '" + id + "'"))
			.isOne();
		assertThat(auditCount("idp-mapping.deleted", id)).isOne();
	}

	record Session(String token, String csrfToken) {
	}

	private Session login() {
		MvcTestResult login = this.mvc.post()
			.uri("/api/v1/auth/local/login")
			.contentType("application/json")
			.content("{\"loginName\":\"admin\",\"password\":\"" + PASSWORD + "\"}")
			.exchange();
		assertThat(login).hasStatus(HttpStatus.CREATED);
		String cookie = login.getResponse().getHeader("Set-Cookie");
		return new Session(cookie.substring("__Host-OEA_SESSION=".length(), cookie.indexOf(';')),
				body(login).path("csrfToken").asString());
	}

	private static String provider(String issuer, String secret) {
		var node = JsonMapper.shared()
			.createObjectNode()
			.put("name", "Corporate IdP")
			.put("type", "oidc")
			.put("vendor", "generic-oidc")
			.put("issuerUrl", issuer)
			.put("clientId", "oea");
		if (secret != null) {
			node.put("clientSecret", secret);
		}
		return node.toString();
	}

	private static String mapping(String group, String role) {
		return "{\"authProviderId\":\"" + providerId + "\",\"groupIdentifier\":\"" + group + "\",\"roleId\":\"" + role
				+ "\"}";
	}

	private static String identity(String subject) {
		return "{\"authProviderId\":\"" + providerId + "\",\"subject\":\"" + subject + "\"}";
	}

	private MvcTestResult post(String uri, Session session, String body) {
		return this.mvc.post()
			.uri(uri)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body)
			.exchange();
	}

	private MvcTestResult get(String uri, Session session) {
		return this.mvc.get().uri(uri).cookie(new Cookie(SessionCookie.NAME, session.token())).exchange();
	}

	private int count(String sql) {
		return this.jdbc.queryForObject(sql, Integer.class);
	}

	private int auditCount(String eventType, String targetId) {
		return this.jdbc.queryForObject("SELECT count(*) FROM audit.events WHERE event_type = ? AND target_id = ?",
				Integer.class, eventType, targetId);
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Path tempFile() {
		try {
			return Files.createTempDirectory("oea-access").resolve("setup-token-" + UUID.randomUUID());
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

package io.oea.config.web;

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
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec platform/request-limits (REQ-186, REQ-184; design D1 of s2b-backend-hardening). The
 * operator lowered the default limit to 100 KB and the package limit to 64 KB; the entity
 * batch keeps its 10 MB default.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "oea.web.request-size.default=100KB", "oea.metamodel.import.max-size=64KB" })
@AutoConfigureMockMvc
class RequestSizeLimitTests {

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

	@LocalServerPort
	private int port;

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	private TestPrincipals principals;

	private TestSession architect;

	@BeforeEach
	void instance() {
		this.principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		this.architect = this.principals.personWithIdpRole("enterprise-architect");
		if (count("SELECT count(*) FROM metatypes") == 0) {
			TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
			assertThat(post("/api/v1/admin/metamodel/starter-packages/oea-starter-togaf-classic/import", new byte[0],
					metamodelAdmin)).hasStatus(HttpStatus.OK);
		}
	}

	// Requirement: Size limit of request bodies

	@Test
	void oversizedBatchWithDeclaredLength() {
		int before = count("SELECT count(*) FROM entities");
		MvcTestResult result = post(RequestSizeConfiguration.BATCH_PATH, new byte[10 * 1024 * 1024 + 1], this.architect);
		assertTooLarge(result, "request-too-large");
		assertThat(count("SELECT count(*) FROM entities")).isEqualTo(before);
	}

	@Test
	void oversizedBodyWithoutDeclaredLength() throws Exception {
		int before = count("SELECT count(*) FROM catalogs");
		// a JSON document that only becomes invalid after the limit, streamed without Content-Length
		String body = "{\"name\": \"Streamed\", \"description\": \"" + "x".repeat(200 * 1024) + "\"}";
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/api/v1/catalogs"))
			.header("Cookie", SessionCookie.NAME + "=" + this.architect.token())
			.header(CsrfOriginFilter.HEADER, this.architect.csrfToken())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers
				.ofInputStream(() -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))))
			.build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(413);
		assertThat(JsonMapper.shared().readTree(response.body()).path("type").asString())
			.isEqualTo("urn:oea:problem:request-too-large");
		assertThat(count("SELECT count(*) FROM catalogs")).isEqualTo(before);
	}

	@Test
	void bodyWithinTheLimit() {
		UUID component = this.jdbc.queryForObject("SELECT id FROM metatypes WHERE name = 'Application Component'",
				UUID.class);
		ObjectNode batch = JsonMapper.shared().createObjectNode();
		ArrayNode entities = batch.putArray("entities");
		String prefix = UUID.randomUUID().toString().substring(0, 8);
		for (int i = 0; i < 500; i++) {
			entities.addObject()
				.put("name", "Sized " + prefix + " " + i)
				.put("metatypeId", component.toString())
				.put("description", "d".repeat(1000));
		}
		byte[] body = batch.toString().getBytes(StandardCharsets.UTF_8);
		assertThat(body.length).isGreaterThan(100 * 1024).isLessThan(10 * 1024 * 1024);
		MvcTestResult result = post(RequestSizeConfiguration.BATCH_PATH, body, this.architect);
		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(body(result).path("summary").path("created").asInt()).isEqualTo(500);
	}

	// Requirement: Configurable limits

	@Test
	void limitConfiguredByTheOperator() {
		assertTooLarge(post("/api/v1/catalogs", new byte[200 * 1024], this.architect), "request-too-large");
	}

	@Test
	void oversizedPackage() {
		TestSession metamodelAdmin = this.principals.personWithIdpRole("metamodel-admin");
		assertTooLarge(post(RequestSizeConfiguration.IMPORT_PATH, new byte[65 * 1024], metamodelAdmin),
				"package-too-large");
	}

	// Requirement: Authentication before the size check

	@Test
	void oversizedBodyWithoutSession() {
		MvcTestResult result = this.mvc.post()
			.uri(RequestSizeConfiguration.BATCH_PATH)
			.contentType("application/json")
			.content(new byte[10 * 1024 * 1024 + 1])
			.exchange();
		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private static void assertTooLarge(MvcTestResult result, String problemType) {
		assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(body(result).path("type").asString()).isEqualTo("urn:oea:problem:" + problemType);
	}

	private MvcTestResult post(String path, byte[] body, TestSession session) {
		return this.mvc.post()
			.uri(path)
			.cookie(new Cookie(SessionCookie.NAME, session.token()))
			.header(CsrfOriginFilter.HEADER, session.csrfToken())
			.contentType("application/json")
			.content(body)
			.exchange();
	}

	private int count(String sql) {
		Integer count = this.jdbc.queryForObject(sql, Integer.class);
		return (count != null) ? count : 0;
	}

	private static JsonNode body(MvcTestResult result) {
		try {
			return JsonMapper.shared().readTree(result.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-request-size").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

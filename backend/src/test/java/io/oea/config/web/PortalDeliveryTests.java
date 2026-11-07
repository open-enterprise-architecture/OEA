package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.config.security.SecurityConfiguration;
import io.oea.config.security.SessionCookie;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
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

/**
 * Delivery of the web portal by the backend (s9-portal-catalogs D3, D4): platform/portal-delivery
 * with a portal directory holding {@code index.html}, {@code favicon.svg} and one hashed asset.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PortalDeliveryTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	private static final String INDEX = "<!doctype html><title>OEA portal</title>";

	private static final Path PORTAL = portal();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> PORTAL.getParent().resolve("setup-token").toString());
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
		registry.add("oea.portal.directory", PORTAL::toString);
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

	// Requirement: Backend serves the web portal

	@Test
	void theRootIsThePortal() throws Exception {
		// MockMvc does not dispatch the forward of /, so the root is requested from the server
		HttpResponse<String> root = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/")).build(),
					HttpResponse.BodyHandlers.ofString());
		assertThat(root.statusCode()).isEqualTo(200);
		assertThat(root.body()).isEqualTo(INDEX);
		assertThat(root.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
		assertThat(root.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-cache");
		assertThat(root.headers().firstValue("Content-Security-Policy").orElse(""))
			.isEqualTo(SecurityConfiguration.PORTAL_CONTENT_SECURITY_POLICY);
	}

	@Test
	void linkIntoThePortal() {
		assertIndex(this.mvc.get().uri("/portal/catalogs/0b6c3d5e-1c1f-4c55-9d1a-3f1c1e2a4b5c").exchange());
		assertIndex(this.mvc.get().uri("/catalogs").exchange());
	}

	@Test
	void unknownApiPathIsNoPortal() {
		MvcTestResult result = this.mvc.get().uri("/api/v1/unknown").exchange();
		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(content(result)).doesNotContain("OEA portal");
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		MvcTestResult authenticated = this.mvc.get()
			.uri("/api/v1/unknown")
			.cookie(new Cookie(SessionCookie.NAME, principals.systemAdmin().token()))
			.exchange();
		assertThat(authenticated).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(content(authenticated)).doesNotContain("OEA portal");
	}

	@Test
	void theApiStaysClosedWithoutASession() {
		assertThat(this.mvc.get().uri("/api/v1/catalogs").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.get().uri("/api/v1/portal/catalogs").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void writesOutsideTheApiNeedASession() {
		assertThat(this.mvc.post().uri("/portal").exchange().getResponse().getStatus())
			.isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
	}

	// Requirement: Caching of the portal files

	@Test
	void hashedAsset() {
		MvcTestResult result = this.mvc.get().uri("/assets/app-abc123.js").exchange();
		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(content(result)).isEqualTo("console.log('portal')");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("max-age=31536000, public, immutable");
	}

	@Test
	void missingAssetIsNotFound() {
		assertThat(this.mvc.get().uri("/assets/app-old.js").exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.get().uri("/missing.js").exchange()).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void otherFilesAreRevalidated() {
		MvcTestResult result = this.mvc.get().uri("/favicon.svg").exchange();
		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-cache");
	}

	// Requirement: Security headers of the web portal

	@Test
	void headersOfIndexHtml() {
		MvcTestResult result = this.mvc.get().uri("/portal").exchange();
		assertThat(result).headers()
			.hasValue("X-Content-Type-Options", "nosniff")
			.hasValue("Referrer-Policy", "no-referrer")
			.hasValue("X-Frame-Options", "DENY")
			.containsHeader("Strict-Transport-Security");
		String policy = result.getResponse().getHeader("Content-Security-Policy");
		assertThat(policy).isEqualTo(SecurityConfiguration.PORTAL_CONTENT_SECURITY_POLICY)
			.contains("default-src 'self'", "script-src 'self'", "connect-src 'self'", "object-src 'none'",
					"base-uri 'none'", "form-action 'self'", "frame-ancestors 'none'")
			.doesNotContain("unsafe-eval");
		String scripts = policy.substring(policy.indexOf("script-src"));
		assertThat(scripts.substring(0, scripts.indexOf(';'))).doesNotContain("unsafe-inline");
	}

	@Test
	void apiHeadersUnchanged() {
		TestPrincipals principals = new TestPrincipals(this.sessions, this.accounts, this.jdbc);
		MvcTestResult result = this.mvc.get()
			.uri("/api/v1/me")
			.cookie(new Cookie(SessionCookie.NAME, principals.systemAdmin().token()))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result.getResponse().getHeader("Content-Security-Policy"))
			.isEqualTo("default-src 'none'; frame-ancestors 'none'");
		assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
	}

	private static void assertIndex(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(content(result)).isEqualTo(INDEX);
		assertThat(result.getResponse().getContentType()).startsWith("text/html");
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-cache");
		assertThat(result.getResponse().getHeader("Content-Security-Policy"))
			.isEqualTo(SecurityConfiguration.PORTAL_CONTENT_SECURITY_POLICY);
	}

	private static String content(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Path portal() {
		try {
			Path portal = Files.createTempDirectory("oea-portal").resolve("portal");
			Files.createDirectories(portal.resolve("assets"));
			Files.writeString(portal.resolve("index.html"), INDEX);
			Files.writeString(portal.resolve("favicon.svg"), "<svg xmlns=\"http://www.w3.org/2000/svg\"/>");
			Files.writeString(portal.resolve("assets/app-abc123.js"), "console.log('portal')");
			return portal;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}

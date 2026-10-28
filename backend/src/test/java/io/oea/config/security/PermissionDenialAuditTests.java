package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.testsupport.auth.TestPrincipals;
import io.oea.testsupport.auth.TestPrincipals.TestSession;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Spec platform/authorization: Permission denials are audited (REQ-175 AC1, issue #75). */
@SpringBootTest
@AutoConfigureMockMvc
class PermissionDenialAuditTests {

	// own database: the IdP providers of the test principals must not reach the shared database
	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", PermissionDenialAuditTests::tokenFile);
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static String tokenFile() {
		try {
			return Files.createTempDirectory("oea-test").resolve("setup-token").toString();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private Sessions sessions;

	@Autowired
	private SystemAdminAccountRepository accounts;

	@Autowired
	private JdbcTemplate jdbc;

	private TestPrincipals principals() {
		return new TestPrincipals(this.sessions, this.accounts, this.jdbc);
	}

	private List<Map<String, Object>> denials(TestSession session) {
		return this.jdbc.queryForList("""
				SELECT e.actor_type, e.target_id, e.details ->> 'operation' AS operation, e.details ->> 'reason' AS reason,
				       host(e.source_ip) AS source_ip, e.request_id
				FROM audit.events e JOIN sessions s ON s.person_id = e.actor_person_id
				WHERE e.event_type = 'authz.denied' AND s.id = ? ORDER BY e.chain_seq""", session.sessionId());
	}

	@Test
	void viewerCallingAnAdministrativeOperationIsAudited() {
		TestSession viewer = principals().personWithIdpRole("viewer");
		assertThat(this.mvc.get()
			.uri("/api/v1/admin/idp-group-mappings")
			.cookie(new Cookie(SessionCookie.NAME, viewer.token()))
			.header("X-Request-Id", "req-denial-1")).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:forbidden");

		assertThat(denials(viewer)).singleElement()
			.satisfies((row) -> assertThat(row).containsEntry("actor_type", "person")
				.containsEntry("operation", "listIdpGroupMappings")
				.containsEntry("reason", "missing-permission")
				.containsEntry("source_ip", "127.0.0.1")
				.containsEntry("request_id", "req-denial-1"));
	}

	@Test
	void denialWithAnIdInThePathCarriesTheTarget() {
		TestSession viewer = principals().personWithIdpRole("viewer");
		String id = UUID.randomUUID().toString();
		assertThat(this.mvc.get()
			.uri("/api/v1/admin/auth/providers/" + id)
			.cookie(new Cookie(SessionCookie.NAME, viewer.token()))).hasStatus(HttpStatus.FORBIDDEN);

		assertThat(denials(viewer)).singleElement()
			.satisfies((row) -> assertThat(row).containsEntry("operation", "getAuthProvider")
				.containsEntry("target_id", id));
	}

	@Test
	void denialInsideAContractConditionIsAudited() {
		TestSession viewer = principals().personWithIdpRole("viewer");
		assertThat(this.mvc.post()
			.uri("/api/v1/catalogs")
			.cookie(new Cookie(SessionCookie.NAME, viewer.token()))
			.header(CsrfOriginFilter.HEADER, viewer.csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Shared", "primaryMetatypeId": "%s", "scope": "instance"}""".formatted(UUID.randomUUID())))
			.hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.type")
			.isEqualTo("urn:oea:problem:forbidden");

		assertThat(denials(viewer)).singleElement()
			.satisfies((row) -> assertThat(row).containsEntry("operation", "createCatalog")
				.containsEntry("reason", "condition"));
	}

	@Test
	void permittedRequestIsNotAudited() {
		TestSession viewer = principals().personWithIdpRole("viewer");
		assertThat(this.mvc.get()
			.uri("/api/v1/catalogs")
			.cookie(new Cookie(SessionCookie.NAME, viewer.token()))).hasStatus(HttpStatus.OK);
		assertThat(denials(viewer)).isEmpty();
	}

}

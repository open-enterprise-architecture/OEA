package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.app.login.InvalidCredentialsException;
import io.oea.app.login.LocalLogin;
import io.oea.app.security.Sessions;
import io.oea.core.repository.SystemAdminAccountRepository;
import io.oea.core.service.PasswordHasher;
import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
import io.oea.testsupport.containers.OeaKeycloak;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec identity/access-administration "Deactivation of the local system admin account" and
 * "The last path to system administration is kept" (US-019, ADR-032 SoD-7; design D3, D7 of
 * us-019-admin-safeguards). One flow, because the steps build on each other.
 */
class LocalAdminAccountTests {

	private static final String DEACTIVATE = "/api/v1/admin/system-admin-account/deactivate";

	private static final String PASSWORD = "local-admin-password-1";

	private static OidcTestInstance instance;

	private static UUID adminMapping;

	@BeforeAll
	static void start() throws Exception {
		instance = OidcTestInstance.start();
		for (String user : List.of("dev-viewer", "dev-system-admin")) {
			instance.person(user, "active");
		}
		adminMapping = instance.mapping("oea-system-admins", "system-admin");
		instance.mapping("oea-viewers", "viewer");
		// the fixture mappings have no creator; the listing shows one
		instance.update("UPDATE idp_group_mappings SET created_by_person_id = (SELECT min(id) FROM persons)");
		instance.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'idp-group-mapping')""");
		String hash = instance.application().getBean(PasswordHasher.class).hash(PASSWORD);
		instance.update("""
				WITH account AS (INSERT INTO system_admin_accounts (login_name, status) VALUES ('admin', 'active')
				                 RETURNING id),
				     credential AS (INSERT INTO local_credentials (system_admin_account_id, type)
				                    SELECT id, 'password' FROM account RETURNING id)
				INSERT INTO password_credentials (credential_id, password_hash) SELECT id, '%s' FROM credential"""
			.formatted(hash));
	}

	@AfterAll
	static void stop() {
		instance.close();
	}

	@Test
	void deactivationKeepsTheLastPathAndEndsTheLocalLogin() throws Exception {
		Sessions.NewSession local = localSession();

		// a viewer lacks auth-config:manage
		ApplicationLogin viewer = instance.login("dev-viewer");
		assertThat(post(DEACTIVATE, viewer.sessionToken(), csrf(viewer)).statusCode()).isEqualTo(403);
		assertThat(status()).isEqualTo("active");

		// the mapping to system-admin has never matched: the local account is the last path
		HttpResponse<String> last = post(DEACTIVATE, local.token(), local.csrfToken());
		assertThat(last.statusCode()).isEqualTo(409);
		assertThat(JsonMapper.shared().readTree(last.body()).path("type").asString())
			.isEqualTo("urn:oea:problem:last-admin-path");
		assertThat(status()).isEqualTo("active");
		assertThat(instance.audit("system-admin-account.deactivated", "last-admin-path")).isEqualTo(1);
		assertThat(instance.get("/api/v1/me", local.token()).statusCode()).isEqualTo(200);

		// a member of the admin group logs in with a second factor: the mapping is a usable path
		ApplicationLogin remote = instance.login("dev-system-admin");
		assertThat(instance.single("SELECT last_matched_at IS NOT NULL FROM idp_group_mappings WHERE id = '"
				+ adminMapping + "'")).isEqualTo("t");
		HttpResponse<String> state = instance.get("/api/v1/admin/system-admin-account", remote.sessionToken());
		assertThat(state.statusCode()).isEqualTo(200);
		assertThat(JsonMapper.shared().readTree(state.body()).path("status").asString()).isEqualTo("active");

		HttpResponse<String> deactivated = post(DEACTIVATE, remote.sessionToken(), csrf(remote));
		assertThat(deactivated.statusCode()).isEqualTo(200);
		JsonNode body = JsonMapper.shared().readTree(deactivated.body());
		assertThat(body.path("status").asString()).isEqualTo("deactivated");
		assertThat(body.path("deactivatedAt").isString()).isTrue();
		assertThat(instance.single("SELECT deactivated_by_person_id IS NOT NULL FROM system_admin_accounts"))
			.isEqualTo("t");
		assertThat(instance.audit("system-admin-account.deactivated", "sessionsRevoked")).isEqualTo(1);

		// sessions revoked, password login refused like a wrong password, remote route unaffected
		assertThat(instance.get("/api/v1/me", local.token()).statusCode()).isEqualTo(401);
		LocalLogin login = instance.application().getBean(LocalLogin.class);
		assertThatExceptionOfType(InvalidCredentialsException.class)
			.isThrownBy(() -> login.login("admin", PASSWORD, new LocalLogin.Client("192.0.2.1", "test")));
		assertThat(instance.get("/api/v1/admin/idp-group-mappings", instance.login("dev-system-admin").sessionToken())
			.statusCode()).isEqualTo(200);

		// repeated deactivation: unchanged, no new event
		assertThat(post(DEACTIVATE, remote.sessionToken(), csrf(remote)).statusCode()).isEqualTo(200);
		assertThat(instance.audit("system-admin-account.deactivated", "sessionsRevoked")).isEqualTo(1);
	}

	/** Design D3: only active, approved, live mappings of an active provider that have matched count. */
	@Test
	void onlyMatchedLiveMappingsAreUsablePaths() throws Exception {
		AdministrationPaths paths = instance.application().getBean(AdministrationPaths.class);
		UUID inactiveProvider = instance.provider("Inactive realm", "http://localhost:8180/realms/inactive", false);
		String[] disqualified = {
				"active = false", "approval_status = 'pending'", "deleted_at = now()", "last_matched_at = NULL",
				"auth_provider_id = '" + inactiveProvider + "'" };
		for (String disqualifier : disqualified) {
			UUID mapping = instance.mapping(instance.providerId(), OeaKeycloak.issuer(),
					UUID.randomUUID().toString(), "system-admin");
			instance.update("UPDATE idp_group_mappings SET last_matched_at = now() WHERE id = '" + mapping + "'");
			assertThat(paths.isUsableMapping(mapping)).as("usable before: " + disqualifier).isTrue();
			instance.update("UPDATE idp_group_mappings SET " + disqualifier + " WHERE id = '" + mapping + "'");
			assertThat(paths.isUsableMapping(mapping)).as(disqualifier).isFalse();
			instance.update("UPDATE idp_group_mappings SET active = false WHERE id = '" + mapping + "'");
		}
		UUID viewer = instance.mapping(instance.providerId(), OeaKeycloak.issuer(),
				UUID.randomUUID().toString(), "viewer");
		instance.update("UPDATE idp_group_mappings SET last_matched_at = now() WHERE id = '" + viewer + "'");
		assertThat(paths.isUsableMapping(viewer)).as("other role").isFalse();
	}

	private static Sessions.NewSession localSession() {
		var account = instance.application().getBean(SystemAdminAccountRepository.class).findByLoginName("admin")
			.orElseThrow();
		return instance.application().getBean(Sessions.class).createForSystemAdmin(account, true, "192.0.2.1", "test");
	}

	private static String status() throws Exception {
		return instance.single("SELECT status FROM system_admin_accounts WHERE login_name = 'admin'");
	}

	private static String csrf(ApplicationLogin login) throws Exception {
		return JsonMapper.shared().readTree(instance.get("/api/v1/me", login.sessionToken()).body())
			.path("csrfToken").asString();
	}

	private static HttpResponse<String> post(String path, String token, String csrf) throws Exception {
		return instance.send(HttpRequest.newBuilder(URI.create(instance.url() + path))
			.header("Cookie", "__Host-OEA_SESSION=" + token)
			.header("X-XSRF-TOKEN", csrf)
			.POST(HttpRequest.BodyPublishers.noBody()));
	}

}

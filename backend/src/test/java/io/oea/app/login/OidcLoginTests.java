package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec identity/oidc-login through the application's flow and the development realm (ADR-036 §5). */
class OidcLoginTests {

	private static OidcTestInstance instance;

	@BeforeAll
	static void start() throws Exception {
		instance = OidcTestInstance.start();
		instance.person("dev-viewer", "active");
		instance.person("dev-architect", "invited");
		instance.person("dev-auditor", "inactive");
		instance.person("dev-analyst", "offboarded");
	}

	@AfterAll
	static void stop() {
		instance.close();
	}

	@Test
	void successfulLoginAndExistingSession() throws Exception {
		int logins = instance.audit("auth.login", "\"oidc\"");
		ApplicationLogin login = instance.login("dev-viewer");
		assertThat(login.status()).isEqualTo(302);
		assertThat(login.location()).isEqualTo(OidcTestInstance.PUBLIC_URL + "/me");
		assertThat(login.sessionToken()).isNotBlank();
		assertThat(instance.audit("auth.login", "\"oidc\"")).isEqualTo(logins + 1);

		// US-001 AC2: the session is used without another redirect to the IdP
		HttpResponse<String> me = instance.get("/api/v1/me", login.sessionToken());
		assertThat(me.statusCode()).isEqualTo(200);
		JsonNode body = JsonMapper.shared().readTree(me.body());
		assertThat(body.path("principal").path("principalType").asString()).isEqualTo("person");
		assertThat(body.path("principal").path("displayName").asString()).isEqualTo("dev-viewer");
		assertThat(instance.single("SELECT count(*)::text FROM oidc_login_states")).isEqualTo("0");
		assertThat(instance.single("SELECT idp_refresh_token_encrypted IS NOT NULL FROM sessions "
				+ "WHERE auth_method = 'oidc' ORDER BY created_at DESC LIMIT 1")).isEqualTo("t");
	}

	@Test
	void loginWithoutPkceCreatesNoSession() throws Exception {
		int failures = instance.audit("auth.login-failed", "provider-error");
		ApplicationLogin login = instance.login("dev-viewer", (request) -> UriComponentsBuilder.fromUri(request)
			.replaceQueryParam("code_challenge")
			.replaceQueryParam("code_challenge_method")
			.build(true)
			.toUri());
		assertThat(login.location()).isEqualTo(OidcTestInstance.PUBLIC_URL + "/login?error=login-failed");
		assertThat(login.sessionToken()).isNull();
		assertThat(instance.audit("auth.login-failed", "provider-error")).isEqualTo(failures + 1);
	}

	@Test
	void callbackWithoutTheLoginCookieIsRefused() throws Exception {
		HttpResponse<String> callback = instance.get("/api/v1/auth/oidc/callback?code=abc&state=xyz", null);
		assertThat(callback.statusCode()).isEqualTo(302);
		assertThat(callback.headers().firstValue("Location"))
			.hasValue(OidcTestInstance.PUBLIC_URL + "/login?error=login-failed");
	}

	@Test
	void onlyEnabledProvidersAreOffered() throws Exception {
		UUID disabled = instance.provider("Disabled IdP", "http://localhost:8180/realms/disabled", false);
		JsonNode providers = JsonMapper.shared().readTree(instance.get("/api/v1/auth/oidc/providers", null).body());
		assertThat(providers.valueStream().map((provider) -> provider.path("id").asString()))
			.contains(instance.providerId().toString())
			.doesNotContain(disabled.toString());
		assertThat(providers).allSatisfy(
				(provider) -> assertThat(provider.propertyNames()).containsExactlyInAnyOrder("id", "name"));
	}

	@Test
	void firstLoginOfAnInvitedPerson() throws Exception {
		ApplicationLogin login = instance.login("dev-architect");
		assertThat(login.sessionToken()).isNotBlank();
		assertThat(instance.single("SELECT status FROM persons WHERE name = 'dev-architect'")).isEqualTo("active");
		assertThat(instance.single("""
				SELECT i.last_login_at IS NOT NULL FROM person_identities i JOIN persons p ON p.id = i.person_id
				WHERE p.name = 'dev-architect'""")).isEqualTo("t");
		assertThat(instance.audit("person.activated", "first-login")).isEqualTo(1);
	}

	@Test
	void matchingEmailWithADifferentSubjectGetsNoSession() throws Exception {
		// dev-email-twin carries the e-mail address of dev-architect, but its own sub (REQ-166 AC1)
		ApplicationLogin login = instance.login("dev-email-twin");
		assertThat(login.sessionToken()).isNull();
		assertThat(login.location()).isEqualTo(OidcTestInstance.PUBLIC_URL + "/login?error=login-failed");
		assertThat(instance.audit("auth.login-failed", "identity-unknown")).isPositive();
	}

	@Test
	void unknownAndInactiveIdentitiesGetTheSameFailure() throws Exception {
		ApplicationLogin inactive = instance.login("dev-auditor");
		ApplicationLogin offboarded = instance.login("dev-analyst");
		ApplicationLogin unknown = instance.login("dev-no-role");
		assertThat(inactive.location()).isEqualTo(unknown.location()).isEqualTo(offboarded.location())
			.isEqualTo(OidcTestInstance.PUBLIC_URL + "/login?error=login-failed");
		assertThat(inactive.sessionToken()).isNull();
		assertThat(offboarded.sessionToken()).isNull();
		assertThat(instance.audit("auth.login-failed", "person-not-active")).isEqualTo(2);
		assertThat(instance.audit("auth.login-failed", "identity-unknown")).isPositive();
	}

	@Test
	void unreachableProviderShowsTheTechnicalMessage() throws Exception {
		UUID unreachable = instance.provider("Unreachable", "http://127.0.0.1:9/realms/none", true);
		HttpResponse<String> start = instance.get("/api/v1/auth/oidc/" + unreachable + "/start", null);
		assertThat(start.statusCode()).isEqualTo(302);
		assertThat(start.headers().firstValue("Location"))
			.hasValue(OidcTestInstance.PUBLIC_URL + "/login?error=provider-unavailable");
		assertThat(URI.create(start.headers().firstValue("Location").orElseThrow()).getHost()).isEqualTo("localhost");
	}

}

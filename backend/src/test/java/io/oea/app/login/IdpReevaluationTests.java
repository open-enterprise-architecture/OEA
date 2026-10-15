package io.oea.app.login;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.auth.KeycloakAdmin;
import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
import io.oea.testsupport.containers.OeaKeycloak;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec identity/idp-roles (re-evaluation within 15 minutes, deleted mappings) and
 * identity/sessions (IdP refresh token expired or rejected). The 15 minutes are shortened by
 * moving the time of the last evaluation back; IdP-side changes go through the Admin API.
 */
class IdpReevaluationTests {

	private static OidcTestInstance instance;

	private static UUID auditorMapping;

	@BeforeAll
	static void start() throws Exception {
		instance = OidcTestInstance.start();
		for (String user : List.of("dev-portal-reader", "dev-analyst", "dev-auditor", "dev-architect", "dev-viewer")) {
			instance.person(user, "active");
		}
		instance.mapping("oea-portal-readers", "portal-reader");
		instance.mapping("oea-analysts", "analyst");
		instance.mapping("oea-enterprise-architects", "enterprise-architect");
		instance.mapping("oea-viewers", "viewer");
		auditorMapping = instance.mapping("oea-auditors", "auditor");
	}

	@AfterAll
	static void stop() {
		instance.close();
	}

	@Test
	void successfulReevaluationRotatesTheRefreshToken() throws Exception {
		ApplicationLogin login = instance.login("dev-viewer");
		String before = refreshToken(login);
		evaluationDue(login);
		assertThat(roles(login)).containsExactly("viewer");
		assertThat(refreshToken(login)).isNotEqualTo(before);
		assertThat(instance.single(sessionColumn(login, "idp_claims_evaluated_at > now() - interval '1 minute'")))
			.isEqualTo("true");
	}

	@Test
	void removalFromTheGroupInTheIdp() throws Exception {
		ApplicationLogin login = instance.login("dev-portal-reader");
		assertThat(roles(login)).containsExactly("portal-reader");
		KeycloakAdmin.removeFromGroup("dev-portal-reader", "oea-portal-readers");
		try {
			// still within 15 minutes: the derived role applies
			assertThat(roles(login)).containsExactly("portal-reader");
			evaluationDue(login);
			assertThat(roles(login)).isEmpty();
			assertThat(instance.audit("auth.idp-roles-derived", "\"refresh\"", "\"roles\": []")).isPositive();
		}
		finally {
			KeycloakAdmin.addToGroup("dev-portal-reader", "oea-portal-readers");
		}
	}

	@Test
	void idpUnreachableAtReevaluation() throws Exception {
		ApplicationLogin login = instance.login("dev-analyst");
		assertThat(roles(login)).containsExactly("analyst");
		instance.update("UPDATE auth_providers SET issuer = 'http://127.0.0.1:9/realms/oea-dev' WHERE id = '"
				+ instance.providerId() + "'");
		try {
			evaluationDue(login);
			HttpResponse<String> me = instance.get("/api/v1/me", login.sessionToken());
			assertThat(me.statusCode()).isEqualTo(200);
			assertThat(JsonMapper.shared().readTree(me.body()).path("roles")).isEmpty();
			assertThat(instance.audit("auth.idp-groups-unusable", "idp-unreachable")).isPositive();
		}
		finally {
			instance.update("UPDATE auth_providers SET issuer = '" + OeaKeycloak.issuer() + "' WHERE id = '"
					+ instance.providerId() + "'");
		}
	}

	@Test
	void mappingDeletedInOea() throws Exception {
		ApplicationLogin login = instance.login("dev-auditor");
		assertThat(roles(login)).containsExactly("auditor");
		instance.update("UPDATE idp_group_mappings SET deleted_at = now() WHERE id = '" + auditorMapping + "'");
		instance.clock().advance(Duration.ofSeconds(61));
		assertThat(roles(login)).isEmpty();
	}

	@Test
	void refreshRejectedByTheIdpEndsTheSession() throws Exception {
		ApplicationLogin login = instance.login("dev-architect");
		assertThat(roles(login)).containsExactly("enterprise-architect");
		KeycloakAdmin.logout("dev-architect");
		evaluationDue(login);
		assertThat(instance.get("/api/v1/me", login.sessionToken()).statusCode()).isEqualTo(401);
		assertThat(instance.single(sessionColumn(login, "revoked_reason"))).isEqualTo("idp-refresh-failed");
		assertThat(instance.single(sessionColumn(login, "idp_refresh_token_encrypted IS NULL"))).isEqualTo("true");
		assertThat(instance.audit("session.revoked", "idp-refresh-failed")).isPositive();
	}

	@Test
	void expiredIdpRefreshTokenEndsTheSession() throws Exception {
		ApplicationLogin login = instance.login("dev-viewer");
		instance.update("UPDATE sessions SET idp_token_expires_at = now() - interval '1 minute' WHERE "
				+ sessionWhere(login));
		assertThat(instance.get("/api/v1/me", login.sessionToken()).statusCode()).isEqualTo(401);
		assertThat(instance.single(sessionColumn(login, "revoked_reason"))).isEqualTo("idp-refresh-failed");
	}

	private static List<String> roles(ApplicationLogin login) throws Exception {
		HttpResponse<String> me = instance.get("/api/v1/me", login.sessionToken());
		assertThat(me.statusCode()).isEqualTo(200);
		JsonNode body = JsonMapper.shared().readTree(me.body());
		return body.path("roles").valueStream().map((role) -> role.path("role").path("id").asString()).toList();
	}

	/** The last evaluation lies 16 minutes back, so the next request re-evaluates (Q16). */
	private static void evaluationDue(ApplicationLogin login) throws Exception {
		instance.update("UPDATE sessions SET idp_claims_evaluated_at = idp_claims_evaluated_at - interval '16 minutes' "
				+ "WHERE " + sessionWhere(login));
	}

	private static String refreshToken(ApplicationLogin login) throws Exception {
		return instance.single(sessionColumn(login, "idp_refresh_token_encrypted"));
	}

	private static String sessionColumn(ApplicationLogin login, String expression) {
		return "SELECT (" + expression + ")::text FROM sessions WHERE " + sessionWhere(login);
	}

	private static String sessionWhere(ApplicationLogin login) {
		return "token_hash = sha256(convert_to('" + login.sessionToken() + "', 'UTF8'))";
	}

}

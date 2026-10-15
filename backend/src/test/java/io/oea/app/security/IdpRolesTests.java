package io.oea.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.auth.DevRealm;
import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Spec identity/idp-roles: derivation at login, fail-closed cases and SoD-8 (ADR-032 §7). */
class IdpRolesTests {

	private static OidcTestInstance instance;

	private static UUID viewerMapping;

	@BeforeAll
	static void start() throws Exception {
		instance = OidcTestInstance.start();
		for (String user : List.of("dev-viewer", "dev-no-role", "dev-architect", "dev-admin-no-mfa",
				"dev-system-admin", "dev-security-admin")) {
			instance.person(user, "active");
		}
		viewerMapping = instance.mapping("oea-viewers", "viewer");
		instance.mapping("oea-system-admins", "system-admin");
		instance.mapping("oea-security-admins", "security-admin");
		// mappings of persons need an ended setup phase (ADR-042)
		instance.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'idp-group-mapping')""");
		// the architects' group is mapped only for another issuer
		UUID other = instance.provider("Other realm", "http://localhost:8180/realms/other", true);
		instance.mapping(other, "http://localhost:8180/realms/other", DevRealm.groupId("oea-enterprise-architects"),
				"enterprise-architect");
	}

	@AfterAll
	static void stop() {
		instance.close();
	}

	@Test
	void roleDerivedAtLogin() throws Exception {
		ApplicationLogin login = instance.login("dev-viewer");
		JsonNode me = me(login);
		assertThat(me.path("roles")).hasSize(1);
		JsonNode role = me.path("roles").get(0);
		assertThat(role.path("role").path("id").asString()).isEqualTo("viewer");
		assertThat(role.path("source").asString()).isEqualTo("idp");
		assertThat(role.path("mappingId").asString()).isEqualTo(viewerMapping.toString());
		assertThat(role.path("effective").asBoolean()).isTrue();
		assertThat(me.path("permissions").valueStream().map((p) -> p.path("permission").asString()))
			.contains("entity:read");
		assertThat(instance.single("SELECT count(*)::text FROM session_idp_roles WHERE role_id = 'viewer'"))
			.isEqualTo("1");
		assertThat(instance.audit("auth.idp-roles-derived", "\"viewer\"", "\"login\"")).isPositive();
	}

	@Test
	void personWithoutAnyRole() throws Exception {
		ApplicationLogin login = instance.login("dev-no-role");
		JsonNode me = me(login);
		assertThat(me.path("roles")).isEmpty();
		assertThat(me.path("permissions")).isEmpty();
		assertThat(instance.get("/api/v1/admin/permissions", login.sessionToken()).statusCode()).isEqualTo(403);
		// Keycloak omits the claim for users without groups: nothing is derived (fail closed)
		assertThat(instance.audit("auth.idp-groups-unusable", "\"missing\"")).isPositive();
	}

	@Test
	void sameGroupIdentifierFromAnotherIssuerDerivesNothing() throws Exception {
		JsonNode me = me(instance.login("dev-architect"));
		assertThat(me.path("roles")).isEmpty();
	}

	@Test
	void groupsClaimClassification() {
		assertThat(IdpRoles.GroupsClaim.of(Map.of("groups", List.of("a", "b"))))
			.isEqualTo(new IdpRoles.GroupsClaim("present", List.of("a", "b")));
		// Entra ID overage indicators instead of the groups claim
		assertThat(IdpRoles.GroupsClaim.of(Map.of("_claim_names", Map.of("groups", "src1"))).status())
			.isEqualTo("truncated");
		assertThat(IdpRoles.GroupsClaim.of(Map.of("hasgroups", true)).status()).isEqualTo("truncated");
		assertThat(IdpRoles.GroupsClaim.of(Map.of("sub", "x")).status()).isEqualTo("missing");
	}

	@Test
	void privilegedRoleWithoutSecondFactorIsNotEffective() throws Exception {
		ApplicationLogin login = instance.login("dev-admin-no-mfa");
		JsonNode me = me(login);
		assertThat(me.path("mfaSatisfied").asBoolean()).isFalse();
		JsonNode role = me.path("roles").get(0);
		assertThat(role.path("role").path("id").asString()).isEqualTo("system-admin");
		assertThat(role.path("effective").asBoolean()).isFalse();
		assertThat(role.path("notEffectiveReason").asString()).isEqualTo("mfa-required");
		assertThat(me.path("permissions")).isEmpty();
		assertThat(instance.get("/api/v1/admin/permissions", login.sessionToken()).statusCode()).isEqualTo(403);
	}

	@Test
	void privilegedRoleWithSecondFactorIsEffective() throws Exception {
		ApplicationLogin login = instance.login("dev-system-admin");
		JsonNode me = me(login);
		assertThat(me.path("mfaSatisfied").asBoolean()).isTrue();
		assertThat(me.path("roles").get(0).path("effective").asBoolean()).isTrue();
		assertThat(instance.get("/api/v1/admin/permissions", login.sessionToken()).statusCode()).isEqualTo(200);
	}

	@Test
	void ownGroupIsRejected() throws Exception {
		ApplicationLogin login = instance.login("dev-security-admin");
		JsonNode me = me(login);
		assertThat(me.path("roles").get(0).path("effective").asBoolean()).isTrue();
		String csrf = me.path("csrfToken").asString();
		int mappings = Integer.parseInt(instance.single("SELECT count(*)::text FROM idp_group_mappings"));

		HttpResponse<String> own = createMapping(login, csrf, DevRealm.groupId("oea-security-admins"));
		assertThat(own.statusCode()).isEqualTo(403);
		assertThat(JsonMapper.shared().readTree(own.body()).path("reason").asString())
			.isEqualTo("self-administration");
		assertThat(instance.single("SELECT count(*)::text FROM idp_group_mappings")).isEqualTo(String.valueOf(mappings));
		assertThat(instance.audit("authz.denied", "self-administration")).isPositive();

		assertThat(createMapping(login, csrf, DevRealm.groupId("oea-auditors")).statusCode()).isEqualTo(201);
	}

	private static HttpResponse<String> createMapping(ApplicationLogin login, String csrf, String group)
			throws Exception {
		String body = JsonMapper.shared()
			.createObjectNode()
			.put("authProviderId", instance.providerId().toString())
			.put("groupIdentifier", group)
			.put("roleId", "viewer")
			.toString();
		return instance.send(HttpRequest.newBuilder(URI.create(instance.url() + "/api/v1/admin/idp-group-mappings"))
			.header("Cookie", "__Host-OEA_SESSION=" + login.sessionToken())
			.header("X-XSRF-TOKEN", csrf)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	private static JsonNode me(ApplicationLogin login) throws Exception {
		assertThat(login.sessionToken()).as("session after login").isNotBlank();
		HttpResponse<String> response = instance.get("/api/v1/me", login.sessionToken());
		assertThat(response.statusCode()).isEqualTo(200);
		return JsonMapper.shared().readTree(response.body());
	}

}

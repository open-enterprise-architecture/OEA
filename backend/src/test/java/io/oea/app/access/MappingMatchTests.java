package io.oea.app.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.auth.DevRealm;
import io.oea.testsupport.auth.OidcLogin.ApplicationLogin;
import io.oea.testsupport.auth.OidcTestInstance;
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
 * Specs identity/idp-roles "Match of a mapping is recorded" and identity/access-administration
 * "Last match of IdP group mappings", "Warning for a system admin mapping without evidence of
 * members" (US-018; design D2, D4 of us-019-admin-safeguards).
 */
class MappingMatchTests {

	private static OidcTestInstance instance;

	private static UUID adminMapping;

	private static UUID architectMapping;

	@BeforeAll
	static void start() throws Exception {
		instance = OidcTestInstance.start();
		for (String user : List.of("dev-viewer", "dev-admin-no-mfa", "dev-system-admin")) {
			instance.person(user, "active");
		}
		adminMapping = instance.mapping("oea-system-admins", "system-admin");
		// no member of the architects' group logs in during these tests
		architectMapping = instance.mapping("oea-enterprise-architects", "enterprise-architect");
		// the fixture mappings have no creator; the listing shows one
		instance.update("UPDATE idp_group_mappings SET created_by_person_id = (SELECT min(id) FROM persons)");
		// mappings of persons need an ended setup phase (ADR-042)
		instance.update("""
				INSERT INTO system_settings (id, instance_slug, setup_phase_ended_at, setup_phase_ended_by_action)
				VALUES (1, 'acme-corp', now(), 'idp-group-mapping')""");
	}

	@AfterAll
	static void stop() {
		instance.close();
	}

	@Test
	void effectiveRoleIsRecordedAndShownAsLastMatch() throws Exception {
		// a privileged role without a second factor is not effective and no match
		instance.login("dev-admin-no-mfa");
		assertThat(lastMatchedAt(adminMapping)).isNull();

		ApplicationLogin admin = instance.login("dev-system-admin");
		assertThat(lastMatchedAt(adminMapping)).isNotNull();

		HttpResponse<String> listed = instance.get("/api/v1/admin/idp-group-mappings", admin.sessionToken());
		assertThat(listed.statusCode()).isEqualTo(200);
		JsonNode mappings = JsonMapper.shared().readTree(listed.body());
		assertThat(mapping(mappings, adminMapping).path("lastMatchedAt").isString()).isTrue();
		assertThat(mapping(mappings, adminMapping).path("warnings")).isEmpty();
		assertThat(mapping(mappings, architectMapping).path("lastMatchedAt").isNull()).isTrue();
	}

	@Test
	void warningOnlyForSystemAdminMappingWithoutEvidence() throws Exception {
		ApplicationLogin admin = instance.login("dev-system-admin");
		String csrf = JsonMapper.shared().readTree(instance.get("/api/v1/me", admin.sessionToken()).body())
			.path("csrfToken").asString();

		HttpResponse<String> unseen = create(admin, csrf, DevRealm.groupId("oea-auditors"), "system-admin");
		assertThat(unseen.statusCode()).isEqualTo(201);
		JsonNode warnings = JsonMapper.shared().readTree(unseen.body()).path("warnings");
		assertThat(warnings).hasSize(1);
		assertThat(warnings.get(0).path("code").asString()).isEqualTo("group-not-seen");
		assertThat(warnings.get(0).path("message").asString()).isNotBlank();

		HttpResponse<String> otherRole = create(admin, csrf, DevRealm.groupId("oea-auditors"), "viewer");
		assertThat(otherRole.statusCode()).isEqualTo(201);
		assertThat(JsonMapper.shared().readTree(otherRole.body()).path("warnings")).isEmpty();

		// the viewers' group is in the groups claim of a current session
		instance.login("dev-viewer");
		HttpResponse<String> seen = create(admin, csrf, DevRealm.groupId("oea-viewers"), "system-admin");
		assertThat(seen.statusCode()).isEqualTo(201);
		assertThat(JsonMapper.shared().readTree(seen.body()).path("warnings")).isEmpty();
	}

	private static String lastMatchedAt(UUID mapping) throws Exception {
		return instance.single("SELECT last_matched_at::text FROM idp_group_mappings WHERE id = '" + mapping + "'");
	}

	private static JsonNode mapping(JsonNode mappings, UUID id) {
		return mappings.valueStream()
			.filter((node) -> node.path("id").asString().equals(id.toString()))
			.findFirst()
			.orElseThrow();
	}

	private static HttpResponse<String> create(ApplicationLogin login, String csrf, String group, String role)
			throws Exception {
		String body = JsonMapper.shared()
			.createObjectNode()
			.put("authProviderId", instance.providerId().toString())
			.put("groupIdentifier", group)
			.put("roleId", role)
			.toString();
		return instance.send(HttpRequest.newBuilder(URI.create(instance.url() + "/api/v1/admin/idp-group-mappings"))
			.header("Cookie", "__Host-OEA_SESSION=" + login.sessionToken())
			.header("X-XSRF-TOKEN", csrf)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

}

package io.oea.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.auth.DevRealm;
import io.oea.testsupport.auth.OidcLogin;
import io.oea.testsupport.containers.OeaKeycloak;
import io.oea.testsupport.containers.OeaTestDatabase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;

/** The shared test infrastructure of design D7: container singletons and the real login flow. */
@SpringBootTest
@AutoConfigureMockMvc
class TestInfrastructureTests {

	@DynamicPropertySource
	static void containers(DynamicPropertyRegistry registry) {
		OeaTestDatabase.registerRuntimeDataSource(registry);
	}

	@Autowired
	private MockMvcTester mvc;

	@Test
	void applicationStartsAgainstPostgresAndKeycloakSingletons() {
		assertThat(OeaKeycloak.issuer()).endsWith("/realms/" + OeaKeycloak.REALM);
		assertThat(this.mvc.get().uri("/api/v1/health")).hasStatus(HttpStatus.OK);
	}

	@Test
	void viewerLoginWithPkceYieldsGroupIdentifiers() {
		JsonNode claims = OidcLogin.login(OeaKeycloak.issuer(), "dev-viewer").accessTokenClaims();
		assertThat(claims.path("preferred_username").asString()).isEqualTo("dev-viewer");
		assertThat(groups(claims)).containsExactly(DevRealm.groupId("oea-viewers"));
	}

	@Test
	void privilegedLoginPassesTheSecondFactor() {
		JsonNode claims = OidcLogin.login(OeaKeycloak.issuer(), "dev-system-admin").accessTokenClaims();
		assertThat(claims.path("preferred_username").asString()).isEqualTo("dev-system-admin");
		assertThat(groups(claims)).containsExactly(DevRealm.groupId("oea-system-admins"));
	}

	private static List<String> groups(JsonNode claims) {
		List<String> groups = new ArrayList<>();
		claims.path("groups").forEach((group) -> groups.add(group.asString()));
		return groups;
	}

}

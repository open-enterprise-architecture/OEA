package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Requirement "Implementation status of the security schemes" of platform/api-contract (API-1;
 * design D4 of s11-review-hardening), on variants of the packaged contract.
 */
class SecuritySchemeCoverageCheckTests {

	@Test
	void markersMatchTheAcceptedSchemes() {
		ContractAuthorizationRules rules = ContractAuthorizationRules.load();
		// all four schemes are accepted since s12-client-credentials-token (ADR-050)
		assertThat(rules.securitySchemes()).containsEntry("cookieAuth", false)
			.containsEntry("bearerAuth", false)
			.containsEntry("oauth2ClientCredentials", false)
			.containsEntry("clientSecretBasic", false);
		assertThatNoException().isThrownBy(() -> new SecuritySchemeCoverageCheck(rules));
	}

	@Test
	void acceptedSchemeMarkedAsPlanned() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new SecuritySchemeCoverageCheck(variant((schemes) -> mark(schemes, "cookieAuth"))))
			.withMessageContaining("cookieAuth is accepted but marked as planned")
			.withMessageNotContaining("bearerAuth");
	}

	@Test
	void acceptedTokenSchemeMarkedAsPlanned() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new SecuritySchemeCoverageCheck(variant((schemes) -> mark(schemes, "bearerAuth"))))
			.withMessageContaining("bearerAuth is accepted but marked as planned")
			.withMessageNotContaining("cookieAuth");
	}

	@Test
	void plannedSchemeWithoutMarker() {
		// a release that accepts only the cookie must mark the token schemes as planned
		assertThat(SecuritySchemeCoverageCheck.problems(
				Map.of("cookieAuth", false, "bearerAuth", false, "oauth2ClientCredentials", true), Set.of("cookieAuth")))
			.containsExactly("bearerAuth is not accepted but not marked as planned");
	}

	@Test
	void bothErrorsAreNamed() {
		assertThat(SecuritySchemeCoverageCheck.problems(Map.of("cookieAuth", true, "bearerAuth", false),
				Set.of("cookieAuth")))
			.containsExactly("bearerAuth is not accepted but not marked as planned",
					"cookieAuth is accepted but marked as planned");
	}

	@Test
	void acceptedSchemeMissingInTheContract() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new SecuritySchemeCoverageCheck(variant((schemes) -> schemes.remove("cookieAuth"))))
			.withMessageContaining("cookieAuth is accepted but missing in the contract");
	}

	private static void mark(ObjectNode schemes, String scheme) {
		((ObjectNode) schemes.get(scheme)).put("x-oea-status", "planned");
	}


	private static ContractAuthorizationRules variant(Consumer<ObjectNode> change) {
		try (InputStream in = new ClassPathResource(ContractAuthorizationRules.CONTRACT).getInputStream()) {
			ObjectNode contract = (ObjectNode) YAMLMapper.builder().build().readTree(in);
			change.accept((ObjectNode) contract.get("components").get("securitySchemes"));
			return ContractAuthorizationRules.parse(contract);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}

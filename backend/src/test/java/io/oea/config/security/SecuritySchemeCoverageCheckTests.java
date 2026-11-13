package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.io.InputStream;
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
		assertThat(rules.securitySchemes()).containsEntry("cookieAuth", false)
			.containsEntry("bearerAuth", true)
			.containsEntry("oauth2ClientCredentials", true)
			.containsEntry("clientSecretBasic", true);
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
	void plannedSchemeWithoutMarker() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new SecuritySchemeCoverageCheck(variant((schemes) -> unmark(schemes, "bearerAuth"))))
			.withMessageContaining("bearerAuth is not accepted but not marked as planned")
			.withMessageNotContaining("cookieAuth");
	}

	@Test
	void bothErrorsAreNamed() {
		assertThatIllegalStateException().isThrownBy(() -> new SecuritySchemeCoverageCheck(variant((schemes) -> {
			mark(schemes, "cookieAuth");
			unmark(schemes, "bearerAuth");
		})))
			.withMessageContaining("cookieAuth is accepted but marked as planned")
			.withMessageContaining("bearerAuth is not accepted but not marked as planned");
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

	private static void unmark(ObjectNode schemes, String scheme) {
		((ObjectNode) schemes.get(scheme)).remove("x-oea-status");
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

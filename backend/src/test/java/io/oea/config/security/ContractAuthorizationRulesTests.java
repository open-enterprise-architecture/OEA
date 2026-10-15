package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Rules read from the contract (design D3). */
class ContractAuthorizationRulesTests {

	@Test
	void packagedContractYieldsRulesForEveryOperation() {
		ContractAuthorizationRules rules = ContractAuthorizationRules.load();
		assertThat(rules.operationIds()).contains("getHealth", "localLogin", "getCurrentPrincipal", "listPermissions");
		assertThat(rules.rule("getHealth").access()).isEqualTo(ContractAuthorizationRules.Rule.Access.PUBLIC);
		assertThat(rules.rule("listPermissions").allows(Set.of("role:assign"))).isTrue();
		assertThat(rules.rule("listPermissions").allows(Set.of("entity:read"))).isFalse();
		assertThat(rules.publicEndpoints()).contains(new ContractAuthorizationRules.PublicEndpoint("GET", "/api/v1/health"));
		assertThat(rules.permissionDescriptions()).hasSize(20);
	}

	@Test
	void operationWithoutDeclarationFailsStartup() {
		var contract = YAMLMapper.builder().build().readTree("""
				paths:
				  /x:
				    get:
				      operationId: undeclared
				""");
		assertThatIllegalStateException().isThrownBy(() -> ContractAuthorizationRules.parse(contract))
			.withMessageContaining("lacks operationId or x-oea-authorization");
	}

}

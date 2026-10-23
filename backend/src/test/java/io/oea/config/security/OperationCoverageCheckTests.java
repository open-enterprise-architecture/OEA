package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * The marks that let an implemented operation with contract conditions start (design D3) and the
 * planned marker of the contract (spec platform/api-contract, s2b-ci-and-contract D1).
 */
class OperationCoverageCheckTests {

	private static final ContractAuthorizationRules CONTRACT = ContractAuthorizationRules.parse(YAMLMapper.builder()
		.build()
		.readTree("""
				paths:
				  /things:
				    get:
				      operationId: listThings
				      x-oea-authorization: {"access": "authenticated"}
				    post:
				      operationId: createThing
				      x-oea-authorization: {"access": "authenticated"}
				      x-oea-status: planned
				"""));

	// Requirement: Implementation status in the contract

	@Test
	void plannedMarkerMatchesTheImplementation() {
		assertThat(OperationCoverageCheck.statusProblems(Map.of("listThings", true, "createThing", false), CONTRACT))
			.isEmpty();
	}

	@Test
	void implementedOperationStillMarkedAsPlanned() {
		assertThat(OperationCoverageCheck.statusProblems(Map.of("listThings", true, "createThing", true), CONTRACT))
			.containsExactly("createThing is implemented but marked as planned");
	}

	@Test
	void unimplementedOperationWithoutPlannedMarker() {
		assertThat(OperationCoverageCheck.statusProblems(Map.of("listThings", false, "createThing", false), CONTRACT))
			.containsExactly("listThings is not implemented but not marked as planned");
	}

	@Test
	void operationWithoutHandler() {
		assertThat(OperationCoverageCheck.statusProblems(Map.of("listThings", true), CONTRACT))
			.containsExactly("createThing has no handler");
	}

	@Test
	void handlerEnforcedConditionsNeedReasonRoleAndBody() throws NoSuchMethodException {
		assertThat(OperationCoverageCheck.conditionsEnforcedByHandler(handler("enforced"))).isTrue();
		assertThat(OperationCoverageCheck.conditionsEnforcedByHandler(handler("withoutReason"))).isFalse();
		assertThat(OperationCoverageCheck.conditionsEnforcedByHandler(handler("withoutProbe"))).isFalse();
		assertThat(OperationCoverageCheck.conditionsEnforcedByHandler(handler("unmarked"))).isFalse();
	}

	@Test
	void unreachableConditionsNeedAReason() throws NoSuchMethodException {
		assertThat(OperationCoverageCheck.conditionsNotReachable(handler("unreachable"))).isTrue();
		assertThat(OperationCoverageCheck.conditionsNotReachable(handler("unmarked"))).isFalse();
	}

	private static HandlerMethod handler(String name) throws NoSuchMethodException {
		return new HandlerMethod(new Handlers(), Handlers.class.getMethod(name));
	}

	public static class Handlers {

		@ConditionsEnforcedByHandler(value = "checked by the service", probeRole = "viewer", probeBody = "{}")
		public void enforced() {
		}

		@ConditionsEnforcedByHandler(value = " ", probeRole = "viewer", probeBody = "{}")
		public void withoutReason() {
		}

		@ConditionsEnforcedByHandler(value = "checked by the service", probeRole = "viewer", probeBody = "")
		public void withoutProbe() {
		}

		@ConditionsNotReachable("rejected before")
		public void unreachable() {
		}

		public void unmarked() {
		}

	}

}

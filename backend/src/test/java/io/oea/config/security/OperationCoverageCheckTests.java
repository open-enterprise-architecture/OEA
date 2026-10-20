package io.oea.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

/** The marks that let an implemented operation with contract conditions start (design D3). */
class OperationCoverageCheckTests {

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

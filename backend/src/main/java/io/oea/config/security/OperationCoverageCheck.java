package io.oea.config.security;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Fails startup if a handler of a generated operation has no rule in the contract, or if an
 * implemented operation declares {@code conditions}, which are not enforced generically (design D3),
 * unless the handler rejects every request that would reach them ({@link ConditionsNotReachable}) or
 * enforces them itself ({@link ConditionsEnforcedByHandler}).
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Component
class OperationCoverageCheck implements SmartInitializingSingleton {

	private final RequestMappingHandlerMapping mapping;

	private final ContractAuthorizationRules rules;

	OperationCoverageCheck(RequestMappingHandlerMapping requestMappingHandlerMapping, ContractAuthorizationRules rules) {
		this.mapping = requestMappingHandlerMapping;
		this.rules = rules;
	}

	@Override
	public void afterSingletonsInstantiated() {
		List<String> problems = new ArrayList<>();
		for (HandlerMethod handler : this.mapping.getHandlerMethods().values()) {
			if (!OperationHandlers.isGeneratedOperation(handler)) {
				continue;
			}
			String operationId = handler.getMethod().getName();
			ContractAuthorizationRules.Rule rule = this.rules.rule(operationId);
			if (rule == null) {
				problems.add(operationId + " has no x-oea-authorization rule");
			}
			else if (rule.hasConditions() && isImplemented(handler) && !conditionsNotReachable(handler)
					&& !conditionsEnforcedByHandler(handler)) {
				problems.add(operationId + " declares conditions, which are not enforced yet");
			}
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Authorization coverage incomplete: " + String.join("; ", problems));
		}
	}

	static boolean conditionsNotReachable(HandlerMethod handler) {
		ConditionsNotReachable mark = handler.getMethodAnnotation(ConditionsNotReachable.class);
		return mark != null && !mark.value().isBlank();
	}

	static boolean conditionsEnforcedByHandler(HandlerMethod handler) {
		ConditionsEnforcedByHandler mark = handler.getMethodAnnotation(ConditionsEnforcedByHandler.class);
		return mark != null && !mark.value().isBlank() && !mark.probeRole().isBlank() && !mark.probeBody().isBlank();
	}

	/** Implemented = overridden in the controller, not the generated default method (501). */
	static boolean isImplemented(HandlerMethod handler) {
		Method method = handler.getMethod();
		return !method.getDeclaringClass().getPackageName().equals(OperationHandlers.GENERATED_PACKAGE);
	}

}

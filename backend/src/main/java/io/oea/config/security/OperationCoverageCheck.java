package io.oea.config.security;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Fails startup if a handler of a generated operation has no rule in the contract, or if an
 * implemented operation declares {@code conditions}, which are not enforced generically (design D3),
 * unless the handler rejects every request that would reach them ({@link ConditionsNotReachable}) or
 * enforces them itself ({@link ConditionsEnforcedByHandler}). It also fails if an operation of the
 * contract has no handler, or if the planned marker ({@code x-oea-status}) and the implementation
 * disagree (s2b-ci-and-contract D1).
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
		Map<String, Boolean> implemented = new TreeMap<>();
		for (HandlerMethod handler : this.mapping.getHandlerMethods().values()) {
			if (!OperationHandlers.isGeneratedOperation(handler)) {
				continue;
			}
			String operationId = handler.getMethod().getName();
			implemented.merge(operationId, isImplemented(handler), Boolean::logicalOr);
			ContractAuthorizationRules.Rule rule = this.rules.rule(operationId);
			if (rule == null) {
				problems.add(operationId + " has no x-oea-authorization rule");
			}
			else if (rule.hasConditions() && isImplemented(handler) && !conditionsNotReachable(handler)
					&& !conditionsEnforcedByHandler(handler)) {
				problems.add(operationId + " declares conditions, which are not enforced yet");
			}
		}
		problems.addAll(statusProblems(implemented, this.rules));
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Operation coverage incomplete: " + String.join("; ", problems));
		}
	}

	/**
	 * Operations whose handler is missing or whose planned marker disagrees with the
	 * implementation; {@code implemented} maps the operationId of every handler to whether a
	 * controller implements it.
	 */
	static List<String> statusProblems(Map<String, Boolean> implemented, ContractAuthorizationRules rules) {
		List<String> problems = new ArrayList<>();
		for (String operationId : rules.operationIds()) {
			Boolean isImplemented = implemented.get(operationId);
			boolean planned = rules.rule(operationId).planned();
			if (isImplemented == null) {
				problems.add(operationId + " has no handler");
			}
			else if (isImplemented && planned) {
				problems.add(operationId + " is implemented but marked as planned");
			}
			else if (!isImplemented && !planned) {
				problems.add(operationId + " is not implemented but not marked as planned");
			}
		}
		return problems;
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

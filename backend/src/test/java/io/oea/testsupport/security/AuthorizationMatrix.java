package io.oea.testsupport.security;

import io.oea.config.security.ConditionsEnforcedByHandler;
import io.oea.config.security.ContractAuthorizationRules;
import io.oea.config.security.OperationHandlers;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Enumerates the API operations served by the application (ADR-032 §8, REQ-158 AC3). */
public final class AuthorizationMatrix {

	private AuthorizationMatrix() {
	}

	/**
	 * One served operation: HTTP method, a concrete path, its contract rule (or none) and the
	 * media type of its request body ({@code application/json} unless the handler consumes another);
	 * {@code conditions} is set if the handler enforces the contract conditions itself.
	 */
	public record Operation(String method, String path, String handler, ContractAuthorizationRules.Rule rule,
			String contentType, ConditionsEnforcedByHandler conditions) {
	}

	public static List<Operation> operations(RequestMappingHandlerMapping mapping, ContractAuthorizationRules rules) {
		List<Operation> operations = new ArrayList<>();
		mapping.getHandlerMethods().forEach((RequestMappingInfo info, HandlerMethod handler) -> {
			for (String pattern : info.getPatternValues()) {
				if (!pattern.startsWith("/api/v1/")) {
					continue;
				}
				String path = pattern.replaceAll("\\{[^}]+}", "1");
				ContractAuthorizationRules.Rule rule = OperationHandlers.isGeneratedOperation(handler)
						? rules.rule(handler.getMethod().getName()) : null;
				String contentType = info.getConsumesCondition()
					.getConsumableMediaTypes()
					.stream()
					.findFirst()
					.map(Object::toString)
					.orElse("application/json");
				for (var method : info.getMethodsCondition().getMethods()) {
					operations.add(new Operation(method.name(), path,
							handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName(), rule,
							contentType, handler.getMethodAnnotation(ConditionsEnforcedByHandler.class)));
				}
			}
		});
		return operations;
	}

	/** Served API operations without a contract rule – must be empty. */
	public static List<Operation> uncovered(List<Operation> operations) {
		return operations.stream().filter((operation) -> operation.rule() == null).toList();
	}

}

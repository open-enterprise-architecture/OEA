package io.oea.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Answers a planned operation – a generated handler that no controller implements – with 501
 * and the problem type {@code not-implemented}. Registered after {@link AuthorizationInterceptor},
 * so the caller is authenticated and authorized first (spec platform/api-contract,
 * s2b-ci-and-contract D2).
 */
class NotImplementedInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (handler instanceof HandlerMethod method && OperationHandlers.isGeneratedOperation(method)
				&& !OperationCoverageCheck.isImplemented(method)) {
			Problems.write(response, HttpServletResponse.SC_NOT_IMPLEMENTED, "not-implemented", "Not implemented");
			return false;
		}
		return true;
	}

}

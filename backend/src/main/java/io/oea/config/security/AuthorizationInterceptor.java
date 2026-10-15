package io.oea.config.security;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Function-level authorization L2 (ADR-032 §8): the permissions declared for the operation
 * are checked before the controller runs; handlers without a rule are denied for everybody.
 */
public class AuthorizationInterceptor implements HandlerInterceptor {

	private final ContractAuthorizationRules rules;

	private final PermissionResolver permissions;

	public AuthorizationInterceptor(ContractAuthorizationRules rules, PermissionResolver permissions) {
		this.rules = rules;
		this.permissions = permissions;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		ContractAuthorizationRules.Rule rule = this.rules.rule(method.getMethod().getName());
		if (rule == null || !OperationHandlers.isGeneratedOperation(method)) {
			Problems.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Forbidden");
			return false;
		}
		if (rule.access() == ContractAuthorizationRules.Rule.Access.PUBLIC) {
			return true;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof OeaAuthentication session)) {
			Problems.write(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated", "Authentication required");
			return false;
		}
		OeaPrincipal principal = session.getPrincipal();
		Set<String> granted = this.permissions.permissions(principal);
		if (!rule.allows(granted)) {
			Problems.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Forbidden");
			return false;
		}
		return true;
	}

}

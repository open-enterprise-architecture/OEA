package io.oea.config.security;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.PermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Function-level authorization L2 (ADR-032 §8): the permissions declared for the operation
 * are checked before the controller runs; handlers without a rule are denied for everybody.
 * A missing permission is audited as {@code authz.denied} (REQ-175 AC1).
 */
public class AuthorizationInterceptor implements HandlerInterceptor {

	private final ContractAuthorizationRules rules;

	private final PermissionResolver permissions;

	private final PermissionDenials denials;

	public AuthorizationInterceptor(ContractAuthorizationRules rules, PermissionResolver permissions,
			PermissionDenials denials) {
		this.rules = rules;
		this.permissions = permissions;
		this.denials = denials;
	}

	/** The target of the request: the path variable {@code id}, or the only path variable. */
	static String targetId(HttpServletRequest request) {
		if (!(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables)) {
			return null;
		}
		Object id = variables.containsKey("id") ? variables.get("id")
				: (variables.size() == 1) ? variables.values().iterator().next() : null;
		return (id instanceof String value) ? value : null;
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
			this.denials.record(principal, rule.operationId(), null, targetId(request),
					PermissionDenials.MISSING_PERMISSION);
			Problems.write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Forbidden");
			return false;
		}
		return true;
	}

}

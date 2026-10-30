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

	/**
	 * Operations a session with a required password change may call (design D4 of
	 * us-011-local-password; the suggestion: design D5 of us-027-password-administration).
	 */
	private static final Set<String> PASSWORD_CHANGE_OPERATIONS = Set.of("getCurrentPrincipal", "logout",
			"changeOwnPassword", "suggestPassword");

	/** Operations a session with a required enrollment may call (design D4 of us-031-second-factor-enforcement). */
	private static final Set<String> ENROLLMENT_OPERATIONS = Set.of("getCurrentPrincipal", "logout",
			"listOwnSecondFactors", "startTotpEnrollment", "confirmTotpEnrollment", "startPasskeyRegistration",
			"confirmPasskeyRegistration");

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
		if (OeaPrincipal.CHANGE_PASSWORD.equals(principal.requiredAction())
				&& !PASSWORD_CHANGE_OPERATIONS.contains(rule.operationId())) {
			// REQ-173 AC2: a temporary password only leads to the password change
			Problems.write(response, HttpServletResponse.SC_FORBIDDEN, "password-change-required",
					"Password change required");
			return false;
		}
		if (OeaPrincipal.ENROLL_SECOND_FACTOR.equals(principal.requiredAction())
				&& !ENROLLMENT_OPERATIONS.contains(rule.operationId())) {
			// US-031 AC2: an enforced second factor must be enrolled first
			Problems.write(response, HttpServletResponse.SC_FORBIDDEN, "second-factor-enrollment-required",
					"Second factor enrollment required");
			return false;
		}
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

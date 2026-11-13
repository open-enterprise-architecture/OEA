package io.oea.config.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/**
 * Fails startup if the planned markers of the security schemes in the contract disagree with
 * the schemes the backend accepts ({@link OeaSessionFilter#ACCEPTED_SCHEMES}): an accepted scheme
 * must not be marked as planned, and every other scheme must be (API-1 of the architecture
 * review 2026-11; design D4 of s11-review-hardening).
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Component
class SecuritySchemeCoverageCheck {

	SecuritySchemeCoverageCheck(ContractAuthorizationRules rules) {
		List<String> problems = problems(rules.securitySchemes(), OeaSessionFilter.ACCEPTED_SCHEMES);
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Security scheme markers disagree with the implementation: "
					+ String.join("; ", problems));
		}
	}

	/** {@code schemes} maps every scheme of the contract to whether it is marked as planned. */
	static List<String> problems(Map<String, Boolean> schemes, Set<String> accepted) {
		List<String> problems = new ArrayList<>();
		for (String scheme : new TreeSet<>(schemes.keySet())) {
			boolean planned = schemes.get(scheme);
			if (accepted.contains(scheme) && planned) {
				problems.add(scheme + " is accepted but marked as planned");
			}
			else if (!accepted.contains(scheme) && !planned) {
				problems.add(scheme + " is not accepted but not marked as planned");
			}
		}
		for (String scheme : new TreeSet<>(accepted)) {
			if (!schemes.containsKey(scheme)) {
				problems.add(scheme + " is accepted but missing in the contract");
			}
		}
		return problems;
	}

}

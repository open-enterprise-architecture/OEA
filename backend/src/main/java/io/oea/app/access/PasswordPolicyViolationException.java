package io.oea.app.access;

import java.util.List;

/** A new password does not satisfy the password policy (422 {@code password-policy-violation}); names every unmet rule. */
public class PasswordPolicyViolationException extends RuntimeException {

	private final List<String> rules;

	public PasswordPolicyViolationException(List<String> rules) {
		super("The password does not satisfy the password policy: " + String.join(", ", rules) + ".");
		this.rules = List.copyOf(rules);
	}

	public List<String> getRules() {
		return this.rules;
	}

}

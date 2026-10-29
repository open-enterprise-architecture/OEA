package io.oea.app.access;

/** No password can satisfy the effective password policy (409 {@code password-policy-unsatisfiable}, REQ-027 AC5). */
public class PasswordPolicyUnsatisfiableException extends RuntimeException {

	public PasswordPolicyUnsatisfiableException(String message) {
		super(message);
	}

}

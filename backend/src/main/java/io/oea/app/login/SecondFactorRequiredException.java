package io.oea.app.login;

import java.util.List;

/**
 * The password was correct; the login continues with the second step (design D5), with a TOTP
 * code or a passkey (design D5 of us-022-passkeys).
 */
public class SecondFactorRequiredException extends RuntimeException {

	private final String loginTransaction;

	private final List<String> methods;

	public SecondFactorRequiredException(String loginTransaction, List<String> methods) {
		super("second factor required");
		this.loginTransaction = loginTransaction;
		this.methods = List.copyOf(methods);
	}

	public String getLoginTransaction() {
		return this.loginTransaction;
	}

	/** {@code totp} and/or {@code passkey}. */
	public List<String> getMethods() {
		return this.methods;
	}

}

package io.oea.app.login;

/** The password was correct; the login continues with the TOTP step (design D5). */
public class SecondFactorRequiredException extends RuntimeException {

	private final String loginTransaction;

	public SecondFactorRequiredException(String loginTransaction) {
		super("second factor required");
		this.loginTransaction = loginTransaction;
	}

	public String getLoginTransaction() {
		return this.loginTransaction;
	}

}

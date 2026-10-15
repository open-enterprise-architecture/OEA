package io.oea.app.login;

/** A TOTP code was wrong or already used (REQ-170). */
public class InvalidTotpCodeException extends RuntimeException {

	public InvalidTotpCodeException() {
		super("invalid TOTP code");
	}

}

package io.oea.config.oidc;

/** The ID token failed validation (REQ-166 AC2); the message names the check, never the token. */
public class InvalidIdTokenException extends RuntimeException {

	public InvalidIdTokenException(String message, Throwable cause) {
		super(message, cause);
	}

}

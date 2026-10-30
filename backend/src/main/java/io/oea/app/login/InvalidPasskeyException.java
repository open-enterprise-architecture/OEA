package io.oea.app.login;

/** A passkey registration response did not pass the verification (400 {@code invalid-passkey}). */
public class InvalidPasskeyException extends RuntimeException {

	public InvalidPasskeyException() {
		super("The passkey could not be verified.");
	}

}

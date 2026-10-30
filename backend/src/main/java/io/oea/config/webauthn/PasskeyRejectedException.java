package io.oea.config.webauthn;

/** A registration or authentication response did not pass the WebAuthn verification. */
public class PasskeyRejectedException extends RuntimeException {

	public PasskeyRejectedException(String message, Throwable cause) {
		super(message, cause);
	}

	public PasskeyRejectedException(String message) {
		super(message);
	}

}

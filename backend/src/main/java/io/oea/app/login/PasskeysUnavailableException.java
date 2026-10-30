package io.oea.app.login;

/** The instance has no relying party for passkeys (409 {@code passkeys-unavailable}, design D1 of us-022-passkeys). */
public class PasskeysUnavailableException extends RuntimeException {

	public PasskeysUnavailableException() {
		super("Passkeys are not available: the instance has no public URL and no passkey settings.");
	}

}

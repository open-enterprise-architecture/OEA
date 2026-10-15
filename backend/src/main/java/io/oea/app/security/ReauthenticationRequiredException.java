package io.oea.app.security;

/** The action needs an authentication within the last 5 minutes (REQ-172). */
public class ReauthenticationRequiredException extends RuntimeException {

	public ReauthenticationRequiredException() {
		super("reauthentication required");
	}

}

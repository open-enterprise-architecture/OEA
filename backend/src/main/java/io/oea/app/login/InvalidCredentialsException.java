package io.oea.app.login;

/** Generic rejection of a local login; identical for unknown names and wrong secrets (REQ-168). */
public class InvalidCredentialsException extends RuntimeException {

	private final long retryAfterSeconds;

	public InvalidCredentialsException(long retryAfterSeconds) {
		super("invalid credentials");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/** Seconds until the next attempt is accepted; 0 if not throttled. */
	public long getRetryAfterSeconds() {
		return this.retryAfterSeconds;
	}

}

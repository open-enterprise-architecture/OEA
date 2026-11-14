package io.oea.app.access;

/**
 * A service account request outside the limits of its fields (400; design D3 of
 * us-142-service-accounts): a blank or too long name, too many roles, or a secret validity
 * outside 1 day to the configured maximum.
 */
public class InvalidServiceAccountException extends RuntimeException {

	private final String field;

	public InvalidServiceAccountException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return this.field;
	}

}

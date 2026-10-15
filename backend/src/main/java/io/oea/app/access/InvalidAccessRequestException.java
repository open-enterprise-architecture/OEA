package io.oea.app.access;

/** The request violates a rule of access administration (422); names the offending field. */
public class InvalidAccessRequestException extends RuntimeException {

	private final String field;

	public InvalidAccessRequestException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String getField() {
		return this.field;
	}

}

package io.oea.app.access;

import java.util.List;

/** The request violates a rule of access administration (422); names the offending fields. */
public class InvalidAccessRequestException extends RuntimeException {

	private final List<String> fields;

	public InvalidAccessRequestException(String field, String message) {
		this(List.of(field), message);
	}

	public InvalidAccessRequestException(List<String> fields, String message) {
		super(message);
		this.fields = List.copyOf(fields);
	}

	public List<String> getFields() {
		return this.fields;
	}

}

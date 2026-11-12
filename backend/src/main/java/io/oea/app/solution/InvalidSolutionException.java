package io.oea.app.solution;

import java.util.List;

/** The solution cannot be stored: a blank or taken name (REQ-038). Nothing was stored. */
public class InvalidSolutionException extends RuntimeException {

	private final List<String> fields;

	InvalidSolutionException(String field, String message) {
		super(message);
		this.fields = List.of(field);
	}

	/** The rejected fields, for {@code invalidFields}. */
	public List<String> getFields() {
		return this.fields;
	}

}

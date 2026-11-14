package io.oea.app.history;

import java.util.List;

/** A history read with {@code from} after {@code to}, or with a cursor of another query (400). */
public class InvalidHistoryQueryException extends RuntimeException {

	private final List<String> fields;

	InvalidHistoryQueryException(String field) {
		super("The parameter '" + field + "' is invalid.");
		this.fields = List.of(field);
	}

	/** The rejected parameters, for {@code invalidFields}. */
	public List<String> getFields() {
		return this.fields;
	}

}

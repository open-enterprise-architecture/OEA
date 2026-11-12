package io.oea.app.solution;

import java.util.List;

/** A query of the baseline with a cursor of another query, of another page or one that does not parse. */
public class InvalidSolutionQueryException extends RuntimeException {

	private final List<String> fields;

	InvalidSolutionQueryException(String field) {
		super("The parameter '" + field + "' is invalid.");
		this.fields = List.of(field);
	}

	/** The rejected parameters, for {@code invalidFields}. */
	public List<String> getFields() {
		return this.fields;
	}

}

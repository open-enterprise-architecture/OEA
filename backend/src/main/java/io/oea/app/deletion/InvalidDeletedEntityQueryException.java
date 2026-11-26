package io.oea.app.deletion;

import java.util.List;

/** An invalid query of the list of deleted entities, such as a cursor of another query (design D1 of s17-deleted-entities). */
public class InvalidDeletedEntityQueryException extends RuntimeException {

	private final List<String> invalidFields;

	InvalidDeletedEntityQueryException(List<String> invalidFields) {
		super("Invalid parameters: " + String.join(", ", invalidFields));
		this.invalidFields = List.copyOf(invalidFields);
	}

	/** The rejected parameters, e.g. {@code cursor}. */
	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

}

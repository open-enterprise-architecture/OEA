package io.oea.app.entity;

import java.util.List;

/**
 * A deletion the operation does not offer: a structuring aid, which has its own operation, or an
 * automatic composition connection, which the parent change removes (422, design D2 of
 * s17-entity-delete). Nothing changed.
 */
public class InvalidDeletionException extends RuntimeException {

	private final List<String> invalidFields;

	public InvalidDeletionException(String field, String message) {
		super(message);
		this.invalidFields = List.of(field);
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

}

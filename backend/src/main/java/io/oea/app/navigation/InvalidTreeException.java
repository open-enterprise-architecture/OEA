package io.oea.app.navigation;

import java.util.List;

/**
 * A change of the navigation tree is invalid: a field such as {@code name}, {@code parentId} or
 * {@code referenceId}, or the deletion of the root (without field). Nothing was stored.
 */
public class InvalidTreeException extends RuntimeException {

	private final List<String> invalidFields;

	private InvalidTreeException(String message, List<String> invalidFields) {
		super(message);
		this.invalidFields = List.copyOf(invalidFields);
	}

	static InvalidTreeException invalid(String field, String message) {
		return new InvalidTreeException(message, List.of(field));
	}

	static InvalidTreeException rootProtected() {
		return new InvalidTreeException("The root folder cannot be deleted.", List.of());
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

}

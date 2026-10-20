package io.oea.app.catalog;

import java.util.List;

/** The catalog cannot be stored: an invalid field or a taken name (REQ-043). Nothing was stored. */
public class InvalidCatalogException extends RuntimeException {

	private final boolean nameTaken;

	private final List<String> invalidFields;

	private InvalidCatalogException(String message, boolean nameTaken, List<String> invalidFields) {
		super(message);
		this.nameTaken = nameTaken;
		this.invalidFields = List.copyOf(invalidFields);
	}

	static InvalidCatalogException invalid(String field, String message) {
		return new InvalidCatalogException(message, false, List.of(field));
	}

	static InvalidCatalogException nameTaken() {
		return new InvalidCatalogException("A catalog with this name already exists", true, List.of("name"));
	}

	/** The name collides with an existing catalog (problem type {@code catalog-name-taken}). */
	public boolean isNameTaken() {
		return this.nameTaken;
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

}

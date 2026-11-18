package io.oea.app.hierarchy;

import java.util.List;

/**
 * A parent change, a structuring aid or its deletion that violates a rule (422 with the rejected
 * field, design D3 of us-135-component-hierarchy). Nothing changed.
 */
public class InvalidHierarchyException extends RuntimeException {

	private final List<String> invalidFields;

	InvalidHierarchyException(String field, String message) {
		super(message);
		this.invalidFields = List.of(field);
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

}

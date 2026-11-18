package io.oea.app.navigation;

import java.util.List;

/**
 * A query of the solution browser is invalid: it combines its parameters in a way the scope does
 * not allow (422 with the rejected fields), or it names a value the contract does not know (400,
 * without fields).
 */
public class InvalidBrowserQueryException extends RuntimeException {

	private final List<String> invalidFields;

	InvalidBrowserQueryException(String field, String message) {
		super(message);
		this.invalidFields = List.of(field);
	}

	private InvalidBrowserQueryException(String message) {
		super(message);
		this.invalidFields = List.of();
	}

	static InvalidBrowserQueryException unknownValue(String parameter, String value) {
		return new InvalidBrowserQueryException("Unknown " + parameter + " " + value + ".");
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

}

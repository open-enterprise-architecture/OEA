package io.oea.app.solution;

import java.util.List;

/**
 * The plateau cannot be stored (design D2 of us-084-plateaus): a field violates a rule of
 * REQ-084, US-089 or US-155, or the solution names plateaus that do not fit (US-038 AC3, AC4).
 * Nothing was stored.
 */
public class InvalidPlateauException extends RuntimeException {

	private final String field;

	InvalidPlateauException(String field, String message) {
		super(message);
		this.field = field;
	}

	/** The rejected fields, for {@code invalidFields}; empty for a rule over two fields. */
	public List<String> getFields() {
		return (this.field != null) ? List.of(this.field) : List.of();
	}

}

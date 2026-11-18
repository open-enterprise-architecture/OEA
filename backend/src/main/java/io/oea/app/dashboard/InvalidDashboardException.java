package io.oea.app.dashboard;

import java.util.List;

/**
 * A dashboard or widget change that violates a rule (422 with the rejected fields, design D2 and
 * D3 of us-055-dashboards). A taken name is {@link #isNameTaken()}. Nothing changed.
 */
public class InvalidDashboardException extends RuntimeException {

	private final List<String> invalidFields;

	private final boolean nameTaken;

	private InvalidDashboardException(String message, List<String> invalidFields, boolean nameTaken) {
		super(message);
		this.invalidFields = invalidFields;
		this.nameTaken = nameTaken;
	}

	static InvalidDashboardException invalid(String field, String message) {
		return new InvalidDashboardException(message, List.of(field), false);
	}

	static InvalidDashboardException nameTaken() {
		return new InvalidDashboardException("A dashboard with this name already exists", List.of("name"), true);
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

	public boolean isNameTaken() {
		return this.nameTaken;
	}

}

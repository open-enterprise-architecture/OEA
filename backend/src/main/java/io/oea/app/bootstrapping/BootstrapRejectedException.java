package io.oea.app.bootstrapping;

import java.util.List;
import java.util.Locale;

/** A rejected bootstrapping attempt (REQ-015, REQ-017); never carries token or password. */
public class BootstrapRejectedException extends RuntimeException {

	public enum Reason {

		INVALID_SETUP_TOKEN, ALREADY_BOOTSTRAPPED, VALIDATION_FAILED;

		/** Name used in audit details and problem types, e.g. {@code invalid-setup-token}. */
		public String code() {
			return name().toLowerCase(Locale.ROOT).replace('_', '-');
		}

	}

	private final Reason reason;

	private final List<String> invalidFields;

	public BootstrapRejectedException(Reason reason, List<String> invalidFields) {
		super("Bootstrapping rejected: " + reason.code()
				+ (invalidFields.isEmpty() ? "" : " " + String.join(", ", invalidFields)));
		this.reason = reason;
		this.invalidFields = List.copyOf(invalidFields);
	}

	public Reason getReason() {
		return this.reason;
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

}

package io.oea.app.bootstrapping;

import java.util.List;
import java.util.Locale;

/** A rejected bootstrapping attempt (REQ-015, REQ-017); never carries token or password. */
public class BootstrapRejectedException extends RuntimeException {

	public enum Reason {

		INVALID_SETUP_TOKEN, ALREADY_BOOTSTRAPPED, VALIDATION_FAILED,

		/** The source address is throttled after too many wrong setup tokens (REQ-168). */
		THROTTLED;

		/** Name used in audit details and problem types, e.g. {@code invalid-setup-token}. */
		public String code() {
			return name().toLowerCase(Locale.ROOT).replace('_', '-');
		}

	}

	private final Reason reason;

	private final List<String> invalidFields;

	private final long retryAfterSeconds;

	public BootstrapRejectedException(Reason reason, List<String> invalidFields) {
		this(reason, invalidFields, 0);
	}

	private BootstrapRejectedException(Reason reason, List<String> invalidFields, long retryAfterSeconds) {
		super("Bootstrapping rejected: " + reason.code()
				+ (invalidFields.isEmpty() ? "" : " " + String.join(", ", invalidFields)));
		this.reason = reason;
		this.invalidFields = List.copyOf(invalidFields);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/** A rejection because the source address is throttled. */
	public static BootstrapRejectedException throttled(long retryAfterSeconds) {
		return new BootstrapRejectedException(Reason.THROTTLED, List.of(), retryAfterSeconds);
	}

	/** Seconds until the next attempt is accepted, for {@link Reason#THROTTLED}. */
	public long getRetryAfterSeconds() {
		return this.retryAfterSeconds;
	}

	public Reason getReason() {
		return this.reason;
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

}

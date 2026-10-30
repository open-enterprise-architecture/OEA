package io.oea.app.access;

/**
 * The last second factor of the system admin account after the setup phase (ADR-042), or of a
 * person while the enforcement is on (REQ-020); 409 {@code last-second-factor}.
 */
public class LastSecondFactorException extends RuntimeException {

	public LastSecondFactorException() {
		super("The last second factor cannot be removed: it is required after the setup phase or by the "
				+ "enforcement of a second factor.");
	}

}

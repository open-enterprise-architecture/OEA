package io.oea.app.access;

/** The last TOTP credential of the system admin account after the setup phase (409 {@code last-second-factor}, ADR-042). */
public class LastSecondFactorException extends RuntimeException {

	public LastSecondFactorException() {
		super("The system admin account needs at least one second factor after the setup phase.");
	}

}

package io.oea.app.access;

/** The change would remove the last usable path to {@code system-admin} (ADR-032 SoD-7). */
public class LastAdminPathException extends RuntimeException {

	public LastAdminPathException() {
		super("The local system admin account is the last usable path to system administration. Map an IdP group "
				+ "to system-admin and log in through it first.");
	}

}

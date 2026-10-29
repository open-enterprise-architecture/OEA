package io.oea.app.access;

/** The change would remove the last usable path to {@code system-admin} (ADR-032 SoD-7). */
public class LastAdminPathException extends RuntimeException {

	public LastAdminPathException() {
		super("This change would leave no usable path to system administration. Keep another path first: an active "
				+ "local system admin account, an IdP group mapping to system-admin that has granted the role in a "
				+ "login, or an open-ended system-admin assignment of an active person.");
	}

}

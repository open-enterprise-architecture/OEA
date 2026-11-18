package io.oea.app.history;

/**
 * A restore whose {@code If-Match} names another version than the current one (412
 * {@code stale-version}, design D2 of us-101-entity-restore).
 */
public class StaleEntityVersionException extends RuntimeException {

	private final int currentVersion;

	public StaleEntityVersionException(int currentVersion) {
		super("The entity was changed meanwhile; its current version is " + currentVersion + ".");
		this.currentVersion = currentVersion;
	}

	public int getCurrentVersion() {
		return this.currentVersion;
	}

}

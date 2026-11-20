package io.oea.app.entity;

/**
 * A connection restored directly whose endpoint is deleted, or an automatic composition
 * connection whose child has another parent (409 {@code connection-not-restorable}, design D3 of
 * s17-entity-delete). Nothing changed.
 */
public class ConnectionNotRestorableException extends RuntimeException {

	private final String reason;

	public ConnectionNotRestorableException(String reason) {
		super("The connection cannot be restored while its endpoint is deleted or its parent changed.");
		this.reason = reason;
	}

	/** {@code endpoint-deleted} or {@code parent-changed}. */
	public String getReason() {
		return this.reason;
	}

}

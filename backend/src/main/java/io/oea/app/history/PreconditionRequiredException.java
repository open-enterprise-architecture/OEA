package io.oea.app.history;

/** A write without the required {@code If-Match} (428, design D2 of us-101-entity-restore). */
public class PreconditionRequiredException extends RuntimeException {

	public PreconditionRequiredException() {
		super("The restore requires If-Match with the current version of the entity.");
	}

	/** A write of another element that requires {@code If-Match} (e.g. a viewpoint, us-090-viewpoints D3). */
	public PreconditionRequiredException(String message) {
		super(message);
	}

}

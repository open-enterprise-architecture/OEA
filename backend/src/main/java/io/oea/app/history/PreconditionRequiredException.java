package io.oea.app.history;

/** A restore without {@code If-Match} (428, design D2 of us-101-entity-restore). */
public class PreconditionRequiredException extends RuntimeException {

	public PreconditionRequiredException() {
		super("The restore requires If-Match with the current version of the entity.");
	}

}

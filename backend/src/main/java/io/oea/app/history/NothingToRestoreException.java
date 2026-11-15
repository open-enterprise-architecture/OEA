package io.oea.app.history;

/**
 * A restore in which every restorable field already has its target value (422
 * {@code nothing-to-restore}, design D2 of us-101-entity-restore).
 */
public class NothingToRestoreException extends RuntimeException {

	NothingToRestoreException() {
		super("Every field that can be restored already has the value of the target version.");
	}

}

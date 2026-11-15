package io.oea.app.history;

/**
 * A partial restore with an empty selection, or with a key that is not a field whose current
 * value differs from the target version (422 naming {@code fields}, design D2 of
 * us-104-partial-restore).
 */
public class InvalidRestoreSelectionException extends RuntimeException {

	InvalidRestoreSelectionException() {
		super("The selection must name at least one field whose value differs from the target version.");
	}

}

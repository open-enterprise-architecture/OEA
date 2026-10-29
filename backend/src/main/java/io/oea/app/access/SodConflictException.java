package io.oea.app.access;

/**
 * SoD-3 (ADR-032): the assignment combines metamodel and visibility administration for the
 * person and was not acknowledged (409, problem type {@code sod-conflict}).
 */
public class SodConflictException extends RuntimeException {

	public SodConflictException() {
		super("The assignment combines metamodel:write with visibility permissions for the person. Repeat it with "
				+ "acknowledgeSodConflict to accept the flagged combination.");
	}

}

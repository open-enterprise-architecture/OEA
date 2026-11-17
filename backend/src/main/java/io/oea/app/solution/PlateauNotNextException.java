package io.oea.app.solution;

/**
 * Only the target or transition plateau that succeeds the baseline goes live (owner decision
 * 2026-11-17; design D3 of us-086-plateau-go-live). Nothing was changed.
 */
public class PlateauNotNextException extends RuntimeException {

	PlateauNotNextException(String name) {
		super("The plateau '" + name + "' does not succeed the baseline and cannot go live yet");
	}

}

package io.oea.app.solution;

/**
 * A solution that targets a plateau goes live only with its plateau (US-157 AC3; design D4 of
 * us-086-plateau-go-live). Nothing was changed.
 */
public class PlateauSolutionException extends RuntimeException {

	PlateauSolutionException() {
		super("This solution targets a plateau and goes live with its plateau");
	}

}

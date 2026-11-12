package io.oea.app.solution;

/**
 * The solution is {@code implemented} or {@code archived} and accepts no change (solution:BR-03;
 * design D4 of us-040-entity-deltas). Nothing was changed.
 */
public class SolutionCompletedException extends RuntimeException {

	SolutionCompletedException() {
		super("This solution is completed and can no longer be changed");
	}

}

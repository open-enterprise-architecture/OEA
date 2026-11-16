package io.oea.app.solution;

import java.util.List;

/**
 * A go-live with unresolved field conflicts (409 {@code merge-required}, design D4 of
 * us-152-solution-go-live); nothing changed.
 */
public class MergeRequiredException extends RuntimeException {

	private final List<SolutionGoLive.Conflict> conflicts;

	MergeRequiredException(List<SolutionGoLive.Conflict> conflicts) {
		super("The solution has field conflicts; resolve them before the go-live.");
		this.conflicts = List.copyOf(conflicts);
	}

	public List<SolutionGoLive.Conflict> getConflicts() {
		return this.conflicts;
	}

}

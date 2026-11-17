package io.oea.app.metamodel;

import java.util.List;

/**
 * A metamodel change that removes values active solutions use, sent without a decision for each
 * of them (409 {@code solution-decision-required}, US-154 AC1; design D9 of us-153-open-issues).
 * Nothing changed.
 */
public class SolutionDecisionRequiredException extends RuntimeException {

	private final List<SolutionImpact.Affected> solutions;

	SolutionDecisionRequiredException(List<SolutionImpact.Affected> solutions) {
		super("The change removes values that active solutions use; decide for each whether it applies there.");
		this.solutions = List.copyOf(solutions);
	}

	public List<SolutionImpact.Affected> getSolutions() {
		return this.solutions;
	}

}

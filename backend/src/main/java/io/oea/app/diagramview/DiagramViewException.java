package io.oea.app.diagramview;

import io.oea.app.entity.Issue;
import java.util.List;

/**
 * A diagram view request that cannot be applied; nothing was changed (design D2 and D4 of
 * us-074-diagram-canvas). {@code INVALID_REQUEST} is a value outside the contract that bean
 * validation cannot see (400); {@code INVALID} lists the shapes and new entities that violate a
 * rule (422) and {@code CONFLICTS} the conflicts that block a save (409), each as an issue whose field is its path, e.g. {@code shapes[3].entityId}.
 */
public class DiagramViewException extends RuntimeException {

	public enum Reason {

		/** A value outside the contract, e.g. more than two decimals (400 {@code validation-failed}). */
		INVALID_REQUEST,
		/** Shapes or new entities that violate a rule (422 {@code validation-failed}). */
		INVALID,
		/** Shapes or edges whose type the viewpoint does not allow stay in the layout (409 {@code diagram-conflicts}). */
		CONFLICTS

	}

	private final Reason reason;

	private final List<Issue> issues;

	DiagramViewException(Reason reason, String message, List<Issue> issues) {
		super(message);
		this.reason = reason;
		this.issues = List.copyOf(issues);
	}

	public Reason getReason() {
		return this.reason;
	}

	public List<Issue> getIssues() {
		return this.issues;
	}

	/** The paths of the faulty fields, without duplicates, in the order they were found. */
	public List<String> getInvalidFields() {
		return this.issues.stream().map(Issue::field).distinct().toList();
	}

}

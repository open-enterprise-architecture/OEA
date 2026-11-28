package io.oea.app.lineage;

import io.oea.app.entity.Issue;
import java.util.List;

/**
 * A lineage query that cannot be answered (design D4 of us-065-lineage-model-and-query): a value
 * the contract cannot check (400), an unknown or deleted start (404) or a request that violates a
 * rule (422), each with the issues that name the fields.
 */
public class LineageException extends RuntimeException {

	/** The kind of the failure. */
	public enum Reason {

		/** A value outside the contract that bean validation cannot see (400 {@code validation-failed}). */
		INVALID_REQUEST,
		/** The start entity does not exist or is deleted (404). */
		NOT_FOUND,
		/** A request that violates a rule (422 {@code validation-failed}). */
		INVALID

	}

	private final Reason reason;

	private final List<Issue> issues;

	LineageException(Reason reason, String message, List<Issue> issues) {
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

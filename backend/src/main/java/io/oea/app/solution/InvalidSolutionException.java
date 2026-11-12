package io.oea.app.solution;

import io.oea.app.entity.Issue;
import java.util.List;

/**
 * The solution or one of its deltas cannot be stored: a blank or taken name (REQ-038), or a delta
 * that violates the metamodel or a rule of the solution (REQ-040). Nothing was stored.
 */
public class InvalidSolutionException extends RuntimeException {

	private final List<Issue> errors;

	InvalidSolutionException(String field, String message) {
		this(List.of(new Issue(field, "INVALID_VALUE", message)));
	}

	InvalidSolutionException(List<Issue> errors) {
		super(errors.size() == 1 ? errors.get(0).message()
				: String.join(" ", errors.stream().map(Issue::message).toList()));
		this.errors = List.copyOf(errors);
	}

	/** The rejected fields, for {@code invalidFields}. */
	public List<String> getFields() {
		return this.errors.stream().map(Issue::field).distinct().toList();
	}

	/** One entry per rejected field with its message, for {@code errors}. */
	public List<Issue> getErrors() {
		return this.errors;
	}

}

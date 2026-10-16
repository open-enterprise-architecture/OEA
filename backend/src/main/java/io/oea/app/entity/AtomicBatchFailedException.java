package io.oea.app.entity;

import java.util.List;

/**
 * An atomic batch with at least one faulty item: nothing was written (422, REQ-154). The
 * fields of the errors carry the item path, e.g. {@code entities[3].properties}.
 */
public class AtomicBatchFailedException extends RuntimeException {

	private final List<Issue> errors;

	public AtomicBatchFailedException(List<Issue> errors) {
		super(errors.size() + " faulty item(s); nothing was written");
		this.errors = List.copyOf(errors);
	}

	public List<Issue> getErrors() {
		return this.errors;
	}

}

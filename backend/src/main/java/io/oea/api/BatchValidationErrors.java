package io.oea.api;

import io.oea.api.generated.model.ValidationError;
import io.oea.app.entity.Issue;

/**
 * Maps validation issues of the application to the {@code ValidationError} of the contract, for
 * the entity batch and the problem responses (design D5 of s11-review-hardening: shared by
 * {@code io.oea.api} and {@code io.oea.api.entity} without a package cycle).
 */
public final class BatchValidationErrors {

	private BatchValidationErrors() {
	}

	public static ValidationError validationError(Issue issue) {
		return new ValidationError(issue.field(), issue.message()).code(issue.code());
	}

}

package io.oea.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How an operation answers a request that violates the contract – an invalid body, parameter
 * or JSON document (design D2 of s2b-backend-hardening). {@link ApiExceptionHandler} reads it
 * from the handler method; operations without it answer 400 {@code validation-failed}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContractViolationResponse {

	/** The shape of the problem body. */
	enum Shape {

		/** {@code validation-failed} with the rejected fields or parameters in {@code invalidFields}. */
		INVALID_FIELDS,

		/** {@code validation-failed} with one {@code errors} entry per violated field (entity batch, REQ-154). */
		BATCH_ERRORS,

		/** {@code invalid-package} with {@code pointer} or {@code location}; the rejection is audited (REQ-033). */
		PACKAGE

	}

	Shape shape() default Shape.INVALID_FIELDS;

	/** HTTP status, 400 or 422. */
	int status() default 400;

	String title();

	String detail();

}

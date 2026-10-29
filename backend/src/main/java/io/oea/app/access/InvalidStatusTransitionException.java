package io.oea.app.access;

/** The status transition of a person is not allowed (409, problem type {@code invalid-status-transition}). */
public class InvalidStatusTransitionException extends RuntimeException {

	public InvalidStatusTransitionException(String from, String to) {
		super("A person in status " + from + " cannot be set to " + to + ".");
	}

}

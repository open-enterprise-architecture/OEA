package io.oea.app.access;

/** The person already has a local password (409, problem type {@code password-already-set}). */
public class PasswordAlreadySetException extends RuntimeException {

	public PasswordAlreadySetException() {
		super("The person already has a password. A reset by an administrator follows with a later change.");
	}

}

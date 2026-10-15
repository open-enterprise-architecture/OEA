package io.oea.app.access;

/** The request conflicts with the current state (409), e.g. an identity pair that is already bound. */
public class AccessConflictException extends RuntimeException {

	public AccessConflictException(String message) {
		super(message);
	}

}

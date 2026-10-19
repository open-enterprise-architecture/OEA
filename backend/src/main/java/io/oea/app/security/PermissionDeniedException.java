package io.oea.app.security;

/**
 * A permission that the contract requires only for certain requests (a contract condition,
 * ADR-032 §8) is missing. Answered like a denial of the authorization filter: 403 with the
 * generic problem, never naming the permission (REQ-158).
 */
public class PermissionDeniedException extends RuntimeException {

	public PermissionDeniedException() {
		super("permission denied");
	}

}

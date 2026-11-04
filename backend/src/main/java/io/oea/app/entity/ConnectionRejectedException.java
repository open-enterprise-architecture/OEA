package io.oea.app.entity;

import java.util.List;

/**
 * A connection of {@code createConnection} that was not written (us-036-connection-metatypes D3):
 * 404 if an endpoint does not exist, 422 otherwise; the errors carry the error codes.
 */
public class ConnectionRejectedException extends RuntimeException {

	private final List<Issue> errors;

	public ConnectionRejectedException(List<Issue> errors) {
		super("The connection was not written.");
		this.errors = List.copyOf(errors);
	}

	public List<Issue> getErrors() {
		return this.errors;
	}

	/** Whether a missing endpoint is the reason (404). */
	public boolean endpointMissing() {
		return this.errors.stream()
			.anyMatch((error) -> "SOURCE_NOT_FOUND".equals(error.code()) || "TARGET_NOT_FOUND".equals(error.code()));
	}

}

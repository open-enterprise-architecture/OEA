package io.oea.app.entity;

/** The batch as a whole is rejected before any item is processed (422, design D4). */
public class BatchRejectedException extends RuntimeException {

	private final String type;

	public BatchRejectedException(String type, String message) {
		super(message);
		this.type = type;
	}

	/** Problem type, e.g. {@code batch-too-large} or {@code not-supported}. */
	public String getType() {
		return this.type;
	}

}

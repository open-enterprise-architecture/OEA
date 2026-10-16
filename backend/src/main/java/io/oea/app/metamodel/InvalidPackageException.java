package io.oea.app.metamodel;

/**
 * A package that cannot be imported (422, REQ-033, REQ-184).
 *
 * @see #getType() {@code invalid-package} or {@code unsupported-package-content}
 */
public class InvalidPackageException extends RuntimeException {

	private final String type;

	private final String pointer;

	public InvalidPackageException(String type, String pointer, String message) {
		super(message);
		this.type = type;
		this.pointer = pointer;
	}

	public String getType() {
		return this.type;
	}

	/** JSON pointer to the offending element. */
	public String getPointer() {
		return this.pointer;
	}

}

package io.oea.app.entity;

/**
 * A validation error or warning (ValidationError): the field path is relative to the item,
 * e.g. {@code properties[0].value}; the code is machine-readable (REQ-149).
 */
public record Issue(String field, String code, String message) {

	/** The issue with the field prefixed by the path of its item, e.g. {@code entities[3]}. */
	public Issue within(String itemPath) {
		return new Issue(itemPath + "." + this.field, this.code, this.message);
	}

}

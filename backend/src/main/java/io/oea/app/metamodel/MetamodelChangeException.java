package io.oea.app.metamodel;

import java.util.List;

/**
 * A metamodel change that cannot be applied; nothing was changed (design D1 of
 * us-032-metatype-editing). The reason gives the problem type.
 */
public class MetamodelChangeException extends RuntimeException {

	public enum Reason {

		/** Built-in or imported content (409 {@code read-only-metatype}, ADR-002). */
		READ_ONLY,
		/** The name is used already, compared case-insensitively (409 {@code name-taken}). */
		NAME_TAKEN,
		/** Still used; {@link #getCount()} gives the number of entities where it applies (409 {@code in-use}). */
		IN_USE,
		/** A property mapped twice in a base type chain (409 {@code conflict}). */
		CONFLICT,
		/** An invalid field (422 {@code validation-failed}). */
		INVALID

	}

	private final Reason reason;

	private final List<String> invalidFields;

	private final Long count;

	private MetamodelChangeException(Reason reason, String message, List<String> invalidFields, Long count) {
		super(message);
		this.reason = reason;
		this.invalidFields = List.copyOf(invalidFields);
		this.count = count;
	}

	static MetamodelChangeException readOnly(String element) {
		return new MetamodelChangeException(Reason.READ_ONLY,
				"The " + element + " is built-in or imported and cannot be changed.", List.of(), null);
	}

	static MetamodelChangeException nameTaken(String field) {
		return new MetamodelChangeException(Reason.NAME_TAKEN, "The name is used already.", List.of(field), null);
	}

	static MetamodelChangeException inUse(String message, Long count) {
		return new MetamodelChangeException(Reason.IN_USE, message, List.of(), count);
	}

	static MetamodelChangeException conflict(String message) {
		return new MetamodelChangeException(Reason.CONFLICT, message, List.of(), null);
	}

	static MetamodelChangeException invalid(String field, String message) {
		return new MetamodelChangeException(Reason.INVALID, message, List.of(field), null);
	}

	public Reason getReason() {
		return this.reason;
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

	/** The number of entities for {@link Reason#IN_USE}, or {@code null}. */
	public Long getCount() {
		return this.count;
	}

}

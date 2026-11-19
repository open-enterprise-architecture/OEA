package io.oea.app.diagram;

import java.util.List;

/**
 * A diagram write or query that cannot be applied; nothing was changed (design D3 of
 * us-074-diagrams). The reason gives the problem type. The package has its own exception because
 * the exceptions of {@code app.history} would make a package cycle with {@code app.metamodel}.
 */
public class DiagramChangeException extends RuntimeException {

	public enum Reason {

		/** An invalid field (422 {@code validation-failed}). */
		INVALID,
		/** The name is used by a diagram that is not deleted (422 {@code diagram-name-taken}). */
		NAME_TAKEN,
		/** {@code If-Match} names another version (412 {@code stale-version}). */
		STALE_VERSION,
		/** A write without {@code If-Match} (428 {@code precondition-required}). */
		PRECONDITION_REQUIRED,
		/** The diagram is not deleted and cannot be restored (409 {@code diagram-not-deleted}). */
		NOT_DELETED,
		/** An invalid query, as a cursor of another query (400 {@code validation-failed}). */
		INVALID_QUERY

	}

	private final Reason reason;

	private final List<String> invalidFields;

	private final Integer currentVersion;

	private DiagramChangeException(Reason reason, String message, List<String> invalidFields,
			Integer currentVersion) {
		super(message);
		this.reason = reason;
		this.invalidFields = List.copyOf(invalidFields);
		this.currentVersion = currentVersion;
	}

	static DiagramChangeException invalid(String field, String message) {
		return new DiagramChangeException(Reason.INVALID, message, List.of(field), null);
	}

	static DiagramChangeException nameTaken() {
		return new DiagramChangeException(Reason.NAME_TAKEN, "A diagram with this name already exists",
				List.of("name"), null);
	}

	static DiagramChangeException staleVersion(int currentVersion) {
		return new DiagramChangeException(Reason.STALE_VERSION,
				"The diagram was changed meanwhile; its current version is " + currentVersion + ".", List.of(),
				currentVersion);
	}

	static DiagramChangeException preconditionRequired() {
		return new DiagramChangeException(Reason.PRECONDITION_REQUIRED,
				"The write requires If-Match with the current version of the diagram.", List.of(), null);
	}

	static DiagramChangeException notDeleted() {
		return new DiagramChangeException(Reason.NOT_DELETED, "The diagram is not deleted.", List.of(), null);
	}

	static DiagramChangeException invalidQuery(String field) {
		return new DiagramChangeException(Reason.INVALID_QUERY, "The " + field + " does not belong to this query.",
				List.of(field), null);
	}

	public Reason getReason() {
		return this.reason;
	}

	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

	public Integer getCurrentVersion() {
		return this.currentVersion;
	}

}

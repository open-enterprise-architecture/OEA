package io.oea.app.entity;

/**
 * A write that needs an active entity, such as the restore of a version, addressed a deleted one
 * (409 {@code entity-deleted}, design D3 of s17-entity-delete). The entity is undeleted first;
 * nothing changed.
 */
public class DeletedEntityException extends RuntimeException {

	public DeletedEntityException() {
		super("The entity is deleted; restore it from the deleted entities first.");
	}

}

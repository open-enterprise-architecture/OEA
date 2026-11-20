package io.oea.app.entity;

/** The undelete of an entity that is not deleted (409 {@code entity-not-deleted}, design D3 of s17-entity-delete). */
public class EntityNotDeletedException extends RuntimeException {

	public EntityNotDeletedException() {
		super("The entity is not deleted.");
	}

}

package io.oea.app.entity;

import java.util.List;

/**
 * An interactive write names an entity version that is no longer the current one (409
 * {@code entity-version-conflict}, issue #69; design D2 of us-074-diagram-entity-details). Nothing
 * changed. Every stale entity is named, so that the client can reload exactly those.
 */
public class EntityVersionConflictException extends RuntimeException {

	/** A stale entity: the index of its update in the request, its ID and its current version. */
	public record Conflict(int index, int entityId, int currentVersion) {
	}

	private final List<Conflict> conflicts;

	public EntityVersionConflictException(List<Conflict> conflicts) {
		super(conflicts.size() + " entity(ies) changed meanwhile; nothing was written.");
		this.conflicts = List.copyOf(conflicts);
	}

	public List<Conflict> getConflicts() {
		return this.conflicts;
	}

}

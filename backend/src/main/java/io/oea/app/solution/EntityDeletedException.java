package io.oea.app.solution;

import java.util.List;
import java.util.UUID;

/**
 * A go-live with a delta {@code modified} whose entity was deleted meanwhile (409
 * {@code entity-deleted}, design D4 of us-152-solution-go-live); remove the delta and go live
 * again. Nothing changed.
 */
public class EntityDeletedException extends RuntimeException {

	private final List<UUID> deltaIds;

	EntityDeletedException(List<UUID> deltaIds) {
		super("The entity of a modification was deleted meanwhile; remove the delta first.");
		this.deltaIds = List.copyOf(deltaIds);
	}

	public List<UUID> getDeltaIds() {
		return this.deltaIds;
	}

}

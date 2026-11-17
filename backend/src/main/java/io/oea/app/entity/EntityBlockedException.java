package io.oea.app.entity;

import java.util.List;
import java.util.UUID;

/**
 * A write of an entity with an open issue of a solution (409 {@code entity-blocked}, US-153 AC1;
 * design D3 of us-153-open-issues). Nothing changed.
 */
public class EntityBlockedException extends RuntimeException {

	private final List<EntityBlocks.Block> blocks;

	private final List<UUID> deltaIds;

	public EntityBlockedException(List<EntityBlocks.Block> blocks) {
		this(blocks, List.of());
	}

	public EntityBlockedException(List<EntityBlocks.Block> blocks, List<UUID> deltaIds) {
		super("The entity has open issues of a solution; resolve them first.");
		this.blocks = List.copyOf(blocks);
		this.deltaIds = List.copyOf(deltaIds);
	}

	public List<EntityBlocks.Block> getBlocks() {
		return this.blocks;
	}

	/** The deltas of a go-live that touch the blocked entities; empty for other writes. */
	public List<UUID> getDeltaIds() {
		return this.deltaIds;
	}

}

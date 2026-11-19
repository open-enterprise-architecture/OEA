package io.oea.app.solution;

import io.oea.core.solution.DeltaStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The conflicts of a solution with other active solutions that change the same entities (design
 * D0 of us-044-conflict-warning; US-044, REQ-042): computed on each read, never stored. A solution
 * is active unless it is {@code implemented} or {@code archived}; a delta {@code new} has no entity
 * and never conflicts.
 */
@Service
public class SolutionConflicts {

	private final DeltaStore deltas;

	SolutionConflicts(DeltaStore deltas) {
		this.deltas = deltas;
	}

	/** Another active solution that holds a delta for the same entity, with its owner. */
	public record Conflict(UUID solutionId, String solutionName, String status, long ownerId, String ownerName,
			SolutionDeltas.Type deltaType) {
	}

	/**
	 * The conflicts of each of {@code entityIds} with the other active solutions than
	 * {@code solutionId}, each list ordered by solution name (case-insensitive), then by ID; an
	 * entity without conflicts has no key.
	 */
	public Map<Integer, List<Conflict>> of(UUID solutionId, Collection<Integer> entityIds) {
		Map<Integer, List<Conflict>> conflicts = new LinkedHashMap<>();
		if (entityIds.isEmpty()) {
			return conflicts;
		}
		for (DeltaStore.ConflictRow row : this.deltas.conflicts(solutionId, entityIds)) {
			conflicts.computeIfAbsent(row.entityId(), (entity) -> new ArrayList<>())
				.add(new Conflict(row.solutionId(), row.solutionName(), row.status(), row.ownerId(), row.ownerName(),
						SolutionDeltas.Type.of(row.deltaType())));
		}
		return conflicts;
	}

	/** The number of deltas of the solution with at least one conflict. */
	public long conflicted(UUID solutionId) {
		return this.deltas.conflicted(solutionId);
	}

}

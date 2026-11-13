package io.oea.app.solution;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The conflicts of a solution with other active solutions that change the same entities (design
 * D0 of us-044-conflict-warning; US-044, REQ-042): computed on each read, never stored. A solution
 * is active unless it is {@code implemented} or {@code archived}; a delta {@code new} has no entity
 * and never conflicts.
 */
@Service
public class SolutionConflicts {

	private static final String ACTIVE = " AND s.status NOT IN ('implemented', 'archived')";

	private final JdbcTemplate jdbc;

	SolutionConflicts(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
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
		this.jdbc.query("""
				SELECT d.entity_id, s.id, s.name, s.status, s.owner_id, p.name, d.delta_type
				FROM entity_deltas d
				JOIN solutions s ON s.id = d.solution_id
				JOIN persons p ON p.id = s.owner_id
				WHERE d.entity_id = ANY(?) AND d.solution_id <> ?""" + ACTIVE
				+ " ORDER BY d.entity_id, lower(s.name), s.id", (ResultSet rs) -> {
			conflicts.computeIfAbsent(rs.getInt(1), (entity) -> new ArrayList<>())
				.add(new Conflict(rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getLong(5),
						rs.getString(6), SolutionDeltas.Type.of(rs.getString(7))));
		}, entityIds.toArray(Integer[]::new), solutionId);
		return conflicts;
	}

	/** The number of deltas of the solution with at least one conflict. */
	public long conflicted(UUID solutionId) {
		Long count = this.jdbc.queryForObject("""
				SELECT count(*) FROM entity_deltas d
				WHERE d.solution_id = ? AND d.entity_id IS NOT NULL
				  AND EXISTS (SELECT 1 FROM entity_deltas o JOIN solutions s ON s.id = o.solution_id
				              WHERE o.entity_id = d.entity_id AND o.solution_id <> d.solution_id""" + ACTIVE
				+ ")", Long.class, solutionId);
		return (count != null) ? count : 0;
	}

}

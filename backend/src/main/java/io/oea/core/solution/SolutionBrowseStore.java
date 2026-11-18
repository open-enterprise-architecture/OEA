package io.oea.core.solution;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads of the solution browser (design D2 of us-132-solution-browser; ADR-051: SQL only in
 * core): the active solutions with their number of deltas from one grouped query, whether a
 * solution exists, and a page of the deltas of one solution. Active means a status other than
 * {@code implemented} and {@code archived}.
 */
@Repository
public class SolutionBrowseStore {

	private final JdbcTemplate jdbc;

	public SolutionBrowseStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** An active solution with its target plateau ({@code null} in project mode) and its deltas. */
	public record ActiveSolution(UUID id, String name, String status, UUID toPlateauId, int deltaCount) {
	}

	/** A delta of a solution with the name and metatype abbreviation it shows. */
	public record DeltaRow(UUID id, String name, String abbreviation, Integer entityId, String deltaType) {
	}

	/** The active solutions by name regardless of case, then by ID, each with its number of deltas. */
	public List<ActiveSolution> activeSolutions() {
		return this.jdbc.query("""
				SELECT s.id, s.name, s.status, s.to_plateau_id, count(d.id)
				FROM solutions s LEFT JOIN entity_deltas d ON d.solution_id = s.id
				WHERE s.status NOT IN ('implemented', 'archived')
				GROUP BY s.id, s.name, s.status, s.to_plateau_id
				ORDER BY lower(s.name), s.id""",
				(rs, row) -> new ActiveSolution(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
						rs.getObject(4, UUID.class), rs.getInt(5)));
	}

	/** Whether the solution exists, whatever its status. */
	public boolean exists(UUID id) {
		return Boolean.TRUE.equals(
				this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM solutions WHERE id = ?)", Boolean.class, id));
	}

	/** The number of deltas of the solution. */
	public int countDeltas(UUID solutionId) {
		return this.jdbc.queryForObject("SELECT count(*) FROM entity_deltas WHERE solution_id = ?", Integer.class,
				solutionId);
	}

	/**
	 * One page of the deltas by shown name regardless of case, then by ID. A delta {@code new}
	 * shows its own name and metatype, also after the go-live set its entity; any other delta shows
	 * those of its entity, also when that entity was deleted meanwhile.
	 */
	public List<DeltaRow> pageDeltas(UUID solutionId, int limit, long offset) {
		return this.jdbc.query("""
				SELECT id, name, abbreviation, entity_id, delta_type FROM (
				    SELECT d.id, CASE WHEN d.delta_type = 'new' THEN d.name ELSE e.name END AS name,
				           m.abbreviation, d.entity_id, d.delta_type
				    FROM entity_deltas d
				    LEFT JOIN entities e ON e.id = d.entity_id
				    LEFT JOIN metatypes m ON m.id = CASE WHEN d.delta_type = 'new' THEN d.metatype_id ELSE e.metatype_id END
				    WHERE d.solution_id = ?) shown
				ORDER BY lower(name), id LIMIT ? OFFSET ?""",
				(rs, row) -> new DeltaRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
						rs.getObject(4, Integer.class), rs.getString(5)),
				solutionId, limit, offset);
	}

}

package io.oea.core.solution;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The entity deltas of solutions (US-040 to US-044, REQ-040): writes, the page of the delta list
 * and the conflicts with other active solutions. A duplicate delta surfaces as a Spring
 * {@code DuplicateKeyException}. The values of a delta are in {@link DeltaValueStore}. Callers run
 * in a transaction.
 */
@Repository
public class DeltaStore {

	private static final String RANK = "CASE d.delta_type WHEN 'new' THEN 0 WHEN 'modified' THEN 1 ELSE 2 END";

	private static final String SORT_NAME = "lower(coalesce(d.name, e.name))";

	private static final String ACTIVE = " AND s.status NOT IN ('implemented', 'archived')";

	private final JdbcTemplate jdbc;

	public DeltaStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The type and entity of a locked delta; the entity is {@code null} for a delta {@code new}. */
	public record Locked(String type, Integer entityId) {
	}

	/** A delta with the current name and metatype of its entity, without values and conflicts. */
	public record DeltaRow(UUID id, String type, Integer entityId, String name, UUID metatypeId, String metatypeName,
			String description, Instant createdAt, Instant updatedAt) {
	}

	/** Another active solution that holds a delta for the same entity, with its owner. */
	public record ConflictRow(int entityId, UUID solutionId, String solutionName, String status, long ownerId,
			String ownerName, String deltaType) {
	}

	/**
	 * Inserts a delta.
	 *
	 * @throws org.springframework.dao.DuplicateKeyException if the solution holds a delta for the
	 * entity or the name already
	 */
	public void insert(UUID id, UUID solutionId, String type, Integer entityId, UUID metatypeId, String name,
			String description, long personId) {
		this.jdbc.update("""
				INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, metatype_id, name, description, created_by)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", id, solutionId, type, entityId, metatypeId, name, description,
				personId);
	}

	/** Locks the delta of the solution against concurrent edits; empty if there is none. */
	public Optional<Locked> lock(UUID deltaId, UUID solutionId) {
		return this.jdbc
			.query("SELECT delta_type, entity_id FROM entity_deltas WHERE id = ? AND solution_id = ? FOR UPDATE",
					(rs, n) -> new Locked(rs.getString(1), rs.getObject(2, Integer.class)), deltaId, solutionId)
			.stream()
			.findFirst();
	}

	/** Records the edit of the delta. */
	public void touch(UUID deltaId, long personId) {
		this.jdbc.update("UPDATE entity_deltas SET updated_at = now(), updated_by = ? WHERE id = ?", personId,
				deltaId);
	}

	/** Removes the delta with its values. */
	public void delete(UUID deltaId, UUID solutionId) {
		this.jdbc.update("DELETE FROM entity_deltas WHERE id = ? AND solution_id = ?", deltaId, solutionId);
	}

	/** The number of deltas per type; a type without deltas has no key. */
	public Map<String, Long> countsByType(UUID solutionId) {
		Map<String, Long> counts = new HashMap<>();
		this.jdbc.query("SELECT delta_type, count(*) FROM entity_deltas WHERE solution_id = ? GROUP BY delta_type",
				(rs) -> {
					counts.put(rs.getString(1), rs.getLong(2));
				}, solutionId);
		return counts;
	}

	/**
	 * The deltas of the solution, {@code new}, then {@code modified}, then {@code retiring}, each by
	 * name regardless of case, then by ID.
	 *
	 * @param deltaId only this delta, or {@code null} for all
	 * @param after the keyset of the last delta of the previous page (rank, name, ID as text), or
	 * {@code null} for the first page or an offset page
	 * @param limit the page size
	 * @param offset the offset, used without {@code after}
	 */
	public List<DeltaRow> page(UUID solutionId, UUID deltaId, List<Object> after, int limit, long offset) {
		StringBuilder where = new StringBuilder();
		List<Object> arguments = new java.util.ArrayList<>();
		arguments.add(solutionId);
		if (deltaId != null) {
			where.append(" AND d.id = ?");
			arguments.add(deltaId);
		}
		if (after != null) {
			where.append(" AND (" + RANK + ", " + SORT_NAME + ", d.id::text) > (?, lower(?), ?)");
			arguments.addAll(after);
		}
		String paging = "";
		if (deltaId == null) {
			paging = " LIMIT " + limit + ((after != null) ? "" : " OFFSET " + offset);
		}
		return this.jdbc.query("""
				SELECT d.id, d.delta_type, d.entity_id, coalesce(d.name, e.name), m.id, m.name, d.description, d.created_at,
				       d.updated_at
				FROM entity_deltas d
				LEFT JOIN entities e ON e.id = d.entity_id
				JOIN metatypes m ON m.id = coalesce(d.metatype_id, e.metatype_id)
				WHERE d.solution_id = ?""" + where + " ORDER BY " + RANK + ", " + SORT_NAME + ", d.id::text" + paging,
				(rs, n) -> {
					java.sql.Timestamp updated = rs.getTimestamp(9);
					return new DeltaRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, Integer.class),
							rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
							rs.getTimestamp(8).toInstant(), (updated != null) ? updated.toInstant() : null);
				}, arguments.toArray());
	}

	/**
	 * The deltas of other active solutions than {@code solutionId} for these entities, per entity
	 * ordered by solution name regardless of case, then by solution ID.
	 */
	public List<ConflictRow> conflicts(UUID solutionId, Collection<Integer> entityIds) {
		if (entityIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.query("""
				SELECT d.entity_id, s.id, s.name, s.status, s.owner_id, p.name, d.delta_type
				FROM entity_deltas d
				JOIN solutions s ON s.id = d.solution_id
				JOIN persons p ON p.id = s.owner_id
				WHERE d.entity_id = ANY(?) AND d.solution_id <> ?""" + ACTIVE
				+ " ORDER BY d.entity_id, lower(s.name), s.id", (rs, n) -> new ConflictRow(rs.getInt(1),
						rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6),
						rs.getString(7)),
				entityIds.toArray(Integer[]::new), solutionId);
	}

	/** The number of deltas of the solution with at least one conflict. */
	public long conflicted(UUID solutionId) {
		Long count = this.jdbc.queryForObject("""
				SELECT count(*) FROM entity_deltas d
				WHERE d.solution_id = ? AND d.entity_id IS NOT NULL
				  AND EXISTS (SELECT 1 FROM entity_deltas o JOIN solutions s ON s.id = o.solution_id
				              WHERE o.entity_id = d.entity_id AND o.solution_id <> d.solution_id""" + ACTIVE + ")",
				Long.class, solutionId);
		return (count != null) ? count : 0;
	}

}

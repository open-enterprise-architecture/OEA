package io.oea.core.diagram;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
 * Diagrams (V46, ADR-051: all SQL of the feature lives here). A deleted diagram keeps its row with
 * {@code deleted_at} and {@code deleted_by} and is restorable; the names are unique among the
 * diagrams that are not deleted, compared on {@code lower(name)}: an insert, update or restore with
 * a taken name fails with a {@link org.springframework.dao.DuplicateKeyException}.
 */
@Repository
public class DiagramStore {

	private static final String SELECT = """
			SELECT d.id, d.name, d.description, d.viewpoint_id, v.name, v.notation, d.version, d.created_at,
			       d.updated_at, d.created_by, cp.name, cp.person_type, d.deleted_at, d.deleted_by, dp.name,
			       dp.person_type, lower(d.name)
			FROM diagrams d
			JOIN viewpoints v ON v.id = d.viewpoint_id
			JOIN persons cp ON cp.id = d.created_by
			LEFT JOIN persons dp ON dp.id = d.deleted_by""";

	private final JdbcTemplate jdbc;

	public DiagramStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A person or service account behind a diagram or its deletion. */
	public record Actor(long id, String name, boolean serviceAccount) {
	}

	/** A diagram with its viewpoint; {@code deletedAt} and {@code deletedBy} are {@code null} while it is active. */
	public record Row(UUID id, String name, String description, UUID viewpointId, String viewpointName,
			String viewpointNotation, int version, Instant createdAt, Instant updatedAt, Actor createdBy,
			Instant deletedAt, Actor deletedBy, String sortName) {
	}

	/** A diagram of a viewpoint, for its removal. */
	public record Removed(UUID id, String name, boolean deleted) {
	}

	/**
	 * A page query: {@code deleted} selects the deleted diagrams; {@code like} is an escaped
	 * pattern or {@code null}; {@code afterSortName} and {@code afterId} (both set or both
	 * {@code null}) start after that row instead of at {@code offset}.
	 */
	public record Query(boolean deleted, String like, String afterSortName, UUID afterId, int limit, long offset) {
	}

	/** Whether the viewpoint exists. */
	public boolean viewpointExists(UUID viewpointId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM viewpoints WHERE id = ?)",
				Boolean.class, viewpointId));
	}

	/** Stores a diagram at version 1 and returns its ID. */
	public UUID insert(String name, String description, UUID viewpointId, long personId, Instant now) {
		Timestamp at = Timestamp.from(now);
		return this.jdbc.queryForObject("""
				INSERT INTO diagrams (name, description, viewpoint_id, created_at, created_by, updated_at, updated_by)
				VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""", UUID.class, name, description, viewpointId, at, personId,
				at, personId);
	}

	/** The diagram, deleted or not. */
	public Optional<Row> find(UUID id) {
		return this.jdbc.query(SELECT + " WHERE d.id = ?", DiagramStore::row, id).stream().findFirst();
	}

	/** The diagram as {@link #find}, locked until the transaction ends. */
	public Optional<Row> findForUpdate(UUID id) {
		return this.jdbc.query(SELECT + " WHERE d.id = ? FOR UPDATE OF d", DiagramStore::row, id)
			.stream()
			.findFirst();
	}

	/** Changes name and description and raises the version. */
	public void update(UUID id, String name, String description, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE diagrams SET name = ?, description = ?, version = version + 1, updated_at = ?, updated_by = ?
				WHERE id = ?""", name, description, Timestamp.from(now), personId, id);
	}

	/** Raises the version without another change (a saved layout) and returns the new version. */
	public int touch(UUID id, long personId, Instant now) {
		return this.jdbc.queryForObject("""
				UPDATE diagrams SET version = version + 1, updated_at = ?, updated_by = ? WHERE id = ?
				RETURNING version""", Integer.class, Timestamp.from(now), personId, id);
	}

	/** Sets another viewpoint and raises the version; fails with a foreign key violation for an unknown viewpoint. */
	public void changeViewpoint(UUID id, UUID viewpointId, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE diagrams SET viewpoint_id = ?, version = version + 1, updated_at = ?, updated_by = ?
				WHERE id = ?""", viewpointId, Timestamp.from(now), personId, id);
	}

	/**
	 * The number of shown shapes and edges of the diagram whose exact metatype its viewpoint does not
	 * allow (design D2 of us-074-diagram-viewpoint-change); shapes and edges of deleted entities are
	 * hidden and do not count.
	 */
	public int conflictCount(UUID diagramId) {
		return this.jdbc.queryForObject("""
				SELECT
				  (SELECT count(*) FROM diagram_shapes s JOIN entities e ON e.id = s.entity_id
				   WHERE s.diagram_id = d.id AND e.deleted_at IS NULL
				     AND NOT EXISTS (SELECT 1 FROM viewpoint_metatypes vm
				                     WHERE vm.viewpoint_id = d.viewpoint_id AND vm.metatype_id = e.metatype_id))
				  +
				  (SELECT count(*) FROM diagram_edges ed JOIN entities c ON c.id = ed.connection_id
				   JOIN diagram_shapes ss ON ss.id = ed.source_shape_id JOIN entities se ON se.id = ss.entity_id
				   JOIN diagram_shapes ts ON ts.id = ed.target_shape_id JOIN entities te ON te.id = ts.entity_id
				   WHERE ed.diagram_id = d.id AND c.deleted_at IS NULL AND se.deleted_at IS NULL
				     AND te.deleted_at IS NULL
				     AND NOT EXISTS (SELECT 1 FROM viewpoint_connection_types vc
				                     WHERE vc.viewpoint_id = d.viewpoint_id AND vc.metatype_id = c.metatype_id))
				FROM diagrams d WHERE d.id = ?""", Integer.class, diagramId);
	}

	/**
	 * The number of diagrams of the viewpoint that are not deleted and have a shown shape whose
	 * metatype is not in {@code metatypeIds} or a shown edge whose metatype is not in
	 * {@code connectionTypeIds} (design D4 of us-074-diagram-viewpoint-change).
	 */
	public int countAffected(UUID viewpointId, Collection<UUID> metatypeIds, Collection<UUID> connectionTypeIds) {
		return this.jdbc.queryForObject("""
				SELECT count(*) FROM diagrams d
				WHERE d.viewpoint_id = ? AND d.deleted_at IS NULL
				  AND (EXISTS (SELECT 1 FROM diagram_shapes s JOIN entities e ON e.id = s.entity_id
				               WHERE s.diagram_id = d.id AND e.deleted_at IS NULL
				                 AND e.metatype_id <> ALL (?::uuid[]))
				    OR EXISTS (SELECT 1 FROM diagram_edges ed JOIN entities c ON c.id = ed.connection_id
				               JOIN diagram_shapes ss ON ss.id = ed.source_shape_id
				               JOIN entities se ON se.id = ss.entity_id
				               JOIN diagram_shapes ts ON ts.id = ed.target_shape_id
				               JOIN entities te ON te.id = ts.entity_id
				               WHERE ed.diagram_id = d.id AND c.deleted_at IS NULL AND se.deleted_at IS NULL
				                 AND te.deleted_at IS NULL AND c.metatype_id <> ALL (?::uuid[])))""", Integer.class,
				viewpointId, metatypeIds.stream().map(UUID::toString).toArray(String[]::new),
				connectionTypeIds.stream().map(UUID::toString).toArray(String[]::new));
	}

	/** Marks the diagram as deleted and raises the version. */
	public void markDeleted(UUID id, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE diagrams SET deleted_at = ?, deleted_by = ?, version = version + 1, updated_at = ?, updated_by = ?
				WHERE id = ?""", Timestamp.from(now), personId, Timestamp.from(now), personId, id);
	}

	/** Clears the deletion and raises the version. */
	public void restore(UUID id, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE diagrams SET deleted_at = NULL, deleted_by = NULL, version = version + 1, updated_at = ?,
				    updated_by = ? WHERE id = ?""", Timestamp.from(now), personId, id);
	}

	/** One page of the active or of the deleted diagrams, by {@code lower(name)}, then ID. */
	public List<Row> page(Query query) {
		StringBuilder sql = new StringBuilder(SELECT).append(" WHERE ").append(filter(query));
		List<Object> args = filterArgs(query);
		if (query.afterId() != null) {
			sql.append(" AND (lower(d.name), d.id) > (?, ?)");
			args.add(query.afterSortName());
			args.add(query.afterId());
		}
		sql.append(" ORDER BY lower(d.name), d.id LIMIT ?");
		args.add(query.limit());
		if (query.afterId() == null) {
			sql.append(" OFFSET ?");
			args.add(query.offset());
		}
		return this.jdbc.query(sql.toString(), DiagramStore::row, args.toArray());
	}

	/** The number of the diagrams the query matches, without its position. */
	public long count(Query query) {
		return this.jdbc.queryForObject("SELECT count(*) FROM diagrams d WHERE " + filter(query), Long.class,
				filterArgs(query).toArray());
	}

	/** The number of diagrams that are not deleted. */
	public int countActive() {
		return this.jdbc.queryForObject("SELECT count(*) FROM diagrams WHERE deleted_at IS NULL", Integer.class);
	}

	/** Whether a diagram that is not deleted has the ID. */
	public boolean existsActive(UUID id) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM diagrams WHERE id = ? AND deleted_at IS NULL)", Boolean.class, id));
	}

	/** The number of diagrams that are not deleted, per viewpoint with at least one. */
	public Map<UUID, Integer> activeCounts() {
		Map<UUID, Integer> counts = new HashMap<>();
		this.jdbc.query("""
				SELECT viewpoint_id, count(*) FROM diagrams WHERE deleted_at IS NULL GROUP BY viewpoint_id""",
				(rs) -> {
					counts.put(rs.getObject(1, UUID.class), rs.getInt(2));
				});
		return counts;
	}

	/** The diagrams of the viewpoint, deleted ones included, by name then ID, locked until the transaction ends. */
	public List<Removed> ofViewpointForUpdate(UUID viewpointId) {
		return this.jdbc.query("""
				SELECT id, name, deleted_at IS NOT NULL FROM diagrams WHERE viewpoint_id = ?
				ORDER BY lower(name), id FOR UPDATE""",
				(rs, rowNum) -> new Removed(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3)),
				viewpointId);
	}

	/** Removes all diagrams of the viewpoint for good and returns how many. */
	public int deleteOfViewpoint(UUID viewpointId) {
		return this.jdbc.update("DELETE FROM diagrams WHERE viewpoint_id = ?", viewpointId);
	}

	private static String filter(Query query) {
		return (query.deleted() ? "d.deleted_at IS NOT NULL" : "d.deleted_at IS NULL")
				+ ((query.like() != null) ? " AND d.name ILIKE ? ESCAPE '\\'" : "");
	}

	private static List<Object> filterArgs(Query query) {
		List<Object> args = new java.util.ArrayList<>();
		if (query.like() != null) {
			args.add(query.like());
		}
		return args;
	}

	private static Row row(ResultSet rs, int rowNum) throws SQLException {
		Timestamp deletedAt = rs.getTimestamp(13);
		return new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class),
				rs.getString(5), rs.getString(6), rs.getInt(7), rs.getTimestamp(8).toInstant(),
				rs.getTimestamp(9).toInstant(),
				new Actor(rs.getLong(10), rs.getString(11), "service-account".equals(rs.getString(12))),
				(deletedAt != null) ? deletedAt.toInstant() : null,
				(deletedAt != null) ? new Actor(rs.getLong(14), rs.getString(15), "service-account".equals(rs.getString(16)))
						: null,
				rs.getString(17));
	}

}

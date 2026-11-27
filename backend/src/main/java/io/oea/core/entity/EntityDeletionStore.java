package io.oea.core.entity;

import io.oea.core.access.PersonLinkTrigger;
import io.oea.core.metamodel.BuiltInMetatypes;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The queries of the deletion cascade and its restore (design D2, D3 of s17-entity-delete; V49):
 * the children on all levels, the connections of a set, the rows of one cascade and the set-based
 * delete and undelete. Callers run in a transaction.
 */
@Repository
public class EntityDeletionStore {

	/** The recursion stops here; the hierarchy allows 10 levels, so a deeper chain cannot occur. */
	private static final int RECURSION_LIMIT = 64;

	private static final String ROWS = """
			SELECT e.id, e.metatype_id, e.name, e.version, e.parent_entity_id, m.is_connection, e.auto_created,
			       e.metatype_id = ?, e.deleted_at, e.deleted_with_entity_id, c.source_entity_id, c.target_entity_id
			FROM entities e JOIN metatypes m ON m.id = e.metatype_id
			LEFT JOIN entity_connections c ON c.entity_id = e.id""";

	private final JdbcTemplate jdbc;

	private final PersonLinkTrigger links;

	public EntityDeletionStore(JdbcTemplate jdbc) {
		this(jdbc, PersonLinkTrigger.NONE);
	}

	@Autowired
	public EntityDeletionStore(JdbcTemplate jdbc, PersonLinkTrigger links) {
		this.jdbc = jdbc;
		this.links = links;
	}

	/**
	 * An entity with what the deletion and its restore need: whether it is a connection (with its
	 * endpoints, {@code null} otherwise), an automatic composition connection or a structuring
	 * aid, and its deletion time and cascade origin ({@code null} when active or deleted on its own).
	 */
	public record Row(int id, UUID metatypeId, String name, int version, Integer parentEntityId, boolean connection,
			boolean autoCreated, boolean structuringAid, Instant deletedAt, Integer deletedWithEntityId,
			Integer sourceId, Integer targetId) {

		public boolean deleted() {
			return this.deletedAt != null;
		}

	}

	/** The entity, without a lock. */
	public Optional<Row> find(int id) {
		return this.jdbc.query(ROWS + " WHERE e.id = ?", EntityDeletionStore::row, BuiltInMetatypes.STRUCTURING_AID, id)
			.stream()
			.findFirst();
	}

	/** The rows with these IDs, in ID order. */
	public List<Row> findAll(Collection<Integer> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return this.jdbc.query(ROWS + " WHERE e.id = ANY(?) ORDER BY e.id", EntityDeletionStore::row,
				BuiltInMetatypes.STRUCTURING_AID, (Object) ids.toArray(Integer[]::new));
	}

	/**
	 * The IDs of the children of the entity on all levels that are not deleted, by level then ID.
	 * A deleted child ends the chain: what hangs below it is not part of the deletion.
	 */
	public List<Integer> activeDescendants(int entityId) {
		return this.jdbc.queryForList("""
				WITH RECURSIVE down(id, depth) AS (
				    SELECT id, 1 FROM entities WHERE parent_entity_id = ? AND deleted_at IS NULL
				    UNION ALL
				    SELECT e.id, down.depth + 1 FROM entities e JOIN down ON e.parent_entity_id = down.id
				    WHERE e.deleted_at IS NULL AND down.depth < ?)
				SELECT id FROM down ORDER BY depth, id""", Integer.class, entityId, RECURSION_LIMIT);
	}

	/**
	 * The IDs of the connections that are not deleted and have one of these entities as source or
	 * target, without the entities themselves, by ID.
	 */
	public List<Integer> activeConnectionsOf(Collection<Integer> entityIds) {
		if (entityIds.isEmpty()) {
			return List.of();
		}
		Object ids = entityIds.toArray(Integer[]::new);
		return this.jdbc.queryForList("""
				SELECT DISTINCT c.entity_id FROM entity_connections c JOIN entities e ON e.id = c.entity_id
				WHERE (c.source_entity_id = ANY(?) OR c.target_entity_id = ANY(?)) AND e.deleted_at IS NULL
				AND NOT (c.entity_id = ANY(?))
				ORDER BY c.entity_id""", Integer.class, ids, ids, ids);
	}

	/**
	 * The metatypes of the entities, as the scoping check of connections needs them: the distinct
	 * metatype IDs of the connections among these IDs.
	 */
	public List<UUID> connectionMetatypes(Collection<Integer> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return this.jdbc.queryForList("""
				SELECT DISTINCT e.metatype_id FROM entities e JOIN metatypes m ON m.id = e.metatype_id
				WHERE e.id = ANY(?) AND m.is_connection""", UUID.class, (Object) ids.toArray(Integer[]::new));
	}

	/**
	 * The rows deleted with the same cascade as the entity: the same origin and the same deletion
	 * time, by ID. Empty when the entity has no origin and is not an origin itself.
	 */
	public List<Row> cascadeOf(int originId, int sameTimeAsEntityId) {
		return this.jdbc.query(ROWS + """
				 WHERE e.deleted_with_entity_id = ?
				 AND e.deleted_at = (SELECT deleted_at FROM entities WHERE id = ?)
				ORDER BY e.id""", EntityDeletionStore::row, BuiltInMetatypes.STRUCTURING_AID, originId,
				sameTimeAsEntityId);
	}

	/**
	 * Soft-deletes the entities in one statement: one time, actor and reason, and the entity whose
	 * deletion takes them along as origin ({@code null} for the entity itself).
	 */
	public void softDeleteAll(Collection<Integer> ids, long personId, Instant now, String reason, Integer originId) {
		if (ids.isEmpty()) {
			return;
		}
		this.links.changed();
		this.jdbc.update("""
				UPDATE entities SET deleted_at = ?, deleted_by = ?, deletion_reason = ?, deleted_with_entity_id = ?
				WHERE id = ANY(?) AND deleted_at IS NULL""", Timestamp.from(now), personId, reason, originId,
				(Object) ids.toArray(Integer[]::new));
	}

	/** Undeletes the entities in one statement; versions and history stay as they are. */
	public void restoreAll(Collection<Integer> ids) {
		if (ids.isEmpty()) {
			return;
		}
		this.links.changed();
		this.jdbc.update("""
				UPDATE entities SET deleted_at = NULL, deleted_by = NULL, deletion_reason = NULL,
				    deleted_with_entity_id = NULL
				WHERE id = ANY(?) AND deleted_at IS NOT NULL""", (Object) ids.toArray(Integer[]::new));
	}

	private static Row row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		Timestamp deletedAt = rs.getTimestamp(9);
		return new Row(rs.getInt(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4),
				rs.getObject(5, Integer.class), rs.getBoolean(6), rs.getBoolean(7), rs.getBoolean(8),
				(deletedAt != null) ? deletedAt.toInstant() : null, rs.getObject(10, Integer.class),
				rs.getObject(11, Integer.class), rs.getObject(12, Integer.class));
	}

}

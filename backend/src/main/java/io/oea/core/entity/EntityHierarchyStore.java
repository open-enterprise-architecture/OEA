package io.oea.core.entity;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The queries of the component hierarchy (design D3 of us-135-component-hierarchy): the lock that
 * serializes parent changes, the level of a parent, the depth of a subtree and the connections the
 * parent change created. Callers run in a transaction.
 */
@Repository
public class EntityHierarchyStore {

	/** Key of the transaction-scoped advisory lock that serializes parent changes. */
	private static final long LOCK_KEY = 0x0EA5_4C1E_A0C1L;

	/** The recursion stops here; the service allows 10 levels, so a deeper chain cannot occur. */
	private static final int RECURSION_LIMIT = 64;

	private final JdbcTemplate jdbc;

	public EntityHierarchyStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The parent's place in the hierarchy: its level (the root level is 1), and whether the entity is an ancestor. */
	public record Ancestry(int level, boolean ancestor) {
	}

	/**
	 * Takes the advisory lock for the rest of the transaction, so that two concurrent parent
	 * changes cannot create a cycle together.
	 */
	public void lock() {
		this.jdbc.query("SELECT pg_advisory_xact_lock(?)", (rs) -> null, LOCK_KEY);
	}

	/**
	 * The level of the entity {@code parentId} (the root level is 1; an ancestor that is deleted
	 * ends the chain, as it does in the browser) and whether {@code childId} is among its
	 * ancestors or the entity itself.
	 */
	public Ancestry ancestry(int parentId, Integer childId) {
		return this.jdbc.queryForObject("""
				WITH RECURSIVE up(id, parent, depth) AS (
				    SELECT id, parent_entity_id, 1 FROM entities WHERE id = ?
				    UNION ALL
				    SELECT e.id, e.parent_entity_id, up.depth + 1 FROM entities e JOIN up ON e.id = up.parent
				    WHERE e.deleted_at IS NULL AND up.depth < ?)
				SELECT max(depth), coalesce(bool_or(id = ?), false) FROM up""",
				(rs, n) -> new Ancestry(rs.getInt(1), rs.getBoolean(2)), parentId, RECURSION_LIMIT, childId);
	}

	/** The number of levels of the subtree of the entity, the entity included, over children that are not deleted. */
	public int subtreeLevels(int entityId) {
		return this.jdbc.queryForObject("""
				WITH RECURSIVE down(id, depth) AS (
				    SELECT id, 1 FROM entities WHERE id = ?
				    UNION ALL
				    SELECT e.id, down.depth + 1 FROM entities e JOIN down ON e.parent_entity_id = down.id
				    WHERE e.deleted_at IS NULL AND down.depth < ?)
				SELECT max(depth) FROM down""", Integer.class, entityId, RECURSION_LIMIT);
	}

	/** The connections from parent to child that were created automatically and are not deleted, by ID. */
	public List<Integer> autoCreatedConnections(int parentId, int childId) {
		return this.jdbc.queryForList("""
				SELECT e.id FROM entity_connections c JOIN entities e ON e.id = c.entity_id
				WHERE c.source_entity_id = ? AND c.target_entity_id = ? AND e.auto_created AND e.deleted_at IS NULL
				ORDER BY e.id""", Integer.class, parentId, childId);
	}

}

package io.oea.core.navigation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Folders ({@code tree_nodes}) and their entries ({@code tree_node_items}) of the navigation tree
 * (V21, V40). A taken folder name fails with a {@link org.springframework.dao.DuplicateKeyException}
 * on {@link #NAME_INDEX}, a second entry of the same target in a folder on {@link #ITEM_INDEX} or
 * {@link #ENTITY_ITEM_INDEX}. Folders and entries carry a dense manual order per parent and per
 * folder (0…n-1); new ones are appended.
 */
@Repository
public class NavigationTreeStore {

	/** Unique index of the folder names among siblings, regardless of case. */
	public static final String NAME_INDEX = "tree_nodes_sibling_name";

	/** Unique index of a catalog or solution per folder. */
	public static final String ITEM_INDEX = "tree_node_items_once_per_folder";

	/** Unique index of an entity per folder. */
	public static final String ENTITY_ITEM_INDEX = "tree_node_items_entity_once_per_folder";

	/** Item type of a catalog entry. */
	public static final String CATALOG = "catalog";

	/** Item type of an entity entry. */
	public static final String ENTITY = "entity";

	/** Item type of a solution entry. */
	public static final String SOLUTION = "solution";

	/** Item type of a diagram entry. */
	public static final String DIAGRAM = "diagram";

	/**
	 * The entries a person sees: catalogs that exist and are instance catalogs or the person's own
	 * personal catalogs, entities that exist (deleted ones included) and solutions that exist.
	 */
	private static final String VISIBLE_ITEMS = """
			SELECT i.id, i.tree_node_id, i.item_type, i.reference_id, i.entity_id, i.display_label, i.sort_order,
			       coalesce(c.name, e.name, s.name, g.name), c.scope, e.deleted_at IS NOT NULL
			FROM tree_node_items i
			LEFT JOIN catalogs c ON i.item_type = 'catalog' AND c.id = i.reference_id
			LEFT JOIN entities e ON i.item_type = 'entity' AND e.id = i.entity_id
			LEFT JOIN solutions s ON i.item_type = 'solution' AND s.id = i.reference_id
			LEFT JOIN diagrams g ON i.item_type = 'diagram' AND g.id = i.reference_id AND g.deleted_at IS NULL
			WHERE ((i.item_type = 'catalog' AND (c.scope = 'instance' OR c.created_by = ?))
			    OR (i.item_type = 'entity' AND e.id IS NOT NULL)
			    OR (i.item_type = 'solution' AND s.id IS NOT NULL)
			    OR (i.item_type = 'diagram' AND g.id IS NOT NULL))""";

	private final JdbcTemplate jdbc;

	public NavigationTreeStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A folder; {@code parentId} is {@code null} for the root. */
	public record StoredNode(UUID id, String name, UUID parentId, int sortOrder) {
	}

	/**
	 * An entry the person sees, with the current name of its target; {@code referenceId} is set
	 * for a catalog or solution, {@code entityId} for an entity. {@code scope} is the scope of a
	 * catalog, else {@code null}; {@code deleted} is true for an entry of a deleted entity.
	 * {@code displayLabel} may be {@code null}.
	 */
	public record StoredVisibleItem(UUID id, UUID nodeId, String itemType, UUID referenceId, Integer entityId,
			String displayLabel, int sortOrder, String name, String scope, boolean deleted) {
	}

	/** An entry of any type, as stored. */
	public record StoredItem(UUID id, UUID nodeId, String itemType, UUID referenceId, Integer entityId) {
	}

	/** A folder of a subtree, with its level below the subtree's top folder (0 for the folder itself). */
	public record StoredDescendant(UUID id, int level) {
	}

	/** The number of folders and entries of a subtree, the folder itself included. */
	public record SubtreeSize(int folders, int items) {
	}

	/** Locks the root until the transaction ends, so that writes to the tree run one after another. */
	public void lock() {
		this.jdbc.queryForList("SELECT id FROM tree_nodes WHERE parent_id IS NULL FOR UPDATE", UUID.class);
	}

	/** All folders. */
	public List<StoredNode> nodes() {
		return this.jdbc.query("SELECT id, name, parent_id, sort_order FROM tree_nodes",
				(rs, rowNum) -> new StoredNode(rs.getObject(1, UUID.class), rs.getString(2),
						rs.getObject(3, UUID.class), rs.getInt(4)));
	}

	/** The entries the person sees (see {@link #VISIBLE_ITEMS}), in their manual order per folder. */
	public List<StoredVisibleItem> items(long personId) {
		return this.jdbc.query(VISIBLE_ITEMS + " ORDER BY i.tree_node_id, i.sort_order, i.id",
				(rs, rowNum) -> visibleItem(rs), personId);
	}

	/** The entry if it belongs to the folder and the person sees it. */
	public Optional<StoredVisibleItem> findVisibleItem(UUID nodeId, UUID itemId, long personId) {
		return this.jdbc
			.query(VISIBLE_ITEMS + " AND i.id = ? AND i.tree_node_id = ?", (rs, rowNum) -> visibleItem(rs), personId,
					itemId, nodeId)
			.stream()
			.findFirst();
	}

	/** The IDs of all entries of the folder, whatever their visibility, in their manual order. */
	public List<UUID> itemOrder(UUID nodeId) {
		return this.jdbc.queryForList(
				"SELECT id FROM tree_node_items WHERE tree_node_id = ? ORDER BY sort_order, created_at, id", UUID.class,
				nodeId);
	}

	/** Stores a folder as the last child of the parent and returns its ID. */
	public UUID createNode(String name, UUID parentId, long personId, Instant now) {
		return this.jdbc.queryForObject("""
				INSERT INTO tree_nodes (name, parent_id, sort_order, created_by, created_at)
				VALUES (?, ?, (SELECT coalesce(max(sort_order) + 1, 0) FROM tree_nodes WHERE parent_id = ?), ?, ?)
				RETURNING id""", UUID.class, name, parentId, parentId, personId, Timestamp.from(now));
	}

	/** Renames the folder. */
	public void renameNode(UUID id, String name) {
		this.jdbc.update("UPDATE tree_nodes SET name = ? WHERE id = ?", name, id);
	}

	/**
	 * The folder and its descendants with their levels below it, followed at most
	 * {@code maxLevels} levels down, so that the query ends even on a corrupt tree.
	 */
	public List<StoredDescendant> descendants(UUID id, int maxLevels) {
		return this.jdbc.query("""
				WITH RECURSIVE subtree (id, level) AS (
				    SELECT id, 0 FROM tree_nodes WHERE id = ?
				    UNION ALL
				    SELECT n.id, s.level + 1 FROM tree_nodes n JOIN subtree s ON n.parent_id = s.id WHERE s.level < ?)
				SELECT id, level FROM subtree""",
				(rs, rowNum) -> new StoredDescendant(rs.getObject(1, UUID.class), rs.getInt(2)), id, maxLevels);
	}

	/**
	 * Moves the folder under the parent to the position among its new siblings (clamped to the
	 * end) and numbers the new siblings densely; then numbers the siblings of the old parent
	 * densely. A taken name among the new siblings fails on {@link #NAME_INDEX}.
	 */
	public void moveNode(UUID id, UUID oldParentId, UUID parentId, int position) {
		this.jdbc.update("""
				WITH others AS (
				    SELECT id, (row_number() OVER (ORDER BY sort_order, lower(name), id) - 1) * 2 AS sort_key
				    FROM tree_nodes WHERE parent_id = ? AND id <> ?),
				keyed AS (
				    SELECT id, sort_key FROM others
				    UNION ALL
				    SELECT ?::uuid, ?::bigint * 2 - 1),
				ranked AS (
				    SELECT id, row_number() OVER (ORDER BY sort_key) - 1 AS position FROM keyed)
				UPDATE tree_nodes t SET parent_id = ?, sort_order = r.position
				FROM ranked r WHERE t.id = r.id""", parentId, id, id, position, parentId);
		if (!oldParentId.equals(parentId)) {
			this.jdbc.update("""
					WITH ranked AS (
					    SELECT id, row_number() OVER (ORDER BY sort_order, lower(name), id) - 1 AS position
					    FROM tree_nodes WHERE parent_id = ?)
					UPDATE tree_nodes t SET sort_order = r.position
					FROM ranked r WHERE t.id = r.id AND t.sort_order <> r.position""", oldParentId);
		}
	}

	/** The number of folders and entries the deletion of the folder removes. */
	public SubtreeSize subtreeSize(UUID id) {
		return this.jdbc.queryForObject("""
				WITH RECURSIVE subtree AS (
				    SELECT id FROM tree_nodes WHERE id = ?
				    UNION ALL
				    SELECT n.id FROM tree_nodes n JOIN subtree s ON n.parent_id = s.id)
				SELECT (SELECT count(*) FROM subtree),
				       (SELECT count(*) FROM tree_node_items WHERE tree_node_id IN (SELECT id FROM subtree))""",
				(rs, rowNum) -> new SubtreeSize(rs.getInt(1), rs.getInt(2)), id);
	}

	/** Deletes the folder; the foreign keys delete its subfolders and all their entries. */
	public void deleteNode(UUID id) {
		this.jdbc.update("DELETE FROM tree_nodes WHERE id = ?", id);
	}

	/** The number of entries of the folder, whatever their visibility. */
	public int itemCount(UUID nodeId) {
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM tree_node_items WHERE tree_node_id = ?",
				Integer.class, nodeId);
		return (count != null) ? count : 0;
	}

	/**
	 * Stores an entry as the last of the folder and returns its ID; {@code referenceId} for a
	 * catalog or solution, {@code entityId} for an entity.
	 */
	public UUID addItem(UUID nodeId, String itemType, UUID referenceId, Integer entityId, String displayLabel,
			long personId, Instant now) {
		return this.jdbc.queryForObject("""
				INSERT INTO tree_node_items (tree_node_id, item_type, reference_id, entity_id, display_label, sort_order,
				                             created_by, created_at)
				VALUES (?, ?, ?, ?, ?, (SELECT coalesce(max(sort_order) + 1, 0) FROM tree_node_items WHERE tree_node_id = ?),
				        ?, ?)
				RETURNING id""", UUID.class, nodeId, itemType, referenceId, entityId, displayLabel, nodeId, personId,
				Timestamp.from(now));
	}

	/** The entry if it belongs to the folder, whatever its visibility. */
	public Optional<StoredItem> findItem(UUID nodeId, UUID itemId) {
		return this.jdbc.query("""
				SELECT id, tree_node_id, item_type, reference_id, entity_id FROM tree_node_items
				WHERE id = ? AND tree_node_id = ?""",
				(rs, rowNum) -> new StoredItem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
						rs.getString(3), rs.getObject(4, UUID.class), (Integer) rs.getObject(5)),
				itemId, nodeId)
			.stream()
			.findFirst();
	}

	/**
	 * Moves the entry to the folder at the absolute position among all its entries (clamped to the
	 * end) and numbers the entries of the folder densely; then numbers the entries of the old
	 * folder densely. A target already in the folder fails on {@link #ITEM_INDEX} or
	 * {@link #ENTITY_ITEM_INDEX}.
	 */
	public void moveItem(UUID itemId, UUID oldNodeId, UUID nodeId, int position) {
		this.jdbc.update("""
				WITH others AS (
				    SELECT id, (row_number() OVER (ORDER BY sort_order, created_at, id) - 1) * 2 AS sort_key
				    FROM tree_node_items WHERE tree_node_id = ? AND id <> ?),
				keyed AS (
				    SELECT id, sort_key FROM others
				    UNION ALL
				    SELECT ?::uuid, ?::bigint * 2 - 1),
				ranked AS (
				    SELECT id, row_number() OVER (ORDER BY sort_key) - 1 AS position FROM keyed)
				UPDATE tree_node_items t SET tree_node_id = ?, sort_order = r.position
				FROM ranked r WHERE t.id = r.id""", nodeId, itemId, itemId, position, nodeId);
		if (!oldNodeId.equals(nodeId)) {
			renumberItems(oldNodeId);
		}
	}

	/** Deletes the entry and numbers the remaining entries of its folder densely. */
	public void removeItem(UUID nodeId, UUID itemId) {
		this.jdbc.update("DELETE FROM tree_node_items WHERE id = ?", itemId);
		renumberItems(nodeId);
	}

	/** True if the entity exists, is not deleted and is an element, not a connection. */
	public boolean existsElementEntity(int entityId) {
		Boolean exists = this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM entities e JOIN metatypes m ON m.id = e.metatype_id
				               WHERE e.id = ? AND e.deleted_at IS NULL AND NOT m.is_connection)""", Boolean.class,
				entityId);
		return Boolean.TRUE.equals(exists);
	}

	/** True if a diagram that is not deleted exists. */
	public boolean existsDiagram(UUID diagramId) {
		Boolean exists = this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM diagrams WHERE id = ? AND deleted_at IS NULL)", Boolean.class,
				diagramId);
		return Boolean.TRUE.equals(exists);
	}

	/** True if the solution exists. */
	public boolean existsSolution(UUID solutionId) {
		Boolean exists = this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM solutions WHERE id = ?)",
				Boolean.class, solutionId);
		return Boolean.TRUE.equals(exists);
	}

	private void renumberItems(UUID nodeId) {
		this.jdbc.update("""
				WITH ranked AS (
				    SELECT id, row_number() OVER (ORDER BY sort_order, created_at, id) - 1 AS position
				    FROM tree_node_items WHERE tree_node_id = ?)
				UPDATE tree_node_items t SET sort_order = r.position
				FROM ranked r WHERE t.id = r.id AND t.sort_order <> r.position""", nodeId);
	}

	private static StoredVisibleItem visibleItem(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new StoredVisibleItem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
				rs.getObject(4, UUID.class), (Integer) rs.getObject(5), rs.getString(6), rs.getInt(7), rs.getString(8),
				rs.getString(9), rs.getBoolean(10));
	}

}

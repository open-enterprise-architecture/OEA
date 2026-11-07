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
 * (V21). A taken folder name fails with a {@link org.springframework.dao.DuplicateKeyException} on
 * {@link #NAME_INDEX}, a second entry of the same target in a folder on {@link #ITEM_INDEX}.
 */
@Repository
public class NavigationTreeStore {

	/** Unique index of the folder names among siblings, regardless of case. */
	public static final String NAME_INDEX = "tree_nodes_sibling_name";

	/** Unique index of a target per folder. */
	public static final String ITEM_INDEX = "tree_node_items_once_per_folder";

	/** Item type of a catalog entry. */
	public static final String CATALOG = "catalog";

	private final JdbcTemplate jdbc;

	public NavigationTreeStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A folder; {@code parentId} is {@code null} for the root. */
	public record StoredNode(UUID id, String name, UUID parentId, int sortOrder) {
	}

	/**
	 * A catalog entry with the current name and scope of its catalog; {@code displayLabel} may be
	 * {@code null}.
	 */
	public record StoredCatalogItem(UUID id, UUID nodeId, UUID referenceId, String displayLabel, int sortOrder,
			String catalogName, String catalogScope) {
	}

	/** An entry of any type, as stored. */
	public record StoredItem(UUID id, UUID nodeId, String itemType, UUID referenceId) {
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

	/**
	 * The catalog entries whose catalog exists and is visible to the person: instance catalogs and
	 * the person's own personal catalogs.
	 */
	public List<StoredCatalogItem> visibleCatalogItems(long personId) {
		return this.jdbc.query("""
				SELECT i.id, i.tree_node_id, i.reference_id, i.display_label, i.sort_order, c.name, c.scope
				FROM tree_node_items i
				JOIN catalogs c ON c.id = i.reference_id
				WHERE i.item_type = 'catalog' AND (c.scope = 'instance' OR c.created_by = ?)""",
				(rs, rowNum) -> new StoredCatalogItem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
						rs.getObject(3, UUID.class), rs.getString(4), rs.getInt(5), rs.getString(6), rs.getString(7)),
				personId);
	}

	/** Stores a folder under the parent and returns its ID. */
	public UUID createNode(String name, UUID parentId, long personId, Instant now) {
		return this.jdbc.queryForObject("""
				INSERT INTO tree_nodes (name, parent_id, created_by, created_at) VALUES (?, ?, ?, ?) RETURNING id""",
				UUID.class, name, parentId, personId, Timestamp.from(now));
	}

	/** Renames the folder. */
	public void renameNode(UUID id, String name) {
		this.jdbc.update("UPDATE tree_nodes SET name = ? WHERE id = ?", name, id);
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

	/** Stores an entry in the folder and returns its ID. */
	public UUID addItem(UUID nodeId, String itemType, UUID referenceId, String displayLabel, long personId,
			Instant now) {
		return this.jdbc.queryForObject("""
				INSERT INTO tree_node_items (tree_node_id, item_type, reference_id, display_label, created_by,
				                             created_at)
				VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", UUID.class, nodeId, itemType, referenceId, displayLabel,
				personId, Timestamp.from(now));
	}

	/** The entry if it belongs to the folder. */
	public Optional<StoredItem> findItem(UUID nodeId, UUID itemId) {
		return this.jdbc
			.query("SELECT id, tree_node_id, item_type, reference_id FROM tree_node_items WHERE id = ? AND tree_node_id = ?",
					(rs, rowNum) -> new StoredItem(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
							rs.getString(3), rs.getObject(4, UUID.class)),
					itemId, nodeId)
			.stream()
			.findFirst();
	}

	/** Deletes the entry. */
	public void removeItem(UUID itemId) {
		this.jdbc.update("DELETE FROM tree_node_items WHERE id = ?", itemId);
	}

}

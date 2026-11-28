package io.oea.core.diagram;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The shapes of diagrams (V47, ADR-051: all SQL of the feature lives here). A shape shows an
 * entity at a position with a size in pt; the drawing order is {@code z_order}. The shapes of an
 * entity that is deleted stay stored and are hidden from the view, so that they show again when
 * the entity is restored (BR-DG-02).
 */
@Repository
public class DiagramShapeStore {

	private final JdbcTemplate jdbc;

	public DiagramShapeStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * A shown shape with the name and metatype of its entity, the stored container, the number of
	 * the entity's children that are not deleted and its parent ({@code null} at the root level or
	 * when the parent is deleted).
	 */
	public record ShownShape(UUID id, int entityId, String entityName, UUID metatypeId, BigDecimal x, BigDecimal y,
			BigDecimal width, BigDecimal height, UUID parentShapeId, boolean collapsed, String orientation,
			int childCount, Integer parentEntityId) {
	}

	/** A stored shape with its stored container; {@code hidden} is true while its entity is deleted. */
	public record StoredShape(UUID id, int entityId, BigDecimal x, BigDecimal y, BigDecimal width,
			BigDecimal height, UUID parentShapeId, boolean collapsed,
			String orientation, boolean hidden) {
	}

	/** A shape to store; its drawing order is its index in the list. */
	public record ShapeWrite(UUID id, int entityId, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height,
			UUID parentShapeId, boolean collapsed, String orientation) {
	}

	/** A direct child of an entity that is not deleted. */
	public record Child(int id, String name, UUID metatypeId, int childCount, Integer parentEntityId) {
	}

	/** A connection of the composition metatype from a parent to a child, not deleted. */
	public record CompositionConnection(int childId, int connectionId, String name, UUID metatypeId) {
	}

	/** An entity a shape may refer to. */
	public record EntityRef(int id, UUID metatypeId, boolean deleted, boolean connection) {
	}

	/** The shapes of the diagram whose entity is not deleted, in drawing order. */
	public List<ShownShape> shown(UUID diagramId) {
		return this.jdbc.query("""
				SELECT s.id, s.entity_id, e.name, e.metatype_id, s.x_pt, s.y_pt, s.width_pt, s.height_pt,
				       s.parent_shape_id, s.collapsed, s.orientation,
				       (SELECT count(*) FROM entities c WHERE c.parent_entity_id = e.id AND c.deleted_at IS NULL),
				       (SELECT p.id FROM entities p WHERE p.id = e.parent_entity_id AND p.deleted_at IS NULL)
				FROM diagram_shapes s JOIN entities e ON e.id = s.entity_id
				WHERE s.diagram_id = ? AND e.deleted_at IS NULL ORDER BY s.z_order, s.id""",
				(rs, n) -> new ShownShape(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3),
						rs.getObject(4, UUID.class), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
						rs.getBigDecimal(8), rs.getObject(9, UUID.class), rs.getBoolean(10), rs.getString(11),
						rs.getInt(12), (Integer) rs.getObject(13)),
				diagramId);
	}

	/** All stored shapes of the diagram in drawing order, the hidden ones included. */
	public List<StoredShape> stored(UUID diagramId) {
		return this.jdbc.query("""
				SELECT s.id, s.entity_id, s.x_pt, s.y_pt, s.width_pt, s.height_pt, s.parent_shape_id,
				       s.collapsed, s.orientation, e.deleted_at IS NOT NULL
				FROM diagram_shapes s JOIN entities e ON e.id = s.entity_id
				WHERE s.diagram_id = ? ORDER BY s.z_order, s.id""",
				(rs, n) -> new StoredShape(rs.getObject(1, UUID.class), rs.getInt(2), rs.getBigDecimal(3),
						rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getObject(7, UUID.class),
						rs.getBoolean(8), rs.getString(9), rs.getBoolean(10)),
				diagramId);
	}

	/** The IDs among the given ones that are shapes of another diagram. */
	public Set<UUID> idsOfOtherDiagrams(UUID diagramId, Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(this.jdbc.query("SELECT id FROM diagram_shapes WHERE diagram_id <> ? AND id = ANY (?::uuid[])",
				(rs, n) -> rs.getObject(1, UUID.class), diagramId,
				ids.stream().map(UUID::toString).toArray(String[]::new)));
	}

	/** The entities with these IDs, as far as they exist, with their metatype and whether it is a connection. */
	public Map<Integer, EntityRef> entities(Collection<Integer> ids) {
		Map<Integer, EntityRef> found = new HashMap<>();
		if (ids.isEmpty()) {
			return found;
		}
		this.jdbc.query("""
				SELECT e.id, e.metatype_id, e.deleted_at IS NOT NULL, m.is_connection
				FROM entities e JOIN metatypes m ON m.id = e.metatype_id WHERE e.id = ANY (?)""", (rs) -> {
			found.put(rs.getInt(1), new EntityRef(rs.getInt(1), rs.getObject(2, UUID.class), rs.getBoolean(3),
					rs.getBoolean(4)));
		}, (Object) ids.toArray(Integer[]::new));
		return found;
	}

	/**
	 * Replaces the shapes of the diagram with the given ones: shapes of an entity that is not
	 * deleted and that the list does not contain are removed, the others are inserted or updated by
	 * ID. Shapes of a deleted entity stay, so that they show again after a restore.
	 */
	public void replace(UUID diagramId, List<ShapeWrite> shapes) {
		this.jdbc.update("""
				DELETE FROM diagram_shapes s USING entities e
				WHERE s.diagram_id = ? AND e.id = s.entity_id AND e.deleted_at IS NULL AND NOT (s.id = ANY (?::uuid[]))""",
				diagramId, shapes.stream().map((shape) -> shape.id().toString()).toArray(String[]::new));
		List<Object[]> rows = new ArrayList<>();
		for (int i = 0; i < shapes.size(); i++) {
			ShapeWrite shape = shapes.get(i);
			rows.add(new Object[] { shape.id(), diagramId, shape.entityId(), shape.x(), shape.y(), shape.width(),
					shape.height(), i, shape.parentShapeId(), shape.collapsed(), shape.orientation() });
		}
		// in drawing order, so that a container is stored before the shapes nested in it
		this.jdbc.batchUpdate("""
				INSERT INTO diagram_shapes (id, diagram_id, entity_id, x_pt, y_pt, width_pt, height_pt, z_order,
				    parent_shape_id, collapsed, orientation)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET entity_id = EXCLUDED.entity_id, x_pt = EXCLUDED.x_pt,
				    y_pt = EXCLUDED.y_pt, width_pt = EXCLUDED.width_pt, height_pt = EXCLUDED.height_pt,
				    z_order = EXCLUDED.z_order, parent_shape_id = EXCLUDED.parent_shape_id,
				    collapsed = EXCLUDED.collapsed, orientation = EXCLUDED.orientation
				WHERE diagram_shapes.diagram_id = EXCLUDED.diagram_id""", rows);
	}

	/**
	 * The direct children of the element entity that are not deleted, by name ignoring case, then ID,
	 * at most {@code limit}.
	 */
	public List<Child> children(int entityId, int limit) {
		return this.jdbc.query("""
				SELECT e.id, e.name, e.metatype_id,
				       (SELECT count(*) FROM entities c WHERE c.parent_entity_id = e.id AND c.deleted_at IS NULL)
				FROM entities e WHERE e.parent_entity_id = ? AND e.deleted_at IS NULL
				ORDER BY lower(e.name), e.id LIMIT ?""",
				(rs, n) -> new Child(rs.getInt(1), rs.getString(2), rs.getObject(3, UUID.class), rs.getInt(4),
						entityId),
				entityId, limit);
	}

	/**
	 * The connections of the composition metatype from the parent to each of the children that are
	 * not deleted, by connection ID; a child without one is missing.
	 */
	public Map<Integer, CompositionConnection> compositionConnections(int parentId, Collection<Integer> childIds) {
		Map<Integer, CompositionConnection> found = new HashMap<>();
		if (childIds.isEmpty()) {
			return found;
		}
		this.jdbc.query("""
				SELECT ec.target_entity_id, c.id, c.name, c.metatype_id
				FROM entity_connections ec JOIN entities c ON c.id = ec.entity_id
				JOIN metatypes m ON m.id = c.metatype_id
				WHERE ec.source_entity_id = ? AND ec.target_entity_id = ANY (?) AND c.deleted_at IS NULL
				  AND m.is_composition
				ORDER BY c.id DESC""", (rs) -> {
					// the lowest connection ID wins
					found.put(rs.getInt(1), new CompositionConnection(rs.getInt(1), rs.getInt(2), rs.getString(3),
							rs.getObject(4, UUID.class)));
				}, parentId, (Object) childIds.toArray(Integer[]::new));
		return found;
	}

	/** The parent of each of these entities that exists; an entity without a parent maps to {@code null}. */
	public Map<Integer, Integer> parents(Collection<Integer> entityIds) {
		Map<Integer, Integer> found = new HashMap<>();
		if (entityIds.isEmpty()) {
			return found;
		}
		this.jdbc.query("SELECT id, parent_entity_id FROM entities WHERE id = ANY (?)", (rs) -> {
			found.put(rs.getInt(1), (Integer) rs.getObject(2));
		}, (Object) entityIds.toArray(Integer[]::new));
		return found;
	}

	/** Whether the entity is an element entity that is not deleted (children are listed only of such). */
	public boolean activeElement(int entityId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM entities e JOIN metatypes m ON m.id = e.metatype_id
				               WHERE e.id = ? AND e.deleted_at IS NULL AND NOT m.is_connection)""", Boolean.class,
				entityId));
	}

	/** The IDs of the entities that have a value for at least one property, with the properties they have values for. */
	public Map<Integer, Set<UUID>> valueProperties(Collection<Integer> entityIds) {
		Map<Integer, Set<UUID>> found = new HashMap<>();
		if (entityIds.isEmpty()) {
			return found;
		}
		this.jdbc.query("SELECT entity_id, property_def_id FROM entity_property_values WHERE entity_id = ANY (?)",
				(rs) -> {
					found.computeIfAbsent(rs.getInt(1), (id) -> new HashSet<>()).add(rs.getObject(2, UUID.class));
				}, (Object) entityIds.toArray(Integer[]::new));
		return found;
	}

	/** The entities among the given ones whose value of the boolean property is true (us-083-bpmn-data-objects D2). */
	public Set<Integer> withTrueValue(Collection<Integer> entityIds, UUID propertyDefId) {
		Set<Integer> found = new HashSet<>();
		if (entityIds.isEmpty()) {
			return found;
		}
		this.jdbc.query("""
				SELECT entity_id FROM entity_property_values
				WHERE property_def_id = ? AND value_bool IS TRUE AND entity_id = ANY (?)""",
				(rs) -> {
					found.add(rs.getInt(1));
				}, propertyDefId, (Object) entityIds.toArray(Integer[]::new));
		return found;
	}

}

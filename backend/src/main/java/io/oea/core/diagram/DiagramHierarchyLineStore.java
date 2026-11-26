package io.oea.core.diagram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The hierarchy lines of diagrams (V51, ADR-051: all SQL of the feature lives here): a line from the
 * shape of a parent entity to the shape of one of its children that the diagram shows elsewhere. The
 * lines of a diagram are replaced as a whole on each save, like its edges.
 */
@Repository
public class DiagramHierarchyLineStore {

	private final JdbcTemplate jdbc;

	public DiagramHierarchyLineStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A line from the container's shape to the child's shape. */
	public record Line(UUID id, UUID parentShapeId, UUID childShapeId) {
	}

	/**
	 * The lines to show, by ID: both shapes belong to entities that are not deleted and the hierarchy
	 * holds, i.e. the entity of the child shape has the entity of the parent shape as its parent.
	 */
	public List<Line> shown(UUID diagramId) {
		return this.jdbc.query("""
				SELECT l.id, l.parent_shape_id, l.child_shape_id
				FROM diagram_hierarchy_lines l
				JOIN diagram_shapes ps ON ps.id = l.parent_shape_id
				JOIN entities pe ON pe.id = ps.entity_id
				JOIN diagram_shapes cs ON cs.id = l.child_shape_id
				JOIN entities ce ON ce.id = cs.entity_id
				WHERE l.diagram_id = ? AND pe.deleted_at IS NULL AND ce.deleted_at IS NULL
				  AND ce.parent_entity_id = pe.id
				ORDER BY l.id""",
				(rs, n) -> new Line(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
						rs.getObject(3, UUID.class)),
				diagramId);
	}

	/** The IDs among the given ones that are lines of another diagram. */
	public Set<UUID> idsOfOtherDiagrams(UUID diagramId, Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(this.jdbc.query(
				"SELECT id FROM diagram_hierarchy_lines WHERE diagram_id <> ? AND id = ANY (?::uuid[])",
				(rs, n) -> rs.getObject(1, UUID.class), diagramId,
				ids.stream().map(UUID::toString).toArray(String[]::new)));
	}

	/** Replaces the lines of the diagram with the given ones; the shapes they join must be stored. */
	public void replace(UUID diagramId, List<Line> lines) {
		this.jdbc.update("DELETE FROM diagram_hierarchy_lines WHERE diagram_id = ?", diagramId);
		List<Object[]> rows = new ArrayList<>();
		for (Line line : lines) {
			rows.add(new Object[] { line.id(), diagramId, line.parentShapeId(), line.childShapeId() });
		}
		this.jdbc.batchUpdate("""
				INSERT INTO diagram_hierarchy_lines (id, diagram_id, parent_shape_id, child_shape_id)
				VALUES (?, ?, ?, ?)""", rows);
	}

}

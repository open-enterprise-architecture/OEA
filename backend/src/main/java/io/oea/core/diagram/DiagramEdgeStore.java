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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The edges of diagrams (V48, ADR-051: all SQL of the feature lives here) and the search of the
 * connections to offer. An edge shows a connection from a shape of its source entity to a shape of
 * its target entity. An edge is hidden while its connection is deleted or one of its shapes shows a
 * deleted entity; it stays stored and shows again after a restore (BR-DG-02). Removing a shape
 * removes its edges (foreign key).
 */
@Repository
public class DiagramEdgeStore {

	private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

	private final JdbcTemplate jdbc;

	public DiagramEdgeStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A bend point in pt. */
	public record Point(BigDecimal x, BigDecimal y) {
	}

	/** A shown edge with the name and metatype of its connection. */
	public record ShownEdge(UUID id, int connectionId, String connectionName, UUID metatypeId, UUID sourceShapeId,
			UUID targetShapeId, List<Point> points) {
	}

	/** A stored edge; {@code hidden} is true while its connection or one of its shapes' entities is deleted. */
	public record StoredEdge(UUID id, int connectionId, UUID sourceShapeId, UUID targetShapeId, List<Point> points,
			boolean hidden) {
	}

	/** An edge to store. */
	public record EdgeWrite(UUID id, int connectionId, UUID sourceShapeId, UUID targetShapeId, List<Point> points) {
	}

	/** The source and target entity of a connection. */
	public record Endpoints(int sourceId, int targetId) {
	}

	/** A connection between two entities, as offered. */
	public record Found(int connectionId, String name, UUID metatypeId, int sourceId, int targetId) {
	}

	/** The connections found, and whether more matched than the limit. */
	public record FoundPage(List<Found> items, boolean truncated) {
	}

	private static final String HIDDEN_JOINS = """
			FROM diagram_edges ed
			JOIN entities c ON c.id = ed.connection_id
			JOIN diagram_shapes ss ON ss.id = ed.source_shape_id JOIN entities se ON se.id = ss.entity_id
			JOIN diagram_shapes ts ON ts.id = ed.target_shape_id JOIN entities te ON te.id = ts.entity_id
			""";

	/** The edges of the diagram whose connection and shapes' entities are not deleted. */
	public List<ShownEdge> shown(UUID diagramId) {
		return this.jdbc.query("SELECT ed.id, ed.connection_id, c.name, c.metatype_id, ed.source_shape_id, "
				+ "ed.target_shape_id, ed.points::text " + HIDDEN_JOINS
				+ "WHERE ed.diagram_id = ? AND c.deleted_at IS NULL AND se.deleted_at IS NULL "
				+ "AND te.deleted_at IS NULL ORDER BY ed.id",
				(rs, n) -> new ShownEdge(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3),
						rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class),
						points(rs.getString(7))),
				diagramId);
	}

	/** All stored edges of the diagram, the hidden ones included. */
	public List<StoredEdge> stored(UUID diagramId) {
		return this.jdbc.query("SELECT ed.id, ed.connection_id, ed.source_shape_id, ed.target_shape_id, "
				+ "ed.points::text, (c.deleted_at IS NOT NULL OR se.deleted_at IS NOT NULL OR te.deleted_at IS NOT NULL) "
				+ HIDDEN_JOINS + "WHERE ed.diagram_id = ? ORDER BY ed.id",
				(rs, n) -> new StoredEdge(rs.getObject(1, UUID.class), rs.getInt(2), rs.getObject(3, UUID.class),
						rs.getObject(4, UUID.class), points(rs.getString(5)), rs.getBoolean(6)),
				diagramId);
	}

	/** The IDs among the given ones that are edges of another diagram. */
	public Set<UUID> idsOfOtherDiagrams(UUID diagramId, Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(this.jdbc.query("SELECT id FROM diagram_edges WHERE diagram_id <> ? AND id = ANY (?::uuid[])",
				(rs, n) -> rs.getObject(1, UUID.class), diagramId,
				ids.stream().map(UUID::toString).toArray(String[]::new)));
	}

	/** The source and target entity of each of these connections, as far as they exist. */
	public Map<Integer, Endpoints> endpoints(Collection<Integer> connectionIds) {
		Map<Integer, Endpoints> found = new HashMap<>();
		if (connectionIds.isEmpty()) {
			return found;
		}
		this.jdbc.query("SELECT entity_id, source_entity_id, target_entity_id FROM entity_connections "
				+ "WHERE entity_id = ANY (?)", (rs) -> {
					found.put(rs.getInt(1), new Endpoints(rs.getInt(2), rs.getInt(3)));
				}, (Object) connectionIds.toArray(Integer[]::new));
		return found;
	}

	/** The IDs of the connections among the given ones that a shown edge of the diagram shows. */
	public Set<Integer> shownConnections(UUID diagramId, Collection<Integer> connectionIds) {
		if (connectionIds.isEmpty()) {
			return Set.of();
		}
		Set<Integer> found = new HashSet<>();
		this.jdbc.query("SELECT DISTINCT ed.connection_id " + HIDDEN_JOINS
				+ "WHERE ed.diagram_id = ? AND ed.connection_id = ANY (?) AND c.deleted_at IS NULL "
				+ "AND se.deleted_at IS NULL AND te.deleted_at IS NULL", (rs) -> {
					found.add(rs.getInt(1));
				}, diagramId, connectionIds.toArray(Integer[]::new));
		return found;
	}

	/**
	 * Removes the edges of the diagram that are not in {@code keep}. This runs before the shapes
	 * are replaced; the edges of a shape that the replace removes go with it.
	 */
	public void deleteExcept(UUID diagramId, Collection<UUID> keep) {
		this.jdbc.update("DELETE FROM diagram_edges WHERE diagram_id = ? AND NOT (id = ANY (?::uuid[]))", diagramId,
				keep.stream().map(UUID::toString).toArray(String[]::new));
	}

	/** Inserts or updates the edges by ID; the shapes they join must be stored. */
	public void upsert(UUID diagramId, List<EdgeWrite> edges) {
		List<Object[]> rows = new ArrayList<>();
		for (EdgeWrite edge : edges) {
			rows.add(new Object[] { edge.id(), diagramId, edge.connectionId(), edge.sourceShapeId(),
					edge.targetShapeId(), json(edge.points()) });
		}
		this.jdbc.batchUpdate("""
				INSERT INTO diagram_edges (id, diagram_id, connection_id, source_shape_id, target_shape_id, points)
				VALUES (?, ?, ?, ?, ?, ?::jsonb)
				ON CONFLICT (id) DO UPDATE SET connection_id = EXCLUDED.connection_id,
				    source_shape_id = EXCLUDED.source_shape_id, target_shape_id = EXCLUDED.target_shape_id,
				    points = EXCLUDED.points
				WHERE diagram_edges.diagram_id = EXCLUDED.diagram_id""", rows);
	}

	/**
	 * The connections that are not deleted, have one of the allowed metatypes and join one of the
	 * placed entities with itself, another placed entity or one of the other shown entities, by
	 * connection ID, at most {@code limit}; {@code truncated} when more matched.
	 */
	public FoundPage find(Collection<Integer> placed, Collection<Integer> shown, Collection<UUID> metatypes,
			int limit) {
		if (placed.isEmpty() || metatypes.isEmpty()) {
			return new FoundPage(List.of(), false);
		}
		Integer[] placedIds = placed.toArray(Integer[]::new);
		Integer[] otherIds = new HashSet<>(
				java.util.stream.Stream.concat(placed.stream(), shown.stream()).toList()).toArray(Integer[]::new);
		List<Found> items = this.jdbc.query("""
				SELECT c.id, c.name, c.metatype_id, ec.source_entity_id, ec.target_entity_id
				FROM entity_connections ec JOIN entities c ON c.id = ec.entity_id
				WHERE c.deleted_at IS NULL AND c.metatype_id = ANY (?::uuid[])
				  AND ((ec.source_entity_id = ANY (?) AND ec.target_entity_id = ANY (?))
				    OR (ec.target_entity_id = ANY (?) AND ec.source_entity_id = ANY (?)))
				ORDER BY c.id LIMIT ?""",
				(rs, n) -> new Found(rs.getInt(1), rs.getString(2), rs.getObject(3, UUID.class), rs.getInt(4),
						rs.getInt(5)),
				metatypes.stream().map(UUID::toString).toArray(String[]::new), placedIds, otherIds, placedIds,
				otherIds, limit + 1);
		boolean truncated = items.size() > limit;
		return new FoundPage(truncated ? items.subList(0, limit) : items, truncated);
	}

	private static String json(List<Point> points) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < points.size(); i++) {
			json.append((i == 0) ? "" : ",")
				.append('[')
				.append(points.get(i).x().toPlainString())
				.append(',')
				.append(points.get(i).y().toPlainString())
				.append(']');
		}
		return json.append(']').toString();
	}

	private static List<Point> points(String json) {
		List<BigDecimal> numbers = new ArrayList<>();
		Matcher matcher = NUMBER.matcher(json);
		while (matcher.find()) {
			numbers.add(new BigDecimal(matcher.group()));
		}
		List<Point> points = new ArrayList<>();
		for (int i = 0; i + 1 < numbers.size(); i += 2) {
			points.add(new Point(numbers.get(i), numbers.get(i + 1)));
		}
		return points;
	}

}

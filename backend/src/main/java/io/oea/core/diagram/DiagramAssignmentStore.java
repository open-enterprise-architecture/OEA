package io.oea.core.diagram;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The assignments of shapes (design D3, D4 of us-075-lane-assignments, ADR-051: all SQL of the
 * feature lives here): the outgoing connections of an entity of a connection metatype and its
 * subtypes, with their targets.
 */
@Repository
public class DiagramAssignmentStore {

	private final JdbcTemplate jdbc;

	public DiagramAssignmentStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** An assignment: the connection with its version and the target with its name. */
	public record Assignment(int connectionId, int connectionVersion, int entityId, String name) {
	}

	/**
	 * The assignments of the source entities: connections of the metatype or a subtype that are not
	 * deleted, whose target is not deleted and of the target metatype or a subtype, by target name
	 * ignoring case, then target and connection ID.
	 */
	public Map<Integer, List<Assignment>> assignments(Collection<Integer> sourceIds, UUID connectionType,
			UUID targetType) {
		Map<Integer, List<Assignment>> found = new LinkedHashMap<>();
		if (sourceIds.isEmpty() || connectionType == null || targetType == null) {
			return found;
		}
		this.jdbc.query("""
				WITH RECURSIVE ctypes (id) AS (
				    SELECT ?::uuid UNION SELECT m.id FROM metatypes m JOIN ctypes c ON m.base_metatype_id = c.id),
				ttypes (id) AS (
				    SELECT ?::uuid UNION SELECT m.id FROM metatypes m JOIN ttypes t ON m.base_metatype_id = t.id)
				SELECT ec.source_entity_id, c.id, c.version, t.id, t.name
				FROM entity_connections ec
				JOIN entities c ON c.id = ec.entity_id AND c.deleted_at IS NULL AND c.metatype_id IN (SELECT id FROM ctypes)
				JOIN entities t ON t.id = ec.target_entity_id AND t.deleted_at IS NULL
				                AND t.metatype_id IN (SELECT id FROM ttypes)
				WHERE ec.source_entity_id = ANY (?)
				ORDER BY lower(t.name), t.id, c.id""", (rs) -> {
			found.computeIfAbsent(rs.getInt(1), (id) -> new java.util.ArrayList<>())
				.add(new Assignment(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getString(5)));
		}, connectionType, targetType, (Object) sourceIds.toArray(Integer[]::new));
		return found;
	}

	/**
	 * The realizations of the source entities (us-083-bpmn-data-objects D8): connections of the
	 * metatype or a subtype that are not deleted, whose target is not deleted, by target name ignoring
	 * case, then target and connection ID.
	 */
	public Map<Integer, List<Assignment>> realizations(Collection<Integer> sourceIds, UUID connectionType) {
		Map<Integer, List<Assignment>> found = new LinkedHashMap<>();
		if (sourceIds.isEmpty() || connectionType == null) {
			return found;
		}
		this.jdbc.query("""
				WITH RECURSIVE ctypes (id) AS (
				    SELECT ?::uuid UNION SELECT m.id FROM metatypes m JOIN ctypes c ON m.base_metatype_id = c.id)
				SELECT ec.source_entity_id, c.id, c.version, t.id, t.name
				FROM entity_connections ec
				JOIN entities c ON c.id = ec.entity_id AND c.deleted_at IS NULL AND c.metatype_id IN (SELECT id FROM ctypes)
				JOIN entities t ON t.id = ec.target_entity_id AND t.deleted_at IS NULL
				WHERE ec.source_entity_id = ANY (?)
				ORDER BY lower(t.name), t.id, c.id""", (rs) -> {
			found.computeIfAbsent(rs.getInt(1), (id) -> new java.util.ArrayList<>())
				.add(new Assignment(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getString(5)));
		}, connectionType, (Object) sourceIds.toArray(Integer[]::new));
		return found;
	}

	/** The number of connections of the metatype or a subtype from the entity that are not deleted. */
	public int countFrom(int sourceId, UUID connectionType) {
		Integer count = this.jdbc.queryForObject("""
				WITH RECURSIVE ctypes (id) AS (
				    SELECT ?::uuid UNION SELECT m.id FROM metatypes m JOIN ctypes c ON m.base_metatype_id = c.id)
				SELECT count(*) FROM entity_connections ec
				JOIN entities c ON c.id = ec.entity_id AND c.deleted_at IS NULL AND c.metatype_id IN (SELECT id FROM ctypes)
				WHERE ec.source_entity_id = ?""", Integer.class, connectionType, sourceId);
		return (count != null) ? count : 0;
	}

	/** The source entity of each of the connections that exist. */
	public Map<Integer, Integer> sources(Collection<Integer> connectionIds) {
		Map<Integer, Integer> found = new HashMap<>();
		if (connectionIds.isEmpty()) {
			return found;
		}
		this.jdbc.query("SELECT entity_id, source_entity_id FROM entity_connections WHERE entity_id = ANY (?)", (rs) -> {
			found.put(rs.getInt(1), rs.getInt(2));
		}, (Object) connectionIds.toArray(Integer[]::new));
		return found;
	}

}

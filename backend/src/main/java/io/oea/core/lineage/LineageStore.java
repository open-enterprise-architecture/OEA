package io.oea.core.lineage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The reads of the data lineage walk (design D4 of us-065-lineage-model-and-query): one
 * set-based statement per hop over {@code entity_connections}, whose source and target indexes
 * carry the walk. Only active entities are returned: a deleted flow, carrying connection,
 * association or endpoint is left out.
 */
@Repository
public class LineageStore {

	/** A data flow with its endpoints; the endpoint names and metatypes serve the nodes. */
	public record FlowRow(int id, UUID metatypeId, int sourceId, String sourceName, UUID sourceMetatypeId,
			int targetId, String targetName, UUID targetMetatypeId) {
	}

	/** A data flow that carries the data object, with the carrying connection. */
	public record CarryingFlow(FlowRow flow, int carriesId) {
	}

	/** A data object a data flow carries, with the carrying connection. */
	public record Carried(int flowId, int carriesId, int dataObjectId, String dataObjectName) {
	}

	/** A data association of the start with the task at its other end. */
	public record Association(int id, UUID metatypeId, int taskId, String taskName, UUID taskMetatypeId) {
	}

	private static final String FLOW_COLUMNS = """
			c.entity_id, f.metatype_id, c.source_entity_id, s.name, s.metatype_id, c.target_entity_id, t.name,
			       t.metatype_id""";

	private static final String FLOW_FROM = """
			FROM entity_connections c
			JOIN entities f ON f.id = c.entity_id AND f.deleted_at IS NULL
			JOIN entities s ON s.id = c.source_entity_id AND s.deleted_at IS NULL
			JOIN entities t ON t.id = c.target_entity_id AND t.deleted_at IS NULL""";

	private final JdbcTemplate jdbc;

	public LineageStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * The data flows that leave ({@code downstream}) or enter the entities, by ID; with a data
	 * object only the flows that carry it.
	 */
	public List<FlowRow> flows(Collection<Integer> entityIds, boolean downstream, Collection<UUID> flowTypes,
			Integer dataObjectId, Collection<UUID> carriesTypes) {
		if (entityIds.isEmpty()) {
			return List.of();
		}
		String endpoint = downstream ? "c.source_entity_id" : "c.target_entity_id";
		List<Object> arguments = new ArrayList<>();
		arguments.add(entityIds.toArray(Integer[]::new));
		arguments.add(flowTypes.toArray(UUID[]::new));
		String carried = "";
		if (dataObjectId != null) {
			carried = """
					 AND EXISTS (SELECT 1 FROM entity_connections cd
					             JOIN entities cde ON cde.id = cd.entity_id AND cde.deleted_at IS NULL
					             WHERE cd.source_entity_id = c.entity_id AND cd.target_entity_id = ?
					               AND cde.metatype_id = ANY (?))""";
			arguments.add(dataObjectId);
			arguments.add(carriesTypes.toArray(UUID[]::new));
		}
		return this.jdbc.query("SELECT " + FLOW_COLUMNS + " " + FLOW_FROM + " WHERE " + endpoint
				+ " = ANY (?) AND f.metatype_id = ANY (?)" + carried + " ORDER BY c.entity_id",
				(rs, n) -> flow(rs), arguments.toArray());
	}

	/** The active data flows that carry the data object, each with its carrying connections, by flow and connection ID. */
	public List<CarryingFlow> carrying(int dataObjectId, Collection<UUID> flowTypes, Collection<UUID> carriesTypes) {
		return this.jdbc.query("SELECT " + FLOW_COLUMNS + ", cd.entity_id " + FLOW_FROM + """

				JOIN entity_connections cd ON cd.source_entity_id = c.entity_id AND cd.target_entity_id = ?
				JOIN entities cde ON cde.id = cd.entity_id AND cde.deleted_at IS NULL AND cde.metatype_id = ANY (?)
				WHERE f.metatype_id = ANY (?)
				ORDER BY c.entity_id, cd.entity_id""", (rs, n) -> new CarryingFlow(flow(rs), rs.getInt(9)),
				dataObjectId, (Object) carriesTypes.toArray(UUID[]::new), (Object) flowTypes.toArray(UUID[]::new));
	}

	/**
	 * The data objects the flows carry, per flow by connection ID, at most {@code limit} per flow;
	 * the caller asks for one more than it shows to know whether there are more.
	 */
	public List<Carried> carried(Collection<Integer> flowIds, Collection<UUID> carriesTypes, int limit) {
		if (flowIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.query("""
				SELECT flow_id, carries_id, data_object_id, name FROM (
				    SELECT cd.source_entity_id AS flow_id, cd.entity_id AS carries_id, d.id AS data_object_id,
				           d.name AS name,
				           row_number() OVER (PARTITION BY cd.source_entity_id ORDER BY cd.entity_id) AS position
				    FROM entity_connections cd
				    JOIN entities cde ON cde.id = cd.entity_id AND cde.deleted_at IS NULL
				    JOIN entities d ON d.id = cd.target_entity_id AND d.deleted_at IS NULL
				    WHERE cd.source_entity_id = ANY (?) AND cde.metatype_id = ANY (?)) x
				WHERE position <= ?
				ORDER BY flow_id, carries_id""",
				(rs, n) -> new Carried(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4)),
				(Object) flowIds.toArray(Integer[]::new), (Object) carriesTypes.toArray(UUID[]::new), limit);
	}

	/**
	 * The data associations of the entity with the task at the other end: those that leave it
	 * ({@code input}, data input associations from the entity to a task) or enter it (data output
	 * associations from a task to the entity), by connection ID.
	 */
	public List<Association> associations(int entityId, boolean input, Collection<UUID> types) {
		if (types.isEmpty()) {
			return List.of();
		}
		String own = input ? "c.source_entity_id" : "c.target_entity_id";
		String other = input ? "c.target_entity_id" : "c.source_entity_id";
		return this.jdbc.query("""
				SELECT c.entity_id, a.metatype_id, o.id, o.name, o.metatype_id
				FROM entity_connections c
				JOIN entities a ON a.id = c.entity_id AND a.deleted_at IS NULL
				JOIN entities o ON o.id = %s AND o.deleted_at IS NULL
				WHERE %s = ? AND a.metatype_id = ANY (?)
				ORDER BY c.entity_id""".formatted(other, own),
				(rs, n) -> new Association(rs.getInt(1), rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4),
						rs.getObject(5, UUID.class)),
				entityId, (Object) types.toArray(UUID[]::new));
	}

	/** The enum values of the property per entity, for the entities that have one. */
	public Map<Integer, String> enumValues(Collection<Integer> entityIds, UUID propertyDefId) {
		Map<Integer, String> values = new java.util.HashMap<>();
		if (entityIds.isEmpty()) {
			return values;
		}
		this.jdbc.query("""
				SELECT entity_id, value_enum FROM entity_property_values
				WHERE property_def_id = ? AND value_enum IS NOT NULL AND entity_id = ANY (?)""",
				(rs) -> {
					values.put(rs.getInt(1), rs.getString(2));
				}, propertyDefId, (Object) entityIds.toArray(Integer[]::new));
		return values;
	}

	private static FlowRow flow(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new FlowRow(rs.getInt(1), rs.getObject(2, UUID.class), rs.getInt(3), rs.getString(4),
				rs.getObject(5, UUID.class), rs.getInt(6), rs.getString(7), rs.getObject(8, UUID.class));
	}

}

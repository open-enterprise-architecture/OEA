package io.oea.core.solution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The delta values and extension metatypes of active solutions that a metamodel change touches
 * (design D9 of us-153-open-issues, US-154): who is affected, and the removal of the values for
 * the decision {@code apply}. Active means neither {@code implemented} nor {@code archived}.
 */
@Repository
public class DeltaValueStore {

	private static final String ACTIVE = "s.status NOT IN ('implemented', 'archived')";

	private final JdbcTemplate jdbc;

	public DeltaValueStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** An active solution with its name and status. */
	public record SolutionRow(UUID id, String name, String status, UUID plateauId, String plateauName) {
	}

	/** The delta values of a property on a metatype in one solution. */
	public record ValueHit(UUID solutionId, UUID metatypeId, UUID propertyDefId, int count) {
	}

	/** A metatype of the extension of an active solution. */
	public record ExtensionHit(UUID solutionId, UUID metatypeId) {
	}

	/**
	 * The delta values of the property in active solutions on deltas of the metatypes, the metatype
	 * of a delta {@code new} or the entity of any other; with {@code enumValue}, only values that
	 * are this enumeration value.
	 */
	public List<ValueHit> values(UUID propertyDefId, Collection<UUID> metatypeIds, String enumValue) {
		if (metatypeIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.query("""
				SELECT d.solution_id, coalesce(d.metatype_id, e.metatype_id), count(*)
				FROM entity_delta_property_values v
				JOIN entity_deltas d ON d.id = v.delta_id
				JOIN solutions s ON s.id = d.solution_id
				LEFT JOIN entities e ON e.id = d.entity_id
				WHERE %s AND v.property_def_id = ? AND coalesce(d.metatype_id, e.metatype_id) = ANY (?::uuid[])
				  AND (?::text IS NULL OR v.after_value_enum = ?::text)
				GROUP BY d.solution_id, coalesce(d.metatype_id, e.metatype_id)""".formatted(ACTIVE),
				(rs, n) -> new ValueHit(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), propertyDefId,
						rs.getInt(3)),
				propertyDefId, ids(metatypeIds), enumValue, enumValue);
	}

	/** The metatypes among these that belong to the extension of an active solution. */
	public List<ExtensionHit> extensionMetatypes(Collection<UUID> metatypeIds) {
		if (metatypeIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.query("""
				SELECT s.id, m.id FROM metatypes m
				JOIN metamodel_configurations c ON c.id = m.config_id
				JOIN solutions s ON s.id = c.solution_id
				WHERE %s AND c.scope = 'solution' AND m.id = ANY (?::uuid[])""".formatted(ACTIVE),
				(rs, n) -> new ExtensionHit(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
				(Object) ids(metatypeIds));
	}

	/** The active solutions with these IDs. */
	public List<SolutionRow> solutions(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return this.jdbc.query("SELECT s.id, s.name, s.status, p.id, p.name FROM solutions s"
				+ " LEFT JOIN plateaus p ON p.id = s.to_plateau_id WHERE " + ACTIVE
				+ " AND s.id = ANY (?::uuid[]) ORDER BY lower(s.name), s.id",
				(rs, n) -> new SolutionRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
						rs.getObject(4, UUID.class), rs.getString(5)),
				(Object) ids(ids));
	}

	/**
	 * Removes the values of the property from the deltas of the solution on these metatypes (with
	 * {@code enumValue}, only that value) and returns the IDs of the touched deltas.
	 */
	public List<UUID> removeValues(UUID solutionId, UUID propertyDefId, Collection<UUID> metatypeIds,
			String enumValue) {
		if (metatypeIds.isEmpty()) {
			return List.of();
		}
		return new ArrayList<>(this.jdbc.queryForList("""
				DELETE FROM entity_delta_property_values v
				USING entity_deltas d LEFT JOIN entities e ON e.id = d.entity_id
				WHERE d.id = v.delta_id AND d.solution_id = ? AND v.property_def_id = ?
				  AND coalesce(d.metatype_id, e.metatype_id) = ANY (?::uuid[])
				  AND (?::text IS NULL OR v.after_value_enum = ?::text)
				RETURNING v.delta_id""", UUID.class, solutionId, propertyDefId, ids(metatypeIds), enumValue, enumValue)
			.stream()
			.distinct()
			.toList());
	}

	/** The deltas {@code modified} among these that have no field left. */
	public List<UUID> emptyModifications(Collection<UUID> deltaIds) {
		if (deltaIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.queryForList("""
				SELECT d.id FROM entity_deltas d
				WHERE d.id = ANY (?::uuid[]) AND d.delta_type = 'modified'
				  AND NOT EXISTS (SELECT 1 FROM entity_delta_property_values v WHERE v.delta_id = d.id)""", UUID.class,
				(Object) ids(deltaIds));
	}

	/** Removes a delta with its values; returns its entity. */
	public Integer removeDelta(UUID deltaId) {
		List<Integer> entity = this.jdbc.queryForList("DELETE FROM entity_deltas WHERE id = ? RETURNING entity_id",
				Integer.class, deltaId);
		return entity.isEmpty() ? null : entity.get(0);
	}

	/** The names of the property definitions. */
	public java.util.Map<UUID, String> propertyNames(Collection<UUID> ids) {
		java.util.Map<UUID, String> names = new java.util.HashMap<>();
		if (!ids.isEmpty()) {
			this.jdbc.query("SELECT id, name FROM property_definitions WHERE id = ANY (?::uuid[])",
					(rs) -> {
						names.put(rs.getObject(1, UUID.class), rs.getString(2));
					}, (Object) ids(ids));
		}
		return names;
	}

	private static String[] ids(Collection<UUID> ids) {
		return ids.stream().map(UUID::toString).toArray(String[]::new);
	}

}

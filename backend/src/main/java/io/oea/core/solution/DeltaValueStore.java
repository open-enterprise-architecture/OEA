package io.oea.core.solution;

import io.oea.core.entity.LabeledValue;
import io.oea.core.entity.PropertyValueStore.NamedValue;
import io.oea.core.entity.TypedValue;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The delta values and extension metatypes of active solutions that a metamodel change touches
 * (design D9 of us-153-open-issues, US-154): who is affected, and the removal of the values for
 * the decision {@code apply}. Active means neither {@code implemented} nor {@code archived}.
 * Also the values of the deltas of a solution (US-040, US-041): insert, delete and the labeled
 * reads, the counterpart of {@code PropertyValueStore.labeledValues}. Values are returned as
 * stored, never redacted.
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

	/** A value recorded in a delta {@code modified}: a system field or a property, before and after. */
	public record RecordedValue(String systemField, UUID propertyDefId, String propertyName, TypedValue before,
			TypedValue after) {
	}

	/**
	 * A change of a delta {@code modified} with the labels of its values; {@code propertyDefId},
	 * {@code propertyName} and {@code dataType} are {@code null} for a system field.
	 */
	public record ChangeRow(UUID deltaId, int entityId, String systemField, UUID propertyDefId, String propertyName,
			String dataType, LabeledValue before, LabeledValue after) {
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

	/** Inserts a value: a system field or a property, with its before and after value ({@code null} for none). */
	public void insertValue(UUID deltaId, String systemField, UUID propertyDefId, TypedValue before,
			TypedValue after) {
		this.jdbc.update("""
				INSERT INTO entity_delta_property_values (id, delta_id, system_field, property_def_id,
				    before_value_text, before_value_int, before_value_bool, before_value_date, before_value_ref, before_value_enum,
				    after_value_text, after_value_int, after_value_bool, after_value_date, after_value_ref, after_value_enum)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), deltaId, systemField,
				propertyDefId, text(before), integer(before), bool(before), date(before), reference(before),
				enumValue(before), text(after), integer(after), bool(after), date(after), reference(after),
				enumValue(after));
	}

	/** Removes all values of the delta. */
	public void deleteValues(UUID deltaId) {
		this.jdbc.update("DELETE FROM entity_delta_property_values WHERE delta_id = ?", deltaId);
	}

	/** The values recorded in the delta, with the property names; a side without value is {@code null}. */
	public List<RecordedValue> recorded(UUID deltaId) {
		return this.jdbc.query("""
				SELECT system_field, property_def_id, before_value_text, before_value_int, before_value_bool,
				       before_value_date, before_value_ref, before_value_enum, after_value_text, after_value_int,
				       after_value_bool, after_value_date, after_value_ref, after_value_enum, p.name
				FROM entity_delta_property_values v LEFT JOIN property_definitions p ON p.id = v.property_def_id
				WHERE delta_id = ?""",
				(rs, n) -> new RecordedValue(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(15),
						present(TypedValue.read(rs, 3)), present(TypedValue.read(rs, 9))),
				deltaId);
	}

	/**
	 * The after values of the deltas {@code new} with property name, data type and reference target,
	 * per delta ordered by property name regardless of case, then by property ID.
	 */
	public Map<UUID, List<NamedValue>> newValues(Collection<UUID> deltaIds) {
		Map<UUID, List<NamedValue>> values = new HashMap<>();
		if (deltaIds.isEmpty()) {
			return values;
		}
		this.jdbc.query("""
				SELECT v.delta_id, p.id, p.name, p.data_type, v.after_value_text, v.after_value_int, v.after_value_bool,
				       v.after_value_date, v.after_value_ref, v.after_value_enum, r.name, rm.abbreviation
				FROM entity_delta_property_values v
				JOIN entity_deltas d ON d.id = v.delta_id
				JOIN property_definitions p ON p.id = v.property_def_id
				LEFT JOIN entities r ON r.id = v.after_value_ref
				LEFT JOIN metatypes rm ON rm.id = r.metatype_id
				WHERE d.delta_type = 'new' AND d.id = ANY(?) ORDER BY lower(p.name), p.id""", (rs) -> {
			values.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new ArrayList<>())
				.add(new NamedValue(rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
						new LabeledValue(present(TypedValue.read(rs, 5)), rs.getString(11), rs.getString(12))));
		}, (Object) deltaIds.toArray(UUID[]::new));
		return values;
	}

	/**
	 * The changes of the deltas {@code modified} with the labels of their values, per delta in the
	 * order name, description, then properties by name regardless of case, then by property ID.
	 */
	public Map<UUID, List<ChangeRow>> changes(Collection<UUID> deltaIds) {
		Map<UUID, List<ChangeRow>> changes = new HashMap<>();
		if (deltaIds.isEmpty()) {
			return changes;
		}
		this.jdbc.query("""
				SELECT v.delta_id, d.entity_id, v.system_field, p.id, p.name, p.data_type,
				       v.before_value_text, v.before_value_int, v.before_value_bool, v.before_value_date, v.before_value_ref,
				       v.before_value_enum, br.name, brm.abbreviation,
				       v.after_value_text, v.after_value_int, v.after_value_bool, v.after_value_date, v.after_value_ref,
				       v.after_value_enum, ar.name, arm.abbreviation
				FROM entity_delta_property_values v
				JOIN entity_deltas d ON d.id = v.delta_id
				LEFT JOIN property_definitions p ON p.id = v.property_def_id
				LEFT JOIN entities br ON br.id = v.before_value_ref
				LEFT JOIN metatypes brm ON brm.id = br.metatype_id
				LEFT JOIN entities ar ON ar.id = v.after_value_ref
				LEFT JOIN metatypes arm ON arm.id = ar.metatype_id
				WHERE d.delta_type = 'modified' AND d.id = ANY(?)"""
				+ " ORDER BY v.system_field IS NULL, v.system_field = 'description', lower(p.name), p.id", (rs) -> {
			changes.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new ArrayList<>())
				.add(new ChangeRow(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3),
						rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6),
						new LabeledValue(present(TypedValue.read(rs, 7)), rs.getString(13), rs.getString(14)),
						new LabeledValue(present(TypedValue.read(rs, 15)), rs.getString(21), rs.getString(22))));
		}, (Object) deltaIds.toArray(UUID[]::new));
		return changes;
	}

	/** The value, or {@code null} when no component is set. */
	private static TypedValue present(TypedValue value) {
		return (value.asText() != null) ? value : null;
	}

	private static String text(TypedValue value) {
		return (value != null) ? value.text() : null;
	}

	private static Long integer(TypedValue value) {
		return (value != null) ? value.integer() : null;
	}

	private static Boolean bool(TypedValue value) {
		return (value != null) ? value.bool() : null;
	}

	private static Date date(TypedValue value) {
		return (value != null && value.date() != null) ? Date.valueOf(value.date()) : null;
	}

	private static Integer reference(TypedValue value) {
		return (value != null) ? value.reference() : null;
	}

	private static String enumValue(TypedValue value) {
		return (value != null) ? value.enumValue() : null;
	}

	private static String[] ids(Collection<UUID> ids) {
		return ids.stream().map(UUID::toString).toArray(String[]::new);
	}

}

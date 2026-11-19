package io.oea.core.entity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Property values changed by metamodel changes (design D6, D7 of us-032-metatype-editing):
 * removing the values of a property from the entities of metatypes, each affected entity
 * getting a new version with the removed value in its history, and the marks of entities that
 * miss a mandatory property. Set-based statements; callers run in a transaction.
 */
@Repository
public class PropertyValueStore {

	private final JdbcTemplate jdbc;

	public PropertyValueStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * A value of an entity with its property's name and data type and the label of its reference
	 * target; the value is as stored, never redacted.
	 */
	public record NamedValue(UUID propertyDefId, String propertyName, String dataType, LabeledValue value) {
	}

	/**
	 * The current values of the entities with property name, data type and reference target (the
	 * read shared by the baseline and the diff of a solution), per entity ordered by property name
	 * regardless of case, then by property ID; an entity without values has no key.
	 */
	public Map<Integer, List<NamedValue>> labeledValues(Collection<Integer> entityIds) {
		Map<Integer, List<NamedValue>> values = new LinkedHashMap<>();
		if (entityIds.isEmpty()) {
			return values;
		}
		this.jdbc.query("""
				SELECT v.entity_id, p.id, p.name, p.data_type, v.value_text, v.value_int, v.value_bool, v.value_date,
				       v.value_ref, v.value_enum, r.name, rm.abbreviation
				FROM entity_property_values v
				JOIN property_definitions p ON p.id = v.property_def_id
				LEFT JOIN entities r ON r.id = v.value_ref
				LEFT JOIN metatypes rm ON rm.id = r.metatype_id
				WHERE v.entity_id = ANY(?) ORDER BY lower(p.name), p.id""", (rs) -> {
			TypedValue value = TypedValue.read(rs, 5);
			values.computeIfAbsent(rs.getInt(1), (entity) -> new ArrayList<>())
				.add(new NamedValue(rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
						new LabeledValue(value, rs.getString(11), rs.getString(12))));
		}, (Object) entityIds.toArray(Integer[]::new));
		return values;
	}

	/**
	 * Removes the values of the property from the entities of the metatypes, also deleted ones,
	 * and the marks of the property on them.
	 *
	 * @param enumValue only values with this enum value, or {@code null} for all
	 * @param personId the author of the new versions
	 * @return the number of entities that lost a value
	 */
	public int removeValues(UUID propertyDefId, Collection<UUID> metatypeIds, String enumValue, long personId,
			Instant now) {
		if (metatypeIds.isEmpty()) {
			return 0;
		}
		String[] metatypes = uuidArray(metatypeIds);
		Timestamp at = Timestamp.from(now);
		this.jdbc.update("""
				DELETE FROM entity_mandatory_marks m USING entities e
				WHERE m.entity_id = e.id AND m.property_def_id = ? AND e.metatype_id = ANY (?::uuid[]) AND ?::text IS NULL""",
				propertyDefId, metatypes, enumValue);
		Integer removed = this.jdbc.queryForObject("""
				WITH removed AS (
				    DELETE FROM entity_property_values v USING entities e
				    WHERE v.entity_id = e.id AND v.property_def_id = ? AND e.metatype_id = ANY (?::uuid[])
				      AND (?::text IS NULL OR v.value_enum = ?)
				    RETURNING v.*),
				bumped AS (
				    UPDATE entities e SET version = e.version + 1, updated_at = ?, updated_by = ?
				    FROM removed r WHERE e.id = r.entity_id
				    RETURNING e.id, e.version),
				headers AS (
				    INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at)
				    SELECT id, version, ?, ? FROM bumped),
				history AS (
				    INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_text,
				        value_int, value_bool, value_date, value_ref, value_enum, changed_at, changed_by)
				    SELECT r.entity_id, r.property_def_id, b.version, r.value_text, r.value_int, r.value_bool,
				           r.value_date, r.value_ref, r.value_enum, ?, ?
				    FROM removed r JOIN bumped b ON b.id = r.entity_id)
				SELECT count(*) FROM removed""", Integer.class, propertyDefId, metatypes, enumValue, enumValue, at,
				personId, personId, at, at, personId);
		return (removed != null) ? removed : 0;
	}

	/**
	 * Marks the active entities of the metatypes that have no value for the mandatory property.
	 *
	 * @return the number of newly marked entities
	 */
	public int mark(UUID propertyDefId, Collection<UUID> metatypeIds, Instant now) {
		if (metatypeIds.isEmpty()) {
			return 0;
		}
		return this.jdbc.update("""
				INSERT INTO entity_mandatory_marks (entity_id, property_def_id, marked_at)
				SELECT e.id, ?, ? FROM entities e
				WHERE e.metatype_id = ANY (?::uuid[]) AND e.deleted_at IS NULL
				  AND NOT EXISTS (SELECT 1 FROM entity_property_values v
				                  WHERE v.entity_id = e.id AND v.property_def_id = ?)
				ON CONFLICT DO NOTHING""", propertyDefId, Timestamp.from(now), uuidArray(metatypeIds), propertyDefId);
	}

	/**
	 * Marks the entity as missing the mandatory properties, e.g. after a restore to a version in
	 * which they were empty (us-101-entity-restore D2); an existing mark is kept.
	 */
	public void mark(int entityId, Set<UUID> properties, Instant now) {
		for (UUID property : properties) {
			this.jdbc.update("""
					INSERT INTO entity_mandatory_marks (entity_id, property_def_id, marked_at) VALUES (?, ?, ?)
					ON CONFLICT DO NOTHING""", entityId, property, Timestamp.from(now));
		}
	}

	/** Clears the marks of the entity for the properties it now has a value for. */
	public void clearMarks(int entityId, Set<UUID> propertiesWithValue) {
		if (!propertiesWithValue.isEmpty()) {
			this.jdbc.update("DELETE FROM entity_mandatory_marks WHERE entity_id = ? AND property_def_id = ANY (?::uuid[])",
					entityId, uuidArray(propertiesWithValue));
		}
	}

	/**
	 * Clears the marks of the property on the entities of the metatypes, when it is no longer
	 * mandatory for them (us-137-mapping-effective-date D4).
	 *
	 * @return the number of cleared marks
	 */
	public int clearPropertyMarks(UUID propertyDefId, Collection<UUID> metatypeIds) {
		if (metatypeIds.isEmpty()) {
			return 0;
		}
		return this.jdbc.update("""
				DELETE FROM entity_mandatory_marks m USING entities e
				WHERE m.entity_id = e.id AND m.property_def_id = ? AND e.metatype_id = ANY (?::uuid[])""",
				propertyDefId, uuidArray(metatypeIds));
	}

	/** The properties the entity is marked as missing. */
	public Set<UUID> marks(int entityId) {
		return Set.copyOf(this.jdbc.queryForList(
				"SELECT property_def_id FROM entity_mandatory_marks WHERE entity_id = ?", UUID.class, entityId));
	}

	/** Whether an entity has a current or historic value for the property. */
	public boolean hasValues(UUID propertyDefId, boolean includingHistory) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM entity_property_values WHERE property_def_id = ?)
				    OR (? AND EXISTS (SELECT 1 FROM entity_property_value_history WHERE property_def_id = ?))""",
				Boolean.class, propertyDefId, includingHistory, propertyDefId));
	}

	private static String[] uuidArray(Collection<UUID> ids) {
		return ids.stream().map(UUID::toString).toArray(String[]::new);
	}

}

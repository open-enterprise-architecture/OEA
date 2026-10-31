package io.oea.core.entity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
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

	/** Clears the marks of the entity for the properties it now has a value for. */
	public void clearMarks(int entityId, Set<UUID> propertiesWithValue) {
		if (!propertiesWithValue.isEmpty()) {
			this.jdbc.update("DELETE FROM entity_mandatory_marks WHERE entity_id = ? AND property_def_id = ANY (?::uuid[])",
					entityId, uuidArray(propertiesWithValue));
		}
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

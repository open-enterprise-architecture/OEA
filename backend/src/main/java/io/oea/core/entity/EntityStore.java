package io.oea.core.entity;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Entities, their property values and the delta history (V6, ADR-022). Callers run in a
 * transaction; the history is only ever appended.
 */
@Repository
public class EntityStore {

	private static final String SELECT = """
			SELECT id, metatype_id, name, description, is_logical, version, external_id, deleted_at IS NOT NULL
			FROM entities""";

	private final JdbcTemplate jdbc;

	public EntityStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The stored system fields of an entity. */
	public record StoredEntity(int id, UUID metatypeId, String name, String description, boolean logical, int version,
			String externalId, boolean deleted) {
	}

	/** System fields of a new entity or of a change. */
	public record Fields(UUID metatypeId, String name, String description, boolean logical, String externalId) {
	}

	/** A changed system field (old and new value as text). */
	public record FieldChange(String field, String oldValue, String newValue) {
	}

	/** The entity with this external ID, locked for the rest of the transaction. */
	public Optional<StoredEntity> lockByExternalId(String externalId) {
		return this.jdbc.query(SELECT + " WHERE external_id = ? FOR UPDATE", EntityStore::entity, externalId)
			.stream()
			.findFirst();
	}

	/** Whether an active entity with this ID and metatype exists (reference values). */
	public boolean isActive(int id, UUID metatypeId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM entities WHERE id = ? AND metatype_id = ? AND deleted_at IS NULL)",
				Boolean.class, id, metatypeId));
	}

	/** The current property values of an entity by property definition. */
	public Map<UUID, TypedValue> values(int entityId) {
		Map<UUID, TypedValue> values = new HashMap<>();
		this.jdbc.query("""
				SELECT property_def_id, value_text, value_int, value_bool, value_date, value_ref, value_enum
				FROM entity_property_values WHERE entity_id = ?""",
				(rs) -> {
					values.put(rs.getObject(1, UUID.class), value(rs, 2));
				}, entityId);
		return values;
	}

	/** Creates the entity in version 1 with its values and the version header. */
	public int create(Fields fields, Map<UUID, TypedValue> values, long personId, Instant now) {
		Integer id = this.jdbc.queryForObject("""
				WITH created AS (
				    INSERT INTO entities (metatype_id, name, description, is_logical, external_id, created_at, created_by)
				    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id),
				header AS (
				    INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at)
				    SELECT id, 1, ?, ? FROM created)
				SELECT id FROM created""", Integer.class, fields.metatypeId(), fields.name(), fields.description(),
				fields.logical(), fields.externalId(), Timestamp.from(now), personId, personId, Timestamp.from(now));
		insertValues(id, values);
		return id;
	}

	/**
	 * Applies a change as the next version: system fields, values (a {@code null} value removes
	 * it) and the history with the values before the change.
	 */
	public int update(StoredEntity entity, Fields fields, List<FieldChange> fieldChanges,
			Map<UUID, TypedValue> previousValues, Map<UUID, TypedValue> newValues, long personId, Instant now) {
		int version = entity.version() + 1;
		this.jdbc.update("""
				UPDATE entities SET name = ?, description = ?, is_logical = ?, version = ?, updated_at = ?, updated_by = ?
				WHERE id = ?""", fields.name(), fields.description(), fields.logical(), version, Timestamp.from(now),
				personId, entity.id());
		header(entity.id(), version, personId, now);
		for (FieldChange change : fieldChanges) {
			this.jdbc.update("""
					INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value,
					    changed_at, changed_by)
					VALUES (?, ?, ?, ?, ?, ?, ?)""", entity.id(), version, change.field(), change.oldValue(),
					change.newValue(), Timestamp.from(now), personId);
		}
		for (Map.Entry<UUID, TypedValue> change : newValues.entrySet()) {
			UUID definition = change.getKey();
			TypedValue before = previousValues.get(definition);
			this.jdbc.update("""
					INSERT INTO entity_property_value_history (entity_id, property_def_id, entity_version, value_text,
					    value_int, value_bool, value_date, value_ref, value_enum, changed_at, changed_by)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", entity.id(), definition, version,
					(before != null) ? before.text() : null, (before != null) ? before.integer() : null,
					(before != null) ? before.bool() : null, (before != null) ? sqlDate(before) : null,
					(before != null) ? before.reference() : null, (before != null) ? before.enumValue() : null,
					Timestamp.from(now), personId);
			this.jdbc.update("DELETE FROM entity_property_values WHERE entity_id = ? AND property_def_id = ?",
					entity.id(), definition);
		}
		Map<UUID, TypedValue> remaining = new HashMap<>();
		newValues.forEach((definition, value) -> {
			if (value != null) {
				remaining.put(definition, value);
			}
		});
		insertValues(entity.id(), remaining);
		return version;
	}

	private void insertValues(int entityId, Map<UUID, TypedValue> values) {
		if (values.isEmpty()) {
			return;
		}
		List<Object[]> rows = values.entrySet()
			.stream()
			.map((entry) -> new Object[] { entityId, entry.getKey(), entry.getValue().text(), entry.getValue().integer(),
					entry.getValue().bool(), sqlDate(entry.getValue()), entry.getValue().reference(),
					entry.getValue().enumValue() })
			.toList();
		this.jdbc.batchUpdate("""
				INSERT INTO entity_property_values (entity_id, property_def_id, value_text, value_int, value_bool,
				    value_date, value_ref, value_enum)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", rows);
	}

	private void header(int entityId, int version, long personId, Instant now) {
		this.jdbc.update(
				"INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at) VALUES (?, ?, ?, ?)",
				entityId, version, personId, Timestamp.from(now));
	}

	private static Date sqlDate(TypedValue value) {
		return (value.date() != null) ? Date.valueOf(value.date()) : null;
	}

	private static StoredEntity entity(ResultSet rs, int row) throws SQLException {
		return new StoredEntity(rs.getInt(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
				rs.getBoolean(5), rs.getInt(6), rs.getString(7), rs.getBoolean(8));
	}

	private static TypedValue value(ResultSet rs, int first) throws SQLException {
		Date date = rs.getDate(first + 3);
		return new TypedValue(rs.getString(first), rs.getObject(first + 1, Long.class),
				rs.getObject(first + 2, Boolean.class), (date != null) ? date.toLocalDate() : null,
				rs.getObject(first + 4, Integer.class), rs.getString(first + 5));
	}

}

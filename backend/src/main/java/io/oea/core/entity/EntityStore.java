package io.oea.core.entity;

import io.oea.core.metamodel.BuiltInMetatypes;
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
			SELECT id, metatype_id, name, description, is_logical, version, external_id, deleted_at IS NOT NULL,
			       parent_entity_id
			FROM entities""";

	private final JdbcTemplate jdbc;

	public EntityStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The stored system fields of an entity; {@code parentEntityId} is {@code null} at the root level. */
	public record StoredEntity(int id, UUID metatypeId, String name, String description, boolean logical, int version,
			String externalId, boolean deleted, Integer parentEntityId) {

		public StoredEntity(int id, UUID metatypeId, String name, String description, boolean logical, int version,
				String externalId, boolean deleted) {
			this(id, metatypeId, name, description, logical, version, externalId, deleted, null);
		}

	}

	/** System fields of a new entity or of a change. */
	public record Fields(UUID metatypeId, String name, String description, boolean logical, String externalId) {
	}

	/** A changed system field (old and new value as text). */
	public record FieldChange(String field, String oldValue, String newValue) {
	}

	/**
	 * The restore that writes a version: its target version, the optional reason (us-101 D1) and
	 * the keys of the fields of a partial restore, {@code null} for a full one (us-104 D1).
	 */
	public record Restore(int fromVersion, String reason, List<String> fields) {

		public Restore(int fromVersion, String reason) {
			this(fromVersion, reason, null);
		}

	}

	/** An entity as endpoint of a connection (us-036-connection-metatypes D3). */
	public record Endpoint(int id, UUID metatypeId, String name, boolean deleted) {
	}

	/** The stored detail of a connection. */
	public record ConnectionRow(int id, String name, UUID metatypeId, String externalId, int version, Endpoint source,
			Endpoint target) {
	}

	/** The entity with this ID as endpoint. */
	public Optional<Endpoint> endpoint(int id) {
		return this.jdbc.query("SELECT id, metatype_id, name, deleted_at IS NOT NULL FROM entities WHERE id = ?",
				EntityStore::endpoint, id).stream().findFirst();
	}

	/** The entity with this external ID as endpoint. */
	public Optional<Endpoint> endpointByExternalId(String externalId) {
		return this.jdbc.query(
				"SELECT id, metatype_id, name, deleted_at IS NOT NULL FROM entities WHERE external_id = ?",
				EntityStore::endpoint, externalId).stream().findFirst();
	}

	/** Source and target of a connection, or empty if the entity is no connection. */
	public Optional<int[]> connectionEndpoints(int entityId) {
		return this.jdbc.query("SELECT source_entity_id, target_entity_id FROM entity_connections WHERE entity_id = ?",
				(rs, n) -> new int[] { rs.getInt(1), rs.getInt(2) }, entityId).stream().findFirst();
	}

	/** Stores the fixed endpoints of a new connection. */
	public void insertConnection(int entityId, int sourceId, int targetId) {
		this.jdbc.update("INSERT INTO entity_connections (entity_id, source_entity_id, target_entity_id) VALUES (?, ?, ?)",
				entityId, sourceId, targetId);
	}

	/** The connection with its endpoints. */
	public Optional<ConnectionRow> connection(int id) {
		return this.jdbc.query("""
				SELECT e.id, e.name, e.metatype_id, e.external_id, e.version,
				       s.id, s.metatype_id, s.name, s.deleted_at IS NOT NULL,
				       t.id, t.metatype_id, t.name, t.deleted_at IS NOT NULL
				FROM entity_connections c JOIN entities e ON e.id = c.entity_id
				JOIN entities s ON s.id = c.source_entity_id JOIN entities t ON t.id = c.target_entity_id
				WHERE c.entity_id = ?""", (rs, n) -> new ConnectionRow(rs.getInt(1), rs.getString(2),
				rs.getObject(3, UUID.class), rs.getString(4), rs.getInt(5),
				new Endpoint(rs.getInt(6), rs.getObject(7, UUID.class), rs.getString(8), rs.getBoolean(9)),
				new Endpoint(rs.getInt(10), rs.getObject(11, UUID.class), rs.getString(12), rs.getBoolean(13))), id)
			.stream()
			.findFirst();
	}

	private static Endpoint endpoint(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new Endpoint(rs.getInt(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getBoolean(4));
	}

	/** The entity with this external ID, locked for the rest of the transaction. */
	public Optional<StoredEntity> lockByExternalId(String externalId) {
		return this.jdbc.query(SELECT + " WHERE external_id = ? FOR UPDATE", EntityStore::entity, externalId)
			.stream()
			.findFirst();
	}

	/** The entity with this ID, without a lock. */
	public Optional<StoredEntity> find(int id) {
		return this.jdbc.query(SELECT + " WHERE id = ?", EntityStore::entity, id).stream().findFirst();
	}

	/** The entity with this ID, locked for the rest of the transaction (FOR UPDATE, us-101 D1). */
	public Optional<StoredEntity> lock(int id) {
		return this.jdbc.query(SELECT + " WHERE id = ? FOR UPDATE", EntityStore::entity, id).stream().findFirst();
	}

	/**
	 * The entity with this ID, locked against changes for the rest of the transaction (FOR SHARE);
	 * readers and other share locks proceed (design D2 of us-041-modified-delta).
	 */
	public Optional<StoredEntity> lockForShare(int id) {
		return this.jdbc.query(SELECT + " WHERE id = ? FOR SHARE", EntityStore::entity, id).stream().findFirst();
	}

	/**
	 * Soft-deletes the entity (ADR-019; us-152-solution-go-live D4): it leaves catalogs and the
	 * baseline, and its history stays.
	 */
	public void softDelete(int id, long personId, Instant now, String reason) {
		this.jdbc.update("""
				UPDATE entities SET deleted_at = ?, deleted_by = ?, deletion_reason = ? WHERE id = ? AND deleted_at IS NULL""",
				Timestamp.from(now), personId, reason, id);
	}

	/** The active connections whose source or target is the entity, by ID. */
	public List<Integer> activeConnectionsOf(int entityId) {
		return this.jdbc.queryForList("""
				SELECT c.entity_id FROM entity_connections c JOIN entities e ON e.id = c.entity_id
				WHERE (c.source_entity_id = ? OR c.target_entity_id = ?) AND e.deleted_at IS NULL
				ORDER BY c.entity_id""", Integer.class, entityId, entityId);
	}

	/** Whether an active entity with this ID and metatype exists (reference values). */
	public boolean isActive(int id, UUID metatypeId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM entities WHERE id = ? AND metatype_id = ? AND deleted_at IS NULL)",
				Boolean.class, id, metatypeId));
	}

	/**
	 * An entity of the solution browser with the abbreviation of its metatype ({@code null} when it
	 * has none), the number of its children that are not deleted, whether it is a structuring aid
	 * and its version (design D5 of us-135-component-hierarchy).
	 */
	public record BrowseRow(int id, String name, String abbreviation, int childCount, boolean structuringAid,
			int version) {
	}

	private static final String BROWSE = """
			SELECT e.id, e.name, m.abbreviation,
			       (SELECT count(*) FROM entities c WHERE c.parent_entity_id = e.id AND c.deleted_at IS NULL),
			       e.metatype_id = ?, e.version
			FROM entities e JOIN metatypes m ON m.id = e.metatype_id""";

	/** The element entities at the root level: without a parent, or whose parent is deleted. */
	private static final String ROOT_LEVEL = """
			e.deleted_at IS NULL AND NOT m.is_connection AND (e.parent_entity_id IS NULL
			    OR NOT EXISTS (SELECT 1 FROM entities p WHERE p.id = e.parent_entity_id AND p.deleted_at IS NULL))""";

	/** The entities that are not deleted: elements, or connections with {@code connection} (design D2 of us-132-solution-browser). */
	public int countActive(boolean connection) {
		return this.jdbc.queryForObject("""
				SELECT count(*) FROM entities e JOIN metatypes m ON m.id = e.metatype_id
				WHERE e.deleted_at IS NULL AND m.is_connection = ?""", Integer.class, connection);
	}

	/** The element entities at the root level (US-135). */
	public int countRoot() {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM entities e JOIN metatypes m ON m.id = e.metatype_id WHERE " + ROOT_LEVEL,
				Integer.class);
	}

	/** One page of the root level, by name regardless of case, then by ID. */
	public List<BrowseRow> pageRoot(int limit, long offset) {
		return this.jdbc.query(BROWSE + " WHERE " + ROOT_LEVEL + " ORDER BY lower(e.name), e.id LIMIT ? OFFSET ?",
				EntityStore::browseRow, BuiltInMetatypes.STRUCTURING_AID, limit, offset);
	}

	/** The children of an element entity that are not deleted. */
	public int countChildren(int parentId) {
		return this.jdbc.queryForObject(
				"SELECT count(*) FROM entities WHERE parent_entity_id = ? AND deleted_at IS NULL", Integer.class,
				parentId);
	}

	/** One page of the children of an element entity, by name regardless of case, then by ID. */
	public List<BrowseRow> pageChildren(int parentId, int limit, long offset) {
		return this.jdbc.query(BROWSE + """
				 WHERE e.parent_entity_id = ? AND e.deleted_at IS NULL
				ORDER BY lower(e.name), e.id LIMIT ? OFFSET ?""", EntityStore::browseRow,
				BuiltInMetatypes.STRUCTURING_AID, parentId, limit, offset);
	}

	/** One page of the entities that are not deleted, by name regardless of case, then by ID. */
	public List<BrowseRow> pageActive(boolean connection, int limit, long offset) {
		return this.jdbc.query(BROWSE + """
				 WHERE e.deleted_at IS NULL AND m.is_connection = ?
				ORDER BY lower(e.name), e.id LIMIT ? OFFSET ?""", EntityStore::browseRow,
				BuiltInMetatypes.STRUCTURING_AID, connection, limit, offset);
	}

	/** Whether an element entity that is not deleted has this ID. */
	public boolean isActiveElement(int id) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM entities e JOIN metatypes m ON m.id = e.metatype_id
				               WHERE e.id = ? AND e.deleted_at IS NULL AND NOT m.is_connection)""", Boolean.class, id));
	}

	private static BrowseRow browseRow(ResultSet rs, int row) throws SQLException {
		return new BrowseRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBoolean(5),
				rs.getInt(6));
	}

	/**
	 * The children of an entity that are not deleted, locked in ID order for the rest of the
	 * transaction (deleting a structuring aid).
	 */
	public List<StoredEntity> lockChildren(int parentId) {
		return this.jdbc.query(SELECT + " WHERE parent_entity_id = ? AND deleted_at IS NULL ORDER BY id FOR UPDATE",
				EntityStore::entity, parentId);
	}

	/**
	 * Sets or clears the parent as the next version (design D3 of us-135-component-hierarchy): the
	 * entity, its version header and the history row of the field {@code parent_entity_id} with the
	 * parent before the change. Returns the new version.
	 */
	public int updateParent(StoredEntity entity, Integer parentId, long personId, Instant now) {
		int version = entity.version() + 1;
		this.jdbc.update("""
				UPDATE entities SET parent_entity_id = ?, version = ?, updated_at = ?, updated_by = ? WHERE id = ?""",
				parentId, version, Timestamp.from(now), personId, entity.id());
		header(entity.id(), version, personId, now);
		this.jdbc.update("""
				INSERT INTO entity_system_field_history (entity_id, entity_version, field_name, old_value, new_value,
				    changed_at, changed_by)
				VALUES (?, ?, 'parent_entity_id', ?, ?, ?, ?)""", entity.id(), version,
				(entity.parentEntityId() != null) ? entity.parentEntityId().toString() : null,
				(parentId != null) ? parentId.toString() : null, Timestamp.from(now), personId);
		return version;
	}

	/** Sets the hierarchy fields of an entity that was just created (version 1, no history row). */
	public void placeCreated(int id, Integer parentId, boolean structuralGrouper, boolean autoCreated) {
		this.jdbc.update("""
				UPDATE entities SET parent_entity_id = ?, is_structural_grouper = ?, auto_created = ? WHERE id = ?""",
				parentId, structuralGrouper, autoCreated, id);
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
		return update(entity, fields, fieldChanges, previousValues, newValues, personId, now, null);
	}

	/**
	 * As {@link #update(StoredEntity, Fields, List, Map, Map, long, Instant)}; a restore is
	 * recorded in the version header.
	 */
	public int update(StoredEntity entity, Fields fields, List<FieldChange> fieldChanges,
			Map<UUID, TypedValue> previousValues, Map<UUID, TypedValue> newValues, long personId, Instant now,
			Restore restore) {
		int version = entity.version() + 1;
		this.jdbc.update("""
				UPDATE entities SET name = ?, description = ?, is_logical = ?, version = ?, updated_at = ?, updated_by = ?
				WHERE id = ?""", fields.name(), fields.description(), fields.logical(), version, Timestamp.from(now),
				personId, entity.id());
		this.jdbc.update("""
				INSERT INTO entity_version_headers (entity_id, version, changed_by, changed_at, restored_from_version,
				    change_reason, restored_fields)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", entity.id(), version, personId, Timestamp.from(now),
				(restore != null) ? restore.fromVersion() : null, (restore != null) ? restore.reason() : null,
				(restore != null && restore.fields() != null) ? restore.fields().toArray(String[]::new) : null);
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
				rs.getBoolean(5), rs.getInt(6), rs.getString(7), rs.getBoolean(8), rs.getObject(9, Integer.class));
	}

	private static TypedValue value(ResultSet rs, int first) throws SQLException {
		Date date = rs.getDate(first + 3);
		return new TypedValue(rs.getString(first), rs.getObject(first + 1, Long.class),
				rs.getObject(first + 2, Boolean.class), (date != null) ? date.toLocalDate() : null,
				rs.getObject(first + 4, Integer.class), rs.getString(first + 5));
	}

}

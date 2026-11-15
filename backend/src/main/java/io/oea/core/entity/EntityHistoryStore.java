package io.oea.core.entity;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads the version history of an entity (V6, ADR-022; design D1 of us-098-entity-history): the
 * version headers with filters and keyset paging on the version number, the fields each version
 * changed, the persons who made versions and the creation. Values are not read.
 */
@Repository
public class EntityHistoryStore {

	private static final String HEADERS = """
			SELECT h.version, h.changed_at, h.changed_by, p.name, h.restored_from_version, h.change_reason,
			       h.restored_fields
			FROM entity_version_headers h JOIN persons p ON p.id = h.changed_by
			WHERE h.entity_id = ?""";

	private final JdbcTemplate jdbc;

	public EntityHistoryStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The entity whose history is read, deleted or not. */
	public record HistoryEntity(int id, UUID metatypeId, String name, int version) {
	}

	/** A person who made a version. */
	public record Actor(long id, String name) {
	}

	/** A version header; a version written by a restore names its target version and reason (us-101). */
	public record Version(int version, Instant changedAt, Actor changedBy, Integer restoredFromVersion,
			String reason, List<String> restoredFields) {

		public Version(int version, Instant changedAt, Actor changedBy) {
			this(version, changedAt, changedBy, null, null, null);
		}

	}

	/** A field changed by a version: a system field ({@code field_name}) or a property definition. */
	public record ChangedField(String systemField, UUID propertyDefId) {
	}

	/** The filters of a history read; each one is optional, {@code from} and {@code to} are inclusive. */
	public record Filter(Instant from, Instant to, Long actorId) {
	}

	/** The entity with this ID, if a row exists. */
	public Optional<HistoryEntity> entity(int id) {
		return this.jdbc
			.query("SELECT id, metatype_id, name, version FROM entities WHERE id = ?",
					(rs, n) -> new HistoryEntity(rs.getInt(1), rs.getObject(2, UUID.class), rs.getString(3),
							rs.getInt(4)),
					id)
			.stream()
			.findFirst();
	}

	/**
	 * The versions that match the filters, newest first: after {@code beforeVersion} (keyset,
	 * exclusive) when it is set, otherwise from {@code offset}.
	 */
	public List<Version> versions(int entityId, Filter filter, Integer beforeVersion, long offset, int limit) {
		StringBuilder sql = new StringBuilder(HEADERS);
		List<Object> arguments = new ArrayList<>();
		arguments.add(entityId);
		where(sql, arguments, filter);
		if (beforeVersion != null) {
			sql.append(" AND h.version < ?");
			arguments.add(beforeVersion);
		}
		sql.append(" ORDER BY h.version DESC LIMIT ").append(limit);
		if (beforeVersion == null && offset > 0) {
			sql.append(" OFFSET ").append(offset);
		}
		return this.jdbc.query(sql.toString(), (rs, n) -> new Version(rs.getInt(1), rs.getTimestamp(2).toInstant(),
				new Actor(rs.getLong(3), rs.getString(4)), rs.getObject(5, Integer.class), rs.getString(6),
				textArray(rs.getArray(7))), arguments.toArray());
	}

	/** The number of versions that match the filters. */
	public long count(int entityId, Filter filter) {
		StringBuilder sql = new StringBuilder("SELECT count(*) FROM entity_version_headers h WHERE h.entity_id = ?");
		List<Object> arguments = new ArrayList<>();
		arguments.add(entityId);
		where(sql, arguments, filter);
		Long count = this.jdbc.queryForObject(sql.toString(), Long.class, arguments.toArray());
		return (count != null) ? count : 0;
	}

	/**
	 * The fields changed by each of the versions, system fields first in the order of their
	 * history rows, then properties; versions without changed fields are absent.
	 */
	public Map<Integer, List<ChangedField>> changes(int entityId, Collection<Integer> versions) {
		Map<Integer, List<ChangedField>> changes = new LinkedHashMap<>();
		if (versions.isEmpty()) {
			return changes;
		}
		Integer[] numbers = versions.toArray(Integer[]::new);
		this.jdbc.query("""
				SELECT entity_version, field_name FROM entity_system_field_history
				WHERE entity_id = ? AND entity_version = ANY(?) ORDER BY entity_version, id""", (rs) -> {
			changes.computeIfAbsent(rs.getInt(1), (key) -> new ArrayList<>()).add(new ChangedField(rs.getString(2), null));
		}, entityId, numbers);
		this.jdbc.query("""
				SELECT entity_version, property_def_id FROM entity_property_value_history
				WHERE entity_id = ? AND entity_version = ANY(?) ORDER BY entity_version, id""", (rs) -> {
			changes.computeIfAbsent(rs.getInt(1), (key) -> new ArrayList<>())
				.add(new ChangedField(null, rs.getObject(2, UUID.class)));
		}, entityId, numbers);
		return changes;
	}

	/** A connection of an entity changed or created after a time (us-104-partial-restore D3). */
	public record ChangedConnection(int entityId, String name, String otherEndpoint, boolean outgoing, boolean added) {
	}

	/**
	 * The connections in which the entity is source or target and that have a version after
	 * {@code time}: created after it ({@code added}) or changed; created ones first, then by name.
	 */
	public List<ChangedConnection> connectionsChangedSince(int entityId, Instant time) {
		return this.jdbc.query("""
				SELECT c.entity_id, e.name, o.name, c.source_entity_id = ?, first.changed_at > ?
				FROM entity_connections c
				JOIN entities e ON e.id = c.entity_id
				JOIN entities o ON o.id = CASE WHEN c.source_entity_id = ? THEN c.target_entity_id ELSE c.source_entity_id END
				LEFT JOIN entity_version_headers first ON first.entity_id = c.entity_id AND first.version = 1
				WHERE (c.source_entity_id = ? OR c.target_entity_id = ?)
				  AND EXISTS (SELECT 1 FROM entity_version_headers h WHERE h.entity_id = c.entity_id AND h.changed_at > ?)
				ORDER BY 5 DESC, lower(e.name), c.entity_id""",
				(rs, n) -> new ChangedConnection(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
						rs.getBoolean(5)),
				entityId, Timestamp.from(time), entityId, entityId, entityId, Timestamp.from(time));
	}

	private static List<String> textArray(java.sql.Array array) throws java.sql.SQLException {
		return (array != null) ? List.of((String[]) array.getArray()) : null;
	}

	/** Every person who made a version of the entity, without filters, by name. */
	public List<Actor> actors(int entityId) {
		return this.jdbc.query("""
				SELECT p.id, p.name FROM persons p
				WHERE p.id IN (SELECT changed_by FROM entity_version_headers WHERE entity_id = ?)
				ORDER BY lower(p.name), p.id""", (rs, n) -> new Actor(rs.getLong(1), rs.getString(2)), entityId);
	}

	/**
	 * The creation: version 1, or the creation fields of the entity row when it has no version
	 * header (rows written outside the entity writer).
	 */
	public Optional<Version> creation(int entityId) {
		return this.jdbc.query("""
				SELECT coalesce(h.changed_at, e.created_at), p.id, p.name
				FROM entities e
				LEFT JOIN entity_version_headers h ON h.entity_id = e.id AND h.version = 1
				JOIN persons p ON p.id = coalesce(h.changed_by, e.created_by)
				WHERE e.id = ?""", (rs, n) -> new Version(1, rs.getTimestamp(1).toInstant(),
				new Actor(rs.getLong(2), rs.getString(3))), entityId).stream().findFirst();
	}

	private static void where(StringBuilder sql, List<Object> arguments, Filter filter) {
		if (filter.from() != null) {
			sql.append(" AND h.changed_at >= ?");
			arguments.add(filter.from().atOffset(ZoneOffset.UTC));
		}
		if (filter.to() != null) {
			sql.append(" AND h.changed_at <= ?");
			arguments.add(filter.to().atOffset(ZoneOffset.UTC));
		}
		if (filter.actorId() != null) {
			sql.append(" AND h.changed_by = ?");
			arguments.add(filter.actorId());
		}
	}

}

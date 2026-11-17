package io.oea.core.metamodel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The viewpoints of the instance (design D1, D2 of us-090-viewpoints; V39): a name, a description
 * and the allowed element and connection metatypes, with a version for optimistic locking.
 */
@Repository
public class ViewpointStore {

	private static final String SELECT = """
			SELECT v.id, v.name, v.description, v.version, v.created_at, v.created_by, p.name
			FROM viewpoints v JOIN persons p ON p.id = v.created_by
			""";

	private final JdbcTemplate jdbc;

	public ViewpointStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A metatype allowed by a viewpoint. */
	public record TypeRow(UUID id, String name, String abbreviation) {
	}

	/** A viewpoint with its allowed types, each ordered by name. */
	public record ViewpointRow(UUID id, String name, String description, int version, Instant createdAt,
			long createdById, String createdByName, List<TypeRow> metatypes, List<TypeRow> connectionTypes) {
	}

	/** The kind of a metatype: a connection or not, and whether it belongs to the instance configuration. */
	public record Kind(boolean connection, boolean instance) {
	}

	/** The fields a write sets. */
	public record Fields(String name, String description, List<UUID> metatypeIds, List<UUID> connectionTypeIds) {
	}

	/** All viewpoints, ordered by name case-insensitively. */
	public List<ViewpointRow> list() {
		return withTypes(this.jdbc.query(SELECT + "ORDER BY lower(v.name), v.id", ViewpointStore::row));
	}

	/** One viewpoint. */
	public Optional<ViewpointRow> find(UUID id) {
		return withTypes(this.jdbc.query(SELECT + "WHERE v.id = ?", ViewpointStore::row, id)).stream().findFirst();
	}

	/** The current version of the viewpoint, locked for a write; empty for an unknown one. */
	public Optional<Integer> lockVersion(UUID id) {
		return this.jdbc.queryForList("SELECT version FROM viewpoints WHERE id = ? FOR UPDATE", Integer.class, id)
			.stream()
			.findFirst();
	}

	/** Whether another viewpoint has the name, compared case-insensitively. */
	public boolean nameTaken(String name, UUID except) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM viewpoints WHERE lower(name) = lower(?) AND id IS DISTINCT FROM ?)",
				Boolean.class, name, except));
	}

	/** The kinds of the metatypes; unknown IDs are missing from the map. */
	public Map<UUID, Kind> kinds(Collection<UUID> ids, UUID instanceConfiguration) {
		Map<UUID, Kind> kinds = new HashMap<>();
		if (ids.isEmpty()) {
			return kinds;
		}
		this.jdbc.query("SELECT id, is_connection, config_id = ? FROM metatypes WHERE id = ANY (?::uuid[])",
				(ResultSet rs) -> {
					kinds.put(rs.getObject(1, UUID.class), new Kind(rs.getBoolean(2), rs.getBoolean(3)));
				}, instanceConfiguration, array(ids));
		return kinds;
	}

	/** Stores a new viewpoint with version 1; {@code false} when a parallel write took the name. */
	public boolean insert(UUID id, Fields fields, long personId, Instant now) {
		int inserted = this.jdbc.update("""
				INSERT INTO viewpoints (id, name, description, created_at, created_by) VALUES (?, ?, ?, ?, ?)
				ON CONFLICT DO NOTHING""", id, fields.name(), fields.description(), Timestamp.from(now), personId);
		if (inserted == 0) {
			return false;
		}
		links(id, fields);
		return true;
	}

	/** Replaces the fields and raises the version by one; the caller locked the viewpoint and checked the name. */
	public void update(UUID id, Fields fields, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE viewpoints SET name = ?, description = ?, version = version + 1, updated_at = ?, updated_by = ?
				WHERE id = ?""", fields.name(), fields.description(), Timestamp.from(now), personId, id);
		this.jdbc.update("DELETE FROM viewpoint_metatypes WHERE viewpoint_id = ?", id);
		this.jdbc.update("DELETE FROM viewpoint_connection_types WHERE viewpoint_id = ?", id);
		links(id, fields);
	}

	/** Deletes the viewpoint with its links. */
	public void delete(UUID id) {
		this.jdbc.update("DELETE FROM viewpoints WHERE id = ?", id);
	}

	private void links(UUID id, Fields fields) {
		for (UUID metatype : fields.metatypeIds()) {
			this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", id, metatype);
		}
		for (UUID metatype : fields.connectionTypeIds()) {
			this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?)", id,
					metatype);
		}
	}

	/** Adds the allowed types to the rows, each ordered by name. */
	private List<ViewpointRow> withTypes(List<ViewpointRow> rows) {
		if (rows.isEmpty()) {
			return rows;
		}
		Map<UUID, List<TypeRow>> metatypes = types("viewpoint_metatypes", rows);
		Map<UUID, List<TypeRow>> connections = types("viewpoint_connection_types", rows);
		List<ViewpointRow> complete = new ArrayList<>();
		for (ViewpointRow row : rows) {
			complete.add(new ViewpointRow(row.id(), row.name(), row.description(), row.version(), row.createdAt(),
					row.createdById(), row.createdByName(), metatypes.getOrDefault(row.id(), List.of()),
					connections.getOrDefault(row.id(), List.of())));
		}
		return complete;
	}

	private Map<UUID, List<TypeRow>> types(String table, List<ViewpointRow> rows) {
		Map<UUID, List<TypeRow>> types = new LinkedHashMap<>();
		this.jdbc.query("SELECT l.viewpoint_id, m.id, m.name, coalesce(m.abbreviation, '') FROM " + table + " l"
				+ " JOIN metatypes m ON m.id = l.metatype_id WHERE l.viewpoint_id = ANY (?::uuid[])"
				+ " ORDER BY lower(m.name), m.id", (ResultSet rs) -> {
					types.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new ArrayList<>())
						.add(new TypeRow(rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)));
				}, array(rows.stream().map(ViewpointRow::id).toList()));
		return types;
	}

	private static String array(Collection<UUID> ids) {
		return "{" + String.join(",", ids.stream().map(UUID::toString).toList()) + "}";
	}

	private static ViewpointRow row(ResultSet rs, int row) throws SQLException {
		return new ViewpointRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4),
				rs.getTimestamp(5).toInstant(), rs.getLong(6), rs.getString(7), List.of(), List.of());
	}

}

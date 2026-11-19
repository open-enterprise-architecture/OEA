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
			SELECT v.id, v.name, v.description, v.version, v.created_at, v.created_by, p.name, v.notation,
			       v.viewpoint_type, v.source_package_id, v.source_package_version
			FROM viewpoints v JOIN persons p ON p.id = v.created_by
			""";

	private final JdbcTemplate jdbc;

	public ViewpointStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A metatype allowed by a viewpoint. */
	public record TypeRow(UUID id, String name, String abbreviation) {
	}

	/** The mapping of an allowed metatype to a notation element; the hint may be {@code null}. */
	public record MappingRow(UUID metatypeId, String notationElement, String visualHint) {
	}

	/**
	 * The default size in pt of an allowed metatype: width and height for an element metatype, the
	 * length for a connection metatype; the other values are {@code null}.
	 */
	public record SizeRow(UUID metatypeId, Integer width, Integer height, Integer length) {
	}

	/**
	 * A viewpoint with its allowed types, each ordered by name, and its mappings. {@code viewpointType}
	 * is {@code system-defined} or {@code user-defined}; the source package is set for the former only.
	 */
	public record ViewpointRow(UUID id, String name, String description, int version, Instant createdAt,
			long createdById, String createdByName, String notation, String viewpointType, String sourcePackageId,
			String sourcePackageVersion, List<TypeRow> metatypes, List<TypeRow> connectionTypes,
			List<MappingRow> mappings, List<SizeRow> sizes) {
	}

	/** The origin of a viewpoint that has the ID or the name of an imported one. */
	public record Owner(UUID id, String viewpointType, String sourcePackageId) {
	}

	/** The kind of a metatype: a connection or not, and whether it belongs to the instance configuration. */
	public record Kind(boolean connection, boolean instance) {
	}

	/** The fields a write sets. */
	public record Fields(String name, String description, String notation, List<UUID> metatypeIds,
			List<UUID> connectionTypeIds, List<MappingRow> mappings, List<SizeRow> sizes) {
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
				INSERT INTO viewpoints (id, name, description, notation, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT DO NOTHING""", id, fields.name(), fields.description(), fields.notation(),
				Timestamp.from(now), personId);
		if (inserted == 0) {
			return false;
		}
		links(id, fields);
		return true;
	}

	/** Replaces the fields and raises the version by one; the caller locked the viewpoint and checked the name. */
	public void update(UUID id, Fields fields, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE viewpoints SET name = ?, description = ?, notation = ?, version = version + 1, updated_at = ?,
				       updated_by = ?
				WHERE id = ? AND viewpoint_type = 'user-defined'""", fields.name(), fields.description(),
				fields.notation(), Timestamp.from(now), personId, id);
		replaceLinks(id, fields);
	}

	/** The viewpoints that have the ID or, case-insensitively, the name, with their origin. */
	public List<Owner> owners(UUID id, String name) {
		return this.jdbc.query("""
				SELECT id, viewpoint_type, source_package_id FROM viewpoints
				WHERE id = ? OR (lower(name) = lower(?) AND id <> ?)""",
				(rs, n) -> new Owner(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), id, name, id);
	}

	/** The IDs of the viewpoints a package installed. */
	public List<UUID> packageViewpointIds(String packageId) {
		return this.jdbc.queryForList("SELECT id FROM viewpoints WHERE source_package_id = ? ORDER BY id",
				UUID.class, packageId);
	}

	/**
	 * Installs or replaces a system-defined viewpoint of the package, with its links and mappings.
	 * The ID and the package were checked by the caller; a re-import keeps the creator and the
	 * creation time and records the importing person as updater.
	 *
	 * @return {@code true} if the viewpoint was created, {@code false} if it was replaced
	 */
	public boolean upsertSystemDefined(UUID id, Fields fields, String packageId, String packageVersion,
			long personId, Instant now) {
		boolean created = this.jdbc.update("""
				INSERT INTO viewpoints (id, name, description, notation, viewpoint_type, source_package_id,
				    source_package_version, created_at, created_by)
				VALUES (?, ?, ?, ?, 'system-defined', ?, ?, ?, ?)
				ON CONFLICT (id) DO NOTHING""", id, fields.name(), fields.description(), fields.notation(),
				packageId, packageVersion, Timestamp.from(now), personId) == 1;
		if (!created) {
			this.jdbc.update("""
					UPDATE viewpoints SET name = ?, description = ?, notation = ?, source_package_version = ?,
					       version = version + 1, updated_at = ?, updated_by = ?
					WHERE id = ? AND source_package_id = ?""", fields.name(), fields.description(),
					fields.notation(), packageVersion, Timestamp.from(now), personId, id, packageId);
		}
		replaceLinks(id, fields);
		return created;
	}

	/** The origin of the viewpoint, without locking it; empty for an unknown one. */
	public Optional<String> viewpointType(UUID id) {
		return this.jdbc.queryForList("SELECT viewpoint_type FROM viewpoints WHERE id = ?", String.class, id)
			.stream()
			.findFirst();
	}

	private void replaceLinks(UUID id, Fields fields) {
		this.jdbc.update("DELETE FROM viewpoint_metatypes WHERE viewpoint_id = ?", id);
		this.jdbc.update("DELETE FROM viewpoint_connection_types WHERE viewpoint_id = ?", id);
		this.jdbc.update("DELETE FROM viewpoint_mappings WHERE viewpoint_id = ?", id);
		this.jdbc.update("DELETE FROM viewpoint_sizes WHERE viewpoint_id = ?", id);
		links(id, fields);
	}

	/** Deletes the viewpoint with its links. */
	public void delete(UUID id) {
		this.jdbc.update("DELETE FROM viewpoints WHERE id = ?", id);
	}

	/** Gives the viewpoints of the package placeholder names, so that the import can set any names. */
	public void releaseNames(String packageId) {
		this.jdbc.update("UPDATE viewpoints SET name = id::text WHERE source_package_id = ?", packageId);
	}

	/** Deletes a viewpoint of the package, with its links and mappings; others stay. */
	public void deletePackageViewpoint(UUID id, String packageId) {
		this.jdbc.update("DELETE FROM viewpoints WHERE id = ? AND source_package_id = ?", id, packageId);
	}

	private void links(UUID id, Fields fields) {
		for (UUID metatype : fields.metatypeIds()) {
			this.jdbc.update("INSERT INTO viewpoint_metatypes (viewpoint_id, metatype_id) VALUES (?, ?)", id, metatype);
		}
		for (UUID metatype : fields.connectionTypeIds()) {
			this.jdbc.update("INSERT INTO viewpoint_connection_types (viewpoint_id, metatype_id) VALUES (?, ?)", id,
					metatype);
		}
		for (MappingRow mapping : fields.mappings()) {
			this.jdbc.update("""
					INSERT INTO viewpoint_mappings (viewpoint_id, metatype_id, notation_element, visual_hint)
					VALUES (?, ?, ?, ?)""", id, mapping.metatypeId(), mapping.notationElement(),
					mapping.visualHint());
		}
		for (SizeRow size : fields.sizes()) {
			this.jdbc.update("""
					INSERT INTO viewpoint_sizes (viewpoint_id, metatype_id, width_pt, height_pt, length_pt)
					VALUES (?, ?, ?, ?, ?)""", id, size.metatypeId(), size.width(), size.height(), size.length());
		}
	}

	/** Adds the allowed types to the rows, each ordered by name. */
	private List<ViewpointRow> withTypes(List<ViewpointRow> rows) {
		if (rows.isEmpty()) {
			return rows;
		}
		Map<UUID, List<TypeRow>> metatypes = types("viewpoint_metatypes", rows);
		Map<UUID, List<TypeRow>> connections = types("viewpoint_connection_types", rows);
		Map<UUID, List<MappingRow>> mappings = mappings(rows);
		Map<UUID, List<SizeRow>> sizes = sizes(rows);
		List<ViewpointRow> complete = new ArrayList<>();
		for (ViewpointRow row : rows) {
			complete.add(new ViewpointRow(row.id(), row.name(), row.description(), row.version(), row.createdAt(),
					row.createdById(), row.createdByName(), row.notation(), row.viewpointType(),
					row.sourcePackageId(), row.sourcePackageVersion(), metatypes.getOrDefault(row.id(), List.of()),
					connections.getOrDefault(row.id(), List.of()), mappings.getOrDefault(row.id(), List.of()),
					sizes.getOrDefault(row.id(), List.of())));
		}
		return complete;
	}

	private Map<UUID, List<MappingRow>> mappings(List<ViewpointRow> rows) {
		Map<UUID, List<MappingRow>> mappings = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT l.viewpoint_id, l.metatype_id, l.notation_element, l.visual_hint
				FROM viewpoint_mappings l JOIN metatypes m ON m.id = l.metatype_id
				WHERE l.viewpoint_id = ANY (?::uuid[]) ORDER BY lower(m.name), m.id""", (ResultSet rs) -> {
					mappings.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new ArrayList<>())
						.add(new MappingRow(rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)));
				}, array(rows.stream().map(ViewpointRow::id).toList()));
		return mappings;
	}

	private Map<UUID, List<SizeRow>> sizes(List<ViewpointRow> rows) {
		Map<UUID, List<SizeRow>> sizes = new LinkedHashMap<>();
		this.jdbc.query("""
				SELECT l.viewpoint_id, l.metatype_id, l.width_pt, l.height_pt, l.length_pt
				FROM viewpoint_sizes l JOIN metatypes m ON m.id = l.metatype_id
				WHERE l.viewpoint_id = ANY (?::uuid[]) ORDER BY lower(m.name), m.id""", (ResultSet rs) -> {
					sizes.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new ArrayList<>())
						.add(new SizeRow(rs.getObject(2, UUID.class), (Integer) rs.getObject(3),
								(Integer) rs.getObject(4), (Integer) rs.getObject(5)));
				}, array(rows.stream().map(ViewpointRow::id).toList()));
		return sizes;
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
				rs.getTimestamp(5).toInstant(), rs.getLong(6), rs.getString(7), rs.getString(8), rs.getString(9),
				rs.getString(10), rs.getString(11), List.of(), List.of(), List.of(), List.of());
	}

}

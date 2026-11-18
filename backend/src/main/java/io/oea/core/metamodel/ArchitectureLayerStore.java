package io.oea.core.metamodel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The architecture layers of the instance metamodel (design D1, D3 of s17-architecture-layers; V44):
 * a name unique regardless of case, a dense order, an origin and a version for optimistic locking.
 * The version is raised by the callers through {@code MetamodelVersions}; the limits of the
 * number and the rules of the origin are checked by the service.
 */
@Repository
public class ArchitectureLayerStore {

	private static final String SELECT = """
			SELECT l.id, l.name, l.sort_order, l.scope, l.source_package_id, l.source_package_version, l.version,
			       (SELECT count(*) FROM metatypes m WHERE m.architecture_layer_id = l.id)
			FROM architecture_layers l
			""";

	private final JdbcTemplate jdbc;

	public ArchitectureLayerStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A layer with the number of metatypes of all configurations that have it. */
	public record LayerRow(UUID id, String name, int sortOrder, String scope, String sourcePackageId,
			String sourcePackageVersion, int version, long metatypeCount) {
	}

	/** The origin of a layer that has the ID or the name of an imported one. */
	public record Owner(String scope, String packageId) {
	}

	/** All layers in their order. */
	public List<LayerRow> list() {
		return this.jdbc.query(SELECT + "ORDER BY l.sort_order, lower(l.name), l.id", ArchitectureLayerStore::row);
	}

	/** One layer. */
	public Optional<LayerRow> find(UUID id) {
		return this.jdbc.query(SELECT + "WHERE l.id = ?", ArchitectureLayerStore::row, id).stream().findFirst();
	}

	/** Whether the layer exists. */
	public boolean exists(UUID id) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM architecture_layers WHERE id = ?)",
				Boolean.class, id));
	}

	/** The layer of each of the metatypes that has one; metatypes without a layer are missing from the map. */
	public java.util.Map<UUID, UUID> layerByMetatype(Collection<UUID> metatypeIds) {
		java.util.Map<UUID, UUID> layers = new java.util.HashMap<>();
		if (metatypeIds.isEmpty()) {
			return layers;
		}
		this.jdbc.query("""
				SELECT id, architecture_layer_id FROM metatypes
				WHERE architecture_layer_id IS NOT NULL AND id = ANY (?::uuid[])""", (java.sql.ResultSet rs) -> {
					layers.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
				}, array(metatypeIds));
		return layers;
	}

	/** The layer of the metatype, or empty for none. */
	public Optional<UUID> layerOf(UUID metatypeId) {
		return this.jdbc
			.queryForList("SELECT architecture_layer_id FROM metatypes WHERE id = ? AND architecture_layer_id IS NOT NULL",
					UUID.class, metatypeId)
			.stream()
			.findFirst();
	}

	/** The number of layers. */
	public int count() {
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM architecture_layers", Integer.class);
		return (count != null) ? count : 0;
	}

	/** The number of layers that do not belong to the package. */
	public int countOutside(String packageId) {
		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM architecture_layers WHERE source_package_id IS DISTINCT FROM ?", Integer.class,
				packageId);
		return (count != null) ? count : 0;
	}

	/** Whether another layer has the name, compared case-insensitively and without surrounding blanks. */
	public boolean nameTaken(String name, UUID except) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM architecture_layers WHERE lower(btrim(name)) = lower(btrim(?))
				    AND id IS DISTINCT FROM ?::uuid)""", Boolean.class, name, except));
	}

	/** Stores a new organization layer at the end with version 1; {@code false} if a parallel write took the name. */
	public boolean insert(UUID id, String name, long personId, Instant now) {
		return this.jdbc.update("""
				INSERT INTO architecture_layers (id, name, sort_order, scope, created_at, created_by, updated_at, updated_by)
				VALUES (?, ?, (SELECT count(*) FROM architecture_layers), 'organization', ?, ?, ?, ?)
				ON CONFLICT DO NOTHING""", id, name, Timestamp.from(now), personId, Timestamp.from(now), personId) == 1;
	}

	/** Renames the layer; the caller holds the metamodel lock and checked that the name is free. */
	public void rename(UUID id, String name, long personId, Instant now) {
		this.jdbc.update("UPDATE architecture_layers SET name = ?, updated_at = ?, updated_by = ? WHERE id = ?", name,
				Timestamp.from(now), personId, id);
	}

	/**
	 * Moves the layer to the position among the layers (clamped to the end) and numbers all layers
	 * densely.
	 */
	public void move(UUID id, int position) {
		this.jdbc.update("""
				WITH others AS (
				    SELECT id, (row_number() OVER (ORDER BY sort_order, lower(name), id) - 1) * 2 AS sort_key
				    FROM architecture_layers WHERE id <> ?),
				keyed AS (
				    SELECT id, sort_key FROM others
				    UNION ALL
				    SELECT ?::uuid, ?::bigint * 2 - 1),
				ranked AS (
				    SELECT id, row_number() OVER (ORDER BY sort_key) - 1 AS position FROM keyed)
				UPDATE architecture_layers t SET sort_order = r.position
				FROM ranked r WHERE t.id = r.id AND t.sort_order <> r.position""", id, id, position);
	}

	/** Numbers the layers densely in their current order. */
	public void renumber() {
		this.jdbc.update("""
				WITH ranked AS (
				    SELECT id, row_number() OVER (ORDER BY sort_order, lower(name), id) - 1 AS position
				    FROM architecture_layers)
				UPDATE architecture_layers t SET sort_order = r.position
				FROM ranked r WHERE t.id = r.id AND t.sort_order <> r.position""");
	}

	/** Deletes the layer and numbers the remaining ones densely. */
	public void delete(UUID id) {
		this.jdbc.update("DELETE FROM architecture_layers WHERE id = ?", id);
		renumber();
	}

	/** The layers that have the ID or, regardless of case, the name, with their origin. */
	public List<Owner> owners(UUID id, String name) {
		return this.jdbc.query("""
				SELECT scope, source_package_id FROM architecture_layers
				WHERE id = ? OR (lower(btrim(name)) = lower(btrim(?)) AND id <> ?)""",
				(rs, n) -> new Owner(rs.getString(1), rs.getString(2)), id, name, id);
	}

	/** The IDs of the layers a package installed. */
	public List<UUID> packageLayerIds(String packageId) {
		return this.jdbc.queryForList("SELECT id FROM architecture_layers WHERE source_package_id = ? ORDER BY id",
				UUID.class, packageId);
	}

	/** Gives the layers of the package placeholder names, so that the import can set any names. */
	public void releaseNames(String packageId) {
		this.jdbc.update("UPDATE architecture_layers SET name = id::text WHERE source_package_id = ?", packageId);
	}

	/** The layers a package installed, in their order. */
	public List<LayerRow> packageLayers(String packageId) {
		return this.jdbc.query(SELECT + "WHERE l.source_package_id = ? ORDER BY l.sort_order, lower(l.name), l.id",
				ArchitectureLayerStore::row, packageId);
	}

	/**
	 * Installs or replaces an imported layer of the package; the ID and the package were checked
	 * by the caller. A new layer takes {@code sortOrder} as its position key (the caller numbers all
	 * layers densely afterwards), a replaced one keeps its position. {@code personId} is {@code null}
	 * for an import by a system administrator.
	 *
	 * @param bumpVersion whether the layer changed, which raises its version
	 * @return {@code true} if the layer was created, {@code false} if it was replaced
	 */
	public boolean upsertImported(UUID id, String name, int sortOrder, String packageId, String packageVersion,
			boolean bumpVersion, Long personId, Instant now) {
		boolean created = this.jdbc.update("""
				INSERT INTO architecture_layers (id, name, sort_order, scope, source_package_id,
				    source_package_version, created_at, created_by, updated_at, updated_by)
				VALUES (?, ?, ?, 'imported', ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id) DO NOTHING""", id, name, sortOrder, packageId, packageVersion, Timestamp.from(now),
				personId, Timestamp.from(now), personId) == 1;
		if (!created) {
			this.jdbc.update("""
					UPDATE architecture_layers SET name = ?, source_package_version = ?,
					       version = version + ?, updated_at = ?, updated_by = ?
					WHERE id = ? AND source_package_id = ?""", name, packageVersion, bumpVersion ? 1 : 0,
					Timestamp.from(now), personId, id, packageId);
		}
		return created;
	}

	/** The number of metatypes, of any origin, that have one of the layers. */
	public int assignedCount(Collection<UUID> layerIds) {
		if (layerIds.isEmpty()) {
			return 0;
		}
		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM metatypes WHERE architecture_layer_id = ANY (?::uuid[])", Integer.class,
				array(layerIds));
		return (count != null) ? count : 0;
	}

	/**
	 * Removes the assignments to the layers from all metatypes, whatever their origin, and raises
	 * their versions; returns the number of metatypes changed.
	 */
	public int clearAssignments(Collection<UUID> layerIds) {
		if (layerIds.isEmpty()) {
			return 0;
		}
		return this.jdbc.update("""
				UPDATE metatypes SET architecture_layer_id = NULL, version = version + 1
				WHERE architecture_layer_id = ANY (?::uuid[])""", array(layerIds));
	}

	/** Deletes a layer of the package; others stay. */
	public void deletePackageLayer(UUID id, String packageId) {
		this.jdbc.update("DELETE FROM architecture_layers WHERE id = ? AND source_package_id = ?", id, packageId);
	}

	private static String array(Collection<UUID> ids) {
		return "{" + String.join(",", ids.stream().map(UUID::toString).toList()) + "}";
	}

	private static LayerRow row(ResultSet rs, int n) throws SQLException {
		return new LayerRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getString(4),
				rs.getString(5), rs.getString(6), rs.getInt(7), rs.getLong(8));
	}

}

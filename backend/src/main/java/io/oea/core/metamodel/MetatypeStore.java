package io.oea.core.metamodel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The metatypes (design D3, D4, D8 of us-032-metatype-editing): their fields, base types,
 * settings, the checks before a deletion and the content a package installs. The tables
 * of other aggregates that a deletion must find (entities, catalogs, visibility settings) are
 * only counted in SQL.
 */
@Repository
public class MetatypeStore {

	private final JdbcTemplate jdbc;

	public MetatypeStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A metatype of the catalog read model. */
	public record MetatypeRow(UUID id, String name, String abbreviation, boolean connection, int sortOrder,
			String scope, String sourcePackageId, String sourcePackageVersion, UUID baseMetatypeId,
			String reimportPolicy, int version, UUID configId, boolean showParallelChanges, boolean composition) {
	}

	/** A metatype with the kind and base type, to find the connection metatypes between two metatypes. */
	public record KindRow(UUID id, String name, String abbreviation, boolean connection, UUID baseMetatypeId) {
	}

	/** The re-import setting and the base type of a metatype. */
	public record PolicyRow(UUID id, String policy, UUID baseMetatypeId) {
	}

	/** The stored fields of a metatype. */
	public record StoredRow(String name, String abbreviation, boolean connection, int sortOrder, String scope,
			UUID baseMetatypeId, UUID configId) {
	}

	/** The fields a package defines for a metatype. */
	public record ImportedFields(String name, String abbreviation, boolean connection, int sortOrder) {
	}

	/** How often a metatype is used by other content, which keeps it from being deleted. */
	public record Usage(long entities, long catalogs, long subtypes, long endpoints, long scopingTypes,
			long personMetatype) {
	}

	/** The metatypes of the instance configuration and of {@code config}, by sort order and name. */
	public List<MetatypeRow> inConfigurations(UUID instance, UUID config) {
		return this.jdbc.query("""
				SELECT m.id, m.name, m.abbreviation, m.is_connection, m.sort_order, m.scope, m.source_package_id,
				       m.source_package_version, m.base_metatype_id, m.reimport_organization_mappings,
				       m.version, m.config_id, m.show_parallel_changes, m.is_composition
				FROM metatypes m
				WHERE m.config_id IN (?, ?) ORDER BY m.sort_order, m.name""",
				(rs, n) -> new MetatypeRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
						rs.getBoolean(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8),
						rs.getObject(9, UUID.class), rs.getString(10), rs.getInt(11), rs.getObject(12, UUID.class),
						rs.getBoolean(13), rs.getBoolean(14)),
				instance, config);
	}

	/** All metatypes by sort order and name. */
	public List<KindRow> kinds() {
		return this.jdbc.query("""
				SELECT id, name, abbreviation, is_connection, base_metatype_id FROM metatypes
				ORDER BY sort_order, name""",
				(rs, n) -> new KindRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
						rs.getBoolean(4), rs.getObject(5, UUID.class)));
	}

	/** The IDs of all metatypes. */
	public List<UUID> ids() {
		return this.jdbc.queryForList("SELECT id FROM metatypes", UUID.class);
	}

	/** The names of all metatypes by ID. */
	public Map<UUID, String> names() {
		Map<UUID, String> names = new LinkedHashMap<>();
		this.jdbc.query("SELECT id, name FROM metatypes", (rs) -> {
			names.put(rs.getObject(1, UUID.class), rs.getString(2));
		});
		return names;
	}

	/** The IDs of the connection metatypes. */
	public List<UUID> connectionIds() {
		return this.jdbc.queryForList("SELECT id FROM metatypes WHERE is_connection", UUID.class);
	}

	/** The base type of each metatype that has one. */
	public Map<UUID, UUID> bases() {
		Map<UUID, UUID> bases = new HashMap<>();
		this.jdbc.query("SELECT id, base_metatype_id FROM metatypes WHERE base_metatype_id IS NOT NULL", (rs) -> {
			bases.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
		});
		return bases;
	}

	/** The re-import setting and the base type of every metatype. */
	public List<PolicyRow> policies() {
		return this.jdbc.query("SELECT id, reimport_organization_mappings, base_metatype_id FROM metatypes",
				(rs, n) -> new PolicyRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class)));
	}

	/** The configuration of the metatype. */
	public Optional<UUID> configOf(UUID id) {
		return this.jdbc.queryForList("SELECT config_id FROM metatypes WHERE id = ?", UUID.class, id)
			.stream()
			.findFirst();
	}

	public Optional<StoredRow> stored(UUID id) {
		return this.jdbc.query("""
				SELECT name, abbreviation, is_connection, sort_order, scope, base_metatype_id, config_id
				FROM metatypes WHERE id = ?""",
				(rs, n) -> new StoredRow(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4),
						rs.getString(5), rs.getObject(6, UUID.class), rs.getObject(7, UUID.class)),
				id)
			.stream()
			.findFirst();
	}

	/** The name and abbreviation of a metatype. */
	public record NameRow(String name, String abbreviation) {
	}

	/**
	 * The name and abbreviation of the metatype.
	 *
	 * @throws org.springframework.dao.EmptyResultDataAccessException for an unknown ID
	 */
	public NameRow nameAndAbbreviation(UUID id) {
		return this.jdbc.queryForObject("SELECT name, abbreviation FROM metatypes WHERE id = ?",
				(rs, n) -> new NameRow(rs.getString(1), rs.getString(2)), id);
	}

	/** Whether the metatype is a connection metatype; empty for an unknown metatype. */
	public Optional<Boolean> isConnection(UUID id) {
		return this.jdbc.queryForList("SELECT is_connection FROM metatypes WHERE id = ?", Boolean.class, id)
			.stream()
			.findFirst();
	}

	public Optional<String> reimportPolicy(UUID id) {
		return this.jdbc
			.queryForList("SELECT reimport_organization_mappings FROM metatypes WHERE id = ?", String.class, id)
			.stream()
			.findFirst();
	}

	public void setReimportPolicy(UUID id, String policy) {
		this.jdbc.update("UPDATE metatypes SET reimport_organization_mappings = ? WHERE id = ?", policy, id);
	}

	public Optional<Boolean> showParallelChanges(UUID id) {
		return this.jdbc.queryForList("SELECT show_parallel_changes FROM metatypes WHERE id = ?", Boolean.class, id)
			.stream()
			.findFirst();
	}

	public void setShowParallelChanges(UUID id, boolean show) {
		this.jdbc.update("UPDATE metatypes SET show_parallel_changes = ? WHERE id = ?", show, id);
	}

	/**
	 * Whether a metatype has the name, case-insensitively: in the instance and all extensions if
	 * {@code instance}, otherwise in the instance and {@code config}; the metatype {@code excluded}
	 * (may be {@code null}) does not count.
	 */
	public boolean nameTaken(boolean instance, UUID instanceConfig, UUID config, String name, UUID excluded) {
		return count("""
				SELECT count(*) FROM metatypes WHERE (? OR config_id IN (?, ?)) AND lower(name) = lower(?)
				    AND id IS DISTINCT FROM ?::uuid""", instance, instanceConfig, config, name, excluded) > 0;
	}

	/** Whether the metatype exists with this kind in the instance or {@code config}. */
	public boolean existsOfKind(UUID id, boolean connection, UUID instanceConfig, UUID config) {
		return count("SELECT count(*) FROM metatypes WHERE id = ? AND is_connection = ? AND config_id IN (?, ?)", id,
				connection, instanceConfig, config) > 0;
	}

	/** Whether the metatype exists in the instance or {@code config}. */
	public boolean existsIn(UUID id, UUID instanceConfig, UUID config) {
		return count("SELECT count(*) FROM metatypes WHERE id = ? AND config_id IN (?, ?)", id, instanceConfig,
				config) > 0;
	}

	/** Whether the metatype is a built-in one. */
	public boolean builtIn(UUID id) {
		return Boolean.TRUE.equals(this.jdbc
			.queryForObject("SELECT EXISTS (SELECT 1 FROM metatypes WHERE id = ? AND scope = 'built-in')", Boolean.class, id));
	}

	/** Whether the metatype exists with this kind. */
	public boolean existsOfKind(UUID id, boolean connection) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM metatypes WHERE id = ? AND is_connection = ?)", Boolean.class, id,
				connection));
	}

	public void insert(UUID id, UUID config, String name, String abbreviation, boolean connection, UUID layerId,
			int sortOrder, Long personId, UUID baseId, boolean composition) {
		this.jdbc.update("""
				INSERT INTO metatypes (id, config_id, name, abbreviation, is_connection, architecture_layer_id, sort_order,
				    scope, created_by, base_metatype_id, is_composition) VALUES (?, ?, ?, ?, ?, ?, ?, 'organization', ?, ?, ?)""",
				id, config, name, abbreviation, connection, layerId, sortOrder, personId, baseId, composition);
	}

	public void update(UUID id, String name, String abbreviation, UUID layerId, int sortOrder, UUID baseId,
			boolean composition) {
		this.jdbc.update("""
				UPDATE metatypes SET name = ?, abbreviation = ?, architecture_layer_id = ?, sort_order = ?,
				       base_metatype_id = ?, is_composition = ? WHERE id = ?""", name, abbreviation, layerId, sortOrder,
				baseId, composition, id);
	}

	public void delete(UUID id) {
		this.jdbc.update("DELETE FROM metatypes WHERE id = ?", id);
	}

	/** What uses the metatype, to decide whether it can be deleted. */
	public Usage usage(UUID id) {
		return new Usage(count("SELECT count(*) FROM entities WHERE metatype_id = ?", id),
				count("SELECT count(*) FROM catalogs WHERE primary_metatype_id = ?", id),
				count("SELECT count(*) FROM metatypes WHERE base_metatype_id = ?", id),
				count("""
						SELECT count(*) FROM metatype_connection_constraints WHERE allowed_metatype_id = ? AND metatype_id <> ?""",
						id, id),
				count("SELECT count(*) FROM property_definitions WHERE scoping_connection_type_id = ?", id),
				count("SELECT count(*) FROM visibility_settings WHERE person_metatype_id = ?", id));
	}

	/** Existing metatypes with the identifier or the name of an imported one. */
	public List<ContentOwner> owners(UUID id, String name) {
		return this.jdbc.query("""
				SELECT scope, source_package_id FROM metatypes
				WHERE id = ? OR (lower(name) = lower(?) AND id <> ?)""",
				(rs, n) -> new ContentOwner(rs.getString(1), rs.getString(2)), id, name, id);
	}

	/** The fields of an existing metatype that a package defines. */
	public Optional<ImportedFields> importedFields(UUID id) {
		return this.jdbc
			.query("SELECT name, abbreviation, is_connection, sort_order FROM metatypes WHERE id = ?",
					(rs, n) -> new ImportedFields(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4)), id)
			.stream()
			.findFirst();
	}

	public void insertImported(UUID id, UUID config, String name, String abbreviation, boolean connection,
			int sortOrder, String packageId, String packageVersion, boolean composition, UUID layerId) {
		this.jdbc.update("""
				INSERT INTO metatypes (id, config_id, name, abbreviation, is_connection, sort_order, scope,
				    source_package_id, source_package_version, is_composition, architecture_layer_id)
				VALUES (?, ?, ?, ?, ?, ?, 'imported', ?, ?, ?, ?)""", id, config, name, abbreviation, connection,
				sortOrder, packageId, packageVersion, composition, layerId);
	}

	public void updateImported(UUID id, String name, String abbreviation, boolean connection, int sortOrder,
			String packageVersion, boolean composition, UUID layerId) {
		this.jdbc.update("""
				UPDATE metatypes SET name = ?, abbreviation = ?, is_connection = ?, sort_order = ?,
				       source_package_version = ?, is_composition = ?, architecture_layer_id = ? WHERE id = ?""", name,
				abbreviation, connection, sortOrder, packageVersion, composition, layerId, id);
	}

	/** Sets the base type; returns how many metatypes changed. */
	public int setBase(UUID id, UUID baseId) {
		return this.jdbc.update(
				"UPDATE metatypes SET base_metatype_id = ? WHERE id = ? AND base_metatype_id IS DISTINCT FROM ?::uuid",
				baseId, id, baseId);
	}

	private long count(String sql, Object... arguments) {
		Long count = this.jdbc.queryForObject(sql, Long.class, arguments);
		return (count != null) ? count : 0;
	}

}

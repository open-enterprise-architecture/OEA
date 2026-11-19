package io.oea.core.metamodel;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The metamodel configurations (design D1, D2 of us-037-solution-extensions-and-lock-mode): the
 * instance configuration and the extensions of solutions. The instance configuration is also the
 * metamodel lock, and the versions of the versioned elements are kept here (design D1, D2 of
 * scr-030-metamodel-configuration).
 */
@Repository
public class MetamodelConfigurationStore {

	/** The instance configuration created by the migration. */
	public static final UUID INSTANCE = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static final String COLUMNS = """
			c.id, c.scope, c.parent_id, c.solution_id, c.name, c.edit_mode, c.version, c.last_modified_at,
			(SELECT count(*) FROM metatypes m WHERE m.config_id = c.id)""";

	/** The versioned elements with their tables (never taken from input). */
	public enum Versioned {

		METATYPE("metatypes", ""),
		PROPERTY_DEFINITION("property_definitions", " AND retired_at IS NULL"),
		CATEGORY("property_categories", ""),
		ARCHITECTURE_LAYER("architecture_layers", ""),
		CONFIGURATION("metamodel_configurations", "");

		private final String table;

		private final String condition;

		Versioned(String table, String condition) {
			this.table = table;
			this.condition = condition;
		}

	}

	private final JdbcTemplate jdbc;

	public MetamodelConfigurationStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A configuration with the number of its own metatypes. */
	public record ConfigurationRow(UUID id, String scope, UUID parentId, UUID solutionId, String name, String editMode,
			int version, Instant lastModifiedAt, int metatypeCount) {
	}

	/** Locks the configuration for the transaction; one metamodel change at a time. */
	public void lock(UUID configId) {
		this.jdbc.queryForObject("SELECT id FROM metamodel_configurations WHERE id = ? FOR UPDATE", UUID.class,
				configId);
	}

	/** The instance configuration first, then the extensions by name. */
	public List<ConfigurationRow> list() {
		return this.jdbc.query("SELECT " + COLUMNS
				+ " FROM metamodel_configurations c ORDER BY c.scope <> 'instance', lower(c.name), c.id",
				MetamodelConfigurationStore::row);
	}

	public Optional<ConfigurationRow> find(UUID id) {
		return this.jdbc.query("SELECT " + COLUMNS + " FROM metamodel_configurations c WHERE c.id = ?",
				MetamodelConfigurationStore::row, id)
			.stream()
			.findFirst();
	}

	/** Whether the configuration exists. */
	public boolean exists(UUID id) {
		Long exists = this.jdbc.queryForObject("SELECT count(*) FROM metamodel_configurations WHERE id = ?", Long.class,
				id);
		return exists != null && exists > 0;
	}

	/** Whether the solution exists. */
	public boolean solutionExists(UUID solutionId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM solutions WHERE id = ?)",
				Boolean.class, solutionId));
	}

	/** Whether another configuration than {@code excluded} (may be {@code null}) has the name, case-insensitively. */
	public boolean nameTaken(String name, UUID excluded) {
		Long taken = this.jdbc.queryForObject("""
				SELECT count(*) FROM metamodel_configurations WHERE lower(name) = lower(?) AND id IS DISTINCT FROM ?::uuid""",
				Long.class, name, excluded);
		return taken != null && taken > 0;
	}

	public void insertExtension(UUID id, UUID parentId, UUID solutionId, String name, String editMode, Instant now,
			Long personId) {
		this.jdbc.update("""
				INSERT INTO metamodel_configurations (id, scope, parent_id, solution_id, name, edit_mode, last_modified_at,
				    last_modified_by_person_id) VALUES (?, 'solution', ?, ?, ?, ?, ?, ?)""", id, parentId, solutionId,
				name, editMode, Timestamp.from(now), personId);
	}

	public void update(UUID id, String name, String editMode, Instant now, Long personId) {
		this.jdbc.update("""
				UPDATE metamodel_configurations SET name = ?, edit_mode = ?, last_modified_at = ?,
				    last_modified_by_person_id = ?, last_modified_by_admin_id = NULL WHERE id = ?""", name, editMode,
				Timestamp.from(now), personId, id);
	}

	public void delete(UUID id) {
		this.jdbc.update("DELETE FROM metamodel_configurations WHERE id = ?", id);
	}

	/** Records who changed the configuration last, after a package import. */
	public void touch(UUID id, Instant now, Long personId, UUID adminId) {
		this.jdbc.update("""
				UPDATE metamodel_configurations SET last_modified_at = ?, last_modified_by_person_id = ?,
				       last_modified_by_admin_id = ? WHERE id = ?""", Timestamp.from(now), personId, adminId, id);
	}

	/** The re-import setting of the configuration for organization mappings. */
	public String reimportPolicy(UUID id) {
		return this.jdbc.queryForObject("SELECT reimport_organization_mappings FROM metamodel_configurations WHERE id = ?",
				String.class, id);
	}

	public void setReimportPolicy(UUID id, String policy) {
		this.jdbc.update("UPDATE metamodel_configurations SET reimport_organization_mappings = ? WHERE id = ?", policy,
				id);
	}

	/** The current version of the element; empty if it does not exist (or is retired). */
	public Optional<Integer> version(Versioned element, UUID id) {
		return this.jdbc
			.queryForList("SELECT version FROM " + element.table + " WHERE id = ?" + element.condition, Integer.class, id)
			.stream()
			.findFirst();
	}

	/** Increments the version of the element after a change. */
	public void incrementVersion(Versioned element, UUID id) {
		this.jdbc.update("UPDATE " + element.table + " SET version = version + 1 WHERE id = ?", id);
	}

	private static ConfigurationRow row(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
		return new ConfigurationRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
				rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getInt(7),
				rs.getTimestamp(8).toInstant(), rs.getInt(9));
	}

}

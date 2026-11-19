package io.oea.core.metamodel;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The visibility configuration of the metamodel: the mode and roles of property definitions
 * (design D1 of us-124-property-visibility), the history visibility per role and metatype (design D3
 * of us-098-entity-history), the override roles (design D1 of us-128-override-role-list) and the
 * person metatype (design D2 of us-127-connection-scoped-visibility).
 */
@Repository
public class VisibilityStore {

	private final JdbcTemplate jdbc;

	public VisibilityStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A role with read or write access to a property. */
	public record RoleAccessRow(UUID propertyDefId, String roleId, String access) {
	}

	/** The visibility mode and scoping connection type of a property definition. */
	public record ModeRow(UUID propertyDefId, String mode, UUID scopingTypeId) {
	}

	/** The ID and name of a property definition. */
	public record PropertyName(UUID id, String name) {
	}

	/** The history mode of a role for a metatype. */
	public record HistoryRow(String roleId, UUID metatypeId, String mode) {
	}

	/** The person metatype with its name; both {@code null} while none is set. */
	public record PersonMetatypeRow(UUID metatypeId, String metatypeName) {
	}

	/** The roles of the properties with their access, by role. */
	public List<RoleAccessRow> roleAccess() {
		return this.jdbc.query("SELECT property_def_id, role_id, access FROM property_visibility_roles ORDER BY role_id",
				(rs, n) -> new RoleAccessRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
	}

	/** The mode of every property definition, retired ones included. */
	public List<ModeRow> modes() {
		return this.jdbc.query("SELECT id, visibility_mode, scoping_connection_type_id FROM property_definitions",
				(rs, n) -> new ModeRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class)));
	}

	/** The property definitions that are not retired, by name. */
	public List<PropertyName> activeProperties() {
		return this.jdbc.query("SELECT id, name FROM property_definitions WHERE retired_at IS NULL ORDER BY lower(name), id",
				(rs, n) -> new PropertyName(rs.getObject(1, UUID.class), rs.getString(2)));
	}

	public List<UUID> retiredPropertyIds() {
		return this.jdbc.queryForList("SELECT id FROM property_definitions WHERE retired_at IS NOT NULL", UUID.class);
	}

	public String propertyName(UUID id) {
		return this.jdbc.queryForObject("SELECT name FROM property_definitions WHERE id = ?", String.class, id);
	}

	/** The IDs of the roles that are not deleted. */
	public List<String> activeRoleIds() {
		return this.jdbc.queryForList("SELECT id FROM authorization_roles WHERE deleted_at IS NULL", String.class);
	}

	/** Whether one of the connection metatypes is the scoping type of a property definition. */
	public boolean isScopingType(Collection<UUID> connectionMetatypes) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM property_definitions WHERE scoping_connection_type_id = ANY (?))",
				Boolean.class, (Object) connectionMetatypes.toArray(UUID[]::new)));
	}

	/** Stores the visibility of a property definition and replaces its role lists. */
	public void save(UUID id, String mode, UUID scopingType, List<String> readRoles, List<String> writeRoles) {
		this.jdbc.update("UPDATE property_definitions SET visibility_mode = ?, scoping_connection_type_id = ? WHERE id = ?",
				mode, scopingType, id);
		this.jdbc.update("DELETE FROM property_visibility_roles WHERE property_def_id = ?", id);
		for (String role : readRoles) {
			this.jdbc.update("INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?, ?, 'read')",
					id, role);
		}
		for (String role : writeRoles) {
			this.jdbc.update(
					"INSERT INTO property_visibility_roles (property_def_id, role_id, access) VALUES (?, ?, 'write')", id,
					role);
		}
	}

	/** The configured history entries of roles that are not deleted, by role and metatype name. */
	public List<HistoryRow> history() {
		return this.jdbc.query("""
				SELECT s.role_id, s.metatype_id, s.mode
				FROM history_visibility_settings s
				JOIN authorization_roles r ON r.id = s.role_id AND r.deleted_at IS NULL
				JOIN metatypes t ON t.id = s.metatype_id
				ORDER BY s.role_id, lower(t.name), s.metatype_id""",
				(rs, n) -> new HistoryRow(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3)));
	}

	/** How many of the roles that are not deleted hide the history fields for exactly the metatype. */
	public int hidingRoles(UUID metatypeId, Collection<String> roleIds) {
		Integer hiding = this.jdbc.queryForObject("""
				SELECT count(*) FROM history_visibility_settings s
				JOIN authorization_roles r ON r.id = s.role_id AND r.deleted_at IS NULL
				WHERE s.metatype_id = ? AND s.role_id = ANY(?)""", Integer.class, metatypeId,
				roleIds.toArray(String[]::new));
		return (hiding != null) ? hiding : 0;
	}

	/** Locks the roles that are not deleted for a write and returns their IDs. */
	public List<String> lockActiveRoleIds() {
		return this.jdbc.queryForList("SELECT id FROM authorization_roles WHERE deleted_at IS NULL FOR UPDATE",
				String.class);
	}

	/** Whether the role hides the history fields for the metatype. */
	public boolean hides(String roleId, UUID metatypeId) {
		Integer rows = this.jdbc.queryForObject(
				"SELECT count(*) FROM history_visibility_settings WHERE role_id = ? AND metatype_id = ?", Integer.class,
				roleId, metatypeId);
		return rows != null && rows > 0;
	}

	public void hide(String roleId, UUID metatypeId) {
		this.jdbc.update("INSERT INTO history_visibility_settings (role_id, metatype_id, mode) VALUES (?, ?, 'hide')",
				roleId, metatypeId);
	}

	public void show(String roleId, UUID metatypeId) {
		this.jdbc.update("DELETE FROM history_visibility_settings WHERE role_id = ? AND metatype_id = ?", roleId,
				metatypeId);
	}

	/** The IDs of the override roles, sorted. */
	public List<String> overrideRoles() {
		return this.jdbc.queryForList("""
				SELECT id FROM authorization_roles WHERE overrides_property_visibility AND deleted_at IS NULL ORDER BY id""",
				String.class);
	}

	/** Locks the roles that are not deleted for a write and returns their IDs, sorted. */
	public List<String> lockActiveRoleIdsSorted() {
		return this.jdbc.queryForList(
				"SELECT id FROM authorization_roles WHERE deleted_at IS NULL ORDER BY id FOR UPDATE", String.class);
	}

	/** Makes exactly the listed roles override the property visibility. */
	public void setOverrideRoles(Collection<String> roleIds) {
		this.jdbc.update("UPDATE authorization_roles SET overrides_property_visibility = (id = ANY(?))",
				(Object) roleIds.toArray(String[]::new));
	}

	public PersonMetatypeRow personMetatype() {
		return this.jdbc.queryForObject("""
				SELECT s.person_metatype_id, m.name FROM visibility_settings s
				LEFT JOIN metatypes m ON m.id = s.person_metatype_id WHERE s.id = 1""",
				(rs, n) -> new PersonMetatypeRow(rs.getObject(1, UUID.class), rs.getString(2)));
	}

	public void setPersonMetatype(UUID metatypeId) {
		this.jdbc.update("UPDATE visibility_settings SET person_metatype_id = ? WHERE id = 1", metatypeId);
	}

}

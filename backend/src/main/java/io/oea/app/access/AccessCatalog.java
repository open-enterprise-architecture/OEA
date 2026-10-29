package io.oea.app.access;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission catalog and authorization roles as stored (seeded from the contract). */
@Service
public class AccessCatalog {

	private final JdbcTemplate jdbc;

	public AccessCatalog(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record PermissionEntry(String id, String category, boolean adminClass) {
	}

	public record RoleEntry(String id, String name, String description, boolean builtin,
			boolean assignableToServiceAccounts, boolean overridesPropertyVisibility, Set<String> permissions,
			boolean privileged) {
	}

	@Transactional(readOnly = true)
	public List<PermissionEntry> permissions() {
		return this.jdbc.query("SELECT id, category, admin_class FROM permissions ORDER BY sort_order, id",
				(rs, n) -> new PermissionEntry(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
	}

	@Transactional(readOnly = true)
	public List<RoleEntry> roles(Boolean assignableToServiceAccounts) {
		Map<String, Set<String>> permissions = new LinkedHashMap<>();
		Set<String> adminClass = new LinkedHashSet<>(
				this.jdbc.queryForList("SELECT id FROM permissions WHERE admin_class", String.class));
		this.jdbc.query("SELECT role_id, permission_id FROM role_permissions WHERE approval_status = 'approved' "
				+ "ORDER BY role_id, permission_id", (rs) -> {
					permissions.computeIfAbsent(rs.getString(1), (k) -> new LinkedHashSet<>()).add(rs.getString(2));
				});
		List<RoleEntry> roles = new ArrayList<>();
		this.jdbc.query("""
				SELECT id, name, description, is_builtin, assignable_to_service_accounts, overrides_property_visibility
				FROM authorization_roles WHERE deleted_at IS NULL ORDER BY is_builtin DESC, id""", (rs) -> {
			Set<String> granted = permissions.getOrDefault(rs.getString(1), Set.of());
			RoleEntry role = new RoleEntry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
					rs.getBoolean(5), rs.getBoolean(6), granted, granted.stream().anyMatch(adminClass::contains));
			if (assignableToServiceAccounts == null || assignableToServiceAccounts == role.assignableToServiceAccounts()) {
				roles.add(role);
			}
		});
		return roles;
	}

	/**
	 * One role with its permissions (role preview, design D7 of us-144-local-role-assignments).
	 * @throws NoSuchElementException for an unknown or deleted role
	 */
	@Transactional(readOnly = true)
	public RoleEntry role(String roleId) {
		return roles(null).stream()
			.filter((role) -> role.id().equals(roleId))
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no role " + roleId));
	}

}

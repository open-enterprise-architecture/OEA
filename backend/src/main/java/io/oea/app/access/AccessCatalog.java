package io.oea.app.access;

import io.oea.core.access.RoleStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Permission catalog and authorization roles as stored (seeded from the contract). */
@Service
public class AccessCatalog {

	private final RoleStore roles;

	public AccessCatalog(RoleStore roles) {
		this.roles = roles;
	}

	public record PermissionEntry(String id, String category, boolean adminClass) {
	}

	public record RoleEntry(String id, String name, String description, boolean builtin,
			boolean assignableToServiceAccounts, boolean overridesPropertyVisibility, Set<String> permissions,
			boolean privileged) {
	}

	@Transactional(readOnly = true)
	public List<PermissionEntry> permissions() {
		return this.roles.permissions().stream().map((p) -> new PermissionEntry(p.id(), p.category(), p.adminClass())).toList();
	}

	@Transactional(readOnly = true)
	public List<RoleEntry> roles(Boolean assignableToServiceAccounts) {
		Map<String, Set<String>> permissions = new LinkedHashMap<>();
		Set<String> adminClass = new LinkedHashSet<>(this.roles.adminClassPermissionIds());
		for (RoleStore.RolePermissionRow row : this.roles.approvedRolePermissions()) {
			permissions.computeIfAbsent(row.roleId(), (k) -> new LinkedHashSet<>()).add(row.permissionId());
		}
		List<RoleEntry> result = new ArrayList<>();
		for (RoleStore.RoleRow row : this.roles.liveRoles()) {
			Set<String> granted = permissions.getOrDefault(row.id(), Set.of());
			RoleEntry role = new RoleEntry(row.id(), row.name(), row.description(), row.builtin(),
					row.assignableToServiceAccounts(), row.overridesPropertyVisibility(), granted,
					granted.stream().anyMatch(adminClass::contains));
			if (assignableToServiceAccounts == null || assignableToServiceAccounts == role.assignableToServiceAccounts()) {
				result.add(role);
			}
		}
		return result;
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

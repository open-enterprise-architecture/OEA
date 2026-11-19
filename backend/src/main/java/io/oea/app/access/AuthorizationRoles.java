package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.RoleStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom authorization roles (UC-23 A5, US-151; design D4 of us-128-override-role-list): a role
 * with an explicit permission list, possibly empty, next to the built-in roles. Four-eyes
 * approval of privileged roles follows with US-150.
 */
@Service
public class AuthorizationRoles {

	/** SoD-2: metamodel administration never joins visibility administration in one role. */
	private static final Set<String> VISIBILITY_ADMINISTRATION = Set.of("visibility:configure",
			"visibility-scope:assign");

	private final RoleStore roles;

	private final AccessCatalog catalog;

	private final AuditWriter audit;

	public AuthorizationRoles(RoleStore roles, AccessCatalog catalog, AuditWriter audit) {
		this.roles = roles;
		this.catalog = catalog;
		this.audit = audit;
	}

	/** The definition of a new custom role; the contract checks ID pattern and lengths. */
	public record NewRole(String id, String name, String description, Set<String> permissions,
			boolean assignableToServiceAccounts) {
	}

	/**
	 * Creates the role and audits {@code role.created}.
	 *
	 * @throws AccessConflictException for an ID that is taken
	 * @throws InvalidAccessRequestException for an unknown permission, SoD-2 or SoD-4
	 */
	@Transactional
	public AccessCatalog.RoleEntry create(OeaPrincipal principal, NewRole role) {
		Map<String, AccessCatalog.PermissionEntry> known = this.catalog.permissions()
			.stream()
			.collect(Collectors.toMap(AccessCatalog.PermissionEntry::id, (p) -> p));
		Set<String> permissions = new TreeSet<>(role.permissions());
		List<String> unknown = permissions.stream().filter((p) -> !known.containsKey(p)).toList();
		if (!unknown.isEmpty()) {
			throw new InvalidAccessRequestException("permissions", "Unknown permissions: " + String.join(", ", unknown));
		}
		if (permissions.contains("metamodel:write")
				&& permissions.stream().anyMatch(VISIBILITY_ADMINISTRATION::contains)) {
			throw new InvalidAccessRequestException("permissions",
					"A role cannot combine metamodel:write with visibility:configure or visibility-scope:assign (SoD-2).");
		}
		boolean privileged = permissions.stream().anyMatch((p) -> known.get(p).adminClass());
		if (privileged && role.assignableToServiceAccounts()) {
			throw new InvalidAccessRequestException("assignableToServiceAccounts",
					"A role with an administrative permission cannot be assignable to service accounts (SoD-4).");
		}
		int inserted = this.roles.insertRole(role.id(), role.name(), role.description(),
				role.assignableToServiceAccounts(), principal.personId(), principal.systemAdminAccountId());
		if (inserted == 0) {
			throw new AccessConflictException("The role ID " + role.id() + " is already taken.");
		}
		for (String permission : permissions) {
			this.roles.insertApprovedPermission(role.id(), permission, principal.personId(),
					principal.systemAdminAccountId());
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", role.name());
		details.put("permissions", List.copyOf(permissions));
		details.put("assignableToServiceAccounts", role.assignableToServiceAccounts());
		details.put("privileged", privileged);
		this.audit.append(new AuditEvent("role.created", AuditEvent.Category.SECURITY,
				privileged ? AuditEvent.Severity.HIGH : AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "authorization-role", role.id(), details));
		return this.catalog.role(role.id());
	}

}

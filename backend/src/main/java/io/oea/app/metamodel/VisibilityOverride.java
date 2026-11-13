package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The property-visibility override role list (REQ-128, US-128; design D1 of
 * us-128-override-role-list): holders of a listed role read every property of the objects they
 * can already read. Empty by default (owner decision Q14); only security administration
 * ({@code visibility:configure}) changes it, and every change is audited.
 */
@Service
public class VisibilityOverride {

	private final JdbcTemplate jdbc;

	private final PermissionResolver permissions;

	private final AuditWriter audit;

	public VisibilityOverride(JdbcTemplate jdbc, PermissionResolver permissions, AuditWriter audit) {
		this.jdbc = jdbc;
		this.permissions = permissions;
		this.audit = audit;
	}

	/** The IDs of the override roles, sorted. */
	@Transactional(readOnly = true)
	public List<String> roles() {
		return this.jdbc.queryForList("""
				SELECT id FROM authorization_roles WHERE overrides_property_visibility AND deleted_at IS NULL ORDER BY id""",
				String.class);
	}

	/**
	 * Replaces the list; the same list again changes and audits nothing.
	 *
	 * @throws InvalidAccessRequestException naming {@code roles} for an unknown role
	 */
	@Transactional
	public List<String> set(OeaPrincipal principal, Set<String> roles) {
		Set<String> next = new TreeSet<>(roles);
		List<String> existing = this.jdbc.queryForList(
				"SELECT id FROM authorization_roles WHERE deleted_at IS NULL ORDER BY id FOR UPDATE", String.class);
		List<String> unknown = next.stream().filter((role) -> !existing.contains(role)).toList();
		if (!unknown.isEmpty()) {
			throw new InvalidAccessRequestException("roles", "Unknown roles: " + String.join(", ", unknown));
		}
		List<String> old = roles();
		if (old.equals(List.copyOf(next))) {
			return old;
		}
		this.jdbc.update("UPDATE authorization_roles SET overrides_property_visibility = (id = ANY(?))",
				(Object) next.toArray(String[]::new));
		Set<String> added = new HashSet<>(next);
		old.forEach(added::remove);
		Set<String> held = new HashSet<>(this.permissions.roles(principal));
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("oldRoles", old);
		details.put("newRoles", List.copyOf(next));
		details.put("selfBenefiting", added.stream().anyMatch(held::contains));
		this.audit.append(new AuditEvent("visibility.override-changed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"settings", "override-roles", details));
		return roles();
	}

}

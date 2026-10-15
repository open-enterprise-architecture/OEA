package io.oea.app.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Effective permissions of a principal (ADR-032 §7), cached for at most 60 seconds. The
 * system admin account holds the permissions of the built-in role {@code system-admin};
 * roles of persons follow with the OIDC login.
 */
@Service
public class PermissionResolver {

	public static final String SYSTEM_ADMIN_ROLE = "system-admin";

	static final Duration MAXIMUM_AGE = Duration.ofSeconds(60);

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final Map<String, Cached> cache = new ConcurrentHashMap<>();

	public PermissionResolver(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	private record Cached(Set<String> permissions, Instant at) {
	}

	/** Roles of the principal (role IDs). */
	public List<String> roles(OeaPrincipal principal) {
		return (principal.type() == OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT) ? List.of(SYSTEM_ADMIN_ROLE) : List.of();
	}

	public Set<String> permissions(OeaPrincipal principal) {
		Instant now = this.clock.instant();
		String key = principal.type() + ":" + principal.systemAdminAccountId() + ":" + principal.personId();
		Cached cached = this.cache.get(key);
		if (cached != null && cached.at().plus(MAXIMUM_AGE).isAfter(now)) {
			return cached.permissions();
		}
		Set<String> permissions = new TreeSet<>();
		for (String role : roles(principal)) {
			permissions.addAll(this.jdbc.queryForList("""
					SELECT permission_id FROM role_permissions
					WHERE role_id = ? AND approval_status = 'approved'""", String.class, role));
		}
		Set<String> result = Set.copyOf(permissions);
		this.cache.put(key, new Cached(result, now));
		return result;
	}

	/** Drops cached permission sets after changes of roles, mappings or assignments. */
	public void evictAll() {
		this.cache.clear();
	}

}

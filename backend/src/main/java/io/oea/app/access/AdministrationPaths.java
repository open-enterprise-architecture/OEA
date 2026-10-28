package io.oea.app.access;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Usable paths to {@code system-admin} besides the local system admin account (ADR-032 SoD-7;
 * design D3 of us-019-admin-safeguards). A mapping counts only after it has granted the role in
 * at least one login, so that a mapping to an empty group never counts. Direct role assignments
 * to persons follow with US-146.
 */
@Service
public class AdministrationPaths {

	private final JdbcTemplate jdbc;

	public AdministrationPaths(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Number of IdP group mappings that are a usable path to {@code system-admin}. */
	public int remotePaths() {
		Integer count = this.jdbc.queryForObject("""
				SELECT count(*) FROM idp_group_mappings m JOIN auth_providers p ON p.id = m.auth_provider_id
				WHERE m.role_id = 'system-admin' AND m.active AND m.approval_status = 'approved'
				  AND m.deleted_at IS NULL AND p.active AND m.last_matched_at IS NOT NULL""", Integer.class);
		return (count != null) ? count : 0;
	}

}

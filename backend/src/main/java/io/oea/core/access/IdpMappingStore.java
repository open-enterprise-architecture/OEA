package io.oea.core.access;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL on {@code idp_group_mappings} beyond the JPA repository (S17 change 3, ADR-051). */
@Repository
public class IdpMappingStore {

	/** An approved mapping that matches a group. */
	public record MatchedMapping(UUID id, String roleId) {
	}

	private final JdbcTemplate jdbc;

	public IdpMappingStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Forgets the last match of the mapping. */
	public void clearLastMatch(UUID mappingId) {
		this.jdbc.update("UPDATE idp_group_mappings SET last_matched_at = NULL WHERE id = ?", mappingId);
	}

	/** The active, approved mappings of the issuer for the groups, ordered by role. */
	public List<MatchedMapping> findActiveForGroups(String issuer, List<String> groupIdentifiers) {
		return this.jdbc.query("""
				SELECT id, role_id FROM idp_group_mappings
				WHERE issuer = ? AND active AND deleted_at IS NULL AND approval_status = 'approved'
				  AND group_identifier = ANY (?) ORDER BY role_id, id""",
				(rs, n) -> new MatchedMapping(rs.getObject(1, UUID.class), rs.getString(2)), issuer,
				groupIdentifiers.toArray(String[]::new));
	}

	/**
	 * Records the last match of the mapping at most once per threshold; a mapping to a role with an
	 * administrative permission only if the second factor is verified.
	 */
	public void recordMatch(UUID mappingId, Instant now, Instant matchedBefore, boolean secondFactorVerified) {
		this.jdbc.update("""
				UPDATE idp_group_mappings m SET last_matched_at = ?
				WHERE m.id = ? AND (m.last_matched_at IS NULL OR m.last_matched_at < ?)
				  AND (? OR NOT EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
				                        WHERE rp.role_id = m.role_id AND p.admin_class))""", Timestamp.from(now),
				mappingId, Timestamp.from(matchedBefore), secondFactorVerified);
	}

}

package io.oea.core.catalog;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The scope of connection-scoped visibility (REQ-127; design D4 of
 * us-127-connection-scoped-visibility): an entity is in the scope of an anchor element if an
 * active connection of the scoping type connects both, in either direction (depth 1).
 */
@Repository
public class ScopeStore {

	private final JdbcTemplate jdbc;

	public ScopeStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The scope check as SQL for the entity {@code alias}; the IDs are inlined as literals (D5). */
	public static String inScope(String alias, UUID scopingType, int anchor) {
		return ("EXISTS (SELECT 1 FROM entity_connections sc JOIN entities sce ON sce.id = sc.entity_id"
				+ " AND sce.metatype_id = '%2$s'::uuid AND sce.deleted_at IS NULL"
				+ " WHERE (sc.source_entity_id = %1$s.id AND sc.target_entity_id = %3$d)"
				+ " OR (sc.target_entity_id = %1$s.id AND sc.source_entity_id = %3$d))")
			.formatted(alias, scopingType, anchor);
	}

	/** Whether the entity is in the scope of the anchor for the scoping type. */
	public boolean inScope(int anchor, UUID scopingType, int entityId) {
		Boolean found = this.jdbc.queryForObject("SELECT " + inScope("e", scopingType, anchor)
				+ " FROM entities e WHERE e.id = ?", Boolean.class, entityId);
		return Boolean.TRUE.equals(found);
	}

}

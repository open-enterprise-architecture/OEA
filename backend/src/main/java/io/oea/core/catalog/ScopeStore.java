package io.oea.core.catalog;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

	/**
	 * The entities among {@code entityIds} in the scope of the anchor for the scoping type, in one
	 * query (design D3 of us-128-override-role-list).
	 */
	public Set<Integer> inScope(int anchor, UUID scopingType, Collection<Integer> entityIds) {
		if (entityIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(this.jdbc.queryForList("SELECT e.id FROM entities e WHERE e.id = ANY(?) AND "
				+ inScope("e", scopingType, anchor), Integer.class, (Object) entityIds.toArray(Integer[]::new)));
	}

	/**
	 * The source and target entity per connection entity, for the connections among
	 * {@code connectionIds} (design D3 of us-128-override-role-list).
	 */
	public Map<Integer, int[]> endpoints(Collection<Integer> connectionIds) {
		Map<Integer, int[]> endpoints = new HashMap<>();
		if (connectionIds.isEmpty()) {
			return endpoints;
		}
		this.jdbc.query("SELECT entity_id, source_entity_id, target_entity_id FROM entity_connections WHERE entity_id = ANY(?)",
				(rs) -> {
					endpoints.put(rs.getInt(1), new int[] { rs.getInt(2), rs.getInt(3) });
				}, (Object) connectionIds.toArray(Integer[]::new));
		return endpoints;
	}

}

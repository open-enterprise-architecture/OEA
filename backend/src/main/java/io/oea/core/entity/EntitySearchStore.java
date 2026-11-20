package io.oea.core.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Searching entities by name within a set of metatypes (ADR-051: SQL only in core), for the entity
 * search of a diagram (us-074-diagram-canvas D2). Only entities that are not deleted are found.
 */
@Repository
public class EntitySearchStore {

	private final JdbcTemplate jdbc;

	public EntitySearchStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A found entity with the name and abbreviation of its metatype. */
	public record Found(int id, String name, UUID metatypeId, String metatypeName, String metatypeAbbreviation) {
	}

	/** One page of the entities of the metatypes whose name matches {@code like} (an escaped pattern or {@code null}), by name ignoring case, then ID. */
	public List<Found> page(Collection<UUID> metatypeIds, String like, int limit, long offset) {
		if (metatypeIds.isEmpty()) {
			return List.of();
		}
		List<Object> args = new ArrayList<>();
		String filter = filter(metatypeIds, like, args);
		args.add(limit);
		args.add(offset);
		return this.jdbc.query("""
				SELECT e.id, e.name, e.metatype_id, m.name, m.abbreviation
				FROM entities e JOIN metatypes m ON m.id = e.metatype_id
				WHERE\040""" + filter + " ORDER BY lower(e.name), e.id LIMIT ? OFFSET ?",
				(rs, n) -> new Found(rs.getInt(1), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
						rs.getString(5)),
				args.toArray());
	}

	/** The number of the entities the page query matches. */
	public long count(Collection<UUID> metatypeIds, String like) {
		if (metatypeIds.isEmpty()) {
			return 0;
		}
		List<Object> args = new ArrayList<>();
		String filter = filter(metatypeIds, like, args);
		return this.jdbc.queryForObject("SELECT count(*) FROM entities e WHERE " + filter, Long.class, args.toArray());
	}

	private static String filter(Collection<UUID> metatypeIds, String like, List<Object> args) {
		args.add(metatypeIds.stream().map(UUID::toString).toArray(String[]::new));
		StringBuilder filter = new StringBuilder("e.deleted_at IS NULL AND e.metatype_id = ANY (?::uuid[])");
		if (like != null) {
			filter.append(" AND e.name ILIKE ? ESCAPE '\\'");
			args.add(like);
		}
		return filter.toString();
	}

}

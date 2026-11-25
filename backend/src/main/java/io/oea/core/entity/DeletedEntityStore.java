package io.oea.core.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The list of deleted entities (design D1 of s17-deleted-entities; ADR-019, index {@code entities_deleted}):
 * newest deletion first, then by ID, with the actor, the cascade origin and the endpoints of a connection.
 * Read only; callers run in a transaction.
 */
@Repository
public class DeletedEntityStore {

	private static final String SELECT = """
			SELECT e.id, e.name, e.metatype_id, m.name, m.abbreviation, m.is_connection, e.version, e.deleted_at,
			       e.deleted_by, dp.name, dp.person_type, e.deleted_with_entity_id, w.name,
			       c.source_entity_id, s.name, c.target_entity_id, t.name
			FROM entities e
			JOIN metatypes m ON m.id = e.metatype_id
			LEFT JOIN persons dp ON dp.id = e.deleted_by
			LEFT JOIN entities w ON w.id = e.deleted_with_entity_id
			LEFT JOIN entity_connections c ON c.entity_id = e.id
			LEFT JOIN entities s ON s.id = c.source_entity_id
			LEFT JOIN entities t ON t.id = c.target_entity_id""";

	private final JdbcTemplate jdbc;

	public DeletedEntityStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** An entity named by a deleted entity, active or deleted. */
	public record Ref(int id, String name) {
	}

	/** The actor of a deletion; {@code name} is {@code null} when the person is unknown. */
	public record Actor(long id, String name, boolean serviceAccount) {
	}

	/**
	 * A deleted entity; {@code source} and {@code target} are set for a connection, {@code deletedBy}
	 * when an actor was recorded, {@code deletedWith} for an entity taken along by a cascade.
	 */
	public record Row(int id, String name, UUID metatypeId, String metatypeName, String metatypeAbbreviation,
			boolean connection, int version, Instant deletedAt, Actor deletedBy, Ref deletedWith, Ref source,
			Ref target) {
	}

	/**
	 * A page query: {@code like} is an escaped pattern or {@code null}, {@code metatypeId} or {@code null}
	 * for all; {@code afterDeletedAt} and {@code afterId} (both set or both {@code null}) start after that
	 * row instead of at {@code offset}.
	 */
	public record Query(String like, UUID metatypeId, Instant afterDeletedAt, Integer afterId, int limit,
			long offset) {
	}

	/** One page of the deleted entities, newest deletion first, then by descending ID. */
	public List<Row> page(Query query) {
		List<Object> args = new ArrayList<>();
		String where = where(query.like(), query.metatypeId(), args);
		if (query.afterDeletedAt() != null && query.afterId() != null) {
			where += " AND (e.deleted_at, e.id) < (?::timestamptz, ?)";
			args.add(Timestamp.from(query.afterDeletedAt()));
			args.add(query.afterId());
		}
		args.add(query.limit());
		args.add((query.afterDeletedAt() != null) ? 0L : query.offset());
		return this.jdbc.query(SELECT + where + " ORDER BY e.deleted_at DESC, e.id DESC LIMIT ? OFFSET ?",
				DeletedEntityStore::row, args.toArray());
	}

	/** The number of deleted entities that match the filters. */
	public long count(String like, UUID metatypeId) {
		List<Object> args = new ArrayList<>();
		String where = where(like, metatypeId, args);
		Long count = this.jdbc.queryForObject("SELECT count(*) FROM entities e" + where, Long.class, args.toArray());
		return (count != null) ? count : 0;
	}

	private static String where(String like, UUID metatypeId, List<Object> args) {
		StringBuilder where = new StringBuilder(" WHERE e.deleted_at IS NOT NULL");
		if (metatypeId != null) {
			where.append(" AND e.metatype_id = ?");
			args.add(metatypeId);
		}
		if (like != null) {
			where.append(" AND e.name ILIKE ? ESCAPE '\\'");
			args.add(like);
		}
		return where.toString();
	}

	private static Row row(ResultSet rs, int rowNum) throws SQLException {
		Long deletedBy = rs.getObject(9, Long.class);
		Integer withId = rs.getObject(12, Integer.class);
		Integer sourceId = rs.getObject(14, Integer.class);
		Integer targetId = rs.getObject(16, Integer.class);
		String abbreviation = rs.getString(5);
		return new Row(rs.getInt(1), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
				(abbreviation != null) ? abbreviation : "", rs.getBoolean(6), rs.getInt(7),
				rs.getTimestamp(8).toInstant(),
				(deletedBy != null) ? new Actor(deletedBy, rs.getString(10), "service-account".equals(rs.getString(11)))
						: null,
				(withId != null) ? new Ref(withId, rs.getString(13)) : null,
				(sourceId != null) ? new Ref(sourceId, rs.getString(15)) : null,
				(targetId != null) ? new Ref(targetId, rs.getString(17)) : null);
	}

}

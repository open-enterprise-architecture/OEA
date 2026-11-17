package io.oea.core.solution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The open issues of solutions (design D2 of us-153-open-issues; V35, V36): the metamodel
 * violations a go-live took live, their resolution with person and time, and the unresolved
 * issues of entities for the blocking check. An automatic resolution is never stored.
 */
@Repository
public class OpenIssueStore {

	private static final String SELECT = """
			SELECT i.id, i.solution_id, s.name, i.entity_id, e.name, i.field, i.property_def_id, i.kind, i.created_at,
			       i.resolution, i.resolved_at, i.resolved_by, p.name, i.kept_value
			FROM solution_open_issues i
			JOIN solutions s ON s.id = i.solution_id
			JOIN entities e ON e.id = i.entity_id
			LEFT JOIN persons p ON p.id = i.resolved_by
			""";

	private final JdbcTemplate jdbc;

	public OpenIssueStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * An open issue; {@code resolution}, {@code resolvedAt} and {@code resolvedBy} are {@code null}
	 * while it is not resolved, {@code keptValue} is set for {@code not-in-metamodel} only.
	 */
	public record IssueRow(UUID id, UUID solutionId, String solutionName, int entityId, String entityName, String field,
			UUID propertyDefId, String kind, Instant createdAt, String resolution, Instant resolvedAt, Long resolvedBy,
			String resolvedByName, String keptValue) {

		public boolean resolved() {
			return this.resolution != null;
		}

	}

	/** Stores an issue of a go-live; a second issue for the same field of the entity is ignored. */
	public UUID insert(UUID solutionId, int entityId, String field, UUID propertyDefId, String kind, String keptValue,
			Instant now) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO solution_open_issues (id, solution_id, entity_id, field, property_def_id, kind, kept_value,
				                                  created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""", id, solutionId, entityId, field, propertyDefId,
				kind, keptValue, Timestamp.from(now));
		return id;
	}

	/** All issues of the solution, by entity name and field. */
	public List<IssueRow> bySolution(UUID solutionId) {
		return this.jdbc.query(SELECT + "WHERE i.solution_id = ? ORDER BY lower(e.name), i.entity_id, i.field",
				OpenIssueStore::row, solutionId);
	}

	/** The unresolved issues of the entities, of every solution. */
	public List<IssueRow> unresolved(Collection<Integer> entityIds) {
		if (entityIds.isEmpty()) {
			return List.of();
		}
		return this.jdbc.query(SELECT + """
				WHERE i.resolution IS NULL AND i.entity_id = ANY (?::int[])
				ORDER BY i.entity_id, lower(s.name), i.field""", OpenIssueStore::row,
				(Object) entityIds.stream().map(String::valueOf).toArray(String[]::new));
	}

	/** Locks the issues of the solution with these IDs; unknown IDs and issues of other solutions are left out. */
	public List<IssueRow> lock(UUID solutionId, Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		this.jdbc.queryForList("""
				SELECT id FROM solution_open_issues WHERE solution_id = ? AND id = ANY (?::uuid[]) ORDER BY id FOR UPDATE""",
				UUID.class, solutionId, (Object) ids.stream().map(UUID::toString).toArray(String[]::new));
		return this.jdbc.query(SELECT + "WHERE i.solution_id = ? AND i.id = ANY (?::uuid[])", OpenIssueStore::row,
				solutionId, (Object) ids.stream().map(UUID::toString).toArray(String[]::new));
	}

	/** Marks the issue resolved with {@code value} or {@code removed}. */
	public void resolve(UUID id, String resolution, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE solution_open_issues SET resolution = ?, resolved_at = ?, resolved_by = ?
				WHERE id = ? AND resolution IS NULL""", resolution, Timestamp.from(now), personId, id);
	}

	private static IssueRow row(ResultSet rs, int n) throws SQLException {
		Timestamp resolvedAt = rs.getTimestamp(11);
		return new IssueRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4),
				rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class), rs.getString(8),
				rs.getTimestamp(9).toInstant(), rs.getString(10), (resolvedAt != null) ? resolvedAt.toInstant() : null,
				rs.getObject(12, Long.class), rs.getString(13), rs.getString(14));
	}

}

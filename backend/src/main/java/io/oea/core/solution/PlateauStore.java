package io.oea.core.solution;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The plateaus of plateau mode (design D1, D2 of us-084-plateaus; V37): reading, inserting and
 * updating them, the chain of predecessors and the number of solutions that target a plateau.
 */
@Repository
public class PlateauStore {

	private static final String SELECT = """
			SELECT p.id, p.name, p.description, p.status, p.succeeds, p.basis, p.valid_from, p.valid_to, p.created_at,
			       p.created_by, c.name, p.updated_at,
			       (SELECT count(*) FROM solutions s WHERE s.to_plateau_id = p.id)
			FROM plateaus p JOIN persons c ON c.id = p.created_by
			""";

	/**
	 * Order of the overview (REQ-085): the baseline, the transition and target plateaus by
	 * {@code validFrom}, then the realized ones newest first.
	 */
	private static final String ORDER = """
			ORDER BY CASE p.status WHEN 'baseline' THEN 0 WHEN 'realized' THEN 2 ELSE 1 END,
			         CASE WHEN p.status = 'realized' THEN coalesce(p.valid_to, p.valid_from) END DESC NULLS LAST,
			         p.valid_from NULLS LAST, lower(p.name), p.id""";

	private final JdbcTemplate jdbc;

	public PlateauStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A plateau with its creator and the number of solutions that target it. */
	public record PlateauRow(UUID id, String name, String description, String status, UUID succeeds, String basis,
			LocalDate validFrom, LocalDate validTo, Instant createdAt, long createdById, String createdByName,
			Instant updatedAt, int solutionCount) {
	}

	/** The fields a write sets. */
	public record Fields(String name, String description, UUID succeeds, String basis, LocalDate validFrom) {
	}

	/** Stores a new plateau; {@code false} when its name is taken or a second baseline would arise. */
	public boolean insert(UUID id, String status, Fields fields, long personId, Instant now) {
		return this.jdbc.update("""
				INSERT INTO plateaus (id, name, description, status, succeeds, basis, valid_from, created_at, created_by)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""", id, fields.name(), fields.description(),
				status, fields.succeeds(), fields.basis(), date(fields.validFrom()), Timestamp.from(now),
				personId) == 1;
	}

	/** Updates the fields of a plateau; the caller checked the name. */
	public void update(UUID id, Fields fields, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE plateaus SET name = ?, description = ?, succeeds = ?, basis = ?, valid_from = ?, updated_at = ?,
				                    updated_by = ?
				WHERE id = ?""", fields.name(), fields.description(), fields.succeeds(), fields.basis(),
				date(fields.validFrom()), Timestamp.from(now), personId, id);
	}

	/** Whether another plateau has the name, compared case-insensitively. */
	public boolean nameTaken(String name, UUID except) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM plateaus WHERE lower(name) = lower(?) AND id IS DISTINCT FROM ?)",
				Boolean.class, name, except));
	}

	/** One plateau. */
	public Optional<PlateauRow> find(UUID id) {
		return this.jdbc.query(SELECT + "WHERE p.id = ?", PlateauStore::row, id).stream().findFirst();
	}

	/**
	 * One plateau, locked: {@code FOR UPDATE} for a write of it, {@code FOR SHARE} while a solution
	 * is created against it (D3).
	 */
	public Optional<PlateauRow> lock(UUID id, boolean exclusive) {
		this.jdbc.query("SELECT id FROM plateaus WHERE id = ? " + (exclusive ? "FOR UPDATE" : "FOR SHARE"),
				(rs, row) -> rs.getObject(1), id);
		return find(id);
	}

	/** All plateaus in the order of the overview. */
	public List<PlateauRow> list() {
		return this.jdbc.query(SELECT + ORDER, PlateauStore::row);
	}

	/** The baseline plateau, if any. */
	public Optional<PlateauRow> baseline() {
		return this.jdbc.query(SELECT + "WHERE p.status = 'baseline'", PlateauStore::row).stream().findFirst();
	}

	/**
	 * The active solutions of the plateau in the order of its go-live (design D2 of
	 * us-086-plateau-go-live): by the last change of their deltas, a solution without deltas first,
	 * then by name and ID.
	 */
	public List<UUID> goLiveOrder(UUID plateauId) {
		return this.jdbc.queryForList("""
				SELECT s.id FROM solutions s
				LEFT JOIN entity_deltas d ON d.solution_id = s.id
				WHERE s.to_plateau_id = ? AND s.status NOT IN ('implemented', 'archived')
				GROUP BY s.id, s.name
				ORDER BY max(coalesce(d.updated_at, d.created_at)) NULLS FIRST, lower(s.name), s.id""", UUID.class,
				plateauId);
	}

	/**
	 * The transition of a plateau go-live (design D3): the old baseline is realized until
	 * {@code day} first, so that the partial unique index keeps one baseline, then the plateau
	 * becomes the baseline from {@code day}, without predecessor and basis as a baseline has none; the plateaus that succeeded the old baseline succeed
	 * the new one, and their solutions start from it. Returns the IDs of those plateaus.
	 */
	public List<UUID> transition(UUID plateauId, UUID oldBaselineId, LocalDate day, long personId, Instant now) {
		this.jdbc.update("UPDATE plateaus SET status = 'realized', valid_to = ? WHERE id = ?", Date.valueOf(day),
				oldBaselineId);
		this.jdbc.update("""
				UPDATE plateaus SET status = 'baseline', valid_from = ?, succeeds = NULL, basis = NULL, updated_at = ?,
				                    updated_by = ?
				WHERE id = ?""",
				Date.valueOf(day), Timestamp.from(now), personId, plateauId);
		List<UUID> followers = this.jdbc.queryForList("""
				UPDATE plateaus SET succeeds = ?, updated_at = ?, updated_by = ?
				WHERE succeeds = ? AND id <> ? RETURNING id""", UUID.class, plateauId, Timestamp.from(now), personId,
				oldBaselineId, plateauId);
		this.jdbc.update("UPDATE solutions SET from_plateau_id = ? WHERE from_plateau_id = ? AND to_plateau_id <> ?",
				plateauId, oldBaselineId, plateauId);
		return followers;
	}

	/** Whether a baseline plateau exists. */
	public boolean baselineExists() {
		return Boolean.TRUE.equals(this.jdbc
			.queryForObject("SELECT EXISTS (SELECT 1 FROM plateaus WHERE status = 'baseline')", Boolean.class));
	}

	/**
	 * The chain of predecessors starting with the plateau itself, nearest first; it stops at a
	 * plateau seen before, so a chain is finite even if it contained a cycle.
	 */
	public List<PlateauRow> chain(UUID id) {
		List<UUID> ids = this.jdbc.queryForList("""
				WITH RECURSIVE chain (id, succeeds, depth, seen) AS (
				    SELECT id, succeeds, 0, ARRAY[id] FROM plateaus WHERE id = ?
				    UNION ALL
				    SELECT p.id, p.succeeds, c.depth + 1, c.seen || p.id
				    FROM plateaus p JOIN chain c ON p.id = c.succeeds
				    WHERE NOT p.id = ANY (c.seen)
				)
				SELECT id FROM chain ORDER BY depth""", UUID.class, id);
		return ids.stream().map((plateau) -> find(plateau).orElseThrow()).toList();
	}

	private static Date date(LocalDate value) {
		return (value != null) ? Date.valueOf(value) : null;
	}

	private static PlateauRow row(ResultSet rs, int row) throws SQLException {
		Date from = rs.getDate(7);
		Date to = rs.getDate(8);
		Timestamp updated = rs.getTimestamp(12);
		return new PlateauRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getObject(5, UUID.class), rs.getString(6), (from != null) ? from.toLocalDate() : null,
				(to != null) ? to.toLocalDate() : null, rs.getTimestamp(9).toInstant(), rs.getLong(10),
				rs.getString(11), (updated != null) ? updated.toInstant() : null, rs.getInt(13));
	}

}

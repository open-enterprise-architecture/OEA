package io.oea.core.solution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The solutions themselves (US-038, REQ-038): insert and reads of {@code solutions} with the names
 * of the owner and the creator. Callers run in a transaction.
 */
@Repository
public class SolutionStore {

	private static final String COLUMNS = """
			s.id, s.name, s.description, s.status, s.from_plateau_id, s.to_plateau_id, s.owner_id, o.name,
			s.created_at, s.created_by, c.name""";

	private static final String FROM = " FROM solutions s JOIN persons o ON o.id = s.owner_id"
			+ " JOIN persons c ON c.id = s.created_by";

	private final JdbcTemplate jdbc;

	public SolutionStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A solution with the names of its owner and creator. */
	public record SolutionRow(UUID id, String name, String description, String status, UUID fromPlateauId,
			UUID toPlateauId, long ownerId, String ownerName, Instant createdAt, long createdById,
			String createdByName) {
	}

	/**
	 * Inserts a draft solution owned and created by {@code personId}.
	 *
	 * @return {@code false} if the name is already taken (nothing is inserted)
	 */
	public boolean insert(UUID id, String name, String description, long personId, UUID fromPlateauId,
			UUID toPlateauId) {
		return this.jdbc.update("""
				INSERT INTO solutions (id, name, description, owner_id, created_by, from_plateau_id, to_plateau_id)
				VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""", id, name, description, personId, personId,
				fromPlateauId, toPlateauId) > 0;
	}

	/** All solutions, ordered by name regardless of case, then by ID. */
	public List<SolutionRow> list() {
		return this.jdbc.query("SELECT " + COLUMNS + FROM + " ORDER BY lower(s.name), s.id", SolutionStore::row);
	}

	public Optional<SolutionRow> find(UUID id) {
		return this.jdbc.query("SELECT " + COLUMNS + FROM + " WHERE s.id = ?", SolutionStore::row, id)
			.stream()
			.findFirst();
	}

	/** The status of the solution; empty for an unknown ID. */
	public Optional<String> status(UUID id) {
		return this.jdbc.queryForList("SELECT status FROM solutions WHERE id = ?", String.class, id)
			.stream()
			.findFirst();
	}

	private static SolutionRow row(ResultSet rs, int row) throws SQLException {
		return new SolutionRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getObject(5, UUID.class), rs.getObject(6, UUID.class), rs.getLong(7), rs.getString(8),
				rs.getTimestamp(9).toInstant(), rs.getLong(10), rs.getString(11));
	}

}

package io.oea.app.solution;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating and reading solutions (US-038, REQ-038; design D3 of us-038-create-solution). A
 * solution is created in project mode: status {@code draft}, the caller as owner and creator, no
 * plateau references. The permission {@code solution:write} is checked from the contract.
 */
@Service
public class Solutions {

	private static final String COLUMNS = """
			s.id, s.name, s.description, s.status, s.from_plateau_id, s.to_plateau_id, s.owner_id, o.name,
			s.created_at, s.created_by, c.name""";

	private static final String FROM = " FROM solutions s JOIN persons o ON o.id = s.owner_id"
			+ " JOIN persons c ON c.id = s.created_by";

	private final JdbcTemplate jdbc;

	private final AuditWriter audit;

	Solutions(JdbcTemplate jdbc, AuditWriter audit) {
		this.jdbc = jdbc;
		this.audit = audit;
	}

	/** The input of {@link #create}; the contract checks the lengths. */
	public record NewSolution(String name, String description) {
	}

	/** A solution with the names of its owner and creator. */
	public record Solution(UUID id, String name, String description, String status, UUID fromPlateauId,
			UUID toPlateauId, long ownerId, String ownerName, Instant createdAt, long createdById,
			String createdByName) {
	}

	/**
	 * Creates the solution and audits {@code solution.created}.
	 *
	 * @throws PermissionDeniedException for an account that is not a person
	 * @throws InvalidSolutionException naming {@code name} for a blank or taken name
	 */
	@Transactional
	public Solution create(OeaPrincipal principal, NewSolution input) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty()) {
			throw new InvalidSolutionException("name", "The name must not be blank.");
		}
		UUID id = UUID.randomUUID();
		int inserted = this.jdbc.update("""
				INSERT INTO solutions (id, name, description, owner_id, created_by) VALUES (?, ?, ?, ?, ?)
				ON CONFLICT DO NOTHING""", id, name, input.description(), person, person);
		if (inserted == 0) {
			throw new InvalidSolutionException("name", "Name '" + name + "' is already taken");
		}
		this.audit.append(new AuditEvent("solution.created", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "solution",
				id.toString(), Map.of("name", name)));
		return find(id).orElseThrow();
	}

	/** All solutions, ordered by name case-insensitively. */
	@Transactional(readOnly = true)
	public List<Solution> list() {
		return this.jdbc.query("SELECT " + COLUMNS + FROM + " ORDER BY lower(s.name), s.id", Solutions::solution);
	}

	/**
	 * One solution.
	 *
	 * @throws NoSuchElementException for an unknown ID (404)
	 */
	@Transactional(readOnly = true)
	public Solution get(UUID id) {
		return find(id).orElseThrow(() -> new NoSuchElementException("solution " + id));
	}

	private java.util.Optional<Solution> find(UUID id) {
		return this.jdbc.query("SELECT " + COLUMNS + FROM + " WHERE s.id = ?", Solutions::solution, id)
			.stream()
			.findFirst();
	}

	private static Solution solution(ResultSet rs, int row) throws SQLException {
		return new Solution(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getObject(5, UUID.class), rs.getObject(6, UUID.class), rs.getLong(7), rs.getString(8),
				rs.getTimestamp(9).toInstant(), rs.getLong(10), rs.getString(11));
	}

}

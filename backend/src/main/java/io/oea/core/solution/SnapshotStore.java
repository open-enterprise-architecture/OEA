package io.oea.core.solution;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The snapshots of solutions (design D1, D9, D10 of us-153-open-issues; V36): a property, or one
 * enumeration value, of a metatype that a solution keeps after a metamodel change removed it
 * (decision {@code keep}, US-154 AC3), until its go-live drops them.
 */
@Repository
public class SnapshotStore {

	private final JdbcTemplate jdbc;

	public SnapshotStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A snapshot; {@code enumValue} is {@code null} when it keeps the whole property. */
	public record SnapshotRow(UUID id, UUID solutionId, UUID metatypeId, UUID propertyDefId, String enumValue,
			Instant createdAt) {
	}

	/** Stores a snapshot; an equal one of the solution is kept. */
	public void insert(UUID solutionId, UUID metatypeId, UUID propertyDefId, String enumValue, long personId,
			Instant now) {
		this.jdbc.update("""
				INSERT INTO solution_metamodel_snapshots (id, solution_id, metatype_id, property_def_id, enum_value,
				                                          created_at, created_by)
				VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""", UUID.randomUUID(), solutionId, metatypeId,
				propertyDefId, enumValue, Timestamp.from(now), personId);
	}

	/** The snapshots of the solution. */
	public List<SnapshotRow> of(UUID solutionId) {
		return this.jdbc.query("""
				SELECT id, solution_id, metatype_id, property_def_id, enum_value, created_at
				FROM solution_metamodel_snapshots WHERE solution_id = ? ORDER BY created_at, id""",
				(rs, n) -> new SnapshotRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
						rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5),
						rs.getTimestamp(6).toInstant()),
				solutionId);
	}

	/** The solution of a solution extension configuration, or {@code null} for any other configuration. */
	public UUID solutionOfConfiguration(UUID configId) {
		return this.jdbc
			.queryForList("SELECT solution_id FROM metamodel_configurations WHERE id = ? AND scope = 'solution'",
					UUID.class, configId)
			.stream()
			.findFirst()
			.orElse(null);
	}

	/** Whether a snapshot keeps the property definition. */
	public boolean keeps(UUID propertyDefId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM solution_metamodel_snapshots WHERE property_def_id = ?)", Boolean.class,
				propertyDefId));
	}

	/** Drops the snapshots of the solution (its go-live). */
	public int deleteOf(UUID solutionId) {
		return this.jdbc.update("DELETE FROM solution_metamodel_snapshots WHERE solution_id = ?", solutionId);
	}

}

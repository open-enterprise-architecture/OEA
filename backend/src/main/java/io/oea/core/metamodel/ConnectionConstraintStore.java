package io.oea.core.metamodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The allowed sources and targets of a connection metatype in {@code metatype_connection_constraints}
 * (design D2 of us-036-connection-metatypes): no row for a direction allows every metatype.
 */
@Repository
public class ConnectionConstraintStore {

	public static final String SOURCE = "source";

	public static final String TARGET = "target";

	private final JdbcTemplate jdbc;

	public ConnectionConstraintStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** One allowed metatype of a direction. */
	public record ConstraintRow(UUID metatypeId, String direction, UUID allowedMetatypeId) {
	}

	/** All constraints, ordered by metatype, direction and allowed metatype. */
	public List<ConstraintRow> all() {
		return this.jdbc.query("""
				SELECT metatype_id, direction, allowed_metatype_id FROM metatype_connection_constraints
				ORDER BY metatype_id, direction, allowed_metatype_id""",
				(rs, n) -> new ConstraintRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class)));
	}

	/** The stored lists of a metatype by direction ({@link #SOURCE}, {@link #TARGET}). */
	public Map<String, List<UUID>> of(UUID metatypeId) {
		List<UUID> sources = new ArrayList<>();
		List<UUID> targets = new ArrayList<>();
		this.jdbc.query("SELECT direction, allowed_metatype_id FROM metatype_connection_constraints WHERE metatype_id = ?",
				(rs) -> {
					(SOURCE.equals(rs.getString(1)) ? sources : targets).add(rs.getObject(2, UUID.class));
				}, metatypeId);
		return Map.of(SOURCE, sources, TARGET, targets);
	}

	/** Replaces the lists of a metatype. */
	public void replace(UUID metatypeId, List<UUID> sources, List<UUID> targets) {
		deleteOf(metatypeId);
		insert(metatypeId, SOURCE, sources);
		insert(metatypeId, TARGET, targets);
	}

	/** Removes the lists of a metatype. */
	public void deleteOf(UUID metatypeId) {
		this.jdbc.update("DELETE FROM metatype_connection_constraints WHERE metatype_id = ?", metatypeId);
	}

	private void insert(UUID metatypeId, String direction, List<UUID> allowed) {
		for (UUID metatype : allowed) {
			this.jdbc.update("""
					INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
					VALUES (?, ?, ?) ON CONFLICT DO NOTHING""", metatypeId, direction, metatype);
		}
	}

}

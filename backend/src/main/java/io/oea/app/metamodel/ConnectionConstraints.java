package io.oea.app.metamodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The allowed sources and targets of a connection metatype in {@code metatype_connection_constraints}
 * (design D2 of us-036-connection-metatypes): no row for a direction allows every metatype.
 */
final class ConnectionConstraints {

	static final String SOURCE = "source";

	static final String TARGET = "target";

	private ConnectionConstraints() {
	}

	/** The stored lists of a metatype by direction. */
	static Map<String, List<UUID>> of(JdbcTemplate jdbc, UUID metatypeId) {
		List<UUID> sources = new ArrayList<>();
		List<UUID> targets = new ArrayList<>();
		jdbc.query("SELECT direction, allowed_metatype_id FROM metatype_connection_constraints WHERE metatype_id = ?",
				(rs) -> {
					(SOURCE.equals(rs.getString(1)) ? sources : targets).add(rs.getObject(2, UUID.class));
				}, metatypeId);
		return Map.of(SOURCE, sources, TARGET, targets);
	}

	/** Replaces the lists of a metatype. */
	static void replace(JdbcTemplate jdbc, UUID metatypeId, List<UUID> sources, List<UUID> targets) {
		jdbc.update("DELETE FROM metatype_connection_constraints WHERE metatype_id = ?", metatypeId);
		insert(jdbc, metatypeId, SOURCE, sources);
		insert(jdbc, metatypeId, TARGET, targets);
	}

	private static void insert(JdbcTemplate jdbc, UUID metatypeId, String direction, List<UUID> allowed) {
		for (UUID metatype : allowed) {
			jdbc.update("""
					INSERT INTO metatype_connection_constraints (metatype_id, direction, allowed_metatype_id)
					VALUES (?, ?, ?) ON CONFLICT DO NOTHING""", metatypeId, direction, metatype);
		}
	}

}

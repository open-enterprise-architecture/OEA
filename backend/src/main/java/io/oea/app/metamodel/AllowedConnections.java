package io.oea.app.metamodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which connection metatypes may connect two metatypes (design D5 of us-036-connection-metatypes;
 * ADR-026): an allowed endpoint metatype also allows its subtypes, and no constraint for a
 * direction allows every metatype.
 */
@Service
public class AllowedConnections {

	public enum Direction {

		SOURCE_TO_TARGET, TARGET_TO_SOURCE, BIDIRECTIONAL

	}

	/** A connection metatype that may connect the two metatypes, and in which direction. */
	public record Option(MetatypeCatalog.MetatypeRef connection, Direction direction) {
	}

	private final JdbcTemplate jdbc;

	public AllowedConnections(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public List<Option> between(UUID sourceMetatypeId, UUID targetMetatypeId) {
		Map<UUID, UUID> bases = new HashMap<>();
		Map<UUID, MetatypeCatalog.MetatypeRef> connections = new HashMap<>();
		List<UUID> order = new ArrayList<>();
		Set<UUID> existing = new HashSet<>();
		this.jdbc.query("""
				SELECT id, name, abbreviation, is_connection, base_metatype_id FROM metatypes
				ORDER BY sort_order, name""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			existing.add(id);
			UUID base = rs.getObject(5, UUID.class);
			if (base != null) {
				bases.put(id, base);
			}
			if (rs.getBoolean(4)) {
				connections.put(id, new MetatypeCatalog.MetatypeRef(id, rs.getString(2), rs.getString(3)));
				order.add(id);
			}
		});
		if (!existing.contains(sourceMetatypeId) || !existing.contains(targetMetatypeId)) {
			throw new NoSuchElementException("unknown metatype");
		}
		Map<UUID, Map<String, Set<UUID>>> constraints = new HashMap<>();
		this.jdbc.query("SELECT metatype_id, direction, allowed_metatype_id FROM metatype_connection_constraints",
				(rs) -> {
					constraints.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new HashMap<>())
						.computeIfAbsent(rs.getString(2), (k) -> new HashSet<>())
						.add(rs.getObject(3, UUID.class));
				});
		List<Option> options = new ArrayList<>();
		for (UUID connection : order) {
			Map<String, Set<UUID>> own = constraints.getOrDefault(connection, Map.of());
			boolean forward = allows(own.get(ConnectionConstraints.SOURCE), sourceMetatypeId, bases)
					&& allows(own.get(ConnectionConstraints.TARGET), targetMetatypeId, bases);
			boolean backward = allows(own.get(ConnectionConstraints.SOURCE), targetMetatypeId, bases)
					&& allows(own.get(ConnectionConstraints.TARGET), sourceMetatypeId, bases);
			if (forward || backward) {
				options.add(new Option(connections.get(connection), forward && backward ? Direction.BIDIRECTIONAL
						: forward ? Direction.SOURCE_TO_TARGET : Direction.TARGET_TO_SOURCE));
			}
		}
		return options;
	}

	/** Whether the metatype or one of its base types is allowed; no list allows every metatype. */
	public static boolean allows(Set<UUID> allowed, UUID metatype, Map<UUID, UUID> bases) {
		if (allowed == null || allowed.isEmpty()) {
			return true;
		}
		return MetamodelEffects.chain(metatype, bases).stream().anyMatch(allowed::contains);
	}

}

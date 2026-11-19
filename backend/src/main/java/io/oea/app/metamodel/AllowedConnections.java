package io.oea.app.metamodel;

import io.oea.core.metamodel.BuiltInMetatypes;
import io.oea.core.metamodel.ConnectionConstraintStore;
import io.oea.core.metamodel.MetatypeStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
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

	private final MetatypeStore metatypes;

	private final ConnectionConstraintStore constraintStore;

	public AllowedConnections(MetatypeStore metatypes, ConnectionConstraintStore constraintStore) {
		this.metatypes = metatypes;
		this.constraintStore = constraintStore;
	}

	@Transactional(readOnly = true)
	public List<Option> between(UUID sourceMetatypeId, UUID targetMetatypeId) {
		Map<UUID, UUID> bases = new HashMap<>();
		Map<UUID, MetatypeCatalog.MetatypeRef> connections = new HashMap<>();
		List<UUID> order = new ArrayList<>();
		Set<UUID> existing = new HashSet<>();
		for (MetatypeStore.KindRow row : this.metatypes.kinds()) {
			UUID id = row.id();
			existing.add(id);
			if (row.baseMetatypeId() != null) {
				bases.put(id, row.baseMetatypeId());
			}
			if (row.connection()) {
				connections.put(id, new MetatypeCatalog.MetatypeRef(id, row.name(), row.abbreviation()));
				order.add(id);
			}
		}
		if (!existing.contains(sourceMetatypeId) || !existing.contains(targetMetatypeId)) {
			throw new NoSuchElementException("unknown metatype");
		}
		if (BuiltInMetatypes.STRUCTURING_AID.equals(sourceMetatypeId)
				|| BuiltInMetatypes.STRUCTURING_AID.equals(targetMetatypeId)) {
			// a structuring aid has no semantic connections (US-136)
			return List.of();
		}
		Map<UUID, Map<String, Set<UUID>>> constraints = new HashMap<>();
		for (ConnectionConstraintStore.ConstraintRow row : this.constraintStore.all()) {
			constraints.computeIfAbsent(row.metatypeId(), (k) -> new HashMap<>())
				.computeIfAbsent(row.direction(), (k) -> new HashSet<>())
				.add(row.allowedMetatypeId());
		}
		List<Option> options = new ArrayList<>();
		for (UUID connection : order) {
			Map<String, Set<UUID>> own = constraints.getOrDefault(connection, Map.of());
			boolean forward = allows(own.get(ConnectionConstraintStore.SOURCE), sourceMetatypeId, bases)
					&& allows(own.get(ConnectionConstraintStore.TARGET), targetMetatypeId, bases);
			boolean backward = allows(own.get(ConnectionConstraintStore.SOURCE), targetMetatypeId, bases)
					&& allows(own.get(ConnectionConstraintStore.TARGET), sourceMetatypeId, bases);
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

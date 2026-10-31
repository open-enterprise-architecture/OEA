package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.entity.PropertyValueStore;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Base type chains and the effects of a metamodel change on the entities (design D4, D6, D7 of
 * us-032-metatype-editing). A change takes a snapshot of the effective mappings before it and
 * applies the difference afterwards: the values of properties a metatype no longer has are
 * removed, entities that miss a new mandatory property are marked.
 */
@Component
public class MetamodelEffects {

	private final JdbcTemplate jdbc;

	private final PropertyValueStore values;

	private final Clock clock;

	public MetamodelEffects(JdbcTemplate jdbc, PropertyValueStore values, Clock clock) {
		this.jdbc = jdbc;
		this.values = values;
		this.clock = clock;
	}

	/** The effective mappings: metatype → property definition → necessity, inherited ones included. */
	public record Snapshot(Map<UUID, Map<UUID, String>> mappings) {
	}

	/** Entities affected per property definition. */
	public record Effects(Map<UUID, Integer> removed, Map<UUID, Integer> marked) {

		int removed(UUID propertyDefId) {
			return this.removed.getOrDefault(propertyDefId, 0);
		}

		int marked(UUID propertyDefId) {
			return this.marked.getOrDefault(propertyDefId, 0);
		}

	}

	/** Base type of each metatype that has one. */
	Map<UUID, UUID> bases() {
		Map<UUID, UUID> bases = new HashMap<>();
		this.jdbc.query("SELECT id, base_metatype_id FROM metatypes WHERE base_metatype_id IS NOT NULL",
				(rs) -> {
					bases.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
				});
		return bases;
	}

	/** The metatype and its base types, nearest first. */
	static List<UUID> chain(UUID id, Map<UUID, UUID> bases) {
		List<UUID> chain = new ArrayList<>();
		for (UUID type = id; type != null && !chain.contains(type); type = bases.get(type)) {
			chain.add(type);
		}
		return chain;
	}

	/** The metatype and all its subtypes. */
	static Set<UUID> subtree(UUID id, Map<UUID, UUID> bases) {
		Set<UUID> subtree = new HashSet<>();
		subtree.add(id);
		boolean grown = true;
		while (grown) {
			grown = false;
			for (Map.Entry<UUID, UUID> base : bases.entrySet()) {
				if (subtree.contains(base.getValue()) && subtree.add(base.getKey())) {
					grown = true;
				}
			}
		}
		return subtree;
	}

	/** One metamodel change at a time: the instance configuration is the lock (as for the import). */
	void lock() {
		this.jdbc.queryForObject("SELECT id FROM metamodel_configurations WHERE id = ? FOR UPDATE", UUID.class,
				PackageImporter.INSTANCE_CONFIGURATION);
	}

	/** IDs as an SQL parameter for {@code ANY (?::uuid[])}. */
	static String[] ids(Collection<UUID> ids) {
		return ids.stream().map(UUID::toString).toArray(String[]::new);
	}

	LocalDate today() {
		return LocalDate.now(this.clock);
	}

	Instant now() {
		return this.clock.instant();
	}

	/** The active own mappings (latest {@code valid_from} not after today) per metatype. */
	private Map<UUID, Map<UUID, String>> ownMappings() {
		Map<UUID, Map<UUID, String>> own = new HashMap<>();
		this.jdbc.query("""
				SELECT DISTINCT ON (metatype_id, property_def_id) metatype_id, property_def_id, necessity
				FROM metatype_property_mappings WHERE valid_from <= ?
				ORDER BY metatype_id, property_def_id, valid_from DESC""", (rs) -> {
			own.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new LinkedHashMap<>())
				.put(rs.getObject(2, UUID.class), rs.getString(3));
		}, Date.valueOf(today()));
		return own;
	}

	public Snapshot snapshot() {
		Map<UUID, Map<UUID, String>> own = ownMappings();
		Map<UUID, UUID> bases = bases();
		Map<UUID, Map<UUID, String>> effective = new HashMap<>();
		for (UUID metatype : this.jdbc.queryForList("SELECT id FROM metatypes", UUID.class)) {
			Map<UUID, String> mappings = new LinkedHashMap<>();
			for (UUID type : chain(metatype, bases)) {
				own.getOrDefault(type, Map.of()).forEach(mappings::putIfAbsent);
			}
			effective.put(metatype, mappings);
		}
		return new Snapshot(effective);
	}

	/** Applies the difference between the snapshot and the current effective mappings. */
	public Effects apply(Snapshot before, OeaPrincipal principal) {
		Snapshot after = snapshot();
		Instant now = now();
		long author = author(principal);
		Map<UUID, Integer> removed = new LinkedHashMap<>();
		Map<UUID, Integer> marked = new LinkedHashMap<>();
		Set<UUID> metatypes = new HashSet<>(before.mappings().keySet());
		metatypes.addAll(after.mappings().keySet());
		for (UUID metatype : metatypes) {
			Map<UUID, String> old = before.mappings().getOrDefault(metatype, Map.of());
			Map<UUID, String> current = after.mappings().getOrDefault(metatype, Map.of());
			for (UUID property : old.keySet()) {
				if (!current.containsKey(property)) {
					removed.merge(property, this.values.removeValues(property, List.of(metatype), null, author, now),
							Integer::sum);
				}
			}
			current.forEach((property, necessity) -> {
				if ("mandatory".equals(necessity) && !"mandatory".equals(old.get(property))) {
					marked.merge(property, this.values.mark(property, List.of(metatype), now), Integer::sum);
				}
			});
		}
		return new Effects(removed, marked);
	}

	/** Marks the entities of every metatype where the property is effectively mandatory. */
	int markWhereMandatory(UUID propertyDefId) {
		Instant now = now();
		int marked = 0;
		for (Map.Entry<UUID, Map<UUID, String>> metatype : snapshot().mappings().entrySet()) {
			if ("mandatory".equals(metatype.getValue().get(propertyDefId))) {
				marked += this.values.mark(propertyDefId, List.of(metatype.getKey()), now);
			}
		}
		return marked;
	}

	/** Metamodel changes need {@code metamodel:write}, which only persons hold (V3, ADR-032). */
	static long author(OeaPrincipal principal) {
		if (principal.personId() == null) {
			throw new IllegalStateException("metamodel changes are made by persons");
		}
		return principal.personId();
	}

}

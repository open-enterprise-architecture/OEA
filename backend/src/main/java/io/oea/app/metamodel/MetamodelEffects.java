package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.metamodel.MetamodelConfigurationStore;
import io.oea.core.metamodel.MetamodelLabelStore;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.core.metamodel.PropertyMappingStore;
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
import org.springframework.stereotype.Component;

/**
 * Base type chains and the effects of a metamodel change on the entities (design D4, D6, D7 of
 * us-032-metatype-editing). A change takes a snapshot of the effective mappings before it and
 * applies the difference afterwards: the values of properties a metatype no longer has are
 * removed, entities that miss a new mandatory property are marked.
 */
@Component
public class MetamodelEffects {

	private final MetamodelConfigurationStore configurations;

	private final MetatypeStore metatypes;

	private final PropertyMappingStore mappings;

	private final MetamodelLabelStore labels;

	private final PropertyValueStore values;

	private final Clock clock;

	public MetamodelEffects(MetamodelConfigurationStore configurations, MetatypeStore metatypes,
			PropertyMappingStore mappings, MetamodelLabelStore labels, PropertyValueStore values, Clock clock) {
		this.configurations = configurations;
		this.metatypes = metatypes;
		this.mappings = mappings;
		this.labels = labels;
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
		return this.metatypes.bases();
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
		this.configurations.lock(PackageImporter.INSTANCE_CONFIGURATION);
	}

	/** IDs as an SQL parameter for {@code ANY (?::uuid[])}. */
	static String[] ids(Collection<UUID> ids) {
		return ids.stream().map(UUID::toString).toArray(String[]::new);
	}

	LocalDate today() {
		return LocalDate.now(this.clock);
	}

	/** Removes the labels of elements that no longer exist; called by every delete path (design D2 of us-140-dynamic-labels). */
	void removeOrphanLabels() {
		this.labels.deleteOrphans();
	}

	Instant now() {
		return this.clock.instant();
	}

	/** The active own mappings (latest {@code valid_from} not after the date) per metatype. */
	private Map<UUID, Map<UUID, String>> ownMappings(LocalDate date) {
		return this.mappings.active(date);
	}

	public Snapshot snapshot() {
		return snapshot(today());
	}

	/** The effective mappings on the date. */
	Snapshot snapshot(LocalDate date) {
		Map<UUID, Map<UUID, String>> own = ownMappings(date);
		Map<UUID, UUID> bases = bases();
		Map<UUID, Map<UUID, String>> effective = new HashMap<>();
		for (UUID metatype : this.metatypes.ids()) {
			Map<UUID, String> mappings = new LinkedHashMap<>();
			for (UUID type : chain(metatype, bases)) {
				own.getOrDefault(type, Map.of()).forEach(mappings::putIfAbsent);
			}
			effective.put(metatype, mappings);
		}
		return new Snapshot(effective);
	}

	/** The properties metatypes had in the snapshot and no longer have (US-154; design D9 of us-153-open-issues). */
	public List<SolutionImpact.Lost> lost(Snapshot before) {
		Snapshot after = snapshot();
		List<SolutionImpact.Lost> lost = new ArrayList<>();
		before.mappings().forEach((metatype, old) -> {
			Map<UUID, String> current = after.mappings().getOrDefault(metatype, Map.of());
			old.keySet()
				.stream()
				.filter((property) -> !current.containsKey(property))
				.forEach((property) -> lost.add(new SolutionImpact.Lost(metatype, property, null)));
		});
		return lost;
	}

	/** The metatypes that have the property in effect today, with the enumeration value as lost. */
	List<SolutionImpact.Lost> lostEnumValue(UUID propertyDefId, String value) {
		List<SolutionImpact.Lost> lost = new ArrayList<>();
		snapshot().mappings().forEach((metatype, mappings) -> {
			if (mappings.containsKey(propertyDefId)) {
				lost.add(new SolutionImpact.Lost(metatype, propertyDefId, value));
			}
		});
		return lost;
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
				else if (!"mandatory".equals(necessity) && "mandatory".equals(old.get(property))) {
					// marks follow the necessity in effect (us-137-mapping-effective-date D4)
					this.values.clearPropertyMarks(property, List.of(metatype));
				}
			});
		}
		return new Effects(removed, marked);
	}

	/** Marked and cleared entities when the marks of a property were brought in line with the necessity in effect. */
	record Synced(int marked, int cleared) {
	}

	/**
	 * Brings the marks of the property on the metatype and its subtypes in line with the necessity
	 * in effect today: entities without a value are marked where it is mandatory, marks are
	 * cleared elsewhere (an entry taking effect, us-137-mapping-effective-date D3). Idempotent.
	 */
	Synced syncMarks(UUID propertyDefId, UUID metatypeId) {
		Map<UUID, Map<UUID, String>> effective = snapshot().mappings();
		Instant now = now();
		int marked = 0;
		int cleared = 0;
		for (UUID metatype : subtree(metatypeId, bases())) {
			if ("mandatory".equals(effective.getOrDefault(metatype, Map.of()).get(propertyDefId))) {
				marked += this.values.mark(propertyDefId, List.of(metatype), now);
			}
			else {
				cleared += this.values.clearPropertyMarks(propertyDefId, List.of(metatype));
			}
		}
		return new Synced(marked, cleared);
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

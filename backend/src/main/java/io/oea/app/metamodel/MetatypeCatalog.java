package io.oea.app.metamodel;

import io.oea.core.metamodel.ArchitectureLayerStore;
import io.oea.core.metamodel.ConnectionConstraintStore;
import io.oea.core.metamodel.MetamodelConfigurationStore;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.core.metamodel.PropertyDefinitionStore;
import io.oea.core.metamodel.PropertyMappingStore;
import io.oea.core.solution.SnapshotStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The metatypes of the instance configuration with their properties (design D8, UC-04 step 2),
 * their base types, inherited mappings and re-import settings (us-032-metatype-editing D4, D8).
 */
@Service
public class MetatypeCatalog {

	private final MetatypeStore metatypeStore;

	private final MetamodelConfigurationStore configurationStore;

	private final ConnectionConstraintStore constraintStore;

	private final PropertyMappingStore mappingStore;

	private final PropertyDefinitionStore definitionStore;

	private final ArchitectureLayerStore layers;

	private final Clock clock;

	private final PropertyVisibility visibility;

	private final SnapshotStore snapshots;

	public MetatypeCatalog(MetatypeStore metatypeStore, MetamodelConfigurationStore configurationStore,
			ConnectionConstraintStore constraintStore, PropertyMappingStore mappingStore,
			PropertyDefinitionStore definitionStore, ArchitectureLayerStore layers, Clock clock,
			PropertyVisibility visibility, SnapshotStore snapshots) {
		this.snapshots = snapshots;
		this.metatypeStore = metatypeStore;
		this.configurationStore = configurationStore;
		this.constraintStore = constraintStore;
		this.mappingStore = mappingStore;
		this.definitionStore = definitionStore;
		this.layers = layers;
		this.clock = clock;
		this.visibility = visibility;
	}

	public record EnumValue(UUID id, String value, int sortOrder) {
	}

	public record Category(UUID id, String name, String description, int sortOrder, String scope, int version) {
	}

	public record Property(UUID id, String name, String dataType, Category category, Integer maxLength,
			BigDecimal minValue, BigDecimal maxValue, String pattern, String scope,
			String sourcePackageId, String sourcePackageVersion, List<EnumValue> enumValues, String sqlColumnName,
			boolean retired, int version, PropertyVisibility.Visibility visibility) {
	}

	/**
	 * A mapping entry (us-137-mapping-effective-date D1, D5).
	 *
	 * @param scope {@code organization} for an entry of an administrator, else the package scope
	 * @param state {@code past}, {@code active} or {@code future} relative to today
	 * @param inheritedFrom the base type that owns the mapping, {@code null} for an own mapping
	 */
	public record Mapping(UUID id, Property property, String necessity, LocalDate validFrom, int sortOrder,
			String scope, String state, MetatypeRef inheritedFrom, boolean snapshot, List<String> snapshotEnumValues) {

		public Mapping(UUID id, Property property, String necessity, LocalDate validFrom, int sortOrder, String scope,
				String state, MetatypeRef inheritedFrom) {
			this(id, property, necessity, validFrom, sortOrder, scope, state, inheritedFrom, false, List.of());
		}

		Mapping inheritedFrom(MetatypeRef owner) {
			return new Mapping(this.id, this.property, this.necessity, this.validFrom, this.sortOrder, this.scope,
					this.state, owner, this.snapshot, this.snapshotEnumValues);
		}

		Mapping withSnapshotEnumValue(String value) {
			List<String> values = new ArrayList<>(this.snapshotEnumValues);
			values.add(value);
			return new Mapping(this.id, this.property, this.necessity, this.validFrom, this.sortOrder, this.scope,
					this.state, this.inheritedFrom, this.snapshot, List.copyOf(values));
		}

	}

	public record MetatypeRef(UUID id, String name, String abbreviation) {
	}

	public record Metatype(UUID id, String name, String abbreviation, boolean connection, int sortOrder, String scope,
			String sourcePackageId, String sourcePackageVersion, UUID baseMetatypeId, String reimportPolicy,
			String effectiveReimportPolicy, UUID architectureLayerId, String architectureLayerName, List<Mapping> mappings,
			int version,
			List<Constraint> constraints, UUID configId, boolean showParallelChanges, boolean composition) {
	}

	/** An allowed source or target of a connection metatype (us-036-connection-metatypes D2). */
	public record Constraint(String direction, MetatypeRef allowed) {
	}

	private record Row(UUID id, String name, String abbreviation, boolean connection, int sortOrder, String scope,
			String sourcePackageId, String sourcePackageVersion, UUID baseMetatypeId, String reimportPolicy,
			int version, UUID configId, boolean showParallelChanges, boolean composition) {
	}

	/** The metatypes of the instance configuration. */
	@Transactional(readOnly = true)
	public List<Metatype> list() {
		return query(null, false, PackageImporter.INSTANCE_CONFIGURATION);
	}

	/**
	 * The effective metamodel of a configuration: for an extension the instance metatypes and its
	 * own (design D4 of us-037-solution-extensions-and-lock-mode).
	 *
	 * @throws NoSuchElementException if the configuration does not exist (404)
	 */
	@Transactional(readOnly = true)
	public List<Metatype> list(UUID configId) {
		if (!this.configurationStore.exists(configId)) {
			throw new NoSuchElementException("no metamodel configuration " + configId);
		}
		List<Metatype> metatypes = query(null, false, configId);
		UUID solution = this.snapshots.solutionOfConfiguration(configId);
		return (solution != null) ? withSnapshots(metatypes, this.snapshots.of(solution)) : metatypes;
	}

	/**
	 * The metatypes with the snapshots of the solution of an extension (design D10 of
	 * us-153-open-issues, US-154 AC3): a kept property is an optional mapping marked as snapshot, a
	 * kept enumeration value is listed with its mapping.
	 */
	private List<Metatype> withSnapshots(List<Metatype> metatypes, List<SnapshotStore.SnapshotRow> kept) {
		if (kept.isEmpty()) {
			return metatypes;
		}
		Map<UUID, Property> properties = propertyMap();
		List<Metatype> result = new ArrayList<>();
		for (Metatype metatype : metatypes) {
			List<Mapping> mappings = new ArrayList<>(metatype.mappings());
			for (SnapshotStore.SnapshotRow snapshot : kept) {
				if (!snapshot.metatypeId().equals(metatype.id())) {
					continue;
				}
				int index = -1;
				for (int i = 0; i < mappings.size(); i++) {
					if (mappings.get(i).property() != null
							&& mappings.get(i).property().id().equals(snapshot.propertyDefId())) {
						index = i;
					}
				}
				if (index < 0) {
					Property property = properties.get(snapshot.propertyDefId());
					if (property == null) {
						continue;
					}
					mappings.add(new Mapping(snapshot.id(), property, "optional",
							LocalDate.ofInstant(snapshot.createdAt(), java.time.ZoneOffset.UTC), Integer.MAX_VALUE,
							"organization", "active", null, true, List.of()));
					index = mappings.size() - 1;
				}
				if (snapshot.enumValue() != null) {
					mappings.set(index, mappings.get(index).withSnapshotEnumValue(snapshot.enumValue()));
				}
			}
			result.add(new Metatype(metatype.id(), metatype.name(), metatype.abbreviation(), metatype.connection(),
					metatype.sortOrder(), metatype.scope(), metatype.sourcePackageId(), metatype.sourcePackageVersion(),
					metatype.baseMetatypeId(), metatype.reimportPolicy(), metatype.effectiveReimportPolicy(),
					metatype.architectureLayerId(), metatype.architectureLayerName(), List.copyOf(mappings), metatype.version(),
					metatype.constraints(),
					metatype.configId(), metatype.showParallelChanges(), metatype.composition()));
		}
		return result;
	}

	/** The metatype with the mappings in effect today. */
	@Transactional(readOnly = true)
	public Metatype get(UUID id) {
		return get(id, false);
	}

	/** The metatype; with history, every mapping entry ordered by property and date. */
	@Transactional(readOnly = true)
	public Metatype get(UUID id, boolean includeHistory) {
		UUID config = this.metatypeStore.configOf(id)
			.orElseThrow(() -> new NoSuchElementException("no metatype " + id));
		return query(id, includeHistory, config).stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no metatype " + id));
	}

	/** The property definitions that are not retired, by name. */
	@Transactional(readOnly = true)
	public List<Property> properties() {
		return propertyMap().values()
			.stream()
			.filter((property) -> !property.retired())
			.sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
			.toList();
	}

	@Transactional(readOnly = true)
	public Property property(UUID id) {
		Property property = propertyMap().get(id);
		if (property == null || property.retired()) {
			throw new NoSuchElementException("no property definition " + id);
		}
		return property;
	}

	@Transactional(readOnly = true)
	public List<Category> categories() {
		return List.copyOf(categoryMap().values());
	}

	/** The global re-import setting of the instance configuration. */
	String globalReimportPolicy() {
		return this.configurationStore.reimportPolicy(PackageImporter.INSTANCE_CONFIGURATION);
	}

	/** The re-import setting that applies to each metatype (design D8). */
	Map<UUID, String> effectiveReimportPolicies() {
		Map<UUID, String> own = new HashMap<>();
		Map<UUID, UUID> bases = new HashMap<>();
		for (MetatypeStore.PolicyRow row : this.metatypeStore.policies()) {
			own.put(row.id(), row.policy());
			if (row.baseMetatypeId() != null) {
				bases.put(row.id(), row.baseMetatypeId());
			}
		}
		String global = globalReimportPolicy();
		Map<UUID, String> effective = new HashMap<>();
		own.keySet().forEach((id) -> effective.put(id, MetamodelEffects.chain(id, bases)
			.stream()
			.map(own::get)
			.filter((policy) -> !"inherit".equals(policy))
			.findFirst()
			.orElse(global)));
		return effective;
	}

	/** Metatypes of the instance and of {@code config}; base types cross the boundary (design D4). */
	private List<Metatype> query(UUID id, boolean includeHistory, UUID config) {
		Map<UUID, Map<UUID, List<Mapping>>> own = entries(includeHistory);
		Map<UUID, Row> rows = new LinkedHashMap<>();
		for (MetatypeStore.MetatypeRow row : this.metatypeStore.inConfigurations(PackageImporter.INSTANCE_CONFIGURATION,
				config)) {
			rows.put(row.id(), new Row(row.id(), row.name(), row.abbreviation(), row.connection(), row.sortOrder(),
					row.scope(), row.sourcePackageId(), row.sourcePackageVersion(), row.baseMetatypeId(),
					row.reimportPolicy(), row.version(), row.configId(), row.showParallelChanges(),
					row.composition()));
		}
		Map<UUID, UUID> bases = new HashMap<>();
		rows.values().stream().filter((row) -> row.baseMetatypeId() != null)
			.forEach((row) -> bases.put(row.id(), row.baseMetatypeId()));
		Map<UUID, String> policies = effectiveReimportPolicies();
		Map<UUID, List<Constraint>> constraints = new HashMap<>();
		for (ConnectionConstraintStore.ConstraintRow constraint : this.constraintStore.all()) {
			Row allowed = rows.get(constraint.allowedMetatypeId());
			if (allowed != null) {
				constraints.computeIfAbsent(constraint.metatypeId(), (k) -> new ArrayList<>())
					.add(new Constraint(constraint.direction(),
							new MetatypeRef(allowed.id(), allowed.name(), allowed.abbreviation())));
			}
		}
		Map<UUID, UUID> layerIds = this.layers.layerByMetatype(rows.keySet());
		Map<UUID, String> layerNames = new HashMap<>();
		this.layers.list().forEach((layer) -> layerNames.put(layer.id(), layer.name()));
		List<Metatype> metatypes = new ArrayList<>();
		for (Row row : rows.values()) {
			if (id != null && !id.equals(row.id())) {
				continue;
			}
			List<Mapping> mappings = new ArrayList<>();
			for (UUID type : MetamodelEffects.chain(row.id(), bases).reversed()) {
				Row owner = rows.get(type);
				MetatypeRef inheritedFrom = type.equals(row.id()) ? null
						: new MetatypeRef(owner.id(), owner.name(), owner.abbreviation());
				own.getOrDefault(type, Map.of())
					.values()
					.forEach((entries) -> entries.forEach((mapping) -> mappings.add(mapping.inheritedFrom(inheritedFrom))));
			}
			metatypes.add(new Metatype(row.id(), row.name(), row.abbreviation(), row.connection(), row.sortOrder(),
					row.scope(), row.sourcePackageId(), row.sourcePackageVersion(), row.baseMetatypeId(),
					row.reimportPolicy(), policies.get(row.id()), layerIds.get(row.id()), layerNames.get(layerIds.get(row.id())),
					List.copyOf(mappings),
					row.version(), List.copyOf(constraints.getOrDefault(row.id(), List.of())), row.configId(),
					row.showParallelChanges(), row.composition()));
		}
		return metatypes;
	}

	/**
	 * The mapping entries per metatype and property, in display order; without history only the
	 * entry in effect today (latest {@code valid_from} not after today, ADR-022).
	 */
	private Map<UUID, Map<UUID, List<Mapping>>> entries(boolean includeHistory) {
		Map<UUID, Property> properties = propertyMap();
		LocalDate today = LocalDate.now(this.clock);
		List<PropertyMappingStore.EntryRow> rows = this.mappingStore.entries();
		Map<List<UUID>, LocalDate> active = new HashMap<>();
		for (PropertyMappingStore.EntryRow row : rows) {
			if (!row.validFrom().isAfter(today)) {
				active.merge(List.of(row.metatypeId(), row.propertyDefId()), row.validFrom(),
						(a, b) -> a.isAfter(b) ? a : b);
			}
		}
		Map<UUID, Map<UUID, List<Mapping>>> entries = new LinkedHashMap<>();
		for (PropertyMappingStore.EntryRow row : rows) {
			LocalDate activeFrom = active.get(List.of(row.metatypeId(), row.propertyDefId()));
			String state = row.validFrom().isAfter(today) ? "future"
					: row.validFrom().equals(activeFrom) ? "active" : "past";
			if (includeHistory || "active".equals(state)) {
				entries.computeIfAbsent(row.metatypeId(), (k) -> new LinkedHashMap<>())
					.computeIfAbsent(row.propertyDefId(), (k) -> new ArrayList<>())
					.add(new Mapping(row.id(), properties.get(row.propertyDefId()), row.necessity(), row.validFrom(),
							row.sortOrder(), row.scope(), state, null));
			}
		}
		return entries;
	}

	private Map<UUID, Category> categoryMap() {
		Map<UUID, Category> categories = new LinkedHashMap<>();
		for (PropertyDefinitionStore.CategoryRow row : this.definitionStore.categories()) {
			categories.put(row.id(), new Category(row.id(), row.name(), row.description(), row.sortOrder(), row.scope(),
					row.version()));
		}
		return categories;
	}

	private Map<UUID, Property> propertyMap() {
		Map<UUID, Category> categories = categoryMap();
		Map<UUID, List<EnumValue>> values = new LinkedHashMap<>();
		for (PropertyDefinitionStore.EnumRow row : this.definitionStore.enumValues()) {
			values.computeIfAbsent(row.propertyDefId(), (k) -> new ArrayList<>())
				.add(new EnumValue(row.id(), row.value(), row.sortOrder()));
		}
		Map<UUID, Property> properties = new LinkedHashMap<>();
		Map<UUID, PropertyVisibility.Visibility> visibilities = this.visibility.all();
		for (PropertyDefinitionStore.PropertyRow row : this.definitionStore.properties()) {
			UUID id = row.id();
			properties.put(id, new Property(id, row.name(), row.dataType(), categories.get(row.categoryId()),
					row.maxLength(), row.minValue(), row.maxValue(), row.pattern(), row.scope(),
					row.sourcePackageId(), row.sourcePackageVersion(), List.copyOf(values.getOrDefault(id, List.of())),
					row.sqlColumnName(), row.retired(), row.version(),
					visibilities.getOrDefault(id, PropertyVisibility.Visibility.DEFAULT)));
		}
		return properties;
	}

}

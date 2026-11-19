package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.metamodel.ArchitectureLayerStore;
import io.oea.core.metamodel.BuiltInMetatypes;
import io.oea.core.metamodel.CompositionStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, changing and deleting the organization's own metatypes and their base types (design
 * D3, D4 of us-032-metatype-editing; US-032, REQ-032, ADR-002). Built-in and imported metatypes
 * are read-only.
 */
@Service
public class MetatypeEditing {

	static final int NAME_MAX_LENGTH = 100;

	static final int ABBREVIATION_MAX_LENGTH = 10;

	private final JdbcTemplate jdbc;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	private final MetamodelVersions versions;

	private final MetamodelConfigurations configurations;

	private final SolutionImpact impact;

	private CompositionStore composition;

	private ArchitectureLayerStore layers;

	public MetatypeEditing(JdbcTemplate jdbc, MetamodelEffects effects, MetamodelAudit audit,
			MetamodelVersions versions, MetamodelConfigurations configurations, SolutionImpact impact) {
		this.impact = impact;
		this.jdbc = jdbc;
		this.effects = effects;
		this.audit = audit;
		this.versions = versions;
		this.configurations = configurations;
	}

	/** The store of the composition mark; set by Spring, so that the constructor stays as the frozen architecture rule knows it. */
	@Autowired
	void setComposition(CompositionStore composition) {
		this.composition = composition;
	}

	/** The store of the architecture layers; set by Spring, so that the constructor stays as the frozen architecture rule knows it. */
	@Autowired
	void setLayers(ArchitectureLayerStore layers) {
		this.layers = layers;
	}

	/**
	 * The writable fields of a metatype.
	 *
	 * @param allowedSources metatypes allowed as source of a connection metatype; empty allows every
	 *     metatype (us-036-connection-metatypes D2)
	 * @param allowedTargets the same for the target
	 */
	public record Input(String name, String abbreviation, boolean connection, UUID architectureLayerId,
			int sortOrder, UUID configId, UUID baseMetatypeId, List<UUID> allowedSources, List<UUID> allowedTargets,
			boolean composition) {

		public Input {
			allowedSources = (allowedSources != null) ? List.copyOf(new LinkedHashSet<>(allowedSources)) : List.of();
			allowedTargets = (allowedTargets != null) ? List.copyOf(new LinkedHashSet<>(allowedTargets)) : List.of();
		}

		public Input(String name, String abbreviation, boolean connection, UUID architectureLayerId, int sortOrder,
				UUID configId, UUID baseMetatypeId, List<UUID> allowedSources, List<UUID> allowedTargets) {
			this(name, abbreviation, connection, architectureLayerId, sortOrder, configId, baseMetatypeId,
					allowedSources, allowedTargets, false);
		}

	}

	private record Stored(String name, String abbreviation, boolean connection, UUID architectureLayerId,
			int sortOrder, String scope, UUID baseMetatypeId, UUID configId) {

		Stored withLayer(UUID layerId) {
			return new Stored(this.name, this.abbreviation, this.connection, layerId, this.sortOrder, this.scope,
					this.baseMetatypeId, this.configId);
		}

	}

	@Transactional
	public UUID create(OeaPrincipal principal, Input input) {
		this.effects.lock();
		UUID config = input.configId();
		if (config == null || (!PackageImporter.INSTANCE_CONFIGURATION.equals(config)
				&& !this.configurations.isExtension(config))) {
			throw MetamodelChangeException.invalid("configId",
					"The configuration is neither the instance nor an extension.");
		}
		this.configurations.requireWritable(principal, config);
		String name = validate(input, null, config);
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO metatypes (id, config_id, name, abbreviation, is_connection, architecture_layer_id, sort_order,
				    scope, created_by, base_metatype_id, is_composition) VALUES (?, ?, ?, ?, ?, ?, ?, 'organization', ?, ?, ?)""",
				id, config, name, abbreviation(input), input.connection(),
				input.architectureLayerId(), input.sortOrder(), principal.personId(), input.baseMetatypeId(),
				input.composition());
		ConnectionConstraints.replace(this.jdbc, id, input.allowedSources(), input.allowedTargets());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", name);
		if (!PackageImporter.INSTANCE_CONFIGURATION.equals(config)) {
			details.put("configId", config.toString());
		}
		if (input.baseMetatypeId() != null) {
			details.put("baseMetatypeId", input.baseMetatypeId().toString());
		}
		if (input.architectureLayerId() != null) {
			details.put("architectureLayerId", input.architectureLayerId().toString());
		}
		if (input.composition()) {
			details.put("isComposition", true);
		}
		if (input.connection()) {
			details.put("isConnection", true);
			details.put("allowedSourceMetatypeIds", List.of(MetamodelEffects.ids(input.allowedSources())));
			details.put("allowedTargetMetatypeIds", List.of(MetamodelEffects.ids(input.allowedTargets())));
		}
		this.audit.changed("metamodel.metatype-created", principal, "metatype", id, details);
		return id;
	}

	/** @param expectedVersion the version of {@code If-Match}, or {@code null} */
	public void update(OeaPrincipal principal, UUID id, Input input, Long expectedVersion) {
		update(principal, id, input, expectedVersion, SolutionImpact.Decisions.of(Map.of()));
	}

	/**
	 * Changes the metatype with the decisions for the active solutions a base type change affects
	 * (US-154).
	 *
	 * @throws SolutionDecisionRequiredException when an affected solution has no decision (409)
	 */
	@Transactional
	public void update(OeaPrincipal principal, UUID id, Input input, Long expectedVersion,
			SolutionImpact.Decisions decisions) {
		this.effects.lock();
		this.configurations.requireWritableMetatype(principal, id);
		this.versions.check(MetamodelVersions.Element.METATYPE, id, expectedVersion);
		Stored stored = stored(id);
		if (!"organization".equals(stored.scope())) {
			throw MetamodelChangeException.readOnly("metatype");
		}
		if (!stored.configId().equals(input.configId())) {
			throw MetamodelChangeException.invalid("configId", "A metatype keeps its configuration.");
		}
		String name = validate(input, id, stored.configId());
		if (input.connection() != stored.connection()) {
			throw MetamodelChangeException.invalid("isConnection", "Whether a metatype is a connection cannot change.");
		}
		MetamodelEffects.Snapshot before = this.effects.snapshot();
		Map<String, List<UUID>> constraintsBefore = ConnectionConstraints.of(this.jdbc, id);
		boolean wasComposition = this.composition.marked().filter(id::equals).isPresent();
		this.jdbc.update("""
				UPDATE metatypes SET name = ?, abbreviation = ?, architecture_layer_id = ?, sort_order = ?,
				       base_metatype_id = ?, is_composition = ? WHERE id = ?""", name, abbreviation(input),
				input.architectureLayerId(), input.sortOrder(), input.baseMetatypeId(), input.composition(), id);
		ConnectionConstraints.replace(this.jdbc, id, input.allowedSources(), input.allowedTargets());
		this.versions.increment(MetamodelVersions.Element.METATYPE, id);
		List<String> changed = new ArrayList<>();
		compare(changed, "allowedSourceMetatypeIds", Set.copyOf(constraintsBefore.get(ConnectionConstraints.SOURCE)),
				Set.copyOf(input.allowedSources()));
		compare(changed, "allowedTargetMetatypeIds", Set.copyOf(constraintsBefore.get(ConnectionConstraints.TARGET)),
				Set.copyOf(input.allowedTargets()));
		compare(changed, "name", stored.name(), name);
		compare(changed, "abbreviation", stored.abbreviation(), abbreviation(input));
		compare(changed, "architectureLayerId", stored.architectureLayerId(), input.architectureLayerId());
		compare(changed, "sortOrder", stored.sortOrder(), input.sortOrder());
		compare(changed, "baseMetatypeId", stored.baseMetatypeId(), input.baseMetatypeId());
		compare(changed, "isComposition", wasComposition, input.composition());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("changedFields", changed);
		if (changed.contains("baseMetatypeId")) {
			// removed and added inherited properties take effect as for a mapping (design D4)
			// only a change of the instance metamodel asks the solutions; an extension is their own (US-154)
			boolean instance = PackageImporter.INSTANCE_CONFIGURATION.equals(stored.configId());
			this.impact.apply(principal, this.effects.lost(before), instance ? decisions : null, details,
					this.effects.now());
			MetamodelEffects.Effects effects = this.effects.apply(before, principal);
			details.put("entitiesWithRemovedValues", effects.removed().values().stream().mapToInt(Integer::intValue).sum());
			details.put("entitiesMarked", effects.marked().values().stream().mapToInt(Integer::intValue).sum());
		}
		this.audit.changed("metamodel.metatype-updated", principal, "metatype", id, details);
	}

	@Transactional
	public void delete(OeaPrincipal principal, UUID id, Long expectedVersion) {
		this.effects.lock();
		this.configurations.requireWritableMetatype(principal, id);
		this.versions.check(MetamodelVersions.Element.METATYPE, id, expectedVersion);
		Stored stored = stored(id);
		if (!"organization".equals(stored.scope())) {
			throw MetamodelChangeException.readOnly("metatype");
		}
		long entities = count("SELECT count(*) FROM entities WHERE metatype_id = ?", id);
		long catalogs = count("SELECT count(*) FROM catalogs WHERE primary_metatype_id = ?", id);
		long subtypes = count("SELECT count(*) FROM metatypes WHERE base_metatype_id = ?", id);
		// a deletion must not widen the constraints of a connection metatype to "any" (us-036 D2)
		long endpoints = count("""
				SELECT count(*) FROM metatype_connection_constraints WHERE allowed_metatype_id = ? AND metatype_id <> ?""",
				id, id);
		if (entities > 0 || catalogs > 0 || subtypes > 0 || endpoints > 0) {
			throw MetamodelChangeException.inUse("The metatype has " + entities + " entities, " + catalogs
					+ " catalogs, " + subtypes + " subtypes and " + endpoints
					+ " uses as endpoint of connection metatypes.", entities);
		}
		// the scoping type of a property definition (design D4 of us-124-property-visibility)
		long scoping = count("SELECT count(*) FROM property_definitions WHERE scoping_connection_type_id = ?", id);
		// the person metatype of connection-scoped visibility (design D2 of us-127-connection-scoped-visibility)
		if (count("SELECT count(*) FROM visibility_settings WHERE person_metatype_id = ?", id) > 0) {
			throw MetamodelChangeException.inUse("The metatype is the person metatype of connection-scoped visibility.",
					null);
		}
		if (scoping > 0) {
			throw MetamodelChangeException.inUse(
					"The metatype is the scoping type of " + scoping + " property definitions.", scoping);
		}
		this.jdbc.update("DELETE FROM metatype_connection_constraints WHERE metatype_id = ?", id);
		this.jdbc.update("DELETE FROM metatype_property_mappings WHERE metatype_id = ?", id);
		this.jdbc.update("DELETE FROM metatypes WHERE id = ?", id);
		this.effects.removeOrphanLabels();
		this.audit.changed("metamodel.metatype-deleted", principal, "metatype", id, Map.of("name", stored.name()));
	}

	/**
	 * Checks name, abbreviation, base type and allowed endpoints in the configuration
	 * {@code config}; returns the stripped name. A name in an extension must not exist in the
	 * instance or the same extension, one in the instance in no configuration; base type and
	 * endpoints come from the instance or the same extension (design D3 of
	 * us-037-solution-extensions-and-lock-mode).
	 */
	private String validate(Input input, UUID id, UUID config) {
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("name", "The name must have 1 to 100 characters.");
		}
		String abbreviation = abbreviation(input);
		if (abbreviation != null && abbreviation.length() > ABBREVIATION_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("abbreviation", "The abbreviation has more than 10 characters.");
		}
		// the layers of the instance, also for the metatypes of an extension (design D3 of s17-architecture-layers)
		if (input.architectureLayerId() != null && !this.layers.exists(input.architectureLayerId())) {
			throw MetamodelChangeException.invalid("architectureLayerId", "The architecture layer does not exist.");
		}
		boolean instance = PackageImporter.INSTANCE_CONFIGURATION.equals(config);
		if (count("""
				SELECT count(*) FROM metatypes WHERE (? OR config_id IN (?, ?)) AND lower(name) = lower(?)
				    AND id IS DISTINCT FROM ?::uuid""", instance, PackageImporter.INSTANCE_CONFIGURATION, config, name,
				id) > 0) {
			throw MetamodelChangeException.nameTaken("name");
		}
		UUID base = input.baseMetatypeId();
		if (input.composition()) {
			checkComposition(input, id, instance);
		}
		if (!input.connection() && (!input.allowedSources().isEmpty() || !input.allowedTargets().isEmpty())) {
			throw MetamodelChangeException.invalid(List.of("allowedSourceMetatypeIds", "allowedTargetMetatypeIds"),
					"Only connection metatypes have allowed sources and targets.");
		}
		checkExisting("allowedSourceMetatypeIds", input.allowedSources(), id, config);
		checkExisting("allowedTargetMetatypeIds", input.allowedTargets(), id, config);
		if (BuiltInMetatypes.STRUCTURING_AID.equals(base)) {
			throw MetamodelChangeException.invalid("baseMetatypeId", "The structuring aid cannot be a base type.");
		}
		if (base != null) {
			// base types have the same kind (us-036-connection-metatypes D2)
			if (count("SELECT count(*) FROM metatypes WHERE id = ? AND is_connection = ? AND config_id IN (?, ?)", base,
					input.connection(), PackageImporter.INSTANCE_CONFIGURATION, config) == 0) {
				throw MetamodelChangeException.invalid("baseMetatypeId", "The base type does not exist, is not of the "
						+ "same kind (connection or element) or belongs to another configuration.");
			}
			Map<UUID, UUID> bases = this.effects.bases();
			if (id != null && MetamodelEffects.chain(base, bases).contains(id)) {
				throw MetamodelChangeException.invalid("baseMetatypeId", "The base type would form a cycle.");
			}
			if (id != null) {
				checkUniqueInChain(id, base, bases);
			}
		}
		return name;
	}

	/**
	 * The composition mark (design D4 of us-135-component-hierarchy): a connection metatype of the
	 * instance configuration, and the only one marked.
	 */
	private void checkComposition(Input input, UUID id, boolean instance) {
		if (!input.connection()) {
			throw MetamodelChangeException.invalid("isComposition", "Only a connection metatype can be the composition.");
		}
		if (!instance) {
			throw MetamodelChangeException.invalid("isComposition",
					"The composition is a metatype of the instance metamodel, not of a solution extension.");
		}
		UUID marked = this.composition.marked().orElse(null);
		if (marked != null && !marked.equals(id)) {
			throw MetamodelChangeException.invalid("isComposition", "Another connection metatype is the composition.");
		}
	}

	/**
	 * Each listed metatype exists in the instance or the configuration; the metatype itself may be
	 * listed once it exists.
	 */
	private void checkExisting(String field, List<UUID> metatypes, UUID id, UUID config) {
		for (UUID metatype : metatypes) {
			if (BuiltInMetatypes.STRUCTURING_AID.equals(metatype)) {
				throw MetamodelChangeException.invalid(field, "A structuring aid has no connections.");
			}
			if (!metatype.equals(id) && count("SELECT count(*) FROM metatypes WHERE id = ? AND config_id IN (?, ?)",
					metatype, PackageImporter.INSTANCE_CONFIGURATION, config) == 0) {
				throw MetamodelChangeException.invalid(field,
						"The metatype " + metatype + " does not exist in this configuration or the instance.");
			}
		}
	}

	/** A property may be mapped once in the chain of the new base types and the subtree of the metatype. */
	private void checkUniqueInChain(UUID id, UUID base, Map<UUID, UUID> bases) {
		List<UUID> above = MetamodelEffects.chain(base, bases);
		Set<UUID> below = MetamodelEffects.subtree(id, bases);
		Integer duplicates = this.jdbc.queryForObject("""
				SELECT count(*) FROM metatype_property_mappings a JOIN metatype_property_mappings b
				    ON a.property_def_id = b.property_def_id
				WHERE a.metatype_id = ANY (?::uuid[]) AND b.metatype_id = ANY (?::uuid[])""", Integer.class,
				MetamodelEffects.ids(above), MetamodelEffects.ids(below));
		if (duplicates != null && duplicates > 0) {
			throw MetamodelChangeException.conflict("A property would be mapped twice in the base type chain.");
		}
	}

	private Stored stored(UUID id) {
		return this.jdbc.query("""
				SELECT name, abbreviation, is_connection, sort_order, scope, base_metatype_id, config_id
				FROM metatypes WHERE id = ?""", (rs, n) -> new Stored(rs.getString(1), rs.getString(2), rs.getBoolean(3),
				null, rs.getInt(4), rs.getString(5), rs.getObject(6, UUID.class), rs.getObject(7, UUID.class)),
				id)
			.stream()
			.findFirst()
			.map((stored) -> stored.withLayer(this.layers.layerOf(id).orElse(null)))
			.orElseThrow(() -> new NoSuchElementException("no metatype " + id));
	}

	private long count(String sql, Object... arguments) {
		Long count = this.jdbc.queryForObject(sql, Long.class, arguments);
		return (count != null) ? count : 0;
	}

	private static String abbreviation(Input input) {
		return (input.abbreviation() != null && !input.abbreviation().isBlank()) ? input.abbreviation().strip() : null;
	}

	private static void compare(List<String> changed, String field, Object before, Object after) {
		if (!Objects.equals(before, after)) {
			changed.add(field);
		}
	}

}

package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.metamodel.ArchitectureLayerStore;
import io.oea.core.metamodel.BuiltInMetatypes;
import io.oea.core.metamodel.CompositionStore;
import io.oea.core.metamodel.ViewpointStore;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic import of a metamodel package into the instance configuration (design D1, D2, D5;
 * REQ-114, ADR-002): new content gets {@code scope=imported} and the package as origin,
 * content of the same package is updated, content of others is never overwritten. Organization
 * mappings of re-imported metatypes are kept or removed by the re-import setting
 * (us-032-metatype-editing D8).
 */
@Service
public class PackageImporter {

	/** The instance configuration created by the migration (design D4). */
	public static final UUID INSTANCE_CONFIGURATION = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private final JdbcTemplate jdbc;

	private final AuditWriter audit;

	private final Clock clock;

	private final MetamodelEffects effects;

	private final PropertyMappings mappings;

	private final ReimportPolicies policies;

	private final MetamodelLabels labels;

	private final MetamodelVersions versions;

	private final ViewpointStore viewpointStore;

	private final NotationCatalog catalog;

	private CompositionStore composition;

	private ArchitectureLayerStore layerStore;

	public PackageImporter(JdbcTemplate jdbc, AuditWriter audit, Clock clock, MetamodelEffects effects,
			PropertyMappings mappings, ReimportPolicies policies, MetamodelLabels labels, MetamodelVersions versions,
			ViewpointStore viewpointStore, NotationCatalog catalog) {
		this.viewpointStore = viewpointStore;
		this.catalog = catalog;
		this.labels = labels;
		this.versions = versions;
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
		this.effects = effects;
		this.mappings = mappings;
		this.policies = policies;
	}

	/** The store of the composition mark; set by Spring, so that the constructor stays as the frozen architecture rule knows it. */
	@Autowired
	void setComposition(CompositionStore composition) {
		this.composition = composition;
	}

	/** The store of the architecture layers; set by Spring, so that the constructor stays as the frozen architecture rule knows it. */
	@Autowired
	void setLayerStore(ArchitectureLayerStore layerStore) {
		this.layerStore = layerStore;
	}

	/**
	 * Result of an import; counts refer to metatypes, {@code labels} to the stored package labels, the
	 * viewpoint counts to the system-defined viewpoints of the package and the layer counts to its
	 * architecture layers ({@code layerAssignmentsCleared}: metatypes that lost a deleted layer).
	 */
	public record Outcome(UUID batchId, int created, int updated, int labels, int viewpointsCreated,
			int viewpointsUpdated, int viewpointsDeleted, int layersCreated, int layersUpdated, int layersDeleted,
			int layerAssignmentsCleared) {
	}

	/** Existing content with an identifier or a name of the package. */
	private record Owner(String scope, String packageId) {
	}

	/**
	 * Imports the package in one transaction.
	 *
	 * @throws InvalidPackageException if the package violates a rule (nothing stored)
	 * @throws PackageConflictException if it collides with content of others (nothing stored)
	 */
	@Transactional
	public Outcome importPackage(OeaPrincipal principal, PackageDefinition definition) {
		try {
			PackageValidator.validate(definition, this.catalog);
		}
		catch (InvalidPackageException ex) {
			rejected(principal, definition, ex.getType());
			throw ex;
		}
		// one import at a time: the instance configuration is the lock
		this.jdbc.queryForObject("SELECT id FROM metamodel_configurations WHERE id = ? FOR UPDATE", UUID.class,
				INSTANCE_CONFIGURATION);
		List<PackageConflictException.Conflict> conflicts = conflicts(definition);
		if (!conflicts.isEmpty()) {
			rejected(principal, definition, "conflict");
			throw new PackageConflictException(conflicts);
		}
		try {
			checkBaseTypes(definition);
			checkConnections(definition);
			checkComposition(definition);
			checkLayerLimit(definition);
		}
		catch (InvalidPackageException ex) {
			rejected(principal, definition, ex.getType());
			throw ex;
		}
		Instant now = this.clock.instant();
		UUID batchId = UUID.randomUUID();
		MetamodelEffects.Snapshot before = this.effects.snapshot();
		LayerImport layerImport = importLayers(principal, definition, now);
		for (PackageDefinition.Category category : definition.categories()) {
			upsertCategory(definition, category);
		}
		Map<UUID, String> actions = new HashMap<>();
		// the mark of the package moves without meeting itself in the unique index
		UUID wasComposition = this.composition.marked().orElse(null);
		UUID willBe = definition.metatypes()
			.stream()
			.filter(PackageDefinition.Metatype::composition)
			.map(PackageDefinition.Metatype::id)
			.findFirst()
			.orElse(null);
		if (wasComposition != null && !wasComposition.equals(willBe)) {
			this.composition.unmarkPackage(definition.packageId());
		}
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			String action = upsertMetatype(definition, metatype);
			// a changed mark counts as an update of the metatype
			boolean marked = metatype.id().equals(wasComposition);
			actions.put(metatype.id(), ("unchanged".equals(action) && marked != metatype.composition()) ? "updated" : action);
		}
		for (PackageDefinition.Property property : definition.properties()) {
			upsertProperty(definition, property);
		}
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			actions.put(metatype.id(), setBaseType(metatype, actions.get(metatype.id())));
		}
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			actions.put(metatype.id(), replaceConstraints(metatype, actions.get(metatype.id())));
		}
		List<UUID> skipped = new ArrayList<>();
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			String action = replaceMappings(metatype, actions.get(metatype.id()), skipped);
			actions.put(metatype.id(), action);
		}
		uniqueInChains(principal, definition);
		int cleared = dropLayers(definition, layerImport);
		ViewpointImport viewpoints = importViewpoints(principal, definition, now);
		// organization mappings are kept or removed by the re-import setting (us-032-metatype-editing D8)
		Map<UUID, String> policies = this.policies.effective();
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			boolean removed = "remove".equals(policies.get(metatype.id()))
					&& this.mappings.removeForReimport(principal, metatype.id(), batchId) > 0;
			// a re-import counts as a change of the metatype (scr-030-metamodel-configuration D1)
			if (removed || "updated".equals(actions.get(metatype.id()))) {
				this.versions.increment(MetamodelVersions.Element.METATYPE, metatype.id());
			}
		}
		// package labels replace those of the previous import; organization labels stay (us-140-dynamic-labels D5)
		int labelCount = this.labels.replacePackageLabels(definition.packageId(), definition.labels());
		this.effects.removeOrphanLabels();
		MetamodelEffects.Effects effects = this.effects.apply(before, principal);
		int created = (int) actions.values().stream().filter("created"::equals).count();
		int updated = (int) actions.values().stream().filter("updated"::equals).count();
		this.jdbc.update("""
				UPDATE metamodel_configurations SET last_modified_at = ?, last_modified_by_person_id = ?,
				       last_modified_by_admin_id = ? WHERE id = ?""", Timestamp.from(now), principal.personId(),
				principal.systemAdminAccountId(), INSTANCE_CONFIGURATION);
		this.jdbc.update("""
				INSERT INTO metamodel_package_imports (package_id, package_version, publisher, batch_id, imported_at,
				    imported_by_person_id, imported_by_admin_id, metatypes_created, metatypes_updated)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", definition.packageId(), definition.version(),
				definition.publisher(), batchId, Timestamp.from(now), principal.personId(),
				principal.systemAdminAccountId(), created, updated);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("packageId", definition.packageId());
		details.put("version", definition.version());
		details.put("batchId", batchId.toString());
		details.put("metatypesCreated", created);
		details.put("metatypesUpdated", updated);
		details.put("labels", labelCount);
		details.put("entitiesWithRemovedValues", sum(effects.removed()));
		details.put("entitiesMarked", sum(effects.marked()));
		details.put("skippedPackageEntries", skipped.stream().map(UUID::toString).toList());
		details.put("viewpointsCreated", viewpoints.created());
		details.put("viewpointsUpdated", viewpoints.updated());
		details.put("viewpointsDeleted", viewpoints.deleted().stream().map(UUID::toString).toList());
		details.put("architectureLayersCreated", layerImport.created());
		details.put("architectureLayersUpdated", layerImport.updated());
		details.put("architectureLayersDeleted", layerImport.dropped().size());
		details.put("layerAssignmentsCleared", cleared);
		this.audit.append(new AuditEvent("metamodel.package-imported", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"metamodel-package", definition.packageId(), details));
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			String action = actions.get(metatype.id());
			if (!"unchanged".equals(action)) {
				this.audit.append(new AuditEvent("metamodel.metatype-imported", AuditEvent.Category.CHANGE,
						AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API,
						Sessions.actor(principal), "metatype", metatype.id().toString(),
						Map.of("batchId", batchId.toString(), "packageId", definition.packageId(), "action", action)));
			}
		}
		return new Outcome(batchId, created, updated, labelCount, viewpoints.created(), viewpoints.updated(),
				viewpoints.deleted().size(), layerImport.created(), layerImport.updated(), layerImport.dropped().size(),
				cleared);
	}

	/** What the import did to the layers of the package; {@code dropped} holds the IDs the package no longer has. */
	private record LayerImport(int created, int updated, List<UUID> dropped, int assigned) {
	}

	/**
	 * Installs the layers of the package as imported ones before the metatypes that reference them
	 * (design D3 of s17-architecture-layers). The names of the package's layers are released first,
	 * so that a rename or swap cannot collide; the layers the new version no longer has are removed
	 * after the metatypes by {@link #dropLayers}.
	 */
	private LayerImport importLayers(OeaPrincipal principal, PackageDefinition definition, Instant now) {
		Map<UUID, ArchitectureLayerStore.LayerRow> existing = new HashMap<>();
		this.layerStore.packageLayers(definition.packageId()).forEach((row) -> existing.put(row.id(), row));
		Set<UUID> wanted = new HashSet<>();
		definition.layers().forEach((layer) -> wanted.add(layer.id()));
		List<UUID> dropped = existing.keySet().stream().filter((id) -> !wanted.contains(id)).toList();
		// metatypes of any origin that have a layer the new version drops
		int assigned = this.layerStore.assignedCount(dropped);
		this.layerStore.releaseNames(definition.packageId());
		// new layers go behind the existing ones in the order of the package; existing ones keep their position
		int end = this.layerStore.count();
		int created = 0;
		int updated = 0;
		for (PackageDefinition.Layer layer : definition.layers()) {
			ArchitectureLayerStore.LayerRow row = existing.get(layer.id());
			String name = layer.name().strip();
			boolean changed = row != null && !row.name().equals(name);
			if (this.layerStore.upsertImported(layer.id(), name, end + layer.sortOrder(), definition.packageId(),
					definition.version(), changed, principal.personId(), now)) {
				created++;
			}
			else if (changed) {
				updated++;
			}
		}
		return new LayerImport(created, updated, dropped, assigned);
	}

	/**
	 * Removes the layers the package no longer has, after clearing the assignments of every
	 * metatype to them, organization metatypes included; then numbers the layers densely. Returns
	 * the number of metatypes that had one of the layers before the import.
	 */
	private int dropLayers(PackageDefinition definition, LayerImport layerImport) {
		this.layerStore.clearAssignments(layerImport.dropped());
		int cleared = layerImport.assigned();
		for (UUID id : layerImport.dropped()) {
			this.layerStore.deletePackageLayer(id, definition.packageId());
		}
		this.layerStore.renumber();
		return cleared;
	}

	/** The layers of the package and the other layers of the instance number at most 20. */
	private void checkLayerLimit(PackageDefinition definition) {
		if (this.layerStore.countOutside(definition.packageId()) + definition.layers().size()
				> ArchitectureLayers.MAXIMUM_LAYERS) {
			throw new InvalidPackageException("invalid-package", "/architectureLayers",
					"The layers of the package and of the instance exceed " + ArchitectureLayers.MAXIMUM_LAYERS + ".");
		}
	}

	/** What the import did to the viewpoints of the package; {@code deleted} holds the IDs. */
	private record ViewpointImport(int created, int updated, List<UUID> deleted) {
	}

	/**
	 * Installs the viewpoints as system-defined ones after the metatypes (design D4 of
	 * us-091-viewpoint-notations): viewpoints of the package that the new version no longer has are
	 * deleted, the others are created or replaced with their links and mappings. Referenced types
	 * must be element or connection metatypes of the instance configuration, including those the
	 * package has just installed.
	 */
	private ViewpointImport importViewpoints(OeaPrincipal principal, PackageDefinition definition, Instant now) {
		Set<UUID> wanted = new HashSet<>();
		definition.viewpoints().forEach((viewpoint) -> wanted.add(viewpoint.id()));
		List<UUID> deleted = new ArrayList<>();
		for (UUID id : this.viewpointStore.packageViewpointIds(definition.packageId())) {
			if (!wanted.contains(id)) {
				this.viewpointStore.deletePackageViewpoint(id, definition.packageId());
				deleted.add(id);
			}
		}
		for (int i = 0; i < definition.viewpoints().size(); i++) {
			PackageDefinition.Viewpoint viewpoint = definition.viewpoints().get(i);
			List<UUID> all = new ArrayList<>(viewpoint.metatypeIds());
			all.addAll(viewpoint.connectionTypeIds());
			Map<UUID, ViewpointStore.Kind> kinds = this.viewpointStore.kinds(all, INSTANCE_CONFIGURATION);
			for (UUID id : new HashSet<>(viewpoint.metatypeIds())) {
				ViewpointStore.Kind kind = kinds.get(id);
				fail(kind == null || kind.connection() || !kind.instance(), principal, definition,
						"/viewpoints/" + i + "/metatypeIds", "A metatype of the viewpoint is not an element metatype "
								+ "of the instance or of the package.");
			}
			for (UUID id : new HashSet<>(viewpoint.connectionTypeIds())) {
				ViewpointStore.Kind kind = kinds.get(id);
				fail(kind == null || !kind.connection() || !kind.instance(), principal, definition,
						"/viewpoints/" + i + "/connectionTypeIds", "A connection type of the viewpoint is not a "
								+ "connection metatype of the instance or of the package.");
			}
		}
		// the names of the package's viewpoints are free again, so that a rename or swap cannot collide
		this.viewpointStore.releaseNames(definition.packageId());
		int created = 0;
		int updated = 0;
		for (PackageDefinition.Viewpoint viewpoint : definition.viewpoints()) {
			ViewpointStore.Fields fields = new ViewpointStore.Fields(viewpoint.name().strip(),
					(viewpoint.description() != null && !viewpoint.description().isBlank()) ? viewpoint.description()
							: null,
					viewpoint.notation(), List.copyOf(new LinkedHashSet<>(viewpoint.metatypeIds())),
					List.copyOf(new LinkedHashSet<>(viewpoint.connectionTypeIds())),
					viewpoint.mappings()
						.stream()
						.map((m) -> new ViewpointStore.MappingRow(m.metatypeId(), m.notationElement(),
								m.defaultWidth(), m.defaultHeight(), m.visualHint()))
						.toList());
			if (this.viewpointStore.upsertSystemDefined(viewpoint.id(), fields, definition.packageId(),
					definition.version(), principal.personId(), now)) {
				created++;
			}
			else {
				updated++;
			}
		}
		return new ViewpointImport(created, updated, List.copyOf(deleted));
	}

	private void fail(boolean condition, OeaPrincipal principal, PackageDefinition definition, String pointer,
			String message) {
		if (condition) {
			InvalidPackageException ex = new InvalidPackageException("invalid-package", pointer, message);
			rejected(principal, definition, ex.getType());
			throw ex;
		}
	}

	private List<PackageConflictException.Conflict> conflicts(PackageDefinition definition) {
		List<PackageConflictException.Conflict> conflicts = new ArrayList<>();
		for (PackageDefinition.Category category : definition.categories()) {
			check(conflicts, definition, "property category " + category.name(), owners("""
					SELECT scope, source_package_id FROM property_categories
					WHERE id = ? OR (lower(name) = lower(?) AND id <> ?)""", category.id(), category.name(),
					category.id()));
		}
		for (PackageDefinition.Property property : definition.properties()) {
			check(conflicts, definition, "property definition " + property.name(), owners("""
					SELECT scope, source_package_id FROM property_definitions
					WHERE id = ? OR ((lower(name) = lower(?) OR sql_column_name = ?) AND id <> ?)""", property.id(),
					property.name(), StorageNames.derive(property.name()), property.id()));
			for (PackageDefinition.EnumValue value : property.enumValues()) {
				check(conflicts, definition, "enum value " + value.value(), owners("""
						SELECT d.scope, d.source_package_id FROM property_enum_values v
						JOIN property_definitions d ON d.id = v.property_def_id
						WHERE v.id = ? AND v.property_def_id <> ?""", value.id(), property.id()));
			}
		}
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			// also the names of extensions: an instance metatype never takes one (BR-09, us-037 D3)
			check(conflicts, definition, "metatype " + metatype.name(), owners("""
					SELECT scope, source_package_id FROM metatypes
					WHERE id = ? OR (lower(name) = lower(?) AND id <> ?)""", metatype.id(), metatype.name(),
					metatype.id()));
			for (PackageDefinition.Mapping mapping : metatype.mappings()) {
				check(conflicts, definition, "property mapping of " + metatype.name(), owners("""
						SELECT t.scope, t.source_package_id FROM metatype_property_mappings m
						JOIN metatypes t ON t.id = m.metatype_id
						WHERE m.id = ? AND m.metatype_id <> ?""", mapping.id(), metatype.id()));
			}
		}
		for (PackageDefinition.Layer layer : definition.layers()) {
			check(conflicts, definition, "architecture layer " + layer.name().strip(),
					this.layerStore.owners(layer.id(), layer.name().strip())
						.stream()
						.map((owner) -> new Owner(owner.scope(), owner.packageId()))
						.toList());
		}
		for (PackageDefinition.Viewpoint viewpoint : definition.viewpoints()) {
			List<Owner> owners = this.viewpointStore.owners(viewpoint.id(), viewpoint.name().strip())
				.stream()
				.map((owner) -> new Owner("system-defined".equals(owner.viewpointType()) ? "imported" : "organization",
						owner.sourcePackageId()))
				.toList();
			check(conflicts, definition, "viewpoint " + viewpoint.name().strip(), owners);
		}
		return conflicts;
	}

	private void check(List<PackageConflictException.Conflict> conflicts, PackageDefinition definition, String element,
			List<Owner> owners) {
		for (Owner owner : owners) {
			boolean ownContent = "imported".equals(owner.scope()) && definition.packageId().equals(owner.packageId());
			if (!ownContent) {
				String reason = switch (owner.scope()) {
					case "built-in" -> "built-in";
					case "organization" -> "organization";
					default -> "other-package";
				};
				conflicts.add(new PackageConflictException.Conflict(element, reason));
				return;
			}
		}
	}

	private List<Owner> owners(String sql, Object... arguments) {
		return this.jdbc.query(sql, (rs, n) -> new Owner(rs.getString(1), rs.getString(2)), arguments);
	}

	private void upsertCategory(PackageDefinition definition, PackageDefinition.Category category) {
		this.jdbc.update("""
				INSERT INTO property_categories (id, name, sort_order, description, scope, source_package_id,
				    source_package_version) VALUES (?, ?, ?, ?, 'imported', ?, ?)
				ON CONFLICT (id) DO UPDATE SET name = excluded.name, sort_order = excluded.sort_order,
				    description = excluded.description, source_package_version = excluded.source_package_version,
				    version = property_categories.version + 1""",
				category.id(), category.name().strip(), category.sortOrder(), category.description(),
				definition.packageId(), definition.version());
	}

	/** Stored fields of a metatype that a package defines. */
	private record MetatypeFields(String name, String abbreviation, boolean connection, int sortOrder,
			UUID architectureLayerId) {

		MetatypeFields withLayer(UUID layerId) {
			return new MetatypeFields(this.name, this.abbreviation, this.connection, this.sortOrder, layerId);
		}

	}

	/** Returns {@code created}, {@code updated} or {@code unchanged} (mappings are compared later). */
	private String upsertMetatype(PackageDefinition definition, PackageDefinition.Metatype metatype) {
		String abbreviation = (metatype.abbreviation() != null) ? metatype.abbreviation().strip() : null;
		MetatypeFields wanted = new MetatypeFields(metatype.name().strip(), abbreviation, metatype.connection(),
				metatype.sortOrder(), metatype.architectureLayerId());
		List<MetatypeFields> existing = this.jdbc.query(
				"SELECT name, abbreviation, is_connection, sort_order FROM metatypes WHERE id = ?",
				(rs, n) -> new MetatypeFields(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4), null),
				metatype.id())
			.stream()
			.map((fields) -> fields.withLayer(this.layerStore.layerOf(metatype.id()).orElse(null)))
			.toList();
		if (existing.isEmpty()) {
			this.jdbc.update("""
					INSERT INTO metatypes (id, config_id, name, abbreviation, is_connection, sort_order, scope,
					    source_package_id, source_package_version, is_composition, architecture_layer_id)
					VALUES (?, ?, ?, ?, ?, ?, 'imported', ?, ?, ?, ?)""",
					metatype.id(), INSTANCE_CONFIGURATION, wanted.name(), abbreviation, metatype.connection(),
					metatype.sortOrder(), definition.packageId(), definition.version(), metatype.composition(),
					metatype.architectureLayerId());
			return "created";
		}
		this.jdbc.update("""
				UPDATE metatypes SET name = ?, abbreviation = ?, is_connection = ?, sort_order = ?,
				       source_package_version = ?, is_composition = ?, architecture_layer_id = ? WHERE id = ?""",
				wanted.name(), abbreviation, metatype.connection(), metatype.sortOrder(), definition.version(),
				metatype.composition(), metatype.architectureLayerId(), metatype.id());
		return existing.get(0).equals(wanted) ? "unchanged" : "updated";
	}

	private void upsertProperty(PackageDefinition definition, PackageDefinition.Property property) {
		this.jdbc.update("""
				INSERT INTO property_definitions (id, name, sql_column_name, data_type, category_id, ref_metatype_id,
				    max_length, min_value, max_value, pattern, scope, source_package_id, source_package_version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'imported', ?, ?)
				ON CONFLICT (id) DO UPDATE SET name = excluded.name, data_type = excluded.data_type,
				    category_id = excluded.category_id, ref_metatype_id = excluded.ref_metatype_id,
				    max_length = excluded.max_length, min_value = excluded.min_value, max_value = excluded.max_value,
				    pattern = excluded.pattern, source_package_version = excluded.source_package_version,
				    version = property_definitions.version + 1""",
				property.id(), property.name().strip(), StorageNames.derive(property.name()),
				property.dataType(), property.categoryId(), property.refMetatypeId(), property.maxLength(),
				property.minValue(), property.maxValue(), property.pattern(), definition.packageId(),
				definition.version());
		this.jdbc.update("DELETE FROM property_enum_values WHERE property_def_id = ?", property.id());
		for (PackageDefinition.EnumValue value : property.enumValues()) {
			this.jdbc.update("INSERT INTO property_enum_values (id, property_def_id, value, sort_order) VALUES (?, ?, ?, ?)",
					value.id(), property.id(), value.value(), value.sortOrder());
		}
	}

	/** A base type outside the package must be a built-in metatype. */
	private void checkBaseTypes(PackageDefinition definition) {
		Set<UUID> own = new HashSet<>();
		definition.metatypes().forEach((metatype) -> own.add(metatype.id()));
		for (int i = 0; i < definition.metatypes().size(); i++) {
			UUID base = definition.metatypes().get(i).baseMetatypeId();
			if (BuiltInMetatypes.STRUCTURING_AID.equals(base)) {
				throw new InvalidPackageException("invalid-package", "/metatypes/" + i + "/baseMetatypeId",
						"The structuring aid cannot be a base type.");
			}
			if (base != null && !own.contains(base) && !Boolean.TRUE.equals(this.jdbc.queryForObject(
					"SELECT EXISTS (SELECT 1 FROM metatypes WHERE id = ? AND scope = 'built-in')", Boolean.class, base))) {
				throw new InvalidPackageException("invalid-package", "/metatypes/" + i + "/baseMetatypeId",
						"The base type is neither part of the package nor built-in.");
			}
		}
	}

	/**
	 * Allowed endpoints are metatypes of the package or built-in ones, a built-in base type has the
	 * same kind, and an imported metatype keeps its kind at a re-import (us-036-connection-metatypes D2).
	 */
	private void checkConnections(PackageDefinition definition) {
		Set<UUID> own = new HashSet<>();
		definition.metatypes().forEach((metatype) -> own.add(metatype.id()));
		for (int i = 0; i < definition.metatypes().size(); i++) {
			PackageDefinition.Metatype metatype = definition.metatypes().get(i);
			String pointer = "/metatypes/" + i;
			List<UUID> allowed = new ArrayList<>(metatype.allowedSources());
			allowed.addAll(metatype.allowedTargets());
			for (UUID endpoint : allowed) {
				if (BuiltInMetatypes.STRUCTURING_AID.equals(endpoint)) {
					throw new InvalidPackageException("invalid-package", pointer + "/connectionConstraints",
							"A structuring aid has no connections.");
				}
				if (!own.contains(endpoint) && !Boolean.TRUE.equals(this.jdbc.queryForObject(
						"SELECT EXISTS (SELECT 1 FROM metatypes WHERE id = ? AND scope = 'built-in')", Boolean.class,
						endpoint))) {
					throw new InvalidPackageException("invalid-package", pointer + "/connectionConstraints",
							"The allowed metatype " + endpoint + " is neither part of the package nor built-in.");
				}
			}
			UUID base = metatype.baseMetatypeId();
			if (base != null && !own.contains(base) && !Boolean.TRUE.equals(this.jdbc.queryForObject(
					"SELECT EXISTS (SELECT 1 FROM metatypes WHERE id = ? AND is_connection = ?)", Boolean.class, base,
					metatype.connection()))) {
				throw new InvalidPackageException("invalid-package", pointer + "/baseMetatypeId",
						"The base type is not of the same kind (connection or element).");
			}
			List<Boolean> stored = this.jdbc.queryForList("SELECT is_connection FROM metatypes WHERE id = ?",
					Boolean.class, metatype.id());
			if (!stored.isEmpty() && stored.get(0) != metatype.connection()) {
				throw new InvalidPackageException("invalid-package", pointer + "/isConnection",
						"Whether a metatype is a connection cannot change at a re-import.");
			}
		}
	}

	/**
	 * The composition metatype of the package must not meet another marked one of the instance
	 * (design D4 of us-135-component-hierarchy).
	 */
	private void checkComposition(PackageDefinition definition) {
		for (int i = 0; i < definition.metatypes().size(); i++) {
			PackageDefinition.Metatype metatype = definition.metatypes().get(i);
			CompositionStore.Marked marked = metatype.composition() ? this.composition.markedMetatype().orElse(null)
					: null;
			// the mark of this package moves with its re-import; one of another origin blocks it
			if (marked != null && !marked.id().equals(metatype.id())
					&& !definition.packageId().equals(marked.packageId())) {
				throw new InvalidPackageException("invalid-package", "/metatypes/" + i + "/isComposition",
						"Another connection metatype of the instance is the composition.");
			}
		}
	}

	/** Replaces the allowed endpoints; a metatype whose endpoints changed counts as updated. */
	private String replaceConstraints(PackageDefinition.Metatype metatype, String action) {
		Map<String, List<UUID>> before = ConnectionConstraints.of(this.jdbc, metatype.id());
		ConnectionConstraints.replace(this.jdbc, metatype.id(), metatype.allowedSources(), metatype.allowedTargets());
		boolean changed = !Set.copyOf(before.get(ConnectionConstraints.SOURCE)).equals(Set.copyOf(metatype.allowedSources()))
				|| !Set.copyOf(before.get(ConnectionConstraints.TARGET)).equals(Set.copyOf(metatype.allowedTargets()));
		return (changed && "unchanged".equals(action)) ? "updated" : action;
	}

	/** Sets the base type; a metatype whose base type changed counts as updated. */
	private String setBaseType(PackageDefinition.Metatype metatype, String action) {
		int changed = this.jdbc.update(
				"UPDATE metatypes SET base_metatype_id = ? WHERE id = ? AND base_metatype_id IS DISTINCT FROM ?::uuid",
				metatype.baseMetatypeId(), metatype.id(), metatype.baseMetatypeId());
		return (changed > 0 && "unchanged".equals(action)) ? "updated" : action;
	}

	/** A property may be mapped once in the base type chain of every metatype (design D4). */
	private void uniqueInChains(OeaPrincipal principal, PackageDefinition definition) {
		Map<UUID, UUID> bases = this.effects.bases();
		for (int i = 0; i < definition.metatypes().size(); i++) {
			UUID id = definition.metatypes().get(i).id();
			Set<UUID> related = new HashSet<>(MetamodelEffects.chain(id, bases));
			related.addAll(MetamodelEffects.subtree(id, bases));
			Boolean duplicate = this.jdbc.queryForObject("""
					SELECT EXISTS (SELECT 1 FROM metatype_property_mappings a JOIN metatype_property_mappings b
					    ON a.property_def_id = b.property_def_id AND a.metatype_id <> b.metatype_id
					WHERE a.metatype_id = ? AND b.metatype_id = ANY (?::uuid[]))""", Boolean.class, id,
					MetamodelEffects.ids(related));
			if (Boolean.TRUE.equals(duplicate)) {
				InvalidPackageException ex = new InvalidPackageException("invalid-package",
						"/metatypes/" + i + "/propertyMappings", "A property is mapped twice in the base type chain.");
				rejected(principal, definition, ex.getType());
				throw ex;
			}
		}
	}

	private static int sum(Map<UUID, Integer> counts) {
		return counts.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Replaces the mappings of the package and keeps organization mappings (us-032-metatype-editing
	 * D8); a metatype whose package mappings changed counts as updated.
	 */
	private String replaceMappings(PackageDefinition.Metatype metatype, String action, List<UUID> skipped) {
		List<String> before = mappingSignature(metatype.id());
		// rows already applied keep that state, so a re-import does not apply an entry twice
		Map<UUID, Timestamp> applied = new HashMap<>();
		this.jdbc.query("""
				DELETE FROM metatype_property_mappings WHERE metatype_id = ? AND scope <> 'organization'
				RETURNING id, effective_applied_at""", (rs) -> {
			applied.put(rs.getObject(1, UUID.class), rs.getTimestamp(2));
		}, metatype.id());
		Timestamp now = Timestamp.from(this.clock.instant());
		for (PackageDefinition.Mapping mapping : metatype.mappings()) {
			boolean inEffect = !mapping.validFrom().isAfter(this.effects.today());
			// a new row in effect takes effect within the import; a kept row keeps its state
			Timestamp appliedAt = applied.containsKey(mapping.id()) ? applied.get(mapping.id())
					: inEffect ? now : null;
			// on a date that an organization entry has, the organization entry wins (ADR-049)
			int inserted = this.jdbc.update("""
					INSERT INTO metatype_property_mappings (id, metatype_id, property_def_id, necessity, valid_from,
					    sort_order, scope, effective_applied_at) VALUES (?, ?, ?, ?, ?, ?, 'imported', ?)
					ON CONFLICT (metatype_id, property_def_id, valid_from) DO NOTHING""", mapping.id(), metatype.id(),
					mapping.propertyDefinitionId(), mapping.necessity(), Date.valueOf(mapping.validFrom()),
					mapping.sortOrder(), appliedAt);
			if (inserted == 0) {
				skipped.add(mapping.id());
			}
		}
		if ("unchanged".equals(action) && !before.equals(mappingSignature(metatype.id()))) {
			return "updated";
		}
		return action;
	}

	private List<String> mappingSignature(UUID metatypeId) {
		return this.jdbc.query("""
				SELECT m.id || '|' || m.property_def_id || '|' || m.necessity || '|' || m.valid_from || '|' || m.sort_order
				FROM metatype_property_mappings m WHERE m.metatype_id = ? AND m.scope <> 'organization' ORDER BY m.id""", (rs, n) -> rs.getString(1),
				metatypeId);
	}

	/** A package rejected before it reached the import (unreadable or not matching the format). */
	public void recordRejected(OeaPrincipal principal, String reason) {
		if (principal != null) {
			rejected(principal, null, reason);
		}
	}

	private void rejected(OeaPrincipal principal, PackageDefinition definition, String reason) {
		Map<String, Object> details = new HashMap<>();
		details.put("reason", reason);
		if (definition != null && definition.packageId() != null
				&& definition.packageId().matches("^[a-z0-9][a-z0-9-]{1,62}$")) {
			details.put("packageId", definition.packageId());
		}
		this.audit.appendIndependently(new AuditEvent("metamodel.package-rejected", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.FAILURE, AuditEvent.Source.API,
				Sessions.actor(principal), "metamodel-package", null, details));
	}

}

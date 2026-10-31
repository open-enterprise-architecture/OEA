package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

	public PackageImporter(JdbcTemplate jdbc, AuditWriter audit, Clock clock, MetamodelEffects effects,
			PropertyMappings mappings, ReimportPolicies policies) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
		this.effects = effects;
		this.mappings = mappings;
		this.policies = policies;
	}

	/** Result of an import; counts refer to metatypes. */
	public record Outcome(UUID batchId, int created, int updated) {
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
			PackageValidator.validate(definition);
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
		}
		catch (InvalidPackageException ex) {
			rejected(principal, definition, ex.getType());
			throw ex;
		}
		Instant now = this.clock.instant();
		UUID batchId = UUID.randomUUID();
		MetamodelEffects.Snapshot before = this.effects.snapshot();
		for (PackageDefinition.Category category : definition.categories()) {
			upsertCategory(definition, category);
		}
		Map<UUID, String> actions = new HashMap<>();
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			actions.put(metatype.id(), upsertMetatype(definition, metatype));
		}
		for (PackageDefinition.Property property : definition.properties()) {
			upsertProperty(definition, property);
		}
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			actions.put(metatype.id(), setBaseType(metatype, actions.get(metatype.id())));
		}
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			String action = replaceMappings(metatype, actions.get(metatype.id()));
			actions.put(metatype.id(), action);
		}
		uniqueInChains(principal, definition);
		// organization mappings are kept or removed by the re-import setting (us-032-metatype-editing D8)
		Map<UUID, String> policies = this.policies.effective();
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			if ("remove".equals(policies.get(metatype.id()))) {
				this.mappings.removeForReimport(principal, metatype.id(), batchId);
			}
		}
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
		this.audit.append(new AuditEvent("metamodel.package-imported", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"metamodel-package", definition.packageId(), Map.of("packageId", definition.packageId(), "version",
						definition.version(), "batchId", batchId.toString(), "metatypesCreated", created,
						"metatypesUpdated", updated, "entitiesWithRemovedValues", sum(effects.removed()),
						"entitiesMarked", sum(effects.marked()))));
		for (PackageDefinition.Metatype metatype : definition.metatypes()) {
			String action = actions.get(metatype.id());
			if (!"unchanged".equals(action)) {
				this.audit.append(new AuditEvent("metamodel.metatype-imported", AuditEvent.Category.CHANGE,
						AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API,
						Sessions.actor(principal), "metatype", metatype.id().toString(),
						Map.of("batchId", batchId.toString(), "packageId", definition.packageId(), "action", action)));
			}
		}
		return new Outcome(batchId, created, updated);
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
			check(conflicts, definition, "metatype " + metatype.name(), owners("""
					SELECT scope, source_package_id FROM metatypes
					WHERE id = ? OR (config_id = ? AND lower(name) = lower(?) AND id <> ?)""", metatype.id(),
					INSTANCE_CONFIGURATION, metatype.name(), metatype.id()));
			for (PackageDefinition.Mapping mapping : metatype.mappings()) {
				check(conflicts, definition, "property mapping of " + metatype.name(), owners("""
						SELECT t.scope, t.source_package_id FROM metatype_property_mappings m
						JOIN metatypes t ON t.id = m.metatype_id
						WHERE m.id = ? AND m.metatype_id <> ?""", mapping.id(), metatype.id()));
			}
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
				    description = excluded.description, source_package_version = excluded.source_package_version""",
				category.id(), category.name().strip(), category.sortOrder(), category.description(),
				definition.packageId(), definition.version());
	}

	/** Stored fields of a metatype that a package defines. */
	private record MetatypeFields(String name, String abbreviation, boolean connection, int sortOrder) {
	}

	/** Returns {@code created}, {@code updated} or {@code unchanged} (mappings are compared later). */
	private String upsertMetatype(PackageDefinition definition, PackageDefinition.Metatype metatype) {
		String abbreviation = (metatype.abbreviation() != null) ? metatype.abbreviation().strip() : null;
		MetatypeFields wanted = new MetatypeFields(metatype.name().strip(), abbreviation, metatype.connection(),
				metatype.sortOrder());
		List<MetatypeFields> existing = this.jdbc.query(
				"SELECT name, abbreviation, is_connection, sort_order FROM metatypes WHERE id = ?",
				(rs, n) -> new MetatypeFields(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4)),
				metatype.id());
		if (existing.isEmpty()) {
			this.jdbc.update("""
					INSERT INTO metatypes (id, config_id, name, abbreviation, is_connection, sort_order, scope,
					    source_package_id, source_package_version) VALUES (?, ?, ?, ?, ?, ?, 'imported', ?, ?)""",
					metatype.id(), INSTANCE_CONFIGURATION, wanted.name(), abbreviation, metatype.connection(),
					metatype.sortOrder(), definition.packageId(), definition.version());
			return "created";
		}
		this.jdbc.update("""
				UPDATE metatypes SET name = ?, abbreviation = ?, is_connection = ?, sort_order = ?,
				       source_package_version = ? WHERE id = ?""", wanted.name(), abbreviation, metatype.connection(),
				metatype.sortOrder(), definition.version(), metatype.id());
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
				    pattern = excluded.pattern, source_package_version = excluded.source_package_version""",
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
			if (base != null && !own.contains(base) && !Boolean.TRUE.equals(this.jdbc.queryForObject(
					"SELECT EXISTS (SELECT 1 FROM metatypes WHERE id = ? AND scope = 'built-in')", Boolean.class, base))) {
				throw new InvalidPackageException("invalid-package", "/metatypes/" + i + "/baseMetatypeId",
						"The base type is neither part of the package nor built-in.");
			}
		}
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
	private String replaceMappings(PackageDefinition.Metatype metatype, String action) {
		List<String> before = mappingSignature(metatype.id());
		this.jdbc.update("""
				DELETE FROM metatype_property_mappings m USING property_definitions p
				WHERE m.property_def_id = p.id AND m.metatype_id = ? AND p.scope <> 'organization'""", metatype.id());
		for (PackageDefinition.Mapping mapping : metatype.mappings()) {
			this.jdbc.update("""
					INSERT INTO metatype_property_mappings (id, metatype_id, property_def_id, necessity, valid_from,
					    sort_order) VALUES (?, ?, ?, ?, ?, ?)""", mapping.id(), metatype.id(),
					mapping.propertyDefinitionId(), mapping.necessity(), Date.valueOf(mapping.validFrom()),
					mapping.sortOrder());
		}
		if ("unchanged".equals(action) && !before.equals(mappingSignature(metatype.id()))) {
			return "updated";
		}
		return action;
	}

	private List<String> mappingSignature(UUID metatypeId) {
		return this.jdbc.query("""
				SELECT m.id || '|' || m.property_def_id || '|' || m.necessity || '|' || m.valid_from || '|' || m.sort_order
				FROM metatype_property_mappings m JOIN property_definitions p ON p.id = m.property_def_id
				WHERE m.metatype_id = ? AND p.scope <> 'organization' ORDER BY m.id""", (rs, n) -> rs.getString(1),
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

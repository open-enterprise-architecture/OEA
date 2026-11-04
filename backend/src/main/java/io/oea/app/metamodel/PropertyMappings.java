package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mapping organization property definitions to metatypes and removing them again (design D7 of
 * us-032-metatype-editing; REQ-149), and entries by effective date (us-137-mapping-effective-date
 * D2; REQ-148, ADR-049). A mandatory entry in effect marks the entities without a value; removing
 * the last entry of a property deletes its values from the entities.
 */
@Service
public class PropertyMappings {

	private static final Set<String> NECESSITIES = Set.of("mandatory", "warning", "optional");

	private final JdbcTemplate jdbc;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	private final MetamodelVersions versions;

	public PropertyMappings(JdbcTemplate jdbc, MetamodelEffects effects, MetamodelAudit audit,
			MetamodelVersions versions) {
		this.jdbc = jdbc;
		this.effects = effects;
		this.audit = audit;
		this.versions = versions;
	}

	/** A typed default value; at most one field is set. */
	public record DefaultValue(String text, Long integer, Boolean bool, LocalDate date, String enumValue) {
	}

	public record Input(UUID propertyDefId, String necessity, LocalDate validFrom, int sortOrder,
			DefaultValue defaultValue) {
	}

	/** The new mapping and the number of entities marked as missing the property. */
	public record Created(UUID id, int markedEntities) {
	}

	@Transactional
	public Created create(OeaPrincipal principal, UUID metatypeId, Input input, Long expectedVersion) {
		this.effects.lock();
		// a mapping is part of the metatype's version (scr-030-metamodel-configuration D1)
		this.versions.check(MetamodelVersions.Element.METATYPE, metatypeId, expectedVersion);
		List<String[]> definition = this.jdbc.query(
				"SELECT scope, data_type FROM property_definitions WHERE id = ? AND retired_at IS NULL",
				(rs, n) -> new String[] { rs.getString(1), rs.getString(2) }, input.propertyDefId());
		if (definition.isEmpty()) {
			throw MetamodelChangeException.invalid("propertyDefId", "The property definition does not exist.");
		}
		if (!NECESSITIES.contains(input.necessity())) {
			throw MetamodelChangeException.invalid("necessity", "Unknown necessity.");
		}
		checkDefault(input, definition.get(0)[1]);
		LocalDate validFrom = (input.validFrom() != null) ? input.validFrom() : this.effects.today();
		List<Integer> existingOrder = this.jdbc.queryForList("""
				SELECT sort_order FROM metatype_property_mappings WHERE metatype_id = ? AND property_def_id = ?
				ORDER BY valid_from DESC""", Integer.class, metatypeId, input.propertyDefId());
		int sortOrder = input.sortOrder();
		if (existingOrder.isEmpty()) {
			// a new mapping: organization definitions only, once per base type chain
			PropertyDefinitions.readOnlyUnlessOrganization(definition.get(0)[0], "property definition");
			Map<UUID, UUID> bases = this.effects.bases();
			Integer duplicates = this.jdbc.queryForObject("""
					SELECT count(*) FROM metatype_property_mappings
					WHERE property_def_id = ? AND (metatype_id = ANY (?::uuid[]) OR metatype_id = ANY (?::uuid[]))""",
					Integer.class, input.propertyDefId(),
					MetamodelEffects.ids(MetamodelEffects.chain(metatypeId, bases)),
					MetamodelEffects.ids(MetamodelEffects.subtree(metatypeId, bases)));
			if (duplicates != null && duplicates > 0) {
				throw MetamodelChangeException.conflict("The property is mapped in the base type chain already.");
			}
		}
		else {
			// a further entry by effective date, also on a package mapping (US-137, ADR-049)
			if (exists("""
					SELECT EXISTS (SELECT 1 FROM metatype_property_mappings
					               WHERE metatype_id = ? AND property_def_id = ? AND valid_from = ?)""", metatypeId,
					input.propertyDefId(), Date.valueOf(validFrom))) {
				throw MetamodelChangeException.conflict("An entry with this valid-from date exists already.");
			}
			sortOrder = existingOrder.get(0);
		}
		boolean inEffect = !validFrom.isAfter(this.effects.today());
		MetamodelEffects.Snapshot before = this.effects.snapshot();
		UUID id = UUID.randomUUID();
		DefaultValue value = input.defaultValue();
		this.jdbc.update("""
				INSERT INTO metatype_property_mappings (id, metatype_id, property_def_id, necessity, valid_from, sort_order,
				    default_value_text, default_value_int, default_value_bool, default_value_date, default_value_enum,
				    created_by, scope, effective_applied_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'organization', ?)""", id, metatypeId, input.propertyDefId(),
				input.necessity(), Date.valueOf(validFrom), sortOrder, (value != null) ? value.text() : null,
				(value != null) ? value.integer() : null, (value != null) ? value.bool() : null,
				(value != null && value.date() != null) ? Date.valueOf(value.date()) : null,
				(value != null) ? value.enumValue() : null, principal.personId(),
				inEffect ? Timestamp.from(this.effects.now()) : null);
		this.versions.increment(MetamodelVersions.Element.METATYPE, metatypeId);
		// a future entry takes effect on its date (MappingEffectiveDates)
		int marked = inEffect ? this.effects.apply(before, principal).marked(input.propertyDefId()) : 0;
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("mappingId", id.toString());
		details.put("propertyDefId", input.propertyDefId().toString());
		details.put("necessity", input.necessity());
		details.put("validFrom", validFrom.toString());
		details.put("entitiesMarked", marked);
		this.audit.changed("metamodel.mapping-created", principal, "metatype", metatypeId, details);
		return new Created(id, marked);
	}

	/**
	 * Removes an organization entry; when the metatype then no longer has the property, its values
	 * are deleted (design D6, D7; us-137-mapping-effective-date D4).
	 */
	@Transactional
	public void delete(OeaPrincipal principal, UUID metatypeId, UUID mappingId, Long expectedVersion) {
		this.effects.lock();
		this.versions.check(MetamodelVersions.Element.METATYPE, metatypeId, expectedVersion);
		List<Object[]> mapping = this.jdbc.query("""
				SELECT property_def_id, scope FROM metatype_property_mappings WHERE id = ? AND metatype_id = ?""",
				(rs, n) -> new Object[] { rs.getObject(1, UUID.class), rs.getString(2) }, mappingId, metatypeId);
		if (mapping.isEmpty()) {
			throw new NoSuchElementException("no mapping " + mappingId);
		}
		PropertyDefinitions.readOnlyUnlessOrganization((String) mapping.get(0)[1], "mapping");
		UUID property = (UUID) mapping.get(0)[0];
		MetamodelEffects.Snapshot before = this.effects.snapshot();
		this.jdbc.update("DELETE FROM metatype_property_mappings WHERE id = ?", mappingId);
		this.versions.increment(MetamodelVersions.Element.METATYPE, metatypeId);
		int removed = this.effects.apply(before, principal).removed(property);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("mappingId", mappingId.toString());
		details.put("propertyDefId", property.toString());
		details.put("entitiesWithRemovedValues", removed);
		this.audit.changed("metamodel.mapping-deleted", principal, "metatype", metatypeId, details);
	}

	/**
	 * Removes the organization mappings of a re-imported metatype (design D8); the caller applies
	 * the effects. Audited with the batch of the import.
	 */
	int removeForReimport(OeaPrincipal principal, UUID metatypeId, UUID batchId) {
		int[] removed = { 0 };
		this.jdbc.query("""
				DELETE FROM metatype_property_mappings WHERE metatype_id = ? AND scope = 'organization'
				RETURNING id, property_def_id""", (rs) -> {
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("mappingId", rs.getObject(1, UUID.class).toString());
			details.put("propertyDefId", rs.getObject(2, UUID.class).toString());
			details.put("batchId", batchId.toString());
			details.put("reason", "reimport");
			this.audit.changed("metamodel.mapping-deleted", principal, "metatype", metatypeId, details);
			removed[0]++;
		}, metatypeId);
		return removed[0];
	}

	private static void checkDefault(Input input, String dataType) {
		DefaultValue value = input.defaultValue();
		if (value == null) {
			return;
		}
		int set = 0;
		String expected = null;
		if (value.text() != null) {
			set++;
			expected = "string|text|url";
		}
		if (value.integer() != null) {
			set++;
			expected = "integer";
		}
		if (value.bool() != null) {
			set++;
			expected = "boolean";
		}
		if (value.date() != null) {
			set++;
			expected = "date";
		}
		if (value.enumValue() != null) {
			set++;
			expected = "enum";
		}
		if (set > 1 || (expected != null && !Set.of(expected.split("\\|")).contains(dataType))) {
			throw MetamodelChangeException.invalid("defaultValue", "The default value does not match the data type.");
		}
	}

	private boolean exists(String sql, Object... arguments) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject(sql, Boolean.class, arguments));
	}

}

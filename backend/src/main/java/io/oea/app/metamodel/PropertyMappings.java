package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.metamodel.BuiltInMetatypes;
import io.oea.core.metamodel.PropertyDefinitionStore;
import io.oea.core.metamodel.PropertyMappingStore;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

	private final PropertyMappingStore store;

	private final PropertyDefinitionStore definitions;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	private final MetamodelConfigurations configurations;

	private final MetamodelVersions versions;

	private final SolutionImpact impact;

	private final io.oea.core.access.PersonLinkTrigger personLinks;

	public PropertyMappings(PropertyMappingStore store, PropertyDefinitionStore definitions, MetamodelEffects effects, MetamodelAudit audit,
			MetamodelVersions versions, MetamodelConfigurations configurations, SolutionImpact impact,
			io.oea.core.access.PersonLinkTrigger personLinks) {
		this.personLinks = personLinks;
		this.impact = impact;
		this.store = store;
		this.definitions = definitions;
		this.effects = effects;
		this.audit = audit;
		this.versions = versions;
		this.configurations = configurations;
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
		this.configurations.requireWritableMetatype(principal, metatypeId);
		// a mapping is part of the metatype's version (scr-030-metamodel-configuration D1)
		this.versions.check(MetamodelVersions.Element.METATYPE, metatypeId, expectedVersion);
		requireMappable(metatypeId);
		Optional<PropertyDefinitionStore.StoredRow> definition = this.definitions.stored(input.propertyDefId());
		if (definition.isEmpty()) {
			throw MetamodelChangeException.invalid("propertyDefId", "The property definition does not exist.");
		}
		if (!NECESSITIES.contains(input.necessity())) {
			throw MetamodelChangeException.invalid("necessity", "Unknown necessity.");
		}
		checkDefault(input, definition.get().dataType());
		LocalDate validFrom = (input.validFrom() != null) ? input.validFrom() : this.effects.today();
		List<Integer> existingOrder = this.store.sortOrders(metatypeId, input.propertyDefId());
		int sortOrder = input.sortOrder();
		if (existingOrder.isEmpty()) {
			// a new mapping: organization definitions only, once per base type chain
			PropertyDefinitions.readOnlyUnlessOrganization(definition.get().scope(), "property definition");
			Map<UUID, UUID> bases = this.effects.bases();
			if (this.store.mappedOnAny(input.propertyDefId(), MetamodelEffects.chain(metatypeId, bases),
					MetamodelEffects.subtree(metatypeId, bases))) {
				throw MetamodelChangeException.conflict("The property is mapped in the base type chain already.");
			}
		}
		else {
			// a further entry by effective date, also on a package mapping (US-137, ADR-049)
			if (this.store.entryExists(metatypeId, input.propertyDefId(), validFrom)) {
				throw MetamodelChangeException.conflict("An entry with this valid-from date exists already.");
			}
			sortOrder = existingOrder.get(0);
		}
		boolean inEffect = !validFrom.isAfter(this.effects.today());
		MetamodelEffects.Snapshot before = this.effects.snapshot();
		UUID id = UUID.randomUUID();
		DefaultValue value = input.defaultValue();
		this.store.insert(id, metatypeId, input.propertyDefId(), input.necessity(), validFrom, sortOrder,
				(value != null) ? new PropertyMappingStore.DefaultValue(value.text(), value.integer(), value.bool(),
						value.date(), value.enumValue()) : null,
				principal.personId(), inEffect ? this.effects.now() : null);
		this.versions.increment(MetamodelVersions.Element.METATYPE, metatypeId);
		this.personLinks.changed();
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
	public void delete(OeaPrincipal principal, UUID metatypeId, UUID mappingId, Long expectedVersion) {
		delete(principal, metatypeId, mappingId, expectedVersion, SolutionImpact.Decisions.of(Map.of()));
	}

	/**
	 * Removes an organization entry with the decisions for the affected active solutions (US-154).
	 *
	 * @throws SolutionDecisionRequiredException when an affected solution has no decision (409)
	 */
	@Transactional
	public void delete(OeaPrincipal principal, UUID metatypeId, UUID mappingId, Long expectedVersion,
			SolutionImpact.Decisions decisions) {
		this.effects.lock();
		this.configurations.requireWritableMetatype(principal, metatypeId);
		this.versions.check(MetamodelVersions.Element.METATYPE, metatypeId, expectedVersion);
		requireMappable(metatypeId);
		PropertyMappingStore.MappingRef mapping = this.store.find(mappingId, metatypeId)
			.orElseThrow(() -> new NoSuchElementException("no mapping " + mappingId));
		PropertyDefinitions.readOnlyUnlessOrganization(mapping.scope(), "mapping");
		UUID property = mapping.propertyDefId();
		MetamodelEffects.Snapshot before = this.effects.snapshot();
		this.store.delete(mappingId);
		this.personLinks.changed();
		this.versions.increment(MetamodelVersions.Element.METATYPE, metatypeId);
		Map<String, Object> details = new LinkedHashMap<>();
		// only a change of the instance metamodel asks the solutions; an extension is their own (US-154)
		boolean instance = PackageImporter.INSTANCE_CONFIGURATION.equals(this.configurations.configOf(metatypeId));
		this.impact.apply(principal, this.effects.lost(before), instance ? decisions : null, details,
				this.effects.now());
		int removed = this.effects.apply(before, principal).removed(property);
		details.put("mappingId", mappingId.toString());
		details.put("propertyDefId", property.toString());
		details.put("entitiesWithRemovedValues", removed);
		this.audit.changed("metamodel.mapping-deleted", principal, "metatype", metatypeId, details);
	}

	/** The structuring aid has no properties (US-136). */
	private static void requireMappable(UUID metatypeId) {
		if (BuiltInMetatypes.STRUCTURING_AID.equals(metatypeId)) {
			throw MetamodelChangeException.readOnly("metatype", "has no properties.");
		}
	}

	/**
	 * Removes the organization mappings of a re-imported metatype (design D8); the caller applies
	 * the effects. Audited with the batch of the import.
	 */
	int removeForReimport(OeaPrincipal principal, UUID metatypeId, UUID batchId) {
		List<PropertyMappingStore.RemovedRow> removed = this.store.deleteOrganization(metatypeId);
		for (PropertyMappingStore.RemovedRow row : removed) {
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("mappingId", row.id().toString());
			details.put("propertyDefId", row.propertyDefId().toString());
			details.put("batchId", batchId.toString());
			details.put("reason", "reimport");
			this.audit.changed("metamodel.mapping-deleted", principal, "metatype", metatypeId, details);
		}
		return removed.size();
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

}

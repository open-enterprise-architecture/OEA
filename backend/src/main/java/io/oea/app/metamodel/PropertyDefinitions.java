package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.core.metamodel.PropertyDefinitionStore;
import io.oea.core.metamodel.PropertyMappingStore;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The organization's own property definitions with their enum values, and property categories
 * (design D2, D5 of us-032-metatype-editing; ADR-022). Built-in and imported content is read-only.
 */
@Service
public class PropertyDefinitions {

	static final int NAME_MAX_LENGTH = 100;

	/** Data types an organization property can have; {@code reference} follows with connection metatypes. */
	static final Set<String> DATA_TYPES = Set.of("string", "text", "integer", "boolean", "date", "url", "enum");

	private final PropertyDefinitionStore store;

	private final PropertyMappingStore mappingStore;

	private final MetatypeStore metatypes;

	private final PropertyValueStore values;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	private final MetamodelConfigurations configurations;

	private final MetamodelVersions versions;

	private final SolutionImpact impact;

	private final io.oea.core.solution.SnapshotStore snapshots;

	private final PersonLinkMappings personLinks;

	public PropertyDefinitions(PropertyDefinitionStore store, PropertyMappingStore mappingStore,
			MetatypeStore metatypes, PropertyValueStore values, MetamodelEffects effects,
			MetamodelAudit audit, MetamodelVersions versions, MetamodelConfigurations configurations,
			SolutionImpact impact, io.oea.core.solution.SnapshotStore snapshots,
			PersonLinkMappings personLinks) {
		this.personLinks = personLinks;
		this.impact = impact;
		this.snapshots = snapshots;
		this.store = store;
		this.mappingStore = mappingStore;
		this.metatypes = metatypes;
		this.values = values;
		this.effects = effects;
		this.audit = audit;
		this.versions = versions;
		this.configurations = configurations;
	}

	/** The writable fields of a property definition. */
	public record Input(String name, String dataType, UUID categoryId, UUID refMetatypeId, Integer maxLength,
			BigDecimal minValue, BigDecimal maxValue, String pattern) {
	}

	/** The writable fields of a category. */
	public record CategoryInput(String name, String description, int sortOrder) {
	}

	@Transactional
	public UUID create(OeaPrincipal principal, Input input) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		String name = validate(input, null);
		UUID id = UUID.randomUUID();
		String column = StorageNames.forNewProperty(name, this.store::columnNameTaken);
		this.store.insert(id, fields(input, name), column, principal.personId());
		this.audit.changed("metamodel.property-created", principal, "property-definition", id,
				Map.of("name", name, "dataType", input.dataType(), "sqlColumnName", column));
		return id;
	}

	/** @param expectedVersion the version of {@code If-Match}, or {@code null} */
	@Transactional
	public void update(OeaPrincipal principal, UUID id, Input input, Long expectedVersion) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		this.versions.check(MetamodelVersions.Element.PROPERTY_DEFINITION, id, expectedVersion);
		PropertyDefinitionStore.StoredRow stored = stored(id);
		readOnlyUnlessOrganization(stored.scope(), "property definition");
		String name = validate(input, id);
		if (!stored.dataType().equals(input.dataType()) && this.values.hasValues(id, true)) {
			throw MetamodelChangeException.inUse("The data type cannot change while entity values exist.", null);
		}
		if (!"enum".equals(input.dataType())) {
			this.store.deleteEnumValues(id);
			this.effects.removeOrphanLabels();
		}
		this.store.update(id, fields(input, name));
		this.versions.increment(MetamodelVersions.Element.PROPERTY_DEFINITION, id);
		List<String> changed = new ArrayList<>();
		compare(changed, "name", stored.name(), name);
		compare(changed, "dataType", stored.dataType(), input.dataType());
		compare(changed, "categoryId", stored.categoryId(), input.categoryId());
		compare(changed, "maxLength", stored.maxLength(), input.maxLength());
		compare(changed, "minValue", stored.minValue(), input.minValue());
		compare(changed, "maxValue", stored.maxValue(), input.maxValue());
		compare(changed, "pattern", stored.pattern(), input.pattern());
		this.audit.changed("metamodel.property-updated", principal, "property-definition", id,
				Map.of("changedFields", changed));
	}

	/** Deletes an unmapped definition, or retires it when the entity history references it. */
	@Transactional
	public void delete(OeaPrincipal principal, UUID id, Long expectedVersion) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		this.versions.check(MetamodelVersions.Element.PROPERTY_DEFINITION, id, expectedVersion);
		PropertyDefinitionStore.StoredRow stored = stored(id);
		readOnlyUnlessOrganization(stored.scope(), "property definition");
		if (this.mappingStore.mapped(id)) {
			throw MetamodelChangeException.inUse("The property definition is mapped to metatypes.", null);
		}
		if (this.snapshots.keeps(id)) {
			throw MetamodelChangeException.inUse("A solution keeps the property definition as a snapshot.", null);
		}
		boolean retired = this.values.hasValues(id, true);
		this.personLinks.propertyRemoved(principal, id, retired ? "property-retired" : "property-deleted");
		if (retired) {
			this.store.retire(id, this.effects.now());
		}
		else {
			this.store.delete(id);
			this.effects.removeOrphanLabels();
		}
		this.audit.changed("metamodel.property-deleted", principal, "property-definition", id,
				Map.of("name", stored.name(), "retired", retired));
	}

	@Transactional
	public UUID createEnumValue(OeaPrincipal principal, UUID propertyDefId, String value, Integer sortOrder,
			Long expectedVersion) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		this.versions.check(MetamodelVersions.Element.PROPERTY_DEFINITION, propertyDefId, expectedVersion);
		PropertyDefinitionStore.StoredRow stored = stored(propertyDefId);
		readOnlyUnlessOrganization(stored.scope(), "property definition");
		if (!"enum".equals(stored.dataType())) {
			throw MetamodelChangeException.invalid("value", "Only enum properties have values.");
		}
		String stripped = (value != null) ? value.strip() : "";
		if (stripped.isEmpty() || stripped.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("value", "The value must have 1 to 100 characters.");
		}
		if (this.store.enumValueExists(propertyDefId, stripped)) {
			throw MetamodelChangeException.nameTaken("value");
		}
		// a missing or zero sort order appends the value
		int order = (sortOrder != null && sortOrder != 0) ? sortOrder
				: this.store.nextEnumOrder(propertyDefId);
		UUID id = UUID.randomUUID();
		this.store.insertEnumValue(id, propertyDefId, stripped, order);
		this.versions.increment(MetamodelVersions.Element.PROPERTY_DEFINITION, propertyDefId);
		this.audit.changed("metamodel.enum-value-created", principal, "property-definition", propertyDefId,
				Map.of("valueId", id.toString(), "value", stripped));
		return id;
	}

	/** Deletes the value; entities holding it lose it with a new version (design D5, D6). */
	public void deleteEnumValue(OeaPrincipal principal, UUID propertyDefId, UUID valueId, Long expectedVersion) {
		deleteEnumValue(principal, propertyDefId, valueId, expectedVersion, SolutionImpact.Decisions.of(Map.of()));
	}

	/**
	 * Deletes the value with the decisions for the affected active solutions (US-154).
	 *
	 * @throws SolutionDecisionRequiredException when an affected solution has no decision (409)
	 */
	@Transactional
	public void deleteEnumValue(OeaPrincipal principal, UUID propertyDefId, UUID valueId, Long expectedVersion,
			SolutionImpact.Decisions decisions) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		this.versions.check(MetamodelVersions.Element.PROPERTY_DEFINITION, propertyDefId, expectedVersion);
		PropertyDefinitionStore.StoredRow stored = stored(propertyDefId);
		readOnlyUnlessOrganization(stored.scope(), "property definition");
		String value = this.store.enumValue(valueId, propertyDefId)
			.orElseThrow(() -> new NoSuchElementException("no enum value " + valueId));
		Map<String, Object> details = new LinkedHashMap<>();
		this.impact.apply(principal, this.effects.lostEnumValue(propertyDefId, value), decisions, details,
				this.effects.now());
		List<UUID> metatypes = this.metatypes.ids();
		int removed = this.values.removeValues(propertyDefId, metatypes, value, MetamodelEffects.author(principal),
				this.effects.now());
		this.store.deleteEnumValue(valueId);
		this.versions.increment(MetamodelVersions.Element.PROPERTY_DEFINITION, propertyDefId);
		this.effects.removeOrphanLabels();
		int marked = (removed > 0) ? this.effects.markWhereMandatory(propertyDefId) : 0;
		details.put("valueId", valueId.toString());
		details.put("value", value);
		details.put("entitiesWithRemovedValues", removed);
		details.put("entitiesMarked", marked);
		this.audit.changed("metamodel.enum-value-deleted", principal, "property-definition", propertyDefId, details);
	}

	@Transactional
	public UUID createCategory(OeaPrincipal principal, CategoryInput input) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		String name = categoryName(input, null);
		UUID id = UUID.randomUUID();
		this.store.insertCategory(id, name, input.sortOrder(), input.description());
		this.audit.changed("metamodel.category-created", principal, "property-category", id, Map.of("name", name));
		return id;
	}

	@Transactional
	public void updateCategory(OeaPrincipal principal, UUID id, CategoryInput input, Long expectedVersion) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		this.versions.check(MetamodelVersions.Element.CATEGORY, id, expectedVersion);
		readOnlyUnlessOrganization(categoryScope(id), "property category");
		String name = categoryName(input, id);
		this.store.updateCategory(id, name, input.sortOrder(), input.description());
		this.versions.increment(MetamodelVersions.Element.CATEGORY, id);
		this.audit.changed("metamodel.category-updated", principal, "property-category", id, Map.of("name", name));
	}

	@Transactional
	public void deleteCategory(OeaPrincipal principal, UUID id, Long expectedVersion) {
		this.effects.lock();
		this.configurations.requireWritable(principal, PackageImporter.INSTANCE_CONFIGURATION);
		this.versions.check(MetamodelVersions.Element.CATEGORY, id, expectedVersion);
		readOnlyUnlessOrganization(categoryScope(id), "property category");
		if (this.store.categoryInUse(id)) {
			throw MetamodelChangeException.inUse("Property definitions belong to the category.", null);
		}
		this.store.deleteCategory(id);
		this.effects.removeOrphanLabels();
		this.audit.changed("metamodel.category-deleted", principal, "property-category", id, Map.of());
	}

	/** Checks the input; returns the stripped name. */
	private String validate(Input input, UUID id) {
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("name", "The name must have 1 to 100 characters.");
		}
		if (!DATA_TYPES.contains(input.dataType())) {
			throw MetamodelChangeException.invalid("dataType", "The data type is not supported.");
		}
		if (input.refMetatypeId() != null) {
			throw MetamodelChangeException.invalid("refMetatypeId", "Reference properties are not supported yet.");
		}
		if (input.maxLength() != null
				&& (!Set.of("string", "text", "url").contains(input.dataType()) || input.maxLength() < 1)) {
			throw MetamodelChangeException.invalid("maxLength", "A positive maximum length applies to string, text and url.");
		}
		if ((input.minValue() != null || input.maxValue() != null) && !"integer".equals(input.dataType())) {
			throw MetamodelChangeException.invalid("minValue", "A range applies to integer properties.");
		}
		if (input.minValue() != null && input.maxValue() != null && input.minValue().compareTo(input.maxValue()) > 0) {
			throw MetamodelChangeException.invalid("minValue", "The minimum is greater than the maximum.");
		}
		if (input.pattern() != null) {
			if (!"string".equals(input.dataType())) {
				throw MetamodelChangeException.invalid("pattern", "A pattern applies to string properties.");
			}
			try {
				Pattern.compile(input.pattern());
			}
			catch (PatternSyntaxException ex) {
				throw MetamodelChangeException.invalid("pattern", "The pattern is not a valid regular expression.");
			}
		}
		if (input.categoryId() != null && !this.store.categoryExists(input.categoryId())) {
			throw MetamodelChangeException.invalid("categoryId", "The category does not exist.");
		}
		if (this.store.nameTaken(name, id)) {
			throw MetamodelChangeException.nameTaken("name");
		}
		return name;
	}

	private String categoryName(CategoryInput input, UUID id) {
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("name", "The name must have 1 to 100 characters.");
		}
		if (this.store.categoryNameTaken(name, id)) {
			throw MetamodelChangeException.nameTaken("name");
		}
		return name;
	}

	private PropertyDefinitionStore.StoredRow stored(UUID id) {
		return this.store.stored(id).orElseThrow(() -> new NoSuchElementException("no property definition " + id));
	}

	private String categoryScope(UUID id) {
		return this.store.categoryScope(id).orElseThrow(() -> new NoSuchElementException("no property category " + id));
	}

	private static PropertyDefinitionStore.Fields fields(Input input, String name) {
		return new PropertyDefinitionStore.Fields(name, input.dataType(), input.categoryId(), input.maxLength(),
				input.minValue(), input.maxValue(), input.pattern());
	}

	static void readOnlyUnlessOrganization(String scope, String element) {
		if (!"organization".equals(scope)) {
			throw MetamodelChangeException.readOnly(element);
		}
	}

	private static void compare(List<String> changed, String field, Object before, Object after) {
		if (!Objects.equals(before, after)) {
			changed.add(field);
		}
	}

}

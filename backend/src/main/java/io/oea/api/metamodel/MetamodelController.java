package io.oea.api.metamodel;

import io.oea.api.generated.MetamodelApi;
import io.oea.api.generated.model.CreateEnumValueRequest;
import io.oea.api.generated.model.MetamodelScope;
import io.oea.api.generated.model.MetatypeDetail;
import io.oea.api.generated.model.MetatypePropertyMapping;
import io.oea.api.generated.model.MetatypePropertyMappingWrite;
import io.oea.api.generated.model.MetatypePropertyMappingWriteDefaultValue;
import io.oea.api.generated.model.MetatypeReimportPolicy;
import io.oea.api.generated.model.MetatypeSummary;
import io.oea.api.generated.model.MetatypeWrite;
import io.oea.api.generated.model.PropertyCategory;
import io.oea.api.generated.model.PropertyCategoryWrite;
import io.oea.api.generated.model.PropertyDefinitionDetail;
import io.oea.api.generated.model.PropertyDefinitionWrite;
import io.oea.api.generated.model.PropertyEnumValue;
import io.oea.api.generated.model.ReimportPolicy;
import io.oea.api.generated.model.SourcePackage;
import io.oea.app.metamodel.MetatypeCatalog;
import io.oea.app.metamodel.MetatypeEditing;
import io.oea.app.metamodel.PackageImporter;
import io.oea.app.metamodel.PropertyDefinitions;
import io.oea.app.metamodel.PropertyMappings;
import io.oea.app.metamodel.ReimportPolicies;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading and editing the metamodel (design D8 of us-033-import-metamodel, design D1 to D9 of
 * us-032-metatype-editing).
 */
@RestController
public class MetamodelController implements MetamodelApi {

	private final MetatypeCatalog catalog;

	private final MetatypeEditing metatypes;

	private final PropertyDefinitions properties;

	private final PropertyMappings mappings;

	private final ReimportPolicies policies;

	public MetamodelController(MetatypeCatalog catalog, MetatypeEditing metatypes, PropertyDefinitions properties,
			PropertyMappings mappings, ReimportPolicies policies) {
		this.catalog = catalog;
		this.metatypes = metatypes;
		this.properties = properties;
		this.mappings = mappings;
		this.policies = policies;
	}

	@Override
	public ResponseEntity<List<MetatypeDetail>> listMetatypes(UUID configId) {
		if (configId != null && !PackageImporter.INSTANCE_CONFIGURATION.equals(configId)) {
			// solution configurations follow later; only the instance configuration exists
			return ResponseEntity.ok(List.of());
		}
		return ResponseEntity.ok(this.catalog.list().stream().map(MetamodelController::metatype).toList());
	}

	@Override
	public ResponseEntity<MetatypeDetail> getMetatype(UUID id) {
		return ResponseEntity.ok(metatype(this.catalog.get(id)));
	}

	@Override
	public ResponseEntity<MetatypeDetail> createMetatype(MetatypeWrite metatypeWrite) {
		UUID id = this.metatypes.create(principal(), metatypeInput(metatypeWrite));
		return ResponseEntity.status(HttpStatus.CREATED).body(metatype(this.catalog.get(id)));
	}

	@Override
	public ResponseEntity<MetatypeDetail> updateMetatype(UUID id, MetatypeWrite metatypeWrite) {
		this.metatypes.update(principal(), id, metatypeInput(metatypeWrite));
		return ResponseEntity.ok(metatype(this.catalog.get(id)));
	}

	@Override
	public ResponseEntity<Void> deleteMetatype(UUID id) {
		this.metatypes.delete(principal(), id);
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<MetatypeDetail> updateMetatypeReimportPolicy(UUID id,
			MetatypeReimportPolicy metatypeReimportPolicy) {
		this.policies.updateMetatype(principal(), id, metatypeReimportPolicy.getOrganizationMappings().getValue());
		return ResponseEntity.ok(metatype(this.catalog.get(id)));
	}

	@Override
	public ResponseEntity<ReimportPolicy> getReimportPolicy() {
		return ResponseEntity.ok(reimportPolicy(this.policies.global()));
	}

	@Override
	public ResponseEntity<ReimportPolicy> updateReimportPolicy(ReimportPolicy reimportPolicy) {
		this.policies.updateGlobal(principal(), reimportPolicy.getOrganizationMappings().getValue());
		return ResponseEntity.ok(reimportPolicy(this.policies.global()));
	}

	@Override
	public ResponseEntity<List<MetatypePropertyMapping>> listPropertyMappings(UUID id) {
		return ResponseEntity
			.ok(this.catalog.get(id).mappings().stream().map(MetamodelController::mapping).toList());
	}

	@Override
	public ResponseEntity<MetatypePropertyMapping> createPropertyMapping(UUID id,
			MetatypePropertyMappingWrite write) {
		MetatypePropertyMappingWriteDefaultValue value = write.getDefaultValue();
		PropertyMappings.DefaultValue defaultValue = (value != null)
				? new PropertyMappings.DefaultValue(value.getValueText(),
						(value.getValueInt() != null) ? value.getValueInt().longValue() : null, value.getValueBool(),
						value.getValueDate(), value.getValueEnum())
				: null;
		PropertyMappings.Created created = this.mappings.create(principal(), id,
				new PropertyMappings.Input(write.getPropertyDefId(), write.getNecessity().getValue(),
						write.getValidFrom(), Objects.requireNonNullElse(write.getSortOrder(), 0), defaultValue));
		MetatypeCatalog.Mapping mapping = this.catalog.get(id)
			.mappings()
			.stream()
			.filter((candidate) -> candidate.id().equals(created.id()))
			.findFirst()
			.orElseThrow();
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(mapping(mapping).markedEntityCount(created.markedEntities()));
	}

	@Override
	public ResponseEntity<Void> deletePropertyMapping(UUID id, UUID mappingId) {
		this.mappings.delete(principal(), id, mappingId);
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<List<PropertyDefinitionDetail>> listPropertyDefinitions(UUID categoryId, String dataType) {
		return ResponseEntity.ok(this.catalog.properties()
			.stream()
			.filter((property) -> categoryId == null
					|| (property.category() != null && categoryId.equals(property.category().id())))
			.filter((property) -> dataType == null || dataType.equals(property.dataType()))
			.map(MetamodelController::property)
			.toList());
	}

	@Override
	public ResponseEntity<PropertyDefinitionDetail> createPropertyDefinition(PropertyDefinitionWrite write) {
		UUID id = this.properties.create(principal(), propertyInput(write));
		return ResponseEntity.status(HttpStatus.CREATED).body(property(this.catalog.property(id)));
	}

	@Override
	public ResponseEntity<PropertyDefinitionDetail> updatePropertyDefinition(UUID id, PropertyDefinitionWrite write) {
		this.properties.update(principal(), id, propertyInput(write));
		return ResponseEntity.ok(property(this.catalog.property(id)));
	}

	@Override
	public ResponseEntity<Void> deletePropertyDefinition(UUID id) {
		this.properties.delete(principal(), id);
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<List<PropertyEnumValue>> listEnumValues(UUID id) {
		return ResponseEntity.ok(this.catalog.property(id).enumValues().stream().map(MetamodelController::enumValue)
			.toList());
	}

	@Override
	public ResponseEntity<PropertyEnumValue> createEnumValue(UUID id, CreateEnumValueRequest request) {
		UUID valueId = this.properties.createEnumValue(principal(), id, request.getValue(), request.getSortOrder());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(this.catalog.property(id)
				.enumValues()
				.stream()
				.filter((value) -> value.id().equals(valueId))
				.map(MetamodelController::enumValue)
				.findFirst()
				.orElseThrow());
	}

	@Override
	public ResponseEntity<Void> deleteEnumValue(UUID id, UUID valueId) {
		this.properties.deleteEnumValue(principal(), id, valueId);
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<List<PropertyCategory>> listPropertyCategories() {
		return ResponseEntity.ok(this.catalog.categories().stream().map(MetamodelController::category).toList());
	}

	@Override
	public ResponseEntity<PropertyCategory> createPropertyCategory(PropertyCategoryWrite write) {
		UUID id = this.properties.createCategory(principal(), categoryInput(write));
		return ResponseEntity.status(HttpStatus.CREATED).body(category(id));
	}

	@Override
	public ResponseEntity<PropertyCategory> updatePropertyCategory(UUID id, PropertyCategoryWrite write) {
		this.properties.updateCategory(principal(), id, categoryInput(write));
		return ResponseEntity.ok(category(id));
	}

	@Override
	public ResponseEntity<Void> deletePropertyCategory(UUID id) {
		this.properties.deleteCategory(principal(), id);
		return ResponseEntity.noContent().build();
	}

	private PropertyCategory category(UUID id) {
		return this.catalog.categories()
			.stream()
			.filter((category) -> category.id().equals(id))
			.map(MetamodelController::category)
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no property category " + id));
	}

	private static MetatypeEditing.Input metatypeInput(MetatypeWrite write) {
		return new MetatypeEditing.Input(write.getName(), write.getAbbreviation(),
				Boolean.TRUE.equals(write.getIsConnection()), write.getArchitectureLayerId(),
				Objects.requireNonNullElse(write.getSortOrder(), 0), write.getConfigId(), write.getBaseMetatypeId());
	}

	private static PropertyDefinitions.Input propertyInput(PropertyDefinitionWrite write) {
		return new PropertyDefinitions.Input(write.getName(),
				(write.getDataType() != null) ? write.getDataType().getValue() : null, write.getCategoryId(),
				write.getRefMetatypeId(), write.getMaxLength(), decimal(write.getMinValue()),
				decimal(write.getMaxValue()), write.getPattern());
	}

	private static PropertyDefinitions.CategoryInput categoryInput(PropertyCategoryWrite write) {
		return new PropertyDefinitions.CategoryInput(write.getName(), write.getDescription(),
				Objects.requireNonNullElse(write.getSortOrder(), 0));
	}

	private static BigDecimal decimal(Number number) {
		return (number == null) ? null
				: (number instanceof BigDecimal value) ? value : new BigDecimal(number.toString());
	}

	private static ReimportPolicy reimportPolicy(String value) {
		return new ReimportPolicy().organizationMappings(ReimportPolicy.OrganizationMappingsEnum.fromValue(value));
	}

	private static MetatypeDetail metatype(MetatypeCatalog.Metatype metatype) {
		return new MetatypeDetail(metatype.id(), metatype.name(),
				(metatype.abbreviation() != null) ? metatype.abbreviation() : "")
			.isConnection(metatype.connection())
			.architectureLayer(metatype.architectureLayerId())
			.sortOrder(metatype.sortOrder())
			.scope(MetamodelScope.fromValue(metatype.scope()))
			.baseMetatypeId(metatype.baseMetatypeId())
			.reimportPolicy(MetatypeDetail.ReimportPolicyEnum.fromValue(metatype.reimportPolicy()))
			.effectiveReimportPolicy(
					MetatypeDetail.EffectiveReimportPolicyEnum.fromValue(metatype.effectiveReimportPolicy()))
			.sourcePackage(source(metatype.sourcePackageId(), metatype.sourcePackageVersion()))
			.propertyMappings(metatype.mappings().stream().map(MetamodelController::mapping).toList())
			.connectionConstraints(List.of());
	}

	private static MetatypePropertyMapping mapping(MetatypeCatalog.Mapping mapping) {
		MetatypeCatalog.MetatypeRef owner = mapping.inheritedFrom();
		return new MetatypePropertyMapping(mapping.id(), property(mapping.property()),
				MetatypePropertyMapping.NecessityEnum.fromValue(mapping.necessity()), mapping.validFrom(),
				mapping.sortOrder())
			.scope(MetamodelScope.fromValue(mapping.scope()))
			.inheritedFrom((owner != null) ? new MetatypeSummary(owner.id(), owner.name(),
					(owner.abbreviation() != null) ? owner.abbreviation() : "") : null);
	}

	private static PropertyDefinitionDetail property(MetatypeCatalog.Property property) {
		if (property == null) {
			throw new NoSuchElementException("mapped property definition missing");
		}
		PropertyCategory category = (property.category() != null) ? category(property.category()) : null;
		return new PropertyDefinitionDetail(property.id(), property.name(),
				PropertyDefinitionDetail.DataTypeEnum.fromValue(property.dataType()))
			.label(property.name())
			.category(category)
			.sqlColumnName(property.sqlColumnName())
			.maxLength(property.maxLength())
			.minValue(property.minValue())
			.maxValue(property.maxValue())
			.pattern(property.pattern())
			.scope(MetamodelScope.fromValue(property.scope()))
			.sourcePackage(source(property.sourcePackageId(), property.sourcePackageVersion()))
			.enumValues(property.enumValues().stream().map(MetamodelController::enumValue).toList());
	}

	private static PropertyCategory category(MetatypeCatalog.Category category) {
		return new PropertyCategory(category.id(), category.name(), category.sortOrder())
			.description(category.description());
	}

	private static PropertyEnumValue enumValue(MetatypeCatalog.EnumValue value) {
		return new PropertyEnumValue(value.id(), value.value(), value.sortOrder()).label(value.value());
	}

	private static SourcePackage source(String packageId, String version) {
		return (packageId != null) ? new SourcePackage(packageId, version) : null;
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}

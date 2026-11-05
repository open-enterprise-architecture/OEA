package io.oea.api.metamodel;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.MetamodelApi;
import io.oea.api.generated.model.AllowedConnectionResult;
import io.oea.api.generated.model.AllowedConnectionResultOptionsInner;
import io.oea.api.generated.model.CreateEnumValueRequest;
import io.oea.api.generated.model.MetamodelConfiguration;
import io.oea.api.generated.model.MetamodelConfigurationCreate;
import io.oea.api.generated.model.MetamodelConfigurationUpdate;
import io.oea.api.generated.model.MetamodelEditMode;
import io.oea.api.generated.model.MetamodelScope;
import io.oea.api.generated.model.MetamodelLabel;
import io.oea.api.generated.model.MetamodelLabelsUpdate;
import io.oea.api.generated.model.MetatypeDetail;
import io.oea.api.generated.model.MetatypeDetailAllOfConnectionConstraints;
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
import io.oea.app.metamodel.AllowedConnections;
import io.oea.app.metamodel.MetamodelConfigurations;
import io.oea.app.metamodel.MetamodelLabels;
import io.oea.app.metamodel.MetatypeCatalog;
import io.oea.app.metamodel.MetatypeEditing;
import io.oea.app.metamodel.PropertyDefinitions;
import io.oea.app.metamodel.PropertyMappings;
import io.oea.app.metamodel.ReimportPolicies;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.ConditionsEnforcedByHandler;
import io.oea.config.security.OeaAuthentication;
import java.math.BigDecimal;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

	private static final Pattern STRONG_VERSION = Pattern.compile("\"([1-9][0-9]{0,9})\"");

	private final MetatypeCatalog catalog;

	private final MetatypeEditing metatypes;

	private final PropertyDefinitions properties;

	private final PropertyMappings mappings;

	private final ReimportPolicies policies;

	private final MetamodelLabels labels;

	private final AllowedConnections allowedConnections;

	private final MetamodelConfigurations configurations;

	public MetamodelController(MetatypeCatalog catalog, MetatypeEditing metatypes, PropertyDefinitions properties,
			PropertyMappings mappings, ReimportPolicies policies, MetamodelLabels labels,
			AllowedConnections allowedConnections, MetamodelConfigurations configurations) {
		this.configurations = configurations;
		this.labels = labels;
		this.allowedConnections = allowedConnections;
		this.catalog = catalog;
		this.metatypes = metatypes;
		this.properties = properties;
		this.mappings = mappings;
		this.policies = policies;
	}

	@Override
	public ResponseEntity<List<MetamodelConfiguration>> listMetamodelConfigurations() {
		return ResponseEntity
			.ok(this.configurations.list().stream().map(MetamodelController::configuration).toList());
	}

	@Override
	public ResponseEntity<MetamodelConfiguration> createMetamodelConfiguration(MetamodelConfigurationCreate create) {
		UUID id = this.configurations.create(principal(), new MetamodelConfigurations.NewExtension(
				create.getSolutionId(), create.getName(),
				(create.getEditMode() != null) ? create.getEditMode().getValue() : null));
		MetamodelConfiguration created = configuration(this.configurations.get(id));
		return ResponseEntity.created(URI.create("/api/v1/metamodel/configurations/" + id))
			.eTag(etag(created.getVersion()))
			.body(created);
	}

	@Override
	@ConditionsEnforcedByHandler(value = "MetamodelConfigurations.requireWritable: the instance needs metamodel:write, an extension solution-metamodel:write",
			probeRole = "enterprise-architect", probeBody = """
					{"name": "Probe", "editMode": "import-only"}""",
			probePath = "/api/v1/metamodel/configurations/00000000-0000-0000-0000-000000000001")
	public ResponseEntity<MetamodelConfiguration> updateMetamodelConfiguration(UUID id,
			MetamodelConfigurationUpdate update, String ifMatch) {
		this.configurations.update(principal(), id, new MetamodelConfigurations.Change(update.getName(),
				(update.getEditMode() != null) ? update.getEditMode().getValue() : null), expectedVersion(ifMatch));
		MetamodelConfiguration changed = configuration(this.configurations.get(id));
		return ResponseEntity.ok().eTag(etag(changed.getVersion())).body(changed);
	}

	@Override
	public ResponseEntity<Void> deleteMetamodelConfiguration(UUID id, String ifMatch) {
		this.configurations.delete(principal(), id, expectedVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<List<MetatypeDetail>> listMetatypes(UUID configId) {
		List<MetatypeCatalog.Metatype> metatypes = (configId != null) ? this.catalog.list(configId)
				: this.catalog.list();
		return ResponseEntity.ok(metatypes.stream().map(MetamodelController::metatype).toList());
	}

	@Override
	public ResponseEntity<MetatypeDetail> getMetatype(UUID id) {
		return versioned(metatype(this.catalog.get(id)));
	}

	@Override
	@ConditionsEnforcedByHandler(value = "MetamodelConfigurations.requireWritable: the instance needs metamodel:write, an extension solution-metamodel:write",
			probeRole = "enterprise-architect", probeBody = """
					{"name": "Probe", "configId": "00000000-0000-0000-0000-000000000001"}""")
	public ResponseEntity<MetatypeDetail> createMetatype(MetatypeWrite metatypeWrite) {
		UUID id = this.metatypes.create(principal(), metatypeInput(metatypeWrite));
		return ResponseEntity.status(HttpStatus.CREATED).body(metatype(this.catalog.get(id)));
	}

	@Override
	@ConditionsEnforcedByHandler(value = "MetamodelConfigurations.requireWritable: the instance needs metamodel:write, an extension solution-metamodel:write",
			probeRole = "enterprise-architect", probeBody = """
					{"name": "Probe", "configId": "00000000-0000-0000-0000-000000000001"}""",
			probePath = "/api/v1/metamodel/metatypes/00000000-0000-0000-0000-000000000000")
	public ResponseEntity<MetatypeDetail> updateMetatype(UUID id, MetatypeWrite metatypeWrite, String ifMatch) {
		this.metatypes.update(principal(), id, metatypeInput(metatypeWrite), expectedVersion(ifMatch));
		return versioned(metatype(this.catalog.get(id)));
	}

	@Override
	@ConditionsEnforcedByHandler(value = "MetamodelConfigurations.requireWritable: the instance needs metamodel:write, an extension solution-metamodel:write",
			probeRole = "enterprise-architect", probeBody = """
					{}""",
			probePath = "/api/v1/metamodel/metatypes/00000000-0000-0000-0000-000000000000")
	public ResponseEntity<Void> deleteMetatype(UUID id, String ifMatch) {
		this.metatypes.delete(principal(), id, expectedVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<MetatypeDetail> updateMetatypeReimportPolicy(UUID id,
			MetatypeReimportPolicy metatypeReimportPolicy, String ifMatch) {
		this.policies.updateMetatype(principal(), id, metatypeReimportPolicy.getOrganizationMappings().getValue(),
				expectedVersion(ifMatch));
		return versioned(metatype(this.catalog.get(id)));
	}

	@Override
	public ResponseEntity<AllowedConnectionResult> getAllowedConnections(UUID sourceMetatypeId,
			UUID targetMetatypeId) {
		List<AllowedConnectionResultOptionsInner> options = this.allowedConnections
			.between(sourceMetatypeId, targetMetatypeId)
			.stream()
			.map((option) -> new AllowedConnectionResultOptionsInner(
					new MetatypeSummary(option.connection().id(), option.connection().name(),
							(option.connection().abbreviation() != null) ? option.connection().abbreviation() : ""),
					AllowedConnectionResultOptionsInner.DirectionEnum
						.fromValue(option.direction().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'))))
			.toList();
		return ResponseEntity.ok(new AllowedConnectionResult(!options.isEmpty(), options));
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
	public ResponseEntity<List<MetamodelLabel>> getMetamodelLabels(String locale) {
		return ResponseEntity.ok(this.labels.list(locale).stream().map(MetamodelController::label).toList());
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid labels", detail = "Some labels are invalid.")
	public ResponseEntity<List<MetamodelLabel>> updateMetamodelLabels(String locale,
			MetamodelLabelsUpdate metamodelLabelsUpdate) {
		Map<String, String> labels = (metamodelLabelsUpdate.getLabels() != null) ? metamodelLabelsUpdate.getLabels()
				: Map.of();
		return ResponseEntity
			.ok(this.labels.update(principal(), locale, labels).stream().map(MetamodelController::label).toList());
	}

	private static MetamodelLabel label(MetamodelLabels.Label label) {
		return new MetamodelLabel(label.key().toString(),
				MetamodelLabel.ElementTypeEnum.fromValue(label.key().element().prefix()), label.name(),
				label.packageText(), label.organizationText());
	}

	@Override
	public ResponseEntity<List<MetatypePropertyMapping>> listPropertyMappings(UUID id, Boolean includeHistory) {
		return ResponseEntity.ok(this.catalog.get(id, Boolean.TRUE.equals(includeHistory))
			.mappings()
			.stream()
			.map(MetamodelController::mapping)
			.toList());
	}

	@Override
	@ConditionsEnforcedByHandler(value = "MetamodelConfigurations.requireWritable: the instance needs metamodel:write, an extension solution-metamodel:write",
			probeRole = "enterprise-architect", probeBody = """
					{"propertyDefId": "00000000-0000-0000-0000-000000000000", "necessity": "optional"}""",
			probePath = "/api/v1/metamodel/metatypes/00000000-0000-0000-0000-000000000000/property-mappings")
	public ResponseEntity<MetatypePropertyMapping> createPropertyMapping(UUID id,
			MetatypePropertyMappingWrite write, String ifMatch) {
		MetatypePropertyMappingWriteDefaultValue value = write.getDefaultValue();
		PropertyMappings.DefaultValue defaultValue = (value != null)
				? new PropertyMappings.DefaultValue(value.getValueText(),
						(value.getValueInt() != null) ? value.getValueInt().longValue() : null, value.getValueBool(),
						value.getValueDate(), value.getValueEnum())
				: null;
		PropertyMappings.Created created = this.mappings.create(principal(), id,
				new PropertyMappings.Input(write.getPropertyDefId(), write.getNecessity().getValue(),
						write.getValidFrom(), Objects.requireNonNullElse(write.getSortOrder(), 0), defaultValue),
				expectedVersion(ifMatch));
		MetatypeCatalog.Mapping mapping = this.catalog.get(id, true)
			.mappings()
			.stream()
			.filter((candidate) -> candidate.id().equals(created.id()))
			.findFirst()
			.orElseThrow();
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(mapping(mapping).markedEntityCount(created.markedEntities()));
	}

	@Override
	@ConditionsEnforcedByHandler(value = "MetamodelConfigurations.requireWritable: the instance needs metamodel:write, an extension solution-metamodel:write",
			probeRole = "enterprise-architect", probeBody = """
					{}""",
			probePath = "/api/v1/metamodel/metatypes/00000000-0000-0000-0000-000000000000/property-mappings/00000000-0000-0000-0000-000000000000")
	public ResponseEntity<Void> deletePropertyMapping(UUID id, UUID mappingId, String ifMatch) {
		this.mappings.delete(principal(), id, mappingId, expectedVersion(ifMatch));
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
	public ResponseEntity<PropertyDefinitionDetail> updatePropertyDefinition(UUID id, PropertyDefinitionWrite write,
			String ifMatch) {
		this.properties.update(principal(), id, propertyInput(write), expectedVersion(ifMatch));
		PropertyDefinitionDetail property = property(this.catalog.property(id));
		return ResponseEntity.ok().eTag(etag(property.getVersion())).body(property);
	}

	@Override
	public ResponseEntity<Void> deletePropertyDefinition(UUID id, String ifMatch) {
		this.properties.delete(principal(), id, expectedVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<List<PropertyEnumValue>> listEnumValues(UUID id) {
		return ResponseEntity.ok(this.catalog.property(id).enumValues().stream().map(MetamodelController::enumValue)
			.toList());
	}

	@Override
	public ResponseEntity<PropertyEnumValue> createEnumValue(UUID id, CreateEnumValueRequest request,
			String ifMatch) {
		UUID valueId = this.properties.createEnumValue(principal(), id, request.getValue(), request.getSortOrder(),
				expectedVersion(ifMatch));
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
	public ResponseEntity<Void> deleteEnumValue(UUID id, UUID valueId, String ifMatch) {
		this.properties.deleteEnumValue(principal(), id, valueId, expectedVersion(ifMatch));
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
	public ResponseEntity<PropertyCategory> updatePropertyCategory(UUID id, PropertyCategoryWrite write,
			String ifMatch) {
		this.properties.updateCategory(principal(), id, categoryInput(write), expectedVersion(ifMatch));
		PropertyCategory category = category(id);
		return ResponseEntity.ok().eTag(etag(category.getVersion())).body(category);
	}

	@Override
	public ResponseEntity<Void> deletePropertyCategory(UUID id, String ifMatch) {
		this.properties.deleteCategory(principal(), id, expectedVersion(ifMatch));
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

	/**
	 * The version named by {@code If-Match} (design D2 of scr-030-metamodel-configuration):
	 * {@code null} without the header or for {@code *}; {@code -1}, which never matches, for a
	 * value that is not one quoted version (also a weak ETag, which If-Match never matches).
	 */
	static Long expectedVersion(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank() || "*".equals(ifMatch.strip())) {
			return null;
		}
		Matcher matcher = STRONG_VERSION.matcher(ifMatch.strip());
		if (!matcher.matches()) {
			return -1L;
		}
		try {
			return Long.parseLong(matcher.group(1));
		}
		catch (NumberFormatException ex) {
			return -1L;
		}
	}

	private static String etag(Integer version) {
		return "\"" + version + "\"";
	}

	private static ResponseEntity<MetatypeDetail> versioned(MetatypeDetail metatype) {
		return ResponseEntity.ok().eTag(etag(metatype.getVersion())).body(metatype);
	}

	private static MetatypeEditing.Input metatypeInput(MetatypeWrite write) {
		return new MetatypeEditing.Input(write.getName(), write.getAbbreviation(),
				Boolean.TRUE.equals(write.getIsConnection()), write.getArchitectureLayerId(),
				Objects.requireNonNullElse(write.getSortOrder(), 0), write.getConfigId(), write.getBaseMetatypeId(),
				write.getAllowedSourceMetatypeIds(), write.getAllowedTargetMetatypeIds());
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
			.connectionConstraints(metatype.constraints()
				.stream()
				.map((constraint) -> new MetatypeDetailAllOfConnectionConstraints()
					.direction(MetatypeDetailAllOfConnectionConstraints.DirectionEnum.fromValue(constraint.direction()))
					.allowedMetatype(new MetatypeSummary(constraint.allowed().id(), constraint.allowed().name(),
							(constraint.allowed().abbreviation() != null) ? constraint.allowed().abbreviation() : "")))
				.toList())
			.version(metatype.version())
			.configId(metatype.configId());
	}

	private static MetamodelConfiguration configuration(MetamodelConfigurations.Configuration configuration) {
		return new MetamodelConfiguration().id(configuration.id())
			.scope(MetamodelConfiguration.ScopeEnum.fromValue(configuration.scope()))
			.parentId(configuration.parentId())
			.solutionId(configuration.solutionId())
			.name(configuration.name())
			.editMode(MetamodelEditMode.fromValue(configuration.editMode()))
			.version(configuration.version())
			.lastModifiedAt(configuration.lastModifiedAt().atOffset(ZoneOffset.UTC))
			.metatypeCount(configuration.metatypeCount());
	}

	private static MetatypePropertyMapping mapping(MetatypeCatalog.Mapping mapping) {
		MetatypeCatalog.MetatypeRef owner = mapping.inheritedFrom();
		return new MetatypePropertyMapping(mapping.id(), property(mapping.property()),
				MetatypePropertyMapping.NecessityEnum.fromValue(mapping.necessity()), mapping.validFrom(),
				mapping.sortOrder())
			.scope(MetamodelScope.fromValue(mapping.scope()))
			.state(MetatypePropertyMapping.StateEnum.fromValue(mapping.state()))
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
			.enumValues(property.enumValues().stream().map(MetamodelController::enumValue).toList())
			.version(property.version());
	}

	private static PropertyCategory category(MetatypeCatalog.Category category) {
		return new PropertyCategory(category.id(), category.name(), category.sortOrder())
			.description(category.description())
			.scope(MetamodelScope.fromValue(category.scope()))
			.version(category.version());
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

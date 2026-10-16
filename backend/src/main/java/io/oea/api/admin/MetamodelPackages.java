package io.oea.api.admin;

import io.oea.api.generated.model.ImportMetamodelPackageRequest;
import io.oea.api.generated.model.ImportResult;
import io.oea.api.generated.model.ImportResultConflictListInner;
import io.oea.api.generated.model.MetatypeDetail;
import io.oea.api.generated.model.MetatypePropertyMapping;
import io.oea.api.generated.model.PropertyCategory;
import io.oea.api.generated.model.PropertyDefinitionDetail;
import io.oea.api.generated.model.PropertyEnumValue;
import io.oea.app.metamodel.PackageConflictException;
import io.oea.app.metamodel.PackageDefinition;
import io.oea.app.metamodel.PackageImporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mapping between the contract's package format (ADR-026) and the import (design D2, D3). */
final class MetamodelPackages {

	private MetamodelPackages() {
	}

	static PackageDefinition definition(ImportMetamodelPackageRequest request) {
		List<String> unsupported = new ArrayList<>();
		if (!empty(request.getViewpoints())) {
			unsupported.add("viewpoints");
		}
		if (!empty(request.getShapeAssignments())) {
			unsupported.add("shapeAssignments");
		}
		if (request.getI18nBundles() != null && !request.getI18nBundles().isEmpty()) {
			unsupported.add("i18nBundles");
		}
		return new PackageDefinition(request.getPackageId(), request.getPackageVersion(), request.getPublisher(),
				(request.getStrategy() != null) ? request.getStrategy().getValue() : null, unsupported,
				list(request.getPropertyCategories()).stream().map(MetamodelPackages::category).toList(),
				list(request.getPropertyDefinitions()).stream().map(MetamodelPackages::property).toList(),
				list(request.getMetatypes()).stream().map(MetamodelPackages::metatype).toList());
	}

	static ImportResult result(PackageImporter.Outcome outcome) {
		return new ImportResult(ImportResult.StatusEnum.COMPLETED, outcome.created(), outcome.updated(), 0)
			.batchId(outcome.batchId())
			.conflictList(List.of());
	}

	static ImportResult result(PackageConflictException conflict) {
		return new ImportResult(ImportResult.StatusEnum.FAILED, 0, 0, conflict.getConflicts().size())
			.conflictList(conflict.getConflicts()
				.stream()
				.map((c) -> new ImportResultConflictListInner(c.element(),
						ImportResultConflictListInner.ReasonEnum.fromValue(c.reason())))
				.toList());
	}

	private static PackageDefinition.Category category(PropertyCategory category) {
		return new PackageDefinition.Category(category.getId(), category.getName(), category.getDescription(),
				Objects.requireNonNullElse(category.getSortOrder(), 0));
	}

	private static PackageDefinition.Property property(PropertyDefinitionDetail property) {
		return new PackageDefinition.Property(property.getId(), property.getName(),
				(property.getDataType() != null) ? property.getDataType().getValue() : null,
				(property.getCategory() != null) ? property.getCategory().getId() : null,
				(property.getRefMetatype() != null) ? property.getRefMetatype().getId() : null, property.getMaxLength(),
				property.getMinValue(), property.getMaxValue(), property.getPattern(),
				list(property.getEnumValues()).stream().map(MetamodelPackages::enumValue).toList());
	}

	private static PackageDefinition.EnumValue enumValue(PropertyEnumValue value) {
		return new PackageDefinition.EnumValue(value.getId(), value.getValue(),
				Objects.requireNonNullElse(value.getSortOrder(), 0));
	}

	private static PackageDefinition.Metatype metatype(MetatypeDetail metatype) {
		return new PackageDefinition.Metatype(metatype.getId(), metatype.getName(), metatype.getAbbreviation(),
				Boolean.TRUE.equals(metatype.getIsConnection()), Objects.requireNonNullElse(metatype.getSortOrder(), 0),
				list(metatype.getPropertyMappings()).stream().map(MetamodelPackages::mapping).toList());
	}

	private static PackageDefinition.Mapping mapping(MetatypePropertyMapping mapping) {
		return new PackageDefinition.Mapping(mapping.getId(),
				(mapping.getPropertyDefinition() != null) ? mapping.getPropertyDefinition().getId() : null,
				(mapping.getNecessity() != null) ? mapping.getNecessity().getValue() : null, mapping.getValidFrom(),
				Objects.requireNonNullElse(mapping.getSortOrder(), 0));
	}

	private static <T> List<T> list(List<T> values) {
		return (values != null) ? values : List.of();
	}

	private static boolean empty(List<?> values) {
		return values == null || values.isEmpty();
	}

	static boolean empty(Map<?, ?> values) {
		return values == null || values.isEmpty();
	}

}

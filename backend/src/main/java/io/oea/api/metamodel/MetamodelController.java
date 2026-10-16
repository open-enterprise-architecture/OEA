package io.oea.api.metamodel;

import io.oea.api.generated.MetamodelApi;
import io.oea.api.generated.model.MetamodelScope;
import io.oea.api.generated.model.MetatypeDetail;
import io.oea.api.generated.model.MetatypePropertyMapping;
import io.oea.api.generated.model.PropertyCategory;
import io.oea.api.generated.model.PropertyDefinitionDetail;
import io.oea.api.generated.model.PropertyEnumValue;
import io.oea.api.generated.model.SourcePackage;
import io.oea.app.metamodel.MetatypeCatalog;
import io.oea.app.metamodel.PackageImporter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Reading the metatypes (design D8 of us-033-import-metamodel); further operations follow (US-032). */
@RestController
public class MetamodelController implements MetamodelApi {

	private final MetatypeCatalog catalog;

	public MetamodelController(MetatypeCatalog catalog) {
		this.catalog = catalog;
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

	private static MetatypeDetail metatype(MetatypeCatalog.Metatype metatype) {
		return new MetatypeDetail(metatype.id(), metatype.name(),
				(metatype.abbreviation() != null) ? metatype.abbreviation() : "")
			.isConnection(metatype.connection())
			.sortOrder(metatype.sortOrder())
			.scope(MetamodelScope.fromValue(metatype.scope()))
			.sourcePackage(source(metatype.sourcePackageId(), metatype.sourcePackageVersion()))
			.propertyMappings(metatype.mappings().stream().map(MetamodelController::mapping).toList())
			.connectionConstraints(List.of());
	}

	private static MetatypePropertyMapping mapping(MetatypeCatalog.Mapping mapping) {
		return new MetatypePropertyMapping(mapping.id(), property(mapping.property()),
				MetatypePropertyMapping.NecessityEnum.fromValue(mapping.necessity()), mapping.validFrom(),
				mapping.sortOrder());
	}

	private static PropertyDefinitionDetail property(MetatypeCatalog.Property property) {
		if (property == null) {
			throw new NoSuchElementException("mapped property definition missing");
		}
		PropertyCategory category = (property.category() != null)
				? new PropertyCategory(property.category().id(), property.category().name(),
						property.category().sortOrder())
					.description(property.category().description())
				: null;
		return new PropertyDefinitionDetail(property.id(), property.name(),
				PropertyDefinitionDetail.DataTypeEnum.fromValue(property.dataType()))
			.label(property.name())
			.category(category)
			.maxLength(property.maxLength())
			.minValue(property.minValue())
			.maxValue(property.maxValue())
			.pattern(property.pattern())
			.scope(MetamodelScope.fromValue(property.scope()))
			.sourcePackage(source(property.sourcePackageId(), property.sourcePackageVersion()))
			.enumValues(property.enumValues()
				.stream()
				.map((value) -> new PropertyEnumValue(value.id(), value.value(), value.sortOrder()).label(value.value()))
				.toList());
	}

	private static SourcePackage source(String packageId, String version) {
		return (packageId != null) ? new SourcePackage(packageId, version) : null;
	}

}

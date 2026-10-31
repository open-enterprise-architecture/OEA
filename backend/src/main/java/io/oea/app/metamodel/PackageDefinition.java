package io.oea.app.metamodel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A metamodel package as the import needs it (ADR-026 format, design D2 of us-033-import-metamodel).
 *
 * @param unsupportedSections non-empty package sections this version cannot import yet
 */
public record PackageDefinition(String packageId, String version, String publisher, String strategy,
		List<String> unsupportedSections, List<Category> categories, List<Property> properties,
		List<Metatype> metatypes) {

	public record Category(UUID id, String name, String description, int sortOrder) {
	}

	public record Property(UUID id, String name, String dataType, UUID categoryId, UUID refMetatypeId,
			Integer maxLength, BigDecimal minValue, BigDecimal maxValue, String pattern, List<EnumValue> enumValues) {
	}

	public record EnumValue(UUID id, String value, int sortOrder) {
	}

	/** @param baseMetatypeId base type: a metatype of the package or a built-in one, or {@code null} */
	public record Metatype(UUID id, String name, String abbreviation, boolean connection, int sortOrder,
			UUID baseMetatypeId, List<Mapping> mappings) {
	}

	public record Mapping(UUID id, UUID propertyDefinitionId, String necessity, LocalDate validFrom, int sortOrder) {
	}

}

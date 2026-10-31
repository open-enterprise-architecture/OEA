package io.oea.app.metamodel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Rules of a metamodel package beyond the contract schema (design D2, D3): element counts,
 * unique identifiers and names, references within the package, derived SQL column names and
 * unsupported content.
 */
public final class PackageValidator {

	static final int MAXIMUM_ELEMENTS = 10_000;

	private static final Pattern PACKAGE_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}$");

	private static final Set<String> DATA_TYPES = Set.of("string", "integer", "boolean", "enum", "reference", "date",
			"url", "text");

	private static final Set<String> NECESSITIES = Set.of("mandatory", "warning", "optional");

	private PackageValidator() {
	}

	public static void validate(PackageDefinition definition) {
		if (definition.strategy() != null && !"merge".equals(definition.strategy())) {
			throw unsupported("/strategy", "The strategy '" + definition.strategy() + "' is not supported yet.");
		}
		if (!definition.unsupportedSections().isEmpty()) {
			String section = definition.unsupportedSections().get(0);
			throw unsupported("/" + section, "The package section '" + section + "' is not supported yet.");
		}
		require(definition.packageId() != null && PACKAGE_ID.matcher(definition.packageId()).matches(), "/packageId",
				"The package identifier is missing or invalid.");
		require(notBlank(definition.version()), "/packageVersion", "The package version is missing.");
		require(notBlank(definition.publisher()), "/publisher", "The publisher is missing.");
		limit(definition.categories(), "/propertyCategories");
		limit(definition.properties(), "/propertyDefinitions");
		limit(definition.metatypes(), "/metatypes");

		Set<UUID> ids = new HashSet<>();
		Set<String> categoryNames = new HashSet<>();
		Set<UUID> categoryIds = new HashSet<>();
		for (int i = 0; i < definition.categories().size(); i++) {
			PackageDefinition.Category category = definition.categories().get(i);
			String pointer = "/propertyCategories/" + i;
			require(category.id() != null && ids.add(category.id()), pointer + "/id", "Missing or duplicate identifier.");
			require(notBlank(category.name()) && categoryNames.add(key(category.name())), pointer + "/name",
					"Missing or duplicate category name.");
			categoryIds.add(category.id());
		}
		Set<UUID> metatypeIds = new HashSet<>();
		definition.metatypes().forEach((metatype) -> metatypeIds.add(metatype.id()));
		Set<String> propertyNames = new HashSet<>();
		Set<String> columns = new HashSet<>();
		Set<UUID> propertyIds = new HashSet<>();
		for (int i = 0; i < definition.properties().size(); i++) {
			PackageDefinition.Property property = definition.properties().get(i);
			String pointer = "/propertyDefinitions/" + i;
			require(property.id() != null && ids.add(property.id()), pointer + "/id", "Missing or duplicate identifier.");
			require(notBlank(property.name()) && propertyNames.add(key(property.name())), pointer + "/name",
					"Missing or duplicate property name.");
			require(DATA_TYPES.contains(property.dataType()), pointer + "/dataType", "Unknown data type.");
			String column = StorageNames.derive(property.name());
			require(StorageNames.usable(column) && columns.add(column),
					pointer + "/name", "The name '" + property.name() + "' gives the SQL column name '" + column
							+ "', which is invalid, reserved or used twice.");
			require(property.categoryId() == null || categoryIds.contains(property.categoryId()),
					pointer + "/category", "The category is not part of the package.");
			require("reference".equals(property.dataType()) == (property.refMetatypeId() != null),
					pointer + "/refMetatype", "A reference property needs a metatype, other types none.");
			require(property.refMetatypeId() == null || metatypeIds.contains(property.refMetatypeId()),
					pointer + "/refMetatype", "The referenced metatype is not part of the package.");
			require("enum".equals(property.dataType()) == !property.enumValues().isEmpty(), pointer + "/enumValues",
					"An enum property needs values, other types none.");
			limit(property.enumValues(), pointer + "/enumValues");
			Set<String> values = new HashSet<>();
			for (int j = 0; j < property.enumValues().size(); j++) {
				PackageDefinition.EnumValue value = property.enumValues().get(j);
				require(value.id() != null && ids.add(value.id()) && notBlank(value.value())
						&& values.add(value.value()), pointer + "/enumValues/" + j,
						"Missing or duplicate enum value or identifier.");
			}
			propertyIds.add(property.id());
		}
		Set<String> metatypeNames = new HashSet<>();
		for (int i = 0; i < definition.metatypes().size(); i++) {
			PackageDefinition.Metatype metatype = definition.metatypes().get(i);
			String pointer = "/metatypes/" + i;
			require(metatype.id() != null && ids.add(metatype.id()), pointer + "/id", "Missing or duplicate identifier.");
			require(notBlank(metatype.name()) && metatype.name().length() <= 100 && metatypeNames.add(key(metatype.name())),
					pointer + "/name", "Missing, too long or duplicate metatype name.");
			require(metatype.abbreviation() == null || metatype.abbreviation().length() <= 10, pointer + "/abbreviation",
					"The abbreviation has more than 10 characters.");
			require(!metatype.id().equals(metatype.baseMetatypeId()), pointer + "/baseMetatypeId",
					"A metatype cannot be its own base type.");
			limit(metatype.mappings(), pointer + "/propertyMappings");
			for (int j = 0; j < metatype.mappings().size(); j++) {
				PackageDefinition.Mapping mapping = metatype.mappings().get(j);
				String mappingPointer = pointer + "/propertyMappings/" + j;
				require(mapping.id() != null && ids.add(mapping.id()), mappingPointer + "/id",
						"Missing or duplicate identifier.");
				require(propertyIds.contains(mapping.propertyDefinitionId()), mappingPointer + "/propertyDefinition",
						"The property definition is not part of the package.");
				require(NECESSITIES.contains(mapping.necessity()) && mapping.validFrom() != null, mappingPointer,
						"Necessity or validity date missing.");
			}
		}
		noCycles(definition);
	}

	/** Base types within the package form no cycle (us-032-metatype-editing D4). */
	private static void noCycles(PackageDefinition definition) {
		Map<UUID, UUID> bases = new HashMap<>();
		definition.metatypes().stream().filter((metatype) -> metatype.baseMetatypeId() != null)
			.forEach((metatype) -> bases.put(metatype.id(), metatype.baseMetatypeId()));
		for (int i = 0; i < definition.metatypes().size(); i++) {
			Set<UUID> seen = new HashSet<>();
			for (UUID type = definition.metatypes().get(i).id(); type != null; type = bases.get(type)) {
				require(seen.add(type), "/metatypes/" + i + "/baseMetatypeId", "The base types form a cycle.");
			}
		}
	}

	private static String key(String name) {
		return name.strip().toLowerCase(Locale.ROOT);
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

	private static void limit(List<?> elements, String pointer) {
		require(elements.size() <= MAXIMUM_ELEMENTS, pointer, "More than " + MAXIMUM_ELEMENTS + " elements.");
	}

	private static void require(boolean condition, String pointer, String message) {
		if (!condition) {
			throw new InvalidPackageException("invalid-package", pointer, message);
		}
	}

	private static InvalidPackageException unsupported(String pointer, String message) {
		return new InvalidPackageException("unsupported-package-content", pointer, message);
	}

}

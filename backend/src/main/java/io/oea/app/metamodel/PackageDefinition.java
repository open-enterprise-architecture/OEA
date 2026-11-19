package io.oea.app.metamodel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A metamodel package as the import needs it (ADR-026 format, design D2 of us-033-import-metamodel).
 *
 * @param unsupportedSections non-empty package sections this version cannot import yet
 * @param labels locale → label key → text of the package's own elements (us-140-dynamic-labels D5)
 * @param viewpoints the viewpoints the package installs as system-defined ones
 * @param layers the architecture layers the package installs as imported ones (s17-architecture-layers D3)
 */
public record PackageDefinition(String packageId, String version, String publisher, String strategy,
		List<String> unsupportedSections, List<Category> categories, List<Property> properties,
		List<Metatype> metatypes, Map<String, Map<String, String>> labels, List<Viewpoint> viewpoints,
		List<Layer> layers) {

	/** An architecture layer of the package, identified by its stable ID within the package. */
	public record Layer(UUID id, String name, int sortOrder) {
	}

	/**
	 * A viewpoint of the package, installed as a system-defined one (US-092; design D4 of
	 * us-091-viewpoint-notations); {@code id} is its stable identity within the package.
	 */
	public record Viewpoint(UUID id, String name, String description, String notation, List<UUID> metatypeIds,
			List<UUID> connectionTypeIds, List<ViewpointMapping> mappings, List<ViewpointSize> sizes) {
	}

	public record ViewpointMapping(UUID metatypeId, String notationElement, String visualHint) {
	}

	/** The default size in pt of an allowed metatype: width and height, or the length of a connection. */
	public record ViewpointSize(UUID metatypeId, Integer width, Integer height, Integer length) {
	}

	public record Category(UUID id, String name, String description, int sortOrder) {
	}

	public record Property(UUID id, String name, String dataType, UUID categoryId, UUID refMetatypeId,
			Integer maxLength, BigDecimal minValue, BigDecimal maxValue, String pattern, List<EnumValue> enumValues) {
	}

	public record EnumValue(UUID id, String value, int sortOrder) {
	}

	/**
	 * @param baseMetatypeId base type: a metatype of the package or a built-in one, or {@code null}
	 * @param allowedSources allowed sources of a connection metatype (us-036-connection-metatypes D2)
	 * @param allowedTargets allowed targets of a connection metatype
	 * @param composition the connection metatype of the automatic composition (US-135); at most one
	 * @param architectureLayerId the layer of the package the metatype has, or {@code null}
	 */
	public record Metatype(UUID id, String name, String abbreviation, boolean connection, int sortOrder,
			UUID baseMetatypeId, List<Mapping> mappings, List<UUID> allowedSources, List<UUID> allowedTargets,
			boolean composition, UUID architectureLayerId) {

		public Metatype(UUID id, String name, String abbreviation, boolean connection, int sortOrder,
				UUID baseMetatypeId, List<Mapping> mappings, List<UUID> allowedSources, List<UUID> allowedTargets) {
			this(id, name, abbreviation, connection, sortOrder, baseMetatypeId, mappings, allowedSources,
					allowedTargets, false, null);
		}

	}

	public record Mapping(UUID id, UUID propertyDefinitionId, String necessity, LocalDate validFrom, int sortOrder) {
	}

}

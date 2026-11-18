package io.oea.app.metamodel;

import io.oea.core.metamodel.ViewpointStore.MappingRow;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of a notation mapping (US-091 AC2, REQ-091; viewpoint BO BR-06, BR-07), shared by the
 * API and the package import: a mapped metatype is allowed by the viewpoint and mapped once, its
 * element is in the catalog, belongs to the notation or is generic, and is an element shape for
 * an element metatype and a relationship shape for a connection metatype. Messages never repeat
 * the content of the request.
 */
final class ViewpointMappings {

	private ViewpointMappings() {
	}

	/** The first rule the mappings violate, or empty if they are valid. */
	static Optional<String> problem(NotationCatalog catalog, String notation, Set<UUID> elementTypes,
			Set<UUID> connectionTypes, List<MappingRow> mappings) {
		Set<UUID> seen = new HashSet<>();
		for (MappingRow mapping : mappings) {
			boolean connection = connectionTypes.contains(mapping.metatypeId());
			if (!connection && !elementTypes.contains(mapping.metatypeId())) {
				return Optional.of("A mapping names a metatype the viewpoint does not allow.");
			}
			if (!seen.add(mapping.metatypeId())) {
				return Optional.of("A metatype is mapped more than once.");
			}
			Optional<NotationCatalog.Element> element = catalog.element(mapping.notationElement());
			if (element.isEmpty()) {
				return Optional.of("A mapping names an unknown notation element.");
			}
			if (!catalog.fits(notation, mapping.notationElement())) {
				return Optional.of("A mapping names an element that does not belong to the notation.");
			}
			if (element.get().relationship() != connection) {
				return Optional.of(connection ? "A connection metatype needs a relationship shape."
						: "An element metatype needs an element shape.");
			}
		}
		return Optional.empty();
	}

}

package io.oea.app.metamodel;

import io.oea.core.metamodel.MetamodelConfigurationStore;
import io.oea.core.metamodel.MetamodelConfigurationStore.Versioned;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Versions of metatypes, property definitions and property categories for optimistic locking
 * (design D1, D2 of scr-030-metamodel-configuration; UC-04 A5). Callers hold the metamodel lock,
 * so a check and the following write cannot interleave with another write.
 */
@Component
public class MetamodelVersions {

	/** The versioned elements with their tables. */
	public enum Element {

		METATYPE(Versioned.METATYPE, "metatype"),
		PROPERTY_DEFINITION(Versioned.PROPERTY_DEFINITION, "property definition"),
		CATEGORY(Versioned.CATEGORY, "property category"),
		ARCHITECTURE_LAYER(Versioned.ARCHITECTURE_LAYER, "architecture layer"),
		CONFIGURATION(Versioned.CONFIGURATION, "metamodel configuration");

		private final Versioned stored;

		private final String label;

		Element(Versioned stored, String label) {
			this.stored = stored;
			this.label = label;
		}

	}

	private final MetamodelConfigurationStore store;

	public MetamodelVersions(MetamodelConfigurationStore store) {
		this.store = store;
	}

	/**
	 * Checks that the element exists and, if a version is expected, that it is the current one.
	 *
	 * @param expected the version of {@code If-Match}, or {@code null} without the header
	 * @throws NoSuchElementException if the element does not exist (404)
	 * @throws MetamodelChangeException with {@code STALE_VERSION} if the version differs (412)
	 */
	public void check(Element element, UUID id, Long expected) {
		Integer current = this.store.version(element.stored, id)
			.orElseThrow(() -> new NoSuchElementException("no " + element.label + " " + id));
		if (expected != null && expected != current.longValue()) {
			throw MetamodelChangeException.staleVersion(element.label, current);
		}
	}

	/** Increments the version of the element after a change. */
	public void increment(Element element, UUID id) {
		this.store.incrementVersion(element.stored, id);
	}

}

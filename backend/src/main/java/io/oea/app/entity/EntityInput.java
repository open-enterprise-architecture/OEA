package io.oea.app.entity;

import java.util.List;
import java.util.UUID;

/**
 * An entity as written by a client (EntityWrite). {@code description} and {@code logical} are
 * {@code null} when omitted; on update, omitted fields stay unchanged (design D3).
 */
public record EntityInput(String name, UUID metatypeId, String externalId, String description, Boolean logical,
		String lifecycle, Integer parentId, List<PropertyInput> properties) {

	public EntityInput {
		properties = (properties != null) ? List.copyOf(properties) : List.of();
	}

	/** A property value serialized as text; an empty value removes it. */
	public record PropertyInput(UUID propertyDefId, String value) {
	}

}

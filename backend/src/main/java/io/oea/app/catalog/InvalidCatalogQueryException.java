package io.oea.app.catalog;

import java.util.List;

/** Invalid paging or sort parameters of a catalog query (design D1 of us-049-show-catalog-data). */
public class InvalidCatalogQueryException extends RuntimeException {

	private final List<String> invalidFields;

	InvalidCatalogQueryException(List<String> invalidFields) {
		super("Invalid parameters: " + String.join(", ", invalidFields));
		this.invalidFields = List.copyOf(invalidFields);
	}

	/** The rejected parameters: {@code page}, {@code pageSize} or {@code sort}. */
	public List<String> getInvalidFields() {
		return this.invalidFields;
	}

}

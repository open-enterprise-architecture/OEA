package io.oea.app.catalog;

/**
 * A catalog query cancelled by its time limit (design D4 of s9-catalog-query-performance),
 * answered with 503 {@code query-timeout}.
 */
public class CatalogQueryTimeoutException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CatalogQueryTimeoutException(Throwable cause) {
		super("The catalog query took longer than its time limit", cause);
	}

}

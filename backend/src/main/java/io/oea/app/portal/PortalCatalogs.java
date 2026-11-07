package io.oea.app.portal;

import io.oea.app.catalog.CatalogQueries;
import io.oea.app.catalog.Catalogs;
import io.oea.app.security.OeaPrincipal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shared catalogs of the web portal (design D1 of s9-portal-catalogs): the catalogs with
 * scope {@code instance}, and their query with the configured columns, default sort and default
 * join mode. The query is the catalog query, so columns, visibility and parameter checks are
 * the same; a personal catalog is answered as not existing, also for its creator.
 */
@Service
public class PortalCatalogs {

	private static final String INSTANCE = "instance";

	private final Catalogs catalogs;

	private final CatalogQueries queries;

	PortalCatalogs(Catalogs catalogs, CatalogQueries queries) {
		this.catalogs = catalogs;
		this.queries = queries;
	}

	/** The catalogs with scope {@code instance}, ordered by name. */
	@Transactional(readOnly = true)
	public List<Catalogs.Catalog> list() {
		return this.catalogs.listShared();
	}

	/** One page of a shared catalog; a personal or unknown catalog is answered with 404. */
	public CatalogQueries.Result query(OeaPrincipal principal, UUID id, int page, int pageSize, String sort) {
		return query(principal, id, page, pageSize, sort, null);
	}

	/**
	 * One page of a shared catalog, after the last row of the previous page with {@code cursor}
	 * (design D3 of s9-catalog-query-performance). The query runs in its own transaction with the
	 * time limit and is measured with channel {@code portal}.
	 */
	public CatalogQueries.Result query(OeaPrincipal principal, UUID id, int page, int pageSize, String sort,
			String cursor) {
		Catalogs.Catalog catalog = this.catalogs.get(principal, id);
		if (!INSTANCE.equals(catalog.scope())) {
			throw new NoSuchElementException("catalog " + id);
		}
		return this.queries.query(principal, id,
				new CatalogQueries.Request(page, pageSize, sort, Map.of(), List.of(), null, null, cursor),
				CatalogQueries.Channel.PORTAL);
	}

}

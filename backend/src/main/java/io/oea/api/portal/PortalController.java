package io.oea.api.portal;

import io.oea.api.ContractViolationResponse;
import io.oea.api.catalog.CatalogQueryMapping;
import io.oea.api.generated.PortalApi;
import io.oea.api.generated.model.CatalogQueryResult;
import io.oea.api.generated.model.CatalogSummary;
import io.oea.app.portal.PortalCatalogs;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * The shared catalogs of the web portal (SCR-050; design D2 of s9-portal-catalogs). The query
 * answers exactly like the catalog query; the other portal operations stay planned.
 */
@RestController
public class PortalController implements PortalApi {

	private final PortalCatalogs catalogs;

	public PortalController(PortalCatalogs catalogs) {
		this.catalogs = catalogs;
	}

	@Override
	public ResponseEntity<List<CatalogSummary>> listPortalCatalogs() {
		return ResponseEntity.ok(this.catalogs.list().stream().map(CatalogQueryMapping::summary).toList());
	}

	@Override
	@ContractViolationResponse(title = "Invalid query", detail = "The paging or sort parameters are invalid.")
	public ResponseEntity<CatalogQueryResult> queryPortalCatalog(UUID id, Integer page, Integer pageSize,
			String sort) {
		return ResponseEntity
			.ok(CatalogQueryMapping.result(this.catalogs.query(principal(), id, page, pageSize, sort)));
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}

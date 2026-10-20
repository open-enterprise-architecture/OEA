package io.oea.api.catalog;

import io.oea.api.generated.CatalogsApi;
import io.oea.api.generated.model.Catalog;
import io.oea.api.generated.model.CatalogCreate;
import io.oea.api.generated.model.CatalogScope;
import io.oea.api.generated.model.CatalogSummary;
import io.oea.api.generated.model.JoinMode;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.app.catalog.Catalogs;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.ConditionsEnforcedByHandler;
import io.oea.config.security.OeaAuthentication;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/** Creating, listing and reading catalogs (US-046, design D3–D5 of us-046-create-catalog). */
@RestController
public class CatalogController implements CatalogsApi {

	/** Path of the catalog collection. */
	public static final String CATALOGS_PATH = "/api/v1/catalogs";

	private final Catalogs catalogs;

	public CatalogController(Catalogs catalogs) {
		this.catalogs = catalogs;
	}

	@Override
	@ConditionsEnforcedByHandler(value = "Catalogs.create requires catalog:manage for scope=instance",
			probeRole = "viewer", probeBody = """
					{"name": "Probe", "primaryMetatypeId": "00000000-0000-0000-0000-000000000000", "scope": "instance"}""")
	public ResponseEntity<Catalog> createCatalog(CatalogCreate request) {
		Catalogs.Catalog created = this.catalogs.create(principal(),
				new Catalogs.NewCatalog(request.getName(), request.getDescription(), request.getPrimaryMetatypeId(),
						Catalogs.Scope.valueOf(request.getScope().name()),
						(request.getDefaultJoinMode() != null)
								? Catalogs.JoinMode.valueOf(request.getDefaultJoinMode().name()) : null));
		return ResponseEntity.created(URI.create(CATALOGS_PATH + "/" + created.id())).body(catalog(created));
	}

	@Override
	public ResponseEntity<List<CatalogSummary>> listCatalogs() {
		return ResponseEntity
			.ok(this.catalogs.list(principal()).stream().map(CatalogController::summary).toList());
	}

	@Override
	public ResponseEntity<Catalog> getCatalog(UUID id) {
		return ResponseEntity.ok(catalog(this.catalogs.get(principal(), id)));
	}

	private static CatalogSummary summary(Catalogs.Catalog catalog) {
		return new CatalogSummary(catalog.id(), catalog.name(), catalog.primaryMetatypeId(),
				catalog.primaryMetatypeName(), CatalogScope.fromValue(catalog.scope()), creator(catalog),
				time(catalog.createdAt()));
	}

	private static Catalog catalog(Catalogs.Catalog catalog) {
		// columns, joins, saved filters and saved views follow with US-047 and later stories
		return new Catalog(catalog.id(), catalog.name(), catalog.primaryMetatypeId(), catalog.primaryMetatypeName(),
				CatalogScope.fromValue(catalog.scope()), creator(catalog), time(catalog.createdAt()),
				catalog.description(), JoinMode.fromValue(catalog.defaultJoinMode()), time(catalog.lastModifiedAt()),
				List.of(), List.of(), List.of(), List.of());
	}

	private static PrincipalRef creator(Catalogs.Catalog catalog) {
		return new PrincipalRef(PrincipalRef.PrincipalTypeEnum.PERSON, catalog.createdByName())
			.id(Math.toIntExact(catalog.createdById()));
	}

	private static OffsetDateTime time(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}

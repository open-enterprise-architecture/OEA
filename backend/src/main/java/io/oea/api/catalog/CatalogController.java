package io.oea.api.catalog;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.CatalogsApi;
import io.oea.api.generated.model.Catalog;
import io.oea.api.generated.model.CatalogColumn;
import io.oea.api.generated.model.CatalogColumnConfig;
import io.oea.api.generated.model.CatalogColumnsUpdate;
import io.oea.api.generated.model.CatalogSystemField;
import io.oea.api.generated.model.CatalogCreate;
import io.oea.api.generated.model.CatalogQueryResult;
import io.oea.api.generated.model.CatalogRow;
import io.oea.api.generated.model.CatalogScope;
import io.oea.api.generated.model.CatalogSort;
import io.oea.api.generated.model.CatalogSummary;
import io.oea.api.generated.model.JoinMode;
import io.oea.api.generated.model.PageMeta;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.app.catalog.CatalogQueries;
import io.oea.app.catalog.Catalogs;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.ConditionsEnforcedByHandler;
import io.oea.config.security.OeaAuthentication;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creating, listing and reading catalogs (US-046, design D3–D5 of us-046-create-catalog) and the
 * catalog query (US-049, design D1–D4 of us-049-show-catalog-data).
 */
@RestController
public class CatalogController implements CatalogsApi {

	/** Path of the catalog collection. */
	public static final String CATALOGS_PATH = "/api/v1/catalogs";

	private final Catalogs catalogs;

	private final CatalogQueries queries;

	public CatalogController(Catalogs catalogs, CatalogQueries queries) {
		this.catalogs = catalogs;
		this.queries = queries;
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid catalog", detail = "Some fields are invalid.")
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
	@ContractViolationResponse(status = 422, title = "Invalid catalog", detail = "Some fields are invalid.")
	@ConditionsEnforcedByHandler(value = "Catalogs.updateColumns requires catalog:manage for scope=instance; "
			+ "without it only one's own personal catalog", probeRole = "viewer", probeBody = """
					{"columns": [{"field": "name"}]}""",
			probePath = "/api/v1/catalogs/00000000-0000-0000-0000-000000000000/columns")
	public ResponseEntity<Catalog> updateCatalogColumns(UUID id, CatalogColumnsUpdate update) {
		return ResponseEntity.ok(catalog(this.catalogs.updateColumns(principal(), id,
				update.getColumns().stream().map(CatalogController::column).toList())));
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

	@Override
	@ContractViolationResponse(title = "Invalid query", detail = "The paging or sort parameters are invalid.")
	public ResponseEntity<CatalogQueryResult> queryCatalog(UUID id, Integer page, Integer pageSize, String sort) {
		CatalogQueries.Result result = this.queries.query(principal(), id, page, pageSize, sort);
		return ResponseEntity.ok(new CatalogQueryResult(
				result.columns()
					.stream()
					.map((column) -> new CatalogColumn(column.key(), column.label(), column.sortable()))
					.toList(),
				result.rows()
					.stream()
					.map((row) -> new CatalogRow(row.entityId(), new LinkedHashMap<>(row.values())))
					.toList(),
				new PageMeta(result.page(), result.pageSize(), Math.toIntExact(result.totalElements()),
						result.totalPages()))
			.sort((result.sort() != null) ? new CatalogSort(result.sort().column(),
					result.sort().descending() ? CatalogSort.DirectionEnum.DESC : CatalogSort.DirectionEnum.ASC)
					: null));
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
				catalog.columns().stream().map(CatalogController::column).toList(), List.of(), List.of(), List.of());
	}

	private static CatalogColumnConfig column(Catalogs.Column column) {
		return new CatalogColumnConfig()
			.field((column.field() != null) ? CatalogSystemField.fromValue(column.field().key()) : null)
			.propertyDefId(column.propertyDefId())
			.displayLabel(column.displayLabel())
			.visible(column.visible())
			.sortable(column.sortable())
			.defaultSortDirection((column.defaultSortDirection() != null)
					? CatalogColumnConfig.DefaultSortDirectionEnum.fromValue(column.defaultSortDirection()) : null)
			.available(column.available());
	}

	private static Catalogs.Column column(CatalogColumnConfig config) {
		return new Catalogs.Column(
				(config.getField() != null) ? Catalogs.SystemField.ofKey(config.getField().getValue()) : null,
				config.getPropertyDefId(), config.getDisplayLabel(), !Boolean.FALSE.equals(config.getVisible()),
				!Boolean.FALSE.equals(config.getSortable()),
				(config.getDefaultSortDirection() != null) ? config.getDefaultSortDirection().getValue() : null, true);
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

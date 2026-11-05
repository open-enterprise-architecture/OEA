package io.oea.api.catalog;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.CatalogsApi;
import io.oea.api.generated.model.Catalog;
import io.oea.api.generated.model.CatalogColumn;
import io.oea.api.generated.model.CatalogColumnConfig;
import io.oea.api.generated.model.CatalogColumnsUpdate;
import io.oea.api.generated.model.CatalogSystemField;
import io.oea.api.generated.model.CatalogCreate;
import io.oea.api.generated.model.CatalogJoinDefinition;
import io.oea.api.generated.model.CatalogJoinInput;
import io.oea.api.generated.model.CatalogJoinResult;
import io.oea.api.generated.model.CatalogQueryJoin;
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
import java.util.Map;
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

	/** A join body the probe of the handler-enforced condition sends. */
	private static final String PROBE_JOIN = """
			{"name": "Probe", "connectionMetatypeId": "00000000-0000-0000-0000-000000000000",
			 "joinDirection": "outbound", "targetMetatypeId": "00000000-0000-0000-0000-000000000000",
			 "targetColumns": [{"field": "name"}]}""";

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
	@ContractViolationResponse(status = 422, title = "Invalid catalog", detail = "Some fields are invalid.")
	@ConditionsEnforcedByHandler(value = "Catalogs.addJoin requires catalog:manage for scope=instance; "
			+ "without it only one's own personal catalog", probeRole = "viewer", probeBody = PROBE_JOIN,
			probePath = "/api/v1/catalogs/00000000-0000-0000-0000-000000000000/joins")
	public ResponseEntity<Catalog> addCatalogJoin(UUID id, CatalogJoinInput input) {
		Catalogs.Catalog changed = this.catalogs.addJoin(principal(), id, join(input));
		return ResponseEntity.created(URI.create(CATALOGS_PATH + "/" + id)).body(catalog(changed));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid catalog", detail = "Some fields are invalid.")
	@ConditionsEnforcedByHandler(value = "Catalogs.updateJoin requires catalog:manage for scope=instance; "
			+ "without it only one's own personal catalog", probeRole = "viewer", probeBody = PROBE_JOIN,
			probePath = "/api/v1/catalogs/00000000-0000-0000-0000-000000000000/joins/"
					+ "00000000-0000-0000-0000-000000000000")
	public ResponseEntity<Catalog> updateCatalogJoin(UUID id, UUID joinId, CatalogJoinInput input) {
		return ResponseEntity.ok(catalog(this.catalogs.updateJoin(principal(), id, joinId, join(input))));
	}

	@Override
	@ConditionsEnforcedByHandler(value = "Catalogs.removeJoin requires catalog:manage for scope=instance; "
			+ "without it only one's own personal catalog", probeRole = "viewer", probeBody = "{}",
			probePath = "/api/v1/catalogs/00000000-0000-0000-0000-000000000000/joins/"
					+ "00000000-0000-0000-0000-000000000000")
	public ResponseEntity<Catalog> removeCatalogJoin(UUID id, UUID joinId) {
		return ResponseEntity.ok(catalog(this.catalogs.removeJoin(principal(), id, joinId)));
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
	public ResponseEntity<CatalogQueryResult> queryCatalog(UUID id, Integer page, Integer pageSize, String sort,
			Map<String, JoinMode> joinMode) {
		CatalogQueries.Result result = this.queries.query(principal(), id, page, pageSize, sort, joinModes(joinMode));
		return ResponseEntity.ok(new CatalogQueryResult(
				result.columns()
					.stream()
					.map((column) -> new CatalogColumn(column.key(), column.label(), column.sortable()))
					.toList(),
				result.rows()
					.stream()
					.map((row) -> new CatalogRow(row.entityId(), new LinkedHashMap<>(row.values()))
						.joinResults(joinResults(row.joinResults())))
					.toList(),
				new PageMeta(result.page(), result.pageSize(), Math.toIntExact(result.totalElements()),
						result.totalPages()))
			.joins(result.joins()
				.stream()
				.map((join) -> new CatalogQueryJoin(join.id(), join.name(), JoinMode.fromValue(join.mode()),
						join.columns()
							.stream()
							.map((column) -> new CatalogColumn(column.key(), column.label(), column.sortable()))
							.toList()))
				.toList())
			.sort((result.sort() != null) ? new CatalogSort(result.sort().column(),
					result.sort().descending() ? CatalogSort.DirectionEnum.DESC : CatalogSort.DirectionEnum.ASC)
					: null));
	}

	/**
	 * The join modes of the request. The generated interface binds the deep-object parameter as
	 * a map of all query parameters with text values, so only the keys {@code joinMode[...]} are
	 * taken, as text; the service checks them (design D1 of us-048-catalog-joins).
	 */
	@SuppressWarnings("unchecked")
	static Map<String, String> joinModes(Map<String, ?> parameters) {
		Map<String, String> modes = new LinkedHashMap<>();
		if (parameters != null) {
			for (Map.Entry<String, ?> entry : ((Map<String, Object>) parameters).entrySet()) {
				String key = entry.getKey();
				if (key.startsWith("joinMode[") && key.endsWith("]")) {
					modes.put(key.substring("joinMode[".length(), key.length() - 1), String.valueOf(entry.getValue()));
				}
			}
		}
		return modes;
	}

	private static Map<String, List<CatalogJoinResult>> joinResults(
			Map<UUID, List<CatalogQueries.JoinResult>> results) {
		Map<String, List<CatalogJoinResult>> json = new LinkedHashMap<>();
		results.forEach((joinId, list) -> json.put(joinId.toString(),
				list.stream()
					.map((result) -> new CatalogJoinResult(result.entityId(), new LinkedHashMap<>(result.values())))
					.toList()));
		return json;
	}

	private static Catalogs.JoinInput join(CatalogJoinInput input) {
		return new Catalogs.JoinInput(input.getName(), input.getConnectionMetatypeId(),
				(input.getJoinDirection() != null) ? input.getJoinDirection().getValue() : null,
				input.getTargetMetatypeId(),
				(input.getTargetColumns() != null)
						? input.getTargetColumns().stream().map(CatalogController::column).toList() : List.of(),
				(input.getDefaultJoinMode() != null) ? input.getDefaultJoinMode().getValue() : null);
	}

	private static CatalogJoinDefinition join(Catalogs.Join join) {
		return new CatalogJoinDefinition()
			.id(join.id())
			.name(join.name())
			.connectionMetatypeId(join.connectionMetatypeId())
			.joinDirection(CatalogJoinDefinition.JoinDirectionEnum.fromValue(join.direction()))
			.targetMetatypeId(join.targetMetatypeId())
			.targetColumns(join.targetColumns().stream().map(CatalogController::column).toList())
			.defaultJoinMode((join.defaultJoinMode() != null) ? JoinMode.fromValue(join.defaultJoinMode()) : null)
			.available(join.available());
	}

	private static CatalogSummary summary(Catalogs.Catalog catalog) {
		return new CatalogSummary(catalog.id(), catalog.name(), catalog.primaryMetatypeId(),
				catalog.primaryMetatypeName(), CatalogScope.fromValue(catalog.scope()), creator(catalog),
				time(catalog.createdAt()));
	}

	private static Catalog catalog(Catalogs.Catalog catalog) {
		// saved filters and saved views follow with US-052 and US-053
		return new Catalog(catalog.id(), catalog.name(), catalog.primaryMetatypeId(), catalog.primaryMetatypeName(),
				CatalogScope.fromValue(catalog.scope()), creator(catalog), time(catalog.createdAt()),
				catalog.description(), JoinMode.fromValue(catalog.defaultJoinMode()), time(catalog.lastModifiedAt()),
				catalog.columns().stream().map(CatalogController::column).toList(),
				catalog.joins().stream().map(CatalogController::join).toList(), List.of(), List.of());
	}

	private static CatalogColumnConfig column(Catalogs.Column column) {
		return new CatalogColumnConfig()
			.endpoint((column.endpoint() != null) ? CatalogColumnConfig.EndpointEnum.fromValue(column.endpoint().key())
					: null)
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
				(config.getDefaultSortDirection() != null) ? config.getDefaultSortDirection().getValue() : null, true,
				(config.getEndpoint() != null) ? Catalogs.Endpoint.ofKey(config.getEndpoint().getValue()) : null);
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

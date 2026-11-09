package io.oea.api.catalog;

import io.oea.api.generated.model.CatalogColumn;
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
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The response mapping of a catalog query and a catalog summary, shared by the catalog API and
 * the portal API so that both answer alike (design D2 of s9-portal-catalogs).
 */
public final class CatalogQueryMapping {

	private CatalogQueryMapping() {
	}

	/** One page of a catalog query as the contract's {@code CatalogQueryResult}. */
	public static CatalogQueryResult result(CatalogQueries.Result result) {
		return new CatalogQueryResult(result.columns().stream().map(CatalogQueryMapping::column).toList(),
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
						join.columns().stream().map(CatalogQueryMapping::column).toList()))
				.toList())
			.nextCursor(result.nextCursor())
			.sort((result.sort() != null) ? new CatalogSort(result.sort().column(),
					result.sort().descending() ? CatalogSort.DirectionEnum.DESC : CatalogSort.DirectionEnum.ASC)
					: null);
	}

	/** A catalog as the contract's {@code CatalogSummary}. */
	public static CatalogSummary summary(Catalogs.Catalog catalog) {
		return new CatalogSummary(catalog.id(), catalog.name(), catalog.primaryMetatypeId(),
				catalog.primaryMetatypeName(), CatalogScope.fromValue(catalog.scope()), creator(catalog),
				catalog.createdAt().atOffset(ZoneOffset.UTC));
	}

	static PrincipalRef creator(Catalogs.Catalog catalog) {
		return new PrincipalRef(PrincipalRef.PrincipalTypeEnum.PERSON, catalog.createdByName())
			.id(Math.toIntExact(catalog.createdById()));
	}

	private static CatalogColumn column(CatalogQueries.Column column) {
		return new CatalogColumn(column.key(), column.label(), column.sortable(),
				CatalogColumn.FilterTypeEnum.fromValue(column.filterType()))
			.readable(column.readable())
			.enumValues((column.enumValues() != null) ? List.copyOf(column.enumValues()) : null);
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

}

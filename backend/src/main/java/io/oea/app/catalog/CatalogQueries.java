package io.oea.app.catalog;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.catalog.CatalogQueryStore;
import io.oea.core.catalog.CatalogQueryStore.PropertyColumn;
import io.oea.core.catalog.CatalogQueryStore.QueryColumn;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The live query of a catalog (US-049, REQ-046; design D1, D2 and D4 of us-049-show-catalog-data):
 * resolves the columns, checks the paging and sort parameters and reads one page. The catalog is
 * read through {@link Catalogs#get}, so a foreign personal catalog is answered as not existing.
 */
@Service
public class CatalogQueries {

	/** Key of the entity name column. */
	public static final String NAME_COLUMN = "name";

	/** Name of the property shown as default column besides the name (design D2). */
	static final String STATUS_PROPERTY = "Status";

	static final int MAX_PAGE_SIZE = 200;

	private static final Pattern SORT = Pattern.compile("([a-zA-Z][a-zA-Z0-9_]*):(asc|desc)");

	private final Catalogs catalogs;

	private final CatalogQueryStore store;

	private final Clock clock;

	public CatalogQueries(Catalogs catalogs, CatalogQueryStore store, Clock clock) {
		this.catalogs = catalogs;
		this.store = store;
		this.clock = clock;
	}

	/** A column of the result; the key names the value in each row. */
	public record Column(String key, String label, boolean sortable) {
	}

	/** An entity row; {@code values} holds the text form per column key, {@code null} without value. */
	public record Row(int entityId, Map<String, String> values) {
	}

	/** The applied sort. */
	public record Sort(String column, boolean descending) {
	}

	/** One page of the catalog; {@code sort} is {@code null} for the default order. */
	public record Result(List<Column> columns, List<Row> rows, int page, int pageSize, long totalElements,
			int totalPages, Sort sort) {
	}

	/** A resolved column with the store column, its key and label, and the configured default sort. */
	private record Resolved(QueryColumn column, Column result, String defaultSortDirection) {
	}

	/**
	 * One page of the catalog. Order: visibility of the catalog (404), paging parameters, sort
	 * (400 naming the parameter). Configured columns apply when one of them can be shown (design
	 * D4 of us-047-configure-columns), else the default columns.
	 */
	@Transactional(readOnly = true)
	public Result query(OeaPrincipal principal, UUID catalogId, int page, int pageSize, String sort) {
		Catalogs.Catalog catalog = this.catalogs.get(principal, catalogId);
		List<String> invalid = new ArrayList<>();
		if (page < 0) {
			invalid.add("page");
		}
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			invalid.add("pageSize");
		}
		List<Resolved> columns = configured(catalog);
		if (columns.isEmpty()) {
			columns = defaults(catalog.primaryMetatypeId());
		}
		Sort applied = null;
		CatalogQueryStore.Sort storeSort = CatalogQueryStore.Sort.DEFAULT;
		if (sort != null && !sort.isEmpty()) {
			Matcher matcher = SORT.matcher(sort);
			Integer index = matcher.matches() ? indexOf(columns, matcher.group(1)) : null;
			if (index != null && columns.get(index).result().sortable()) {
				applied = new Sort(matcher.group(1), "desc".equals(matcher.group(2)));
				storeSort = new CatalogQueryStore.Sort(index, applied.descending());
			}
			else {
				invalid.add("sort");
			}
		}
		else {
			for (int i = 0; i < columns.size(); i++) {
				String direction = columns.get(i).defaultSortDirection();
				if (direction != null && columns.get(i).result().sortable()) {
					applied = new Sort(columns.get(i).result().key(), "desc".equals(direction));
					storeSort = new CatalogQueryStore.Sort(i, applied.descending());
					break;
				}
			}
		}
		if (!invalid.isEmpty()) {
			throw new InvalidCatalogQueryException(invalid);
		}
		List<Resolved> resolved = columns;
		long total = this.store.count(catalog.primaryMetatypeId());
		List<Row> rows = this.store
			.page(catalog.primaryMetatypeId(), resolved.stream().map(Resolved::column).toList(), storeSort,
					(long) page * pageSize, pageSize)
			.stream()
			.map((row) -> {
				Map<String, String> values = new LinkedHashMap<>();
				for (int i = 0; i < resolved.size(); i++) {
					values.put(resolved.get(i).result().key(), row.values().get(i));
				}
				return new Row(row.entityId(), Collections.unmodifiableMap(values));
			})
			.toList();
		return new Result(resolved.stream().map(Resolved::result).toList(), rows, page, pageSize, total,
				Math.toIntExact((total + pageSize - 1) / pageSize), applied);
	}

	/** The visible configured columns that can be shown, in their order. */
	private List<Resolved> configured(Catalogs.Catalog catalog) {
		if (catalog.columns().isEmpty()) {
			return List.of();
		}
		Map<UUID, PropertyColumn> available = this.store.availableProperties(catalog.primaryMetatypeId(),
				LocalDate.now(this.clock));
		List<Resolved> columns = new ArrayList<>();
		for (Catalogs.Column column : catalog.columns()) {
			if (!column.visible()) {
				continue;
			}
			if (column.field() != null) {
				columns.add(new Resolved(QueryColumn.of(storeField(column.field())),
						new Column(column.field().key(),
								(column.displayLabel() != null) ? column.displayLabel() : column.field().label(),
								column.sortable()),
						column.defaultSortDirection()));
			}
			else if (available.containsKey(column.propertyDefId())) {
				PropertyColumn property = available.get(column.propertyDefId());
				columns.add(new Resolved(QueryColumn.of(property),
						new Column(property.sqlColumnName(),
								(column.displayLabel() != null) ? column.displayLabel() : property.name(),
								column.sortable()),
						column.defaultSortDirection()));
			}
		}
		return columns;
	}

	/**
	 * The columns of a catalog without configured columns (US-049): the name and the property
	 * {@value #STATUS_PROPERTY} if the metatype maps it today with visibility {@code public}.
	 */
	private List<Resolved> defaults(UUID metatypeId) {
		List<Resolved> columns = new ArrayList<>();
		columns.add(new Resolved(QueryColumn.of(CatalogQueryStore.SystemField.NAME),
				new Column(NAME_COLUMN, "Name", true), null));
		this.store.mappedPublicProperty(metatypeId, STATUS_PROPERTY, LocalDate.now(this.clock))
			.ifPresent((property) -> columns.add(new Resolved(QueryColumn.of(property),
					new Column(property.sqlColumnName(), property.name(), true), null)));
		return columns;
	}

	private static CatalogQueryStore.SystemField storeField(Catalogs.SystemField field) {
		return CatalogQueryStore.SystemField.valueOf(field.name());
	}

	private static Integer indexOf(List<Resolved> columns, String key) {
		for (int i = 0; i < columns.size(); i++) {
			if (columns.get(i).result().key().equals(key)) {
				return i;
			}
		}
		return null;
	}

}

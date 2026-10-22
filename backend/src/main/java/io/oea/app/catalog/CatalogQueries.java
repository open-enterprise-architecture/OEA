package io.oea.app.catalog;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.catalog.CatalogQueryStore;
import io.oea.core.catalog.CatalogQueryStore.PropertyColumn;
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

	private static final Pattern SORT = Pattern.compile("([a-z][a-z0-9_]*):(asc|desc)");

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

	/**
	 * One page of the catalog. Order: visibility of the catalog (404), paging parameters, sort
	 * (400 naming the parameter).
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
		List<PropertyColumn> properties = defaultProperties(catalog.primaryMetatypeId());
		Sort applied = null;
		CatalogQueryStore.Sort storeSort = CatalogQueryStore.Sort.DEFAULT;
		if (sort != null && !sort.isEmpty()) {
			Matcher matcher = SORT.matcher(sort);
			Integer property = null;
			boolean known = matcher.matches();
			if (known && !NAME_COLUMN.equals(matcher.group(1))) {
				property = indexOf(properties, matcher.group(1));
				known = property != null;
			}
			if (known) {
				applied = new Sort(matcher.group(1), "desc".equals(matcher.group(2)));
				storeSort = new CatalogQueryStore.Sort(property, applied.descending());
			}
			else {
				invalid.add("sort");
			}
		}
		if (!invalid.isEmpty()) {
			throw new InvalidCatalogQueryException(invalid);
		}
		List<Column> columns = new ArrayList<>();
		columns.add(new Column(NAME_COLUMN, "Name", true));
		properties.forEach((property) -> columns.add(new Column(property.sqlColumnName(), property.name(), true)));
		long total = this.store.count(catalog.primaryMetatypeId());
		List<Row> rows = this.store
			.page(catalog.primaryMetatypeId(), properties, storeSort, (long) page * pageSize, pageSize)
			.stream()
			.map((row) -> {
				Map<String, String> values = new LinkedHashMap<>();
				values.put(NAME_COLUMN, row.name());
				for (int i = 0; i < properties.size(); i++) {
					values.put(properties.get(i).sqlColumnName(), row.values().get(i));
				}
				return new Row(row.entityId(), Collections.unmodifiableMap(values));
			})
			.toList();
		return new Result(List.copyOf(columns), rows, page, pageSize, total,
				Math.toIntExact((total + pageSize - 1) / pageSize), applied);
	}

	/**
	 * The columns of a catalog without configured columns (US-047 follows): the property
	 * {@value #STATUS_PROPERTY} if the metatype maps it today with visibility {@code public}.
	 */
	private List<PropertyColumn> defaultProperties(UUID metatypeId) {
		return this.store.mappedPublicProperty(metatypeId, STATUS_PROPERTY, LocalDate.now(this.clock))
			.map(List::of)
			.orElse(List.of());
	}

	private static Integer indexOf(List<PropertyColumn> properties, String key) {
		for (int i = 0; i < properties.size(); i++) {
			if (properties.get(i).sqlColumnName().equals(key)) {
				return i;
			}
		}
		return null;
	}

}

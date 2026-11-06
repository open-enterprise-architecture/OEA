package io.oea.app.catalog;

import io.oea.core.catalog.CatalogQueryStore;
import io.oea.core.catalog.CatalogQueryStore.PropertyColumn;
import io.oea.core.catalog.CatalogQueryStore.QueryColumn;
import io.oea.core.catalog.CatalogQueryStore.ValueType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The columns a catalog query returns (design D4 of us-047-configure-columns, D2 of
 * us-080-connection-catalogs) with the filter type of each column (design D2 of
 * us-051-catalog-filters). Shared by the query and by the validation of saved filters, which
 * check expressions against the same columns.
 */
@Component
class CatalogColumns {

	/** Name of the property shown as default column besides the name (design D2 of us-049-show-catalog-data). */
	static final String STATUS_PROPERTY = "Status";

	private final CatalogQueryStore store;

	private final Clock clock;

	CatalogColumns(CatalogQueryStore store, Clock clock) {
		this.store = store;
		this.clock = clock;
	}

	/** A resolved column with the store column, its result column and the configured default sort. */
	record Resolved(QueryColumn column, CatalogQueries.Column result, String defaultSortDirection) {

		ValueType type() {
			return ValueType.valueOf(this.result.filterType().toUpperCase(Locale.ROOT));
		}

	}

	/** A column a filter can name, with its join ({@code null} for a primary column). */
	record Filterable(Resolved column, UUID joinId) {
	}

	/** The primary columns: the configured ones if one of them can be shown, else the defaults. */
	List<Resolved> primary(UUID metatypeId, List<Catalogs.Column> configured) {
		List<Resolved> columns = configured(metatypeId, configured);
		return columns.isEmpty() ? defaults(metatypeId) : columns;
	}

	/**
	 * The columns filters can name (design D2 of us-051-catalog-filters): the primary columns and
	 * the columns of every available join that shows one, by key ({@code {joinId}.{key}} for a
	 * join column).
	 */
	Map<String, Filterable> filterable(UUID metatypeId, List<Catalogs.Column> configured, List<Catalogs.Join> joins) {
		Map<String, Filterable> columns = new LinkedHashMap<>();
		for (Resolved column : primary(metatypeId, configured)) {
			columns.put(column.result().key(), new Filterable(column, null));
		}
		for (Catalogs.Join join : joins) {
			if (join.available()) {
				for (Resolved column : configured(join.targetMetatypeId(), join.targetColumns())) {
					columns.put(join.id() + "." + column.result().key(), new Filterable(column, join.id()));
				}
			}
		}
		return columns;
	}

	/** The visible configured columns that can be shown for the metatype, in their order. */
	List<Resolved> configured(UUID metatypeId, List<Catalogs.Column> configured) {
		if (configured.isEmpty()) {
			return List.of();
		}
		LocalDate today = LocalDate.now(this.clock);
		Map<UUID, PropertyColumn> available = this.store.availableProperties(metatypeId, today);
		Map<Catalogs.Endpoint, Map<UUID, PropertyColumn>> offered = new EnumMap<>(Catalogs.Endpoint.class);
		List<Column> columns = new ArrayList<>();
		for (Catalogs.Column column : configured) {
			if (!column.visible()) {
				continue;
			}
			Catalogs.Endpoint side = column.endpoint();
			Map<UUID, PropertyColumn> properties = (side == null) ? available : offered.computeIfAbsent(side,
					(key) -> this.store.endpointProperties(metatypeId, key.key(), today));
			QueryColumn query;
			String key;
			String name;
			if (column.field() != null) {
				query = QueryColumn.of(storeField(column.field()));
				key = column.field().key();
				name = column.field().label();
			}
			else if (properties.containsKey(column.propertyDefId())) {
				PropertyColumn property = properties.get(column.propertyDefId());
				query = QueryColumn.of(property);
				key = property.sqlColumnName();
				name = property.name();
			}
			else {
				continue;
			}
			if (side != null) {
				query = query.at(CatalogQueryStore.Endpoint.valueOf(side.name()));
				name = endpointLabel(side, key, name);
				key = side.key() + "." + key;
			}
			columns.add(new Column(query, key, (column.displayLabel() != null) ? column.displayLabel() : name,
					column.sortable(), column.defaultSortDirection()));
		}
		return resolve(columns);
	}

	/**
	 * The columns of a catalog without configured columns (US-049): the name and the property
	 * {@value #STATUS_PROPERTY} if the metatype maps it today with visibility {@code public}; for a
	 * connection metatype also the names of source and target (US-080).
	 */
	List<Resolved> defaults(UUID metatypeId) {
		List<Column> columns = new ArrayList<>();
		boolean connection = this.store.isConnection(metatypeId);
		if (connection) {
			columns.add(endpointName(Catalogs.Endpoint.SOURCE));
		}
		columns.add(new Column(QueryColumn.of(CatalogQueryStore.SystemField.NAME), CatalogQueries.NAME_COLUMN, "Name",
				true, null));
		if (connection) {
			columns.add(endpointName(Catalogs.Endpoint.TARGET));
		}
		this.store.mappedPublicProperty(metatypeId, STATUS_PROPERTY, LocalDate.now(this.clock))
			.ifPresent((property) -> columns
				.add(new Column(QueryColumn.of(property), property.sqlColumnName(), property.name(), true, null)));
		return resolve(columns);
	}

	/** The filter type of a column (design D2 of us-051-catalog-filters). */
	static ValueType typeOf(QueryColumn column) {
		if (column.field() != null) {
			return switch (column.field()) {
				case NAME, DESCRIPTION, EXTERNAL_ID -> ValueType.TEXT;
				case CREATED_AT, UPDATED_AT -> ValueType.DATE;
				case ENTITY_VERSION -> ValueType.NUMBER;
			};
		}
		return switch (column.property().dataType()) {
			case "integer", "reference" -> ValueType.NUMBER;
			case "date" -> ValueType.DATE;
			case "enum" -> ValueType.ENUM;
			case "boolean" -> ValueType.BOOLEAN;
			default -> ValueType.TEXT;
		};
	}

	/** A column before its enum values are known. */
	private record Column(QueryColumn column, String key, String label, boolean sortable,
			String defaultSortDirection) {
	}

	/** The columns with their filter types and the values of their enum properties, read at once. */
	private List<Resolved> resolve(List<Column> columns) {
		List<UUID> enums = columns.stream()
			.filter((column) -> typeOf(column.column()) == ValueType.ENUM)
			.map((column) -> column.column().property().propertyDefId())
			.distinct()
			.toList();
		Map<UUID, List<String>> values = this.store.enumValues(enums);
		return columns.stream().map((column) -> {
			ValueType type = typeOf(column.column());
			List<String> enumValues = (type == ValueType.ENUM)
					? values.getOrDefault(column.column().property().propertyDefId(), List.of()) : null;
			return new Resolved(column.column(), new CatalogQueries.Column(column.key(), column.label(),
					column.sortable(), type.name().toLowerCase(Locale.ROOT), enumValues),
					column.defaultSortDirection());
		}).toList();
	}

	/** The name of the source or target as a default column of a connection catalog (US-080). */
	private static Column endpointName(Catalogs.Endpoint side) {
		return new Column(
				QueryColumn.of(CatalogQueryStore.SystemField.NAME).at(CatalogQueryStore.Endpoint.valueOf(side.name())),
				side.key() + "." + CatalogQueries.NAME_COLUMN, side.label(), true, null);
	}

	/** "Source" or "Target" for the name, else "Source: {attribute}" or "Target: {attribute}". */
	private static String endpointLabel(Catalogs.Endpoint side, String key, String name) {
		return CatalogQueries.NAME_COLUMN.equals(key) ? side.label() : side.label() + ": " + name;
	}

	private static CatalogQueryStore.SystemField storeField(Catalogs.SystemField field) {
		return CatalogQueryStore.SystemField.valueOf(field.name());
	}

}

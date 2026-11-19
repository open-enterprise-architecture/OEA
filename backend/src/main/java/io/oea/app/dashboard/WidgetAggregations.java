package io.oea.app.dashboard;

import io.oea.app.dashboard.Dashboards.Aggregation;
import io.oea.core.entity.EntityAggregationStore.ValueColumn;
import io.oea.core.metamodel.MetamodelConfigurationStore;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.core.metamodel.PropertyDefinitionStore;
import io.oea.core.metamodel.PropertyMappingStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Checks a property aggregation of a KPI or chart widget against the current metamodel and
 * resolves it for the query (design D2 and D5 of us-056-kpi-and-chart-widgets). The same checks
 * run when a widget is written (a failure is a 422 naming the field) and when its data is read
 * (a failure is the widget error {@code invalid-configuration}).
 */
@Component
class WidgetAggregations {

	private final MetatypeStore metatypes;

	private final PropertyDefinitionStore definitions;

	private final PropertyMappingStore mappings;

	private final Clock clock;

	WidgetAggregations(MetatypeStore metatypes, PropertyDefinitionStore definitions, PropertyMappingStore mappings,
			Clock clock) {
		this.metatypes = metatypes;
		this.definitions = definitions;
		this.mappings = mappings;
		this.clock = clock;
	}

	/** The aggregation function. */
	enum Function {

		COUNT, SUM, AVG, MIN, MAX;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Function of(String value) {
			for (Function function : values()) {
				if (function.dbValue().equals(value)) {
					return function;
				}
			}
			return null;
		}

	}

	/** The kind of a grouping. */
	enum GroupKind {

		NONE, PROPERTY, METATYPE, LAYER

	}

	/**
	 * An aggregation resolved against the metamodel: the metatypes to query (the metatype and its
	 * subtypes, or all element metatypes), the aggregated property or {@code null}, and the
	 * grouping with the property and its value column.
	 */
	record Resolved(Set<UUID> metatypeIds, UUID propertyDefId, Function function, GroupKind groupKind,
			UUID groupPropertyDefId, String groupDataType, ValueColumn groupColumn) {
	}

	/**
	 * Resolves the aggregation.
	 *
	 * @param chart whether the widget is a chart, which needs a grouping, a KPI must have none
	 * @throws InvalidDashboardException naming the rejected field
	 */
	Resolved resolve(Aggregation aggregation, boolean chart) {
		Function function = Function.of(aggregation.function());
		if (function == null) {
			throw InvalidDashboardException.invalid("function", "The function must be count, sum, avg, min or max.");
		}
		Map<UUID, UUID> bases = this.metatypes.bases();
		Map<UUID, Map<UUID, String>> active = this.mappings.active(LocalDate.now(this.clock));
		Set<UUID> metatypeIds;
		if (aggregation.metatypeId() == null) {
			if (function != Function.COUNT || aggregation.propertyDefId() != null) {
				throw InvalidDashboardException.invalid("metatypeId",
						"A metatype is required unless all element entities are counted.");
			}
			metatypeIds = elementMetatypes();
		}
		else {
			if (!isElementMetatype(aggregation.metatypeId())) {
				throw InvalidDashboardException.invalid("metatypeId", "The metatype is not an element metatype.");
			}
			metatypeIds = subtree(aggregation.metatypeId(), bases);
		}
		if (aggregation.propertyDefId() != null) {
			String type = mappedType(aggregation.metatypeId(), aggregation.propertyDefId(), bases, active);
			if (type == null) {
				throw InvalidDashboardException.invalid("propertyDefId", "The property is not mapped to the metatype.");
			}
			if (function != Function.COUNT && !"integer".equals(type)) {
				throw InvalidDashboardException.invalid("function",
						function.dbValue() + " requires an integer property");
			}
		}
		else if (function != Function.COUNT) {
			throw InvalidDashboardException.invalid("propertyDefId", function.dbValue() + " requires an integer property");
		}
		Dashboards.GroupBy groupBy = aggregation.groupBy();
		if (!chart) {
			if (groupBy != null) {
				throw InvalidDashboardException.invalid("groupBy", "A KPI widget does not group.");
			}
			return new Resolved(metatypeIds, aggregation.propertyDefId(), function, GroupKind.NONE, null, null, null);
		}
		if (groupBy == null || groupBy.kind() == null) {
			throw InvalidDashboardException.invalid("groupBy", "A chart widget needs a grouping.");
		}
		return switch (groupBy.kind()) {
			case "metatype" -> new Resolved(metatypeIds, aggregation.propertyDefId(), function, GroupKind.METATYPE,
					null, null, null);
			case "layer" -> new Resolved(metatypeIds, aggregation.propertyDefId(), function, GroupKind.LAYER, null,
					null, null);
			case "property" -> {
				String type = (groupBy.propertyDefId() != null)
						? mappedType(aggregation.metatypeId(), groupBy.propertyDefId(), bases, active) : null;
				ValueColumn column = (type != null) ? column(type) : null;
				if (column == null) {
					throw InvalidDashboardException.invalid("groupBy",
							"The grouping property must be a mapped property of type enum, string, boolean or reference.");
				}
				yield new Resolved(metatypeIds, aggregation.propertyDefId(), function, GroupKind.PROPERTY,
						groupBy.propertyDefId(), type, column);
			}
			default -> throw InvalidDashboardException.invalid("groupBy",
					"The grouping must be a property, the metatype or the layer.");
		};
	}

	private static ValueColumn column(String dataType) {
		return switch (dataType) {
			case "enum" -> ValueColumn.ENUM;
			case "string" -> ValueColumn.TEXT;
			case "boolean" -> ValueColumn.BOOL;
			case "reference" -> ValueColumn.REF;
			default -> null;
		};
	}

	/** The data type of the property if it is mapped now to the metatype or a base type, else {@code null}. */
	private String mappedType(UUID metatypeId, UUID propertyDefId, Map<UUID, UUID> bases,
			Map<UUID, Map<UUID, String>> active) {
		if (metatypeId == null) {
			return null;
		}
		Set<UUID> chain = new LinkedHashSet<>();
		for (UUID type = metatypeId; type != null && chain.add(type); type = bases.get(type)) {
			if (active.getOrDefault(type, Map.of()).containsKey(propertyDefId)) {
				return this.definitions.stored(propertyDefId).map(PropertyDefinitionStore.StoredRow::dataType).orElse(null);
			}
		}
		return null;
	}

	private boolean isElementMetatype(UUID id) {
		return this.metatypes.stored(id)
			.filter((row) -> !row.connection() && MetamodelConfigurationStore.INSTANCE.equals(row.configId()))
			.isPresent();
	}

	/** The element metatypes of the instance metamodel. */
	private Set<UUID> elementMetatypes() {
		Set<UUID> ids = new HashSet<>();
		for (MetatypeStore.MetatypeRow row : this.metatypes.inConfigurations(MetamodelConfigurationStore.INSTANCE,
				MetamodelConfigurationStore.INSTANCE)) {
			if (!row.connection()) {
				ids.add(row.id());
			}
		}
		return ids;
	}

	/** The metatype and all its subtypes. */
	static Set<UUID> subtree(UUID id, Map<UUID, UUID> bases) {
		Set<UUID> subtree = new HashSet<>(List.of(id));
		boolean grown = true;
		while (grown) {
			grown = false;
			for (Map.Entry<UUID, UUID> base : bases.entrySet()) {
				if (subtree.contains(base.getValue()) && subtree.add(base.getKey())) {
					grown = true;
				}
			}
		}
		return subtree;
	}

}

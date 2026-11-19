package io.oea.api.dashboard;

import io.oea.api.catalog.CatalogQueryMapping;
import io.oea.api.generated.model.DashboardConfig;
import io.oea.api.generated.model.DashboardOwner;
import io.oea.api.generated.model.DashboardScope;
import io.oea.api.generated.model.DashboardSummary;
import io.oea.api.generated.model.GridPosition;
import io.oea.api.generated.model.WidgetAggregation;
import io.oea.api.generated.model.WidgetConfig;
import io.oea.api.generated.model.WidgetData;
import io.oea.api.generated.model.WidgetDataError;
import io.oea.api.generated.model.WidgetGroupBy;
import io.oea.api.generated.model.WidgetSeriesEntry;
import io.oea.api.generated.model.WidgetSettings;
import io.oea.api.generated.model.WidgetType;
import io.oea.app.dashboard.DashboardData;
import io.oea.app.dashboard.Dashboards;
import io.oea.app.entity.EntityBlocks;
import java.time.ZoneOffset;
import java.util.Locale;

/** Maps dashboards and widget data to the contract model, for the work area and the portal. */
public final class DashboardMapping {

	private DashboardMapping() {
	}

	public static DashboardSummary summary(Dashboards.Summary summary) {
		return new DashboardSummary(summary.id(), summary.name(), summary.description(),
				DashboardScope.fromValue(summary.scope()), owner(summary.owner()), summary.widgetCount(),
				summary.canEdit());
	}

	public static WidgetData widgetData(DashboardData.WidgetResult result, EntityBlocks blocks) {
		WidgetData widget = new WidgetData(result.widgetId(), type(result.type()),
				WidgetData.StatusEnum.fromValue(result.status().name().toLowerCase(Locale.ROOT)));
		if (result.error() != null) {
			widget.error(new WidgetDataError(WidgetDataError.CodeEnum.fromValue(result.error().code()),
					result.error().message()));
		}
		if (result.table() != null) {
			widget.table(CatalogQueryMapping.result(result.table(), blocks));
		}
		widget.value(result.value()).target(result.target()).previousValue(result.previousValue());
		widget.previousAt((result.previousAt() != null) ? result.previousAt().atOffset(ZoneOffset.UTC) : null);
		widget.series((result.series() != null) ? result.series()
			.stream()
			.map((entry) -> new WidgetSeriesEntry(WidgetSeriesEntry.KindEnum.fromValue(entry.kind()), entry.value())
				.key(entry.key())
				.label(entry.label())
				.labelKey(entry.labelKey()))
			.toList() : null);
		return widget;
	}


	public static DashboardConfig config(Dashboards.Dashboard dashboard) {
		return new DashboardConfig(dashboard.id(), dashboard.name(), dashboard.description(),
				DashboardScope.fromValue(dashboard.scope()), owner(dashboard.owner()), dashboard.version(),
				dashboard.canEdit(),
				dashboard.widgets()
					.stream()
					.map((widget) -> new WidgetConfig(widget.id(), type(widget.type()), widget.title(),
							new GridPosition(widget.position().col(), widget.position().row(),
									widget.position().width(), widget.position().height()),
							settings(widget.config())))
					.toList());
	}

	static WidgetSettings settings(Dashboards.Settings config) {
		WidgetSettings settings = new WidgetSettings().content(config.content())
			.catalogId(config.catalogId())
			.savedViewId(config.savedViewId())
			.pageSize(config.pageSize());
		if (config.aggregation() == null) {
			return settings;
		}
		Dashboards.Aggregation aggregation = config.aggregation();
		WidgetAggregation out = new WidgetAggregation(
				WidgetAggregation.FunctionEnum.fromValue(aggregation.function()))
			.metatypeId(aggregation.metatypeId())
			.propertyDefId(aggregation.propertyDefId());
		if (aggregation.groupBy() != null) {
			out.groupBy(new WidgetGroupBy(WidgetGroupBy.KindEnum.fromValue(aggregation.groupBy().kind()))
				.propertyDefId(aggregation.groupBy().propertyDefId()));
		}
		return settings.dataSource(WidgetSettings.DataSourceEnum.PROPERTY_AGGREGATION)
			.aggregation(out)
			.unit(config.unit())
			.comparison((config.comparison() != null) ? WidgetSettings.ComparisonEnum.fromValue(config.comparison())
					: null)
			.targetValue(config.targetValue())
			.chartType((config.chartType() != null) ? WidgetSettings.ChartTypeEnum.fromValue(config.chartType())
					: null)
			.showLegend(config.showLegend())
			.showDataLabels(config.showDataLabels());
	}

	static WidgetType type(Dashboards.WidgetType type) {
		return WidgetType.fromValue(type.name().toLowerCase(Locale.ROOT));
	}

	static DashboardOwner owner(Dashboards.Owner owner) {
		return new DashboardOwner((int) owner.id(), owner.displayName());
	}

}

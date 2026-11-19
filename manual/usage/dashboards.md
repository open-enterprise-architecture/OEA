---
title: Dashboards
covers:
  screens: [SCR-024, SCR-056]
  stories: [US-055, US-056, US-057, US-058, US-059, US-060, US-062]
---

# Dashboards

A dashboard gives readers a structured view of the architecture. It arranges **widgets** in a grid of 12 columns: text widgets with formatted notes, table widgets that show the rows of a [catalog](./catalogs), KPI widgets with one figure, and chart widgets with distributions. You need the permission `entity:read` (for example the role `viewer`) to see dashboards and to create personal ones; shared dashboards need `dashboard:manage` (role `enterprise-architect`).

There are two kinds of dashboards:

- **Shared** dashboards are visible to every reader of the instance. Every person with `dashboard:manage` can change and delete every shared dashboard.
- **Personal** dashboards are visible to you only. Nobody else sees them in a list, and their address answers *The dashboard does not exist or you may not see it*. The exception are administrators with the permission `settings:manage`: they see the personal dashboards of other persons in the list, marked with the owner, and can read them, but never change or delete them.

Service accounts have no access to dashboards.

## The page Dashboards

Choose *Dashboards* in the header. The page lists the dashboards you may see with their visibility (*Shared* or *Personal*), the owner and the number of widgets. Choose a dashboard to open it.

## Create a dashboard

1. Choose *Create dashboard* on the page *Dashboards*.
2. Fill in the dialog:

   | Field | Meaning |
   |---|---|
   | Name | required, 1 to 200 characters; unique among the shared dashboards and among your personal dashboards, regardless of upper and lower case |
   | Description | optional, up to 1000 characters |
   | Visibility | *Shared*: visible to every reader (needs the permission to manage dashboards); *Personal*: visible to you only |

3. Choose *Create dashboard*. The new dashboard opens with the notice "No widgets yet. Add widget."

If the name is already taken, the dialog says so. Without the permission to manage dashboards, *Shared* is not selectable.

## Read a dashboard

A dashboard shows its widgets at their positions. The data is computed each time you open it, with your own permissions: a table widget shows the catalog as you may see it, including locked columns (see [Property visibility](../administration/property-visibility)). Readers who work in the [portal](./portal#dashboards) see the same dashboards there, read-only.

- A **text widget** shows its Markdown as formatted text.
- A **table widget** shows the rows of its catalog, with the columns of its saved view if it has one. Below the table, *Page 1 of 5 · 47 entries* and the pagination move between pages.
- A **KPI widget** shows one aggregated value with its unit, for example *42,000,000 CHF*. With a target value it also shows the target, the difference and a trend as icon and text (*8,000,000 CHF below the target*). Without a value (for example, no entity has a value of the property) it shows *– (no data)*; counting nothing gives 0. With the comparison *Compared with 30 days ago* it also shows *30 days ago (Oct 20, 2026): 35,000,000 CHF* (the date of the value is part of the text), the signed difference with its percentage and a trend as icon and text (*Increase: +7,000,000 CHF (+20%)*, *Decrease: …* or *Unchanged*); the percentage is left out when the value 30 days ago was 0. If no entity had a value then, the KPI shows *No comparison value*; a count compares with 0.
- A **chart widget** shows one aggregation as bars, lines, areas, pie or donut segments, or a treemap, with legend and data labels if configured. Hover over or focus a group for its value. Names are shown in your language; entities without a value form the group *(none)*, and only the 50 largest groups are shown, the rest is summed as *Other*. A table with the same figures is available for screen readers.
- A KPI or chart widget that aggregates a property whose values you may not read for every entity (see [Property visibility](../administration/property-visibility)) shows *The values of this property are not readable for every entity, so no aggregate is shown.* Roles that may override the visibility do not lift this. Counting entities and grouping by element type or layer are always possible.
- A KPI or chart widget whose element type or property was removed from the metamodel since then shows *The widget no longer fits the metamodel. Edit or remove it.* The other widgets stay unaffected, and no widget is removed automatically. A deleted architecture layer moves its element types into the group *(none)*.
- A widget whose catalog is gone or not visible to you shows *Catalog not found*; a widget whose saved view is gone shows *Saved view not found*. The other widgets stay unaffected.
- Widgets that overlap are marked with a red border and the note *Overlaps another widget*.

## Change a dashboard

If you may change a dashboard, the page has the button *Edit*. In the edit mode you can:

- **Add widget**: choose the type *Text*, *Table*, *KPI* or *Chart*, enter a title of 1 to 150 characters and configure it (see below). New widgets go below the existing ones and can then be arranged. A dashboard has at most 50 widgets.
- **Edit** or **Remove** a widget with the buttons in its header. The type of a widget cannot change; remove it and add a new one.
- **Rename** the dashboard, change its description, or **Share with everyone**: a personal dashboard becomes shared, if you hold `dashboard:manage`, the name is free among the shared dashboards, and no widget shows a personal catalog. A shared dashboard does not become personal again.
- **Delete** the dashboard with all its widgets, after a confirmation.

Choose *Done* to leave the edit mode.

### Text widgets

A text widget holds Markdown of up to 10000 characters, for example methodological notes: bold and italic text, lists, quotes, headings, tables and links. Links work with `http`, `https` and `mailto` only. Raw HTML is not interpreted; it appears as text. Scripts, event handlers and other schemes such as `javascript:` never run.

### Table widgets

A table widget needs a catalog you may see. On a shared dashboard only shared catalogs can be chosen, so that every reader sees data; a personal dashboard can also show your personal catalogs. Optionally choose a saved view of that catalog for its columns, and the rows per page (5 to 100, default 10). The widget always reads current data through the catalog query, as the reader.

### KPI and chart widgets

Both aggregate the entities of an **element type**, subtypes included; deleted entities and the changes of solutions are not counted. Choose:

| Field | Meaning |
|---|---|
| Element type | the element type to aggregate, or *All element types* for a count of every entity |
| Function | *Count*, *Sum*, *Average*, *Minimum* or *Maximum*. Sum, average, minimum and maximum need a property of the type number (integer); a count takes the entities themselves or any mapped property and counts the entities that have a value of it |
| Property | the property to aggregate; only properties mapped to the element type are offered |
| Unit | optional, up to 20 characters, shown after the value |

For a **KPI widget**, choose the comparison *With a target value* and enter the target to see the difference and the trend; or choose *Compared with 30 days ago* to compare with the value the same aggregation had 30 days before (exactly 30 times 24 hours). That value is computed from the history of the entities: the entities that existed then and were not deleted yet, each with the value of the last version up to then. It uses the current metamodel, so a change of an element type since then (for example its base type) is not reconstructed, and the comparison is approximate in that case. It follows the same visibility rules as the current value: for a property you may not read for every entity, neither value is shown. Charts have no comparison.

For a **chart widget**, choose *Group by*: a property (of the type text, selection, yes/no or reference; a reference groups by the name of the referenced entity), the element type, or the architecture layer. Entities of a metatype without a layer form the group *(none)*. Choose the chart type (*Bar*, *Line*, *Area*, *Pie*, *Donut* or *Treemap*) and whether to show the legend and the data labels. A chart shows one series.

A new widget starts with all entities counted, and a chart grouped by element type. Changing the element type removes a property or grouping that it does not have. Grouping by the name of the entities is not offered.

## Arrange the widgets

The grid has 12 columns. A widget has a column, a width, a row and a height:

| Property | Allowed values |
|---|---|
| Column | 1 to 12 |
| Width | 1 to 12; column + width is at most 13 |
| Row | 1 to 500 |
| Height | 1 to 6 rows |

In the edit mode:

- **Drag** a widget by the handle at the left of its title to move it, and drag the corner at the bottom right to resize it.
- Or use the **arrangement buttons** below a widget (move left, right, up, down; one column narrower or wider; one row shorter or taller), or the **arrow keys** on the handle: arrow keys move the widget, with Shift they resize it.

Every change is saved at once, as one request. If a widget would reach beyond column 12, the page says *Widget exceeds the grid width* and does not save the position. Overlaps are allowed; the editor marks them.

## Changes by several people

Every change refers to the version of the dashboard you have loaded. If someone else changed the dashboard in the meantime, the page reloads it and says so; repeat your change.

## API

The dashboards are available through `/dashboards` and `/views/dashboards` (see the [API reference](../api/)):

- `GET /dashboards` and `POST /dashboards` list and create dashboards; `PUT` and `DELETE /dashboards/{id}` change and delete one.
- `POST /dashboards/{id}/widgets`, `PUT` and `DELETE /dashboards/{id}/widgets/{widgetId}` change widgets (a KPI or chart widget carries the property `aggregation` with `metatypeId`, `propertyDefId`, `function` and, for a chart, `groupBy`), and `PUT /dashboards/{id}/layout` positions up to 50 widgets in one request. Every write needs the header `If-Match` with the version that was read (`"3"`); without it the answer is 428, with another version 412. The answers carry the new version as `ETag`.
- `GET /views/dashboards/{id}` returns the configuration, `GET /views/dashboards/{id}/data` the data of all widgets, and `GET /views/dashboards/{id}/widgets/{widgetId}/data?page=` another page of a table widget (from 0). The data of a KPI widget has `value` (null without data), `target` and, with the comparison `vs-previous-value`, `previousValue` (null if no entity had a value then) and `previousAt` (the point in time, 30 days before the request), that of a chart widget `series` with `kind`, `key`, `label`, `labelKey` and `value` per group; a widget that cannot be computed has the status `error` with the code `restricted` or `invalid-configuration`.
- A taken name answers 422 with the problem type `urn:oea:problem:dashboard-name-taken`; other invalid input answers 422 with `invalidFields`. The personal dashboard of another person answers 404.

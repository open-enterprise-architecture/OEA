# dashboard/widgets Specification

## Purpose

Defines the widgets of a dashboard: the text and table widgets with their limits, the computation of their data with the reader's permissions, and the 12-column grid layout with its editor.

## Requirements

### Requirement: Text widget

A text widget SHALL hold Markdown of 0 to 10 000 characters and no data source. Its data SHALL have `status=static`. The web app SHALL render it as sanitized HTML: no scripts, event handlers or unknown tags, and links only with `http`, `https` or `mailto`.

Trace: US-059 AC1–AC4, REQ-052, REQ-055

#### Scenario: Methodological notes

- **WHEN** Kurt adds the text widget "Methodological Notes" with bold text, a list and a link
- **THEN** it is saved, its data has `status=static`, and the dashboard shows the formatted text, not the Markdown source

#### Scenario: Empty and maximal content

- **WHEN** Kurt adds text widgets with empty content, with 10 000 and with 10 001 characters
- **THEN** the first two are added, and the third is rejected with 422 naming `content`

#### Scenario: Script in the content

- **GIVEN** a text widget with the content `<script>alert(1)</script>`, `<img src=x onerror=alert(1)>` and `[x](javascript:alert(1))`
- **WHEN** Franz opens the dashboard
- **THEN** no script runs, and the link is not clickable

### Requirement: Table widget

A table widget SHALL show a catalog visible to its writer (403 otherwise), on a shared dashboard a shared one (422 otherwise), optionally through a saved view of that catalog (422 otherwise), with 5 to 100 rows per page (default 10). A data source other than a catalog query SHALL give 422 "Table widget requires CatalogQuery DataSource".

Trace: US-058 AC1, AC2, AC3, AC5, REQ-054

#### Scenario: Catalog with a saved view

- **GIVEN** the shared catalog "Application Inventory" with the saved view "Compact view" and 47 entities
- **WHEN** Kurt adds the table widget "Application Overview" with that catalog, the view and 10 rows per page
- **THEN** its data shows the first 10 rows with the columns of "Compact view" and the total 47

#### Scenario: Without a saved view

- **WHEN** Kurt adds a table widget for "Application Inventory" without a view
- **THEN** its data uses the default state of the catalog

#### Scenario: Another person's personal catalog

- **WHEN** Kurt adds a table widget for Franz's personal catalog
- **THEN** the request is rejected with 403, and no widget is saved

#### Scenario: Own personal catalog on a shared dashboard

- **WHEN** Kurt adds a table widget for his own personal catalog to a shared dashboard
- **THEN** the request is rejected with 422 naming `catalogId`

#### Scenario: Page size at the limits

- **WHEN** Kurt adds table widgets with 5, 100, 4 and 101 rows per page
- **THEN** 5 and 100 are accepted, and 4 and 101 are rejected with 422 naming `pageSize`

#### Scenario: Wrong data source

- **WHEN** Kurt adds a table widget with a property aggregation as data source
- **THEN** the request is rejected with 422 "Table widget requires CatalogQuery DataSource"

### Requirement: Dashboard data

The data of a dashboard SHALL be computed on each request from the current repository, with the reader's permissions and visibility. A widget whose catalog or view no longer exists or is not visible SHALL get an error status; the other widgets SHALL be computed normally. Further pages of a table widget SHALL be read per widget.

Trace: US-058 AC4, REQ-055, REQ-056

#### Scenario: Deleted catalog

- **GIVEN** a dashboard with a text widget and a table widget for the catalog "Inventory"
- **WHEN** "Inventory" is deleted and Kurt opens the dashboard
- **THEN** the table widget shows "Catalog not found", and the text widget is shown normally

#### Scenario: Second page of a table

- **GIVEN** the table widget "Application Overview" over 47 entities with 10 rows per page
- **WHEN** Franz chooses page 2
- **THEN** the widget shows entities 11 to 20 and "page 2 of 5"

#### Scenario: Restricted values

- **GIVEN** a column of "Application Inventory" that Franz may not read
- **WHEN** Franz opens the dashboard
- **THEN** the table widget shows that column redacted, as the catalog does

### Requirement: Grid layout

Each widget SHALL have a position in the 12-column grid: column 1 to 12, width 1 to 12 with column + width at most 13, row 1 to 500, height 1 to 6. Several positions SHALL be changeable in one request. Overlaps SHALL be accepted by the server and marked in the editor.

Trace: US-060 AC1–AC4, REQ-057

#### Scenario: Position two widgets

- **WHEN** Kurt places a widget at column 1, row 1, width 3, height 1 and another at column 4, row 1, width 9, height 3
- **THEN** both positions are saved and are the same after reloading

#### Scenario: Width at the grid border

- **WHEN** Kurt places a widget at column 5 with width 8, and then with width 9
- **THEN** width 8 is accepted, and width 9 is rejected with "Widget exceeds the grid width", naming `width`

#### Scenario: Height at the limits

- **WHEN** Kurt places a widget with height 6, and then with height 7
- **THEN** height 6 is accepted, and height 7 is rejected with 422 naming `height`

#### Scenario: Overlap

- **WHEN** Kurt places two widgets so that they overlap
- **THEN** both positions are saved, and the editor marks both widgets

#### Scenario: Many rows

- **WHEN** Kurt places widgets on rows 1 to 8
- **THEN** the dashboard scrolls vertically and shows all of them

### Requirement: Arrange widgets in the editor

In edit mode, the dashboard page SHALL let widgets be moved and resized by drag and drop, and by keyboard controls for move and resize. A change SHALL be saved as one layout request. The view mode SHALL show the widgets at their positions without drag and drop.

Trace: US-060 AC1, AC5, REQ-057

#### Scenario: Move a widget by keyboard

- **WHEN** Kurt selects a widget in edit mode and moves it one column to the right with the keyboard
- **THEN** its new position is saved

#### Scenario: Width beyond the grid in the editor

- **WHEN** Kurt drags a widget so that it would exceed column 12
- **THEN** the editor shows "Widget exceeds the grid width", and the position is not saved

### Requirement: Widgets and their types

Those who may change a dashboard SHALL add, configure and remove widgets with a title of 1 to 150 characters and a grid position, up to 50 per dashboard. The types `text`, `table`, `kpi` and `chart` SHALL be accepted, and the type SHALL NOT change after creation.

Trace: REQ-052, US-056, US-057, US-058, US-059

#### Scenario: Type cannot change

- **WHEN** Kurt changes a text widget into a table widget
- **THEN** the request is rejected with 422 naming `type`

#### Scenario: Widgets at the limit

- **GIVEN** a dashboard with 49 widgets
- **WHEN** Kurt adds a widget, and then another one
- **THEN** the 50th is added, and the 51st is rejected with 422

#### Scenario: KPI and chart accepted

- **WHEN** Kurt adds a widget of type `kpi` and one of type `chart` with a valid property aggregation
- **THEN** both are added

### Requirement: Property aggregation

A KPI or chart widget SHALL aggregate the entities of an element metatype and its subtypes that are not deleted: `count` of the entities, or `count`, `sum`, `avg`, `min` or `max` of a mapped property; all but `count` SHALL need an integer property (422). Without a metatype, `count` SHALL count all element entities.

Trace: US-056 AC1–AC3, US-057, REQ-053 BR-01–BR-03

#### Scenario: Sum of a property

- **GIVEN** three application components with the integer property "Investment" set to 5 000 000, 12 000 000 and 25 000 000, and a fourth one that is deleted
- **WHEN** Kurt adds the KPI widget "Total Investment" with the sum of "Investment" and the unit "CHF"
- **THEN** its data has the value 42 000 000 with the unit "CHF"

#### Scenario: Count

- **GIVEN** 15 application components, one of a subtype
- **WHEN** Kurt adds a KPI widget counting the application components
- **THEN** its value is 15

#### Scenario: Sum of a text property

- **WHEN** Kurt adds a KPI widget with the sum of the text property "Name"
- **THEN** the request is rejected with 422 "sum requires an integer property"

#### Scenario: Unknown or unmapped property

- **WHEN** Kurt adds a KPI widget with a property that is not mapped to the metatype
- **THEN** the request is rejected with 422 naming `propertyDefId`

### Requirement: KPI widget

A KPI widget SHALL show the aggregated value with an optional unit of at most 20 characters, and compare it with `none`, `vs-target` or `vs-previous-value`; `vs-target` SHALL need `targetValue`. Without data it SHALL show "– (no data)" without an error.

Trace: US-056 AC1, AC4, AC5, owner decision 2026-11-18

#### Scenario: Target value missing

- **WHEN** Kurt sets the comparison `vs-target` without a target value
- **THEN** the request is rejected with 422 naming `targetValue`

#### Scenario: Below the target

- **GIVEN** a KPI widget with the value 42 and the target 50
- **WHEN** Franz opens the dashboard
- **THEN** the widget shows 42, the target 50 and a downward trend

#### Scenario: No data

- **GIVEN** no application component has a value for "Investment"
- **WHEN** Franz opens the dashboard with the sum of "Investment"
- **THEN** the widget shows "– (no data)" and no error

#### Scenario: Unit at the limit

- **WHEN** Kurt sets a unit of 20 characters, and then of 21
- **THEN** 20 is accepted, and 21 is rejected with 422 naming `unit`

### Requirement: Chart widget

A chart widget SHALL show one aggregation grouped by a property of type enum, string, boolean or reference, by metatype or by layer, as bar, line, area, pie, donut or treemap, with legend and data labels switchable. Labels SHALL be translated names, never IDs. At most 50 groups SHALL be shown; the rest SHALL form "Other", entities without a value "(none)".

Trace: US-057 AC1, AC2, AC4–AC6, REQ-053 BR-04, owner decision 2026-11-18

#### Scenario: Investment per plateau as bars

- **GIVEN** application components with "Investment" and the enum property "Plateau" with the values P1, P2 and P3
- **WHEN** Kurt adds a bar chart with the sum of "Investment" grouped by "Plateau"
- **THEN** its data has three groups with the labels of the enum values and the sums

#### Scenario: Entities per layer as pie

- **GIVEN** 50 entities whose metatypes have the layers Business, Application and Technology
- **WHEN** Kurt adds a pie chart counting all element entities grouped by layer
- **THEN** its data has three groups named after the layers, summing to 50

#### Scenario: Grouping by an unknown property

- **WHEN** Kurt groups a chart by a property that is not mapped to the metatype
- **THEN** the request is rejected with 422 naming `groupBy`

#### Scenario: Many groups

- **GIVEN** 60 distinct values of the grouping property
- **WHEN** Franz opens the chart
- **THEN** it shows the 50 largest groups and "Other" with the sum of the rest

#### Scenario: Chart without grouping

- **WHEN** Kurt adds a chart widget without `groupBy`
- **THEN** the request is rejected with 422 naming `groupBy`

### Requirement: Aggregates respect visibility

An aggregation or grouping over a property whose values the reader may not read for every entity (role-restricted without a read role, or connection-scoped) SHALL give the widget the error `restricted` instead of a value; override roles SHALL NOT lift this. Counting entities and grouping by metatype or layer SHALL always be readable.

Trace: US-056, US-057, ADR-032, REQ-056

#### Scenario: Restricted property

- **GIVEN** the property "Investment" is readable only for the role `finance`
- **WHEN** Franz, without that role, opens a dashboard with the sum of "Investment"
- **THEN** the widget shows an error `restricted`, and Kurt, holding `finance`, sees the sum

#### Scenario: Connection-scoped grouping

- **GIVEN** the property "Owner Unit" is connection-scoped
- **WHEN** Franz opens a chart counting application components grouped by "Owner Unit"
- **THEN** the widget shows an error `restricted`, even for application components in his scope

### Requirement: Widgets after metamodel changes

A widget whose metatype or property no longer exists or no longer fits SHALL give the error `invalid-configuration` on read; the other widgets SHALL be computed normally, and no widget SHALL be deleted automatically. A deleted layer SHALL move its metatypes into the group "(none)".

Trace: REQ-053 BR-05, REQ-055, US-061 AC2

#### Scenario: Property unmapped later

- **GIVEN** a KPI widget with the sum of "Investment"
- **WHEN** "Investment" is unmapped from the metatype and Franz opens the dashboard
- **THEN** the KPI widget shows `invalid-configuration`, and the other widgets show their data

### Requirement: Comparison with the value 30 days ago

With `vs-previous-value` the KPI SHALL also show the value of the same aggregation 30 days before the request: over the entities that existed and were not deleted then, with each property value of their last version up to then, under the current metamodel and visibility. If no entity had a value then, sum, average, minimum and maximum SHALL show "No comparison value", and a count SHALL compare with 0.

Trace: US-056, REQ-053, owner decision 2026-11-18

#### Scenario: Count grew

- **GIVEN** 12 application components existed 30 days ago, and 3 were created and 1 was deleted since
- **WHEN** Franz opens a KPI counting application components with `vs-previous-value`
- **THEN** it shows 14, the previous value 12 labelled "30 days ago", the difference +2 and an upward trend

#### Scenario: Value changed since

- **GIVEN** "Investment" of one application component was changed from 5 000 000 to 7 000 000 ten days ago, and the others did not change
- **WHEN** Franz opens the KPI with the sum of "Investment" and `vs-previous-value`
- **THEN** the previous value is the current sum minus 2 000 000

#### Scenario: Boundary of the period

- **GIVEN** one application component was created exactly 30 days before the request, and another 30 days minus one second before it
- **WHEN** Franz opens the counting KPI with `vs-previous-value`
- **THEN** the first counts for the previous value, and the second does not

#### Scenario: No data 30 days ago

- **GIVEN** all application components were created within the last 30 days
- **WHEN** Franz opens the KPI with the sum of "Investment" and `vs-previous-value`
- **THEN** the widget shows the current value and "No comparison value"

#### Scenario: Restricted property

- **GIVEN** "Investment" is readable only for the role `finance`
- **WHEN** Franz, without that role, opens the KPI with the sum of "Investment" and `vs-previous-value`
- **THEN** the widget shows the error `restricted` and neither value

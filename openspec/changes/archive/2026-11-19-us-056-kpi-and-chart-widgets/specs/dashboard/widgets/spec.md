## REMOVED Requirements

### Requirement: Widgets of a dashboard

**Reason**: The requirement rejected the types `kpi` and `chart` until they are supported; this change supports them, so the requirement is replaced by "Widgets and their types".

**Migration**: See the ADDED requirement "Widgets and their types", which keeps the title, position, type and count rules.

## ADDED Requirements

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

A KPI widget SHALL show the aggregated value with an optional unit of at most 20 characters, and compare it with `none` or `vs-target`; `vs-target` SHALL need `targetValue`. Without data it SHALL show "– (no data)" without an error.

Trace: US-056 AC1, AC4, AC5

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

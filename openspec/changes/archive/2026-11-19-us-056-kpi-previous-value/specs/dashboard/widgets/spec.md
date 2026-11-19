## MODIFIED Requirements

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

## ADDED Requirements

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

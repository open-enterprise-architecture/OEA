## MODIFIED Requirements

### Requirement: Comparison with the value 30 days ago

With `vs-previous-value` the KPI SHALL also show, with its date as visible text, the value of the same aggregation 30 days before the request: over the entities existing and not deleted then, each with the value of its last version up to then, under the current metamodel and visibility. Without values then, sum, average, minimum and maximum SHALL show "No comparison value", and a count SHALL compare with 0.

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

#### Scenario: Date of the previous value

- **GIVEN** the request is on 19 November 2026
- **WHEN** Franz opens the counting KPI with `vs-previous-value`
- **THEN** the date 20 October 2026 is shown as text next to "30 days ago", reachable without hovering

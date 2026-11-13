# Delta for solution/solution-baseline

## ADDED Requirements

### Requirement: Delta type of a baseline entry

Each entry of the baseline SHALL carry the type of its delta in this solution, `modified` or `retiring`, or none. The page "Solution" SHALL show the markers "Modified" and "Retiring" from it, independent of how many deltas the area "Changes" has loaded.

Trace: US-041 AC1, US-042 AC1, owner decision 2026-11-13

#### Scenario: Marker of an entity with a delta

- **GIVEN** the solution holds a delta `retiring` for `CRM-Legacy` and no delta for `ERP-Core`
- **WHEN** the baseline is read
- **THEN** `CRM-Legacy` carries the delta type `retiring` and `ERP-Core` none

#### Scenario: Marker beyond the loaded deltas

- **GIVEN** the solution holds 60 deltas and the delta `modified` of `ERP-Core` is not on the first page of the area "Changes"
- **WHEN** a reader opens the page "Solution"
- **THEN** the baseline row `ERP-Core` shows "Modified"

#### Scenario: Delta of another solution

- **GIVEN** another solution holds a delta `retiring` for `CRM-Legacy`
- **WHEN** the baseline of this solution is read
- **THEN** `CRM-Legacy` carries no delta type

---
id: REQ-143
title: Inline column filter in catalog table (AND-combined)
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
  business_rules: []
  stakeholders:
    - SH-01
    - SH-02
    - SH-03
    - SH-04
    - SH-06
    - SH-07
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-143: Inline column filter in catalog table (AND-combined)

## Statement

The system MUST offer a filter input row directly below the column headers in the catalog table view. Each column MUST have its own input field that enables a quick filter on the respective attribute. All set inline filters MUST always be combined with logical AND. Inline filters are runtime filters (ad hoc); they do not change the saved catalog configuration and are NOT automatically persisted as a SavedFilter. For complex queries with OR logic, the separate "+ Filter" dialog is available (REQ-047).

## Rationale

Catalog visitors need a quick way to narrow results at a glance without building a full filter expression (REQ-047). The inline filter is the UX mechanism for UC-06/A1 step 6 ("Franz sets an ad hoc filter, e.g. `layer = 'application'`"). The AND combination is intuitive and sufficient for the most common use cases; more complex OR logic is reachable via the "+ Filter" dialog.

## Context

The inline filter complements the SavedFilter mechanism (REQ-047) but does not replace it:

| Mechanism | Operator | Persistence | Entry point |
|---|---|---|---|
| Inline column filter (this REQ) | simple value comparison (contains / eq) | no | input field below column header |
| SavedFilter (REQ-047) | all 12 operators, AND/OR | yes (as a named filter) | "+ Filter" dialog |

Inline filters and SavedFilters are combinable: both are applied AND-combined to the live query.

## Type-Specific Fields

### For type=functional

**Inputs (setting an inline filter)**:

| Field | Type | Description |
|---|---|---|
| columnKey | string | Attribute name of the column (corresponds to `attributePath` in REQ-047) |
| value | string | Freely entered value; the operator is chosen automatically: `contains` for text columns, `eq` for enum columns (status, layer) |

**Processing**:

1. On every input change, the table is queried again (debounce: 300 ms)
2. All non-empty inline filters are passed as AND expressions to `GET /api/v1/catalogs/{id}/query` (cf. REQ-046), combined with active SavedFilters
3. Inline filters are held in the browser state; no API call to save
4. The backend treats inline filters identically to ad hoc filter expressions in REQ-047

**Outputs**:

- The table updates live; number of visible entries in the status bar
- Optional: note "N column filters active" when at least 1 inline filter is set

## Acceptance Criteria

**AC1** (Inline filter sets result):

- Given: catalog "Application Inventory" with 47 ApplicationComponent entries
- When: the user types "Catalog" into the inline filter field of the "Name" column
- Then: the table shows only entities whose name contains "Catalog" (contains); status bar updates the count

**AC2** (AND combination of multiple inline filters):

- When: the user sets inline filter Name="Service" AND Layer="Application"
- Then: only entities that meet BOTH conditions appear in the table

**AC3** (Combination of inline filter + SavedFilter):

- Given: SavedFilter "Active systems only" is active
- When: additionally inline filter Layer="Application" is set
- Then: result = active AND layer=Application (all filters AND-combined)

**AC4** (Inline filter not persistent):

- When: the user sets an inline filter, leaves the catalog, and opens it again
- Then: inline filters are cleared; SavedFilters remain active (since persisted)

**AC5** (No API call to save):

- When: an inline filter is set
- Then: no `POST /api/v1/catalogs/{id}/filters` — only a GET query with an inline parameter

**AC6** (OR note):

- Given: the user wants to filter Name="Gateway" OR Name="Service"
- Then: the inline filter does not support OR; the user is referred to the "+ Filter" dialog by a tooltip/note

## Dependencies

- Blocked by: REQ-043 (catalog), REQ-046 (query endpoint)
- Complements: REQ-047 (SavedFilter / complex filter dialog)
- Related: UC-06/A1 step 6 (set ad hoc filter)

## Implementation Notes

- Inline filters are passed as additional query parameters to `GET /api/v1/catalogs/{id}/query`, e.g. `?inlineFilters[name]=Catalog&inlineFilters[layer]=Application`
- Backend: inlineFilters are AND-combined with SavedFilter expressions before the query
- Frontend: debounce 300 ms to avoid unnecessary requests
- Enum columns (status, layer): dropdown quick selection instead of free-text input

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | Requirements Engineer | Initial draft; AND inline filter, delimitation from REQ-047 (OR/SavedFilter) |

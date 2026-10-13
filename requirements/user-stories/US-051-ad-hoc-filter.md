# US-051: Set an Ad-Hoc Filter in a Catalog

**ID**: US-051
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a catalog visitor, I want to be able to set filter conditions on columns of the catalog that take effect immediately on the result list – so that I can find specifically the entities that are relevant for my current task, without changing the catalog configuration.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: All stakeholders (SH-01 to SH-07)
**Requirement**: [REQ-047: Set Filters and Save as SavedFilter](../req/REQ-047-set-and-save-filters.md)

## Acceptance Criteria

**AC1** (simple filter, happy path):
- Given: Catalog shows 10 ApplicationComponents with different statuses
- When: Franz sets filter `status = "active"`
- Then: Table immediately shows only the active applications; updated row by row without page reload; `totalCount` shows the reduced number

**AC2** (filter on join attribute):
- Given: Join "Interfaces" with target attribute `protocol`
- When: Lukas sets filter `Interfaces.protocol = "REST"`
- Then: Only applications whose DataFlow connections have at least one REST interface; interfaces without REST are not displayed in the aggregated list

**AC3** (multiple filters, AND combination):
- When: Max sets filter `status = "active"` AND `layer = "application"`
- Then: Only entities that meet BOTH conditions are shown

**AC4** (remove filter):
- When: Franz removes a set filter (X button on the filter chip)
- Then: Table shows all entities again; filter is completely removed

**AC5** (operators supported in the UI):
- When: Franz opens the filter configurator for column "name"
- Then: At least the following operators are available: equals (eq), contains (contains), starts with (startsWith), is empty (isNull), is not empty (isNotNull)
- For numeric / date attributes additionally: > (gt), < (lt), >= (gte), <= (lte)

## Technical Notes

- Affected components: filter bar above the table, backend query parameters (REQ-046)
- UX: filter chips in a bar; "Add filter" button opens dropdown (select column → select operator → enter value)
- Ad-hoc filters are not persisted; they live only in browser state until the page is reloaded
- Dependency: US-049

## Definition of Done

- [ ] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [ ] Tests: simple filter, filter on join attribute (dot notation), AND combination, remove filter, operator selection
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

# US-052: Save a Set Filter as a SavedFilter

**ID**: US-052
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As a catalog user, I want to be able to name and save an ad-hoc set filter, so that on my next visit to the catalog I can reactivate it via quick select without re-entering it.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: All stakeholders (SH-01 to SH-08); catalog managers (SH-03, SH-07) can additionally choose scope=instance
**Requirement**: [REQ-047: Set Filters and Save as SavedFilter](../req/REQ-047-set-and-save-filters.md)

## Acceptance Criteria

**AC1** (save filter, happy path):
- Given: Kurt has set filter `status = "active"` (US-051)
- When: Kurt clicks "Save filter", enters the name "Active systems only" and confirms
- Then: SavedFilter persisted in the catalog configuration; appears in the quick-select list for all visitors (scope=instance, because Kurt is a manager)

**AC2** (activate SavedFilter):
- Given: SavedFilter "Active systems only" exists
- When: Franz opens the catalog and clicks "Active systems only" in the quick select
- Then: Filter immediately active; table shows only active entities

**AC3** (personal filter – scope=personal):
- Given: Franz has the visitor role (no write permission)
- When: Franz sets a filter and clicks "Save filter"
- Then: Filter saved as scope=personal; appears only in Franz's quick select

**AC4** (AND / OR between expressions):
- When: Kurt saves a filter with 2 expressions and chooses OR as the combination
- Then: SavedFilter saved with `logicalOperator=or`; query returns entities that meet at least one condition

## Technical Notes

- Affected components: "Save filter" button in the filter bar, name input dialog, backend `POST /api/v1/catalogs/{id}/filters`
- UX: minimal modal: name + AND/OR selection + confirmation
- Dependency: US-051

## Definition of Done

- [ ] Acceptance criteria AC1–AC4 met
- [ ] Code review by a second person
- [ ] Tests: save filter (instance), visible in quick select, personal scope for visitors, AND/OR combination
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

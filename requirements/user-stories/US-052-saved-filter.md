# US-052: Save a Set Filter as a SavedFilter

**ID**: US-052
**Story Points**: 2
**Status**: done
**Sprint**: S9
**Implementation**: OpenSpec change `us-051-catalog-filters` – `POST /catalogs/{id}/filters`, `DELETE /catalogs/{id}/filters/{filterId}`, the dialog "Save filter" and the quick selection "Saved filters" in SCR-020

## Story

As a catalog user, I want to be able to name and save an ad-hoc set filter, so that on my next visit to the catalog I can reactivate it via quick select without re-entering it.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: All stakeholders (SH-01 to SH-08); saving needs the right to configure the catalog (catalog managers SH-03, SH-07 for shared catalogs, the creator for a personal catalog)
**Requirement**: [REQ-047: Set Filters and Save as SavedFilter](../req/REQ-047-set-and-save-filters.md)

## Acceptance Criteria

**AC1** (save filter, happy path):

- Given: Kurt has set filter `status = "active"` (US-051)
- When: Kurt clicks "Save filter", enters the name "Active systems only" and confirms
- Then: SavedFilter persisted in the catalog configuration; appears in the quick-select list for all visitors of the catalog (Kurt holds `catalog:manage`)

**AC2** (activate SavedFilter):

- Given: SavedFilter "Active systems only" exists
- When: Franz opens the catalog and clicks "Active systems only" in the quick select
- Then: Filter immediately active; table shows only active entities

**AC3** (who saves filters, realigned 2026-11-05, owner decision):

- Given: Franz has the visitor role (no `catalog:manage`)
- When: Franz sets a filter in a shared catalog
- Then: "Save filter" is not offered, and the API answers 403; in his own personal catalog Franz saves the filter, and it appears in its quick selection
- The `scope` of a SavedFilter was dropped: saved filters belong to the catalog configuration

**AC4** (AND / OR between expressions):

- When: Kurt saves a filter with 2 expressions and chooses OR as the combination
- Then: SavedFilter saved with `logicalOperator=or`; query returns entities that meet at least one condition

## Technical Notes

- Affected components: "Save filter" button in the filter bar, name input dialog, backend `POST /api/v1/catalogs/{id}/filters`
- UX: minimal modal: name and description; the AND/OR combination is the one of the filter bar
- Activating a saved filter replaces the filters of the bar with its expressions and operator; the query receives them as `filter` and `filterMode`, not as a saved filter ID
- Dependency: US-051

## Definition of Done

- [ ] Acceptance criteria AC1–AC4 met
- [ ] Code review by a second person
- [ ] Tests: save filter (instance), visible in quick select, personal scope for visitors, AND/OR combination
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

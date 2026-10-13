# US-049: Display and Sort Catalog Data

**ID**: US-049
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a catalog visitor, I want to see the entities of a catalog as a sortable table with the configured columns – so that I get a quick structured overview of the architecture landscape without having to search the repository manually.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: All stakeholders (SH-01 to SH-07)
**Requirement**: [REQ-046: Execute Catalog Query](../req/REQ-046-catalog-query.md)

## Acceptance Criteria

**AC1** (display table):
- Given: Catalog "Application Inventory" with primaryEntityType=ApplicationComponent, columns [name, status], join "Interfaces" (aggregate), repository contains 3 ApplicationComponents
- When: Franz opens the catalog
- Then: Table with 3 rows (one per application), columns "name" and "status" visible; column "Interfaces" shows aggregated interface names

**AC2** (sort ascending):
- When: Franz clicks on column header "name"
- Then: Table sorted alphabetically ascending by name; header shows arrow ↑

**AC3** (switch sorting):
- When: Franz clicks the "name" header again
- Then: Table sorted descending; second click → no sorting (back to default)

**AC4** (empty repository):
- Given: primaryEntityType has no entities
- When: Franz opens the catalog
- Then: Empty table with hint text "No entries found. Entities can be captured as a delta in a solution."

**AC5** (pagination):
- Given: Repository contains 120 ApplicationComponents
- When: Franz opens the catalog (pageSize=50 default)
- Then: First page shows 50 entries; pagination control shows "Page 1 of 3" (120 / 50); page 2 reachable via pagination control

**AC6** (apply default SavedView):
- Given: Catalog has default SavedView "Compact view" (only columns name, status; filter "Active systems only" active)
- When: Franz opens the catalog
- Then: Table shows only columns name and status; only active entities visible; active filter is visibly marked in the filter bar

## Technical Notes

- Affected components: catalog detail view (table component), backend `GET /api/v1/catalogs/{id}/query`
- Sorting: sortBy + sortDir as query parameters; clicking a header sets/changes these parameters
- Join columns in aggregate mode: comma-separated list of the first N values; tooltip or popover for the full list if > N values
- Dependency: US-046 must be completed; US-047 should be completed (otherwise no columns configured)

## Definition of Done

- [ ] Acceptance criteria AC1–AC6 met
- [ ] Code review by a second person
- [ ] Tests: table populated, sorting asc/desc, empty table, pagination, default SavedView applied
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

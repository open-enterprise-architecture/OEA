# US-050: Switch Join Mode Between aggregate and rows

**ID**: US-050
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a catalog visitor, I want to be able to switch the join mode for a join between "compact" (aggregate: one row per main entity) and "rows" (rows: one row per join result) – without changing the saved catalog configuration – so that I can consume the same data basis differently depending on the analysis context.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: All stakeholders (SH-01 to SH-07)
**Requirement**: [REQ-049: Switch Join Mode at Runtime](../req/REQ-049-join-mode-runtime.md)

## Acceptance Criteria

**AC1** (switch aggregate→rows):
- Given: Catalog "Application Inventory" in default mode aggregate; CRM system has 2 interfaces
- When: Franz clicks "Rows" on the join "Interfaces"
- Then: Table shows 2 rows for CRM system (one per interface); switch without page reload

**AC2** (switch rows→aggregate):
- Given: Table shows rows mode
- When: Franz clicks "Compact"
- Then: CRM system appears again as one row with aggregated interfaces

**AC3** (no mutation):
- When: Franz switches multiple times
- Then: `GET /api/v1/catalogs/{id}` shows unchanged catalog configuration (`defaultJoinMode` unchanged)

**AC4** (multiple joins independent):
- Given: Catalog has 2 joins (Interfaces, Technologies)
- When: Franz switches only "Interfaces" to rows; "Technologies" stays aggregate
- Then: Table expands rows for Interfaces; Technologies continue to appear aggregated in one cell

## Technical Notes

- Affected components: catalog detail view (toggle control per JoinDefinition), backend (query parameter `joinMode[{joinId}]` to the REQ-046 endpoint)
- UX: toggle or segmented control per join in the table header row or join column header; label e.g. "Compact / Rows"
- No dedicated API endpoint; the mode override is passed along as a query parameter in the query
- Dependency: US-048, US-049

## Definition of Done

- [ ] Acceptance criteria AC1–AC4 met
- [ ] Code review by a second person
- [ ] Tests: aggregate→rows, rows→aggregate, no mutation of the configuration, multiple joins independent
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

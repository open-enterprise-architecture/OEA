# US-053: Create a SavedView and Mark It as Default

**ID**: US-053
**Story Points**: 3
**Status**: done
**Sprint**: S9
**Implementation**: OpenSpec change `us-053-saved-views` – `POST /catalogs/{id}/views`, `PUT` and `DELETE /catalogs/{id}/views/{viewId}`, `view={viewId}` in the query, and the view selection, "Save view" and the dialog "Views" in SCR-020

## Story

As a Lead Enterprise Architect, I want to create a named view for a catalog that bundles a certain column order, active filters and join modes – so that visitors immediately see the view preconfigured for them when opening the catalog, without having to adjust it manually.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-048: Create SavedView and Apply on Opening](../req/REQ-048-saved-view.md)

## Acceptance Criteria

**AC1** (create SavedView, happy path):

- Given: Catalog "Application Inventory" with columns [name, status, vendor], SavedFilter "Active systems only", join "Interfaces"
- When: Kurt creates view "Compact view" with columnOrder=[name, status], the filter `status = "active"` and isDefault=true (realigned 2026-11-06: a view keeps the filter expressions, not references to saved filters)
- Then: View saved; the next time the catalog is opened without view state in the URL, the compact view appears immediately

**AC2** (view selection):

- Given: Catalog has 2 SavedViews: "Compact view" (default) and "Full view"
- When: Franz opens the catalog
- Then: "Compact view" is automatically active; Franz can switch to the "Full view" via dropdown menu

**AC3** (default switch):

- Given: "Compact view" is isDefault=true
- When: Kurt marks "Full view" as default
- Then: "Full view" isDefault=true; "Compact view" automatically isDefault=false

**AC4** (JoinMode override in view):

- When: Kurt creates view "Detail view" with joinModeOverrides=[{joinId: join-1, joinMode: rows}]
- Then: When activating "Detail view", join "Interfaces" switches to rows mode

**AC5** (view without default):

- Given: No isDefault set
- When: Max opens the catalog
- Then: All configured columns visible; no filter preselected; join modes as in the configuration

## Technical Notes

- Affected components: the view selection, "Save view" and the dialog "Views" in SCR-020 (realigned 2026-11-06), backend `POST /api/v1/catalogs/{id}/views`, `PUT` and `DELETE /api/v1/catalogs/{id}/views/{viewId}`
- UX: view selection next to the filter bar; "Save view" opens a modal with name, description, the columns of the table and their order, and the default; it also keeps the filters, join modes and sort of the table
- The view is part of the URL (`view={viewId}`, ADR-044); the query shows its columns, the page passes its filters, modes and sort as parameters
- Saving needs the right to configure the catalog (`catalog:manage` for a shared catalog, the creator for a personal one)
- Dependency: US-047 (columns), US-052 (SavedFilter); US-050 (JoinMode override)

## Definition of Done

- [ ] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [ ] Tests: create view, default applied on opening, default switch (old default reset), JoinMode override, no default
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

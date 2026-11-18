# Proposal

## Why

The sidebar of the web portal shows the navigation tree (SCR-055): folders that the Lead Enterprise Architect arranges by hand. UC-13 also asks for a second view that needs no manual work:

- the solutions as the primary navigation level, with a computed "Current state" node first (US-132, REQ-138, REQ-140);
- a fixed base structure under each solution, with the number of objects per category (US-133, REQ-139).

This is S16 change 1 ([sprint plan](../../../docs/sprint-plan.md), UC-13 2/2). Change 2 (`us-135-component-hierarchy`) nests components under "Components". Change 3 (`us-055-dashboards`) starts UC-07.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-13](../../../requirements/use-cases/UC-13-manage-navigation-tree.md) |
| User Story | [US-132](../../../requirements/user-stories/US-132-solution-browser-as-default-tab.md) (AC1–AC3), [US-133](../../../requirements/user-stories/US-133-solution-base-structure-explorer.md) (AC1, AC2; AC3 follows with the building blocks) |
| Requirements | [REQ-138](../../../requirements/req/REQ-138-explorer-solution-navigation.md), [REQ-139](../../../requirements/req/REQ-139-solution-base-structure-explorer.md), [REQ-140](../../../requirements/req/REQ-140-plateau-free-solutions-current-state.md) |
| ADRs | [ADR-020](../../../adrs/ADR-020-explorer-navigation-model.md) (browser model), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`entity:read`), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) (SQL only in `core`) |
| Sprint / Milestone | S16 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-18:**

- **Categories:** only categories whose content exists are shown. Functional and Solution Building Blocks follow with the continuum (S20), Diagrams with the diagrams.
- **Project mode:** active project solutions appear in their own group "Projects" after the plateaus, instead of only flowing into "Current state" (REQ-140 realigned).

**Decisions and assumptions of this change (open for review):**

- **Where:** the sidebar SCR-055 gets two tabs, "Browser" (default) and "Navigation tree" (the existing tree). The web portal is the client for now; the Electron client follows with ADR-008.
- **Top level:** "Current state" first, then one node per plateau with status `transition` or `target` in the order of the plateau overview (by `validFrom`, then by name), then "Projects". The baseline plateau is the current state and has no node; realized plateaus are history.
- **Solutions shown:** only active ones, that is, status neither `implemented` nor `archived` (US-132 AC3). A plateau solution sits under its target plateau (`toPlateauId`), sorted by name. An empty plateau or an empty "Projects" group stays visible with 0, like an empty category.
- **Categories of "Current state":** Components (element entities that are not deleted), Connections (connection entities that are not deleted) and Catalogs (the catalogs the reader sees: instance catalogs and the reader's own personal ones).
- **Categories of a solution:** Components only, that is, the deltas of the solution (`new`, `modified`, `retiring`). Deltas exist only for element entities, and catalogs are not assigned to solutions. Connections and Catalogs of a solution follow when solutions can hold them.
- **Counts:** every category shows its count; an empty category is greyed out, not hidden (US-133 AC2).
- **Contents:** a category lists its objects by name in pages of 50, loaded when it is expanded. An entity opens its change history (SCR-026), a catalog its catalog page, a solution and a delta its solution page (SCR-061).
- **Hierarchy:** "Components" is a flat list in this change; change 2 nests it.

**Contract (spec-first, `api/openapi.yaml`):**

- `getBrowserView` (`GET /views/browser`, beside `/views/explorer`): the top level with all counts.
- `listBrowserItems` (`GET /views/browser/items`): one page of one category of "Current state" or of a solution.

Both are read-only and need `entity:read`.

**Not in this change:**

- Building blocks and diagrams (US-133 AC3).
- Nesting, structuring aids (change 2).
- Placing browser objects in the navigation tree by drag and drop; "Place in navigation" stays on the pages.

## Capabilities

### New Capabilities

- `navigation/solution-browser`: the browser tab of the sidebar with "Current state", plateaus, projects, solutions and their categories.

### Modified Capabilities

- `navigation/navigation-tree`: the sidebar holds the tree in the second tab "Navigation tree".

## Impact

- **Contract:** two new operations and their schemas in `api/openapi.yaml`.
- **Backend:** a read service, read methods in the `core` stores of entities and catalogs, and a new `core/solution` store; no migration.
- **Frontend:** tabs in the sidebar SCR-055, a new browser component with lazy categories, fakes and stories.
- **Documentation:**
  - manual pages "Navigation" (en/de);
  - SCR-055 in `SCREENS.md`;
  - US-132 and US-133 to `done` and `in-progress`;
  - REQ-138 to REQ-140 and UC-13 realigned.

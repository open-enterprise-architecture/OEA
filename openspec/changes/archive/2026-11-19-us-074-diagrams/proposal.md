# Proposal

## Why

UC-10 models business processes in diagrams, and OEA has no diagrams yet:

- **The contract** has only the planned read `getDiagramView`.
- **The business object** [Diagram](../../../business-objects/diagram.md) is modeled.
- **The viewpoints** (S15) and their default sizes (S17 change 9) are ready.
- **US-090 AC2 and US-093 AC1** are still open: the diagrams affected by deleting a viewpoint.

This change brings diagrams as objects: create, list, rename and delete, and where people find them. The canvas editor follows in change 11 (`us-074-diagram-canvas`).

This is S17 change 10 ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-10](../../../requirements/use-cases/UC-10-model-business-processes.md), [UC-12](../../../requirements/use-cases/UC-12-manage-viewpoints.md), [UC-13](../../../requirements/use-cases/UC-13-manage-navigation-tree.md) |
| User Stories | [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) (creating a diagram), [US-090](../../../requirements/user-stories/US-090-viewpoint-crud.md) AC2, [US-093](../../../requirements/user-stories/US-093-viewpoint-delete-warning.md) AC1 |
| Business Object | [Diagram](../../../business-objects/diagram.md) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-19:**

- **Permissions:** creating and editing diagrams needs `entity:write`; reading needs `entity:read`.
- **Saving:** changes on the canvas are saved explicitly with "Save" when working alone, and immediately when working collaboratively. Collaboration follows with UC-24 (S22); this change has no canvas yet.
- **Where diagrams are found:**
  - a page "Diagrams";
  - the browser of the sidebar;
  - the navigation tree.
- **Deleting a viewpoint used by diagrams:** it warns with their number and deletes them after confirmation; the entities stay.

**Decisions and assumptions of this change (open for review):**

- **Attributes:** name (1–200, unique ignoring case after trimming, stored trimmed), description (at most 2000) and viewpoint, as in the business object.
  - The viewpoint is fixed at creation.
  - The plateau of the business object is not part of this change: diagrams show the current state.
- **Principals:** service accounts with `entity:write` may create and change diagrams like persons; `createdBy` is the actor. The local system admin account, which is no person, gets 403.
- **Lost updates and audit:** `If-Match` on every change; audit `diagram.created`, `diagram.updated` and `diagram.deleted`.
- **List:** ordered by name, with a name filter of at most 200 characters and cursor paging as for the solution baseline (`page`, `pageSize` 1–200, default 50, `cursor`).
- **Restorable deletion (owner decision 2026-11-19):** a deleted diagram is kept and can be restored by holders of `entity:restore` (`listDeletedDiagrams`, `restoreDiagram`, audit `diagram.restored`). Its navigation tree entries are left out while it is deleted and show again after the restore. Deleting a viewpoint removes its diagrams for good, deleted ones included.
- **Deleting a viewpoint:**
  - the viewpoint read and list carry `diagramCount`;
  - deleting the viewpoint deletes its diagrams in the same transaction, each audited as `diagram.deleted` with the cause `viewpoint-deleted`.
  - A package re-import that removes a system-defined viewpoint deletes its diagrams the same way; the import result names their number.
- **Viewpoint editing (US-090 AC2):** removing an allowed type from a viewpoint used by diagrams warns "N diagrams affected" without blocking; until diagrams have shapes (change 11), N is the diagram count of the viewpoint.
- **Browser:** "Current state" gets the category Diagrams with the notation of the viewpoint as marker; the counts and the paging are as for the other categories.
- **Navigation tree:** entries of the type `diagram`, placed with "Place in navigation" on the diagram page; the rules are as for solutions, so placing needs `viewpoint:manage` and `entity:read`.
- **Screens:**
  - the page "Diagrams" is the new screen SCR-067;
  - the diagram page shows name, description and viewpoint until the canvas (SCR-021) exists in change 11.

**Not in this change:**

- the canvas, shapes, connections and the layout (change 11);
- the plateau of a diagram;
- the portal view (SCR-052);
- collaboration (UC-24).

## Capabilities

### New Capabilities

- `diagram/diagrams`: diagrams as objects – create, read, list, rename, delete, audit and the page "Diagrams".

### Modified Capabilities

- `metamodel/viewpoints`: deleting a viewpoint deletes its diagrams; the viewpoint carries its diagram count; the section "Viewpoints" warns about affected and deleted diagrams.
- `metamodel/package-import`: a re-import that drops a viewpoint deletes its diagrams.
- `navigation/solution-browser`: the category Diagrams.
- `navigation/navigation-tree`: diagrams as entries; entries of deleted diagrams are left out.

## Impact

- **Contract:**
  - `listDiagrams`, `createDiagram`, `getDiagram`, `updateDiagram` and `deleteDiagram` under `/diagrams` (tag `Diagrams`);
  - `diagramCount` on viewpoints;
  - the browser category `diagrams`, the item kind `diagram` and its `notation`;
  - the tree item type `diagram`;
  - the package import result.
- **Backend:**
  - the migration `V46__diagrams.sql`;
  - `core.diagram.DiagramStore` and `app.diagram.Diagrams`, with a controller;
  - the viewpoint deletion and the package import;
  - the browser and the navigation tree.
- **Frontend:**
  - `DiagramsPage.vue` (SCR-067) and `DiagramPage.vue`;
  - the browser category, the tree entry and "Place in navigation";
  - the viewpoint deletion and edit warnings on SCR-031;
  - stories and fakes.
- **Documentation:**
  - the manual "Diagrams" (new page, en/de), "Viewpoints" and "Navigation";
  - SCREENS.md, the business object Diagram, `docs/data-model.puml` and the audit event catalog;
  - US-090, US-093 and the status of US-074, UC-10, UC-12 and UC-13;
  - the sprint plan.

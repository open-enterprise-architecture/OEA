# Proposal

## Why

The viewpoint of a diagram is fixed at its creation. The owner decided on 2026-11-19 that it can be changed: the change is saved immediately, shapes whose type the new viewpoint does not allow stay marked as conflicts, and further changes can be saved only after all conflicts are resolved – the shape removed or the type allowed in the viewpoint. On 2026-11-26 the owner decided that the same conflict rule applies to shapes whose type is removed from a viewpoint that diagrams use; today they are tolerated silently, and the warning "N diagrams affected" counts every diagram of the viewpoint.

This is S17 change 17 ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| User story | [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) |
| Use cases | UC-10, UC-12 |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Screens | SCR-021 "Diagram editor", SCR-031 "Viewpoints" |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions:**

- **2026-11-19:** the viewpoint of a diagram can be changed and is saved at once; shapes of types the new viewpoint does not allow are conflicts; saving is possible only after all conflicts are resolved.
- **2026-11-26:** the same rule applies to shapes and edges whose type is removed from a used viewpoint; "N diagrams affected" counts exactly the diagrams with such shapes or edges.

**Decisions and assumptions of this change (open for review):**

- **`changeDiagramViewpoint`:** `PUT /diagrams/{id}/viewpoint` (`entity:write`, `If-Match`), saved at once, audited as `diagram.viewpoint-changed`. `updateDiagram` keeps ignoring a viewpoint ID.
- **Conflicts** are computed when the view is read, not stored: a shown shape or edge whose exact metatype the current viewpoint does not allow (the rule of the existing save check, no subtypes). Edges count as well as shapes.
- **Blocked save:** `saveDiagramView` answers 409 `diagram-conflicts` while the saved layout still contains a conflict, including saves without any change and saves of entity changes only; it comes after the 422 of the layout and before the 403 of the property visibility. Removing the conflicting shapes and edges in the same save is accepted.
- **Hidden shapes** of deleted entities are no conflicts while hidden; after a restore they can become conflicts.
- **Accurate count:** `countViewpointAffectedDiagrams` (`viewpoint:manage`) (`POST /admin/viewpoints/{id}/affected-diagrams/query`) counts, for a proposed set of types, the diagrams with a shown shape or edge of a removed type; SCR-031 uses it for the warning.
- **Editor:** "Change viewpoint" on the diagram page with a preview of the conflicts (computed in the client from the allowed types of the chosen viewpoint), conflict marks on shapes and edges, a list of conflicts with "Remove from diagram" per conflict and "Remove all conflicts".

**Not in this change:** replacing the metatype of an entity to resolve a conflict; a conflict count in the diagram list.

## Capabilities

### New Capabilities

- `diagram/diagram-viewpoint-change`: changing the viewpoint, conflicts and the blocked save.

### Modified Capabilities

- `diagram/diagrams`: the viewpoint changes only through `changeDiagramViewpoint`.
- `metamodel/viewpoints`: the accurate number of affected diagrams.

## Impact

- **Contract:** `changeDiagramViewpoint`, `countViewpointAffectedDiagrams`, `conflict` on `DiagramShape` and `DiagramEdge`, `conflictCount` on `DiagramView`, 409 `diagram-conflicts` on `saveDiagramView`.
- **Database:** none expected (conflicts are computed).
- **Backend:** `core.diagram` queries, the diagram services, the save check.
- **Frontend:** the dialog "Change viewpoint", conflict marks and the conflict list on SCR-021, the warning on SCR-031.
- **Documentation:** the manual "Diagrams" and "Viewpoints" (en/de), SCREENS.md, the business object diagram, the audit catalog, US-074, UC-10 and the sprint plan.

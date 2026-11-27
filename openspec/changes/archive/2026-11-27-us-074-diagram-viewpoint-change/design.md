# Design

## Context

- **Diagrams (change 10):** `diagrams.viewpoint_id`, `updateDiagram` with `If-Match` (the version is shared with `saveDiagramView`), the audit `diagram.*`.
- **Canvas (changes 11 to 13, 16):** `getDiagramView` returns the viewpoint, shapes, edges and hierarchy lines; `saveDiagramView` replaces the layout as a whole and keeps stored shapes and edges of an allowed type check only for new or changed items ("a stored edge that keeps its connection stays"). The order of the answers of the save is part of the contract.
- **Viewpoints:** `diagramCount` on `ViewpointConfig`; SCR-031 warns "N diagrams affected" with `diagramCount` whenever a type is removed.

## Goals / Non-Goals

**Goals:** change the viewpoint of a diagram; conflicts from any cause; the blocked save; the accurate affected count.

**Non-Goals:** changing entity metatypes, conflict counts in lists, collaboration.

## Architecture Constraints

- **ADR-051:** SQL in `core` stores only; no package cycles.
- **ADR-032:** `changeDiagramViewpoint` needs `entity:write`; `countViewpointAffectedDiagrams` needs `viewpoint:manage`.
- **ADR-013:** contract first; the conventions check applies.

## Decisions

### D1 `changeDiagramViewpoint`

- `PUT /diagrams/{id}/viewpoint` with `{viewpointId}`, `If-Match` required (428, 412 `stale-version`), `entity:write`, persons and service accounts as for `updateDiagram` (the local system admin account 403).
- 404 for an unknown or deleted diagram; 422 naming `viewpointId` for an unknown viewpoint.
- The same viewpoint: 200 with the unchanged diagram, no new version, no audit.
- Otherwise: `viewpoint_id` updated, the version raised, `diagram.viewpoint-changed` with `fromViewpointId`, `toViewpointId` and `conflicts` (the number of conflicting shapes and edges after the change).
- The response: the `Diagram` plus `conflictCount`.

### D2 Conflicts

- Computed in the view query: a shape is a conflict when its entity is not deleted and its metatype is not among the allowed metatypes of the diagram's viewpoint; an edge when its connection is not deleted, its shapes are shown and its metatype is not among the allowed connection types.
- `DiagramShape.conflict` and `DiagramEdge.conflict` (boolean, required), `DiagramView.conflictCount`.
- Causes: `changeDiagramViewpoint`, `updateViewpoint` and a package re-import that updates a package viewpoint (`importMetamodelPackage`) without a type; none of them is blocked by conflicts. Deleting a metatype cannot cause one (409 `in-use` while entities exist).
- Hierarchy lines are never conflicts.
- The allowed check is the one of the save and of `searchDiagramEntities`: the exact metatype ID is in the allowed set of the viewpoint (`DiagramViews.validate` and `validateEdges`); a viewpoint that allows a base type does not allow its subtypes. Conflicts reuse that rule, so a shape is never accepted by one check and a conflict by the other.

### D3 Blocked save

- `saveDiagramView` checks whether any shape or edge of the request is a conflict under the current viewpoint. Only a stored shape that keeps its entity and a stored edge that keeps its connection can be one: a new shape, a shape that shows another entity, a new or changed edge and a new entity or connection of a type not allowed keep their 422 of the layout (`METATYPE_NOT_ALLOWED`). If so: 409 `diagram-conflicts` (`DiagramConflictProblem` with `invalidFields` such as `shapes[3]` and `edges[1]`), nothing changes.
- The 409 applies to every save while a conflict is left in the request, a save without any change and a save of only `entityUpdates` or `parentChanges` included.
- Place in the answer order: 400, 403 of a scoping connection type, 412 or 428, 422 of the layout and the edges (with the structural 422 of nesting, lines and parent changes), **409 `diagram-conflicts`**, then unchanged the 403 of the visibility and the rest of the documented order. In the code it follows the thrown 422 of the collected layout errors and comes before the entity updates. The descriptions of `saveDiagramView` and of its 409 response (now two problem schemas) are updated.
- The kept hidden shapes and edges of deleted entities are not checked.

### D4 `countViewpointAffectedDiagrams`

- `POST /admin/viewpoints/{id}/affected-diagrams/query` with `{metatypeIds, connectionTypeIds}` (each 0 to 500 UUIDs as in `ViewpointWrite`, 400 beyond), `viewpoint:manage`, read only, no `If-Match`, not audited; 404 for an unknown viewpoint. Answer `{count}`. The `/query` suffix follows the read-only `POST` of `findDiagramConnections` and `queryCatalog`; the path does not collide with `/admin/viewpoints/notations` or `/admin/viewpoints/{id}/shape-palette`.
- Counts the diagrams of the viewpoint that are not deleted and have a shape or edge that would be a conflict under the proposed set (the rule of D2, exact metatype IDs): a shown shape whose metatype is not in `metatypeIds`, or a shown edge whose metatype is not in `connectionTypeIds`. IDs that name no type are ignored.
- SCR-031 calls it when the selection removes a type of a viewpoint with `diagramCount` > 0, debounced, and shows "N diagrams affected – their shapes of removed types become conflicts" when N > 0.

### D5 Editor

- **Diagram page:** "Change viewpoint" for `entity:write` (SCR-021 toolbar; `PUT /diagrams/{id}/viewpoint` follows the sub-resource precedent `PUT /entities/{id}/parent`), disabled with unsaved changes (the hint asks to save or discard first). The dialog lists the viewpoints (`listViewpoints`) and, for the selected one, the shapes and edges of the current view whose type it does not allow ("N shapes and M edges will become conflicts" with their names) or "No conflicts". Confirm sends `changeDiagramViewpoint` with the diagram version and reloads the view with the new viewpoint, its palette, default sizes and effective `askNewParent`; 412 shows the existing changed-meanwhile dialog.
- **Canvas:** conflict shapes and edges get a warning border and the text "Conflict: type not allowed in this viewpoint" (not only colour). A panel "Conflicts (N)" lists them with "Remove from diagram" each and "Remove all conflicts"; selecting an entry selects the shape. "Save" stays enabled; a 409 `diagram-conflicts` marks the named items and opens the panel. The panel is visible read-only for readers.
- "Remove from diagram" on a conflict is the existing removal: the edges and hierarchy lines of the shape go with it, and removing a conflicting container asks and removes its nested shapes as in change 16, even when they are of allowed types. A new element dropped into a conflicting container is accepted on the canvas, but the save stays 409 until the container is resolved.

### D6 Documentation

The manual pages "Diagrams" and "Viewpoints" (en, de); SCREENS.md (SCR-021, SCR-031, SCR-067 page of a diagram); the business object diagram (a rule for conflicts) and viewpoint; the audit catalog (`diagram.viewpoint-changed`); US-074 and UC-10 realigned; the sprint plan status of change 17.

## Risks / Trade-offs

- **Behavior change of the save:** diagrams that today carry shapes of a removed type cannot be saved until those shapes are removed or the type is allowed again. This is the owner decision of 2026-11-26.
- **Computed conflicts** cost a join per view read; the allowed sets are small, the view is already one query per kind.

# Design

## Context

- **Hierarchy (change `us-135-component-hierarchy`, V42):** `entities.parent_entity_id`, `setEntityParent` with the depth limit of 10, the cycle check, the automatic composition connection (`autoCreated`) and the audit `entity.parent-changed`. `listBrowserItems` lists children with `childCount`.
- **Canvas (changes 11 to 13):** `diagram_shapes` (V47) and `diagram_edges` (V48); `getDiagramView` and `saveDiagramView` replace the layout as a whole in one transaction; `EntityBatch.createInTransaction`, `updateInTransaction` and `createConnectionsInTransaction` write entities in the save's transaction; the order of the answers of the save is part of the contract.
- **Viewpoints:** `ViewpointConfig` and `ViewpointWrite` (replacing write), the package section `viewpoints`, system-defined viewpoints read-only, the metamodel lock mode does not apply (spec `metamodel/viewpoints`).
- **Vue Flow (ADR-007):** nodes with `parentNode` take positions relative to the parent and support `expandParent`; parents must precede their children in the node list.

## Goals / Non-Goals

**Goals:** containers on the canvas, the children question, hierarchy lines, "New parent?" with its setting, new children in containers.

**Non-Goals:** subtrees, automatic layout, BPMN pools and lanes (change 18), collaboration.

## Architecture Constraints

- **ADR-051:** SQL only in `core` stores; no package cycles (`CycleRules`).
  - The parent write of `setEntityParent` lives in `io.oea.app.hierarchy.EntityHierarchy.setParent` (its SQL in `core.entity`: `EntityStore.updateParent`, `EntityHierarchyStore`). This change adds `EntityHierarchy.setParentInTransaction(principal, entityId, parentId, agent)` with `@Transactional(propagation = MANDATORY)`, as `EntityBatch.*InTransaction`: the checks of the parent, the removal and creation of the automatic composition connection and the audit are shared with `setParent`, which keeps its advisory lock and its `If-Match` check (428, 412) and delegates. It does not check the version: the save checks it with the entity updates (409, D3). No second implementation.
  - `app.diagramview` → `app.hierarchy` is a new dependency; `app.hierarchy` uses only `app.entity`, `app.history` and `app.security`, and nothing in `app` uses `app.diagramview`, so no cycle arises.
  - `parent_shape_id` belongs to `core.diagram.DiagramShapeStore`, the lines to a new `core.diagram.DiagramHierarchyLineStore`. The global setting belongs to the viewpoints feature: `core.metamodel.DiagramSettingsStore` and the service `io.oea.app.metamodel.DiagramSettings` next to `Viewpoints` (`app.diagramview` → `app.metamodel` exists already).
- **ADR-032:** `getDiagramSettings` needs `entity:read` or `viewpoint:manage` (as `listViewpoints`), `updateDiagramSettings` `viewpoint:manage`, `listDiagramEntityChildren` `entity:read`, `saveDiagramView` keeps `entity:write` (a parent change needs no further permission, as `setEntityParent`).
- **ADR-013:** contract first; the conventions check applies (`items` envelope, `maxLength`, `UuidIdParam`).

## Decisions

### D1 Data

One migration `V51__diagram_nesting.sql` (the next free number; V50 is the last on `main`), run as `oea_owner`:

- `diagram_shapes.parent_shape_id UUID NULL REFERENCES diagram_shapes (id) ON DELETE SET NULL`, with a partial index on it. `SET NULL`, not `CASCADE`: the save deletes the removed shapes before it upserts the others, so a cascade would delete a nested shape that the request keeps under another container, together with its edges and the hidden edges kept by design D1 of `us-074-diagram-connections`, and it would delete the hidden shapes of deleted entities nested in a removed container. Removing a container removes its nested shapes because the editor leaves them out of the request (D4). The service checks the same diagram, the drawing order, the depth (≤ 10), cycles and containment.
- `width_pt` and `height_pt` change from `NUMERIC(6,2)` (at most 9999.99) to `NUMERIC(7,2)`, and their checks become `BETWEEN 1 AND 10000` (drop and re-add the generated constraints `diagram_shapes_width_pt_check` and `diagram_shapes_height_pt_check`).
- `diagram_hierarchy_lines (id UUID PRIMARY KEY, diagram_id UUID NOT NULL REFERENCES diagrams (id) ON DELETE CASCADE, parent_shape_id UUID NOT NULL REFERENCES diagram_shapes (id) ON DELETE CASCADE, child_shape_id UUID NOT NULL REFERENCES diagram_shapes (id) ON DELETE CASCADE, CHECK (parent_shape_id <> child_shape_id), UNIQUE (parent_shape_id, child_shape_id))`, index on `diagram_id`; `REVOKE ALL … FROM PUBLIC`, `GRANT SELECT, INSERT, UPDATE, DELETE … TO oea_app` as `diagram_edges`.
- `viewpoints.ask_new_parent BOOLEAN NULL`.
- The global setting: a single-row table `diagram_settings (id SMALLINT PRIMARY KEY CHECK (id = 1), ask_new_parent BOOLEAN NOT NULL DEFAULT true, version INTEGER NOT NULL DEFAULT 1, updated_at TIMESTAMPTZ NULL, updated_by BIGINT NULL REFERENCES persons (id))` with the row `(1)` inserted by the migration, as `visibility_settings` (V24); `REVOKE ALL … FROM PUBLIC`, `GRANT SELECT, UPDATE … TO oea_app` (no insert or delete).

### D2 Contract

- **`DiagramShapeEntity`:** `childCount` (direct children that are not deleted) and `parentEntityId` (nullable). The items of `searchDiagramEntities` (`DiagramEntity`) carry the same two fields.
- **`DiagramShape` / `DiagramShapeWrite`:** `parentShapeId` (uuid, nullable); `width` and `height` maximum 10 000 in both (the read schema too). The view returns `parentShapeId` only when the container is shown, comes earlier in the drawing order and contains the shape completely; otherwise null (a container of a deleted entity, or a stored nesting that a restore brings back after the container moved), so that the editor can always save what it read.
- **`DiagramView`:** `hierarchyLines` (`DiagramHierarchyLine`: `id`, `parentShapeId`, `childShapeId`) and `askNewParent` (boolean, effective).
- **`DiagramViewWrite`:** `hierarchyLines` (at most 4000, `DiagramHierarchyLineWrite`: `id`, `parentShapeId`, `childShapeId`) and `parentChanges` (at most 500, `DiagramParentChange`: `entityId`, `expectedVersion`, and at most one of `parentEntityId` and `parentNewEntityKey`; neither, or `parentEntityId` null, clears the parent; both set is 422). A parent change names an element entity that a shape of the same request shows, once (422 otherwise, as `entityUpdates`). `DiagramNewEntity` gets the optional `parentEntityId` and `parentNewEntityKey` (at most one, 422 for both or for its own key).
- **`listDiagramEntityChildren`:** `GET /views/diagrams/{id}/entities/{entityId}/children` (`entity:read`), items `DiagramEntityChild` (`entity` as `DiagramShapeEntity`, `allowed` – the viewpoint allows the type, `compositionConnection` – the not deleted connection of the composition metatype from the entity to the child, nullable, with `allowed` for its type), ordered by name case-insensitively, then ID, at most 500 with `truncated`. 404 for an unknown or deleted diagram, an unknown, deleted or connection entity.
- **`getDiagramSettings` / `updateDiagramSettings`:** `GET`/`PUT /admin/diagram-settings`, tag `Viewpoints` (schema `DiagramSettings`: `askNewParent`, `version` read-only; `DiagramSettingsWrite`: `askNewParent` required), `ETag` with the version, 429 as every implemented operation. Path decision: `/views/diagrams/settings` would sit beside `/views/diagrams/{id}`, and `/admin/viewpoints/settings` beside `/admin/viewpoints/{id}`; a literal segment next to a template depends on the router's precedence, so neither is used. `/metamodel/…` holds the `metamodel:write` operations. `/admin/diagram-settings` has no templated sibling and follows `/admin/property-visibility`, a setting of a configuration screen with its own permission. `PUT` needs `If-Match` (428, 412), audits `diagram.settings-updated` with old and new value; a write of the same value changes nothing (no new version) and audits nothing.
- **`ViewpointConfig` / `ViewpointWrite` / `PackageViewpoint`:** `askNewParent` (boolean or null). `updateViewpoint` replaces the viewpoint as a whole (as `sizes`), so an omitted value stores null; `viewpoint.updated` names it when it changes. In a package the field is optional (omitted = null); the format version stays `1.0` (an optional field, as `sizes`). The starter package `oea-starter-togaf-classic` (1.4.0) sets no value, its viewpoints keep the global setting, and its version does not change.
- **`saveDiagramView` description:** the new checks and their place in the documented order of the answers (only the first applies): 400 (also the new limits and two decimals), 403 of a scoping connection type, 412 or 428, 422 of the layout and the edges **with the structural 422 of nesting, lines and parent changes** (unknown, later or own container, depth, cycle, containment; a line between two different shapes of the request, each pair once; a parent change of an entity no shape shows, twice, with both parent fields, or of a connection), 403, 422 of a deleted entity (also of a parent change), 409 `entity-version-conflict` and 409 `entity-blocked` (entity updates and parent changes together, each conflicting entity named once), 422 of the changes, 403 and 422 of the new entities, **422 of the parents** (the rules of `setEntityParent`: unknown, deleted or connection parent, cycle, more than 10 levels; naming `parentChanges[i].parentEntityId` or `newEntities[i].parentEntityId`), **422 of the hierarchy lines** (a line whose child's parent is not the container's entity after the parent writes, naming `hierarchyLines[i]`), last 403 and 422 of the new connections. `diagram.layout-saved` carries `parentsChanged` (the entities whose parent the save changed, new entities with a parent included).

### D3 Save

The steps of the save's transaction, in the order of the answers (D2):

1. Lock the diagram; when the request carries parent changes or new entities with a parent, take the advisory lock of the hierarchy (`EntityHierarchyStore.lock`) right after it, before any entity lock. `setEntityParent` takes the hierarchy lock before the entity lock; taking it later in the save, after `updateInTransaction` locked the entities, could deadlock with a concurrent `setEntityParent`.
2. The structural checks (layout, edges, nesting, lines, parent changes), then the deleted entities, then the versions and blocks of entity updates and parent changes together. A parent change and an entity update of the same entity are allowed; both carry the version read.
3. The entity updates, then the new entities (`createInTransaction`).
4. The parents, through `EntityHierarchy.setParentInTransaction` with the save's agent: first the parents of new entities in the order of `newEntities`, then `parentChanges` in request order. A new entity gets its parent after it is created, so it ends at version 2 with `entity.created` and `entity.parent-changed`, like a create followed by `setEntityParent`; a parent change after an update of the same entity writes the version after the update. A change to the current parent writes nothing and is not counted.
5. The hierarchy lines are checked against the hierarchy after step 4.
6. The new connections, the shapes with `parent_shape_id` (in drawing order, so a container is upserted before its nested shapes), the edges, the lines (replaced as a whole), the version, the audit.

The view hides a hierarchy line whose shapes are hidden or whose hierarchy no longer holds; a save replaces the stored set, so such lines disappear on the next save (unlike the hidden edges, which are kept: a restore does not restore the parent, spec `repository/entity-hierarchy`, so such a line would not hold after a restore anyway). A shape whose container is not returned as its container (D2) is returned with `parentShapeId` null; when it is saved, it loses the nesting (documented in the manual). A hidden shape keeps its stored `parent_shape_id` until its container is removed (`SET NULL`).

### D4 Editor

- Nested shapes are Vue Flow nodes with `parentNode` and relative positions, converted to absolute pt on load and save; `expandParent` lets a container grow when a child is moved or resized beyond its edge; it never shrinks by itself.
- **Placing an existing entity with `childCount` > 0** asks "Show children" / "Only this entity". On "Show children" the editor loads `listDiagramEntityChildren` and lays out the children not yet on the diagram whose type is allowed in a grid inside the container (default sizes of the viewpoint, 10 pt padding, as many columns as make the container about square), and grows the container. Children already on the diagram get the composition connection as an edge where `compositionConnection` is set and allowed, else a hierarchy line. A notice names the children whose type is not allowed and, when the list is `truncated`, says that only the first 500 children were considered.
- **Drawing order:** a nested shape always follows its container. Nesting a shape moves it, with its own nested shapes, directly behind its new container; bringing a container to the front carries its nested shapes along.
- **Context menu "Show children"** on a shape with children does the same for an already placed shape.
- **Moving into or out of a container** (dropping a shape with its center inside another shape, or outside its container): when `askNewParent` is true and the entity's parent differs from the new container's entity (or none), the dialog "New parent?" names the new parent (or "none") with "Yes" and "No"; "Yes" records a parent change (sent with the next save), "No" changes only the nesting. A shape of a new entity changes the parent of its `newEntities` item instead. Structuring aids follow the same rules.
- **Palette drop into a container** nests the new shape and sets the parent of the new entity to the container's entity (or its new entity key).
- **Removing a container** confirms "Also removes N nested shapes from the diagram" and removes them and their edges and lines.
- Hierarchy lines are drawn dashed without an arrowhead, are selectable and removable, and have no details panel.
- **SCR-031:** a section "Diagram settings" above the viewpoint list with the switch "Ask 'New parent?' when moving shapes" (`viewpoint:manage` to change), and the field "Ask 'New parent?'" with "Global setting", "Ask" and "Do not ask" in the viewpoint dialog.

### D5 Documentation

The manual pages "Diagrams" and "Viewpoints" (en, de); SCREENS.md (SCR-021, SCR-031); the business object diagram (containers, hierarchy lines) and `docs/data-model.puml`; the business object viewpoint; the audit catalog (`diagram.settings-updated`, `parentsChanged`); US-074 and UC-10 realigned; the sprint plan status of change 16.

## Risks / Trade-offs

- **Absolute positions with containment:** a client must convert relative Vue Flow positions; the containment check catches mistakes. Rounding to two decimals can push a child 0.01 pt out: the editor rounds the container outward.
- **Lost nesting under a deleted container:** accepted for v1.0; the restore brings back the container but not the nesting of shapes saved meanwhile.
- **Larger shapes:** 10 000 pt allow big containers; the default sizes stay at 2000.

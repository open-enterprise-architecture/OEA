# Design

## Context

- **The canvas (change 11):**
  - `app.diagramview.DiagramViews` reads the view and saves the layout atomically.
  - It creates new entities through `EntityBatch.createInTransaction` with `Necessity.WARN`.
  - Shapes carry `missingMandatory` (names of the mandatory properties without a value, computed).
- **Entity writes:** they run through `PropertyWriteAccess`, `MetatypeRules`, `EntityValidator` and `EntityWriter`, with history and audit. The batch is last-writer-wins on the entity version (#69) and finds existing entities only by `externalId` (`EntityStore.lockByExternalId`).
- **Existing entity version checks:** `setEntityParent`, `deleteStructuringAid` and `restoreEntity` already check the entity version with `If-Match` (412 `stale-version`, `StaleEntityVersionException`). An interactive write of name, description and values has none yet.
- **Open issues:** an entity with an open issue of a solution is blocked; every write other than resolving the issues is rejected with 409 `entity-blocked` (solution/solution-go-live, "Entities blocked by open issues"; `EntityBlocks.check`), except the batch, which reports the item error `OPEN_ISSUES`.
- **SCR-061:** the dialog "Modify entity" (`SolutionModifyDialog.vue`) reads the entity with `getSolutionLandscapeEntity` and the field description with `getMetatype` (`propertyMappings`: definition, data type, enum values, limits, referenced metatype, necessity, state). `PropertyInput.vue` renders one input per data type; its reference search is bound to the solution baseline (`solutionId`). The dialog does not know which properties are writable; the server answers 403.

## Goals / Non-Goals

**Goals:**

- Reading and changing name, description and values of shown entities on the canvas.
- Entering values of new entities.
- Completing incomplete ones.
- An optimistic lock per entity for this interactive write.

**Non-Goals:**

- Editing elsewhere (`patchEntity` stays planned).
- Connections and their properties (change 13).
- Deletion (change 14).

## Architecture Constraints

- **ADR-051:** `app.diagramview` may depend on `app.entity`; nothing in `app` depends on `app.diagramview`. SQL stays in `core`; the locks use the existing `EntityStore.lock`.
- **ADR-032 and REQ-160 to REQ-162:**
  - The read applies the read projection: redacted values.
  - The write applies the write guard: 403 for a restricted property without a write role, as a whole request (repository/entity-write, "Writing restricted properties").
- **ADR-033:** `entity.updated` per changed entity, exactly as in the batch (written by `EntityWriter`, agent headers included); `diagram.layout-saved` as before.

## Decisions

### D1 Read `getDiagramEntityDetails`

- `GET /views/diagrams/{id}/entities/{entityId}`, `entity:read`. `id` is `UuidIdParam`; `entityId` is an inline integer path parameter with minimum 1 (as in `getSolutionLandscapeEntity`; the conventions check only governs parameters named `id`).
- **404:** an unknown or deleted diagram, an unknown or deleted entity, or an entity without a shown shape in the diagram.
- **The answer `DiagramEntityDetails`:**
  - `id`, `name`, `description`, `metatype` (`MetatypeSummary`) and `version`;
  - `properties`: `PropertyValue` items with the redaction of the other reads (the values the entity has);
  - `fields`: one entry per active mapped property of the metatype with `propertyDefId`, `readable` and `writable` – whether this caller may read and change the value of this entity. `writable` is `false` for every property without `entity:write`, and otherwise follows `PropertyAccess.Snapshot` and `PropertyWriteAccess.writer` (role-restricted: a write role; connection-scoped: the entity in scope);
  - `missingMandatory`: names, as `DiagramShape.missingMandatory`.
- **No duplicated field description:** data type, enum values, limits, referenced metatype and necessity are not repeated in the answer. The panel takes them from `getMetatype` (`entity:read`), as the SCR-061 dialog does; for a new entity it only has that source anyway.
- It is not audited, as for the other reads, except for the `property-override-access` audit of override roles, as in every read projection.

### D1a Reference candidates `searchDiagramReferenceTargets`

A reference value needs a choice of entities; `PropertyInput.vue` searches the solution baseline, which the canvas does not have, and `searchDiagramEntities` is limited to the metatypes of the viewpoint.

- `GET /views/diagrams/{id}/reference-targets?propertyDefId=&q=&page=&pageSize=`, `entity:read`, answering `DiagramEntityPage`.
- The active element entities of exactly the referenced metatype of the reference property `propertyDefId` (the entity write accepts only that metatype), by name ignoring case, then ID, with the query parameters and limits of `searchDiagramEntities` (`q` at most 255, `pageSize` 1–200, `page` ≥ 0; 400 otherwise).
- 422 naming `propertyDefId` for an unknown property definition or one that is not of data type `reference`; 404 for an unknown or deleted diagram. Reuses `EntitySearchStore.count` and `page`. Not audited.

### D2 Save with entity changes

`DiagramViewWrite` gains:

- `entityUpdates` (`maxItems` 500), each `DiagramEntityUpdate` with:
  - `entityId` (integer ≥ 1);
  - `expectedVersion` (integer ≥ 1);
  - optional `name` (`maxLength` 255; blank gives 422 `REQUIRED`), `description` (`maxLength` 2000) and `properties` (`PropertyWriteValue` items; an empty `value` removes the value).

  A field or property not named keeps its value, as in the batch.
- In `DiagramNewEntity`: optional `description` (`maxLength` 2000) and `properties` (`PropertyWriteValue`).
- Reusing `PropertyWriteValue` keeps its existing baseline entry `PropertyWriteValue.value` in `check_openapi_conventions.py`; the new string properties carry `maxLength`, so the allowlists do not grow.

The save, in its transaction (`DiagramViews.save`):

1. The diagram row is locked and its `If-Match` checked (412/428, as before); the shape and new-entity checks run (422, as before).
2. Each entity update names an entity shown by a shape of the request (`entityId`), at most once; otherwise 422 naming `entityUpdates[i].entityId` (`ENTITY_NOT_SHOWN`, `DUPLICATE_ENTITY`).
3. `EntityBatch.updateInTransaction(principal, updates, agent)` (new, propagation `MANDATORY` like `createInTransaction`, `IllegalTransactionStateException` without a transaction):
   1. **403** – `PropertyWriteAccess.require(principal, properties, entityId)` for every update before anything is written; a restricted property without write access rejects the whole save (the denial is audited by the handler, as for the batch).
   2. **Locks** – `EntityStore.lock` per entity in ascending ID order; a deleted entity gives 422 `ENTITY_NOT_FOUND`.
   3. **409 `entity-version-conflict`** – every locked entity whose version differs from `expectedVersion` is collected; any gives 409 with all of them.
   4. **409 `entity-blocked`** – `EntityBlocks.check(principal, ids)`: an entity with an open issue of a solution, with the extension `entities` as for restore and hierarchy writes (solution/solution-go-live). The batch's item error `OPEN_ISSUES` is not used, because this is not a batch.
   5. **Validation** – the input is merged with the stored entity (missing name and metatype taken from it, `externalId`, `logical` and the parent unchanged) and checked with `EntityValidator.check(…, Necessity.ENFORCE)` against `MetatypeRules.forWriter`. All issues of all updates are collected and thrown as one `AtomicBatchFailedException` (422, paths `entityUpdates[i]...`, e.g. `MANDATORY_MISSING` on `entityUpdates[i].properties`). Structuring aids give `METATYPE_NOT_ALLOWED`, as in the batch.
   6. **Write** – `EntityWriter.write` with one `WriteContext` (batch ID, agent): next version, history, `entity.updated`, clearing the marks – identical to a batch update. An update without an effective change leaves the entity unchanged (no version, no audit); its version is still checked.
4. The new entities are created with their description and values through `createInTransaction` (`Necessity.WARN`, as in change 11; a connection-scoped value of a new entity gives 403, as in the batch).
5. The shapes are replaced, the diagram version rises and `diagram.layout-saved` is audited, unchanged. A save with only entity changes also raises the diagram version.

Any failure rolls back everything. The response is the new view, so `missingMandatory` reflects the completed entities.

- **Order of the answers:** 400 (contract), 412/428, 422 of the layout, 403, 422 of a deleted entity, 409 `entity-version-conflict`, 409 `entity-blocked`, 422 of the entity updates, then the 403 and 422 of the new entities. Only the first applies.
- **409 problem:** `urn:oea:problem:entity-version-conflict`, schema `DiagramEntityConflictProblem` (`Problem` plus `invalidFields` with `entityUpdates[i]` and `conflicts`, a list of `{entityId, currentVersion}`). The 409 of `saveDiagramView` documents both problem types.
- **Why 409 and not 412:** the request's `If-Match` names the diagram version; the entity versions are in the body. A body precondition is a conflict of state (409), as #69 proposes.
- **Necessity and restricted mandatory properties:** a mandatory property the writer cannot change is not checked (repository/entity-write); such an entity stays incomplete after the save, and the panel shows the property as required but not changeable.
- **Body size:** the 2 MB limit of the save path stays. 2000 shapes, 500 new entities and 500 updates, each with a name of 255 and a description of 2000 ASCII characters, are about 2.6 MB and do not fit; property values have no fixed upper size. The client therefore limits the changes it collects (D3), and a larger body gets 413 as before. The backend test checks that 500 updates each with a name of 255 and a description of 2000 characters and five values of 50 characters, with 500 shapes, are accepted (about 1.6 MB).
- **Locking order and risk:** the diagram row first, then entity rows in ascending ID. An atomic batch locks its entities in item order; a deadlock between the two is detected by PostgreSQL and fails one request, which is retried by the user.

### D3 Panel (frontend)

- **`EntityDetailsPanel.vue`:** a side panel of the canvas page. It opens on selection; with several shapes of the same entity, it shows that one entity.
  - It loads `getDiagramEntityDetails` and `getMetatype` for saved entities, and only `getMetatype` plus the local state for new ones.
  - It shows name, description, metatype, version and the fields of the metatype (active mappings in their order), with required fields marked and missing ones named.
  - Input fields appear only where `writable` (for a new entity, which has no details read: every field except connection-scoped ones, as in the SCR-061 dialog "New entity"; a role-restricted value without a write role gives 403 on save); redacted values show as "Restricted".
- **The form** reuses `PropertyInput.vue`: it moves from `components/solutions/` to a shared place and gets a reference search function as a prop instead of `solutionId`; SCR-061 passes the baseline search, the canvas `searchDiagramReferenceTargets`. No second input component is written.
- **The canvas model** keeps the edits per entity (only changed fields) with the version read, marks the diagram as dirty, and adds `entityUpdates` and the new entities' description and values to `writeRequest`. Like `placementBlock`, it refuses an edit of a 501st entity with a notice. After a successful save the edits are cleared and an open panel reads the details again (the new version).
- **On 409 `entity-version-conflict`:** the editor names the entities and offers "Reload entity" per entity, which reads the details again; local changes of that entity are discarded on confirmation, the other changes stay.
- **On 409 `entity-blocked`:** the editor names the entity and its open issues; the changes stay.
- **On 422 or 403:** the fields of `entityUpdates[i]` / `newEntities[i]` and the shapes are marked with the reason (`rejectedShapes` extended); 403 gives a general message, as on SCR-061.
- **On 413:** a message to save fewer changes at once.
- **Keyboard:** the panel is reachable by `Tab` from the selected shape, and `Escape` closes it.

### D4 Documentation

- **The manual "Diagrams" (en/de):** the details panel, completing incomplete entities, and conflicts.
- **SCREENS.md:** the SCR-021 description, with the story extended by the panel.
- **US-074 and UC-10:** step 6 (property values; the person assignment of user tasks stays with US-075).
- **The sprint plan:** the status of change 12.
- **#69:** closed by the pull request (`Closes #69`).

## Data Model

No change. The entity version already exists.

## API

- New: `getDiagramEntityDetails` (`DiagramEntityDetails`, `DiagramEntityField`), `searchDiagramReferenceTargets`.
- Changed: `saveDiagramView` (description, 409 with `entity-version-conflict` and `entity-blocked`), `DiagramViewWrite.entityUpdates` (`DiagramEntityUpdate`), `DiagramNewEntity.description` and `properties`; new `DiagramEntityConflictProblem`.

## Risks / Trade-offs

- **[Risk]** Locking up to 500 entity rows in one save.
  - **Mitigation:** the rows are locked in ID order, and the save is short.
- **[Trade-off]** A version conflict on one entity rejects the whole save. The user keeps the local changes and reloads only the conflicting entities.
- **[Trade-off]** The diagram version rises with every save, also one that changes only entity values; the `If-Match` flow stays one rule.

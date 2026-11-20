# Proposal

## Why

Since change 11 (`us-074-diagram-canvas`), entities are placed on the canvas, and new ones may be created without their mandatory values. The owner decided on 2026-11-20 that they are marked as incomplete "until someone enters the values". No screen lets anyone enter or change values of an entity yet; only the batch API writes them. UC-10 step 6 also asks for a detail panel of a selected element.

Interactive edits need the optimistic lock on the entity version that issue [#69](https://github.com/open-enterprise-architecture/OEA/issues/69) deferred: the batch writes last-writer-wins, and the canvas is the first interactive entity write.

This is S17 change 12 ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-10](../../../requirements/use-cases/UC-10-model-business-processes.md) step 6 |
| User Story | [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) |
| Issue | [#69](https://github.com/open-enterprise-architecture/OEA/issues/69) (optimistic locking for interactive entity edits) |
| Requirements | [REQ-160](../../../requirements/req/REQ-160-visibility-on-all-read-channels.md) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions used:**

- **2026-11-19:** "Save" saves explicitly when working alone; `entity:write` edits.
- **2026-11-20:** an entity created on the canvas without mandatory values is marked as incomplete until the values are written; every other write requires them.

**Decisions and assumptions of this change (open for review, details in the design):**

- **Reading details:** a new read `getDiagramEntityDetails` (`GET /views/diagrams/{id}/entities/{entityId}`, `entity:read`) returns name, description, metatype, version, the values (redacted as in the other reads), per mapped property whether the reader may read and change it, and the missing mandatory properties. Only entities shown in the diagram can be read this way (404 otherwise). Data types, enum values and necessity come from `getMetatype`, as in the SCR-061 dialog, and are not repeated.
- **Reference values:** a new read `searchDiagramReferenceTargets` (`GET /views/diagrams/{id}/reference-targets`, `entity:read`) lists the entities a reference property may point to, because the existing search of the canvas is limited to the viewpoint and the one of SCR-061 to a solution.
- **Writing:**
  - `saveDiagramView` gets `entityUpdates`, at most 500: entity ID, expected version, and optionally name, description and property values.
  - `newEntities` get optional description and property values.
  - The updates are written by a new `EntityBatch.updateInTransaction` in the save's transaction with the steps of a batch update: metamodel checks, property visibility (403 for the whole save), history and audit `entity.updated`.
  - An entity with an open issue of a solution gives 409 `entity-blocked`, as every write other than the batch (solution/solution-go-live).
  - Mandatory values are required (strict) for changed entities, so an incomplete entity must be completed when it is changed.
  - New entities keep the incomplete rule of change 11.
- **Version check (#69):** changed entities whose version differs from the expected one reject the whole save with 409 `entity-version-conflict`, naming each of them with its current version. The batch API stays last-writer-wins. This closes #69.
- **Panel:**
  - it opens on the selected shape and shows the form of the metatype with the inputs of the SCR-061 dialog "Modify entity" (`PropertyInput.vue`, shared);
  - the changes join the unsaved changes of the diagram;
  - it is read-only for readers.

**Not in this change:**

- editing outside the canvas (catalog, entity page);
- connections and their properties (change 13);
- deleting entities (change 14).

## Capabilities

### New Capabilities

- `diagram/diagram-entity-details`: the details of a shown entity, changing entities with the diagram, the entity version check for interactive edits, and the details panel.

### Modified Capabilities

None. `diagram/diagram-canvas` keeps its requirements; the save gains the entity changes defined in the new capability.

## Impact

- **Contract:** `getDiagramEntityDetails`, `searchDiagramReferenceTargets`, `entityUpdates` and the values of `newEntities` in `DiagramViewWrite`, and the 409 problems `entity-version-conflict` and `entity-blocked` of `saveDiagramView`.
- **Backend:**
  - `app.diagramview` (read, reference search and save);
  - `app.entity`: `EntityBatch.updateInTransaction`, an update by ID in the caller's transaction with the expected version;
  - `core.entity`: no new SQL (`EntityStore.lock`, `EntitySearchStore`).
- **Frontend:**
  - `EntityDetailsPanel.vue` on the canvas page, with `PropertyInput.vue` of SCR-061 moved to a shared place and its reference search passed in;
  - the canvas model (dirty state, the save body);
  - the 409, 413 and 422 handling, stories and fakes.
- **Documentation:**
  - the manual "Diagrams" (en/de);
  - SCREENS.md (SCR-021 with the panel);
  - US-074 and UC-10;
  - the sprint plan;
  - #69 closed by the pull request.

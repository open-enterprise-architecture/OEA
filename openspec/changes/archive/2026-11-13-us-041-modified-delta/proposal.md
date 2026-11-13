# Proposal

## Why

Since change 2, a solution can hold new and retiring entities. Most initiatives, though, change entities that already exist: a new release, another owner, a changed interface ([UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md)). [US-041](../../../requirements/user-stories/US-041-delta-modified.md) adds the delta `modified`, with the before and after values of each changed field. It is S11 change 3. The diff view of change 4 builds on it.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-05: Describe the Architecture Vision of a Change Initiative](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) |
| User Stories | [US-041: Capture an Existing Entity in a Solution as Modified](../../../requirements/user-stories/US-041-delta-modified.md) (AC1–AC5, realigned) |
| Requirements | [REQ-040: Capture the EntityDeltas of a solution](../../../requirements/req/REQ-040-capture-entity-deltas.md) (`modified`, AC3, AC5, AC6; editing a delta) |
| ADRs | [ADR-022: Structured property model](../../../adrs/ADR-022-structured-property-model.md), [ADR-028: Backend layered architecture](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md), [ADR-033: Audit trail](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-015: Database migration](../../../adrs/ADR-015-db-migration.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S11 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-12 (change 3):**

- **Before value:** the server records the before value when the delta is captured. It is the current value in the catalog at that moment and cannot be entered. Only the after value is entered.
- **Fields:** a delta `modified` changes property values, the name and the description of an entity. The metatype and `isLogical` stay as they are.
- **Editing:** a delta `modified` can be edited in place (`updateSolutionDelta`, new). The action is "Edit" in the delta list.
- **Reading an entity:** the form reads the current values of an entity through a new entry read of the baseline, `getSolutionLandscapeEntity`, which applies the property visibility rules.

**Capturing a modification (`createSolutionDelta`, `deltaType=modified`):**

- **Target:** the ID of an entity of the baseline. Any other ID answers "Entity not found in the current landscape", as for `retiring`.
- **Changes:** a new name, a new description and property values, each optional. An empty property value removes the value.
- **Before values:**
  - each change stores the current value of its field as the before value;
  - a field whose after value equals its before value is not a change;
  - a delta without any change answers 422.
- **Checks:**
  - checked like an update of the entity: name of 1–255 characters, description of at most 2000 characters, values by data type and constraint;
  - removing the value of a mandatory property is rejected.
- **Visibility:**
  - a value of a `role-restricted` property needs a write role (403);
  - a `connection-scoped` value needs the entity in the writer's scope (403), as for a write of an existing entity.
- **One delta per entity:** a second delta for the same entity, `retiring` or `modified`, answers "A delta already exists for this entity in this solution".
- **Audit:** `solution.delta-added` names the changed fields, not their values.

**Editing a modification (`updateSolutionDelta`, new, `solution:write`):**

- replaces the after values of a delta `modified`, with the same checks;
- a field that was already part of the delta keeps its recorded before value, and a newly added field records the current one;
- a delta of another type answers 422 naming `deltaType`, and a completed solution answers 409;
- audited as `solution.delta-updated`.

**Reading:**

- **Delta list:** `listSolutionDeltas` returns a delta `modified` with its changes: field, before value and after value. A value the reader may not read is redacted.
- **Entry read (`getSolutionLandscapeEntity`, new):** `GET /solutions/{id}/landscape/{entityId}` with `entity:read` returns name, description, metatype, version and the readable property values of an entity of the baseline. It answers 404 for any other ID.

**Frontend (SCR-061):**

- **Baseline:**
  - the action "Modify" per row, next to "Retire";
  - the marker "Modified" for an entity with such a delta.
- **Dialog "Modify entity":**
  - per field, the current value (read-only) and an input for the new value;
  - only changed fields are sent;
  - the same dialog edits a delta `modified` from the delta list ("Edit").
- **Delta list:** the type "Modified" with the number of changed fields.

**Realigned sections:**

- **US-041 AC1 and REQ-040 AC3:** `version` is the lock counter of an entity, not a property, so the example changes a property, "Release" from `v4` to `v5`.
- **US-041 AC2:** the before value is recorded by the server and shown in the form, not prefilled for editing.
- **US-041 AC4:** also covers `archived`; the message reads "This solution is completed and can no longer be changed".
- **US-041 AC5:** the message reads "A delta already exists for this entity in this solution".
- **US-041 Technical Notes:**
  - request body `{deltaType: "modified", entityId: integer, name?, description?, properties: PropertyWriteValue[]}` with after values only;
  - the response carries `changes` with before and after values.
- **REQ-040 Implementation Notes:** `PUT /solutions/{id}/deltas/{deltaId}` for a delta `modified`; the `changes` map is a list of changes with typed values.

## Out of Scope

- **Editing deltas of other types:** deltas `new` and `retiring` are still removed and captured again.
- **Drift detection:** a before value that no longer matches the catalog belongs to the diff view (change 4) and the go-live (S14).
- **Changing the metatype or `isLogical`.**
- **Connections as deltas.**
- **Optimistic locking of a delta:** concurrent edits of the same delta keep the last write.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `solution/solution-deltas`:
  - the delta `modified` with recorded before values;
  - editing it;
  - its rules, visibility and audit;
  - "Modify" and "Edit" on the page "Solution".
- `solution/solution-baseline`: reading one entry of the baseline with its values (`getSolutionLandscapeEntity`).

## Impact

- **`api/openapi.yaml`:**
  - the new operations `updateSolutionDelta` and `getSolutionLandscapeEntity`;
  - `SolutionDeltaType` gains `modified`;
  - `SolutionDeltaWrite` gets the field rules of `modified`;
  - `SolutionDelta` gains `changes`;
  - the new schemas `SolutionDeltaChange` and `SolutionLandscapeEntity`.
- **`backend/`:**
  - migration `V27__entity_delta_changes.sql` with the before columns and `system_field`;
  - `SolutionDeltas` and `SolutionLandscape` extended;
  - `PropertyWriteAccess` accepts an entity ID;
  - `SolutionController` extended;
  - the audit event `solution.delta-updated`;
  - integration and Cucumber tests.
- **`frontend/`:**
  - the dialog "Modify entity", "Modify" in the baseline and "Edit" in the delta list, with the API client and i18n;
  - the stories of SCR-061 with their images.
- **`manual/`:** the page on solutions, in English and German.
- **Documentation:**
  - US-041 and REQ-040;
  - `business-objects/solution.md` and `docs/data-model.puml`;
  - `docs/audit-event-catalog.md`;
  - `docs/screens/SCREENS.md`;
  - the sprint plan.

# Proposal

## Why

A solution describes the changes of an initiative against the current landscape ([UC-05](../../../requirements/use-cases/UC-05-describe-architecture-vision.md)). Since change 1 of S11 a solution shows its baseline, but it cannot hold a change yet. This change adds the first two kinds of entity deltas: a new entity the initiative introduces ([US-040](../../../requirements/user-stories/US-040-delta-new-entity.md)) and an existing entity it retires ([US-042](../../../requirements/user-stories/US-042-delta-retiring.md)). It is S11 change 2, and the diff view and the conflict warning build on it.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-05: Describe the Architecture Vision of a Change Initiative](../../../requirements/use-cases/UC-05-describe-architecture-vision.md) |
| User Stories | [US-040: Create a New Entity in the Catalog as a Delta](../../../requirements/user-stories/US-040-delta-new-entity.md) (AC1–AC4, realigned), [US-042: Mark an Existing Entity in a Solution as Retiring](../../../requirements/user-stories/US-042-delta-retiring.md) (AC1–AC4, realigned) |
| Requirements | [REQ-040: Capture the EntityDeltas of a solution](../../../requirements/req/REQ-040-capture-entity-deltas.md) (`new` through the catalog path, `retiring`, removal; `modified` follows in change 3, the diagram path in S15–S17) |
| ADRs | [ADR-028: Backend layered architecture](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md), [ADR-033: Audit trail](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-022: Structured property model](../../../adrs/ADR-022-structured-property-model.md), [ADR-015: Database migration](../../../adrs/ADR-015-db-migration.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S11 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-12 (change 2):**

- **Who:** anyone with `solution:write` captures and removes deltas, in any solution, not only the owner of the solution.
- **Content of a `new` delta:**
  - the metatype, the name and the description;
  - the property values, checked against the metamodel like a write of an entity, and subject to the property visibility rules.
- **Removal:** a delta can be removed again. Removal is audited.
- **Locked statuses:** a solution in `implemented` or `archived` accepts no change to its deltas (409 "This solution is completed and can no longer be changed"). Every other status accepts it.

**Capturing deltas (`createSolutionDelta`, new, `solution:write`):**

- **`new`:**
  - an element metatype, a name and an optional description, with property values;
  - checked like a new entity: name of 1–255 characters, description of at most 2000 characters, the values by data type and constraint, mandatory properties present;
  - a value of a `role-restricted` property needs a write role (403); a `connection-scoped` property cannot be set, because the entity does not exist yet (403);
  - no entity row is written before the go-live (S14).
- **`retiring`:** the ID of an entity of the baseline (an active entity of an element metatype). Any other ID answers "Entity not found in the current landscape".
- **One delta per entity and solution:**
  - a second `retiring` delta for the same entity answers "A delta already exists for this entity in this solution";
  - the same applies to a second `new` delta with the same metatype and the same name (case-insensitive).
- **Audit:** `solution.delta-added` with the solution, the delta, its type, and the entity ID or the metatype and name. Property values are not part of the event.

**Reading deltas (`listSolutionDeltas`, new, `entity:read`):**

- all deltas of the solution, `new` before `retiring`, then by name;
- each with type, name, metatype, the entity ID for `retiring`, and the description and property values for `new`;
- a value the reader may not read is shown as redacted, like the entity read.

**Removing a delta (`deleteSolutionDelta`, new, `solution:write`):** removes the delta and its values; audited as `solution.delta-removed`; 404 for a delta of another solution.

**Limit:** at most 1000 deltas per solution; the 1001st answers a validation error. This keeps the unpaged delta list bounded.

**Frontend:** the page "Solution" (SCR-061):

- **area "Changes":**
  - the delta list with type, name and metatype;
  - the action "Remove" with a confirmation;
  - the button "New entity", which opens a dialog with metatype, name, description and the property fields of the chosen metatype;
- **area "Baseline":** an action "Retire" per row, and the marker "Retiring" for an entity that already has a delta;
- **access:** the write actions only for `solution:write` and only while the solution is not completed;
- **empty landscape:** the notice in the empty landscape stays. "Retire" needs a row, so it is not offered there.

**Realigned sections:**

- **US-040 AC1:** "the area 'Create new entity'" becomes the dialog "New entity" on SCR-061.
- **US-040 AC1 and AC3:** a `new` delta is not in the baseline; a second `new` delta with the same metatype and name is the duplicate.
- **US-040 Technical Notes:**
  - request body `{deltaType: "new", metatypeId, name, description, properties: PropertyWriteValue[]}`;
  - no UUID is assigned to the future entity; the go-live (S14) creates the entity row.
- **US-042 AC3:** a `new` delta describes a future entity of its own, never one of the baseline. The duplicate is a second `retiring` delta for the same entity. A `modified` delta for the same entity follows with change 3.
- **US-042 Technical Notes:** request body `{deltaType: "retiring", entityId: integer}` (entity IDs are integers).
- **US-040 AC2 and US-042 AC4, REQ-040 AC4:** also `archived`, not only `implemented`. The message reads "This solution is completed and can no longer be changed".
- **REQ-040:**
  - Implementation Notes: `POST`, `GET` and `DELETE /solutions/{id}/deltas`; editing a delta (`PUT`) is removal and capture again;
  - Context: the separate tables `entity_deltas` and `entity_delta_property_values`;
  - Statement: "the solution architect" becomes anyone with `solution:write`.
- **`business-objects/solution.md`:**
  - `EntityDelta.entityId` is null for `new` until the go-live;
  - `new` carries metatype, name, description and property values.

**Sprint plan:** the decisions of change 2.

## Out of Scope

- **`modified`:** the delta `modified` with before and after values follows in change 3 (US-041).
- **Diagram path:** the diagram path of REQ-040 with name suggestions follows with US-045 in S15–S17.
- **Connections:** connections as deltas, and so `connection-scoped` values of a new entity, come when connection deltas are specified.
- **Further fields of a `new` delta:** `isLogical`, lifecycle, parent and external ID. The go-live (S14) sets the defaults of an entity.
- **Editing a delta in place:** a delta is removed and captured again.
- **Status transitions of a solution:** these follow in S12. Until then, a solution reaches `implemented` or `archived` only in tests.

## Capabilities

### New Capabilities

- `solution/solution-deltas`: the entity deltas of a solution in project mode, `new` and `retiring`:
  - capturing them, reading them and removing them through the API;
  - their checks and limits, and their audit;
  - the area "Changes" and the action "Retire" on the page "Solution".

### Modified Capabilities

None.

## Impact

- **`api/openapi.yaml`:**
  - the new operations `createSolutionDelta`, `listSolutionDeltas` and `deleteSolutionDelta` (tag `Solutions`);
  - the schemas `SolutionDeltaWrite`, `SolutionDelta` and `SolutionDeltaList`;
  - the 409 response of a completed solution.
- **`backend/`:**
  - migration `V26__entity_deltas.sql`;
  - `app/solution/SolutionDeltas`, with the property write check shared with the batch write;
  - `SolutionController` extended;
  - the audit events `solution.delta-added` and `solution.delta-removed`;
  - integration and Cucumber tests.
- **`frontend/`:**
  - the area "Changes", the dialog "New entity" and the action "Retire" on SCR-061, with the API client and i18n in English and German;
  - the stories of SCR-061, with their images.
- **`manual/`:** the page on solutions, in English and German.
- **Documentation:**
  - US-040, US-042 and REQ-040;
  - `business-objects/solution.md` and `docs/data-model.puml`;
  - `docs/audit-event-catalog.md`;
  - `docs/screens/SCREENS.md` (SCR-061);
  - the sprint plan.

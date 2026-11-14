# Proposal

## Why

The timeline of change 1 (`us-098-entity-history`) names the fields a version changed, but not their values. Architects cannot see what a change did, and auditors cannot prove how an entity looked at a given time ([UC-14](../../../requirements/use-cases/UC-14-view-change-history.md)). [US-099](../../../requirements/user-stories/US-099-version-history-diff.md) adds the field-by-field diff and the comparison of two versions, and [US-100](../../../requirements/user-stories/US-100-version-history-snapshot.md) adds the complete state of a version. This is S12 change 2.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-14: View Change History](../../../requirements/use-cases/UC-14-view-change-history.md) |
| User Stories | [US-099: Show Field-by-Field Diff Between Versions](../../../requirements/user-stories/US-099-version-history-diff.md) (AC1, AC3; AC2 realigned), [US-100: Retrieve the Complete Historical Entity State](../../../requirements/user-stories/US-100-version-history-snapshot.md) (AC1–AC3; AC2 and AC3 realigned) |
| Requirements | [REQ-099: Version history field-by-field diff](../../../requirements/req/REQ-099-version-history-diff-view.md) (statement and implementation notes, realigned), [REQ-100: Historical entity state as an immutable snapshot](../../../requirements/req/REQ-100-version-history-snapshot-retrieval.md) (statement, AC2, AC3 and implementation notes, realigned), [REQ-098: Version history timeline](../../../requirements/req/REQ-098-version-history-timeline.md), [REQ-160: Visibility on all read channels](../../../requirements/req/REQ-160-visibility-on-all-read-channels.md), [REQ-128: Admin override](../../../requirements/req/REQ-128-property-visibility-admin-override.md) |
| ADRs | [ADR-022: Structured property model and delta versioning](../../../adrs/ADR-022-structured-property-model.md), [ADR-028: Backend layered architecture](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S12 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-14:**

- **Field names:** the diff names a field by its translated label, as the timeline does. Each property is a field of its own, never part of a JSON blob. The dot notation `properties.owner` of US-099 AC2 is dropped.
- **Comparing two versions** is a net diff: the state of the earlier version against the state of the later one. A field that was changed and later changed back does not appear.
- **Complete state:** it contains the properties mapped to the entity's metatype at the time of the version, including empty ones. Every property that had a value then is added.
- **Screen:** everything is shown inline on SCR-026.
  - Clicking a version expands its diff, with "Show complete state vN".
  - A "Compare" mode selects two versions and shows their diff in a panel on the same page.

**States are reconstructed, not stored:** ADR-022 records deltas only. The history tables hold the value *before* each change, so the state of a version follows backwards from the current state. REQ-100's `entity_versions.snapshot` does not exist and is realigned. Nothing is copied or stored.

**Reading versions (`repository`):**

- **`getEntityVersion`** (`GET /entities/{id}/versions/{version}`, `entity:read`) returns the complete state of a version:
  - its number, time and person;
  - the system fields;
  - the properties by the rule above, each with label, data type and value (empty properties without value).
- **`compareEntityVersions`** (`GET /entities/{id}/diff?from=&to=`, `entity:read`) returns the changed fields between two versions, each as a `FieldDiff` with old and new value.
  - The diff of a single version N is the comparison of N−1 with N.
  - `from` must be at least 1 and before `to`, otherwise the request is answered with 400.
  - An unknown version is answered with 404.
- **Values the reader may not read:** they are never returned, by the rules of the timeline (current visibility, override role).
  - Such a field appears as restricted (`redacted`, without values), or is left out when the history visibility of the reader's roles is "hide" for the metatype.
  - A value read only through an override role is audited as `property-override-access` with the new channel `history`.
- **Immutable versions:** the version resource has no write method. `PUT` and `DELETE` on `/entities/{id}/versions/{version}` are answered with 405 and `Allow: GET`.

**Frontend (SCR-026):**

- A version card expands on click and shows its diff:
  - the old value in red and struck through;
  - the new value in green;
  - "Restricted" without values.
- "Show complete state vN" lists all fields of the version; empty properties are shown as "—".
- "Compare" switches the cards to selection. With two versions chosen, a panel shows their net diff.
- The story of SCR-026 gains the states "diff", "complete state" and "compare".

**Realigned sections:**

- **US-099 AC2:** "properties.owner: 'Kurt' → 'Michael'" becomes "Owner: Kurt → Michael", where each property is a field of its own.
- **US-099 AC3 and REQ-099 AC3:** the cumulative diff becomes the net diff of the two states.
- **REQ-099 statement:** a version is compared with its *predecessor*, not its successor; fields are named by label.
- **REQ-100:**
  - statement and implementation notes: reconstruction from the delta tables, with the property set from the mappings at the time instead of frozen `propertyDefinitions`;
  - AC2 and AC3: the path `/entity-versions/{id}` becomes `/entities/{id}/versions/{version}`.

## Out of Scope

- Restoring a version, in full or in part (UC-15, UC-16; S13).
- Historic visibility rules: visibility is decided with the current configuration, as in the timeline.
- Mappings that a package re-import removed: they are not recorded, so an empty property of such a mapping is missing from an old state. A property with a value then is still listed.
- A comparison across entities, or an export of a state.
- The portal screen SCR-054.

## Capabilities

### New Capabilities

- `repository/entity-versions`: the complete state of a version, the field-by-field diff of a version and the net comparison of two versions, their visibility rules and immutability, and how SCR-026 shows them.

### Modified Capabilities

- None. `repository/entity-history` stays as it is: the timeline names fields without values, and the values come from the new capability.

## Impact

- **api/openapi.yaml:**
  - the new operations `getEntityVersion` and `compareEntityVersions` (tag `Entities`);
  - the new schemas `EntityVersionState`, `EntityVersionField` and `EntityVersionDiff`;
  - `FieldDiff` gets `field` and `dataType`, and is no longer ignored by the Redocly lint.
- **backend:**
  - `core/entity`: the state read (`EntityVersionStore`);
  - `app/history`: state, diff and the visibility rule, shared with the timeline;
  - `api`: `EntityHistoryController`;
  - the handler for 405;
  - no migration.
- **frontend:** `EntityHistoryPage.vue` with the diff, the state and the compare mode; `api/history.ts`; the SCR-026 story.
- **Documentation:**
  - `docs/audit-event-catalog.md` (channel `history`);
  - `docs/screens/SCREENS.md`;
  - the user manual, `usage/change-history.md` in English and German;
  - US-099, US-100, REQ-099 and REQ-100.
  - `docs/data-model.puml`: no change.

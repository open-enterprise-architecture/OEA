# Proposal

## Why

Change 1 (`us-101-entity-restore`) restores an entity in full. Often only some fields were changed wrongly, while later changes to other fields are correct. A full restore would overwrite them too ([UC-16](../../../requirements/use-cases/UC-16-partial-entity-restore.md)).

This change adds the partial restore:

- [US-104](../../../requirements/user-stories/US-104-partial-restore-wizard.md): a wizard leads through the general fields, the properties and the connections of the entity;
- [US-105](../../../requirements/user-stories/US-105-partial-restore-field-selection.md): only differing fields can be selected;
- [US-106](../../../requirements/user-stories/US-106-partial-restore-restored-fields.md): the new version records the restored fields.

This is S13 change 2.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-16: Partial Entity Restore](../../../requirements/use-cases/UC-16-partial-entity-restore.md) (basic flow, A1–A4, E1–E3; step 3 and step 13 realigned) |
| User Stories | [US-104](../../../requirements/user-stories/US-104-partial-restore-wizard.md) (AC1–AC3), [US-105](../../../requirements/user-stories/US-105-partial-restore-field-selection.md) (AC1–AC3), [US-106](../../../requirements/user-stories/US-106-partial-restore-restored-fields.md) (AC1–AC3; field keys realigned) |
| Requirements | [REQ-104](../../../requirements/req/REQ-104-partial-restore-wizard.md) (statement realigned), [REQ-105](../../../requirements/req/REQ-105-partial-restore-field-selection.md) (AC1–AC5), [REQ-106](../../../requirements/req/REQ-106-partial-restore-restored-fields.md) (statement and AC realigned), [REQ-102](../../../requirements/req/REQ-102-restore-audit-snapshot.md), [REQ-103](../../../requirements/req/REQ-103-restore-atomic-transaction.md), [REQ-162](../../../requirements/req/REQ-162-property-write-protection-all-paths.md) |
| ADRs | [ADR-022](../../../adrs/ADR-022-structured-property-model.md), [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Sprint / Milestone | S13 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-15:**

- **Wizard:** a separate action "Partially restore" on SCR-026 opens a wizard in a modal. It is the new screen SCR-029, and SCR-027 stays the full restore.
  - The steps are "General", "Properties", "Connections" and "Summary".
  - A step without differences is skipped with a note. Every step can be skipped.
- **Connections:** the step "Connections" is informational.
  - It lists the connections of the entity that changed since the source version, each with a link to its own history (SCR-026). There a connection is restored on its own, not in the same transaction.
  - Connections created after the source version are listed with the note that they did not exist then.
  - Removed connections do not exist yet (`deleteEntity` is planned, #70).
- **API:** `restoreEntity` gets the optional `fields`, the keys of the preview fields to restore.
  - Without `fields`, the restore is full, as in change 1.
  - Preview, `If-Match`, the transaction and the event `entity.restored` stay the same; the event gains `restoredFields`.
- **Source version:** a selection at the top of the wizard lists the earlier versions. Changing it reloads the preview and clears the selection.

**Partial restore (`repository`):**

- **Selection:** `fields` names the fields to restore, by the keys of the preview: `name`, `description`, `isLogical` or the ID of a property definition.
  - Only the selected restorable fields are set to the source version; every other field stays as it is.
  - A selected field that cannot be restored (`write-protected`, `not-mapped`, `invalid-value`, `not-in-source`) keeps its value and is reported as not restored (REQ-105 AC4).
- **Rejections:**
  - An empty `fields`, or a key that is not a differing field of the preview, is answered with 422 `validation-failed` naming `fields` (US-106 AC2).
  - Selected fields that are all not restorable give 422 `nothing-to-restore`, as in change 1.
- **Record:** the new version records `restoredFields`, the keys of the fields it restored, besides `restoredFromVersion` and the reason. The history returns them with their labels. The timeline shows "Partially restored from v4 (Description, Owner)" (US-106 AC3).
- **Not in the source version (REQ-105 AC2):** a property that was neither mapped nor set at the source version is listed with the new reason `not-in-source` and is not restored. The same now holds for the full restore of change 1: it no longer clears a property that was added after the source version.
- **Connections in the preview:** the preview lists the connections where the entity is source or target, each marked:
  - `changed`: it has a version after the time of the source version;
  - `added`: it was created after that time.

  Each entry has the ID, name, the other endpoint and the direction.

**Frontend (SCR-026, SCR-029):**

- SCR-026 offers "Partially restore" next to "Restore this version", with the same permissions.
- **Wizard SCR-029** (new story with real components):
  - the source version selection and the version info (number, time, person);
  - "General" and "Properties": a table "Field | Source value | Current value | Restore?". Identical fields are grayed out and not selectable. Fields that cannot be restored show their reason and are not selectable. A redacted field shows "Restricted" without values;
  - "Connections": the informational lists with "Open history";
  - "Summary": the selected fields, the warnings, the notice that every other field stays unchanged, and the optional reason.
- "Partially restore" is disabled without a selection. A 412 reloads the preview, as in SCR-027.
- **Timeline:** a partially restored version shows "Partially restored from vN" with the restored fields.

**Realigned sections:**

- **REQ-104 statement:** four steps, including "Summary". Step 3 links each changed connection to its own history and restores nothing itself. Removed connections follow with deletion.
- **REQ-106 and US-106 AC1, AC3:** the keys of the preview instead of the dot notation (owner decision of 2026-11-14). The timeline names the fields by label.
- **UC-16:**
  - step 3: connections are opened in their own history;
  - step 13: no copy is written, as in UC-15;
  - E2: 412 instead of 409.

## Out of Scope

- Restoring connections within the wizard, and removed connections (#70).
- Restoring a property of a connection field by field from the entity's wizard. A connection is an entity and has its own wizard on its own history.
- Restoring from an entity detail view.
- The portal screen SCR-054.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `repository/entity-restore`:
  - the partial restore with `fields`, its rejections and `restoredFields`;
  - the reason `not-in-source`, which applies to the full restore too;
  - the connections in the preview;
  - the wizard SCR-029.
- `repository/entity-history`: a partially restored version carries its restored fields, and the timeline names them.

## Impact

- **api/openapi.yaml:**
  - `RestoreRequest.fields`;
  - `not-in-source` in the reason enumerations;
  - `RestorePreview.connections` with the new schema `RestoreConnection`;
  - `EntityVersionSummary.restoredFields`;
  - the descriptions of `restoreEntity` and `previewEntityRestore`.
- **backend:**
  - migration `V33__partial_restore.sql`: `entity_version_headers.restored_fields TEXT[]`;
  - `EntityStore.Restore` with the fields;
  - `EntityRestore` with the selection, `not-in-source` and the connections;
  - the history read with `restoredFields`.
- **frontend:**
  - the new `PartialRestoreWizard.vue`;
  - `EntityHistoryPage.vue` with the action and the marker;
  - `api/history.ts`;
  - the stories of SCR-029 and SCR-026.
- **Documentation:**
  - `docs/screens/SCREENS.md` (new SCR-029);
  - `docs/audit-event-catalog.md`;
  - `docs/data-model.puml` and `business-objects/entity-version.md`;
  - the user manual in English and German;
  - UC-16, US-104 to US-106 and REQ-104 to REQ-106.

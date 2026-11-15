# Design

## Context

Change 1 (`us-101-entity-restore`) built the preview and the full restore:

- `EntityRestore` in `app/history` compares the current state with the reconstructed target state and classifies every differing property (`write-protected`, `not-mapped`, `invalid-value`).
- It writes one version with `restoredFromVersion` and the reason (`entity_version_headers`, V32) and the event `entity.restored`.
- The preview reuses the display rules of the diff (`EntityVersions.compare`).
- The restore requires `If-Match`. The modal SCR-027 shows the preview.

A connection is an entity with its own history. Its endpoints are in `entity_connections`.

## Goals / Non-Goals

**Goals:**

- The partial restore by a field selection on the same operation.
- The record of the restored fields, the reason `not-in-source`, and the connections in the preview.
- The wizard SCR-029 with the source version selection and the marker in the timeline.

**Non-Goals:**

- Restoring connections from the wizard, and removed connections (#70).
- A detail view of the entity.

## Architecture Constraints

- **Layers (ADR-028, ArchUnit):** `api` → `app` → `core`; the api layer uses no core records.
- **Contract first (ADR-013):** Redocly lint and `check_openapi_authorization.py` pass.
- **History:** stays append-only for the runtime role; the migration only adds a column.

## Decisions

### D1 – Restored fields (`V33__partial_restore.sql`)

`entity_version_headers.restored_fields TEXT[]` holds the keys of the restored fields, with two checks:

- it is `NULL` or has at least one entry;
- it is set only together with `restored_from_version`.

`EntityStore.Restore` gets `List<String> fields` (`null` for a full restore). `EntityHistoryStore.Version` reads the column.

*Alternative:* a child table `entity_version_restored_fields`. Rejected: the list is read only with its header and never queried by field.

### D2 – Selection and classification (`EntityRestore`)

`restore(principal, id, target, expectedVersion, reason, fields)`, where `fields` is `null` for a full restore. The order of the checks:

1. **Before the lock:**
   - reason length;
   - an empty `fields` gives `InvalidRestoreSelectionException` (422 naming `fields`).
2. **Lock and versions:** `If-Match` (428, 412) and the target version (400, 404), as in change 1.
3. **Plan:** it classifies every differing field, as in change 1, with the new reason `not-in-source`. A property gets it when the target state neither maps it (`state.mapped()`) nor has a value for it, while the current state has a value.
4. **Selection:**
   - a key outside the differing fields gives 422 naming `fields`;
   - the input contains only the selected restorable fields; the system fields not selected keep their current values;
   - the not restored list contains only the selected fields that cannot be restored.
5. **Nothing restorable:** no selected field can be restored, which gives 422 `nothing-to-restore`.
6. **Write and audit:**
   - the version records `restoredFields`, the selected restored keys in the order of the preview;
   - `entity.restored` adds `restoredFields` to its details;
   - the changes list only the restored fields.

**Warnings:**

- The `field` of a warning becomes the key of the preview field (the ID of the property). Its message names the label.
- The preview lists the warnings of all differing fields. The result lists only those of restored fields.
- Marks are set only for mandatory properties the restore emptied.

The batch keeps its `properties[name]` form.

### D3 – Connections in the preview (`core/entity`)

`EntityHistoryStore.connectionsChangedSince(entityId, Instant time)` reads the connections where the entity is source or target and that have a version header after `time`. The time is that of the target version.

| Column | Value |
|---|---|
| connection | ID and name |
| other endpoint | its name |
| direction | `outgoing` when the entity is the source, `incoming` when it is the target |
| change | `added` when version 1 is after `time`, otherwise `changed` |

The list is ordered by change, then by name. Connections hold no restricted system fields, so their names are shown to a reader with `entity:read`; the preview needs more permissions anyway.

### D4 – Contract

- **`RestoreRequest.fields`:** an optional array of strings, `minItems` not declared (the 422 is a business rule), `maxItems: 500`.
- **Reason enumerations:** `RestoreField.reason` and `RestoreFieldRef.reason` gain `not-in-source`.
- **`RestorePreview.connections`:** an array of the new schema `RestoreConnection { entityId, name, otherEndpoint, direction, change }`.
- **`EntityVersionSummary.restoredFields`:** an array of `{ field, label, restricted }`, or `null`.
- **Descriptions:** `restoreEntity` explains `fields` and the 422 naming `fields`.

The warning `field` is documented as the field key.

### D5 – History read

`EntityHistory` maps `restoredFields` with the same visibility rule as the changed fields (`HistoryAccess`):

- a system field gets its English label;
- a property gets its label, `restricted` when the reader may not read it, and is left out when it is hidden.

### D6 – Frontend

**`PartialRestoreWizard.vue`** (new, SCR-029) is a `UModal` with `UStepper` and four steps. The source version comes from the versions of the page as a `USelect`; changing it reloads the preview and clears the selection.

- **"General" and "Properties"** are tables with a checkbox per differing field:
  - a checkbox is disabled for an identical field (the current state of each field comes from the preview, and identical fields come from the state of the source version via `fetchEntityVersion`);
  - it is disabled for a not restorable field, with the reason;
  - a redacted field shows "Restricted".

  "Next" and "Skip" move on. A step without differences is skipped with a note.
- **"Connections"** lists `connections` with a `RouterLink` "Open history" to `/entities/{id}/history`.
- **"Summary"** lists the selection with the source values, the warnings of the selected fields, the notice "Every other field stays unchanged" and the reason (`maxlength` 1000). "Partially restore" sends `fields` with `If-Match`; a 412 reloads, as in SCR-027.

**`EntityHistoryPage.vue`:**

- the action "Partially restore";
- a restored version shows "Partially restored from vN (labels)" instead of "Restored from vN" when `restoredFields` is set.

**Stories:**

- SCR-029 with `General`, `Properties`, `Connections`, `Summary` and `NothingSelected`;
- SCR-026 gains `PartiallyRestored`.

### D7 – Tests

- **Unit and integration:**
  - `EntityStoreTests`: the `restored_fields` checks;
  - `EntityRestoreTests`: the selection, the rejections, `not-in-source`, the warnings of the selection, `restoredFields` and the event;
  - `EntityHistoryStoreTests`: `connectionsChangedSince`;
  - the history read with `restoredFields`.
- **Cucumber:** the scenarios of the spec in `entity-restore.feature` and `entity-history.feature`.
- **Frontend:** `PartialRestoreWizard.spec.ts` and `EntityHistoryPage.spec.ts`.

## Data Model

`EntityVersionHeader.restoredFields : String[0..*]`, only with `restoredFromVersion`. It goes in `docs/data-model.puml` and `business-objects/entity-version.md`.

## API

| Operation | Change |
|---|---|
| `restoreEntity` | optional `fields`; 422 naming `fields` |
| `previewEntityRestore` | `connections`; reason `not-in-source`; warning `field` is the field key |
| `getEntityHistory` | `restoredFields` per version |

## Risks / Trade-offs

- **`not-in-source` changes the full restore of change 1:** a property mapped after the target version is no longer cleared. This is the consistent reading of REQ-105 AC2: a restore never clears a property the source version did not know.
- **Warning keys change:** the warning `field` changes from `properties[name]` to the property ID. Change 1 was merged on 2026-11-15, and only SCR-027 reads the warnings, so no other client depends on the old form.
- **Connections by time:** a connection changed in the same second as the target version counts as unchanged. This boundary is tested on both sides.

## Open Questions

- None. The owner decisions of 2026-11-15 settle the wizard, the connection step, the API and the source selection.

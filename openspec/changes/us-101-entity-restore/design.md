# Design

## Context

Since S12, the history SCR-026 shows the versions of an entity, the diff of each version and the complete state of any version (`repository/entity-history`, `repository/entity-versions`). The states are reconstructed backwards from the current state through the delta tables `entity_version_headers`, `entity_system_field_history` and `entity_property_value_history` (ADR-022, `EntityVersionStore`).

Entities are written by `EntityWriter` after `EntityValidator` has checked them. The writer appends one version with the previous values of the changed fields and one audit event `entity.created` or `entity.updated`.

The contract already declares `restoreEntity` (`POST /entities/{id}/restore`, `planned`) with `RestoreRequest { targetVersion, reason }`, `entity:write` and `entity:restore`, a response `EntityDetail` and a 409 for an outdated version. `EntityDetail` belongs to the planned `getEntity` and is not implemented.

The metamodel already answers a stale `If-Match` with 412 `stale-version` (`PreconditionFailed`). Entities cannot be deleted yet (`deleteEntity` is planned), so no entity is soft-deleted.

## Goals / Non-Goals

**Goals:**

- Preview and full restore of an entity to an earlier version, with the rules of the owner decisions of 2026-11-15.
- One new version with `restoredFromVersion` and the reason, visible in the timeline.
- `If-Match` on the restore.
- The modal SCR-027 with the action on SCR-026.

**Non-Goals:**

- The partial restore (change 2). The preview is built so that change 2 can reuse it.
- Restoring soft-deleted entities (#70).
- Optimistic locking of other entity writes (#69, #257).

## Architecture Constraints

- **Layers (ADR-028, ArchUnit):** `api` → `app` → `core`.
  - The api layer uses no core records.
  - `app/history` may use `app/entity` (validator, writer) and `app/metamodel` (access, labels).
  - `app/entity` must not use `app/history`.
- **Contract first (ADR-013):** the contract changes come before the code; Redocly lint and `check_openapi_authorization.py` pass.
- **Runtime role:** the history tables stay append-only. The migration adds columns only; the runtime role keeps `INSERT` without `UPDATE` on `entity_version_headers`.

## Decisions

### D1 – Restore header (`V32__entity_restore.sql`)

`entity_version_headers` gets two columns:

- `restored_from_version INTEGER`, with `CHECK (restored_from_version >= 1 AND restored_from_version < version)`;
- `change_reason TEXT`, with `CHECK (change_reason IS NULL OR length(change_reason) <= 1000)`.

Both columns are `NULL` for every other version.

`EntityStore.update` gets an optional `Restore(int fromVersion, String reason)` and writes it into the header. `EntityStore.lock(int id)` reads the entity `FOR UPDATE`, as `lockByExternalId` does.

*Alternative:* a separate table `entity_restores` keyed by entity and version. Rejected: the marker belongs to the version, and the history read already joins the header.

### D2 – Preview and restore (`app/history/EntityRestore`)

`EntityRestore` has two methods. `preview(principal, entityId, targetVersion)` reads without a lock. `restore(principal, entityId, targetVersion, expectedVersion, reason)` runs in one `@Transactional` method.

1. **Check the version range** against the current version, as `EntityVersions` does for the diff:
   - 0, or a version at or after the current one, gives 400 naming `targetVersion`;
   - current + 1 and higher gives 404.
2. **Lock and check the version (restore only):** `EntityStore.lock(id)`. A missing `expectedVersion` gives 428 (`PreconditionRequiredException`), and a different one gives 412 (`StaleVersionException` with `currentVersion`, as in the metamodel).
3. **Target state:** `EntityVersionStore.state(id, targetVersion)` gives the system fields and values; the current ones come from `EntityStore`.
4. **Classify the differences:**
   - Name, description and the logical flag are always restorable.
   - The metatype and the endpoints are never compared; they are immutable.
   - For each property whose current and target values differ:
     - not mapped to the current metatype → `not-mapped`;
     - not writable for the caller (`PropertyWriteAccess.writer`) → `write-protected`;
     - target value not accepted by the current definition (`EntityValidator.accepts`, extracted from the conversion of the validator: data type, length, enumeration values, reference to an active entity) → `invalid-value`;
     - otherwise restorable.
5. **Validate:** `EntityValidator.check` receives an `EntityInput` with the target name, description, logical flag and the restorable properties (an empty value removes one). It runs with the new `Necessity.WARN`, which reports an empty mandatory property as a warning `MANDATORY_MISSING` instead of an error. Recommended properties give the existing `VALUE_MISSING` warning.
6. **Nothing to restore:** if no restorable field differs, the result is 422 `nothing-to-restore` (`NothingToRestoreException`).
7. **Write:** `EntityWriter.write` with the new `WriteContext.restore` (target version, reason).
   - The writer passes it to `EntityStore.update` and emits `entity.restored` instead of `entity.updated`, with `restoredFromVersion` and `reason` in the details.
   - Then `PropertyValueStore.mark(entityId, missingMandatory, now)` (new, per entity) marks the empty mandatory properties, as `mark` does after a metatype change.

**Visibility:** the preview applies `HistoryAccess`, as the diff does.

- A property the caller may not read is listed as redacted, without values.
- A property hidden for the caller's roles is left out of the list, but is still classified and restored when writable.
- An override read is audited with channel `history`.

The restore result lists only labels and reasons, never values, so it needs no redaction.

*Alternative:* restore through `EntityBatch` with the target values. Rejected: the batch rejects write-protected properties with 403 and missing mandatory values with an error, while the restore must keep the first and warn about the second. It also has no place for the restore header.

### D3 – Contract

- **`previewEntityRestore`** (`GET /entities/{id}/restore`): query `targetVersion` (required, `minimum: 1`), `entity:write` and `entity:restore`, returns `RestorePreview`, 400, 403 and 404.
- **`restoreEntity`:**
  - loses `planned`;
  - requires the `If-Match` header (a new parameter `IfMatchRequired`);
  - returns `RestoreResult` and answers 400, 403, 404, 412 (`PreconditionFailed`), 422 (`nothing-to-restore`) and 428 (the new response `PreconditionRequired`);
  - its 409 is removed;
  - its description no longer mentions soft-deleted entities.
- **New schemas:**
  - `RestoreField { field, label, isSystemField, dataType, oldValue, newValue, redacted, restore, reason }`, where `reason` is one of `write-protected`, `not-mapped`, `invalid-value` or `null`;
  - `RestorePreview { entityId, currentVersion, targetVersion, fields[], warnings[] }`;
  - `RestoreResult { entityId, version, restoredFromVersion, restored[], notRestored[], warnings[] }`.

  Warnings reuse the issue schema of the batch (`field`, `code`, `message`).
- `RestoreRequest.reason` gets `maxLength: 1000`.
- `EntityVersionSummary` gets `restoredFromVersion` and `reason`, both nullable.
- `EntityHistoryResponse` gets `currentVersion`. The client sends it as `If-Match` and hides the action on the current version.

### D4 – Errors

`ApiExceptionHandler` maps the new exceptions:

- 428 with the problem type `precondition-required`;
- 412 reuses the existing `stale-version` mapping;
- 422 with the problem type `nothing-to-restore`.

Each is a problem document without request-path branching (ArchUnit).

### D5 – Screens SCR-026 and SCR-027

- **`EntityHistoryPage.vue`:**
  - The page reads `entity:write` and `entity:restore` from `session.me`, as `SolutionsPage.vue` does.
  - With both, every version card except `currentVersion` shows "Restore this version".
  - A restored version shows a badge "Restored from vN" and its reason.
- **`RestoreEntityModal.vue`** (new, `UModal`):
  - It loads the preview and shows:
    - a table "Field | Current | After restore", with struck-through current values, as on SCR-026;
    - a section "Not restored" with the reason in words;
    - the warnings;
    - the two notices of UC-15;
    - a `UTextarea` "Reason for restore" (`maxlength` 1000, with a counter).
  - "Restore" is disabled while no field is restorable or a request is running.
  - After success, the modal closes, a toast confirms, and the page reloads the timeline.
  - On 412, the modal shows "This entity was changed meanwhile", reloads the preview and the timeline, and uses the new current version.
- **Stories:**
  - SCR-027 `SCR-027-restore-entity.stories.ts` with the states `Default`, `NotRestored`, `Warnings`, `NothingToRestore` and `ChangedMeanwhile`;
  - SCR-026 gains `Restored`.

  The legacy SVGs of SCR-027 are deleted, and its status becomes `implemented` (ADR-031).

### D6 – Tests

- **Unit and integration (JUnit):**
  - `EntityStoreTests` (restore header, `lock`);
  - `EntityValidatorTests` (`accepts`, `Necessity.WARN`);
  - `EntityRestoreTests` (classification, boundaries, `If-Match`, 422, marks, rollback);
  - `EntityWriterTests` (`entity.restored`);
  - the migration test on the runtime role.
- **Cucumber:** `features/repository/entity-restore.feature` with the scenarios of the spec, and the restore scenarios added to `entity-history.feature`.
- **Frontend:** `RestoreEntityModal.spec.ts` and `EntityHistoryPage.spec.ts`.
- **Rollback (REQ-103 AC1):** an `AuditWriter` that fails, injected in `EntityRestoreTests`. The test checks that the entity, the header and the history rows are unchanged.

## Data Model

- **`docs/data-model.puml`:** `EntityVersionHeader` gets `restoredFromVersion : Integer [0..1]` and `changeReason : String [0..1] (≤ 1000)`, with the check constraints.
- **`business-objects/entity-version.md`:** the same attributes and a note that the state before a restore is the previous version (owner decision 2026-11-15).

## API

| Operation | Method and path | Permissions |
|---|---|---|
| `previewEntityRestore` | `GET /entities/{id}/restore?targetVersion=` | `entity:write`, `entity:restore` |
| `restoreEntity` | `POST /entities/{id}/restore`, `If-Match` required | `entity:write`, `entity:restore` |
| `getEntityHistory` | unchanged path; `currentVersion`, `restoredFromVersion`, `reason` | `entity:read` |

## Risks / Trade-offs

- **Required `If-Match`:** this differs from the metamodel, where the header is optional. A script must read the history first. *Mitigation:* the restore is an interactive operation. The 428 names the header, and the manual says so.
- **Values of the target state are revalidated with today's rules:** a value that was valid then may be refused today (`invalid-value`). That is intended: the restore must not write values the metamodel no longer allows. The modal shows the refused fields before the restore.
- **Hidden properties are restored without being listed:** a caller who may write but not read a property restores it blindly. This matches the batch write, which also lets writers write without reading. The result names the field, but not its value.
- **References to entities:** a target value that references an entity which no longer exists cannot occur yet, because there is no delete. `accepts` already refuses an inactive reference, so the rule holds once `deleteEntity` exists.

## Open Questions

- None. The owner decisions of 2026-11-15 settle entry, snapshot, metamodel handling and `If-Match`.

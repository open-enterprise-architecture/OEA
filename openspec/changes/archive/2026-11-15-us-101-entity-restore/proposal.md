# Proposal

## Why

Architects can see in the history (SCR-026) how an entity looked earlier, but they cannot return to that state. Undoing a wrong change means retyping every field by hand, and the history does not show that it was an undo ([UC-15](../../../requirements/use-cases/UC-15-restore-entity-state.md)). [US-101](../../../requirements/user-stories/US-101-entity-full-restore.md) restores an entity to an earlier version after a confirmation dialog with the diff. [US-102](../../../requirements/user-stories/US-102-restore-audit-snapshot.md) marks the restore in the timeline. [US-103](../../../requirements/user-stories/US-103-restore-transaction.md) makes it atomic. This is S13 change 1; the partial restore (UC-16) follows in change 2.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-15: Restore Entity State](../../../requirements/use-cases/UC-15-restore-entity-state.md) (basic flow and E1; step 5 realigned; A1 later) |
| User Stories | [US-101: Fully Restore an Entity to an Earlier State](../../../requirements/user-stories/US-101-entity-full-restore.md) (AC1–AC3), [US-102: Automatic Audit Snapshot Before Restore](../../../requirements/user-stories/US-102-restore-audit-snapshot.md) (AC1, AC2; AC3 realigned), [US-103: Restore Is Atomic and Rollback-Safe](../../../requirements/user-stories/US-103-restore-transaction.md) (AC1 realigned, AC2) |
| Requirements | [REQ-101: Entity full restore](../../../requirements/req/REQ-101-entity-full-restore.md) (AC1–AC5), [REQ-102: Restore audit snapshot](../../../requirements/req/REQ-102-restore-audit-snapshot.md) (statement and AC3 realigned), [REQ-103: Restore atomic transaction](../../../requirements/req/REQ-103-restore-atomic-transaction.md) (AC1 realigned), [REQ-162: Property write protection on all paths](../../../requirements/req/REQ-162-property-write-protection-all-paths.md), [REQ-160: Visibility on all read channels](../../../requirements/req/REQ-160-visibility-on-all-read-channels.md) |
| ADRs | [ADR-022: Structured property model and delta versioning](../../../adrs/ADR-022-structured-property-model.md), [ADR-028: Backend layered architecture](../../../adrs/ADR-028-backend-layered-architecture.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md), [ADR-031: Screen prototypes](../../../adrs/ADR-031-ui-prototyping-storybook.md) |
| Issues | [#69](https://github.com/open-enterprise-architecture/OEA/issues/69) and [#257](https://github.com/open-enterprise-architecture/OEA/issues/257): optimistic locking; this change locks the restore only, both issues stay open |
| Sprint / Milestone | S13 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-15:**

- **S13 cut:** two changes.
  1. `us-101-entity-restore`: the full restore (US-101, US-102, US-103).
  2. The partial restore with wizard and field selection (US-104, US-105, US-106).

  The fixes of the issues #256, #263 (T-1), #262 (API-3, API-5, API-6) and #237 follow as change 3.
- **Entry:** every earlier version on SCR-026 gets the action "Restore this version". It opens the modal SCR-027. Restoring from an entity detail view (UC-15 A1) follows with that view.
- **Snapshot:** the history stores deltas (ADR-022), so the state before the restore stays version N, immutable and reconstructable. The restore writes exactly one new version N+1 with `restoredFromVersion` and the reason. No copy is written. REQ-102, US-102 AC3, US-103 AC1 and UC-15 step 5 are realigned.
- **Current metamodel (UC-15 E1):** the restore warns and does not block.
  - A mandatory or recommended property that was empty in the target version is shown as a warning. After the restore it is marked as missing, as after a metatype change.
  - A value whose property is no longer mapped to the metatype is not restored and is reported as "not restored". So is a value that the current definition no longer accepts. Both are handled like a property the caller may not write (REQ-101 AC4).
- **Lost updates:** the restore requires `If-Match` with the current version of the entity. A missing header is answered with 428. A different version is answered with 412 `stale-version`, as in the metamodel operations. The contract's 409 is replaced, because the metamodel answers a stale version with 412, not 409.

**Restore (`repository`):**

- **`previewEntityRestore`** (`GET /entities/{id}/restore?targetVersion=`, `entity:write` and `entity:restore`) returns what a restore to the target version would do:
  - the current version;
  - every field whose current value differs from the target, with both values;
  - for each such field, whether it is restored or why not (`write-protected`, `not-mapped`, `invalid-value`);
  - the warnings for empty mandatory and recommended properties.

  Values the caller may not read are redacted (REQ-101 AC5).
- **`restoreEntity`** (`POST /entities/{id}/restore`) sets name, description, the logical flag and the restorable properties to the target version.
  - The ID, metatype, connection endpoints and creation fields are never changed.
  - The response lists the restored fields, the fields not restored with their reason, and the warnings.
  - The new version records `restoredFromVersion` and the optional reason (at most 1000 characters).
  - The audit event `entity.restored` lists the changes.
  - The restore is one transaction. On an error, no version is written and the entity is unchanged.
- **Boundaries:** the target version must be at least 1 and before the current version; anything else is answered with 400 naming `targetVersion`. A version after the current one is answered with 404. A target whose restorable fields all equal the current values is answered with 422 `nothing-to-restore`.
- **Timeline:** a version written by a restore carries `restoredFromVersion` and its reason in the history.

**Frontend (SCR-026, SCR-027):**

- SCR-026 shows "Restore this version" on every version except the current one, only for a caller with `entity:write` and `entity:restore`.
- A restored version shows the badge "Restored from vN" and its reason.
- The modal SCR-027 is a new story with real components. It shows:
  - the diff "current → target";
  - the fields that will not be restored, with their reason;
  - the warnings;
  - the notices of UC-15 step 3;
  - the optional reason.

  "Restore" is disabled when nothing can be restored. Cancel changes nothing. A 412 shows "changed meanwhile" and reloads the preview.
- After the restore, the timeline reloads and shows the new version.

**Realigned sections:**

- **REQ-102:** statement and AC3 name version N as the snapshot of the state before the restore, in the version tables instead of a table `entity_versions`.
- **US-102 AC3:** realigned the same way.
- **REQ-103 AC1 and US-103 AC1:** after an error, no new version exists, instead of "no snapshot in `entity_versions`".
- **UC-15 step 5:** one new version with `restoredFromVersion`, the previous state remains version N.
- **REQ-101:** no realignment.

## Out of Scope

- The partial restore (UC-16, US-104 to US-106; S13 change 2).
- Restoring a soft-deleted entity: entities cannot be deleted yet (`deleteEntity` is planned, #70). The contract text is adjusted accordingly.
- Restoring from an entity detail view (UC-15 A1) and from the catalog.
- Optimistic locking for `patchEntity` and solution deltas (#69, #257).
- The portal screen SCR-054.

## Capabilities

### New Capabilities

- `repository/entity-restore`: the preview and the full restore of an entity to an earlier version, including:
  - the fields it never changes;
  - the handling of write protection, unmapped and invalid values, and missing mandatory values;
  - `If-Match`, the boundaries and the atomic write;
  - the audit event;
  - the screens SCR-026 (action) and SCR-027 (modal).

### Modified Capabilities

- `repository/entity-history`: a version written by a restore carries `restoredFromVersion` and its reason, and the timeline marks it "Restored from vN".

## Impact

- **api/openapi.yaml:**
  - `restoreEntity` loses `planned`, requires `If-Match` and answers 412, 422 and 428; it returns `RestoreResult` instead of `EntityDetail`;
  - the new operation `previewEntityRestore`;
  - the new schemas `RestorePreview`, `RestoreField` and `RestoreResult`;
  - `RestoreRequest.reason` gets `maxLength: 1000`;
  - `EntityVersionSummary` gets `restoredFromVersion` and `reason`;
  - `EntityHistoryResponse` gets `currentVersion`.
- **backend:**
  - migration `V32__entity_restore.sql`: `entity_version_headers.restored_from_version` and `change_reason`;
  - `core/entity`: writing the restore header, and locking an entity by ID;
  - `app/history`: `EntityRestore` (preview and restore);
  - `api`: `EntityHistoryController`.
- **frontend:**
  - `EntityHistoryPage.vue`: the action and the badge;
  - the new `RestoreEntityModal.vue`;
  - `api/history.ts`;
  - the stories of SCR-026 and SCR-027.
- **Documentation:**
  - `docs/audit-event-catalog.md` (`entity.restored`);
  - `docs/screens/SCREENS.md` (SCR-027 `implemented`, legacy SVGs removed);
  - `docs/data-model.puml` and `business-objects/entity-version.md`;
  - the user manual, `usage/change-history.md` in English and German;
  - UC-15, US-101 to US-103, REQ-102 and REQ-103.

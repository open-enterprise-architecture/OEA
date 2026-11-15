# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D3):
  - the new operation `previewEntityRestore` (`GET /entities/{id}/restore`);
  - `restoreEntity` without `planned`, with the required `If-Match`, `RestoreResult`, and 412, 422 and 428 instead of 409;
  - the schemas `RestoreField`, `RestorePreview` and `RestoreResult`;
  - `RestoreRequest.reason` with `maxLength: 1000`;
  - `restoredFromVersion` and `reason` in `EntityVersionSummary`, and `currentVersion` in `EntityHistoryResponse`;
  - the response `PreconditionRequired`.

  Verify: Redocly lint passes, the generated API compiles, `check_openapi_authorization.py`, `test_check_openapi_authorization.py` and the contract authorization tests pass, and neither operation answers 501.

## 2. Backend: storage

- [x] 2.1 `V32__entity_restore.sql` and `EntityStore` (design D1): the columns `restored_from_version` and `change_reason` with their checks; `update` with the optional `Restore`; `lock(id)`.

  Verify with `EntityStoreTests` and the migration test on the runtime role:
  - a restore header stores target and reason;
  - a normal update stores neither;
  - `restored_from_version` boundaries: version − 1 is accepted, while version and 0 are rejected by the check;
  - reason boundaries: 1000 characters are accepted, 1001 are rejected;
  - the runtime role still cannot `UPDATE` or `DELETE` a header.
- [x] 2.2 `PropertyValueStore.mark(entityId, definitions, now)` per entity.

  Verify: one entity is marked, others are not; marking twice keeps one mark.

## 3. Backend: preview and restore

- [x] 3.1 `EntityValidator` (design D2):
  - `accepts(definition, value)`, extracted from the conversion without changing behavior;
  - `Necessity.WARN`.

  Verify with `EntityValidatorTests` and the existing batch tests unchanged:
  - `accepts` takes a string of the maximum length and rejects one character more;
  - `accepts` rejects a removed enumeration value;
  - with `WARN`, an empty mandatory property gives the warning `MANDATORY_MISSING` and no error;
  - without `WARN`, it still gives the error.
- [x] 3.2 `EntityWriter` with `WriteContext.restore`: the restore header and the event `entity.restored` with `restoredFromVersion`, `reason` and the changes.

  Verify with `EntityWriterTests`: a restore writes `entity.restored` with both details, and a normal update still writes `entity.updated`.
- [x] 3.3 `EntityRestore` in `app/history` (design D2): preview, restore, classification, visibility, `If-Match`, 422 and the marks.

  Verify with `EntityRestoreTests`:
  - positive: a restore of version 5 to version 3 sets name, description, logical flag and properties to version 3, removes a value empty in version 3, and writes version 6 with target 3 and the reason; version 5 is unchanged;
  - classification: `write-protected`, `not-mapped` and `invalid-value` each keep the current value and are listed;
  - visibility: a value without a read role is redacted in the preview, and a hidden property is left out of the preview and still restored when writable;
  - metamodel: an empty mandatory property gives a warning and a mark, and the restore succeeds;
  - connection: source and target are unchanged;
  - `If-Match`: the current version is executed, the previous version gives 412 with `currentVersion`, and a missing header gives 428; neither writes;
  - target boundaries at version 5: 1 and 4 are accepted, 0 and 5 give 400 naming `targetVersion`, and 6 gives 404; an unknown entity gives 404;
  - only write-protected differences give 422 `nothing-to-restore` and write nothing;
  - rollback: a failing `AuditWriter` leaves the entity, the header and the history rows unchanged.
- [x] 3.4 `previewEntityRestore` and `restoreEntity` in `EntityHistoryController`; `currentVersion` and the restore marker in `getEntityHistory`; the mappings for 428, 412 and 422 in `ApiExceptionHandler` (design D4).

  Verify with controller tests:
  - a reason of 1000 characters is accepted, while 1001 gives 400 naming `reason`;
  - without `entity:restore`, the result is 403 and nothing changes;
  - the history names `currentVersion` with a filter, and `restoredFromVersion` only on the restored version.
- [x] 3.5 Cucumber `features/repository/entity-restore.feature` with the scenarios of the spec `repository/entity-restore`:
  - "Differing fields", "Write-protected property in the preview", "Property no longer mapped", "Value no longer accepted", "Empty mandatory property", "Redacted value in the preview";
  - "Restore to an earlier version", "Value empty in the target version", "Write-protected property is kept", "Endpoints of a connection", "Restore version with reason", "Missing mandatory value after the restore";
  - "Current version", "Changed meanwhile", "Without If-Match";
  - "Target version boundaries", "Reason length boundary", "Only write-protected differences", "Without restore permission";
  - "Successful restore"; "Error after the entity was written" is covered by the rollback test of 3.3.

  The screen scenarios of "Restore action on the history" and "Restore modal" are covered by the frontend tests of 4.2.

  `entity-history.feature` gains "Restored version in the timeline" and "Current version with a filter". Verify that both features pass.

## 4. Frontend: SCR-026 and SCR-027

- [x] 4.1 Story first (design D5): `SCR-027-restore-entity.stories.ts` with the states `Default`, `NotRestored`, `Warnings`, `NothingToRestore` and `ChangedMeanwhile`, and SCR-026 with `Restored`. The fake backend in `test/history.ts` covers preview and restore.

  Verify that the stories render without a11y violations.
- [x] 4.2 `api/history.ts` (`fetchRestorePreview`, `restoreEntity` with `If-Match`), `RestoreEntityModal.vue`, and `EntityHistoryPage.vue` with the action and the badge. Texts go in `i18n` en/de.

  Verify with `RestoreEntityModal.spec.ts` and `EntityHistoryPage.spec.ts`:
  - the action appears on every version except the current one, only with both permissions, and not with `entity:read` alone;
  - the modal lists the diff, the not restored fields with their reasons, and the warnings;
  - "Restore" is disabled when nothing is restorable;
  - the reason is limited to 1000 characters;
  - cancel sends nothing;
  - confirm sends `If-Match` with `currentVersion` and the reason, then reloads the timeline;
  - a 412 shows "changed meanwhile" and reloads the preview;
  - a restored version shows "Restored from v3" and its reason.
- [x] 4.3 `docs/screens/SCREENS.md`: SCR-027 becomes `implemented` with the generated images, and the legacy SVGs are deleted; the entry of SCR-026 names the restore action.

  Verify with `python3 scripts/check_screens.py` (the images follow from CI).

## 5. Documentation

- [x] 5.1 Realign REQ-102 (statement, AC3), REQ-103 (AC1), US-102 (AC3), US-103 (AC1) and UC-15 (step 5) as listed in the proposal, with a change history entry in each REQ.
- [x] 5.2 `docs/audit-event-catalog.md`: `entity.restored`. `docs/data-model.puml` and `business-objects/entity-version.md`: the restore header (design, Data Model).
- [x] 5.3 User manual `usage/change-history.md` in English and German: restoring a version, the reasons for "not restored", the warnings, "changed meanwhile", and `If-Match` for API callers.

  Verify the manual build.

## 6. Wrap-up

- [x] 6.1 Set US-101, US-102 and US-103 to done and record the implementation hash. Update the S13 row in `docs/sprint-plan.md` with the owner decisions and the cut.
- [x] 6.2 Synchronize `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md`, and regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [x] 6.3 Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --strict`, and verify they pass.

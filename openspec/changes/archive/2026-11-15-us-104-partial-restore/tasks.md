# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D4):
  - `RestoreRequest.fields` (`maxItems: 500`);
  - `not-in-source` in both reason enumerations;
  - `RestorePreview.connections` with `RestoreConnection`;
  - `EntityVersionSummary.restoredFields`;
  - the descriptions of `restoreEntity` and `previewEntityRestore`, including the warning `field` as field key.

  Verify: Redocly lint passes, the generated API compiles, and `check_openapi_authorization.py` and `test_check_openapi_authorization.py` pass.

## 2. Backend: storage

- [x] 2.1 `V33__partial_restore.sql`, `EntityStore.Restore` with `fields`, and `EntityHistoryStore.Version` with `restoredFields` (design D1).

  Verify with `EntityStoreTests`:
  - a partial restore header stores its fields, and a full restore header stores none;
  - boundaries: one field is accepted, and an empty array is rejected by the check;
  - fields without `restored_from_version` are rejected;
  - the runtime role still cannot `UPDATE` a header.
- [x] 2.2 `EntityHistoryStore.connectionsChangedSince` (design D3).

  Verify through the preview in `EntityRestoreTests` (the store runs against the full schema there):
  - an outgoing and an incoming connection are listed with their direction;
  - `changed` and `added` are told apart;
  - an unchanged connection is left out;
  - time boundaries: a version 1 second after the time is listed, and a version exactly at the time is not.

## 3. Backend: partial restore

- [x] 3.1 `EntityRestore` (design D2): selection, `not-in-source`, warnings by field key, warnings and marks of the selection, `restoredFields` in the header and the event, and the connections in the preview.

  Verify with `EntityRestoreTests`:
  - positive: of `description`, `owner` and `criticality`, selecting `description` and `owner` restores those two, and `criticality` keeps its value; the header and `entity.restored` name both;
  - a selected write-protected property is kept and listed;
  - only the selected fields appear in `restored` and `notRestored`;
  - `not-in-source`: a property mapped after the target version is listed and kept, by the partial and by the full restore;
  - rejections: an empty selection gives 422 naming `fields`; an identical or unknown key gives 422 naming `fields`; only not restorable selected fields give 422 `nothing-to-restore`; none of them writes;
  - selection boundaries: 1 of 3 differing fields is accepted, and all 3 is accepted;
  - a full restore records no `restoredFields`;
  - warnings: the preview names the property ID, and the result names only selected fields.
- [x] 3.2 `EntityHistory` and `EntityHistoryController` (design D5): `restoredFields` with label, `restricted` and hiding; `fields` in `restoreEntity`; the 422 mapping of `InvalidRestoreSelectionException`.

  Verify with `EntityRestoreTests`:
  - the history lists `restoredFields` with labels;
  - a restricted field is marked `restricted`;
  - a hidden field is left out;
  - the history of a full restore has `restoredFields` `null`.
- [x] 3.3 Cucumber: `entity-restore.feature` gains the scenarios of the spec:
  - "Property added after the target version";
  - "Only the selected fields", "Selected write-protected property";
  - "Empty selection", "Identical field selected", "One selected field of several";
  - "Recorded fields", "Full restore records no fields";
  - "Changed and added connections", "Unchanged connection".

  `entity-history.feature` gains "Partially restored version in the timeline". Verify that both features pass. The screen scenarios are covered by 4.2.

## 4. Frontend: SCR-029 and SCR-026

- [x] 4.1 Story first (design D6): `SCR-029-partial-restore.stories.ts` with `General`, `Properties`, `Connections`, `Summary` and `NothingSelected`, and SCR-026 with `PartiallyRestored`. The fake backend in `test/history.ts` covers `fields`, the connections and `restoredFields`.

  Verify that the stories render without a11y violations.
- [x] 4.2 `api/history.ts` (`fields`), `PartialRestoreWizard.vue`, and `EntityHistoryPage.vue` with the action and the marker. Texts go in `i18n` en/de.

  Verify with `PartialRestoreWizard.spec.ts` and `EntityHistoryPage.spec.ts`:
  - the wizard opens with the version as source;
  - an identical field is disabled;
  - a not restorable field shows its reason and is disabled;
  - a redacted field shows no values;
  - a selected field shows its source value;
  - a step without differences is skipped with the note;
  - "Skip" moves on without a selection;
  - the connections link to their history;
  - "Partially restore" is disabled without a selection and sends `fields` with `If-Match` with one;
  - changing the source version reloads and clears the selection;
  - a 412 reloads the preview;
  - the action appears only with both permissions;
  - the marker shows "Partially restored from v4 (Description, Owner)".
- [x] 4.3 `docs/screens/SCREENS.md`: the new SCR-029 `implemented` with its images; the SCR-026 entry names the action. Verify with `python3 scripts/check_screens.py`.

## 5. Documentation

- [x] 5.1 Realign REQ-104 (statement), REQ-106 (statement, AC1, AC3), US-106 (AC1, AC3) and UC-16 (step 3, step 13, E2) as listed in the proposal, with a change history entry in each REQ.
- [x] 5.2 `docs/audit-event-catalog.md` (`restoredFields`), `docs/data-model.puml` and `business-objects/entity-version.md` (`restoredFields`).
- [x] 5.3 User manual `usage/change-history.md` in English and German: the partial restore, the steps, the connections and `fields` for API callers; the full restore keeps properties the source version did not know. Verify the manual build.

## 6. Wrap-up

- [x] 6.1 Set US-104, US-105 and US-106 to done with the implementation note, and update the S13 row in `docs/sprint-plan.md`.
- [x] 6.2 Synchronize `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md`, and regenerate `requirements/traceability.md`.
- [x] 6.3 Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --strict`, and verify they pass.

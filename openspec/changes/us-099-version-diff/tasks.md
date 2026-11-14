# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D4):
  - the operations `getEntityVersion` (`GET /entities/{id}/versions/{version}`) and `compareEntityVersions` (`GET /entities/{id}/diff`), tag `Entities`, `entity:read`;
  - the schemas `EntityVersionState`, `EntityVersionField` and `EntityVersionDiff`;
  - `FieldDiff` with `field`, `dataType` and a required `label`;
  - `FieldDiff` removed from `.redocly.lint-ignore.yaml`.

  Verify: Redocly lint passes, the generated API compiles, `check_openapi_authorization.py` and the contract authorization tests pass, and neither operation answers 501.

## 2. Backend: reconstruction

- [x] 2.1 `EntityVersionStore` in `core/entity` (D1): the state of a version (system fields, property values, header) and the property set from the mappings at the time.

  Verify with `EntityVersionStoreTests` on the runtime role:
  - values: `lifecycleStatus` `planned` up to version 3 and `active` from version 4 gives `planned` at 3 and `active` at 4 and 5; a value removed in version 3 has no value at 3 and its value at 2;
  - system fields: a name changed in version 2 gives the old name at 1 and the new one at 2 and later; the current version gives the current columns;
  - mappings by date: a mapping with `valid_from` equal to the version's date counts, one day later does not;
  - a metatype change in version 3 gives the old metatype's mappings at 2 and the new one's at 3;
  - version boundaries: 1 and the current version exist, the current version + 1 does not.

## 3. Backend: state, diff and visibility

- [x] 3.1 Extract the visibility rule of `EntityHistory` into `HistoryAccess` (`app/history`), and the override event builder of `CatalogQueries` into `OverrideAccessAudit` (`app/metamodel`), without changing behavior.

  Verify: `EntityHistoryTests`, the catalog override tests and `features/repository/entity-history.feature` pass unchanged.
- [x] 3.2 `EntityVersions` in `app/history` (D2): state, diff of a version, comparison, values as text with `dataType`, labels, visibility and the override audit with channel `history`.

  Verify with `EntityVersionsTests`:
  - positive: the diff lists only changed fields with old and new value; an added value has no old value, and a removed value has no new value; the net comparison 2→7 leaves out a field changed and changed back;
  - text values: boolean, date, reference (entity name) and `metatypeId` (metatype name);
  - restricted: a `role-restricted` property without a read role is redacted without values; with `hide` for all roles it is left out of state and diff;
  - override: the values are returned, and one `property-override-access` event with `channel: history` names the property and the entity; a read through a read role writes none;
  - range boundaries: 1→7 and 6→7 are accepted, 7→7 and 0→7 give 400 naming `from`, and 1→8 gives 404; an unknown entity gives 404.
- [x] 3.3 `getEntityVersion` and `compareEntityVersions` in `EntityHistoryController`, and the 405 handler in `ApiExceptionHandler` (D3).

  Verify with controller tests:
  - `PUT` and `DELETE` on `/entities/{id}/versions/4` give 405 with `Allow: GET` and a problem document, and the state is unchanged;
  - `GET` with version 0 gives 400, and version 1 gives 200.
- [x] 3.4 Cucumber `features/repository/entity-versions.feature` with the scenarios of the spec `repository/entity-versions`:
  - "Complete state with empty properties", "Value of an older version", "Version boundaries", "Mapping effective after the version";
  - "Only changed fields", "Added and removed values", "Net diff across versions", "Version range boundaries";
  - "Restricted value in the diff", "Hidden in the complete state", "Read through the override role", "Read through a read role";
  - "Delete rejected", "Edit rejected", "Without permission".

  Verify that the feature passes.

## 4. Frontend: SCR-026

- [x] 4.1 Story first: `SCR-026-change-history.stories.ts` gets the states `Diff`, `CompleteState` and `Compare`, with a fake backend in `test/history.ts` for both operations. Verify that the stories render without a11y violations.
- [x] 4.2 `api/history.ts`: `fetchEntityVersion` and `compareEntityVersions`. `EntityHistoryPage.vue` (D5):
  - the expandable diff per version, cached;
  - "Show complete state vN";
  - the compare mode with the panel;
  - the restricted badge;
  - texts in `i18n` en/de.

  Verify with `EntityHistoryPage.spec.ts`:
  - expanding version 5 shows "Owner", `Kurt` struck through and `Michael`, and loads once when toggled twice;
  - "Created" has no diff but has "Show complete state v1";
  - the complete state shows "—" for an empty property;
  - compare: versions 7 and 2 request `from=2&to=7`, a third selection replaces the version selected first, and leaving the mode closes the panel;
  - a redacted field shows "Restricted" and no values;
  - a 404 of the diff shows the error message.
- [x] 4.3 `docs/screens/SCREENS.md`: SCR-026 stays `implemented`, and its entry names the diff, the complete state and the compare mode. Verify with `python3 scripts/check_screens.py`.

## 5. Documentation

- [x] 5.1 Realign US-099 (AC2, AC3), US-100 (AC2, AC3), REQ-099 (statement, AC3, implementation notes) and REQ-100 (statement, AC2, AC3, implementation notes) as listed in the proposal, with a change history entry in each REQ.
- [x] 5.2 `docs/audit-event-catalog.md`: the channel `history` of `property-override-access`. `business-objects/entity-version.md`: the note on reconstruction (Data Model).
- [x] 5.3 User manual `usage/change-history.md` in English and German: the diff, the complete state, the compare mode, and why an old state can miss an empty property of a removed mapping. Verify the manual build.

## 6. Wrap-up

- [x] 6.1 Set US-099 and US-100 to done and record the implementation hash; update the S12 status in `docs/sprint-plan.md`.
- [x] 6.2 Synchronize `docs/data-model.puml` (no change expected), `business-objects/` and `docs/screens/SCREENS.md`; regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [x] 6.3 Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --strict`, and verify they pass.

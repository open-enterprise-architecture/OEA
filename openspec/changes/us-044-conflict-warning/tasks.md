# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design D2):
  - the schema `SolutionDeltaConflict`;
  - required `conflicts` on `SolutionDelta` and `SolutionDiffEntry`, required `conflicted` on `SolutionDeltaList` and required `parallelChanges` on `SolutionLandscapeEntry`;
  - `showParallelChanges` on `MetatypeDetail`;
  - the operation `updateMetatypeParallelChanges` (`PUT /metamodel/metatypes/{id}/parallel-changes`, `metamodel:write`, `If-Match`) with the schema `MetatypeParallelChanges`.

  Verify: Redocly lint passes, the generated API compiles, and the contract authorization tests pass.

## 2. Migration

- [ ] 2.1 Flyway migration `V28__parallel_changes.sql` (D0, D3): the column `metatypes.show_parallel_changes BOOLEAN NOT NULL DEFAULT false` and the partial index `entity_deltas_entity ON entity_deltas (entity_id) WHERE entity_id IS NOT NULL`. Verify: the migration tests pass, and every existing metatype reads `false` after migrating.

## 3. Backend: conflicts of deltas

- [ ] 3.1 `SolutionConflicts` in `app/solution` (D0), with a test helper that sets `solutions.status` directly.

  Verify with `SolutionConflictsTests`:
  - positive: another solution with a delta `modified` or `retiring` for the same entity conflicts, in each active status (`draft`, `proposed`, `approved`, `in-progress`, parameterized); a conflict names the solution's ID, name and status, the owner's ID and name, and the type of its delta; two conflicting solutions are ordered by name, case-insensitive;
  - negative: no conflict with a solution `implemented` or `archived` (parameterized), with the solution itself, with a delta on another entity, or between two deltas `new` of the same name and metatype;
  - boundaries: 0 other solutions give an empty list, 1 gives one conflict, 2 give two; an empty set of entity IDs runs no query.
- [ ] 3.2 `SolutionDeltas` attaches `conflicts` to every delta of `list`, `create` and `update`, and returns `conflicted` (D1); `SolutionController` maps both.

  Verify with `SolutionDeltasTests`, extended:
  - positive: a captured delta returns 201 with its conflict; an edited delta returns 200 with its conflict; a delta read again still carries it;
  - negative: a conflict does not reject the write, and the delta is stored; once the other solution is `implemented`, or has removed its delta, the next read carries no conflict and `conflicted` is 0;
  - boundaries: `conflicted` counts over all pages: 60 deltas with 3 conflicts give 3 on page 0 of size 50; a solution without conflicts gives 0, and one with a single conflicting delta gives 1.
- [ ] 3.3 `SolutionDiff` attaches the conflicts to each entry (D1). Verify with `SolutionDiffTests`: an entry of a conflicting delta carries the conflict; an entry `new` carries none.
- [ ] 3.4 Cucumber `features/solution/solution-conflicts.feature`, with the scenarios:
  - "Conflict detected", "No conflict with a completed solution", "Every active status conflicts", "Multiple conflicts";
  - "New deltas never conflict", "Other entity", "Owner and delta type";
  - "Editing a conflicting delta", "Warning persists", "Number of conflicted deltas over all pages", "No conflicted deltas";
  - "Conflicts in the diff", "Other solution implemented", "Other delta removed".

  Verify that the feature passes.

## 4. Backend: the setting and the baseline marking

- [ ] 4.1 `ParallelChangeSettings` in `app/metamodel` and `MetamodelController.updateMetatypeParallelChanges`; `MetatypeCatalog` reads `showParallelChanges` (D3).

  Verify with `ParallelChangeSettingsTests`:
  - positive: an administrator turns the setting on for a built-in, an imported and an organization metatype; the version rises by 1; one audit event `metamodel.metatype-parallel-changes-changed` holds `before` false and `after` true; the metamodel changes list it under the kind `metatype`; it is saved while the instance is `import-only`; a re-import of the package keeps it on; a new metatype starts off;
  - negative: without `metamodel:write`, and with `solution-metamodel:write` only, the request answers 403; an outdated `If-Match` answers 412; an unknown metatype answers 404; a missing `showInBaseline` answers 400 or 422 as the contract states. In each case the setting and the version are unchanged and no event is written;
  - boundaries: turning it off again is saved; sending the current value is accepted and audited, as the re-import setting is.
- [ ] 4.2 `SolutionLandscape` attaches `parallelChanges` to the page and to `entry`, only for metatypes with the setting on (D1).

  Verify with `SolutionLandscapeTests`:
  - positive: with the setting on, an entity changed by another active solution names it in the page and in `entry`;
  - negative: with the setting off, the list is empty; it is also empty for a completed solution and for the solution's own delta; a subtype with the setting off and a base type with it on gives an empty list;
  - boundaries: 1 and 2 other solutions give 1 and 2 entries.
- [ ] 4.3 Cucumber:
  - in `features/solution/solution-conflicts.feature`: "Setting on", "Setting off", "Completed solution not shown" and "Own delta not shown";
  - in `features/metamodel/metatype-editing.feature`: "Turning the setting on for a built-in metatype", "Default off", "Without metamodel administration", "Outdated version", "Re-import keeps the setting" and "Not inherited";
  - in `features/metamodel/lock-mode.feature`: "Parallel-change setting in a locked instance".

  Verify that the features pass.

## 5. Frontend: warnings on SCR-061 and SCR-062

- [ ] 5.1 API clients and types for `conflicts`, `conflicted` and `parallelChanges`. The fake API in `src/test/solutions.ts` gains conflicts per entity, set by the test, and computes `conflicted`.
- [ ] 5.2 The component `components/solutions/SolutionConflicts.vue`, plus the warnings in `SolutionDeltas.vue`, `SolutionPage.vue` (header notice and the toast after saving) and `SolutionBaseline.vue` (marker) (D4).

  Verify with the specs:
  - positive: an entry with one conflict reads "Also changed in 'Solution A' (owner: Maria)" and links to `/solutions/{id}`; two conflicts show two lines; the header reads "1 change also concerns other active solutions" for 1 and the plural for 2; saving a conflicting delta shows the toast with the solution name; a baseline entry reads "Changed in 1 other solution" and "Changed in 2 other solutions" with the names;
  - negative: with `conflicted` 0, neither the notice nor any entry warning shows; saving a delta without conflicts shows no warning; a baseline entry without parallel changes shows no marker.
- [ ] 5.3 `SolutionDiffPage.vue` shows the conflicts on the card. Verify with `SolutionDiffPage.spec.ts`: a conflicting entry shows the warning line, and an entry without conflicts shows none.
- [ ] 5.4 i18n keys `solutions.conflicts.*` in `en.json` and `de.json`, with plural forms. Verify: the i18n completeness test passes.
- [ ] 5.5 Stories: SCR-061 `Conflicts` (entry, header notice and baseline marker) and SCR-062 `Conflicts`, each with a play function. Render the images, and in `docs/screens/SCREENS.md` name the conflict warnings for SCR-061 and SCR-062. Verify: story tests (including axe) and `python3 scripts/check_screens.py` pass.

## 6. Frontend: the switch on SCR-030

- [ ] 6.1 `MetatypeDetail.vue`: the section "Show parallel changes in the baseline" with a `USwitch` and a hint, editable with `canConfigure ?? canWrite`, otherwise text; the client `updateMetatypeParallelChanges` with `If-Match` and the collision handling of the re-import setting; i18n `metamodel.parallelChanges.*` (D4).

  Verify with the specs:
  - positive: switching sends `showInBaseline` true with the current version, and shows the new value; switching works in a locked metamodel;
  - negative: without `metamodel:write`, the value shows as text without a switch; a 412 shows the collision warning, and the switch returns to the stored value.
- [ ] 6.2 Story SCR-030 with the switch in the metatype detail; render the images, and name the setting for SCR-030 in `docs/screens/SCREENS.md`. Verify: story tests and `python3 scripts/check_screens.py` pass.

## 7. Manual

- [ ] 7.1 `manual/usage/solutions.md` and `manual/de/usage/solutions.md`: the section "Parallel changes" / "Parallele Änderungen" (what conflicts, where the warnings show, when they disappear, the baseline marker and its setting), and the API fields. `manual/administration/metamodel-editing.md` and its German counterpart: the setting with its default and its effect. Verify: the manual tests and build pass.

## 8. Wrap-up

- [ ] 8.1 Doc sync:
  - US-044: status, sprint S11, the realigned AC3, AC4 and Technical Notes;
  - REQ-042: AC5, AC6, the Error Cases and the Implementation Notes, plus the version and change history;
  - `business-objects/solution.md` (BR-05) and `business-objects/metamodel-configuration.md` (the attribute);
  - `docs/data-model.puml` (Data Model);
  - `docs/audit-event-catalog.md`: `metamodel.metatype-parallel-changes-changed`;
  - `docs/screens/SCREENS.md` (done in 5.5 and 6.2);
  - the sprint plan: the S11 row with the decisions and the status of change 5.
- [ ] 8.2 Run `python3 scripts/generate_traceability.py`, `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, markdownlint and `openspec validate us-044-conflict-warning --strict`. Verify that all pass.
- [ ] 8.3 Full verification: backend `verify` (surefire, failsafe, Cucumber), frontend typecheck, unit and story tests and build, and manual tests and build. Verify that all pass.

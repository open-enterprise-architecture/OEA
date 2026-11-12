# Tasks

## 1. Data model and contract

- [ ] 1.1 Flyway migration `V26__entity_deltas.sql` with `entity_deltas`, `entity_delta_property_values`, the checks, the unique indexes and the grants of D1.

  Verify:
  - the backend starts against an empty database and `MigrationStepTests` passes;
  - a row `new` without a name and a row `retiring` without `entity_id` are rejected by the check (SQL test);
  - a value row with no value or with two values is rejected.
- [ ] 1.2 `api/openapi.yaml`:
  - `createSolutionDelta`, `listSolutionDeltas` and `deleteSolutionDelta` with their responses;
  - the schemas `SolutionDeltaWrite`, `SolutionDelta` and `SolutionDeltaList` (design, API);
  - `x-oea-authorization` of D4 and ADR-032.

  Verify: Redocly lint passes, the generated `SolutionsApi` compiles, and the contract authorization test passes.

## 2. Backend: capture, read and remove

- [ ] 2.1 Move `requireWritable` and `writer(access)` from `EntityBatch` into `app/entity/PropertyWriteAccess` (D2). Verify: the existing batch, `role-restricted` and `connection-scoped` test classes and their Cucumber features pass unchanged.
- [ ] 2.2 `app/solution/SolutionDeltas`:
  - `create`, `list` and `delete` with the checks and their order (D2–D7);
  - `SolutionCompletedException`, mapped to 409 `solution-completed`;
  - the audit events of D8;
  - `SolutionController` implements the three operations.

  Verify with `SolutionDeltasTests` (integration). Positive cases:
  - a delta `new` with values of type string, integer, boolean, date, enum and reference is stored and read back;
  - a delta `retiring` is stored, and the list carries its entity ID and the current name of the entity;
  - order of the list: `new` before `retiring`, by name case-insensitively;
  - removal returns 204, also removes the values, and then lets the same delta be captured again;
  - another holder of `solution:write` than the owner captures a delta;
  - the same entity retired in two solutions, and the same name `new` with another metatype, are both accepted.

  Negative cases, each storing nothing:
  - an unknown metatype, a connection metatype, an invalid value and a missing mandatory property answer 422 with the field named;
  - for `retiring`: an unknown ID, a deleted entity, a data flow, and a request that also sends `name`, each answer 422 with the field named;
  - a second `retiring` delta for the same entity, a second `new` delta with the same metatype and a name in other case, and a concurrent duplicate (unique index) answer 422 "A delta already exists for this entity in this solution";
  - capture and removal in a solution in `implemented` and in `archived` (status set in the test database) answer 409 and change nothing; `proposed`, `approved` and `in-progress` accept a capture (parameterized);
  - a `role-restricted` value without a write role and a `connection-scoped` value answer 403; a `role-restricted` value with a write role is accepted;
  - on read, a `role-restricted` value is redacted for a reader without a read role and shown to one with one;
  - an unknown solution answers 404 on all three operations, and a delta of another solution answers 404 on removal;
  - without `solution:write` (`portal-reader` with `entity:read`), capture and removal answer 403; without `entity:read`, the read answers 403;
  - `solution.delta-added` and `solution.delta-removed` hold the details of D8 and no property values, and a rejected capture writes no event.

  Boundaries (parameterized):
  - name: 255 characters accepted; 256 characters and blank answer 422;
  - description: 2000 characters accepted; 2001 answer 422;
  - limit: with 999 deltas a capture answers 201, and with 1000 the next one answers 422 naming `deltas`.
- [ ] 2.3 Cucumber feature `features/solution/solution-deltas.feature` with the scenarios:
  - "New entity", "New entity with property values", "Unknown metatype", "Connection metatype", "Invalid property value", "Mandatory property missing", "Length of the name", "Length of the description";
  - "Restricted property with a write role", "Restricted property without a write role", "Connection-scoped property", "Restricted value redacted on read";
  - "Retire an entity", "Entity not in the baseline";
  - "Second new entity with the same name", "Same name, other metatype", "Entity retired twice", "Other solution";
  - "Limit of deltas";
  - "Implemented solution", "Archived solution", "Solution in progress";
  - "Delta list", "No deltas", "Unknown solution";
  - "Remove a delta", "Delta of another solution";
  - "Other solution architect", "Without solution:write", "Without entity:read";
  - "Delta captured", "Delta removed", "Rejected capture".

  Verify that they pass.
- [ ] 2.4 `docs/audit-event-catalog.md`: `solution.delta-added` and `solution.delta-removed` with their details. Verify: the event names match those written by `SolutionDeltas`, and link validation passes.

## 3. Frontend: area "Changes", dialog "New entity" and action "Retire"

- [ ] 3.1 API client `fetchSolutionDeltas`, `createSolutionDelta` and `deleteSolutionDelta` in `src/api/solutions.ts`. The fake API of `src/test/solutions.ts` holds deltas statefully and answers the duplicate rule (422), a completed solution (409) and a field error (422).
- [ ] 3.2 `SolutionDeltas.vue` and `SolutionDeltaDialog.vue` on SCR-061, and the action "Retire" in `SolutionBaseline.vue` (D9).

  Verify (`SolutionPage.spec.ts`, `SolutionDeltaDialog.spec.ts`):
  - the list shows type, name and metatype, and "No changes yet" when empty;
  - the dialog offers the element metatypes only, and one field per mapping of the chosen metatype by data type, with mandatory ones marked;
  - saving sends name, description and the values as text, closes the dialog and lists the delta;
  - a 422 keeps the dialog open with the message at the field (`name`, `properties[0].value`), and a 403 shows the restricted message;
  - name: 255 characters can be entered, the 256th is cut by `maxlength`; description: 2000 and 2001 likewise;
  - "Retire" in the baseline lists the delta, and the row then shows "Retiring" instead of the action;
  - "Remove" asks for confirmation; cancel keeps the delta, and confirm removes it;
  - without `solution:write`, and for a solution in `implemented`, the list shows no "New entity", "Remove" or "Retire";
  - a 409 hides the actions.
- [ ] 3.3 i18n keys `solutions.deltas.*` in `en.json` and `de.json`. Verify: the i18n completeness test passes.
- [ ] 3.4 Stories in `SCR-061-solution.stories.ts`: the first story shows a solution with deltas, plus "Changes", "New entity dialog" and "Read only" with play functions. Render the images, and name the area "Changes" and the action "Retire" in SCR-061 of `docs/screens/SCREENS.md`. Verify: story tests and `python3 scripts/check_screens.py` pass.

## 4. Manual

- [ ] 4.1 The section "Changes" / "Änderungen" in `manual/usage/solutions.md` and `manual/de/usage/solutions.md`: capture a new entity, retire an entity of the baseline, remove a delta, and why a completed solution cannot be changed. The walkthrough captures one delta. Verify: the manual tests and build pass.

## 5. Wrap-up

- [ ] 5.1 Doc sync:
  - US-040 and US-042: status, sprint S11, the realigned AC and Technical Notes (proposal);
  - REQ-040: the realigned Statement, Context and Implementation Notes, plus the version and change history;
  - `business-objects/solution.md` 0.5.0 and `docs/data-model.puml` (design, Data Model);
  - the sprint plan: the S11 row with the decisions and the status of change 2.
- [ ] 5.2 Run `python3 scripts/generate_traceability.py`, `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, markdownlint and `openspec validate us-040-entity-deltas --strict`. Verify that all pass.
- [ ] 5.3 Full verification: backend `verify` (surefire, failsafe, Cucumber), frontend typecheck, unit and story tests and build, and manual tests and build. Verify that all pass.

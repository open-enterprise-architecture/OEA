# Tasks

## 1. Data model and contract

- [x] 1.1 Flyway migration `V27__entity_delta_changes.sql` (design D1).

  Verify with `EntityDeltaSchemaTests`, extended:
  - a row with both `property_def_id` and `system_field`, or with neither, is rejected;
  - a row with two after values or two before values is rejected, and a row with no after value is accepted;
  - a second row for the same system field of a delta is rejected;
  - `oea_app` holds `UPDATE` only on `updated_at` and `updated_by`.

  `MigrationStepTests` passes.
- [x] 1.2 `api/openapi.yaml`:
  - `SolutionDeltaType` with `modified`; `SolutionDeltaWrite` extended;
  - the new schemas `SolutionDeltaUpdate`, `SolutionDeltaValue`, `SolutionDeltaChange` and `SolutionLandscapeEntity`; `SolutionDelta` with `changes` and `updatedAt`;
  - `updateSolutionDelta` and `getSolutionLandscapeEntity` (design, API).

  Verify: Redocly lint passes, the generated API compiles, and the contract authorization tests pass.

## 2. Backend: capture, edit and read modifications

- [x] 2.1 `PropertyWriteAccess.require` with an entity ID, `PropertyAccess.Snapshot.readableFor(property, entityId)` and `EntityStore.lockForShare` (D2, D4).

  Verify:
  - the existing batch, `RestrictedWriteTests` and `ScopingConnectionTests` pass unchanged;
  - unit tests of `readableFor`: a `connection-scoped` property is readable for an entity in scope and not for one outside it; the other modes behave like `readable`.
- [x] 2.2 `SolutionDeltas`: capture `modified` (D2), edit (D3), read the changes (D4) and the audit (D5); `SolutionController` implements `updateSolutionDelta`.

  Verify with `SolutionDeltasTests`, extended. Positive cases:
  - a property change records the catalog value as its before value, and the catalog is unchanged;
  - name and description changes;
  - removing an optional value;
  - the before value stays after the catalog changes;
  - an edit keeps the recorded before value of a kept field, records the current value of an added field and drops a field that is no longer sent;
  - the list order is `new`, `modified`, `retiring`, with the changes in the order name, description, properties.

  Negative cases, each storing nothing:
  - an unknown, deleted or connection entity answers 422 `entityId`;
  - a delta without any change, and one whose only value equals the current value, answer 422 `properties`;
  - an invalid value answers 422 naming `properties[0].value`;
  - removing a mandatory value answers 422 `properties`;
  - `retiring` then `modified` for the same entity answers 422 duplicate;
  - editing a `new` or `retiring` delta answers 422 `deltaType`; editing a delta of another solution answers 404;
  - capture, edit and removal in an `implemented` or `archived` solution answer 409;
  - a `role-restricted` value without a write role and a `connection-scoped` value outside the scope answer 403, also on an edit;
  - on read, the before and after values of a restricted property are redacted for a reader without a read role;
  - without `solution:write`, an edit answers 403;
  - the audit events `solution.delta-added` and `solution.delta-updated` name the fields but no values, and a rejected request writes none.

  Boundaries (parameterized):
  - new name: 255 characters accepted; 256 characters and blank answer 422;
  - new description: 2000 characters accepted; 2001 answer 422.
- [x] 2.3 `SolutionLandscape.entry` and `getSolutionLandscapeEntity` (D4).

  Verify with `SolutionLandscapeTests`, extended:
  - an entry carries name, description, metatype, version and its values;
  - a restricted value is redacted without a read role;
  - an unknown, deleted or connection entity and an unknown solution answer 404;
  - without `entity:read`, the read answers 403.
- [x] 2.4 Cucumber scenarios for the ADDED and MODIFIED requirements of `solution/solution-deltas`, in `features/solution/solution-deltas.feature`:
  - "Modify a property", "Rename and describe", "Remove a value", "Entity not in the baseline" (modified);
  - "Before value from the catalog", "Before value stays when the catalog changes", "Nothing changes";
  - "Invalid value", "Mandatory value removed", "Length of the new name", "Length of the new description";
  - "Restricted property without a write role", "Connection-scoped property outside the scope", "Restricted values redacted on read";
  - "Edit the after value", "Edit a retiring delta", "Edit without solution:write";
  - "Modification audited", "Retired entity modified", and "Implemented solution" with an edit.

  Add the scenarios of "Read one entry of the baseline" to `features/solution/solution-baseline.feature`. Verify that both features pass.
- [x] 2.5 `docs/audit-event-catalog.md`: `solution.delta-updated`, and the `fields` detail for `modified` in `solution.delta-added`. Verify: link validation passes.

## 3. Frontend: dialog "Modify entity", "Modify" and "Edit"

- [x] 3.1 API client `updateSolutionDelta` and `fetchSolutionLandscapeEntity`; the fake API of `src/test/solutions.ts` holds entries with values and records before values.
- [x] 3.2 `PropertyInput.vue`, extracted from `SolutionDeltaDialog.vue`. Verify: `SolutionDeltaDialog.spec.ts` passes unchanged.
- [x] 3.3 `SolutionModifyDialog.vue`, "Modify" and "Modified" in `SolutionBaseline.vue`, and "Edit" with the number of changes in `SolutionDeltas.vue` (D6).

  Verify (`SolutionModifyDialog.spec.ts`, `SolutionPage.spec.ts`):
  - the dialog shows the current values as read-only text, and a redacted one as hidden;
  - it sends only changed fields, and a cleared input as `""`;
  - opened through "Edit", it starts from the after values;
  - a 422 shows the message at the field;
  - "Modify" in the baseline lists the delta and marks the row "Modified";
  - name: 255 characters can be entered and the 256th is cut by `maxlength`; description: 2000 and 2001 likewise;
  - without `solution:write` there is no "Modify" and no "Edit".
- [x] 3.4 i18n keys `solutions.deltas.modify.*` in `en.json` and `de.json`. Verify: the i18n completeness test passes.
- [x] 3.5 Stories in `SCR-061-solution.stories.ts`: the first story with a delta `modified`, plus "Modify entity dialog" with a play function. Render the images, and name "Modify" in SCR-061 of `docs/screens/SCREENS.md`. Verify: story tests and `python3 scripts/check_screens.py` pass.

## 4. Manual

- [x] 4.1 The section "Changes" / "Änderungen" in `manual/usage/solutions.md` and `manual/de/usage/solutions.md`: modify an entity, recorded before values, edit a modification; the API with `PUT`. Verify: the manual tests and build pass.

## 5. Wrap-up

- [x] 5.1 Doc sync:
  - US-041: status, sprint S11, the realigned AC and Technical Notes;
  - REQ-040: AC3 and the Implementation Notes, plus the version and change history;
  - `business-objects/solution.md` 0.6.0 and `docs/data-model.puml`;
  - the sprint plan: the S11 row with the status of change 3.
- [x] 5.2 Run `python3 scripts/generate_traceability.py`, `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, markdownlint and `openspec validate us-041-modified-delta --strict`. Verify that all pass.
- [x] 5.3 Full verification: backend `verify` (surefire, failsafe, Cucumber), frontend typecheck, unit and story tests and build, and manual tests and build. Verify that all pass.

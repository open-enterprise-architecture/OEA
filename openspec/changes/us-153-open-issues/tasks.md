# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design D5): `resolveSolutionOpenIssues`, the new fields of `SolutionOpenIssue`, `CatalogRow.openIssues`, `openIssues` in the history page, the problem type `entity-blocked`, and `OPEN_ISSUES` in the batch description.
- [ ] 1.2 `api/openapi.yaml` (D5, snapshots): the decisions of `deletePropertyMapping`, `deleteEnumValue` and `updateMetatype`, the problem type `solution-decision-required`, `DeltaConflict.kind`, the choice `discard`, the issue kind `not-in-metamodel` with `keptValue`, and `snapshot` in the effective metamodel.

  Verify: Redocly lint and both authorization scripts pass.

## 2. Backend: schema, store and guard

- [ ] 2.1 `V36__open_issue_resolution.sql` (D1) and `core/solution/OpenIssueStore` (D2); move `openIssues` and `openIssueCount` out of `GoLiveStore`.

  Verify with a migration test:
  - the three columns are all null or all set, and a partial set is rejected;
  - `resolution` accepts `value` and `removed` and rejects `automatic`;
  - the runtime role updates the three columns and no other;
  - `kept_value` is set exactly for `not-in-metamodel`;
  - a snapshot row is unique per solution, metatype, property and enumeration value, and a definition with a snapshot cannot be deleted.
- [ ] 2.2 `app/entity/EntityBlocks` (D3).

  Verify with `EntityBlocksTests`:
  - `mandatory-missing` holds while the property is mandatory and empty, and no longer holds once it is optional or has a value;
  - `invalid-value` holds above `max_value` and no longer holds when the maximum is raised;
  - an entity without rows is not blocked;
  - a stored resolution is not evaluated.

- [ ] 2.3 `core/solution/SnapshotStore` and `core/solution/DeltaValueStore` (D9), and the `MetatypeRules` overload with a solution (D10).

  Verify:
  - the rules with a solution contain its snapshot property as optional, and its enumeration value;
  - the rules without a solution do not.

## 3. Backend: resolution and blocking

- [ ] 3.1 `app/solution/OpenIssues` with list and resolution (D4), and `openIssueCount` of open issues only.

  Verify with `OpenIssuesTests`:
  - a value resolves with `value`, a removal with `removed`, and a metamodel change counts as `automatic`;
  - the 422s name the issue and change nothing: a rejected value, the removal of a mandatory value, a resolved issue, and an issue of another solution;
  - the `max_value` boundary: 1000 is accepted and 1001 rejected;
  - the list size: 1 and 200 items are accepted, and 0 and 201 rejected;
  - `solution:write` without the write role resolves a role-restricted property;
  - `entity:write` without the write role gives 403, and with it the issue resolves;
  - `entity:read` only gives 403;
  - `solution.open-issues-resolved` and `entity.updated` are written.
- [ ] 3.2 Blocking (D3).

  Verify, each with an entity with an open issue (blocked) and one without (not blocked):
  - in `EntityBatchTests`, `OPEN_ISSUES` with `atomic` false (the other item is written) and true (nothing is written);
  - in `EntityRestoreTests`, the full and the partial restore give 409 `entity-blocked`, and the preview gives 200;
  - in `SolutionDeltasTests`, `modified` and `retiring` give 409, `new` is stored, and an existing delta stays editable;
  - in `SolutionGoLiveTests`, the 409 names the deltas, and the go-live runs after the resolution.
- [ ] 3.3 Controllers: `resolveSolutionOpenIssues`, the `entity-blocked` handler in `ApiExceptionHandler`, `CatalogRow.openIssues` and the history field (D6).

  Verify with controller tests: the catalog row is true and false, and the history lists the issue and is empty without one.
- [ ] 3.4 Cucumber `features/solution/solution-open-issues.feature` with the scenarios of the delta specs.

  Verify that it passes, together with the `@solution` and catalog scenarios.

## 3b. Backend: metamodel question and snapshots

- [ ] 3.5 `app/metamodel/SolutionImpact` in `MetamodelEffects` and `PropertyDefinitions` (D9).

  Verify with `SolutionImpactTests`:
  - an affected solution through a delta value, a subtype delta, or an extension metatype;
  - no question for `implemented`, `archived` or unaffected solutions;
  - a missing decision gives 409 listing all affected solutions, and an extra decision gives 422, both without a change;
  - `apply` removes the values and an empty modification, and `keep` stores the snapshot;
  - the audit details name the decisions;
  - the enumeration value and base type paths behave the same;
  - a definition with a snapshot gives 409 `in-use`.
- [ ] 3.6 Snapshots in checks and the effective metamodel (D10).

  Verify:
  - a delta of the own solution accepts a snapshot property, and one of another solution rejects it with 422;
  - the batch rejects it;
  - `listMetatypes?configId=` marks it `snapshot`.
- [ ] 3.7 Snapshot values at the go-live and their open issues (D11).

  Verify:
  - `not-in-metamodel` conflicts are listed and do not cause `merge-required`;
  - `discard` removes the value, and the field choices give 422 for this kind;
  - the go-live inserts the issue with the kept value and drops the snapshots;
  - the issue resolves with `remove`, gives 422 for a value while not mapped, and resolves with a value after mapping again;
  - it is never resolved automatically.
- [ ] 3.8 Cucumber `features/metamodel/metamodel-solution-impact.feature`, with the scenarios of the metamodel delta specs and the snapshot scenarios of `solution-go-live`.

  Verify that it passes, together with the `@metamodel` scenarios.

## 4. Frontend

- [ ] 4.1 Stories first:
  - SCR-064 `OpenIssues` (new);
  - SCR-061 `Implemented` with "Resolve";
  - SCR-020 with a marked row;
  - SCR-026 with the notice.

  Verify that the stories render without a11y violations.
- [ ] 4.2 `api/solutions.ts`, `OpenIssuesDialog.vue` and `SolutionPage.vue` (D7). Texts go in `i18n` en/de.

  Verify with the specs:
  - the status badge;
  - "Resolve" appears only with `solution:write` or `entity:write` and open issues;
  - inputs are typed per data type;
  - "Remove value" appears only for `invalid-value` of an optional property;
  - "Apply" is disabled until every issue has an entry;
  - a 422 marks the issue;
  - the list refreshes after success.
- [ ] 4.3 `CatalogPage` marker and `EntityHistoryPage` notice (D6).

  Verify with the specs:
  - the marker appears for `openIssues: true` and not for false;
  - the notice links each solution, and no notice appears without issues.
- [ ] 4.4 `SolutionDecisions.vue` in the metamodel dialogs, and snapshot values in `DeltaMergeDialog` (D12). Stories of SCR-030 with the question, and of SCR-063 with a snapshot value.

  Verify with the specs:
  - the first 409 shows the list;
  - "Confirm" is disabled until every solution has an answer, and enabled after the last;
  - the second request carries the decisions;
  - SCR-063 shows "Discard value" and "Resolve later", and "Apply" does not wait for snapshot values.
- [ ] 4.5 `docs/screens/SCREENS.md`: SCR-064 `implemented`; SCR-030, SCR-061, SCR-063, SCR-020 and SCR-026 name the additions.

  Verify with `python3 scripts/check_screens.py`.

## 5. Documentation and wrap-up

- [ ] 5.1 Update the documentation:
  - `docs/audit-event-catalog.md` (`solution.open-issues-resolved`);
  - `docs/data-model.puml` and `business-objects/solution.md` (resolution columns, BR-08 blocking);
  - the user manual "Solutions", "Catalogs", "Change history" and "Metamodel" in English and German;
  - UC-04 (the question per solution), `docs/audit-event-catalog.md` (decisions in the metamodel events) and `business-objects/solution.md` (snapshots).
- [ ] 5.2 Set US-153 and US-154 to done with the implementation note; update the S14 row; regenerate `requirements/traceability.md`.
- [ ] 5.3 Run `python3 scripts/validate_links.py`, markdownlint, `openspec validate --strict` and `check_manual.py`, and verify they pass.

# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml`: `getSolutionDiff` with `entity:read`, and the schemas `SolutionDiff`, `SolutionDiffEntry` and `SolutionDiffField` (design D1).

  Verify: Redocly lint passes, the generated API compiles, and the contract authorization tests pass.

## 2. Backend: compute the diff

- [ ] 2.1 `EntityStore.values(Collection<Integer>)` and a read of the affected entities with their deleted flag (design D2, step 3).

  Verify: the existing entity tests pass unchanged; a unit or integration test reads the values of two entities in one call and returns an empty map for an empty collection.
- [ ] 2.2 `SolutionDiff` in `app/solution` and `SolutionController.getSolutionDiff` (D2).

  Verify with `SolutionDiffTests`. Positive cases:
  - `new`: no current state, the target holds name, description and values, every field changes;
  - `modified`: the changed field from catalog to after value; an unchanged property shows the catalog value on both sides, not changing; a removed value has no target value;
  - `retiring`: the current state, no target state;
  - the order follows the delta list; only affected entities appear (3 deltas in a baseline of more entities give 3 entries);
  - drift: a recorded before value that differs from the catalog marks `catalogChanged`; an equal one does not;
  - an entity deleted after capture has `inCatalog` false and no current state;
  - a reference value carries ID and name.

  Negative cases:
  - an unknown solution answers 404;
  - without `entity:read`, the read answers 403;
  - a `role-restricted` value is redacted on both sides for a reader without a read role, with no drift mark, and readable with the role;
  - a `connection-scoped` value outside the reader's scope is redacted.

  Boundaries: a solution without deltas returns an empty list (0); a solution with 1000 deltas, the limit, returns 1000 entries.
- [ ] 2.3 Cucumber scenarios for `solution/solution-diff` in `features/solution/solution-diff.feature`: "Only affected entities", "No deltas", "Unknown solution or missing permission", "Retiring entity", "New entity", "Modified entity", "Removed value", "Reference value", "Catalog changed since capture", "Catalog unchanged", "Entity deleted after capture", "Restricted value redacted", "Restricted value readable". Verify that the feature passes.

## 3. Frontend: screen SCR-062 and the link on SCR-061

- [ ] 3.1 API client `fetchSolutionDiff`; the fake API of `src/test/solutions.ts` returns a diff computed from its deltas and entries, including drift.
- [ ] 3.2 `SolutionDiffPage.vue` with the route `/solutions/:id/diff` (D3).

  Verify (`SolutionDiffPage.spec.ts`):
  - `new` shows "New" and "Does not exist yet";
  - `retiring` shows "Retiring" and "Out of service" with struck-through values;
  - `modified` highlights the changed field and dims the unchanged one;
  - drift shows "Changed in the catalog since capture"; an entity no longer in the catalog shows "No longer in the catalog";
  - a redacted field shows "Hidden";
  - the counts per type; with 0 deltas the notice "No changes captured yet" and no error; with 1 delta one card;
  - "Refresh" loads the diff again and shows a delta added meanwhile;
  - a failed load and a 404 show their message.
- [ ] 3.3 The link "Compare" in `SolutionDeltas.vue`, shown when deltas exist. Verify (`SolutionPage.spec.ts`): the link points to `/solutions/{id}/diff` with one delta and is absent with none.
- [ ] 3.4 i18n keys `solutions.diff.*` in `en.json` and `de.json`. Verify: the i18n completeness test passes.
- [ ] 3.5 Story `SCR-062-solution-diff.stories.ts` (all three types with drift, empty, narrow screen) with a play function; the story of SCR-061 with "Compare". Render the images, and add SCR-062 to `docs/screens/SCREENS.md` with status `implemented`, and name "Compare" in SCR-061. Verify: story tests (including axe) and `python3 scripts/check_screens.py` pass.

## 4. Manual

- [ ] 4.1 The section "Compare" / "Vergleichen" in `manual/usage/solutions.md` and `manual/de/usage/solutions.md`: current and target state, markings, drift; the API with `GET /solutions/{id}/diff`. Verify: the manual tests and build pass.

## 5. Wrap-up

- [ ] 5.1 Doc sync:
  - US-043: status, sprint S11, the realigned AC3, AC6 and Technical Notes;
  - REQ-041: AC2, AC3 and the Implementation Notes, plus the version and change history;
  - `docs/screens/SCREENS.md` (done in 3.5); no change to `docs/data-model.puml` (D2: nothing is stored);
  - the sprint plan: the S11 row with the owner decisions and the status of change 4.
- [ ] 5.2 Run `python3 scripts/generate_traceability.py`, `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, markdownlint and `openspec validate us-043-diff-view --strict`. Verify that all pass.
- [ ] 5.3 Full verification: backend `verify` (surefire, failsafe, Cucumber), frontend typecheck, unit and story tests and build, and manual tests and build. Verify that all pass.

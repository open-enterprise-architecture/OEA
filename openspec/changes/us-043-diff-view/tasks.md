# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design D0, D1):
  - `listSolutionDeltas` with `page`, `pageSize` and `cursor`; `SolutionDeltaList` with `page`, `counts` and `nextCursor`; the new schema `SolutionDeltaCounts`;
  - `createSolutionDelta` without the limit of 1000 and its 422 `deltas`;
  - `SolutionLandscapeEntry` with `deltaType`;
  - `getSolutionDiff` with `entity:read` and the same paging parameters, and the schemas `SolutionDiff`, `SolutionDiffEntry` and `SolutionDiffField`.

  Verify: Redocly lint passes, the generated API compiles, and the contract authorization tests pass.

## 2. Backend: no limit, paged deltas, baseline markers

- [ ] 2.1 `SolutionDeltas`: remove `MAX_DELTAS`; page `list` by cursor with `CatalogCursors` and return the counts per type (D0); `SolutionController.listSolutionDeltas` maps the page.

  Verify with `SolutionDeltasTests`, extended:
  - positive: a solution with 1000 deltas accepts the 1001st (the former limit and the value beyond it); every page of size 20 over 60 deltas of all types holds each delta once, in the order of the list, with the counts over all 60, and the last page has no `nextCursor`; a solution without deltas returns an empty page with the counts 0;
  - negative: a changed cursor, a cursor of another solution and a cursor without `page` answer 400 naming `cursor`;
  - boundaries (parameterized): page size 1 and 200 accepted, 0 and 201 answer 400.

  The former limit test is removed.
- [ ] 2.2 `SolutionLandscape`: `deltaType` per entry in the baseline query and in `entry` (D0).

  Verify with `SolutionLandscapeTests`, extended: an entity with a delta `retiring` or `modified` in this solution carries that type; an entity without a delta, and one with a delta only in another solution, carries none; the entry read carries it as well.
- [ ] 2.3 Cucumber in `features/solution/solution-deltas.feature`: the scenario "Limit of deltas" is replaced by "No limit of deltas"; add "Pages by cursor", "Limits of the page size" and "Cursor of another solution". In `features/solution/solution-baseline.feature`: "Marker of an entity with a delta" and "Delta of another solution". Verify that both features pass.

## 3. Backend: compute the diff

- [ ] 3.1 `EntityStore.values(Collection<Integer>)` and a read of the affected entities with their deleted flag (design D2, step 3).

  Verify: the existing entity tests pass unchanged; a test reads the values of two entities in one call and returns an empty map for an empty collection.
- [ ] 3.2 `SolutionDiff` in `app/solution` and `SolutionController.getSolutionDiff` (D2).

  Verify with `SolutionDiffTests`. Positive cases:
  - `new`: no current state, the target holds name, description and values, every field changes;
  - `modified`: the changed field from catalog to after value; an unchanged property shows the catalog value on both sides, not changing; a removed value has no target value;
  - `retiring`: the current state, no target state;
  - the order follows the delta list; only affected entities appear (3 deltas in a baseline of more entities give 3 entries);
  - drift: a recorded before value that differs from the catalog marks `catalogChanged`; an equal one does not;
  - an entity deleted after capture has `inCatalog` false and no current state;
  - a reference value carries ID and name;
  - paging: every page of size 50 over 120 deltas holds each entry once with the counts 50, 40 and 30, and the last page has no `nextCursor`.

  Negative cases:
  - an unknown solution answers 404;
  - without `entity:read`, the read answers 403;
  - a changed cursor answers 400 naming `cursor`;
  - a `role-restricted` value is redacted on both sides for a reader without a read role, with no drift mark, and readable with the role;
  - a `connection-scoped` value outside the reader's scope is redacted.

  Boundaries: a solution without deltas returns an empty list (0 entries) and one with a single delta one entry; page size 1 and 200 accepted, 0 and 201 answer 400.
- [ ] 3.3 Cucumber scenarios for `solution/solution-diff` in `features/solution/solution-diff.feature`: "Only affected entities", "No deltas", "Unknown solution or missing permission", "Pages by cursor", "Limits of the page size", "Retiring entity", "New entity", "Modified entity", "Removed value", "Reference value", "Catalog changed since capture", "Catalog unchanged", "Entity deleted after capture", "Restricted value redacted", "Restricted value readable". Verify that the feature passes.

## 4. Frontend: "Load more" on SCR-061 and the screen SCR-062

- [ ] 4.1 API client: `fetchSolutionDeltas` with paging, `fetchSolutionDiff`; the fake API of `src/test/solutions.ts` pages its deltas, returns `deltaType` on its baseline entries, and returns a diff computed from its deltas and entries, including drift.
- [ ] 4.2 SCR-061 (D0, D3): `SolutionDeltas.vue` with the total, "Load more" and the link "Compare"; `SolutionPage.vue` loads the first page after each write and takes the baseline markers from `deltaType`.

  Verify (`SolutionPage.spec.ts`, `SolutionDeltas.spec.ts` if present):
  - 60 deltas show 50, the total 60 and "Load more"; "Load more" shows all 60 and hides the button; 50 deltas show no "Load more" (boundary of one page);
  - a captured delta reloads the first page;
  - a baseline row whose delta is not on the loaded page shows "Modified";
  - "Compare" points to `/solutions/{id}/diff` with one delta and is absent with none.
- [ ] 4.3 `SolutionDiffPage.vue` with the route `/solutions/:id/diff` (D3).

  Verify (`SolutionDiffPage.spec.ts`):
  - `new` shows "New" and "Does not exist yet";
  - `retiring` shows "Retiring" and "Out of service" with struck-through values;
  - `modified` highlights the changed field and dims the unchanged one;
  - drift shows "Changed in the catalog since capture"; an entity no longer in the catalog shows "No longer in the catalog";
  - a redacted field shows "Hidden";
  - the counts per type; with 0 deltas the notice "No changes captured yet" and no error; with 1 delta one card;
  - 60 entries show 50 and "Load more", which appends the other 10;
  - "Refresh" loads the first page again and shows a delta added meanwhile;
  - a failed load and a 404 show their message.
- [ ] 4.4 i18n keys `solutions.diff.*` and the keys of "Load more" and the total in `en.json` and `de.json`. Verify: the i18n completeness test passes.
- [ ] 4.5 Story `SCR-062-solution-diff.stories.ts` (all three types with drift, empty, narrow screen, a following page) with a play function; the story of SCR-061 with "Compare" and "Load more". Render the images; add SCR-062 to `docs/screens/SCREENS.md` with status `implemented`, and name "Compare" and "Load more" in SCR-061. Verify: story tests (including axe) and `python3 scripts/check_screens.py` pass.

## 5. Manual

- [ ] 5.1 `manual/usage/solutions.md` and `manual/de/usage/solutions.md`: the section "Compare" / "Vergleichen" (current and target state, markings, drift, "Load more"); the rule "at most 1000 changes" removed; "Load more" in "Changes"; the API with `GET /solutions/{id}/diff` and the paging of the deltas. Verify: the manual tests and build pass.

## 6. Wrap-up

- [ ] 6.1 Doc sync:
  - US-043: status, sprint S11, the realigned AC3, AC6 and Technical Notes;
  - REQ-041: AC2, AC3 and the Implementation Notes, plus the version and change history;
  - REQ-040: the constraint "At most 1000 deltas per solution" removed, the paged list, plus the version and change history;
  - `business-objects/solution.md` (BR-06) and `docs/data-model.puml` (D0, Data Model);
  - `docs/screens/SCREENS.md` (done in 4.5);
  - the sprint plan: the S11 row with the status of change 4 and the removed limit in the status of change 2.
- [ ] 6.2 Run `python3 scripts/generate_traceability.py`, `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, markdownlint and `openspec validate us-043-diff-view --strict`. Verify that all pass.
- [ ] 6.3 Full verification: backend `verify` (surefire, failsafe, Cucumber), frontend typecheck, unit and story tests and build, and manual tests and build. Verify that all pass.

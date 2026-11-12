# Tasks

## 1. Contract and backend

- [x] 1.1 `api/openapi.yaml`:
  - `getSolutionLandscape` (`GET /solutions/{id}/landscape`, tag `Solutions`, `entity:read`) with `q` (`maxLength` 255), `metatypeId` (uuid), `PageParam`, `PageSizeParam` and `CursorParam`;
  - responses 200, 400, 403, 404;
  - the schemas `SolutionLandscapeEntry` and `SolutionLandscapePage` (design, API).

  Verify: Redocly lint passes, the generated `SolutionsApi` compiles, and the contract authorization test passes.
- [x] 1.2 `app/solution/SolutionLandscape` with the query of D2, the cursor of D3 and `InvalidSolutionQueryException` mapped to 400 `validation-failed` naming `cursor`; `SolutionController` implements `getSolutionLandscape`.

  Verify with `SolutionLandscapeTests` (integration):
  - the baseline lists the active entities of element metatypes once, with name, metatype and version, and no data flow;
  - a deleted entity (`deleted_at` set in the test database) is not listed;
  - after a rename to version 2, the new name and version 2 are listed;
  - order by name case-insensitively, ties by ID;
  - the metatype filter, the search by part of the name (case-insensitive, `%` and `_` as plain characters), both combined, a blank search as none, and an unknown metatype as an empty page;
  - limits as a parameterized test: a search term of 255 characters answers 200 and one of 256 answers 400; page size 1 and 200 answer 200, 0 and 201 answer 400;
  - paging: 60 entities with equal names among them, loaded page by page through the cursor with page size 20, hold every entity exactly once with the total 60, and the last page has no `nextCursor`;
  - a cursor with another filter, a changed cursor and a cursor without `page` answer 400 naming `cursor`;
  - an unknown solution answers 404, and a principal without `entity:read` (`portal-reader`) answers 403.
- [x] 1.3 Performance: a page of the baseline with a search term over 10,000 entities answers within 500 ms (`SolutionLandscapeLatencyIT`, median of 5 runs after a warm-up). Verify that it passes.
- [x] 1.4 Cucumber feature `features/solution/solution-baseline.feature` with the scenarios "Entities of the landscape", "Deleted entity", "Current state", "Unknown solution", "Without entity:read", "Filter by metatype", "Search by part of the name", "Limit of the search term", "Pages by cursor", "Limits of the page size" and "Cursor of another query". Verify that they pass.

## 2. Frontend: area "Baseline"

- [x] 2.1 API client `getSolutionLandscape` in `src/api/solutions.ts`; the fake API of `src/test/solutions.ts` gets a landscape with filter, search and cursor.
- [x] 2.2 The area "Baseline" in `SolutionPage.vue` (design D5).

  Verify (`SolutionPage.spec.ts`):
  - the list shows name and metatype;
  - the metatype filter offers element metatypes only and reloads the list;
  - the search reloads after the delay;
  - the next page is loaded with the cursor, and a page jump without it;
  - a 400 naming `cursor` repeats the query without the cursor;
  - the notice of the empty landscape for an empty unfiltered baseline;
  - "No entity matches" for an empty filtered result, without that notice.
- [x] 2.3 i18n keys `solutions.baseline.*` in `en.json` and `de.json`. Verify: the i18n completeness test passes.
- [x] 2.4 Stories in `SCR-061-solution.stories.ts`: "Baseline", "Empty landscape" and "No match" with play functions; images rendered; SCR-061 in `docs/screens/SCREENS.md` names the baseline.

  Verify: `npm run test:stories` passes and `python3 scripts/check_screens.py` passes.

## 3. Manual

- [x] 3.1 `usage/solutions.md` in `manual/en/` and `manual/de/`:
  - a section on the baseline: what it lists, search, filter, and the empty landscape;
  - the walkthrough screenshot `solution` shows the baseline with entities.

  Verify: the manual build and its link check pass.

## 4. Wrap-up

- [x] 4.1 Realign and record:
  - US-039: status `done`, the realigned AC1–AC4, the Technical Notes and the performance item of the Definition of Done;
  - REQ-039: version bump, realigned Statement, Context, Processing and AC1–AC3, and the implementation note;
  - UC-05: main flow step 8, E2 and the implementation notes.
- [x] 4.2 Synchronize:
  - `docs/data-model.puml` and `business-objects/solution.md` (design, Data Model);
  - `docs/screens/SCREENS.md`;
  - the sprint plan status of S11 change 1.

  Regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [x] 4.3 Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --all --strict` and verify they pass.

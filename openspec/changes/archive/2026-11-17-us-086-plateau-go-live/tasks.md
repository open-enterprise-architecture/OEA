# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D6): `previewPlateauGoLive` and `goLivePlateau` with their schemas, the problem types `plateau-not-next` and `plateau-solution`, and `solutionId` in `DeltaConflict`.

  Verify: Redocly lint and both authorization scripts pass.

## 2. Backend

- [x] 2.1 `V38__plateau_go_live.sql` (D1) and `PlateauStore.goLiveOrder` and `transition` (D2, D3).

  Verify with a migration test:
  - the runtime role updates `status`, `valid_from` and `valid_to` of plateaus and `from_plateau_id` of solutions, and no other column of `solutions`;
  - with P0 updated first, the transition keeps one baseline.
- [x] 2.2 `SolutionGoLive.plan` with `ahead`, `resolve` for plateau solutions, and the refusal of the single go-live (D2, D4).

  Verify with `SolutionGoLiveTests`:
  - a plateau solution gives 409 `plateau-solution`, and a project solution still goes live;
  - the preview of a plateau solution stays readable;
  - a resolution records the value at its turn.
- [x] 2.3 `app/solution/PlateauGoLive` and its controller methods (D3).

  Verify with `PlateauGoLiveTests`:
  - the preview: counts, solutions, warnings `notApproved` and `openIssues`, and no warnings without them;
  - 409 `plateau-not-next` for a plateau after the next one, for the baseline and for a realized plateau;
  - the name: the exact name is accepted, while another case or a trailing space is rejected with 422;
  - two solutions in order, with soft deletion, renaming and open issues, and archived and implemented solutions untouched;
  - a conflict with the catalog and a conflict with an earlier solution give 409 `merge-required` with `solutionId`, and nothing changes; after the resolution the go-live runs;
  - the effects: the baseline from today, the old one realized until today, a sibling plateau and its solution following;
  - the rollback: a test trigger that fails the update of P0 leaves plateaus, solutions and entities unchanged;
  - the permissions: `plateau:go-live` is required (403 with only `plateau:write`), and write roles are not checked;
  - the audit: `plateau.went-live`, one `solution.went-live` per solution, and `plateau.updated` per sibling.
- [x] 2.4 `SolutionDiff` on the basis (D5).

  Verify with `SolutionDiffTests`:
  - for the basis `predecessor`, the current value is that of the predecessor, without drift;
  - a project solution reads the catalog.
- [x] 2.5 Cucumber `features/solution/plateau-go-live.feature`, with the scenarios of the delta specs.

  Verify that it passes, together with the `@solution` scenarios.
- [x] 2.6 Measure a go-live of one plateau with four solutions and 1,000 deltas over 50,000 entities.

  Verify that it completes, and record the time in the design.

## 3. Frontend

- [x] 3.1 Stories first:
  - SCR-066 `TakePlateauLive` (new), with the states "warnings", "no warnings", "wrong name" and "conflicts";
  - SCR-065 with "Go live";
  - SCR-061 with a plateau solution.

  Verify that the stories render without a11y violations.
- [x] 3.2 `api/plateaus.ts`, `PlateauGoLiveDialog.vue` and the action on `PlateausPage.vue` (D7). Texts go in `i18n` en/de.

  Verify with the specs:
  - "Go live" appears only on the next plateau and only with `plateau:go-live`;
  - the button is disabled without a name and with a wrong name (another case), and enabled with the exact name;
  - the warning section is absent without warnings;
  - the conflicts are listed per solution and open the merge dialog;
  - the page reloads after success.
- [x] 3.3 `SolutionPage.vue` for a plateau solution (D7).

  Verify with the specs: no "Go live", and the link to the plateau.
- [x] 3.4 `docs/screens/SCREENS.md`: SCR-066 `implemented`; SCR-065 and SCR-061 name the additions; the screen images.

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [x] 4.1 Update the documentation:
  - `docs/audit-event-catalog.md`;
  - `business-objects/plateau.md`, `business-objects/solution.md` and `docs/data-model.puml` (D8);
  - UC-11, and REQ-086, REQ-087 and REQ-088, realigned (AC2 of US-087 deferred, `retired` as soft deletion);
  - the user manual "Plateaus" and "Solutions" in English and German.
- [x] 4.2 Set US-086, US-088 and US-157 to done, and US-087 to in progress with AC2 deferred. Update the S14 row, and regenerate `requirements/traceability.md`.
- [x] 4.3 Run `python3 scripts/validate_links.py`, markdownlint, `openspec validate --strict` and `check_manual.py`, and verify they pass.

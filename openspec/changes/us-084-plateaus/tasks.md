# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design D7):
  - the four plateau operations with `Plateau`, `PlateauCreate`, `PlateauUpdate` and `PlateauBasis`;
  - `plateau:write` in the permission catalog and in `enterprise-architect`;
  - `fromPlateauId` and `toPlateauId` in `SolutionCreate`;
  - `lifecycle`, `changedBy` and `deltaId` in `SolutionLandscapeEntry`, with `id` nullable;
  - `SolutionDecision` with `oneOf` `solutionId` or `plateauId`, and `plateau` in the members of `solution-decision-required`;
  - the problem type `plateau-in-use`.

  Verify: Redocly lint and both authorization scripts pass.

## 2. Backend: plateaus

- [ ] 2.1 `V37__plateaus.sql` (D1) and `core/solution/PlateauStore` (D2).

  Verify with a migration test:
  - a second baseline is rejected (23505), and a baseline after the first was realized is accepted;
  - a baseline with `succeeds` or `basis`, and a target without them, are rejected (23514);
  - `succeeds = id` is rejected;
  - the runtime role updates the columns of D1 and not `status` or `valid_to`;
  - the foreign keys from `solutions` reject an unknown plateau;
  - the role `enterprise-architect` holds `plateau:write`.
- [ ] 2.2 `app/solution/Plateaus` (D2).

  Verify with `PlateausTests`:
  - a baseline, a target and a transition are created, each with its audit event;
  - "A baseline plateau already exists" for a second baseline, and the target message without a baseline;
  - the name boundaries: 255 characters are accepted and 256 rejected; a description of 2000 characters is accepted and 2001 rejected; a blank name and a taken name (other case, padded) give 422;
  - the predecessor checks: missing, realized, unknown, cycle of two and of three plateaus; a change while a solution targets the plateau gives 409, and with no solution it gives 200;
  - the basis is missing on a target (422), set on a baseline (422), and changed (200 and audited);
  - an edit of a realized plateau gives 422 "Realized plateau is read-only", and a status change by an edit gives 422 naming `status`;
  - a rejected write is neither stored nor audited;
  - the order of `list`, and 404 for an unknown ID.
- [ ] 2.3 Controller `PlateauController` and the handlers for `InvalidPlateauException` and `PlateauInUseException`.

  Verify with controller tests:
  - 201, 200, 400 (name of 256 characters), 404, 409 and 422;
  - 403 for creation without `plateau:write`;
  - reads are allowed with each of `plateau:write`, `plateau:go-live` and `solution:write`, and give 403 with only `entity:read`.

## 3. Backend: solutions, basis and metamodel

- [ ] 3.1 `Solutions.create` with plateau references (D3).

  Verify with `SolutionsTests`:
  - both references are stored, and none is stored once plateaus exist;
  - only the start or only the target gives the BR-01 message;
  - the target is the baseline, realized or unknown (422 naming `toPlateauId`);
  - the start is not the predecessor (422 naming `fromPlateauId`);
  - nothing is stored or audited on rejection.
- [ ] 3.2 `core/solution/PlateauContentStore` and the layers (D4).

  Verify with `PlateauContentTests`:
  - no layers for the basis `catalog`, for a predecessor that is the baseline, and for a project solution;
  - two layers for a chain with the basis `predecessor`;
  - `modified` applies name, description and a property;
  - `retiring` marks the entity and names the solution, and a later `modified` keeps it retiring;
  - `new` gives a proposed row;
  - `implemented` and `archived` solutions are not applied;
  - for the same field, the later delta wins, and the later layer beats the earlier one.
- [ ] 3.3 `SolutionLandscape` on the content (D4) and before values in `SolutionDeltas` (D5).

  Verify with `SolutionLandscapeTests` and `SolutionDeltasTests`:
  - search finds the overlaid name and not the catalog name;
  - the metatype filter includes proposed rows;
  - paging by offset and by cursor runs across proposed rows and catalog rows without gaps or duplicates (page size 1 and 2);
  - `entry` returns the overlaid values;
  - before values come from the predecessor for a solution with layers, and from the catalog for one without;
  - a retiring entity still gets a delta.
- [ ] 3.4 `SolutionImpact` with decisions by plateau (D6).

  Verify with `SolutionImpactTests`:
  - two solutions of one plateau need one decision, and `keep` stores a snapshot for both;
  - a solution without a plateau still needs its own decision (409);
  - a decision by solution for a solution of a plateau gives 422, and so does a plateau without affected solutions;
  - the 409 lists the plateau of each solution;
  - the enumeration value and base type paths behave the same.
- [ ] 3.5 Cucumber `features/solution/plateaus.feature` and the plateau scenarios of the solution and metamodel features, with the scenarios of the delta specs.

  Verify that they pass, together with the `@solution` and `@metamodel` scenarios.
- [ ] 3.6 Measure the baseline with 50,000 entities and 1,000 deltas in two layers (D4).

  Verify that p95 stays below 500 ms, and record the result in the design.

## 4. Frontend

- [ ] 4.1 Stories first:
  - SCR-065 `Plateaus` (new), with the states "grouped", "no target", "no baseline" and "read only";
  - SCR-060 with the plateau field;
  - SCR-061 with plateaus in the header and retiring and proposed entries;
  - SCR-030 with the question grouped by plateau.

  Verify that the stories render without a11y violations.
- [ ] 4.2 `api/plateaus.ts`, `PlateausPage.vue`, `PlateauDialog.vue`, the route and the navigation entry (D8). Texts go in `i18n` en/de.

  Verify with the specs:
  - the sections and the highlighted baseline;
  - the empty texts of US-085 AC2 and UC-11 A1;
  - "New plateau" and "Edit" appear only with `plateau:write`, and there is no "Edit" for a realized plateau;
  - the dialog preselects the baseline, disables the predecessor while solutions target the plateau, and shows a 422 at its field.
- [ ] 4.3 `SolutionCreateDialog.vue`, `SolutionPage.vue` and `SolutionBaseline.vue` (D8).

  Verify with the specs:
  - there is no plateau field without plateaus;
  - choosing a target shows the start and sends both references;
  - the header names the plateaus;
  - the badges "Retiring by" and "Proposed by" appear, and a proposed row offers no action.
- [ ] 4.4 `SolutionDecisions.vue` grouped by plateau (D6).

  Verify with the specs:
  - one choice per plateau, naming its solutions;
  - "Confirm" waits for each plateau and each solution without a plateau;
  - the request sends `plateauId`.
- [ ] 4.5 `docs/screens/SCREENS.md`: SCR-065 `implemented`; SCR-060, SCR-061 and SCR-030 name the additions; the screen images.

  Verify with `python3 scripts/check_screens.py`.

## 5. Documentation and wrap-up

- [ ] 5.1 Update the documentation:
  - `docs/audit-event-catalog.md` (`plateau.created`, `plateau.updated`, decisions by plateau);
  - `docs/data-model.puml`, `business-objects/plateau.md` and `business-objects/solution.md` (D9);
  - UC-11 (implicit plateau mode, basis, permission `plateau:write`);
  - REQ-084, REQ-085, REQ-089 and REQ-038, realigned;
  - the user manual: a new page "Plateaus", and "Solutions" and "Metamodel", in English and German.
- [ ] 5.2 Set US-084, US-085, US-089, US-155 and US-156 to done, and US-038 AC3 and AC4 to met, with the implementation note. Update the S14 row, and regenerate `requirements/traceability.md`.
- [ ] 5.3 Run `python3 scripts/validate_links.py`, markdownlint, `openspec validate --strict` and `check_manual.py`, and verify they pass.

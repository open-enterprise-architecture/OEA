# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design D5): the three operations, `GET /solutions/{id}/open-issues`, the schemas, `openIssueCount`, and the problem types `merge-required` and `solution-completed`.

  Verify: Redocly lint and both authorization scripts pass.

## 2. Backend: schema and stores

- [ ] 2.1 `V35__solution_go_live.sql` (design D1) and `EntityStore.softDelete`.

  Verify with a migration test and `EntityStoreTests`:
  - a delta `new` may carry its entity, and `modified` without an entity is rejected;
  - the runtime role updates `solutions.status` and `entity_deltas.entity_id` but no other column;
  - `softDelete` sets the three columns and keeps the history.

## 3. Backend: go-live

- [ ] 3.1 Field conflicts and preview (D2).

  Verify with `SolutionGoLiveTests`:
  - a changed delta field is a conflict, and a changed other field is not;
  - the counts include the connections of a retired entity;
  - redacted values stay redacted.
- [ ] 3.2 Resolution (D3).

  Verify:
  - `current`, `delta` and `value` behave as specified;
  - a field without a conflict gives 422 naming it, and so does an invalid own value;
  - nothing changes on a rejection.
- [ ] 3.3 Go-live (D4).

  Verify:
  - every delta type and the connections behave as specified;
  - an unresolved conflict gives 409 `merge-required`, and the second go-live gives 409, without a write;
  - without `solution:write`, 403;
  - open issues "mandatory missing" and "invalid value" are recorded;
  - status boundaries: `draft` and `in-progress` are accepted, `implemented` and `archived` rejected;
  - the audit events are written;
  - a failing writer rolls everything back.
- [ ] 3.4 Cucumber `features/solution/solution-go-live.feature` with the scenarios of the spec.

  Verify that it passes.

## 4. Frontend

- [ ] 4.1 Story first: SCR-061 `GoLive` and `Implemented`, and the new SCR-063 `Conflicts`, with a fake backend.

  Verify that the stories render without a11y violations.
- [ ] 4.2 `api/solutions.ts`, `DeltaMergeDialog.vue` and `SolutionPage.vue` (D6). Texts go in `i18n` en/de.

  Verify with the specs:
  - "Go live" appears only with `solution:write` and a status that is not completed;
  - conflicts open the merge dialog first;
  - each choice sends its resolution;
  - an own value needs input;
  - the confirmation shows the counts and runs the go-live;
  - afterwards, the page shows `implemented` and the open issues.
- [ ] 4.3 `docs/screens/SCREENS.md`: SCR-063 `implemented`; SCR-061 names the go-live.

  Verify with `python3 scripts/check_screens.py`.

## 5. Documentation and wrap-up

- [ ] 5.1 UC-05 (go-live in project mode), `docs/audit-event-catalog.md` (`solution.went-live`, `entity.deleted`), `docs/data-model.puml` and `business-objects/solution.md`, and the user manual "Solutions" in English and German.
- [ ] 5.2 Set US-152 to done with the implementation note; update the S14 row; regenerate `requirements/traceability.md`.
- [ ] 5.3 Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --strict`, and verify they pass.

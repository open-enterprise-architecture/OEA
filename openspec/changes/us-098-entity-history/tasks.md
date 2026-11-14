# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design D4):
  - `getEntityHistory` without `x-oea-status: planned`, with the parameters `from`, `to`, `actorId` and `CursorParam`;
  - `EntityHistoryResponse` with `created`, `actors` and `nextCursor`; `EntityVersionSummary.changes` with the new schema `EntityVersionChange`, and `changedBy` required;
  - the schemas `HistoryVisibility`, `HistoryVisibilityEntry` and `HistoryVisibilityUpdate` (1 to 500 entries);
  - the operations `getHistoryVisibility` and `updateHistoryVisibility` (`/admin/history-visibility`, `visibility:configure`).

  Verify: Redocly lint passes, the generated API compiles, `check_openapi_authorization.py` and the contract authorization tests pass, and `getEntityHistory` no longer answers 501.

## 2. Migration

- [ ] 2.1 Flyway migration `V29__history_visibility.sql` (D3, Data Model): the table `history_visibility_settings` with PK `(role_id, metatype_id)`, `ON DELETE CASCADE` to `authorization_roles`, the FK to `metatypes`, `CHECK (mode = 'hide')`, and select, insert and delete for the runtime role.

  Verify with the migration tests:
  - `hide` is stored;
  - a second row for the same pair and the mode `show-restricted` are rejected by the database;
  - deleting a custom role removes its rows.

## 3. Backend: history read

- [ ] 3.1 `EntityHistoryStore` in `core/entity` (D1): versions with filters and keyset paging, changed fields per version, actors, creation and count.

  Verify with `EntityHistoryStoreTests`:
  - positive: 5 versions are returned 5 to 1; version 3 with a changed name and `lifecycleStatus` gives both; version 1 gives no fields;
  - filters: `actorId` returns only that person's versions, and an actor without versions gives an empty list; the actors are independent of the filters;
  - period boundaries: `from` and `to` equal to the times of versions 2 and 4 give 2 to 4, and ±1 ms inside gives only 3;
  - paging boundaries: 50 versions with page size 50 give one page; 51 give 51 to 2 and then 1.
- [ ] 3.2 `EntityHistory` in `app/history` (D2): the timeline with the visibility rule and labels in the reader's language.

  Verify with `EntityHistoryTests`:
  - positive: a readable property is not restricted, and system fields are never restricted;
  - restricted: a `role-restricted` property without a read role is restricted; with `hide` for all roles of the reader it is left out; a version with only hidden fields stays without fields;
  - connection-scoped: inside the scope the field is readable, outside it is restricted;
  - override role: the field is readable;
  - errors: an unknown entity gives 404, and `from` after `to` gives 400 naming `from`.
- [ ] 3.3 `EntityHistoryController` in `api` (`getEntityHistory`). Verify the page-size boundaries: 1 and 200 are accepted, 0 and 201 are rejected with 400, and an invalid cursor gives 400 `cursor`.
- [ ] 3.4 Cucumber `features/repository/entity-history.feature`, with the scenarios of the spec `repository/entity-history`:
  - "Timeline with versions", "Changed fields of a version", "Entity without changes", "Unknown entity", "Without permission";
  - "Filter by actor", "Actors with a filter", "Period boundaries", "Just outside the period", "Reversed period";
  - "Exactly one page", "One version more", "Page size limits";
  - "Restricted field shown as restricted", "Restricted field hidden", "Readable for a read role", "Version with only hidden changes", "Connection-scoped outside the scope", "Override role".

  Verify that the feature passes.

## 4. Backend: history visibility

- [ ] 4.1 `HistoryVisibility` in `app/metamodel` (D3): `mode(roles, metatypeId)` and the change with validation, atomicity and audit event `visibility.history-changed`, including the self-benefiting flag.

  Verify with `HistoryVisibilityTests`:
  - the more-open rule: no role, one of two and both of two set to `hide`, parameterized;
  - no inheritance to a subtype;
  - `show-restricted` removes the row;
  - an unchanged entry writes no event;
  - a duplicate pair is invalid.
- [ ] 4.2 `HistoryVisibilityController` in `api` (`getHistoryVisibility`, `updateHistoryVisibility`). Verify:
  - an unknown role, metatype or mode gives 422 with `invalidFields` and stores nothing;
  - the entry limits: 1 and 500 entries are accepted, 0 and 501 are rejected with 400;
  - a metamodel administrator without `visibility:configure` gets 403;
- [ ] 4.3 Cucumber `features/metamodel/history-visibility.feature`, with the scenarios of the spec `metamodel/history-visibility`:
  - "Nothing configured", "More open setting wins", "All roles hide", "No inheritance", "Re-import keeps the settings";
  - "Hide for a role", "Back to the default", "Unknown role among valid entries", "Entry limits", "Metamodel administrator";
  - "Change audited", "Unchanged entry", "Self-benefiting change".

  Verify that the feature passes.
- [ ] 4.4 `docs/audit-event-catalog.md`: the event `visibility.history-changed` with its details. Verify that the audit event catalog check passes.

## 5. Frontend: SCR-026 "Change history"

- [ ] 5.1 Story `frontend/src/screens/SCR-026-change-history.stories.ts` (D5), with the states normal, filtered by person, restricted field, empty history and many versions with "Load more". Update `docs/screens/SCREENS.md`: SCR-026 becomes `prototype`, the legacy SVG links are replaced by `SCR-026-light.png` and `SCR-026-dark.png`, and the legacy SVGs are deleted. Verify that `python3 scripts/check_screens.py` passes.
- [ ] 5.2 API module `frontend/src/api/history.ts` and the page `EntityHistoryPage.vue` at `/entities/:id/history`, with the back link to the catalog it was opened from. English and German texts.

  Verify with unit tests:
  - versions newest first, and version 1 as "Created";
  - the badge "Restricted" without a value;
  - the person filter, and a period filter that sends `from` and `to`;
  - the empty state "No changes since creation. Created on {date} by {person}.";
  - "Load more" with 51 versions, and none with 50;
  - the back link returns to the catalog it was opened from;
  - a 404 shows "Entity not found".
- [ ] 5.3 Row action "History" in `CatalogTable.vue` for the catalog (SCR-020) and the portal catalog. Verify with unit tests that each entity row has the action, that it opens `/entities/{id}/history` with the catalog as back link, and that it carries an icon and text.
- [ ] 5.4 Set SCR-026 to `implemented` in `docs/screens/SCREENS.md`. Verify that `check_screens.py`, the type check, the frontend tests and the build pass.

## 6. Frontend: section on SCR-032

- [ ] 6.1 Section "Change history" on `PropertyVisibilityPage.vue` (D5): the metatype selection, one row per role with "Restricted" or "Hidden", a combined save, `invalidFields` per row, and the explanation of the rules; English and German texts. Update the SCR-032 story with the section.

  Verify with unit tests:
  - a metatype without entries shows every role as "Restricted";
  - setting `viewer` to "Hidden" sends one `PUT` with only that entry;
  - a 422 shows the error at its row;
  - without changes, saving is disabled.

## 7. User manual

- [ ] 7.1 Manual pages in English and German:
  - "Change history of an entity": how to open it from the catalog, what the entries, filters and "Restricted" mean, and that values follow;
  - the section "Change history" on the page "Property visibility", with the rule for several roles.

  Verify that the manual tests, `check_manual.py` and the build pass.

## 8. Wrap-up

- [ ] 8.1 Realign US-098 (AC3: "the page shows") and REQ-098:
  - the statement drops the change reason, which is noted as a later extension;
  - the implementation notes name the cursor paging and the history visibility per role and metatype;
  - add history rows to both.

  Set US-098 to `done` with the implementation hash after the merge.
- [ ] 8.2 Synchronize `docs/data-model.puml` (`HistoryVisibilitySetting`) and `business-objects/authorization-role.md`. Regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify that `--check` passes.
- [ ] 8.3 Update the S12 status in `docs/sprint-plan.md`. Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --strict`, and verify that they pass.

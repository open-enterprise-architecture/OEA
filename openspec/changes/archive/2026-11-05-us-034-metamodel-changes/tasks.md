# Tasks

## 1. Contract

- [x] 1.1 Update `api/openapi.yaml` (design D1, D5):
  - add `listMetamodelChanges` (`GET /metamodel/changes`) with the parameters `kind`, `actorRef`, `from`, `to`, `batchId`, `page` and `pageSize`, and the schemas `MetamodelChangeKind`, `MetamodelChangeActor`, `MetamodelChange` and `MetamodelChangePage`;
  - extend the description of `metamodel:write` in `x-oea-permissions`.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.

## 2. Persistence

- [x] 2.1 Audit migration `V4__metamodel_events_index.sql` with the partial index of design D3. Verify with a schema test that the index exists and that the runtime role still cannot update or delete audit events.

## 3. Backend

- [x] 3.1 A reader `MetamodelChanges` in `io.oea.app.metamodel` (D1, D2): the predicate of the index, the kinds, the filters, paging newest first, actor and element names, and the actors for the filter. Add the controller method. Verify with integration tests:
  - names of two administrators and of a system admin account;
  - each kind;
  - a person filter;
  - a period;
  - an import batch of 5 metatypes;
  - 403 for `viewer`, 400 for invalid parameters;
  - no events of other families.
- [x] 3.2 Atomicity (D4): a test that lets the audit insert fail for a metatype creation and checks that nothing is created.
- [x] 3.3 Cucumber `features/metamodel/change-history.feature` with the scenarios of the spec (US-034 AC1, AC2, AC3). The steps use the shared instance of the metamodel editing features. Verify with the `CucumberIT` run.

## 4. Web portal

- [x] 4.1 SCR-030 section "Changes" (D6): the API function and types; the section with table, filters, batch chip and "Load more"; the tab only with `metamodel:write`; messages per event type in English and German. Verify with specs for "Who changed what", "Readers see no changes", the batch filter and paging, and with the bundle key parity. Add the story "Changes", run `npm run test:stories`, and update `docs/screens/SCREENS.md`.

## 5. Manual and documentation

- [x] 5.1 Manual in English and German: `administration/metamodel-editing.md` with a section "Tracing changes" and the operation in the scripting section. Verify with `python3 scripts/check_manual.py` and the manual build.
- [x] 5.2 ADR-032 amendment (D5); `business-objects/person.md` and `business-objects/audit-event.md` (who sees resolved names); `docs/audit-event-catalog.md` (readers of the metamodel events); `docs/data-model.puml` (the index).
- [x] 5.3 Realign US-034 (AC3, technical notes, status) and REQ-034 (statement, AC1–AC4, implementation); update the sprint plan. Regenerate the traceability matrix. Verify with `validate_links.py` and `generate_traceability.py --check`.

## 6. Verification

- [x] 6.1 Run `./mvnw verify`, the frontend type-check, tests, build and Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.

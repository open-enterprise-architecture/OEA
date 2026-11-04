# Tasks

## 1. Contract

- [x] 1.1 Update `api/openapi.yaml` as described in design – API:
  - implement `getI18nBundle` (`If-None-Match`, `ETag` on 200 and 304, 400 instead of 404, `429`, no `501`, content-hash description, UUID keys in the `I18nBundle` example);
  - add `getMetamodelLabels` and `updateMetamodelLabels` with `MetamodelLabel` and `MetamodelLabelsUpdate`;
  - add `updateMyPreferences` with `PersonPreferences`;
  - add `Me.uiLanguage`, `ImportResult.labelCount` and the key rule of `MetamodelPackage.i18nBundles`.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.
- [x] 1.2 Regenerate the frontend API types (`npm run generate:api`) and the manual API reference. Verify with `npm run type-check` in `frontend/` and `npm run build` in `manual/`.

## 2. Persistence

- [x] 2.1 Flyway `V17__i18n_entries.sql`:
  - the table `i18n_entries` with `CHECK`s on `entity_type`, `locale`, `field` and `scope`, `UNIQUE (entity_type, entity_id, locale, field, scope)`, an index on `locale` and grants;
  - `persons.ui_language` nullable, without a default, existing rows set to `NULL`, with a `CHECK`.

  Verify with a schema test for constraints, uniqueness and the migrated `NULL` values.

## 3. Labels and bundle (backend)

- [x] 3.1 `io.oea.app.metamodel.MetamodelLabels`:
  - `bundle(locale)` as one query with the precedence organization → package → name;
  - `list(locale)` for administrators;
  - `update(locale, labels, principal)` with key validation (422 `invalidFields`), trimming, the 200-character limit, removal by empty text or `null`, and the audit event `metamodel.labels-changed`.

  Verify with integration tests for precedence, fallback, removal, unknown keys and the audit event.
- [x] 3.2 Delete the label rows when a metatype, category, property definition or enum value is deleted (`MetatypeEditing`, `PropertyDefinitions`). Verify with one integration test per element type.
- [x] 3.3 `io.oea.api.i18n.I18nController` (`getI18nBundle`):
  - weak content-hash ETag;
  - `Cache-Control: max-age=300, stale-while-revalidate=3600`;
  - 304 without a body when `If-None-Match` matches.

  Extend `MetamodelController` with `getMetamodelLabels` and `updateMetamodelLabels`. Verify with MockMvc tests for 200/304/400/401/403/422.
- [x] 3.4 Cucumber `features/metamodel/labels.feature` for REQ-152 AC1 (304 on an unchanged state) and AC2 (200 with a new ETag and the new label after a change), and for US-140 AC1 on the API side (German bundle after the starter import). Verify that the scenarios pass with the `CucumberIT` run.

## 4. Package labels

- [x] 4.1 Accept `i18nBundles` in `MetamodelPackages`:
  - check that each key belongs to the package and each locale is supported (422 with the key);
  - in `PackageImporter`, replace the `imported` rows of the package's elements and keep the organization rows;
  - add `labelCount` to the result and to `metamodel.package-imported`.

  Verify with integration tests for import, re-import with an organization label, a foreign key and an unsupported locale.
- [x] 4.2 `packages/oea-starter-togaf-classic.json` 1.1.0 with `i18nBundles.de` for all metatypes, the category, `Status` and its values. Adjust the starter-package tests and the Cucumber steps to 1.1.0. Verify with `StarterPackageSteps` and the import test.

## 5. Language of the person (backend)

- [x] 5.1 `GET /me` returns `uiLanguage`. `updateMyPreferences` sets or clears it for the caller (204), rejects service accounts with 403 and an unsupported value with 400. Verify with MockMvc tests and a Cucumber scenario in `features/identity/preferences.feature` (US-140 AC1: the person sets `de` and `GET /me` returns it).

## 6. Web portal

- [x] 6.1 Add `vue-i18n` (exact version, MIT) and regenerate `THIRD-PARTY-LICENSES.md` (`npm run build`, `python3 scripts/generate_third_party_licenses.py`). Add `src/i18n/index.ts` with `en.json` and `de.json`, fallback `en`, and the effective language (D6). Install it in `main.ts` and in `.storybook/preview.ts` with `en`. Verify with a unit test for the order `uiLanguage` → browser → `en` and the fallback for `fr`.
- [x] 6.2 Convert the static texts of every page and component (`pages/`, `components/`, `App.vue`, `AppHeader.vue`) to message keys with German translations. Verify that all existing Vitest specs pass unchanged with the English instance, and add a spec that renders the login page in German.
- [x] 6.3 `src/i18n/labels.ts`:
  - load the bundle of the effective language after login, with `If-None-Match`;
  - on 304, keep the map and ETag;
  - refresh every five minutes, and stop on logout and when the language changes;
  - `label(key, fallback)`.

  Use it in `CatalogPage`/`CatalogTable` for metatype names and enum values. Verify with unit tests: the store requests the right locale, on 304 the shown labels stay, a new ETag replaces the map, and the interval is set to five minutes (US-140 AC2).
- [x] 6.4 Language select on the profile page (SCR-005) with "Browser language", `de`, `en`, `fr` and `it`. It calls `updateMyPreferences`, and the new language applies at once. Update `SCR-005-profile.stories.ts` with the select and `SCR-020-catalog-browser.stories.ts` with translated labels. Verify with a Vitest spec of the profile page and the Storybook test run.

## 7. Manual and walkthrough

- [x] 7.1 Manual (EN and DE):
  - the profile page gets the language setting;
  - `administration/metamodel-editing.md` gets "Translating labels" (API until SCR-030, organization over package, five-minute refresh);
  - the starter package page names version 1.1.0 with German labels.

  Verify with `python3 scripts/check_manual.py` and the manual build.
- [ ] 7.2 Set `OEA_WALKTHROUGH_LANGUAGES: "en de"` in `.github/workflows/pages.yml`, and switch any walkthrough step that matches on visible text to test IDs. Verify with `scripts/walkthrough.sh` locally with `OEA_WALKTHROUGH_LANGUAGES="en de"`, or in the `pages` run of the PR branch.

## 8. Wrap-up

- [x] 8.1 Audit event catalog: `metamodel.labels-changed`, and `labelCount` in `metamodel.package-imported`. ADR-025: a dated refinement note on the content-hash ETag and the UUID keys. Verify with link validation.
- [x] 8.2 Update US-140 (status `done` with AC3 noted as open until the event stream, sprint S7, notes) and REQ-152 (implementation note, AC3 open). Record the implementation hash in the archive PR.
- [x] 8.3 Synchronize `docs/data-model.puml`, `business-objects/person.md` and `business-objects/metamodel-configuration.md` (new BR, version bump), `docs/screens/SCREENS.md` (SCR-005, SCR-020 unchanged in status) and the sprint plan status. Regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [x] 8.4 Run `python3 scripts/validate_links.py`, `openspec validate --specs --strict`, markdownlint, `./mvnw verify`, and the frontend type-check, Vitest and build. Verify that all pass.

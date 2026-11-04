# Tasks

## 1. Contract

- [ ] 1.1 Update `api/openapi.yaml` as in design D2:
  - a read-only `version` in `MetatypeDetail`, `PropertyDefinitionDetail` and `PropertyCategory`;
  - an `ETag` on `getMetatype` and on the single-element write responses;
  - a reusable optional `If-Match` parameter and a `PreconditionFailed` response (412, `stale-version`, `currentVersion`) on the eleven writes named in the proposal;
  - `stale-version` in the problem type list.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.
- [ ] 1.2 Regenerate the frontend API types and the manual API reference. Verify with `npm run type-check` in `frontend/` and `npm run build` in `manual/`.

## 2. Versions and conditional writes (backend)

- [ ] 2.1 Flyway `V18__metamodel_versions.sql`: add `version` to `metatypes`, `property_definitions` and `property_categories`, with `DEFAULT 1` and a `CHECK`. Verify with a schema test.
- [ ] 2.2 Increment the versions as in D1:
  - in `MetatypeEditing`, `PropertyDefinitions`, `PropertyMappings`, `MappingEffectiveDates` and `ReimportPolicies`, including the owner bump for mappings and enum values;
  - in `PackageImporter` for updated elements.

  Return `version` in the read models. Verify with integration tests for each write path and for the re-import.
- [ ] 2.3 Check `If-Match` as in D2: read the row `FOR UPDATE`, check in the order 404 → 412 → element rules, and throw a stale-version exception that `ApiExceptionHandler` maps to 412 with `currentVersion`. Set `ETag` in `MetamodelController`. Verify with MockMvc tests for a match, a mismatch, a missing header, `*` and a malformed header on each of the eleven operations.
- [ ] 2.4 Cucumber `features/metamodel/optimistic-locking.feature` with the scenarios of the spec `metamodel/optimistic-locking`. Verify with the `CucumberIT` run.

## 3. Web portal

- [ ] 3.1 Add API functions in `src/api/metamodel.ts` for every metamodel read and write, with `If-Match`, CSRF and the result type `{ ok, value | rejection }`. Add the store `src/metamodel/store.ts` (D4). Verify with Vitest specs for headers, 412 and reloading after a write.
- [ ] 3.2 Add the routes `/metamodel`, `/metamodel/:section` and `/metamodel/metatypes/:id`, and the header entry for `entity:read` or `metamodel:write`. Verify with specs for the header entry with and without permission and the "access missing" state.
- [ ] 3.3 Build the `MetamodelPage.vue` metatypes section (D3): list with search, scope badges and source package, the detail with base type, own and inherited mappings and dated entries, and the narrow layout. Verify with specs for a reader (no actions), a built-in metatype (read-only) and inherited and future entries.
- [ ] 3.4 Add `MetatypeDialog` (create, edit, delete with base type) and `MappingDialog` (property, necessity, valid-from, default value, a new entry on a package mapping, removal with a warning, marked entities). Verify with specs for creating `SecurityZone` with `trustLevel`, `name-taken`, `in-use` with count, and 3 marked entities.
- [ ] 3.5 Add the sections properties (with `PropertyDialog` and enum values) and categories (with `CategoryDialog`). Verify with specs for an enum property with three values and a property in use.
- [ ] 3.6 Add the translations section (D5) with saving only changed keys, removal by clearing and reloading the label store. Add the settings section and the per-metatype re-import setting. Verify with specs for the German name of `SecurityZone` in the list and for the effective `remove`.
- [ ] 3.7 Add the collision handling of D4 in every dialog: the warning with the current state, "Overwrite" with the new version and "Discard". Verify with a spec in which a 412 is followed by overwriting.
- [ ] 3.8 Add every new text to `en.json` and `de.json`. Verify with a new spec `src/i18n/bundles.spec.ts` that both bundles have the same keys.

## 4. Screen

- [ ] 4.1 Add `frontend/src/screens/SCR-030-metamodel-configuration.stories.ts` with the administrator detail as the first story, plus reader, collision and narrow variants (D6). Update `docs/screens/SCREENS.md`: `CA+WP`, `implemented`, generated images, legacy SVGs deleted. Verify with `npm run test:stories` and `python3 scripts/check_screens.py`.

## 5. Manual

- [ ] 5.1 Rewrite `manual/administration/metamodel-editing.md` and `manual/de/administration/metamodel-editing.md` for the screen, with the scripting section and the `If-Match` recommendation, and set `covers.screens: [SCR-030]`. Extend the walkthrough with the metamodel steps in English and German. Verify with `python3 scripts/check_manual.py`, the manual build and the walkthrough run.

## 6. Documentation

- [ ] 6.1 Add the version attributes to `docs/data-model.puml` and `business-objects/metamodel-configuration.md`.
- [ ] 6.2 Update US-032 and REQ-032: implementation note, status `done` and `realized` without relations. Refine UC-04 A5 to optimistic locking with a warning. Update the sprint plan. Regenerate the traceability matrix. Verify with `validate_links.py` and `generate_traceability.py --check`.

## 7. Verification

- [ ] 7.1 Run `./mvnw verify`, the frontend type-check, tests and build, the Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.

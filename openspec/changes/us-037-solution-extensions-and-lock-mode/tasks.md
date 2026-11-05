# Tasks

## 1. Contract

- [x] 1.1 Update `api/openapi.yaml` (design D1, D2, D4, D5):
  - add the four configuration operations with `MetamodelConfiguration`, `MetamodelConfigurationCreate` and `MetamodelConfigurationUpdate`, `If-Match` and 412 on `PUT`;
  - add `MetatypeDetail.configId`;
  - change `x-oea-authorization` of the metatype and mapping writes to `anyOfPermissions` with the condition;
  - add 403 `metamodel-locked` on all content writes;
  - add `solution-metamodel:write` to the catalog and the roles `enterprise-architect` and `metamodel-admin`.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.
- [x] 1.2 Regenerate the frontend API types and the manual API reference.

## 2. Persistence and permission

- [x] 2.1 Flyway `V20__metamodel_configurations.sql`: add `name` and `version` to `metamodel_configurations`, with a check that `solution_id` is set exactly for extensions. Insert `solution-metamodel:write` and its role grants. Verify with a schema test and with the existing tests of the built-in roles.

## 3. Backend

- [x] 3.1 `MetamodelConfigurations` (D1, D2):
  - list, create, update with version and audit (`metamodel.configuration-*` and `metamodel.edit-mode-changed`), and delete with 409 while metatypes exist;
  - `requireWritable` with the lock before the permission;
  - `MetamodelChangeException` with the reason `LOCKED`, mapped to 403 `metamodel-locked`.

  Add a controller for the four operations. Verify with integration tests.
- [x] 3.2 Call `requireWritable` in every content write of `MetatypeEditing`, `PropertyDefinitions`, `PropertyMappings` and `MetamodelLabels`; leave out the re-import settings and the package import. Mark the metatype and mapping operations and `updateMetamodelConfiguration` with `@ConditionsEnforcedByHandler` and an `enterprise-architect` probe on the instance (`probePath` for path IDs). Verify with tests: every content operation is rejected in `import-only`, while the import, the re-import settings and the edit mode stay possible.
- [x] 3.3 Names and base types in extensions (D3) in `MetatypeEditing`, the connection constraints included. Verify with tests for the name rules in both directions, base types across the boundary and a fixed `configId`.
- [x] 3.4 Effective metamodel (D4) in `MetatypeCatalog` and `MetamodelController` (`configId`, `MetatypeDetail.configId`), and `MetatypeRules` with only instance metatypes. Verify with tests for the union, the instance list without extension metatypes and `METATYPE_NOT_FOUND` in the batch.
- [x] 3.5 Cucumber `features/metamodel/solution-extensions.feature` and `features/metamodel/lock-mode.feature` with the scenarios of the specs (US-037 AC1, AC3, AC4; US-035 AC1, AC2). The steps use the shared instance of the metamodel editing features. Verify with the `CucumberIT` run.

## 4. Web portal

- [x] 4.1 SCR-030 (D6): load the instance configuration in the store; add the lock switch in the settings with `If-Match`; show a notice in every section while locked and hide the content actions; add the messages in English and German. Verify with specs for "Locked metamodel" and "Unlocking", the collision on the switch and the bundle key parity. Add the story "Locked" and run `npm run test:stories`.

## 5. Manual and documentation

- [x] 5.1 Manual in English and German:
  - `administration/metamodel-editing.md`: a lock mode section, and extensions and configurations in the scripting section;
  - `administration/roles-and-permissions.md`: `solution-metamodel:write`.

  Verify with `python3 scripts/check_manual.py` and the manual build.
- [x] 5.2 ADR-032 amendment (D5). `docs/data-model.puml` and `business-objects/metamodel-configuration.md` (name, version, enforced edit mode, BR-06/BR-09/BR-10 as implemented). Update the audit event catalog.
- [x] 5.3 US-035, US-037, REQ-035, REQ-037 (status and implementation) and the sprint plan. Regenerate the traceability matrix. Verify with `validate_links.py` and `generate_traceability.py --check`.

## 6. Verification

- [x] 6.1 Run `./mvnw verify`, the frontend type-check, tests, build and Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.

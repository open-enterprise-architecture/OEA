# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D5): notation, viewpoint type, source package, mappings and effective mappings; `ViewpointMapping`, `NotationElement` and `listNotationElements`; `PackageViewpoint` extended.

  Security (D8): `ViewpointWrite` without `viewpointType` and `sourcePackage`; `mappings` `maxItems: 1000`, `notationElement` `maxLength: 100`, sizes 1–2000, `visualHint` pattern; `PackageViewpoint` with a required `id` and the same limits; `ImportResult` with `viewpointsCreated`, `viewpointsUpdated` and `viewpointsDeleted`; the problem type `read-only-viewpoint` (422) on update and delete; `listNotationElements` with `anyOfPermissions` `entity:read`, `viewpoint:manage` and the responses 401, 403 and 429; `metamodel.package-imported` details in the description of the import.

  Verify: Redocly lint and both authorization scripts pass.

## 2. Backend

- [x] 2.1 `V41__viewpoint_notations.sql` (D1).

  Verify with a migration test:
  - existing viewpoints become `generic` and `user-defined`;
  - the notation check;
  - system-defined exactly with a package;
  - the size boundaries: 1 and 2000 are accepted, and 0 and 2001 rejected;
  - the visual hint pattern;
  - the cascade on a deleted metatype.
- [x] 2.2 The element catalog and `NotationCatalog` (D2).

  Verify with `NotationCatalogTests`:
  - every ArchiMate 3.2 element and relationship is present;
  - the counts per notation;
  - unique keys, each of at most 100 characters (the contract limit of `notationElement`);
  - `fits` for the own prefix and `generic:`, and not for a foreign prefix.
- [x] 2.3 `Viewpoints` and `ViewpointStore` with notation, mappings and read-only protection (D3).

  Verify with `ViewpointsTests`:
  - a notation is created, and a missing one becomes `generic`;
  - a mapping is accepted, while a foreign element, an unknown element, a relationship for an entity type and a type that is not allowed give 422;
  - the sizes 1 and 2000 are accepted, and 0 and 2001 give 400 (controller test); the hint `#1a2B3c` is accepted, and `red` and `#1a2b3` give 400;
  - a notation change with old elements gives 422;
  - the effective fallback;
  - a system-defined viewpoint cannot be updated or deleted (422, problem type `read-only-viewpoint`);
  - the catalog can be read with `entity:read`, and reading without it gives 403;
  - routing: `GET /api/v1/admin/viewpoints/notations` reaches `listNotationElements` (200 with the catalog, not 400 or 404 from `getViewpoint`), with the rule of `listNotationElements`.
- [x] 2.4 Package import of viewpoints, and the starter package 1.2.0 (D4).

  Verify with `PackageImportTests`:
  - the starter package installs two system-defined viewpoints;
  - a re-import updates and removes the viewpoints of the package;
  - another package with the same name gives 409, and an organization viewpoint with the name gives 409;
  - a re-import with the same viewpoint ID and a new name renames the viewpoint and keeps its ID;
  - an unknown metatype gives 422;
  - shape assignments still give 422;
  - the import result counts the viewpoints.
- [x] 2.5 Security of viewpoints and package viewpoints (D8).

  Verify with `ViewpointsTests` and `PackageImportTests`:
  - mappings: 1000 (a viewpoint with 500 entity and 500 connection types) accepted and 1001 give 400; a key of 100 characters reaches the catalog check (422 as unknown) and 101 give 400; the same metatype mapped twice gives 422 naming `mappings`, not 500;
  - a create with `viewpointType` `system-defined` and a `sourcePackage` in the body stores a user-defined viewpoint without a package;
  - update and delete of a system-defined viewpoint give 422 also with the current version, and write no audit event;
  - `listNotationElements` with only `viewpoint:manage` gives 200, and without both permissions 403;
  - package limits (parameterized): name 255 imported and 256 rejected; description 1000 and 1001; 500 and 501 metatype IDs and connection type IDs; 1000 and 1001 mappings; 10000 and 10001 viewpoints; each rejection gives 422 naming `/viewpoints/<i>` and stores nothing;
  - two package viewpoints whose names differ only in case give 422;
  - a package visual hint `red;background:url(x)` gives 422;
  - a package viewpoint that references a metatype of a solution extension gives 422;
  - a package viewpoint carrying the ID of an organization viewpoint, or of a viewpoint of another package, gives 409, and both stay unchanged;
  - a re-import that drops a viewpoint deletes only that viewpoint of the package; `metamodel.package-imported` holds `viewpointsCreated`, `viewpointsUpdated` and the ID in `viewpointsDeleted`;
  - `viewpoint.updated` lists `notation` and `mappings` when they change.
- [x] 2.6 Cucumber: extend `viewpoints.feature` and `package-import.feature` with the scenarios of the delta specs.

  Verify that they pass, together with the `@metamodel` scenarios.

## 3. Frontend

- [x] 3.1 Stories of SCR-031: the groups, the dialog with notation and mapping, and the notation change.

  Verify that the stories render without a11y violations.
- [x] 3.2 `ViewpointsSection.vue`, `ViewpointDialog.vue` and `api/metamodel.ts` (D6). Texts go in `i18n` en/de.

  Verify with the specs:
  - a system-defined viewpoint has no actions;
  - the element choice is filtered by notation and kind;
  - a notation change clears the elements that no longer fit;
  - the request carries the notation and the mappings;
  - a 422 shows at the mapping.
- [x] 3.3 `docs/screens/SCREENS.md` and the screen images of SCR-031 and SCR-030.

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [x] 4.1 Update the documentation:
  - `business-objects/viewpoint.md` and `docs/data-model.puml` (D7);
  - `docs/audit-event-catalog.md`: `notation` on `viewpoint.*`, `notation` and `mappings` among the fields of `viewpoint.updated`, and `viewpointsCreated`, `viewpointsUpdated` and `viewpointsDeleted` on `metamodel.package-imported` (D8);
  - UC-12, REQ-090, REQ-091, REQ-092 and REQ-059, realigned;
  - the user manual "Editing the metamodel" (section "Viewpoints") and the package import, in English and German.
- [x] 4.2 Set US-091 and US-092 to done, and US-090 to done with its notation (AC2 stays with the diagrams). Update the S15 row, and regenerate `requirements/traceability.md`.
- [x] 4.3 Run `python3 scripts/validate_links.py`, markdownlint, `openspec validate --strict` and `check_manual.py`, and verify they pass.

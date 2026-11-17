# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design D5): notation, viewpoint type, source package, mappings and effective mappings; `ViewpointMapping`, `NotationElement` and `listNotationElements`; `PackageViewpoint` extended.

  Verify: Redocly lint and both authorization scripts pass.

## 2. Backend

- [ ] 2.1 `V41__viewpoint_notations.sql` (D1).

  Verify with a migration test:
  - existing viewpoints become `generic` and `user-defined`;
  - the notation check;
  - system-defined exactly with a package;
  - the size boundaries: 1 and 2000 are accepted, and 0 and 2001 rejected;
  - the visual hint pattern;
  - the cascade on a deleted metatype.
- [ ] 2.2 The element catalog and `NotationCatalog` (D2).

  Verify with `NotationCatalogTests`:
  - every ArchiMate 3.2 element and relationship is present;
  - the counts per notation;
  - unique keys;
  - `fits` for the own prefix and `generic:`, and not for a foreign prefix.
- [ ] 2.3 `Viewpoints` and `ViewpointStore` with notation, mappings and read-only protection (D3).

  Verify with `ViewpointsTests`:
  - a notation is created, and a missing one becomes `generic`;
  - a mapping is accepted, while a foreign element, an unknown element, a relationship for an entity type and a type that is not allowed give 422;
  - the sizes 1 and 2000 are accepted, and 0 and 2001 give 400; a bad hint gives 422;
  - a notation change with old elements gives 422;
  - the effective fallback;
  - a system-defined viewpoint cannot be updated or deleted (422);
  - the catalog can be read with `entity:read`, and reading without it gives 403.
- [ ] 2.4 Package import of viewpoints, and the starter package 1.2.0 (D4).

  Verify with `PackageImportTests`:
  - the starter package installs two system-defined viewpoints;
  - a re-import updates and removes the viewpoints of the package;
  - another package with the same name gives 409, and an organization viewpoint with the name gives 409;
  - an unknown metatype gives 422;
  - shape assignments still give 422;
  - the import result counts the viewpoints.
- [ ] 2.5 Cucumber: extend `viewpoints.feature` and `package-import.feature` with the scenarios of the delta specs.

  Verify that they pass, together with the `@metamodel` scenarios.

## 3. Frontend

- [ ] 3.1 Stories of SCR-031: the groups, the dialog with notation and mapping, and the notation change.

  Verify that the stories render without a11y violations.
- [ ] 3.2 `ViewpointsSection.vue`, `ViewpointDialog.vue` and `api/metamodel.ts` (D6). Texts go in `i18n` en/de.

  Verify with the specs:
  - a system-defined viewpoint has no actions;
  - the element choice is filtered by notation and kind;
  - a notation change clears the elements that no longer fit;
  - the request carries the notation and the mappings;
  - a 422 shows at the mapping.
- [ ] 3.3 `docs/screens/SCREENS.md` and the screen images of SCR-031 and SCR-030.

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [ ] 4.1 Update the documentation:
  - `business-objects/viewpoint.md` and `docs/data-model.puml` (D7);
  - `docs/audit-event-catalog.md`;
  - UC-12, REQ-090, REQ-091, REQ-092 and REQ-059, realigned;
  - the user manual "Editing the metamodel" (section "Viewpoints") and the package import, in English and German.
- [ ] 4.2 Set US-091 and US-092 to done, and US-090 to done with its notation (AC2 stays with the diagrams). Update the S15 row, and regenerate `requirements/traceability.md`.
- [ ] 4.3 Run `python3 scripts/validate_links.py`, markdownlint, `openspec validate --strict` and `check_manual.py`, and verify they pass.

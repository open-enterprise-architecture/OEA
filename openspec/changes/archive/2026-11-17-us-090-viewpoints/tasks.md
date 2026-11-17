# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D3): the five viewpoint operations, `ViewpointConfig` and `ViewpointWrite`, and the `planned` markers removed.

  Verify: Redocly lint and both authorization scripts pass.

## 2. Backend

- [x] 2.1 `V39__viewpoints.sql` and `core/metamodel/ViewpointStore` (D1, D2).

  Verify with a migration test:
  - the name is unique case-insensitively;
  - the length limits hold (name 255 and 256, description 1000 and 1001);
  - deleting a metatype removes its links;
  - the grants of the runtime role are as listed in D1.
- [x] 2.2 `app/metamodel/Viewpoints` and `ViewpointController` (D2, D3).

  Verify with `ViewpointsTests`:
  - create, list (ordered), get, and 404;
  - the boundaries: a name of 255 characters is accepted and 256 rejected; a description of 1000 is accepted and 1001 rejected;
  - the 422s: a blank or taken name, no metatype, an unknown metatype, a connection type as metatype, a metatype as connection type, and a metatype of a solution extension;
  - edit with the version, and 412 and 428;
  - delete with 204 and 412;
  - a metatype deleted later leaves the viewpoint;
  - the permissions: 403 without `viewpoint:manage`, and reading with `entity:read` or with `viewpoint:manage`;
  - the audit of the three events, and no event on a rejection.
- [x] 2.3 Cucumber `features/metamodel/viewpoints.feature` with the scenarios of the spec.

  Verify that it passes, together with the `@metamodel` scenarios.

## 3. Frontend

- [x] 3.1 Story SCR-031 `Viewpoints` (list, reader, dialog, stale version), replacing the legacy mockup.

  Verify that the stories render without a11y violations.
- [x] 3.2 `api/viewpoints.ts`, `ViewpointsSection.vue` and `ViewpointDialog.vue` (D4). Texts go in `i18n` en/de.

  Verify with the specs:
  - the list;
  - the actions appear only with `viewpoint:manage`;
  - the checkbox groups separate element metatypes and connection types;
  - a 422 shows at its field;
  - a 412 shows the collision warning;
  - delete with confirmation.
- [x] 3.3 `docs/screens/SCREENS.md`: SCR-031 `implemented`, with the screen images.

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [x] 4.1 Update the documentation:
  - `business-objects/viewpoint.md` and `docs/data-model.puml` (D5);
  - `docs/audit-event-catalog.md`;
  - UC-12, REQ-090 and REQ-093, realigned;
  - the user manual "Editing the metamodel" (section "Viewpoints") in English and German.
- [x] 4.2 Set US-090 and US-093 to in progress (AC2 of US-090 and AC1 of US-093 follow with the diagrams). Update the S15 row, and regenerate `requirements/traceability.md`.
- [x] 4.3 Run `python3 scripts/validate_links.py`, markdownlint, `openspec validate --strict` and `check_manual.py`, and verify they pass.

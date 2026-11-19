# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D2): `ViewpointSize`, `sizes` in `ViewpointWrite`, `ViewpointConfig` and `PackageViewpoint`, `EffectiveViewpointMapping` with `width`, `height`, `length` and `sizeStored`, and no sizes in `ViewpointMapping` and `NotationElement`; the descriptions say pt.

  Verify:
  - Redocly lint;
  - `check_openapi_authorization.py` and its test;
  - `check_openapi_conventions.py` and its test (`ViewpointSize` declares no string without a limit).

## 2. Backend

- [x] 2.1 The migration `V45__viewpoint_sizes.sql` (D1), with a migration test:
  - a mapping of 120 × 55 px becomes 90 × 41 pt;
  - 1 px becomes 1 pt and 2000 px 1500 pt;
  - a width alone gets the height 30, a height alone the width 90;
  - a connection mapping with a width of 200 becomes a length of 150, one with only a height gives no row;
  - a mapping without a size gives no size row;
  - the mapping columns are gone.
- [x] 2.2 `ViewpointStore`, `Viewpoints` and `ViewpointMappings` (D3), `NotationCatalog` and `notations/catalog.json` without sizes, and `ViewpointController`.

  Verify with tests:
  - create and update with sizes, also without a mapping;
  - the effective size stored or 90 × 30 and 90;
  - the bounds 1 and 2000 accepted and 0 and 2001 rejected with 400, for width, height and length;
  - 1000 sizes accepted and 1001 rejected with 400;
  - 422 `sizes` for a width without a height, a length on an element metatype, a width on a connection metatype, a metatype not allowed, and two sizes for one metatype;
  - a mapping that still sends `defaultWidth` is accepted and stores no size (unknown property ignored);
  - an update without `sizes` removes the stored sizes;
  - removing a metatype drops its size, and deleting a metatype removes its sizes;
  - the audit names `sizes`, and nothing is audited on a rejected write;
  - system-defined viewpoints stay read-only (422);
  - `If-Match` as before.
- [x] 2.3 Packages (D4): `MetamodelPackages`, `PackageDefinition`, `PackageValidator` and `PackageImporter`.

  Verify with tests:
  - import of `sizes`, and a re-import replaces them;
  - 422 naming `/viewpoints/<i>/sizes` for a wrong kind of size, a metatype not allowed, two sizes for one metatype, and the bounds 0 and 2001 (1 and 2000 accepted);
  - a package mapping with `defaultWidth` is imported without a size;
  - 1000 sizes in a package viewpoint accepted and 1001 rejected with 422 naming the viewpoint;
  - the starter package 1.4.0 still validates and imports unchanged.
- [x] 2.4 Cucumber: extend the viewpoint feature with the scenarios of the delta spec. Reuse the shared instance and existing sessions; never log the same IdP user in twice.

  Verify: `mvn verify` with the full Cucumber suite passes.

## 3. Frontend

- [x] 3.1 `ViewpointDialog.vue` and the viewpoint view (D5), fakes, `i18n` en and de.

  Verify with specs:
  - the size fields per entity type and connection type, with the placeholders;
  - width without height is marked;
  - sizes are kept on a notation change;
  - the request carries `sizes`;
  - removing a type removes its size fields from the request.
- [x] 3.2 The SCR-031 story with sizes.

  Verify: the story tests with a11y pass; `python3 scripts/check_screens.py` passes.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D6.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate s17-viewpoint-default-sizes --strict` and `openspec validate --specs --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`.

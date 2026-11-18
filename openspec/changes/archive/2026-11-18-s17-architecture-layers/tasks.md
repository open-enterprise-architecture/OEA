# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D2): the four layer operations, `ArchitectureLayer` and `ArchitectureLayerWrite`, the metatype fields, the package section, and the enum values `layer` (`MetamodelChangeKind`) and `architecture-layer` (`MetamodelLabel.elementType`).

  Security (D6): declare `anyOfPermissions: [entity:read, metamodel:write]` on the list and `metamodel:write` on the writes; `If-Match` on update and delete; `name` `minLength: 1` and `maxLength: 100`; `architectureLayers` `maxItems: 20`.

  Verify: Redocly lint, `python3 scripts/check_openapi_authorization.py` and its test pass.

## 2. Backend

- [x] 2.1 `V44__architecture_layers.sql` (D1).

  Verify with a migration test:
  - the conversion of the column, and that a free-text value is cleared;
  - the unique name regardless of case;
  - the foreign key from metatypes.
- [x] 2.2 `ArchitectureLayers` with store and controller (D3).

  Verify with `ArchitectureLayersTests`:
  - **create:** the name at 100 accepted, 101 and blank rejected, a duplicate regardless of case rejected; the 20th layer accepted, the 21st rejected;
  - **rename and reorder:** positions 0 and 19, and 50 clamped to the end; the order is dense afterwards;
  - **delete:** an unused layer is deleted with its organization labels; a layer in use (also by an extension metatype) gives 409 `in-use` with `count`;
  - **name taken:** 409 `name-taken` regardless of case;
  - **imported layers:** renaming or deleting one gives 409 `read-only-metatype`;
  - **lock mode:** every write in `import-only` gives 403 `metamodel-locked` and changes nothing;
  - **`If-Match`:** 428 and 412;
  - **authorization:** 403 without `metamodel:write`, and 403 for reading without `entity:read`;
  - **audit:** the three events, listed by `listMetamodelChanges` with `kind=layer`, and none on a rejection;
  - **labels:** `architecture-layer.{id}.name` is accepted by `updateMetamodelLabels`, listed by `getMetamodelLabels` and carried by the bundle.
- [x] 2.3 The layer of a metatype in `MetatypeEditing` (D3).

  Verify:
  - setting and clearing the layer is audited and raises the metatype version;
  - an extension metatype takes an instance layer;
  - an unknown layer gives 422 naming `architectureLayerId`;
  - the read returns `architectureLayerId` and `architectureLayerName`.
- [x] 2.4 Packages and the starter package 1.4.0 (D3).

  Verify:
  - a package with layers imports them with their labels and references;
  - a reference to a layer outside the package gives 422;
  - 21 layers give 422;
  - a re-import removes its own dropped layer, clears the assignments to it and keeps the organization layers;
  - an ID or name collision with an organization layer or another package's layer gives 409 `package-conflict` with the layer in `conflictList`;
  - a package label `architecture-layer.{id}.name` for a layer outside the package gives 422;
  - the starter package imports the four layers and the assignments of D3;
  - the existing starter-package tests are updated (version, counts).
- [x] 2.5 Cucumber: `features/metamodel/architecture-layers.feature` with the scenarios of the delta spec.

  Logins on shared instances reuse existing sessions; never log the same IdP user in twice.

  Verify: the full Cucumber suite passes.

## 3. Frontend

- [x] 3.1 Stories first: SCR-030 with the section "Layers" (organization and imported layers, the delete warning) and the layer select in the metatype dialog.

  Verify: the stories render without a11y violations.
- [x] 3.2 `ArchitectureLayersSection.vue`, the metatype dialog and the list column, with `api/metamodel.ts` and the fake (D4). Texts go in `i18n` en/de.

  Verify with specs:
  - create, rename and move send the right requests with `If-Match`;
  - a 409 on delete shows the number of metatypes;
  - imported layers have no actions;
  - without `metamodel:write` or in `import-only` the section is read-only;
  - "Translations" lists layers, and "Changes" filters by the kind `layer`;
  - a name with markup is shown literally.
- [x] 3.3 `docs/screens/SCREENS.md`: SCR-030 names the section.

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D5.
- [x] 4.2 Regenerate `requirements/traceability.md` if needed.
- [x] 4.3 Run the checks:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate s17-architecture-layers --strict`;
  - `python3 scripts/check_manual.py`.

  Verify that they pass.

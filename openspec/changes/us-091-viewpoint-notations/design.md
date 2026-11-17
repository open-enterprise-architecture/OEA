# Design

## Context

- **Viewpoints (V39, `us-090-viewpoints`):**
  - `viewpoints` holds `id`, `name`, `description` and `version`;
  - the link tables `viewpoint_metatypes` and `viewpoint_connection_types` hold the allowed types;
  - `core/metamodel/ViewpointStore` and `app/metamodel/Viewpoints` own them, and SCR-031 is a section of the metamodel page.
- **Package import (`app/metamodel/PackageImporter`, `PackageValidator`):**
  - it imports categories, property definitions, metatypes and labels with `scope = imported` and `source_package`;
  - it rejects foreign content with 409 and unsupported sections with 422;
  - `PackageViewpoint` in the contract is a stub.
- **Starter package (`packages/oea-starter-togaf-classic.json`):** seven metatypes and no viewpoints.

## Goals / Non-Goals

**Goals:**

- Notation and notation mapping with its checks.
- The element catalog.
- System-defined viewpoints from packages, read-only.
- The starter package with two viewpoints.
- SCR-031 extended.

**Non-Goals:** rendering, export and import outside packages, shape assignments, diagram counts.

## Decisions

### D1 – Schema (`V41__viewpoint_notations.sql`)

- **`viewpoints` gets:**
  - `notation TEXT NOT NULL DEFAULT 'generic' CHECK (notation IN ('generic', 'archimate3', 'uml', 'bpmn2'))`;
  - `viewpoint_type TEXT NOT NULL DEFAULT 'user-defined' CHECK (viewpoint_type IN ('system-defined', 'user-defined'))`;
  - `source_package_id TEXT` and `source_package_version TEXT`, with the check `(viewpoint_type = 'system-defined') = (source_package_id IS NOT NULL)`.
- **`viewpoint_mappings`:**
  - `(viewpoint_id, metatype_id)` is the primary key, with `ON DELETE CASCADE` on both foreign keys;
  - `notation_element TEXT NOT NULL`;
  - `default_width` and `default_height INTEGER CHECK BETWEEN 1 AND 2000`;
  - `visual_hint TEXT CHECK (visual_hint ~ '^#[0-9a-fA-F]{6}$')`.

  The service checks that the metatype is allowed.
- **Existing rows** get `generic` and `user-defined` through the defaults.
- **Grants:** the runtime role gets all rights on `viewpoint_mappings`.

### D2 – Element catalog

- **Resource:** `backend/src/main/resources/notations/catalog.json` holds entries `{key, notation, label, kind: element|relationship, defaultWidth, defaultHeight}`.
- **Content:**
  - **ArchiMate 3.2:** all elements of the business, application, technology, physical, motivation, strategy and implementation-and-migration layers, plus the eleven relationships.
  - **UML:** Component, Interface, Class, Package, Node, Artifact, Actor and UseCase, with Association, Dependency, Realization and Generalization.
  - **BPMN 2.0:** Task, SubProcess, StartEvent, EndEvent, IntermediateEvent, Gateway, DataObject, DataStore, Pool and Lane, with SequenceFlow, MessageFlow and Association.
  - **Generic:** `generic:Box` and `generic:Arrow`.
- **Reader:** `app/metamodel/NotationCatalog` reads it once at startup. The resource is code, so this is not SQL and not a store. It answers `element(key)`, `fits(notation, key)` (the prefix, or `generic:`) and `list()`.
- **Operation:** `listNotationElements` (`GET /admin/viewpoints/notations`), with `entity:read` or `viewpoint:manage`.

### D3 – Service and store

- **`ViewpointStore`:** reads and writes `notation`, `viewpoint_type`, the source package and the mappings. `systemDefined(id)` is new, and so is `byPackageAndName` for the import.
- **`Viewpoints`:**
  - **checks:** the notation (an unknown one gives 400 from the contract enum), and per mapping: allowed metatype, element known, `fits`, and the shape kind against `is_connection`;
  - **read-only:** a system-defined viewpoint gives 422 "System-defined viewpoints are read-only" on update and delete;
  - **effective element:** reading returns `effectiveElement` per allowed type, which is the mapping or the generic fallback, together with the size from the mapping or the catalog.
- **Audit:** `notation` is added to the details of `viewpoint.*`.

### D4 – Package import

- **Definition and validation:**
  - `PackageDefinition` gets `viewpoints`, and `viewpoints` leaves `unsupportedSections` (`shapeAssignments` stays there);
  - `PackageValidator` checks names, notation and mappings with the catalog (422 naming `/viewpoints/<i>`).
- **Import:** `PackageImporter` imports the viewpoints after the metatypes.
  - **Conflicts:** an organization viewpoint, or one of another package, with the same name (case-insensitive) is a conflict in the existing conflict list (409).
  - **Re-import:** viewpoints of the same package are matched by name and replaced, with links and mappings; viewpoints of the package that no longer appear in it are deleted.
  - **References:** metatype IDs are resolved against the package's metatypes and the instance. An unknown ID gives 422.
- **Result:** `ImportResult` counts `viewpoints`, and `metamodel.package-imported` names the count.
- **Starter package (version 1.2.0):** "Application Landscape" and "Technology Landscape" (`archimate3`), with mappings to `archimate:ApplicationComponent`, `archimate:ApplicationService`, `archimate:DataObject`, `archimate:SystemSoftware`, `archimate:Node`, `archimate:TechnologyService` and the like.

### D5 – Contract (delta to `api/openapi.yaml`)

- **`ViewpointConfig`:** `notation`, `viewpointType`, `sourcePackage` (`PackageRef` or null) and `mappings` (`ViewpointMapping[]`). Each allowed type in `allowedMetatypes` and `allowedRelations` stays a `MetatypeSummary`. `effectiveMappings` gives one entry per allowed type, with `metatypeId`, `notationElement`, `defaultWidth`, `defaultHeight`, `visualHint` and `fallback` (boolean).
- **`ViewpointWrite`:** `notation` (default `generic`) and `mappings` (0–500).
- **New schemas:**
  - `ViewpointMapping`: `metatypeId`, `notationElement`, `defaultWidth`, `defaultHeight`, `visualHint`;
  - `NotationElement`: `key`, `notation`, `label`, `kind`, `defaultWidth`, `defaultHeight`.
- **`PackageViewpoint`:** `name`, `description`, `notation`, `metatypeIds`, `connectionTypeIds` and `mappings`.
- **Operation:** `listNotationElements`.

### D6 – Frontend

- **`ViewpointsSection.vue`:** two groups, "System-defined" (with a lock and without actions) and "Organization". It shows the notation badge of each viewpoint.
- **`ViewpointDialog.vue`:**
  - a notation `USelect`;
  - after the types, a mapping table per selected type, with an element `USelect` (filtered by notation and by kind, plus the generic element), width, height and a color input;
  - changing the notation clears the elements that no longer fit (US-091 AC3).

  The catalog is read once with `fetchNotationElements`.
- **Tests:** stories and specs of the group, the filter and the reset.

### D7 – Documentation and data model

- `business-objects/viewpoint.md` (0.4.0): notation, viewpoint type and source package, mappings, and the generic notation.
- `docs/data-model.puml`: the columns and the table of V41.
- REQ-059: the import of viewpoints in packages is implemented, while the export is not.

## Risks / Trade-offs

- **A hand-maintained catalog:** it can lag behind new versions of the notations, but it is data and easy to extend.
- **Re-import replaces the viewpoints of the package:** organization changes to them are impossible, because they are read-only, so nothing is lost.

## Migration Plan

- V41 adds columns with defaults and a new table. The starter package version rises to 1.2.0, and instances get its viewpoints with the next import or re-import of the package.

## Open Questions

- None. The assumptions of the proposal are open for review in the proposal pull request.

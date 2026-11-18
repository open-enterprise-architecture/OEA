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
- **Creator:** a package viewpoint records the importing person in `created_by` (and `updated_by` on a re-import). Only holders of `metamodel:write` import, and the system admin account does not hold it (ADR-032 role matrix), so the column stays `NOT NULL`.
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
- **Routing (decision of the architect, 2026-11-17):** the literal path `/admin/viewpoints/notations` sits next to `/admin/viewpoints/{id}`. Spring MVC ranks a pattern with fewer variables first, so the literal path wins; `AuthorizationInterceptor` takes the rule from the handler method (`ContractAuthorizationRules.rule(methodName)`), not from the path, so it follows the routing; `scripts/check_openapi_authorization.py` checks per operation and matches no paths. A controller test pins it (task 2.3).
- **Key length:** the longest key of the catalog has about 40 characters (e.g. `archimate:ApplicationCollaboration`), well within the contract limit of 100; `NotationCatalogTests` asserts that every key has at most 100 characters.

### D3 – Service and store

- **`ViewpointStore`:** reads and writes `notation`, `viewpoint_type`, the source package and the mappings. `systemDefined(id)` is new, and so is `byPackageAndName` for the import.
- **`Viewpoints`:**
  - **checks:** the notation (an unknown one gives 400 from the contract enum), and per mapping: allowed metatype, element known, `fits`, and the shape kind against `is_connection`. Count, key length, sizes and the hint pattern are contract checks (400; `ViewpointController` declares no `@ContractViolationResponse`);
  - **read-only:** a system-defined viewpoint gives 422 with the problem type `urn:oea:problem:read-only-viewpoint` and the detail "System-defined viewpoints are read-only" on update and delete. The status follows US-092 AC1 (422), unlike the 409 `read-only-metatype` of imported metatypes;
  - **effective element:** reading returns `effectiveElement` per allowed type, which is the mapping or the generic fallback, together with the size from the mapping or the catalog.
- **Audit:** `notation` is added to the details of `viewpoint.*`.

### D4 – Package import

- **Definition and validation:**
  - `PackageDefinition` gets `viewpoints`, and `viewpoints` leaves `unsupportedSections` (`shapeAssignments` stays there);
  - `PackageValidator` checks names, notation and mappings with the catalog (422 naming `/viewpoints/<i>`).
- **Import:** `PackageImporter` imports the viewpoints after the metatypes.
  - **Identity (decision of the architect, 2026-11-17):** a package viewpoint carries a stable `id` (required, UUID), like the metatypes, categories and property definitions of a package; the conflict check uses the same query shape as for metatypes (`WHERE id = ? OR (lower(name) = lower(?) AND id <> ?)`). Identity by ID keeps a viewpoint's ID across a rename in a new package version, which the diagrams will reference.
  - **Conflicts:** an organization viewpoint, or one of another package, with the same ID or the same name (case-insensitive) is a conflict in the existing conflict list (409), with the element `viewpoint <name>`.
  - **Re-import:** viewpoints of the same package are matched by ID and replaced, with links and mappings; viewpoints of the package whose IDs no longer appear in it are deleted.
  - **References:** metatype IDs are resolved against the package's metatypes and the instance. An unknown ID gives 422.
- **Result:** `ImportResult` gets `viewpointsCreated`, `viewpointsUpdated` and `viewpointsDeleted` (counts, null outside a metamodel package import), and `metamodel.package-imported` names them (D8).
- **Starter package (version 1.2.0):** "Application Landscape" and "Technology Landscape" (`archimate3`), with fixed UUIDs (v5, like its metatypes) and the mappings:
  - Application Component → `archimate:ApplicationComponent`, Application Service → `archimate:ApplicationService`, Data Object → `archimate:DataObject`;
  - Technology Component → `archimate:Node`, Technology Service → `archimate:TechnologyService`, System Software → `archimate:SystemSoftware`, Network Node → `archimate:Device`.

### D5 – Contract (delta to `api/openapi.yaml`)

- **`ViewpointConfig`:** `notation`, `viewpointType`, `sourcePackage` (`PackageRef` or null) and `mappings` (`ViewpointMapping[]`). Each allowed type in `allowedMetatypes` and `allowedRelations` stays a `MetatypeSummary`. `effectiveMappings` gives one entry per allowed type, with `metatypeId`, `notationElement`, `defaultWidth`, `defaultHeight`, `visualHint` and `fallback` (boolean).
- **`ViewpointWrite`:** `notation` (default `generic`) and `mappings` (0–1000).
- **Limit of `mappings` (decision of the architect, 2026-11-17):** at most one mapping per allowed type, and a viewpoint allows up to 500 element and 500 connection metatypes, so the limit is 1000 instead of the 500 first designed. A lower limit would make a viewpoint that maps every allowed type impossible.
- **New schemas:**
  - `ViewpointMapping`: `metatypeId`, `notationElement`, `defaultWidth`, `defaultHeight`, `visualHint`;
  - `NotationElement`: `key`, `notation`, `label`, `kind`, `defaultWidth`, `defaultHeight`.
- **`PackageViewpoint`:** `id` (required, UUID), `name`, `description`, `notation`, `metatypeIds`, `connectionTypeIds` and `mappings`, with the limits of `ViewpointWrite`.
- **Problem type:** `urn:oea:problem:read-only-viewpoint` (422) on `updateViewpoint` and `deleteViewpoint`.
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

### D8 – Security (added by the security engineer)

- **Authorization:**
  - `createViewpoint`, `updateViewpoint` and `deleteViewpoint` stay with `viewpoint:manage`, and the reads with `entity:read` or `viewpoint:manage`; `listNotationElements` uses the same `anyOfPermissions` as `listViewpoints`. The catalog is static data shipped with the release and holds nothing of the instance.
  - The package import stays with `metamodel:write`, which among the built-in roles only `metamodel-admin` holds. A metamodel administrator thereby installs viewpoints that holders of `viewpoint:manage` (`enterprise-architect`) cannot change or delete. **Owner decision of 2026-11-17 (Q-1 of the security review, option a):** `metamodel:write` alone installs the system-defined viewpoints of a package, the same model as for metatypes; no `viewpoint:manage` is needed for the import.
- **Origin only from the import:** `ViewpointWrite` has no `viewpointType` and no `sourcePackage`, and `Viewpoints.create` and `update` always store `user-defined` without a package, so a forged body cannot create a viewpoint nobody can delete. The check `(viewpoint_type = 'system-defined') = (source_package_id IS NOT NULL)` of V41 backs it in the schema. The read-only check runs after the row lock of `locked(id, version)` and rejects even with the current version.
- **Input limits (API):** `mappings` `maxItems: 1000`, `notationElement` `maxLength: 100`, `defaultWidth` and `defaultHeight` 1–2000, `visualHint` with the pattern `^#[0-9a-fA-F]{6}$`. The service rejects a second mapping of the same metatype with 422 before the insert; without it the primary key of `viewpoint_mappings` would answer 500.
- **Untrusted package content:**
  - The package schema has no limits on `PackageViewpoint`. `PackageValidator` therefore applies the API limits itself, because the package path does not pass the contract checks of `ViewpointWrite`: name 1–255 characters, description at most 1000, at most 500 entries in each of `metatypeIds` and `connectionTypeIds` and 1000 in `mappings`, the key length, sizes and hint pattern, and at most `MAXIMUM_ELEMENTS` (10000) viewpoints. The 10 MB size limit of the import bounds the total.
  - Names are unique within the package regardless of case. Without this check the second viewpoint would overwrite the first in the same import.
  - Referenced types are element or connection metatypes of the instance configuration (or of the package), checked with the same `kinds(…, INSTANCE_CONFIGURATION)` as the API, so a package cannot place a metatype of a solution extension into a viewpoint.
  - The visual hint pattern stops CSS or SVG injection when the diagrams later render the hint; the element key is only ever a key of the catalog.
- **Identity on import:** an imported viewpoint is matched by its `id` and `source_package_id` (D4). An ID or a name of an organization viewpoint or of another package's viewpoint is a conflict in the conflict list (409), so an import never takes over a foreign row. Re-import deletes only rows with the same `source_package_id`.
- **Audit:**
  - `viewpoint.*` name `notation`, and `viewpoint.updated` lists `notation` and `mappings` among `fields`; mapping values are not copied;
  - `metamodel.package-imported` gets `viewpointsCreated`, `viewpointsUpdated` (counts) and `viewpointsDeleted` (the IDs), so that a re-import that removes viewpoints is traceable;
  - a viewpoint conflict is audited as `metamodel.package-rejected` with the reason `conflict`, as for metatypes.

  No personal data enters the details.
- **Error responses:** the read-only rejection uses the fixed detail "System-defined viewpoints are read-only"; validation problems name the field or JSON pointer without echoing package content beyond the viewpoint name.

## Risks / Trade-offs

- **A hand-maintained catalog:** it can lag behind new versions of the notations, but it is data and easy to extend.
- **Re-import replaces the viewpoints of the package:** organization changes to them are impossible, because they are read-only, so nothing is lost.

## Migration Plan

- V41 adds columns with defaults and a new table. The starter package version rises to 1.2.0, and instances get its viewpoints with the next import or re-import of the package.

## Open Questions

- None. The assumptions of the proposal are open for review in the proposal pull request.

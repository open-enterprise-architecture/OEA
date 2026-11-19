# Design

## Context

- **The mapping table:** `viewpoint_mappings` (V41) holds per viewpoint and metatype a notation element and the optional `default_width`, `default_height` (1–2000) and `visual_hint`.
- **The element catalog:** `notations/catalog.json` gives each element `defaultWidth` and `defaultHeight` in canvas units, read by `NotationCatalog`.
- **The service:** `Viewpoints` computes `effectiveMappings`: the mapping, or `generic:Box`/`generic:Arrow` with the catalog size.
- **The persistence:** `core.metamodel.ViewpointStore` (ADR-051).
- **The packages:** they carry viewpoints with mappings (`PackageViewpoint` in the contract, read by `MetamodelPackages` into `PackageDefinition`, checked by `PackageValidator`, written by `PackageImporter`). `exportMetamodelPackage` is still `planned` (no export code). The starter package 1.4.0 maps its metatypes without sizes.
- **Unknown properties:** the generated models are read by Jackson with unknown properties ignored (Spring Boot default; the scenario "Forged origin" relies on it); `additionalProperties: false` is not enforced by the generator.
- **The client:** SCR-031 edits mappings in `ViewpointDialog.vue`.

## Goals / Non-Goals

**Goals:**

- A default size per allowed metatype, independent of the mapping, in pt.
- The defaults 90 × 30 pt and 90 pt.
- The effective size in the viewpoint read.

**Non-Goals:**

- Using the sizes on a canvas (change 10).
- Sizes per diagram or per notation element.

## Architecture Constraints

- **ADR-051:** SQL only in `core.metamodel.ViewpointStore`.
- **ADR-013:** the contract first.
- **ADR-033:** audit of viewpoint writes.
- **ADR-032:** `viewpoint:manage` for writes, and `entity:read` or `viewpoint:manage` for reads, unchanged.
- **REQ-184:** a faulty package is rejected with 422 and the location of the first problem.

## Decisions

### D1 Table `viewpoint_sizes`

```sql
CREATE TABLE viewpoint_sizes (
    viewpoint_id UUID NOT NULL REFERENCES viewpoints (id) ON DELETE CASCADE,
    metatype_id  UUID NOT NULL REFERENCES metatypes (id) ON DELETE CASCADE,
    width_pt     INTEGER CHECK (width_pt  BETWEEN 1 AND 2000),
    height_pt    INTEGER CHECK (height_pt BETWEEN 1 AND 2000),
    length_pt    INTEGER CHECK (length_pt BETWEEN 1 AND 2000),
    PRIMARY KEY (viewpoint_id, metatype_id),
    CHECK ((width_pt IS NOT NULL AND height_pt IS NOT NULL AND length_pt IS NULL)
        OR (width_pt IS NULL AND height_pt IS NULL AND length_pt IS NOT NULL))
);
```

- The migration is `V45__viewpoint_sizes.sql` (V44 is the last one).
- **Data migration** (the kind from `metatypes.is_connection`; `ROUND(numeric)` rounds half away from zero):
  - Each mapping row of an element metatype with a width or a height becomes a size row with `GREATEST(1, ROUND(px × 0.75))`, the missing value taken from 90 × 30 pt.
  - Each mapping row of a connection metatype with a width becomes a length of `GREATEST(1, ROUND(width × 0.75))`; a height alone gives no row, since a connection has no height.
- The columns `default_width` and `default_height` of `viewpoint_mappings` are dropped.
- The grant follows the other tables (`oea_app`).
- An index on `metatype_id` follows the mapping table.

### D2 Contract

- **`ViewpointSize`** (new):
  - `metatypeId` (uuid);
  - `width`, `height` and `length`: integers 1–2000, each optional in the schema; the combination is checked by the service (D3).
  - The description says "pt".
- **`ViewpointWrite.sizes`:** an optional array of `ViewpointSize`, `maxItems: 1000`.
- **`ViewpointConfig.sizes`:** the stored sizes.
- **`ViewpointMapping`:** loses `defaultWidth` and `defaultHeight`. The project does not reject unknown properties (the generator ignores `additionalProperties: false`, and Jackson ignores unknown properties), so a write that still sends them is accepted and stores no size; this is tested. No `additionalProperties: false` is added.
- **`EffectiveViewpointMapping`:**
  - `defaultWidth`/`defaultHeight` are replaced by `width`, `height` (element metatypes) and `length` (connection metatypes), as nullable integers in pt;
  - `sizeStored` (boolean) is added.
- **`NotationElement`:** loses `defaultWidth` and `defaultHeight`. `catalog.json` keeps no sizes either.
- **Package schema:** `PackageViewpoint` gets `sizes` (`ViewpointSize`, `maxItems: 1000`); its mappings use `ViewpointMapping` and so lose the size fields.

### D3 Validation in `Viewpoints`

- Each size names an allowed metatype of the write (422 `sizes` otherwise), at most once (422 `sizes`).
- An element metatype needs `width` and `height` and no `length`; a connection metatype needs `length` and neither width nor height (422 `sizes` otherwise).
- Bounds 1–2000 and `maxItems` are checked by the contract (400).
- On update, the sizes of the write replace the stored ones, as the mappings do; omitted `sizes` store none. A write that removes a metatype therefore carries no size for it (one for it is 422), and its stored size is gone; deleting a metatype removes its sizes by `ON DELETE CASCADE`.
- The audit `viewpoint.updated` adds `sizes` to the changed fields when the stored set differs.
- The effective size is the stored one, or 90 × 30 pt for an element metatype and 90 pt for a connection metatype (constants in `Viewpoints`).

### D4 Packages

- `MetamodelPackages` maps `sizes` into `PackageDefinition`; `PackageValidator` checks them with the rules of D3 (the bounds 1–2000 and at most 1000 sizes included, since the starter package is read without bean validation and a package violation is 422, not 400) and gives 422 with the location, naming `/viewpoints/<i>/sizes`, as mappings name `/viewpoints/<i>/mappings`.
- `PackageImporter` stores the sizes with the viewpoint and replaces them on a re-import.
- `defaultWidth`/`defaultHeight` in a package mapping are ignored like any unknown property (context).
- No export: `exportMetamodelPackage` stays `planned`; only its schema `MetamodelPackage` gets `sizes` through `PackageViewpoint`.
- The starter package 1.4.0 carries no mapping sizes and stays unchanged; `StarterPackageCheck` keeps validating it.

### D5 SCR-031

- In `ViewpointDialog.vue`, each selected entity type gets the fields "Width (pt)" and "Height (pt)", and each connection type "Length (pt)".
- The fields are empty by default, with the placeholders 90, 30 and 90.
- Width and height are filled together or left empty together, with a field message otherwise.
- Changing the notation keeps the sizes.
- System-defined viewpoints keep their list row without a dialog (no read view is added).
- Removing a type in the dialog removes its size fields and their values.
- The story SCR-031 shows sizes; the a11y check runs on it.

### D6 Documentation

- **The manual (en/de):** "Viewpoints" (the default sizes in pt, 90 × 30 and 90) and "Metamodel packages" (`sizes`).
- **The business object Viewpoint and `docs/data-model.puml`:** `ViewpointSize`; the size leaves `NotationMapping`.
- **REQ-091, US-091 and UC-12:** realigned.
- **The sprint plan:** the status of change 9.

## Data Model

- New: `ViewpointSize` (`viewpointId`, `metatypeId`, `widthPt`, `heightPt`, `lengthPt`).
- `NotationMapping` loses `defaultWidth` and `defaultHeight`.

The migration is D1.

## API

The schema changes of D2. No new operation.

## Risks / Trade-offs

- **[Risk]** Clients or packages that still send the size in a mapping break.
  - **Mitigation:** before 1.0 the only client is the one in this repository, and no shipped or exported package carries mapping sizes. Such fields are ignored, not rejected, as all unknown properties are.
- **[Risk]** Converting stored sizes to pt rounds.
  - **Mitigation:** the rounding is at most 0.5 pt and is shown in the migration test.
- **[Trade-off]** The defaults 90 × 30 pt replace notation-specific defaults: a BPMN event is placed as 90 × 30 unless the viewpoint stores a size (owner decision). The starter package can bring sizes for its viewpoints where it makes sense.

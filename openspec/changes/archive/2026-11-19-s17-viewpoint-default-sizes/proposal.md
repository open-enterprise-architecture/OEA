# Proposal

## Why

UC-10 brings the first diagrams. The owner decided on 2026-11-19 how large shapes are when they are placed:

- **Per viewpoint:** every viewpoint stores a default size for each allowed metatype.
- **Without a stored size:** an element shape is 90 pt wide and 30 pt high, and a connection 90 pt long.

Today a viewpoint can store a size only inside a notation mapping (S15, `us-091-viewpoint-notations`). Without a mapping the size of the element catalog applies, for example 120 × 55 for ArchiMate, 36 × 36 for BPMN events and 600 × 200 for a pool. The unit is unspecified (canvas units). The diagram editor of the next changes needs the size per metatype before it can place a shape.

This is S17 change 9 ([sprint plan](../../../docs/sprint-plan.md)), the first of UC-10.

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-10](../../../requirements/use-cases/UC-10-model-business-processes.md), [UC-12](../../../requirements/use-cases/UC-12-manage-viewpoints.md) |
| User Stories | [US-091](../../../requirements/user-stories/US-091-viewpoint-notation-mapping.md) (notation mapping with default size), [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) |
| Requirements | [REQ-091](../../../requirements/req/REQ-091-viewpoint-notation-mapping.md) |
| Business Object | [Viewpoint](../../../business-objects/viewpoint.md) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-19:**

- **What a viewpoint stores:** a default size per allowed metatype, also without a notation mapping:
  - width and height for an element metatype;
  - a length for a connection metatype.
- **Without a stored size:**
  - every element shape is 90 × 30 pt and every connection 90 pt, for every notation element;
  - the sizes of the element catalog are no longer defaults.
- **Unit:** sizes are in pt everywhere: API, storage and screens.
- **The length** is the distance at which a connected new element is placed from its source. It is used by the diagram editor (change 10); this change only stores it.

**Decisions and assumptions of this change (open for review):**

- **Contract:**
  - The sizes move out of the mapping into a list `sizes` of the viewpoint: `{metatypeId, width, height}` or `{metatypeId, length}`, integers 1–2000 pt, at most one per metatype, at most 1000 per write.
  - `ViewpointMapping` loses `defaultWidth` and `defaultHeight`. A write that still sends them stores no size: the API ignores unknown properties (Spring Boot default, as for `viewpointType` in a write), so they are not rejected.
  - `EffectiveViewpointMapping` carries the effective `width` and `height`, or `length`, and `sizeStored` (true when the viewpoint stores it).
  - `NotationElement` loses `defaultWidth` and `defaultHeight`.
- **Validation:**
  - A width without a height, a length for an element metatype, or a width for a connection metatype is rejected with 422 naming `sizes`.
  - The same applies to a size for a metatype that is not allowed, or to two sizes for one metatype.
- **Removing a metatype** from a viewpoint removes its stored size, as it removes its mapping.
- **Migration:**
  - The sizes stored in mappings today move to the new sizes, converted from canvas units (px at 96 dpi) to pt by `round(px × 0.75)`, at least 1.
  - A mapping with only one of width and height gets the other value from 90 × 30.
  - Mapping rows keep element and hint.
- **Metamodel packages:**
  - The section `viewpoints` (`PackageViewpoint`) gets `sizes` in the same form, checked with the location of a violation (REQ-184).
  - `defaultWidth`/`defaultHeight` in a package mapping are ignored like any unknown property: the starter package 1.4.0 carries no sizes, and no package was exported yet (`exportMetamodelPackage` is still `planned`). The starter package stays 1.4.0.
- **Permissions, lost updates and read-only:** `viewpoint:manage`, `If-Match` and the read-only rule of system-defined viewpoints stay as they are.
- **Audit:** `viewpoint.updated` names `sizes` when they change.

**Not in this change:**

- placing shapes (diagram editor, change 10);
- sizes per diagram.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `metamodel/viewpoints`: default sizes per metatype in pt, with the defaults 90 × 30 pt and 90 pt; the size leaves the mapping and the element catalog.
- `metamodel/package-import`: package viewpoints carry default sizes with the same checks and limits.

## Impact

- **Contract:** `ViewpointWrite`, `ViewpointConfig`, `ViewpointMapping`, `EffectiveViewpointMapping`, `NotationElement` and the package schema.
- **Backend:**
  - a migration (new table `viewpoint_sizes`, data moved, the mapping columns dropped);
  - `ViewpointStore`, `Viewpoints`, `ViewpointMappings`, `NotationCatalog` (and `notations/catalog.json`), `PackageDefinition`, `PackageValidator`, `PackageImporter`, `MetamodelPackages` and `ViewpointController`.
- **Frontend:**
  - `ViewpointDialog.vue` (size fields in pt per type);
  - `ViewpointNotation`, fakes and stories (SCR-031).
- **Documentation:**
  - the manual "Viewpoints" and "Metamodel packages" (en/de);
  - the business object Viewpoint and `docs/data-model.puml`;
  - REQ-091, US-091 and UC-12;
  - the sprint plan.

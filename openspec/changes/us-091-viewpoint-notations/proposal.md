# Proposal

## Why

The viewpoints of change 1 (`us-090-viewpoints`) are generic: they name entity and connection types, but no notation. UC-12 describes viewpoints that also choose a notation (ArchiMate 3, UML or BPMN 2.0) and map each entity type to an element of it, so that diagrams render consistently. It also describes system-defined viewpoints that nobody can change. The owner decided on 2026-11-17 to add these parts now instead of waiting for the diagrams.

This change covers:

- [US-091](../../../requirements/user-stories/US-091-viewpoint-notation-mapping.md): a consistent notation mapping;
- [US-092](../../../requirements/user-stories/US-092-system-viewpoint-readonly.md): read-only system viewpoints;
- the notation part of [US-090](../../../requirements/user-stories/US-090-viewpoint-crud.md).

This is S15 change 3.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-12](../../../requirements/use-cases/UC-12-manage-viewpoints.md) (basic flow steps 4, 8, 9; A1; the system-defined group of step 2); [UC-04](../../../requirements/use-cases/UC-04-configure-metamodel.md) (package import) |
| User Story | [US-091](../../../requirements/user-stories/US-091-viewpoint-notation-mapping.md) (AC1–AC3), [US-092](../../../requirements/user-stories/US-092-system-viewpoint-readonly.md) (AC1–AC3), [US-090](../../../requirements/user-stories/US-090-viewpoint-crud.md) (notation) |
| Requirements | [REQ-090](../../../requirements/req/REQ-090-create-edit-delete-viewpoint.md), [REQ-091](../../../requirements/req/REQ-091-viewpoint-notation-mapping.md), [REQ-092](../../../requirements/req/REQ-092-system-viewpoint-read-only.md), [REQ-059](../../../requirements/req/REQ-059-viewpoint-import-export.md) (import of viewpoints in packages), [REQ-033](../../../requirements/req/REQ-033-metamodel-import.md) |
| ADRs | [ADR-002](../../../adrs/ADR-002-continuum-repository.md) (package content is read-only), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Sprint / Milestone | S15 / Milestone 3 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-17:**

- **Notations:** viewpoints support ArchiMate 3, UML and BPMN 2.0, plus the neutral elements `generic:Box` and `generic:Arrow`. OEA ships the element catalog as data: ArchiMate 3.2 in full, the UML component and class elements, and the BPMN 2.0 core elements.
- **System-defined viewpoints come from metamodel packages.** A package that carries viewpoints installs them as system-defined, read-only viewpoints, which no other package or person overwrites (US-092). The import of the package section `viewpoints` becomes supported. The starter package gets two viewpoints.
- **Existing viewpoints:** viewpoints without a notation get the notation `generic`, which stays selectable.
- **Notation mapping is optional:** per allowed entity type it names a notation element, a default size and a visual hint. An entity type without a mapping is rendered as `generic:Box` with the default size.

**Assumptions recorded here (minor details, open for review):**

- **Notations and keys:**
  - the values are `generic`, `archimate3`, `uml` and `bpmn2`;
  - element keys carry the prefix of their notation (`archimate:`, `uml:`, `bpmn:`), and `generic:Box` and `generic:Arrow` fit every notation;
  - the catalog distinguishes element shapes from relationship shapes. An entity type maps to an element shape, and a connection type to a relationship shape.
- **The catalog** is read with `listNotationElements` (`GET /admin/viewpoints/notations`), with `entity:read` or `viewpoint:manage`.
- **Mapping validation (US-091 AC2):**
  - an element of another notation, an unknown element, or a mapping of a type the viewpoint does not allow gives 422 naming `mappings`;
  - a width or height outside 1 to 2000 gives 400;
  - the visual hint is an optional color `#rrggbb` (422 otherwise).
- **Changing the notation:** a request with a new notation must not keep elements of the old one (422). The dialog clears them, as US-091 AC3 asks.
- **System-defined viewpoints:**
  - they carry `viewpointType = system-defined` and their package;
  - update and delete give 422 "System-defined viewpoints are read-only" (US-092 AC1);
  - SCR-031 shows them in their own group without "Edit" and "Delete" (AC2);
  - a re-import of the same package updates them;
  - another package or an organization viewpoint with the same name gives 409, as for metatypes (AC3; package-import "Content of others is never overwritten").
- **Package viewpoints** reference entity types by the IDs of the package's metatypes. A viewpoint whose type is not in the package, nor in the instance, makes the import fail with 422 (BO BR-08).
- **Starter package:** "Application Landscape" (ArchiMate: Application Component, Application Service, Data Object) and "Technology Landscape" (ArchiMate: Technology Component, Technology Service, System Software, Network Node).
- **Shape assignments** (the package section `shapeAssignments`) stay unsupported.
- **Audit:** the existing `viewpoint.*` events name `notation`, and the package import counts the imported viewpoints in its `metamodel.package-imported`.

**Viewpoints (`metamodel`):**

- `ViewpointConfig` and `ViewpointWrite` get `notation` and `mappings` (`metatypeId`, `notationElement`, `defaultWidth`, `defaultHeight`, `visualHint`). `ViewpointConfig` also gets `viewpointType` and `sourcePackage`.
- `listNotationElements` is new.
- `PackageViewpoint` gets the fields of a viewpoint with metatype IDs, and the import supports it.

**Frontend (SCR-031):**

- The list shows two groups, "System-defined" with a lock icon and "Organization", and the notation of each viewpoint.
- The dialog gets the notation select. The mapping step shows, per selected entity type, an element select filtered by the notation (US-091 AC1), width, height and the visual hint.
- Changing the notation clears incompatible elements (AC3).

## Out of Scope

- Rendering the notation in diagrams (with the diagrams).
- Export of viewpoints and their import outside a package (UC-12 A3, A4).
- Shape assignments.
- The diagram counts (US-090 AC2, US-093 AC1).

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `metamodel/viewpoints`: notation, notation mapping and its checks, the element catalog, system-defined viewpoints, and the extended SCR-031.
- `metamodel/package-import`: the package section `viewpoints` becomes supported.

## Impact

- **api/openapi.yaml:**
  - `notation`, `mappings`, `viewpointType` and `sourcePackage` on the viewpoint schemas;
  - `ViewpointMapping`, `NotationElement` and `listNotationElements`;
  - `PackageViewpoint` extended;
  - the problem detail for read-only viewpoints.
- **backend:**
  - migration `V41__viewpoint_notations.sql`:
    - `notation`, `viewpoint_type` and `source_package` on `viewpoints`;
    - the table `viewpoint_mappings`;
    - existing viewpoints set to `generic`;
  - the element catalog as a resource (`notations/catalog.json`) with a reader;
  - `ViewpointStore` and `Viewpoints` extended;
  - the package import of viewpoints;
  - the starter package with two viewpoints.
- **frontend:**
  - `ViewpointsSection.vue` and `ViewpointDialog.vue` with notation and mapping;
  - `api/metamodel.ts`;
  - the stories of SCR-031.
- **Documentation:**
  - US-091 and US-092 done, and US-090 with its notation;
  - REQ-090 to REQ-092, REQ-059 and UC-12, realigned;
  - `business-objects/viewpoint.md` and `docs/data-model.puml`;
  - `docs/audit-event-catalog.md`;
  - `docs/screens/SCREENS.md`;
  - the user manual "Editing the metamodel" (section "Viewpoints") and "Importing packages", in English and German;
  - the S15 row of the sprint plan.

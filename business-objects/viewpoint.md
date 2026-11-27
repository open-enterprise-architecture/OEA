---
identifier: viewpoint
name_de: Viewpoint / Architektursicht
name_en: Viewpoint
version: 0.6.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Architektursicht
  - Diagrammtyp
  - View Definition
related_capabilities: []
references:
  - concept: concept/20-entities/06-core-entity-types.md
  - concept: concept/30-dynamics/12-domain-views.md
  - concept: concept/70-platform/21-api-architecture.md
---

# Business Object: Viewpoint

## Definition

A `Viewpoint` is a named architecture view that determines which entity types and connection types may appear in a diagram of this type, in which notation they are displayed, and with which default dimensions and presentation types they are rendered. It is the bridge between the semantic metamodel (EntityTypes, connections) and the visual representation.

Every entity type and connection type has a **stable, unique ID** (kebab-case; e.g. `application-component`). Viewpoints reference these IDs – not the display names. This keeps viewpoint definitions consistent during import/export between OEA instances, even if display names are localized or renamed.

## Description

A viewpoint answers three questions:

1. **What may appear in this view?** – Selection from the EntityTypes and connection types defined in the metamodel (referenced via ID)
2. **How is it displayed?** – Choice of a notation: generic, ArchiMate 3, UML, or BPMN 2.0; per EntityType a concrete notation element (e.g. `archimate:ApplicationComponent`, `bpmn:UserTask`), else the generic fallback
3. **In which default size?** – per allowed metatype a default size in pt, which the diagram editor uses when inserting new elements: width and height for an EntityType, a length for a connection type (the distance at which a connected new element is placed from its source); without a stored size an element shape is 90 × 30 pt and a connection 90 pt long

Every instance of an OEA [MetamodelConfiguration](./metamodel-configuration.md) contains a set of viewpoints, analogous to the EntityTypes and stereotypes. Some viewpoints are **system-defined** (installed by a metamodel package, read-only); others are added by the architecture team as **user-defined**.

---

### System-defined Viewpoints

A viewpoint becomes system-defined only through the import of a metamodel package (REQ-059, REQ-092). It records its source package (ID and version), cannot be updated or deleted through the API (422, problem type `read-only-viewpoint`), and is replaced or removed only by a re-import of the same package. A package viewpoint carries a stable UUID as its ID, so a rename in a new package version keeps its identity. The starter package `oea-starter-togaf-classic` (version 1.2.0) installs "Application Landscape" and "Technology Landscape" (`archimate3`).

The following tables show typical viewpoints that packages can ship for the three standard notations:

**ArchiMate 3 (selection)**:

| Name | Allowed Layers | Typical Entity Types |
|---|---|---|
| Business Architecture View | Business | Actor, Role, BusinessProcess, BusinessFunction, BusinessService, Capability |
| Application Architecture View | Application | ApplicationComponent, ApplicationService, ApplicationFunction, Interface |
| Technology Architecture View | Technology | TechnologyComponent, Platform, Node, CommunicationPath |
| Application Cooperation View | Application | ApplicationComponent, ApplicationService, Interface (focus on relationships) |
| Layered View | All | All layers visible; full landscape |

**UML (selection)**:

| Name | Diagram Type | Typical Entity Types |
|---|---|---|
| Component Diagram | Components | ApplicationComponent, Interface, Dependency |
| Class Diagram | Classes/data | DataEntity, LogicalDataComponent, Property |
| Deployment Diagram | Deployment | Node, TechnologyComponent, ApplicationComponent |

**BPMN 2.0 (selection)**:

| Name | Diagram Type | Typical Entity Types |
|---|---|---|
| Process View | Process flow | BusinessProcess, Task, Event, Gateway, SequenceFlow |
| Collaboration View | Collaboration | Pool, Lane, MessageFlow, BusinessProcess |

---

### Notation and Presentation

A viewpoint is assigned to exactly one notation. The notation controls how the elements are rendered visually:

| Notation | Characteristic | Typical Use |
|---|---|---|
| `generic` | Notation-neutral boxes and arrows (`generic:Box`, `generic:Arrow`); default of a new viewpoint | Viewpoints without a standard notation, existing viewpoints of us-090 |
| `archimate3` | Box-based, color-coded TOGAF layers, standardized symbols | Landscape overviews, plateaus, business-IT alignment |
| `uml` | Structure diagrams (component, class, deployment), behavior diagrams (sequence, activity) | Software architecture, interface contracts |
| `bpmn2` | Flow-based, events/tasks/gateways, pool-lane structure | Business processes, workflows, organizational handovers |

---

### Interplay with MetamodelConfiguration

```text
MetamodelConfiguration (scope=instance)
  ├── EntityTypeDefinitions: application-component, data-flow, security-zone, ...
  ├── Stereotypes: saas-application, ...
  ├── ConstraintRules: every-interface-has-owner, ...
  └── Viewpoints:
        ├── [built-in] Application Architecture View (archimate3)
        │     allowedEntityTypes: [application-component, application-service, interface, ...]
        │     allowedConnectionTypes: [data-flow, ...]
        │     notationMappings: [{entityTypeId: application-component, notationElement: archimate:ApplicationComponent}, ...]
        │     sizes: [{metatypeId: application-component, width: 120, height: 45}, ...]
        ├── [built-in] Process View (bpmn2)
        │     allowedEntityTypes: [business-process, task, start-event, ...]
        │     allowedConnectionTypes: [sequence-flow, message-flow, ...]
        └── [user-defined] Cloud Security View (archimate3)
              allowedEntityTypes: [application-component, security-zone, technology-component]
              allowedConnectionTypes: [data-flow]
```

A diagram in OEA always references exactly one viewpoint. Entities and connections that are not in the viewpoint's `allowedEntityTypes`/`allowedConnectionTypes` set cannot be added to this diagram.

---

### Notation Mappings and Default Sizes

For each EntityType there is a **default mapping table** (system-defined) that maps the OEA type to the corresponding notation element; the usual dimensions are recommendations only (see "Default Sizes" below):

| OEA EntityType (ID) | ArchiMate 3 | UML | BPMN 2.0 | Usual W×H (pt) |
|---|---|---|---|---|
| `application-component` | `archimate:ApplicationComponent` | `uml:Component` | – | 120 × 55 |
| `business-process` | `archimate:BusinessProcess` | `uml:Activity` | `bpmn:Process` | 120 × 55 |
| `interface` | `archimate:ApplicationInterface` | `uml:Interface` | – | 100 × 55 |
| `data-flow` (connection) | `archimate:FlowRelationship` | `uml:Dependency` | `bpmn:MessageFlow` | – |
| `sequence-flow` (connection) | `archimate:TriggeringRelationship` | – | `bpmn:SequenceFlow` | – |
| Custom EntityType | `archimate:ApplicationComponent` (fallback) | `uml:Class` (fallback) | `bpmn:Task` (fallback) | 120 × 55 |

User-defined viewpoints can optionally store their own notation mappings for their custom EntityTypes to override the default rendering (e.g. specific ArchiMate symbols for custom types).

As implemented (us-091-viewpoint-notations), a viewpoint stores at most one mapping per allowed metatype. An allowed metatype without a mapping is rendered with the **generic fallback**: `generic:Box` for an element metatype, `generic:Arrow` for a connection metatype, . Reading a viewpoint returns the **effective element** per allowed metatype (the mapping or the fallback, marked as such) together with its **effective default size** (see "Default Sizes").

### Default Sizes

Since `s17-viewpoint-default-sizes` (owner decisions 2026-11-19) the size is independent of the notation mapping and the element catalog. A viewpoint stores per allowed metatype an optional **ViewpointSize** in **pt**: `width` and `height` (1–2000 each, together) for an element metatype, a `length` (1–2000) for a connection metatype. Without a stored size an element shape is **90 × 30 pt** and a connection **90 pt** long, for every notation element. The effective size is returned per allowed metatype together with `sizeStored`. The sizes the element catalog used to carry are no longer defaults. Removing a metatype from the viewpoint removes its size. The migration V45 converted the sizes stored in mappings from canvas units (px at 96 dpi) to pt by `round(px × 0.75)`, at least 1.

## Notation Element Catalog

Valid values for `NotationMapping.notationElement`, organized by notation standard. The prefix determines the standard (`archimate:`, `uml:`, `bpmn:`, `generic:`). The viewpoint may only use elements of its own notation or generic elements.

The binding catalog is shipped with the software as the resource `notations/catalog.json` and read through `listNotationElements`; each entry has a key (at most 100 characters), the notation, a label and the kind (`element` or `relationship`); it carries no sizes. It contains all elements and the eleven relationships of ArchiMate 3.2, the component and class elements of UML, the core elements of BPMN 2.0, and the generic elements. The lists below are the business overview; where they differ, the shipped catalog applies.

### ArchiMate 3 – Elements (prefix `archimate:`)

**Business layer**: `archimate:BusinessActor` · `archimate:BusinessRole` · `archimate:BusinessProcess` · `archimate:BusinessService` · `archimate:BusinessFunction` · `archimate:BusinessInteraction` · `archimate:BusinessEvent` · `archimate:BusinessObject` · `archimate:Contract` · `archimate:Capability` · `archimate:ValueStream`

**Application layer**: `archimate:ApplicationComponent` · `archimate:ApplicationService` · `archimate:ApplicationFunction` · `archimate:ApplicationInterface` · `archimate:ApplicationCollaboration` · `archimate:DataObject`

**Technology layer**: `archimate:TechnologyComponent` · `archimate:TechnologyService` · `archimate:TechnologyFunction` · `archimate:Node` · `archimate:Device` · `archimate:SystemSoftware` · `archimate:CommunicationNetwork` · `archimate:Path` · `archimate:Artifact`

**Relationships (connections)**: `archimate:AssociationRelationship` · `archimate:CompositionRelationship` · `archimate:AggregationRelationship` · `archimate:RealizationRelationship` · `archimate:AssignmentRelationship` · `archimate:ServingRelationship` · `archimate:AccessRelationship` · `archimate:TriggeringRelationship` · `archimate:FlowRelationship` · `archimate:InfluenceRelationship` · `archimate:SpecializationRelationship`

### UML – Elements (prefix `uml:`)

**Structure**: `uml:Class` · `uml:Interface` · `uml:Component` · `uml:Package` · `uml:Node` · `uml:Artifact` · `uml:Collaboration`

**Behavior**: `uml:Actor` · `uml:UseCase` · `uml:Activity` · `uml:State`

**Relationships (connections)**: `uml:Dependency` · `uml:Association` · `uml:Realization` · `uml:Generalization` · `uml:Composition` · `uml:Aggregation`

**Other**: `uml:Note`

### BPMN 2.0 – Elements (prefix `bpmn:`)

**Tasks**: `bpmn:Task` · `bpmn:UserTask` · `bpmn:ServiceTask` · `bpmn:SendTask` · `bpmn:ReceiveTask` · `bpmn:ManualTask` · `bpmn:BusinessRuleTask` · `bpmn:ScriptTask` · `bpmn:SubProcess` · `bpmn:CallActivity`

**Events**: `bpmn:StartEvent` · `bpmn:EndEvent` · `bpmn:IntermediateCatchEvent` · `bpmn:IntermediateThrowEvent` · `bpmn:BoundaryEvent`

**Gateways**: `bpmn:ExclusiveGateway` · `bpmn:InclusiveGateway` · `bpmn:ParallelGateway` · `bpmn:EventBasedGateway`

**Flows (connections)**: `bpmn:SequenceFlow` · `bpmn:MessageFlow`

**Data & structure**: `bpmn:DataObject` · `bpmn:DataStore` · `bpmn:Pool` · `bpmn:Lane` · `bpmn:Group`

### Generic Fallback

`generic:Box` (entity without a specific notation element) · `generic:Arrow` (connection without a specific notation element)

The generic elements fit every notation and are the only elements of the notation `generic`.

---

### Recommended Sizes by Notation Element

These sizes are recommendations for viewpoints that store sizes; they are not applied by the software (the default is 90 × 30 pt, see "Default Sizes").

| Category | Example Elements | Recommendation W×H (pt) |
|---|---|---|
| ArchiMate box (layer elements) | `archimate:ApplicationComponent`, `archimate:BusinessProcess` | 120 × 55 |
| ArchiMate node / device | `archimate:Node`, `archimate:Device` | 120 × 70 |
| UML class / component | `uml:Class`, `uml:Component` | 140 × 80 |
| UML actor | `uml:Actor` | 40 × 60 |
| BPMN task | `bpmn:Task`, `bpmn:UserTask`, `bpmn:ServiceTask` | 100 × 80 |
| BPMN subprocess | `bpmn:SubProcess`, `bpmn:CallActivity` | 140 × 80 |
| BPMN event (circle) | `bpmn:StartEvent`, `bpmn:EndEvent`, `bpmn:IntermediateCatchEvent` | 36 × 36 |
| BPMN gateway (diamond) | `bpmn:ExclusiveGateway`, `bpmn:ParallelGateway` | 50 × 50 |
| BPMN pool | `bpmn:Pool` | 600 × 150 |
| BPMN lane | `bpmn:Lane` | 600 × 120 |

The values are recommendations; every user-defined viewpoint can store its own values per EntityType and connection type as `ViewpointSize`.

---

## Attributes

### ViewpointDefinition

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID; a package viewpoint keeps the stable UUID of its package across renames | Stable identifier |
| name | string | required | | trimmed, 1–255 characters, unique within the instance regardless of case | Display name |
| description | string | optional | | max. 1000 characters | Business description of what this viewpoint is used for |
| notation | enum | required | `generic` | `[generic, archimate3, uml, bpmn2]`; an unknown value is rejected | Notation for the presentation; a viewpoint belongs to exactly one notation |
| viewpointType | enum | required | `user-defined` | `[system-defined, user-defined]`; set only by the package import, never by an API write (BR-10) | `system-defined` = installed by a metamodel package (read-only); `user-defined` = created by the architecture team |
| sourcePackage | PackageRef | optional | null | `{id, version}` of the metamodel package; set exactly when `viewpointType = system-defined` | Origin package of a system-defined viewpoint |
| allowedEntityTypes | reference[] | required | [] | element metatypes of the instance configuration, at most 500 | EntityTypes that may be used in diagrams of this viewpoint |
| allowedConnectionTypes | reference[] | required | [] | connection metatypes (`isConnection=true`) of the instance configuration, at most 500 | Connection types allowed as edges in diagrams of this viewpoint |
| notationMappings | NotationMapping[] | optional | [] | at most one per allowed metatype, at most 1000 (BR-09) | Notation element and visual hint per allowed metatype |
| sizes | ViewpointSize[] | optional | [] | at most one per allowed metatype, at most 1000 (BR-09, BR-13); omitted on a write = none stored | Default size in pt per allowed metatype |
| askNewParent | boolean | optional | null | true, false or null; null means the global setting applies; omitted on a write = null; a package may name it (BR-14) | Whether diagrams of the viewpoint ask "New parent?" when a shape moves into or out of a container |
| effectiveMappings | EffectiveMapping[] | derived | | read only; one entry per allowed metatype | The mapping, or the generic fallback, with the effective size in pt |
| version | integer | required | 1 | >= 1; optimistic locking (`If-Match`) | Change counter |
| createdBy | reference | required | | target: person; for a package viewpoint the importing person | Creating person |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |
| updatedBy | reference | optional | null | target: person; for a package viewpoint the person of the re-import | Last changing person |
| updatedAt | datetime | optional | null | ISO 8601, UTC | Time of the last change |

### NotationMapping (value object)

| Attribute | Type | Optional | Description |
|---|---|---|---|
| entityTypeId | reference | required | ID of the allowed metatype (element or connection) to which the mapping applies (`metatypeId` in the API) |
| notationElement | string | required | Key of an element of the catalog (e.g. `archimate:ApplicationFunction`, `uml:Interface`, `bpmn:SubProcess`), at most 100 characters; prefix must match the viewpoint's `notation` or be `generic:`; an element shape for an element metatype, a relationship shape for a connection metatype |
| visualHint | string | optional | Fill color in the form `#rrggbb` (pattern `^#[0-9a-fA-F]{6}$`); other style overrides are not supported |

### ViewpointSize (value object)

| Attribute | Type | Optional | Description |
|---|---|---|---|
| metatypeId | reference | required | ID of the allowed metatype (element or connection) the size applies to |
| width | integer | optional | Default width in pt, 1–2000; element metatype only, together with `height` |
| height | integer | optional | Default height in pt, 1–2000; element metatype only, together with `width` |
| length | integer | optional | Default length in pt, 1–2000; connection metatype only: the distance at which a connected new element is placed from its source |

### EffectiveMapping (derived value object)

| Attribute | Type | Optional | Description |
|---|---|---|---|
| metatypeId | reference | required | Allowed metatype |
| notationElement | string | required | Mapped element, or `generic:Box` / `generic:Arrow` |
| width | integer | optional | Effective width in pt (element metatype): the stored one, else 90 |
| height | integer | optional | Effective height in pt (element metatype): the stored one, else 30 |
| length | integer | optional | Effective length in pt (connection metatype): the stored one, else 90 |
| sizeStored | boolean | required | true when the viewpoint stores the size, false for the default |
| visualHint | string | optional | Visual hint of the mapping |
| fallback | boolean | required | true when no mapping exists and the generic fallback applies |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| definedIn | [metamodel-configuration](./metamodel-configuration.md) | n:1 | no | Each viewpoint belongs to a MetamodelConfiguration |
| allowsEntityTypes | EntityTypeDefinition (via id) | n:n | no | Which types may appear in this viewpoint |
| allowsConnectionTypes | EntityTypeDefinition (isConnection=true, via id) | n:n | no | Which connection types may appear as edges |
| mapsMetatypes | EntityTypeDefinition (via id) | 1:0..1000 | yes | Notation mappings per allowed metatype; removed with the viewpoint or the metatype |
| sizesMetatypes | EntityTypeDefinition (via id) | 1:0..1000 | yes | Default sizes per allowed metatype; removed with the viewpoint or the metatype |
| installedBy | metamodel package (via id and version) | n:0..1 | yes | Origin package of a system-defined viewpoint |
| usedBy | Diagram[] | 1:n | yes | Diagrams that use this viewpoint |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `ViewpointDefinition.name` MUST be unique within a MetamodelConfiguration | onCreate, onUpdate | – |
| BR-02 | A viewpoint with `viewpointType=system-defined` MUST NOT be updated or deleted through the API (422, `read-only-viewpoint`), even with its current version; only a re-import of its package replaces or removes it | onDelete, onUpdate | – |
| BR-03 | All entries in `allowedEntityTypes` MUST point to valid `EntityTypeDefinition.id` values of the associated MetamodelConfiguration (built-in + custom) | onCreate, onUpdate | – |
| BR-04 | All entries in `allowedConnectionTypes` MUST point to `EntityTypeDefinition.id` values for which `isConnection=true` holds | onCreate, onUpdate | – |
| BR-05 | A diagram MUST only contain entities and connections of the type listed in the linked viewpoint's `allowedEntityTypes`/`allowedConnectionTypes`; a shape or edge whose exact type the viewpoint stops allowing (a viewpoint edit, a package re-import, or the change of the viewpoint of the diagram) stays as a conflict that blocks the save of the diagram until it is removed or the type is allowed again (change us-074-diagram-viewpoint-change; Diagram BR-DG-14) | onDiagramEdit | – |
| BR-06 | A `NotationMapping.entityTypeId` MUST be contained in `allowedEntityTypes` or `allowedConnectionTypes` of this viewpoint | onCreate, onUpdate | – |
| BR-07 | `NotationMapping.notationElement` MUST be an element of the catalog and match the prefix of the viewpoint's `notation` (archimate3 → `archimate:*`, uml → `uml:*`, bpmn2 → `bpmn:*`, generic → none) or be generic (`generic:Box` / `generic:Arrow`); an element metatype MUST map to an element shape, a connection metatype to a relationship shape | onCreate, onUpdate | – |
| BR-08 | When importing a viewpoint, all IDs in `allowedEntityTypes` and `allowedConnectionTypes` MUST be metatypes of the package or of the instance configuration; unknown IDs are reported as validation errors (no partial import) | onImport | – |
| BR-09 | A viewpoint MUST hold at most one mapping per metatype and at most 1000 mappings; a visual hint MUST be of the form `#rrggbb` | onCreate, onUpdate, onImport | – |
| BR-10 | Only the package import MUST make a viewpoint `system-defined` and set its `sourcePackage`; a viewpoint created or updated through the API is always `user-defined` without a package | onCreate, onUpdate | – |
| BR-11 | An allowed metatype without a mapping MUST be rendered as `generic:Box` (element) or `generic:Arrow` (connection) | onRead | – |
| BR-12 | A package viewpoint whose ID or name (regardless of case) belongs to an organization viewpoint or to a viewpoint of another package MUST be rejected as a conflict (409); a re-import MUST only replace or remove viewpoints of the same package | onImport | – |
| BR-13 | A `ViewpointSize` MUST name an allowed metatype once; an element metatype MUST have `width` and `height` and no `length`, a connection metatype a `length` and neither; every value MUST be 1–2000 pt; without a stored size an element metatype MUST get 90 × 30 pt and a connection metatype 90 pt, whatever the notation element | onCreate, onUpdate, onImport, onRead | – |
| BR-14 | `askNewParent` MUST be true, false or null; a write replaces it like the other fields (an omitted value stores null); a system-defined viewpoint gets it from its package (omitted = null) and stays read-only; the effective value of a diagram is the one of its viewpoint, else the global setting `diagram_settings.ask_new_parent` (default true, changed with `viewpoint:manage`, audited as `diagram.settings-updated`) | onCreate, onUpdate, onImport | – |

## Lifecycle

Viewpoints have no standalone status lifecycle. They exist as a configuration element within the MetamodelConfiguration and follow its change tracking (audit log, schemaVersion). A system-defined viewpoint lives with its package: the import creates it, a re-import replaces it, and a re-import without it deletes it. Existing viewpoints created before us-091-viewpoint-notations are `generic` and `user-defined`.

## Examples

**User-defined viewpoint – Cloud Security View (ArchiMate 3)**:

```yaml
viewpoint:
  id: cloud-security-view
  name: Cloud Security View
  notation: archimate3
  viewpointType: user-defined
  description: >
    Shows cloud components and their security zone assignment;
    suitable for security reviews and compliance documentation.
  allowedEntityTypes:
    - application-component
    - technology-component
    - security-zone        # custom EntityType (ID kebab-case)
    - node
  allowedConnectionTypes:
    - data-flow            # custom connection type with protocol/dataClassification
  notationMappings:
    - entityTypeId: security-zone
      notationElement: archimate:TechnologyComponent
      visualHint: "#ffcccc"
    - entityTypeId: application-component
      notationElement: archimate:ApplicationComponent
    - entityTypeId: technology-component
      notationElement: archimate:TechnologyComponent
  sizes:                   # in pt; without a size: 90 x 30 (element) or length 90 (connection)
    - metatypeId: security-zone
      width: 105
      height: 53
    - metatypeId: application-component
      width: 90
      height: 41
    - metatypeId: technology-component
      width: 90
      height: 41
```

**System-defined viewpoint – Application Architecture View (ArchiMate 3)**:

```yaml
viewpoint:
  id: application-architecture-view
  name: Application Architecture View
  notation: archimate3
  viewpointType: system-defined
  description: Standard ArchiMate Application Layer View
  allowedEntityTypes:
    - application-component
    - application-service
    - application-function
    - interface
    - data-entity
  allowedConnectionTypes:
    - data-flow
    - used-by        # built-in connection type: Application → Application
  notationMappings:
    - entityTypeId: application-component
      notationElement: archimate:ApplicationComponent
    - entityTypeId: application-service
      notationElement: archimate:ApplicationService
    - entityTypeId: interface
      notationElement: archimate:ApplicationInterface
  sizes:                   # in pt; without a size: 90 x 30 (element) or length 90 (connection)
    - metatypeId: application-component
      width: 90
      height: 41
    - metatypeId: application-service
      width: 90
      height: 41
    - metatypeId: interface
      width: 75
      height: 41
```

**BPMN viewpoint – Process View**:

```yaml
viewpoint:
  id: process-view
  name: Process View
  notation: bpmn2
  viewpointType: system-defined
  description: Standard BPMN 2.0 process diagram
  allowedEntityTypes:
    - business-process
    - user-task
    - service-task
    - start-event
    - end-event
    - exclusive-gateway
    - parallel-gateway
    - pool
    - lane
  allowedConnectionTypes:
    - sequence-flow
    - message-flow
  notationMappings:
    - entityTypeId: user-task
      notationElement: bpmn:UserTask
    - entityTypeId: start-event
      notationElement: bpmn:StartEvent
    - entityTypeId: end-event
      notationElement: bpmn:EndEvent
    - entityTypeId: exclusive-gateway
      notationElement: bpmn:ExclusiveGateway
    - entityTypeId: pool
      notationElement: bpmn:Pool
  sizes:                   # in pt; without a size: 90 x 30 (element) or length 90 (connection)
    - metatypeId: user-task
      width: 75
      height: 60
    - metatypeId: start-event
      width: 27
      height: 27
    - metatypeId: end-event
      width: 27
      height: 27
    - metatypeId: exclusive-gateway
      width: 38
      height: 38
    - metatypeId: pool
      width: 450
      height: 113
```

## Delimitation

- **NOT** [MetamodelConfiguration](./metamodel-configuration.md): A viewpoint *selects from* the metamodel; it defines no new types. The metamodel is the foundation, the viewpoint is a filter on it.
- **NOT** a diagram: A viewpoint is the definition; a concrete diagram is an instance of this viewpoint with concrete entities and their positions.
- **NOT** a domain view (§12): Domain views structure the model by business criteria (e.g. by domain/bounded context); viewpoints structure the *visual presentation* by notation and allowed types. Both can be combined.

## Usage in Use Cases

- Future: UC for managing viewpoints (creating, editing user-defined viewpoints)
- [UC-04: Configure Metamodel Collaboratively](../requirements/use-cases/UC-04-configure-metamodel.md) – viewpoints are part of the MetamodelConfiguration that UC-04 manages

## Open Questions

- Can a single EntityType have different `notationMappings` in several viewpoints (i.e. ArchiMate box in one view, UML class in another)? → Yes, by design; each viewpoint has its own mapping table.
- How are viewpoints used differently in a client app vs. web portal? Client app: full editing scope incl. drag & drop (ADR-008 accepted: client app = Electron, ADR-009 accepted); web portal: read-only rendering.
- Do BPMN viewpoints need explicit pool/lane configuration, or is the EntityType `pool`/`lane` sufficient? → Open; probably representable via `allowedEntityTypes`.

## Notes

- Change `us-091-viewpoint-notations` (V41): the notation, the viewpoint type and the source package become columns of `viewpoints`, and the notation mappings the table `viewpoint_mappings`, so that system-defined viewpoints can be installed by metamodel packages and protected against changes, and so that a viewpoint states how its types are rendered. The notation `generic` was added as the default, so existing viewpoints keep a valid notation. The migration file did not exist when this description was written; columns and constraints follow design D1 of the change.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.7.0 | 2026-11-26 | Change us-074-diagram-viewpoint-change | BR-05 extended: a type removed from a viewpoint makes the shapes and edges of its diagrams conflicts (computed, no new column); the count of the affected diagrams of a proposed edit (`countViewpointAffectedDiagrams`) |
| 0.6.0 | 2026-11-26 | Change us-074-diagram-nesting | `askNewParent` added: the override of the global setting "Ask for a new parent" per viewpoint (V51), BR-14 |
| 0.5.0 | 2026-11-19 | Change s17-viewpoint-default-sizes | `ViewpointSize` added: a default size in pt per allowed metatype (width and height, or a length for a connection), also without a mapping, defaults 90 × 30 pt and 90 pt; the size left `NotationMapping` and the element catalog; `EffectiveMapping` carries `width`, `height`, `length` and `sizeStored`; BR-09, BR-11 revised, BR-13 added (V45) |
| 0.4.0 | 2026-11-17 | Change us-091-viewpoint-notations | Notation `generic` added as the default; `viewpointType` and `sourcePackage` set only by the package import; notation mappings with sizes 1–2000, visual hint `#rrggbb`, at most one per metatype and 1000 per viewpoint; generic fallback and effective mappings; element catalog shipped as a resource; BR-02, BR-06 to BR-08 revised, BR-09 to BR-12 added (V41) |
| 0.3.0 | 2026-11-17 | Change us-090-viewpoints | Generic viewpoints implemented as the table `viewpoints` with link tables to the allowed element and connection metatypes of the instance (V39), UUID IDs, `version` for optimistic locking, name unique case-insensitively, at least one metatype; `notation`, `viewpointType`, `notationMappings` and the diagram references follow with the diagrams (owner decision 2026-11-17) |
| 0.2.0 | 2026-06-26 | Business Engineer | ID-based references (entityTypeId instead of name); defaultWidth/defaultHeight in NotationMapping; notation element catalog (ArchiMate 3, UML, BPMN 2.0); BR-07/BR-08 (prefix consistency, import validation); import/export basis through REQ-058–060 |
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft; ArchiMate 3, UML, BPMN 2.0 as notations; system-defined + user-defined viewpoints; NotationMapping as optional value object |

---
identifier: viewpoint
name_de: Viewpoint / Architektursicht
name_en: Viewpoint
version: 0.2.0
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
2. **How is it displayed?** – Choice of a notation: ArchiMate 3, UML, or BPMN 2.0; per EntityType a concrete notation element (e.g. `archimate:ApplicationComponent`, `bpmn:UserTask`)
3. **In which default size?** – per EntityType a default width and height in pixels, which the canvas editor uses when inserting new elements

Every instance of an OEA [MetamodelConfiguration](./metamodel-configuration.md) contains a set of viewpoints, analogous to the EntityTypes and stereotypes. Some viewpoints are **system-defined** (built-in, not deletable); others are added by the architecture team as **user-defined**.

---

### Built-in Viewpoints (system-defined)

OEA ships a set of predefined viewpoints from the three supported notations by default:

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
        │     notationMappings: [{entityTypeId: application-component, notationElement: archimate:ApplicationComponent,
        │                          defaultWidth: 120, defaultHeight: 55}, ...]
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

For each EntityType there is a **default mapping table** (system-defined) that maps the OEA type to the corresponding notation element and the usual default dimensions:

| OEA EntityType (ID) | ArchiMate 3 | UML | BPMN 2.0 | Default W×H (px) |
|---|---|---|---|---|
| `application-component` | `archimate:ApplicationComponent` | `uml:Component` | – | 120 × 55 |
| `business-process` | `archimate:BusinessProcess` | `uml:Activity` | `bpmn:Process` | 120 × 55 |
| `interface` | `archimate:ApplicationInterface` | `uml:Interface` | – | 100 × 55 |
| `data-flow` (connection) | `archimate:FlowRelationship` | `uml:Dependency` | `bpmn:MessageFlow` | – |
| `sequence-flow` (connection) | `archimate:TriggeringRelationship` | – | `bpmn:SequenceFlow` | – |
| Custom EntityType | `archimate:ApplicationComponent` (fallback) | `uml:Class` (fallback) | `bpmn:Task` (fallback) | 120 × 55 |

User-defined viewpoints can optionally store their own notation mappings for their custom EntityTypes to override the default rendering (e.g. specific ArchiMate symbols for custom types, deviating default sizes).

## Notation Element Catalog

Valid values for `NotationMapping.notationElement`, organized by notation standard. The prefix determines the standard (`archimate:`, `uml:`, `bpmn:`). The viewpoint may only use elements of its own notation.

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

---

### Recommended Default Sizes by Notation Element

| Category | Example Elements | Recommendation W×H (px) |
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

The values are recommendations; every user-defined viewpoint can store deviating values per EntityType in `NotationMapping.defaultWidth`/`defaultHeight`.

---

## Attributes

### ViewpointDefinition

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | kebab-case, unique within the MetamodelConfiguration | Stable identifier (e.g. `application-architecture-view`) |
| name | string | required | | max. 255 characters, unique within the MetamodelConfiguration | Display name |
| description | string | optional | | max. 1000 characters | Business description of what this viewpoint is used for |
| notation | enum | required | | `[archimate3, uml, bpmn2]` | Notation for the presentation; a viewpoint belongs to exactly one notation |
| viewpointType | enum | required | `user-defined` | `[system-defined, user-defined]` | `system-defined` = built-in (read-only); `user-defined` = created by the architecture team |
| allowedEntityTypes | string[] | required | [] | Values are `EntityTypeDefinition.id` (kebab-case); all IDs must exist in the associated MetamodelConfiguration | EntityTypes that may be used in diagrams of this viewpoint |
| allowedConnectionTypes | string[] | required | [] | Values are `EntityTypeDefinition.id`; all IDs must point to types for which `isConnection=true` holds | Connection types allowed as edges in diagrams of this viewpoint |
| notationMappings | NotationMapping[] | optional | [] | Overrides default mapping per EntityType; recommended for all custom EntityTypes | Notation element, presentation type, and default size per EntityType |
| createdBy | reference | optional | null | target: person; only for `viewpointType=user-defined` | Creating person |
| createdAt | datetime | optional | null | ISO 8601, UTC; only for `viewpointType=user-defined` | Time of creation |

### NotationMapping (value object)

| Attribute | Type | Optional | Description |
|---|---|---|---|
| entityTypeId | string | required | `EntityTypeDefinition.id` (kebab-case) of the OEA type to which the mapping applies |
| notationElement | string | required | Notation-specific element from the catalog (e.g. `archimate:ApplicationFunction`, `uml:Interface`, `bpmn:SubProcess`); prefix must match the viewpoint's `notation` |
| defaultWidth | integer | optional | Default width in pixels that the canvas editor uses on insertion (must be > 0) |
| defaultHeight | integer | optional | Default height in pixels that the canvas editor uses on insertion (must be > 0) |
| visualHint | string | optional | Optional style override (e.g. fill color `#ffcccc`, icon override); format TBD |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| definedIn | [metamodel-configuration](./metamodel-configuration.md) | n:1 | no | Each viewpoint belongs to a MetamodelConfiguration |
| allowsEntityTypes | EntityTypeDefinition (via id) | n:n | no | Which types may appear in this viewpoint |
| allowsConnectionTypes | EntityTypeDefinition (isConnection=true, via id) | n:n | no | Which connection types may appear as edges |
| usedBy | Diagram[] | 1:n | yes | Diagrams that use this viewpoint |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `ViewpointDefinition.name` MUST be unique within a MetamodelConfiguration | onCreate, onUpdate | – |
| BR-02 | A viewpoint with `viewpointType=system-defined` MUST NOT be deleted or changed in `allowedEntityTypes`, `allowedConnectionTypes`, and `notation` | onDelete, onUpdate | – |
| BR-03 | All entries in `allowedEntityTypes` MUST point to valid `EntityTypeDefinition.id` values of the associated MetamodelConfiguration (built-in + custom) | onCreate, onUpdate | – |
| BR-04 | All entries in `allowedConnectionTypes` MUST point to `EntityTypeDefinition.id` values for which `isConnection=true` holds | onCreate, onUpdate | – |
| BR-05 | A diagram MUST only contain entities and connections of the type listed in the linked viewpoint's `allowedEntityTypes`/`allowedConnectionTypes` | onDiagramEdit | – |
| BR-06 | A `NotationMapping.entityTypeId` MUST be contained in `allowedEntityTypes` of this viewpoint | onCreate, onUpdate | – |
| BR-07 | `NotationMapping.notationElement` MUST match the prefix of the viewpoint's `notation` (archimate3 → `archimate:*`, uml → `uml:*`, bpmn2 → `bpmn:*`) or have the value `generic:Box` / `generic:Arrow` | onCreate, onUpdate | – |
| BR-08 | When importing a viewpoint, all IDs in `allowedEntityTypes` and `allowedConnectionTypes` MUST exist in the target MetamodelConfiguration; missing IDs are reported as validation errors (no partial import at type level) | onImport | – |

## Lifecycle

Viewpoints have no standalone status lifecycle. They exist as a configuration element within the MetamodelConfiguration and follow its change tracking (audit log, schemaVersion).

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
      defaultWidth: 140
      defaultHeight: 70
      visualHint: "color=#ffcccc"
    - entityTypeId: application-component
      notationElement: archimate:ApplicationComponent
      defaultWidth: 120
      defaultHeight: 55
    - entityTypeId: technology-component
      notationElement: archimate:TechnologyComponent
      defaultWidth: 120
      defaultHeight: 55
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
      defaultWidth: 120
      defaultHeight: 55
    - entityTypeId: application-service
      notationElement: archimate:ApplicationService
      defaultWidth: 120
      defaultHeight: 55
    - entityTypeId: interface
      notationElement: archimate:ApplicationInterface
      defaultWidth: 100
      defaultHeight: 55
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
      defaultWidth: 100
      defaultHeight: 80
    - entityTypeId: start-event
      notationElement: bpmn:StartEvent
      defaultWidth: 36
      defaultHeight: 36
    - entityTypeId: end-event
      notationElement: bpmn:EndEvent
      defaultWidth: 36
      defaultHeight: 36
    - entityTypeId: exclusive-gateway
      notationElement: bpmn:ExclusiveGateway
      defaultWidth: 50
      defaultHeight: 50
    - entityTypeId: pool
      notationElement: bpmn:Pool
      defaultWidth: 600
      defaultHeight: 150
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

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.2.0 | 2026-06-26 | Business Engineer | ID-based references (entityTypeId instead of name); defaultWidth/defaultHeight in NotationMapping; notation element catalog (ArchiMate 3, UML, BPMN 2.0); BR-07/BR-08 (prefix consistency, import validation); import/export basis through REQ-058–060 |
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft; ArchiMate 3, UML, BPMN 2.0 as notations; system-defined + user-defined viewpoints; NotationMapping as optional value object |

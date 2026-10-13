---
identifier: architecture-entity
name_de: Architektur-Entität
name_en: Architecture Entity
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Entity
  - Architektur-Element
  - Komponente
  - ArchEntity
related_capabilities: []
references:
  - concept: concept/20-entities/06-core-entity-types.md
  - concept: concept/40-extensibility/14-extensibility.md
  - concept: concept/40-extensibility/15-schema-evolution.md
---

# Business Object: ArchitectureEntity

## Definition

An `ArchitectureEntity` is a concrete, named instance of an entity type in the architecture model – e.g. the application "SAP S/4HANA" as an instance of the type `application-component`. Every entity has a **globally unique, sequential integer ID** (starting at 1, human-readable) and a **mandatory reference to its EntityTypeDefinition** (the meta-type). This link is immutable and structurally enforced.

## Description

ArchitectureEntities are the central content objects of every OEA instance. They form the building blocks of the enterprise architecture: applications, services, processes, interfaces, servers, domains, person roles, etc. The concrete type of each entity – i.e. what an entity *is* – is determined exclusively by its `entityTypeId`.

The ID is a **sequential integer** (1, 2, 3, …) that is unique instance-wide. This makes IDs readable and memorable in URLs, API calls, exports, and manual references. Connections (`isConnection=true`) and elements (`isConnection=false`) share the same number space.

**Two kinds of entities:**

| Kind | Characteristic | Example |
|---|---|---|
| **Element** (node) | `EntityType.isConnection=false` | ApplicationComponent "SAP ERP", BusinessProcess "Invoice Approval" |
| **Connection** (edge) | `EntityType.isConnection=true` | DataFlow "ERP→CRM", SequenceFlow "Step 1 → Step 2" |

Connections are full-fledged entities with their own UUID, their own meta-type, and their own properties. They additionally reference their source and target entities.

---

### Meta-Type Binding

The link between `ArchitectureEntity` and its `EntityTypeDefinition` is the structurally most important invariant of the model:

```
EntityTypeDefinition (in the metamodel)     ArchitectureEntity (instance)
─────────────────────────────────────       ────────────────────────────
id:             application-component  ←── entityTypeId: application-component
name:           ApplicationComponent        id:           42
isConnection:   false                       name:         SAP S/4HANA
propertyDefs:   [investitionskosten, ...]   properties:   [{investitionskosten: "450000"}]
architectureLayerId: application-layer      architectureDomainIds: [finance-domain]
```

The `entityTypeId` determines:
- which **properties** the entity can and must have (PropertyDefinitions with validationMode)
- which **notation element** is used (NotationMapping in viewpoints)
- which **default size** applies on the canvas (NotationMapping.defaultWidth / defaultHeight)
- whether it appears as a **node or edge** in the diagram (`isConnection`)
- which **architecture layer** it belongs to (via `architectureLayerId` of the EntityTypeDefinition)

---

### Diagram Reference

When an entity is placed in a diagram (viewpoint instance), it is referenced as a shape on the canvas. The shape's presentation (notation element, size, color) is derived from the viewpoint's `NotationMapping` for `entityTypeId`. The entity itself contains no layout information – that resides in the diagram object.

---

## Property Categories

Every entity has two kinds of properties:

### General Category (system-defined, locked)

The `General` category contains the immutable system fields of every ArchitectureEntity. It is always visible first in the UI. No further properties can be added to the `General` category in the metamodel.

| Field | Display Name | Editable |
|---|---|---|
| `id` | ID | no (system-assigned) |
| `name` | Name | yes |
| `description` | Description | yes |
| `entityTypeId` | Type | no (immutable after creation) |
| `isLogical` | Logical | yes |

### Custom Categories (metamodel-configured)

All further properties are defined in `PropertyGroup` categories, which are configured per EntityType in the metamodel (e.g. "Finance", "Governance", "Technical"). These properties end up in `properties JSONB`. No custom category may use the reserved name `General`.

---

## Attributes

### ArchitectureEntity

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | integer | required | | positive integer value ≥ 1; unique instance-wide; sequential; immutable after creation | **[General]** Primary key; assigned by the system on onCreate as the next free sequence number |
| entityTypeId | string | required | | FK → `EntityTypeDefinition.id`; must exist in the instance's MetamodelConfiguration; immutable after creation | **[General]** Meta-type reference; determines type, properties, and notation |
| name | string | required | | max. 255 characters | **[General]** Display name (e.g. "SAP S/4HANA", "Invoice Approval") |
| description | string | required | `""` | max. 2000 characters; never null | **[General]** Business description; always present, initially empty |
| isLogical | boolean | required | `true` | | **[General]** `true` = logical/conceptual entity (e.g. capability, process, data concept); `false` = physical entity (concrete implementation, e.g. server, database table, application instance). Relevant for plateau presentation (see below) |
| version | integer | required | `1` | ≥ 1; incremented by the system on every update | Optimistic lock counter (ADR-016); the client must send the current version on update |
| stereotypeIds | string[] | optional | `[]` | FK → `StereotypeDefinition.id`; all IDs must exist in the metamodel and be applicable to the `entityTypeId` | Assigned stereotypes (e.g. `saas-application`) |
| properties | PropertyValue[] | optional | `[]` | Keys must correspond to `PropertyDefinition.name` of the `entityTypeId` type; mandatory properties must be filled | Values of the custom properties (non-General) defined by the meta-type |
| sourceEntityId | integer | conditional | null | REQUIRED if `EntityType.isConnection=true`; FK → ArchitectureEntity.id with `isConnection=false`; immutable after creation | Source entity (connections only) |
| targetEntityId | integer | conditional | null | REQUIRED if `EntityType.isConnection=true`; FK → ArchitectureEntity.id with `isConnection=false`; immutable after creation | Target entity (connections only) |
| parentEntityId | integer | optional | null | FK → ArchitectureEntity.id; elements only (`isConnection=false`); no cycles allowed | Parent element in the explorer tree; creates an implicit composition connection if both sides have `isStructuralGrouper=false` (→ ADR-021, REQ-141, REQ-142) |
| isStructuralGrouper | boolean | required | `false` | | `true` = pure navigation aid in the browser panel (not an ArchiMate element); creates no implicit connection; appears with a folder icon instead of an ArchiMate icon |
| autoCreated | boolean | required | `false` | only relevant if `isConnection=true` | `true` = this connection was created automatically by setting `parentEntityId`; is deleted automatically on detaching unless manually edited |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |
| createdBy | reference | required | | target: person | Creating person |
| updatedAt | datetime | optional | null | ISO 8601, UTC | Last change |
| updatedBy | reference | optional | null | target: person | Person who last made a change |

### isLogical — Meaning in the Plateau Context

| Value | Meaning | Plateau Behavior |
|---|---|---|
| `true` (logical) | Conceptual entity (e.g. "CRM capability", "customer master" as a data concept) | Exists across plateau boundaries; lifecycle state changes per plateau |
| `false` (physical) | Concrete implementation (e.g. "SAP CRM 7.0 on server PROD-01", "table customers in PostgreSQL") | Has a clear period of productive existence; typically `active` in baseline, `retired` in target |

`isLogical` can be set on creation and is editable afterwards. The `EntityTypeDefinition` in the metamodel can specify a `defaultIsLogical` value for new instances (e.g. `capability` → `true`; `server` → `false`).

### EntityVersion (value object / snapshot)

A version snapshot is created automatically for every update on an ArchitectureEntity (ADR-016). The snapshot is written **before** the update.

| Attribute | Type | Description |
|---|---|---|
| entityId | integer | FK → ArchitectureEntity.id |
| version | integer | Version at the time of this snapshot |
| name | string | Name before the update |
| description | string | Description before the update |
| isLogical | boolean | Logical flag before the update |
| properties | object | Custom properties before the update (JSONB) |
| changedBy | integer | Who triggered this update |
| changedAt | datetime | Time of the update |

### PropertyValue (value object)

| Attribute | Type | Optional | Constraints | Description |
|---|---|---|---|---|
| propertyName | string | required | must correspond to a `PropertyDefinition.name` of the `entityTypeId` type | Key; references the PropertyDefinition |
| value | string \| null | required | Serialization format: `int` as `"42"`, `enum` as enum value string, `varchar` directly | Value; null = not filled |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| instanceOf | [metamodel-configuration](./metamodel-configuration.md) → EntityTypeDefinition | n:1 | **no** | Each entity belongs to exactly one meta-type; MANDATORY |
| hasStereotypes | [metamodel-configuration](./metamodel-configuration.md) → StereotypeDefinition | n:0..n | yes | Assigned stereotypes |
| source | ArchitectureEntity (isConnection=false) | 1:0..1 | conditional | Source entity (connections only) |
| target | ArchitectureEntity (isConnection=false) | 1:0..1 | conditional | Target entity (connections only) |
| referencedBy | EntityDelta (in [solution](./solution.md)) | 1:0..n | yes | Solutions that change this entity |
| appearsIn | Diagram[] | 1:0..n | yes | Diagrams in which this entity is placed |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | Every ArchitectureEntity MUST have an `entityTypeId` that points to an existing `EntityTypeDefinition` in the instance's MetamodelConfiguration | onCreate | – |
| BR-02 | `id` is a sequential integer ≥ 1; it is assigned by the system on creation as the next free sequence number; elements and connections share the same number space; immutable after creation | onCreate | – |
| BR-03 | `entityTypeId` is immutable after creation; a type change is not possible (create a new entity, delete the old one) | onUpdate | – |
| BR-04 | If `EntityTypeDefinition.isConnection=true`: `sourceEntityId` AND `targetEntityId` MUST be set. `sourceEntityId` may point to a connection instance **only if** its meta-type carries `allowsConnectionAsSource=true` (ADR-010 n-connection); otherwise `sourceEntityId` must point to a non-connection instance. `targetEntityId` must always point to a non-connection instance. | onCreate, onUpdate | ADR-010 |
| BR-05 | If `EntityTypeDefinition.isConnection=false`: `sourceEntityId` and `targetEntityId` MUST be null | onCreate, onUpdate | – |
| BR-06 | All `PropertyValue.propertyName` values MUST correspond to PropertyDefinitions of the `entityTypeId` meta-type; unknown property names are rejected (422) | onCreate, onUpdate | – |
| BR-07 | PropertyDefinitions with `validationMode=mandatory` MUST have a non-empty value; if the value is missing → 422; `validationMode=warning` → save with warning response possible; `validationMode=optional` → always valid | onCreate, onUpdate | – |
| BR-08 | `stereotypeIds` may only reference stereotypes that are defined for the `entityTypeId` meta-type | onCreate, onUpdate | – |
| BR-09 | `sourceEntityId` and `targetEntityId` are immutable after a connection is created; edge direction cannot be changed | onUpdate | – |
| BR-10 | `description` is always present (never null); initially the empty string `""`; max. 2000 characters | onCreate, onUpdate | – |
| BR-11 | `isLogical` is a boolean; default is `true`; can be changed afterwards | onCreate, onUpdate | – |
| BR-12 | On every update to an ArchitectureEntity, the client MUST send the current `version`; if it does not match the stored value → HTTP 409 Conflict; after a successful update, `version` is incremented by 1 (optimistic locking, ADR-016) | onUpdate | ADR-016 |
| BR-13 | Before every update, an EntityVersion snapshot of the previous state MUST be written to `entity_versions`; this snapshot is immutable | onUpdate | ADR-016 |
| BR-14 | No `PropertyGroup` in the metamodel may carry the reserved name `general` (case-insensitive); General fields are system-defined and not extensible | metamodel configuration | – |

## Lifecycle

ArchitectureEntities have no standalone lifecycle status. Their state within the architecture model (active, retiring, realized) is controlled contextually:

- **Project mode**: Status results from the `EntityDelta.deltaType` values of all `implemented` solutions
- **Plateau mode**: Each plateau has a state (`active`, `retiring`, `retired`) for each contained entity; the state is plateau-specific and stored in the `PlateauEntityMembership` (open; cf. architecture.md Open Questions)

## Examples

**Element entity (ApplicationComponent)**:
```yaml
entity:
  id: 1
  entityTypeId: "application-component"
  name: "SAP S/4HANA"
  description: "Central ERP system of the finance department"
  stereotypeIds:
    - "on-premise-application"
  architectureDomainIds:
    - "finance-domain"
    - "hr-domain"
  properties:
    - propertyName: "investitionskosten"
      value: "450000"
    - propertyName: "betreiber"
      value: "IT-Operations"
  sourceEntityId: null
  targetEntityId: null
```

**Connection entity (DataFlow)** – ID 5, links entity 1 to entity 3:
```yaml
entity:
  id: 5
  entityTypeId: "data-flow"
  name: "ERP → CRM customer master sync"
  description: "Nightly synchronization of the customer master"
  properties:
    - propertyName: "protocol"
      value: "REST"
    - propertyName: "dataClassification"
      value: "internal"
  sourceEntityId: 1   # SAP S/4HANA
  targetEntityId: 3   # Salesforce CRM
```

**Entity with mandatory property not filled → warning**:
```yaml
entity:
  id: 7
  entityTypeId: "application-component"
  name: "Legacy Interface"
  properties: []   # investitionskosten (validationMode=mandatory) is missing
# → save possible if validationMode=warning; error if validationMode=mandatory
```

## Delimitation

- **NOT** [EntityTypeDefinition](./metamodel-configuration.md): An EntityTypeDefinition is the type (meta-type); an ArchitectureEntity is the instance. Types define what exists; entities are concrete elements.
- **NOT** an [EntityDelta](./solution.md): An EntityDelta describes *what changes* (new/modified/retiring); the ArchitectureEntity is the *object that changes*.
- **NOT** a diagram shape: A shape is the visual representation of an entity in a specific diagram (incl. position, size override); the entity itself has no position.

## Usage in Use Cases

- [UC-05: Describe Architecture Vision of a Change Initiative](../requirements/use-cases/UC-05-describe-architecture-vision.md) – EntityDeltas create new entities or modify existing ones
- Future: UC for direct entity management (create/update/delete without solution context)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft; integer ID; entityTypeId as mandatory meta-type reference; PropertyValue value object; BR-01–BR-10 |
| 0.2.0 | 2026-06-26 | Business Engineer | BR-04 extended: `sourceEntityId` may point to a connection instance if `allowsConnectionAsSource=true` (ADR-010) |
| 0.3.0 | 2026-06-27 | Business Engineer | Added `isLogical: boolean` (General, default true) (BR-12); `description` as mandatory field with default `""` (BR-11); General category formalized (BR-15); `version` attribute for optimistic locking (BR-13); EntityVersion snapshot mechanism (BR-14; ADR-016) |

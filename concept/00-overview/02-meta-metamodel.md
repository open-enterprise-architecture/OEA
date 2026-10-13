## 2. Meta-Metamodel: The Three Levels

The tool works with three levels of abstraction – identical to the OMG/MOF approach, but deliberately kept lean.

### Level M2 – Meta-Metamodel (the foundation)

Defines the **language** in which schemas are described. Built in, not changed by users.

| Concept | Meaning |
|---|---|
| `EntityType` | Defines a type of architecture object (e.g., "Application", "BusinessService") |
| `RelationType` | Defines a **directed** relationship with a source and a target (e.g., "realizes", "uses", "belongsTo"). Source and target may each be an `EntityType` **or** a `RelationType`. |
| `Property` | Attribute of an EntityType or RelationType with data type and cardinality |
| `Constraint` | Rule (e.g., "an Application must be assigned to exactly one Capability") |
| `Stereotype` | Extension of an existing EntityType without a new type (UML profile principle) |
| `Lifecycle` | State machine that an EntityType can pass through |

### 2.1 Relations as First-Class Objects (Reification)

A central design principle: **relations have identity**. Every relation instance has a URN, can carry properties, is subject to validation, and has a lifecycle per plateau – like an entity. This enables:

- Relations as targets of architecture decisions (e.g., an ADR affects the relationship A→B)
- Waivers on relations (e.g., "exception: this dependency may exist temporarily")
- Versioning of relationships across plateaus (interface exists in 2027, not in 2026)
- Rich metadata on relationships (protocol, frequency, data volume, SLAs)

**Direction**: Every relation is directed (source → target). Symmetric semantics are expressed via the property `symmetric: true` on the RelationType – structurally the relation remains directed, so that query engines and visualizations work uniformly.

**Reification depth**: A RelationType may have a RelationType as source or target ("relationship about a relationship"). To avoid pathological recursion, a maximum reification depth of **2** applies by default. This means: A relation can reference another relation, but cannot be stacked arbitrarily deep. The value is configurable per schema.

**Opt-in character**: Reification is an *opt-in* mechanism. Most relations are trivial edges between two entities and remain so. Reification is used only where relations are independent in business terms – with metadata, governance relevance, or their own lifespan.

**Typical use cases**:

| Case | Example |
|---|---|
| ADR affects relation | `ADR-042 -[affects]-> Relation(Shop -[uses]-> ERP)` – the decision "switch the interface to asynchronous" refers to the connection, not to either of the systems |
| Waiver on relation | `Waiver -[grantedFor]-> Relation(LegacyApp -[uses]-> DeprecatedTech)` – exception for a specific dependency |
| InformationFlow implemented by interface | `Relation(BusinessFunc A -[flows-to]-> BusinessFunc B) -[implementedBy]-> Interface X` |
| Interface with rich properties | Protocol, frequency, data volume, encryption as relation properties |
| Compliance deviation on relation | `ComplianceDeviation -[deviatesFrom]-> Standard`, but concerns the relation "A sends plaintext to B" |

**API impact**: Simple relations remain exposed as sub-resources of entities (`/entities/{urn}/relations`). Relations with their own business meaning are additionally directly addressable (`/relations/{urn}`). Details in [§21 (API Architecture)](../70-platform/21-api-architecture.md).

**Query impact**: Graph traversals do not distinguish between "edge" and "node role of a relation" in the API – both appear as normal graph elements. The complexity is offloaded to the persistence layer (see [§22 (Analyzability)](../70-platform/22-analyzability.md)).

### Level M1 – Schemas (the configurable layer)

This is where concrete modeling languages are defined as schemas:

- **Schema `togaf-content-metamodel`** – defines Actor, Role, BusinessService, ApplicationComponent, Technology, etc.
- **Schema `archimate-3.2`** – defines the ArchiMate concepts and layers
- **Schema `arc42`** – defines building blocks, runtime scenarios, solution strategies, cross-cutting concepts
- **Schema `bpmn-2.0`** – defines Process, Task, Lane, DataObject, MessageFlow, etc.
- **Schema `custom-<org>`** – organization-specific extensions

Schemas can **reference and extend** each other (e.g., Arc42 "Building Block" inherits from ArchiMate "ApplicationComponent" with additional properties).

### Level M0 – Instances (the repository)

The concrete model data: "Application SAP S/4HANA", "Business process invoicing", "Interface OrderAPI between shop and ERP".

---

← [Positioning and Objectives](01-positioning.md) · [🏠 Overview](../README.md) · [Framework Relationship](../10-foundations/03-framework-relationship.md) →

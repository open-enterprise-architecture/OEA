---
identifier: tree-node
name_de: Navigationsbaum-Knoten
name_en: Tree Node
version: 0.1.0
status: draft
maturity: initial
owner_role: Lead Enterprise Architect
aliases:
  - Ordner
  - Navigationseintrag
  - Architektur-Navigator
related_capabilities: []
references:
  - concept: concept/20-entities/06-core-entity-types.md
---

# Business Object: TreeNode

## Definition

A `TreeNode` is a folder in the hierarchical navigation tree of an OEA instance. It can contain subfolders (further TreeNodes) and references to repository content (entities, diagrams, catalogs). The tree serves exclusively navigation and organization; it does not affect the storage or semantics of the referenced objects.

## Description

The architecture repository of an OEA instance can grow quickly: hundreds of entities, many diagrams for different viewpoints, dozens of catalogs. The navigation tree gives the enterprise architect control to present this content in a business-meaningful hierarchy – without changing the internal object identities or their semantic linking.

```
Architecture Repository (Root)
├── Business Architecture
│   ├── [Catalog] Business Capabilities
│   ├── [Catalog] Business Processes
│   └── [Diagram] Business Architecture Overview
├── Application Landscape
│   ├── [Catalog] Application Inventory
│   ├── [Catalog] Interface Overview
│   └── [Diagram] Application Cooperation
├── Technology Infrastructure
│   └── [Diagram] Deployment Architecture
├── Architecture Decisions
│   ├── [Entity] ADR-006 (Auth Stack)
│   └── [Entity] ADR-007 (Canvas Library)
└── Solutions
    ├── ERP Extension Logistics
    └── CRM Replacement
```

---

### Content Types of a TreeNode

A TreeNode can contain entries of four types:

| Type | Description | Example |
|---|---|---|
| `tree-node` | Subfolder | "Business Architecture" |
| `entity` | Reference to an entity in the repository | ApplicationComponent "CRM-System" |
| `diagram` | Reference to a diagram (instance of a viewpoint) | "Application Cooperation View – current" |
| `catalog` | Reference to a [Catalog](./catalog.md) | "Application Inventory" |

---

### Soft-Reference Principle

A repository object (entity, diagram, catalog) can be referenced in **several** TreeNodes at the same time. The TreeNode does not own the object; it merely points to it. Deleting a TreeNode entry does not delete the object itself – it only removes the navigation reference.

Analogy: TreeNodes behave like bookmarks or shortcuts, not like file system folders with ownership.

---

### Root Node

Every OEA instance has exactly **one root node** (`parentId = null`). All other TreeNodes are descendants of this root node. The root node is created automatically when an instance is created and cannot be deleted; its name can be changed (default: name of the OEA instance).

---

### Relationship to Other Presentation Forms

| Presentation Form | Purpose | Primary Object |
|---|---|---|
| Navigation tree (TreeNode) | Organization and navigation of all repository content | TreeNode |
| Diagram | Graphical visualization (viewpoint-based) | Diagram + [Viewpoint](./viewpoint.md) |
| Catalog | Tabular query with joins and filters | [Catalog](./catalog.md) |

All three views show the same data set; they are complementary, not competing.

## Attributes

### TreeNode (folder)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4, globally unique | Internal primary key |
| name | string | required | | max. 255 characters, unique within the same parent node | Readable name of the folder |
| description | string | optional | | max. 500 characters | Short description of the folder content |
| parentId | reference | optional | null | target: TreeNode; null = root node | Parent node; null only for the instance root node |
| sortOrder | integer | required | 0 | >= 0 | Order within the parent node |
| items | TreeNodeItem[] | required | [] | | Ordered list of references in this node |
| instanceId | reference | required | | target: OEA instance | Associated OEA instance |
| createdBy | reference | required | | target: person | Creating person |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |

### TreeNodeItem (reference to repository content)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4 | Internal key of the entry |
| itemType | enum | required | | `[tree-node, entity, diagram, catalog]` | Type of the referenced object |
| referenceId | string | required | | UUID of the referenced object | Reference to entity / diagram / catalog / subfolder |
| displayLabel | string | optional | = name of the object | max. 255 characters | Optional display text in the tree (overrides the name of the original) |
| sortOrder | integer | required | 0 | >= 0 | Order within the parent TreeNode |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| parent | TreeNode | n:0..1 | yes | Parent node (null = root) |
| children | TreeNode | 1:n | yes | Subfolders of this node |
| refersToEntities | entity | n:n | yes | Entities to which this node refers |
| refersTodiagrams | diagram (future BO) | n:n | yes | Diagrams to which this node refers |
| refersToCatalogs | [catalog](./catalog.md) | n:n | yes | Catalogs to which this node refers |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `TreeNode.name` MUST be unique among all direct children of the same `parentId` | onCreate, onUpdate | – |
| BR-02 | The root node (`parentId = null`) MUST NOT be deleted | onDelete | – |
| BR-03 | A `TreeNode` MUST NOT be its own ancestor (no cycles) | onCreate, onUpdate | – |
| BR-04 | A `TreeNodeItem.referenceId` MUST point to an existing object of the specified `itemType` | onCreate; verifiable via consistency check | – |
| BR-05 | Deleting a `TreeNode` MUST recursively remove all its child nodes and their items; referenced objects (entities, diagrams, catalogs) are NOT deleted | onDelete | – |
| BR-06 | A `TreeNodeItem` can reference the same object (`referenceId`) in several different TreeNodes (soft-reference principle) | – | – |

## Lifecycle

TreeNodes have no formal status lifecycle. They exist as long as the OEA instance is active. Individual items can be added, renamed (displayLabel), reordered, and removed. The root node is created automatically during instance setup.

## Examples

**Structure for an SME (project mode)**:
```
Root: "Architecture Repository Sample Company AG"
├── Business Architecture
│   └── [Catalog] Business Capabilities (capabilities)
├── IT Landscape
│   ├── [Catalog] Application Inventory
│   ├── [Catalog] Interface Overview (DataFlow → Interface)
│   └── [Diagram] Layered View (archimate3)
└── Initiatives
    ├── ERP Extension 2026
    │   └── [Entity] Solution: ERP Extension 2026
    └── CRM Replacement
        └── [Entity] Solution: CRM Replacement
```

**Item that appears multiple times in the tree**:
```
[Catalog] "Application Inventory" appears in:
  1. IT Landscape/Application Inventory  ← primary location
  2. Compliance/ISO 27001 View/System Inventory  ← cross-reference
  (the catalog object exists once; two TreeNodeItems point to it)
```

## Delimitation

- **NOT** archive storage: TreeNodes store no data; they are pure navigation. Deleting a TreeNode does not delete the referenced objects.
- **NOT** access control: Permissions are not granted via TreeNodes (separate concept, still open).
- **NOT** [Viewpoint](./viewpoint.md): A viewpoint defines which types are allowed in a diagram; a TreeNode organizes diagrams, catalogs, and entities for navigation.

## Usage in Use Cases

- Future: UC for managing the navigation tree (primary actor: SH-03 Kurt)
- Can serve as the default entry point of the client app and web portal (ADR-008 pending)

## Open Questions

- Should there be several named trees per instance (e.g. "EA team tree" vs. "stakeholder publication")? → Open; currently one tree per instance.
- Should the tree be visible in a role-based manner (certain folders only for the EA team)? → Depends on the permission concept (still open).
- Can solutions be represented as TreeNodes (subfolder structure for solution content)? → Yes, a solution could be modeled as a folder + its EntityDeltas as items. Details TBD.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft; soft-reference principle, root node, four item types (entity/diagram/catalog/tree-node) |

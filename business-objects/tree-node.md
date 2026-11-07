---
identifier: tree-node
name_de: Navigationsbaum-Knoten
name_en: Tree Node
version: 0.2.0
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

```text
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

Every OEA instance has exactly **one root node** (`parentId = null`). All other TreeNodes are descendants of this root node. The root node "Repository" is created by the database migration of the navigation tree (V21) without a creator and cannot be deleted; its name can be changed.

---

### Visibility of Entries

Every reader with `entity:read` sees the whole tree. An entry on a personal [catalog](./catalog.md) is shown only to the creator of that catalog, and an entry whose catalog no longer exists is left out, so the tree never leads to an object the reader cannot open. The decision is made on every read, so the tree follows the catalog's current scope. Changing the tree needs `viewpoint:manage` (ADR-032).

---

### Limits

At most 10 levels below the root, at most 500 folders per instance and at most 200 entries per folder. Folders and entries are sorted by name regardless of case (entries by their shown label) until manual ordering follows (REQ-096); `sortOrder` stays 0.

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
| name | string | required | | trimmed, 1–255 characters, no control characters; unique within the same parent node regardless of case (BR-01) | Readable name of the folder |
| description | string | optional | | max. 500 characters | Short description of the folder content |
| parentId | reference | optional | null | target: TreeNode; null = root node; at most 10 levels below the root | Parent node; null only for the instance root node |
| sortOrder | integer | required | 0 | >= 0 | Order within the parent node |
| items | TreeNodeItem[] | required | [] | | Ordered list of references in this node |
| instanceId | reference | required | | target: OEA instance | Associated OEA instance |
| createdBy | reference | required | | target: person; none for the root | Creating person |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |

### TreeNodeItem (reference to repository content)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4 | Internal key of the entry |
| itemType | enum | required | | `[tree-node, entity, diagram, catalog]`; implemented: `catalog` | Type of the referenced object |
| referenceId | string | required | | UUID of the referenced object | Reference to entity / diagram / catalog / subfolder |
| displayLabel | string | optional | = name of the object | trimmed, 1–255 characters | Optional display text in the tree (overrides the name of the original) |
| sortOrder | integer | required | 0 | >= 0 | Order within the parent TreeNode |
| createdBy | reference | required | | target: person | Person who placed the object |
| createdAt | datetime | required | | ISO 8601, UTC | Time of placement |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| parent | TreeNode | n:0..1 | yes | Parent node (null = root) |
| children | TreeNode | 1:n | yes | Subfolders of this node |
| refersToEntities | entity | n:n | yes | Entities to which this node refers |
| refersTodiagrams | [diagram](./diagram.md) | n:n | yes | Diagrams to which this node refers |
| refersToCatalogs | [catalog](./catalog.md) | n:n | yes | Catalogs to which this node refers |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `TreeNode.name` MUST be unique among all direct children of the same `parentId`, regardless of case | onCreate, onUpdate | – |
| BR-02 | The root node (`parentId = null`) MUST NOT be deleted | onDelete | – |
| BR-03 | A `TreeNode` MUST NOT be its own ancestor (no cycles) | onCreate, onUpdate | – |
| BR-04 | A `TreeNodeItem.referenceId` MUST point to an existing object of the specified `itemType` that the placing person can see | onCreate; verifiable via consistency check | – |
| BR-05 | Deleting a `TreeNode` MUST recursively remove all its child nodes and their items; referenced objects (entities, diagrams, catalogs) are NOT deleted | onDelete | – |
| BR-06 | A `TreeNodeItem` can reference the same object (`referenceId`) in several different TreeNodes (soft-reference principle), but at most once per TreeNode | onCreate | – |

## Lifecycle

TreeNodes have no formal status lifecycle. They exist as long as the OEA instance is active. Individual items can be added, renamed (displayLabel), reordered, and removed. The root node is created automatically during instance setup.

## Examples

**Structure for an SME (project mode)**:

```text
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

```text
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

- [UC-13: Manage Navigation Tree](../requirements/use-cases/UC-13-manage-navigation-tree.md) (primary actor: SH-03 Kurt); [UC-06](../requirements/use-cases/UC-06-create-and-use-catalog.md) step 14 (place a catalog)
- Implemented for catalogs with US-054 (change `us-054-catalog-navigation-tree`): the sidebar of the web portal (SCR-055)
- Can serve as the default entry point of the client app and web portal (ADR-008 pending)

## Open Questions

- Should there be several named trees per instance (e.g. "EA team tree" vs. "stakeholder publication")? → Open; currently one tree per instance.
- Should the tree be visible in a role-based manner (certain folders only for the EA team)? → Not yet: every reader sees all folders; only entries on other persons' personal catalogs are hidden (owner decision 2026-11-06).
- Can solutions be represented as TreeNodes (subfolder structure for solution content)? → Yes, a solution could be modeled as a folder + its EntityDeltas as items. Details TBD.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft; soft-reference principle, root node, four item types (entity/diagram/catalog/tree-node) |
| 0.2.0 | 2026-11-07 | Solution Architect | US-054: names unique regardless of case, root "Repository" created by the migration, visibility of personal catalogs, limits, one entry per target and folder, `catalog` implemented |

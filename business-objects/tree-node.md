---
identifier: tree-node
name_de: Navigationsbaum-Knoten
name_en: Tree Node
version: 0.3.0
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

A `TreeNode` is a folder in the hierarchical navigation tree of an OEA instance. It can contain subfolders (further TreeNodes) and references to repository content (entities, solutions, diagrams, catalogs). The tree serves exclusively navigation and organization; it does not affect the storage or semantics of the referenced objects.

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

A TreeNode can contain entries of five types:

| Type | Description | Example | Status |
|---|---|---|---|
| `tree-node` | Subfolder | "Business Architecture" | planned as an entry; subfolders are TreeNodes with `parentId` |
| `entity` | Reference to an element entity in the repository (no connection) | ApplicationComponent "CRM-System" | implemented (US-095) |
| `solution` | Reference to a [Solution](./solution.md) | "CRM Replacement" | implemented (US-095) |
| `diagram` | Reference to a diagram (instance of a viewpoint) | "Application Cooperation View – current" | planned |
| `catalog` | Reference to a [Catalog](./catalog.md) | "Application Inventory" | implemented (US-054) |

An `entity` entry references the entity by its integer ID (`entityId`); every other type references its target by UUID (`referenceId`). Exactly one of both is set, matching the item type (BR-07).

---

### Deleted Entities in the Tree

Entities are only soft-deleted (ADR-019). An `entity` entry whose entity is deleted stays in every folder that holds it and is marked as deleted; removing it from one folder removes only that entry. When the entity is restored, the marker disappears. The entry of a deleted entity cannot be created anew: placing requires an element entity that is not deleted (BR-04).

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

At most 10 levels below the root, at most 500 folders per instance and at most 200 entries of all item types per folder (BR-08, BR-09). A move position is 0 to 10000.

---

### Manual Order

Folders and entries are shown in their manual order (`sortOrder`, REQ-096), with the name as a tie-breaker. A new folder is appended after its siblings and a new entry after the entries of its folder; the order of the others does not change. Moving a folder or an entry places it at a 0-based position and renumbers the siblings densely (0 … n-1); a position past the end places it last. The position of an entry counts the entries the caller sees: entries hidden from the caller (another person's personal catalogs) keep their order relative to the others. The migration of the manual order (V40) numbers existing folders per parent and entries per folder by their current name, regardless of case, so the visible order does not change.

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
| sortOrder | integer | required | 0 | >= 0; dense (0 … n-1) among siblings after every move; a new folder gets `max + 1` | Manual order within the parent node (REQ-096) |
| items | TreeNodeItem[] | required | [] | at most 200 per node (BR-09) | Ordered list of references in this node |
| instanceId | reference | required | | target: OEA instance | Associated OEA instance |
| createdBy | reference | required | | target: person; none for the root | Creating person |
| createdAt | datetime | required | | ISO 8601, UTC | Time of creation |

### TreeNodeItem (reference to repository content)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string | required | | UUID v4 | Internal key of the entry |
| itemType | enum | required | | `[tree-node, entity, solution, diagram, catalog]`; placeable: `catalog`, `entity`, `solution` | Type of the referenced object |
| referenceId | string | optional | null | UUID of the referenced object; set exactly when `itemType` is not `entity` (BR-07); soft reference without foreign key | Reference to catalog / solution / diagram / subfolder |
| entityId | integer | optional | null | ID of an element entity; set exactly when `itemType = entity` (BR-07); foreign key to the entity (entities are only soft-deleted, so the key never blocks) | Reference to an entity |
| deleted | boolean | derived | false | read only; `entity` entries only: true while the entity is soft-deleted | Marker of a deleted entity in the tree |
| displayLabel | string | optional | = name of the object | trimmed, 1–255 characters | Optional display text in the tree (overrides the name of the original) |
| sortOrder | integer | required | 0 | >= 0; dense (0 … n-1) within the folder after every move; a new entry gets `max + 1` | Manual order within the parent TreeNode (REQ-096) |
| createdBy | reference | required | | target: person | Person who placed the object |
| createdAt | datetime | required | | ISO 8601, UTC | Time of placement |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| parent | TreeNode | n:0..1 | yes | Parent node (null = root) |
| children | TreeNode | 1:n | yes | Subfolders of this node |
| refersToEntities | entity | n:n | yes | Element entities to which this node refers (via `entityId`) |
| refersToSolutions | [solution](./solution.md) | n:n | yes | Solutions to which this node refers (via `referenceId`) |
| refersTodiagrams | [diagram](./diagram.md) | n:n | yes | Diagrams to which this node refers |
| refersToCatalogs | [catalog](./catalog.md) | n:n | yes | Catalogs to which this node refers |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | `TreeNode.name` MUST be unique among all direct children of the same `parentId`, regardless of case | onCreate, onUpdate | – |
| BR-02 | The root node (`parentId = null`) MUST NOT be deleted | onDelete | – |
| BR-03 | A `TreeNode` MUST NOT be its own ancestor (no cycles): a folder MUST NOT be moved under itself or one of its descendants, and the root MUST NOT be moved | onCreate, onMove | – |
| BR-04 | A `TreeNodeItem` MUST point to an existing object of the specified `itemType` that the placing person can see; an `entity` entry MUST point to an element entity (no connection) that is not deleted at placing time | onCreate; verifiable via consistency check | – |
| BR-05 | Deleting a `TreeNode` MUST recursively remove all its child nodes and their items; referenced objects (entities, solutions, diagrams, catalogs) are NOT deleted | onDelete | – |
| BR-06 | A `TreeNodeItem` can reference the same object (`referenceId` or `entityId`) in several different TreeNodes (soft-reference principle), but at most once per TreeNode, also when moved into another TreeNode | onCreate, onMove | – |
| BR-07 | `entityId` MUST be set exactly when `itemType = entity`, and `referenceId` exactly for every other item type | onCreate | – |
| BR-08 | A folder MUST NOT be created or moved so that it or its deepest subfolder lies more than 10 levels below the root | onCreate, onMove | – |
| BR-09 | A `TreeNode` MUST hold at most 200 entries of all item types; reordering within a full folder is allowed | onCreate, onMove | – |
| BR-10 | An `entity` entry of a soft-deleted entity MUST stay in the tree and be marked as deleted until it is removed; the marker MUST disappear when the entity is restored | onEntityDelete, onEntityRestore, onRead | – |
| BR-11 | New folders and entries MUST be appended at the end of their siblings; moves MUST leave the siblings in a dense order (0 … n-1) | onCreate, onMove | – |

## Lifecycle

TreeNodes have no formal status lifecycle. They exist as long as the OEA instance is active. Folders can be created, renamed, moved and deleted; individual items can be added, reordered, moved to another folder and removed. The root node is created by the migration of the navigation tree (V21) and cannot be moved or deleted.

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
- Implemented for catalogs with US-054 (change `us-054-catalog-navigation-tree`, commit e44b736 (#197)): the sidebar of the web portal (SCR-055)
- Entities, solutions, manual order and moving with US-094 to US-097 (change `us-095-navigation-tree-items`): drag and drop and move actions in SCR-055, "Place in navigation" on SCR-020 and SCR-061
- Can serve as the default entry point of the client app and web portal (ADR-008 pending)

## Open Questions

- Should there be several named trees per instance (e.g. "EA team tree" vs. "stakeholder publication")? → Open; currently one tree per instance.
- Should the tree be visible in a role-based manner (certain folders only for the EA team)? → Not yet: every reader sees all folders; only entries on other persons' personal catalogs are hidden (owner decision 2026-11-06).
- Can solutions be represented as TreeNodes (subfolder structure for solution content)? → Partly answered: a solution is placed as an entry of type `solution` (us-095-navigation-tree-items); a folder structure for its EntityDeltas is not planned.

## Notes

- Change `us-095-navigation-tree-items` (V40): entities have `INTEGER` IDs, so `reference_id UUID` cannot hold them. `tree_node_items` therefore gets a nullable `reference_id` and a new `entity_id` with a foreign key to `entities`, the item type `solution`, and a check that exactly the field fitting the item type is set. The manual order replaces the order by name, and the limits of depth and entries per folder now also apply to moves. The migration file did not exist when this description was written; columns and constraints follow design D1 of the change.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Business Engineer | Initial draft; soft-reference principle, root node, four item types (entity/diagram/catalog/tree-node) |
| 0.2.0 | 2026-11-07 | Solution Architect | US-054: names unique regardless of case, root "Repository" created by the migration, visibility of personal catalogs, limits, one entry per target and folder, `catalog` implemented |
| 0.3.0 | 2026-11-17 | Change us-095-navigation-tree-items | Item types `entity` (via `entityId`) and `solution` implemented, `referenceId` nullable, marker of deleted entities, manual order, moving folders and entries; BR-03 extended to moves, BR-07 to BR-11 added (V40) |

---
identifier: diagram
name_de: Diagramm
name_en: Diagram
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Architekturdiagramm
  - View (instance)
  - Sicht
related_capabilities: []
references:
  - concept: concept/30-dynamics/12-domain-views.md
  - concept: concept/70-platform/21-api-architecture.md
---

# Business Object: Diagram

## Definition

A `Diagram` is a stored graphical view of a part of the architecture repository: an instance of a [Viewpoint](./viewpoint.md) that places entities and connections on a canvas with position and size, plus elements that belong only to the diagram, such as [sticky notes](./diagram-note.md).

## Description

The viewpoint defines *what may* appear and *how* it is drawn; the diagram is *one concrete drawing* – for example "Application Cooperation View – current" or "CRM migration target state". The diagram does not own the entities it shows: removing a shape from a diagram leaves the entity in the repository, and an entity can appear in many diagrams. What the diagram owns is its **layout** (positions, sizes, bend points, z-order) and its **diagram-only elements** (sticky notes, groups without semantics).

Diagrams are edited by several people at the same time (§21.10): their working state is merged automatically and turned into versions of the diagram layout and of the entities and connections changed through it. Comment threads ([CommentThread](./comment-thread.md)) discuss a diagram without being part of it.

Until this object was modeled, the [TreeNode](./tree-node.md) referred to diagrams as a "future BO"; the canvas requirements (REQ-040, REQ-063, REQ-072) describe its editing.

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string (UUID) | required | generated | immutable | Stable identity |
| name | string | required | – | 1–200 characters; unique within the instance, case-insensitive | Display name |
| description | string | optional | – | max 2000 characters | Purpose of the diagram |
| viewpointId | reference | required | – | target: viewpoint | Viewpoint the diagram is an instance of |
| plateauId | reference | optional | – | target: plateau | Plateau (time slice) the diagram shows; empty = current state |
| layout | composite | required | empty | see DiagramLayout | Positions and sizes of the shapes |
| version | integer | required | 1 | increases with every saved change | Version of the diagram layout |
| createdBy | reference | required | – | target: person | Creator |
| createdAt | datetime | required | now | immutable | Creation time |
| updatedAt | datetime | required | now | – | Time of the last saved change |

### DiagramLayout (composite)

| Attribute | Type | Optional | Constraints | Description |
|---|---|---|---|---|
| shapes | list of DiagramShape | required | – | Placed entities and diagram-only elements |
| edges | list of DiagramEdge | required | – | Drawn connections with bend points |

### DiagramShape (composite)

| Attribute | Type | Optional | Constraints | Description |
|---|---|---|---|---|
| shapeId | string (UUID) | required | unique within the diagram | Identity of the shape (anchor for comment threads) |
| kind | enum | required | `[entity, note, group]` | Entity shape, sticky note or visual group |
| entityId | reference | optional | target: entity; required for `kind = entity` | Represented entity |
| noteId | reference | optional | target: diagram-note; required for `kind = note` | Represented sticky note |
| x, y, width, height | decimal | required | width, height > 0 | Geometry in canvas units |
| zIndex | integer | required | – | Drawing order |

### DiagramEdge (composite)

| Attribute | Type | Optional | Constraints | Description |
|---|---|---|---|---|
| edgeId | string (UUID) | required | unique within the diagram | Identity of the edge |
| connectionId | reference | required | target: entity (connection) | Represented connection |
| points | list of (x, y) | optional | – | Bend points |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| instanceOf | [viewpoint](./viewpoint.md) | n:1 | no | Viewpoint that defines allowed types and notation |
| shows | [entity](./entity.md) | n:m | yes | Entities and connections placed on the canvas |
| contains | [diagram-note](./diagram-note.md) | 1:n | yes | Sticky notes of the diagram |
| discussedIn | [comment-thread](./comment-thread.md) | 1:n | yes | Comment threads anchored to the diagram |
| showsPlateau | [plateau](./plateau.md) | n:1 | yes | Time slice shown |
| referencedBy | [tree-node](./tree-node.md) | n:m | yes | Navigation folders that list the diagram |

## Lifecycle

```text
active → deleted (soft delete, restorable)
```

- `active`: visible and editable for persons with the permissions of the viewpoint's entity types
- `deleted`: hidden; its notes and comment threads are hidden with it; shown entities are not affected

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-DG-01 | A shape of `kind = entity` MUST reference an entity whose type the viewpoint allows | onUpdate | – |
| BR-DG-02 | Removing a shape MUST NOT delete the entity; deleting an entity removes its shapes from all diagrams | onUpdate, onEntityDelete | – |
| BR-DG-03 | Concurrent edits of the same diagram MUST be merged without losing any participant's change (REQ-199) | onUpdate | – |
| BR-DG-04 | A participant MUST NOT receive properties of shown entities that are hidden from them (property-level authorization, §21.8) | onRead, onCollaborate | REQ-126 |

## Examples

**Example 1**: Application cooperation view

```yaml
name: "Application Cooperation View – current"
viewpointId: archimate-application-cooperation
layout:
  shapes:
    - { shapeId: s1, kind: entity, entityId: crm-system, x: 120, y: 80, width: 160, height: 60, zIndex: 1 }
    - { shapeId: s2, kind: note, noteId: n1, x: 320, y: 40, width: 200, height: 120, zIndex: 2 }
  edges:
    - { edgeId: e1, connectionId: crm-to-erp, points: [] }
```

## Distinction from Similar Objects

- **NOT** [Viewpoint](./viewpoint.md): the viewpoint is the type (allowed elements, notation); the diagram is one drawing of that type
- **NOT** [Catalog](./catalog.md): a catalog is a tabular query, a diagram a drawn selection with layout
- **NOT** a rendered export (PNG, ArchiMate exchange file): exports are derived from the diagram

## Usage in Use Cases

- [UC-24: Collaborate in real time](../requirements/use-cases/UC-24-collaborate-in-real-time.md)
- [UC-10: Model business processes](../requirements/use-cases/UC-10-model-business-processes.md)
- [UC-12: Manage viewpoints](../requirements/use-cases/UC-12-manage-viewpoints.md)

## Concept Reference

- [§12.3 View definition](../concept/30-dynamics/12-domain-views.md)
- [§21.10 Real-time collaboration](../concept/70-platform/21-api-architecture.md)

## Notes

Modeled on 2026-10-28 because sticky notes and comment threads (UC-24) need an owner. The layout is kept as a composite of the diagram; whether it is stored as rows or as a document (JSONB, CRDT state) is decided with ADR-047 and the implementing change.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | Business Engineer | Initial draft (UC-24) |

---
identifier: diagram-note
name_de: Diagrammnotiz (Haftnotiz)
name_en: Diagram Note
version: 0.1.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Sticky Note
  - Haftnotiz
  - Annotation
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
---

# Business Object: Diagram Note

## Definition

A `DiagramNote` is a free text note placed on the canvas of a [Diagram](./diagram.md) – a sticky note that everyone who sees the diagram sees. It belongs to the diagram and is versioned with it, but it is not an entity of the architecture model.

## Description

Architects use sticky notes in workshops and reviews to record assumptions, open points or explanations directly where they apply: "Interface to be replaced in 2027", "Owner unclear". A note has a position and size on the canvas, a short formatted text and a colour. It can be connected to shapes visually (a line without semantics) but creates no connection in the repository.

Unlike a [CommentThread](./comment-thread.md), a note is *content of the diagram*: it appears in exports and printouts of the diagram unless hidden, and it is edited like any other shape – also by several people at the same time (REQ-199, REQ-200).

## Attributes

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string (UUID) | required | generated | immutable | Stable identity |
| diagramId | reference | required | – | target: diagram; immutable | Diagram the note belongs to |
| text | string | required | – | 1–4000 characters; Markdown subset (emphasis, lists, links) | Content |
| color | enum | required | `yellow` | `[yellow, blue, green, pink, grey]` | Background colour |
| linkedShapeIds | list of string | optional | empty | shapes of the same diagram | Shapes the note points to visually |
| hiddenInExport | boolean | required | false | – | Leaves the note out of exports |
| createdBy | reference | required | – | target: person | Author |
| createdAt | datetime | required | now | immutable | Creation time |
| updatedBy | reference | required | – | target: person | Last editor |
| updatedAt | datetime | required | now | – | Time of the last change |

Position and size are part of the diagram layout (`DiagramShape` with `kind = note`).

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| belongsTo | [diagram](./diagram.md) | n:1 | no | Owning diagram |
| pointsTo | diagram shapes | n:m | yes | Visual links, no semantics |
| authoredBy | [person](./person.md) | n:1 | no | Author and last editor |

## Lifecycle

```text
active → deleted (with the diagram version, restorable through the diagram history)
```

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-DN-01 | A note MUST NOT create, change or delete entities or connections | onCreate, onUpdate | – |
| BR-DN-02 | Creating, changing and deleting a note requires the permission to edit the diagram and is recorded in the audit trail | onCreate, onUpdate, onDelete | §21.8 |
| BR-DN-03 | A note is visible to everyone who may read the diagram | onRead | – |

## Examples

```yaml
diagramId: application-cooperation-current
text: "**Assumption:** the ERP interface is replaced by the event bus in 2027"
color: yellow
linkedShapeIds: [e-crm-erp]
```

## Distinction from Similar Objects

- **NOT** [CommentThread](./comment-thread.md): a comment thread is a discussion *about* the diagram with replies and a resolved state, not shown in exports
- **NOT** an entity (e.g. a Requirement or Constraint of the motivation layer): a note carries no semantics and is not part of queries, catalogs or analyses

## Usage in Use Cases

- [UC-24: Collaborate in real time](../requirements/use-cases/UC-24-collaborate-in-real-time.md)

## Concept Reference

- [§21.10 Real-time collaboration](../concept/70-platform/21-api-architecture.md)

## Notes

Owner decision of 2026-10-28: diagrams offer both sticky notes and comment threads.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | Business Engineer | Initial draft (UC-24) |

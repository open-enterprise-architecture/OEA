---
identifier: comment-thread
name_de: Kommentar-Thread
name_en: Comment Thread
version: 0.2.0
status: draft
maturity: initial
owner_role: Business Engineer
aliases:
  - Comment
  - Kommentar
  - Review-Anmerkung
related_capabilities: []
references:
  - concept: concept/70-platform/21-api-architecture.md
---

# Business Object: Comment Thread

## Definition

A `CommentThread` is a discussion anchored to a [Diagram](./diagram.md) – to one of its shapes or edges, or to a position on the canvas. It collects comments and replies from several persons and can be resolved; it is not part of the diagram's content.

## Description

Reviews and workshops produce questions and remarks that need an answer: "Why does the portal call the ERP directly?", "@Kurt please confirm the owner". A comment thread keeps such a discussion next to the element it concerns, notifies mentioned persons and ends with *resolved*. Resolved threads are hidden by default and stay retrievable.

If the anchored shape is removed from the diagram, the thread keeps its last position and is marked *detached*, so no discussion is lost.

## Attributes

### CommentThread (root object)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | string (UUID) | required | generated | immutable | Stable identity |
| diagramId | reference | required | – | target: diagram; immutable | Diagram the thread belongs to |
| anchor | composite | required | – | see Anchor | Where the thread is attached |
| status | enum | required | `open` | `[open, resolved]` | State of the discussion |
| resolvedBy | reference | optional | – | target: person; set with `resolved` | Person who resolved the thread |
| resolvedAt | datetime | optional | – | set with `resolved` | Time of resolution |
| comments | list of Comment | required | – | at least one | Comments in order of creation |
| createdAt | datetime | required | now | immutable | Creation time |

### Anchor (composite)

| Attribute | Type | Optional | Constraints | Description |
|---|---|---|---|---|
| kind | enum | required | `[shape, edge, position]` | Type of anchor |
| targetId | string | optional | shapeId or edgeId of the diagram; required for `shape`, `edge` | Anchored element |
| x, y | decimal | required | – | Canvas position (last position for detached threads) |
| detached | boolean | required | false | The anchored element no longer exists |

### Comment (composite)

| Attribute | Type | Optional | Constraints | Description |
|---|---|---|---|---|
| commentId | string (UUID) | required | unique | Identity |
| author | reference | required | target: person | Author |
| text | string | required | 1–4000 characters; Markdown subset | Content |
| mentions | list of reference | optional | target: person | Mentioned persons (notified) |
| createdAt | datetime | required | immutable | Creation time |
| editedAt | datetime | optional | – | Time of the last edit by the author |
| deleted | boolean | required | default false | Deleted by the author; text no longer shown |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| belongsTo | [diagram](./diagram.md) | n:1 | no | Diagram the discussion concerns |
| writtenBy | [person](./person.md) | n:m | no | Authors of the comments |
| mentions | [person](./person.md) | n:m | yes | Persons mentioned |

## Lifecycle

```text
open ⇄ resolved
```

- `open → resolved`: by any person who may read the diagram
- `resolved → open`: reopened by a new reply or explicitly

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-CT-01 | Reading, opening, answering, resolving and reopening a thread requires read permission on the diagram; there is no separate permission to comment | onRead, onCreate, onUpdate | – |
| BR-CT-02 | Only the author may edit or delete their comment; deleting keeps a placeholder so replies stay understandable | onUpdate, onDelete | – |
| BR-CT-03 | Only persons who may read the diagram can be mentioned | onCreate | – |
| BR-CT-04 | Creating, resolving and deleting is recorded in the audit trail | onCreate, onUpdate, onDelete | §21.8 |
| BR-CT-05 | A thread whose anchored element disappears is marked detached, not deleted | onDiagramUpdate | – |

## Examples

```yaml
diagramId: application-cooperation-current
anchor: { kind: edge, targetId: e-portal-erp, x: 410, y: 220, detached: false }
status: open
comments:
  - { author: michael, text: "Why does the portal call the ERP directly?", mentions: [] }
  - { author: kurt, text: "Legacy; replaced with the event bus in 2027.", mentions: [michael] }
```

## Distinction from Similar Objects

- **NOT** [DiagramNote](./diagram-note.md): a note is content of the diagram and appears in exports; a comment thread is a discussion with replies and a resolved state
- **NOT** an audit event: the audit trail records that a comment was written, the thread holds the discussion

## Usage in Use Cases

- [UC-24: Collaborate in real time](../requirements/use-cases/UC-24-collaborate-in-real-time.md)

## Concept Reference

- [§21.10 Real-time collaboration](../concept/70-platform/21-api-architecture.md)

## Notes

Owner decisions of 2026-10-28: comment threads in diagrams; commenting needs no separate permission, read access to the diagram is enough (ADR-047). Comments on other objects (entities, documents) are not part of this decision and can extend the anchor later.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | Business Engineer | Initial draft (UC-24) |
| 0.2.0 | 2026-10-28 | Business Engineer | BR-CT-01: commenting with read access, no separate permission (owner decision) |

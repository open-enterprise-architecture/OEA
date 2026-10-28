# ADR-047: Architecture of Real-Time Collaboration

**Status**: accepted
**Date**: 2026-10-28
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Security Engineer
**Informed**: all contributors

## Context and Problem

The repository owner asked on 2026-10-28 for collaborative work: participants see each other's mouse pointers, follow changes live everywhere, edit the same diagram at the same time with automatic merge, and leave sticky notes and comment threads in diagrams ([UC-24](../requirements/use-cases/UC-24-collaborate-in-real-time.md), REQ-197 to REQ-204, [§21.10](../concept/70-platform/21-api-architecture.md); priority *should*, after the MVP).

The current architecture does not support this:

- The backend is a Spring Boot application with a REST API and an event stream planned as Server-Sent Events (concept §21.5, issue #26: WebSocket "optional from v2.0").
- The canvas (Vue Flow, ADR-007) in the client app (Electron, ADR-009) and the web portal saves explicitly (UC-10 step 9); concurrent edits would overwrite each other (REQ-032 mentions optimistic locking).
- Property-level authorization (§21.8, REQ-126) must hold for every participant, so the server has to see and filter every change – a purely peer-to-peer replication between clients is not possible.

Three questions need a decision: how concurrent edits are merged, where the merge runs, and how presence and edits travel between clients.

## Decision Drivers

- **No lost changes** without locks (owner decision: real co-editing, REQ-199)
- **Authorization per participant and per property** on every operation (REQ-203); the server must understand the content it relays
- **One backend runtime**: a single Maven module and deployment unit (ADR-027); every additional service increases the operator's effort (SH-06)
- **Persistence as regular versions** of entities, connections and diagrams with audit trail (§11, §21.8), not as an opaque document only the collaboration layer understands
- **Text and structure**: diagrams are structured (shapes with attributes); documents (arc42, UC-09) need character-level merging of text
- **Offline work** in the client app with merge on reconnect (UC-24 E1)
- **Latency and scale** of REQ-202 (≤ 150 ms pointers, ≤ 500 ms changes, 20 participants per diagram)

## Considered Options

### Option 1: CRDT library (Yjs) in a separate collaboration service

A Node.js service (for example with Hocuspocus) holds the Yjs documents of open diagrams and documents, relays updates over WebSocket and asks the backend for permissions; the backend stores snapshots and derives repository changes.

- **Pro**: mature, widely used CRDT including text; offline merge built in; client bindings exist
- **Pro**: fast to a working prototype
- **Con**: second runtime and deployment unit next to the backend (contradicts the single-module setup of ADR-027; more operator effort)
- **Con**: Yjs documents are opaque binary structures; filtering hidden properties per participant and turning updates into audited repository versions needs a translation layer in both directions
- **Con**: authorization is split between two services

### Option 2: CRDT library inside the backend (Automerge through its Java binding)

The backend holds Automerge documents per open diagram or document and relays changes over WebSocket from the same Spring Boot application.

- **Pro**: one runtime; merge, authorization and persistence in one place
- **Pro**: CRDT including text; offline merge on the client with the JavaScript implementation
- **Con**: the Java binding is a native library (Rust through JNI) with a smaller user base; maturity and container compatibility have to be verified
- **Con**: same filtering problem as option 1: a replicated document contains everything, so hidden properties must be kept out of the shared document and delivered separately

### Option 3: Server-ordered operations on the OEA model, text CRDT only for documents

The backend defines collaboration operations on its own model (add/move/resize/delete shape, set attribute, add note, …). Clients send operations over WebSocket; the server orders them, checks permissions per operation and attribute, applies them (per-attribute last-writer-wins by server order, deletions as tombstones), persists them as versions per author and time window, and sends each participant the operations filtered to what they may read. Clients apply their own operations optimistically and rebase them on the server order. Only free text in documents uses a text CRDT (Yjs or Automerge for the text fields), relayed by the backend.

- **Pro**: one runtime; the server understands every operation, so authorization per property and audit come naturally (REQ-203)
- **Pro**: persistence stays in the repository model (entities, connections, diagram layout) instead of opaque documents
- **Pro**: diagram attributes are atomic values (position, name, colour) – per-attribute ordering by the server keeps all non-conflicting changes and resolves conflicting ones deterministically, which is what REQ-199 asks for diagrams
- **Con**: own protocol and client logic (optimistic apply and rebase) to build and test, including randomized convergence tests (REQ-202)
- **Con**: offline editing needs a queue of operations replayed on reconnect; long offline phases produce more "applied with notice" cases than a full CRDT
- **Con**: two mechanisms (operations for structure, CRDT for text)

### Option 4: Live display with locks per element

Changes are shown live, but an element being edited is locked for others.

- **Pro**: simplest; no merge logic
- **Con**: contradicts the owner decision for real co-editing (2026-10-28); waiting for locks disturbs workshops
- **Con**: locks of disconnected participants need timeouts

### Transport (all options)

WebSocket for the collaboration session (bidirectional, low latency, one connection per open object or multiplexed), authenticated with the regular session cookie and checked for origin at the handshake (REQ-187); SSE stays the event stream for committed changes to other views (REQ-198) and for integrations (§21.5).

## Decision

Chosen: **Option 3 – server-ordered operations on the OEA model, text CRDT only for documents**, with WebSocket for the collaboration session and SSE for the event stream (accepted by the repository owner on 2026-10-28).

**Rationale**: it is the only option in which the server understands every change, which REQ-203 (authorization per property, audit per author) and the persistence as regular repository versions require; it keeps one backend runtime (ADR-027). Diagrams consist of atomic attributes, for which server ordering per attribute delivers the automatic merge of REQ-199 without a general-purpose CRDT. Text in documents is the one place where character-level merging is needed; there a CRDT library is used for the text fields only.

**Validation**: the first task of the implementing change is a spike that confirms the client rebase in Vue Flow and the latency of REQ-202 with 20 simulated participants. If the spike fails, this ADR is revisited before further collaboration work.

### Version Granularity

Continuous editing is recorded on two levels, plus named checkpoints (owner decision of 2026-10-28):

1. **Operation log (traceability)**: every operation the server accepts is recorded with author, time, element, attribute and old and new value – in the audit trail (§21.8) and in the diagram's operation log, which keeps the server order for resynchronisation and point-in-time restore. A gesture is one operation: a drag records the final position, a renamed field is taken over when the participant leaves it. Pointer movements, selections and presence are not recorded.
2. **Versions (usability)**: a version groups the operations of **one author on one object** – the diagram, or an entity or connection changed through it. A version closes after **2 minutes** without a further operation of that author on that object, after **15 minutes** at the latest, or when the author leaves the diagram or loses the connection. Both durations are defaults the administrator can change. Every version therefore has exactly one author. Entity forms keep explicit saving (one version per save).
3. **Named checkpoints**: a participant who may edit the diagram can name the current state (for example "Workshop result 2026-10-28"). A checkpoint refers to a position in the operation log, appears highlighted in the history and can be compared and restored (UC-14, UC-15).

Restoring sets the whole diagram to the state at a version boundary or checkpoint; it is not limited to one author's changes, because selectively reverting one author would conflict with later changes of others. Undo works on single operations and only on one's own (UC-24 A3), independent of versions. The operation log follows the lifecycle of the diagram; its entries in the audit trail follow the audit retention (ADR-033).

### Permission to Comment

Commenting needs no separate permission: everyone who may read a diagram may open, answer and resolve comment threads (owner decision of 2026-10-28, BR-CT-01). Sticky notes are diagram content and need edit permission (BR-DN-02).

## Consequences

### Positive Consequences

- Authorization, property filtering and audit stay in the backend, consistent with the REST API
- Versions of entities, connections and diagrams remain readable and restorable with the existing history (UC-14, UC-15)
- No additional service for operators

### Negative Consequences / Trade-offs

- An own collaboration protocol (operations, ordering, rebase, resynchronisation) has to be designed, documented and tested
- Long offline phases produce more conflicts that are applied with a notice than a full CRDT would
- WebSocket connections must pass the operator's reverse proxy (timeouts, upgrade headers); the installation guide has to cover it
- Explicit saving in the canvas is replaced by continuous saving (UC-10)
- The operation log and the per-operation audit events grow with every accepted operation; coalescing gestures keeps the volume bounded
- Point-in-time states between version boundaries are reconstructible from the operation log but not offered for restore in the user interface

### Follow-up Decisions

- Choice of the text CRDT library for documents (Yjs or Automerge) when documents are implemented
- Horizontal scaling: routing all participants of one object to the same backend node, or a shared broker (only if one node does not reach REQ-202)
- Revision of concept issue #26 (done in concept v0.20.0: WebSocket in v1.x)

## References

**Concept Chapters**:

- [§21.10 Real-time collaboration](../concept/70-platform/21-api-architecture.md)
- [§21.5 Event stream](../concept/70-platform/21-api-architecture.md)
- [§21.8 Security and authorization](../concept/70-platform/21-api-architecture.md)
- [§23 Open issues, #26 event transport](../concept/90-backlog/23-open-issues.md)

**Related ADRs**:

- [ADR-007](./ADR-007-canvas-library.md) (Vue Flow canvas), [ADR-009](./ADR-009-client-app-framework.md) (client app, offline), [ADR-027](./ADR-027-project-setup-monorepo.md) (single backend module), [ADR-032](./ADR-032-authorization-model.md) (authorization)

**Use Cases / Requirements**:

- [UC-24: Collaborate on the architecture in real time](../requirements/use-cases/UC-24-collaborate-in-real-time.md)
- REQ-197 to REQ-204

## Notes

Owner decisions of 2026-10-28: priority *should* after the MVP; presence and live changes in every view; real co-editing with automatic merge; sticky notes and comment threads in diagrams. Later the same day the owner accepted option 3, commenting with read access and the version granularity above (2 minutes without change, 15 minutes at most, named checkpoints).

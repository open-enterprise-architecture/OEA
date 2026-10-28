---
id: UC-24
title: Collaborate on the architecture in real time
status: draft
priority: should
target_release: v1.x
complexity: xl
version: 0.1.0
created: 2026-10-28
last_updated: 2026-10-28
primary_actor: SH-03
secondary_actors:
  - SH-04
  - SH-01
  - SH-08
references:
  business_objects:
    - diagram
    - diagram-note
    - comment-thread
    - entity
    - person
  concept:
    - concept/70-platform/21-api-architecture.md
  related_use_cases:
    - UC-05
    - UC-06
    - UC-09
    - UC-10
    - UC-14
---

# UC-24: Collaborate on the architecture in real time

## Goal in Context

Architecture is shaped in workshops, reviews and parallel work of several architects on the same landscape. Today each person edits alone and saves; others see the result only after reloading, and remarks are collected outside the tool. This use case lets a team work on the same content at the same time: everyone sees who else is there, follows changes as they happen and leaves notes and questions directly in the diagram ([§21.10](../../concept/70-platform/21-api-architecture.md), owner request of 2026-10-28).

## Persona and Story

**Primary Actor**: [Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Other Stakeholders**: [Michael – Solution Architect](../../business-analysis/stakeholders/SH-04-michael-solution-architect.md), [Franz – Domain Architect](../../business-analysis/stakeholders/SH-01-franz-junior-domain-architect.md), [Anna – Business Analyst](../../business-analysis/stakeholders/SH-08-anna-business-analyst.md)

**Story**: As a lead enterprise architect, I want to work on a diagram together with my colleagues at the same time, see their pointers and changes live and leave notes and questions in the diagram, so that a workshop produces the agreed architecture directly in the repository instead of in a separate whiteboard that someone has to transfer afterwards.

## Trigger

- External occasion: architecture workshop, review of a solution architecture, parallel modeling of a domain
- Timing: ad hoc, several times a week
- Predecessor use case: a diagram exists or is created (UC-05, UC-10)

## Preconditions

- [ ] All participants are logged in (UC-01)
- [ ] Every participant may read the diagram; editing participants may change it
- [ ] The diagram exists

## Postconditions

### On Success

- The diagram, its entities and connections contain the changes of all participants, none is lost
- Every change is a version attributed to its author and recorded in the audit trail
- Sticky notes are part of the diagram; comment threads are stored with the diagram and can be resolved later

### On Failure

- Changes a participant made while disconnected are merged on reconnect or, if that is not possible, kept as a local copy and reported to the participant
- No change of another participant is overwritten

## Basic Flow

1. **Kurt**: opens the diagram "CRM migration – target state" and shares its link with Michael and Franz
2. **System**: shows Kurt as present; when Michael and Franz open the diagram, their names, colours and mouse pointers appear for everyone
3. **Michael**: moves the CRM system and adds an interface to the ERP system
4. **System**: shows the change to Kurt and Franz within a second, with Michael's colour marking the changed shapes briefly
5. **Franz**: renames the ERP system at the same time as Kurt moves it
6. **System**: merges both changes – the ERP system has the new name and the new position – and shows the result to everyone
7. **Kurt**: places a sticky note "Assumption: event bus available in 2027" next to the interface
8. **Michael**: opens a comment thread on the interface: "@Kurt who owns this interface?"
9. **System**: shows the note and the thread to everyone and notifies Kurt of the mention
10. **Kurt**: answers in the thread and resolves it; leaves the diagram
11. **System**: removes Kurt's pointer for the others; the diagram keeps all changes as versions per author

## Alternative Flows

**A1 – Following without editing**: At step 2, a participant has only read permission (e.g. Anna as business analyst):

1. Anna sees pointers, changes, notes and threads live and may comment if she has the permission to comment
2. She cannot move, add or change shapes

- Rejoins at step 3

**A2 – Collaboration outside diagrams**: Instead of a diagram, the participants open the same entity form, catalog, document or dashboard:

1. The system shows who else has it open; in documents also pointers and selections, in forms and catalogs the field or row someone is editing
2. A saved change of another participant updates the open view without reloading
3. In documents, concurrent edits are merged as in step 6

- Ends here

**A3 – Undo**: A participant undoes a change; only their own changes are undone, changes of others stay.

## Exception Flows

**E1 – Connection lost**:

- Condition: a participant loses the network connection while editing
- Expected reaction: their pointer disappears for the others after a few seconds; they keep editing locally with a visible "offline" indicator
- Resumption: on reconnect, their changes are merged; changes that cannot be merged (e.g. the entity was deleted meanwhile) are reported and kept as a copy

**E2 – Permission withdrawn during the session**:

- Condition: a participant loses read or write permission while the diagram is open
- Expected reaction: the system ends their editing or their session for this diagram immediately and discards changes they make afterwards
- Resumption: after the permission is granted again

**E3 – Hidden properties**:

- Condition: a shared diagram shows an entity with a property one participant may not read
- Expected reaction: that participant never receives the property value, neither in the diagram nor in the live changes of others

## Data Flow

| Step | Data | Direction | Remark |
|---|---|---|---|
| 2 | presence: participant, colour, pointer, selection | Participant ↔ System ↔ Participants | not persisted |
| 3–6 | edit operations on the diagram | Participant → System → Participants | merged, filtered per participant |
| 4, 6 | new versions of diagram, entities, connections | System → DB | per author and time window |
| 7–10 | notes, comments, mentions | Participant → System → Participants | persisted and audited |

## Involved Business Objects

| Business Object | Operation | Note |
|---|---|---|
| [diagram](../../business-objects/diagram.md) | read, update | layout merged from all participants |
| [diagram-note](../../business-objects/diagram-note.md) | create, update, delete | part of the diagram |
| [comment-thread](../../business-objects/comment-thread.md) | create, update | discussion with replies and resolved state |
| [entity](../../business-objects/entity.md) | read, update | changed through the diagram |
| [person](../../business-objects/person.md) | read | participants, mentions |

## Acceptance Criteria

- [ ] Every participant sees the names and pointers of all others in an open diagram; a pointer movement appears for the others within the time of REQ-202
- [ ] A change of one participant appears for all others without reloading, in diagrams, forms, catalogs, documents and dashboards (REQ-198)
- [ ] Two participants change the same shape at the same time (one renames, one moves); both changes are kept (REQ-199)
- [ ] Sticky notes can be created, edited, coloured and deleted and are part of the diagram (REQ-200)
- [ ] Comment threads can be anchored to a shape, an edge or a position, answered, resolved and reopened; mentioned persons are notified (REQ-201)
- [ ] A participant without read permission on a property never receives its value (E3, REQ-203)
- [ ] After a lost connection, local changes are merged on reconnect (E1)
- [ ] The audit trail contains every saved change, note and comment with its author; pointer movements are not recorded

## Out of Scope

- Voice, video or chat outside comment threads
- Comments on objects other than diagrams (entities, documents) – possible extension of the comment thread anchor
- Following another participant's viewport ("follow mode") – can follow later
- Collaboration across OEA instances

## Concept References

- [§21.10 Real-time collaboration](../../concept/70-platform/21-api-architecture.md)
- [§21.5 Event stream](../../concept/70-platform/21-api-architecture.md)
- [§21.8 Security and authorization](../../concept/70-platform/21-api-architecture.md)
- [§12.3 View definition](../../concept/30-dynamics/12-domain-views.md)

## Implementation Notes

- Co-editing needs a mergeable working state (CRDT or OT) on the server – [ADR-047](../../adrs/ADR-047-real-time-collaboration.md)
- The canvas (Vue Flow, ADR-007) and the client app (Electron, ADR-009) move from explicit saving to continuous saving (UC-10 step 9)
- Expected load: up to 20 simultaneous participants per diagram, up to 200 concurrent sessions per instance (REQ-202)

## Realizing Components

- Requirements: REQ-197, REQ-198, REQ-199, REQ-200, REQ-201, REQ-202, REQ-203
- User Stories: – (priority *should*; stories follow when the use case is planned)
- ADRs: ADR-047 (proposed)
- Test Cases: –
- Implementation: –

## Open Questions

- [ ] Granularity of versions from continuous editing (time window per author) – to be set with ADR-047
- [ ] Separate permission to comment, or comment rights with diagram read access (BR-CT-01 assumes the latter)

## Notes

Owner decisions of 2026-10-28: priority *should* after the MVP; presence and live changes in every view; real co-editing with automatic merge; both sticky notes and comment threads in diagrams.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | requirements-engineer | Initial draft (owner request of 2026-10-28) |

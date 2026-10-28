---
id: REQ-201
title: Comment threads in diagrams
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-10-28
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-24
  business_objects:
    - diagram
    - comment-thread
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
    - SH-01
    - SH-08
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-047
supersedes: []
superseded_by: []
---

# REQ-201: Comment threads in diagrams

## Statement

The system SHOULD let a participant who may read a diagram open a comment thread anchored to a shape, an edge or a position, reply to it, mention persons, resolve and reopen it; mentioned persons SHOULD be notified, and a thread whose anchored element is removed SHOULD remain as detached.

## Rationale

Reviews produce questions that need an answer from a specific person; a thread next to the element keeps question, answer and outcome together (UC-24 steps 8 to 10; owner decision of 2026-10-28).

## Context

A thread is a [CommentThread](../../business-objects/comment-thread.md); it is stored with the diagram but is not part of its content or exports. Resolved threads are hidden by default and can be shown.

## Type-Specific Fields

### For type=functional

**Trigger**: A participant creates a thread, replies, edits or deletes their own comment, resolves or reopens a thread

**Input**: anchor, text (Markdown subset, at most 4000 characters), mentions

**Output / Behavior**: the thread for all participants who may read the diagram; an in-app notification for mentioned persons

**Business rules**: BR-CT-01 to BR-CT-05; only persons who may read the diagram can be mentioned; creating, resolving and deleting are recorded in the audit trail

## Acceptance Criteria

**AC1** (Thread on an edge):

- Given: Michael may read the diagram
- When: he opens a thread on the interface with the text "@Kurt who owns this interface?"
- Then: all participants see the thread at the interface, and Kurt receives a notification

**AC2** (Resolve and reopen):

- Given: a thread is open
- When: Kurt answers and resolves it, then Michael replies again
- Then: the thread is resolved after Kurt's action and open again after Michael's reply

**AC3** (Detached):

- Given: a thread is anchored to a shape
- When: the shape is removed from the diagram
- Then: the thread stays at its last position marked as detached

**AC4** (Mention without access):

- Given: Franz may not read the diagram
- When: Michael tries to mention Franz
- Then: the mention is rejected, and Franz receives no notification

**AC5** (Own comments only):

- Given: Kurt wrote a comment
- When: Michael tries to edit or delete it
- Then: the change is rejected

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: two or more browser sessions of different persons against the same instance (Playwright with several browser contexts)
- [x] Measuring tool: Playwright, backend integration tests
- [x] Pass criterion: all acceptance criteria green
- [ ] Integrated in CI: planned with the implementing change

## Dependencies

- **Prerequisites**: BO diagram, comment-thread; notification mechanism (in-app)
- **Downstream effects**: e-mail or chat notifications (later), comments on other objects (later)

## Risks of Non-Fulfillment

- Review questions are lost in e-mail and meetings; decisions cannot be traced back to the diagram (medium)

## Trade-offs

- A separate permission to comment would allow read-only reviewers without comment rights; BR-CT-01 grants commenting with read access unless restricted (open question in UC-24)

## Implementation

- ADR(s): ADR-047
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Owner request and decisions of 2026-10-28 (UC-24): priority *should* after the MVP; presence and live changes in every view; real co-editing with automatic merge; sticky notes and comment threads in diagrams.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | requirements-engineer | Initial draft (UC-24) |

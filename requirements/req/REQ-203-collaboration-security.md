---
id: REQ-203
title: Authorization and audit of collaboration sessions
type: non-functional
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
    - diagram-note
    - comment-thread
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
    - SH-01
    - SH-08
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-047
    - ADR-032
supersedes: []
superseded_by: []
---

# REQ-203: Authorization and audit of collaboration sessions

## Statement

The system SHOULD authorize every collaboration session and every operation in it with the same rules as the REST API, SHOULD never send a participant content they may not read, SHOULD end a participant's access within 5 seconds after their permission is withdrawn, and SHOULD record every persisted change, note and comment in the audit trail.

## Rationale

A live channel bypasses the request-by-request checks of the REST API unless it applies them itself; it must not become a way around property-level authorization (§21.8) or the audit trail (UC-24 E2, E3).

## Context

Applies to the collaboration session (WebSocket) and to the live updates through the event stream. Presence data is not persisted and not audited (REQ-197).

## Type-Specific Fields

### For type=non-functional

**Category**: security

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| Operations checked against the permission of the participant | 100 % | – | < 100 % | every operation of a session |
| Values of properties hidden from a participant sent to them | 0 | – | > 0 | every session and live update |
| Time from withdrawing a permission to the end of access | ≤ 5 s | 10 s | 30 s | open sessions of the affected person |
| Persisted changes, notes and comments with audit event and author | 100 % | – | < 100 % | all collaboration operations |
| Session handshakes without a valid session or with a foreign origin accepted | 0 | – | > 0 | every handshake (REQ-187) |

## Acceptance Criteria

**AC1** (Join requires read):

- Given: Franz may not read the diagram
- When: he tries to join its collaboration session
- Then: the session is refused, and Franz receives no content or presence

**AC2** (Write requires write):

- Given: Anna may only read the diagram
- When: her client sends an operation that moves a shape
- Then: the server rejects the operation; the others never see it

**AC3** (Hidden property):

- Given: a diagram shows an entity whose property Cost is hidden from Anna
- When: Kurt changes Cost while Anna is in the session
- Then: Anna's session receives neither the old nor the new value

**AC4** (Withdrawn permission):

- Given: Michael's role with write permission is revoked while he edits
- When: the revocation takes effect
- Then: within 5 seconds his further operations are rejected, and his session is ended or reduced to read access

**AC5** (Audit):

- Given: three participants co-edited a diagram, added a note and a comment
- When: the audit trail is queried
- Then: every persisted change, the note and the comment are recorded with their authors

**AC6** (Handshake):

- Given: a page of another origin opens a WebSocket to the instance with a valid session cookie
- When: the handshake reaches the server
- Then: it is refused

## Verification Method

- [x] Method: test (automated security tests), review by the security engineer
- [x] Test setup: sessions of persons with different roles and property visibility; manipulated clients sending forbidden operations
- [x] Measuring tool: backend integration tests, Playwright
- [x] Pass criterion: all acceptance criteria green; security review without high findings
- [ ] Integrated in CI: planned with the implementing change

## Dependencies

- **Prerequisites**: ADR-032 (authorization), REQ-126 (property visibility), REQ-187 (origin checks), REQ-186 (rate limits), ADR-047
- **Downstream effects**: no peer-to-peer replication between clients (filtering needs the server)

## Risks of Non-Fulfillment

- Confidential properties leak through live updates; revoked users keep editing (high)

## Trade-offs

- Filtering per participant prevents forwarding the same operation to all clients unchanged and costs server resources

## Implementation

- ADR(s): ADR-047, ADR-032
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Owner request and decisions of 2026-10-28 (UC-24): priority *should* after the MVP; presence and live changes in every view; real co-editing with automatic merge; sticky notes and comment threads in diagrams.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | requirements-engineer | Initial draft (UC-24) |

---
id: REQ-202
title: Latency and scale of real-time collaboration
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

# REQ-202: Latency and scale of real-time collaboration

## Statement

The system SHOULD propagate presence and changes between participants within defined time budgets and SHOULD support a defined number of simultaneous participants per object and per instance.

## Rationale

Collaboration feels live only if pointers follow the hand and changes appear before the speaker has finished the sentence; workshops need a whole team in one diagram (UC-24).

## Context

Measured between two clients in the same region as the server, from the action of one participant to the display at another (end-to-end). Does not include the initial loading of a diagram (REQ-072).

## Type-Specific Fields

### For type=non-functional

**Category**: performance, scalability

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| Pointer update, end to end (p95) | ≤ 150 ms | 300 ms | 500 ms | 20 participants in one diagram |
| Change in a co-edited diagram, end to end (p95) | ≤ 500 ms | 1 s | 2 s | 20 participants, diagram with 150 shapes |
| Change in forms, catalogs, dashboards, end to end (p95) | ≤ 2 s | 3 s | 5 s | 200 concurrent sessions per instance |
| Resynchronisation after reconnect | ≤ 5 s | 10 s | 20 s | 10 minutes offline, 500 operations |
| Participants per diagram or document | ≥ 20 | 15 | 10 | one object |
| Concurrent collaboration sessions per instance | ≥ 200 | 150 | 100 | reference hardware of REQ-074 |
| Convergence of merged states | 100 % | – | < 100 % | randomized concurrent operations |

## Acceptance Criteria

**AC1** (Pointer latency):

- Given: 20 participants have the same diagram open
- When: one participant moves the pointer
- Then: the pointer is displayed at the others within 150 ms (p95)

**AC2** (Change latency):

- Given: 20 participants edit a diagram with 150 shapes
- When: a participant moves a shape
- Then: the others see the new position within 500 ms (p95)

**AC3** (Forms and catalogs):

- Given: 200 concurrent sessions are open on the instance
- When: an entity shown in open catalogs changes
- Then: the catalogs show the change within 2 s (p95)

**AC4** (Reconnect):

- Given: a participant was offline for 10 minutes while others made 500 operations
- When: the connection returns
- Then: the participant's view is synchronised within 5 s

**AC5** (Convergence):

- Given: randomized concurrent operations of 20 simulated participants
- When: all operations are applied
- Then: all participants end with identical states in 100 % of runs

## Verification Method

- [x] Method: test (automated load and latency test)
- [x] Test setup: simulated participants (headless clients) against a test instance on the reference hardware of REQ-074
- [x] Measuring tool: k6 or Playwright with timestamps per operation
- [x] Pass criterion: target values of the table
- [ ] Integrated in CI: nightly performance run (planned)

## Dependencies

- **Prerequisites**: ADR-047; REQ-074 (reference hardware)
- **Downstream effects**: sizing of the WebSocket infrastructure behind reverse proxies

## Risks of Non-Fulfillment

- Laggy pointers and late changes make co-editing confusing; participants talk over the tool (medium)

## Trade-offs

- Throttling pointer updates (e.g. 20 per second) saves bandwidth at the cost of smoothness

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

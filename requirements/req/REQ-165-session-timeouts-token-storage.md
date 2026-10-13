---
id: REQ-165
title: Session timeouts and client token storage
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-008
    - ADR-009
supersedes: []
superseded_by: []
---

# REQ-165: Session Timeouts and Client Token Storage

## Statement

The system **MUST** enforce an OEA-controlled idle timeout and absolute session lifetime independent of IdP token lifetimes, **MUST** rotate refresh tokens on use with reuse detection, and **MUST NOT** store session or refresh tokens in browser `localStorage` or `sessionStorage` (web portal: `HttpOnly`, `Secure`, `SameSite` cookies; Electron: OS-protected storage).

## Rationale

IdP refresh tokens can live for 90 days; a stolen token in script-readable storage is a long-lived credential. Idle and absolute limits bound the damage of an unattended or stolen session.

## Context

Source: security review SEC-06 (proposal PR-09). `SystemSettings.sessionTimeoutMin` in the API contract and REQ-001 AC4 / REQ-012 need alignment with this REQ (follow-up).

## Type-Specific Fields

### For type=non-functional

**Category**: security

**Measurable target values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| Idle timeout (default) | 30 min | – | > 8 h configured | all interactive sessions; operator-configurable 5 min–8 h |
| Absolute session lifetime (default) | 12 h | – | > 7 days configured | all interactive sessions; operator-configurable 1 h–7 days |
| Refresh token reuse accepted | 0 | – | ≥ 1 | all clients |
| Tokens in script-readable browser storage | 0 | – | ≥ 1 | web portal |

## Acceptance Criteria

**AC1**:

- Given: default configuration (idle 30 min, absolute 12 h)
- When: a session is idle for longer than the idle timeout or older than the absolute lifetime
- Then: the next request is rejected with 401

**AC2**:

- Given: a refresh token that has already been used once
- When: it is presented again
- Then: it is rejected, all tokens of that session family are revoked and an audit event is written

**AC3**:

- Given: the web portal after login
- When: the browser storage is inspected
- Then: no access or refresh token is present in `localStorage` or `sessionStorage`

**AC4**:

- Given: the Electron client after login
- When: its storage is inspected
- Then: tokens are stored only in the OS-protected credential store

## Verification Method

- [ ] Method: test (automated, controlled clock) + inspection (browser storage, Electron storage)
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes (AC1–AC3)

## Dependencies

- **Prerequisites**: REQ-001, REQ-164
- **Follow-on effects**: REQ-012, REQ-187 (cookie attributes)
- **Conflicts**: REQ-001 AC4 (IdP-dictated lifetime)

## Risks of Non-Fulfillment

- Risk 1: long-lived stolen tokens after XSS or device loss – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-06 (PR-09) |

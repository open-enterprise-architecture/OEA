---
id: REQ-165
title: Session timeouts and client token storage
type: non-functional
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-23
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
    - ADR-032
supersedes: []
superseded_by: []
---

# REQ-165: Session Timeouts and Client Token Storage

## Statement

The system **MUST** enforce an OEA-controlled idle timeout and absolute session lifetime independent of IdP token lifetimes, **MUST** rotate refresh tokens on use with reuse detection, and **MUST NOT** store session or refresh tokens in browser `localStorage` or `sessionStorage` (web portal: `HttpOnly`, `Secure`, `SameSite` cookies; Electron: OS-protected storage). For sessions established through an IdP, the system **MUST** re-evaluate the IdP group claims at every token refresh and at the latest 15 minutes after the previous evaluation, so that removal of a person from an IdP group removes the roles derived from that group (ADR-032 IdP group mappings) within 15 minutes; changes of local role assignments remain effective within 60 seconds (REQ-003, REQ-164).

## Rationale

IdP refresh tokens can live for 90 days; a stolen token in script-readable storage is a long-lived credential. Idle and absolute limits bound the damage of an unattended or stolen session.

Without a bounded re-evaluation interval, a person removed from an IdP group keeps the derived OEA roles until the absolute session lifetime (default 12 h) ends. Owner decision Q16 (2026-10-13) bounds this delay to 15 minutes, without requiring SCIM provisioning.

## Context

Source: security review SEC-06 (proposal PR-09). `SystemSettings.sessionTimeoutMin` in the API contract and REQ-001 AC4 / REQ-012 need alignment with this REQ (follow-up).

Owner decision Q16 (2026-10-13), ADR-032 §7: IdP group claims are re-evaluated at every token refresh, at the latest every 15 minutes. If the claims cannot be re-evaluated (refresh rejected by the IdP, groups claim missing or truncated), the IdP-derived roles no longer apply (fail closed, consistent with ADR-032 §7); local assignments are not affected. Immediate offboarding remains possible by changing the person's status in OEA (REQ-164, 60 s).

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
| Delay between removal from an IdP group and loss of the derived OEA roles | ≤ 15 min | – | > 15 min | all sessions established through an IdP with configured IdP group mappings |
| Delay between a local role assignment change and its effect | ≤ 60 s | – | > 60 s | all principals (REQ-003, REQ-164) |

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

**AC5** (IdP group removal within 15 minutes):

- Given: a person with an active session established through an IdP who holds `viewer` only through an IdP group mapping, and who stays active in the session without a refresh triggered by the client
- When: the person is removed from the mapped group in the IdP at time T
- Then: every request of that session after T + 15 min is evaluated without `viewer` (403 on repository content), and a request after a token refresh that occurs earlier is evaluated without `viewer` from that refresh on

**AC6** (Re-evaluation fails closed):

- Given: a person holding roles from an IdP group mapping and from a local assignment
- When: the re-evaluation of the group claims fails because the IdP rejects the refresh or returns no or a truncated groups claim
- Then: the IdP-derived roles no longer apply from that point on, the locally assigned role still applies, and a warning event is written

## Verification Method

- [ ] Method: test (automated, controlled clock, IdP stub with changeable group membership) + inspection (browser storage, Electron storage)
- [ ] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes (AC1–AC3, AC5, AC6)

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
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-032 / owner decision Q16: IdP group claims re-evaluated at every token refresh and at the latest every 15 min; local role changes stay within 60 s; target values, AC5–AC6 added |

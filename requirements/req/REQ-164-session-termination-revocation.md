---
id: REQ-164
title: Session termination and server-side revocation
type: functional
priority: must
status: proposed
version: 0.3.0
created: 2026-10-13
last_updated: 2026-10-29
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - person
    - authorization-role
    - session
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-164: Session Termination and Server-Side Revocation

## Statement

The system **MUST** provide a logout that invalidates the OEA session and refresh token server-side, and **MUST** revoke all sessions and refresh tokens of a person within 60 seconds when the person leaves the `active` lifecycle state, loses a role assignment, has a credential revoked, or has the password reset.

## Rationale

Offboarding in OEA must take effect immediately, even while the IdP account is still active. A stolen device must be cut off when the password is reset. Users on shared machines must be able to log out.

## Context

Source: security review SEC-06, SEC-12, SEC-25 (proposal PR-08). REQ-083 already lists logout as an audited event although no logout was specified. Requires a server-side session/refresh-token store (follow-up for the business engineer: data model).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Logout request; lifecycle, role assignment or credential changes of a person

**Processing**:

- Invalidate session, access and refresh token server-side on logout
- On the listed triggers: revoke all sessions and refresh tokens of the person; close open event streams

**Outputs**:

- Audit events `auth.logout` and `auth.session-revoked`

**Error cases**:

- Requests with revoked tokens → 401

## Acceptance Criteria

**AC1**:

- Given: a logged-in user
- When: they log out
- Then: any further request with the previous session cookie, access token or refresh token is rejected with 401, and an audit event `auth.logout` exists

**AC2**:

- Given: a user with an active session
- When: an admin sets the person to `inactive` or `offboarded`
- Then: within 60 s all requests of that session, including open event streams, are rejected

**AC3**:

- Given: a user whose role assignment is removed
- When: they make the next request after at most 60 s
- Then: their effective permissions no longer include that role

**AC4**:

- Given: an admin password reset (REQ-031)
- When: it completes
- Then: all existing sessions of the target person are revoked

## Verification Method

- [ ] Method: test (automated, controlled clock)
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-001, REQ-004
- **Follow-on effects**: REQ-003, REQ-031, REQ-165, REQ-181 (client cache deletion on revocation)
- **Conflicts**: REQ-001 AC4 (IdP token lifetime adopted unchanged) – OEA session lifetime is OEA-controlled (REQ-165)

## Risks of Non-Fulfillment

- Risk 1: offboarded persons keep exporting the landscape for days – high

## Implementation Notes

- AC3 implemented for local role assignments by OpenSpec change `us-144-local-role-assignments`: a revocation revokes all sessions of the person immediately (reason `role-change`), and permission sets are cached for at most 60 seconds; roles from IdP group mappings follow the re-evaluation rules of REQ-165 AC5. AC1 is implemented (`identity/sessions`); AC2 is implemented by OpenSpec change `us-148-person-lifecycle`: setting a person to `inactive` or `offboarded` revokes all sessions at once, and requests of such persons are refused anyway (no event streams exist yet); AC4 with the password reset (REQ-031).
- The event stream must authenticate without long-lived tokens in the URL (follow-up for API contract/ADR-013)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-06/12/25 (PR-08) |
| 0.2.0 | 2026-10-29 | requirements-engineer | Implementation note: AC3 for local role assignments (change `us-144-local-role-assignments`) |
| 0.3.0 | 2026-10-29 | requirements-engineer | Implementation note: AC2 (change `us-148-person-lifecycle`) |

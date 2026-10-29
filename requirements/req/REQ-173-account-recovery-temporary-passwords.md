---
id: REQ-173
title: Account recovery and temporary password lifecycle
type: functional
priority: must
status: proposed
version: 0.3.0
created: 2026-10-13
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-02
    - UC-03
  business_objects:
    - local-credential
    - person
    - system-admin-account
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-173: Account Recovery and Temporary Password Lifecycle

## Statement

Initial and admin-reset passwords **MUST** expire after a configurable period (default 72 hours) and **MUST** be usable only to reach the mandatory password change; the system **MUST** provide an audited recovery path for lost second factors (recovery codes or admin-initiated factor reset with re-enrollment) and an audited break-glass procedure for complete admin lockout.

## Rationale

A temporary password without expiry can be used by whoever obtains it first. Without a defined recovery path, operators improvise unaudited database edits after a lost phone or an admin lockout.

## Context

Source: security review SEC-12 (proposal PR-17). REQ-024 and REQ-031 were aligned on 2026-10-13. UC-02 E4 (admin lockout) and UC-03 "Out of Scope" (recovery codes) leave the recovery path open; this REQ closes the requirement gap.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Temporary password (initial or reset); recovery code; admin factor-reset request; break-glass command on the host

**Processing**:

- Store an expiry timestamp with every temporary password; reject expired ones with the generic error of REQ-006
- After login with a temporary password: only the password change is reachable
- Resets of accounts holding `admin`-class permissions only by the system admin account
- Lost second factor: recovery code (single use) or admin-initiated factor reset followed by re-enrollment at next login
- Break-glass: a host-side command creates a time-limited recovery credential for the system admin account

**Outputs**:

- Audit events for every reset, recovery and break-glass use

**Error cases**:

- Expired temporary password → login fails; admin must issue a new one
- Admin-on-admin reset by a non-system-admin → 403

## Acceptance Criteria

**AC1**:

- Given: an initial password set 73 hours ago and never used
- When: the person tries to log in with it
- Then: login fails and the admin must issue a new one

**AC2**:

- Given: a person logging in with an initial or reset password
- When: authentication succeeds
- Then: no application access is possible until a new password is set

**AC3**:

- Given: an administrator who is not the system admin account and a target person with an `admin`-class permission
- When: the administrator requests a password reset
- Then: it is rejected; every reset attempt is audited

**AC4**:

- Given: complete admin lockout (UC-02 E4)
- When: the documented break-glass command is run on the host
- Then: it creates a time-limited recovery credential and writes an audit event

**AC5**:

- Given: a person who lost their only second factor
- When: an administrator performs a factor reset
- Then: the old factor is revoked, the person must enroll a new factor at the next login, sessions are revoked (REQ-164) and an audit event exists

## Verification Method

- [ ] Method: test (automated) + inspection of operator documentation
- [ ] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes (AC1–AC3, AC5)

## Dependencies

- **Prerequisites**: REQ-024, REQ-031, REQ-164
- **Follow-on effects**: UC-02 E4, UC-03
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: account takeover through leaked initial passwords – medium
- Risk 2: unaudited emergency changes in the database – medium

## Implementation Notes

- AC1 and AC2 realized by OpenSpec change `us-011-local-password` for initial passwords of persons: they expire (default 72 hours) and fail like wrong passwords afterwards; a session created with them allows only `GET /me`, the logout and `POST /me/password`. AC3 and AC5 follow with the password reset and the factor reset of persons.
- AC4 realigned by OpenSpec change `us-019-admin-safeguards` (owner decision 2026-10-28): the break-glass command `admin recover` runs inside the backend container. The operator types the new password and, after the setup phase, confirms a new TOTP factor directly in the command instead of receiving a time-limited recovery credential. Nothing crosses a network, and the password-change flow a temporary credential would need does not exist yet. The recovery is audited as `system.admin-recovered` with source `CLI`. AC1–AC3 and AC5 follow with UC-03.
- Recovery codes vs. admin factor reset is a design choice; at least one must exist
- Temporary-password expiry and recovery codes need data model support (follow-up for business engineer)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-12 (PR-17) |
| 0.2.0 | 2026-10-28 | requirements-engineer | AC4 realigned to the break-glass command `admin recover` (change `us-019-admin-safeguards`) |
| 0.3.0 | 2026-10-29 | requirements-engineer | AC1/AC2 realized for initial passwords (change `us-011-local-password`) |

---
id: REQ-029
title: Configure role-based 2FA exemption by the operator
type: functional
priority: should
status: proposed
version: 0.3.0
created: 2026-06-25
last_updated: 2026-10-15
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-03
  business_objects:
    - person
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

# REQ-029: Configure role-based 2FA exemption by the operator

## Statement

If instance-wide 2FA enforcement is active (REQ-020), the operator MUST additionally be able to configure which OEA roles are exempt from this obligation; persons whose active roles are all contained in the exemption list MUST be able to satisfy REQ-020 without a second factor. Roles that grant an `admin`-class permission (REQ-157) MUST NOT be exemptable. The system admin account (UC-02) is independent of this configuration; it is subject to REQ-171 (second factor mandatory after the first bootstrap login).

## Rationale

A rigid "all or nothing" enforcement (REQ-020) forces the operator either to enforce 2FA for all persons or to forgo enforcement entirely. The role-based configuration makes it possible to obligate privileged and regular users to 2FA while selected low-risk roles (e.g., read-only viewers in a protected network) can be exempted if needed. Addresses SH-06's requirement for flexible, practical security configuration.

Security review SEC-10 (2026-10-13): exempting administrator roles removes MFA from exactly the accounts with the highest impact, and "at least one exempt role suffices" removes MFA from all other roles of the same person. Both are reversed in version 0.2.0 (see REQ-171).

## Context

Applies only if REQ-020 is active (`enforce2FA=true`). If enforcement is inactive, REQ-029 is meaningless. The exemption applies to OEA-internal roles (not to external IdP claims). Multiple roles can be exempted. A person is exempt only if all of their active roles are in the exemption list. The exemption check takes place at login (UC-01), directly after the password check and before the required action check (UC-03).

## Type-Specific Fields

### For type=functional

**Inputs** (configuration by the operator in the admin UI or via config file):

| Rule name | Type | Default | Description |
|---|---|---|---|
| `twoFactorExemptRoles` | string[] | `[]` | List of OEA role IDs exempt from the 2FA obligation; empty = no exemption |

**Processing**:

- After successful password check (UC-01 A5): check whether all active roles of the person are in `twoFactorExemptRoles`
- If yes: UC-03 required action is not triggered, even if no active TOTP/passkey credential exists; login grants access directly
- If no: normal required action check (UC-03) per REQ-020
- The system admin account (UC-02) is not configured via `twoFactorExemptRoles`; REQ-171 applies to it

**Outputs**:

- Configuration stored; effective at the next login of the affected persons

**Error Cases**:

- Role ID in `twoFactorExemptRoles` does not exist → validation error on save; no implicit ignoring of unknown IDs
- Role in `twoFactorExemptRoles` grants an `admin`-class permission → validation error on save
- Operator tries to set `twoFactorExemptRoles` without `enforce2FA=true` → permitted (is stored but has no effect; admin UI shows a note)

## Acceptance Criteria

**AC1**:

- Given: `enforce2FA=true` and `twoFactorExemptRoles=['viewer']`
- When: a person whose only role is `viewer` logs in with a password and has no 2FA credential
- Then: they gain access directly, without the UC-03 required action

**AC2**:

- Given: the same configuration
- When: a person without the role `viewer` logs in with a password and has no 2FA credential
- Then: the UC-03 required action is triggered (behavior of REQ-020 unchanged)

**AC3**:

- Given: `twoFactorExemptRoles=['viewer']` and `enforce2FA=true`
- When: a person has both the role `viewer` and `enterprise-architect`
- Then: the person is not exempt and the UC-03 required action is triggered (all roles must be exempt)

**AC4**:

- Given: `twoFactorExemptRoles` contains a non-existent role ID
- When: the operator wants to save
- Then: the configuration is rejected with a specific error message (unknown role ID)

**AC5**:

- Given: the system admin account from UC-02 and any `twoFactorExemptRoles` configuration
- When: the system admin account logs in after the end of the setup phase (ADR-042)
- Then: a second factor is required regardless of this configuration (REQ-171)

**AC6**:

- Given: `twoFactorExemptRoles` contains a role that grants an `admin`-class permission (e.g. `system-admin`)
- When: the operator wants to save
- Then: the configuration is rejected with a specific error message

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: instance with `enforce2FA=true`; person with exempt role + person without exempt role; login flow test
- [x] Measurement tool: test suite of the auth module + configuration module
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-020 (2FA enforcement; REQ-029 is an extension of it); the OEA role model must exist (role IDs referenceable, permission classes per REQ-157)
- **Related**: REQ-171 (MFA for privileged accounts)
- **Downstream effects**: UC-03 (required action is not triggered for exempt persons); UC-01 (login flow checks the exemption)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without a role-based exemption, the operator cannot exempt low-risk roles – all or nothing; limits practicality
- Risk 2: An overly broad exemption list effectively overrides REQ-020 for non-privileged roles – operator responsibility; roles with `admin`-class permissions are protected by AC6

## Trade-offs

- Exemption on a role basis (not on a person basis) is deliberate: exemptions are controllable through OEA governance (role assignment), no ad hoc exclusion of individual persons.
- `twoFactorExemptRoles=[]` (default) yields the original REQ-020 behavior: no exemptions for regular persons.

## Implementation Notes

- `twoFactorExemptRoles` as part of the instance auth configuration (alongside `enforce2FA` from REQ-020)
- Check: at login after the password check; before the UC-03 required action dispatch; O(1) lookup via set intersection
- Admin UI: role dropdown instead of free-text IDs; only existing roles selectable → AC4 satisfied automatically

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from operator requirement (SH-06). Version 0.1.0 allowed exempting admin roles for recovery/setup scenarios; version 0.2.0 forbids this (security review SEC-10). Recovery and setup scenarios are covered by REQ-173 (recovery path and break-glass procedure) instead. The configuration remains a security trade-off that the operator makes deliberately for non-privileged roles.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-10: "all roles exempt" semantics; roles with `admin`-class permissions not exemptable (AC6); system admin subject to REQ-171 |
| 0.3.0 | 2026-10-15 | requirements-engineer | AC5 aligned with ADR-042 (setup phase instead of first bootstrap login) |

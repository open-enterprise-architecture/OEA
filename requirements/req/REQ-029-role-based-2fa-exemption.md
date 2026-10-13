---
id: REQ-029
title: Configure role-based 2FA exemption by the operator
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
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

If instance-wide 2FA enforcement is active (REQ-020), the operator MUST additionally be able to configure which OEA roles are exempt from this obligation; persons whose active role(s) are entirely contained in the exemption list MUST be able to satisfy REQ-020 without a second factor. The system admin account (UC-02) is independent of this configuration – it is always exempt (REQ-020).

## Rationale

Administrators of an OEA instance often need unrestricted access even when 2FA is enforced instance-wide – for example for initial setup, recovery scenarios, or emergency access. A rigid "all or nothing" enforcement (REQ-020) forces the operator either to enforce 2FA for all persons or to forgo enforcement entirely. The role-based configuration makes it possible to obligate regular users to 2FA while privileged roles (e.g., instance administrators) can be exempted if needed. Addresses SH-06's requirement for flexible, practical security configuration.

## Context

Applies only if REQ-020 is active (`enforce2FA=true`). If enforcement is inactive, REQ-029 is meaningless. The exemption applies to OEA-internal roles (not to external IdP claims). Multiple roles can be exempted. A person is exempt if at least one of their active roles is in the exemption list. The exemption check takes place at login (UC-01), directly after the password check and before the required action check (UC-03).

## Type-Specific Fields

### For type=functional

**Inputs** (configuration by the operator in the admin UI or via config file):

| Rule name | Type | Default | Description |
|---|---|---|---|
| `twoFactorExemptRoles` | string[] | `[]` | List of OEA role IDs exempt from the 2FA obligation; empty = no exemption |

**Processing**:
- After successful password check (UC-01 A5): check whether the person has a role that is in `twoFactorExemptRoles`
- If yes: UC-03 required action is not triggered, even if no active TOTP/passkey credential exists; login grants access directly
- If no: normal required action check (UC-03) per REQ-020
- The exemption for the system admin account (UC-02) is hard-coded; it is not configured via `twoFactorExemptRoles` and applies independently of it

**Outputs**:
- Configuration stored; effective at the next login of the affected persons

**Error Cases**:
- Role ID in `twoFactorExemptRoles` does not exist → validation error on save; no implicit ignoring of unknown IDs
- Operator tries to set `twoFactorExemptRoles` without `enforce2FA=true` → permitted (is stored but has no effect; admin UI shows a note)

## Acceptance Criteria

**AC1**:
- Given: `enforce2FA=true` and `twoFactorExemptRoles=['administrator']`
- When: a person with the role `administrator` logs in with a password and has no 2FA credential
- Then: they gain access directly, without the UC-03 required action

**AC2**:
- Given: the same configuration
- When: a person without the role `administrator` logs in with a password and has no 2FA credential
- Then: the UC-03 required action is triggered (behavior of REQ-020 unchanged)

**AC3**:
- Given: `twoFactorExemptRoles=['administrator']` and `enforce2FA=true`
- When: a person has both the role `administrator` and `editor`
- Then: the person is exempt (at least one exempt role suffices)

**AC4**:
- Given: `twoFactorExemptRoles` contains a non-existent role ID
- When: the operator wants to save
- Then: the configuration is rejected with a specific error message (unknown role ID)

**AC5**:
- Given: the system admin account from UC-02 and any `twoFactorExemptRoles` configuration
- When: the system admin account logs in
- Then: it is always exempt, regardless of this configuration

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: instance with `enforce2FA=true`; person with exempt role + person without exempt role; login flow test
- [x] Measurement tool: test suite of the auth module + configuration module
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-020 (2FA enforcement; REQ-029 is an extension of it); the OEA role model must exist (role IDs referenceable)
- **Downstream effects**: UC-03 (required action is not triggered for exempt persons); UC-01 (login flow checks the exemption)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without a role-based exemption, the operator cannot enforce 2FA for regular users only without also forcing admins – all or nothing; limits practicality
- Risk 2: An overly broad exemption list (e.g., all roles exempt) effectively overrides REQ-020 – operator responsibility; no technical safeguard against it

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

Derived from operator requirement (SH-06): admin roles must be exemptable from the 2FA obligation, because several admins may exist and admins need unrestricted access in recovery/setup scenarios. The configuration is a security trade-off that the operator makes deliberately.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |

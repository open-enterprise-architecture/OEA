---
id: REQ-028
title: Configure password policies by the operator
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - local-credential
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

# REQ-028: Configure password policies by the operator

## Statement

The system MUST allow the operator to configure instance-wide password policies that are enforced on every password setting or change; the policies MUST include at least the following rules: minimum length, enforcement of character types (uppercase, lowercase, digits, special characters – individually activatable), expiry period, and password history.

## Rationale

Different organizations have different security requirements (compliance, internal IT policy). The operator (SH-06, Max) must be able to reflect their organization's password policy without OEA prescribing a rigid uniform policy. Concerns all places where passwords are set or checked: initial password (REQ-024), password generator (REQ-027), future password change UC.

## Context

The policies apply instance-wide to all persons with a local password credential. They are stored in the instance configuration (not in the Person object). Changes take effect from the next password setting or change; existing hashes are not retroactively invalidated (except when the expiry period is activated, which then marks all existing passwords as "expires in N days"). Does not apply to the system admin account (UC-02), which has its own credential rules.

## Type-Specific Fields

### For type=functional

**Inputs** (configuration by the operator in the admin UI or via config file):

| Rule name | Type | Default | Description |
|---|---|---|---|
| `minLength` | integer | 12 | Minimum password length in characters |
| `requireUppercase` | boolean | false | At least one uppercase letter (A–Z) |
| `requireLowercase` | boolean | false | At least one lowercase letter (a–z) |
| `requireDigits` | boolean | false | At least one digit (0–9) |
| `requireSpecialChars` | boolean | false | At least one special character from the full ASCII special character set |
| `expiryDays` | integer | 0 | Expiry period in days (0 = no expiry) |
| `historyCount` | integer | 0 | Number of previous passwords that must not be reused (0 = no check) |

**Processing**:
- Policies are checked synchronously on password setting (REQ-024), password change, and generator call (REQ-027)
- If `expiryDays > 0`: the system checks `LocalCredential.lastUsedAt` / `createdAt` at login; if the password has expired, the required action "Change password" is triggered (analogous to UC-03 for 2FA)
- If `historyCount > 0`: the system keeps the last N password hashes per person and rejects reuse (comparison via hash verification, not plain-text comparison)

**Outputs**:
- Validation result on password check: success or a list of errors with the specific unmet rules
- Operator configuration: stored and active for new password operations

**Error Cases**:
- Configuration is inconsistent (e.g., `minLength < number of enforced character types`) → validation error when saving the configuration, not only at first password use
- `historyCount` requires storing old hashes that no longer exist as `LocalCredential` with `status=active` → separate `password_history` table

## Acceptance Criteria

**AC1**:
- Given: the operator enables `requireUppercase`, `requireLowercase`, `requireDigits`, `requireSpecialChars` and sets `minLength=16`
- When: a person sets a password that contains only lowercase letters
- Then: it is rejected with an error message that names all unmet rules

**AC2**:
- Given: the operator sets `expiryDays=90`
- When: a person logs in 91 days after the last password change
- Then: the required action "Change password" is triggered; no full access until the change

**AC3**:
- Given: the operator sets `historyCount=5`
- When: a person tries to set a password that matches one of the last 5 passwords
- Then: it is rejected with a note on the reuse prohibition

**AC4**:
- Given: the operator sets an inconsistent configuration (`minLength=3`, all 4 character types enforced)
- When: the configuration is to be saved
- Then: it is rejected with a specific error message (minimum length must be ≥ 4)

**AC5**:
- Given: all rules are at default (false / 0)
- When: a password with the minimum length is set
- Then: it is accepted, even if it contains only lowercase letters

**AC6**:
- Given: `requireSpecialChars=true`
- When: a password is checked
- Then: all printable ASCII special characters are accepted as valid (no restricted subset check)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: set the configuration with various rule combinations; check passwords against rules; test expiry period with a simulated date; history check
- [x] Measurement tool: test suite of the auth module + admin configuration module
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-024 (password is set initially – first place where policies take effect)
- **Downstream effects**: REQ-027 (generator reads policies); REQ-011 (login checks expiry period); future password change UC (checks history and policies)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without configurable policies, compliance requirements (the organization's IT security policies) cannot be reflected – rejection by corporate IT, medium severity
- Risk 2: Without a history check, users can rotate the same password endlessly – weakens the expiry policy, low severity

## Trade-offs

- `historyCount > 0` requires storing password hashes that are no longer active (increased storage need, additional table). Acceptable, since hashes are small and the history is limited.
- Overly strict policies (all 4 types + short minimum length) increase the error rate when setting passwords – mitigated by meaningful error messages and the generator (REQ-027).

## Implementation Notes

- Store policies as instance-wide configuration (YAML/ENV/DB table `instance_config`), not per person
- Validation logic as a standalone, purely functional check function (input: plain-text password + policy object → output: list of unmet rules); reusable for REQ-024, REQ-027, password change UC
- Password history: dedicated table `password_history` (`person_id`, `password_hash`, `created_at`); hash comparison via verification (not plain text); automatically trim to `historyCount` entries
- Expiry check: on every login via REQ-011; `LocalCredential.last_changed_at` ≤ now - expiryDays → required action

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from operator requirement (SH-06). The policies are deliberately all optional (default: off / 0) so that self-hosting instances without an explicit IT policy work without immediately running into hard rules. Operators activate specifically what their organization needs.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |

---
id: REQ-011
title: Username/password login without second factor (minimal variant)
type: functional
priority: could
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
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
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-011: Username/password login without second factor (minimal variant)

## Statement

The system MAY allow persons to log in with username/password only, without a second factor; this variant MUST be explicitly enabled at instance level and MUST NOT be the default state of a newly installed OEA instance.

## Rationale

Concept §21.8 deliberately names this variant as the "risky minimal variant" for the simplest self-hosting case (e.g., Kurt as a single user in an SME who wants to operate neither an external IdP nor passkey/TOTP infrastructure). UC-01, Alternative A5. Because it offers the weakest security level of all mechanisms, it is modeled as an optional exception to be enabled deliberately, not as a default.

## Context

Concerns exclusively instances that explicitly forgo a second factor and passkeys.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Username, password

**Processing**:

- Check the password hash against the person's stored hash
- Check whether this authentication variant is enabled for the instance; if not, it is treated like a non-existent mechanism

**Outputs**:

- If the password is correct: continue with person/role lookup (see REQ-001, step 6)

**Error Cases**:

- Wrong password → generic error message (see REQ-006)
- Variant is disabled instance-wide → mechanism is not offered

## Acceptance Criteria

**AC1**:

- Given: an instance on which this variant has been explicitly enabled, and a person with a correct password
- When: they log in
- Then: they receive a session according to their active role assignments

**AC2**:

- Given: a newly installed, unconfigured OEA instance
- When: its authentication setup is checked
- Then: this variant is disabled by default

**AC3**:

- Given: every successful or failed login via this variant
- When: the audit log entry is created (see REQ-005)
- Then: the entry shows that no second factor was used

## Verification Method

- [x] Method: test (automated) + inspection (Default-Konfiguration)
- [x] Test setup: login attempts with the variant enabled and disabled; check of the default configuration of a fresh installation
- [x] Measurement tool: test suite of the auth module, configuration inspection
- [x] Pass criterion: AC1–AC3 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-024 (password enrollment must have been completed beforehand)
- **Downstream effects**: none known
- **Conflicts**: [REQ-020](./REQ-020-enforce-second-factor.md) – if instance-wide enforcement of a second factor is active, this variant must not be enableable for regular persons

## Risks of Non-Fulfillment

- Risk 1: If this variant were active by default, every OEA instance would unintentionally ship with the weakest authentication level – severe
- Risk 2: Without this option, operators without passkey/TOTP infrastructure (e.g., Max in an SME) could not operate the tool at all without an external IdP

## Trade-offs

- vs. security: explicitly weaker than REQ-009 (passkey) and REQ-010 (TOTP); the existence of this option is a deliberate usability/self-hosting compromise, not a recommended default

## Implementation Notes

- Enabling should be a deliberate configuration decision accompanied by a warning (e.g., explicitly asked in the setup wizard, not hidden in a config file)
- Password hashing as in REQ-010 (e.g., Argon2id), rate limiting against brute force

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, Alternative A5. Priority deliberately `could` instead of `should`, because this variant is not intended for the productive standard case but for the lowest-barrier self-hosting case.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |

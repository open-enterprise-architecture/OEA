---
id: REQ-019
title: Ability to deactivate the local system admin account when a remote mapping is active
type: business-rule
priority: should
status: proposed
version: 0.2.0
created: 2026-06-24
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
    - system-admin-account
  business_rules:
    - BR-02
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-019: Ability to deactivate the local system admin account when a remote mapping is active

## Statement

As soon as at least one functional remote bootstrapping mapping (REQ-014) is configured for an instance, the local system admin account (REQ-013) SHOULD be deliberately deactivatable by a system admin.

## Rationale

Organizations with established IdP governance do not want to leave an additional access outside central identity management open after initial configuration. Business Rule BR-02 from `system-admin-account.md`; ADR-006, Negative Consequences.

## Context

Concerns the state after successful remote bootstrapping (UC-02, A1 or A2) – the local credential remains active by default until it is deliberately deactivated.

## Type-Specific Fields

### For type=business-rule

**Trigger**: onUpdate (manual action by a system admin)

**Affected Business Objects**: [system-admin-account](../../business-objects/system-admin-account.md)

**Formal Statement** (simplified):

```text
if SystemAdminAccount.mode == "remote" and mapping.isFunctional:
    allow(deactivate(localSystemAdminAccount))
```

**Example**: After Max has set up a working remote mapping to an Entra ID group, he deactivates the original local bootstrap account to reduce the attack surface.

## Acceptance Criteria

**AC1**:

- Given: an active local system admin account and a functional remote mapping
- When: a system admin deactivates the local account
- Then: login via the local account is no longer possible afterwards, while the remote path continues to work

**AC2**:

- Given: no functional remote mapping is configured
- When: an attempt is made to deactivate the local account
- Then: the system warns of a possible complete lockout but does not necessarily block the action (operator decision)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: deactivation of the local account with and without a functional remote mapping
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-013, REQ-014
- **Downstream effects**: none known
- **Conflicts**: increases the risk from UC-02 Exception Flow E4 (complete lockout) if deactivated without consideration – hence AC2 as a warning instead of a hard block

## Risks of Non-Fulfillment

- Risk 1: Without a deactivation option, an additional access that cannot be controlled via central IdP governance remains permanently – compliance/security risk for organizations with strict requirements

## Trade-offs

- vs. lockout risk (E4): deactivation without consideration and without functional remote access can make the instance completely unadministrable – hence warning instead of prohibition

## Implementation Notes

- Realigned by OpenSpec change `us-019-admin-safeguards` (owner decision 2026-10-28): without another usable path to `system-admin` the deactivation is rejected with `409` (`last-admin-path`, ADR-032 SoD-7) instead of only warning. A mapping to `system-admin` counts as a usable path only after it has granted the role in at least one login. A deactivated account is reactivated only by the break-glass command `admin recover`.
- "Functional" for the remote mapping should rely on REQ-018 (warning on empty claim) where possible, but cannot ultimately be verified 100% at runtime

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: OpenSpec change `us-019-admin-safeguards`
- Tests: `LocalAdminAccountTests`, Cucumber `identity/access-administration.feature`, `identity/local-admin-deactivation.feature` (US-019)
- Verification status: not-verified

## Notes

Derived from UC-02, open question on BR-02 implementation, and ADR-006, Follow-up Decisions.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-02 |
| 0.2.0 | 2026-10-28 | requirements-engineer | Realigned: 409 instead of a warning for the last path to `system-admin` (SoD-7, change `us-019-admin-safeguards`) |

---
id: REQ-003
title: Restricted access without an active role assignment
type: business-rule
priority: must
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
    - role
  business_rules:
    - BR-01
  stakeholders:
    - SH-03
  concept:
    - concept/20-entities/08-organization-roles-persons.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-003: Restricted access without an active role assignment

## Statement

A successfully authenticated person WITHOUT an active role assignment MUST NOT access any repository content other than publicly readable content.

## Rationale

The concept constraint `person-minimum-role` (§8.8) is modeled as a warning, not a hard block – a person without a role should be able to log in (e.g., for onboarding) but receive no permissions beyond the minimum. UC-01, Exception Flow E3.

## Context

Concerns the case that a person was successfully authenticated at the identity provider but has (not yet) an active `RoleAssignment` in the EA repository.

## Type-Specific Fields

### For type=business-rule

**Trigger**: onLogin

**Affected Business Objects**: [person](../../business-objects/person.md), [role](../../business-objects/role.md)

**Formal Statement** (simplified):

```text
if Person.fillsRoles.filter(active=true).isEmpty():
    Session.permissions = PUBLIC_READ_ONLY
```

**Example**: A newly invited person has not yet been given a role assignment. They can log in but only see publicly released content until a person with authorization rights assigns them a role.

## Acceptance Criteria

**AC1**:

- Given: a person is successfully authenticated but has no active role assignment
- When: they access the repository
- Then: they receive access to publicly readable content only, and a notice that no role is assigned

**AC2**:

- Given: a person is subsequently given an active role assignment
- When: they log in again or the session is refreshed
- Then: their permissions reflect the new role

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: login with a test person without RoleAssignment, check the resulting permissions
- [x] Measurement tool: test suite of the auth/authorization module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001
- **Downstream effects**: none known
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without this rule, newly authenticated persons could accidentally receive full access – security risk, severe
- Risk 2: If the rule is implemented as a hard block instead of a soft limit, it needlessly blocks the onboarding of new persons

## Trade-offs

- vs. usability: a hard block at login without a role would be simpler to implement, but contradicts the concept constraint (warning instead of error) and would make onboarding harder

## Implementation Notes

- A session refresh mechanism is needed so that newly assigned roles take effect without a full re-login (see REQ-001 Implementation Notes on caching)

## Implementation

- ADR(s): none
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, Exception Flow E3, and the concept constraint `person-minimum-role`.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |

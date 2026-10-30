---
id: REQ-003
title: Restricted access without an active role assignment
type: business-rule
priority: must
status: proposed
version: 0.4.0
created: 2026-06-24
last_updated: 2026-10-30
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-23
  business_objects:
    - person
    - role
  business_rules:
    - BR-01
  stakeholders:
    - SH-03
  concept:
    - concept/20-entities/08-organization-roles-persons.md
  adrs:
    - ADR-032
supersedes: []
superseded_by: []
---

# REQ-003: Restricted access without an active role assignment

## Statement

A successfully authenticated person WITHOUT an active authorization role (ADR-032: neither an active local role assignment nor a role derived from an IdP group mapping) MUST NOT access any repository content; the session MUST hold no permission of the permission model (REQ-157) and MUST only allow viewing the own profile and a notice that no role is assigned.

## Rationale

The concept constraint `person-minimum-role` (§8.8) is modeled as a warning, not a hard block – a person without a role should be able to log in (e.g., for onboarding) but receive no permissions beyond the minimum. UC-01, Exception Flow E3.

## Context

Concerns the case that a person was successfully authenticated at the identity provider but has (not yet) an active authorization role. Since ADR-032, authorization roles are separate from business roles modeled as EA content: a `RoleAssignment` connection between a Person entity and a business Role entity (`Person.fillsRoles`, concept §8.3) grants no permission and does not count as an active role for this rule.

Security review SEC-05 (2026-10-13): "publicly readable content" and `PUBLIC_READ_ONLY` were defined nowhere; there is no anonymous or "public" access in v1 (owner decision Q1, REQ-163). Version 0.2.0 therefore grants no repository content without a role (deny by default, REQ-158). Role changes take effect within 60 seconds (REQ-164).

## Type-Specific Fields

### For type=business-rule

**Trigger**: onLogin

**Affected Business Objects**: [person](../../business-objects/person.md), [role](../../business-objects/role.md)

**Formal Statement** (simplified):

```text
activeRoles(principal, now) =
      { a.role | a in roleAssignments(principal),          # local authorization role assignments (ADR-032 §7.1)
                 a.validFrom <= now,
                 a.validTo is null or now < a.validTo,
                 a.revokedAt is null,
                 principal.status = active }
    ∪ { m.role | m in idpGroupMappings,                   # IdP group mappings (ADR-032 §7.2)
                 m.active,
                 m.groupIdentifier in session.evaluatedGroupClaims(m.issuer) }
if activeRoles(principal, now).isEmpty():
    effectivePermissions(principal) = {}   # own profile and "no role assigned" notice only
# Person.fillsRoles (business roles, EA content) is not part of this evaluation
```

**Example**: A newly invited person has not yet been given an authorization role. They can log in but see only a notice that no role is assigned until a principal with `role:assign` assigns them a role (UC-23), or until they log in as a member of an IdP group mapped to a role. Modeling them as "Enterprise Architect" in the EA repository does not change this.

## Acceptance Criteria

**AC1**:

- Given: a person is successfully authenticated but has no active role assignment
- When: they access the repository
- Then: every request for repository content (entities, catalogs, diagrams, dashboards, portal views) is rejected with 403, and the client shows a notice that no role is assigned

**AC2**:

- Given: a person is subsequently given an active role assignment
- When: they make the next request after at most 60 seconds
- Then: their permissions reflect the new role (REQ-164)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: login with a test person without authorization role assignment and without mapped IdP group (optionally with a business RoleAssignment connection in the EA model), check the resulting permissions
- [x] Measurement tool: test suite of the auth/authorization module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001, REQ-157 (permission model), REQ-158 (deny by default)
- **Downstream effects**: none known
- **Related**: REQ-163 (no anonymous access), REQ-164 (propagation of role changes)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without this rule, newly authenticated persons could accidentally receive full access – security risk, severe
- Risk 2: If the rule is implemented as a hard block instead of a soft limit, it needlessly blocks the onboarding of new persons

## Trade-offs

- vs. usability: a hard block at login without a role would be simpler to implement, but contradicts the concept constraint (warning instead of error) and would make onboarding harder

## Implementation Notes

- Confirmed by OpenSpec change `us-008-login-latency`: covered by the scenarios "Person without any role" (`identity/idp-roles`) and "Assignment effective within 60 seconds" (`identity/role-assignments`); group changes at the identity provider follow the IdP re-evaluation (owner decision 2026-10-30).
- Newly assigned roles must take effect without a full re-login; per ADR-032 §7, permissions are not embedded in tokens but resolved server-side per request (cache ≤ 60 s)

## Implementation

- ADR(s): ADR-032 (authorization model)
- Implemented by: OpenSpec change `us-008-login-latency`
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, Exception Flow E3, and the concept constraint `person-minimum-role`.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-05/SEC-06 / owner decision Q1: undefined "publicly readable content" removed – no repository content without a role; role changes effective within 60 s |
| 0.3.0 | 2026-10-13 | requirements-engineer | ADR-032: statement and formal statement refer to authorization roles (local assignments and IdP group mappings) instead of `Person.fillsRoles`; business roles explicitly excluded; reference to UC-23 |
| 0.4.0 | 2026-10-30 | requirements-engineer | Coverage confirmed (change `us-008-login-latency`) |

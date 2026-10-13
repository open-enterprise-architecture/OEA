---
id: REQ-018
title: Warning on empty or non-existent remote admin claim
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
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

# REQ-018: Warning on empty or non-existent remote admin claim

## Statement

When configuring a remote bootstrapping mapping (REQ-014), the system SHOULD warn if the specified group/role verifiably does not exist at the identity provider at configuration time or has no members.

## Rationale

A mapping to an empty or non-existent group would mean that nobody receives system admin permissions via this path – a lockout risk if this is the only configured access. UC-02, Exception Flow E2.

## Context

Takes effect during configuration of the remote mapping (UC-02, A1, step 2), not only at a later login attempt.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Configured provider, claim type, and claim value for the remote mapping

**Processing**:

- Where technically possible (depending on provider API access): check whether the specified group/role exists and has at least one member
- Display a warning if the check is negative or cannot be performed

**Outputs**:

- Warning to the operator (Max), together with the advice to additionally keep the local bootstrapping path (REQ-013) active

**Error Cases**:

- Provider API for the check unreachable → warning is output as "check not possible", configuration is not blocked (no hard stop)

## Acceptance Criteria

**AC1**:

- Given: a remote mapping to a group that is empty or non-existent at configuration time
- When: Max saves the configuration
- Then: he receives a warning that this mapping currently grants nobody access

**AC2**:

- Given: the same situation
- When: Max acknowledges the warning
- Then: he can still save the configuration (no hard block), ideally with an active local fallback (REQ-013)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: configure a mapping against a test IdP with an empty/non-existent group
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-014
- **Downstream effects**: none known
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without a warning, an operator could accidentally configure the instance so that nobody gets system admin access via the remote path anymore – medium severity, mitigated by REQ-013 as a fallback

## Trade-offs

- vs. implementation effort: a reliable check requires provider-specific API access (e.g., Microsoft Graph for Entra ID) that goes beyond plain OIDC standards compliance

## Implementation Notes

- The check is best-effort, not a mandatory hard block, since not every provider offers a corresponding query API

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-02, Exception Flow E2.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-02 |

---
id: REQ-014
title: Remote bootstrapping via IdP claim mapping
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

# REQ-014: Remote bootstrapping via IdP claim mapping

## Statement

The system SHOULD make it possible to grant system admin permissions via a mapping to a group or role claim of a configured external identity provider (Entra ID or Authentik, see ADR-006), without having to create a local credential for this.

## Rationale

Organizations with established IdP governance (e.g., SH-05-style corporate group environments, but also SH-06 as an operator connected to an existing OIDC/LDAP) want to control system admin permissions via central identity management, not via an additional local credential. UC-02, Alternative A1.

## Context

Assumes that an OIDC provider is already configured for the instance (Entra ID or Authentik). The mapping is stored as part of the [system admin account](../../business-objects/system-admin-account.md) with `mode: remote`.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Configuration: provider (Entra ID or Authentik), `claimType` (group | role), `claimValue` (name of the group/role)
- Identity token with claims on subsequent logins

**Processing**:

- Store the mapping as a system admin account with `mode: remote`
- On every login: check whether the identity token contains the configured claim with the configured value
- On a match: the session receives system admin permissions in addition to any existing regular roles

**Outputs**:

- System admin account record with `mode: remote`
- On a matching login: session with system admin permissions

**Error Cases**:

- Identity token does not contain the configured claim or contains it with a different value → no system admin permissions, the regular login flow (UC-01) remains unaffected

## Acceptance Criteria

**AC1**:

- Given: a configured claim mapping to an IdP group
- When: a person with this group claim in the identity token logs in
- Then: the session receives system admin permissions

**AC2**:

- Given: the same mapping
- When: a person without this claim logs in
- Then: the session does not receive system admin permissions via this path

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: test IdP with configurable claims, login with and without a matching claim
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001 (OIDC login must work before claims can be checked)
- **Downstream effects**: REQ-018 (warning on empty claim), REQ-019 (ability to disable local access)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without remote mapping, organizations with strict IdP governance would still have to maintain a local credential – contradicts their security policies, medium severity

## Trade-offs

- vs. REQ-013 (local bootstrapping): remote mapping depends on the availability and correct configuration of the IdP; both paths should remain available in parallel (see UC-02, A2)

## Implementation Notes

- Claim mapping conventions per provider (Entra ID `groups` claim vs. Authentik groups/properties) are technical specification, not part of this requirement (see ADR-006, Follow-up Decisions)

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-02, Alternative A1.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-02 |

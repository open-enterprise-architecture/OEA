---
id: REQ-014
title: Remote bootstrapping via IdP claim mapping
type: functional
priority: should
status: proposed
version: 0.3.0
created: 2026-06-24
last_updated: 2026-10-28
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

Organizations with established IdP governance (e.g., SH-05-style corporate group environments, but also SH-06 as an operator connected to an existing OIDC provider; LDAP is not supported, owner decision Q6 of the 2026-10 security review) want to control system admin permissions via central identity management, not via an additional local credential. UC-02, Alternative A1.

## Context

Assumes that an OIDC provider is already configured for the instance (Entra ID or Authentik). The mapping is stored as part of the [system admin account](../../business-objects/system-admin-account.md) with `mode: remote`.

Security review SEC-08: group display names are mutable and not unique (e.g. user-created Microsoft 365 groups), so a mapping by name allows privilege escalation. The mapping therefore references immutable, provider-controlled identifiers; details in REQ-167.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Configuration: provider (Entra ID or Authentik), `claimType` (group | role), `claimValue` (immutable, provider-controlled identifier of the group/role, e.g. Entra group object ID or app role value, Authentik group UUID – never a display name)
- Identity token with claims on subsequent logins

**Processing**:

- Validate on save that `claimValue` matches the identifier format of the selected provider (REQ-167)
- Store the mapping as a system admin account with `mode: remote`
- On every login: check that the identity token was issued by the configured issuer of the mapped provider and contains the configured claim with the configured value
- On a match: the session receives system admin permissions in addition to any existing regular roles
- If the claim is missing or truncated (e.g. Entra groups overage indicator): fail closed – no system admin permissions – and log a warning event

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

**AC3**:

- Given: the admin enters a group display name instead of an immutable identifier as `claimValue`
- When: the mapping is saved
- Then: it is rejected with an explanatory message

**AC4**:

- Given: an identity token from an issuer other than the mapped provider, or a token with a groups overage indicator instead of the groups claim
- When: the person logs in
- Then: the session does not receive system admin permissions and a warning event is logged

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: test IdP with configurable claims, login with and without a matching claim
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001 (OIDC login must work before claims can be checked), REQ-166 (token validation), REQ-167 (immutable claim values)
- **Downstream effects**: REQ-018 (warning on empty claim), REQ-019 (ability to disable local access)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without remote mapping, organizations with strict IdP governance would still have to maintain a local credential – contradicts their security policies, medium severity

## Trade-offs

- vs. REQ-013 (local bootstrapping): remote mapping depends on the availability and correct configuration of the IdP; both paths should remain available in parallel (see UC-02, A2)

## Implementation Notes

- Realigned by OpenSpec change `us-019-admin-safeguards` (owner decision 2026-10-28): the "configuration endpoint for claim mapping" is the IdP group mapping operation (`/admin/idp-group-mappings`) with role `system-admin` (US-147, ADR-032 DM-6); there is no separate remote admin setting.
- Claim mapping conventions per provider (Entra ID `groups` claim vs. Authentik groups/properties) are technical specification, not part of this requirement (see ADR-006, Follow-up Decisions)

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: OpenSpec change `us-019-admin-safeguards` (US-014 counted as realized through US-147)
- Tests: `IdpRolesTests`, Cucumber `identity/idp-roles.feature` (US-014 AC1, AC2)
- Verification status: not-verified

## Notes

Derived from UC-02, Alternative A1.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-02 |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-08: immutable claim identifiers, issuer binding, fail-closed overage handling (AC3, AC4); LDAP wording removed (owner decision Q6) |
| 0.3.0 | 2026-10-28 | requirements-engineer | Realigned to the IdP group mappings with role `system-admin` (change `us-019-admin-safeguards`) |

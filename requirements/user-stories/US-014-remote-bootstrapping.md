# US-014: Remote Bootstrapping via IdP Claim Mapping

**ID**: US-014
**Story Points**: 5
**Status**: done
**Sprint**: S3
**Implementation**: OpenSpec change `us-019-admin-safeguards`, commit 065c58b (#117) – realized through the IdP group mappings of US-147 with role `system-admin`; AC1 and AC2 verified by Cucumber scenarios in `identity/idp-roles.feature`

## Story

As an operator with an already configured Entra ID or Authentik, I want to be able to assign system admin permissions via a group/role claim, so that I do not have to maintain an additional local account.

## Reference

**Use Case**: [UC-02: System Admin Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-014: Remote Bootstrapping via IdP Claim Mapping](../req/REQ-014-remote-bootstrapping.md)

## Acceptance Criteria

**AC1**:

- Given: a configured claim mapping to an IdP group
- When: a person with this group claim in the identity token signs in
- Then: the session receives system admin permissions

**AC2**:

- Given: the same mapping
- When: a person without this claim signs in
- Then: the session receives no system admin permissions via this route

## Technical Notes

- Affected components: auth module (claim evaluation), instance configuration
- Affected EntityTypes/Relations: `SystemAdminAccount` with `mode: remote`
- API endpoints: configuration endpoint for claim mapping
- Database changes: storage of `provider`, `claimType`, `claimValue`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (test IdP with configurable claims)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001 (OIDC login must work), US-013
- Blocks: US-018, US-019

## Notes

Covers alternative A1 from UC-02. Claim mapping conventions per provider are a technical specification, not part of this story.

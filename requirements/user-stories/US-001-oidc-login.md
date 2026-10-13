# US-001: OIDC Login with Session Creation

**ID**: US-001
**Story Points**: 8
**Status**: backlog
**Sprint**: unassigned

## Story

As a person with a role stored in the repository, I want to sign in via the configured OIDC identity provider, so that I receive a session with my active permissions, without an already existing IdP session being queried again.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-001: OIDC-based Sign-in and Session Creation](../req/REQ-001-oidc-login-session.md)

## Acceptance Criteria

**AC1**:

- Given: a person without a valid session accesses a protected resource
- When: they successfully authenticate with the configured OIDC provider
- Then: they receive a session that reflects their active role assignments

**AC2**:

- Given: a person already has a valid session
- When: they access another protected resource
- Then: there is no renewed redirect to the identity provider

**AC3**:

- Given: a token issued by the external IdP with a validity period set by the IdP
- When: the session is created
- Then: this validity period is adopted unchanged

**AC4** (optional, configurable):

- Given: an instance for which online token validation against the IdP (e.g. token introspection) is configured, and a token that has already been revoked at the IdP
- When: the login process is completed
- Then: session creation is refused

## Technical Notes

- Affected components: auth module, session management
- Affected EntityTypes/Relations: `Person`, `Role`, `RoleAssignment`
- API endpoints: OIDC redirect/callback endpoints, session creation
- Database changes: session storage; lookup index on `Person.externalReference`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration test against mock OIDC provider)
- [ ] Documentation updated
- [ ] ADR created if architecture decision (see ADR-006)
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: ADR-006 (auth stack choice, already accepted)
- Blocks: US-002, US-003, US-004, US-007

## Notes

Covers the basic flow and alternative A1 from UC-01. AC4 (optional online token validation) can be added in a separate sprint if needed, without blocking the core login flow.

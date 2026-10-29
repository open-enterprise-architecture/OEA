# US-001: OIDC Login with Session Creation

**ID**: US-001
**Story Points**: 8
**Status**: done
**Sprint**: S1
**Implementation**: OpenSpec change `us-001-oidc-login`, commit a5bbdb8 (#35) – AC1–AC3 implemented (OIDC login with PKCE through the stored providers, roles from IdP group mappings); AC4 (optional online validation) tracked as issue [#122](https://github.com/open-enterprise-architecture/OEA/issues/122)

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
- Then: OEA never extends this validity period; the session ends at the first of the OEA idle timeout, the OEA absolute lifetime (REQ-165) and the expiry or rejection of the IdP refresh token (owner decision 2026-10-15)

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

Set to done on 2026-10-29 in the re-scoping of S4 (owner decision): AC1–AC3 are covered by the specs `identity/oidc-login` and `identity/sessions`; the optional AC4 is tracked as issue [#122](https://github.com/open-enterprise-architecture/OEA/issues/122).

AC3 clarified on 2026-10-15 (owner decision, change `us-001-oidc-login`): "adopted unchanged" meant that OEA does not prolong IdP tokens; the OEA session limits of REQ-165 still apply.

Covers the basic flow and alternative A1 from UC-01. AC4 (optional online token validation) can be added in a separate sprint if needed, without blocking the core login flow.

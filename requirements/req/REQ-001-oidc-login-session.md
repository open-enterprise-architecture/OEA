---
id: REQ-001
title: OIDC-based login and session creation
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-24
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - person
    - role
  business_rules: []
  stakeholders:
    - SH-03
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-001: OIDC-based login and session creation

## Statement

The system MUST redirect a person who has no valid session to a configured OIDC identity provider for authentication and, after successful authentication, create a session bound to the person and their active role assignments. In addition to the local signature check of the identity token, the system MAY optionally perform an online validation against the identity provider (e.g., token introspection per RFC 7662 or the userinfo endpoint) if this is configured for the instance.

## Rationale

Login is a prerequisite for every use case that goes beyond publicly readable content (see UC-01). Without standards-compliant authentication, no role-based authorization (RBAC, concept §21.8) can take place.

## Context

Concept §21.8 defines OAuth 2.0/OIDC as the standard authentication mechanism. UC-01, Basic Flow steps 2–7, and Alternative A1 (an existing valid session is not re-authenticated).

**Token validity**: With an external IdP (Entra ID, Authentik, see ADR-006), the identity provider dictates the validity period of access, ID, and refresh tokens; OEA never extends it. A session established through an IdP ends at the first of the OEA idle timeout, the OEA absolute lifetime (REQ-165) and the expiry or rejection of the IdP refresh token (owner decision 2026-10-15). This does not apply to local authentication – see REQ-012.

## Type-Specific Fields

### For type=functional

**Inputs**:

- HTTP request without a valid session to a protected resource
- Authorization code from the identity provider after a successful redirect

**Processing**:

- Check for an existing valid session; if present, no new redirect (A1)
- Redirect to the configured OIDC provider with state/nonce protection
- Redeem the authorization code for an identity token (access token, ID token)
- Token validation: by default a local signature check against the provider's JWKS; optionally (configurable) an additional online check with the IdP for real-time revocation detection
- Match the identity claims against the referenced [Person](../../business-objects/person.md) object
- Load the active role assignments (RoleAssignment with `validFrom <= today <= validTo`)
- Create a session bound to the person and active roles

**Outputs**:

- Valid session/access token
- Login confirmation with a role-based landing area

**Error Cases**:

- Identity provider rejects authentication → see REQ-006 (no session, generic error message)
- Person not found/offboarded → see REQ-004
- Identity provider unreachable → technical error message, no session established, retry possible

## Acceptance Criteria

**AC1**:

- Given: a person without a valid session calls a protected resource
- When: they successfully authenticate with the configured OIDC provider
- Then: they receive a session that reflects their active role assignments

**AC2**:

- Given: a person already has a valid session
- When: they call another protected resource
- Then: no new redirect to the identity provider occurs

**AC3**:

- Given: an instance for which online token validation against the IdP is configured, and an identity token that is validly signed locally but has already been revoked at the IdP
- When: the login process is completed
- Then: session creation is refused

**AC4**:

- Given: a token issued by the external IdP with a validity period set by the IdP
- When: OEA creates the session
- Then: OEA never extends this validity period; the OEA session additionally ends at the idle timeout or absolute lifetime of REQ-165, whichever comes first

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: integration test against a test OIDC provider (e.g., mock IdP), including session cookie/token check
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1 and AC2 green, no redirect when a session exists
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: none
- **Downstream effects**: REQ-002 (API key as alternative), REQ-003, REQ-004, REQ-007
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without standards-compliant OIDC, no uniform RBAC connection to the organization's existing identity providers – severe
- Risk 2: Custom-built authentication increases the attack surface (credential handling in own code) – severe

## Trade-offs

- vs. self-hosting simplicity (SH-03 Kurt, single-user SME): OIDC requires an identity provider; a lightweight default provider for single-user operation should be considered in the ADR on the auth stack choice.
- vs. REQ-008 (login latency): the optional online token validation against the IdP adds an extra network round trip and is therefore disabled by default; when enabled, the latency budget from REQ-008 must be reassessed.

## Implementation Notes

- Known technical challenges: state/nonce handling against CSRF, token refresh, mapping identity claims to existing Person objects with claim formats that vary per provider
- The concrete provider choice is not part of this requirement but of a still-open ADR

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, Basic Flow and Alternative A1.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |
| 0.2.0 | 2026-10-13 | Lukas Mathis | AC4: IdP token lifetime is an upper bound; OEA session limits (REQ-165) may shorten it (security review SEC-06) |
| 0.3.0 | 2026-10-15 | Lukas Mathis | Token validity: the session ends at the first of OEA idle timeout, OEA absolute lifetime and the IdP refresh token (change `us-001-oidc-login`) |

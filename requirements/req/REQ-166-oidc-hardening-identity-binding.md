---
id: REQ-166
title: OIDC relying-party hardening with mandatory PKCE and immutable identity binding
type: constraint
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
    - ADR-009
    - ADR-036
supersedes: []
superseded_by: []
---

# REQ-166: OIDC Relying-Party Hardening with Mandatory PKCE and Immutable Identity Binding

## Statement

Every OIDC client of OEA – the Electron client as a public client per RFC 8252 (system browser and loopback redirect) and the web portal – **MUST** use the authorization code flow with PKCE (`S256`); the system **MUST** validate `iss`, `aud`, `exp`, `nbf`, `nonce`, `azp` and the signing algorithm of every ID token, and **MUST** bind a person to the immutable pair (`iss`, `sub`) instead of mutable claims such as e-mail.

## Rationale

Matching by the `email` claim allows account takeover where e-mail is mutable or unverified ("nOAuth" pattern). Without PKCE an intercepted authorization code of the public Electron client can be redeemed. Without audience validation tokens for other applications of the same IdP are accepted.

## Context

Source: security review SEC-07 (proposal PR-10); owner decision (2026-10-13, additional): PKCE (S256) is mandatory for every OIDC client including the Electron client and the web portal, priority must. For Entra ID, `tid` + `oid` are the stable identifiers; single-tenant configuration is the default.

## Type-Specific Fields

**Type of constraint**: technical

**Source**: OpenID Connect Core 1.0 §3.1.3.7, RFC 7636 (PKCE), RFC 8252 (native apps), RFC 9700 (OAuth 2.0 security BCP), owner decision 2026-10-13

**Binding strength**: hard

**Impact of non-compliance**: account takeover, code interception, token confusion

## Acceptance Criteria

**AC1**:

- Given: a valid ID token from the configured issuer whose `sub` differs from the stored binding but whose `email` matches a person
- When: login completes
- Then: no session is created for that person

**AC2**:

- Given: an ID token with a wrong `aud`, an expired `exp`, a mismatching `nonce`, or `alg: none`/HS256
- When: login completes
- Then: no session is created

**AC3**:

- Given: an authorization request of the Electron client or the web portal without a PKCE `code_challenge` (method `S256`), or a token request with a wrong `code_verifier`
- When: processed
- Then: authentication fails

**AC4**:

- Given: first-time linking by e-mail is enabled
- When: an ID token has `email_verified` false or absent
- Then: no link is created, and every link created by e-mail requires administrator confirmation

**AC5**:

- Given: the Electron client
- When: it starts a login
- Then: it opens the system browser and receives the code on a loopback redirect URI; no embedded web view handles IdP credentials

## Verification Method

- [ ] Method: test (automated, against the development IdP – Keycloak Testcontainer with the versioned dev realm, ADR-036 – plus crafted invalid tokens) + inspection of the Electron login flow
- [ ] Test setup: tokens obtained only through the real authorization code flow with PKCE; wrong `aud` via the client `oea-test-foreign-app`, changed `sub` with matching e-mail via `dev-email-twin`, unverified e-mail via `dev-email-unverified`, expiry via the injectable application clock, `nonce` mismatch by manipulating the authorization request; tokens with `alg: none` or `HS256` crafted in test code; the application never trusts a key other than the IdP's JWKS. The Authentik reference suite runs nightly and before every release
- [ ] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-001, ADR-006, ADR-036 (development and test IdP)
- **Follow-on effects**: REQ-004 (matching on (`iss`, `sub`)), REQ-014, REQ-167
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: login as another person – high

## Implementation Notes

- Identity binding (`iss`, `sub`) needs persistent fields on the person (follow-up for business engineer: `person.md`, `docs/data-model.puml`; candidates `external_ref_system/external_ref_id`)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-07 (PR-10) with owner decision on mandatory PKCE |
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-036 CH-4: verification against the Keycloak Testcontainer plus crafted invalid tokens instead of a mock IdP; test setup added |

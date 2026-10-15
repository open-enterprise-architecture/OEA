# Spec Delta

## Purpose

Defines how a person logs in to OEA through a configured OIDC identity provider: which providers are offered, how the identity token is validated, how the person is found through the immutable identity binding, and which logins are refused.

## ADDED Requirements

### Requirement: Login through a configured provider

The login page SHALL offer every enabled OIDC provider stored through the admin API. A login SHALL use the authorization code flow with PKCE (`S256`), `state` and `nonce`; the code SHALL be redeemed by the backend, and the browser SHALL receive only the session cookie of the identity/sessions capability, never an IdP token. A person who already has a valid session SHALL not be redirected to the provider again.

Trace: US-001 AC1, US-001 AC2, REQ-001, REQ-165 AC3, REQ-166 AC3

#### Scenario: Successful login

- **WHEN** a person whose (`iss`, `sub`) pair is bound to a person in status `active` logs in through the provider
- **THEN** a session for that person is created, the browser holds only the session cookie, and an audit event `auth.login` with the method `oidc` exists

#### Scenario: Existing session

- **WHEN** a person with a valid session requests the current principal
- **THEN** the request succeeds without a redirect to the provider

#### Scenario: Login without PKCE

- **WHEN** the authorization request reaches the provider without a `code_challenge` of method `S256`, or the token request carries a wrong `code_verifier`
- **THEN** no session is created

#### Scenario: Only enabled providers offered

- **WHEN** the login page lists the providers while one stored provider is disabled
- **THEN** only the enabled providers are listed, with identifier and name, and without client settings

### Requirement: Identity token validation

The system SHALL accept an identity token only if its signature verifies against the provider's published keys with an asymmetric algorithm, its issuer equals the stored issuer, its audience contains the client ID (and `azp` equals the client ID when present), it is not expired or not yet valid, and its `nonce` equals the nonce of the running login. Any other token SHALL produce no session.

Trace: REQ-166 AC2, ADR-036 §6 G1

#### Scenario: Invalid identity token

- **WHEN** the identity token has a wrong `aud`, an expired `exp`, a mismatching `nonce`, the algorithm `none` or `HS256`
- **THEN** no session is created and an audit event `auth.login-failed` exists

### Requirement: Person found by immutable identity binding

The system SHALL find the person only through the stored binding of the pair (`iss`, `sub`), never through e-mail or other mutable claims. Persons in status `invited` or `active` SHALL get a session; the first login of an `invited` person SHALL change the status to `active`. Unknown pairs and persons in any other status SHALL get no session.

Trace: REQ-004 AC1–AC4, REQ-007 AC1, REQ-166 AC1

#### Scenario: First login of an invited person

- **WHEN** a person in status `invited` logs in for the first time
- **THEN** the session is created, the person is `active`, and the binding records the login time

#### Scenario: Matching e-mail with a different subject

- **WHEN** a valid identity token carries the e-mail address of a bound person but a different `sub`
- **THEN** no session is created for that person

#### Scenario: Person not active

- **WHEN** the bound person has the status `inactive` or `offboarded`
- **THEN** no session is created and an audit event `auth.login-failed` exists

### Requirement: Generic login failure

Every refused login – unknown identity, person not active, invalid token, provider error or cancelled login – SHALL lead to the same externally visible failure without revealing whether the identity exists, and SHALL be audited with the mechanism and the internal reason. An unreachable provider SHALL produce a technical error message that allows a retry.

Trace: REQ-005 AC1, REQ-006 AC1, REQ-001 (error cases)

#### Scenario: Unknown and inactive identities

- **WHEN** one login fails because the identity is not bound and another because the person is `offboarded`
- **THEN** both end on the login page with the same message, and each produces an audit event `auth.login-failed` with its reason

# Spec Delta

## Purpose

Defines how service accounts access the API without a browser: the token endpoint with its client authentication, limits and errors, the opaque access token with its storage, lifetime, revocation and cleanup, the bearer authentication of requests, and the audit of issued and refused tokens.

## ADDED Requirements

### Requirement: Token endpoint

The token endpoint SHALL issue an access token for `grant_type=client_credentials` with the client ID and secret of an active service account in HTTP Basic authentication. The response SHALL contain the token, `token_type` `Bearer` and `expires_in` 900, without refresh token, with `Cache-Control: no-store`. A grant SHALL set the last use of the account and be audited as `token.issued`.

Trace: US-142 AC2, REQ-155 AC2, REQ-176 AC3, ADR-050 rules 3, 5, 8, 9

#### Scenario: Token issued

- **WHEN** the service account "ci-scanner" requests a token with its client ID and secret
- **THEN** the response is 200 with an access token starting with `oea_at_`, `token_type` `Bearer`, `expires_in` 900 and `Cache-Control: no-store`, the last use of "ci-scanner" is set, and one `token.issued` event names its client ID

#### Scenario: Other grant type

- **WHEN** a token is requested with `grant_type=password`
- **THEN** the response is 400 with the error `unsupported_grant_type` and no token is stored

#### Scenario: Credentials outside the header

- **WHEN** a token is requested with the client ID and secret in the request body or in the query string instead of HTTP Basic
- **THEN** the response is 401 with the error `invalid_client` and no token is stored

### Requirement: Refused client authentication

An unknown client, a wrong secret, an expired secret and a deactivated service account SHALL all be answered with the same 401 response with the error `invalid_client` and `WWW-Authenticate: Basic`, and SHALL be audited as `token.denied` with the claimed client marked as unverified. For an unknown client, the secret SHALL be checked against a fixed dummy hash.

Trace: REQ-176 AC2, REQ-155 AC3, ADR-050 rules 5, 6, 8

#### Scenario: Old secret after rotation

- **WHEN** the secret of "ci-scanner" was rotated and a token is requested with the old secret
- **THEN** the response is 401 `invalid_client`, and a request with the new secret gets a token

#### Scenario: Expiry boundary of the secret

- **WHEN** a token is requested one second before the expiry of the secret and at its expiry
- **THEN** the first request gets a token and the second is answered with 401 `invalid_client`

#### Scenario: Same answer for every refusal

- **WHEN** tokens are requested for an unknown client, with a wrong secret, with an expired secret and for a deactivated service account
- **THEN** all four responses have the same status, headers and body, and four `token.denied` events are recorded

### Requirement: Limits of the token endpoint

Failed token requests SHALL be throttled as local logins are: per client ID with an exponential backoff once 5 failures occurred within 15 minutes (at most 15 minutes), and per client address at 100 failures within 10 minutes. A throttled request SHALL get 429 with `Retry-After` without a secret check. At most 4 secret checks SHALL run at once; a request waiting longer than 2 seconds SHALL get 429.

Trace: ADR-050 rules 6, 7, REQ-168, owner decision 2026-11-15

#### Scenario: Backoff per client

- **WHEN** a client has 4 failed token requests within 15 minutes, and then a fifth
- **THEN** the fifth is still checked and answered with 401, and the next request within the backoff is answered with 429 and `Retry-After`, even with the correct secret

#### Scenario: Limit per address

- **WHEN** 100 failed token requests came from one address within 10 minutes for different clients
- **THEN** the next request from that address is answered with 429, while a request from another address is checked

### Requirement: Access token

An access token SHALL consist of the prefix `oea_at_` and 256 random bits. Only its SHA-256 hash SHALL be stored, with the client ID, the issue time, the expiry and the revocation time. Its lifetime SHALL be 900 seconds. Neither the token nor the secret SHALL appear in any log or audit event.

Trace: REQ-176 AC3, ADR-050 rules 1–3, 8

#### Scenario: Stored as a hash

- **WHEN** a token is issued
- **THEN** the stored record holds the SHA-256 hash of the token, the client ID and an expiry 900 seconds after the issue time, and no log line or audit event contains the token

### Requirement: Bearer authentication

A request with `Authorization: Bearer` and a stored, unexpired, unrevoked token of an active service account SHALL be authenticated as that service account; its permissions SHALL come from the roles of the account, resolved per request. Any other bearer token SHALL be answered with 401 and `WWW-Authenticate: Bearer`. A bearer request SHALL need no CSRF token.

Trace: US-142 AC2, US-149 AC2, REQ-155 AC2, REQ-176 AC3, ADR-050 rule 10

#### Scenario: Write with integration-writer

- **WHEN** "cmdb-importer" holding `integration-writer` writes an entity batch with its token and no CSRF token
- **THEN** the batch is accepted, and its audit events carry the actor type `service-account` and the client ID of "cmdb-importer" as `agentId`

#### Scenario: Token lifetime boundary

- **WHEN** a request is sent with a token one second before its expiry and at its expiry
- **THEN** the first request is accepted and the second is answered with 401

#### Scenario: Unknown token

- **WHEN** a request carries a bearer token that was never issued
- **THEN** the response is 401 with `WWW-Authenticate: Bearer`

### Requirement: Permissions of a service account

A service account SHALL be refused with 403 for every operation whose permissions its roles do not grant, and for the operations of the own account other than reading the current principal.

Trace: US-142 AC4, US-149 AC5, ADR-032 SoD-4

#### Scenario: Write without integration-writer

- **WHEN** a service account holding only `viewer` writes an entity batch with its token
- **THEN** the response is 403 and nothing is written

#### Scenario: Administration refused

- **WHEN** "cmdb-importer" holding `integration-writer` lists the service accounts with its token
- **THEN** the response is 403 and an `authz.denied` event is recorded

#### Scenario: Own account

- **WHEN** a service account reads the current principal and then tries to change a password with its token
- **THEN** the current principal is returned with the type `service-account`, and the password change is answered with 403

### Requirement: Token transport

The access token SHALL be accepted only in the `Authorization` header. A token in a query parameter or a cookie SHALL NOT authenticate the request, and a request carrying both a session cookie and a bearer token SHALL be answered with 401.

Trace: ADR-050 rule 10

#### Scenario: Token in the query string

- **WHEN** a request carries a valid token only as the query parameter `access_token`
- **THEN** the response is 401

#### Scenario: Cookie and bearer token

- **WHEN** a request carries a valid session cookie and a valid bearer token
- **THEN** the response is 401

### Requirement: Revocation of access tokens

Rotating the secret of a service account and deactivating it SHALL revoke all its unexpired tokens at once, each audited as `token.revoked`; a revoked token SHALL be answered with 401 on its next use. Tokens SHALL be deleted one day after their expiry.

Trace: ADR-050 rules 2, 4, US-148

#### Scenario: Rotation revokes tokens

- **WHEN** "ci-scanner" holds a token and its secret is rotated
- **THEN** the next request with the token is answered with 401 and one `token.revoked` event names the token

#### Scenario: Deactivation revokes tokens

- **WHEN** "ci-scanner" holds a token and is deactivated
- **THEN** the next request with the token is answered with 401

#### Scenario: Cleanup boundary

- **WHEN** the cleanup runs for a token that expired 23 hours and 59 minutes ago and one that expired 24 hours ago
- **THEN** the first token is kept and the second is deleted

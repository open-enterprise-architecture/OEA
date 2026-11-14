# ADR-050: Access Tokens of Service Accounts

**Status**: accepted
**Date**: 2026-11-14
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Security Engineer, Solution Architect
**Informed**: all contributors

## Context and Problem

Machine access to the API is possible only through service accounts with the OAuth2 client credentials grant ([REQ-176](../requirements/req/REQ-176-machine-credential-lifecycle.md), [REQ-155](../requirements/req/REQ-155-service-account-management.md), [ADR-026](./ADR-026-external-system-integration.md)). The contract declares the token endpoint `POST /oauth2/token` and the security schemes `bearerAuth`, `oauth2ClientCredentials` and `clientSecretBasic`, all marked `x-oea-status: planned` since the hardening change `s11-review-hardening` (architecture review 2026-11, finding API-1). Sprint S12 implements them ([#264](https://github.com/open-enterprise-architecture/OEA/issues/264), owner decision 2026-11-14).

OAuth2 (RFC 6749) does not prescribe a format for access tokens, and REQ-176 requires only a lifetime of at most 900 seconds and a link to the `client_id`. The format decides how a token is validated, how fast a revocation takes effect, and which key material the instance has to manage. Without a decision, the implementation would choose one implicitly.

In OEA, the backend that issues a token is also the only resource server that accepts it. One backend instance runs per deployment (Docker Compose, PostgreSQL). Browser sessions already use an opaque random token whose SHA-256 hash is stored in the database. The instance has a key ring for encrypting secrets (KEK), but no key material for signatures.

## Decision Drivers

- **Prompt revocation:** a disabled service account or a rotated secret must lose access at once, as offboarding does for persons ([US-148](../requirements/user-stories/US-148-revoke-access-on-offboarding.md)).
- **Trustworthy audit actor:** the audit `agent_id` comes only from the authenticated `client_id` ([ADR-033](./ADR-033-audit-trail-hardening.md), REQ-174); a forged token must not be able to claim another client.
- **Little new key material:** every additional key needs storage, rotation and a compromise procedure.
- **Leakage:** tokens end up in CI logs and shell histories; a leaked token should disclose as little as possible and be easy to find with secret scanning.
- **Market conformance:** standard OAuth2 clients and libraries must work without OEA-specific code.

## Considered Options

### Option 1: Signed JWT access token (RFC 9068)

The instance signs a JWT (`typ: at+jwt`) with its own key (ES256 or EdDSA) and validates the signature on each request. This is the default of Keycloak, Auth0, Okta, Entra ID, AWS Cognito and Spring Authorization Server.

- **Pro**: validation without a database read; a client can read `exp` and `client_id` itself; the established format for deployments in which the resource servers are separate from the authorization server.
- **Con**: a new signing key with storage, rotation, `kid` and algorithm pinning; with a stolen key, anyone can forge tokens for every `client_id`, which breaks the audit actor; a revocation takes effect only after up to 900 seconds unless a denylist is added, and that denylist is a database read again; a leaked token discloses the `client_id` and its claims in clear text.

### Option 2: Opaque reference token stored as a hash

The instance issues a random token, stores only its SHA-256 hash with `client_id`, issue time, expiry and revocation time, and looks it up on each request, like the session cookie. This is the format of GitHub, Stripe, Slack and Google, and an option in Spring Authorization Server, Keycloak and Duende.

- **Pro**: a revocation takes effect on the next request; no signing key that could be stolen; a database read of the hashes yields no usable token; the token carries no information; the validation reuses the session mechanism; standard clients treat the token as an opaque string anyway.
- **Con**: every request needs a database read (as every cookie request does today); a future resource server outside the backend needs an introspection endpoint (RFC 7662); a client cannot read the expiry from the token and relies on `expires_in` of the token response.

### Option 3: Sender-constrained tokens (DPoP or mTLS)

Option 1 or 2, with the token bound to a key of the client (RFC 9449, RFC 8705).

- **Pro**: a leaked token cannot be used without the client's key.
- **Con**: additional complexity in every client (CI scripts, ETL jobs); mTLS needs client certificates through the reverse proxy; with a lifetime of 900 seconds and prompt revocation, the gain is small at the threat level of ADR-026.

## Decision

We choose **Option 2: Opaque reference token stored as a hash**.

**Rationale**: the backend is issuer and only resource server at the same time, so the main benefit of a JWT, a validation without the issuer, does not apply. The opaque token gives prompt revocation and no signing key, which protects the audit actor. It keeps the token free of information, and the validation reuses the session mechanism. Sender constraint (Option 3) stays a later option if a threat requires it. Recommendation of the security engineer, accepted by the owner on 2026-11-14.

The token and the token endpoint follow these rules:

1. **Format:** the prefix `oea_at_` followed by 256 bits from a cryptographically secure random generator, encoded base64url. The prefix lets secret scanners and log redaction recognize the token.
2. **Storage:** only the SHA-256 hash of the token, with `client_id`, issue time, expiry and revocation time. Expired tokens are deleted by a cleanup job.
3. **Lifetime:** at most 900 seconds, enforced by the server; the token response states it in `expires_in`. No refresh token is issued (RFC 6749 §4.4.3).
4. **Revocation:** disabling or deleting a service account and rotating or revoking its secret revoke its tokens at once.
5. **Token endpoint:** only `grant_type=client_credentials` with client authentication `client_secret_basic`. Other grant types are answered with `unsupported_grant_type`, other client authentication methods and credentials in the query string with 401. Unknown client, wrong secret, expired secret and disabled account all get the same 401 `invalid_client`.
6. **Secret check:** the client secret is stored as an argon2id hash (REQ-176); for an unknown `client_id` the check runs against a fixed dummy hash, so the response time does not reveal whether the client exists. Concurrent hashing is limited, so the hashing cost cannot be used for a denial of service.
7. **Throttling:** failed token requests are limited per `client_id` and per client address (the forwarded address of `s11-review-hardening`); exceeding the limit answers 429 with `Retry-After`.
8. **Audit:** `token.issued` (client, token ID, expiry), `token.denied` (claimed client marked as unverified, reason internal only) and `token.revoked`. Neither the secret nor the token is ever logged.
9. **Response headers:** `Cache-Control: no-store` and `Pragma: no-cache` (RFC 6749 §5.1).
10. **Transport:** the token is accepted only in the `Authorization: Bearer` header, never in a query parameter or a cookie.

The concrete limits of rules 6 and 7 are set in the change that implements the token endpoint.

## Consequences

### Positive Consequences

- Revoking a service account takes effect as fast as offboarding a person.
- No signing key has to be generated, stored, rotated or protected; a database dump yields no usable token.
- The audit `agent_id` can only come from a token that the instance issued itself.
- Standard OAuth2 client libraries work unchanged, since they treat the access token as opaque.

### Negative Consequences / Trade-offs

- Every request with a bearer token reads the database, as every cookie request does today.
- A resource server outside the backend would need an introspection endpoint (RFC 7662); none is planned.
- AC3 of REQ-176 ("the issued access token is inspected") is reworded to the token response and the stored token record (REQ-176 0.2.0).
- The data model gets a table for the hashes of issued access tokens; it is added to `docs/data-model.puml` and `business-objects/machine-credential.md` with the change that implements the token endpoint.

## References

**Related ADRs**: [ADR-006](./ADR-006-auth-stack-choice.md) (auth stack), [ADR-026](./ADR-026-external-system-integration.md) (write access through service accounts), [ADR-032](./ADR-032-authorization-model.md) (authorization), [ADR-033](./ADR-033-audit-trail-hardening.md) (audit actor)
**Requirements**: [REQ-176](../requirements/req/REQ-176-machine-credential-lifecycle.md), [REQ-155](../requirements/req/REQ-155-service-account-management.md), [US-142](../requirements/user-stories/US-142-create-service-account.md), [US-149](../requirements/user-stories/US-149-assign-roles-to-service-account.md)
**Concept**: [§21 API architecture](../concept/70-platform/21-api-architecture.md)
**Review**: [architecture review 2026-11](../docs/reviews/architecture-2026-11.md), finding API-1; [#264](https://github.com/open-enterprise-architecture/OEA/issues/264)

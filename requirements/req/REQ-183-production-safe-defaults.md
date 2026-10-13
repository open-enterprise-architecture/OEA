---
id: REQ-183
title: Production-safe defaults without development authentication bypass
type: constraint
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects: []
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-027
    - ADR-013
    - ADR-006
    - ADR-036
supersedes: []
superseded_by: []
---

# REQ-183: Production-Safe Defaults Without Development Authentication Bypass

## Statement

The system **MUST NOT** contain any authentication stub or bypass: local development and tests **MUST** use a real OIDC identity provider (a container in the development/test Compose profile and in integration tests); the production artifact **MUST NOT** contain test-only authentication classes; the application **MUST** refuse to start outside the development profile if token signature verification is disabled, the configured issuer is a localhost address, or default or known development secrets are configured; the application **MUST** refuse to start in the development profile if any configured issuer is not a loopback address; and the operator Compose file **MUST** require all secrets explicitly and fail if they are unset.

## Rationale

A self-hosting operator who copies the repository's Compose file to a server must not end up with an instance that accepts any token or uses well-known default credentials.

## Context

Source: security review SEC-20 (proposal PR-27); additional owner decision of 2026-10-13: there is no JWT stub at all; development and tests use a real OIDC IdP container (e.g. Keycloak or Authentik in the dev/test Compose profile, Testcontainers in tests). ADR-027 §4 described a "simplified JWT stub" in dev mode; this conflict is resolved by ADR-036, which partially supersedes ADR-027 §4: Keycloak is the single development and test IdP (versioned realm file, Keycloak Testcontainer), an Authentik reference suite runs nightly and before releases, no security bean is conditional on a Spring profile, and the operator file `compose.yaml` is separated from the development file `compose.dev.yaml`. Swagger UI is enabled only with the Spring profile `dev` (ADR-013, refined by ADR-036).

Startup guards (ADR-036 §6): outside the Spring profile `dev`, startup fails if signature verification is not in effect, an issuer is a loopback address or not `https`, a secret is empty, too short, a generic weak value or matches the known-development-secrets list, CORS allows `*`, `http` or loopback origins, or Swagger UI is enabled. With the profile `dev` active, startup fails if any issuer is **not** a loopback address (reverse guard), so that a production instance accidentally started with `dev` and a real IdP does not silently relax rules. The known-development-secrets list is a resource in the production artifact containing only SHA-256 hashes of every secret value used in the development files; a CI script fails if a development secret is missing from it. Owner confirmation (2026-10-13): reverse guard and the list of known development secret hashes.

## Type-Specific Fields

**Type of constraint**: technical

**Source**: owner decision 2026-10-13, security review SEC-20

**Binding strength**: hard

**Impact of non-compliance**: unauthenticated full access to production instances

## Acceptance Criteria

**AC1**:

- Given: the production artifact (container image)
- When: its classpath is scanned by a build-time check
- Then: no test-only authentication class, token stub or static signing key is present, and the build fails otherwise

**AC2**:

- Given: a profile other than the development profile
- When: the application starts with signature verification disabled, with an issuer on `localhost`/`127.0.0.1`, or with a known default secret or a secret whose SHA-256 hash is in the known-development-secrets list
- Then: startup fails with a clear message naming the offending setting (never the secret value)

**AC3**:

- Given: the operator Compose file
- When: `docker compose up` runs without the required secrets in the environment
- Then: startup fails rather than using default credentials, and Swagger UI is not reachable

**AC4**:

- Given: the development/test Compose profile and the integration test setup
- When: inspected
- Then: authentication uses a real OIDC IdP container; no mock token issuer is part of the application code

**AC5** (Reverse guard for the development profile):

- Given: the Spring profile `dev`
- When: the application starts with an issuer that is not a loopback address (e.g. `https://login.example.com`)
- Then: startup fails with a message naming the issuer property

**AC6** (Known development secrets complete):

- Given: the development files (`compose.dev.yaml`, `dev/`, realm and blueprint files)
- When: the CI check extracts all secret literals and compares their SHA-256 hashes with the known-development-secrets list
- Then: the build fails if any development secret is missing from the list

## Verification Method

- [ ] Method: test (automated startup tests) + image inspection in CI
- [ ] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: ADR-006, ADR-036
- **Follow-on effects**: REQ-075, REQ-156 (AC1 `docker compose up` with the operator file requires all secrets explicitly; aligned 2026-10-13), REQ-166 (verification against the Keycloak Testcontainer)
- **Conflicts**: none known (ADR-027 §4 JWT stub in dev mode is resolved by ADR-036)

## Risks of Non-Fulfillment

- Risk 1: insecure instances through copied development configuration – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-20 (PR-27) with owner decision of 2026-10-13 (no stub, real IdP in dev/test) |
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-036 CH-5: ADR-036 referenced, ADR-027 §4 conflict marked resolved; reverse guard for the `dev` profile (statement, AC5); known development secret hashes (AC2, AC6) |

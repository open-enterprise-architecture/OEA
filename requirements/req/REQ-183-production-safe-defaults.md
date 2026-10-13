---
id: REQ-183
title: Production-safe defaults without development authentication bypass
type: constraint
priority: must
status: proposed
version: 0.1.0
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
supersedes: []
superseded_by: []
---

# REQ-183: Production-Safe Defaults Without Development Authentication Bypass

## Statement

The system **MUST NOT** contain any authentication stub or bypass: local development and tests **MUST** use a real OIDC identity provider (a container in the development/test Compose profile and in integration tests); the production artifact **MUST NOT** contain test-only authentication classes; the application **MUST** refuse to start outside the development profile if token signature verification is disabled, the configured issuer is a localhost address, or default secrets are configured; and the operator Compose file **MUST** require all secrets explicitly and fail if they are unset.

## Rationale

A self-hosting operator who copies the repository's Compose file to a server must not end up with an instance that accepts any token or uses well-known default credentials.

## Context

Source: security review SEC-20 (proposal PR-27); additional owner decision of 2026-10-13: there is no JWT stub at all; development and tests use a real OIDC IdP container (e.g. Keycloak or Authentik in the dev/test Compose profile, Testcontainers in tests). ADR-027 §4 describes a "simplified JWT stub" in dev mode; a new ADR is needed (follow-up for the solution architect). Swagger UI remains non-production only (ADR-013).

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
- When: the application starts with signature verification disabled, with an issuer on `localhost`/`127.0.0.1`, or with a known default secret
- Then: startup fails with a clear message naming the offending setting

**AC3**:

- Given: the operator Compose file
- When: `docker compose up` runs without the required secrets in the environment
- Then: startup fails rather than using default credentials, and Swagger UI is not reachable

**AC4**:

- Given: the development/test Compose profile and the integration test setup
- When: inspected
- Then: authentication uses a real OIDC IdP container; no mock token issuer is part of the application code

## Verification Method

- [ ] Method: test (automated startup tests) + image inspection in CI
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: ADR-006, ADR-027
- **Follow-on effects**: REQ-075, REQ-156 (AC1 `docker compose up` with default configuration must supply secrets explicitly)
- **Conflicts**: ADR-027 §4 (JWT stub in dev mode)

## Risks of Non-Fulfillment

- Risk 1: insecure instances through copied development configuration – high

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-20 (PR-27) with owner decision of 2026-10-13 (no stub, real IdP in dev/test) |

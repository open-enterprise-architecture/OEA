---
id: REQ-002
title: API key authentication for machine-to-machine access
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
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
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-002: API key authentication for machine-to-machine access

## Statement

The system SHOULD be able to validate requests with a valid API key directly, without a redirect to an OIDC identity provider, and determine the identity assigned to the API key together with its active role assignments.

## Rationale

Concept §21.8 explicitly provides for API keys for machine-to-machine communication (e.g., discovery adapters, CI scripts, external modules). UC-01, Alternative A2.

## Context

A browser-based OIDC redirect is unsuitable for non-interactive clients. API keys are the alternative provided in §21.8.

## Type-Specific Fields

### For type=functional

**Inputs**:

- HTTP request with API key (e.g., as a header)

**Processing**:

- Validate the API key (exists, not expired, not revoked)
- Determine the service identity or person assigned to the API key
- Load the assigned active role assignments

**Outputs**:

- Authorized request context with the permissions of the assigned roles

**Error Cases**:

- Invalid, expired, or revoked API key → request is rejected with an error, no permission context is created

## Acceptance Criteria

**AC1**:

- Given: a request contains a valid, non-revoked API key
- When: the request is made against a protected resource
- Then: it is authorized without a redirect, according to the roles of the assigned identity

**AC2**:

- Given: a request contains an invalid or revoked API key
- When: the request is made
- Then: it is rejected without an authorization context being created

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: integration test with valid/invalid/revoked API key
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1 and AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001 (shared role/authorization model)
- **Downstream effects**: none known
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without API key support, machine clients would have to simulate OIDC browser flows – impractical, severity medium

## Trade-offs

- vs. security: API keys are long-lived credentials and must therefore support revocation, rotation, and scoping so that they do not become the weakest link.

## Implementation Notes

- Known technical challenges: secure storage and rotation of API keys, scoping (which roles/permissions a key may carry at most)

## Implementation

- ADR(s): pending
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, Alternative A2.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |

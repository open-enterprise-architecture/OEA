---
id: REQ-176
title: Machine credential lifecycle and scoping (OAuth2 client credentials only)
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-22
    - UC-02
  business_objects:
    - person
    - role
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-026
    - ADR-006
supersedes:
  - REQ-002
superseded_by: []
---

# REQ-176: Machine Credential Lifecycle and Scoping (OAuth2 Client Credentials Only)

## Statement

Machine access **MUST** be possible only through service accounts with OAuth2 client credentials; client secrets **MUST** be stored only as a hash, **MUST** have a mandatory expiry (default 90 days, maximum configurable), service accounts **MUST** only receive roles flagged as assignable to service accounts and without `admin`-class permissions, and access tokens issued to them **MUST** have a lifetime of at most 15 minutes.

## Rationale

Two overlapping machine-access mechanisms double the attack surface. A leaked CI secret that never expires and may carry admin roles is an admin credential.

## Context

Source: security review SEC-15 (proposal PR-20); owner decision Q5: machine access only via service accounts with OAuth2 client credentials; API keys (REQ-002) are dropped. REQ-002 is rejected and superseded by this REQ. The token endpoint is not yet in the API contract (follow-up for the solution architect).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Service account creation/rotation request (name, roles, secret expiry)
- Client credentials grant at the token endpoint

**Processing**:

- Validate that every requested role is flagged `assignableToServiceAccounts` and grants no `admin`-class permission
- Store the client secret as argon2id hash with expiry timestamp
- On grant: verify secret and expiry; issue an access token with `client_id`, lifetime ≤ 900 s
- Admin UI lists secrets expiring within 14 days

**Outputs**:

- `client_id` and secret (plaintext once); access tokens

**Error cases**:

- Non-assignable or admin role → 422
- Expired or wrong secret → 401 and audit event

## Acceptance Criteria

**AC1**:

- Given: an admin creates a service account with role `system-admin`
- When: the request is processed
- Then: it is rejected with 422

**AC2**:

- Given: a client secret older than its expiry
- When: it is used at the token endpoint
- Then: authentication fails with 401, an audit event is logged, and the admin UI had shown the secret as expiring within 14 days beforehand

**AC3**:

- Given: a successful client credentials grant
- When: the issued access token is inspected
- Then: `exp - iat` is at most 900 seconds and it contains the `client_id`

**AC4**:

- Given: a request that presents any other machine credential type (e.g. an API key header)
- When: processed
- Then: it is rejected with 401

## Verification Method

- [ ] Method: test (automated) + DB inspection
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-155, REQ-157
- **Follow-on effects**: REQ-008 (token issuance latency), REQ-147, REQ-177
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: long-lived, over-privileged machine credentials – medium

## Implementation Notes

- `machine_credentials` needs an expiry column; `ServiceAccountWrite.roles` must be restricted to assignable roles (follow-ups for business engineer and solution architect)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-15 (PR-20), owner decision Q5; supersedes REQ-002 |

---
id: REQ-155
title: Service Account Management for External Systems
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-02
  adrs:
    - ADR-026
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-155: Service Account Management for External Systems

## Statement

The system **MUST** enable administrators to create service accounts, assign them the role `integration-writer`, and issue rotating client credentials, so that external systems can access the write API without human user login.

## Rationale

External systems (scanners, CI/CD) must not use human user accounts. Service accounts have restricted roles and rotatable credentials. Without this separation, a security and audit problem arises: human actions and automated writes are indistinguishable.

## Context

Service accounts are `persons` with `person_type=service-account`. Credentials in `machine_credentials`. The role `integration-writer` has write permission on entities/connections, but no admin rights. In the audit log, `agent_id` and `agent_run` are set from the JWT claim. (ADR-026)

## Type-Specific Fields

**Inputs**:
- Name, description of the service account
- Role to be assigned (integration-writer)

**Processing**:
- Create a person entry with `person_type=service-account`
- Create a `machine_credentials` entry with `client_id` (UUID) and `client_secret_hash`
- Role assignment via `person_roles`
- Credential rotation: new secret; old one immediately invalid

**Outputs**:
- `client_id` + `client_secret` (plaintext, once) on creation and rotation
- Afterwards: only `client_id` visible (secret is hashed)

**Error Cases**:
- Role `integration-writer` not present → 422
- Service account without a role → must not call write endpoints

## Acceptance Criteria

**AC1**:
- Given: admin is logged in
- When: POST /admin/service-accounts with `{name: "ci-scanner", role: "integration-writer"}`
- Then: 201 Created; response contains `clientSecret` once; DB contains a `machine_credentials` entry

**AC2**:
- Given: service account with valid client_id/secret
- When: OAuth2 client credentials flow → receive JWT; PUT /entities/by-external-id/...
- Then: 200/201; audit log shows `source=API`, `agent_id=<client_id>`

**AC3**:
- Given: admin rotates the credentials of a service account
- When: old secret is used for an auth request
- Then: 401 Unauthorized; new secret required

## Verification Method

- [ ] Method: test (automated) + demonstration
- [ ] Test setup: integration test with client credentials flow; audit log check
- [ ] Pass criterion: service account can write; audit log contains agent_id
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: ADR-006 (auth stack), ADR-026
- **Consequences**: US-142, REQ-153, REQ-154

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-026 |

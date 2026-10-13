---
id: REQ-158
title: Deny-by-default authorization on every API operation
type: constraint
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - role
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-013
supersedes: []
superseded_by: []
---

# REQ-158: Deny-by-Default Authorization on Every API Operation

## Statement

Every operation of the API contract **MUST** declare its required permission(s); the backend **MUST** reject any request whose principal lacks them with HTTP 403 (or 401 if unauthenticated), and operations without a permission declaration **MUST** be rejected for all principals.

## Rationale

The spec-first contract (ADR-013) is the source for generated interfaces and contract tests. If it carries no authorization information, missing checks are invisible to the tests and broken function-level authorization ships unnoticed.

## Context

Source: security review SEC-01 and SEC-31 (proposal PR-02). The contract currently has only a global bearer scheme, prose role hints referencing non-existent roles, and many operations without `401`/`403` responses.

## Type-Specific Fields

**Type of constraint**: technical

**Source**: security review 2026-10 (SEC-01, SEC-31); OWASP API Security Top 10 (API1, API5)

**Binding strength**: hard

**Impact of non-compliance**: any authenticated principal can call privileged operations

## Acceptance Criteria

**AC1**:

- Given: the OpenAPI contract
- When: CI runs
- Then: the build fails if any operation lacks the permission declaration or a `401`/`403` response

**AC2**:

- Given: a principal with only `entity:read`
- When: it calls any operation that requires another permission
- Then: the response is 403 with a generic problem body and no state change occurs

**AC3**:

- Given: an operation generated from the contract without a permission mapping in the security configuration
- When: the integration test suite runs
- Then: a generated authorization matrix test fails for that operation, and a request to it is rejected for every principal

## Verification Method

- [ ] Method: test (automated, generated per operation from the contract) + CI lint of the contract
- [ ] Pass criterion: AC1–AC3 green; authorization matrix test covers 100 % of contract operations
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-157 (permission catalog), ADR-013 (spec-first)
- **Follow-on effects**: REQ-159, REQ-160 (matrix tests extended per read endpoint), REQ-147
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: viewers call write or admin endpoints directly – critical

## Implementation Notes

- Declaration format (e.g. a vendor extension or OAuth scopes per operation) is a contract design decision of the solution architect
- Replace the prose role hints and the "Entities – Write" tag sentence in `api/openapi.yaml`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-01/SEC-31 (PR-02) |

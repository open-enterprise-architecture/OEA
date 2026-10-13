---
id: REQ-008
title: Login latency
type: non-functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-24
last_updated: 2026-10-13
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

# REQ-008: Login latency

## Statement

The system SHOULD complete the login process (from redeeming the authorization code to session confirmation, and from a client credentials token request to token issuance) within a defined time budget.

## Rationale

UC-01, Implementation Notes: Login is a high-frequency, latency-critical path. Long login times impair daily use, especially for SH-03 (Kurt), who uses the tool daily.

## Context

Applies to the server-side portion of the login process (token redemption, person/role lookup, session creation); the time a person spends at the external identity provider itself (entering credentials) is not part of this requirement.

## Type-Specific Fields

### For type=non-functional

**Category**: performance

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| p95 server processing time for login (token redemption to session confirmation) | < 300ms | 500ms | 1000ms | with up to 10,000 persons and 1,000 roles in the repository |
| p95 client credentials token issuance (service accounts, REQ-155/REQ-176) | < 300ms | 500ms | 1000ms | with up to 1,000 active service accounts; secret verification with the argon2id parameters of REQ-176 included |

**Example**: "p95 login processing time < 300ms with 10,000 persons in the repository"

## Acceptance Criteria

**AC1**:

- Given: a repository with 10,000 persons and 1,000 roles
- When: 95% of OIDC login processes are measured (server-side portion)
- Then: the processing time is below 300ms

**AC2**:

- Given: a repository with 1,000 active service accounts
- When: 95% of client credentials token requests are measured (server-side portion)
- Then: the processing time is below 300ms

## Verification Method

- [x] Method: test (automatisiert, Lasttest)
- [x] Test setup: load test with a synthetic repository dataset (10,000 persons, 1,000 roles, 1,000 service accounts)
- [x] Measurement tool: load/performance test tool (specific choice: solution design, not to be fixed here)
- [x] Pass criterion: p95 values met per the table
- [ ] Integrated in CI: yes, as a performance gate once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001, REQ-155, REQ-176 (REQ-002 API keys rejected)
- **Downstream effects**: none known
- **Conflicts**: REQ-004, REQ-006 (additional lookup/anti-timing measures must not blow the latency budget)

## Risks of Non-Fulfillment

- Risk 1: Noticeably slow login reduces daily acceptance, especially with frequent use – medium severity

## Trade-offs

- vs. REQ-006 (constant response time against timing side channels): the artificial delay used as an anti-timing measure must not exceed the budget defined here

## Implementation Notes

- Role assignments should be cached rather than resolved anew from the repository on every login (see UC-01 Implementation Notes)
- The specific target values are provisional (walking skeleton assumption) and must be validated through real load measurement

## Implementation

- ADR(s): none
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Target values are a first estimate for the walking skeleton scope, not validated production SLAs. Adjust in review if needed.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-15 / owner decision Q5: API key validation target replaced by client credentials token issuance |

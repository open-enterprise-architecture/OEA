---
id: REQ-163
title: No anonymous access – every access requires authentication
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
    - UC-07
  business_objects:
    - person
    - dashboard
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-008
supersedes: []
superseded_by: []
---

# REQ-163: No Anonymous Access – Every Access Requires Authentication

## Statement

The system **MUST** require authentication for every API and web portal operation except the login and authentication endpoints, a health endpoint without repository data, and static client assets; there **MUST NOT** be any configuration option that enables anonymous or public access in v1.

## Rationale

The web portal may be exposed to the internet (owner decision Q2). Anonymous portal access combined with enumerable IDs would expose the application landscape, data flows and protection needs to anyone.

## Context

Source: security review SEC-05 (proposal PR-07); owner decision Q1: no anonymous access in v1, every portal and API access requires authentication; `portal-reader` is an authenticated role (REQ-157). REQ-003, REQ-056 and UC-07 were aligned on 2026-10-13.

## Type-Specific Fields

**Type of constraint**: organizational / technical

**Source**: owner decision Q1 (2026-10-13), security review SEC-05

**Binding strength**: hard

**Impact of non-compliance**: unauthenticated disclosure of repository content

## Acceptance Criteria

**AC1**:

- Given: a fresh installation with default configuration
- When: an unauthenticated client calls any portal, view, entity, dashboard, catalog, audit or event-stream operation
- Then: it receives 401 and no repository data

**AC2**:

- Given: the admin settings and configuration reference
- When: inspected
- Then: no setting exists that disables authentication for any repository-data operation

**AC3**:

- Given: the health endpoint
- When: called unauthenticated
- Then: it returns only a liveness/readiness status without version details or repository data

## Verification Method

- [ ] Method: test (automated, unauthenticated sweep over all contract operations) + inspection of default configuration
- [ ] Pass criterion: AC1–AC3 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-158
- **Follow-on effects**: REQ-003, REQ-056, UC-07
- **Conflicts**: ADR-008 context ("no login effort, just a link") – follow-up for the solution architect

## Risks of Non-Fulfillment

- Risk 1: internet-wide enumeration of the architecture repository – high

## Implementation Notes

- A later opt-in anonymous mode would need a new owner decision, a per-object publication flag and its own requirement

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-05 (PR-07), rewritten per owner decision Q1 |

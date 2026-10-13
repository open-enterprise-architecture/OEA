---
id: REQ-193
title: Generic error responses without internal details
type: interface
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-22
  business_objects: []
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-013
supersedes: []
superseded_by: []
---

# REQ-193: Generic Error Responses Without Internal Details

## Statement

Error responses **MUST NOT** contain stack traces, SQL, class names, file paths or framework versions; 5xx responses **MUST** contain only a generic `title` and a correlation ID that maps to the server log entry.

## Rationale

Internal details in error responses help attackers map the technology stack and database schema, especially on an internet-exposed portal.

## Context

Source: security review SEC-24 (proposal PR-37). Error format is RFC 9457 Problem Details (REQ-147).

## Type-Specific Fields

### For type=interface

**Interface**: REST

**Specification**: `api/openapi.yaml` (`Problem` schema)

**Contracts**:

- Endpoint/path: all operations
- Request schema: n/a
- Response schema: `application/problem+json` with `type`, `title`, `status`, optional `detail` (no internal details), `requestId`
- Error codes: 4xx, 5xx

## Acceptance Criteria

**AC1**:

- Given: a forced internal exception in any endpoint
- When: the response is inspected
- Then: it is `application/problem+json` with status 500, a generic title and a `requestId`, and contains none of the forbidden details

**AC2**:

- Given: the same request ID
- When: the server log is searched
- Then: the full exception is found there

## Verification Method

- [ ] Method: test (automated)
- [ ] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-147
- **Follow-on effects**: REQ-006 (generic login errors), REQ-175 (correlation ID)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: information disclosure supporting further attacks – low to medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-24 (PR-37) |

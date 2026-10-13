---
id: REQ-159
title: Object-level authorization for addressed resources
type: functional
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
    - dashboard
    - entity
  business_rules: []
  stakeholders:
    - SH-03
    - SH-05
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-019
supersedes: []
superseded_by: []
---

# REQ-159: Object-Level Authorization for Addressed Resources

## Statement

For every operation that addresses a resource by ID (entity, diagram, dashboard, catalog, saved filter, service account, audit event), the system **MUST** check that the principal may access that specific object (ownership, scope, deletion state), and **MUST** return the same response for "does not exist" and "not permitted" where the existence itself is sensitive.

## Rationale

Sequential IDs are easy to enumerate. A permission check per operation (REQ-158) does not prevent a principal from accessing another user's object by ID (IDOR).

## Context

Source: security review SEC-01, SEC-04, SEC-05 (proposal PR-03). REQ-056 already requires this for personal dashboards; this REQ generalizes it. Soft-deleted entities (ADR-019) are visible only with the restore permission.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Request addressing a resource by ID (path, query, body reference)

**Processing**:

- Load the object and evaluate ownership, scope, deletion state against the principal
- For reference properties in writes: check that the writer may read the referenced object

**Outputs**:

- The object, or 404 (or consistently 403) without object data

**Error cases**:

- Not permitted or not existing → identical response
- Write referencing an unreadable entity → 422 without revealing existence

## Acceptance Criteria

**AC1**:

- Given: a personal dashboard of user A
- When: user B requests its configuration or data by ID on any endpoint (views, portal, dashboards)
- Then: the response is 404 (or consistently 403) and contains no dashboard data

**AC2**:

- Given: a soft-deleted entity
- When: a principal without `entity:restore` requests it by ID or lists with `deleted=true|only`
- Then: it receives 404 and the entity does not appear in the list

**AC3**:

- Given: an entity write whose reference property points to an entity the writer cannot read
- When: the write is submitted
- Then: it is rejected with 422 without revealing whether the referenced entity exists

## Verification Method

- [ ] Method: test (automated IDOR suite over all ID-addressed operations)
- [ ] Pass criterion: AC1–AC3 green; every ID-addressed operation covered
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-157, REQ-158
- **Follow-on effects**: REQ-056 (dashboards), REQ-163 (portal)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: enumeration of the application landscape via IDs – high

## Implementation Notes

- Whether external IDs become URNs/UUIDs (concept §21.3) is open for the solution architect; it reduces enumeration but does not replace this check

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-01/04/05 (PR-03) |

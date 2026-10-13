---
id: REQ-127
title: Enforcement of connection-scoped Property Visibility (ABAC)
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - entity
    - metamodel-configuration
    - role
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-127: Enforcement of connection-scoped Property Visibility (ABAC)

## Statement

For properties with `visibilityMode = connection-scoped`, the entity API MUST check at runtime via graph traversal whether the requesting user has a path to the requested entity via the configured `scopingConnectionType`. The value MAY only be returned if such a path exists. Otherwise the value MUST be replaced with `null` — analogous to the enforcement for `role-restricted` (REQ-126).

**Traversal logic** (depth 1, extensible):

1. Determine all entities in which the current user (via their `Person` entity) is involved via the `scopingConnectionType` (as source or target)
2. Check whether the requested entity is contained in this set
3. Yes → release the value; No → return `null`

The same check applies to every other read channel (REQ-160) and every write path (REQ-162). Connections whose type is used as a `scopingConnectionType` can only be created, changed or deleted by principals with a dedicated permission (REQ-161), so that users cannot grant themselves access.

## Rationale

Connection-based visibility allows dynamic, organizational-structure-driven access control without static role assignment. A domain architect who is assigned a new application via `DomainAssignment` automatically receives read access to restricted fields of this application — without manual role maintenance.

Security review SEC-02, SEC-03 (2026-10-13): if any user with entity write permission can create a scoping connection from their own Person entity, the control is circumvented by the people it is meant to restrict.

## Acceptance Criteria

**AC1** (Traversal positive):

- Given: property `securityClassification` with `visibilityMode=connection-scoped`, `scopingConnectionType=DomainAssignment`
- When: Michael (domain architect) is connected to `ApplicationComponent A` via a `DomainAssignment` connection and retrieves entity A
- Then: the `securityClassification` value is contained in the response

**AC2** (Traversal negative):

- When: Michael is **not** connected to entity B via `DomainAssignment` and retrieves entity B
- Then: `securityClassification` is `null`; no value in the response

**AC3** (Write protection):

- When: a user without a path tries to write to the field via PATCH, PUT, upsert, batch or restore
- Then: HTTP 403 for an explicit change; for full replace and restore the value is preserved (REQ-162); value unchanged

**AC4** (Catalog consistency):

- When: a catalog query contains connection-scoped properties
- Then: values are filtered individually per row; the user only sees values for entities in their own scope; filter, sort and aggregation on the property consider only rows in the user's scope (REQ-160)

**AC5** (Performance):

- When: a graph traversal is triggered for an entity with connection-scoped properties
- Then: response time p95 ≤ 200 ms additional latency compared to an unrestricted query (with ≤ 500 directly connected entities per user; cached)

**AC6** (No self-granted scope):

- Given: `scopingConnectionType=DomainAssignment` and a user with entity write permission but without the scope assignment permission of REQ-161
- When: the user creates a `DomainAssignment` connection from their own Person entity to entity B
- Then: the request is rejected with 403 and `securityClassification` of B remains `null` for the user

## Dependencies

- **Prerequisites**: REQ-124 (data model), REQ-036 (connection types), REQ-126 (enforcement pattern), REQ-161 (governance of scoping connections)
- **Consequences**: REQ-160 (visibility on all read channels), REQ-162 (write protection on all write paths)

## Implementation Notes

- The traversal result (set of scope entities per user and ConnectionType) should be cached per session or in a short-lived cache (TTL ≤ 60 s), since connections rarely change; removal of a scoping connection should invalidate the cache so that access ends within the revocation window of REQ-164
- Depth 1 is sufficient for the v1.0 scope; deeper traversal (e.g. domain → sub-domain → entity) is possible as a later extension

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-02/SEC-03/SEC-04: all read channels and write paths; no self-granted scope (AC6); references REQ-160, REQ-161, REQ-162 |

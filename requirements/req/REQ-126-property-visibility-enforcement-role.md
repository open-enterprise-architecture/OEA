---
id: REQ-126
title: Enforcement of role-restricted Property Visibility
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
    - SH-02
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-126: Enforcement of role-restricted Property Visibility

## Statement

When returning an entity, the entity API MUST replace all `PropertyDefinition` values with `visibilityMode = role-restricted` with `null` (marked as redacted) for users without one of the configured `readRoles`, and MUST reject writes by users without one of the configured `writeRoles`. The frontend MUST display a redacted field as **empty and not editable** — without any hint of the actual value. Filtering happens server-side; the actual value MUST NEVER reach the client for unauthorized users. The same masking applies to every other read channel (REQ-160) and the same write protection to every other write path (REQ-162).

## Rationale

Hiding on the client side is not sufficient — a technically savvy user could read the API response directly. Confidentiality must be enforced server-side.

Security review SEC-02, SEC-04 (2026-10-13): masking on the entity read path alone is bypassed via history, audit, portal, aggregations, filters, webhooks and full-replace or restore writes; these channels are covered by REQ-160 and REQ-162.

## Acceptance Criteria

**AC1** (API response):

- When: a user without an allowed role retrieves an entity with a role-restricted property
- Then: the API response contains `"investmentCostForecast": null`; no actual value in the response

**AC2** (UI empty and read-only):

- When: a user without permission opens the entity detail form
- Then: the restricted field is displayed empty; input is disabled; no tooltip with the real value

**AC3** (Write protection):

- When: a user without write permission tries to write to the field directly via the API (PATCH, PUT, upsert, batch, restore)
- Then: the API responds with HTTP 403 for an explicit change (PATCH); for full replace and restore the value is preserved (REQ-162); the value is not changed in any case

**AC4** (Authorized user):

- When: a user with one of the `readRoles` retrieves the same field
- Then: the actual value is returned; the field is editable if the user also has one of the `writeRoles`

**AC5** (Catalog consistency):

- When: a catalog (UC-06) shows a column for a role-restricted property
- Then: the column shows empty cells for unauthorized users; sorting, ad-hoc filters, saved filters, join filters, dashboard aggregations and `groupBy` on this property are rejected or ignored server-side for these users, so that the result does not differ from the result without that property

## Dependencies

- **Prerequisites**: REQ-124 (data model), REQ-001 (authentication), REQ-003 (role assignment)
- **Consequences**: REQ-160 (visibility on all read channels), REQ-162 (write protection on all write paths)

## Implementation Notes

- Filtering at the API level in a central read pipeline shared by all read channels (REQ-160); no DB filter needed for display — the value is masked after reading, but query building must exclude restricted properties from filter, sort and aggregation for unauthorized users
- Role check: `currentUser.roles ∩ property.readRoles ≠ ∅` → visible; `currentUser.roles ∩ property.writeRoles ≠ ∅` → writable

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-02/SEC-03/SEC-04: read/write roles; masking extended to filters, aggregations, `groupBy` server-side; all write paths; references REQ-160, REQ-162 |

---
id: REQ-126
title: Enforcement of role-restricted Property Visibility
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
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

When returning an entity, the entity API MUST replace all `PropertyDefinition` values with `visibilityMode = role-restricted` with `null` for users without one of the configured `allowedRoles`. The frontend MUST display a `null`-valued field with `visibilityMode ≠ public` as **empty and not editable** — without any hint of the actual value. Filtering happens server-side; the actual value MUST NEVER reach the client for unauthorized users.

## Rationale

Hiding on the client side is not sufficient — a technically savvy user could read the API response directly. Confidentiality must be enforced server-side.

## Acceptance Criteria

**AC1** (API response):
- When: a user without an allowed role retrieves an entity with a role-restricted property
- Then: the API response contains `"investitionskostenPrognose": null`; no actual value in the response

**AC2** (UI empty and read-only):
- When: a user without permission opens the entity detail form
- Then: the restricted field is displayed empty; input is disabled; no tooltip with the real value

**AC3** (Write protection):
- When: a user without permission tries to write to the field directly via the API (PATCH)
- Then: the API responds with HTTP 403; the value is not changed

**AC4** (Authorized user):
- When: a user with one of the `allowedRoles` retrieves the same field
- Then: the actual value is returned; the field is editable

**AC5** (Catalog consistency):
- When: a catalog (UC-06) shows a column for a role-restricted property
- Then: the column shows empty cells for unauthorized users; sorting and filtering of this column are disabled for these users

## Dependencies

- **Prerequisites**: REQ-124 (data model), REQ-001 (authentication), REQ-003 (role assignment)
- **Consequences**: –

## Implementation Notes

- Filtering at the API level in the entity read pipeline (middleware/interceptor); no DB filter needed — the value is masked after reading
- Role check: `currentUser.roles ∩ property.allowedRoles ≠ ∅` → visible

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |

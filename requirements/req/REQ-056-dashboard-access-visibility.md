---
id: REQ-056
title: Dashboard access and visibility (scope logic)
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-07
  business_objects:
    - dashboard
    - person
    - role
  business_rules: []
  stakeholders:
    - SH-01
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-056: Dashboard access and visibility (scope logic)

## Statement

The system MUST enforce the visibility of [dashboards](../../business-objects/dashboard.md) based on the `scope` attribute: `scope=instance` dashboards MUST be visible and readable for all logged-in users of the instance; `scope=personal` dashboards MAY be visible exclusively to the creator. No user may read `scope=personal` dashboards of other users, not even via direct API access.

## Rationale

Dashboards can contain confidential company metrics. Scope control ensures that personal analyses do not become public unintentionally and that C-level dashboards are consistently accessible to everyone.

## Type-Specific Fields

### API Endpoints and Visibility Rules

**Retrieve list**:

- `GET /api/v1/dashboards`
- Returns: all `scope=instance` dashboards + own `scope=personal` dashboards
- Does NOT return: `scope=personal` dashboards of other users

**Retrieve dashboard configuration**:

- `GET /api/v1/dashboards/{id}`
- `scope=instance`: HTTP 200 for all authenticated users
- `scope=personal` + requester ≠ creator: HTTP 403

**Retrieve dashboard data**:

- `GET /api/v1/dashboards/{id}/data`
- Same visibility rule as for the configuration

**Web portal**:

- All endpoints, including the web portal, require authentication (UC-01); there is no public or anonymous mode in v1 (owner decision Q1 of the 2026-10 security review, finding SEC-05; REQ-163)
- Dashboard data is subject to property visibility on all read channels (REQ-160)

## Business Rules

| Rule-ID | Statement | Trigger |
|---|---|---|
| BR-01 | `scope=personal` dashboards are visible exclusively to the creator and admins | onRead, onList |
| BR-02 | `scope=instance` dashboards are readable for all authenticated users of the instance | onRead |
| BR-03 | Write access (create, update, delete) is allowed exclusively to the creator of the dashboard and admins; plus: dashboard write permission for scope=instance (REQ-051 BR-02) | onWrite |
| BR-04 | A dashboard can be promoted from `personal` to `instance` if the creator has the dashboard write permission; downgrading from `instance` to `personal` is not possible if other users are already using the dashboard | onUpdate |
| BR-05 | Knowing the direct URL of a `scope=personal` dashboard ID does not bypass the visibility check; the API always checks the creator against the requester | onRead |

## Acceptance Criteria

**AC1** (instance dashboard: everyone sees it):

- Given: Kurt has created a `scope=instance` dashboard
- When: Franz calls `GET /api/v1/dashboards`
- Then: Kurt's dashboard appears in the list

**AC2** (personal dashboard: no data leak):

- Given: Franz has created a `scope=personal` dashboard
- When: Kurt calls `GET /api/v1/dashboards` or `GET /api/v1/dashboards/{franz-dashboard-id}`
- Then: Franz's dashboard neither appears in the list nor can it be retrieved by ID (403)

**AC3** (personal URL access blocked):

- Given: Kurt has the ID of Franz's personal dashboard (e.g., guessed or from logs)
- When: `GET /api/v1/dashboards/{id}/data` is called directly
- Then: HTTP 403

**AC4** (Scope upgrade):

- Given: Franz has a personal dashboard; he subsequently receives dashboard write permission
- When: `PUT /api/v1/dashboards/{id}` with scope=instance
- Then: HTTP 200; dashboard is visible to everyone from now on

**AC5** (Downgrade blocked if other users):

- Given: Kurt has an instance dashboard; 5 other users have opened it
- When: Kurt tries PUT with scope=personal
- Then: HTTP 409 "Dashboard is used by other users; downgrade not possible"

## Dependencies

- Obtains authentication from: REQ-001–007 (UC-01 Login); REQ-163 (no anonymous access); REQ-159 (object-level authorization)
- Permission model: role "Enterprise Architect" = dashboard write permission (analogous to catalog permission)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; scope enforcement, URL access protection, upgrade/downgrade logic |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-05 / owner decision Q1: "public instance" without login removed; references REQ-159, REQ-160, REQ-163 |

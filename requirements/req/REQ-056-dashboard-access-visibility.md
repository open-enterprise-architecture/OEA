---
id: REQ-056
title: Dashboard access and visibility (scope logic)
type: functional
priority: should
status: proposed
version: 0.3.0
created: 2026-06-26
last_updated: 2026-11-18
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

The system MUST enforce the visibility of [dashboards](../../business-objects/dashboard.md) based on the `scope` attribute: `scope=instance` dashboards MUST be visible and readable for all logged-in users of the instance; `scope=personal` dashboards MUST be visible to the creator and, read-only, to holders of `settings:manage` (owner decision 2026-11-18, ADR-032 amendment). No other user may read `scope=personal` dashboards of other users, not even via direct API access: the ID answers 404, like an ID that does not exist.

## Rationale

Dashboards can contain confidential company metrics. Scope control ensures that personal analyses do not become public unintentionally and that C-level dashboards are consistently accessible to everyone.

## Type-Specific Fields

### API Endpoints and Visibility Rules

**Retrieve list**:

- `GET /api/v1/dashboards`
- Returns: all `scope=instance` dashboards + own `scope=personal` dashboards; for holders of `settings:manage` also the personal dashboards of other persons, marked with their owner
- Does NOT return: `scope=personal` dashboards of other users to everybody else

**Retrieve dashboard configuration**:

- `GET /api/v1/views/dashboards/{id}`
- `scope=instance`: HTTP 200 for all authenticated users
- `scope=personal` + requester ≠ creator: HTTP 404 (HTTP 200 for holders of `settings:manage`; the table data of such a dashboard is computed with the administrator's own visibility)
- Service accounts: HTTP 403 on every dashboard operation

**Retrieve dashboard data**:

- `GET /api/v1/views/dashboards/{id}/data`
- Same visibility rule as for the configuration

**Web portal**:

- All endpoints, including the web portal, require authentication (UC-01); there is no public or anonymous mode in v1 (owner decision Q1 of the 2026-10 security review, finding SEC-05; REQ-163)
- Dashboard data is subject to property visibility on all read channels (REQ-160)

## Business Rules

| Rule-ID | Statement | Trigger |
|---|---|---|
| BR-01 | `scope=personal` dashboards are visible to the creator and, read-only, to holders of `settings:manage` | onRead, onList |
| BR-02 | `scope=instance` dashboards are readable for all authenticated users of the instance | onRead |
| BR-03 | Write access (create, update, delete) to a shared dashboard needs `dashboard:manage`; to a personal dashboard it is allowed to its owner only; administrators never change another person's personal dashboard (403) (REQ-051 BR-02, BR-03) | onWrite |
| BR-04 | A dashboard can be promoted from `personal` to `instance` if the creator has the dashboard write permission; downgrading from `instance` to `personal` is not possible (422 naming `scope`); a personal dashboard with a widget on a personal catalog does not become shared (422 naming `scope`) | onUpdate |
| BR-05 | Knowing the direct URL of a `scope=personal` dashboard ID does not bypass the visibility check; the API always checks the creator against the requester | onRead |

## Acceptance Criteria

**AC1** (instance dashboard: everyone sees it):

- Given: Kurt has created a `scope=instance` dashboard
- When: Franz calls `GET /api/v1/dashboards`
- Then: Kurt's dashboard appears in the list

**AC2** (personal dashboard: no data leak):

- Given: Franz has created a `scope=personal` dashboard
- When: Kurt calls `GET /api/v1/dashboards` or `GET /api/v1/dashboards/{franz-dashboard-id}`
- Then: Franz's dashboard neither appears in the list nor can it be retrieved by ID (404)

**AC3** (personal URL access blocked):

- Given: Kurt has the ID of Franz's personal dashboard (e.g., guessed or from logs)
- When: `GET /api/v1/views/dashboards/{id}/data` is called directly
- Then: HTTP 404, as for an ID that does not exist

**AC4** (Scope upgrade):

- Given: Franz has a personal dashboard; he subsequently receives dashboard write permission
- When: `PUT /api/v1/dashboards/{id}` with scope=instance
- Then: HTTP 200; dashboard is visible to everyone from now on

**AC5** (Downgrade blocked):

- Given: Kurt has an instance dashboard
- When: Kurt tries PUT with scope=personal
- Then: HTTP 422 naming `scope` (realigned 2026-11-18: a shared dashboard never becomes personal, whoever uses it)

**AC6** (Administrator reads a personal dashboard):

- Given: Michael has a personal dashboard; Sandra holds `settings:manage`
- When: Sandra lists the dashboards and opens it
- Then: it is listed with Michael as owner and can be read; changing or deleting it gives HTTP 403

## Dependencies

- Obtains authentication from: REQ-001–007 (UC-01 Login); REQ-163 (no anonymous access); REQ-159 (object-level authorization)
- Permission model: role "Enterprise Architect" = dashboard write permission (analogous to catalog permission)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; scope enforcement, URL access protection, upgrade/downgrade logic |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-05 / owner decision Q1: "public instance" without login removed; references REQ-159, REQ-160, REQ-163 |
| 0.3.0 | 2026-11-18 | Fullstack Engineer | us-055-dashboards, owner decisions 2026-11-18: 404 instead of 403 for another person's personal dashboard; "admins" are holders of `settings:manage` and read only; downgrade 422 instead of 409; paths of the reads |

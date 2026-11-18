---
id: REQ-051
title: Create a dashboard
type: functional
priority: should
status: proposed
version: 0.2.0
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
    - SH-03
    - SH-07
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-051: Create a dashboard

## Statement

The system MUST allow authorized users to create a new [Dashboard](../../business-objects/dashboard.md) object with a name, optional description, and scope (`instance` or `personal`). Dashboards with `scope=instance` MAY only be created by users who have the role "Enterprise Architect" or an equivalent dashboard write permission. A newly created dashboard contains no widgets and is immediately visible to authorized viewers. Only persons use dashboards; service accounts get 403. Shared dashboards are maintained by every holder of `dashboard:manage` (owner decision 2026-11-18).

## Rationale

Dashboards are the entry point for C-level reporting. Creation must be simple and validated; in particular, scope=instance must be protected by a permission so that no uncontrolled public dashboards arise.

## Context

Dashboard management actions take place exclusively in the **client app** (ADR-008). Viewing (read-only) is possible in the **web portal**. This REQ concerns only creation; widget configuration follows in REQ-052.

## Type-Specific Fields

### API Endpoints

**Create**:

- `POST /api/v1/dashboards`
- Request Body:

  ```json
  {
    "name": "IT Investment Planning 2026–2030",
    "description": "C-level reporting for the CIO",
    "scope": "instance"
  }
  ```

- Response 201: `{ "id": "uuid", "name": "...", "scope": "instance", "owner": {"id": 7, "displayName": "Kurt"}, "version": 1, "canEdit": true, "widgets": [] }`, with the `ETag` of the version
- Response 422 `dashboard-name-taken` (detail "A dashboard with this name already exists", `invalidFields` naming `name`): name already taken in the scope, compared case-insensitively after trimming; other invalid input is 422 `validation-failed` (as for catalogs; realigned 2026-11-18, formerly 409)
- Response 403: scope=instance without permission

**Edit (metadata)**:

- `PUT /api/v1/dashboards/{id}` with `If-Match` – update name, description, scope; every write needs the version that was read (428 without, 412 when stale)
- Scope upgrade (`personal` → `instance`) also requires dashboard write permission and no widget on a personal catalog (422 naming `scope`); a shared dashboard does not become personal (422 naming `scope`)

**List**:

- `GET /api/v1/dashboards` – returns `scope=instance` dashboards + own `personal` dashboards; never other people's `personal` dashboards, except for holders of `settings:manage`, who see them marked with the owner, read-only (owner decision 2026-11-18)

## Business Rules

| Rule-ID | Statement | Trigger |
|---|---|---|
| BR-01 | `name` must be unique: for `scope=instance` globally; for `scope=personal` per creator (case-insensitive, trimmed) | onCreate, onUpdate |
| BR-02 | `scope=instance` requires dashboard write permission; if missing → 403 | onCreate, onUpdate |
| BR-03 | A shared dashboard is changed and deleted by every holder of `dashboard:manage`; a personal dashboard by its owner only; administrators (`settings:manage`) never change or delete another person's personal dashboard (403) | onUpdate, onDelete |
| BR-04 | The dashboard `name` may be changed after creation as long as the uniqueness condition (BR-01) is maintained | onUpdate |

## Acceptance Criteria

**AC1** (Create with scope=instance):

- Given: Kurt (role Enterprise Architect) is logged in
- When: `POST /api/v1/dashboards` with name="IT Investment Planning" and scope=instance
- Then: HTTP 201; dashboard object in DB; visible to all users in GET /api/v1/dashboards

**AC2** (Duplicate name):

- Given: dashboard "IT Investment Planning" (scope=instance) already exists
- When: second POST with the same name
- Then: HTTP 422 `dashboard-name-taken` naming `name`, with the message "A dashboard with this name already exists"

**AC3** (Missing permission for scope=instance):

- Given: Franz (role Junior Domain Architect, no dashboard write permission) is logged in
- When: POST with scope=instance
- Then: HTTP 403; no dashboard created

**AC4** (Personal dashboard for any user):

- Given: Franz (without write permission) is logged in
- When: POST with scope=personal, name="My analysis"
- Then: HTTP 201; the dashboard is visible only to Franz (not included in GET /api/v1/dashboards for other users)

**AC5** (Scope upgrade):

- Given: Franz has a personal dashboard; later he receives the dashboard permission
- When: PUT with scope=instance
- Then: HTTP 200; the dashboard is visible to everyone from now on

## Dependencies

- Prerequisite: UC-01 (login, REQ-001–007)
- Implemented by: OpenSpec change `us-055-dashboards` (S16)
- Enables: REQ-052 (add widgets), REQ-055 (retrieve data), REQ-056 (control access)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
| 0.2.0 | 2026-11-18 | Fullstack Engineer | us-055-dashboards: 422 `dashboard-name-taken` instead of 409; every holder of `dashboard:manage` changes shared dashboards, the owner personal ones; version and `If-Match`; administrators read personal dashboards |

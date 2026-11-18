## ADDED Requirements

### Requirement: Create a dashboard

A person SHALL create a dashboard with a name of 1 to 200 characters, a description of at most 1000 and a scope. Scope `instance` SHALL need `dashboard:manage`, scope `personal` only `entity:read`. A new dashboard SHALL have no widgets. Names SHALL be unique among shared dashboards and among the personal ones of one owner, ignoring case. Only persons SHALL use dashboards.

Trace: US-055 AC1, AC2, AC3, US-062 AC1, REQ-051, BR-01

#### Scenario: Shared dashboard

- **WHEN** Kurt, holding `dashboard:manage`, creates "IT Investment Planning 2026–2030" with scope `instance`
- **THEN** it is created without widgets and is listed for every reader with `entity:read`

#### Scenario: Name already taken

- **GIVEN** the shared dashboard "IT Investment Planning 2026–2030"
- **WHEN** Kurt creates a shared dashboard named "it investment planning 2026–2030"
- **THEN** the request is rejected with 422 `dashboard-name-taken` naming `name`, and no dashboard is created

#### Scenario: Personal dashboard without dashboard:manage

- **WHEN** Michael, without `dashboard:manage`, creates "My analysis" with scope `personal`
- **THEN** it is created

#### Scenario: Shared dashboard without dashboard:manage

- **WHEN** Michael, without `dashboard:manage`, creates a dashboard with scope `instance`
- **THEN** the request is rejected with 403

#### Scenario: Service account

- **WHEN** a service account with `entity:read` lists the dashboards or creates one
- **THEN** both requests are rejected with 403

#### Scenario: Name at the limits

- **WHEN** Kurt creates dashboards named with 200 characters, with 201 characters and with only blanks, and one with a description of 1000 and of 1001 characters
- **THEN** the name of 200 and the description of 1000 are accepted, and the others are rejected with 422 naming the field

### Requirement: Visibility of dashboards

A shared dashboard SHALL be readable by every person with `entity:read`. A personal dashboard SHALL be readable by its owner and, read-only, by holders of `settings:manage` with `entity:read`; for everyone else it SHALL not be listed and its ID SHALL answer 404.

Trace: US-055 AC4, US-062 AC2, AC3, REQ-056, owner decision 2026-11-18

#### Scenario: Another person's personal dashboard

- **GIVEN** Michael has the personal dashboard "My analysis"
- **WHEN** Kurt lists the dashboards and reads "My analysis" by its ID
- **THEN** the list does not contain it, and the read answers 404

#### Scenario: Administrator reads a personal dashboard

- **GIVEN** Michael has the personal dashboard "My analysis"
- **WHEN** Sandra, holding `settings:manage`, lists the dashboards and opens "My analysis"
- **THEN** it is listed with Michael as owner and can be read, but changing it is rejected with 403

### Requirement: Who changes a dashboard

Holders of `dashboard:manage` SHALL change and delete every shared dashboard. Only the owner SHALL change and delete a personal dashboard. The owner holding `dashboard:manage` MAY change its scope to `instance` unless a widget shows a personal catalog; a shared dashboard SHALL NOT become personal (422 naming `scope`).

Trace: US-062 AC4, AC5, REQ-051, REQ-052, owner decision 2026-11-18

#### Scenario: Another manager edits a shared dashboard

- **GIVEN** Kurt created the shared dashboard "IT Investment Planning 2026–2030"
- **WHEN** Eva, holding `dashboard:manage`, renames it
- **THEN** the dashboard is renamed

#### Scenario: Scope upgrade with permission

- **GIVEN** Michael has the personal dashboard "My analysis" and was granted `dashboard:manage`
- **WHEN** Michael changes its scope to `instance`
- **THEN** it is shared and listed for every reader

#### Scenario: Scope upgrade without permission

- **GIVEN** Michael has the personal dashboard "My analysis" and no `dashboard:manage`
- **WHEN** Michael changes its scope to `instance`
- **THEN** the request is rejected with 403, and the dashboard stays personal

#### Scenario: Scope upgrade with a personal catalog

- **GIVEN** Michael holds `dashboard:manage`, and his personal dashboard "My analysis" has a table widget on his personal catalog
- **WHEN** Michael changes its scope to `instance`
- **THEN** the request is rejected with 422 naming `scope`, and the dashboard stays personal

#### Scenario: Shared to personal

- **WHEN** Kurt changes the scope of a shared dashboard to `personal`
- **THEN** the request is rejected with 422 naming `scope`

#### Scenario: Delete a dashboard

- **WHEN** Kurt deletes the shared dashboard "IT Investment Planning 2026–2030"
- **THEN** it and its widgets are gone, and its ID answers 404

### Requirement: Lost updates and audit of dashboards

Every change of a dashboard, its widgets or its layout SHALL need `If-Match` with the dashboard version: 428 without, 412 when stale; a successful change SHALL raise the version. Creating, changing and deleting SHALL be audited as `dashboard.created`, `dashboard.updated` and `dashboard.deleted`.

Trace: US-055, US-060, REQ-052, ADR-032

#### Scenario: Stale version

- **GIVEN** the dashboard has version 3
- **WHEN** Kurt adds a widget with `If-Match: 2`, and then without `If-Match`
- **THEN** the first request gives 412, the second 428, and nothing changes

#### Scenario: Audit of a change

- **WHEN** Kurt renames a dashboard
- **THEN** the audit records `dashboard.updated` with the dashboard ID and the changed field `name`

### Requirement: Dashboard pages

The web app SHALL offer the page "Dashboards" (SCR-056) with the visible dashboards and "Create dashboard", and the dashboard page (SCR-024). After creating, the empty dashboard SHALL open with the notice "No widgets yet. Add widget." Only those who may change a dashboard SHALL see its edit actions.

Trace: US-055 AC1, AC3, US-062, REQ-051

#### Scenario: Empty dashboard after creation

- **WHEN** Kurt creates "IT Investment Planning 2026–2030" on the page "Dashboards"
- **THEN** the dashboard page opens with "No widgets yet. Add widget."

#### Scenario: Reader sees no edit actions

- **WHEN** Franz, holding only `viewer`, opens a shared dashboard
- **THEN** it shows its widgets, and neither "Edit", "Add widget" nor "Delete"

# portal/portal-dashboards Specification

## Purpose

Defines how readers of the web portal see dashboards: which dashboards the portal lists, who reads them, the widget data in the portal and the read-only dashboard view (SCR-053).

## Requirements

### Requirement: List the portal dashboards

A person with `portal:read` SHALL list in the portal the dashboards with scope `instance` and the own personal dashboards, sorted by name ignoring case, each with scope, owner and number of widgets and never editable. Personal dashboards of other persons SHALL NOT be listed, also not for holders of `settings:manage`. A caller without `portal:read` or a service account SHALL be rejected with 403.

Trace: US-061, REQ-056, ADR-032, owner decisions 2026-11-19

#### Scenario: Shared dashboards for a portal reader

- **GIVEN** the shared dashboards "IT Investment Planning" and "Portfolio Health", and Michael's personal dashboard "My analysis"
- **WHEN** Petra, who holds only the role `portal-reader`, lists the portal dashboards
- **THEN** the list is "IT Investment Planning", "Portfolio Health", and it does not contain "My analysis"

#### Scenario: Own personal dashboard

- **WHEN** Michael lists the portal dashboards
- **THEN** the list contains "My analysis" with scope `personal` next to the shared dashboards

#### Scenario: Administrator

- **WHEN** Sandra, holding `settings:manage` and `portal:read`, lists the portal dashboards
- **THEN** the list does not contain "My analysis"

#### Scenario: Without portal permission

- **WHEN** a person without `portal:read`, or a service account with the role `viewer`, lists the portal dashboards
- **THEN** the response is 403

### Requirement: Read a dashboard in the portal

A person with `portal:read` SHALL read the configuration and data of a shared or own personal dashboard: widgets with types, titles, settings and grid positions, never editable. Another person's personal dashboard, also for holders of `settings:manage`, and an unknown one SHALL be answered with 404. A caller without `portal:read` or a service account SHALL get 403.

Trace: US-061 AC1, AC5, AC6, US-060 AC5, REQ-055, REQ-056, ADR-032 L3, owner decisions 2026-11-19

#### Scenario: Configuration of a shared dashboard

- **GIVEN** the shared dashboard "IT Investment Planning" with a KPI, a bar chart and a pie chart
- **WHEN** Petra reads it through the portal
- **THEN** she gets the three widgets with their positions and settings, and the dashboard is not editable

#### Scenario: Own personal dashboard

- **WHEN** Michael reads his personal dashboard "My analysis" through the portal
- **THEN** he gets its widgets and their data

#### Scenario: Another person's personal dashboard

- **WHEN** Petra, or Sandra holding `settings:manage`, reads Michael's personal dashboard "My analysis" through the portal, its configuration, its data or the data of one of its widgets
- **THEN** each response is 404, and no content of the dashboard is returned

#### Scenario: Unknown dashboard or widget

- **WHEN** Petra reads the data of an unknown dashboard, or of an unknown widget of "IT Investment Planning"
- **THEN** the response is 404

#### Scenario: Reading without portal permission

- **WHEN** a person without `portal:read`, or a service account with the role `viewer`, reads "IT Investment Planning" or its data through the portal
- **THEN** the response is 403, also for an unknown dashboard ID

#### Scenario: Manager reads without edit rights

- **WHEN** Kurt, holding `dashboard:manage`, reads "IT Investment Planning" through the portal
- **THEN** the dashboard is not editable

### Requirement: Widget data in the portal

The portal SHALL compute the data of every widget as the dashboard data does, with the reader's permissions and property visibility. A failing widget SHALL show its error, and the others SHALL be computed normally. Further pages of a table widget SHALL be read per widget with a 0-based page; a negative page SHALL be rejected with 400.

Trace: US-061 AC1, AC2, AC4, REQ-055, REQ-160

#### Scenario: KPI and charts

- **GIVEN** "IT Investment Planning" with a KPI counting application components and a bar chart grouped by status
- **WHEN** Petra opens its data through the portal
- **THEN** the KPI and the chart carry the same values as in the work area for a reader with the same visibility

#### Scenario: Failing widget

- **GIVEN** a chart widget of "IT Investment Planning" whose property was removed from the metatype
- **WHEN** Petra opens the dashboard in the portal
- **THEN** that widget has the error `invalid-configuration`, and the other widgets have their data

#### Scenario: Restricted property

- **GIVEN** a KPI with the sum of "Investment", readable only for the role `finance`
- **WHEN** Petra, without that role, opens the dashboard in the portal
- **THEN** the KPI has the error `restricted` and no value

#### Scenario: Pages of a table widget

- **GIVEN** a table widget over 47 entities with 10 rows per page
- **WHEN** Petra reads the widget data with page 0, page 1 and page -1
- **THEN** page 0 has entities 1 to 10, page 1 has entities 11 to 20 with the total 47, and page -1 is rejected with 400

### Requirement: Dashboards on the portal pages

The page "Portal" (SCR-050) SHALL list the portal dashboards in a section "Dashboards" next to the catalogs, personal ones marked. A click SHALL open the read-only view (SCR-053), with the widgets in their saved grid positions, without edit actions, drag and drop or resizing, and with "Refresh". A reader without `entity:read` SHALL be taken from `/dashboards` to the portal.

Trace: US-061 AC1, AC3, AC5, US-060 AC5, SCR-050, SCR-053, owner decision 2026-11-19

#### Scenario: Opening a dashboard

- **GIVEN** Petra holds only the role `portal-reader`
- **WHEN** she opens the page "Portal" and clicks "IT Investment Planning"
- **THEN** the dashboard opens with its widgets in their saved positions, and there is no "Edit", "Add widget" or "Delete", no drag handle and no resize handle

#### Scenario: Refresh

- **WHEN** Petra clicks "Refresh" on the dashboard
- **THEN** the widget data is read again and the time of the data is updated

#### Scenario: Work area link of a portal reader

- **WHEN** Petra opens `/dashboards` or `/dashboards/{id}` of "IT Investment Planning"
- **THEN** she is taken to the page "Portal", or to the portal view of "IT Investment Planning"

#### Scenario: No dashboards

- **GIVEN** there is no shared dashboard and Petra has no personal one
- **WHEN** she opens the page "Portal"
- **THEN** the section "Dashboards" shows "No dashboards yet."

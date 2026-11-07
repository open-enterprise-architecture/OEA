# portal/portal-catalogs Specification

## Purpose

Defines how readers of the web portal see the shared catalogs: which catalogs the portal lists, how a portal reader queries one read-only, who may read them, and the portal pages that show them (SCR-050).

## Requirements

### Requirement: List the shared catalogs

A reader with `portal:read` SHALL list the catalogs with scope `instance`, sorted by name ignoring case. The list SHALL NOT contain personal catalogs, not even the reader's own. A caller without `portal:read` SHALL be rejected with 403.

Trace: #67, UC-06, ADR-032 §8, owner decision 2026-11-07

#### Scenario: Shared catalogs only

- **GIVEN** the shared catalogs "Application Inventory" and "Interfaces", and Kurt's personal catalog "Kurt's Candidates"
- **WHEN** Petra, who holds only the role `portal-reader`, lists the portal catalogs
- **THEN** the list is "Application Inventory", "Interfaces", and it does not contain "Kurt's Candidates"

#### Scenario: Own personal catalog not in the portal

- **WHEN** Kurt lists the portal catalogs
- **THEN** the list does not contain "Kurt's Candidates"

#### Scenario: Without portal permission

- **WHEN** a principal without `portal:read` lists the portal catalogs
- **THEN** the response is 403

### Requirement: Query a shared catalog read-only

A reader with `portal:read` SHALL query a catalog with scope `instance` and SHALL get the result of the catalog query with the catalog's configured columns, default sort and default join mode. Paging, sorting, the cursor to the next page and the query time limit SHALL behave as in the catalog query. A personal catalog or an unknown catalog SHALL be answered with 404.

Trace: #67, #66, REQ-046, REQ-071, UC-06, ADR-032 §8, owner decisions 2026-11-07

#### Scenario: Rows of a shared catalog

- **GIVEN** the shared catalog "Application Inventory" with the columns "Name" and "Status"
- **WHEN** Petra queries it through the portal
- **THEN** the result has the columns "Name" and "Status" and the active applications as rows, as the catalog query returns them to an architect

#### Scenario: Sorting and paging

- **WHEN** Petra queries "Application Inventory" with `sort=name:desc` and `pageSize=10`
- **THEN** the result has at most 10 rows, sorted by name descending, and the page metadata counts all rows

#### Scenario: Next page through the cursor

- **WHEN** Petra queries the next page with the `nextCursor` of the first page and `page=1`
- **THEN** the rows are the same as with `page=1` alone

#### Scenario: Personal catalog

- **WHEN** Petra or Kurt queries Kurt's personal catalog "Kurt's Candidates" through the portal
- **THEN** the response is 404

#### Scenario: Unknown sort column

- **WHEN** Petra queries "Application Inventory" with `sort=unknown:asc`
- **THEN** the response is 400 naming `sort`

### Requirement: Visibility in the portal

The portal query SHALL apply the same property visibility as the catalog query: only properties with visibility `public` SHALL appear as columns, and soft-deleted entities SHALL NOT be returned. A sort on a property that is not a column SHALL be rejected.

Trace: REQ-160, REQ-124, ADR-019

#### Scenario: Restricted property

- **GIVEN** "Application Inventory" configures a column on a property whose visibility is not `public`
- **WHEN** Petra queries it through the portal
- **THEN** the result has no column for that property, and sorting by it answers 400 naming `sort`

#### Scenario: Deleted entity

- **WHEN** an application of "Application Inventory" is soft-deleted
- **THEN** the portal query no longer returns it and the total does not count it

### Requirement: Portal pages

The web portal SHALL show the shared catalogs on the page "Portal" (SCR-050) to every reader with `portal:read`. A click on a catalog SHALL open it read-only with its columns, paging and sorting, and with no actions that change the catalog. A reader without `entity:read` SHALL land on the page "Portal" after login and SHALL see the portal as the only entry in the header.

Trace: #67, UC-06, SCR-050, owner decision 2026-11-07

#### Scenario: Opening a shared catalog

- **GIVEN** Petra holds only the role `portal-reader`
- **WHEN** she logs in, sees the page "Portal" and clicks "Application Inventory"
- **THEN** the catalog opens with its columns and rows, she can sort and page, and there is no action to configure the catalog, to save a view or to place it in the navigation

#### Scenario: Landing page of a portal reader

- **WHEN** Petra opens `/` or `/catalogs` after login
- **THEN** she is taken to the page "Portal"

#### Scenario: Portal entry for architects

- **WHEN** Kurt, who holds `entity:read` and `portal:read`, opens the header
- **THEN** the header has the entry "Portal" next to the catalogs, and the page "Portal" lists the same shared catalogs

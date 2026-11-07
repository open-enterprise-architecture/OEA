# Delta for portal/portal-catalogs

## MODIFIED Requirements

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

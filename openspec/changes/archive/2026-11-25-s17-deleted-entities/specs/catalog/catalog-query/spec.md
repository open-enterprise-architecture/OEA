## ADDED Requirements

### Requirement: Show deleted rows

A catalog query with `includeDeleted` SHALL return the deleted entities of the primary metatype as well, each marked with its deletion time, with the same columns, visibility, filters, sorting and paging; it SHALL need `entity:restore` (403 otherwise). The catalog page SHALL offer holders of `entity:restore` the switch "Show deleted" with "Restore" on deleted rows; the switch SHALL NOT be saved in a view.

Trace: ADR-019, owner decision 2026-11-20

#### Scenario: Deleted rows shown

- **GIVEN** the catalog "Application Inventory" with 10 active applications, and "Legacy CRM" deleted
- **WHEN** Petra, holding `entity:restore`, switches on "Show deleted"
- **THEN** the catalog has 11 rows, "Legacy CRM" marked as deleted with its deletion time

#### Scenario: Restore from the catalog

- **WHEN** Petra chooses "Restore" on "Legacy CRM"
- **THEN** "Legacy CRM" is restored and shown as an active row

#### Scenario: Without entity:restore

- **WHEN** an API call by Anna, without `entity:restore`, queries the catalog with `includeDeleted`
- **THEN** the response is 403, and the catalog page offers her no "Show deleted"

#### Scenario: Default unchanged

- **WHEN** Petra queries the catalog without `includeDeleted`
- **THEN** "Legacy CRM" is not returned, as before

#### Scenario: Cursor of the other mode

- **WHEN** Petra sends the `nextCursor` of a query without `includeDeleted` with `includeDeleted`
- **THEN** the response is 400 naming `cursor`

#### Scenario: Deleted connection in a connection catalog

- **GIVEN** a connection catalog of "serves" connections, and "ERP serves Billing" deleted with "ERP"
- **WHEN** Petra queries it with `includeDeleted`
- **THEN** the connection is a row marked as deleted whose source column shows "ERP"

#### Scenario: Deleted row without join results

- **WHEN** Petra queries "Application Inventory" with a join in mode `rows` and `includeDeleted`
- **THEN** "Legacy CRM" is one row without join results

#### Scenario: Not in a saved view

- **WHEN** Petra saves a view while "Show deleted" is on
- **THEN** the view does not keep the switch, and opening it shows only active rows

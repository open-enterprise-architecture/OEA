# Delta for solution/solution-deltas

## ADDED Requirements

### Requirement: Any number of deltas

A solution SHALL hold any number of deltas. Capturing a delta SHALL NOT be rejected because of the number of deltas the solution already holds.

Trace: REQ-040, owner decision 2026-11-13

#### Scenario: No limit of deltas

- **GIVEN** a solution holds 1000 deltas
- **WHEN** a further delta is captured
- **THEN** the response is 201

### Requirement: Paging of the deltas

The deltas SHALL be paged with `page` and `pageSize` from 1 to 200, default 50. The response SHALL carry the number of deltas per type over all pages and, while a following page exists, an opaque `nextCursor`; a query with `cursor` and `page` SHALL return the deltas after the cursor's page. A changed cursor, one of another solution or one without `page` SHALL answer 400 naming `cursor`.

Trace: owner decision 2026-11-13, REQ-071

#### Scenario: Pages by cursor

- **GIVEN** a solution with 60 deltas of all three types
- **WHEN** every page of size 20 is loaded with the cursor of the previous one
- **THEN** the pages hold every delta exactly once, in the order of the delta list, and the last page has no `nextCursor`
- **AND** each page carries the counts per type over all 60

#### Scenario: Limits of the page size

- **WHEN** the deltas are loaded with page size 1 or 200
- **THEN** the response is 200
- **WHEN** they are loaded with page size 0 or 201
- **THEN** the response is 400

#### Scenario: Cursor of another solution

- **WHEN** a cursor of another solution is sent, or a changed cursor
- **THEN** the response is 400 naming `cursor`

### Requirement: Load more deltas on the page "Solution"

The area "Changes" SHALL show the first page of the deltas and the total. While a following page exists, it SHALL offer "Load more", which appends the next page. After a delta is captured, edited or removed, the area SHALL load its first page again.

Trace: owner decision 2026-11-13, SCR-061

#### Scenario: Load more

- **GIVEN** a solution with 60 deltas
- **WHEN** a reader opens the page "Solution"
- **THEN** the area "Changes" shows 50 deltas, the total 60 and "Load more"
- **WHEN** the reader chooses "Load more"
- **THEN** it shows all 60 deltas and no longer offers "Load more"

#### Scenario: One page only

- **GIVEN** a solution with 50 deltas
- **WHEN** a reader opens the page "Solution"
- **THEN** the area "Changes" shows all 50 deltas without "Load more"

## MODIFIED Requirements

### Requirement: Read the deltas

A principal with `entity:read` SHALL read the deltas of a solution, `new`, then `modified`, then `retiring`, each group by name (case-insensitive), then by delta ID. Each delta SHALL carry its ID, type, name, metatype and time of capture; a delta `modified` or `retiring` additionally the entity ID; a delta `new` additionally its description and property values; a delta `modified` its changes. An unknown solution SHALL answer 404.

Trace: US-040 AC1, US-041 AC1, US-042 AC1, REQ-040

#### Scenario: Delta list

- **GIVEN** a solution holds the deltas `new` `Salesforce` and `retiring` `CRM-Legacy`
- **WHEN** a principal with `entity:read` reads the deltas
- **THEN** `Salesforce` is listed before `CRM-Legacy`, each with its type, name and metatype
- **AND** `CRM-Legacy` carries its entity ID

#### Scenario: No deltas

- **WHEN** the deltas of a new solution are read
- **THEN** the list is empty

#### Scenario: Unknown solution

- **WHEN** the deltas of an unknown solution ID are read, or a delta is captured in it
- **THEN** the response is 404

## REMOVED Requirements

### Requirement: Limit of deltas per solution

**Reason**: owner decision 2026-11-13: a solution holds any number of deltas; the delta list and the diff are paged instead.

**Migration**: none; no stored data changes. A client that relied on the complete delta list in one response follows `nextCursor`.

## ADDED Requirements

### Requirement: Baseline of a solution in plateau mode

The baseline of a solution that targets a plateau SHALL be the basis of that plateau: the current catalog for the basis `catalog`, the content of the predecessor for the basis `predecessor`. Search, metatype filter, order, paging and the entry read SHALL apply to it as to the catalog baseline. A solution without plateaus SHALL keep the catalog.

Trace: US-155 AC1, AC2, REQ-039, owner decision 2026-11-17

#### Scenario: Basis catalog

- **GIVEN** "CRM Replacement" targets "Target 2027" with the basis `catalog`
- **WHEN** Michael loads its baseline
- **THEN** it lists the current catalog

#### Scenario: Basis predecessor

- **GIVEN** "Analytics Platform" targets "Target 2028" with the basis `predecessor`, and "CRM Replacement" of "Target 2027" renames "ERP-Core" to "ERP-Core 2"
- **WHEN** Michael loads the baseline of "Analytics Platform" and searches "ERP-Core 2"
- **THEN** it lists "ERP-Core 2"

### Requirement: Lifecycle of a baseline entry

Each baseline entry SHALL carry the lifecycle `active`, `retiring` or `proposed`. An entry that a delta of a predecessor solution retires or proposes SHALL name that solution. A proposed entry SHALL have no entity ID and SHALL NOT get a delta until it exists.

Trace: US-155 AC2, concept §11.2

#### Scenario: Retiring entry

- **GIVEN** "CRM Replacement" of "Target 2027" retires "Legacy CRM"
- **WHEN** the baseline of a solution of "Target 2028" with the basis `predecessor` is loaded
- **THEN** "Legacy CRM" is `retiring` and names "CRM Replacement"

#### Scenario: Proposed entry

- **GIVEN** "CRM Replacement" proposes the new entity "Salesforce"
- **WHEN** the same baseline is loaded
- **THEN** "Salesforce" is `proposed`, names "CRM Replacement" and has no entity ID

#### Scenario: Catalog entry

- **WHEN** the baseline of a project solution is loaded
- **THEN** every entry is `active` and names no solution

### Requirement: Basis in the area "Baseline"

The header of the page "Solution" (SCR-061) SHALL name the start and target plateau of a solution in plateau mode. The area "Baseline" SHALL name its basis and SHALL mark retiring and proposed entries with the solution that changes them; a proposed entry SHALL offer neither "Modify" nor "Retire".

Trace: US-155 AC2, US-038 AC3

#### Scenario: Plateaus in the header

- **WHEN** Michael opens a solution of "Target 2027"
- **THEN** the header names the baseline and "Target 2027"

#### Scenario: Proposed entry on the page

- **WHEN** Michael views "Salesforce" as a proposed entry
- **THEN** it is marked "Proposed by CRM Replacement" and offers no action

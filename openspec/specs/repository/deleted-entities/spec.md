# repository/deleted-entities Specification

## Purpose

Lists the soft-deleted entities of the repository for holders of `entity:restore`, with search, metatype filter and cursor paging, and shows deleted rows in catalogs on request, so that a deleted entity can be found and restored with its cascade (ADR-019, US-074).

## Requirements

### Requirement: List deleted entities

A principal with `entity:restore` SHALL list the deleted entities and connections, newest deletion first, then by ID, in pages of 1 to 200 (default 50), filtered by a name part of at most 255 characters and by metatype. Each entry SHALL carry name, metatype, deletion time and actor, and the entity whose deletion took it along. Without `entity:restore` the request SHALL be rejected with 403.

Trace: ADR-019, owner decision 2026-11-20

#### Scenario: Deleted with a cascade

- **GIVEN** Kurt deleted "ERP" with its child "ERP Finance"
- **WHEN** Petra, holding `entity:restore`, lists the deleted entities
- **THEN** "ERP" and "ERP Finance" are listed with Kurt and the deletion time, and "ERP Finance" names "ERP" as the entity it was deleted with

#### Scenario: Filters

- **WHEN** Petra lists with the name part "fin" and the metatype "Application Component"
- **THEN** only "ERP Finance" is listed

#### Scenario: Limits

- **WHEN** Petra lists with page sizes 1, 200, 0 and 201, and with a name part of 255 and of 256 characters
- **THEN** 1, 200 and the name part of 255 are accepted, and the others are rejected with 400

#### Scenario: Without entity:restore

- **WHEN** Anna, holding `entity:write` but not `entity:restore`, lists the deleted entities
- **THEN** the response is 403

### Requirement: Page "Deleted entities"

The web app SHALL offer holders of `entity:restore` the page "Deleted entities" (SCR-068) with the list, the name and metatype filters, "Load more" and "Restore" per entry; restoring SHALL show what was restored and what stayed deleted. Others SHALL not see the page or its header entry.

Trace: ADR-019, owner decision 2026-11-20

#### Scenario: Restore from the page

- **WHEN** Petra chooses "Restore" on "ERP" and confirms
- **THEN** "ERP" and "ERP Finance" are restored and leave the list, and a notice names the connection that stayed deleted because its other endpoint is deleted

#### Scenario: Version conflict

- **WHEN** Petra restores an entry that someone restored or changed meanwhile
- **THEN** the page says so and reloads the list

#### Scenario: Not offered

- **WHEN** Anna, without `entity:restore`, opens the header
- **THEN** there is no entry "Deleted entities", and opening the page directly shows "No permission"

#### Scenario: Empty

- **WHEN** Petra opens the page and nothing is deleted
- **THEN** it shows "No deleted entities."

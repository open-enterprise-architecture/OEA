# Delta for solution/solution-overview

## Purpose

Defines how solutions are read: the list and the single solution through the API, the page "Solutions" with the dialog "New solution", and the page of one solution with its architecture vision.

## ADDED Requirements

### Requirement: Read solutions

A principal with `entity:read` SHALL list all solutions, ordered by name case-insensitively, each with ID, name, status, owner ID and owner name, and creation time, and SHALL read one solution with its architecture vision, plateau references, owner and creator. An unknown ID SHALL answer 404; a caller without `entity:read` SHALL get 403.

Trace: US-038 AC1, REQ-038 Outputs

#### Scenario: Listed solutions

- **GIVEN** the solutions "CRM Replacement" and "ERP Extension Logistics" exist
- **WHEN** Anna, holding `viewer`, lists the solutions
- **THEN** both are listed in this order with their status and owner name

#### Scenario: Unknown solution

- **WHEN** Anna reads a solution with an unknown ID
- **THEN** the response is 404

#### Scenario: Without entity:read

- **WHEN** a person holding only `portal-reader` lists the solutions
- **THEN** the response is 403

### Requirement: Page "Solutions"

The page "Solutions" (SCR-060) SHALL list the solutions with name, status, owner and creation time and link each to its page. It SHALL offer "New solution" only with `solution:write`; the dialog SHALL ask for name and architecture vision, show a rejection with its detail without closing, and open the page of the created solution on success. The header SHALL link to the page for `entity:read`.

Trace: US-038 AC1, AC2, AC5, REQ-038 Outputs

#### Scenario: Create through the dialog

- **GIVEN** Michael holds `solution-architect`
- **WHEN** he creates "ERP Extension Logistics" in the dialog "New solution"
- **THEN** the page of the new solution opens and shows its name, status `draft` and the architecture vision

#### Scenario: Name taken in the dialog

- **WHEN** Michael creates a solution with a taken name in the dialog
- **THEN** the dialog stays open and shows "Name 'ERP Extension Logistics' is already taken"

#### Scenario: Reader without solution:write

- **WHEN** Anna, holding `viewer`, opens the page "Solutions"
- **THEN** she sees the list and no button "New solution"

### Requirement: Page "Solution"

The page of one solution (SCR-061) SHALL show its name, status, owner, creation time and architecture vision, and a not-found message for an unknown ID.

Trace: US-038 AC1, REQ-038 Outputs

#### Scenario: Unknown solution page

- **WHEN** Anna opens the page of a solution that does not exist
- **THEN** the page shows that the solution was not found

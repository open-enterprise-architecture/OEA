# Delta for catalog/catalog-joins

## MODIFIED Requirements

### Requirement: Edit and remove a join

Editing a join SHALL replace its definition and keep its ID. Removing it SHALL remove its columns from the query, and from the saved views the join mode overrides, filters and sort that refer to it.

Trace: US-048 AC6, REQ-045 AC4, US-053

#### Scenario: Remove a join

- **WHEN** Kurt removes the join "Interfaces"
- **THEN** the catalog no longer contains it, and the query returns no interface results

#### Scenario: Remove a join used by a view

- **GIVEN** the view "Detail view" shows the join "Interfaces" in rows mode, sorted by its protocol
- **WHEN** Kurt removes the join "Interfaces"
- **THEN** "Detail view" has neither the join mode override nor the sort

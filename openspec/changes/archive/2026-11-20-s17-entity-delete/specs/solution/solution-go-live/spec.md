## ADDED Requirements

### Requirement: Deltas on deleted entities

A delta `modified` or `retiring` whose entity is deleted SHALL be a deletion conflict: the delta list SHALL mark it, the go-live preview SHALL list it, and the go-live SHALL be rejected with 409 `entity-deleted` naming the deltas while one exists, and nothing SHALL change. Removing the delta or restoring the entity SHALL resolve it. A delta `new` SHALL NOT be affected.

Trace: US-152, owner decision 2026-11-20, ADR-019

#### Scenario: Conflict after a deletion

- **GIVEN** "Finance Upgrade" has a delta `modified` on "ERP Finance", which is then deleted
- **WHEN** Petra reads the deltas and the go-live preview of "Finance Upgrade"
- **THEN** the delta is marked as on a deleted entity, and the preview lists it as a deletion conflict

#### Scenario: Go-live blocked

- **WHEN** Petra starts the go-live of "Finance Upgrade" with the deletion conflict
- **THEN** the response is 409 `entity-deleted` naming the delta, and nothing goes live

#### Scenario: Retiring a deleted entity

- **GIVEN** "Finance Upgrade" has a delta `retiring` on "Legacy CRM", which is then deleted
- **WHEN** Petra starts the go-live
- **THEN** the response is 409 `entity-deleted` naming the delta

#### Scenario: Resolved by removing the delta

- **WHEN** Petra removes the delta on "ERP Finance" and starts the go-live
- **THEN** the go-live runs

#### Scenario: Resolved by restoring the entity

- **WHEN** Kurt restores "ERP Finance" and Petra starts the go-live
- **THEN** the go-live runs with the delta

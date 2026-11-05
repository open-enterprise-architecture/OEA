# catalog/catalog-joins Specification

## Purpose

Defines the join definitions of a catalog: which connection metatype, direction and target metatype a join follows, which columns of the target it shows, its default join mode, how it is validated, and who may configure it.

## Requirements

### Requirement: Add a join

A join SHALL name a connection metatype, a direction (`outbound`, `inbound` or `both`), a target metatype, at least one visible target column and an optional default join mode. Adding it SHALL assign an ID and keep the joins in creation order. A catalog SHALL have at most 10 joins.

Trace: US-048 AC1, AC5, REQ-045 AC1, owner decision 2026-11-05

#### Scenario: Interfaces of an application

- **GIVEN** a catalog for `Application Component` and the connection metatype `Data Flow` from `Application Component` to `Interface`
- **WHEN** Kurt adds the join "Interfaces" via `Data Flow`, outbound, to `Interface` with the columns name and protocol in mode aggregate
- **THEN** the catalog contains the join with a new ID, and the query returns the interfaces of each application as a list

#### Scenario: Default mode of the join

- **WHEN** Kurt sets the default join mode `rows` for the join "Interfaces"
- **THEN** the query without override returns one row per interface, and the catalog's default join mode stays unchanged

### Requirement: Validation of a join

The connection metatype SHALL be a connection, otherwise 422. The target metatype SHALL be reachable through it in the given direction by its allowed endpoints, otherwise 422. Target columns SHALL follow the column rules for the target metatype.

Trace: US-048 AC2–AC4, REQ-045 AC2, AC3, AC5

#### Scenario: Not a connection metatype

- **WHEN** a join names `Application Component` as its connection metatype
- **THEN** the request is rejected with 422 naming the connection metatype

#### Scenario: Target not reachable

- **GIVEN** `Data Flow` allows only `Interface` as target
- **WHEN** a join via `Data Flow`, outbound, names `Technology Node` as target metatype
- **THEN** the request is rejected with 422 naming the target metatype

#### Scenario: Unknown target column

- **WHEN** a target column names a property that `Interface` does not map
- **THEN** the request is rejected with 422 naming the column

#### Scenario: Only connections and reachable targets offered

- **WHEN** Kurt adds a join in SCR-020 and chooses `Data Flow` and the direction outbound
- **THEN** only connection metatypes are offered as connection, and only `Interface` as target metatype

### Requirement: Edit and remove a join

Editing a join SHALL replace its definition and keep its ID. Removing it SHALL remove its columns from the query and the join mode overrides of saved views that refer to it.

Trace: US-048 AC6, REQ-045 AC4

#### Scenario: Remove a join

- **WHEN** Kurt removes the join "Interfaces"
- **THEN** the catalog no longer contains it, and the query returns no interface results

### Requirement: Who configures the joins

The joins of an instance catalog SHALL be changed only with `catalog:manage`, those of a personal catalog only by its creator; any other request SHALL be rejected with 403. Every change SHALL be audited with the actor.

Trace: REQ-045, ADR-032 §8, owner decision 2026-11-05

#### Scenario: Reader without catalog management

- **WHEN** a person holding only `viewer` adds a join to an instance catalog
- **THEN** the request is rejected with 403 and the catalog stays unchanged

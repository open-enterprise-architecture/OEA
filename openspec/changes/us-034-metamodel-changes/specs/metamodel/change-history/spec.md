# Spec Delta

## Purpose

Defines how metamodel administrators read the changes of the metamodel from the audit trail: which events, with which names and filters, how an import batch is recognized, and that a change and its audit event are atomic.

## ADDED Requirements

### Requirement: Reading metamodel changes

A principal with `metamodel:write` SHALL list the audit events of metamodel changes, newest first, each with time, kind, element name and the name of the person. Readers without `metamodel:write` SHALL be rejected with 403; other audit events SHALL NOT be listed.

Trace: US-034 AC1, REQ-034 AC1, AC3, owner decision 2026-11-05

#### Scenario: Two administrators changed the metamodel

- **GIVEN** Kurt created the metatype `SecurityZone` and Lukas deleted another metatype
- **WHEN** Kurt lists the metamodel changes
- **THEN** he sees both entries with the names Kurt and Lukas, their times and the element names

#### Scenario: Without metamodel administration

- **WHEN** a person holding only `viewer` lists the metamodel changes
- **THEN** the request is rejected with 403

### Requirement: Filtering and import batches

The list SHALL be filterable by kind of change, person, period and import batch. The events of one package import SHALL carry the same batch ID, so that filtering by it shows the whole import.

Trace: US-034 AC2, REQ-034 AC2, owner decision 2026-11-05

#### Scenario: An import as one batch

- **GIVEN** a package with 5 metatypes was imported
- **WHEN** an administrator lists the metamodel changes of its batch
- **THEN** the list shows the 5 imported metatypes with the same batch ID

#### Scenario: Changes of one person

- **WHEN** an administrator filters the metamodel changes by Lukas
- **THEN** only the changes made by Lukas are listed

### Requirement: Change and audit event are atomic

A metamodel change and its audit event SHALL be written together: if the audit event cannot be written, the change SHALL NOT be made.

Trace: US-034 AC3, REQ-034 AC4 (realigned), ADR-033, owner decision 2026-11-05

#### Scenario: Audit trail not writable

- **GIVEN** the audit event of a change cannot be written
- **WHEN** an administrator creates a metatype
- **THEN** the request fails and no metatype is created

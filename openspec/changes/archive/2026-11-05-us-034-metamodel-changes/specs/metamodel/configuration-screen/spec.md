# Spec Delta

## ADDED Requirements

### Requirement: Changes in the configuration

A metamodel administrator SHALL see a section "Changes" listing the metamodel changes with time, person, kind and element, filterable by kind, person and period. An import SHALL show its batch, and selecting it SHALL list the whole batch.

Trace: US-034 AC1, AC2, REQ-034, owner decision 2026-11-05

#### Scenario: Who changed what

- **GIVEN** Kurt created `SecurityZone` and Lukas deleted another metatype
- **WHEN** Kurt opens the section "Changes"
- **THEN** both changes are listed with name, time and element

#### Scenario: Readers see no changes

- **WHEN** a person holding only `entity:read` opens the metamodel configuration
- **THEN** the section "Changes" is not offered

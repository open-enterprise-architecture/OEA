## ADDED Requirements

### Requirement: Parallel-change setting of a metatype

Each metatype SHALL have the setting "Show parallel changes in the baseline" (`showParallelChanges`), off by default. A principal with `metamodel:write` SHALL set it through `PUT /metamodel/metatypes/{id}/parallel-changes` with `If-Match`, for a metatype of any scope. Each change SHALL raise the version of the metatype and SHALL be audited as `metamodel.metatype-parallel-changes-changed` with the value before and after.

Trace: US-044, owner decision 2026-11-13

#### Scenario: Turning the setting on for a built-in metatype

- **GIVEN** the setting of the built-in metatype `Application Component` is off
- **WHEN** a metamodel administrator turns it on
- **THEN** the metatype shows `showParallelChanges` true and a version raised by 1
- **AND** the metamodel changes list the change with the administrator, under the kind `metatype`

#### Scenario: Default off

- **WHEN** a metamodel administrator creates the metatype `SecurityZone`
- **THEN** its setting is off

#### Scenario: Without metamodel administration

- **WHEN** a person holding only `solution-metamodel:write` sets the setting
- **THEN** the request is rejected with 403 and the setting is unchanged

#### Scenario: Outdated version

- **WHEN** the setting is sent with a version other than the current one
- **THEN** the request is rejected with 412 and the setting is unchanged

### Requirement: Parallel-change setting kept and not inherited

A package re-import SHALL keep the setting "Show parallel changes in the baseline". A subtype SHALL NOT inherit the setting of its base type.

Trace: US-044, ADR-049, owner decision 2026-11-13

#### Scenario: Re-import keeps the setting

- **GIVEN** the setting of an imported metatype is on
- **WHEN** its package is imported again
- **THEN** the setting is still on

#### Scenario: Not inherited

- **GIVEN** the setting of a base type is on and that of its subtype is off
- **WHEN** the baseline of a solution is read
- **THEN** entities of the subtype carry no parallel changes

## MODIFIED Requirements

### Requirement: Read-only metatypes

Built-in and imported metatypes SHALL NOT be changed or deleted; such a request SHALL be rejected with 409 and problem type `read-only-metatype`. Mapping organization properties to them, setting their re-import setting and setting "Show parallel changes in the baseline" stay possible.

Trace: REQ-032 AC5, ADR-002, owner decisions 2026-10-30, 2026-10-31 and 2026-11-13

#### Scenario: Imported metatype

- **WHEN** a metamodel administrator renames the imported metatype `Application Component`
- **THEN** the request is rejected with 409 and problem type `read-only-metatype`, and the metatype is unchanged

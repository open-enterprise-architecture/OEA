# Spec Delta

## Purpose

Defines the edit mode of a metamodel configuration: in `import-only`, metamodel content changes only through package imports; who switches the mode and what stays possible while it is active.

## ADDED Requirements

### Requirement: Switching the edit mode

A principal with `metamodel:write` SHALL switch the edit mode of the instance configuration between `gui-and-import` and `import-only`, one with `solution-metamodel:write` that of an extension. Every switch SHALL be audited as `metamodel.edit-mode-changed` with the actor.

Trace: US-035, REQ-035, BR-06, owner decision 2026-11-04

#### Scenario: Instance locked

- **WHEN** a metamodel administrator sets the instance configuration to `import-only`
- **THEN** `GET /metamodel/configurations` returns `editMode=import-only` for it and the audit trail names the administrator

### Requirement: Writes rejected in import-only

While a configuration is `import-only`, every write on its metatypes, mappings and – for the instance – property definitions, enum values, categories and labels SHALL be rejected with 403 and problem type `metamodel-locked`, regardless of the caller's permissions; nothing SHALL change. Package imports, re-import settings and the edit mode SHALL stay possible.

Trace: US-035 AC1, AC2, REQ-035, BR-06

#### Scenario: Creating a metatype in a locked instance

- **GIVEN** the instance configuration is `import-only`
- **WHEN** a metamodel administrator creates a metatype
- **THEN** the request is rejected with 403 `metamodel-locked` and no metatype is created

#### Scenario: Import in a locked instance

- **GIVEN** the instance configuration is `import-only`
- **WHEN** a metamodel administrator imports a package
- **THEN** the package is imported as usual

#### Scenario: Extension stays editable

- **GIVEN** the instance configuration is `import-only` and an extension is `gui-and-import`
- **WHEN** an architect creates a metatype in the extension
- **THEN** the metatype is created

# Spec Delta

## MODIFIED Requirements

### Requirement: Validated and limited package input

The system SHALL accept a package only if it is valid JSON, conforms to the package schema of the contract, and stays within the instance limits for size (default 10 MB), nesting depth and element count. A rejected package SHALL change nothing; the response SHALL name the first problem with its location.

Trace: REQ-033, REQ-184 AC1, REQ-184 AC2

#### Scenario: Syntactically faulty package

- **WHEN** a person with `metamodel:write` imports a file that is not valid JSON
- **THEN** the request is rejected with 422, the problem names line and column, and no metatype is stored

#### Scenario: Package violating the schema

- **WHEN** a package contains a metatype without a name or a property definition with an unknown data type
- **THEN** the request is rejected with 422 naming the offending element, and nothing is stored

#### Scenario: Package above the size limit

- **WHEN** a package is larger than the configured maximum
- **THEN** the request is rejected with 413 before it is parsed

#### Scenario: Unsupported package sections

- **WHEN** a package contains viewpoints or shape assignments, or requests the strategy `replace`
- **THEN** the request is rejected with 422 naming the unsupported part, and nothing is stored

## ADDED Requirements

### Requirement: Labels of a package

A package MAY bring labels per locale for its own metatypes, categories, property definitions and enum values; the import SHALL store them as package entries, and a re-import SHALL replace only them, keeping organization labels. A key outside the package or an unsupported locale SHALL reject the import with 422.

Trace: REQ-152, US-140 AC1, ADR-025, ADR-049, owner decision 2026-11-04

#### Scenario: Starter package with German labels

- **WHEN** a person with `metamodel:write` imports `oea-starter-togaf-classic` 1.1.0
- **THEN** the German labels of its metatypes, of `Status` and of its values are stored as package entries

#### Scenario: Re-import keeps organization labels

- **WHEN** an administrator set a German label for a package metatype and the package is imported again
- **THEN** the package labels are replaced and the organization label stays in effect

#### Scenario: Label of a foreign element

- **WHEN** a package brings a label for a metatype that is not part of the package
- **THEN** the import is rejected with 422 naming the key, and nothing is stored

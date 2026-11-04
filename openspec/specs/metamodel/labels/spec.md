# metamodel/labels Specification

## Purpose

Defines the translated labels of metamodel content – names of metatypes and categories, labels of property definitions and enum values – how administrators and packages maintain them per locale, and how clients read them as a cached bundle.

## Requirements

### Requirement: Maintaining labels per locale

A principal with `metamodel:write` SHALL read and set the labels of a locale (`de`, `en`, `fr`, `it`) for metatypes, categories, property definitions and enum values of any scope; an empty text removes the organization label. An unknown key SHALL be rejected with 422 and nothing stored. Audited as `metamodel.labels-changed` with locale and number of keys.

Trace: REQ-152 AC2, US-140, ADR-025, owner decision 2026-11-04

#### Scenario: Translating an imported metatype

- **WHEN** a metamodel administrator sets the German label `Anwendungskomponente` for the imported metatype `Application Component`
- **THEN** the label is stored as an organization entry, reading the German labels returns it with `scope=organization`, and an audit event `metamodel.labels-changed` names the locale `de`

#### Scenario: Unknown key

- **WHEN** a metamodel administrator sets a label for a key that names no existing metatype
- **THEN** the request is rejected with 422 and problem type `validation-failed` naming the key, and no label is stored

#### Scenario: Setting labels without permission

- **WHEN** a person without `metamodel:write` sets a label
- **THEN** the request is rejected with 403

### Requirement: Organization labels over package labels

For a key with both an organization entry and a package entry in a locale, the organization entry SHALL apply. Removing the organization entry SHALL let the package entry apply again.

Trace: ADR-049 rule 5, owner decision 2026-11-04

#### Scenario: Organization label wins

- **WHEN** the starter package labels `Application Component` in German as `Anwendungskomponente` and an administrator sets `Applikation`
- **THEN** the German bundle contains `Applikation`, and after the administrator removes the label it contains `Anwendungskomponente` again

### Requirement: Label bundle of a locale

Every authenticated principal SHALL read the bundle of a locale as a flat map from label key to text, holding every metatype, category, property definition and enum value; without a label in the locale the key SHALL carry the name in the corporate language. An unsupported locale SHALL be rejected with 400.

Trace: US-140 AC1, REQ-152 AC2, ADR-025

#### Scenario: German bundle after the starter import

- **WHEN** a logged-in person reads the German bundle after `oea-starter-togaf-classic` 1.1.0 was imported
- **THEN** the key of `Application Component` carries its German label, and the key of an organization metatype without a German label carries its name

#### Scenario: Reading without a session

- **WHEN** a request without a session reads the German bundle
- **THEN** the request is rejected with 401

### Requirement: Caching of the bundle by ETag

The bundle SHALL be answered with an `ETag` derived from its content and with `Cache-Control: max-age=300, stale-while-revalidate=3600`. A request whose `If-None-Match` matches the current ETag SHALL be answered with 304 and no body; any change of a text in the bundle SHALL change the ETag.

Trace: REQ-152 AC1, REQ-152 AC2, US-140 AC2, ADR-025

#### Scenario: Unchanged bundle

- **WHEN** the German labels did not change since the last request and the client sends `If-None-Match` with the ETag it received
- **THEN** the response is 304 without a body

#### Scenario: Bundle after a change

- **WHEN** an administrator renames a metatype and the client then reads the German bundle with its former ETag
- **THEN** the response is 200 with a new ETag, and the bundle contains the new text

# Spec Delta

## Purpose

Defines how concurrent changes to the metamodel are detected: the versions of metatypes, property definitions and property categories, the `ETag` and `If-Match` headers on their writes, and the rejection of a write based on a stale version.

## ADDED Requirements

### Requirement: Versions of metamodel elements

Metatypes, property definitions and property categories SHALL carry a version that starts at 1 and grows with every change, also by a package import. Changes to the mappings or the re-import setting of a metatype SHALL count as changes of the metatype, changes to enum values as changes of the definition. Responses SHALL return the version in the body and as `ETag`.

Trace: UC-04 A5, REQ-032 (trade-offs), owner decision 2026-11-04

#### Scenario: Version after a change

- **WHEN** an administrator changes the abbreviation of an organization metatype in version 1
- **THEN** the response contains version 2 and the `ETag` `"2"`, and `GET /metamodel/metatypes/{id}` returns the same

#### Scenario: A mapping changes the metatype version

- **WHEN** an administrator maps a property to a metatype in version 3
- **THEN** `GET /metamodel/metatypes/{id}` returns version 4

#### Scenario: An enum value changes the definition version

- **WHEN** an administrator adds an enum value to a property definition in version 1
- **THEN** the property definition has version 2

### Requirement: Rejecting a stale write

A write on a metatype, property definition or category, or on its mappings, enum values or re-import setting, SHALL be rejected with 412 and problem type `stale-version` when its `If-Match` does not name the current version; nothing SHALL change. Without `If-Match` the write SHALL proceed as before.

Trace: UC-04 A5, REQ-032 (trade-offs), owner decision 2026-11-04

#### Scenario: Two administrators change the same metatype

- **GIVEN** two administrators have read an organization metatype in version 2
- **WHEN** the first saves a new name with `If-Match: "2"` and then the second saves another name with `If-Match: "2"`
- **THEN** the first write succeeds with version 3, and the second is rejected with 412 `stale-version` and the metatype keeps the first name

#### Scenario: Deleting with a stale version

- **WHEN** an administrator deletes a property definition with an `If-Match` of an older version
- **THEN** the request is rejected with 412 and the definition still exists

#### Scenario: Write without If-Match

- **WHEN** an API client changes a property category without `If-Match`
- **THEN** the change succeeds and the version grows by one

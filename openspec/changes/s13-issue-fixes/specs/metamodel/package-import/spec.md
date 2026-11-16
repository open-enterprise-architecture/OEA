## ADDED Requirements

### Requirement: Conflicts answered as a problem document

A package import rejected because of conflicts SHALL be answered with 409, `application/problem+json`, the problem type `urn:oea:problem:package-conflict`, and the conflicting elements in the extension member `conflictList`, each with the element and the reason. No element of the package SHALL be stored.

Trace: REQ-114, architecture review 2026-11 API-3, issue #262

#### Scenario: Conflicting metatype

- **WHEN** a package with a metatype that belongs to another package is imported
- **THEN** the response is 409 with `application/problem+json`, the problem type `urn:oea:problem:package-conflict` and the metatype in `conflictList`, and nothing is stored

#### Scenario: Conflicts shown on the screen

- **WHEN** the import on the metamodel screen is answered with this 409
- **THEN** the screen lists the conflicting elements with their reasons

## ADDED Requirements

### Requirement: Composition mark

A metamodel administrator SHALL mark at most one connection metatype of the instance metamodel as composition (`isComposition`); marking a second one, an element metatype or one of a solution extension SHALL give 422 naming `isComposition`. A package MAY mark one of its connection metatypes. The starter package SHALL bring "Composition", marked, without endpoint restriction.

Trace: REQ-142, ADR-021, owner decision 2026-11-18

#### Scenario: Mark a composition metatype

- **WHEN** an administrator marks the connection metatype "Composed of" as composition
- **THEN** `GET /metamodel/metatypes/{id}` returns `isComposition=true`, and the change is audited like other metatype changes

#### Scenario: Second composition metatype

- **GIVEN** "Composition" is marked as composition
- **WHEN** an administrator marks "Composed of" as composition
- **THEN** the request is rejected with 422 naming `isComposition`

#### Scenario: Element metatype

- **WHEN** an administrator creates the element metatype "Business Unit" marked as composition
- **THEN** the request is rejected with 422 naming `isComposition`

#### Scenario: Starter package

- **WHEN** the starter package 1.3.0 is imported
- **THEN** the connection metatype "Composition" is marked as composition and allows every element metatype except "Structuring aid" as source and target

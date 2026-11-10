# Delta for metamodel/property-visibility

## ADDED Requirements

### Requirement: Person metatype on the page

The page "Property visibility" SHALL show the person metatype and let a holder of `visibility:configure` choose it among the element metatypes, with a hint that connection-based properties need it.

Trace: REQ-127, owner decision 2026-11-10

#### Scenario: Choose the person metatype

- **WHEN** the security administrator chooses `Business Actor` as "Entity type of persons"
- **THEN** the page shows `Business Actor` and offers no connection metatypes in the selection

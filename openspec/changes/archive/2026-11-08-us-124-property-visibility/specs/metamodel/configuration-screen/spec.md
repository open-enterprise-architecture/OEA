# Delta for metamodel/configuration-screen

## ADDED Requirements

### Requirement: Visibility shown read-only

The metamodel configuration SHALL mark every property whose visibility mode is not `public` with the badge "Restricted" in the property list. The detail of a property SHALL show its visibility mode, roles and scoping type read-only and refer to the page "Property visibility" for changes.

Trace: US-125 AC1, REQ-125, owner decision 2026-11-07

#### Scenario: Restricted property in the list

- **WHEN** a metamodel administrator opens the property list and `investmentCost` is `role-restricted`
- **THEN** its row shows the badge "Restricted" and the row of a `public` property does not

#### Scenario: Visibility in the detail

- **WHEN** the metamodel administrator opens `investmentCost`
- **THEN** the detail shows "Role-based" with its read and write roles, without a control to change them

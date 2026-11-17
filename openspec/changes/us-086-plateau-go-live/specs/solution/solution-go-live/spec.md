## ADDED Requirements

### Requirement: A plateau solution goes live with its plateau

The go-live of a single solution that targets a plateau SHALL be refused with 409 `plateau-solution`, and nothing SHALL change. Its preview SHALL stay readable. The page "Solution" SHALL offer no "Go live" for it and SHALL link its plateau.

Trace: US-157 AC3

#### Scenario: Single go-live of a plateau solution

- **GIVEN** "CRM Replacement" targets "Target 2027"
- **WHEN** Michael takes "CRM Replacement" live
- **THEN** the response is 409 `plateau-solution`, and the solution keeps its status

#### Scenario: Project solution

- **GIVEN** "Website Relaunch" has no plateau
- **WHEN** Michael takes it live
- **THEN** it goes live as before

### Requirement: Current value of a plateau solution

For a delta of a solution that targets a plateau, the current value of a field conflict, and the value a resolution records as the before value, SHALL be the value at its turn in the go-live of the plateau.

Trace: US-157 AC2

#### Scenario: Resolved against the earlier solution

- **GIVEN** "CRM Replacement" goes first and changes the description of "ERP-Core" to "Core ERP"
- **WHEN** Michael resolves the conflict of "ERP Extension" on that description with the current value
- **THEN** the field leaves the delta of "ERP Extension", and "Core ERP" stays after the go-live

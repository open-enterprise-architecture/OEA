## ADDED Requirements

### Requirement: Create a solution in plateau mode

A person with `solution:write` SHALL create a solution with `fromPlateauId` and `toPlateauId`. `toPlateauId` SHALL be a target or transition plateau that is not realized, and `fromPlateauId` SHALL be its predecessor. A solution without plateau references SHALL still be created in project mode, also once plateaus exist. The references SHALL NOT change after the creation.

Trace: US-038 AC3, REQ-038, BR-01, owner decision 2026-11-17

#### Scenario: Solution with plateaus

- **GIVEN** the baseline P0 and the target P1, which succeeds P0, exist
- **WHEN** Michael creates a solution with `fromPlateauId=P0` and `toPlateauId=P1`
- **THEN** the response is 201 with both references, and P1 counts one solution

#### Scenario: Project solution once plateaus exist

- **GIVEN** the baseline P0 exists
- **WHEN** Michael creates a solution without plateau references
- **THEN** the response is 201 with both references null

#### Scenario: Target is the baseline

- **WHEN** Michael creates a solution with `toPlateauId=P0`, the baseline
- **THEN** the response is 422 naming `toPlateauId`, and no solution is created

#### Scenario: Start is not the predecessor

- **GIVEN** the transition "Phase 1" succeeds P1
- **WHEN** Michael creates a solution with `fromPlateauId=P0` and `toPlateauId` "Phase 1"
- **THEN** the response is 422 naming `fromPlateauId`, and no solution is created

#### Scenario: Unknown plateau

- **WHEN** Michael creates a solution with an unknown `toPlateauId`
- **THEN** the response is 422 naming `toPlateauId`

### Requirement: Both plateau references or none

A solution with only one of `fromPlateauId` and `toPlateauId` SHALL be rejected with 422 and the detail "Both plateau references must be set or both empty"; nothing SHALL be stored or audited.

Trace: US-038 AC4, REQ-038, BR-01 of the solution

#### Scenario: Only the start

- **WHEN** Michael creates a solution with `fromPlateauId=P0` and without `toPlateauId`
- **THEN** the response is 422 with the detail "Both plateau references must be set or both empty", and no solution is created

#### Scenario: Only the target

- **WHEN** Michael creates a solution with `toPlateauId=P1` and without `fromPlateauId`
- **THEN** the response is 422 with the same detail

### Requirement: Plateau in the dialog "New solution"

When plateaus exist, the dialog "New solution" (SCR-060) SHALL offer the target and transition plateaus that are not realized as an optional target, and SHALL show the predecessor of the chosen plateau as the start. Without plateaus, the dialog SHALL be unchanged.

Trace: US-038 AC3, UC-05 A1

#### Scenario: Target chosen

- **GIVEN** P1 succeeds P0
- **WHEN** Michael picks P1 in the dialog
- **THEN** the dialog shows P0 as the start, and saving sends both references

#### Scenario: No plateaus

- **GIVEN** no plateau exists
- **WHEN** Michael opens the dialog
- **THEN** it shows no plateau field

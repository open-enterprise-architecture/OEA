## ADDED Requirements

### Requirement: Metamodel question per plateau

The question of removing a mapping, deleting an enumeration value or changing a base type SHALL group the affected solutions that target a plateau by that plateau. One decision per plateau SHALL apply to all its affected solutions. A solution without a plateau SHALL be decided on its own. The 409 `solution-decision-required` SHALL name the plateau of each affected solution.

Trace: US-156 AC1, AC2, owner decision 2026-11-17

#### Scenario: Asked once per plateau

- **GIVEN** the active solutions "CRM Replacement" and "ERP Extension" target "Target 2027", and both have values of "Cost Center"
- **WHEN** Kurt removes the mapping of "Cost Center" without decisions
- **THEN** the answer is 409 listing both solutions with "Target 2027", and the dialog asks once for "Target 2027"

#### Scenario: Decision by plateau

- **WHEN** Kurt removes the mapping with the decision `keep` for "Target 2027"
- **THEN** both solutions keep a snapshot of "Cost Center"

#### Scenario: Solution without a plateau

- **GIVEN** "Website Relaunch" has no plateau and has a value of "Cost Center"
- **WHEN** Kurt removes the mapping with `apply` for "Target 2027" and without a decision for "Website Relaunch"
- **THEN** the answer is 409 listing "Website Relaunch", and the mapping stays

### Requirement: Decisions by plateau only

A decision SHALL name either a solution or a plateau. A decision that names a solution which targets a plateau, or a plateau without affected solutions, SHALL be rejected with 422 naming it; nothing SHALL change.

Trace: US-156 AC3

#### Scenario: Decision for a solution of a plateau

- **GIVEN** "CRM Replacement" targets "Target 2027"
- **WHEN** Kurt removes the mapping with a decision for "CRM Replacement"
- **THEN** the answer is 422 naming "CRM Replacement", and the mapping stays

#### Scenario: Decision naming both

- **WHEN** a decision names a solution and a plateau
- **THEN** the answer is 400, and the mapping stays

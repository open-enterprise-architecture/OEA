## ADDED Requirements

### Requirement: Values of a new entity before it exists

A value of a delta `new` has no entity yet. In the deltas, the capture response and the diff of a solution, a `connection-scoped` value SHALL be readable only through an override role, otherwise redacted; a `role-restricted` value SHALL be readable through a read or override role. This SHALL also hold for a default stored for a property the writer may not write.

Trace: REQ-160, security audit 2026-11 AA-02, issue #256

#### Scenario: Property became connection-scoped after the capture

- **GIVEN** a delta `new` with a value of "Salary band", which became `connection-scoped` after the capture
- **WHEN** a person with an account link reads the deltas of the solution
- **THEN** "Salary band" is marked as redacted and carries no value

#### Scenario: Override role reads the value

- **GIVEN** the same delta `new`
- **WHEN** a holder of an override role reads the deltas of the solution
- **THEN** the value of "Salary band" is returned

#### Scenario: Diff of the solution

- **GIVEN** the same delta `new`
- **WHEN** a person with an account link reads the diff of the solution
- **THEN** "Salary band" is redacted

#### Scenario: Stored default of a write-protected property

- **GIVEN** a delta `new` captured by a writer without the write role of the `role-restricted` property "Cost", whose default was stored
- **WHEN** a reader without a read role of "Cost" reads the deltas
- **THEN** "Cost" is redacted, and a reader with a read role sees the default

## ADDED Requirements

### Requirement: Snapshots in the effective metamodel of an extension

The effective metamodel of the extension of a solution SHALL include the snapshots of the solution: a metatype keeps a property, or an enumeration keeps a value, that a snapshot names, marked as `snapshot`.

Trace: US-154 AC3

#### Scenario: Snapshot in the extension

- **WHEN** a client reads the metatypes of the extension of "CRM Replacement", which kept "Cost Center" for "Application Component"
- **THEN** "Application Component" lists "Cost Center" marked as `snapshot`

#### Scenario: Without a snapshot

- **WHEN** the decision was `apply`
- **THEN** "Application Component" no longer lists "Cost Center"

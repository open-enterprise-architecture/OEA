## ADDED Requirements

### Requirement: Metamodel changes in active solutions

Removing a mapping SHALL ask about every affected solution: one not `implemented` or `archived` with a delta value the removal deletes, or with an extension metatype inheriting the metatype. The request SHALL carry `apply` or `keep` per affected solution; otherwise it SHALL answer 409 `solution-decision-required` listing them, and nothing changes. Without affected solutions it SHALL run as before.

Trace: US-154 AC1, AC5, owner decision 2026-11-17

#### Scenario: Asked for an affected solution

- **WHEN** Kurt removes the mapping of "Cost Center" from "Application Component" without decisions, and a delta of "CRM Replacement" has a value of "Cost Center"
- **THEN** the answer is 409 `solution-decision-required` listing "CRM Replacement", and the mapping stays

#### Scenario: No affected solution

- **WHEN** no active solution has a value of "Cost Center" or an extension metatype based on "Application Component"
- **THEN** the mapping is removed without a question

#### Scenario: Implemented solution not asked

- **WHEN** only an `implemented` solution has a value of "Cost Center"
- **THEN** the mapping is removed without a question

#### Scenario: Decision missing for one solution

- **WHEN** two solutions are affected and Kurt sends a decision for one
- **THEN** the answer is 409 listing both, and nothing changes

### Requirement: Apply a metamodel change to a solution

The decision `apply` SHALL remove the values the change removes from the deltas of the solution, in the same transaction as the change. A delta `modified` left without a change SHALL be removed. The metatypes of the extension of the solution SHALL lose the property through inheritance. The audit event of the change SHALL name the decisions.

Trace: US-154 AC2

#### Scenario: Values removed from the deltas

- **WHEN** Kurt removes the mapping of "Cost Center" with `apply` for "CRM Replacement"
- **THEN** the deltas of "CRM Replacement" no longer have "Cost Center", and `metamodel.mapping-deleted` names the decision

#### Scenario: Modification left empty

- **WHEN** a delta `modified` changed only "Cost Center"
- **THEN** the delta is removed

### Requirement: Keep a snapshot in a solution

The decision `keep` SHALL store a snapshot for the solution, naming the metatype and the property, or the enumeration value. The deltas of the solution SHALL keep their values, and the checks of its deltas and the effective metamodel of its extension SHALL treat the snapshot as part of the metamodel, until the go-live drops the snapshots of the solution.

Trace: US-154 AC3

#### Scenario: Snapshot kept

- **WHEN** Kurt removes the mapping of "Cost Center" with `keep` for "CRM Replacement"
- **THEN** the catalog entities lose "Cost Center", and the deltas of "CRM Replacement" keep their values

#### Scenario: New delta with a snapshot property

- **WHEN** Michael captures a delta of an application in "CRM Replacement" with a value of "Cost Center"
- **THEN** the delta is stored

## ADDED Requirements

### Requirement: Base type changes in active solutions

A change of a base type that removes inherited properties SHALL ask about every affected active solution for each removed property, as a removed mapping does, with the decisions `apply` and `keep`.

Trace: US-154 AC1–AC3

#### Scenario: Inherited property lost

- **WHEN** Kurt changes the base type of "Cloud Service" so that it no longer inherits "Cost Center", and a delta of "CRM Replacement" has a value of it
- **THEN** the answer is 409 `solution-decision-required` until a decision for "CRM Replacement" is sent

#### Scenario: Base type change without lost properties

- **WHEN** the new base type still provides every inherited property
- **THEN** the change runs without a question

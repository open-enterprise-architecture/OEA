## ADDED Requirements

### Requirement: Deleted enumeration values in active solutions

Deleting an enumeration value SHALL ask about every active solution with a delta that uses the value, as a removed mapping does, with the decisions `apply` and `keep`. A property definition SHALL NOT be deleted while a snapshot of a solution names it (409 `in-use`).

Trace: US-154 AC1–AC3

#### Scenario: Enumeration value used in a delta

- **WHEN** Kurt deletes the enumeration value `high` without decisions, and a delta of "CRM Replacement" uses it
- **THEN** the answer is 409 `solution-decision-required` listing "CRM Replacement", and the value stays

#### Scenario: Definition with a snapshot

- **WHEN** Kurt deletes the definition "Cost Center", which a snapshot of "CRM Replacement" names
- **THEN** the answer is 409 `in-use`

# Delta for repository/entity-write

## MODIFIED Requirements

### Requirement: Writing restricted properties

A value of a `role-restricted` property SHALL be written or removed only by a principal holding one of its write roles, and a value of a `connection-scoped` property only for an entity in the writer's scope. A batch or connection that names such a property otherwise SHALL be rejected with 403 as a whole and the denial audited. Writing an entity without naming a restricted property SHALL keep its value.

Trace: US-126 AC3, US-127 AC1, AC2, REQ-126, REQ-127 AC3, REQ-162, REQ-160 AC4

#### Scenario: Write attempt without write role

- **WHEN** Anna, without a write role, writes `investmentCost` of an application through the batch
- **THEN** the response is 403, nothing in the batch is written, and the value stays unchanged

#### Scenario: Write with write role

- **WHEN** Kurt, holding the write role `enterprise-architect`, writes `investmentCost` = 120
- **THEN** the value is stored and its audit change is encrypted

#### Scenario: Restricted value kept

- **WHEN** Anna writes the description of the same application without naming `investmentCost`
- **THEN** the entity is written and `investmentCost` keeps its value

#### Scenario: Connection-scoped value in scope

- **WHEN** Michael writes `securityClassification` of "ERP Warehouse", which is assigned to his element
- **THEN** the value is stored and its audit change is encrypted

#### Scenario: Connection-scoped property

- **WHEN** Michael writes `securityClassification` of "CRM Sales", or of a new entity without assignment
- **THEN** the response is 403 and nothing in the batch is written

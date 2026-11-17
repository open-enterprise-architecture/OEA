## ADDED Requirements

### Requirement: Before values from the basis

For a solution that targets a plateau, a delta `modified` SHALL record its before values from the baseline of the solution, so from the basis of its plateau. A delta `modified` or `retiring` SHALL only be captured for an entity in that baseline; an entity retired by a predecessor solution SHALL still be capturable.

Trace: US-155 AC2, REQ-040, owner decision 2026-11-17

#### Scenario: Before value from the predecessor

- **GIVEN** "CRM Replacement" of "Target 2027" changes the description of "ERP-Core" to "Core ERP", and "Analytics Platform" targets "Target 2028" with the basis `predecessor`
- **WHEN** Michael captures a modification of "ERP-Core" in "Analytics Platform"
- **THEN** the before value of the description is "Core ERP"

#### Scenario: Before value from the catalog

- **GIVEN** "Website Relaunch" has no plateau
- **WHEN** Michael captures a modification of "ERP-Core" in it
- **THEN** the before value of the description is the one in the catalog

### Requirement: No delta for a proposed entity

A proposed entry of the baseline has no entity yet. A delta that names it SHALL be impossible; a delta naming an entity ID that is not in the baseline SHALL answer as before.

Trace: US-155 AC2

#### Scenario: Proposed entity

- **GIVEN** "Salesforce" is proposed by a solution of "Target 2027"
- **WHEN** Michael views it in the baseline of a solution of "Target 2028"
- **THEN** it has no entity ID, and no action captures a delta for it

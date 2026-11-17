## ADDED Requirements

### Requirement: Diff of a plateau solution

The current state of a diff entry of a solution that targets a plateau SHALL be the entity in the baseline of the solution, the basis of its plateau, instead of the catalog. The drift marking SHALL compare the recorded before value with that state.

Trace: US-155, REQ-041

#### Scenario: Basis predecessor

- **GIVEN** "CRM Replacement" of "Target 2027" changes the description of "ERP-Core" to "Core ERP", and "Analytics Platform" of "Target 2028" (basis `predecessor`) changes it to "Analytics ERP"
- **WHEN** Michael reads the diff of "Analytics Platform"
- **THEN** the current description is "Core ERP", and the field is not marked as changed since the capture

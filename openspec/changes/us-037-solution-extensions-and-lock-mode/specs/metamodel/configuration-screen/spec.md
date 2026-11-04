# Spec Delta

## ADDED Requirements

### Requirement: Lock mode in the configuration

Under settings, an administrator SHALL see and switch whether the instance metamodel is locked (`import-only`). While it is locked, the configuration SHALL show a notice that points to the package import and SHALL offer no action that changes metamodel content.

Trace: US-035 AC1, AC3, REQ-035, owner decision 2026-11-04

#### Scenario: Locked metamodel

- **GIVEN** the instance metamodel is locked
- **WHEN** an administrator opens the metamodel configuration
- **THEN** all metatypes are shown with a notice about the lock mode and the package import, and no action to create, change or delete is offered

#### Scenario: Unlocking

- **WHEN** an administrator switches the lock mode off under settings
- **THEN** the actions to create, change and delete are offered again

---
id: REQ-066
title: Configurable entity creation workflows (wizard)
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-05
    - UC-06
    - UC-08
  business_objects:
    - metamodel-configuration
    - entity
  business_rules:
    - metamodel-configuration:BR-19
    - metamodel-configuration:BR-20
    - metamodel-configuration:BR-21
    - metamodel-configuration:BR-22
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/40-extensibility/14-extensibility.md
    - concept/40-extensibility/15-schema-evolution.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-066: Configurable entity creation workflows (wizard)

## Statement

For every `EntityTypeDefinition` with at least one configured `creationStep`, the system MUST automatically open a multi-page wizard when creating a new entity — regardless of whether in the catalog context or the diagram context. Each `CreationStep` corresponds to exactly one popover page. Navigation: "Next" for all steps except the last, "Done" on the last step. After "Done", the entity is persisted atomically in the repository and is immediately available in the triggering context.

## Rationale

OEA should allow the greatest possible degree of freedom in the metamodel: each instance defines its own EntityTypes, properties and rules. Pure save-time validations (ConstraintRule, MandatoryConnectionConstraint) block after the fact — but they offer no proactive guidance through mandatory fields and mandatory links. The wizard solves this problem by guiding users step by step through all configured requirements before the entity is even created. The result is a complete, rule-compliant object — without rework.

## Context

The `creationSteps` configuration sits on `EntityTypeDefinition` in the MetamodelConfiguration (BO: metamodel-configuration.md, version 0.8.0). Three step types:

- `properties`: shows all properties of a `propertyCategory`
- `domainAssignment`: shows architecture domain selection (multi-select)
- `connectionAssignment`: shows target entity search + selection; creates the connection after completion

The wizard applies to **all** creation entry points:

- "+ Entity" in the catalog (catalog context: after Done, the entity appears as a new row)
- "Place element" in the diagram (diagram context: after Done, the shape appears on the canvas)
- API (`POST /api/v1/entities` with wizard payload)

## Type-Specific Fields

### Configuration (Metamodel Level, UC-04)

A metamodel configurator defines the steps in the MetamodelConfiguration:

```yaml
entityTypeDefinitions:
  - id: application-component
    name: ApplicationComponent
    creationSteps:
      - stepId: basic-info
        title: "Basic data"
        description: "Name and basic information"
        stepType: properties
        propertyCategory: "General"

      - stepId: domain-assignment
        title: "Domain assignment"
        description: "Which architecture domain does this application belong to?"
        stepType: domainAssignment

      - stepId: capability-link
        title: "Capability link"
        description: "Which capability does this application realize?"
        stepType: connectionAssignment
        connectionType: "realizes-capability"
        targetEntityType: "capability"
        minConnections: 1
```

With this configuration, a 3-page wizard opens when creating an ApplicationComponent: step 1 shows properties of the category "General"; step 2 shows domain selection; step 3 shows capability search. Navigation: "Next" → "Next" → "Done".

### Wizard API (POST /api/v1/entities, extended payload)

```json
{
  "entityTypeId": "application-component",
  "name": "CRM-System",
  "properties": [
    { "propertyName": "owner", "value": "IT-Operations" }
  ],
  "architectureDomainIds": ["finance", "sales"],
  "connections": [
    {
      "connectionTypeId": "realizes-capability",
      "targetEntityId": 17
    }
  ]
}
```

The backend creates the entity and all specified connections in one transaction. If the transaction fails, neither the entity nor the connections are persisted.

### Validation

- Mandatory fields of the `properties` step: immediate inline validation when leaving the field; the "Next" button remains activatable (so the user can navigate forward and return), but "Done" is blocked if mandatory properties are empty
- `connectionAssignment` with `minConnections=1`: "Done" is blocked if no connection is selected
- `domainAssignment`: always optional (minCount=0); no blocking

## Acceptance Criteria

**AC1** (Wizard opens automatically – catalog context):

- Given: ApplicationComponent has 3 `creationSteps`
- When: Lukas clicks "+ New entity" in the ApplicationComponent catalog
- Then: Wizard opens on page 1 "Basic data"; no classic creation modal

**AC2** (Wizard opens automatically – diagram context):

- When: Lukas places an ApplicationComponent element in the diagram (drag & drop from palette)
- Then: Wizard opens immediately; entity is only visible on the canvas after "Done"

**AC3** (Navigation Next / Done):

- Given: 3 steps
- When: Lukas navigates: Next → Next → Done
- Then: Step 1 → step 2 → step 3 → Done; entity and all connections persisted atomically

**AC4** (Back navigation):

- When: Lukas is on step 2 and clicks "Back"
- Then: Step 1 is displayed again; data entered in step 1 is retained

**AC5** (Mandatory property blocks Done):

- Given: Step 1 has property `owner` (validationMode=mandatory); Lukas leaves it empty
- When: Lukas clicks "Done" (or tries to reach step 3)
- Then: Error message on the corresponding step; no persistence

**AC6** (connectionAssignment minConnections=1):

- Given: Step 3 "Capability link" with minConnections=1
- When: Lukas clicks "Done" without having selected a capability
- Then: Step 3 shows error "At least 1 capability must be linked"; no persistence

**AC7** (Cancel = no entity):

- When: Lukas closes the wizard (ESC or X) on step 2
- Then: No entity in the repository; no partially created connection

**AC8** (No wizard without creationSteps):

- Given: EntityType `stereotype` without `creationSteps` (empty list)
- When: User creates a new instance
- Then: Classic creation modal (no wizard); no regression error

**AC9** (Atomic persistence):

- Given: Wizard with entity + 2 connections
- When: The creation of the second connection fails server-side (e.g., target entity deleted)
- Then: Neither the entity nor the first connection is persistent; HTTP 422 with error description

## Dependencies

- **Prerequisites**: metamodel-configuration.md v0.8.0 (CreationStep); REQ-036 (EntityType configuration); REQ-033 (import — workflow configuration must be importable)
- **Downstream effects**: Solves the core problem of proactive data completeness; replaces reactive ConstraintRule blocks for mandatory properties and mandatory connections
- **Optional conflict**: ConstraintRule severity=error and MandatoryConnectionConstraint remain active as a save-time fallback; wizard and constraints are complementary

## Implementation Notes

- Frontend: multi-step panel component (modal/drawer); step indicator (breadcrumb or stepper at the top)
- State: wizard state local in the frontend (no draft persistence); payload is only sent on Done
- Context handling: after Done, the triggering context element (catalog table or diagram canvas) must display the new entity without a full reload
- Client app (Electron): fully editable; web portal: wizard only for types with `isReadOnly=false`

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |

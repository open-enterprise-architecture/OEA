---
id: REQ-060
title: Create and manage data object (data-object) as an entity
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-08
  business_objects:
    - entity
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-02
    - SH-03
  concept:
    - concept/20-entities/06-core-entity-types.md
    - concept/40-extensibility/14-extensibility.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-060: Create and manage data object (data-object) as an entity

## Statement

The system MUST enable persons with entity editor permission to create, edit and delete ArchitectureEntities with the EntityType `data-object`; when creating, the system MUST automatically assign an instance-wide unique integer ID and set `entityTypeId=data-object` as an immutable meta-type binding. For `data-object`, the EntityType in the metamodel MUST support the following PropertyDefinitions: `dataClassification` (enum, mandatory), `personalDataCategories` (varchar, optional), `dataOwner` (varchar, warning).

## Rationale

Data objects (logical data entities such as "Customer master data", "Order item") are the anchor points of every lineage analysis. Without them as first-class entities with a stable integer ID, no traversable lineage is possible — one can only rely on string references that cannot be validated.

## Context

`data-object` is not a built-in EntityType but is created in the metamodel by the data architect or lead EA via UC-04. This REQ describes the behavioral requirements for the system when handling such entities — regardless of what the type is actually called, as long as `isConnection=false` holds.

The PropertyDefinitions in this REQ are recommendations for the standard `data-object` EntityType; deviating names are possible via the metamodel.

## Type-Specific Fields

### For type=functional

**Inputs**:

- `entityTypeId`: `data-object` (or an equivalent custom type with `isConnection=false`)
- `name`: display name (e.g., "Customer master data")
- `properties`: PropertyValue list according to the EntityType's PropertyDefinitions

**Processing**:

1. System assigns the next free integer ID (instance-wide, elements and connections share the number space)
2. `entityTypeId` is set and is immutable
3. PropertyValues are validated against the EntityType's PropertyDefinitions (mandatory → 422 if empty; warning → 200 with warning)
4. Entity is persisted; audit log entry

**Outputs**:

- HTTP 201 with complete entity representation including the assigned integer ID
- On validation warning: HTTP 200 with `warnings` array in the response

**Error Cases**:

- Invalid `entityTypeId` → HTTP 422
- Mandatory property empty → HTTP 422 with list of all violated properties
- No permission → HTTP 403

## Acceptance Criteria

**AC1** (Creation with ID assignment):

- When: Lukas creates entity `Customer master data` (entityTypeId=`data-object`)
- Then: HTTP 201; `id` is an integer ≥ 1; `entityTypeId=data-object` is set; ID is unique in the instance

**AC2** (Mandatory property enforces value):

- Given: `dataClassification` is mandatory
- When: Lukas creates an entity without `dataClassification`
- Then: HTTP 422; error message names `dataClassification` as missing

**AC3** (Warning property saves anyway):

- Given: `dataOwner` is warning
- When: Lukas creates an entity without `dataOwner`
- Then: HTTP 200; entity saved; response contains `warnings: ["dataOwner: value missing"]`

**AC4** (entityTypeId immutable):

- Given: Entity with id=42 and entityTypeId=`data-object` exists
- When: Lukas tries to set `entityTypeId=application-component` via PUT
- Then: HTTP 422; `entityTypeId` was not changed

**AC5** (Deletion removes reference warning):

- Given: Entity id=42 is referenced by carries-data connection id=103
- When: Lukas deletes entity id=42
- Then: System warns about referencing connections; after confirmation: entity deleted; carries-data connection id=103 gets `targetEntityId=null` or is also deleted (cascade TBD in ADR)

## Verification Method

- [x] Method: test (automated)
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, as soon as implementation exists

## Dependencies

- **Prerequisites**: UC-04 / REQ-032 (EntityType `data-object` must exist in the metamodel before instances can be created)
- **Downstream effects**: REQ-061 (carries-data) references integer IDs from REQ-060

## Implementation Notes

- `POST /api/v1/entities` with `entityTypeId=data-object`
- Integer ID: DB sequence, instance-wide; no UUID
- PropertyValue validation: server-side against MetamodelConfiguration

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |

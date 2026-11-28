# US-063: Create a Data Object and Populate Properties

**ID**: US-063
**Story Points**: 3
**Status**: in-progress
**Sprint**: S18

## Story

As a Data Architect, I want to create a data object (e.g. "Customer Master") as an independent entity and populate it with properties such as data classification and GDPR categories – so that it receives a stable integer ID that I can reference in data flows.

## Reference

**Use Case**: [UC-08](../use-cases/UC-08-model-data-lineage.md)
**Persona**: Lukas – Senior Data Architect (SH-02)
**Requirements**: [REQ-060](../req/REQ-060-create-data-object.md)

## Acceptance Criteria

**AC1** (creation with ID):

- When: Lukas creates entity "Customer Master" with entityTypeId=`data-object`
- Then: Entity receives integer ID ≥ 1; ID is unique across the entire instance (no collision with other entities or connections)

**AC2** (populate properties):

- When: Lukas sets `dataClassification=confidential`, `personalDataCategories="Name,Address,Email"`
- Then: Properties saved; entity retrievable with correct values

**AC3** (mandatory validation):

- Given: `dataClassification` is mandatory
- When: Lukas tries to save without dataClassification
- Then: Error message names the missing property; nothing saved

**AC4** (entityTypeId immutable):

- When: Lukas tries to change the EntityType of an existing entity
- Then: Change rejected; error message explains immutability

## Technical Notes

- `POST /api/v1/entities` with entityTypeId=`data-object`
- Integer ID is assigned server-side as the next free sequence number (DB sequence)

## Implementation Notes

Realized by the change `us-065-lineage-model-and-query` (S18, change 1 of 2): the TOGAF starter package 1.5.0 brings "Data Object" with the optional properties "Data classification", "Personal data categories" and "Data owner" (REQ-060). The story stays in progress until the lineage page follows in `us-066-lineage-view`.

## Definition of Done

- [ ] AC1–AC4 met
- [ ] Tests: creation, properties, mandatory error, type immutability
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

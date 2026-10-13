# US-063: Create a Data Object and Populate Properties

**ID**: US-063
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

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

## Definition of Done

- [ ] AC1–AC4 met
- [ ] Tests: creation, properties, mandatory error, type immutability
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

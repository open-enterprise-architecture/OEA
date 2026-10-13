---
id: REQ-061
title: n-Connection carries-data — link DataFlow with DataObject
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
  adrs:
    - ADR-010
supersedes: []
superseded_by: []
---

# REQ-061: n-Connection carries-data — link DataFlow with DataObject

## Statement

The system MUST make it possible to create an ArchitectureEntity of type `carries-data` (`isConnection=true`) where `sourceEntityId` points to a connection entity (i.e., an ArchitectureEntity with `isConnection=true`, e.g., `data-flow`) and `targetEntityId` points to a non-connection entity (e.g., `data-object`); this requires that the EntityTypeDefinition of the source type has `allowsConnectionAsSource=true`. Each `carries-data` instance receives its own integer ID from the same instance-wide number space.

## Rationale

Without n-connections, lineage relies on string references (property string) — semantically weak, not FK-validated, hard to query. The `carries-data` connection as a first-class entity enables traversable lineage queries (REQ-062), type-safe references and later extensions (e.g., column-level lineage through properties on `carries-data`). This is the core differentiator compared to Abacus/Avolution (ADR-010 proposed, option B).

## Context

[ADR-010](../../adrs/ADR-010-n-connection-data-lineage.md) (proposed, option B) describes the decision basis. This REQ presupposes option B.

**Metamodel extension (prerequisite)**:
`EntityTypeDefinition` in MetamodelConfiguration receives a new field `allowsConnectionAsSource: bool` (default: `false`). For the EntityType `data-flow` this is set to `true`. This makes `data-flow` the only type (besides others, if configured by the architecture team) that may appear as `sourceEntityId` in another connection.

**entity.md BR-04 relaxation**:
Currently: `sourceEntityId` MUST point to an entity with `isConnection=false`.
New: `sourceEntityId` may point to an entity with `isConnection=true`, provided its EntityTypeDefinition has `allowsConnectionAsSource=true`.

**Depth**: v1.0 supports exactly one nesting level (connection-of-connection). A `carries-data` connection must not itself be the source of a further n-connection.

## Type-Specific Fields

### For type=functional

**Inputs**:

- `entityTypeId`: `carries-data` (isConnection=true; allowsConnectionAsSource=false, since carries-data is itself not a further connection source)
- `sourceEntityId`: integer ID of an ArchitectureEntity with `isConnection=true` and `allowsConnectionAsSource=true` (e.g., DataFlow id=5)
- `targetEntityId`: integer ID of an ArchitectureEntity with `isConnection=false` (e.g., DataObject id=42)
- `name`: optional (e.g., "ERP→DWH carries customer master data")

**Processing**:

1. Check `sourceEntityId`: entity must exist; its EntityType must have `allowsConnectionAsSource=true` → otherwise HTTP 422
2. Check `targetEntityId`: entity must exist; its EntityType must have `isConnection=false` → otherwise HTTP 422
3. Check depth: the `sourceEntityId` entity must not itself be an n-connection (`sourceEntityId.entityType.allowsConnectionAsSource` must not reference that carries-data itself is a source) → v1.0: max. 1 level
4. System assigns the next free integer ID
5. Persist; audit log

**Outputs**:

- HTTP 201 with complete entity representation
- `sourceEntityId` and `targetEntityId` are immutable after creation

**Error Cases**:

- `sourceEntityId` points to an entity with `allowsConnectionAsSource=false` → HTTP 422 with note "EntityType X does not allow a connection source"
- `targetEntityId` points to a connection entity → HTTP 422
- Both IDs identical → HTTP 422
- Depth > 1 violated → HTTP 422 "n-connection depth exceeded (max. 1)"

## Acceptance Criteria

**AC1** (Create carries-data):

- Given: DataFlow id=5 (`data-flow`, `allowsConnectionAsSource=true`); DataObject id=42 (`data-object`)
- When: Lukas creates a `carries-data` connection with sourceEntityId=5, targetEntityId=42
- Then: HTTP 201; carries-data receives its own integer ID (e.g., 103); sourceEntityId=5 and targetEntityId=42 set

**AC2** (Rejection if source has no allowsConnectionAsSource):

- Given: ApplicationComponent id=1 (`application-component`, `allowsConnectionAsSource=false`)
- When: Lukas tries to create carries-data with sourceEntityId=1
- Then: HTTP 422; error message names `application-component` as not allowed as a connection source

**AC3** (Rejection if target=connection):

- Given: DataFlow id=5 (isConnection=true) as potential targetEntityId
- When: Lukas tries to create carries-data with targetEntityId=5
- Then: HTTP 422

**AC4** (Immutability of source/target):

- Given: carries-data id=103 with sourceEntityId=5, targetEntityId=42
- When: Lukas tries PUT with sourceEntityId=6
- Then: HTTP 422; source/target were not changed

**AC5** (Depth limit v1.0):

- Given: carries-data id=103; carries-data has `allowsConnectionAsSource=false`
- When: Lukas tries to create a further connection with sourceEntityId=103
- Then: HTTP 422 "n-connection depth exceeded"

## Verification Method

- [x] Method: test (automated)
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, as soon as implementation exists

## Dependencies

- **Prerequisites**: REQ-060 (DataObject must exist); metamodel-configuration must support `allowsConnectionAsSource` on EntityTypeDefinition (change to BR-04 in entity.md)
- **Downstream effects**: REQ-062 (lineage API traverses carries-data connections); REQ-063 (canvas shows 3-dot indicator when carries-data exist)

## Implementation Notes

- `POST /api/v1/entities` with entityTypeId=`carries-data`; source/target validation server-side
- DB: same `architecture_entities` table as all other entities; source_entity_id may point to rows with isConnection=true (FK remains on architecture_entities, no separate table)
- Depth check: when creating an n-connection, check whether `sourceEntity.entityType.allowsConnectionAsSource=true` AND `sourceEntity.entityType` itself does not grant a further n-connection source permission

## Implementation

- ADR(s): [ADR-010](../../adrs/ADR-010-n-connection-data-lineage.md) (proposed)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft; n-connection option B per ADR-010 |

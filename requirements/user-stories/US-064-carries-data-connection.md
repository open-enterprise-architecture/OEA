# US-064: carries-data Connection — Link DataFlow to DataObject

**ID**: US-064
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Data Architect, I want to create a `carries-data` connection between a data flow and a data object – so that the data flow is explicitly regarded as the carrier of this data object and the lineage becomes traversable.

## Reference

**Use Case**: [UC-08](../use-cases/UC-08-model-data-lineage.md)
**Persona**: Lukas – Senior Data Architect (SH-02)
**Requirements**: [REQ-061](../req/REQ-061-n-connection-carries-data.md)

## Acceptance Criteria

**AC1** (create carries-data):

- Given: DataFlow id=5 (`data-flow`, `allowsConnectionAsSource=true`); DataObject id=42
- When: Lukas creates carries-data (sourceEntityId=5, targetEntityId=42)
- Then: carries-data receives its own integer ID (e.g. 103); sourceEntityId=5 and targetEntityId=42 set correctly

**AC2** (rejection: source without allowsConnectionAsSource):

- Given: ApplicationComponent id=1 (`allowsConnectionAsSource=false`)
- When: Lukas tries to create carries-data with sourceEntityId=1
- Then: HTTP 422; error message: "application-component does not allow a connection source"

**AC3** (rejection: target is a connection):

- When: Lukas tries to create carries-data with targetEntityId=5 (DataFlow, isConnection=true)
- Then: HTTP 422

**AC4** (depth limit v1.0):

- Given: carries-data id=103 (`allowsConnectionAsSource=false`)
- When: Lukas tries to create another connection with sourceEntityId=103
- Then: HTTP 422 "n-connection depth exceeded"

**AC5** (source/target immutable):

- Given: carries-data id=103 with sourceEntityId=5
- When: Lukas tries to change sourceEntityId to 6
- Then: HTTP 422; sourceEntityId remains 5

## Technical Notes

- `POST /api/v1/entities` with entityTypeId=`carries-data`, sourceEntityId, targetEntityId
- Validation of `allowsConnectionAsSource` server-side against MetamodelConfiguration

## Definition of Done

- [ ] AC1–AC5 met
- [ ] Tests: creation, source rejection, target rejection, depth, immutability
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

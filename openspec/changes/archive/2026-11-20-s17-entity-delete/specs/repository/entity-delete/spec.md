## ADDED Requirements

### Requirement: Preview of a deletion

A principal with `entity:delete` SHALL read what deleting an entity would do: the number of its children on all levels, the number of connections deleted with it and with the children, and the active solutions with a delta `modified` or `retiring` on any of them. An unknown or deleted entity SHALL answer 404, a structuring aid or an automatic composition connection 422.

Trace: REQ-146, US-074, ADR-019, owner decisions 2026-11-20

#### Scenario: Entity with children and connections

- **GIVEN** "ERP" has the children "ERP Finance" and "ERP Logistics", "ERP Logistics" has the child "Warehouse", and the four have 4 connections, one of them to "CRM"
- **WHEN** Kurt reads the deletion preview of "ERP"
- **THEN** it names 3 children and 4 connections

#### Scenario: Delta in an active solution

- **GIVEN** "ERP Finance" has a delta `modified` in the active solution "Finance Upgrade" and one in the implemented solution "Old Upgrade"
- **WHEN** Kurt reads the deletion preview of "ERP"
- **THEN** it names "Finance Upgrade" and not "Old Upgrade"

### Requirement: Delete an entity

A principal with `entity:delete` SHALL soft-delete an element entity with `If-Match`: the entity, its children on all levels and every connection of one of them SHALL be deleted with one time, actor and the entity as cascade origin, each audited, and their IDs answered. A connection SHALL be deleted alone. Open issues on any of them SHALL give 409 `entity-blocked`; a structuring aid or an automatic composition connection 422.

Trace: REQ-146, US-074, ADR-019, owner decisions 2026-11-20

#### Scenario: Deleted with children and connections

- **WHEN** Kurt deletes "ERP" with its current version
- **THEN** the response is 200 listing 8 IDs; "ERP", its 3 children and the 4 connections are deleted, audited as `entity.deleted` for "ERP" and `entity.cascade-deleted` naming "ERP" for the others, and none of them is listed in catalogs

#### Scenario: Connection deleted alone

- **WHEN** Kurt deletes the connection "ERP → CRM"
- **THEN** only the connection is deleted, and "ERP" and "CRM" stay

#### Scenario: Stale version

- **WHEN** Kurt deletes "ERP" with an old version, and then without `If-Match`
- **THEN** the first request gives 412, the second 428, and nothing is deleted

#### Scenario: Without entity:delete

- **WHEN** Anna, holding `entity:write` but not `entity:delete`, deletes "ERP"
- **THEN** the response is 403, and nothing is deleted

#### Scenario: Scoping connection in the cascade

- **GIVEN** a connection of "ERP" has a type used as `scopingConnectionType`
- **WHEN** Kurt, without `visibility-scope:assign`, deletes "ERP"
- **THEN** the response is 403, and nothing is deleted

#### Scenario: Blocked by open issues

- **GIVEN** the child "ERP Finance" has an unresolved open issue of a solution
- **WHEN** Kurt deletes "ERP"
- **THEN** the response is 409 `entity-blocked` naming "ERP Finance", and nothing is deleted

#### Scenario: Structuring aid

- **WHEN** Kurt deletes the structuring aid "Finance landscape" through this operation
- **THEN** the response is 422, and nothing is deleted

#### Scenario: Already deleted

- **WHEN** Kurt deletes an entity that is already deleted
- **THEN** the response is 404

### Requirement: Restore a deleted entity

A principal with `entity:restore` SHALL restore a deleted entity with `If-Match`: the entity and the descendants and connections its deletion took along SHALL be restored with unchanged versions, each audited as `entity.undeleted`. A connection with a deleted endpoint or a changed parent SHALL stay deleted and be named. An element whose parent is deleted SHALL go to the top level. An active entity SHALL give 409.

Trace: REQ-159, ADR-019 (cascade restore), owner decisions 2026-11-20

#### Scenario: Restored with its cascade

- **WHEN** Kurt restores the deleted "ERP"
- **THEN** "ERP", its 3 children and the 4 connections are active again, with their versions unchanged, each audited as `entity.undeleted`

#### Scenario: Other endpoint still deleted

- **GIVEN** "CRM" was deleted after "ERP"
- **WHEN** Kurt restores "ERP"
- **THEN** the connection "ERP → CRM" stays deleted and is named with the reason `endpoint-deleted`

#### Scenario: Separate deletions in the same instant

- **GIVEN** "ERP" and "CRM", not connected, were deleted by Kurt in the same instant in two requests
- **WHEN** Kurt restores "ERP"
- **THEN** "CRM" and its cascade stay deleted

#### Scenario: Restoring a child alone

- **WHEN** Kurt restores the deleted child "ERP Logistics" while "ERP" stays deleted
- **THEN** "ERP Logistics", "Warehouse" and the connections deleted with them whose other endpoint is active are restored, and "ERP Logistics" is placed at the top level as a new version and named in the answer

#### Scenario: Not deleted

- **WHEN** Kurt restores the active "CRM"
- **THEN** the response is 409 `entity-not-deleted`, and nothing changes

#### Scenario: Without entity:restore

- **WHEN** Anna, without `entity:restore`, restores "ERP"
- **THEN** the response is 403, and nothing is restored

### Requirement: Version restore needs an active entity

Restoring a version of a deleted entity, or reading its restore preview, SHALL be rejected with 409 `entity-deleted`, and nothing SHALL change; the entity SHALL be undeleted first.

Trace: ADR-019, REQ-101, owner decision 2026-11-20

#### Scenario: Version restore of a deleted entity

- **GIVEN** "ERP" is deleted
- **WHEN** Kurt restores version 2 of "ERP", or reads the restore preview
- **THEN** the response is 409 `entity-deleted`, and no version is written

#### Scenario: After undelete

- **GIVEN** "ERP" was undeleted
- **WHEN** Kurt restores version 2 of "ERP"
- **THEN** the version is restored as before

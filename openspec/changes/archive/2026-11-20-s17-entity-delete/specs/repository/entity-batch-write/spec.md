## ADDED Requirements

### Requirement: External ID of a deleted entity

An `upsert` item whose external ID belongs to a deleted entity SHALL restore that entity with its cascade and then update it when the writer holds `entity:restore`, and its result SHALL say so. Without that permission, and always for `create-only` and `skip-existing`, the item SHALL be rejected with `EXTERNAL_ID_DELETED`, and an atomic batch SHALL change nothing. The external ID SHALL never be reused by a new entity.

Trace: US-141, #70, ADR-019, owner decision 2026-11-20

#### Scenario: Restored by the batch

- **GIVEN** the entity with the external ID "sap:erp" is deleted
- **WHEN** an integration with `entity:write` and `entity:restore` upserts "sap:erp" with a new description
- **THEN** the item is `updated` with `restored` true, the entity is restored with its cascade and gets the description in a new version, audited as `entity.undeleted` and `entity.updated`

#### Scenario: Without entity:restore

- **WHEN** an integration with `entity:write` only upserts "sap:erp" in an atomic batch with a second, valid item
- **THEN** the item is rejected with `EXTERNAL_ID_DELETED`, the entity stays deleted, and the second item is not written

#### Scenario: Create-only

- **WHEN** an integration with `entity:write` and `entity:restore` sends "sap:erp" with strategy `create-only`
- **THEN** the item is rejected with `EXTERNAL_ID_DELETED`, and the entity stays deleted

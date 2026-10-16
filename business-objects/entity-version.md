---
id: entity-version
title: EntityVersion – Entity Version History
version: 0.2.0
status: draft
created: 2026-06-27
last_updated: 2026-10-16
author: business-engineer
references:
  business_objects:
    - entity
    - person
  adrs:
    - adrs/ADR-016-persistence-strategy.md
    - adrs/ADR-022-structured-property-model.md
  use_cases:
    - UC-14
    - UC-15
    - UC-16
---

# EntityVersion – Entity Version History

## Purpose

`EntityVersion` is the immutable record of one version of an `ArchitectureEntity`. Every create and every change of an entity creates a new version. The history is stored delta-based (ADR-022): a **version header** records who changed the entity and when; for each changed system field and each changed property only the change itself is recorded. Unchanged fields produce no entry, so the history has no redundancy. Together with the current state of the entity, the deltas reconstruct every earlier version, which is the basis for the change history (UC-14) and restore (UC-15, UC-16).

`EntityVersion` records are **never editable or deletable**. They are the history of the repository, not the audit trail (see [audit-event](./audit-event.md)).

## Attributes

### Version header (`entity_version_headers`)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | integer (BIGSERIAL) | required | | system-assigned | Primary key |
| entityId | integer | required | | FK → ArchitectureEntity.id; immutable | Entity to which this version belongs |
| version | integer | required | | ≥ 1; unique per entityId | Version number; equals `ArchitectureEntity.version` after the change |
| changedBy | reference | required | | target: person | Person who made the change |
| changedAt | datetime | required | now | ISO 8601, UTC | Time of the change |

### System field delta (`entity_system_field_history`)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | integer (BIGSERIAL) | required | | system-assigned | Primary key |
| entityId | integer | required | | FK → ArchitectureEntity.id | Entity |
| entityVersion | integer | required | | version created by the change | Version in which the field changed |
| fieldName | enum | required | | `name`, `description`, `is_logical`, `metatype_id`, `source_entity_id`, `target_entity_id` | Changed system field |
| oldValue | string | optional | null | serialized as text; null for a field that had no value | Value before the change |
| newValue | string | optional | null | serialized as text; null for a removed value | Value after the change |
| changedAt | datetime | required | now | ISO 8601, UTC | Time of the change |
| changedBy | reference | required | | target: person | Person who made the change |

### Property value delta (`entity_property_value_history`)

| Attribute | Type | Optional | Default | Constraints | Description |
|---|---|---|---|---|---|
| id | integer (BIGSERIAL) | required | | system-assigned | Primary key |
| entityId | integer | required | | FK → ArchitectureEntity.id | Entity |
| propertyDefId | UUID | required | | FK → PropertyDefinition.id | Changed property |
| entityVersion | integer | required | | version created by the change | Version in which the property changed |
| value (typed) | text, integer, boolean, date, reference or enum | optional | null | at most one typed value column set; all null if the property had no value | Value **before** the change |
| changedAt | datetime | required | now | ISO 8601, UTC | Time of the change |
| changedBy | reference | required | | target: person | Person who made the change |

## Relationships

| Relationship | Target Object | Cardinality | Optional | Description |
|---|---|---|---|---|
| versionOf | [entity](./entity.md) | n:1 | no | Each version belongs to exactly one entity |
| changedByPerson | [person](./person.md) | n:1 | no | Person who made the change |

## Business Rules

| Rule ID | Statement | Trigger | Compliance Reference |
|---|---|---|---|
| BR-01 | Creating an entity writes the version header of version 1 without deltas; the current state is the state of version 1 | onCreate (entity) | ADR-022 |
| BR-02 | History records are **immutable and not deletable** after creation — neither via API nor via GUI; the runtime database role holds only INSERT and SELECT on the history tables; rows are removed only by the purge of soft-deleted entities (REQ-190) | always | ADR-016, ADR-033 §10 |
| BR-03 | `version` + `entityId` form a unique key of the version header | onCreate | – |
| BR-04 | The history records and the entity change MUST be written in the **same DB transaction**; if the transaction fails, neither history nor change is persisted | onUpdate (entity) | ADR-016 |
| BR-05 | A change that modifies no field and no property value creates no version, no delta and no audit event | onUpdate (entity) | ADR-022 |
| BR-06 | Each changed system field produces one system field delta with old and new value; each changed property produces one property value delta with the value before the change | onUpdate (entity) | ADR-022 |

## Lifecycle

`EntityVersion` records have no lifecycle. They are created with every entity change and exist permanently. ADR-033 §10 states that history rows are removed only by the purge of soft-deleted entities (REQ-190); the [audit events](./audit-event.md) remain independently.

## Example

*Entity "SAP ERP" (id=42) is created by Kurt and changed twice:*

| Version | changedBy | System field deltas | Property value deltas (value before) |
|---|---|---|---|
| 1 | Kurt | – | – |
| 2 | Michael | – | `Status`: `planned` |
| 3 | Kurt | `name`: `SAP R/3` → `SAP ERP` | `Owner`: null |

Restoring version 1 applies the deltas of versions 3 and 2 backwards to the current state.

## Open Points

- Change reason, restore markers (`restoredFromVersion`, `restoredFields`) and the metamodel context of a version are decided with UC-14 to UC-16; they are not yet part of `docs/data-model.puml`.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-27 | Business Engineer | Initial draft; basis for UC-14 (change history) and UC-15 (restore); snapshot content, BRs, and lifecycle defined |
| 0.1.1 | 2026-10-13 | Business Engineer | BR-02: append-only for the runtime role, deletion only by the REQ-190 purge (ADR-033 §10, DM-A9); the entity history is not the audit trail (see [audit-event](./audit-event.md)) |
| 0.2.0 | 2026-10-16 | Lukas Mathis | Aligned with ADR-022 and `docs/data-model.puml` (change us-141-entity-batch-upsert): version header plus system field and property value deltas instead of JSON snapshots; unchanged writes create no version (BR-05, replaces the 304 rule); restore markers and change reason moved to open points |
